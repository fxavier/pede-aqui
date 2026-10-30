package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.pricing.PriceQuote;
import com.delivery.geo.api.ServiceArea;
import com.delivery.vendor.entity.Vendor;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Checkout v2 delivery-area rules (W6-A, LAC-20 leftovers), applied by quote and checkout when the order has a
 * delivery address:
 * <ul>
 *   <li><b>Service area</b> — {@link ServiceArea#coverage} of the vendor's {@code delivery_zone_id} over the address
 *   point: NOT_COVERED → 422 {@code out_of_service_area}. Vendors without a zone, zones without a polygon (ACTIVE with
 *   NULL area) and addresses without coordinates are <b>allowed</b> (UNRESTRICTED) so current data keeps working; a
 *   non-ACTIVE zone covers nothing.</li>
 *   <li><b>Maximum distance</b> — the straight-line distance the pricing engine measured (vendor → address) above the
 *   vendor's {@code max_delivery_distance_km}, else the tenant policy's, → 422 {@code address_too_far}. No limit
 *   configured or no distance known (missing coordinates) → allowed.</li>
 * </ul>
 */
@Component
public class DeliveryAreaPolicy {
    private final ServiceArea serviceArea;

    public DeliveryAreaPolicy(ServiceArea serviceArea) {
        this.serviceArea = serviceArea;
    }

    /** 422 out_of_service_area when the vendor's delivery zone has a polygon that does not cover the destination. */
    public void requireInServiceArea(UUID tenantId, Vendor vendor, GeoPoint destination) {
        if (vendor == null || destination == null) {
            return;
        }
        if (serviceArea.coverage(tenantId, vendor.getDeliveryZoneId(), destination) == ServiceArea.Coverage.NOT_COVERED) {
            throw new BusinessException("out_of_service_area", "The delivery address is outside the store's service area", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** 422 address_too_far when the quoted distance exceeds the vendor (or tenant policy) maximum delivery distance. */
    public void requireWithinMaxDistance(Vendor vendor, PriceQuote quote) {
        BigDecimal distanceKm = quote.delivery() == null ? null : quote.delivery().distanceKm();
        BigDecimal limitKm = vendor != null && vendor.getMaxDeliveryDistanceKm() != null
                ? vendor.getMaxDeliveryDistanceKm() : quote.policyMaxDeliveryDistanceKm();
        if (distanceKm == null || limitKm == null || distanceKm.compareTo(limitKm) <= 0) {
            return;
        }
        throw new AddressTooFarException(distanceKm, limitKm);
    }

    /** 422 address_too_far with {@code details.distanceKm} and {@code details.maxDistanceKm}. */
    static final class AddressTooFarException extends BusinessException {
        private final BigDecimal distanceKm;
        private final BigDecimal maxDistanceKm;

        AddressTooFarException(BigDecimal distanceKm, BigDecimal maxDistanceKm) {
            super("address_too_far", "The delivery address is farther than the store delivers", HttpStatus.UNPROCESSABLE_ENTITY);
            this.distanceKm = distanceKm;
            this.maxDistanceKm = maxDistanceKm;
        }

        @Override
        public Object getDetails() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("distanceKm", distanceKm);
            details.put("maxDistanceKm", maxDistanceKm);
            return details;
        }
    }
}
