package com.delivery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.service.NotificationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    @Test
    void createsNotificationsForAllBackofficeAndMarketplaceRoles() {
        UUID tenantId = UUID.randomUUID();
        NotificationRepository repository = mock(NotificationRepository.class);
        AppUserProfileRepository userRepo = mock(AppUserProfileRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationService service = new NotificationService(repository, userRepo, tenantContext);

        var customer = service.create(UUID.randomUUID(), "CUSTOMER", "ORDER", "Pedido confirmado", "Seu pedido foi confirmado", "PA-1");
        var vendor = service.create(UUID.randomUUID(), "VENDOR_ADMIN", "ORDER", "Novo pedido", "Pedido pronto para preparar", "PA-1");
        var courier = service.create(UUID.randomUUID(), "COURIER", "DISPATCH", "Nova atribuicao", "Entrega disponivel", "DJ-1");
        var admin = service.create(UUID.randomUUID(), "ADMIN", "AUDIT", "Acao sensivel", "Reatribuicao executada", "OPS-1");
        var ops = service.create(UUID.randomUUID(), "OPS", "DISPATCH", "Job reatribuivel", "Reatribuicao necessaria", "DJ-1");

        assertThat(customer.title()).isEqualTo("Pedido confirmado");
        assertThat(vendor.title()).isEqualTo("Novo pedido");
        assertThat(courier.title()).isEqualTo("Nova atribuicao");
        assertThat(admin.title()).isEqualTo("Acao sensivel");
        assertThat(ops.title()).isEqualTo("Job reatribuivel");
    }
}
