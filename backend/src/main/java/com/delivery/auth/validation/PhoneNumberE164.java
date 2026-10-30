package com.delivery.auth.validation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * E.164 phone rules of the backend (LAC-18, ADR-0004). Default region Mozambique (+258): a bare national number is
 * read as Mozambican, and Mozambican numbers must be mobiles (9 digits, prefixes 82-87). Mirrors
 * {@code keycloak/extensions/sms-otp/.../PhoneNumbers} (separate build); keep both in sync.
 */
public final class PhoneNumberE164 {
    public static final String MOZAMBIQUE = "258";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern MZ_MOBILE_NATIONAL = Pattern.compile("^8[2-7][0-9]{7}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().\\-/]");

    private PhoneNumberE164() {
    }

    /** Normalises user input ("+258 84 123 4567", "00258…", "84 123 4567") to E.164, or empty when invalid. */
    public static Optional<String> normalize(String raw) {
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
        String candidate = "+" + international;
        return isValid(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    /** True for a canonical E.164 number; +258 numbers must also be Mozambican mobiles. */
    public static boolean isValid(String e164) {
        if (e164 == null || !E164.matcher(e164).matches()) {
            return false;
        }
        if (e164.startsWith("+" + MOZAMBIQUE)) {
            return MZ_MOBILE_NATIONAL.matcher(e164.substring(1 + MOZAMBIQUE.length())).matches();
        }
        return true;
    }

    /** Masked form for logs: country/operator prefix and last two digits only. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return "***";
        }
        int keepStart = e164.startsWith("+" + MOZAMBIQUE) ? 6 : 4;
        return e164.substring(0, keepStart) + "*".repeat(e164.length() - keepStart - 2) + e164.substring(e164.length() - 2);
    }
}
