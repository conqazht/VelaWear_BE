ALTER TABLE users
ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
ADD CONSTRAINT ck_users_security_version CHECK (security_version >= 0);
