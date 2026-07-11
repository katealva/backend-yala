package com.yala.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.appraisal.ResponseAppraisalDTO;
import com.yala.dto.appraisal.ResponseAppraisalDTO.Pricing;
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
            "Eres un experto en cartas coleccionables (TCG). Analizas la foto e identificas la CARTA. "
            + "Juegos válidos: Pokémon, Yu-Gi-Oh!, Magic: The Gathering, One Piece Card Game, Dragon Ball Super, "
            + "Digimon, Disney Lorcana, Star Wars Unlimited, Gundam, entre otros TCG. "
            + "Responde SOLO un objeto JSON válido, sin texto adicional, con esta forma exacta:\n"
            + "{\"category\": \"tcg\", \"franchise\": \"<juego>\", \"character\": \"<nombre de la carta>\", "
            + "\"variant\": \"<edición/rareza si se ve>\", \"confidence\": 0.0, \"recognizable\": true}\n"
            + "Reglas: 'category' es \"tcg\" si es una carta de esos juegos; si no, \"unknown\". 'franchise' es "
            + "el juego (ej. Pokémon, Yu-Gi-Oh!, Magic). 'character' es el NOMBRE de la carta tal como aparece "
            + "(ej. Charizard, Dark Magician, Black Lotus). 'variant' es la edición/rareza/set si se ve, o cadena "
            + "vacía. 'confidence' es un número entre 0 y 1. 'recognizable' es false si la foto es de mala calidad, "
            + "está borrosa, o NO es una carta TCG.";

    private final RestClient restClient;
    private final JustTcgService justTcgService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AppraisalService(
            RestClient.Builder restClientBuilder,
            JustTcgService justTcgService,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${openai.model:gpt-5-nano}") String model) {
        this.restClient = restClientBuilder.build();
        this.justTcgService = justTcgService;
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
                                            "Identifica esta carta TCG y responde solo el JSON."),
                                    Map.of("type", "image_url",
                                            "image_url", Map.of("url", dataUrl))))));
            // Pre-serialized String body (un Map puede ser descartado por el transporte JDK HttpClient).
            String payload = objectMapper.writeValueAsString(body);

            // Leemos como bytes y parseamos con nuestro ObjectMapper (UTF-8 garantizado; evita mojibake
            // en nombres con acentos como "Pokémon").
            byte[] raw = restClient.post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(byte[].class);

            String content = extractContent(raw);
            if (content == null || content.isBlank()) {
                return ResponseAppraisalDTO.unrecognizable(
                        "No pudimos analizar la foto en este momento. Intenta de nuevo.");
            }
            return withPricing(parse(content));
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
                    null,
                    recognizable ? null
                            : "No pudimos reconocer una carta TCG. Sube una foto más clara y bien encuadrada.");
        } catch (Exception e) {
            log.warn("Appraisal JSON parse failed: {}", e.getMessage());
            return ResponseAppraisalDTO.unrecognizable(
                    "No pudimos interpretar la identificación. Intenta con otra foto.");
        }
    }

    // Para cartas TCG reconocidas, adjunta el precio real de JustTCG (o una nota si no hay match).
    private ResponseAppraisalDTO withPricing(ResponseAppraisalDTO id) {
        if (!id.recognizable() || !"tcg".equals(id.category())) {
            return id;
        }
        Pricing pricing = justTcgService.priceCard(id.franchise(), id.character());
        if (pricing == null) {
            String name = id.character() != null && !id.character().isBlank() ? id.character() : "esta carta";
            return new ResponseAppraisalDTO(
                    id.category(), id.franchise(), id.character(), id.variant(),
                    id.confidence(), id.recognizable(), null,
                    "Identificamos " + name + ", pero aún no encontramos su precio en JustTCG.");
        }
        return new ResponseAppraisalDTO(
                id.category(), id.franchise(), id.character(), id.variant(),
                id.confidence(), id.recognizable(), pricing, null);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static String normalizeCategory(String raw) {
        if (raw == null) return "unknown";
        String c = raw.toLowerCase().trim();
        return switch (c) {
            case "tcg", "card", "cards", "carta", "cartas", "trading card", "trading card game" -> "tcg";
            default -> "unknown";
        };
    }

    private String toDataUrl(String imageBase64) {
        String s = imageBase64.trim();
        return s.startsWith("data:") ? s : "data:image/jpeg;base64," + s;
    }

    private String extractContent(byte[] raw) throws java.io.IOException {
        if (raw == null || raw.length == 0) return null;
        JsonNode root = objectMapper.readTree(raw);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? null : content.asText(null);
    }
}
