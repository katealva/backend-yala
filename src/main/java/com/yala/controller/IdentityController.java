package com.yala.controller;

import com.yala.dto.identity.RequestIdentityVerifyDTO;
import com.yala.dto.identity.ResponseIdentityDTO;
import com.yala.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity")
@Tag(name = "Identity", description = "Verificación de identidad (DNI vía Didit/RENIEC)")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/verify")
    @Operation(summary = "Verificar DNI del usuario autenticado contra RENIEC vía Didit")
    public ResponseEntity<ResponseIdentityDTO> verify(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RequestIdentityVerifyDTO request) {
        return ResponseEntity.ok(identityService.verifyIdentity(userDetails.getUsername(), request));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook de Didit (status.updated) — endpoint público para callbacks de Didit")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload) {
        identityService.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
