CREATE TABLE expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE RESTRICT,
    payer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK ( amount > 0 ),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    description VARCHAR NOT NULL,
    spent_at TIMESTAMPTZ NOT NULL,
    split_type VARCHAR NOT NULL CHECK ( split_type IN ('EQUAL', 'SHARES', 'EXACT') ),
    version INTEGER NOT NULL default 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_expenses_group_id_spent_at on expenses(group_id, spent_at  DESC);

CREATE TABLE expense_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK ( amount >= 0 ),
    CONSTRAINT ux_expense_shares_expense_id_user_id UNIQUE(expense_id, user_id)
);