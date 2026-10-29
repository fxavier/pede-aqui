package com.pedeaqui.keycloak.smsotp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {
    private static final Set<String> MZ = Set.of("258");

    @ParameterizedTest
    @ValueSource(strings = {"+258841234567", "+258 84 123 4567", "00258841234567", "258841234567", "841234567",
            "84 123 4567", "84-123-4567", "(84) 123 4567", " +258.84.123.4567 "})
    void normalisesMozambicanMobileFormsToE164(String raw) {
        assertThat(PhoneNumbers.normalize(raw, MZ)).contains("+258841234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"82", "83", "84", "85", "86", "87"})
    void acceptsEveryMobileOperatorPrefix(String prefix) {
        assertThat(PhoneNumbers.normalize(prefix + "1234567", MZ)).contains("+258" + prefix + "1234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"211234567", "+258211234567", "881234567", "811234567", "8412345678", "84123456",
            "+25884123456", "abc", "", "   ", "+258 84 123 456a", "++258841234567", "+0258841234567"})
    void rejectsLandlinesBadPrefixesWrongLengthsAndGarbage(String raw) {
        assertThat(PhoneNumbers.normalize(raw, MZ)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(PhoneNumbers.normalize(null, MZ)).isEmpty();
    }

    @Test
    void rejectsOtherCountriesUnlessAllowed() {
        assertThat(PhoneNumbers.normalize("+27821234567", MZ)).isEmpty();
        assertThat(PhoneNumbers.normalize("+27821234567", Set.of("258", "27"))).contains("+27821234567");
    }

    @Test
    void rejectsMozambiqueWhenNotAllowed() {
        assertThat(PhoneNumbers.normalize("841234567", Set.of("27"))).isEmpty();
    }

    @Test
    void masksKeepingOnlyPrefixAndLastTwoDigits() {
        assertThat(PhoneNumbers.mask("+258841234567")).isEqualTo("+25884*****67");
        assertThat(PhoneNumbers.mask("+27821234567")).isEqualTo("+278******67");
        assertThat(PhoneNumbers.mask(null)).isEqualTo("***");
        assertThat(PhoneNumbers.mask("+2581")).isEqualTo("***");
    }

    @Test
    void recognisesE164() {
        assertThat(PhoneNumbers.isE164("+258841234567")).isTrue();
        assertThat(PhoneNumbers.isE164("258841234567")).isFalse();
        assertThat(PhoneNumbers.isE164(null)).isFalse();
    }
}
