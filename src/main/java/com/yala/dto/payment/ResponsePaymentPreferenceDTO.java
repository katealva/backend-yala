package com.yala.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de Mercado Pago al crear una preference para procesar el pago")
public record ResponsePaymentPreferenceDTO(
        @Schema(description = "URL al que redirigir al usuario para completar el pago en Mercado Pago",
                example = "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=…")
        String initPoint,

        @Schema(description = "ID de la preference creada en Mercado Pago (sirve para reconciliar webhooks)",
                example = "1234567890-abc-def")
        String preferenceId) {
}
