CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(256) NOT NULL CHECK (length(trim(idempotency_key)) > 0),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash) = 32),
    response_status INT,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_idempotency_keys_user_key ON idempotency_keys(user_id, idempotency_key);
CREATE INDEX ix_idempotency_keys_created_at ON idempotency_keys(created_at);