-- Add COMPLIANCE to document_templates category check constraint
ALTER TABLE document_templates DROP CONSTRAINT chk_dt_category;
ALTER TABLE document_templates ADD CONSTRAINT chk_dt_category
    CHECK (category IN ('ENGAGEMENT_LETTER', 'STATEMENT_OF_WORK', 'COVER_LETTER', 'PROJECT_SUMMARY', 'NDA', 'PROPOSAL', 'REPORT', 'COMPLIANCE', 'OTHER'));

-- Add ORGANIZATION to document_templates primary_entity_type check constraint
ALTER TABLE document_templates DROP CONSTRAINT chk_dt_entity_type;
ALTER TABLE document_templates ADD CONSTRAINT chk_dt_entity_type
    CHECK (primary_entity_type IN ('PROJECT', 'CUSTOMER', 'INVOICE', 'ORGANIZATION'));

-- Add ORGANIZATION to generated_documents primary_entity_type check constraint
ALTER TABLE generated_documents DROP CONSTRAINT chk_gd_entity_type;
ALTER TABLE generated_documents ADD CONSTRAINT chk_gd_entity_type
    CHECK (primary_entity_type IN ('PROJECT', 'CUSTOMER', 'INVOICE', 'ORGANIZATION'));
