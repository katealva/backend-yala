package com.yala.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.dto.identity.RequestIdentityVerifyDTO;
import com.yala.dto.identity.ResponseIdentityDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     * Handles the Didit status.updated webhook.
     * Requires HMAC-SHA256 of the raw body in x-signature (hex-encoded).
     * Rejects with 401 if didit.webhook-secret is not configured or the signature is invalid.
     * NOTE: if Didit prefixes the signature pre-image with a timestamp, adapt verifyWebhookSignature.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (diditWebhookSecret == null || diditWebhookSecret.isBlank()) {
            log.error("Didit webhook rejected: didit.webhook-secret is not configured");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret not configured");
        }
        if (!verifyWebhookSignature(rawBody, signature)) {
            log.warn("Didit webhook rejected: invalid or missing signature");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);

            Object dvObj = payload.get("database_validation");
            if (!(dvObj instanceof Map)) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> dv = (Map<String, Object>) dvObj;

            if (!"Approved".equals(dv.get("status"))) return;

            Object vendorData = payload.get("vendor_data");
            if (vendorData == null) return;
            Long userId = Long.parseLong(vendorData.toString());

            userRepository.findById(userId).ifPresent(user -> {
                if (!Boolean.TRUE.equals(user.getIsIdentityVerified())) {
                    user.setIsIdentityVerified(true);
                    userRepository.save(user);
                    messagingTemplate.convertAndSend(
                            "/topic/identity/" + userId,
                            Map.of("verified", true, "userId", userId));
                    log.info("Identity verified for user {} via Didit webhook", userId);
                }
            });
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error parsing Didit webhook payload: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook payload");
        }
    }

    private boolean verifyWebhookSignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    diditWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            log.warn("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

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
