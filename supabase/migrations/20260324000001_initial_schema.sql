-- Last Logged MVP - Initial Supabase Schema
-- Migration: 20260324000001_initial_schema
-- Self-contained migration for Supabase PostgreSQL

-- =============================================================================
-- EXTENSIONS
-- =============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- TYPES
-- =============================================================================
CREATE TYPE sync_status AS ENUM ('synced', 'pending', 'conflict');
CREATE TYPE subscription_tier AS ENUM ('free', 'premium', 'lifetime');

-- =============================================================================
-- TABLES
-- =============================================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    auth_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT,
    subscription_tier subscription_tier NOT NULL DEFAULT 'free',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tracker categories table
CREATE TABLE IF NOT EXISTS tracker_categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    icon_name TEXT NOT NULL,
    color_hex TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tracker items table (mirrors SwiftData TrackerItem model)
CREATE TABLE IF NOT EXISTS tracker_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    category_id UUID NOT NULL REFERENCES tracker_categories(id) ON DELETE CASCADE,
    reminder_interval_days INTEGER,
    last_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sort_order INTEGER NOT NULL DEFAULT 0,
    icon_name TEXT NOT NULL DEFAULT 'checkmark.circle',
    is_archived BOOLEAN NOT NULL DEFAULT false,
    sync_status sync_status NOT NULL DEFAULT 'synced',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Completion logs table
CREATE TABLE IF NOT EXISTS completion_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tracker_item_id UUID NOT NULL REFERENCES tracker_items(id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- INDEXES
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_tracker_items_user_archived
    ON tracker_items(user_id, is_archived);

CREATE INDEX IF NOT EXISTS idx_tracker_items_user_last_completed
    ON tracker_items(user_id, last_completed_at);

CREATE INDEX IF NOT EXISTS idx_completion_logs_tracker_completed
    ON completion_logs(tracker_item_id, completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_completion_logs_user_completed
    ON completion_logs(user_id, completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_tracker_categories_user
    ON tracker_categories(user_id);

-- =============================================================================
-- UPDATED_AT TRIGGER FUNCTION
-- =============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger to tables with updated_at column
CREATE TRIGGER set_updated_at_users
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_updated_at_tracker_categories
    BEFORE UPDATE ON tracker_categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER set_updated_at_tracker_items
    BEFORE UPDATE ON tracker_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- ROW LEVEL SECURITY
-- =============================================================================

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tracker_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE tracker_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE completion_logs ENABLE ROW LEVEL SECURITY;

-- Users: users can only access their own row
CREATE POLICY users_select_own ON users
    FOR SELECT USING (auth_id = auth.uid());

CREATE POLICY users_insert_own ON users
    FOR INSERT WITH CHECK (auth_id = auth.uid());

CREATE POLICY users_update_own ON users
    FOR UPDATE USING (auth_id = auth.uid())
    WITH CHECK (auth_id = auth.uid());

CREATE POLICY users_delete_own ON users
    FOR DELETE USING (auth_id = auth.uid());

-- Helper function to get the user's internal ID from auth.uid()
CREATE OR REPLACE FUNCTION get_user_id()
RETURNS UUID AS $$
    SELECT id FROM users WHERE auth_id = auth.uid() LIMIT 1;
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- Tracker categories: users can only access their own categories
CREATE POLICY tracker_categories_select_own ON tracker_categories
    FOR SELECT USING (user_id = get_user_id());

CREATE POLICY tracker_categories_insert_own ON tracker_categories
    FOR INSERT WITH CHECK (user_id = get_user_id());

CREATE POLICY tracker_categories_update_own ON tracker_categories
    FOR UPDATE USING (user_id = get_user_id())
    WITH CHECK (user_id = get_user_id());

CREATE POLICY tracker_categories_delete_own ON tracker_categories
    FOR DELETE USING (user_id = get_user_id());

-- Tracker items: users can only access their own items
CREATE POLICY tracker_items_select_own ON tracker_items
    FOR SELECT USING (user_id = get_user_id());

CREATE POLICY tracker_items_insert_own ON tracker_items
    FOR INSERT WITH CHECK (user_id = get_user_id());

CREATE POLICY tracker_items_update_own ON tracker_items
    FOR UPDATE USING (user_id = get_user_id())
    WITH CHECK (user_id = get_user_id());

CREATE POLICY tracker_items_delete_own ON tracker_items
    FOR DELETE USING (user_id = get_user_id());

-- Completion logs: users can only access their own logs
CREATE POLICY completion_logs_select_own ON completion_logs
    FOR SELECT USING (user_id = get_user_id());

CREATE POLICY completion_logs_insert_own ON completion_logs
    FOR INSERT WITH CHECK (user_id = get_user_id());

CREATE POLICY completion_logs_update_own ON completion_logs
    FOR UPDATE USING (user_id = get_user_id())
    WITH CHECK (user_id = get_user_id());

CREATE POLICY completion_logs_delete_own ON completion_logs
    FOR DELETE USING (user_id = get_user_id());
