package com.yala.config;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Mercado Pago SDK at startup and exposes a {@link PreferenceClient}
 * bean for the payment service. When {@code mercadopago.access-token} is blank
 * (e.g. dev environments without sandbox credentials), the SDK is left unconfigured
 * and the first call to {@code PreferenceClient#create} will fail — the payment
 * service translates that failure into a {@code PaymentException} with HTTP 502.
 */
@Configuration
public class MercadoPagoSdkConfig {

    public MercadoPagoSdkConfig(
            @Value("${mercadopago.access-token:}") String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            MercadoPagoConfig.setAccessToken(accessToken);
        }
    }

    @Bean
    public PreferenceClient preferenceClient() {
        return new PreferenceClient();
    }
}
