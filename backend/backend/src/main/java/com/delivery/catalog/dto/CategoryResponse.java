package com.delivery.catalog.dto;

import java.util.List;
import java.util.UUID;

/** Category data exposed through catalog APIs. */

public record CategoryResponse(
    UUID id, 
    String name, 
    String vertical, 
    boolean active, 
    UUID parentId, 
    List<CategoryResponse> children
) {
    public static CategoryResponse withoutChildren(UUID id, String name, String vertical, boolean active, UUID parentId) {
        return new CategoryResponse(id, name, vertical, active, parentId, List.of());
    }
}
