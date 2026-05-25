package com.yala.config;

import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link Resend} client bean used by {@code EmailService}.
 * <p>
 * The Resend SDK does not fail at construction time with an empty API key — the
 * first {@code send()} call would return an authentication error. {@code EmailService}
 * skips sending and logs a warning when the API key is blank, so dev/test
 * environments without Resend credentials still start cleanly.
 */
@Configuration
public class ResendConfig {

    @Bean
    public Resend resendClient(@Value("${resend.api-key:}") String apiKey) {
        return new Resend(apiKey);
    }
}
