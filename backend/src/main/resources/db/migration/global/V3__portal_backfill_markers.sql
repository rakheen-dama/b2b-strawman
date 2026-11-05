-- Phase 89 E4: one shared marker table for the portal read-model backfill runners
-- (PortalTaskBackfillRunner 'portal_task_backfill', PortalDeadlineBackfillRunner
-- 'portal_deadline_backfill'). The V2 portal_contact_directory_backfill table is keyed by
-- tenant_schema only and is left untouched. Markers are global because the runners iterate
-- org_schema_mapping and decide "already done?" before binding a tenant scope (Phase 88 A8).
-- Owned by 633A (633B depends on it); next free global number at build time (R6.11).
CREATE TABLE IF NOT EXISTS portal.portal_backfill_markers (
    backfill_name  varchar(64) NOT NULL,
    tenant_schema  varchar(63) NOT NULL,
    backfilled_at  timestamp with time zone DEFAULT now() NOT NULL,
    row_count      integer NOT NULL,
    CONSTRAINT portal_backfill_markers_pkey PRIMARY KEY (backfill_name, tenant_schema)
);
-- No secondary index: the only reads are the PK lookup and a per-name scan at boot.
