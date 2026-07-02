-- Add product variants support for vertical-specific customization
-- This enables size, color, flavor and other variant options

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    variant_type VARCHAR(100) NOT NULL, -- size, color, flavor, model, etc
    variant_options jsonb DEFAULT '{}'::jsonb,
    required BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for efficient queries
CREATE INDEX idx_product_variants_tenant_id ON product_variants(tenant_id);
CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_product_variants_type ON product_variants(variant_type);
CREATE INDEX idx_product_variants_active ON product_variants(active) WHERE active = true;
CREATE INDEX idx_product_variants_display_order ON product_variants(product_id, display_order);

-- GIN index for variant options JSON queries
CREATE INDEX idx_product_variants_options ON product_variants USING GIN (variant_options);

-- Comments
COMMENT ON TABLE product_variants IS 'Product variants for vertical-specific customization (size, color, flavor, etc.)';
COMMENT ON COLUMN product_variants.variant_type IS 'Type of variant: size, color, flavor, model, material, etc.';
COMMENT ON COLUMN product_variants.variant_options IS 'JSON configuration of available options and their metadata (prices, availability, etc.)';
COMMENT ON COLUMN product_variants.required IS 'Whether customer must select this variant when ordering';
COMMENT ON COLUMN product_variants.display_order IS 'Order in which variants are displayed to customers';