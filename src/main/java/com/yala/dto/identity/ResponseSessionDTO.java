package com.yala.dto.identity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sesión de verificación Didit — el frontend abre el url con el SDK o redirect")
public record ResponseSessionDTO(
        @Schema(description = "URL de Didit que el frontend debe abrir (SDK modal / iframe / redirect)",
                example = "https://verify.didit.me/session/...")
        String url,

        @Schema(description = "ID de la sesión Didit — para correlacionar con el webhook",
                example = "4c5c7f3a-0000-0000-0000-000000000000")
        String sessionId) {}
