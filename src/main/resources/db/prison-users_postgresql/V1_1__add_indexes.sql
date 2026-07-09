-- Allow only one primary email per user with index
CREATE UNIQUE INDEX one_primary_email_per_user
    ON user_emails (user_id)
    WHERE is_primary = TRUE;