package com.yala.controller;

import com.yala.dto.identity.RequestIdentityVerifyDTO;
import com.yala.dto.identity.ResponseIdentityDTO;
import com.yala.dto.identity.ResponseSessionDTO;
import com.yala.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
@Tag(name = "Identity", description = "Verificación de identidad (KYC/KYB vía Didit)")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/session")
    @Operation(summary = "Crea una sesión Didit KYB/KYC — devuelve { url, sessionId } para abrir el flujo")
    public ResponseEntity<ResponseSessionDTO> createSession(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(identityService.createSession(userDetails.getUsername()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verifica DNI del usuario autenticado contra RENIEC vía Didit Database Validation")
    public ResponseEntity<ResponseIdentityDTO> verify(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RequestIdentityVerifyDTO request) {
        return ResponseEntity.ok(identityService.verifyIdentity(userDetails.getUsername(), request));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook de Didit — X-Signature-V2 (canonical JSON + shortenFloats) y X-Timestamp")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature-V2", required = false) String signatureV2,
            @RequestHeader(value = "X-Signature",    required = false) String signatureRaw,
            @RequestHeader(value = "X-Timestamp",    required = false) String timestamp) {
        identityService.handleWebhook(rawBody, signatureV2, signatureRaw, timestamp);
        return ResponseEntity.ok().build();
    }
}

// Separate controller for the /api/webhooks destination registered in Didit Business Console.
// Didit sends all webhooks to https://yala.dpdns.org/api/webhooks; this delegates to IdentityService.
@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/api/webhooks")
class DiditWebhookAlias {

    private final IdentityService identityService;

    DiditWebhookAlias(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping
    public ResponseEntity<Void> didit(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature-V2", required = false) String signatureV2,
            @RequestHeader(value = "X-Signature",    required = false) String signatureRaw,
            @RequestHeader(value = "X-Timestamp",    required = false) String timestamp) {
        identityService.handleWebhook(rawBody, signatureV2, signatureRaw, timestamp);
        return ResponseEntity.ok().build();
    }
}
