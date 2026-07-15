package com.delivery.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request payload to apply a coupon code to a cart. */
public record ApplyCouponRequest(@NotBlank @Size(max = 40) String code) {}
