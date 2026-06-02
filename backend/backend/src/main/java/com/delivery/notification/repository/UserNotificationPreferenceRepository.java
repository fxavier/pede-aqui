package com.delivery.notification.repository;

import com.delivery.notification.entity.UserNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {

    Optional<UserNotificationPreference> findByTenantIdAndUserProfileId(UUID tenantId, UUID userProfileId);

    @Query("SELECT u FROM UserNotificationPreference u WHERE u.tenantId = :tenantId AND u.pushEnabled = true")
    List<UserNotificationPreference> findUsersWithPushEnabled(@Param("tenantId") UUID tenantId);

    @Query("SELECT u FROM UserNotificationPreference u WHERE u.tenantId = :tenantId AND u.smsEnabled = true")
    List<UserNotificationPreference> findUsersWithSmsEnabled(@Param("tenantId") UUID tenantId);

    @Query("SELECT u FROM UserNotificationPreference u WHERE u.tenantId = :tenantId AND u.emailEnabled = true")
    List<UserNotificationPreference> findUsersWithEmailEnabled(@Param("tenantId") UUID tenantId);

    void deleteByTenantIdAndUserProfileId(UUID tenantId, UUID userProfileId);
}