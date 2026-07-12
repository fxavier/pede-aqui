package com.delivery.catalog.service;

import com.delivery.catalog.dto.CategoryResponse;
import com.delivery.catalog.dto.CreateCategoryRequest;
import com.delivery.catalog.dto.UpdateCategoryRequest;
import com.delivery.catalog.dto.CreateProductRequest;
import com.delivery.catalog.dto.CreateSkuRequest;
import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.dto.SkuResponse;
import com.delivery.catalog.dto.ProductVariationGroupResponse;
import com.delivery.catalog.dto.ProductVariationOptionResponse;
import com.delivery.catalog.dto.CreateProductVariationGroupRequest;
import com.delivery.catalog.dto.CreateProductVariationOptionRequest;
import com.delivery.catalog.dto.UpdateProductVariationGroupRequest;
import com.delivery.catalog.dto.UpdateProductVariationOptionRequest;
import com.delivery.catalog.entity.Category;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.entity.ProductVariationGroup;
import com.delivery.catalog.entity.ProductVariationOption;
import com.delivery.catalog.mapper.CatalogMapper;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.catalog.repository.ProductVariationGroupRepository;
import com.delivery.catalog.repository.ProductVariationOptionRepository;
import com.delivery.catalog.dto.VendorPublicResponse;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.repository.InventoryItemRepository;
import com.delivery.upload.service.StorageUrlService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ProductVariationGroupRepository variationGroupRepository;
    private final ProductVariationOptionRepository variationOptionRepository;
    private final CatalogMapper mapper;
    private final TenantContext tenantContext;
    private final VendorRepository vendorRepository;
    private final StorageUrlService storageUrlService;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository, SkuRepository skuRepository, InventoryItemRepository inventoryItemRepository, ProductVariationGroupRepository variationGroupRepository, ProductVariationOptionRepository variationOptionRepository, CatalogMapper mapper, TenantContext tenantContext, VendorRepository vendorRepository, StorageUrlService storageUrlService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.variationGroupRepository = variationGroupRepository;
        this.variationOptionRepository = variationOptionRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.vendorRepository = vendorRepository;
        this.storageUrlService = storageUrlService;
    }

    /** Public vendor profile for the customer storefront; anonymous and cross-tenant by design. */
    @Transactional(readOnly = true)
    public VendorPublicResponse getVendorPublicProfile(UUID vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new BusinessException("vendor_not_found", "Vendor not found", HttpStatus.NOT_FOUND));
        return new VendorPublicResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getDescription(),
                vendor.getAddress(),
                vendor.getRating(),
                vendor.isAvailable(),
                vendor.getEstimatedDeliveryMinutes(),
                storageUrlService.presignGet(vendor.getLogoStorageKey()));
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

    /** Lists products for one vendor. Anonymous: active products only (cross-tenant). Authenticated: all products in tenant (incl. PENDING for admin review). */
    @Transactional(readOnly = true)
    public List<ProductResponse> listVendorProducts(UUID vendorId) {
        Optional<UUID> tenantIdOpt = tenantContext.currentTenantId();
        List<Product> products;
        List<Sku> skus;
        if (tenantIdOpt.isEmpty()) {
            products = productRepository.findByVendorIdAndStatus(vendorId, "ACTIVE");
            List<UUID> productIds = products.stream().map(Product::getId).toList();
            skus = productIds.isEmpty() ? List.of() : skuRepository.findByProductIdInAndActiveTrue(productIds);
        } else {
            UUID tenantId = tenantIdOpt.get();
            products = productRepository.findByTenantIdAndVendorId(tenantId, vendorId);
            List<UUID> productIds = products.stream().map(Product::getId).toList();
            skus = productIds.isEmpty() ? List.of() : skuRepository.findByTenantIdAndProductIdInAndActiveTrue(tenantId, productIds);
        }
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

    /** Lists all active categories. Anonymous and platform admins see all tenants; others see their own. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return tenantContext.currentTenantId()
                .map(id -> categoryRepository.findByTenantIdAndActiveTrue(id).stream().map(mapper::toCategoryResponse).toList())
                .orElseGet(() -> categoryRepository.findByActiveTrue().stream().map(mapper::toCategoryResponse).toList());
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

    /** Gets a category by ID. */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId) {
        UUID tenantId = tenantId();
        Category category = categoryRepository.findByTenantIdAndId(tenantId, categoryId)
            .orElseThrow(() -> new BusinessException("category_not_found", "Category not found", HttpStatus.NOT_FOUND));
        return mapper.toCategoryResponse(category);
    }

    /** Updates an existing category. */
    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        UUID tenantId = tenantId();
        
        Category category = categoryRepository.findByTenantIdAndId(tenantId, categoryId)
            .orElseThrow(() -> new BusinessException("category_not_found", "Category not found", HttpStatus.NOT_FOUND));

        // Validate parent category if specified
        if (request.parentId() != null) {
            if (request.parentId().equals(categoryId)) {
                throw new BusinessException("invalid_parent_category", "Category cannot be parent of itself", HttpStatus.BAD_REQUEST);
            }
            
            Category parentCategory = categoryRepository.findByTenantIdAndId(tenantId, request.parentId())
                .orElseThrow(() -> new BusinessException("parent_category_not_found", "Parent category not found", HttpStatus.NOT_FOUND));
            
            if (!parentCategory.isActive()) {
                throw new BusinessException("parent_category_inactive", "Parent category must be active", HttpStatus.BAD_REQUEST);
            }

            // Check for circular dependency
            if (wouldCreateCircularDependency(categoryId, request.parentId(), tenantId)) {
                throw new BusinessException("circular_dependency", "Update would create circular dependency", HttpStatus.BAD_REQUEST);
            }
        }

        category.updateProfile(request.name(), request.vertical(), request.parentId());
        category.setActive(request.active());
        
        return mapper.toCategoryResponse(categoryRepository.save(category));
    }

    /** Deletes a category (soft delete by setting active = false). */
    @Transactional
    public void deleteCategory(UUID categoryId) {
        UUID tenantId = tenantId();
        
        Category category = categoryRepository.findByTenantIdAndId(tenantId, categoryId)
            .orElseThrow(() -> new BusinessException("category_not_found", "Category not found", HttpStatus.NOT_FOUND));

        // Check if category has active children
        List<Category> activeChildren = categoryRepository.findByTenantIdAndParentIdAndActiveTrue(tenantId, categoryId);
        if (!activeChildren.isEmpty()) {
            throw new BusinessException("category_has_children", "Cannot delete category with active children", HttpStatus.BAD_REQUEST);
        }

        // Check if category is used by products
        List<Product> products = productRepository.findByTenantIdAndCategoryId(tenantId, categoryId);
        if (!products.isEmpty()) {
            throw new BusinessException("category_has_products", "Cannot delete category with associated products", HttpStatus.BAD_REQUEST);
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    private boolean wouldCreateCircularDependency(UUID categoryId, UUID newParentId, UUID tenantId) {
        UUID currentParent = newParentId;
        
        while (currentParent != null) {
            if (currentParent.equals(categoryId)) {
                return true;
            }
            
            Category parent = categoryRepository.findByTenantIdAndId(tenantId, currentParent).orElse(null);
            currentParent = parent != null ? parent.getParentId() : null;
        }
        
        return false;
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

    /** Approves a product for listing (changes status from PENDING to ACTIVE). */
    @Transactional
    public ProductResponse approveProduct(UUID productId) {
        UUID tenantId = tenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, productId)
            .orElseThrow(() -> new BusinessException("product_not_found", "Product not found", HttpStatus.NOT_FOUND));
        
        if (!product.isPending()) {
            throw new BusinessException("product_not_pending", "Product is not in PENDING status", HttpStatus.BAD_REQUEST);
        }
        
        product.approve();
        return mapper.toProductResponse(productRepository.save(product));
    }

    /** Rejects a product (changes status from PENDING to REJECTED). */
    @Transactional
    public ProductResponse rejectProduct(UUID productId) {
        UUID tenantId = tenantId();
        Product product = productRepository.findByTenantIdAndId(tenantId, productId)
            .orElseThrow(() -> new BusinessException("product_not_found", "Product not found", HttpStatus.NOT_FOUND));
        
        if (!product.isPending()) {
            throw new BusinessException("product_not_pending", "Product is not in PENDING status", HttpStatus.BAD_REQUEST);
        }
        
        product.reject();
        return mapper.toProductResponse(productRepository.save(product));
    }

    // Product Variation Groups (Families) Management
    
    /** Creates a new product variation group (family). */
    @Transactional
    public ProductVariationGroupResponse createProductVariationGroup(CreateProductVariationGroupRequest request) {
        UUID tenantId = tenantId();
        
        // Validate product exists
        productRepository.findByTenantIdAndId(tenantId, request.productId())
            .orElseThrow(() -> new BusinessException("product_not_found", "Product not found", HttpStatus.NOT_FOUND));
        
        if (request.maxSelections() < request.minSelections()) {
            throw new BusinessException("invalid_selection_limits", "Max selections must be >= min selections", HttpStatus.BAD_REQUEST);
        }
        
        ProductVariationGroup group = new ProductVariationGroup(UUID.randomUUID(), tenantId, request.productId(), request.name());
        group.updateProfile(request.name(), request.description(), request.required(), request.minSelections(), request.maxSelections(), request.displayOrder());
        
        ProductVariationGroup saved = variationGroupRepository.save(group);
        return mapper.toProductVariationGroupResponse(saved);
    }
    
    /** Lists product variation groups for a product. */
    @Transactional(readOnly = true)
    public List<ProductVariationGroupResponse> listProductVariationGroups(UUID productId) {
        UUID tenantId = tenantId();
        
        // Validate product exists
        productRepository.findByTenantIdAndId(tenantId, productId)
            .orElseThrow(() -> new BusinessException("product_not_found", "Product not found", HttpStatus.NOT_FOUND));
        
        List<ProductVariationGroup> groups = variationGroupRepository.findByTenantIdAndProductIdOrderByDisplayOrderAsc(tenantId, productId);
        return groups.stream().map(mapper::toProductVariationGroupResponse).toList();
    }
    
    /** Gets a product variation group by ID. */
    @Transactional(readOnly = true)
    public ProductVariationGroupResponse getProductVariationGroupById(UUID groupId) {
        UUID tenantId = tenantId();
        ProductVariationGroup group = variationGroupRepository.findByTenantIdAndId(tenantId, groupId)
            .orElseThrow(() -> new BusinessException("variation_group_not_found", "Product variation group not found", HttpStatus.NOT_FOUND));
        return mapper.toProductVariationGroupResponse(group);
    }
    
    /** Updates a product variation group. */
    @Transactional
    public ProductVariationGroupResponse updateProductVariationGroup(UUID groupId, UpdateProductVariationGroupRequest request) {
        UUID tenantId = tenantId();
        
        ProductVariationGroup group = variationGroupRepository.findByTenantIdAndId(tenantId, groupId)
            .orElseThrow(() -> new BusinessException("variation_group_not_found", "Product variation group not found", HttpStatus.NOT_FOUND));
            
        if (request.maxSelections() < request.minSelections()) {
            throw new BusinessException("invalid_selection_limits", "Max selections must be >= min selections", HttpStatus.BAD_REQUEST);
        }
        
        group.updateProfile(request.name(), request.description(), request.required(), request.minSelections(), request.maxSelections(), request.displayOrder());
        
        ProductVariationGroup saved = variationGroupRepository.save(group);
        return mapper.toProductVariationGroupResponse(saved);
    }
    
    /** Deletes a product variation group and its options. */
    @Transactional
    public void deleteProductVariationGroup(UUID groupId) {
        UUID tenantId = tenantId();
        
        ProductVariationGroup group = variationGroupRepository.findByTenantIdAndId(tenantId, groupId)
            .orElseThrow(() -> new BusinessException("variation_group_not_found", "Product variation group not found", HttpStatus.NOT_FOUND));
        
        // Delete all options first
        variationOptionRepository.deleteByTenantIdAndGroupId(tenantId, groupId);
        
        // Delete the group
        variationGroupRepository.delete(group);
    }
    
    // Product Variation Options Management
    
    /** Creates a new product variation option. */
    @Transactional
    public ProductVariationOptionResponse createProductVariationOption(CreateProductVariationOptionRequest request) {
        UUID tenantId = tenantId();
        
        // Validate group exists
        variationGroupRepository.findByTenantIdAndId(tenantId, request.groupId())
            .orElseThrow(() -> new BusinessException("variation_group_not_found", "Product variation group not found", HttpStatus.NOT_FOUND));
        
        ProductVariationOption option = new ProductVariationOption(UUID.randomUUID(), tenantId, request.groupId(), request.name());
        option.updateProfile(request.name(), request.description(), request.priceModifier(), request.available(), request.displayOrder());
        
        ProductVariationOption saved = variationOptionRepository.save(option);
        return mapper.toProductVariationOptionResponse(saved);
    }
    
    /** Lists product variation options for a group. */
    @Transactional(readOnly = true)
    public List<ProductVariationOptionResponse> listProductVariationOptions(UUID groupId) {
        UUID tenantId = tenantId();
        
        // Validate group exists
        variationGroupRepository.findByTenantIdAndId(tenantId, groupId)
            .orElseThrow(() -> new BusinessException("variation_group_not_found", "Product variation group not found", HttpStatus.NOT_FOUND));
        
        List<ProductVariationOption> options = variationOptionRepository.findByTenantIdAndGroupIdOrderByDisplayOrderAsc(tenantId, groupId);
        return options.stream().map(mapper::toProductVariationOptionResponse).toList();
    }
    
    /** Gets a product variation option by ID. */
    @Transactional(readOnly = true)
    public ProductVariationOptionResponse getProductVariationOptionById(UUID optionId) {
        UUID tenantId = tenantId();
        ProductVariationOption option = variationOptionRepository.findByTenantIdAndId(tenantId, optionId)
            .orElseThrow(() -> new BusinessException("variation_option_not_found", "Product variation option not found", HttpStatus.NOT_FOUND));
        return mapper.toProductVariationOptionResponse(option);
    }
    
    /** Updates a product variation option. */
    @Transactional
    public ProductVariationOptionResponse updateProductVariationOption(UUID optionId, UpdateProductVariationOptionRequest request) {
        UUID tenantId = tenantId();
        
        ProductVariationOption option = variationOptionRepository.findByTenantIdAndId(tenantId, optionId)
            .orElseThrow(() -> new BusinessException("variation_option_not_found", "Product variation option not found", HttpStatus.NOT_FOUND));
        
        option.updateProfile(request.name(), request.description(), request.priceModifier(), request.available(), request.displayOrder());
        
        ProductVariationOption saved = variationOptionRepository.save(option);
        return mapper.toProductVariationOptionResponse(saved);
    }
    
    /** Deletes a product variation option. */
    @Transactional
    public void deleteProductVariationOption(UUID optionId) {
        UUID tenantId = tenantId();
        
        ProductVariationOption option = variationOptionRepository.findByTenantIdAndId(tenantId, optionId)
            .orElseThrow(() -> new BusinessException("variation_option_not_found", "Product variation option not found", HttpStatus.NOT_FOUND));
        
        variationOptionRepository.delete(option);
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
