package com.yala.service;

import com.yala.dto.identity.RequestIdentityVerifyDTO;
import com.yala.dto.identity.ResponseIdentityDTO;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestClient restClient;

    @Value("${didit.api-key:}")
    private String diditApiKey;

    public IdentityService(UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate,
                           RestClient.Builder restClientBuilder) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public ResponseIdentityDTO verifyIdentity(String email, RequestIdentityVerifyDTO request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (Boolean.TRUE.equals(user.getIsIdentityVerified())) {
            return new ResponseIdentityDTO(true, "already-verified", user.getId());
        }

        boolean verified;
        String source;

        if (diditApiKey == null || diditApiKey.isBlank()) {
            // No API key configured: demo mode (8-digit DNI passes)
            verified = request.personalNumber() != null && request.personalNumber().matches("\\d{8}");
            source = "demo";
            log.info("Didit demo mode for user {}: verified={}", email, verified);
        } else {
            verified = callDidit(request);
            source = "didit";
        }

        if (verified) {
            user.setIsIdentityVerified(true);
            userRepository.save(user);
            messagingTemplate.convertAndSend(
                    "/topic/identity/" + user.getId(),
                    Map.of("verified", true, "userId", user.getId()));
            log.info("Identity verified for user {} via {}", email, source);
        }

        return new ResponseIdentityDTO(verified, source, user.getId());
    }

    /**
     * Handles the Didit status.updated webhook.
     * Didit fires this when save_api_request=true (default). vendor_data contains the user ID.
     */
    @Transactional
    public void handleWebhook(Map<String, Object> payload) {
        try {
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
        } catch (Exception e) {
            log.warn("Error processing Didit webhook: {}", e.getMessage());
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
            // save_api_request=false: stateless call, no Didit webhook fired
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
