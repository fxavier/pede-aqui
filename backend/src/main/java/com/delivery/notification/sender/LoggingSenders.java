package com.delivery.notification.sender;

import com.delivery.notification.service.RecipientMasking;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Development stubs of the three provider seams (LAC-48), active when {@code app.notifications.<channel>.provider=log}
 * (the default). They log only the template key, the masked recipient and the body length — never the body (it may
 * carry an order reference, and a template with sensitive placeholders could carry a code) — and always succeed with
 * a synthetic message id. Any other provider id needs its own bean, or the application fails to start.
 */
@Configuration
public class LoggingSenders {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingSenders.class);

    /** Push stub. */
    @Bean
    @ConditionalOnProperty(prefix = "app.notifications.push", name = "provider", havingValue = "log", matchIfMissing = true)
    public PushSender loggingPushSender() {
        return (platform, token, title, body, templateKey) -> {
            LOGGER.info("notification.push.stub template={} platform={} to={} bodyLength={}", templateKey, platform,
                    RecipientMasking.token(token), body == null ? 0 : body.length());
            return SendResult.sent("log-" + UUID.randomUUID());
        };
    }

    /** SMS stub. */
    @Bean
    @ConditionalOnProperty(prefix = "app.notifications.sms", name = "provider", havingValue = "log", matchIfMissing = true)
    public SmsSender loggingSmsSender() {
        return (phoneE164, body, templateKey) -> {
            LOGGER.info("notification.sms.stub template={} to={} bodyLength={}", templateKey, RecipientMasking.phone(phoneE164),
                    body == null ? 0 : body.length());
            return SendResult.sent("log-" + UUID.randomUUID());
        };
    }

    /** E-mail stub. */
    @Bean
    @ConditionalOnProperty(prefix = "app.notifications.email", name = "provider", havingValue = "log", matchIfMissing = true)
    public EmailSender loggingEmailSender() {
        return (address, subject, body, templateKey) -> {
            LOGGER.info("notification.email.stub template={} to={} bodyLength={}", templateKey, RecipientMasking.email(address),
                    body == null ? 0 : body.length());
            return SendResult.sent("log-" + UUID.randomUUID());
        };
    }
}
