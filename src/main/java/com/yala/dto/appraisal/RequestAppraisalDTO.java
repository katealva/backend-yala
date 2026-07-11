package com.yala.dto.appraisal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Foto de un coleccionable para tasación por IA")
public record RequestAppraisalDTO(
        @Schema(description = "Imagen en base64 (data URL o base64 puro). Comprimida en el cliente.")
        @NotBlank(message = "La imagen es obligatoria")
        String imageBase64) {
}
