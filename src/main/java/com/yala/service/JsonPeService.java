package com.yala.service;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * JSON.pe DNI lookup (RENIEC) used to validate a buyer's identity at registration.
 * The call is server-side so the API key is never exposed to the frontend.
 * POST {base-url}/api/dni  with  Authorization: Bearer {api-key}  and body {"dni": "########"}.
 */
@Slf4j
@Service
public class JsonPeService {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public JsonPeService(
            RestClient.Builder restClientBuilder,
            @Value("${jsonpe.base-url:https://api.json.pe}") String baseUrl,
            @Value("${jsonpe.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /** When false, the backend runs in demo mode and skips the external RENIEC check (local/dev). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Looks up a DNI in RENIEC via JSON.pe. Returns the record, or empty when the DNI does
     * not exist, the response is not successful, or the provider errors out.
     */
    public Optional<DniRecord> lookup(String dni) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            Map<?, ?> response = restClient.post()
                    .uri(baseUrl + "/api/dni")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("dni", dni))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                return Optional.empty();
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                return Optional.empty();
            }
            return Optional.of(new DniRecord(
                    str(data.get("nombres")),
                    str(data.get("apellido_paterno")),
                    str(data.get("apellido_materno")),
                    str(data.get("nombre_completo"))));
        } catch (Exception e) {
            log.warn("JSON.pe DNI lookup failed for {}: {}", dni, e.getMessage());
            return Optional.empty();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** Minimal projection of the JSON.pe DNI response. */
    public record DniRecord(String nombres, String apellidoPaterno, String apellidoMaterno,
            String nombreCompleto) {
    }
}
