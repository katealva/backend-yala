package com.yala.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Notificación in-app dirigida a un usuario (puja superada, subasta ganada, etc.)")
public record ResponseNotificationDTO(
        @Schema(description = "ID de la notificación", example = "8001") Long id,

        @Schema(description = "Tipo de evento que originó la notificación", example = "BID_OUTBID",
                allowableValues = {"BID_OUTBID", "AUCTION_WON", "SALE_CONFIRMED", "NEW_BID"})
        String type,

        @Schema(description = "Mensaje listo para mostrar al usuario",
                example = "Tu puja fue superada en la subasta de Charizard PSA 9")
        String message,

        @Schema(description = "Indica si el usuario ya leyó la notificación", example = "false")
        Boolean isRead,

        @Schema(description = "Fecha en que se generó la notificación", example = "2026-05-19T14:32:00")
        LocalDateTime createdAt) {
}
