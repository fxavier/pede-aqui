package com.delivery.order.service;

import com.delivery.cart.entity.Cart;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Default no-op discount resolver: keeps checkout behaviour unchanged until the promotions lane provides a real implementation (mark it @Primary or remove this bean). */
@Component
public class ZeroCheckoutDiscountResolver implements CheckoutDiscountResolver {

    /** Always resolves to a zero discount. */
    @Override
    public CheckoutDiscount resolve(Cart cart, UUID customerId) {
        return CheckoutDiscount.none();
    }
}
