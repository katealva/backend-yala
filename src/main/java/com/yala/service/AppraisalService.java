package com.yala.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.appraisal.ResponseAppraisalDTO;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * "Agente de Tasación por Foto": envía la imagen de un coleccionable a OpenAI (Vision, gpt-5-nano por
 * defecto) y devuelve una identificación estructurada (categoría / franquicia / personaje / variante /
 * confianza). El match contra el dataset y el rango de precio los resuelve el frontend. Reutiliza el mismo
 * patrón de {@link LiveCommentSummaryService}. Degrada con gracia si no hay API key configurada.
 */
@Slf4j
@Service
public class AppraisalService {

    private static final String SYSTEM_PROMPT =
            "Eres un experto tasador de coleccionables. Analizas la foto e identificas el ítem. "
            + "Categorías válidas EXACTAS: funko, nendoroid, manga, comic, tcg. "
            + "'tcg' = cartas coleccionables (Pokémon, Yu-Gi-Oh!, Magic, etc.). "
            + "Responde SOLO un objeto JSON válido, sin texto adicional, con esta forma exacta:\n"
            + "{\"category\": \"funko|nendoroid|manga|comic|tcg\", \"franchise\": \"...\", "
            + "\"character\": \"...\", \"variant\": \"...\", \"confidence\": 0.0, \"recognizable\": true}\n"
            + "Reglas: 'confidence' es un número entre 0 y 1. 'recognizable' es false si la foto es de mala "
            + "calidad, está borrosa, o no muestra un coleccionable de esas categorías. Si algún campo no es "
            + "visible, usa cadena vacía. 'franchise' es la saga (ej. Pokémon, One Piece, Marvel) y 'character' "
            + "el personaje o título (ej. Charizard, Luffy, Spider-Man). 'variant' es la edición/rareza/"
            + "exclusividad si se ve (ej. '1st Edition Holo', 'Chase', 'Exclusiva SDCC'), o cadena vacía.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AppraisalService(
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

    public ResponseAppraisalDTO identify(String imageBase64) {
        if (!isConfigured()) {
            return ResponseAppraisalDTO.unrecognizable(
                    "La tasación con IA aún no está configurada (falta OPENAI_API_KEY).");
        }
        String dataUrl = toDataUrl(imageBase64);
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text",
                                            "Identifica este coleccionable y responde solo el JSON."),
                                    Map.of("type", "image_url",
                                            "image_url", Map.of("url", dataUrl))))));
            // Pre-serialized String body (un Map puede ser descartado por el transporte JDK HttpClient).
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
                return ResponseAppraisalDTO.unrecognizable(
                        "No pudimos analizar la foto en este momento. Intenta de nuevo.");
            }
            return parse(content);
        } catch (Exception e) {
            log.warn("OpenAI appraisal failed: {}", e.getMessage());
            return ResponseAppraisalDTO.unrecognizable(
                    "No pudimos analizar la foto en este momento. Intenta de nuevo.");
        }
    }

    private ResponseAppraisalDTO parse(String content) {
        try {
            // El modelo a veces envuelve el JSON en ```json ... ```; lo limpiamos.
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            JsonNode n = objectMapper.readTree(cleaned);
            String category = normalizeCategory(text(n, "category"));
            boolean recognizable = n.path("recognizable").asBoolean(true);
            double confidence = n.path("confidence").asDouble(0.0);
            if ("unknown".equals(category)) recognizable = false;
            return new ResponseAppraisalDTO(
                    category,
                    text(n, "franchise"),
                    text(n, "character"),
                    text(n, "variant"),
                    Math.max(0.0, Math.min(1.0, confidence)),
                    recognizable,
                    recognizable ? null
                            : "No pudimos reconocer un coleccionable. Sube una foto más clara y bien encuadrada.");
        } catch (Exception e) {
            log.warn("Appraisal JSON parse failed: {}", e.getMessage());
            return ResponseAppraisalDTO.unrecognizable(
                    "No pudimos interpretar la identificación. Intenta con otra foto.");
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static String normalizeCategory(String raw) {
        if (raw == null) return "unknown";
        String c = raw.toLowerCase().trim();
        return switch (c) {
            case "funko", "funkos", "funko pop", "pop" -> "funko";
            case "nendoroid", "nendoroids" -> "nendoroid";
            case "manga", "mangas" -> "manga";
            case "comic", "comics", "cómic", "cómics", "comic book" -> "comic";
            case "tcg", "card", "cards", "carta", "cartas", "trading card" -> "tcg";
            default -> "unknown";
        };
    }

    private String toDataUrl(String imageBase64) {
        String s = imageBase64.trim();
        return s.startsWith("data:") ? s : "data:image/jpeg;base64," + s;
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
