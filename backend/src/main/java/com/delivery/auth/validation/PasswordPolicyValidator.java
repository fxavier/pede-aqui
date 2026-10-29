package com.delivery.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

/** Validates {@link PasswordPolicy}: reports every unmet rule as a separate error on the {@code password} field. */
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, PasswordPolicy.Credentials> {
    /** Minimum length; matches {@code length(10)} in the realm policy. */
    public static final int MIN_LENGTH = 10;

    @Override
    public boolean isValid(PasswordPolicy.Credentials value, ConstraintValidatorContext context) {
        if (value == null || value.password() == null || value.password().isBlank()) {
            return true; // @NotBlank on the field reports the missing password
        }
        List<String> violations = violations(value.email(), value.password());
        if (violations.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        for (String message : violations) {
            context.buildConstraintViolationWithTemplate(message).addPropertyNode("password").addConstraintViolation();
        }
        return false;
    }

    /** Returns the unmet policy rules for a password (empty when it complies). */
    public static List<String> violations(String username, String password) {
        List<String> violations = new ArrayList<>();
        if (password.length() < MIN_LENGTH) {
            violations.add("must have at least " + MIN_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            violations.add("must contain at least one digit");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            violations.add("must contain at least one upper-case letter");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            violations.add("must contain at least one lower-case letter");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            violations.add("must not be equal to the e-mail");
        }
        return violations;
    }
}
