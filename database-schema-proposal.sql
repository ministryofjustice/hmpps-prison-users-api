-- ============================================
-- Schema
-- ============================================
CREATE SCHEMA IF NOT EXISTS prison_users;
SET search_path TO prison_users;

-- ============================================
-- USER
-- ============================================
CREATE TABLE "user" (
    user_id UUID PRIMARY KEY,
    entra_uuid UUID NOT NULL,
    email TEXT NOT NULL,
    first_name TEXT,
    last_name TEXT,
    status VARCHAR(12),
    legacy_staff_id BIGINT UNIQUE,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT
);

COMMENT ON TABLE "user" IS 'Core user details';

COMMENT ON COLUMN "user".user_id IS 'Unique identifier for the user';
COMMENT ON COLUMN "user".entra_uuid IS 'Entra ID identifier for the user';
COMMENT ON COLUMN "user".email IS 'User email address';
COMMENT ON COLUMN "user".first_name IS 'User first name';
COMMENT ON COLUMN "user".last_name IS 'User last name';
COMMENT ON COLUMN "user".status IS 'Current status of the user e.g. active/inactive';
COMMENT ON COLUMN "user".legacy_staff_id IS 'Legacy system staff id from NOMIS';
COMMENT ON COLUMN "user".created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN "user".created_by IS 'Username of creator';
COMMENT ON COLUMN "user".modified_timestamp IS 'Last modification timestamp';
COMMENT ON COLUMN "user".modified_by IS 'Username of last modifier';

CREATE UNIQUE INDEX ux_user_entra_uuid ON "user"(entra_uuid);
CREATE UNIQUE INDEX ux_user_email ON "user"(email);


-- ============================================
-- USER_ACCOUNT
-- ============================================
CREATE TABLE user_account (
    user_id UUID NOT NULL,
    username TEXT PRIMARY KEY,
    account_type VARCHAR(12),
    account_status VARCHAR(32),
    last_logged_in TIMESTAMP,
    active_caseload_id VARCHAR(6),
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT
-- Delete the user account if the parent user is removed.
    CONSTRAINT fk_user_account_user
        FOREIGN KEY (user_id)
        REFERENCES "user"(user_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE user_account IS 'The accounts associated with a user';

COMMENT ON COLUMN user_account.user_id IS 'Reference to owning user';
COMMENT ON COLUMN user_account.username IS 'Unique username for the account';
COMMENT ON COLUMN user_account.account_type IS 'Type of account e.g. ADMIN/GENERAL';
COMMENT ON COLUMN user_account.account_status IS 'Status of the account e.g. OPEN/EXPIRED/LOCKED';
COMMENT ON COLUMN user_account.last_logged_in IS 'Timestamp of last login';
COMMENT ON COLUMN user_account.active_caseload_id IS 'ID of the currently active caseload';
COMMENT ON COLUMN user_account.created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN user_account.created_by IS 'Username of creator';
COMMENT ON COLUMN user_account.modified_timestamp IS 'Last modification timestamp';
COMMENT ON COLUMN user_account.modified_by IS 'Username of last modifier';


-- ============================================
-- ROLES
-- ============================================
CREATE TABLE roles (
    role_id UUID PRIMARY KEY,
    role_code VARCHAR(50) UNIQUE,
    role_name VARCHAR(128),
    role_description TEXT,
    admin_type BOOLEAN,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT
);

COMMENT ON TABLE roles IS 'List of assignable roles for prison users';

COMMENT ON COLUMN roles.role_id IS 'Unique identifier for the role';
COMMENT ON COLUMN roles.role_code IS 'Unique role code (this is match the NOMIS role code)';
COMMENT ON COLUMN roles.role_name IS 'Display name of the role';
COMMENT ON COLUMN roles.role_description IS 'Description of the role';
COMMENT ON COLUMN roles.admin_type IS 'Indicates if the role is for admin users only';
COMMENT ON COLUMN roles.created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN roles.created_by IS 'Username of creator';
COMMENT ON COLUMN roles.modified_timestamp IS 'Last modification timestamp';
COMMENT ON COLUMN roles.modified_by IS 'Username of last modifier';

CREATE UNIQUE INDEX ux_roles_role_code ON roles(role_code);


-- ============================================
-- CASELOADS
-- ============================================
CREATE TABLE caseloads (
    caseload_id VARCHAR(6) PRIMARY KEY,
    name TEXT,
    active BOOLEAN,
    administration_caseload BOOLEAN,
    user_assignable BOOLEAN,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT
);

COMMENT ON TABLE caseloads IS 'List of caseloads';

COMMENT ON COLUMN caseloads.caseload_id IS 'Unique caseload identifier';
COMMENT ON COLUMN caseloads.name IS 'Name of the caseload';
COMMENT ON COLUMN caseloads.active IS 'ndicates if caseload is active';
COMMENT ON COLUMN caseloads.administration_caseload IS 'Indicates if the csseload is used for user administration';
COMMENT ON COLUMN caseloads.user_assignable IS 'Indicates if the caseload is assignable to users';
COMMENT ON COLUMN caseloads.created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN caseloads.created_by IS 'Username of creator';
COMMENT ON COLUMN caseloads.modified_timestamp IS 'Last modification timestamp';
COMMENT ON COLUMN caseloads.modified_by IS 'Username of last modifier';

CREATE INDEX ix_caseloads_caseload_id ON caseloads(caseload_id);


-- ============================================
-- USER_ROLES
-- ============================================
CREATE TABLE user_roles (
    username TEXT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    created_timestamp TIMESTAMP,
    created_by TEXT,

    CONSTRAINT pk_user_roles PRIMARY KEY (username, role_code),
-- Delete the user role link if the user is removed.
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (username)
        REFERENCES user_account(username)
        ON DELETE CASCADE,

-- Delete the user role link if the role is removed.
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_code)
        REFERENCES roles(role_code)
        ON DELETE CASCADE
);

COMMENT ON TABLE user_roles IS 'The roles assigned to a user';

COMMENT ON COLUMN user_roles.username IS 'Username the role is assigned to';
COMMENT ON COLUMN user_roles.role_code IS 'Role assigned to the user';
COMMENT ON COLUMN user_roles.created_timestamp IS 'Assignment creation timestamp';
COMMENT ON COLUMN user_roles.created_by IS 'Username of creator';

CREATE INDEX ix_user_roles_username ON user_roles(username);
CREATE INDEX ix_user_roles_role_code ON user_roles(role_code);


-- ============================================
-- USER_ACCESSIBLE_CASELOADS
-- ============================================
CREATE TABLE user_accessible_caseloads (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    created_timestamp TIMESTAMP,
    created_by TEXT,

    CONSTRAINT pk_user_accessible_caseloads PRIMARY KEY (username, caseload_id),
-- Delete the user caseload link if the user is removed.
    CONSTRAINT fk_uac_user
        FOREIGN KEY (username)
        REFERENCES user_account(username)
        ON DELETE CASCADE,
-- Delete the user caseload link if the caseload is removed.
    CONSTRAINT fk_uac_caseload
        FOREIGN KEY (caseload_id)
        REFERENCES caseloads(caseload_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE user_accessible_caseloads IS 'The caseloads a user can access';

COMMENT ON COLUMN user_accessible_caseloads.username IS 'Username with access';
COMMENT ON COLUMN user_accessible_caseloads.caseload_id IS 'Accessible caseload';
COMMENT ON COLUMN user_accessible_caseloads.created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN user_accessible_caseloads.created_by IS 'Username of creator';

CREATE INDEX ix_uac_username ON user_accessible_caseloads(username);
CREATE INDEX ix_uac_caseload_id ON user_accessible_caseloads(caseload_id);


-- ============================================
-- USER_CASELOAD_ADMINISTRATORS
-- ============================================
CREATE TABLE user_caseload_administrators (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    active BOOLEAN,
    expiry_date DATE,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,

    CONSTRAINT pk_user_caseload_admin PRIMARY KEY (username, caseload_id),
-- Delete the admin entry if the user account is removed.
    CONSTRAINT fk_uca_user
        FOREIGN KEY (username)
        REFERENCES user_account(username)
        ON DELETE CASCADE,
-- Delete the admin entry if the caseload is removed.
    CONSTRAINT fk_uca_caseload
        FOREIGN KEY (caseload_id)
        REFERENCES caseloads(caseload_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE user_caseload_administrators IS 'The caseloads an admin user can administer';

COMMENT ON COLUMN user_caseload_administrators.username IS 'Administrator username';
COMMENT ON COLUMN user_caseload_administrators.caseload_id IS 'Administered caseload';
COMMENT ON COLUMN user_caseload_administrators.active IS 'Indicates if admin assignment is active';
COMMENT ON COLUMN user_caseload_administrators.expiry_date IS 'Date admin assignment expires';
COMMENT ON COLUMN user_caseload_administrators.created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN user_caseload_administrators.created_by IS 'Username of creator';
COMMENT ON COLUMN user_caseload_administrators.modified_timestamp IS 'Last modification timestamp';
COMMENT ON COLUMN user_caseload_administrators.modified_by IS 'Username of last modifier';

CREATE INDEX ix_uca_username ON user_caseload_administrators(username);
CREATE INDEX ix_uca_caseload_id ON user_caseload_administrators(caseload_id);


-- ============================================
-- USER_CASELOAD_MEMBERS
-- ============================================
CREATE TABLE user_caseload_members (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    start_date DATE,
    expiry_date DATE,
    active BOOLEAN,
    created_timestamp TIMESTAMP,
    created_by TEXT,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,

    CONSTRAINT pk_user_caseload_members PRIMARY KEY (username, caseload_id),
-- Delete the user entry if the user account is removed.
    CONSTRAINT fk_ucm_user
        FOREIGN KEY (username)
        REFERENCES user_account(username)
        ON DELETE CASCADE,
-- Delete the user entry if the caseload is removed.
    CONSTRAINT fk_ucm_caseload
        FOREIGN KEY (caseload_id)
        REFERENCES caseloads(caseload_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE user_caseload_members IS 'The caseload a user is part of for administration purposes';

COMMENT ON COLUMN user_caseload_members.username IS 'Member username';
COMMENT ON COLUMN user_caseload_members.caseload_id IS 'Associated caseload';
COMMENT ON COLUMN user_caseload_members.start_date IS 'Membership start date';
COMMENT ON COLUMN user_caseload_members.expiry_date IS 'Membership expiry date';
COMMENT ON COLUMN user_caseload_members.active IS 'Indicates if membership is active';
COMMENT ON COLUMN user_caseload_members.created_timestamp IS 'Record creation timestamp';
COMMENT ON COLUMN user_caseload_members.created_by IS 'Username of creator';
COMMENT ON COLUMN user_caseload_members.modified_timestamp IS 'Last modification timestamp';
COMMENT ON COLUMN user_caseload_members.modified_by IS 'Username of last modifier';

CREATE INDEX ix_ucm_username ON user_caseload_members(username);
CREATE INDEX ix_ucm_caseload_id ON user_caseload_members(caseload_id);
