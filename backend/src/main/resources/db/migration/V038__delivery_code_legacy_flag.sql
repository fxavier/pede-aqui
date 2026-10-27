-- INC-02 (AS-08): PIN hashes move from unsalted SHA-256 to HMAC-SHA256 (server secret).
-- Rows that exist when this migration runs were hashed with SHA-256 and are flagged legacy, so
-- verification may fall back to SHA-256 for them only. New rows default to FALSE (HMAC).
-- ADD COLUMN with a constant default is metadata-only in PostgreSQL 11+ (no table rewrite).
ALTER TABLE deliveries ADD COLUMN confirmation_code_legacy BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE deliveries ALTER COLUMN confirmation_code_legacy SET DEFAULT FALSE;
