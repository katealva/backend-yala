package com.yala.email;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends transactional emails via the Resend HTTP API, rendering Thymeleaf templates
 * from {@code templates/email/}. All methods are {@link Async} and best-effort:
 * delivery failures are logged but never thrown back to the caller.
 */
@Slf4j
@Service
public class EmailService {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final TemplateEngine templateEngine;
    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;

    public EmailService(
            TemplateEngine templateEngine,
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:noreply@yala.pe}") String fromAddress) {
        this.templateEngine = templateEngine;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.restClient = RestClient.create();
    }

    @Async
    public void sendBidOutbid(String to, String recipientName, String listingTitle,
            Float currentPrice, String auctionUrl) {
        send(to, "Te superaron en la puja — Yala", "email/bid-outbid", Map.of(
                "recipientName", recipientName,
                "listingTitle", listingTitle,
                "currentPrice", currentPrice,
                "auctionUrl", auctionUrl));
    }

    @Async
    public void sendAuctionWon(String to, String recipientName, String listingTitle,
            Float finalPrice, String orderUrl) {
        send(to, "¡Ganaste la subasta! — Yala", "email/auction-won", Map.of(
                "recipientName", recipientName,
                "listingTitle", listingTitle,
                "finalPrice", finalPrice,
                "orderUrl", orderUrl));
    }

    @Async
    public void sendSaleConfirmed(String to, String recipientName, String listingTitle,
            String buyerName, Float amount, String orderUrl) {
        send(to, "Tu venta fue confirmada — Yala", "email/sale-confirmed", Map.of(
                "recipientName", recipientName,
                "listingTitle", listingTitle,
                "buyerName", buyerName,
                "amount", amount,
                "orderUrl", orderUrl));
    }

    @Async
    public void sendOrderConfirmed(String to, String recipientName, String listingTitle,
            Float amount, String orderUrl) {
        send(to, "Tu orden fue confirmada — Yala", "email/order-confirmed", Map.of(
                "recipientName", recipientName,
                "listingTitle", listingTitle,
                "amount", amount,
                "orderUrl", orderUrl));
    }

    private void send(String to, String subject, String templateName,
            Map<String, Object> variables) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured; skipping email to {} (subject: {})",
                    to, subject);
            return;
        }
        try {
            Context context = new Context();
            variables.forEach(context::setVariable);
            String html = templateEngine.process(templateName, context);

            ResendEmailRequest body = new ResendEmailRequest(
                    fromAddress, List.of(to), subject, html);

            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email sent to {} (subject: {})", to, subject);
        } catch (RestClientException ex) {
            log.error("Failed to send email to {} (subject: {}): {}",
                    to, subject, ex.getMessage());
        }
    }

    /** JSON payload accepted by the Resend {@code POST /emails} endpoint. */
    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html) {
    }
}
