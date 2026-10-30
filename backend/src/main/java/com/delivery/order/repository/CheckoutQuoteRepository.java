package com.delivery.order.repository;

import com.delivery.order.entity.CheckoutQuote;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped persistence of checkout quotes with TTL (W6-A). */
public interface CheckoutQuoteRepository extends JpaRepository<CheckoutQuote, UUID> {
    Optional<CheckoutQuote> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Deletes quotes that expired before {@code cutoff}; returns the number of rows removed. */
    @Modifying
    @Query("DELETE FROM CheckoutQuote q WHERE q.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
