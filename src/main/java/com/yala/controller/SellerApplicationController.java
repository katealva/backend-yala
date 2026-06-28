package com.yala.controller;
import com.yala.service.*;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.dto.seller.RequestSellerApplicationDTO;
import com.yala.dto.seller.ResponseSellerApplicationDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/application")
@RequiredArgsConstructor
@Tag(name = "Seller application", description = "Aplicación a vendedor (tienda + KYC Didit)")
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    @PostMapping
    @Operation(summary = "Aplica a vendedor (usuario autenticado). Devuelve la URL de Didit para el KYC.")
    public ResponseEntity<ResponseSellerApplicationDTO> apply(
            @Valid @RequestBody RequestSellerApplicationDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerApplicationService.apply(auth.getName(), request));
    }

    @GetMapping("/me")
    @Operation(summary = "Devuelve la aplicación a vendedor del usuario autenticado (o vacío)")
    public ResponseEntity<ResponseSellerApplicationDTO> getMine(Authentication auth) {
        return ResponseEntity.ok(sellerApplicationService.getMine(auth.getName()));
    }
}
