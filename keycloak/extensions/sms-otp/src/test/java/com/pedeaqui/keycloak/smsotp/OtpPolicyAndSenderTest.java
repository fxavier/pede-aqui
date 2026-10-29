package com.pedeaqui.keycloak.smsotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OtpPolicyAndSenderTest {

    @Test
    void defaultsAreTheAgreedValues() {
        OtpPolicy p = OtpPolicy.defaults();

        assertThat(p.codeTtlSeconds()).isEqualTo(300);
        assertThat(p.maxFailedAttempts()).isEqualTo(5);
        assertThat(p.resendCooldownSeconds()).isEqualTo(60);
        assertThat(p.sendsPerPhone()).isEqualTo(3);
        assertThat(p.windowSeconds()).isEqualTo(900);
        assertThat(p.allowedCountryCodes()).containsExactly("258");
        assertThat(p.requiredRole()).isEqualTo("CUSTOMER");
        assertThat(p.blockedRoles()).contains("ADMIN", "OPS", "FINANCE", "SUPPORT", "VENDOR_ADMIN", "VENDOR_STAFF", "COURIER")
                .doesNotContain("CUSTOMER");
    }

    @Test
    void configOverridesAndBlankFallsBack() {
        OtpPolicy p = OtpPolicy.fromConfig(Map.of(OtpPolicy.KEY_CODE_TTL, "120", OtpPolicy.KEY_MAX_FAILED, " ",
                OtpPolicy.KEY_COUNTRIES, "258, 27"));

        assertThat(p.codeTtlSeconds()).isEqualTo(120);
        assertThat(p.maxFailedAttempts()).isEqualTo(5);
        assertThat(p.allowedCountryCodes()).containsExactlyInAnyOrder("258", "27");
    }

    @Test
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> OtpPolicy.fromConfig(Map.of(OtpPolicy.KEY_CODE_TTL, "abc")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpPolicy.fromConfig(Map.of(OtpPolicy.KEY_TEMPLATE, "no placeholder")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OtpPolicy.fromConfig(Map.of(OtpPolicy.KEY_LOCK_MAX, "10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void smsTemplateIsPortugueseAndCarriesCodeAndMinutes() {
        String text = OtpPolicy.defaults().renderSms("123456");

        assertThat(text).isEqualTo(
                "Pede Aqui: o seu código de acesso é 123456. É válido durante 5 minutos. Não o partilhe com ninguém.");
    }

    @Test
    void stubLogLineNeverContainsTheCodeOrFullNumberUnlessRevealed() {
        OtpPolicy policy = OtpPolicy.defaults();
        SmsMessage message = new SmsMessage("+258841234567", policy.renderSms("654321"), policy.renderSms("******"));

        String masked = LoggingSmsSenderProviderFactory.logLine(message, false);
        assertThat(masked).doesNotContain("654321").doesNotContain("841234567").contains("+25884*****67").contains("******");
        assertThat(message.toString()).doesNotContain("654321").doesNotContain("841234567");

        assertThat(LoggingSmsSenderProviderFactory.logLine(message, true)).contains("654321").doesNotContain("841234567");
    }

    @Test
    void revealIsOnlyEffectiveInDevelopmentMode() {
        assertThat(LoggingSmsSenderProviderFactory.effectiveReveal(true, true)).isTrue();
        assertThat(LoggingSmsSenderProviderFactory.effectiveReveal(true, false)).isFalse();
        assertThat(LoggingSmsSenderProviderFactory.effectiveReveal(false, true)).isFalse();
    }

    @Test
    void hmacSecretIsMandatoryOutsideDevelopmentMode() {
        assertThatThrownBy(() -> PhoneOtpAuthenticatorFactory.resolveSecret(null, false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PhoneOtpAuthenticatorFactory.resolveSecret("too-short", true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(PhoneOtpAuthenticatorFactory.resolveSecret(null, true)).hasSize(32);
        assertThat(PhoneOtpAuthenticatorFactory.resolveSecret("x".repeat(40), false)).hasSize(40);
    }

    @Test
    void phoneUsernameIsOpaqueAndNeverTheNumber() {
        assertThat(PhoneUserResolver.newUsername()).matches("phone-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .isNotEqualTo(PhoneUserResolver.newUsername());
    }
}
