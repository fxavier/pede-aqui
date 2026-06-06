package com.delivery.catalog.controller;

import com.delivery.catalog.dto.CreateProductVariationGroupRequest;
import com.delivery.catalog.dto.CreateProductVariationOptionRequest;
import com.delivery.catalog.dto.ProductVariationGroupResponse;
import com.delivery.catalog.dto.ProductVariationOptionResponse;
import com.delivery.catalog.dto.UpdateProductVariationGroupRequest;
import com.delivery.catalog.dto.UpdateProductVariationOptionRequest;
import com.delivery.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes product variation (families) management endpoints. */
@RestController
@RequestMapping("/api/v1/catalog")
public class ProductVariationController {
    private final CatalogService catalogService;

    public ProductVariationController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // Product Variation Groups (Families)

    @Operation(summary = "Create a product variation group (family)")
    @PostMapping("/product-variation-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariationGroupResponse createProductVariationGroup(@Valid @RequestBody CreateProductVariationGroupRequest request) {
        return catalogService.createProductVariationGroup(request);
    }

    @Operation(summary = "List product variation groups for a product")
    @GetMapping("/products/{productId}/variation-groups")
    public List<ProductVariationGroupResponse> listProductVariationGroups(@PathVariable UUID productId) {
        return catalogService.listProductVariationGroups(productId);
    }

    @Operation(summary = "Get a product variation group by ID")
    @GetMapping("/product-variation-groups/{groupId}")
    public ProductVariationGroupResponse getProductVariationGroupById(@PathVariable UUID groupId) {
        return catalogService.getProductVariationGroupById(groupId);
    }

    @Operation(summary = "Update a product variation group")
    @PutMapping("/product-variation-groups/{groupId}")
    public ProductVariationGroupResponse updateProductVariationGroup(@PathVariable UUID groupId, @Valid @RequestBody UpdateProductVariationGroupRequest request) {
        return catalogService.updateProductVariationGroup(groupId, request);
    }

    @Operation(summary = "Delete a product variation group")
    @DeleteMapping("/product-variation-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductVariationGroup(@PathVariable UUID groupId) {
        catalogService.deleteProductVariationGroup(groupId);
    }

    // Product Variation Options

    @Operation(summary = "Create a product variation option")
    @PostMapping("/product-variation-options")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariationOptionResponse createProductVariationOption(@Valid @RequestBody CreateProductVariationOptionRequest request) {
        return catalogService.createProductVariationOption(request);
    }

    @Operation(summary = "List product variation options for a group")
    @GetMapping("/product-variation-groups/{groupId}/options")
    public List<ProductVariationOptionResponse> listProductVariationOptions(@PathVariable UUID groupId) {
        return catalogService.listProductVariationOptions(groupId);
    }

    @Operation(summary = "Get a product variation option by ID")
    @GetMapping("/product-variation-options/{optionId}")
    public ProductVariationOptionResponse getProductVariationOptionById(@PathVariable UUID optionId) {
        return catalogService.getProductVariationOptionById(optionId);
    }

    @Operation(summary = "Update a product variation option")
    @PutMapping("/product-variation-options/{optionId}")
    public ProductVariationOptionResponse updateProductVariationOption(@PathVariable UUID optionId, @Valid @RequestBody UpdateProductVariationOptionRequest request) {
        return catalogService.updateProductVariationOption(optionId, request);
    }

    @Operation(summary = "Delete a product variation option")
    @DeleteMapping("/product-variation-options/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductVariationOption(@PathVariable UUID optionId) {
        catalogService.deleteProductVariationOption(optionId);
    }
}