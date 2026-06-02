package com.delivery.catalog.service;

import com.delivery.catalog.dto.CategoryResponse;
import com.delivery.catalog.dto.CreateCategoryRequest;
import com.delivery.catalog.dto.CreateProductRequest;
import com.delivery.catalog.dto.CreateSkuRequest;
import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.dto.SkuResponse;
import com.delivery.catalog.entity.Category;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.mapper.CatalogMapper;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.repository.InventoryItemRepository;
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
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final CatalogMapper mapper;
    private final TenantContext tenantContext;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository, SkuRepository skuRepository, InventoryItemRepository inventoryItemRepository, CatalogMapper mapper, TenantContext tenantContext) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.inventoryItemRepository = inventoryItemRepository;
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
        
        // Set vertical-specific attributes
        if (request.attributes() != null) {
            product.setAttributes(request.attributes());
        }
        
        // Set product images
        if (request.primaryImageKey() != null) {
            product.setPrimaryImageKey(request.primaryImageKey());
        }
        if (request.imageGallery() != null) {
            product.setImageGallery(request.imageGallery());
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

    /** Creates a SKU for an existing product and seeds an inventory entry. */
    @Transactional
    public SkuResponse createSku(CreateSkuRequest request) {
        UUID tenantId = tenantId();
        Sku sku = new Sku(UUID.randomUUID(), tenantId, request.productId(), request.skuCode(), request.name(), request.price());
        skuRepository.save(sku);
        InventoryItem inventory = new InventoryItem(UUID.randomUUID(), tenantId, request.vendorId(), sku.getId(), request.initialStock());
        inventoryItemRepository.save(inventory);
        return mapper.toSkuResponse(sku);
    }

    /** Lists all active categories. Platform admins see all tenants; others see their own. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        if (tenantContext.isPlatformAdmin()) {
            return categoryRepository.findByActiveTrue().stream().map(mapper::toCategoryResponse).toList();
        }
        return categoryRepository.findByTenantIdAndActiveTrue(tenantId()).stream().map(mapper::toCategoryResponse).toList();
    }

    /** Lists categories organized in a hierarchical tree structure. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategoriesHierarchical() {
        List<Category> allCategories;
        if (tenantContext.isPlatformAdmin()) {
            allCategories = categoryRepository.findByActiveTrue();
        } else {
            allCategories = categoryRepository.findByTenantIdAndActiveTrue(tenantId());
        }
        return buildCategoryTree(allCategories);
    }

    /** Creates a new category with optional parent. */
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID tenantId = tenantId();
        
        // Validate parent category exists if specified
        if (request.parentId() != null) {
            Category parent = categoryRepository.findByIdAndTenantId(request.parentId(), tenantId)
                .orElseThrow(() -> new BusinessException("parent_category_not_found", "Parent category not found", HttpStatus.BAD_REQUEST));
            
            // Ensure parent is active
            if (!parent.isActive()) {
                throw new BusinessException("parent_category_inactive", "Parent category must be active", HttpStatus.BAD_REQUEST);
            }
        }
        
        Category category = new Category(UUID.randomUUID(), tenantId, request.name(), request.vertical(), request.parentId());
        return mapper.toCategoryResponse(categoryRepository.save(category));
    }

    /** Lists root categories (no parent) for a specific vertical. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listRootCategoriesByVertical(String vertical) {
        UUID tenantId = tenantId();
        List<Category> categories = categoryRepository.findByTenantIdAndVerticalAndParentIdIsNullAndActiveTrue(tenantId, vertical);
        return categories.stream().map(mapper::toCategoryResponse).toList();
    }

    /** Lists child categories for a specific parent category. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listChildCategories(UUID parentId) {
        UUID tenantId = tenantId();
        List<Category> categories = categoryRepository.findByTenantIdAndParentIdAndActiveTrue(tenantId, parentId);
        return categories.stream().map(mapper::toCategoryResponse).toList();
    }

    private List<CategoryResponse> buildCategoryTree(List<Category> allCategories) {
        Map<UUID, List<Category>> childrenByParent = allCategories.stream()
            .filter(cat -> cat.getParentId() != null)
            .collect(Collectors.groupingBy(Category::getParentId));
        
        return allCategories.stream()
            .filter(cat -> cat.getParentId() == null) // Root categories
            .map(cat -> buildCategoryResponse(cat, childrenByParent))
            .toList();
    }

    private CategoryResponse buildCategoryResponse(Category category, Map<UUID, List<Category>> childrenByParent) {
        List<Category> children = childrenByParent.getOrDefault(category.getId(), List.of());
        List<CategoryResponse> childResponses = children.stream()
            .map(child -> buildCategoryResponse(child, childrenByParent))
            .toList();
        
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getVertical(),
            category.isActive(),
            category.getParentId(),
            childResponses
        );
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
