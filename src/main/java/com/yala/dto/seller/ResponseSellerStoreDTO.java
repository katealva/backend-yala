package com.yala.dto.seller;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos PÚBLICOS de la tienda de un vendedor (de su SellerApplication aprobada).
 * Excluye a propósito datos privados: celular y CCI nunca se exponen aquí.
 */
@Schema(description = "Datos públicos de la tienda de un vendedor")
public record ResponseSellerStoreDTO(
        @Schema(description = "Nombre de la tienda", example = "CardVault PE") String storeName,
        @Schema(description = "Dirección del local presencial (opcional, puede ser null)",
                example = "Av. Ejemplo 123, Lima") String address) {
}
