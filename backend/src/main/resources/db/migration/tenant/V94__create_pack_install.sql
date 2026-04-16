-- V94__create_pack_install.sql
--
-- Phase 65: Kazi Packs Catalog & Install Pipeline
--
-- Creates the pack_install table to track installed packs per tenant.
-- Adds source_pack_install_id FK and content_hash columns to
-- document_templates and automation_rules.

-- 1. Create pack_install table
CREATE TABLE IF NOT EXISTS pack_install (
    id                      UUID            DEFAULT gen_random_uuid() PRIMARY KEY,
    pack_id                 VARCHAR(128)    NOT NULL,
    pack_type               VARCHAR(64)     NOT NULL,
    pack_version            VARCHAR(32)     NOT NULL,
    pack_name               VARCHAR(256)    NOT NULL,
    installed_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    installed_by_member_id  UUID,
    item_count              INT             NOT NULL DEFAULT 0,

    CONSTRAINT uq_pack_install_pack_id UNIQUE (pack_id)
);

-- 2. Add source_pack_install_id to document_templates
ALTER TABLE document_templates
    ADD COLUMN IF NOT EXISTS source_pack_install_id UUID
        REFERENCES pack_install(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_document_templates_source_pack_install
    ON document_templates (source_pack_install_id)
    WHERE source_pack_install_id IS NOT NULL;

-- 3. Add content_hash to document_templates
ALTER TABLE document_templates
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- 4. Add source_pack_install_id to automation_rules
ALTER TABLE automation_rules
    ADD COLUMN IF NOT EXISTS source_pack_install_id UUID
        REFERENCES pack_install(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_automation_rules_source_pack_install
    ON automation_rules (source_pack_install_id)
    WHERE source_pack_install_id IS NOT NULL;

-- 5. Add content_hash to automation_rules
ALTER TABLE automation_rules
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
