package com.delivery.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.repository.InventoryItemRepository;
import com.delivery.inventory.service.InventoryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** INC-05 (AS-05): stock updates are tenant-scoped, own-vendor scoped and audited. */
class InventoryServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final InventoryItemRepository repository = mock(InventoryItemRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private InventoryService service;

    @BeforeEach
    void setUp() {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        service = new InventoryService(repository, new InventoryMapper(), tenantContext,
                com.delivery.vendor.MerchantScopeFixtures.legacyFallback(tenantContext), auditLogService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void itemOfAnotherTenantIsNotFound() {
        authenticate("ROLE_ADMIN", Map.of());
        InventoryItem foreign = new InventoryItem(UUID.randomUUID(), UUID.randomUUID(), vendorId, UUID.randomUUID(), 4);
        // A tenant-blind lookup would find it; the service must not use it.
        when(repository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(repository.findByTenantIdAndId(tenantId, foreign.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAvailable(foreign.getId(), 99))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(foreign.getQuantityAvailable()).isEqualTo(4);
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void vendorUserCannotChangeStockOfAnotherVendor() {
        authenticate("ROLE_VENDOR_STAFF", Map.of("vendor_id", UUID.randomUUID().toString()));
        InventoryItem item = item();

        assertThatThrownBy(() -> service.updateAvailable(item.getId(), 9))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(item.getQuantityAvailable()).isEqualTo(4);
    }

    @Test
    void ownVendorUserUpdatesStockAndIsAudited() {
        authenticate("ROLE_VENDOR_ADMIN", Map.of("vendor_id", vendorId.toString()));
        InventoryItem item = item();

        InventoryResponse response = service.updateAvailable(item.getId(), 9);

        assertThat(response.quantityAvailable()).isEqualTo(9);
        verify(auditLogService).log(eq(AuditActions.INVENTORY_STOCK_UPDATED), eq("inventory_item"), eq(item.getId().toString()), eq("available 4 -> 9"), eq("SUCCESS"));
    }

    @Test
    void opsUpdatesAnyVendorStockInTenant() {
        authenticate("ROLE_OPS", Map.of("vendor_id", UUID.randomUUID().toString()));
        InventoryItem item = item();

        assertThat(service.updateAvailable(item.getId(), 0).quantityAvailable()).isZero();
    }

    @Test
    void negativeStockIsRejectedWithoutAudit() {
        authenticate("ROLE_OPS", Map.of());
        InventoryItem item = item();

        assertThatThrownBy(() -> service.updateAvailable(item.getId(), -1)).isInstanceOf(BusinessException.class);
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    private InventoryItem item() {
        InventoryItem item = new InventoryItem(UUID.randomUUID(), tenantId, vendorId, UUID.randomUUID(), 4);
        when(repository.findByTenantIdAndId(tenantId, item.getId())).thenReturn(Optional.of(item));
        return item;
    }

    private void authenticate(String role, Map<String, String> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extraClaims.forEach(builder::claim);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority(role))));
    }
}
