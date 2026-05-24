package com.yala.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.event.OrderConfirmedEvent;
import com.yala.exception.OrderNotConfirmableException;
import com.yala.exception.PaymentException;
import com.yala.exception.ResourceNotFoundException;
import com.yala.exception.UnauthorizedException;
import com.yala.order.Order;
import com.yala.order.OrderRepository;
import com.yala.order.OrderStatus;
import com.yala.payment.dto.CreatePaymentPreferenceRequest;
import com.yala.payment.dto.PaymentPreferenceResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stub implementation of the MercadoPago integration. Generates a fake
 * Preference (no actual call to MercadoPago), persists the {@link Payment}
 * as {@code PENDING} and reconciles state through the webhook handler when
 * MercadoPago-shaped JSON is received. Replacing this with the real SDK
 * only requires swapping the preference generation, signature verification
 * and the {@code GET /v1/payments/{id}} reconciliation while keeping the
 * contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String GATEWAY = "mercadopago";
    private static final String CHECKOUT_BASE_URL =
            "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=";
    private static final String NOTIFICATION_TYPE_PAYMENT = "payment";
    private static final String PAYMENT_STATUS_APPROVED = "approved";
    private static final String PAYMENT_STATUS_REJECTED = "rejected";
    private static final String PAYMENT_STATUS_CANCELLED = "cancelled";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public PaymentPreferenceResponse createPreference(
            CreatePaymentPreferenceRequest request, String buyerEmail) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + request.orderId()));

        if (!order.getBuyer().getEmail().equals(buyerEmail)) {
            throw new UnauthorizedException("Only the buyer can pay this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderNotConfirmableException(
                    "Order " + order.getId() + " is not pending and cannot be paid");
        }

        String preferenceId = "pref_" + UUID.randomUUID();
        String initPoint = CHECKOUT_BASE_URL + preferenceId;

        paymentRepository.save(Payment.builder()
                .gateway(GATEWAY)
                .externalReference(preferenceId)
                .amount(order.getAmount())
                .status(PaymentStatus.PENDING)
                .order(order)
                .build());

        log.info("MercadoPago stub Preference {} created for order {} amount {}",
                preferenceId, order.getId(), order.getAmount());
        return new PaymentPreferenceResponse(initPoint, preferenceId);
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signature) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new PaymentException("Malformed webhook payload");
        }

        String type = root.path("type").asText(null);
        JsonNode data = root.path("data");
        String externalReference = data.path("id").asText(null);

        if (type == null || externalReference == null) {
            throw new PaymentException("Webhook payload missing required fields");
        }

        if (!NOTIFICATION_TYPE_PAYMENT.equals(type)) {
            log.info("MercadoPago webhook notification type ignored: {}", type);
            return;
        }

        Payment payment = paymentRepository.findByExternalReference(externalReference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for preference: " + externalReference));

        String status = data.path("status").asText(null);
        if (status == null) {
            throw new PaymentException("Webhook payload missing payment status");
        }

        switch (status) {
            case PAYMENT_STATUS_APPROVED -> {
                payment.setStatus(PaymentStatus.SUCCESS);
                paymentRepository.save(payment);
                Order order = payment.getOrder();
                if (order.getStatus() == OrderStatus.PENDING) {
                    order.setStatus(OrderStatus.CONFIRMED);
                    Order saved = orderRepository.save(order);
                    eventPublisher.publishEvent(new OrderConfirmedEvent(
                            saved.getId(),
                            saved.getBuyer().getId(),
                            saved.getSeller().getId()));
                }
                log.info("MercadoPago webhook reconciled payment {} as SUCCESS", payment.getId());
            }
            case PAYMENT_STATUS_REJECTED, PAYMENT_STATUS_CANCELLED -> {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.info("MercadoPago webhook reconciled payment {} as FAILED ({})",
                        payment.getId(), status);
            }
            default -> log.info("MercadoPago webhook payment status ignored: {}", status);
        }
    }
}
