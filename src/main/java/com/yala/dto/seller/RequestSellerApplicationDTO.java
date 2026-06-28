package com.yala.dto.seller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para aplicar a vendedor. Requiere estar registrado como usuario.")
public record RequestSellerApplicationDTO(
        @Schema(description = "Nombre de la tienda", example = "CardVault PE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 120) String storeName,

        @Schema(description = "Dirección del local presencial (opcional)", example = "Av. Ejemplo 123, Lima")
        @Size(max = 200) String address,

        @Schema(description = "Número de celular / WhatsApp", example = "+51 999 999 999",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 30) String phone,

        @Schema(description = "Número de CCI (cuenta interbancaria)", example = "00219300012345678901",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 30) String cci) {
}
