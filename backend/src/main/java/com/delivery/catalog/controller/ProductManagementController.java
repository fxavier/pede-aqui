package com.delivery.catalog.controller;

import com.delivery.catalog.dto.PriceUpdateResponse;
import com.delivery.catalog.dto.ProductEditResponse;
import com.delivery.catalog.dto.SetProductImageRequest;
import com.delivery.catalog.dto.UpdatePriceRequest;
import com.delivery.catalog.dto.UpdateProductRequest;
import com.delivery.catalog.service.ProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Backoffice product edit endpoints: attributes, single-SKU price, and image (spec 002 US-1..US-3). */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductManagementController {
    private final ProductService productService;

    public ProductManagementController(ProductService productService) {
        this.productService = productService;
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','OPS','ADMIN')")
    public ProductEditResponse updateProduct(@PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(productId, request);
    }

    @PatchMapping("/{productId}/price")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','OPS','ADMIN')")
    public PriceUpdateResponse updatePrice(@PathVariable UUID productId, @Valid @RequestBody UpdatePriceRequest request) {
        return productService.updatePrice(productId, request.price());
    }

    @PutMapping("/{productId}/image")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','OPS','ADMIN')")
    public ProductEditResponse setImage(@PathVariable UUID productId, @Valid @RequestBody SetProductImageRequest request) {
        return productService.setImage(productId, request.storageKey());
    }

    @DeleteMapping("/{productId}/image")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','OPS','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearImage(@PathVariable UUID productId) {
        productService.clearImage(productId);
    }
}
