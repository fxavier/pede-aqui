package com.delivery.notification.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders notification template text (LAC-48): {@code {name}} placeholders are replaced by the given values; an
 * unknown placeholder renders empty. Placeholders whose name looks secret (PIN, OTP, code, password, token, secret)
 * render only when the template explicitly allows sensitive placeholders — generic templates can never leak a PIN.
 */
public final class TemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]{0,40})}");
    private static final Set<String> SENSITIVE_FRAGMENTS = Set.of("pin", "otp", "code", "password", "token", "secret");

    private TemplateRenderer() {
    }

    /** The placeholder names used in the text, in order of appearance. */
    public static Set<String> placeholders(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** True when a placeholder/parameter name looks like it carries a secret. */
    public static boolean isSensitive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    /**
     * Replaces every placeholder. A sensitive one renders empty unless {@code allowSensitive}; a missing value renders
     * empty. Returns null for null text (templates without a title).
     */
    public static String render(String text, Map<String, String> params, boolean allowSensitive) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = (isSensitive(name) && !allowSensitive) ? "" : params.getOrDefault(name, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
