package com.delivery.payment.repository;

import com.delivery.payment.entity.PaymentAttempt;
import com.delivery.payment.entity.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Payment attempt persistence (LAC-31/32): owner lookups, webhook matching and the poller/expiry work queues. */
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    /**
     * Identity of an attempt read WITHOUT loading the entity: callers lock the payment row first and only then load the
     * attempt FOR UPDATE (lock order), so no stale managed copy can exist before the lock.
     */
    interface AttemptKeys {
        UUID getId();
        UUID getTenantId();
        UUID getPaymentId();
        UUID getOrderId();
        String getProvider();
        String getProviderReference();
        String getOurReference();
        PaymentAttemptStatus getStatus();
    }

    Optional<AttemptKeys> findKeysById(UUID id);

    Optional<AttemptKeys> findKeysByOurReference(String ourReference);

    Optional<AttemptKeys> findKeysByProviderAndProviderReference(String provider, String providerReference);
    Optional<PaymentAttempt> findByTenantIdAndPaymentIdAndId(UUID tenantId, UUID paymentId, UUID id);

    /** Newest first. */
    List<PaymentAttempt> findByTenantIdAndPaymentIdOrderByCreatedAtDesc(UUID tenantId, UUID paymentId);

    long countByTenantIdAndPaymentId(UUID tenantId, UUID paymentId);

    boolean existsByPaymentIdAndStatusIn(UUID paymentId, Collection<PaymentAttemptStatus> statuses);

    /** SUCCEEDED attempts of a payment, first success first (duplicate-charge detection). */
    @Query("select a from PaymentAttempt a where a.paymentId = :paymentId "
            + "and a.status = com.delivery.payment.entity.PaymentAttemptStatus.SUCCEEDED order by a.completedAt, a.id")
    List<PaymentAttempt> findSucceededByPayment(@Param("paymentId") UUID paymentId);

    /** Row lock on the attempt, taken after the payment row (lock order: payment -> attempt). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a where a.id = :id")
    Optional<PaymentAttempt> findByIdForUpdate(@Param("id") UUID id);

    /** Poller work queue: open attempts whose next query is due, oldest due first (read-only selection). */
    @Query("select a.id from PaymentAttempt a where a.status in :statuses and a.nextPollAt <= :now order by a.nextPollAt")
    List<UUID> findDueForPoll(@Param("statuses") Collection<PaymentAttemptStatus> statuses, @Param("now") Instant now,
                              Pageable page);

    /** Expiry work queue: INITIATED/PENDING_CUSTOMER attempts created before {@code cutoff}, oldest first. */
    @Query("select a.id from PaymentAttempt a where a.status in :statuses and a.createdAt < :cutoff order by a.createdAt")
    List<UUID> findStaleOpen(@Param("statuses") Collection<PaymentAttemptStatus> statuses, @Param("cutoff") Instant cutoff,
                             Pageable page);

    /** Reconciliation: SUCCEEDED attempts completed since {@code since}. */
    @Query("select a from PaymentAttempt a where a.status = com.delivery.payment.entity.PaymentAttemptStatus.SUCCEEDED "
            + "and a.completedAt >= :since order by a.completedAt")
    List<PaymentAttempt> findSucceededSince(@Param("since") Instant since, Pageable page);

    /** Reconciliation: UNKNOWN attempts created before {@code cutoff}. */
    @Query("select a from PaymentAttempt a where a.status = com.delivery.payment.entity.PaymentAttemptStatus.UNKNOWN "
            + "and a.createdAt < :cutoff order by a.createdAt")
    List<PaymentAttempt> findUnknownBefore(@Param("cutoff") Instant cutoff, Pageable page);

    boolean existsByPaymentIdAndStatus(UUID paymentId, PaymentAttemptStatus status);
}
