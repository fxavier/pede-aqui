package com.delivery.rating.controller;

import com.delivery.rating.dto.CustomerRatingStatusResponse;
import com.delivery.rating.dto.RatingResponse;
import com.delivery.rating.dto.SubmitRatingRequest;
import com.delivery.rating.service.RatingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated customer rates their own delivered order (LAC-28); CUSTOMER role only. */
@RestController
@RequestMapping("/api/v1/me/orders/{orderId}/rating")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRatingController {
    private final RatingService service;

    public CustomerRatingController(RatingService service) {
        this.service = service;
    }

    /** Rating state: whether the order can be rated now (and until when) and the existing rating, if any. */
    @GetMapping
    public CustomerRatingStatusResponse status(@PathVariable UUID orderId) {
        return service.status(orderId);
    }

    /** Rates the order once (201); 409 when not delivered, past the window or already rated; 404 when not the caller's. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse submit(@PathVariable UUID orderId, @Valid @RequestBody SubmitRatingRequest request) {
        return service.submit(orderId, request);
    }

    /** Replaces the rating's stars and comment within the edit window; 409 {@code rating_edit_window_closed} after it. */
    @PutMapping
    public RatingResponse edit(@PathVariable UUID orderId, @Valid @RequestBody SubmitRatingRequest request) {
        return service.edit(orderId, request);
    }
}
