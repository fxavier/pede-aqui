package com.delivery.geo.controller;

import com.delivery.geo.dto.SearchResponse;
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
    public SearchResponse searchVendors(
            @RequestParam(name = "lat", required = false) Double latitude,
            @RequestParam(name = "lng", required = false) Double longitude,
            @RequestParam(required = false, defaultValue = "5000") Integer radius,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Integer maxDeliveryMinutes,
            @RequestParam(required = false, defaultValue = "RELEVANCE") String sort,
            @RequestParam(required = false, defaultValue = "0") Integer page) {
        return service.searchVendorsWithCount(latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);
    }

    /** Legacy endpoint for backward compatibility. */
    @GetMapping("/vendors/legacy")
    public List<SearchVendorResponse> searchVendorsLegacy(@RequestParam(required = false) Double latitude, @RequestParam(required = false) Double longitude, @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) Boolean available) {
        return service.searchNearby(latitude, longitude, categoryId, available);
    }
}
