package com.delivery.order.dto;

import java.util.List;

/** Paginated admin/vendor order list (same shape as the sales SalesPage contract). */
public record AdminOrderPageResponse(List<AdminOrderResponse> content, int page, int size, long totalElements) {}
