package com.delivery.catalog.mapper;

import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.dto.CategoryResponse;
import com.delivery.catalog.dto.SkuResponse;
import com.delivery.catalog.entity.Category;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts catalog entities to DTOs. */
@Component
public class CatalogMapper {
    public ProductResponse toProductResponse(Product product) {
        return toProductResponse(product, List.of());
    }

    public ProductResponse toProductResponse(Product product, List<Sku> skus) {
        List<SkuResponse> skuResponses = skus.stream().map(this::toSkuResponse).toList();
        return new ProductResponse(
                product.getId(),
                product.getVendorId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.isRequiresPrescriptionMetadata(),
                product.isProhibitedFuel(),
                skuResponses);
    }

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getVertical(), category.isActive());
    }

    public SkuResponse toSkuResponse(Sku sku) {
        return new SkuResponse(sku.getId(), sku.getSkuCode(), sku.getName(), sku.getPrice(), sku.isActive());
    }
}
