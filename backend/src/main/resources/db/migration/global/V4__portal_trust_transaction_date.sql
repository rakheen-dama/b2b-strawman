-- G89-F39: portal trust rows carry the firm transaction's SA business date (transaction_date, a
-- plain DATE: no time-zone shifting) and the firm row's recorded time (recorded_at =
-- trust_transactions.created_at). The portal ledger, the home trust tile and the trust email show
-- transaction_date; rows order by transaction_date, recorded_at, id -- the firm ledger's order, so
-- the projected running_balance reads in sequence. occurred_at (approval / recorded instant) stays
-- for the balance snapshot's last_transaction_at.
ALTER TABLE portal.portal_trust_transaction ADD COLUMN IF NOT EXISTS transaction_date date;
ALTER TABLE portal.portal_trust_transaction ADD COLUMN IF NOT EXISTS recorded_at timestamp with time zone;

-- Interim values for rows projected before this migration (the firm ledger lives in the tenant
-- schemas, which a global migration must not read): the SA calendar day of occurred_at -- what the
-- portal showed until now. TrustLedgerPortalSyncService.recomputeMatter replays the whole matter
-- from the firm ledger on that matter's next trust event (deposit, transfer, approval, interest
-- posting, reconciliation) and rewrites the true transaction_date / recorded_at.
UPDATE portal.portal_trust_transaction
   SET transaction_date = (occurred_at AT TIME ZONE 'Africa/Johannesburg')::date
 WHERE transaction_date IS NULL;
UPDATE portal.portal_trust_transaction
   SET recorded_at = occurred_at
 WHERE recorded_at IS NULL;

ALTER TABLE portal.portal_trust_transaction ALTER COLUMN transaction_date SET NOT NULL;
ALTER TABLE portal.portal_trust_transaction ALTER COLUMN recorded_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_portal_trust_txn_customer_matter_txn_date
    ON portal.portal_trust_transaction (customer_id, matter_id, transaction_date DESC, recorded_at DESC);
