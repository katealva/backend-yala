package com.yala.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yala.dto.identity.RequestIdentityVerifyDTO;
import com.yala.dto.identity.ResponseIdentityDTO;
import com.yala.dto.identity.ResponseSessionDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${didit.api-key:}")
    private String diditApiKey;

    @Value("${didit.webhook-secret:}")
    private String diditWebhookSecret;

    @Value("${didit.workflow-id:}")
    private String diditWorkflowId;

    @Value("${didit.callback-url:}")
    private String diditCallbackUrl;

    public IdentityService(UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate,
                           RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Session flow (KYB / full KYC)
    // -------------------------------------------------------------------------

    /**
     * Creates a Didit verification session and returns { url, sessionId }.
     * The frontend opens the url using the Didit SDK modal, iframe, or redirect.
     * The decision arrives later via webhook (POST /api/v1/identity/webhook).
     */
    public ResponseSessionDTO createSession(String email) {
        requireApiKey();
        if (diditWorkflowId == null || diditWorkflowId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DIDIT_WORKFLOW_ID not configured");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Map<String, Object> body = new HashMap<>();
        body.put("workflow_id", diditWorkflowId);
        body.put("vendor_data", String.valueOf(user.getId()));
        if (diditCallbackUrl != null && !diditCallbackUrl.isBlank()) {
            body.put("callback", diditCallbackUrl);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("https://verification.didit.me/v3/session/")
                    .header("x-api-key", diditApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Empty response from Didit session API");
            }
            log.info("Didit session created for user {}: sessionId={}", email,
                    response.get("session_id"));
            return new ResponseSessionDTO(
                    (String) response.get("url"),
                    (String) response.get("session_id"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Didit session creation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Didit session creation failed");
        }
    }

    // -------------------------------------------------------------------------
    // Standalone Database Validation (DNI vs RENIEC)
    // -------------------------------------------------------------------------

    @Transactional
    public ResponseIdentityDTO verifyIdentity(String email, RequestIdentityVerifyDTO request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        if (Boolean.TRUE.equals(user.getIsIdentityVerified())) {
            return new ResponseIdentityDTO(true, "already-verified", user.getId());
        }

        if (diditApiKey == null || diditApiKey.isBlank()) {
            // Demo mode: not persisted to DB; configure DIDIT_API_KEY for real verification.
            boolean demoVerified = request.personalNumber() != null
                    && request.personalNumber().matches("\\d{8}");
            log.info("Didit demo mode for user {} (not persisted): verified={}", email, demoVerified);
            return new ResponseIdentityDTO(demoVerified, "demo", user.getId());
        }

        boolean verified = callDatabaseValidation(request);

        if (verified) {
            user.setIsIdentityVerified(true);
            userRepository.save(user);
            messagingTemplate.convertAndSend(
                    "/topic/identity/" + user.getId(),
                    (Object) Map.of("verified", true, "userId", user.getId()));
            log.info("Identity verified for user {} via Didit Database Validation", email);
        }

        return new ResponseIdentityDTO(verified, "didit", user.getId());
    }

    // -------------------------------------------------------------------------
    // Webhook handler
    // -------------------------------------------------------------------------

    /**
     * Handles Didit webhooks per the official V3 spec:
     *  1. X-Timestamp freshness ≤ 300 s (anti-replay).
     *  2. X-Signature-V2: HMAC-SHA256 over shortenFloats → sortKeys → JSON.stringify
     *     (or X-Signature fallback: HMAC over raw bytes).
     *  3. Dispatch on webhook_type / status.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signatureV2, String signatureRaw,
                              String timestamp) {
        if (diditWebhookSecret == null || diditWebhookSecret.isBlank()) {
            log.error("Didit webhook rejected: DIDIT_WEBHOOK_SECRET not configured");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Webhook secret not configured");
        }

        validateTimestamp(timestamp);

        if (signatureV2 != null && !signatureV2.isBlank()) {
            if (!verifySignatureV2(rawBody, signatureV2)) {
                log.warn("Didit webhook rejected: invalid X-Signature-V2");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Signature-V2");
            }
        } else if (signatureRaw != null && !signatureRaw.isBlank()) {
            if (!verifySignatureRaw(rawBody, signatureRaw)) {
                log.warn("Didit webhook rejected: invalid X-Signature");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Signature");
            }
        } else {
            log.warn("Didit webhook rejected: no signature header");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing webhook signature");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            String webhookType = (String) payload.get("webhook_type");
            log.debug("Didit webhook received: type={} status={}", webhookType,
                    payload.get("status"));

            if ("status.updated".equals(webhookType)) {
                processStatusUpdated(payload);
            }
            // Future event types: user.status.updated, business.status.updated,
            // transaction.created, transaction.status.updated, activity.created, etc.
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error parsing Didit webhook body: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook payload");
        }
    }

    // -------------------------------------------------------------------------
    // Event processors
    // -------------------------------------------------------------------------

    private void processStatusUpdated(Map<String, Object> payload) {
        String status = (String) payload.get("status");
        Object vendorData = payload.get("vendor_data");
        if (vendorData == null) {
            log.warn("status.updated webhook missing vendor_data — skipping");
            return;
        }

        Long userId;
        try {
            userId = Long.parseLong(vendorData.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid vendor_data in Didit webhook: {}", vendorData);
            return;
        }

        userRepository.findById(userId).ifPresent(user -> {
            // Exact case-sensitive status literals per Didit V3 spec.
            switch (status) {
                case "Approved" -> {
                    if (!Boolean.TRUE.equals(user.getIsIdentityVerified())) {
                        user.setIsIdentityVerified(true);
                        userRepository.save(user);
                        messagingTemplate.convertAndSend(
                                "/topic/identity/" + userId,
                                (Object) Map.of("verified", true, "userId", userId));
                        log.info("Identity approved for user {} via Didit webhook", userId);
                    }
                }
                case "Declined" -> {
                    messagingTemplate.convertAndSend(
                            "/topic/identity/" + userId,
                            (Object) Map.of("verified", false, "status", "declined", "userId", userId));
                    log.info("Identity declined for user {} via Didit webhook", userId);
                }
                case "In Review"      -> log.info("Identity in review for user {}", userId);
                case "In Progress"    -> log.info("Identity in progress for user {}", userId);
                case "Awaiting User"  -> log.info("Identity awaiting user for user {}", userId);
                case "Resubmitted"    -> log.info("Identity resubmitted for user {}", userId);
                case "Abandoned"      -> log.info("Identity session abandoned for user {}", userId);
                case "Expired"        -> log.info("Identity session expired for user {}", userId);
                case "Kyc Expired"    -> {
                    // Verified user's KYC has aged out — revoke and optionally create a new session.
                    user.setIsIdentityVerified(false);
                    userRepository.save(user);
                    messagingTemplate.convertAndSend(
                            "/topic/identity/" + userId,
                            (Object) Map.of("verified", false, "status", "kyc-expired", "userId", userId));
                    log.info("KYC expired for user {} — isIdentityVerified reset to false", userId);
                }
                case "Not Started"    -> log.debug("Identity not started for user {}", userId);
                default -> log.warn("Unknown Didit status '{}' for user {}", status, userId);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    /**
     * X-Signature-V2: HMAC-SHA256 over the canonical JSON.
     * Canonical = shortenFloats (1.0 → 1) → sort keys alphabetically → JSON.stringify,
     * Unicode characters NOT escaped. Matches Didit's Python server-side algorithm.
     */
    private boolean verifySignatureV2(String rawBody, String signature) {
        try {
            Object parsed = objectMapper.readValue(rawBody, Object.class);
            Object shortened = shortenFloats(parsed);

            ObjectMapper canonical = objectMapper.copy()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String canonicalJson = canonical.writeValueAsString(shortened);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    diditWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonicalJson.getBytes(StandardCharsets.UTF_8));
            byte[] actual   = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            log.warn("X-Signature-V2 verification error: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("X-Signature-V2 canonical JSON error: {}", e.getMessage());
            return false;
        }
    }

    /** X-Signature: HMAC-SHA256 over the exact raw request bytes (fallback). */
    private boolean verifySignatureRaw(String rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    diditWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual   = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            log.warn("X-Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Normalises whole-number floats to integers recursively.
     * Mirrors the JS shortenFloats() helper in Didit's official Node/Next.js example.
     * Required before computing X-Signature-V2: Didit's server uses the same transform,
     * so without it any payload containing e.g. "score": 1.0 would fail verification.
     */
    @SuppressWarnings("unchecked")
    private Object shortenFloats(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), shortenFloats(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::shortenFloats).toList();
        }
        if (value instanceof Double d && !d.isInfinite() && !d.isNaN() && d % 1 == 0) {
            return d.longValue();
        }
        return value;
    }

    private void validateTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Timestamp");
        }
        try {
            long ts = Long.parseLong(timestamp.trim());
            if (Math.abs(Instant.now().getEpochSecond() - ts) > 300) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Stale webhook timestamp");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Timestamp format");
        }
    }

    // -------------------------------------------------------------------------
    // Didit Database Validation API (standalone DNI vs RENIEC)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private boolean callDatabaseValidation(RequestIdentityVerifyDTO request) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("issuing_state", "PER");
            body.put("services", List.of("per_dni"));
            body.put("personal_number", request.personalNumber());
            if (request.firstName() != null && !request.firstName().isBlank()) {
                body.put("first_name", request.firstName());
            }
            if (request.lastName() != null && !request.lastName().isBlank()) {
                body.put("last_name", request.lastName());
            }
            body.put("save_api_request", Boolean.FALSE);

            Map<?, ?> response = restClient.post()
                    .uri("https://verification.didit.me/v3/database-validation/")
                    .header("x-api-key", diditApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return false;
            Object dvObj = response.get("database_validation");
            if (!(dvObj instanceof Map)) return false;
            Map<?, ?> dv = (Map<?, ?>) dvObj;
            return "Approved".equals(dv.get("status"));
        } catch (Exception e) {
            log.warn("Didit Database Validation call failed: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void requireApiKey() {
        if (diditApiKey == null || diditApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DIDIT_API_KEY not configured");
        }
    }
}
