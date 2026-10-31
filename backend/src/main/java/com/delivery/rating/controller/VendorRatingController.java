package com.delivery.rating.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.rating.dto.RatingResponse;
import com.delivery.rating.dto.VendorReplyRequest;
import com.delivery.rating.service.VendorRatingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ratings of the caller's own vendor (LAC-28): list for merchant users, single reply for VENDOR_ADMIN. */
@RestController
@RequestMapping("/api/v1/vendor/ratings")
public class VendorRatingController {
    private final VendorRatingService service;

    public VendorRatingController(VendorRatingService service) {
        this.service = service;
    }

    /** Keyset page {@code { items, nextCursor }} of the vendor's ratings, newest first; hidden comments withheld. */
    @GetMapping
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN','VENDOR_STAFF')")
    public CursorPage<RatingResponse> list(@RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
                                           @RequestParam(required = false) String cursor) {
        return service.listOwn(limit, cursor);
    }

    /** The vendor's one public reply to a rating; 409 {@code rating_reply_exists} on a second reply. */
    @PostMapping("/{ratingId}/reply")
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public RatingResponse reply(@PathVariable UUID ratingId, @Valid @RequestBody VendorReplyRequest request) {
        return service.reply(ratingId, request.reply());
    }
}
