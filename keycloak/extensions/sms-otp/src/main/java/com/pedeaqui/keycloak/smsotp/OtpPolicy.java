package com.pedeaqui.keycloak.smsotp;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tunables of the phone OTP login (LAC-18, ADR-0004). Defaults are the agreed values; each can be overridden
 * per realm through the execution's authenticator config (keys = the {@code KEY_*} constants).
 *
 * @param codeTtlSeconds          lifetime of a code (default 300 s)
 * @param maxFailedAttempts       wrong codes per phone (across login sessions) before the phone is locked (5)
 * @param resendCooldownSeconds   minimum gap between two SMS to the same phone (60 s)
 * @param sendsPerPhone           SMS per phone per window (3)
 * @param sendsPerIp              SMS requested from one IP per window (10)
 * @param failuresPerIp           wrong codes from one IP per window before the IP is locked (20)
 * @param windowSeconds           sliding window of the counters above (900 s)
 * @param lockBaseSeconds         first lock duration; doubles for each further lock within 24 h (900 s)
 * @param lockMaxSeconds          cap of the progressive lock (86400 s)
 * @param allowedCountryCodes     country calling codes accepted (258)
 * @param smsTemplate             pt-PT SMS text; {code} and {minutes} are replaced
 * @param blockedRoles            realm roles that may never log in by phone (privileged / staff)
 * @param requiredRole            role every phone-login user must have; granted to users created on first login
 */
public record OtpPolicy(
        int codeTtlSeconds,
        int maxFailedAttempts,
        int resendCooldownSeconds,
        int sendsPerPhone,
        int sendsPerIp,
        int failuresPerIp,
        int windowSeconds,
        int lockBaseSeconds,
        int lockMaxSeconds,
        Set<String> allowedCountryCodes,
        String smsTemplate,
        Set<String> blockedRoles,
        String requiredRole) {

    public static final int CODE_LENGTH = 6;
    public static final String DEFAULT_SMS_TEMPLATE =
            "Pede Aqui: o seu código de acesso é {code}. É válido durante {minutes} minutos. Não o partilhe com ninguém.";
    public static final String DEFAULT_BLOCKED_ROLES = "ADMIN,OPS,FINANCE,SUPPORT,VENDOR_ADMIN,VENDOR_STAFF,COURIER";

    public static final String KEY_CODE_TTL = "codeTtlSeconds";
    public static final String KEY_MAX_FAILED = "maxFailedAttempts";
    public static final String KEY_RESEND_COOLDOWN = "resendCooldownSeconds";
    public static final String KEY_SENDS_PER_PHONE = "sendsPerPhone";
    public static final String KEY_SENDS_PER_IP = "sendsPerIp";
    public static final String KEY_FAILURES_PER_IP = "failuresPerIp";
    public static final String KEY_WINDOW = "windowSeconds";
    public static final String KEY_LOCK_BASE = "lockBaseSeconds";
    public static final String KEY_LOCK_MAX = "lockMaxSeconds";
    public static final String KEY_COUNTRIES = "allowedCountryCodes";
    public static final String KEY_TEMPLATE = "smsTemplate";
    public static final String KEY_BLOCKED_ROLES = "blockedRoles";
    public static final String KEY_REQUIRED_ROLE = "requiredRole";

    public OtpPolicy {
        if (codeTtlSeconds <= 0 || maxFailedAttempts <= 0 || resendCooldownSeconds < 0 || sendsPerPhone <= 0
                || sendsPerIp <= 0 || failuresPerIp <= 0 || windowSeconds <= 0 || lockBaseSeconds <= 0
                || lockMaxSeconds < lockBaseSeconds) {
            throw new IllegalArgumentException("Invalid phone OTP policy");
        }
        if (smsTemplate == null || !smsTemplate.contains("{code}")) {
            throw new IllegalArgumentException("smsTemplate must contain {code}");
        }
        allowedCountryCodes = Set.copyOf(allowedCountryCodes);
        blockedRoles = Set.copyOf(blockedRoles);
    }

    /** The agreed defaults (ADR-0004). */
    public static OtpPolicy defaults() {
        return fromConfig(Map.of());
    }

    /** Builds a policy from authenticator config entries, falling back to the defaults for missing/blank keys. */
    public static OtpPolicy fromConfig(Map<String, String> config) {
        Map<String, String> c = config == null ? Map.of() : config;
        return new OtpPolicy(
                intOf(c, KEY_CODE_TTL, 300),
                intOf(c, KEY_MAX_FAILED, 5),
                intOf(c, KEY_RESEND_COOLDOWN, 60),
                intOf(c, KEY_SENDS_PER_PHONE, 3),
                intOf(c, KEY_SENDS_PER_IP, 10),
                intOf(c, KEY_FAILURES_PER_IP, 20),
                intOf(c, KEY_WINDOW, 900),
                intOf(c, KEY_LOCK_BASE, 900),
                intOf(c, KEY_LOCK_MAX, 86_400),
                csv(c.getOrDefault(KEY_COUNTRIES, PhoneNumbers.MOZAMBIQUE)),
                blankTo(c.get(KEY_TEMPLATE), DEFAULT_SMS_TEMPLATE),
                csv(c.getOrDefault(KEY_BLOCKED_ROLES, DEFAULT_BLOCKED_ROLES)),
                blankTo(c.get(KEY_REQUIRED_ROLE), "CUSTOMER"));
    }

    /** Renders the SMS text; {@code code} replaced by the given value (the real code or a mask). */
    public String renderSms(String code) {
        return smsTemplate.replace("{code}", code).replace("{minutes}", String.valueOf(Math.max(1, codeTtlSeconds / 60)));
    }

    private static int intOf(Map<String, String> c, String key, int fallback) {
        String value = c.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Phone OTP config " + key + " must be an integer");
        }
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
