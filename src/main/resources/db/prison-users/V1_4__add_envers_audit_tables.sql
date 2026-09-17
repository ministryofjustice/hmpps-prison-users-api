-- Envers audit tables setup
-- This creates the base audit infrastructure and audit tables for all audited entities

-- Create REVINFO table (Envers uses this for revision tracking)
CREATE TABLE revinfo (
    rev BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    revtstmp BIGINT NOT NULL
);

-- Audit table for users
CREATE TABLE users_audit (
    user_id UUID NOT NULL,
    entra_uuid UUID,
    first_name TEXT,
    last_name TEXT,
    status VARCHAR(12),
    legacy_staff_id BIGINT,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_users_audit PRIMARY KEY (user_id, rev),
    CONSTRAINT fk_users_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_users_audit_rev ON users_audit(rev);
CREATE INDEX ix_users_audit_legacy_staff_id ON users_audit(legacy_staff_id);

-- Audit table for user_account
CREATE TABLE user_account_audit (
    username TEXT NOT NULL,
    user_id UUID,
    account_type VARCHAR(12),
    account_status VARCHAR(32),
    last_logged_in TIMESTAMP,
    active_caseload_id VARCHAR(6),
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_user_account_audit PRIMARY KEY (username, rev),
    CONSTRAINT fk_user_account_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_user_account_audit_rev ON user_account_audit(rev);
CREATE INDEX ix_user_account_audit_user_id ON user_account_audit(user_id);

-- Audit table for user_emails
CREATE TABLE user_emails_audit (
    id BIGINT NOT NULL,
    user_id UUID,
    email TEXT,
    is_primary BOOLEAN,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_user_emails_audit PRIMARY KEY (id, rev),
    CONSTRAINT fk_user_emails_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_user_emails_audit_rev ON user_emails_audit(rev);
CREATE INDEX ix_user_emails_audit_user_id ON user_emails_audit(user_id);

-- Audit table for user_roles
CREATE TABLE user_roles_audit (
    username TEXT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_user_roles_audit PRIMARY KEY (username, role_code, rev),
    CONSTRAINT fk_user_roles_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_user_roles_audit_rev ON user_roles_audit(rev);
CREATE INDEX ix_user_roles_audit_username ON user_roles_audit(username);
CREATE INDEX ix_user_roles_audit_role_code ON user_roles_audit(role_code);

-- Audit table for user_accessible_caseloads
CREATE TABLE user_accessible_caseloads_audit (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_user_accessible_caseloads_audit PRIMARY KEY (username, caseload_id, rev),
    CONSTRAINT fk_user_accessible_caseloads_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_user_accessible_caseloads_audit_rev ON user_accessible_caseloads_audit(rev);
CREATE INDEX ix_user_accessible_caseloads_audit_username ON user_accessible_caseloads_audit(username);
CREATE INDEX ix_user_accessible_caseloads_audit_caseload_id ON user_accessible_caseloads_audit(caseload_id);

-- Audit table for user_caseload_administrators
CREATE TABLE user_caseload_administrators_audit (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    active BOOLEAN,
    expiry_date DATE,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_user_caseload_administrators_audit PRIMARY KEY (username, caseload_id, rev),
    CONSTRAINT fk_user_caseload_administrators_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_user_caseload_administrators_audit_rev ON user_caseload_administrators_audit(rev);
CREATE INDEX ix_user_caseload_administrators_audit_username ON user_caseload_administrators_audit(username);
CREATE INDEX ix_user_caseload_administrators_audit_caseload_id ON user_caseload_administrators_audit(caseload_id);

-- Audit table for user_caseload_members
CREATE TABLE user_caseload_members_audit (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    start_date DATE,
    expiry_date DATE,
    active BOOLEAN,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,
    rev BIGINT NOT NULL,
    revtype SMALLINT,
    CONSTRAINT pk_user_caseload_members_audit PRIMARY KEY (username, caseload_id, rev),
    CONSTRAINT fk_user_caseload_members_audit_revinfo FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE INDEX ix_user_caseload_members_audit_rev ON user_caseload_members_audit(rev);
CREATE INDEX ix_user_caseload_members_audit_username ON user_caseload_members_audit(username);
CREATE INDEX ix_user_caseload_members_audit_caseload_id ON user_caseload_members_audit(caseload_id);






