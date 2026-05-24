package com.yala.controller;
import com.yala.service.*;

import com.yala.dto.auth.ResponseAuthDTO;
import com.yala.dto.auth.RequestLoginDTO;
import com.yala.dto.auth.RequestRefreshTokenDTO;
import com.yala.dto.auth.RequestRegisterDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registro, login y renovación de tokens JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Registra un nuevo usuario y devuelve los tokens JWT")
    public ResponseEntity<ResponseAuthDTO> register(@Valid @RequestBody RequestRegisterDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica un usuario y devuelve los tokens JWT")
    public ResponseEntity<ResponseAuthDTO> login(@Valid @RequestBody RequestLoginDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Renueva el access token a partir de un refresh token válido")
    public ResponseEntity<ResponseAuthDTO> refreshToken(
            @Valid @RequestBody RequestRefreshTokenDTO request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }
}
