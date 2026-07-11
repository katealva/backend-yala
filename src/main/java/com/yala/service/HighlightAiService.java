package com.yala.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * AI selection of the most iconic moments of a finished live (ADR-001). Given a text
 * timeline of the live (flash auctions, bid bursts, chat) it asks an LLM to pick 3-5
 * highlight windows and write a ready-to-post title/caption for each. Reuses the same
 * OpenAI Chat Completions integration as {@link LiveCommentSummaryService}; degrades to
 * an empty result when no key is configured (the caller falls back to signal heuristics).
 */
@Slf4j
@Service
public class HighlightAiService {

    private static final String SYSTEM_PROMPT =
            "Eres un editor de video para redes sociales de un marketplace de subastas en vivo de "
            + "coleccionables, en español peruano (tuteo). Te doy el timeline de un live que ya terminó "
            + "(subastas flash, ráfagas de pujas y chat), con offsets en milisegundos desde el inicio. "
            + "Elige los 3 a 5 momentos MÁS icónicos para clips cortos (15-30 s) listos para TikTok/Reels. "
            + "Prioriza los cierres de subasta '¡vendido!', las guerras de pujas y los picos de reacción del "
            + "chat. Devuelve SOLO un objeto JSON con esta forma exacta: "
            + "{\"clips\":[{\"startMs\":<int>,\"endMs\":<int>,\"title\":\"...\",\"caption\":\"...\","
            + "\"reason\":\"...\"}]}. startMs/endMs son offsets válidos dentro del live; title corto y "
            + "llamativo; caption listo para publicar con 1-2 hashtags; reason en una frase.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public HighlightAiService(
            RestClient.Builder restClientBuilder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.model:gpt-5-nano}") String model) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** A highlight window chosen by the LLM. */
    public record AiClip(long startMs, long endMs, String title, String caption, String reason) {
    }

    /**
     * Asks the LLM to choose highlight windows from the timeline. Returns an empty list when
     * not configured or on any failure, so the caller can fall back to signal heuristics.
     */
    public List<AiClip> selectClips(String timeline, long liveDurationMs) {
        if (!isConfigured() || timeline == null || timeline.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content",
                                    "Duración del live: " + liveDurationMs + " ms.\n\nTimeline:\n" + timeline)));
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
                return List.of();
            }
            return parseClips(content, liveDurationMs);
        } catch (Exception e) {
            log.warn("Highlight AI selection failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<AiClip> parseClips(String content, long liveDurationMs) throws Exception {
        JsonNode root = objectMapper.readTree(content);
        JsonNode clips = root.path("clips");
        List<AiClip> out = new ArrayList<>();
        if (clips.isArray()) {
            for (JsonNode n : clips) {
                long start = n.path("startMs").asLong(-1);
                long end = n.path("endMs").asLong(-1);
                if (start < 0 || end <= start) {
                    continue;
                }
                // Clamp to the live's bounds.
                start = Math.max(0, Math.min(start, liveDurationMs));
                end = Math.max(start + 1000, Math.min(end, liveDurationMs));
                out.add(new AiClip(
                        start, end,
                        n.path("title").asText(""),
                        n.path("caption").asText(""),
                        n.path("reason").asText("")));
            }
        }
        return out;
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
