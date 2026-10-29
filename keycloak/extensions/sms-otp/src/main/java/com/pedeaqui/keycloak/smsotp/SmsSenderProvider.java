package com.pedeaqui.keycloak.smsotp;

import org.keycloak.provider.Provider;

/**
 * Provider-agnostic SMS gateway (LAC-18, ADR-0004). Implementations live in their own provider jar and are
 * selected with the Keycloak option {@code --spi-sms-sender--provider=<id>}; the authenticator never changes.
 * Implementations must never log {@link SmsMessage#text()} nor the destination unmasked.
 */
public interface SmsSenderProvider extends Provider {
    /** Sends the message; throws {@link SmsDeliveryException} when the gateway refuses or is unreachable. */
    void send(SmsMessage message);

    @Override
    default void close() {
    }
}
