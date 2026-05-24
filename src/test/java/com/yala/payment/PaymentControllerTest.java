package com.yala.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yala.auth.JwtService;
import com.yala.exception.PaymentException;
import com.yala.payment.dto.CreatePaymentPreferenceRequest;
import com.yala.payment.dto.PaymentPreferenceResponse;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    private static Principal principal(String email) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void shouldReturn201WhenCreatePreferenceInvokedByBuyer() throws Exception {
        when(paymentService.createPreference(any(CreatePaymentPreferenceRequest.class),
                        eq("ada@yala.pe")))
                .thenReturn(new PaymentPreferenceResponse(
                        "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref_abc",
                        "pref_abc"));

        mockMvc.perform(post("/api/v1/payments/preference")
                        .principal(principal("ada@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentPreferenceRequest(50L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferenceId").value("pref_abc"))
                .andExpect(jsonPath("$.initPoint").value(
                        "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=pref_abc"));
    }

    @Test
    void shouldReturn200WhenWebhookPayloadIsAcceptedByService() throws Exception {
        String payload = "{\"type\":\"payment\",\"action\":\"payment.updated\","
                + "\"data\":{\"id\":\"pref_abc\",\"status\":\"approved\"}}";

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("x-signature", "sig_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn502WhenWebhookPayloadIsMalformed() throws Exception {
        doThrow(new PaymentException("Malformed webhook payload"))
                .when(paymentService).handleWebhook(anyString(), any());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("x-signature", "sig_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bad\":true}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void shouldReturn400WhenCreatePreferenceBodyMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/payments/preference")
                        .principal(principal("ada@yala.pe"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
