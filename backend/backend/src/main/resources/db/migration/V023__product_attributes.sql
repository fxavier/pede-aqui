-- Add flexible product attributes support for vertical-specific features
-- This migration adds a JSONB attributes column to store vertical-specific product data

ALTER TABLE products ADD COLUMN attributes jsonb DEFAULT '{}'::jsonb;

-- Add GIN index for efficient JSON queries
CREATE INDEX idx_products_attributes ON products USING GIN (attributes);

-- Add indexes for common vertical-specific attribute queries
CREATE INDEX idx_products_ingredients ON products USING GIN ((attributes->'ingredients'));
CREATE INDEX idx_products_allergens ON products USING GIN ((attributes->'allergens'));
CREATE INDEX idx_products_nutritional ON products USING GIN ((attributes->'nutritional'));
CREATE INDEX idx_products_brand ON products ((attributes->>'brand'));
CREATE INDEX idx_products_model ON products ((attributes->>'model'));

-- Comments
COMMENT ON COLUMN products.attributes IS 'Vertical-specific product attributes stored as JSONB. Examples: ingredients, allergens, nutritional info, technical specs, etc.';