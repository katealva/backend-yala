package com.yala.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.exception.OrderNotConfirmableException;
import com.yala.exception.PaymentException;
import com.yala.exception.ResourceNotFoundException;
import com.yala.exception.UnauthorizedException;
import com.yala.order.Order;
import com.yala.order.OrderRepository;
import com.yala.order.OrderStatus;
import com.yala.payment.dto.CreatePaymentIntentRequest;
import com.yala.payment.dto.PaymentIntentResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stub implementation of the Stripe integration. Generates a fake PaymentIntent
 * (no actual call to Stripe), persists the {@link Payment} as {@code PENDING}
 * and reconciles state through the webhook handler when Stripe-shaped JSON is
 * received. Replacing this with the real Stripe SDK only requires swapping the
 * intent generation and signature verification while keeping the contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String GATEWAY = "stripe";
    private static final String EVENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String EVENT_FAILED = "payment_intent.payment_failed";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public PaymentIntentResponse createIntent(
            CreatePaymentIntentRequest request, String buyerEmail) {
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

        String paymentIntentId = "pi_" + UUID.randomUUID();
        String clientSecret = paymentIntentId + "_secret_" + UUID.randomUUID();

        paymentRepository.save(Payment.builder()
                .gateway(GATEWAY)
                .externalReference(paymentIntentId)
                .amount(order.getAmount())
                .status(PaymentStatus.PENDING)
                .order(order)
                .build());

        log.info("Stripe stub PaymentIntent {} created for order {} amount {}",
                paymentIntentId, order.getId(), order.getAmount());
        return new PaymentIntentResponse(clientSecret, paymentIntentId);
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String stripeSignature) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new PaymentException("Malformed webhook payload");
        }

        String type = root.path("type").asText(null);
        String paymentIntentId = root.path("data").path("object").path("id").asText(null);

        if (type == null || paymentIntentId == null) {
            throw new PaymentException("Webhook payload missing required fields");
        }

        Payment payment = paymentRepository.findByExternalReference(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for paymentIntent: " + paymentIntentId));

        switch (type) {
            case EVENT_SUCCEEDED -> {
                payment.setStatus(PaymentStatus.SUCCESS);
                paymentRepository.save(payment);
                Order order = payment.getOrder();
                if (order.getStatus() == OrderStatus.PENDING) {
                    order.setStatus(OrderStatus.CONFIRMED);
                    orderRepository.save(order);
                    // TODO publish OrderConfirmedEvent once event system is merged (PR #5)
                }
                log.info("Stripe webhook reconciled payment {} as SUCCESS", payment.getId());
            }
            case EVENT_FAILED -> {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.info("Stripe webhook reconciled payment {} as FAILED", payment.getId());
            }
            default -> log.info("Stripe webhook event type ignored: {}", type);
        }
    }
}
