package com.delivery.notification.repository;

import com.delivery.notification.entity.DeviceToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Push tokens per user profile (V067). Lookups by token go through the SHA-256 hash (unique index). */
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    Optional<DeviceToken> findByTokenHash(String tokenHash);

    /** The recipient's devices, most recently seen first, bounded by {@code limit}. */
    @Query("select d from DeviceToken d where d.tenantId = :tenantId and d.userProfileId = :profileId order by d.lastSeenAt desc")
    List<DeviceToken> findForRecipient(@Param("tenantId") UUID tenantId, @Param("profileId") UUID profileId, Pageable limit);

    /** Unregister: deletes the token only when it belongs to this profile; returns the number of rows removed. */
    @Modifying
    @Query("delete from DeviceToken d where d.tokenHash = :tokenHash and d.tenantId = :tenantId and d.userProfileId = :profileId")
    int deleteOwned(@Param("tokenHash") String tokenHash, @Param("tenantId") UUID tenantId, @Param("profileId") UUID profileId);

    /** Removes a token the provider reported invalid. */
    @Modifying
    @Query("delete from DeviceToken d where d.id = :id")
    int deleteInvalid(@Param("id") UUID id);
}
