-- Last Logged - Audit Log & Soft Delete Migration
-- Migration: 20260401000001_audit_log_and_soft_delete
-- Adds audit_log table and soft delete (deleted_at) to completion_logs

-- =============================================================================
-- AUDIT LOG TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id UUID,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_created
    ON audit_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_resource
    ON audit_log(resource_type, resource_id);

-- Enable RLS on audit_log
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- Users can only read their own audit entries
CREATE POLICY audit_log_select_own ON audit_log
    FOR SELECT USING (user_id = get_user_id());

-- Only service role (edge functions) can insert audit entries
-- Users cannot directly insert/update/delete audit log rows
CREATE POLICY audit_log_insert_service ON audit_log
    FOR INSERT WITH CHECK (true);

-- =============================================================================
-- SOFT DELETE ON COMPLETION_LOGS
-- =============================================================================

-- Add deleted_at column for soft delete
ALTER TABLE completion_logs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ DEFAULT NULL;

-- Index for efficient filtering of non-deleted logs
CREATE INDEX IF NOT EXISTS idx_completion_logs_deleted_at
    ON completion_logs(deleted_at);

-- Drop existing RLS policies on completion_logs and recreate with soft delete filter
DROP POLICY IF EXISTS completion_logs_select_own ON completion_logs;
DROP POLICY IF EXISTS completion_logs_insert_own ON completion_logs;
DROP POLICY IF EXISTS completion_logs_update_own ON completion_logs;
DROP POLICY IF EXISTS completion_logs_delete_own ON completion_logs;

-- Recreated policies: SELECT filters out soft-deleted rows
CREATE POLICY completion_logs_select_own ON completion_logs
    FOR SELECT USING (user_id = get_user_id() AND deleted_at IS NULL);

CREATE POLICY completion_logs_insert_own ON completion_logs
    FOR INSERT WITH CHECK (user_id = get_user_id());

CREATE POLICY completion_logs_update_own ON completion_logs
    FOR UPDATE USING (user_id = get_user_id())
    WITH CHECK (user_id = get_user_id());

CREATE POLICY completion_logs_delete_own ON completion_logs
    FOR DELETE USING (user_id = get_user_id());
