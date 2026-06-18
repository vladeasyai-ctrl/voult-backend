-- Move file reference from document_versions to documents
ALTER TABLE documents
    ADD COLUMN asset_id UUID REFERENCES assets (id) ON DELETE RESTRICT;

UPDATE documents d
SET asset_id = sub.asset_id
FROM (
    SELECT DISTINCT ON (document_id) document_id, asset_id
    FROM document_versions
    ORDER BY document_id, version DESC
) sub
WHERE d.id = sub.document_id;

DELETE FROM documents WHERE asset_id IS NULL;

ALTER TABLE documents
    ALTER COLUMN asset_id SET NOT NULL;

ALTER TABLE documents
    ADD CONSTRAINT documents_asset_id_unique UNIQUE (asset_id);

DROP TABLE IF EXISTS document_versions;

-- AI enrichment fields on document_metadata
ALTER TABLE document_metadata
    ADD COLUMN IF NOT EXISTS ai_summary TEXT,
    ADD COLUMN IF NOT EXISTS ai_tags TEXT[],
    ADD COLUMN IF NOT EXISTS ai_status VARCHAR(30)
        CHECK (ai_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    ADD COLUMN IF NOT EXISTS ai_processed_at TIMESTAMPTZ;

-- Staging imports before user confirms placement
CREATE TABLE import_sessions (
    id            UUID PRIMARY KEY,
    asset_id      UUID         NOT NULL UNIQUE REFERENCES assets (id) ON DELETE CASCADE,
    status        VARCHAR(30)  NOT NULL
        CHECK (status IN ('UPLOADED', 'ANALYZING', 'PROPOSAL_READY', 'FAILED', 'CONFIRMED', 'DISCARDED')),
    proposal      JSONB,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_sessions_status ON import_sessions (status);

-- Extend module job types for AI pipeline
ALTER TABLE module_jobs DROP CONSTRAINT IF EXISTS module_jobs_module_type_check;
ALTER TABLE module_jobs ADD CONSTRAINT module_jobs_module_type_check
    CHECK (module_type IN (
        'OCR', 'CLASSIFICATION', 'FULL_TEXT_SEARCH', 'TELEGRAM', 'AI_ANALYSIS', 'AI_IMPORT'
    ));
