package com.delivery.cart.mapper;

import com.delivery.cart.dto.CartItemResponse;
import com.delivery.cart.dto.CartResponse;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import org.springframework.stereotype.Component;

/** Converts cart entities to API DTOs. */
@Component
public class CartMapper {
    public CartResponse toResponse(Cart cart) {
        return new CartResponse(cart.getId(), cart.getVendorId(), cart.getSubtotal(), cart.getFees(), cart.getTaxes(), cart.getDiscounts(), cart.getTotal(), cart.getItems().stream().map(this::toItemResponse).toList());
    }

    private CartItemResponse toItemResponse(CartItem item) {
        return new CartItemResponse(item.getSkuId(), item.getProductNameSnapshot(), item.getSkuNameSnapshot(), item.getUnitPriceSnapshot(), item.getQuantity(), item.lineTotal());
    }
}
