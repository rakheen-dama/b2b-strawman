-- GAP-L-61-followup (E9.2): backfill enabled_modules for legal-za tenants that were
-- provisioned before bulk_billing was added to the legal-za vertical profile JSON.
--
-- Root cause:
--   * Most legal-za firms invoice clients in monthly batches rather than one-off.
--     Bulk Billing Runs (module_id = "bulk_billing", see VerticalModuleRegistry +
--     BillingRunService.MODULE_ID) was built but firms had to flip the feature flag
--     manually under Settings → Features. legal-za.json shipped without the module
--     in `enabledModules`, so existing tenants never picked it up via
--     PackReconciliationRunner / VerticalProfileReconciliationSeeder.
--
-- Note on key naming:
--   * The horizontal-module key in the registry is `bulk_billing` (not `billing_runs` —
--     that is the JPA table name). VerticalModuleGuard.requireModule("bulk_billing")
--     is the canonical check used by BillingRunService.
--
-- Idempotency:
--   * Scoped to tenants whose org_settings.vertical_profile = 'legal-za' (untouched for
--     accounting-za / consulting-za / consulting-generic).
--   * Uses jsonb containment check so re-running the migration is a no-op for tenants
--     that already have the module.
--   * Preserves any other modules the tenant may have enabled manually via Settings →
--     Features by deduplicating with jsonb_agg(DISTINCT value) rather than overwriting.

UPDATE org_settings
SET enabled_modules = (
    SELECT jsonb_agg(DISTINCT value)
    FROM jsonb_array_elements_text(
        COALESCE(enabled_modules, '[]'::jsonb)
        || '["bulk_billing"]'::jsonb
    ) AS t(value)
)
WHERE vertical_profile = 'legal-za'
  AND NOT (COALESCE(enabled_modules, '[]'::jsonb) @> '["bulk_billing"]'::jsonb);
