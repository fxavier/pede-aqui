package com.pedeaqui.keycloak.smsotp;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.Environment;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Stub SMS gateway ({@code log}, the default until a real provider is chosen — LAC-18): writes the masked number
 * and the redacted text (code masked) to the Keycloak log and sends nothing. The code is never logged, except
 * when {@code --spi-sms-sender--log--reveal-code-in-dev-mode=true} AND Keycloak runs in development mode
 * ({@code start-dev}); in production mode that option is ignored with a warning.
 */
public class LoggingSmsSenderProviderFactory implements SmsSenderProviderFactory {
    public static final String ID = "log";
    private static final Logger LOG = Logger.getLogger(LoggingSmsSenderProviderFactory.class);

    private boolean revealCode;

    @Override
    public SmsSenderProvider create(KeycloakSession session) {
        boolean reveal = revealCode;
        return message -> LOG.info(logLine(message, reveal));
    }

    /** The only line this stub writes; carries the code only when {@code reveal} (dev mode opt-in). */
    static String logLine(SmsMessage message, boolean reveal) {
        return "SMS (stub, not sent) to " + PhoneNumbers.mask(message.toE164()) + ": "
                + (reveal ? message.text() : message.redactedText());
    }

    @Override
    public void init(Config.Scope config) {
        revealCode = effectiveReveal(config.getBoolean("reveal-code-in-dev-mode", false), Environment.isDevMode());
        if (revealCode) {
            LOG.warn("SMS stub reveals OTP codes in the log (development mode only). Never use in production.");
        }
    }

    /** Reveal only when requested AND in development mode. */
    static boolean effectiveReveal(boolean requested, boolean devMode) {
        if (requested && !devMode) {
            LOG.warn("Ignoring sms-sender log reveal-code-in-dev-mode: Keycloak is not in development mode");
        }
        return requested && devMode;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
