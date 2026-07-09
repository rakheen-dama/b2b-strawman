-- db/migration/tenant/V133__create_collections_tables.sql
-- Phase 83: Collections & Cash Intelligence — dunning policy settings, per-customer exemption,
-- and the chase (collection_activities) ledger.
-- Per-tenant schema (search_path = tenant). No tenant_id column (schema-per-tenant isolation).
-- Every statement is idempotent (IF NOT EXISTS) per the repo migration convention.

-- Collections policy columns on org_settings (new CollectionsSettings embeddable group).
-- NOTE: org_settings shape is pinned by OrgSettingsSchemaSnapshotTest — pin update required.
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS collections_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS collections_stage1_days integer;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS collections_stage2_days integer;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS collections_stage3_days integer;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS collections_escalate_days integer;

-- Per-customer standing exclusion from collections.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS collections_exempt boolean NOT NULL DEFAULT false;

-- The chase ledger: one row per (invoice, stage), transitions in place.
CREATE TABLE IF NOT EXISTS collection_activities (
    id                      uuid PRIMARY KEY,
    invoice_id              uuid NOT NULL,
    customer_id             uuid NOT NULL,
    stage                   varchar(20) NOT NULL,
    status                  varchar(20) NOT NULL,
    gate_id                 uuid,
    email_delivery_log_id   uuid,
    days_overdue_at_action  integer NOT NULL,
    reason                  varchar(255),
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_collection_stage  CHECK (stage IN ('STAGE_1','STAGE_2','STAGE_3','ESCALATION')),
    CONSTRAINT ck_collection_status CHECK (status IN
        ('PROPOSED','SENT','SEND_FAILED','REJECTED','CANCELLED_PAYMENT','SKIPPED','FLAGGED'))
);

-- Idempotency backbone: at most one activity per invoice per stage, ever.
CREATE UNIQUE INDEX IF NOT EXISTS ux_collection_activity_invoice_stage
    ON collection_activities (invoice_id, stage);

-- Scan hot path: "does a non-retryable activity exist for this invoice?" + payment-listener lookup.
CREATE INDEX IF NOT EXISTS ix_collection_activity_invoice_status
    ON collection_activities (invoice_id, status);

-- Customer chase-history page + debtor-book aggregation.
CREATE INDEX IF NOT EXISTS ix_collection_activity_customer_created
    ON collection_activities (customer_id, created_at DESC);

-- System-invoked skills (collections_scan / cash_digest job context) record no member.
-- Verified 2026-07-09: V122 created ai_executions.invoked_by as UUID NOT NULL; drop it.
ALTER TABLE ai_executions ALTER COLUMN invoked_by DROP NOT NULL;
