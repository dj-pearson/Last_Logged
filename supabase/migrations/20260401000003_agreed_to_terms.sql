-- Add agreed_to_terms_at column to users table
-- Records when the user agreed to Terms of Service and Privacy Policy

ALTER TABLE users
ADD COLUMN IF NOT EXISTS agreed_to_terms_at TIMESTAMPTZ;

-- Index for compliance queries (find users who haven't agreed)
CREATE INDEX IF NOT EXISTS idx_users_agreed_to_terms
    ON users(agreed_to_terms_at)
    WHERE agreed_to_terms_at IS NULL;
