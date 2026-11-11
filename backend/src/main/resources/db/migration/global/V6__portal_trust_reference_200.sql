-- G89-F36: the portal trust ledger's reference is as wide as the firm's.
-- tenant trust_transactions.reference is varchar(200) (TrustTransaction.reference, length 200) but
-- portal.portal_trust_transaction.reference was varchar(100), with no truncation in
-- TrustLedgerPortalSyncService: a reference over 100 characters failed the portal upsert, and that
-- matter's portal sync aborted (the deposit never reached the client's portal). Widening is
-- idempotent: re-running sets the same type. The other portal trust text columns already fit their
-- firm source (transaction_type 20 = 20; description 140 is the ADR-254 sanitiser's own cap).
ALTER TABLE portal.portal_trust_transaction ALTER COLUMN reference TYPE varchar(200);
