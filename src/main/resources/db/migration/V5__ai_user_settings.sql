CREATE TABLE ai_user_settings (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    provider   VARCHAR(50)  NOT NULL DEFAULT 'openai',
    api_key    TEXT,
    model      VARCHAR(100),
    base_url   VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
