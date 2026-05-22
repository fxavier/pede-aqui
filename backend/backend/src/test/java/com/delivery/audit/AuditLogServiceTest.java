package com.delivery.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.entity.AuditLog;
import com.delivery.common.repository.AuditLogRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLogServiceTest {
    @Test
    void writesSensitiveActionAuditFields() {
        AuditLogRepository repository = org.mockito.Mockito.mock(AuditLogRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("ops-user"));

        AuditLogService service = new AuditLogService(repository, tenantContext);
        service.log("DISPATCH_REASSIGN", "dispatch_job", "job-1", "order-1", "SUCCESS");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo("ops-user");
        assertThat(saved.getTargetType()).isEqualTo("dispatch_job");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
