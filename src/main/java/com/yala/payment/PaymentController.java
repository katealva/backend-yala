package com.yala.payment;
import com.yala.model.*;

import com.yala.payment.dto.CreatePaymentPreferenceRequest;
import com.yala.payment.dto.PaymentPreferenceResponse;
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
@Tag(name = "Payments", description = "MercadoPago Preference (stub) y webhook de reconciliación")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/preference")
    @Operation(summary = "Crea una Preference MercadoPago (stub) para una orden PENDING del buyer autenticado")
    public ResponseEntity<PaymentPreferenceResponse> createPreference(
            @Valid @RequestBody CreatePaymentPreferenceRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPreference(request, auth.getName()));
    }

    @PostMapping(value = "/webhook", consumes = "application/json")
    @Operation(summary = "Webhook público de MercadoPago (reconcilia el estado del Payment)")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "x-signature", required = false) String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
