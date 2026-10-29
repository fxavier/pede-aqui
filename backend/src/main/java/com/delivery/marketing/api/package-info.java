/**
 * Public API of the marketing module (ADR-0002 C3): the checkout discount seam ({@link
 * com.delivery.marketing.api.CheckoutDiscountResolver}, implemented by marketing, called by order) and the neutral
 * {@link com.delivery.marketing.api.PromotionBasket} that cart/order hand to it, so marketing depends on neither.
 */
@NamedInterface("api")
package com.delivery.marketing.api;

import org.springframework.modulith.NamedInterface;
