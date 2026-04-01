-- Last Logged - User Devices Table for Multi-Device Push
-- Migration: 20260401000002_user_devices
-- Separates device tokens from users table for multi-device support

-- =============================================================================
-- USER_DEVICES TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token TEXT NOT NULL,
    device_name TEXT,
    platform TEXT NOT NULL DEFAULT 'ios',
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(device_token)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_user_id
    ON user_devices(user_id);

CREATE INDEX IF NOT EXISTS idx_user_devices_last_seen
    ON user_devices(last_seen_at);

-- =============================================================================
-- ROW LEVEL SECURITY
-- =============================================================================
ALTER TABLE user_devices ENABLE ROW LEVEL SECURITY;

-- Users can read their own device entries
CREATE POLICY user_devices_select_own ON user_devices
    FOR SELECT USING (user_id = get_user_id());

-- Users can insert their own device entries
CREATE POLICY user_devices_insert_own ON user_devices
    FOR INSERT WITH CHECK (user_id = get_user_id());

-- Users can update their own device entries
CREATE POLICY user_devices_update_own ON user_devices
    FOR UPDATE USING (user_id = get_user_id())
    WITH CHECK (user_id = get_user_id());

-- Users can delete their own device entries
CREATE POLICY user_devices_delete_own ON user_devices
    FOR DELETE USING (user_id = get_user_id());
