-- Phase 92 / 648C (ADR-379; architecture §16.2.7, the trust_accounts part of §16.7.7): a trust
-- investment account (account_type = 'INVESTMENT') carries the basis of the money it holds —
-- FIRM_DISCRETION (section 86(3)) or CLIENT_INSTRUCTION (section 86(4)) — non-null exactly on
-- INVESTMENT accounts. Existing INVESTMENT accounts (test and collections fixtures; no real tenant
-- holds one) default to FIRM_DISCRETION. GENERAL and SECTION_86 rows (the demo seeder's main account
-- is SECTION_86) stay NULL. No row is deleted; no index (the column is never a lookup key). The
-- trust_investments half of §16.7.7 is 648A's migration. Idempotent: ADD COLUMN IF NOT EXISTS, the
-- UPDATE touches only NULL rows, and the constraint is guarded on pg_constraint scoped to
-- current_schema(). The INVESTMENT branch tests investment_basis IS NOT NULL explicitly: with a NULL
-- basis, `investment_basis IN (...)` is NULL, the whole expression is NULL, and a CHECK passes on
-- NULL — without it an INVESTMENT row with no basis would be accepted (§16.7.7's text has that gap).
ALTER TABLE trust_accounts ADD COLUMN IF NOT EXISTS investment_basis varchar(20);

UPDATE trust_accounts SET investment_basis = 'FIRM_DISCRETION'
 WHERE account_type = 'INVESTMENT' AND investment_basis IS NULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_account_investment_basis' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_accounts ADD CONSTRAINT chk_trust_account_investment_basis CHECK (
         (account_type = 'INVESTMENT' AND investment_basis IS NOT NULL
            AND investment_basis IN ('FIRM_DISCRETION', 'CLIENT_INSTRUCTION'))
      OR (account_type <> 'INVESTMENT' AND investment_basis IS NULL));
  END IF;
END $$;
