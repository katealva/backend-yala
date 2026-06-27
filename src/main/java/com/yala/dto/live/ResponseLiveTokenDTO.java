package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acceso a la room LiveKit y datos de conexión")
public record ResponseLiveTokenDTO(
        @Schema(description = "ID de la transmisión", example = "12") Long streamId,
        @Schema(description = "Nombre de la room LiveKit") String roomName,
        @Schema(description = "URL wss del servidor LiveKit") String url,
        @Schema(description = "JWT de acceso a la room") String token) {
}
