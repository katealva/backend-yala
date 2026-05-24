package com.yala.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.yala.user.Role;
import com.yala.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User buyer() {
        return User.builder().id(1L).email("ada@yala.pe").role(Role.USER).build();
    }

    private User seller() {
        return User.builder().id(2L).email("bob@yala.pe").role(Role.SELLER).build();
    }

    private Order pendingOrder() {
        return Order.builder()
                .id(50L).amount(250f).status(OrderStatus.PENDING)
                .buyer(buyer()).seller(seller()).build();
    }

    @Test
    void shouldCreatePreferenceWhenOrderIsPendingAndBuyerMatches() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(999L);
            return p;
        });

        PaymentPreferenceResponse response = paymentService.createPreference(
                new CreatePaymentPreferenceRequest(50L), "ada@yala.pe");

        assertThat(response.preferenceId()).startsWith("pref_");
        assertThat(response.initPoint())
                .startsWith("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref_");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenBuyerDoesNotMatch() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> paymentService.createPreference(
                new CreatePaymentPreferenceRequest(50L), "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenOrderIsNotPending() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createPreference(
                new CreatePaymentPreferenceRequest(50L), "ada@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPreference(
                new CreatePaymentPreferenceRequest(404L), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldUpdatePaymentToSuccessWhenWebhookHasApprovedPayment() {
        Order order = pendingOrder();
        Payment payment = Payment.builder()
                .id(1L).gateway("mercadopago").externalReference("pref_abc123")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pref_abc123"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment",
                    "action": "payment.updated",
                    "data": { "id": "pref_abc123", "status": "approved" }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldUpdatePaymentToFailedWhenWebhookHasRejectedPayment() {
        Order order = pendingOrder();
        Payment payment = Payment.builder()
                .id(1L).gateway("mercadopago").externalReference("pref_abc456")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pref_abc456"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment",
                    "action": "payment.updated",
                    "data": { "id": "pref_abc456", "status": "rejected" }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldPublishOrderConfirmedEventWhenWebhookConfirmsPendingOrder() {
        Order order = pendingOrder();
        Payment payment = Payment.builder()
                .id(2L).gateway("mercadopago").externalReference("pref_pub_ok")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pref_pub_ok"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment",
                    "data": { "id": "pref_pub_ok", "status": "approved" }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        ArgumentCaptor<OrderConfirmedEvent> captor =
                ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        OrderConfirmedEvent event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(50L);
        assertThat(event.buyerId()).isEqualTo(1L);
        assertThat(event.sellerId()).isEqualTo(2L);
    }

    @Test
    void shouldNotPublishOrderConfirmedEventWhenOrderWasAlreadyConfirmed() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        Payment payment = Payment.builder()
                .id(3L).gateway("mercadopago").externalReference("pref_already_ok")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pref_already_ok"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment",
                    "data": { "id": "pref_already_ok", "status": "approved" }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenWebhookReferencesUnknownPayment() {
        when(paymentRepository.findByExternalReference("pref_unknown"))
                .thenReturn(Optional.empty());
        String payload = """
                {
                    "type": "payment",
                    "data": { "id": "pref_unknown", "status": "approved" }
                }
                """;

        assertThatThrownBy(() -> paymentService.handleWebhook(payload, "sig"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowPaymentExceptionWhenWebhookPayloadIsMalformed() {
        assertThatThrownBy(() -> paymentService.handleWebhook("not json", "sig"))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void shouldThrowPaymentExceptionWhenWebhookPayloadMissingFields() {
        assertThatThrownBy(() -> paymentService.handleWebhook("{}", "sig"))
                .isInstanceOf(PaymentException.class);
    }
}
