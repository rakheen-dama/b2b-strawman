-- V144__drop_rate_pack_status.sql
-- Phase 86 (Epic 604C, ADR-331 / ADR-332): remove rate packs — only the accounting-za,
-- consulting-generic and consulting-za rate packs ever existed (no legal pack; §11.3.11 row 3).
-- RatePackSeeder/RatePackDefinition and PackStatusSettings.ratePackStatus are removed in the same
-- PR. Throwaway migration — squashed into tenant V1 at zero tenants (ADR-331).
--
-- No pack_install cleanup (595B §4): the table is pack_install (singular) and pack_type only ever
-- holds DOCUMENT_TEMPLATE / AUTOMATION_TEMPLATE — "rate" was a seeder log label, never persisted.
-- Org-level billing_rates rows (member_id NULL) written by RatePackSeeder are ordinary,
-- user-editable rate-card rows and are left in place (not pack metadata).

-- org_settings.rate_pack_status (V81:50). OrgSettingsSchemaSnapshotTest pin updated in the same
-- PR (§11.7(f)).
ALTER TABLE org_settings DROP COLUMN IF EXISTS rate_pack_status;
