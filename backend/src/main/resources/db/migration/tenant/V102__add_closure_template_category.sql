-- Phase 67, Epic 489B — add the CLOSURE document-template category so the
-- legal-za pack can register matter-closure-letter (category=CLOSURE) without
-- violating chk_dt_category. Keeps every previously-allowed value and appends
-- CLOSURE at the end. See TemplateCategory.CLOSURE.
--
-- Idempotent: drop+re-add chk_dt_category only when present / absent respectively.
-- This is required by the tenant-migration coding guideline so re-runs against
-- a schema that already has the constraint (e.g. partial re-provision) succeed.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_dt_category'
          AND conrelid = 'document_templates'::regclass
    ) THEN
        ALTER TABLE document_templates DROP CONSTRAINT chk_dt_category;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_dt_category'
          AND conrelid = 'document_templates'::regclass
    ) THEN
        ALTER TABLE document_templates
            ADD CONSTRAINT chk_dt_category
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
    END IF;
END $$;
