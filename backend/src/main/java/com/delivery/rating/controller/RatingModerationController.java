package com.delivery.rating.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.rating.dto.HideCommentRequest;
import com.delivery.rating.dto.RatingResponse;
import com.delivery.rating.service.RatingModerationService;
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

/** Rating comment moderation (LAC-28), tenant-scoped; OPS, ADMIN and SUPPORT. */
@RestController
@RequestMapping("/api/v1/ops/ratings")
@PreAuthorize("hasAnyRole('OPS','ADMIN','SUPPORT')")
public class RatingModerationController {
    private final RatingModerationService service;

    public RatingModerationController(RatingModerationService service) {
        this.service = service;
    }

    /** Keyset page of ratings, newest first, optionally by {@code vendorId} and {@code hidden}. */
    @GetMapping
    public CursorPage<RatingResponse> list(@RequestParam(required = false) UUID vendorId,
                                           @RequestParam(required = false) Boolean hidden,
                                           @RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
                                           @RequestParam(required = false) String cursor) {
        return service.list(vendorId, hidden, limit, cursor);
    }

    /** Hides the comment (audited); stars and averages are unchanged. Idempotent. */
    @PostMapping("/{ratingId}/hide")
    public RatingResponse hide(@PathVariable UUID ratingId, @Valid @RequestBody HideCommentRequest request) {
        return service.hide(ratingId, request.reason());
    }

    /** Shows a hidden comment again (audited). Idempotent. */
    @PostMapping("/{ratingId}/unhide")
    public RatingResponse unhide(@PathVariable UUID ratingId) {
        return service.unhide(ratingId);
    }
}
