package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.DispatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DispatchAttemptRepository extends JpaRepository<DispatchAttempt, UUID> {

    List<DispatchAttempt> findByTenantIdAndDispatchJobId(UUID tenantId, UUID dispatchJobId);

    List<DispatchAttempt> findByTenantIdAndCourierId(UUID tenantId, UUID courierId);

    List<DispatchAttempt> findByTenantIdAndDispatchJobIdOrderByAttemptedAtDesc(UUID tenantId, UUID dispatchJobId);

    @Query("SELECT d FROM DispatchAttempt d WHERE d.tenantId = :tenantId AND d.dispatchJobId = :jobId " +
           "AND d.outcome = :outcome")
    List<DispatchAttempt> findByJobAndOutcome(@Param("tenantId") UUID tenantId,
                                              @Param("jobId") UUID dispatchJobId,
                                              @Param("outcome") String outcome);

    @Query("SELECT COUNT(d) FROM DispatchAttempt d WHERE d.tenantId = :tenantId AND d.dispatchJobId = :jobId")
    long countAttemptsForJob(@Param("tenantId") UUID tenantId, @Param("jobId") UUID dispatchJobId);

    @Query("SELECT COUNT(d) FROM DispatchAttempt d WHERE d.tenantId = :tenantId AND d.courierId = :courierId " +
           "AND d.outcome = :outcome")
    long countCourierOutcomes(@Param("tenantId") UUID tenantId,
                              @Param("courierId") UUID courierId,
                              @Param("outcome") String outcome);

    void deleteByTenantIdAndDispatchJobId(UUID tenantId, UUID dispatchJobId);
}