package com.yala.payment;

import com.yala.payment.dto.CreatePaymentPreferenceRequest;
import com.yala.payment.dto.PaymentPreferenceResponse;

public interface PaymentService {

    PaymentPreferenceResponse createPreference(
            CreatePaymentPreferenceRequest request, String buyerEmail);

    /**
     * Processes a MercadoPago-shaped webhook payload. In stub mode the payload
     * is interpreted directly (status is read from {@code data.status}); when
     * the real MercadoPago SDK is wired in this method must verify the
     * {@code x-signature} header and fetch the payment via
     * {@code GET https://api.mercadopago.com/v1/payments/{id}} to read the
     * authoritative status.
     */
    void handleWebhook(String payload, String signature);
}
