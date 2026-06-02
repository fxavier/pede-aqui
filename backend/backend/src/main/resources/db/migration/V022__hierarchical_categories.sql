-- Add hierarchical category support
-- This migration adds parent_id to enable category hierarchies for better organization by vertical

ALTER TABLE categories ADD COLUMN parent_id UUID REFERENCES categories(id);

-- Add index for efficient parent-child queries
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- Add index for querying root categories (parent_id IS NULL)
CREATE INDEX idx_categories_root ON categories(parent_id) WHERE parent_id IS NULL;

-- Add index for hierarchical queries by vertical
CREATE INDEX idx_categories_vertical_parent ON categories(vertical, parent_id);

-- Comments
COMMENT ON COLUMN categories.parent_id IS 'Parent category ID for hierarchical organization. NULL for root categories.';