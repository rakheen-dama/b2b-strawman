-- V44__create_clause_tables.sql
-- Phase 27: Clause library tables and extensions for document clauses

-- Clause library table
CREATE TABLE clauses (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    body            TEXT         NOT NULL,
    category        VARCHAR(100) NOT NULL,
    source          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOM',
    source_clause_id UUID,
    pack_id         VARCHAR(100),
    active          BOOLEAN      NOT NULL DEFAULT true,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT clauses_slug_unique UNIQUE (slug),
    CONSTRAINT clauses_slug_format CHECK (slug ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT clauses_source_check CHECK (source IN ('SYSTEM', 'CLONED', 'CUSTOM'))
);

-- Index for clause library listing (grouped by category, ordered)
CREATE INDEX idx_clauses_active_category ON clauses (active, category, sort_order)
    WHERE active = true;

-- Index for pack management queries
CREATE INDEX idx_clauses_pack_id ON clauses (pack_id)
    WHERE pack_id IS NOT NULL;

-- Template-clause join table
CREATE TABLE template_clauses (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    template_id     UUID        NOT NULL REFERENCES document_templates(id) ON DELETE CASCADE,
    clause_id       UUID        NOT NULL REFERENCES clauses(id) ON DELETE RESTRICT,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    required        BOOLEAN     NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT template_clauses_unique UNIQUE (template_id, clause_id)
);

-- Index for loading a template's clause configuration (most common query)
CREATE INDEX idx_template_clauses_template_id ON template_clauses (template_id, sort_order);

-- Index for checking if a clause is referenced (deletion guard)
CREATE INDEX idx_template_clauses_clause_id ON template_clauses (clause_id);

-- Extend generated_documents with clause snapshot metadata
ALTER TABLE generated_documents
    ADD COLUMN clause_snapshots JSONB;

-- Extend org_settings with clause pack tracking
ALTER TABLE org_settings
    ADD COLUMN clause_pack_status JSONB;
