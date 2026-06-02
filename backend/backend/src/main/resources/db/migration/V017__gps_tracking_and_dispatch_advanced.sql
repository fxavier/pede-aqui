-- V017: GPS Tracking and Advanced Dispatch
-- This migration adds courier location tracking and dispatch attempt logging

-- Table: courier_location_updates
CREATE TABLE courier_location_updates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    courier_id UUID NOT NULL REFERENCES couriers(id),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_meters DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL
);

-- Table: dispatch_attempts
CREATE TABLE dispatch_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    dispatch_job_id UUID NOT NULL REFERENCES dispatch_jobs(id),
    courier_id UUID NOT NULL REFERENCES couriers(id),
    outcome VARCHAR(30) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ
);

-- Add expires_at column to dispatch_jobs table
ALTER TABLE dispatch_jobs ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Add proximity_notified column to deliveries table
ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS proximity_notified BOOLEAN NOT NULL DEFAULT FALSE;

-- Indexes
CREATE INDEX idx_courier_location_delivery ON courier_location_updates(delivery_id);
CREATE INDEX idx_dispatch_attempts_job ON dispatch_attempts(dispatch_job_id);

-- Comments for documentation
COMMENT ON TABLE courier_location_updates IS 'Real-time GPS location updates from couriers during deliveries';
COMMENT ON TABLE dispatch_attempts IS 'Log of dispatch attempts and courier responses';
COMMENT ON COLUMN dispatch_jobs.expires_at IS 'When this dispatch job expires and should no longer be offered to couriers';
COMMENT ON COLUMN deliveries.proximity_notified IS 'Whether customer has been notified that courier is nearby';