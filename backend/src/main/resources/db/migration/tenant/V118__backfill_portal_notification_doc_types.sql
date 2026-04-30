-- V118__backfill_portal_notification_doc_types.sql
-- OBS-2107: Backfill empty portal_notification_doc_types for existing tenants.
--
-- V117 introduced a JSONB DEFAULT for org_settings.portal_notification_doc_types,
-- but Postgres DEFAULTs only apply to NEW INSERTs — pre-existing org_settings
-- rows (created before V117 ran) retained empty allowlists. As a result
-- PortalDocumentNotificationHandler.process skipped with
-- "per-tenant allowlist empty" for every legacy tenant (e.g. Mathebula's
-- tenant_5039f2d497cf), so portal contacts never received per-event document
-- emails on top of the weekly digest.
--
-- This migration backfills any rows where the column is NULL or an empty JSON
-- array to the canonical default seed declared in V117. It is idempotent:
-- re-running has no effect because rows that already contain the default (or
-- any non-empty list a tenant has explicitly configured) are skipped.
--
-- The canonical default mirrors V117 verbatim:
--   ["matter-closure-letter", "statement-of-account"]

UPDATE org_settings
SET portal_notification_doc_types = '["matter-closure-letter", "statement-of-account"]'::jsonb
WHERE portal_notification_doc_types IS NULL
   OR portal_notification_doc_types = '[]'::jsonb;
