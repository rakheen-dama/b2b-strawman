-- Phase 92 / 648D (ADR-379, §16.6.3, §16.7.8 global part): the portal shows a client's section 86(4)
-- invested balance beside the general balance, and every projected trust row names the ledger it
-- moved. A global migration never reads tenant schemas: rows projected before this migration keep
-- the defaults -- ledger 'CLIENT' and invested_balance 0 -- which are right for every one of them,
-- because until 648D the sync admitted CLIENT-ledger rows only (648A, open question #8). A matter
-- that already holds a posted section 86(4) investment shows its invested balance from that matter's
-- next trust event (TrustLedgerPortalSyncService.recomputeMatter replays the whole matter); dev and
-- e2e stacks are reset (R12). transaction_type stays varchar(20): every Phase 92 type name fits (R5).
-- Idempotent (ADD COLUMN IF NOT EXISTS); no catalog guard needed; no new table, so
-- PortalReadModelOrgScopingGuardTest is unaffected; global V1 and global-baseline.snapshot unchanged.
ALTER TABLE portal.portal_trust_balance
  ADD COLUMN IF NOT EXISTS invested_balance numeric(15,2) NOT NULL DEFAULT 0;
ALTER TABLE portal.portal_trust_transaction
  ADD COLUMN IF NOT EXISTS ledger varchar(20) NOT NULL DEFAULT 'CLIENT';
