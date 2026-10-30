package com.delivery.catalog.controller;

import com.delivery.catalog.dto.CategoryResponse;
import com.delivery.catalog.dto.CreateCategoryRequest;
import com.delivery.catalog.dto.UpdateCategoryRequest;
import com.delivery.catalog.dto.CreateProductRequest;
import com.delivery.catalog.dto.CreateSkuRequest;
import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.dto.SkuResponse;
import com.delivery.catalog.dto.VendorPublicResponse;
import com.delivery.catalog.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes catalog endpoints for vendor product creation and customer browsing.
 * Mutations are role-gated (INC-05, AS-04): moderation is OPS/ADMIN only; product, SKU and
 * category writes are VENDOR_ADMIN/OPS/ADMIN, with own-vendor scoping enforced in the service.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) { this.service = service; }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','OPS','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) { return service.createProduct(request); }

    @PostMapping("/skus")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','OPS','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public SkuResponse createSku(@Valid @RequestBody CreateSkuRequest request) { return service.createSku(request); }

    @GetMapping("/vendors/{vendorId}/products")
    public List<ProductResponse> listVendorProducts(@PathVariable UUID vendorId) { return service.listVendorProducts(vendorId); }

    @GetMapping("/vendors/{vendorId}")
    public VendorPublicResponse getVendorPublicProfile(@PathVariable UUID vendorId) { return service.getVendorPublicProfile(vendorId); }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() { return service.listCategories(); }

    @GetMapping("/categories/hierarchical")
    public List<CategoryResponse> listCategoriesHierarchical() { return service.listCategoriesHierarchical(); }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')") // LAC-06: categories are tenant-wide, every merchant shares them
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return service.createCategory(request);
    }

    @GetMapping("/categories/vertical/{vertical}/root")
    public List<CategoryResponse> listRootCategoriesByVertical(@PathVariable String vertical) {
        return service.listRootCategoriesByVertical(vertical);
    }

    @GetMapping("/categories/{parentId}/children")
    public List<CategoryResponse> listChildCategories(@PathVariable UUID parentId) {
        return service.listChildCategories(parentId);
    }

    @GetMapping("/categories/{categoryId}")
    public CategoryResponse getCategoryById(@PathVariable UUID categoryId) {
        return service.getCategoryById(categoryId);
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')")
    public CategoryResponse updateCategory(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return service.updateCategory(categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID categoryId) {
        service.deleteCategory(categoryId);
    }

    @PostMapping("/products/{productId}/approve")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')")
    public ProductResponse approveProduct(@PathVariable UUID productId) {
        return service.approveProduct(productId);
    }

    @PostMapping("/products/{productId}/reject")
    @PreAuthorize("hasAnyRole('OPS','ADMIN')")
    public ProductResponse rejectProduct(@PathVariable UUID productId) {
        return service.rejectProduct(productId);
    }
}
