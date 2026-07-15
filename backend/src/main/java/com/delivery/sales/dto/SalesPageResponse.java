package com.delivery.sales.dto;

import java.util.List;

/** Paginated sales-list payload matching the Spec 002 SalesPage contract. */
public record SalesPageResponse(List<SalesRowResponse> content, int page, int size, long totalElements) {}
