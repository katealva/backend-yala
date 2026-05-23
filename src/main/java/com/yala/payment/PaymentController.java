package com.yala.payment;

import com.yala.payment.dto.CreatePaymentIntentRequest;
import com.yala.payment.dto.PaymentIntentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Stripe PaymentIntent (stub) y webhook de reconciliación")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/intent")
    @Operation(summary = "Crea un PaymentIntent (stub) para una orden PENDING del buyer autenticado")
    public ResponseEntity<PaymentIntentResponse> createIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createIntent(request, auth.getName()));
    }

    @PostMapping(value = "/webhook", consumes = "application/json")
    @Operation(summary = "Webhook público de Stripe (reconcilia el estado del Payment)")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
