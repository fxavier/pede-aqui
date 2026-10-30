package com.delivery.notification.service;

/**
 * Masks notification recipients for logs and the SUPPORT delivery log (LAC-48): never the full phone, e-mail or push
 * token. Phone {@code +258841234567} → {@code +25884*****67}; e-mail {@code ana.silva@example.com} → {@code a***@e***.com};
 * token → {@code ****} + last 4 characters.
 */
public final class RecipientMasking {
    private RecipientMasking() {
    }

    /** Keeps the first 6 and last 2 characters of an E.164 number. */
    public static String phone(String e164) {
        if (e164 == null || e164.isBlank()) {
            return null;
        }
        String value = e164.trim();
        if (value.length() <= 8) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 6) + "*".repeat(value.length() - 8) + value.substring(value.length() - 2);
    }

    /** Keeps the first letter of the local part and of the domain, plus the top-level domain. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String tld = dot > 0 ? domain.substring(dot) : "";
        return email.charAt(0) + "***@" + domain.charAt(0) + "***" + tld;
    }

    /** Only the last 4 characters of a push token. */
    public static String token(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return "****" + (token.length() <= 4 ? "" : token.substring(token.length() - 4));
    }
}
