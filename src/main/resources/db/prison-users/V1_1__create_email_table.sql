-- Create the new user email table.
CREATE TABLE user_emails
(
    user_id UUID NOT NULL
    email TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT NOT NULL,
    modified_timestamp TIMESTAMP,
    modified_by TEXT

    PRIMARY KEY (user_id, email),

    CONSTRAINT fk_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- Allow only one primary email per user with index
CREATE UNIQUE INDEX one_primary_email_per_user
ON user_emails (user_id)
WHERE is_primary = TRUE;

-- Index on user id and email
CREATE UNIQUE INDEX ux_user_emails_user_id ON user_emails(user_id);
CREATE UNIQUE INDEX ux_user_emails_email ON user_emails(email);

-- Migrate existing users into new table
INSERT INTO user_emails (user_id, email, is_primary)
SELECT user_id, email, TRUE
FROM users
WHERE email IS NOT NULL;