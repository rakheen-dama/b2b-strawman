-- V15: Create comments table and audit_events expression index
-- Epic 59A — Comments for tasks and documents, plus activity feed index

CREATE TABLE IF NOT EXISTS comments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(20)  NOT NULL,
    entity_id           UUID         NOT NULL,
    project_id          UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    author_member_id    UUID         NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    body                TEXT         NOT NULL,
    visibility          VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL',
    parent_id           UUID         REFERENCES comments(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_comment_entity_type CHECK (entity_type IN ('TASK', 'DOCUMENT')),
    CONSTRAINT chk_comment_visibility CHECK (visibility IN ('INTERNAL', 'EXTERNAL'))
);

-- Indexes for primary query patterns
CREATE INDEX IF NOT EXISTS idx_comments_entity
    ON comments (entity_type, entity_id, created_at);

CREATE INDEX IF NOT EXISTS idx_comments_project
    ON comments (project_id, created_at);

-- Expression index on audit_events for activity feed project filtering
CREATE INDEX IF NOT EXISTS idx_audit_project
    ON audit_events ((details->>'project_id'));
