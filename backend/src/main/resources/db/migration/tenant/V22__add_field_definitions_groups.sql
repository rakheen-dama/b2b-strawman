-- V24: Create field_definitions, field_groups, field_group_members tables
-- Phase 11 — Tags, Custom Fields & Views (field definitions foundation)

-- =============================================================================
-- field_definitions
-- =============================================================================

CREATE TABLE IF NOT EXISTS field_definitions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type       VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL,
    field_type        VARCHAR(20)  NOT NULL,
    description       TEXT,
    required          BOOLEAN      NOT NULL DEFAULT false,
    default_value     JSONB,
    options           JSONB,
    validation        JSONB,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    pack_id           VARCHAR(100),
    pack_field_key    VARCHAR(100),
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_field_def_type_slug UNIQUE (entity_type, slug)
);

CREATE INDEX IF NOT EXISTS idx_field_def_type_active
    ON field_definitions(entity_type, active);
CREATE INDEX IF NOT EXISTS idx_field_def_pack
    ON field_definitions(pack_id);

-- =============================================================================
-- field_groups
-- =============================================================================

CREATE TABLE IF NOT EXISTS field_groups (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type       VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL,
    description       TEXT,
    pack_id           VARCHAR(100),
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_field_group_type_slug UNIQUE (entity_type, slug)
);

-- =============================================================================
-- field_group_members
-- =============================================================================

CREATE TABLE IF NOT EXISTS field_group_members (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_group_id        UUID         NOT NULL REFERENCES field_groups(id) ON DELETE CASCADE,
    field_definition_id   UUID         NOT NULL REFERENCES field_definitions(id) ON DELETE CASCADE,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_group_member UNIQUE (field_group_id, field_definition_id)
);
