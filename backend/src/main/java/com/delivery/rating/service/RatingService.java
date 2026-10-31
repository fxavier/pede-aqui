package com.delivery.rating.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.api.DeliveryCourierLookup;
import com.delivery.order.api.OrderRatingFacts;
import com.delivery.order.api.OrderRatingFacts.RateableOrder;
import com.delivery.rating.RatingSubmitted;
import com.delivery.rating.config.RatingProperties;
import com.delivery.rating.dto.CustomerRatingStatusResponse;
import com.delivery.rating.dto.RatingResponse;
import com.delivery.rating.dto.SubmitRatingRequest;
import com.delivery.rating.entity.Rating;
import com.delivery.rating.mapper.RatingMapper;
import com.delivery.rating.mapper.RatingMapper.Viewer;
import com.delivery.rating.repository.RatingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer side of ratings (LAC-28): rating state of an order, submit once after DELIVERED within
 * {@code app.rating.window-days}, edit within {@code app.rating.edit-window-hours}. Ownership comes from the JWT
 * (tenant + Keycloak subject → profile id) and is part of every lookup, so another customer's order is a 404.
 * Each write recomputes the vendor/courier aggregates in the same transaction and publishes {@link RatingSubmitted}.
 */
@Service
public class RatingService {
    private final RatingRepository ratingRepository;
    private final OrderRatingFacts orderFacts;
    private final DeliveryCourierLookup courierLookup;
    private final UserAccounts userAccounts;
    private final TenantContext tenantContext;
    private final RatingAggregator aggregator;
    private final RatingMapper mapper;
    private final RatingProperties properties;
    private final ApplicationEventPublisher events;

    public RatingService(RatingRepository ratingRepository, OrderRatingFacts orderFacts, DeliveryCourierLookup courierLookup,
                         UserAccounts userAccounts, TenantContext tenantContext, RatingAggregator aggregator,
                         RatingMapper mapper, RatingProperties properties, ApplicationEventPublisher events) {
        this.ratingRepository = ratingRepository;
        this.orderFacts = orderFacts;
        this.courierLookup = courierLookup;
        this.userAccounts = userAccounts;
        this.tenantContext = tenantContext;
        this.aggregator = aggregator;
        this.mapper = mapper;
        this.properties = properties;
        this.events = events;
    }

    /** Rating state of one of the caller's orders (prompt or existing rating); 404 when the order is not theirs. */
    @Transactional(readOnly = true)
    public CustomerRatingStatusResponse status(UUID orderId) {
        Caller caller = caller();
        RateableOrder order = ownOrder(caller, orderId);
        Optional<Rating> existing = ratingRepository.findByTenantIdAndOrderIdAndRaterUserId(caller.tenantId(), orderId, caller.profileId());
        Instant now = Instant.now();
        Optional<String> reason = RatingRules.notRateableReason(order.status(), order.deliveredAt(), existing.isPresent(),
                now, properties.getWindowDays());
        boolean courierRateable = existing.map(r -> r.getRatedCourierId() != null)
                .orElseGet(() -> courierLookup.courierIdOfOrder(caller.tenantId(), orderId).isPresent());
        return new CustomerRatingStatusResponse(orderId, reason.isEmpty(), reason.orElse(null),
                RatingRules.rateableUntil(order.deliveredAt(), properties.getWindowDays()), courierRateable,
                existing.map(r -> RatingRules.isEditable(r.getCreatedAt(), now, properties.getEditWindowHours())).orElse(false),
                existing.map(r -> RatingRules.editableUntil(r.getCreatedAt(), properties.getEditWindowHours())).orElse(null),
                existing.map(r -> mapper.toResponse(r, Viewer.AUTHOR)).orElse(null));
    }

    /**
     * Rates a delivered order of the caller once: 409 {@code order_not_delivered} / {@code rating_window_closed} /
     * {@code rating_already_submitted}, 400 {@code courier_not_rateable} when courier stars are sent for an order no
     * courier delivered.
     */
    @Transactional
    public RatingResponse submit(UUID orderId, SubmitRatingRequest request) {
        Caller caller = caller();
        RateableOrder order = ownOrder(caller, orderId);
        UUID courierId = courierLookup.courierIdOfOrder(caller.tenantId(), orderId).orElse(null);
        requireCourierRateable(courierId, request.courierStars());
        Instant now = Instant.now();
        RatingRules.notRateableReason(order.status(), order.deliveredAt(), false, now, properties.getWindowDays())
                .ifPresent(code -> { throw conflict(code); });

        aggregator.lock(caller.tenantId(), order.vendorId(), courierId);
        // Re-checked under the vendor lock: a concurrent submit of the same order committed first → clean 409.
        if (ratingRepository.findByTenantIdAndOrderIdAndRaterUserId(caller.tenantId(), orderId, caller.profileId()).isPresent()) {
            throw conflict(RatingRules.ALREADY_SUBMITTED);
        }
        Rating rating = new Rating(UUID.randomUUID(), caller.tenantId(), orderId, caller.profileId(), order.vendorId(),
                courierId, request.vendorStars(), request.courierStars(), RatingRules.normalizeText(request.comment()), now);
        try {
            ratingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException duplicate) {
            // UNIQUE (order_id, rater_user_id): last line of defence behind the locked re-check.
            throw conflict(RatingRules.ALREADY_SUBMITTED);
        }
        aggregator.recompute(caller.tenantId(), order.vendorId(), courierId);
        publish(rating, false, now);
        return mapper.toResponse(rating, Viewer.AUTHOR);
    }

    /** Edits the caller's rating of the order within the edit window: 404 without a rating, 409 {@code rating_edit_window_closed}. */
    @Transactional
    public RatingResponse edit(UUID orderId, SubmitRatingRequest request) {
        Caller caller = caller();
        ownOrder(caller, orderId);
        Rating rating = ratingRepository.findByTenantIdAndOrderIdAndRaterUserId(caller.tenantId(), orderId, caller.profileId())
                .orElseThrow(() -> new NotFoundException("Rating was not found"));
        requireCourierRateable(rating.getRatedCourierId(), request.courierStars());
        Instant now = Instant.now();
        if (!RatingRules.isEditable(rating.getCreatedAt(), now, properties.getEditWindowHours())) {
            throw conflict("rating_edit_window_closed");
        }
        aggregator.lock(caller.tenantId(), rating.getRatedVendorId(), rating.getRatedCourierId());
        rating.edit(request.vendorStars(), request.courierStars(), RatingRules.normalizeText(request.comment()), now);
        ratingRepository.saveAndFlush(rating);
        aggregator.recompute(caller.tenantId(), rating.getRatedVendorId(), rating.getRatedCourierId());
        publish(rating, true, now);
        return mapper.toResponse(rating, Viewer.AUTHOR);
    }

    private void publish(Rating rating, boolean edited, Instant now) {
        events.publishEvent(new RatingSubmitted(rating.getTenantId(), rating.getId(), rating.getOrderId(),
                rating.getRatedVendorId(), rating.getRatedCourierId(), rating.getStars(), rating.getCourierStars(), edited, now));
    }

    private static void requireCourierRateable(UUID courierId, Integer courierStars) {
        if (courierStars != null && courierId == null) {
            throw new BusinessException("courier_not_rateable", "No courier delivered this order", HttpStatus.BAD_REQUEST);
        }
    }

    private RateableOrder ownOrder(Caller caller, UUID orderId) {
        return orderFacts.customerOrder(caller.tenantId(), orderId, caller.profileId())
                .orElseThrow(() -> new NotFoundException("Order was not found"));
    }

    /** The caller's tenant and profile; a customer without a profile in the tenant has no orders here (404). */
    private Caller caller() {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        UUID profileId = tenantContext.currentKeycloakUserId()
                .flatMap(sub -> userAccounts.profileIdOf(tenantId, sub))
                .orElseThrow(() -> new NotFoundException("Order was not found"));
        return new Caller(tenantId, profileId);
    }

    private static BusinessException conflict(String code) {
        String message = switch (code) {
            case RatingRules.ORDER_NOT_DELIVERED -> "Only a delivered order can be rated";
            case RatingRules.WINDOW_CLOSED -> "The rating window for this order has closed";
            case RatingRules.ALREADY_SUBMITTED -> "This order has already been rated";
            default -> "The rating can no longer be edited";
        };
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private record Caller(UUID tenantId, UUID profileId) {}
}
