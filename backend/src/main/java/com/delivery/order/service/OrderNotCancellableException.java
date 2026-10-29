package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.order.entity.OrderStatus;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 409 {@code order_not_cancellable} (LAC-26): the customer can no longer cancel the order (READY_FOR_PICKUP onwards,
 * refunded, legacy rows), or an OPS approval came after the order moved on. The cancellation paths declare it
 * {@code noRollbackFor}, so the automatic closure of a stale open request is committed with the 409.
 */
public class OrderNotCancellableException extends BusinessException {
    private final OrderStatus orderStatus;

    public OrderNotCancellableException(OrderStatus orderStatus) {
        super("order_not_cancellable", "The order can no longer be cancelled in status " + orderStatus, HttpStatus.CONFLICT);
        this.orderStatus = orderStatus;
    }

    @Override
    public Object getDetails() {
        return Map.of("orderStatus", orderStatus.name());
    }
}
