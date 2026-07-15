-- Spec 002 / A3: order-level discount columns.
-- Legacy orders are backfilled with discount_total = 0 and keep their stored total,
-- so the invariant total = subtotal + fees + taxes - discount_total holds for them
-- (the legacy "discounts" column is always 0 in the current checkout flow).
-- FK to promotion(id) is added in V030 once the promotion table exists.
ALTER TABLE orders ADD COLUMN discount_total NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN applied_promotion_id UUID;
