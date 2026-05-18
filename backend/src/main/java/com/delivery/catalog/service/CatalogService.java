package com.delivery.catalog.service;

import com.delivery.catalog.dto.CreateProductRequest;
import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.mapper.CatalogMapper;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains catalog business rules such as fuel blocking and pharmacy flags. */
@Service
public class CatalogService {
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final CatalogMapper mapper;
    private final TenantContext tenantContext;

    public CatalogService(ProductRepository productRepository, SkuRepository skuRepository, CatalogMapper mapper, TenantContext tenantContext) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Creates a product while blocking prohibited fuel listings. */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        UUID tenantId = tenantId();
        Product product = new Product(UUID.randomUUID(), tenantId, request.vendorId(), request.categoryId(), request.name(), request.description());
        if (request.requiresPrescriptionMetadata()) product.markPrescriptionRequired();
        if (request.prohibitedFuel()) {
            // Fuel itself cannot be transported; safe fuel-station convenience goods are allowed.
            throw new BusinessException("fuel_prohibited", "Fuel cannot be listed or delivered", HttpStatus.BAD_REQUEST);
        }
        return mapper.toProductResponse(productRepository.save(product));
    }

    /** Lists active products for one vendor within the current tenant. */
    @Transactional(readOnly = true)
    public List<ProductResponse> listVendorProducts(UUID vendorId) {
        UUID tenantId = tenantId();
        List<Product> products = productRepository.findByTenantIdAndVendorIdAndStatus(tenantId, vendorId, "ACTIVE");
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        List<Sku> skus = productIds.isEmpty() ? List.of() : skuRepository.findByTenantIdAndProductIdInAndActiveTrue(tenantId, productIds);
        Map<UUID, List<Sku>> skusByProduct = skus.stream().collect(Collectors.groupingBy(Sku::getProductId));
        return products.stream().map(product -> mapper.toProductResponse(product, skusByProduct.getOrDefault(product.getId(), List.of()))).toList();
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
