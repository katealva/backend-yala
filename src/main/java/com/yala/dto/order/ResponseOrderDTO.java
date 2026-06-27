package com.yala.dto.order;

import com.yala.dto.listing.ResponseListingDTO;
import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Detalle de una orden de compra (proviene de compra directa o de subasta ganada)")
public record ResponseOrderDTO(
        @Schema(description = "ID de la orden", example = "7001") Long id,

        @Schema(description = "Monto total de la orden en soles", example = "499.90") Float amount,

        @Schema(description = "Estado de la orden", example = "PENDING",
                allowableValues = {"PENDING", "CONFIRMED", "CANCELLED"})
        String status,

        @Schema(description = "Fecha en que se creó la orden", example = "2026-05-20T15:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Fecha límite de pago (para subastas de live; null si no aplica)",
                example = "2026-05-22T15:00:00")
        LocalDateTime paymentDeadline,

        @Schema(description = "Título del item comprado (listing o subasta flash)",
                example = "Charizard PSA 9")
        String itemTitle,

        @Schema(description = "Listing al que corresponde la orden (null si proviene de un live)")
        ResponseListingDTO listing,

        @Schema(description = "Usuario que compra")
        ResponseUserDTO buyer,

        @Schema(description = "Usuario que vende")
        ResponseUserDTO seller) {
}
