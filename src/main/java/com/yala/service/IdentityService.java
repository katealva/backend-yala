package com.yala.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yala.dto.identity.RequestIdentityVerifyDTO;
import com.yala.dto.identity.ResponseIdentityDTO;
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

    public IdentityService(UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate,
                           RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResponseIdentityDTO verifyIdentity(String email, RequestIdentityVerifyDTO request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

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

        boolean verified = callDidit(request);

        if (verified) {
            user.setIsIdentityVerified(true);
            userRepository.save(user);
            messagingTemplate.convertAndSend(
                    "/topic/identity/" + user.getId(),
                    Map.of("verified", true, "userId", user.getId()));
            log.info("Identity verified for user {} via Didit", email);
        }

        return new ResponseIdentityDTO(verified, "didit", user.getId());
    }

    /**
     * Handles Didit webhooks following the official spec:
     *  1. Validates X-Timestamp freshness (≤ 5 minutes, anti-replay).
     *  2. Verifies X-Signature-V2 (HMAC-SHA256 over canonical sorted-key JSON) preferred;
     *     falls back to X-Signature (HMAC-SHA256 over raw bytes).
     *  3. Processes the event by webhook_type.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signatureV2, String signatureRaw, String timestamp) {
        if (diditWebhookSecret == null || diditWebhookSecret.isBlank()) {
            log.error("Didit webhook rejected: DIDIT_WEBHOOK_SECRET not configured");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret not configured");
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
            log.warn("Didit webhook rejected: no signature header present");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing webhook signature");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            String webhookType = (String) payload.get("webhook_type");
            log.debug("Didit webhook received: type={}", webhookType);

            if ("status.updated".equals(webhookType)) {
                processStatusUpdated(payload);
            }
            // Other types (user.status.updated, transaction.created, etc.) can be added here.
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
            switch (status) {
                case "Approved" -> {
                    if (!Boolean.TRUE.equals(user.getIsIdentityVerified())) {
                        user.setIsIdentityVerified(true);
                        userRepository.save(user);
                        messagingTemplate.convertAndSend(
                                "/topic/identity/" + userId,
                                Map.of("verified", true, "userId", userId));
                        log.info("Identity approved for user {} via Didit webhook", userId);
                    }
                }
                case "Declined" -> {
                    messagingTemplate.convertAndSend(
                            "/topic/identity/" + userId,
                            Map.of("verified", false, "status", "declined", "userId", userId));
                    log.info("Identity declined for user {} via Didit webhook", userId);
                }
                case "In Review"    -> log.info("Identity in review for user {}", userId);
                case "In Progress"  -> log.info("Identity in progress for user {}", userId);
                case "Resubmitted"  -> log.info("Identity resubmitted for user {}", userId);
                case "Abandoned"    -> log.info("Identity session abandoned for user {}", userId);
                case "Expired", "KYC Expired" -> log.info("Identity session expired for user {}", userId);
                default -> log.warn("Unknown Didit status '{}' for user {}", status, userId);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    /**
     * X-Signature-V2: HMAC-SHA256 over the canonical JSON.
     * Canonical = all keys sorted alphabetically (recursively), Unicode characters not escaped.
     * Matches Didit's "sorted, Unicode-preserved canonical JSON" spec.
     */
    private boolean verifySignatureV2(String rawBody, String signature) {
        try {
            Object parsed = objectMapper.readValue(rawBody, Object.class);
            ObjectMapper canonical = objectMapper.copy()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String canonicalJson = canonical.writeValueAsString(parsed);

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

    /**
     * X-Signature: HMAC-SHA256 over the exact raw bytes of the request body.
     * Fallback for stacks that guarantee byte-perfect body delivery.
     */
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
    // Didit Database Validation API call
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private boolean callDidit(RequestIdentityVerifyDTO request) {
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
            log.warn("Didit API call failed: {}", e.getMessage());
            return false;
        }
    }
}
