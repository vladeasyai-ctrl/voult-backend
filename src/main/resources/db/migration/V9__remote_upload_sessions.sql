CREATE TABLE remote_upload_sessions (
    id            UUID PRIMARY KEY,
    token         VARCHAR(64)  NOT NULL UNIQUE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id     UUID         REFERENCES nodes (id) ON DELETE SET NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CLOSED')),
    upload_count  INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_remote_upload_sessions_token ON remote_upload_sessions (token);
CREATE INDEX idx_remote_upload_sessions_user ON remote_upload_sessions (user_id);
