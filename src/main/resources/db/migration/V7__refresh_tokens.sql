CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at	TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at	TIMESTAMPTZ,
    CONSTRAINT uq_refresh_tokens_user_id UNIQUE(user_id)
);

CREATE UNIQUE INDEX ux_refresh_tokens_token_hash on refresh_tokens(token_hash);