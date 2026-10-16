ALTER TABLE user_caseload_members
    ALTER COLUMN start_date SET NOT NULL;

ALTER TABLE user_caseload_members_audit
    ALTER COLUMN start_date SET NOT NULL;

