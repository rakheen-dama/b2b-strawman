-- V28: Create saved_views table
-- Phase 12 -- Saved Views (independent of V24/V25)

-- =============================================================================
-- saved_views
-- =============================================================================

CREATE TABLE IF NOT EXISTS saved_views (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type       VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    filters           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    columns           JSONB,
    shared            BOOLEAN      NOT NULL DEFAULT false,
    created_by        UUID         NOT NULL,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Indexes for list queries
CREATE INDEX IF NOT EXISTS idx_saved_view_type
    ON saved_views(entity_type, shared);
CREATE INDEX IF NOT EXISTS idx_saved_view_creator
    ON saved_views(created_by, entity_type);

-- Partial unique: no duplicate shared view names per entity_type
CREATE UNIQUE INDEX IF NOT EXISTS uq_saved_view_shared_name
    ON saved_views(entity_type, name) WHERE shared = true;

-- Partial unique: no duplicate personal view names per entity_type+creator
CREATE UNIQUE INDEX IF NOT EXISTS uq_saved_view_personal_name
    ON saved_views(entity_type, name, created_by) WHERE shared = false;
