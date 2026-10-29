package com.pedeaqui.keycloak.smsotp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

/**
 * Maps a verified phone to the Keycloak user that may log in with it (LAC-18). The phone lives in the user
 * attribute {@code phone_e164} (admin-only edit in the realm user profile) plus {@code phone_verified=true}.
 * A first login creates the customer (opaque username, role CUSTOMER). Users holding any
 * blocked (staff/privileged) role, users without the required role, disabled users and ambiguous phones are
 * refused — privileged accounts keep the password + TOTP flow.
 */
public class PhoneUserResolver {
    public static final String ATTR_PHONE = "phone_e164";
    public static final String ATTR_PHONE_VERIFIED = "phone_verified";
    public static final String USERNAME_PREFIX = "phone-";
    private static final Logger LOG = Logger.getLogger(PhoneUserResolver.class);

    /** Why a verified phone cannot be used to log in. */
    public enum Refusal {
        AMBIGUOUS, PRIVILEGED, NOT_CUSTOMER, DISABLED, MISCONFIGURED
    }

    /** Either the user to authenticate or the refusal reason. */
    public record Resolution(UserModel user, Refusal refusal, boolean created) {
    }

    private final OtpPolicy policy;

    public PhoneUserResolver(OtpPolicy policy) {
        this.policy = policy;
    }

    /** Finds (or, on first login, creates) the customer owning {@code phone}. */
    public Resolution resolve(KeycloakSession session, RealmModel realm, String phone) {
        RoleModel required = realm.getRole(policy.requiredRole());
        if (required == null) {
            LOG.errorf("Phone OTP: required role %s does not exist in realm %s", policy.requiredRole(), realm.getName());
            return new Resolution(null, Refusal.MISCONFIGURED, false);
        }
        List<UserModel> owners = session.users()
                .searchForUserByUserAttributeStream(realm, ATTR_PHONE, phone)
                .limit(2)
                .collect(Collectors.toList());
        if (owners.size() > 1) {
            LOG.warnf("Phone OTP: %s is linked to more than one user; login refused", PhoneNumbers.mask(phone));
            return new Resolution(null, Refusal.AMBIGUOUS, false);
        }
        if (owners.size() == 1) {
            UserModel user = owners.get(0);
            Optional<Refusal> refusal = checkEligible(realm, user, required);
            if (refusal.isPresent()) {
                return new Resolution(null, refusal.get(), false);
            }
            if (!"true".equals(user.getFirstAttribute(ATTR_PHONE_VERIFIED))) {
                user.setSingleAttribute(ATTR_PHONE_VERIFIED, "true");
            }
            return new Resolution(user, null, false);
        }
        UserModel user = session.users().addUser(realm, newUsername());
        user.setEnabled(true);
        user.setSingleAttribute(ATTR_PHONE, phone);
        user.setSingleAttribute(ATTR_PHONE_VERIFIED, "true");
        user.grantRole(required);
        return new Resolution(user, null, true);
    }

    /**
     * Username of a phone-created customer: opaque ({@code phone-<uuid>}), never the number, so Keycloak events and
     * logs that carry usernames do not leak phone numbers and no existing username can be taken over.
     */
    static String newUsername() {
        return USERNAME_PREFIX + UUID.randomUUID();
    }

    private Optional<Refusal> checkEligible(RealmModel realm, UserModel user, RoleModel required) {
        if (!user.isEnabled()) {
            return Optional.of(Refusal.DISABLED);
        }
        for (String blocked : policy.blockedRoles()) {
            RoleModel role = realm.getRole(blocked);
            if (role != null && user.hasRole(role)) {
                return Optional.of(Refusal.PRIVILEGED);
            }
        }
        if (!user.hasRole(required)) {
            return Optional.of(Refusal.NOT_CUSTOMER);
        }
        return Optional.empty();
    }
}
