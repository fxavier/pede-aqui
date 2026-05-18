package com.delivery.notification.repository;

import com.delivery.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped notification queries per recipient. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(UUID tenantId, UUID recipientUserId);
    Optional<Notification> findByTenantIdAndId(UUID tenantId, UUID id);
}
