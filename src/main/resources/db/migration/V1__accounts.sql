CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    username varchar(32) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9][a-z0-9._-]{2,31}$'),
    password_hash varchar(100) NOT NULL,
    display_name varchar(80) NOT NULL,
    bio varchar(500) NOT NULL,
    avatar uuid,
    role varchar(10) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled boolean NOT NULL,
    auth_revision bigint NOT NULL DEFAULT 0,
    color_mode varchar(10) NOT NULL CHECK (color_mode IN ('SYSTEM', 'LIGHT', 'DARK')),
    palette varchar(24) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE TABLE invitation (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    created_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    redeemed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    CHECK (redeemed_at IS NULL OR cancelled_at IS NULL)
);
