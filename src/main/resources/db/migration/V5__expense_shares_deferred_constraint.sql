ALTER TABLE expense_shares DROP CONSTRAINT ux_expense_shares_expense_id_user_id;
ALTER TABLE expense_shares ADD CONSTRAINT ux_expense_shares_expense_id_user_id
    UNIQUE (expense_id, user_id) DEFERRABLE INITIALLY DEFERRED;
