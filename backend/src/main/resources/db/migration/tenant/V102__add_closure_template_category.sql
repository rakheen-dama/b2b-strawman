-- Phase 67, Epic 489B — add the CLOSURE document-template category so the
-- legal-za pack can register matter-closure-letter (category=CLOSURE) without
-- violating chk_dt_category. Keeps every previously-allowed value and appends
-- CLOSURE at the end. See TemplateCategory.CLOSURE.
ALTER TABLE document_templates DROP CONSTRAINT chk_dt_category;
ALTER TABLE document_templates ADD CONSTRAINT chk_dt_category
    CHECK (category IN (
        'ENGAGEMENT_LETTER',
        'STATEMENT_OF_WORK',
        'COVER_LETTER',
        'PROJECT_SUMMARY',
        'NDA',
        'PROPOSAL',
        'REPORT',
        'COMPLIANCE',
        'CLOSURE',
        'OTHER'
    ));
