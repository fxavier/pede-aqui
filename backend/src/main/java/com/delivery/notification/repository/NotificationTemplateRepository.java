package com.delivery.notification.repository;

import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.entity.NotificationTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Platform default ({@code tenantId IS NULL}) and tenant-override notification templates (V067). */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    @Query("""
            select t from NotificationTemplate t
             where t.tenantId is null and t.templateKey = :key and t.channel = :channel and t.locale = :locale""")
    Optional<NotificationTemplate> findDefault(@Param("key") String key, @Param("channel") NotificationChannel channel,
                                               @Param("locale") String locale);

    Optional<NotificationTemplate> findByTenantIdAndTemplateKeyAndChannelAndLocale(UUID tenantId, String templateKey,
                                                                                   NotificationChannel channel, String locale);

    /** Defaults plus this tenant's overrides (config data: a few rows per key, bounded by the key × channel set). */
    @Query("""
            select t from NotificationTemplate t
             where (t.tenantId is null or t.tenantId = :tenantId) and t.locale = :locale
             order by t.templateKey, t.channel""")
    List<NotificationTemplate> findDefaultsAndOverrides(@Param("tenantId") UUID tenantId, @Param("locale") String locale);
}
