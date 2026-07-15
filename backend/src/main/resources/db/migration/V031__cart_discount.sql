-- Spec 002 / A5: cart carries the pending discount from "apply coupon" to checkout,
-- where it is validated again before persisting on the order.
ALTER TABLE carts ADD COLUMN applied_promotion_id UUID REFERENCES promotion(id);
ALTER TABLE carts ADD COLUMN coupon_code VARCHAR(40);
ALTER TABLE carts ADD COLUMN discount_total NUMERIC(12,2) NOT NULL DEFAULT 0;
