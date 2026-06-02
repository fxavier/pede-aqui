-- Add rejection reason field to orders table for vendor rejections
ALTER TABLE orders ADD COLUMN rejection_reason TEXT;