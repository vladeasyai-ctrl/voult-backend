ALTER TABLE remote_upload_sessions
    ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
        CHECK (mode IN ('DIRECT', 'AI_IMPORT'));

ALTER TABLE remote_upload_sessions
    ADD COLUMN space_id UUID REFERENCES spaces (id) ON DELETE SET NULL;
