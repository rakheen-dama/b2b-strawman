-- V143__drop_filing_statuses.sql
-- Phase 86 (Epic 604A, ADR-331 / ADR-332 / ADR-334): remove the Phase 51 firm-side regulatory
-- deadlines (deadline/ package: SARS/CIPC DeadlineTypeRegistry, FilingStatus, /api/deadlines/**)
-- and the regulatory_deadlines + automation_builder module ids. The portal deadlines feed
-- (global V21 portal_deadline_view, customerbackend/*Deadline*) is kept. Throwaway migration —
-- squashed into tenant V1 at zero tenants (ADR-331); no production data exists.

-- 1. filing_statuses (V81:9-36). Nothing references it — its FKs point outward to customers /
--    projects. uq_filing_status_customer_deadline_period, chk_filing_status_status and the three
--    idx_filing_statuses_* indexes go with the table.
DROP TABLE IF EXISTS filing_statuses;

-- 2. Defensive: both module ids leave VerticalModuleRegistry in the same PR. accounting-za.json
--    seeded regulatory_deadlines; V97 and Settings → Features wrote automation_builder.
--    enabled_modules is a jsonb string list; unknown ids are tolerated at runtime but would keep
--    surfacing in the session context. (@> rather than ?, which JDBC can mistake for a bind
--    placeholder — V137/V139/V140.)
UPDATE org_settings
   SET enabled_modules = enabled_modules - 'regulatory_deadlines'
 WHERE enabled_modules @> '["regulatory_deadlines"]'::jsonb;
UPDATE org_settings
   SET enabled_modules = enabled_modules - 'automation_builder'
 WHERE enabled_modules @> '["automation_builder"]'::jsonb;
