-- Server-side auth rate limiting table
-- Tracks auth attempts per IP to prevent brute force even if client is bypassed

CREATE TABLE IF NOT EXISTS auth_rate_limits (
    ip_address TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    window_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    PRIMARY KEY (ip_address)
);

-- Auto-cleanup old entries (older than 1 day)
CREATE INDEX IF NOT EXISTS idx_auth_rate_limits_window
    ON auth_rate_limits(window_start);

-- No RLS needed — this table is only accessed by edge functions via service role
