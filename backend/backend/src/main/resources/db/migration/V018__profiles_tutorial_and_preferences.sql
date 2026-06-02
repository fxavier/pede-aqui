-- V018: User Profiles, Tutorial and Notification Preferences
-- This migration adds tutorial completion tracking and notification preferences

-- Add columns to app_user_profiles table
ALTER TABLE app_user_profiles ADD COLUMN IF NOT EXISTS tutorial_completed_at TIMESTAMPTZ;
ALTER TABLE app_user_profiles ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(10) DEFAULT 'pt';

-- Table: user_notification_preferences
CREATE TABLE user_notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_profile_id UUID NOT NULL REFERENCES app_user_profiles(id),
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, user_profile_id)
);

-- Comments for documentation
COMMENT ON COLUMN app_user_profiles.tutorial_completed_at IS 'When the user completed the app tutorial';
COMMENT ON COLUMN app_user_profiles.preferred_language IS 'User preferred language code (e.g. pt, en, es)';
COMMENT ON TABLE user_notification_preferences IS 'User notification channel preferences for push, SMS, and email';