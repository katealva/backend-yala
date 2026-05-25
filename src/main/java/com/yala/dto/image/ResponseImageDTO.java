package com.yala.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Imagen subida a S3 asociada a un listing")
public record ResponseImageDTO(
        @Schema(description = "ID de la imagen", example = "55")
        Long id,

        @Schema(description = "URL pública del objeto en S3",
                example = "https://yala-collectibles.s3.amazonaws.com/listings/101/0.jpg")
        String url,

        @Schema(description = "Orden de aparición en la galería del listing (asc)", example = "0")
        Integer sortOrder,

        @Schema(description = "ID del listing al que pertenece la imagen", example = "101")
        Long listingId) {
}
