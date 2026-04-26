-- V116: Split documents.visibility — introduce PORTAL alongside SHARED.
--
-- GAP-L-74-followup (E2.3): Every system-auto-shared document (matter closure letter,
-- statement of account) was historically tagged visibility='SHARED', indistinguishable
-- from a firm user manually clicking "share with client". Audit + analytics need to tell
-- those two apart, so we introduce a third value, 'PORTAL', reserved for system-auto-shared
-- artefacts. Both SHARED and PORTAL render on the portal — see PortalQueryService.
--
-- The documents.visibility column has no CHECK constraint today (see V10__extend_documents_scope),
-- so PORTAL is already storable. This migration is therefore data-only:
-- backfill existing system-auto-shared rows from SHARED -> PORTAL by joining through
-- generated_documents -> document_templates on the closure-letter / statement-of-account slugs.
--
-- Idempotent: running twice is a no-op (rows already at PORTAL won't match the WHERE clause).

-- Backfill: flip SHARED -> PORTAL for documents linked to closure-letter or
-- statement-of-account generated artefacts.
UPDATE documents d
   SET visibility = 'PORTAL'
  FROM generated_documents gd
  JOIN document_templates dt ON dt.id = gd.template_id
 WHERE gd.document_id = d.id
   AND d.visibility = 'SHARED'
   AND dt.slug IN ('matter-closure-letter', 'statement-of-account');

-- If the column ever grows a CHECK constraint, this is the place to drop+re-add:
--   ALTER TABLE documents DROP CONSTRAINT IF EXISTS chk_document_visibility;
--   ALTER TABLE documents ADD CONSTRAINT chk_document_visibility
--     CHECK (visibility IN ('INTERNAL','SHARED','PORTAL'));
-- Today the column is plain VARCHAR(20) DEFAULT 'INTERNAL' — no constraint to update.
