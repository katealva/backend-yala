package com.yala.payment;

import com.yala.payment.dto.CreatePaymentIntentRequest;
import com.yala.payment.dto.PaymentIntentResponse;

public interface PaymentService {

    PaymentIntentResponse createIntent(CreatePaymentIntentRequest request, String buyerEmail);

    /**
     * Processes a Stripe-shaped webhook payload. In this iteration the payload
     * is interpreted directly (stub mode) — signature verification will be
     * added when the real Stripe SDK is wired in.
     */
    void handleWebhook(String payload, String stripeSignature);
}
