package com.delivery.order.service;

import com.delivery.inventory.service.InventoryService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the stock lifecycle of an order (COR-01): releases the checkout reservation when the order
 * dies before pickup, or commits it when the courier picks the goods up. The order's stock_status
 * marker makes both operations idempotent, so stock is never released or consumed twice.
 */
@Service
public class OrderStockService {
    private final InventoryService inventoryService;

    public OrderStockService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Returns the order's reserved units to available; no-op when stock was already released or committed. */
    @Transactional
    public void release(Order order) {
        if (!order.markStockReleased()) {
            return;
        }
        for (OrderItem item : lockOrder(order)) {
            inventoryService.release(order.getTenantId(), item.getSkuId(), item.getQuantity());
        }
    }

    /** Consumes the order's reservation at pickup; no-op when stock was already released or committed. */
    @Transactional
    public void commit(Order order) {
        if (!order.markStockCommitted()) {
            return;
        }
        for (OrderItem item : lockOrder(order)) {
            inventoryService.commit(order.getTenantId(), item.getSkuId(), item.getQuantity());
        }
    }

    /** Fixed SKU order keeps row-lock acquisition consistent with checkout and avoids deadlocks. */
    private List<OrderItem> lockOrder(Order order) {
        return order.getItems().stream().sorted(Comparator.comparing(OrderItem::getSkuId)).toList();
    }
}
