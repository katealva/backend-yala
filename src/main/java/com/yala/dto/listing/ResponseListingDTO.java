package com.yala.dto.listing;

import com.yala.dto.auction.ResponseAuctionSummaryDTO;
import com.yala.dto.category.ResponseCategoryDTO;
import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Detalle completo de un listing publicado en Yala, incluyendo seller, categoría, imágenes y resumen de subasta si aplica")
public record ResponseListingDTO(
        @Schema(description = "ID del listing", example = "101")
        Long id,

        @Schema(description = "Título del listing", example = "Charizard PSA 9 — 1st Edition")
        String title,

        @Schema(description = "Descripción larga", example = "Carta en excelente estado…")
        String description,

        @Schema(description = "Modo de venta", example = "AUCTION", allowableValues = {"FIXED", "AUCTION"})
        String mode,

        @Schema(description = "Precio fijo en soles (sólo presente si mode = FIXED)", example = "499.90")
        Float fixedPrice,

        @Schema(description = "Estado físico del coleccionable", example = "PSA 9 — Mint")
        String condition,

        @Schema(description = "Estado del listing", example = "ACTIVE",
                allowableValues = {"ACTIVE", "SOLD", "CANCELLED"})
        String status,

        @Schema(description = "Fecha de creación", example = "2026-05-19T14:32:00")
        LocalDateTime createdAt,

        @Schema(description = "Vendedor que publicó el listing")
        ResponseUserDTO seller,

        @Schema(description = "Categoría a la que pertenece el listing")
        ResponseCategoryDTO category,

        @Schema(description = "URLs públicas de las imágenes asociadas al listing (orden ascendente)",
                example = "[\"https://yala.s3.amazonaws.com/listings/101/0.jpg\"]")
        List<String> imageUrls,

        @Schema(description = "Resumen de la subasta asociada (presente sólo si mode = AUCTION)")
        ResponseAuctionSummaryDTO auction) {
}
