-- Initial schema for CostProject.
-- Money columns are BIGINT whole rupiah. Never NUMERIC-as-float, never text.

CREATE TABLE users (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text        NOT NULL,
    full_name     text        NOT NULL,
    password_hash text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: "Rei@x.com" and "rei@x.com" are the same account.
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));

-- Refresh tokens are opaque random strings. Only their SHA-256 hash is stored,
-- so a leaked table cannot be used to mint sessions.
CREATE TABLE refresh_tokens (
    id          bigserial   PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

-- Project ids are client-generated strings so a create can be retried safely.
CREATE TABLE projects (
    id            text        PRIMARY KEY,
    owner_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          text        NOT NULL,
    customer      text        NOT NULL DEFAULT '',
    pic           text        NOT NULL DEFAULT '',
    harga_kontrak bigint      NOT NULL DEFAULT 0 CHECK (harga_kontrak >= 0),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX projects_owner_created_idx ON projects (owner_id, created_at DESC);

-- All four cost item kinds share one table, discriminated by `kind`.
-- See API.md for which columns each kind uses.
CREATE TABLE cost_items (
    project_id  text    NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    id          text    NOT NULL,
    position    integer NOT NULL,
    kind        text    NOT NULL CHECK (kind IN ('JASA', 'BARANG', 'TRANSPORTASI', 'LAIN_LAIN')),
    description text    NOT NULL DEFAULT '',
    engineer    text,
    quantity    bigint  CHECK (quantity IS NULL OR quantity >= 0),
    amount      bigint  NOT NULL DEFAULT 0 CHECK (amount >= 0),
    -- Item ids only need to be unique within their project.
    PRIMARY KEY (project_id, id)
);

CREATE INDEX cost_items_project_position_idx ON cost_items (project_id, position);
