-- =====================================================================================
-- Phase 89 — go-live readiness. One section per slice. The first slice to merge creates this
-- file with its section; once any persistent stack (Keycloak dev DB, re-walk tenant, VPS) has
-- applied it, later sections take V11, V12, … (§14.7 rule). Every DDL guard is scoped to
-- current_schema(); every statement is idempotent; no data is rewritten.
-- =====================================================================================

-- ---------- §1 632B: payment receipt as a generated document ([ADR-364](../adr/ADR-364-payment-receipt-as-generated-document.md)) ----
-- One nullable pointer from the completed payment event to its receipt row. ON DELETE SET NULL:
-- GeneratedDocumentService.delete must not be blocked; the lazy download path regenerates.
ALTER TABLE payment_events
  ADD COLUMN IF NOT EXISTS receipt_generated_document_id uuid;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
    WHERE c.conname = 'payment_events_receipt_generated_document_id_fkey'
      AND n.nspname = current_schema()) THEN
    ALTER TABLE payment_events
      ADD CONSTRAINT payment_events_receipt_generated_document_id_fkey
      FOREIGN KEY (receipt_generated_document_id) REFERENCES generated_documents(id) ON DELETE SET NULL;
  END IF;
END $$;
-- Partial index: the only lookup is "payment events that have a receipt" (reverse lookup from a
-- GeneratedDocument delete) — most rows stay NULL, so a full index would be wasted.
CREATE INDEX IF NOT EXISTS idx_payment_events_receipt_doc
  ON payment_events (receipt_generated_document_id)
  WHERE receipt_generated_document_id IS NOT NULL;
-- Stored receipt number (R6.2): {invoiceNumber}/R{seq}, written under the invoice row lock before rendering.
-- Stored, not derived, because a reversal DELETES the payment event (InvoiceTransitionService.java:646) and a
-- derived ordinal would renumber every later receipt. The sequence is never decremented, so numbers are never reused.
ALTER TABLE payment_events
  ADD COLUMN IF NOT EXISTS receipt_number varchar(40);
ALTER TABLE invoices
  ADD COLUMN IF NOT EXISTS receipt_sequence integer NOT NULL DEFAULT 0;
