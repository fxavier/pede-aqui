package com.delivery.rating.mapper;

import com.delivery.rating.dto.RatingResponse;
import com.delivery.rating.entity.Rating;
import org.springframework.stereotype.Component;

/** Maps ratings to their API shape per viewer: what a hidden comment shows depends on who is looking (LAC-28). */
@Component
public class RatingMapper {

    /** Who is looking at the rating. */
    public enum Viewer {
        /** The customer who wrote it: always sees their own comment. */
        AUTHOR,
        /** The rated vendor: a hidden comment is withheld. */
        VENDOR,
        /** OPS/ADMIN/SUPPORT: sees everything, including the hiding reason. */
        MODERATOR
    }

    public RatingResponse toResponse(Rating rating, Viewer viewer) {
        boolean withhold = rating.isCommentHidden() && viewer == Viewer.VENDOR;
        return new RatingResponse(
                rating.getId(),
                rating.getOrderId(),
                rating.getRatedVendorId(),
                rating.getRatedCourierId(),
                rating.getStars(),
                rating.getCourierStars(),
                withhold ? null : rating.getComment(),
                rating.isCommentHidden(),
                viewer == Viewer.MODERATOR ? rating.getCommentHiddenReason() : null,
                rating.getVendorReply(),
                rating.getVendorRepliedAt(),
                rating.getCreatedAt(),
                rating.getUpdatedAt());
    }
}
