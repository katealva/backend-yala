package com.yala.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yala.exception.OrderNotConfirmableException;
import com.yala.exception.PaymentException;
import com.yala.exception.ResourceNotFoundException;
import com.yala.exception.UnauthorizedException;
import com.yala.order.Order;
import com.yala.order.OrderRepository;
import com.yala.order.OrderStatus;
import com.yala.payment.dto.CreatePaymentIntentRequest;
import com.yala.payment.dto.PaymentIntentResponse;
import com.yala.user.Role;
import com.yala.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.yala.event.OrderConfirmedEvent;

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
    void shouldCreateIntentWhenOrderIsPendingAndBuyerMatches() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(999L);
            return p;
        });

        PaymentIntentResponse response = paymentService.createIntent(
                new CreatePaymentIntentRequest(50L), "ada@yala.pe");

        assertThat(response.paymentIntentId()).startsWith("pi_");
        assertThat(response.clientSecret()).contains("_secret_");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenBuyerDoesNotMatch() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> paymentService.createIntent(
                new CreatePaymentIntentRequest(50L), "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenOrderIsNotPending() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createIntent(
                new CreatePaymentIntentRequest(50L), "ada@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createIntent(
                new CreatePaymentIntentRequest(404L), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldUpdatePaymentToSuccessWhenWebhookHasPaymentIntentSucceeded() {
        Order order = pendingOrder();
        Payment payment = Payment.builder()
                .id(1L).gateway("stripe").externalReference("pi_abc123")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pi_abc123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment_intent.succeeded",
                    "data": { "object": { "id": "pi_abc123", "amount": 25000 } }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldPublishOrderConfirmedEventWhenWebhookConfirmsPendingOrder() {
        Order order = pendingOrder();
        Payment payment = Payment.builder()
                .id(1L).gateway("stripe").externalReference("pi_evt_pub")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pi_evt_pub")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment_intent.succeeded",
                    "data": { "object": { "id": "pi_evt_pub" } }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        org.mockito.ArgumentCaptor<OrderConfirmedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        org.mockito.Mockito.verify(eventPublisher).publishEvent(captor.capture());
        OrderConfirmedEvent published = captor.getValue();
        assertThat(published.orderId()).isEqualTo(50L);
        assertThat(published.buyerId()).isEqualTo(1L);
        assertThat(published.sellerId()).isEqualTo(2L);
    }

    @Test
    void shouldNotPublishOrderConfirmedEventWhenOrderWasAlreadyConfirmed() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        Payment payment = Payment.builder()
                .id(1L).gateway("stripe").externalReference("pi_already")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pi_already")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment_intent.succeeded",
                    "data": { "object": { "id": "pi_already" } }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldUpdatePaymentToFailedWhenWebhookHasPaymentIntentFailed() {
        Order order = pendingOrder();
        Payment payment = Payment.builder()
                .id(1L).gateway("stripe").externalReference("pi_abc456")
                .amount(250f).status(PaymentStatus.PENDING).order(order).build();
        when(paymentRepository.findByExternalReference("pi_abc456")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
                {
                    "type": "payment_intent.payment_failed",
                    "data": { "object": { "id": "pi_abc456" } }
                }
                """;
        paymentService.handleWebhook(payload, "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenWebhookReferencesUnknownPayment() {
        when(paymentRepository.findByExternalReference("pi_unknown")).thenReturn(Optional.empty());
        String payload = """
                {
                    "type": "payment_intent.succeeded",
                    "data": { "object": { "id": "pi_unknown" } }
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
