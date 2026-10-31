package com.delivery.payment.provider;

import com.delivery.payment.api.PaymentProvider;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Selects the active {@link PaymentProvider} from {@code app.payments.provider} (LAC-31) and fails startup when it is
 * missing (prod has no default), unknown, or the sandbox under the {@code prod} profile without
 * {@code app.payments.sandbox.allow-in-prod}. Only the active provider is reachable (attempts and webhooks).
 */
@Component
public class PaymentProviderRegistry {
    private final PaymentProvider active;

    public PaymentProviderRegistry(List<PaymentProvider> providers, @Value("${app.payments.provider:}") String configured,
                                   Environment environment, SandboxProperties sandboxProperties) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("app.payments.provider (APP_PAYMENTS_PROVIDER) is required; available: " + ids(providers));
        }
        String id = configured.trim().toLowerCase();
        this.active = providers.stream().filter(provider -> provider.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("app.payments.provider=" + id + " is not available; available: " + ids(providers)));
        if (active instanceof SandboxProvider sandbox) {
            if (environment.acceptsProfiles(Profiles.of("prod")) && !sandboxProperties.isAllowInProd()) {
                throw new IllegalStateException("The sandbox payment provider confirms payments without money and is refused in the "
                        + "prod profile (set app.payments.sandbox.allow-in-prod=true only for staging)");
            }
            sandbox.requireConfigured();
        }
    }

    /** The configured provider. */
    public PaymentProvider active() {
        return active;
    }

    /** The active provider when its id is {@code id} (webhook routing); any other id is unknown. */
    public Optional<PaymentProvider> activeById(String id) {
        return active.id().equals(id) ? Optional.of(active) : Optional.empty();
    }

    private static String ids(List<PaymentProvider> providers) {
        return providers.stream().map(PaymentProvider::id).sorted().collect(Collectors.joining(", "));
    }
}
