-- Add product image support
-- This enables products to have a primary image and image gallery

ALTER TABLE products ADD COLUMN primary_image_key VARCHAR(255);
ALTER TABLE products ADD COLUMN image_gallery jsonb DEFAULT '[]'::jsonb;

-- Add indexes for image queries
CREATE INDEX idx_products_primary_image ON products(primary_image_key) WHERE primary_image_key IS NOT NULL;
CREATE INDEX idx_products_image_gallery ON products USING GIN (image_gallery);

-- Comments
COMMENT ON COLUMN products.primary_image_key IS 'S3 storage key for the primary product image';
COMMENT ON COLUMN products.image_gallery IS 'JSON array of additional product image storage keys';