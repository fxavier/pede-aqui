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
import com.delivery.vendor.api.MerchantScopeGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The merchant side of ratings (LAC-28): a merchant user lists its own vendor's ratings (hidden comments withheld) and
 * a VENDOR_ADMIN replies once per rating. The vendor comes from {@link MerchantScopeGuard} (merchant_users), never
 * from the request; a rating of another vendor is a 404.
 */
@Service
public class VendorRatingService {
    private final RatingRepository ratingRepository;
    private final MerchantScopeGuard merchantScopeGuard;
    private final TenantContext tenantContext;
    private final RatingMapper mapper;
    private final AuditLogService auditLogService;

    public VendorRatingService(RatingRepository ratingRepository, MerchantScopeGuard merchantScopeGuard,
                               TenantContext tenantContext, RatingMapper mapper, AuditLogService auditLogService) {
        this.ratingRepository = ratingRepository;
        this.merchantScopeGuard = merchantScopeGuard;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
        this.auditLogService = auditLogService;
    }

    /** Keyset page of the caller's vendor's ratings, newest first ({@code limit} clamped to 1..200). */
    @Transactional(readOnly = true)
    public CursorPage<RatingResponse> listOwn(int limit, String cursor) {
        UUID tenantId = tenantId();
        UUID vendorId = merchantScopeGuard.requireOwnVendorId(tenantId);
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        List<Rating> rows = ratingRepository.findVendorPage(tenantId, vendorId, position.createdAt(), position.id(),
                PageLimits.keysetProbe(effectiveLimit));
        return CursorPage.fromProbe(rows, effectiveLimit, r -> new KeysetCursor(r.getCreatedAt(), r.getId()),
                page -> page.stream().map(r -> mapper.toResponse(r, Viewer.VENDOR)).toList());
    }

    /** Records the vendor's single reply: 404 for another vendor's rating, 409 {@code rating_reply_exists} on a second reply. */
    @Transactional
    public RatingResponse reply(UUID ratingId, String reply) {
        UUID tenantId = tenantId();
        UUID vendorId = merchantScopeGuard.requireOwnVendorId(tenantId);
        Rating rating = ratingRepository.findByTenantIdAndId(tenantId, ratingId)
                .filter(r -> vendorId.equals(r.getRatedVendorId()))
                .orElseThrow(() -> new NotFoundException("Rating was not found"));
        if (rating.hasVendorReply()) {
            throw new BusinessException("rating_reply_exists", "This rating already has a reply", HttpStatus.CONFLICT);
        }
        String text = RatingRules.normalizeText(reply);
        if (text == null) {
            throw new BusinessException("validation_failed", "The reply must not be blank", HttpStatus.BAD_REQUEST);
        }
        rating.reply(text, Instant.now());
        ratingRepository.saveAndFlush(rating);
        auditLogService.log(AuditActions.RATING_VENDOR_REPLIED, "rating", rating.getId().toString(),
                rating.getOrderId().toString(), "SUCCESS");
        return mapper.toResponse(rating, Viewer.VENDOR);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
