package com.yala.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.preference.Preference;
import java.util.Collections;
import com.yala.event.OrderConfirmedEvent;
import com.yala.exceptions.OrderNotConfirmableException;
import com.yala.exceptions.PaymentException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.Listing;
import com.yala.model.Order;
import com.yala.repository.OrderRepository;
import com.yala.model.OrderStatus;
import com.yala.dto.payment.RequestPaymentPreferenceDTO;
import com.yala.dto.payment.ResponsePaymentPreferenceDTO;
import com.yala.model.Role;
import com.yala.model.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PreferenceClient preferenceClient;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "accessToken", "TEST-access-token");
    }

    private User buyer() {
        return User.builder().id(1L).email("ada@yala.pe").role(Role.USER).build();
    }

    private User seller() {
        return User.builder().id(2L).email("bob@yala.pe").role(Role.SELLER).build();
    }

    private Listing listing() {
        return Listing.builder().id(10L).title("Charizard PSA 9").build();
    }

    private Order pendingOrder() {
        return Order.builder()
                .id(50L).amount(250f).status(OrderStatus.PENDING)
                .listing(listing()).buyer(buyer()).seller(seller()).build();
    }

    private Preference fakePreference(String id, String initPoint) {
        Preference preference = new Preference();
        ReflectionTestUtils.setField(preference, "id", id);
        ReflectionTestUtils.setField(preference, "initPoint", initPoint);
        return preference;
    }

    @Test
    void shouldCreatePreferenceWhenOrderIsPendingAndBuyerMatches() throws Exception {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));
        when(preferenceClient.create(any(PreferenceRequest.class))).thenReturn(
                fakePreference("123456789",
                        "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=123456789"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(999L);
            return p;
        });

        ResponsePaymentPreferenceDTO response = paymentService.createPreference(
                new RequestPaymentPreferenceDTO(50L), "ada@yala.pe");

        assertThat(response.preferenceId()).isEqualTo("123456789");
        assertThat(response.initPoint()).contains("pref_id=123456789");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenBuyerDoesNotMatch() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> paymentService.createPreference(
                new RequestPaymentPreferenceDTO(50L), "intruder@yala.pe"))
                .isInstanceOf(UnauthorizedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowOrderNotConfirmableExceptionWhenOrderIsNotPending() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createPreference(
                new RequestPaymentPreferenceDTO(50L), "ada@yala.pe"))
                .isInstanceOf(OrderNotConfirmableException.class);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPreference(
                new RequestPaymentPreferenceDTO(404L), "ada@yala.pe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowPaymentExceptionWhenAccessTokenIsNotConfigured() {
        ReflectionTestUtils.setField(paymentService, "accessToken", "");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> paymentService.createPreference(
                new RequestPaymentPreferenceDTO(50L), "ada@yala.pe"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("not configured");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowPaymentExceptionWhenMercadoPagoApiFails() throws Exception {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder()));
        MPResponse mpResponse = new MPResponse(400, Collections.emptyMap(),
                "{\"error\":\"bad_request\"}");
        when(preferenceClient.create(any(PreferenceRequest.class)))
                .thenThrow(new MPApiException("Bad request", mpResponse));

        assertThatThrownBy(() -> paymentService.createPreference(
                new RequestPaymentPreferenceDTO(50L), "ada@yala.pe"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Mercado Pago");
        verify(paymentRepository, never()).save(any());
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
