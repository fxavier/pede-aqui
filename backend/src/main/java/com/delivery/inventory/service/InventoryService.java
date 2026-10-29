package com.delivery.inventory.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.repository.InventoryItemRepository;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains stock update and reservation rules. */
@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryItemRepository repository;
    private final InventoryMapper mapper;
    private final TenantContext tenantContext;
    private final MerchantScopeGuard merchantScopeGuard;
    private final AuditLogService auditLogService;

    public InventoryService(InventoryItemRepository repository, InventoryMapper mapper, TenantContext tenantContext, MerchantScopeGuard merchantScopeGuard, AuditLogService auditLogService) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.merchantScopeGuard = merchantScopeGuard;
        this.auditLogService = auditLogService;
    }

    /** Updates available stock of a tenant-scoped item owned by the caller's vendor (OPS/ADMIN: any vendor); audited. */
    @Transactional
    public InventoryResponse updateAvailable(UUID inventoryItemId, int quantityAvailable) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        // Tenant-scoped lookup: an item of another tenant is reported as not found (never revealed).
        InventoryItem item = repository.findByTenantIdAndId(tenantId, inventoryItemId)
                .orElseThrow(() -> new NotFoundException("Inventory item was not found"));
        ensureCanManageVendor(item.getVendorId());
        int previous = item.getQuantityAvailable();
        item.updateAvailable(quantityAvailable);
        auditLogService.log(AuditActions.INVENTORY_STOCK_UPDATED, "inventory_item", item.getId().toString(),
                "available " + previous + " -> " + quantityAvailable, "SUCCESS");
        return mapper.toResponse(item);
    }

    /**
     * Reserves stock for checkout. The row is locked (SELECT ... FOR UPDATE) rather than relying on
     * @Version alone: stock rows are hot and a version conflict would force re-running the whole
     * checkout transaction, whereas the row lock simply queues the short critical section.
     */
    @Transactional
    public void reserve(UUID tenantId, UUID skuId, int quantity) {
        InventoryItem item = repository.lockByTenantIdAndSkuId(tenantId, skuId).orElseThrow(() -> new NotFoundException("Inventory item was not found"));
        item.reserve(quantity);
    }

    /** Returns reserved units to available under a row lock; a missing row is logged and skipped so cancellation never blocks. */
    @Transactional
    public void release(UUID tenantId, UUID skuId, int quantity) {
        repository.lockByTenantIdAndSkuId(tenantId, skuId).ifPresentOrElse(
                item -> warnIfShort("release", skuId, quantity, item.release(quantity)),
                () -> log.warn("Stock release skipped: no inventory row for sku {}", skuId));
    }

    /** Consumes reserved units under a row lock (order picked up); a missing row is logged and skipped. */
    @Transactional
    public void commit(UUID tenantId, UUID skuId, int quantity) {
        repository.lockByTenantIdAndSkuId(tenantId, skuId).ifPresentOrElse(
                item -> warnIfShort("commit", skuId, quantity, item.commit(quantity)),
                () -> log.warn("Stock commit skipped: no inventory row for sku {}", skuId));
    }

    /**
     * Own-vendor scoping for stock mutations, same rule as the catalog's CatalogAccessGuard: OPS/ADMIN are tenant-wide,
     * merchant users may only touch their own vendor's stock. Uses vendor.api directly (ADR-0002 C6: inventory no
     * longer depends on catalog). Deliberately ADMIN/OPS only — not MerchantScopeGuard.hasTenantWideRole(), which
     * also counts FINANCE.
     */
    private void ensureCanManageVendor(UUID targetVendorId) {
        if (merchantScopeGuard.hasAuthority("ROLE_ADMIN") || merchantScopeGuard.hasAuthority("ROLE_OPS")) {
            return;
        }
        merchantScopeGuard.ensureCallerOwnsVendor(targetVendorId, "You can only manage products of your own vendor");
    }

    private void warnIfShort(String operation, UUID skuId, int requested, int applied) {
        if (applied < requested) {
            log.warn("Stock {} for sku {} applied {} of {} units: reservation was smaller than the order line", operation, skuId, applied, requested);
        }
    }
}
