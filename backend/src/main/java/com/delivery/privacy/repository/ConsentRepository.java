package com.delivery.privacy.repository;

import com.delivery.privacy.entity.Consent;
import com.delivery.privacy.entity.ConsentPurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consents (V071), scoped by tenant and subject profile. */
public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /** Full consent history of the subject, newest first. */
    List<Consent> findByTenantIdAndSubjectProfileIdOrderByGrantedAtDesc(UUID tenantId, UUID subjectProfileId);

    /** The subject's active (not withdrawn) consent for a purpose. */
    Optional<Consent> findByTenantIdAndSubjectProfileIdAndPurposeAndWithdrawnAtIsNull(UUID tenantId, UUID subjectProfileId,
                                                                                     ConsentPurpose purpose);
}
