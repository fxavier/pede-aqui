package com.delivery.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.config.NotificationProperties;
import com.delivery.notification.entity.UserNotificationPreference;
import com.delivery.notification.service.ChannelPreferences;
import com.delivery.notification.service.RecipientMasking;
import com.delivery.notification.service.TemplateRenderer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** LAC-48 pure rules: template rendering (incl. the sensitive-placeholder guard), masking, preferences, backoff. */
class NotificationRulesTest {

    @Test
    void rendersPlaceholdersAndBlanksUnknownOnes() {
        String body = TemplateRenderer.render("Pedido {orderReference} pronto{unknown}.", Map.of("orderReference", "PA-42"), false);
        assertThat(body).isEqualTo("Pedido PA-42 pronto.");
        assertThat(TemplateRenderer.render(null, Map.of(), false)).isNull();
        assertThat(TemplateRenderer.placeholders("{a} e {orderReference} e {a}")).containsExactly("a", "orderReference");
    }

    @Test
    void sensitivePlaceholdersRenderOnlyWhenTheTemplateAllowsThem() {
        Map<String, String> params = Map.of("deliveryCode", "123456", "otp", "999999", "orderReference", "PA-1");
        String text = "Código {deliveryCode} / {otp} / {orderReference}";

        assertThat(TemplateRenderer.render(text, params, false)).isEqualTo("Código  /  / PA-1").doesNotContain("123456", "999999");
        assertThat(TemplateRenderer.render(text, params, true)).isEqualTo("Código 123456 / 999999 / PA-1");
        assertThat(TemplateRenderer.isSensitive("deliveryCode")).isTrue();
        assertThat(TemplateRenderer.isSensitive("PIN")).isTrue();
        assertThat(TemplateRenderer.isSensitive("resetToken")).isTrue();
        assertThat(TemplateRenderer.isSensitive("orderReference")).isFalse();
    }

    @Test
    void replacementValuesAreLiteral() {
        assertThat(TemplateRenderer.render("{orderReference}", Map.of("orderReference", "$1\\x"), false)).isEqualTo("$1\\x");
    }

    @Test
    void masksPhonesEmailsAndTokens() {
        assertThat(RecipientMasking.phone("+258841234567")).isEqualTo("+25884*****67");
        assertThat(RecipientMasking.phone("+2581")).isEqualTo("*****");
        assertThat(RecipientMasking.phone(null)).isNull();
        assertThat(RecipientMasking.email("ana.silva@example.co.mz")).isEqualTo("a***@e***.mz");
        assertThat(RecipientMasking.email("broken")).isEqualTo("***");
        assertThat(RecipientMasking.token("fcm-token-abcdefgh-wxyz")).isEqualTo("****wxyz");
        assertThat(RecipientMasking.token("abc")).isEqualTo("****");
    }

    @Test
    void preferencesDefaultToPushOnlyAndInAppIsAlwaysAllowed() {
        ChannelPreferences none = ChannelPreferences.of(null);
        assertThat(none.allows(NotificationChannel.IN_APP)).isTrue();
        assertThat(none.allows(NotificationChannel.PUSH)).isTrue();
        assertThat(none.allows(NotificationChannel.SMS)).isFalse();
        assertThat(none.allows(NotificationChannel.EMAIL)).isFalse();

        UserNotificationPreference row = new UserNotificationPreference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        row.updatePreferences(false, true, true);
        ChannelPreferences stored = ChannelPreferences.of(row);
        assertThat(stored.allows(NotificationChannel.PUSH)).isFalse();
        assertThat(stored.allows(NotificationChannel.SMS)).isTrue();
        assertThat(stored.allows(NotificationChannel.EMAIL)).isTrue();
        assertThat(stored.allows(NotificationChannel.IN_APP)).isTrue();
    }

    @Test
    void backoffIsExponentialAndCapped() {
        NotificationProperties properties = new NotificationProperties();
        properties.setBaseBackoff(Duration.ofSeconds(30));
        properties.setMaxBackoff(Duration.ofMinutes(5));
        assertThat(properties.backoffAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.backoffAfter(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.backoffAfter(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.backoffAfter(4)).isEqualTo(Duration.ofSeconds(240));
        assertThat(properties.backoffAfter(5)).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.backoffAfter(60)).isEqualTo(Duration.ofMinutes(5));
    }
}
