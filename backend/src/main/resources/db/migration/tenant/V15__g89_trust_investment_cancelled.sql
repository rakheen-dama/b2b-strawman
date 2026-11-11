-- G89-F87 (D41): a trust investment whose placement PAYMENT is rejected is CANCELLED (terminal).
-- The investment is created ACTIVE while its placement still awaits approval; rejecting the
-- placement used to leave it ACTIVE on the s86 register, in getMaturing and in the Section 35 data
-- pack with no money behind it. chk_trust_invest_status is a baseline CHECK over three values; swap
-- it for four. Idempotent, and every catalog guard is scoped to current_schema() (BASELINE.md §5).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
             WHERE c.conname = 'chk_trust_invest_status' AND n.nspname = current_schema()
               AND pg_get_constraintdef(c.oid) NOT LIKE '%CANCELLED%') THEN
    ALTER TABLE trust_investments DROP CONSTRAINT chk_trust_invest_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                 WHERE c.conname = 'chk_trust_invest_status' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT chk_trust_invest_status
      CHECK (status IN ('ACTIVE', 'MATURED', 'WITHDRAWN', 'CANCELLED'));
  END IF;
END $$;
