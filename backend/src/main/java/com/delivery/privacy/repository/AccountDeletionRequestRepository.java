package com.delivery.privacy.repository;

import com.delivery.privacy.entity.AccountDeletionRequest;
import com.delivery.privacy.entity.AccountDeletionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Account deletion requests (V071); every lookup is scoped by tenant and profile, or by id for the job. */
public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, UUID> {

    /** The subject's newest request, any status. */
    Optional<AccountDeletionRequest> findFirstByTenantIdAndProfileIdOrderByRequestedAtDesc(UUID tenantId, UUID profileId);

    /** All requests of the subject, newest first (export). */
    List<AccountDeletionRequest> findByTenantIdAndProfileIdOrderByRequestedAtDesc(UUID tenantId, UUID profileId);

    /** The subject's open request (PENDING or ERASED), locked for update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AccountDeletionRequest r WHERE r.tenantId = :tenantId AND r.profileId = :profileId AND r.status IN :statuses")
    Optional<AccountDeletionRequest> lockOpen(@Param("tenantId") UUID tenantId, @Param("profileId") UUID profileId,
                                              @Param("statuses") Collection<AccountDeletionStatus> statuses);

    /** Ids of open requests due at {@code now}, oldest first (read-only candidate scan of the job). */
    @Query("SELECT r.id FROM AccountDeletionRequest r WHERE r.status IN :statuses AND r.scheduledFor <= :now ORDER BY r.scheduledFor, r.id")
    List<UUID> findDueIds(@Param("statuses") Collection<AccountDeletionStatus> statuses, @Param("now") Instant now, Limit limit);

    /**
     * Claims one request for the job: locks the row FOR UPDATE SKIP LOCKED and returns its id, or nothing when user
     * traffic (a cancel) holds it — the job then retries on the next run (work-queue pattern).
     */
    @Query(value = "SELECT r.id FROM account_deletion_requests r WHERE r.id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<UUID> claimSkipLocked(@Param("id") UUID id);
}
