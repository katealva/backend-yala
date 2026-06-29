package com.yala.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends transactional emails via the official Resend Java SDK, rendering Thymeleaf
 * templates from {@code templates/email/}. All methods are {@link Async} and
 * best-effort: delivery failures are logged but never thrown back to the caller.
 *
 * <p>Each public method receives the related entity id (auction or order) so that
 * the SDK call is made with an idempotency key in the format
 * {@code <event-type>/<entity-id>} — this protects against duplicate sends when
 * {@code @Async} tasks are retried.
 */
@Slf4j
@Service
public class EmailService {

    private final TemplateEngine templateEngine;
    private final Resend resend;
    private final String apiKey;
    private final String fromAddress;

    public EmailService(
            TemplateEngine templateEngine,
            Resend resend,
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:onboarding@resend.dev}") String fromAddress) {
        this.templateEngine = templateEngine;
        this.resend = resend;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendBidOutbid(String to, String recipientName, String listingTitle,
            Float currentPrice, String auctionUrl, Long auctionId) {
        send(to, "Te superaron en la puja — Yala", "email/bid-outbid",
                Map.of(
                        "recipientName", recipientName,
                        "listingTitle", listingTitle,
                        "currentPrice", currentPrice,
                        "auctionUrl", auctionUrl),
                "outbid/" + auctionId + "-" + Integer.toHexString(to.hashCode()));
    }

    @Async
    public void sendAuctionWon(String to, String recipientName, String listingTitle,
            Float finalPrice, String orderUrl, Long orderId) {
        send(to, "¡Ganaste la subasta! — Yala", "email/auction-won",
                Map.of(
                        "recipientName", recipientName,
                        "listingTitle", listingTitle,
                        "finalPrice", finalPrice,
                        "orderUrl", orderUrl),
                "auction-won/" + orderId);
    }

    @Async
    public void sendSaleConfirmed(String to, String recipientName, String listingTitle,
            String buyerName, Float amount, String orderUrl, Long orderId) {
        send(to, "Tu venta fue confirmada — Yala", "email/sale-confirmed",
                Map.of(
                        "recipientName", recipientName,
                        "listingTitle", listingTitle,
                        "buyerName", buyerName,
                        "amount", amount,
                        "orderUrl", orderUrl),
                "sale-confirmed/" + orderId);
    }

    @Async
    public void sendOrderConfirmed(String to, String recipientName, String listingTitle,
            Float amount, String orderUrl, Long orderId) {
        send(to, "Tu orden fue confirmada — Yala", "email/order-confirmed",
                Map.of(
                        "recipientName", recipientName,
                        "listingTitle", listingTitle,
                        "amount", amount,
                        "orderUrl", orderUrl),
                "order-confirmed/" + orderId);
    }

    @Async
    public void sendPasswordReset(String to, String recipientName, String code) {
        send(to, "Tu código para restablecer la contraseña — Yala", "email/password-reset",
                Map.of(
                        "recipientName", recipientName,
                        "code", code),
                "password-reset/" + to + "/" + code);
    }

    private void send(String to, String subject, String templateName,
            Map<String, Object> variables, String idempotencyKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured; skipping email to {} (subject: {})",
                    to, subject);
            return;
        }

        Context context = new Context();
        variables.forEach(context::setVariable);
        String html = templateEngine.process(templateName, context);

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(to)
                .subject(subject)
                .html(html)
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            CreateEmailResponse response = resend.emails().send(options, requestOptions);
            log.info("Email sent via Resend to {} (subject: {}, id: {}, idempotency: {})",
                    to, subject, response.getId(), idempotencyKey);
        } catch (ResendException ex) {
            log.error("Resend rejected email to {} (subject: {}, idempotency: {}): {}",
                    to, subject, idempotencyKey, ex.getMessage());
        }
    }
}
