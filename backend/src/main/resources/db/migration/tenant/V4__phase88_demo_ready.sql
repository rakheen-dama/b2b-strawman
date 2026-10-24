-- =====================================================================================
-- Phase 88 — demo-ready root-cause closure. One section per unit.
-- Sections are appended to V4 only while no persistent stack has applied it; after that
-- (Keycloak dev DB, E6 re-walk tenant, VPS) later sections take V5, V6, … (§13.7 rule).
-- Every DDL guard is scoped to current_schema(); every DML is idempotent.
-- =====================================================================================

-- ---------- §1 U1: canonical project roles ------------------------------------------
-- project_role was a free varchar; template instantiation wrote 'LEAD' and the demo seeder
-- 'CONTRIBUTOR' while thirteen readers and the one-lead partial index expect 'lead'/'member'.
-- Order matters (ADR-345 rationale 4): the partial unique index is lowercase-only, so a matter
-- may hold a 'lead' (ProjectService) and a 'LEAD' (template) at once. Lower-casing first would
-- violate the index. So: (a) map strays, (b) demote duplicate leads case-insensitively keeping
-- the earliest (created_at, id) — the creator or chosen lead, (c) only then lower-case, (d) CHECK.

-- (a) anything outside lead/member (e.g. the demo seeder's 'CONTRIBUTOR') becomes member.
UPDATE project_members
SET project_role = 'member'
WHERE lower(project_role) NOT IN ('lead', 'member');

-- (b) demote duplicate leads, comparing case-insensitively, before any row is lower-cased.
UPDATE project_members pm
SET project_role = 'member'
WHERE upper(pm.project_role) = 'LEAD'
  AND EXISTS (
    SELECT 1 FROM project_members o
    WHERE o.project_id = pm.project_id
      AND o.id <> pm.id
      AND upper(o.project_role) = 'LEAD'
      AND (o.created_at, o.id) < (pm.created_at, pm.id));

-- (c) now at most one lead per matter remains in any casing; lower-case cannot collide.
UPDATE project_members
SET project_role = lower(project_role)
WHERE project_role <> lower(project_role);

-- (d) the CHECK.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
    WHERE c.conname = 'chk_project_members_role' AND n.nspname = current_schema()) THEN
    ALTER TABLE project_members
      ADD CONSTRAINT chk_project_members_role CHECK (project_role IN ('lead', 'member'));
  END IF;
END $$;

-- ---------- §2 U3: firm payment terms + due-date backfill ---------------------------
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS default_payment_terms_days integer NOT NULL DEFAULT 30;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.conname = 'chk_org_settings_payment_terms' AND n.nspname = current_schema()) THEN
    ALTER TABLE org_settings ADD CONSTRAINT chk_org_settings_payment_terms
      CHECK (default_payment_terms_days BETWEEN 0 AND 365);
  END IF;
END $$;

-- Fee notes approved/sent before a due date was ever computed: issue date + firm terms.
-- The debtor book, aging, cash digest and dunning scan all predicate on due_date IS NOT NULL,
-- so without this the walked tenant shows "No client owes fees." for every fee note it sent.
-- COALESCE sits OUTSIDE the scalar subquery (authorised deviation from §13.7.1): a schema with no
-- org_settings row makes the subquery yield NULL, and date + NULL would leave due_date NULL; the
-- outer COALESCE still backfills with 30 there. Identical result whenever the row exists.
UPDATE invoices i
SET due_date = COALESCE(i.issue_date, (i.created_at AT TIME ZONE 'UTC')::date)
               + COALESCE((SELECT s.default_payment_terms_days FROM org_settings s LIMIT 1), 30)
WHERE i.status IN ('APPROVED', 'SENT')
  AND i.due_date IS NULL;

-- ---------- §3 U15: payment balance model --------------------------------------------
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS amount_paid numeric(14,2) NOT NULL DEFAULT 0;

-- Existing PAID rows were always full settlements (the old service posted invoice.total).
-- total > 0 keeps the statement a true no-op on re-run: a zero-total PAID row already has
-- amount_paid = total = 0, so rewriting it would change nothing but its row version
-- (authorised deviation from §13.7.1, like §2's COALESCE; value-equivalent).
UPDATE invoices SET amount_paid = total WHERE status = 'PAID' AND amount_paid = 0 AND total > 0;

DO $$
BEGIN
  -- chk_invoice_status is a baseline CHECK over five values; swap it for six (PARTIALLY_PAID).
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
             WHERE c.conname = 'chk_invoice_status' AND n.nspname = current_schema()
               AND pg_get_constraintdef(c.oid) NOT LIKE '%PARTIALLY_PAID%') THEN
    ALTER TABLE invoices DROP CONSTRAINT chk_invoice_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.conname = 'chk_invoice_status' AND n.nspname = current_schema()) THEN
    ALTER TABLE invoices ADD CONSTRAINT chk_invoice_status
      CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'PARTIALLY_PAID', 'PAID', 'VOID'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.conname = 'chk_invoices_amount_paid' AND n.nspname = current_schema()) THEN
    ALTER TABLE invoices ADD CONSTRAINT chk_invoices_amount_paid
      CHECK (amount_paid >= 0 AND amount_paid <= total);
  END IF;
END $$;

ALTER TABLE payment_events ADD COLUMN IF NOT EXISTS payment_method varchar(20);
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.conname = 'chk_payment_events_method' AND n.nspname = current_schema()) THEN
    ALTER TABLE payment_events ADD CONSTRAINT chk_payment_events_method
      CHECK (payment_method IS NULL OR payment_method IN ('EFT','CARD','CASH','TRUST_TRANSFER','XERO','OTHER'));
  END IF;
END $$;
-- Debtor-book and aging reads filter on (status, due_date) and sum (total - amount_paid);
-- idx_invoices_status + idx_invoices_customer_status already cover the predicate.
