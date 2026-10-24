-- Phase 88 U11: email → firm routing table for portal sign-in (ADR-351). A row exists iff the
-- firm has at least one ACTIVE portal contact with that email. Maintained by the application
-- (PortalContactDirectoryService) from inside tenant transactions; backfilled by
-- PortalContactDirectoryBackfillRunner, NOT by SQL here — tenant schemas are dynamically named
-- and may live on other shards, so a global migration cannot enumerate them safely.
CREATE TABLE IF NOT EXISTS portal.portal_contact_directory (
    email_lower   varchar(255) NOT NULL,
    org_id        varchar(255) NOT NULL,
    tenant_schema varchar(63)  NOT NULL,
    org_name      varchar(255) NOT NULL,
    updated_at    timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT portal_contact_directory_pkey PRIMARY KEY (email_lower, org_id)   -- natural key; its leading column is the login lookup
);

-- One row per tenant schema so the backfill runner is idempotent across restarts and shards.
CREATE TABLE IF NOT EXISTS portal.portal_contact_directory_backfill (
    tenant_schema varchar(63) NOT NULL,
    backfilled_at timestamp with time zone DEFAULT now() NOT NULL,
    row_count     integer NOT NULL,
    CONSTRAINT portal_contact_directory_backfill_pkey PRIMARY KEY (tenant_schema)
);

-- U15: the portal fee-note read model shows the balance.
ALTER TABLE portal.portal_invoices
  ADD COLUMN IF NOT EXISTS amount_paid numeric(14,2) NOT NULL DEFAULT 0;

-- The DEFAULT above zeroes pre-V2 rows. A PAID fee note gets no further InvoiceSyncEvent unless a
-- payment is reversed, so nothing would heal it; and PAID always means amount_paid = total
-- (Invoice.applyPayment / reapplyPayments cap at total), so stamp it exactly here. Pre-V2 PARTIALLY_PAID rows stay 0 until their next sync: the true value
-- lives in tenant schemas, which a global migration must not read (A8). Data only; idempotent.
UPDATE portal.portal_invoices
   SET amount_paid = total
 WHERE status = 'PAID' AND amount_paid = 0;
