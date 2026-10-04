CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE RESTRICT,
    from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK ( amount > 0 ),
    settled_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);

ALTER TABLE settlements
ADD CONSTRAINT check_settlements_from_user_id_to_user_id CHECK ( from_user_id <> to_user_id );

CREATE INDEX ix_settlements_group_id on settlements(group_id, settled_at DESC);


