-- V35__create_report_definitions.sql
-- Phase 19: Reporting & Data Export

-- Report definitions for the reporting framework
CREATE TABLE report_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)  NOT NULL,
    slug            VARCHAR(100)  NOT NULL,
    description     TEXT,
    category        VARCHAR(50)   NOT NULL,
    parameter_schema JSONB         NOT NULL DEFAULT '{"parameters":[]}',
    column_definitions JSONB       NOT NULL DEFAULT '{"columns":[]}',
    template_body   TEXT          NOT NULL,
    is_system       BOOLEAN       NOT NULL DEFAULT true,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_report_definitions_slug UNIQUE (slug)
);

-- Index on category for grouped listing
CREATE INDEX idx_report_definitions_category ON report_definitions (category);

-- Index on is_system for filtering system vs custom reports
CREATE INDEX idx_report_definitions_system ON report_definitions (is_system);

-- Add report pack status tracking to org_settings
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS report_pack_status JSONB;
