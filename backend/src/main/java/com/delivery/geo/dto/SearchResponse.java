package com.delivery.geo.dto;

import java.util.List;

/** Paginated search response with total count. */
public record SearchResponse(
    List<SearchVendorResponse> vendors,
    Integer totalCount,
    Integer page,
    Integer size
) {}