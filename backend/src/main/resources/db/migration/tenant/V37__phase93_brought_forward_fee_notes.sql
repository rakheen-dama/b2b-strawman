-- Phase 93 / 658A (architecture §17.3.10, §17.7.7, ADR-390): brought-forward fee-note provenance. Nullable column only.
-- Tenant V37 (guide V35; task file V36; V36 = 657C take-on reports; next free at build time, P-1). No CASCADE.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS take_on_batch_id uuid;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'invoices_take_on_batch_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE invoices ADD CONSTRAINT invoices_take_on_batch_fkey FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id);
  END IF;
  -- A brought-forward fee note is never a draft, approved-unsent or partly paid at import; it is SENT or later.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_invoice_brought_forward_status' AND n.nspname = current_schema()) THEN
    ALTER TABLE invoices ADD CONSTRAINT chk_invoice_brought_forward_status CHECK (
      take_on_batch_id IS NULL OR status IN ('SENT', 'PARTIALLY_PAID', 'PAID', 'VOID'));
  END IF;
END $$;
