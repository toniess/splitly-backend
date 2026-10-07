ALTER TABLE refresh_tokens DROP CONSTRAINT uq_refresh_tokens_user_id;

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);