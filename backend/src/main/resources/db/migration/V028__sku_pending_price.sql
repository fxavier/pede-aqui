-- Spec 002 / A2: SKU pending-price moderation columns.
-- Note (A1): products.description and products.updated_at already exist (V003), and the
-- product image is stored in products.primary_image_key / image_gallery (V026), so no
-- product columns are added by this spec.
ALTER TABLE skus ADD COLUMN pending_price NUMERIC(12,2);
ALTER TABLE skus ADD COLUMN pending_price_submitted_at TIMESTAMPTZ;
ALTER TABLE skus ADD COLUMN pending_price_submitted_by VARCHAR(255);

ALTER TABLE skus ADD CONSTRAINT ck_skus_pending_price_non_negative
    CHECK (pending_price IS NULL OR pending_price >= 0);
