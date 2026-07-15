package com.delivery.sales.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.catalog.entity.Sku;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import com.delivery.order.entity.OrderStatus;
import com.delivery.payment.entity.Payment;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the criteria predicates for the commercial sales search over orders. */
final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {}

    static Specification<Order> tenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    static Specification<Order> createdFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<Order> createdTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    static Specification<Order> status(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Order> vendor(UUID vendorId) {
        return (root, query, cb) -> cb.equal(root.get("vendorId"), vendorId);
    }

    /** Orders whose payment record uses the given provider. */
    static Specification<Order> paymentProvider(String provider) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Payment> payment = subquery.from(Payment.class);
            subquery.select(payment.get("orderId"))
                    .where(cb.equal(payment.get("provider"), provider));
            return root.get("id").in(subquery);
        };
    }

    /** Orders containing at least one snapshot line for the given SKU. */
    static Specification<Order> skuId(UUID skuId) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<OrderItem> item = subquery.from(OrderItem.class);
            subquery.select(item.get("id"))
                    .where(cb.equal(item.get("order"), root), cb.equal(item.get("skuId"), skuId));
            return cb.exists(subquery);
        };
    }

    /** Orders containing at least one snapshot line whose SKU belongs to the given product. */
    static Specification<Order> productId(UUID productId) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<OrderItem> item = subquery.from(OrderItem.class);
            Root<Sku> sku = subquery.from(Sku.class);
            subquery.select(item.get("id"))
                    .where(cb.equal(item.get("order"), root),
                            cb.equal(sku.get("id"), item.get("skuId")),
                            cb.equal(sku.get("productId"), productId));
            return cb.exists(subquery);
        };
    }

    /** Free-text match on the order reference or the customer's display name/email. */
    static Specification<Order> freeText(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate referenceMatch = cb.like(cb.lower(root.get("reference")), pattern);
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<AppUserProfile> profile = subquery.from(AppUserProfile.class);
            subquery.select(profile.get("id"))
                    .where(cb.equal(profile.get("id"), root.get("customerId")),
                            cb.or(cb.like(cb.lower(profile.get("displayName")), pattern),
                                    cb.like(cb.lower(profile.get("email")), pattern)));
            return cb.or(referenceMatch, cb.exists(subquery));
        };
    }
}
