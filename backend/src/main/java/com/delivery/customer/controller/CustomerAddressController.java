package com.delivery.customer.controller;

import com.delivery.customer.dto.CustomerAddressRequest;
import com.delivery.customer.dto.CustomerAddressResponse;
import com.delivery.customer.service.CustomerAddressService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD for the authenticated customer's own delivery addresses (FR-003). */
@RestController
@RequestMapping("/api/v1/customers/me/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerAddressController {
    private final CustomerAddressService service;

    public CustomerAddressController(CustomerAddressService service) { this.service = service; }

    @GetMapping
    public List<CustomerAddressResponse> list() { return service.listMine(); }

    @GetMapping("/{addressId}")
    public CustomerAddressResponse get(@PathVariable UUID addressId) { return service.getMine(addressId); }

    @PostMapping
    public ResponseEntity<CustomerAddressResponse> create(@Valid @RequestBody CustomerAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{addressId}")
    public CustomerAddressResponse update(@PathVariable UUID addressId, @Valid @RequestBody CustomerAddressRequest request) {
        return service.update(addressId, request);
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@PathVariable UUID addressId) {
        service.delete(addressId);
        return ResponseEntity.noContent().build();
    }
}
