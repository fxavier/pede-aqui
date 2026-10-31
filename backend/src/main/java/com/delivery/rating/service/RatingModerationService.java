package com.delivery.rating.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.rating.dto.RatingResponse;
import com.delivery.rating.entity.Rating;
import com.delivery.rating.mapper.RatingMapper;
import com.delivery.rating.mapper.RatingMapper.Viewer;
import com.delivery.rating.repository.RatingRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comment moderation (LAC-28) for OPS/ADMIN/SUPPORT, tenant-scoped: list ratings (optionally by vendor / hidden state)
 * and hide or unhide a comment. Hiding never changes the stars or the aggregates. Effective changes are audited; a
 * repeated hide/unhide is an idempotent no-op (no second audit entry).
 */
@Service
public class RatingModerationService {
    private final RatingRepository ratingRepository;
    private final TenantContext tenantContext;
    private final RatingMapper mapper;
    private final AuditLogService auditLogService;

    public RatingModerationService(RatingRepository ratingRepository, TenantContext tenantContext, RatingMapper mapper,
                                   AuditLogService auditLogService) {
        this.ratingRepository = ratingRepository;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
        this.auditLogService = auditLogService;
    }

    /** Keyset page of the tenant's ratings, newest first, optionally filtered. */
    @Transactional(readOnly = true)
    public CursorPage<RatingResponse> list(UUID vendorId, Boolean hidden, int limit, String cursor) {
        UUID tenantId = tenantId();
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        List<Rating> rows = ratingRepository.findModerationPage(tenantId, vendorId, hidden, position.createdAt(), position.id(),
                PageLimits.keysetProbe(effectiveLimit));
        return CursorPage.fromProbe(rows, effectiveLimit, r -> new KeysetCursor(r.getCreatedAt(), r.getId()),
                page -> page.stream().map(r -> mapper.toResponse(r, Viewer.MODERATOR)).toList());
    }

    /** Hides the rating's comment with a reason: 404 outside the tenant, 409 {@code rating_comment_absent} without comment. */
    @Transactional
    public RatingResponse hide(UUID ratingId, String reason) {
        Rating rating = find(ratingId);
        if (rating.getComment() == null) {
            throw new BusinessException("rating_comment_absent", "This rating has no comment to moderate", HttpStatus.CONFLICT);
        }
        if (!rating.isCommentHidden()) {
            rating.hideComment(reason.strip(), Instant.now());
            ratingRepository.saveAndFlush(rating);
            auditLogService.log(AuditActions.RATING_COMMENT_HIDDEN, "rating", rating.getId().toString(),
                    rating.getOrderId().toString(), "SUCCESS");
        }
        return mapper.toResponse(rating, Viewer.MODERATOR);
    }

    /** Makes a hidden comment visible again (404 outside the tenant). */
    @Transactional
    public RatingResponse unhide(UUID ratingId) {
        Rating rating = find(ratingId);
        if (rating.isCommentHidden()) {
            rating.unhideComment(Instant.now());
            ratingRepository.saveAndFlush(rating);
            auditLogService.log(AuditActions.RATING_COMMENT_UNHIDDEN, "rating", rating.getId().toString(),
                    rating.getOrderId().toString(), "SUCCESS");
        }
        return mapper.toResponse(rating, Viewer.MODERATOR);
    }

    private Rating find(UUID ratingId) {
        return ratingRepository.findByTenantIdAndId(tenantId(), ratingId)
                .orElseThrow(() -> new NotFoundException("Rating was not found"));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
