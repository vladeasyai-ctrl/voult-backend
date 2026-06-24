CREATE TABLE spaces (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    preset_id   VARCHAR(64),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    settings    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_spaces_sort_order ON spaces (sort_order);

ALTER TABLE nodes ADD COLUMN space_id UUID REFERENCES spaces (id) ON DELETE CASCADE;

-- Map former top-level FOLDER nodes to new spaces.
CREATE TEMP TABLE root_space_map ON COMMIT DROP AS
SELECT n.id                                                            AS old_root_id,
       gen_random_uuid()                                               AS space_id,
       n.name                                                          AS space_name,
       n.created_at                                                    AS created_at,
       n.updated_at                                                    AS updated_at,
       (ROW_NUMBER() OVER (ORDER BY n.name) - 1)::INTEGER              AS sort_order
FROM nodes n
WHERE n.parent_id IS NULL
  AND n.type = 'FOLDER';

INSERT INTO spaces (id, name, sort_order, created_at, updated_at)
SELECT space_id, space_name, sort_order, created_at, updated_at
FROM root_space_map;

-- Assign space_id to entire subtrees of former root folders.
WITH RECURSIVE subtree AS (SELECT n.id, m.space_id
                             FROM nodes n
                                      JOIN root_space_map m ON n.id = m.old_root_id
                             UNION ALL
                             SELECT c.id, s.space_id
                             FROM nodes c
                                      JOIN subtree s ON c.parent_id = s.id)
UPDATE nodes n
SET space_id = s.space_id
FROM subtree s
WHERE n.id = s.id;

-- Promote direct children of former roots to root branches within their space.
UPDATE nodes n
SET parent_id = NULL
FROM root_space_map m
WHERE n.parent_id = m.old_root_id;

-- Remove former container nodes (children already reparented).
DELETE
FROM nodes n USING root_space_map m
WHERE n.id = m.old_root_id;

-- Orphan nodes at vault root (e.g. DOCUMENT without a folder parent).
INSERT INTO spaces (id, name, sort_order)
SELECT gen_random_uuid(), 'Default', 0
WHERE EXISTS (SELECT 1 FROM nodes WHERE space_id IS NULL);

UPDATE nodes
SET space_id = (SELECT id FROM spaces WHERE name = 'Default' ORDER BY sort_order LIMIT 1)
WHERE space_id IS NULL;

ALTER TABLE nodes ALTER COLUMN space_id SET NOT NULL;

CREATE INDEX idx_nodes_space_id ON nodes (space_id);
CREATE INDEX idx_nodes_space_parent ON nodes (space_id, parent_id);
