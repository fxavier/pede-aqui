-- Spec 002 / A7 (adopted): category at order time for stable category reports.
-- Populated at order creation for new orders; pre-existing rows stay NULL and
-- category reports fall back to the live product.category_id join for them.
ALTER TABLE order_items ADD COLUMN category_id_snapshot UUID;
