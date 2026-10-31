package com.delivery.privacy.service;

import com.delivery.auth.api.UserAccounts.SelfProfile;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.customer.api.CustomerPersonalData;
import com.delivery.notification.api.NotificationPreferences;
import com.delivery.order.api.CustomerOrderPrivacy;
import com.delivery.privacy.dto.DeletionRequestResponse;
import com.delivery.privacy.dto.PersonalDataExport;
import com.delivery.privacy.repository.AccountDeletionRequestRepository;
import com.delivery.privacy.repository.ConsentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Right of access (LAC-63, W7-C): assembles the caller's personal data from every module that holds it into one JSON
 * document and audits the export ({@code PERSONAL_DATA_EXPORTED}). Rate-limited per account by the {@code data-export}
 * group. Only the subject's own data is ever read (subject resolved from the token, never from the request).
 */
@Service
public class DataExportService {
    /** Bumped whenever the export shape changes. */
    public static final int FORMAT_VERSION = 1;

    private final DataSubjectResolver subjects;
    private final NotificationPreferences notificationPreferences;
    private final CustomerPersonalData customerData;
    private final CustomerOrderPrivacy orderPrivacy;
    private final ConsentRepository consentRepository;
    private final AccountDeletionRequestRepository deletionRepository;
    private final AuditLogService auditLogService;
    private Clock clock = Clock.systemUTC();

    public DataExportService(DataSubjectResolver subjects, NotificationPreferences notificationPreferences,
                             CustomerPersonalData customerData, CustomerOrderPrivacy orderPrivacy,
                             ConsentRepository consentRepository, AccountDeletionRequestRepository deletionRepository,
                             AuditLogService auditLogService) {
        this.subjects = subjects;
        this.notificationPreferences = notificationPreferences;
        this.customerData = customerData;
        this.orderPrivacy = orderPrivacy;
        this.consentRepository = consentRepository;
        this.deletionRepository = deletionRepository;
        this.auditLogService = auditLogService;
    }

    /** Test seam for a fixed clock. */
    @Autowired(required = false)
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Builds the caller's export and writes the audit row in the same transaction. */
    @Transactional
    public PersonalDataExport export() {
        SelfProfile p = subjects.currentSubject();
        var prefs = notificationPreferences.get(p.tenantId(), p.profileId());
        PersonalDataExport export = new PersonalDataExport(
                FORMAT_VERSION,
                clock.instant(),
                new PersonalDataExport.Profile(p.profileId(), p.tenantId(), p.email(), p.displayName(), p.fullName(), p.phone(),
                        p.phoneE164(), p.phoneVerifiedAt(), p.nif(), p.dateOfBirth(), p.address(), p.preferredLanguage(),
                        p.status(), p.createdAt(), p.updatedAt()),
                new PersonalDataExport.Preferences(prefs.push(), prefs.sms(), prefs.email()),
                notificationPreferences.deviceCount(p.profileId()),
                customerData.addresses(p.tenantId(), p.profileId()).stream()
                        .map(a -> new PersonalDataExport.Address(a.id(), a.label(), a.recipientName(), a.phone(), a.line1(),
                                a.line2(), a.city(), a.state(), a.postalCode(), a.latitude(), a.longitude(),
                                a.deliveryInstructions(), a.createdAt()))
                        .toList(),
                customerData.favouriteVendorIds(p.tenantId(), p.profileId(), p.keycloakUserId()),
                orderPrivacy.summaries(p.tenantId(), p.profileId()).stream()
                        .map(o -> new PersonalDataExport.OrderSummary(o.id(), o.reference(), o.status(), o.cancellationReason(),
                                o.vendorName(), o.subtotal(), o.fees(), o.taxes(), o.discountTotal(), o.total(),
                                o.deliveryCity(), o.createdAt()))
                        .toList(),
                consentRepository.findByTenantIdAndSubjectProfileIdOrderByGrantedAtDesc(p.tenantId(), p.profileId()).stream()
                        .map(c -> new PersonalDataExport.ConsentRecord(c.getPurpose().name(), c.getVersion(), c.getGrantedAt(),
                                c.getWithdrawnAt()))
                        .toList(),
                deletionRepository.findByTenantIdAndProfileIdOrderByRequestedAtDesc(p.tenantId(), p.profileId()).stream()
                        .map(DeletionRequestResponse::of)
                        .toList());
        auditLogService.logForTenant(p.tenantId(), AuditActions.PERSONAL_DATA_EXPORTED, "user_profile",
                p.profileId().toString(), null, "SUCCESS",
                Map.of("formatVersion", FORMAT_VERSION, "orders", export.orders().size(), "addresses", export.addresses().size()));
        return export;
    }

    /** Suggested download file name for an export generated at {@code at} (UTC date). */
    public static String fileName(Instant at) {
        return "pede-aqui-dados-" + at.toString().substring(0, 10) + ".json";
    }
}
