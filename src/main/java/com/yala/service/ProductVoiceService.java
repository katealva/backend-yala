package com.yala.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.live.ResponseDetectedProductDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.LiveStream;
import com.yala.repository.LiveStreamRepository;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Turns the seller's spoken description of a collectible (captured live by the client's
 * speech recognition after the trigger phrase) into structured flash-auction attributes,
 * using an LLM (ADR-002). Reuses the same OpenAI Chat Completions integration as
 * {@link LiveCommentSummaryService}. Degrades gracefully: when no API key is configured it
 * returns the raw transcript as the title (voice → title still works, just without smart
 * extraction).
 */
@Slf4j
@Service
public class ProductVoiceService {

    private static final String SYSTEM_PROMPT =
            "Eres un experto en coleccionables (cartas Pokémon TCG, Funko Pop, comics) de un marketplace "
            + "peruano. El vendedor, en un live, dijo en voz alta qué producto va a subastar. A partir de su "
            + "descripción, extrae los datos para crear una subasta flash. Responde SOLO un objeto JSON con "
            + "esta forma exacta: {\"title\":\"...\",\"category\":\"...\",\"condition\":\"...\","
            + "\"suggestedBasePrice\":<número o null>,\"confidence\":<0..1>}. "
            + "title: nombre corto y claro del coleccionable (en español). "
            + "category: una de Pokémon TCG, Funko Pop, Comics u otra que corresponda. "
            + "condition: exactamente una de \"Sellado\", \"Como nuevo\", \"Con desgaste\" (deduce la más "
            + "probable si no la dice). suggestedBasePrice: precio base sugerido en soles peruanos (número), "
            + "o null si no hay señal. confidence: qué tan seguro estás (0 a 1). Si el texto no describe un "
            + "producto, usa confidence baja.";

    private final LiveStreamRepository liveStreamRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public ProductVoiceService(
            LiveStreamRepository liveStreamRepository,
            RestClient.Builder restClientBuilder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.model:gpt-5-nano}") String model) {
        this.liveStreamRepository = liveStreamRepository;
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Transactional(readOnly = true)
    public ResponseDetectedProductDTO detect(Long streamId, String sellerEmail, String transcript) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Live stream not found with id: " + streamId));
        if (!stream.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can detect products in this live");
        }

        String text = transcript == null ? "" : transcript.trim();
        if (text.isEmpty()) {
            return new ResponseDetectedProductDTO(null, null, null, null, 0.0);
        }
        if (!isConfigured()) {
            // Fallback: no LLM — use the spoken text as the title so the flow still works.
            return new ResponseDetectedProductDTO(trimTitle(text), null, null, null, 0.0);
        }
        try {
            return callOpenAi(text);
        } catch (Exception e) {
            log.warn("Voice product detection failed: {}", e.getMessage());
            return new ResponseDetectedProductDTO(trimTitle(text), null, null, null, 0.0);
        }
    }

    private ResponseDetectedProductDTO callOpenAi(String transcript) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content",
                                "Descripción del vendedor:\n\n" + transcript)));
        // Pre-serialized body (a Map body can be dropped by the JDK HttpClient transport).
        String payload = objectMapper.writeValueAsString(body);

        Map<?, ?> response = restClient.post()
                .uri(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        String content = extractContent(response);
        if (content == null || content.isBlank()) {
            return new ResponseDetectedProductDTO(trimTitle(transcript), null, null, null, 0.0);
        }
        JsonNode n = objectMapper.readTree(content);
        String title = textOrNull(n, "title");
        return new ResponseDetectedProductDTO(
                title != null ? title : trimTitle(transcript),
                textOrNull(n, "category"),
                textOrNull(n, "condition"),
                n.hasNonNull("suggestedBasePrice") ? (float) n.get("suggestedBasePrice").asDouble() : null,
                n.hasNonNull("confidence") ? n.get("confidence").asDouble() : 0.5);
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String trimTitle(String text) {
        String t = text.trim();
        return t.length() <= 120 ? t : t.substring(0, 120);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> msg) {
                Object content = msg.get("content");
                return content != null ? content.toString() : null;
            }
        }
        return null;
    }
}
