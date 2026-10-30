package com.delivery.notification.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.notification.dto.DeviceResponse;
import com.delivery.notification.dto.RegisterDeviceRequest;
import com.delivery.notification.entity.DeviceToken;
import com.delivery.notification.repository.DeviceTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's push devices (LAC-48): register/refresh and unregister a token. A token is unique platform-wide (by
 * SHA-256): registering a token already held by another profile moves it to the caller (shared device, new login).
 * Unregister only deletes the caller's own token and is silent otherwise (no existence oracle).
 */
@Service
public class DeviceTokenService {
    private final DeviceTokenRepository repository;
    private final UserAccounts userAccounts;
    private final TenantContext tenantContext;

    public DeviceTokenService(DeviceTokenRepository repository, UserAccounts userAccounts, TenantContext tenantContext) {
        this.repository = repository;
        this.userAccounts = userAccounts;
        this.tenantContext = tenantContext;
    }

    /** Registers or refreshes the caller's token (idempotent). */
    @Transactional
    public DeviceResponse register(RegisterDeviceRequest request) {
        UUID tenantId = tenantId();
        UUID profileId = profileId(tenantId);
        String token = request.token().trim();
        String hash = sha256(token);
        DeviceToken device = repository.findByTokenHash(hash).orElse(null);
        if (device == null) {
            device = repository.save(new DeviceToken(UUID.randomUUID(), tenantId, profileId, request.platform(), token, hash));
        } else {
            device.refresh(tenantId, profileId, request.platform());
        }
        return new DeviceResponse(device.getId(), device.getPlatform(), RecipientMasking.token(token), device.getLastSeenAt());
    }

    /** Deletes the caller's token, if it is theirs (204 either way). */
    @Transactional
    public void unregister(String token) {
        UUID tenantId = tenantId();
        UUID profileId = profileId(tenantId);
        repository.deleteOwned(sha256(token.trim()), tenantId, profileId);
    }

    /** SHA-256 hex of a token: the lookup/unique key, so the raw token is never used in a WHERE clause or log. */
    static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private UUID profileId(UUID tenantId) {
        String subject = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        return userAccounts.profileIdOf(tenantId, subject)
                .orElseThrow(() -> new BusinessException("profile_required", "A user profile is required to register devices",
                        HttpStatus.FORBIDDEN));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
