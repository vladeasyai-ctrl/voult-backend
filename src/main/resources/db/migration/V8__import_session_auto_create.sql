ALTER TABLE import_sessions
    ADD COLUMN document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    ADD COLUMN space_id UUID REFERENCES spaces (id) ON DELETE SET NULL,
    ADD COLUMN parent_id UUID REFERENCES nodes (id) ON DELETE SET NULL,
    ADD COLUMN created_folder_ids JSONB;

CREATE INDEX idx_import_sessions_document_id ON import_sessions (document_id);
