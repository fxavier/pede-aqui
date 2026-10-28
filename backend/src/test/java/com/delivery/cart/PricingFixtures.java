package com.delivery.cart;

import static org.mockito.Mockito.mock;

import com.delivery.cart.service.PricingService;
import com.delivery.geo.repository.DeliveryFeeRuleRepository;
import com.delivery.tenant.repository.FeePolicyRepository;
import com.delivery.vendor.repository.VendorRepository;

/** Test helper: a pricing engine whose tenants have no fee policy, rules or coordinates (platform defaults). */
public final class PricingFixtures {
    private PricingFixtures() {}

    /** Engine that always prices with the platform defaults (5.00 delivery, 5 % service fee, 8 % tax). */
    public static PricingService platformDefaults() {
        return new PricingService(mock(FeePolicyRepository.class), mock(DeliveryFeeRuleRepository.class), mock(VendorRepository.class));
    }
}
