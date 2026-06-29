package com.yala.dto.listing;

import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Lightweight listing projection embedded inside an auction so clients (especially mobile)
 * can render the image, title, condition, category and seller from the auction response.
 * Intentionally omits the auction back-reference to avoid recursion.
 */
@Schema(description = "Resumen del listing asociado a una subasta (sin la subasta, para evitar recursión)")
public record ResponseListingSummaryDTO(
        @Schema(description = "ID del listing", example = "101") Long id,
        @Schema(description = "Título", example = "Charizard PSA 9") String title,
        @Schema(description = "Descripción") String description,
        @Schema(description = "URLs de las imágenes") List<String> imageUrls,
        @Schema(description = "Condición", example = "Como nuevo") String condition,
        @Schema(description = "Nombre de la categoría", example = "Cartas TCG") String categoryName,
        @Schema(description = "Vendedor dueño del listing") ResponseUserDTO seller) {
}
