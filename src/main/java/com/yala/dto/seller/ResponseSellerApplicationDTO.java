package com.yala.dto.seller;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Estado de la aplicación a vendedor")
public record ResponseSellerApplicationDTO(
        @Schema(description = "ID de la aplicación", example = "10") Long id,
        @Schema(description = "Estado", example = "PENDING",
                allowableValues = {"PENDING", "APPROVED", "REJECTED"}) String status,
        @Schema(description = "Nombre de la tienda") String storeName,
        @Schema(description = "Dirección (opcional)") String address,
        @Schema(description = "Celular / WhatsApp") String phone,
        @Schema(description = "CCI") String cci,
        @Schema(description = "Fecha de creación") LocalDateTime createdAt,
        @Schema(description = "URL de Didit para completar el KYC (null si no aplica)") String diditUrl,
        @Schema(description = "ID de sesión Didit") String diditSessionId) {
}
