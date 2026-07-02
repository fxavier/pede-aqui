-- Add variant selection support to SKUs
-- This allows SKUs to represent specific combinations of product variants

ALTER TABLE skus ADD COLUMN variant_selection jsonb DEFAULT '{}'::jsonb;

-- Add GIN index for efficient variant queries
CREATE INDEX idx_skus_variant_selection ON skus USING GIN (variant_selection);

-- Add index for finding SKUs by specific variant combinations
CREATE INDEX idx_skus_product_variants ON skus(product_id) INCLUDE (variant_selection);

-- Comments
COMMENT ON COLUMN skus.variant_selection IS 'JSON object storing the specific variant choices for this SKU (e.g. {"size": "Large", "color": "Red"})';