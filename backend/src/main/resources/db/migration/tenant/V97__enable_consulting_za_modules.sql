-- Backfill enabled_modules for consulting-za tenants that were provisioned before the
-- profile JSON was corrected to enable `resource_planning` and `automation_builder`.
--
-- Root causes:
--   * GAP-C-04 — TeamUtilizationWidget / UtilizationService.getTeamUtilization() calls
--     moduleGuard.requireModule("resource_planning") as its first statement. consulting-za
--     shipped with `enabledModules: []`, so every dashboard render for every user 403s and
--     the Next.js server action surfaces a generic 500. Also breaks Day 75 wow moment.
--   * GAP-C-07 — Settings > Automations UI gated behind `automation_builder`. Pack-installed
--     automation rules (from `automation-consulting-za` pack) were invisible in the UI.
--
-- Idempotency:
--   * Scoped to tenants whose org_settings.vertical_profile = 'consulting-za' (untouched for
--     legal-za / accounting-za / consulting-generic).
--   * Uses jsonb containment checks so re-running the migration is a no-op for tenants that
--     already have both modules.
--   * Preserves any other modules the tenant may have enabled manually via Settings → Features
--     (e.g. `bulk_billing`) by concatenating rather than overwriting.

UPDATE org_settings
SET enabled_modules = (
    SELECT jsonb_agg(DISTINCT value)
    FROM jsonb_array_elements_text(
        COALESCE(enabled_modules, '[]'::jsonb)
        || '["resource_planning", "automation_builder"]'::jsonb
    ) AS t(value)
)
WHERE vertical_profile = 'consulting-za'
  AND NOT (
    COALESCE(enabled_modules, '[]'::jsonb) @> '["resource_planning"]'::jsonb
    AND COALESCE(enabled_modules, '[]'::jsonb) @> '["automation_builder"]'::jsonb
  );
