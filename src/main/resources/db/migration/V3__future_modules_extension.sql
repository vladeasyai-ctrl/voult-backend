-- Extension points for future modules (OCR, AI Classification, Full Text Search, Telegram)

CREATE TABLE document_metadata (
    id          UUID PRIMARY KEY,
    document_id UUID        NOT NULL UNIQUE REFERENCES documents (id) ON DELETE CASCADE,
    ocr_text    TEXT,
    ocr_status  VARCHAR(30) CHECK (ocr_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    classification_label VARCHAR(255),
    classification_confidence DECIMAL(5, 4),
    search_vector TSVECTOR,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_metadata_search ON document_metadata USING GIN (search_vector);

CREATE TABLE module_jobs (
    id          UUID PRIMARY KEY,
    module_type VARCHAR(50) NOT NULL CHECK (module_type IN ('OCR', 'CLASSIFICATION', 'FULL_TEXT_SEARCH', 'TELEGRAM')),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID        NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    payload     JSONB,
    result      JSONB,
    error_message TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_module_jobs_status ON module_jobs (module_type, status);
CREATE INDEX idx_module_jobs_entity ON module_jobs (entity_type, entity_id);
