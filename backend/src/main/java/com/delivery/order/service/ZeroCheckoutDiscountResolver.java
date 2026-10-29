package com.delivery.order.service;

import com.delivery.marketing.api.CheckoutDiscount;
import com.delivery.marketing.api.CheckoutDiscountResolver;
import com.delivery.marketing.api.PromotionBasket;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Default no-op discount resolver: keeps checkout behaviour unchanged until the promotions lane provides a real implementation (mark it @Primary or remove this bean). */
@Component
public class ZeroCheckoutDiscountResolver implements CheckoutDiscountResolver {

    /** Always resolves to a zero discount. */
    @Override
    public CheckoutDiscount resolve(PromotionBasket basket, UUID customerId) {
        return CheckoutDiscount.none();
    }
}
