package com.yala.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
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
            // Read raw bytes: JSON.pe sends `application/json` without charset and emits Ñ/accents as
            // single Latin-1 bytes (e.g. Ñ = 0xD1), which are invalid UTF-8 and would become "�".
            byte[] raw = restClient.post()
                    .uri(baseUrl + "/api/dni")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("dni", dni))
                    .retrieve()
                    .body(byte[].class);
            if (raw == null) {
                return Optional.empty();
            }
            Map<?, ?> response = objectMapper.readValue(decode(raw), Map.class);

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

    /**
     * Decodes the response body trying strict UTF-8 first; if the bytes aren't valid UTF-8
     * (JSON.pe sends Latin-1 for Ñ/accents), falls back to ISO-8859-1. Encoding-agnostic.
     */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
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
