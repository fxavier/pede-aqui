package com.pedeaqui.keycloak.smsotp;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/** Declares the custom {@code sms-sender} SPI so gateways can be plugged in as separate Keycloak providers. */
public class SmsSenderSpi implements Spi {
    public static final String NAME = "sms-sender";

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return SmsSenderProvider.class;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return SmsSenderProviderFactory.class;
    }
}
