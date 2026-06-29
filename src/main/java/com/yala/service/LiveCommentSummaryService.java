package com.yala.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.live.ResponseLiveCommentSummaryDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.LiveComment;
import com.yala.model.LiveStream;
import com.yala.repository.LiveCommentRepository;
import com.yala.repository.LiveStreamRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Generates an AI summary of a live's chat comments for the host (seller). Uses OpenAI Chat
 * Completions (gpt-5-nano by default). The summary is returned to the caller only — it is not
 * broadcast to viewers. Degrades gracefully when no API key is configured.
 */
@Slf4j
@Service
public class LiveCommentSummaryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final String SYSTEM_PROMPT =
            "Eres un asistente que resume el chat en vivo de una subasta de coleccionables, en español "
            + "peruano (tuteo). Resume en 3 a 5 viñetas: temas principales, preguntas frecuentes de la "
            + "audiencia, sentimiento general y dudas que el vendedor debería responder. Sé breve y concreto; "
            + "si no hay suficiente información, dilo.";

    private final LiveStreamRepository liveStreamRepository;
    private final LiveCommentRepository liveCommentRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public LiveCommentSummaryService(
            LiveStreamRepository liveStreamRepository,
            LiveCommentRepository liveCommentRepository,
            RestClient.Builder restClientBuilder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.model:gpt-5-nano}") String model) {
        this.liveStreamRepository = liveStreamRepository;
        this.liveCommentRepository = liveCommentRepository;
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Transactional(readOnly = true)
    public ResponseLiveCommentSummaryDTO summarize(Long streamId, String sellerEmail, Integer limit) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Live stream not found with id: " + streamId));
        if (!stream.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can summarize this live's comments");
        }

        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<LiveComment> recent = liveCommentRepository
                .findByLiveStreamIdOrderByCreatedAtDesc(streamId,
                        PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        if (recent.isEmpty()) {
            return new ResponseLiveCommentSummaryDTO(
                    "Aún no hay comentarios para resumir.", 0, LocalDateTime.now());
        }
        if (!isConfigured()) {
            return new ResponseLiveCommentSummaryDTO(
                    "El resumen con IA aún no está configurado (falta OPENAI_API_KEY).",
                    recent.size(), LocalDateTime.now());
        }

        // Oldest-first reads more naturally for the model.
        List<String> lines = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            LiveComment c = recent.get(i);
            String author = c.getUser() != null ? c.getUser().getName() : "Anónimo";
            lines.add(author + ": " + c.getText());
        }
        String commentsText = String.join("\n", lines);

        String summary = callOpenAi(commentsText);
        return new ResponseLiveCommentSummaryDTO(summary, recent.size(), LocalDateTime.now());
    }

    private String callOpenAi(String commentsText) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user",
                                    "content", "Resume estos comentarios del live:\n\n" + commentsText)));
            // Pre-serialized String body (a Map body can be dropped by the JDK HttpClient transport).
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
                return "No pudimos generar el resumen en este momento. Intenta de nuevo.";
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("OpenAI summary failed: {}", e.getMessage());
            return "No pudimos generar el resumen en este momento. Intenta de nuevo.";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null) return null;
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
