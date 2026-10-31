package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.payment.provider.PaymentProviderRegistry;
import com.delivery.payment.provider.SandboxProperties;
import com.delivery.payment.provider.SandboxProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

/** Provider selection fails fast (LAC-31, W7-A): missing/unknown id, sandbox in prod, weak sandbox secret. */
class PaymentProviderRegistryTest {

    @Test
    void missingProviderFailsStartup() {
        assertThatThrownBy(() -> registry("", "dev", properties(true, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_PAYMENTS_PROVIDER");
    }

    @Test
    void unknownProviderFailsStartup() {
        assertThatThrownBy(() -> registry("mpesa", "dev", properties(true, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not available").hasMessageContaining("sandbox");
    }

    @Test
    void sandboxIsRefusedInProdUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> registry("sandbox", "prod", properties(true, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prod");
        assertThat(registry("sandbox", "prod", properties(true, true)).active().id()).isEqualTo("sandbox");
    }

    @Test
    void sandboxNeedsAStrongSecret() {
        assertThatThrownBy(() -> registry("sandbox", "dev", properties(false, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("webhook-secret");
    }

    @Test
    void activeProviderIsTheOnlyRoutableOne() {
        PaymentProviderRegistry registry = registry("SANDBOX", "dev", properties(true, false));
        assertThat(registry.active().id()).isEqualTo("sandbox");
        assertThat(registry.activeById("sandbox")).isPresent();
        assertThat(registry.activeById("mpesa")).isEmpty();
    }

    private static PaymentProviderRegistry registry(String configured, String profile, SandboxProperties properties) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        SandboxProvider sandbox = new SandboxProvider(properties, new ObjectMapper(),
                new StaticListableBeanFactory().getBeanProvider(RestClient.Builder.class));
        return new PaymentProviderRegistry(List.of(sandbox), configured, environment, properties);
    }

    private static SandboxProperties properties(boolean strongSecret, boolean allowInProd) {
        SandboxProperties properties = new SandboxProperties();
        properties.setWebhookSecret(strongSecret ? "a-sufficiently-long-sandbox-secret-0000" : "weak");
        properties.setAllowInProd(allowInProd);
        return properties;
    }
}
