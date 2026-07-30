-- Shared (cross-instance) rate limiting for the edge functions
--
-- `middleware.ts` previously counted requests in a process-local Map, so limits
-- reset on every deploy and were bypassed entirely by running more than one
-- instance. This table moves the counters into Postgres so every instance sees
-- the same budget.
--
-- Mirrors the existing `auth_rate_limits` pattern: service-role access only,
-- no RLS policies, pruned on a schedule.

CREATE TABLE IF NOT EXISTS rate_limits (
    -- "<keyPrefix>:<client ip>"
    bucket_key TEXT PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    reset_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Supports the pruning sweep below.
CREATE INDEX IF NOT EXISTS idx_rate_limits_reset_at
    ON rate_limits(reset_at);

-- =============================================================================
-- ROW LEVEL SECURITY
-- =============================================================================
-- Enabled with no policies: this denies every anon/authenticated request while
-- the service role (used by the edge functions) bypasses RLS. Without this,
-- the table would be readable through the public REST API.
ALTER TABLE rate_limits ENABLE ROW LEVEL SECURITY;

-- =============================================================================
-- ATOMIC INCREMENT
-- =============================================================================
-- Doing read-then-write from the application would race between instances and
-- undercount, which is the whole failure this table exists to fix. One
-- statement, one round trip, correct under concurrency.
--
-- Returns the post-increment count and the window's reset time.
CREATE OR REPLACE FUNCTION increment_rate_limit(
    p_bucket_key TEXT,
    p_window_ms INTEGER
)
RETURNS TABLE (request_count INTEGER, reset_at TIMESTAMPTZ)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_now TIMESTAMPTZ := now();
    v_reset TIMESTAMPTZ := now() + make_interval(secs => p_window_ms / 1000.0);
BEGIN
    RETURN QUERY
    INSERT INTO rate_limits AS rl (bucket_key, request_count, reset_at, updated_at)
    VALUES (p_bucket_key, 1, v_reset, v_now)
    ON CONFLICT (bucket_key) DO UPDATE
        SET
            -- An elapsed window starts over at 1 rather than accumulating.
            request_count = CASE
                WHEN rl.reset_at <= v_now THEN 1
                ELSE rl.request_count + 1
            END,
            reset_at = CASE
                WHEN rl.reset_at <= v_now THEN v_reset
                ELSE rl.reset_at
            END,
            updated_at = v_now
    RETURNING rl.request_count, rl.reset_at;
END;
$$;

-- =============================================================================
-- PRUNING
-- =============================================================================
-- Called from the weekly cleanup job so the table cannot grow without bound.
CREATE OR REPLACE FUNCTION prune_rate_limits()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_deleted INTEGER;
BEGIN
    DELETE FROM rate_limits WHERE reset_at < now() - INTERVAL '1 day';
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

REVOKE ALL ON FUNCTION increment_rate_limit(TEXT, INTEGER) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION prune_rate_limits() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION increment_rate_limit(TEXT, INTEGER) TO service_role;
GRANT EXECUTE ON FUNCTION prune_rate_limits() TO service_role;
