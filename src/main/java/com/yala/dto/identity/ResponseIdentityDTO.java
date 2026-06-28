package com.yala.dto.identity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de la verificación de identidad (DNI vía Didit)")
public record ResponseIdentityDTO(

        @Schema(description = "true si la identidad fue verificada exitosamente", example = "true")
        boolean verified,

        @Schema(description = "'didit' si se consultó RENIEC, 'demo' si no hay API key configurada",
                example = "didit")
        String source,

        @Schema(description = "ID del usuario cuya identidad se verificó", example = "42")
        Long userId) {
}
