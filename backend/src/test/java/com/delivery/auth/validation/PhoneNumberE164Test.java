package com.delivery.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberE164Test {

    @ParameterizedTest
    @ValueSource(strings = {"+258841234567", "+258 84 123 4567", "00258841234567", "258841234567", "841234567", "(84) 123-4567"})
    void normalisesMozambicanMobilesToE164(String raw) {
        assertThat(PhoneNumberE164.normalize(raw)).contains("+258841234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+258211234567", "+258881234567", "+25884123456", "+2588412345678", "211234567", "abc", "", "+0123456789"})
    void rejectsLandlinesBadPrefixesAndGarbage(String raw) {
        assertThat(PhoneNumberE164.normalize(raw)).isEmpty();
    }

    @Test
    void validatesCanonicalForm() {
        assertThat(PhoneNumberE164.isValid("+258871234567")).isTrue();
        assertThat(PhoneNumberE164.isValid("+27821234567")).isTrue();
        assertThat(PhoneNumberE164.isValid("258871234567")).isFalse();
        assertThat(PhoneNumberE164.isValid("+258 87 123 4567")).isFalse();
        assertThat(PhoneNumberE164.isValid(null)).isFalse();
    }

    @Test
    void masksForLogs() {
        assertThat(PhoneNumberE164.mask("+258841234567")).isEqualTo("+25884*****67");
        assertThat(PhoneNumberE164.mask(null)).isEqualTo("***");
    }
}
