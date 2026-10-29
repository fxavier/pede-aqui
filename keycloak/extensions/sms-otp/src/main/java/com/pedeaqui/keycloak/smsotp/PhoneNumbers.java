package com.pedeaqui.keycloak.smsotp;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalises user-typed phone numbers to E.164 (LAC-18). Default region is Mozambique (+258): a bare national
 * number is read as Mozambican, and Mozambican numbers must be mobile (9 digits, prefixes 82-87) because only
 * mobiles receive the SMS. Other country codes are accepted only when explicitly allowed. Mirrored by the
 * backend's {@code PhoneNumberE164} (separate build; keep both in sync).
 */
public final class PhoneNumbers {
    public static final String MOZAMBIQUE = "258";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern MZ_MOBILE_NATIONAL = Pattern.compile("^8[2-7][0-9]{7}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().\\-/]");

    private PhoneNumbers() {
    }

    /**
     * Returns the E.164 form of {@code raw}, or empty when it is not a valid number for an allowed country.
     * Accepts "+258 84 123 4567", "00258841234567", "258841234567" and "84 123 4567".
     */
    public static Optional<String> normalize(String raw, Set<String> allowedCountryCodes) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (compact.isEmpty() || compact.length() > 20) {
            return Optional.empty();
        }
        String international;
        if (compact.startsWith("+")) {
            international = compact.substring(1);
        } else if (compact.startsWith("00")) {
            international = compact.substring(2);
        } else if (compact.startsWith(MOZAMBIQUE) && compact.length() == MOZAMBIQUE.length() + 9) {
            international = compact; // 258XXXXXXXXX typed without '+'
        } else if (MZ_MOBILE_NATIONAL.matcher(compact).matches()) {
            international = MOZAMBIQUE + compact;
        } else {
            return Optional.empty(); // other bare digits are national numbers that are not Mozambican mobiles
        }
        if (!international.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        String candidate = "+" + international;
        if (!E164.matcher(candidate).matches()) {
            return Optional.empty();
        }
        if (international.startsWith(MOZAMBIQUE)) {
            String national = international.substring(MOZAMBIQUE.length());
            return allowedCountryCodes.contains(MOZAMBIQUE) && MZ_MOBILE_NATIONAL.matcher(national).matches()
                    ? Optional.of(candidate) : Optional.empty();
        }
        return allowedCountryCodes.stream().anyMatch(international::startsWith) ? Optional.of(candidate) : Optional.empty();
    }

    /** True when {@code value} is already a syntactically valid E.164 number. */
    public static boolean isE164(String value) {
        return value != null && E164.matcher(value).matches();
    }

    /** Masks a number for logs and screens: keeps the country/operator prefix and the last two digits. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return "***";
        }
        int keepStart = e164.startsWith("+" + MOZAMBIQUE) ? 6 : 4;
        String start = e164.substring(0, keepStart);
        String end = e164.substring(e164.length() - 2);
        return start + "*".repeat(e164.length() - keepStart - 2) + end;
    }
}
