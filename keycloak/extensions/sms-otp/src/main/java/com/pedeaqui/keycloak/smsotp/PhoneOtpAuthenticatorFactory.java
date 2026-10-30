package com.pedeaqui.keycloak.smsotp;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.common.util.Environment;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/**
 * Registers the {@code pede-aqui-phone-otp} authenticator (LAC-18, ADR-0004). Server option
 * {@code --spi-authenticator--pede-aqui-phone-otp--hmac-secret} (env
 * {@code KC_SPI_AUTHENTICATOR__PEDE_AQUI_PHONE_OTP__HMAC_SECRET}, at least 32 characters) keys the code hashes and
 * the counter keys; it must be identical on every Keycloak node. Without it Keycloak refuses to start in
 * production mode; in development mode a random per-boot secret is generated (pending codes die on restart).
 */
public class PhoneOtpAuthenticatorFactory implements AuthenticatorFactory {
    public static final String ID = "pede-aqui-phone-otp";
    private static final Logger LOG = Logger.getLogger(PhoneOtpAuthenticatorFactory.class);
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED};

    private byte[] secret;

    @Override
    public Authenticator create(KeycloakSession session) {
        return new PhoneOtpAuthenticator(session, secret);
    }

    @Override
    public void init(Config.Scope config) {
        secret = resolveSecret(config.get("hmac-secret"), Environment.isDevMode());
    }

    /** The configured secret; a random one only in development mode; otherwise startup fails. */
    static byte[] resolveSecret(String configured, boolean devMode) {
        if (configured != null && !configured.isBlank()) {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException(ID + ": hmac-secret must have at least 32 characters");
            }
            LOG.infof("%s: HMAC secret source=config", ID);
            return bytes;
        }
        if (!devMode) {
            throw new IllegalStateException(ID + ": hmac-secret is required in production mode "
                    + "(KC_SPI_AUTHENTICATOR__PEDE_AQUI_PHONE_OTP__HMAC_SECRET)");
        }
        LOG.warnf("%s: no hmac-secret configured, using a random per-boot secret (development mode only)", ID);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
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

    @Override
    public String getDisplayType() {
        return "Pede Aqui - Phone + SMS code";
    }

    @Override
    public String getReferenceCategory() {
        return "phone-otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENTS;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Customer login with a mobile number and a 6-digit SMS code (LAC-18). Not for staff/privileged roles.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        OtpPolicy d = OtpPolicy.defaults();
        return ProviderConfigurationBuilder.create()
                .property().name(OtpPolicy.KEY_CODE_TTL).label("Code TTL (s)").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.codeTtlSeconds())).add()
                .property().name(OtpPolicy.KEY_MAX_FAILED).label("Wrong codes before lock").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.maxFailedAttempts())).add()
                .property().name(OtpPolicy.KEY_RESEND_COOLDOWN).label("Resend cooldown (s)").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.resendCooldownSeconds())).add()
                .property().name(OtpPolicy.KEY_SENDS_PER_PHONE).label("SMS per phone per window").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.sendsPerPhone())).add()
                .property().name(OtpPolicy.KEY_SENDS_PER_IP).label("SMS per IP per window").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.sendsPerIp())).add()
                .property().name(OtpPolicy.KEY_FAILURES_PER_IP).label("Wrong codes per IP before lock").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.failuresPerIp())).add()
                .property().name(OtpPolicy.KEY_WINDOW).label("Counter window (s)").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.windowSeconds())).add()
                .property().name(OtpPolicy.KEY_LOCK_BASE).label("First lock (s), doubles per lock in 24 h").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.lockBaseSeconds())).add()
                .property().name(OtpPolicy.KEY_LOCK_MAX).label("Maximum lock (s)").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(d.lockMaxSeconds())).add()
                .property().name(OtpPolicy.KEY_COUNTRIES).label("Allowed country codes (CSV)").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(PhoneNumbers.MOZAMBIQUE).add()
                .property().name(OtpPolicy.KEY_TEMPLATE).label("SMS template ({code}, {minutes})").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(OtpPolicy.DEFAULT_SMS_TEMPLATE).add()
                .property().name(OtpPolicy.KEY_BLOCKED_ROLES).label("Roles that may not use phone login (CSV)").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(OtpPolicy.DEFAULT_BLOCKED_ROLES).add()
                .property().name(OtpPolicy.KEY_REQUIRED_ROLE).label("Required / granted role").type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("CUSTOMER").add()
                .build();
    }
}
