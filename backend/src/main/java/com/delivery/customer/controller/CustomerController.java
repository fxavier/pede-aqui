package com.delivery.customer.controller;

import com.delivery.customer.service.CustomerFavoriteService;
import com.delivery.geo.dto.SearchVendorResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer management endpoints including favorites. */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    
    private final CustomerFavoriteService favoriteService;

    public CustomerController(CustomerFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** Add a vendor to current customer's favorites. */
    @PostMapping("/me/favorites/{vendorId}")
    public ResponseEntity<Void> addFavoriteVendor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vendorId) {
        UUID customerId = getCustomerId(jwt);
        favoriteService.addFavoriteVendor(customerId, vendorId);
        return ResponseEntity.ok().build();
    }

    /** Remove a vendor from current customer's favorites. */
    @DeleteMapping("/me/favorites/{vendorId}")
    public ResponseEntity<Void> removeFavoriteVendor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID vendorId) {
        UUID customerId = getCustomerId(jwt);
        favoriteService.removeFavoriteVendor(customerId, vendorId);
        return ResponseEntity.ok().build();
    }

    /** Get current customer's favorite vendors with location data. */
    @GetMapping("/me/favorites")
    public List<SearchVendorResponse> getFavoriteVendors(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "lat", required = false) Double latitude,
            @RequestParam(name = "lng", required = false) Double longitude) {
        UUID customerId = getCustomerId(jwt);
        return favoriteService.getFavoriteVendors(customerId, latitude, longitude);
    }

    private UUID getCustomerId(Jwt jwt) {
        // Extract customer ID from JWT - this assumes the customer ID is stored in the subject claim
        // Adjust this based on your JWT structure
        String subject = jwt.getSubject();
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid customer ID in JWT: " + subject);
        }
    }
}