package com.yala.dto.live;

/**
 * The collectible attributes the AI extracted from the seller's spoken description, used by
 * the client to pre-fill or auto-create a flash auction (ADR-002). {@code title} and
 * {@code suggestedBasePrice} feed the flash auction; {@code category}/{@code condition} are
 * context shown to the seller. {@code confidence} (0-1) gates the automatic mode.
 */
public record ResponseDetectedProductDTO(
        String title,
        String category,
        String condition,
        Float suggestedBasePrice,
        Double confidence) {
}
