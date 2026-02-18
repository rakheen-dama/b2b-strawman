-- V25: Create tags and entity_tags tables
-- Phase 11 -- Tags (independent of V24 field definitions)

-- =============================================================================
-- tags
-- =============================================================================

CREATE TABLE IF NOT EXISTS tags (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(50)  NOT NULL,
    slug              VARCHAR(50)  NOT NULL,
    color             VARCHAR(7),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_tag_slug UNIQUE (slug)
);

-- =============================================================================
-- entity_tags
-- =============================================================================

CREATE TABLE IF NOT EXISTS entity_tags (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_id            UUID         NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    entity_type       VARCHAR(20)  NOT NULL,
    entity_id         UUID         NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_entity_tag UNIQUE (tag_id, entity_type, entity_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_tag_entity
    ON entity_tags(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_tag_tag
    ON entity_tags(tag_id, entity_type);
