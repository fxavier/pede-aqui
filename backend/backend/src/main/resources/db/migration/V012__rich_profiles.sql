-- Add rich profile fields to vendors table
ALTER TABLE vendors 
ADD COLUMN IF NOT EXISTS owner_name VARCHAR(160),
ADD COLUMN IF NOT EXISTS nif VARCHAR(30),
ADD COLUMN IF NOT EXISTS phone VARCHAR(40),
ADD COLUMN IF NOT EXISTS address VARCHAR(300),
ADD COLUMN IF NOT EXISTS description TEXT,
ADD COLUMN IF NOT EXISTS logo_storage_key VARCHAR(300);

-- Add rich profile fields to couriers table
ALTER TABLE couriers 
ADD COLUMN IF NOT EXISTS full_name VARCHAR(160),
ADD COLUMN IF NOT EXISTS phone VARCHAR(40),
ADD COLUMN IF NOT EXISTS nif VARCHAR(30),
ADD COLUMN IF NOT EXISTS vehicle_type VARCHAR(60),
ADD COLUMN IF NOT EXISTS vehicle_plate VARCHAR(30),
ADD COLUMN IF NOT EXISTS date_of_birth DATE;

-- Add rich profile fields to app_user_profiles table
ALTER TABLE app_user_profiles 
ADD COLUMN IF NOT EXISTS full_name VARCHAR(160),
ADD COLUMN IF NOT EXISTS nif VARCHAR(30),
ADD COLUMN IF NOT EXISTS date_of_birth DATE,
ADD COLUMN IF NOT EXISTS address VARCHAR(300),
ADD COLUMN IF NOT EXISTS avatar_storage_key VARCHAR(300);

-- Add unique constraint for tenant_id and nif combination
ALTER TABLE app_user_profiles ADD CONSTRAINT uq_app_user_profiles_tenant_nif UNIQUE (tenant_id, nif);

-- Create courier_documents table
CREATE TABLE IF NOT EXISTS courier_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    courier_id UUID NOT NULL REFERENCES couriers(id) ON DELETE CASCADE,
    document_type VARCHAR(80) NOT NULL,
    storage_key VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Create index for courier_documents
CREATE INDEX IF NOT EXISTS idx_courier_documents_tenant_courier ON courier_documents(tenant_id, courier_id);