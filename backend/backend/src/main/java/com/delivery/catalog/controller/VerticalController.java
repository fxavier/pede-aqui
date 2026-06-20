package com.delivery.catalog.controller;

import com.delivery.catalog.dto.CreateVerticalRequest;
import com.delivery.catalog.dto.UpdateVerticalRequest;
import com.delivery.catalog.dto.VerticalResponse;
import com.delivery.catalog.service.VerticalService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for tenant-scoped business verticals. */
@RestController
@RequestMapping("/api/v1/catalog/verticals")
public class VerticalController {
    private final VerticalService service;

    public VerticalController(VerticalService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPS','VENDOR_ADMIN')")
    public List<VerticalResponse> listAll() { return service.listAll(); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    @ResponseStatus(HttpStatus.CREATED)
    public VerticalResponse create(@Valid @RequestBody CreateVerticalRequest request) {
        return service.create(request);
    }

    @PutMapping("/{verticalId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    public VerticalResponse update(@PathVariable UUID verticalId, @Valid @RequestBody UpdateVerticalRequest request) {
        return service.update(verticalId, request);
    }

    @DeleteMapping("/{verticalId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID verticalId) { service.delete(verticalId); }
}
