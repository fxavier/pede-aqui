package com.delivery.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint mirroring the Keycloak realm {@code passwordPolicy}
 * (length 10, at least one digit, upper-case and lower-case letter, not equal to the username/e-mail),
 * so registration fails fast with a 400 {@code validation_failed} on the {@code password} field
 * instead of reaching Keycloak. Keep in sync with {@code keycloak/delivery-realm.json}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface PasswordPolicy {
    String message() default "Password does not meet the password policy";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Implemented by request records that carry a new password and the username (e-mail) it belongs to. */
    interface Credentials {
        String email();

        String password();
    }
}
