package com.delivery.order.entity;

/** Stock lifecycle of one order: reserved at checkout, then released (cancel/reject) or committed (pickup) exactly once. */
public enum StockStatus {
    RESERVED,
    RELEASED,
    COMMITTED
}
