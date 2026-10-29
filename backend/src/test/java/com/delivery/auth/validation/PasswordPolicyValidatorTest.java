package com.delivery.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.auth.dto.CustomerRegistrationRequest;
import com.delivery.tenant.dto.MerchantRegistrationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The registration DTOs enforce the same rules as the Keycloak realm passwordPolicy. */
class PasswordPolicyValidatorTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void compliantPasswordPasses() {
        assertThat(passwordErrors(customer("ana@example.test", "Segredo1234"))).isEmpty();
        assertThat(passwordErrors(merchant("ana@example.test", "Segredo1234"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "Segr1234, must have at least 10 characters",
            "Segredo-segredo, must contain at least one digit",
            "segredo-1234, must contain at least one upper-case letter",
            "SEGREDO-1234, must contain at least one lower-case letter"
    })
    void eachUnmetRuleIsReportedOnThePasswordField(String password, String expected) {
        assertThat(passwordErrors(customer("ana@example.test", password))).containsExactly(expected);
        assertThat(passwordErrors(merchant("ana@example.test", password))).containsExactly(expected);
    }

    @Test
    void passwordEqualToTheEmailIsRejected() {
        assertThat(passwordErrors(customer("Ana1@Example.test", "ana1@example.TEST")))
                .containsExactly("must not be equal to the e-mail");
    }

    @Test
    void oldEightCharacterPasswordNowReportsEveryUnmetRule() {
        assertThat(passwordErrors(customer("ana@example.test", "password")))
                .containsExactlyInAnyOrder("must have at least 10 characters", "must contain at least one digit",
                        "must contain at least one upper-case letter");
    }

    @Test
    void blankPasswordIsLeftToNotBlank() {
        Set<ConstraintViolation<CustomerRegistrationRequest>> violations = VALIDATOR.validate(customer("ana@example.test", ""));
        assertThat(violations).extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
                .containsExactly("NotBlank");
    }

    private static <T> List<String> passwordErrors(T request) {
        return VALIDATOR.validate(request).stream()
                .filter(v -> v.getPropertyPath().toString().equals("password"))
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    private static CustomerRegistrationRequest customer(String email, String password) {
        return new CustomerRegistrationRequest("Ana", "Machava", email, password);
    }

    private static MerchantRegistrationRequest merchant(String email, String password) {
        return new MerchantRegistrationRequest("Loja", "loja", "MZN", null, null, null, null, "MZ", null, null, null, null,
                "Ana", "Machava", email, null, password, null, null);
    }
}
