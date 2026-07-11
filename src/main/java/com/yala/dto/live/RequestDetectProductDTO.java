package com.yala.dto.live;

/**
 * The seller's spoken description of the collectible being shown, captured by the client's
 * speech recognition after the trigger phrase ("Iniciemos esta nueva subasta"). The backend
 * extracts structured attributes from it to pre-fill a flash auction (ADR-002).
 */
public record RequestDetectProductDTO(String transcript) {
}
