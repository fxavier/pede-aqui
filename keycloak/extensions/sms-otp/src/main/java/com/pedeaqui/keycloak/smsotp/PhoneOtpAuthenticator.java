package com.pedeaqui.keycloak.smsotp;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Browser-flow step "log in with phone + SMS code" for customer clients (LAC-18, ADR-0004). Two screens: phone
 * number, then the 6-digit code. Rules (TTL, attempts, cooldown, quotas, locks) are in {@link PhoneOtpEngine};
 * this class only moves between screens, keeps the challenge in the login session (hash only) and resolves the
 * user. Choosing "email and password" returns ATTEMPTED so the flow falls through to the next alternative
 * (password form + privileged TOTP subflows). An email {@code login_hint} (sent after web/app registration)
 * skips straight to that alternative.
 */
public class PhoneOtpAuthenticator implements Authenticator {
    static final String FORM_PHONE = "phone-otp-phone.ftl";
    static final String FORM_CODE = "phone-otp-code.ftl";
    static final String NOTE_ID = "pa-otp.id";
    static final String NOTE_PHONE = "pa-otp.phone";
    static final String NOTE_HASH = "pa-otp.hash";
    static final String NOTE_EXPIRES = "pa-otp.exp";
    private static final Logger LOG = Logger.getLogger(PhoneOtpAuthenticator.class);

    private final KeycloakSession session;
    private final byte[] secret;

    public PhoneOtpAuthenticator(KeycloakSession session, byte[] secret) {
        this.session = session;
        this.secret = secret;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        if (authSession.getAuthNote(NOTE_HASH) != null) {
            context.challenge(codeForm(context, null));
            return;
        }
        String hint = authSession.getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);
        OtpPolicy policy = policy(context);
        Optional<String> hintedPhone = PhoneNumbers.normalize(hint, policy.allowedCountryCodes());
        if (hint != null && !hint.isBlank() && hintedPhone.isEmpty()) {
            context.attempted(); // e.g. email hint after registration: go to the password alternative
            return;
        }
        context.challenge(phoneForm(context, hintedPhone.orElse(null), null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        String action = Optional.ofNullable(form.getFirst("action")).orElse("verify");
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        OtpPolicy policy = policy(context);
        switch (action) {
            case "password" -> {
                clearChallenge(authSession);
                context.attempted();
            }
            case "change" -> {
                clearChallenge(authSession);
                context.challenge(phoneForm(context, null, null));
            }
            case "send" -> {
                Optional<String> phone = PhoneNumbers.normalize(form.getFirst("phone"), policy.allowedCountryCodes());
                if (phone.isEmpty()) {
                    context.getEvent().error(Errors.INVALID_INPUT);
                    context.challenge(phoneForm(context, null, "phoneOtpInvalidPhone"));
                    return;
                }
                send(context, policy, phone.get());
            }
            case "resend" -> {
                String phone = authSession.getAuthNote(NOTE_PHONE);
                if (phone == null) {
                    context.challenge(phoneForm(context, null, null));
                    return;
                }
                send(context, policy, phone);
            }
            default -> verify(context, policy, form.getFirst("code"));
        }
    }

    private void send(AuthenticationFlowContext context, OtpPolicy policy, String phone) {
        PhoneOtpEngine.SendResult result = engine(policy).issue(phone, clientIp(context));
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        if (result instanceof PhoneOtpEngine.Refused refused) {
            context.getEvent().detail("phone", PhoneNumbers.mask(phone)).detail("reason", refused.reason().name())
                    .error("phone_otp_send_refused");
            boolean pending = authSession.getAuthNote(NOTE_HASH) != null && phone.equals(authSession.getAuthNote(NOTE_PHONE));
            String message = switch (refused.reason()) {
                case COOLDOWN -> "phoneOtpCooldown";
                case LOCKED -> "phoneOtpLocked";
                case PHONE_QUOTA, IP_QUOTA -> "phoneOtpTooManySends";
            };
            Response page = pending
                    ? codeForm(context, message, refused.retryAfterSeconds())
                    : phoneForm(context, phone, message, refused.retryAfterSeconds());
            context.challenge(page);
            return;
        }
        PhoneOtpEngine.Issued issued = (PhoneOtpEngine.Issued) result;
        String text = policy.renderSms(issued.code());
        String redacted = policy.renderSms("*".repeat(OtpPolicy.CODE_LENGTH));
        try {
            SmsSenderProvider sender = session.getProvider(SmsSenderProvider.class);
            if (sender == null) {
                throw new SmsDeliveryException("No sms-sender provider configured", null);
            }
            sender.send(new SmsMessage(phone, text, redacted));
        } catch (RuntimeException e) {
            LOG.errorf("Phone OTP: SMS to %s failed: %s", PhoneNumbers.mask(phone), e.getClass().getSimpleName());
            context.getEvent().detail("phone", PhoneNumbers.mask(phone)).error("phone_otp_sms_failed");
            context.challenge(phoneForm(context, phone, "phoneOtpSmsFailed"));
            return;
        }
        PhoneOtpEngine.Challenge challenge = issued.challenge();
        authSession.setAuthNote(NOTE_ID, challenge.id());
        authSession.setAuthNote(NOTE_PHONE, challenge.phone());
        authSession.setAuthNote(NOTE_HASH, challenge.hash());
        authSession.setAuthNote(NOTE_EXPIRES, String.valueOf(challenge.expiresAtEpochSecond()));
        context.challenge(codeFormWithInfo(context, "phoneOtpCodeSent"));
    }

    private void verify(AuthenticationFlowContext context, OtpPolicy policy, String code) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String hash = authSession.getAuthNote(NOTE_HASH);
        if (hash == null) {
            context.challenge(phoneForm(context, null, null));
            return;
        }
        PhoneOtpEngine.Challenge challenge = new PhoneOtpEngine.Challenge(authSession.getAuthNote(NOTE_ID),
                authSession.getAuthNote(NOTE_PHONE), hash, Long.parseLong(authSession.getAuthNote(NOTE_EXPIRES)));
        PhoneOtpEngine.VerifyResult result = engine(policy).verify(challenge, code, clientIp(context));
        String masked = PhoneNumbers.mask(challenge.phone());
        switch (result) {
            case PhoneOtpEngine.WrongCode wrong -> {
                context.getEvent().detail("phone", masked).error(Errors.INVALID_USER_CREDENTIALS);
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        codeForm(context, "phoneOtpWrongCode", wrong.remainingAttempts()));
            }
            case PhoneOtpEngine.Expired expired -> {
                context.getEvent().detail("phone", masked).error(Errors.EXPIRED_CODE);
                context.challenge(codeForm(context, "phoneOtpExpired"));
            }
            case PhoneOtpEngine.Locked locked -> {
                clearChallenge(authSession);
                context.getEvent().detail("phone", masked).error(Errors.USER_TEMPORARILY_DISABLED);
                context.failureChallenge(AuthenticationFlowError.USER_TEMPORARILY_DISABLED,
                        phoneForm(context, challenge.phone(), "phoneOtpLocked", locked.retryAfterSeconds()));
            }
            case PhoneOtpEngine.Verified verified -> onVerified(context, policy, verified.phone());
        }
    }

    private void onVerified(AuthenticationFlowContext context, OtpPolicy policy, String phone) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        clearChallenge(authSession);
        RealmModel realm = context.getRealm();
        PhoneUserResolver.Resolution resolution = new PhoneUserResolver(policy).resolve(session, realm, phone);
        if (resolution.user() == null) {
            // Same message for every refusal: do not reveal whether the number belongs to a staff account.
            context.getEvent().detail("phone", PhoneNumbers.mask(phone)).detail("reason", resolution.refusal().name())
                    .error(Errors.NOT_ALLOWED);
            context.failureChallenge(AuthenticationFlowError.ACCESS_DENIED, phoneForm(context, null, "phoneOtpNotAllowed"));
            return;
        }
        UserModel user = resolution.user();
        String bruteForceError = AuthenticatorUtils.getDisabledByBruteForceEventError(context, user);
        if (bruteForceError != null) {
            context.getEvent().user(user).error(bruteForceError);
            context.failureChallenge(AuthenticationFlowError.USER_TEMPORARILY_DISABLED,
                    phoneForm(context, null, "phoneOtpLocked", 0L));
            return;
        }
        if (resolution.created()) {
            context.getEvent().detail("phone_otp_user_created", "true");
        }
        context.getEvent().detail("auth_method", "phone_otp");
        context.setUser(user);
        context.success();
    }

    private PhoneOtpEngine engine(OtpPolicy policy) {
        return new PhoneOtpEngine(policy, new KeycloakOtpStore(session.singleUseObjects()), secret,
                Clock.systemUTC(), new SecureRandom());
    }

    static OtpPolicy policy(AuthenticationFlowContext context) {
        return context.getAuthenticatorConfig() == null
                ? OtpPolicy.defaults()
                : OtpPolicy.fromConfig(context.getAuthenticatorConfig().getConfig());
    }

    private static String clientIp(AuthenticationFlowContext context) {
        return context.getConnection() == null ? "unknown" : context.getConnection().getRemoteAddr();
    }

    private static void clearChallenge(AuthenticationSessionModel authSession) {
        authSession.removeAuthNote(NOTE_ID);
        authSession.removeAuthNote(NOTE_PHONE);
        authSession.removeAuthNote(NOTE_HASH);
        authSession.removeAuthNote(NOTE_EXPIRES);
    }

    private Response phoneForm(AuthenticationFlowContext context, String phone, String error, Object... params) {
        LoginFormsProvider form = context.form();
        if (phone != null) {
            form.setAttribute("phone", phone);
        }
        if (error != null) {
            form.setError(error, params);
        }
        return form.createForm(FORM_PHONE);
    }

    private Response codeForm(AuthenticationFlowContext context, String error, Object... params) {
        LoginFormsProvider form = codeFormBase(context);
        if (error != null) {
            form.setError(error, params);
        }
        return form.createForm(FORM_CODE);
    }

    private Response codeFormWithInfo(AuthenticationFlowContext context, String info) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        return codeFormBase(context).setInfo(info, PhoneNumbers.mask(authSession.getAuthNote(NOTE_PHONE)))
                .createForm(FORM_CODE);
    }

    private LoginFormsProvider codeFormBase(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        return context.form()
                .setAttribute("maskedPhone", PhoneNumbers.mask(authSession.getAuthNote(NOTE_PHONE)))
                .setAttribute("codeLength", OtpPolicy.CODE_LENGTH);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
