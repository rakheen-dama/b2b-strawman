-- V29: Create document_templates and generated_documents tables
-- Phase 12 — Document Templates & Generation

-- =============================================================================
-- document_templates
-- =============================================================================

CREATE TABLE IF NOT EXISTS document_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    slug                VARCHAR(200) NOT NULL UNIQUE,
    description         TEXT,
    category            VARCHAR(30) NOT NULL,
    primary_entity_type VARCHAR(20) NOT NULL,
    content             TEXT NOT NULL,
    css                 TEXT,
    source              VARCHAR(20) NOT NULL DEFAULT 'ORG_CUSTOM',
    source_template_id  UUID REFERENCES document_templates(id) ON DELETE SET NULL,
    pack_id             VARCHAR(100),
    pack_template_key   VARCHAR(100),
    active              BOOLEAN NOT NULL DEFAULT true,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_dt_category CHECK (category IN ('ENGAGEMENT_LETTER', 'STATEMENT_OF_WORK', 'COVER_LETTER', 'PROJECT_SUMMARY', 'NDA', 'PROPOSAL', 'REPORT', 'OTHER')),
    CONSTRAINT chk_dt_entity_type CHECK (primary_entity_type IN ('PROJECT', 'CUSTOMER', 'INVOICE')),
    CONSTRAINT chk_dt_source CHECK (source IN ('PLATFORM', 'ORG_CUSTOM')),
    CONSTRAINT chk_dt_slug_format CHECK (slug ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT chk_dt_pack_fields CHECK ((pack_id IS NULL AND pack_template_key IS NULL) OR (pack_id IS NOT NULL AND pack_template_key IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_document_templates_category
    ON document_templates (category, active);

CREATE INDEX IF NOT EXISTS idx_document_templates_entity_type
    ON document_templates (primary_entity_type, active);

CREATE INDEX IF NOT EXISTS idx_document_templates_pack
    ON document_templates (pack_id) WHERE pack_id IS NOT NULL;

-- =============================================================================
-- generated_documents
-- =============================================================================

CREATE TABLE IF NOT EXISTS generated_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id         UUID NOT NULL REFERENCES document_templates(id),
    primary_entity_type VARCHAR(20) NOT NULL,
    primary_entity_id   UUID NOT NULL,
    document_id         UUID REFERENCES documents(id) ON DELETE SET NULL,
    file_name           VARCHAR(500) NOT NULL,
    s3_key              VARCHAR(1000) NOT NULL,
    file_size           BIGINT NOT NULL,
    generated_by        UUID NOT NULL REFERENCES members(id),
    context_snapshot    JSONB,
    generated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_gd_entity_type CHECK (primary_entity_type IN ('PROJECT', 'CUSTOMER', 'INVOICE')),
    CONSTRAINT chk_gd_file_size CHECK (file_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_generated_documents_entity
    ON generated_documents (primary_entity_type, primary_entity_id);

CREATE INDEX IF NOT EXISTS idx_generated_documents_template
    ON generated_documents (template_id);

CREATE INDEX IF NOT EXISTS idx_generated_documents_generated_by
    ON generated_documents (generated_by);

-- =============================================================================
-- org_settings: add document branding columns
-- =============================================================================

ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS logo_s3_key VARCHAR(500);
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS brand_color VARCHAR(7);
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS document_footer_text TEXT;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS template_pack_status JSONB;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_brand_color_format') THEN
    ALTER TABLE org_settings ADD CONSTRAINT chk_brand_color_format
      CHECK (brand_color IS NULL OR brand_color ~ '^#[0-9a-fA-F]{6}$');
  END IF;
END $$;
