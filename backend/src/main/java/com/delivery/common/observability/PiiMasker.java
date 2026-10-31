package com.delivery.common.observability;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks common personal-data and secret patterns in log text (LAC-63, W7-C): e-mail addresses, phone numbers
 * (E.164 and Mozambican mobiles with or without the 258 prefix), JWTs and bearer tokens. It is a safety net for the
 * structured log layout ({@link PiiMaskingJsonMembersCustomizer}) — code must still never log personal data on
 * purpose. Free-text postal addresses cannot be recognised reliably and are not covered: never log them.
 */
public final class PiiMasker {
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]{8,}=*");
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![A-Za-z0-9._%+-])([A-Za-z0-9])[A-Za-z0-9._%+-]*@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    /** + country code and 7–14 more digits, optionally separated by single spaces or dashes. */
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("(?<![\\w+*])\\+[1-9](?:[ -]?\\d){7,14}(?![\\d*])");
    /** Mozambican mobile (82–87 + 7 digits), optionally prefixed by 258 / 00258. */
    private static final Pattern MZ_MOBILE = Pattern.compile("(?<![\\w+*])(?:00258|258)?[ -]?8[2-7](?:[ -]?\\d){7}(?![\\d*])");

    private PiiMasker() {
    }

    /** Returns the text with personal data/secrets masked; null and strings without candidates are returned as is. */
    public static String mask(String text) {
        if (text == null || text.isEmpty() || !hasCandidate(text)) {
            return text;
        }
        String masked = JWT.matcher(text).replaceAll("[jwt]");
        masked = BEARER.matcher(masked).replaceAll("$1[redacted]");
        masked = EMAIL.matcher(masked).replaceAll("$1***@***");
        masked = replace(INTERNATIONAL_PHONE, masked);
        masked = replace(MZ_MOBILE, masked);
        return masked;
    }

    private static boolean hasCandidate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '@' || (c >= '0' && c <= '9')) {
                return true;
            }
        }
        return text.contains("eyJ") || text.toLowerCase(java.util.Locale.ROOT).contains("bearer ");
    }

    /** Keeps the last two digits of a phone number: "+258841234567" -> "+**********67". */
    private static String replace(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[^0-9]", "");
            String prefix = matcher.group().trim().startsWith("+") ? "+" : "";
            String tail = digits.length() > 2 ? digits.substring(digits.length() - 2) : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(prefix + "*".repeat(Math.max(0, digits.length() - 2)) + tail));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
