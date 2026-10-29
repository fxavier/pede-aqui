package com.delivery.cart.service;

import com.delivery.cart.dto.PricingResponse;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.pricing.DeliveryFeeBasis;
import com.delivery.common.pricing.DeliveryFeeQuote;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.pricing.PriceQuote;
import com.delivery.common.pricing.PricingMath;
import com.delivery.geo.api.DeliveryFeeEstimator;
import com.delivery.geo.entity.DeliveryFeeRule;
import com.delivery.geo.repository.DeliveryFeeRuleRepository;
import com.delivery.tenant.entity.FeePolicy;
import com.delivery.tenant.repository.FeePolicyRepository;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Single pricing engine (INC-13, AS-23, FR-021, FR-063) used by cart, checkout, the pricing preview and vendor
 * search, so the delivery fee shown is the one charged. Inputs: the tenant's {@code fee_policies} row, the
 * vendor's active {@code delivery_fee_rules} and the server-side straight-line distance (PostGIS) from the vendor
 * to the destination. Discounts are not computed here: spec-002 promotions/coupons are applied by the callers.
 *
 * <p>Delivery fee, in order: empty cart → 0 (EMPTY_CART); distance known and vendor has active rules → the band
 * fee (DISTANCE_RULE); otherwise the tenant base {@code fee_policies.delivery_fee} (POLICY_BASE), or the platform
 * defaults when the tenant has no policy (PLATFORM_DEFAULT). It is never silently zero for a non-empty cart.
 */
@Service
public class PricingService implements DeliveryFeeEstimator {
    /** Recorded on every order quote; bump when the formula changes. */
    public static final String ENGINE_VERSION = "pricing-v1";
    // Platform defaults = the pre-INC-13 constants, identical to the V048 seed, so tenants without a policy keep today's totals.
    static final BigDecimal DEFAULT_DELIVERY_FEE = new BigDecimal("5.00");
    static final BigDecimal DEFAULT_SERVICE_FEE = new BigDecimal("0.00");
    static final BigDecimal DEFAULT_SERVICE_FEE_RATE = new BigDecimal("0.0500");
    static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.0800");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final Comparator<DeliveryFeeRule> BAND_ORDER =
            Comparator.comparing(DeliveryFeeRule::getMinKm).thenComparing(DeliveryFeeRule::getId);

    private final FeePolicyRepository feePolicyRepository;
    private final DeliveryFeeRuleRepository deliveryFeeRuleRepository;
    private final VendorRepository vendorRepository;

    public PricingService(FeePolicyRepository feePolicyRepository, DeliveryFeeRuleRepository deliveryFeeRuleRepository,
                          VendorRepository vendorRepository) {
        this.feePolicyRepository = feePolicyRepository;
        this.deliveryFeeRuleRepository = deliveryFeeRuleRepository;
        this.vendorRepository = vendorRepository;
    }

    /**
     * Full quote for a vendor cart subtotal delivered to {@code destination} (null = unknown, e.g. the cart before
     * an address is chosen or the legacy checkout without addressId: the tenant base fee applies).
     */
    public PriceQuote quote(UUID tenantId, UUID vendorId, BigDecimal subtotal, GeoPoint destination) {
        Optional<FeePolicy> policy = policyOf(tenantId);
        Rates rates = rates(policy);
        if (subtotal.signum() == 0) {
            DeliveryFeeQuote none = new DeliveryFeeQuote(ZERO_MONEY, DeliveryFeeBasis.EMPTY_CART, null,
                    policy.map(FeePolicy::getId).orElse(null), policy.map(FeePolicy::getVersion).orElse(null), null, null);
            return new PriceQuote(subtotal, ZERO_MONEY, ZERO_MONEY, ZERO_MONEY, PricingMath.orderTotal(subtotal, ZERO_MONEY, ZERO_MONEY, ZERO_MONEY),
                    rates.serviceFeeRate(), rates.taxRate(), none, ENGINE_VERSION);
        }
        BigDecimal distanceKm = destination == null || vendorId == null ? null
                : distancesKm(List.of(vendorId), destination).get(vendorId);
        List<DeliveryFeeRule> rules = distanceKm == null || tenantId == null ? List.of()
                : deliveryFeeRuleRepository.findByTenantIdAndVendorIdAndActive(tenantId, vendorId, true);
        DeliveryFeeQuote delivery = deliveryFee(policy, rules, distanceKm);
        BigDecimal serviceFee = rates.serviceFee().add(subtotal.multiply(rates.serviceFeeRate())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxes = subtotal.multiply(rates.taxRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = PricingMath.orderTotal(subtotal, delivery.amount().add(serviceFee), taxes, BigDecimal.ZERO);
        return new PriceQuote(subtotal, delivery.amount(), serviceFee, taxes, total, rates.serviceFeeRate(), rates.taxRate(), delivery, ENGINE_VERSION);
    }

    /**
     * Delivery fee per vendor for search, from {@code origin} (the customer's position; null = unknown → base fee
     * estimate). Same rules and distance function as {@link #quote}, loaded in batch (one query per table).
     * Implements geo's {@link DeliveryFeeEstimator} (ADR-0002: vendor search reaches the engine without geo -> cart).
     */
    @Override
    public Map<UUID, DeliveryFeeQuote> estimateDeliveryFees(Collection<Vendor> vendors, GeoPoint origin) {
        if (vendors.isEmpty()) {
            return Map.of();
        }
        Set<UUID> tenantIds = vendors.stream().map(Vendor::getTenantId).collect(Collectors.toSet());
        Map<UUID, FeePolicy> policies = feePolicyRepository.findByTenantIdIn(tenantIds).stream()
                .collect(Collectors.toMap(FeePolicy::getTenantId, Function.identity(), (first, second) -> first));
        Set<UUID> vendorIds = vendors.stream().map(Vendor::getId).collect(Collectors.toSet());
        Map<UUID, BigDecimal> distances = origin == null ? Map.of() : distancesKm(vendorIds, origin);
        Map<UUID, List<DeliveryFeeRule>> rulesByVendor = distances.isEmpty() ? Map.of()
                : deliveryFeeRuleRepository.findByVendorIdInAndActiveTrue(distances.keySet()).stream()
                        .collect(Collectors.groupingBy(DeliveryFeeRule::getVendorId));
        Map<UUID, DeliveryFeeQuote> result = new HashMap<>();
        for (Vendor vendor : vendors) {
            List<DeliveryFeeRule> rules = rulesByVendor.getOrDefault(vendor.getId(), List.of()).stream()
                    .filter(rule -> rule.getTenantId().equals(vendor.getTenantId()))
                    .toList();
            result.put(vendor.getId(), deliveryFee(Optional.ofNullable(policies.get(vendor.getTenantId())), rules, distances.get(vendor.getId())));
        }
        return result;
    }

    /**
     * GET /cart/pricing preview for a bare subtotal: tenant policy base fee, service fee and tax, no discount.
     * The former magic coupon "MVP10" is gone; real coupons go through POST /cart/{cartId}/coupon (spec-002).
     */
    public PricingResponse preview(UUID tenantId, BigDecimal subtotal) {
        PriceQuote quote = quote(tenantId, null, subtotal, null);
        return new PricingResponse(quote.subtotal(), quote.deliveryFee(), quote.serviceFee(), quote.taxes(), BigDecimal.ZERO, quote.total());
    }

    /** Pure delivery fee decision (see class comment); rules must be the vendor's active rules in the vendor's tenant. */
    DeliveryFeeQuote deliveryFee(Optional<FeePolicy> policy, List<DeliveryFeeRule> rules, BigDecimal distanceKm) {
        UUID policyId = policy.map(FeePolicy::getId).orElse(null);
        Long policyVersion = policy.map(FeePolicy::getVersion).orElse(null);
        if (distanceKm != null && !rules.isEmpty()) {
            DeliveryFeeRule band = selectBand(rules, distanceKm);
            return new DeliveryFeeQuote(band.getFeeAmount().setScale(2, RoundingMode.HALF_UP), DeliveryFeeBasis.DISTANCE_RULE, distanceKm,
                    policyId, policyVersion, band.getId(), band.getVersion());
        }
        if (policy.isPresent()) {
            return new DeliveryFeeQuote(policy.get().getDeliveryFee().setScale(2, RoundingMode.HALF_UP), DeliveryFeeBasis.POLICY_BASE, distanceKm,
                    policyId, policyVersion, null, null);
        }
        return new DeliveryFeeQuote(DEFAULT_DELIVERY_FEE, DeliveryFeeBasis.PLATFORM_DEFAULT, distanceKm, null, null, null, null);
    }

    /**
     * Bands are inclusive [min_km, max_km]. The applicable band is the one with the greatest min_km not above the
     * distance (ties: lowest id), so a distance in a gap or beyond the last max_km is charged the nearest lower band;
     * a distance below every band is charged the first band. Deterministic; out-of-range rejection is future work.
     */
    private static DeliveryFeeRule selectBand(List<DeliveryFeeRule> rules, BigDecimal distanceKm) {
        List<DeliveryFeeRule> sorted = rules.stream().sorted(BAND_ORDER).toList();
        for (DeliveryFeeRule rule : sorted) {
            if (rule.isApplicableForDistance(distanceKm)) {
                return rule;
            }
        }
        DeliveryFeeRule chosen = sorted.get(0);
        for (DeliveryFeeRule rule : sorted) {
            if (rule.getMinKm().compareTo(distanceKm) <= 0) {
                chosen = rule;
            }
        }
        return chosen;
    }

    /** Straight-line km (2 decimals, HALF_UP) per vendor with usable coordinates; absent vendors have none. */
    private Map<UUID, BigDecimal> distancesKm(Collection<UUID> vendorIds, GeoPoint point) {
        Map<UUID, BigDecimal> distances = new HashMap<>();
        for (VendorRepository.VendorDistance row : vendorRepository.straightLineDistancesKm(vendorIds, point.latitude(), point.longitude())) {
            if (row.getDistanceKm() != null) {
                distances.put(row.getVendorId(), BigDecimal.valueOf(row.getDistanceKm()).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return distances;
    }

    private Optional<FeePolicy> policyOf(UUID tenantId) {
        return tenantId == null ? Optional.empty() : feePolicyRepository.findByTenantId(tenantId);
    }

    /** Service fee and tax inputs; rates above 100 % are a misconfiguration and are refused rather than charged. */
    private static Rates rates(Optional<FeePolicy> policy) {
        if (policy.isEmpty()) {
            return new Rates(DEFAULT_SERVICE_FEE, DEFAULT_SERVICE_FEE_RATE, DEFAULT_TAX_RATE);
        }
        FeePolicy p = policy.get();
        BigDecimal serviceFeeRate = p.getServiceFeeRate() == null ? BigDecimal.ZERO : p.getServiceFeeRate();
        if (p.getTaxRate().compareTo(BigDecimal.ONE) > 0 || serviceFeeRate.compareTo(BigDecimal.ONE) > 0
                || p.getTaxRate().signum() < 0 || serviceFeeRate.signum() < 0 || p.getServiceFee().signum() < 0 || p.getDeliveryFee().signum() < 0) {
            throw new BusinessException("pricing_policy_invalid", "The tenant fee policy is misconfigured; pricing is unavailable", HttpStatus.CONFLICT);
        }
        return new Rates(p.getServiceFee(), serviceFeeRate, p.getTaxRate());
    }

    private record Rates(BigDecimal serviceFee, BigDecimal serviceFeeRate, BigDecimal taxRate) {}
}
