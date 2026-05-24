package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private Resend resend;
    @Mock private Emails emailsApi;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // SpringTemplateEngine usa Spring EL (no OGNL), igual que el bean que arma
        // Spring Boot en producción a partir de spring-boot-starter-thymeleaf.
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);

        emailService = new EmailService(engine, resend,
                "re_test_key",
                "onboarding@resend.dev");
    }

    @Test
    void shouldSkipSendWhenApiKeyIsBlank() {
        ReflectionTestUtils.setField(emailService, "apiKey", "");

        emailService.sendBidOutbid("ada@yala.pe", "Ada", "Pikachu", 100f,
                "http://test/auctions/1", 1L);

        verify(resend, never()).emails();
    }

    @Test
    void shouldCallResendSdkWithRenderedTemplateWhenSendingBidOutbid() throws Exception {
        when(resend.emails()).thenReturn(emailsApi);
        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("email_abc123");
        when(emailsApi.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .thenReturn(response);

        emailService.sendBidOutbid("ada@yala.pe", "Ada Lovelace", "Charizard PSA 9",
                250.50f, "http://test/auctions/5", 5L);

        ArgumentCaptor<CreateEmailOptions> optionsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> requestCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(emailsApi).send(optionsCaptor.capture(), requestCaptor.capture());

        CreateEmailOptions opts = optionsCaptor.getValue();
        // CreateEmailOptions exposes getters via reflection-friendly names
        Object html = ReflectionTestUtils.invokeGetterMethod(opts, "html");
        Object subject = ReflectionTestUtils.invokeGetterMethod(opts, "subject");
        Object from = ReflectionTestUtils.invokeGetterMethod(opts, "from");

        assertThatCode(() -> {
            assert from != null && from.toString().equals("onboarding@resend.dev");
            assert subject != null && subject.toString().contains("superaron");
            assert html != null && html.toString().contains("Ada Lovelace");
            assert html != null && html.toString().contains("Charizard PSA 9");
        }).doesNotThrowAnyException();

        assert requestCaptor.getValue().getIdempotencyKey().startsWith("outbid/5-");
    }

    @Test
    void shouldNotThrowWhenResendSdkFails() throws Exception {
        when(resend.emails()).thenReturn(emailsApi);
        when(emailsApi.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .thenThrow(new ResendException("API error 400"));

        assertThatCode(() -> emailService.sendAuctionWon(
                "ada@yala.pe", "Ada", "Charizard", 1000f,
                "http://test/orders/9", 9L))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldIncludeIdempotencyKeyForOrderConfirmed() throws Exception {
        when(resend.emails()).thenReturn(emailsApi);
        when(emailsApi.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .thenReturn(mock(CreateEmailResponse.class));

        emailService.sendOrderConfirmed("buyer@yala.pe", "Buyer", "Funko",
                250f, "http://test/orders/77", 77L);

        ArgumentCaptor<RequestOptions> requestCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(emailsApi).send(any(CreateEmailOptions.class), requestCaptor.capture());
        assert requestCaptor.getValue().getIdempotencyKey().equals("order-confirmed/77");
    }
}
