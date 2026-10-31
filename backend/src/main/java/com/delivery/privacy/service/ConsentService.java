package com.delivery.privacy.service;

import com.delivery.auth.api.UserAccounts.SelfProfile;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.dto.ConsentResponse;
import com.delivery.privacy.entity.Consent;
import com.delivery.privacy.entity.ConsentPurpose;
import com.delivery.privacy.repository.ConsentRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned consents of the data subject (LAC-63, W7-C): terms of service, privacy policy and marketing. Granting
 * records the accepted text version (must be the configured current one, else 409 {@code consent_version_outdated});
 * re-granting the same version is idempotent, a newer version closes the older grant. Withdrawing keeps the row as
 * evidence ({@code withdrawn_at}). Both are audited.
 */
@Service
public class ConsentService {
    private final DataSubjectResolver subjects;
    private final ConsentRepository repository;
    private final AuditLogService auditLogService;
    private final PrivacyProperties properties;

    public ConsentService(DataSubjectResolver subjects, ConsentRepository repository, AuditLogService auditLogService,
                          PrivacyProperties properties) {
        this.subjects = subjects;
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    /** State of every purpose for the caller. */
    @Transactional(readOnly = true)
    public List<ConsentResponse> list() {
        SelfProfile subject = subjects.currentSubject();
        List<Consent> history = repository.findByTenantIdAndSubjectProfileIdOrderByGrantedAtDesc(subject.tenantId(), subject.profileId());
        return Arrays.stream(ConsentPurpose.values())
                .map(purpose -> toResponse(purpose, history.stream().filter(c -> c.getPurpose() == purpose).findFirst()))
                .toList();
    }

    /** Grants the purpose for the given version (current version only). */
    @Transactional
    public ConsentResponse grant(String purposeName, String version) {
        ConsentPurpose purpose = parse(purposeName);
        String current = properties.getConsentVersions().get(purpose);
        if (current == null || !current.equals(version)) {
            throw new BusinessException("consent_version_outdated", "Only the current version of this text can be accepted",
                    HttpStatus.CONFLICT);
        }
        SelfProfile subject = subjects.currentSubject();
        Optional<Consent> active = repository.findByTenantIdAndSubjectProfileIdAndPurposeAndWithdrawnAtIsNull(
                subject.tenantId(), subject.profileId(), purpose);
        if (active.isPresent() && active.get().getVersion().equals(version)) {
            return toResponse(purpose, active);
        }
        Instant now = Instant.now();
        active.ifPresent(previous -> {
            previous.withdraw(now);
            repository.saveAndFlush(previous);
        });
        Consent granted = repository.saveAndFlush(new Consent(UUID.randomUUID(), subject.tenantId(), subject.profileId(), purpose,
                version, now));
        auditLogService.logForTenant(subject.tenantId(), AuditActions.CONSENT_GRANTED, "consent", granted.getId().toString(),
                purpose.name() + ":" + version, "SUCCESS", Map.of("profileId", subject.profileId().toString()));
        return toResponse(purpose, Optional.of(granted));
    }

    /** Withdraws the active consent of the purpose; 404 {@code consent_not_found} when none is active. */
    @Transactional
    public void withdraw(String purposeName) {
        ConsentPurpose purpose = parse(purposeName);
        SelfProfile subject = subjects.currentSubject();
        Consent active = repository.findByTenantIdAndSubjectProfileIdAndPurposeAndWithdrawnAtIsNull(
                        subject.tenantId(), subject.profileId(), purpose)
                .orElseThrow(() -> new BusinessException("consent_not_found", "No active consent for this purpose", HttpStatus.NOT_FOUND));
        active.withdraw(Instant.now());
        repository.saveAndFlush(active);
        auditLogService.logForTenant(subject.tenantId(), AuditActions.CONSENT_WITHDRAWN, "consent", active.getId().toString(),
                purpose.name() + ":" + active.getVersion(), "SUCCESS", Map.of("profileId", subject.profileId().toString()));
    }

    private ConsentResponse toResponse(ConsentPurpose purpose, Optional<Consent> latest) {
        String current = properties.getConsentVersions().get(purpose);
        return latest.map(c -> new ConsentResponse(purpose.name(), current, c.getVersion(), c.getGrantedAt(), c.getWithdrawnAt(),
                        c.getWithdrawnAt() == null && c.getVersion().equals(current)))
                .orElseGet(() -> new ConsentResponse(purpose.name(), current, null, null, null, false));
    }

    private static ConsentPurpose parse(String purposeName) {
        try {
            return ConsentPurpose.valueOf(purposeName.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("invalid_consent_purpose",
                    "Unknown consent purpose; expected one of " + Arrays.toString(ConsentPurpose.values()), HttpStatus.BAD_REQUEST);
        }
    }
}
