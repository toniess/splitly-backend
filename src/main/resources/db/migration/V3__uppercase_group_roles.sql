ALTER TABLE group_members DROP CONSTRAINT group_members_role_check;
UPDATE group_members SET role = upper(role);
ALTER TABLE group_members ADD CONSTRAINT group_members_role_check CHECK(role in ('OWNER', 'MEMBER'));