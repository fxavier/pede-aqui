package com.pedeaqui.keycloak.smsotp;

import org.keycloak.provider.ProviderFactory;

/** Factory of an {@link SmsSenderProvider}; one per SMS gateway. */
public interface SmsSenderProviderFactory extends ProviderFactory<SmsSenderProvider> {
}
