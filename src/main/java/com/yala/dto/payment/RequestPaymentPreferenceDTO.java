package com.yala.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos para iniciar el flujo de pago de una orden via Mercado Pago")
public record RequestPaymentPreferenceDTO(
        @Schema(description = "ID de la orden a pagar (debe estar en estado PENDING)",
                example = "7001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long orderId) {
}
