package com.delivery.geo.controller;

import com.delivery.geo.dto.SearchVendorResponse;
import com.delivery.geo.service.SearchService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes customer vendor discovery endpoints. */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService service;

    public SearchController(SearchService service) { this.service = service; }

    @GetMapping("/vendors")
    public List<SearchVendorResponse> searchVendors(@RequestParam(required = false) Double latitude, @RequestParam(required = false) Double longitude, @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) Boolean available) {
        return service.searchNearby(latitude, longitude, categoryId, available);
    }
}
