CREATE TABLE users
(
    user_id UUID NOT NULL
        CONSTRAINT user_pk
            PRIMARY KEY,
    entra_uuid UUID,
    first_name TEXT,
    last_name TEXT,
    status VARCHAR(12),
    legacy_staff_id BIGINT UNIQUE,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
    modified_timestamp TIMESTAMP,
    modified_by TEXT
);

CREATE UNIQUE INDEX ux_user_entra_uuid ON users(entra_uuid);

CREATE SEQUENCE user_emails_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE user_emails
(
    id BIGINT NOT NULL DEFAULT nextval('user_emails_id_seq'),
    user_id UUID NOT NULL,
    email TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,

    CONSTRAINT pk_user_emails PRIMARY KEY (id),

    CONSTRAINT fk_user
        FOREIGN KEY (user_id)
            REFERENCES users(user_id)
            ON DELETE CASCADE
);

-- Index on user id and email
CREATE INDEX ux_user_emails_user_id ON user_emails(user_id);
CREATE UNIQUE INDEX ux_user_emails_user_id_email ON user_emails (user_id, email);

CREATE TABLE user_account (
    user_id UUID NOT NULL,
    username TEXT PRIMARY KEY,
    account_type VARCHAR(12) NOT NULL,
    account_status VARCHAR(32) NOT NULL,
    last_logged_in TIMESTAMP,
    active_caseload_id VARCHAR(6),
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
    modified_timestamp TIMESTAMP,
    modified_by TEXT,

    -- Delete the user account if the parent user is removed.
    CONSTRAINT fk_user_account_user
        FOREIGN KEY (user_id)
            REFERENCES users(user_id)
            ON DELETE CASCADE
);

CREATE TABLE caseloads (
    caseload_id VARCHAR(6) PRIMARY KEY,
    name TEXT,
    function TEXT,
    active BOOLEAN,
    administration_caseload BOOLEAN,
    user_assignable BOOLEAN,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
    modified_timestamp TIMESTAMP,
    modified_by TEXT
);

CREATE TABLE user_roles (
    username TEXT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (username, role_code),

    -- Delete the user role link if the user is removed.
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (username)
            REFERENCES user_account(username)
            ON DELETE CASCADE
);

CREATE INDEX ix_user_roles_username ON user_roles(username);
CREATE INDEX ix_user_roles_role_code ON user_roles(role_code);

CREATE TABLE user_accessible_caseloads (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,

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

CREATE INDEX ix_uac_username ON user_accessible_caseloads(username);
CREATE INDEX ix_uac_caseload_id ON user_accessible_caseloads(caseload_id);

CREATE TABLE user_caseload_administrators (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    active BOOLEAN,
    expiry_date DATE,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
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

CREATE INDEX ix_uca_username ON user_caseload_administrators(username);
CREATE INDEX ix_uca_caseload_id ON user_caseload_administrators(caseload_id);

CREATE TABLE user_caseload_members (
    username TEXT NOT NULL,
    caseload_id VARCHAR(6) NOT NULL,
    start_date DATE,
    expiry_date DATE,
    active BOOLEAN,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
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

CREATE INDEX ix_ucm_username ON user_caseload_members(username);
CREATE INDEX ix_ucm_caseload_id ON user_caseload_members(caseload_id);