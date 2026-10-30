package com.delivery.notification.repository;

import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.entity.NotificationDelivery;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read side of the notification delivery log (V067); writes go through {@code NotificationDeliveryStore}. */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    /** SUPPORT log: tenant-scoped, every filter optional, newest first (ordering from the pageable). */
    @Query("""
            select d from NotificationDelivery d
             where d.tenantId = :tenantId
               and (:status is null or d.status = :status)
               and (:channel is null or d.channel = :channel)
               and (:recipientProfileId is null or d.recipientProfileId = :recipientProfileId)
               and (:businessReference is null or d.businessReference = :businessReference)""")
    Page<NotificationDelivery> search(@Param("tenantId") UUID tenantId, @Param("status") NotificationDelivery.Status status,
                                      @Param("channel") NotificationChannel channel,
                                      @Param("recipientProfileId") UUID recipientProfileId,
                                      @Param("businessReference") String businessReference, Pageable pageable);
}
