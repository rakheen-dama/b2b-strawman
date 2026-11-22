-- Phase 92 / FU-6b (rulings F-646C-1, F-652C-1; orchestrator default FU-6 D-1; reproduced by
-- BankStatementReplaceDiscardIntegrationTest): an imported bank statement can be retired — replaced
-- by a re-import, or discarded — and is never deleted. A RETIRED statement keeps its file and its
-- lines for the audit trail; it records who retired it, when and why, and (on a replace) the
-- statement that replaced it. A retired statement no longer counts anywhere a live statement does.
--
-- chk_bank_stmt_status gains 'RETIRED'. chk_bank_stmt_retired: the four who/when/why columns are
-- set exactly on a RETIRED row (replaced_by_statement_id only on a replace, so it may be null on a
-- RETIRED row and must be null on any other). Existing rows need no change: no row is RETIRED and the
-- new columns are null, so both CHECKs validate as added. No row is deleted.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS, and every constraint is guarded on pg_constraint scoped to
-- current_schema() (an unscoped guard would skip every tenant after the first).
ALTER TABLE bank_statements ADD COLUMN IF NOT EXISTS retired_at timestamp with time zone;
ALTER TABLE bank_statements ADD COLUMN IF NOT EXISTS retired_by uuid;
ALTER TABLE bank_statements ADD COLUMN IF NOT EXISTS retired_reason varchar(500);
ALTER TABLE bank_statements ADD COLUMN IF NOT EXISTS replaced_by_statement_id uuid;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
              WHERE c.conname = 'chk_bank_stmt_status' AND n.nspname = current_schema()
                AND pg_get_constraintdef(c.oid) NOT LIKE '%RETIRED%') THEN
    ALTER TABLE bank_statements DROP CONSTRAINT chk_bank_stmt_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_bank_stmt_status' AND n.nspname = current_schema()) THEN
    ALTER TABLE bank_statements ADD CONSTRAINT chk_bank_stmt_status
      CHECK (status IN ('IMPORTED', 'MATCHING_IN_PROGRESS', 'MATCHED', 'RECONCILED', 'RETIRED'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_bank_stmt_retired' AND n.nspname = current_schema()) THEN
    ALTER TABLE bank_statements ADD CONSTRAINT chk_bank_stmt_retired CHECK (
          (status = 'RETIRED') = (retired_at IS NOT NULL)
      AND (status = 'RETIRED') = (retired_by IS NOT NULL)
      AND (status = 'RETIRED') = (retired_reason IS NOT NULL)
      AND (status = 'RETIRED' OR replaced_by_statement_id IS NULL));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'bank_statements_retired_by_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE bank_statements ADD CONSTRAINT bank_statements_retired_by_fkey
      FOREIGN KEY (retired_by) REFERENCES members(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'bank_statements_replaced_by_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE bank_statements ADD CONSTRAINT bank_statements_replaced_by_fkey
      FOREIGN KEY (replaced_by_statement_id) REFERENCES bank_statements(id);
  END IF;
END $$;
