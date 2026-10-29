package com.delivery.notification.repository;

import com.delivery.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides tenant-scoped notification queries per recipient. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId, Pageable pageable);
    Optional<Notification> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Keyset page (LAC-14): the recipient's notifications strictly older than {@code (createdAt, id)}, newest first. */
    @Query("""
            select n from Notification n
             where n.tenantId = :tenantId and n.recipientUserId = :recipientUserId
               and (n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id))
             order by n.createdAt desc, n.id desc""")
    List<Notification> findKeysetPage(@Param("tenantId") UUID tenantId, @Param("recipientUserId") UUID recipientUserId,
                                      @Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable limit);
}
