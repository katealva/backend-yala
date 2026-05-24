package com.yala.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
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
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mercado Pago integration. {@code createPreference} calls the Mercado Pago
 * Preference API (sandbox or production depending on the access token) to
 * obtain a real checkout {@code init_point} for the buyer. {@code handleWebhook}
 * still parses the JSON payload directly to reconcile payment status — a future
 * iteration will verify the {@code x-signature} header and fetch payment data
 * via {@code GET /v1/payments/{id}}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String GATEWAY = "mercadopago";
    private static final String CURRENCY_PEN = "PEN";
    private static final String NOTIFICATION_TYPE_PAYMENT = "payment";
    private static final String PAYMENT_STATUS_APPROVED = "approved";
    private static final String PAYMENT_STATUS_REJECTED = "rejected";
    private static final String PAYMENT_STATUS_CANCELLED = "cancelled";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PreferenceClient preferenceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mercadopago.access-token:}")
    private String accessToken;

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
        if (accessToken == null || accessToken.isBlank()) {
            throw new PaymentException(
                    "Mercado Pago not configured (missing mercadopago.access-token)");
        }

        String itemTitle = order.getListing() != null && order.getListing().getTitle() != null
                ? order.getListing().getTitle()
                : "Order " + order.getId();

        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(itemTitle)
                .quantity(1)
                .currencyId(CURRENCY_PEN)
                .unitPrice(BigDecimal.valueOf(order.getAmount()))
                .build();

        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(List.of(item))
                .externalReference(order.getId().toString())
                .build();

        Preference preference;
        try {
            preference = preferenceClient.create(preferenceRequest);
        } catch (MPApiException ex) {
            log.error("Mercado Pago API error creating preference for order {}: {}",
                    order.getId(), ex.getApiResponse() != null
                            ? ex.getApiResponse().getContent() : ex.getMessage());
            throw new PaymentException(
                    "Mercado Pago rejected the preference request: " + ex.getMessage());
        } catch (MPException ex) {
            log.error("Mercado Pago SDK error creating preference for order {}: {}",
                    order.getId(), ex.getMessage());
            throw new PaymentException(
                    "Mercado Pago could not be reached: " + ex.getMessage());
        }

        paymentRepository.save(Payment.builder()
                .gateway(GATEWAY)
                .externalReference(preference.getId())
                .amount(order.getAmount())
                .status(PaymentStatus.PENDING)
                .order(order)
                .build());

        log.info("Mercado Pago Preference {} created for order {} amount {} PEN",
                preference.getId(), order.getId(), order.getAmount());
        return new PaymentPreferenceResponse(preference.getInitPoint(), preference.getId());
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
