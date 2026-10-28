package com.delivery.inventory.service;

import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.mapper.InventoryMapper;
import com.delivery.inventory.repository.InventoryItemRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains stock update and reservation rules. */
@Service
public class InventoryService {
    private final InventoryItemRepository repository;
    private final InventoryMapper mapper;
    private final TenantContext tenantContext;
    private final CatalogAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    public InventoryService(InventoryItemRepository repository, InventoryMapper mapper, TenantContext tenantContext, CatalogAccessGuard accessGuard, AuditLogService auditLogService) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.accessGuard = accessGuard;
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
        accessGuard.ensureCanManageVendor(item.getVendorId());
        int previous = item.getQuantityAvailable();
        item.updateAvailable(quantityAvailable);
        auditLogService.log(AuditActions.INVENTORY_STOCK_UPDATED, "inventory_item", item.getId().toString(),
                "available " + previous + " -> " + quantityAvailable, "SUCCESS");
        return mapper.toResponse(item);
    }

    /** Reserves stock for checkout using optimistic locking on the inventory row. */
    @Transactional
    public void reserve(UUID tenantId, UUID skuId, int quantity) {
        InventoryItem item = repository.findByTenantIdAndSkuId(tenantId, skuId).orElseThrow(() -> new NotFoundException("Inventory item was not found"));
        item.reserve(quantity);
    }
}
