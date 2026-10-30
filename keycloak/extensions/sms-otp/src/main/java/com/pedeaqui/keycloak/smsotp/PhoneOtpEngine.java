package com.pedeaqui.keycloak.smsotp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Core rules of the phone OTP login (LAC-18, ADR-0004), free of Keycloak types so they are unit-testable:
 * code generation, HMAC hashing at rest, TTL, failed-attempt lock (per phone, across login sessions), resend
 * cooldown, per-phone and per-IP send quotas, per-IP failure lock and progressive lock duration.
 *
 * <p>The code itself is returned once by {@link #issue} (to be sent by SMS) and never stored: the login session
 * keeps only {@link Challenge#hash()} = HMAC-SHA256(secret, challengeId | phone | code). Counter keys are HMACs
 * of the phone/IP, so the shared cache holds no raw phone numbers or addresses.
 */
public final class PhoneOtpEngine {
    private static final String PREFIX = "pa-otp:";
    private static final String HMAC = "HmacSHA256";
    private static final long DAY_SECONDS = 86_400;

    private final OtpPolicy policy;
    private final OtpStore store;
    private final SecretKeySpec key;
    private final Clock clock;
    private final SecureRandom random;

    public PhoneOtpEngine(OtpPolicy policy, OtpStore store, byte[] secret, Clock clock, SecureRandom random) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("OTP HMAC secret must have at least 32 bytes");
        }
        this.policy = policy;
        this.store = store;
        this.key = new SecretKeySpec(secret.clone(), HMAC);
        this.clock = clock;
        this.random = random;
    }

    /** A pending code bound to one phone; lives in the login (authentication) session, never the code itself. */
    public record Challenge(String id, String phone, String hash, long expiresAtEpochSecond) {
    }

    /** Outcome of a send request. */
    public sealed interface SendResult permits Issued, Refused {
    }

    /** A code was generated: send {@code code} by SMS, keep only {@code challenge}. */
    public record Issued(Challenge challenge, String code) implements SendResult {
    }

    /** No SMS may be sent now; {@code retryAfterSeconds} is a hint for the user (0 = unknown). */
    public record Refused(Reason reason, long retryAfterSeconds) implements SendResult {
    }

    /** Why a send or verification was refused. */
    public enum Reason {
        COOLDOWN, PHONE_QUOTA, IP_QUOTA, LOCKED
    }

    /** Outcome of a verification. */
    public sealed interface VerifyResult permits Verified, WrongCode, Expired, Locked {
    }

    /** The code matched; the phone is proven. */
    public record Verified(String phone) implements VerifyResult {
    }

    /** Wrong code; {@code remainingAttempts} before the phone is locked. */
    public record WrongCode(int remainingAttempts) implements VerifyResult {
    }

    /** The code is past its TTL: the user must request a new one. */
    public record Expired() implements VerifyResult {
    }

    /** Phone or IP is locked; the challenge must be discarded. */
    public record Locked(long retryAfterSeconds) implements VerifyResult {
    }

    /** Applies locks, cooldown and quotas and, when allowed, issues a new code for {@code phone}. */
    public SendResult issue(String phone, String ip) {
        long now = now();
        String phoneKey = subjectKey("phone", phone);
        String ipKey = subjectKey("ip", ip);
        long phoneLock = lockRemaining(phoneKey, now);
        long ipLock = lockRemaining(ipKey, now);
        if (phoneLock > 0 || ipLock > 0) {
            return new Refused(Reason.LOCKED, Math.max(phoneLock, ipLock));
        }
        // Read-only checks first so a refused request does not consume the phone's cooldown or quota.
        String cooldownKey = PREFIX + "cool:" + phoneKey;
        Map<String, String> cooldown = store.get(cooldownKey);
        if (policy.resendCooldownSeconds() > 0 && cooldown != null) {
            return new Refused(Reason.COOLDOWN, remaining(cooldown, now, policy.resendCooldownSeconds()));
        }
        if (acquireSlot(PREFIX + "send:" + ipKey, policy.sendsPerIp()) < 0) {
            return new Refused(Reason.IP_QUOTA, 0);
        }
        if (acquireSlot(PREFIX + "send:" + phoneKey, policy.sendsPerPhone()) < 0) {
            return new Refused(Reason.PHONE_QUOTA, 0);
        }
        if (policy.resendCooldownSeconds() > 0) {
            // Atomic gate for concurrent sends to the same phone (the read above can race).
            if (!store.putIfAbsent(cooldownKey, policy.resendCooldownSeconds())) {
                return new Refused(Reason.COOLDOWN, policy.resendCooldownSeconds());
            }
            store.put(cooldownKey, policy.resendCooldownSeconds(),
                    Map.of("until", String.valueOf(now + policy.resendCooldownSeconds())));
        }
        String code = newCode();
        String id = UUID.randomUUID().toString();
        Challenge challenge = new Challenge(id, phone, hash(id, phone, code), now + policy.codeTtlSeconds());
        return new Issued(challenge, code);
    }

    /** Checks {@code submittedCode} against {@code challenge}, counting failures per phone and per IP. */
    public VerifyResult verify(Challenge challenge, String submittedCode, String ip) {
        long now = now();
        String phoneKey = subjectKey("phone", challenge.phone());
        String ipKey = subjectKey("ip", ip);
        long lock = Math.max(lockRemaining(phoneKey, now), lockRemaining(ipKey, now));
        if (lock > 0) {
            return new Locked(lock);
        }
        if (now >= challenge.expiresAtEpochSecond()) {
            return new Expired();
        }
        String candidate = submittedCode == null ? "" : submittedCode.trim();
        if (isWellFormed(candidate) && constantTimeEquals(challenge.hash(), hash(challenge.id(), challenge.phone(), candidate))) {
            clearSlots(PREFIX + "fail:" + phoneKey, policy.maxFailedAttempts());
            return new Verified(challenge.phone());
        }
        int phoneSlot = acquireSlot(PREFIX + "fail:" + phoneKey, policy.maxFailedAttempts());
        int ipSlot = acquireSlot(PREFIX + "fail:" + ipKey, policy.failuresPerIp());
        long locked = 0;
        if (phoneSlot < 0 || phoneSlot == policy.maxFailedAttempts() - 1) {
            locked = Math.max(locked, lock(phoneKey, now));
            clearSlots(PREFIX + "fail:" + phoneKey, policy.maxFailedAttempts());
        }
        if (ipSlot < 0 || ipSlot == policy.failuresPerIp() - 1) {
            locked = Math.max(locked, lock(ipKey, now));
            clearSlots(PREFIX + "fail:" + ipKey, policy.failuresPerIp());
        }
        if (locked > 0) {
            return new Locked(locked);
        }
        return new WrongCode(policy.maxFailedAttempts() - 1 - phoneSlot);
    }

    /** Remaining lock of a phone in seconds (0 = not locked). */
    public long phoneLockRemaining(String phone) {
        return lockRemaining(subjectKey("phone", phone), now());
    }

    /** HMAC of the challenge; the stored form of the code. */
    String hash(String challengeId, String phone, String code) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac((challengeId + "|" + phone + "|" + code).getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean isWellFormed(String code) {
        return code.length() == OtpPolicy.CODE_LENGTH && code.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private String newCode() {
        int bound = (int) Math.pow(10, OtpPolicy.CODE_LENGTH);
        return String.format("%0" + OtpPolicy.CODE_LENGTH + "d", random.nextInt(bound));
    }

    /** Progressive lock: base × 2^(locks in the last 24 h), capped. Returns the applied duration. */
    private long lock(String subjectKey, long now) {
        String countKey = PREFIX + "lockn:" + subjectKey;
        Map<String, String> previous = store.get(countKey);
        int count = previous == null ? 0 : Integer.parseInt(previous.getOrDefault("n", "0"));
        long duration = Math.min((long) policy.lockBaseSeconds() << Math.min(count, 20), policy.lockMaxSeconds());
        store.put(PREFIX + "lock:" + subjectKey, duration, Map.of("until", String.valueOf(now + duration)));
        store.put(countKey, DAY_SECONDS, Map.of("n", String.valueOf(count + 1)));
        return duration;
    }

    private long lockRemaining(String subjectKey, long now) {
        Map<String, String> lock = store.get(PREFIX + "lock:" + subjectKey);
        return lock == null ? 0 : remaining(lock, now, 0);
    }

    private static long remaining(Map<String, String> value, long now, long fallback) {
        if (value == null || value.get("until") == null) {
            return fallback;
        }
        return Math.max(0, Long.parseLong(value.get("until")) - now);
    }

    /** Takes the first free slot of a sliding-window counter (each slot expires on its own); -1 when full. */
    private int acquireSlot(String counterKey, int capacity) {
        for (int i = 0; i < capacity; i++) {
            if (store.putIfAbsent(counterKey + ":" + i, policy.windowSeconds())) {
                return i;
            }
        }
        return -1;
    }

    private void clearSlots(String counterKey, int capacity) {
        for (int i = 0; i < capacity; i++) {
            store.remove(counterKey + ":" + i);
        }
    }

    private String subjectKey(String kind, String value) {
        byte[] digest = mac((kind + ":" + (value == null ? "" : value)).getBytes(StandardCharsets.UTF_8));
        return kind + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
    }

    private byte[] mac(byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }
}
