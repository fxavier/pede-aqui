package com.delivery.catalog.mapper;

import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.dto.CategoryResponse;
import com.delivery.catalog.dto.SkuResponse;
import com.delivery.catalog.dto.ProductVariationGroupResponse;
import com.delivery.catalog.dto.ProductVariationOptionResponse;
import com.delivery.catalog.entity.Category;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.entity.ProductVariationGroup;
import com.delivery.catalog.entity.ProductVariationOption;
import com.delivery.upload.service.StorageUrlService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts catalog entities to DTOs. */
@Component
public class CatalogMapper {
    private final StorageUrlService storageUrlService;

    public CatalogMapper(StorageUrlService storageUrlService) {
        this.storageUrlService = storageUrlService;
    }

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
                product.getStatus(),
                product.isRequiresPrescriptionMetadata(),
                product.isProhibitedFuel(),
                product.getAttributes(),
                product.getPrimaryImageKey(),
                storageUrlService.presignGet(product.getPrimaryImageKey()),
                product.getImageGallery(),
                skuResponses);
    }

    public CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.withoutChildren(
            category.getId(), 
            category.getName(), 
            category.getVertical(), 
            category.isActive(), 
            category.getParentId()
        );
    }

    public SkuResponse toSkuResponse(Sku sku) {
        return new SkuResponse(sku.getId(), sku.getSkuCode(), sku.getName(), sku.getPrice(), sku.getPendingPrice(), sku.isActive());
    }

    public ProductVariationGroupResponse toProductVariationGroupResponse(ProductVariationGroup group) {
        List<ProductVariationOptionResponse> options = group.getOptions().stream()
            .map(this::toProductVariationOptionResponse)
            .toList();
        
        return new ProductVariationGroupResponse(
            group.getId(),
            group.getProductId(),
            group.getName(),
            null, // description field is missing from entity
            group.getRequired(),
            group.getMinSelections(),
            group.getMaxSelections(),
            group.getDisplayOrder(),
            options
        );
    }

    public ProductVariationOptionResponse toProductVariationOptionResponse(ProductVariationOption option) {
        return new ProductVariationOptionResponse(
            option.getId(),
            option.getGroupId(),
            option.getName(),
            null, // description field is missing from entity
            option.getPriceDelta(),
            option.getAvailable(),
            option.getDisplayOrder()
        );
    }
}
