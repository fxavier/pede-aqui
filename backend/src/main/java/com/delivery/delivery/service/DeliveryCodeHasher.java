package com.delivery.delivery.service;

import com.delivery.delivery.config.DeliveryCodeProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Generates delivery PINs and hashes/verifies them with HMAC-SHA256 under a server secret.
 * Rows created before INC-02 carry an unsalted SHA-256 hash and are flagged legacy; only for
 * those does verification fall back to SHA-256. All comparisons are constant-time.
 */
@Component
public class DeliveryCodeHasher {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DeliveryCodeHasher(DeliveryCodeProperties properties) {
        String secret = properties.getCodeHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.delivery.code-hmac-secret must be configured");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /** Returns a uniformly random 6-digit PIN. Uniqueness is not needed: a PIN is checked against one delivery only. */
    public String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    /** Returns the hex HMAC-SHA256 of the PIN. */
    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    /** Constant-time check of a submitted PIN; plain SHA-256 is accepted only for rows flagged legacy. */
    public boolean matches(String code, String storedHash, boolean legacy) {
        if (code == null || storedHash == null) {
            return false;
        }
        byte[] stored = storedHash.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8), stored)) {
            return true;
        }
        return legacy && MessageDigest.isEqual(legacySha256(code).getBytes(StandardCharsets.UTF_8), stored);
    }

    private static String legacySha256(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
