CREATE TABLE nodes (
    id          UUID PRIMARY KEY,
    parent_id   UUID REFERENCES nodes (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('FOLDER', 'DOCUMENT')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_nodes_parent_id ON nodes (parent_id);
CREATE INDEX idx_nodes_type ON nodes (type);

CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    node_id     UUID         NOT NULL UNIQUE REFERENCES nodes (id) ON DELETE CASCADE,
    title       VARCHAR(500) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_title ON documents (title);

CREATE TABLE assets (
    id          UUID PRIMARY KEY,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    mime_type   VARCHAR(255) NOT NULL,
    size        BIGINT       NOT NULL,
    checksum    VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE document_versions (
    id          UUID PRIMARY KEY,
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    asset_id    UUID        NOT NULL REFERENCES assets (id) ON DELETE RESTRICT,
    version     INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, version)
);

CREATE INDEX idx_document_versions_document_id ON document_versions (document_id);
