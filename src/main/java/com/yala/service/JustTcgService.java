package com.yala.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.appraisal.ResponseAppraisalDTO.Comparable;
import com.yala.dto.appraisal.ResponseAppraisalDTO.Pricing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Precios reales de cartas TCG vía JustTCG (https://justtcg.com/docs). Dada la carta identificada por la IA
 * (juego + nombre), consulta {@code GET /cards} y arma un rango de precio (min–max de las variantes) más
 * algunos comparables. Devuelve null si no hay match o no está configurada la API key.
 */
@Slf4j
@Service
public class JustTcgService {

    private static final int MAX_COMPARABLES = 3;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;

    public JustTcgService(
            RestClient.Builder restClientBuilder,
            @Value("${justtcg.api-key:}") String apiKey,
            @Value("${justtcg.base-url:https://api.justtcg.com/v1}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param franchise juego que dio la IA (ej. "Pokémon", "Yu-Gi-Oh!")
     * @param cardName  nombre de la carta (ej. "Charizard")
     * @return rango de precio + comparables, o null si no hay match.
     */
    public Pricing priceCard(String franchise, String cardName) {
        if (!isConfigured() || cardName == null || cardName.isBlank()) return null;
        String game = toGameSlug(franchise);

        try {
            // Leemos como bytes y parseamos con nuestro ObjectMapper (UTF-8 garantizado y evita
            // problemas de converter con JsonNode).
            byte[] raw = restClient.get()
                    .uri(baseUrl + "/cards?q={q}&game={game}&limit=20", cardName.trim(), game)
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .body(byte[].class);

            JsonNode root = raw == null || raw.length == 0 ? null : objectMapper.readTree(raw);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.info("JustTCG: sin resultados para q='{}' game='{}'", cardName, game);
                return null;
            }

            double min = Double.MAX_VALUE;
            double max = 0;
            String matchedName = null;
            List<Comparable> comparables = new ArrayList<>();

            for (JsonNode card : data) {
                if (matchedName == null) matchedName = text(card, "name");
                String setName = text(card, "set_name");
                for (JsonNode variant : card.path("variants")) {
                    double price = variant.path("price").asDouble(0);
                    if (price <= 0) continue;
                    if (price < min) min = price;
                    if (price > max) max = price;
                    if (comparables.size() < MAX_COMPARABLES) {
                        String cond = text(variant, "condition");
                        String printing = text(variant, "printing");
                        String title = buildTitle(setName, cond, printing);
                        comparables.add(new Comparable(title, round(price)));
                    }
                }
            }

            if (max <= 0 || comparables.isEmpty()) {
                return null;
            }
            return new Pricing(
                    matchedName != null && !matchedName.isBlank() ? matchedName : cardName.trim(),
                    game,
                    round(min),
                    round(max),
                    "USD",
                    comparables);
        } catch (Exception e) {
            log.warn("JustTCG pricing failed for '{}' ({}): {}", cardName, game, e.getMessage());
            return null;
        }
    }

    // Mapea la franquicia que da la IA al slug de juego de JustTCG.
    private static String toGameSlug(String franchise) {
        String f = franchise == null ? "" : franchise.toLowerCase(Locale.ROOT);
        if (f.contains("pokemon") || f.contains("pokémon")) return "pokemon";
        if (f.contains("yu-gi") || f.contains("yugi") || f.contains("yu gi")) return "yugioh";
        if (f.contains("magic") || f.contains("gathering") || f.contains("mtg")) return "magic-the-gathering";
        if (f.contains("one piece")) return "one-piece-card-game";
        if (f.contains("dragon ball")) return "dragon-ball-super-fusion-world";
        if (f.contains("digimon")) return "digimon-card-game";
        if (f.contains("lorcana") || f.contains("disney")) return "disney-lorcana";
        if (f.contains("star wars")) return "star-wars-unlimited";
        if (f.contains("gundam")) return "gundam-card-game";
        if (f.contains("flesh")) return "flesh-and-blood-tcg";
        if (f.contains("union arena")) return "union-arena";
        if (f.contains("universus")) return "universus";
        if (f.contains("lol") || f.contains("league of legends") || f.contains("riftbound"))
            return "riftbound-league-of-legends-trading-card-game";
        if (f.contains("hololive")) return "hololive-official-card-game";
        if (f.contains("sorcery")) return "sorcery-contested-realm";
        if (f.contains("grand archive")) return "grand-archive-tcg";
        // Por defecto, el juego más común (mejora la probabilidad de match en el demo).
        return "pokemon";
    }

    private static String buildTitle(String setName, String cond, String printing) {
        StringBuilder sb = new StringBuilder();
        if (setName != null && !setName.isBlank()) sb.append(setName);
        if (cond != null && !cond.isBlank()) sb.append(sb.length() > 0 ? " · " : "").append(cond);
        if (printing != null && !printing.isBlank()) sb.append(sb.length() > 0 ? " · " : "").append(printing);
        return sb.length() > 0 ? sb.toString() : "Carta";
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
