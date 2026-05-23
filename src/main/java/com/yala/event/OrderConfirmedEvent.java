package com.yala.event;

/**
 * Published when a seller confirms an order.
 * Consumed by listeners that notify the buyer and recalculate the seller's
 * reputation from received reviews.
 */
public record OrderConfirmedEvent(
        Long orderId,
        Long buyerId,
        Long sellerId) {
}
