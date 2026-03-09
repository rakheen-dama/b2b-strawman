-- =============================================================================
-- V65: Add DOCX Template Support (Phase 42 -- Word Template Pipeline)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. DocumentTemplate: add format discriminator
-- -----------------------------------------------------------------------------
ALTER TABLE document_templates
    ADD COLUMN format VARCHAR(20) NOT NULL DEFAULT 'TIPTAP';

-- -----------------------------------------------------------------------------
-- 2. DocumentTemplate: add DOCX file storage fields
-- -----------------------------------------------------------------------------
ALTER TABLE document_templates
    ADD COLUMN docx_s3_key VARCHAR(500);

ALTER TABLE document_templates
    ADD COLUMN docx_file_name VARCHAR(255);

ALTER TABLE document_templates
    ADD COLUMN docx_file_size BIGINT;

-- -----------------------------------------------------------------------------
-- 3. DocumentTemplate: add discovered merge fields (JSONB)
-- -----------------------------------------------------------------------------
ALTER TABLE document_templates
    ADD COLUMN discovered_fields JSONB;

-- -----------------------------------------------------------------------------
-- 4. DocumentTemplate: constraints and indexes
-- -----------------------------------------------------------------------------
ALTER TABLE document_templates
    ADD CONSTRAINT chk_template_format CHECK (format IN ('TIPTAP', 'DOCX'));

CREATE INDEX idx_document_templates_format ON document_templates (format);

-- -----------------------------------------------------------------------------
-- 5. GeneratedDocument: add output format + DOCX S3 key
-- -----------------------------------------------------------------------------
ALTER TABLE generated_documents
    ADD COLUMN output_format VARCHAR(20) NOT NULL DEFAULT 'PDF';

ALTER TABLE generated_documents
    ADD COLUMN docx_s3_key VARCHAR(500);

-- -----------------------------------------------------------------------------
-- 6. GeneratedDocument: constraints
-- -----------------------------------------------------------------------------
ALTER TABLE generated_documents
    ADD CONSTRAINT chk_output_format CHECK (output_format IN ('PDF', 'DOCX'));
