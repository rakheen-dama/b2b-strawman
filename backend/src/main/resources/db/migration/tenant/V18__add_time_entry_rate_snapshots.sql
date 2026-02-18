-- V20: Add rate snapshot columns to time_entries
-- Phase 8 -- Point-in-time rate snapshotting for billable/cost valuation

ALTER TABLE time_entries
    ADD COLUMN IF NOT EXISTS billing_rate_snapshot DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS billing_rate_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS cost_rate_snapshot    DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS cost_rate_currency    VARCHAR(3);

-- Constraint: if billing_rate_snapshot is set, currency must also be set (and vice versa)
-- Using a check constraint rather than NOT NULL because existing rows will have NULLs
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'chk_billing_snapshot_currency'
    AND table_name = 'time_entries'
    AND table_schema = current_schema()
  ) THEN
    ALTER TABLE time_entries
      ADD CONSTRAINT chk_billing_snapshot_currency
        CHECK (
          (billing_rate_snapshot IS NULL AND billing_rate_currency IS NULL)
          OR
          (billing_rate_snapshot IS NOT NULL AND billing_rate_currency IS NOT NULL)
        );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'chk_cost_snapshot_currency'
    AND table_name = 'time_entries'
    AND table_schema = current_schema()
  ) THEN
    ALTER TABLE time_entries
      ADD CONSTRAINT chk_cost_snapshot_currency
        CHECK (
          (cost_rate_snapshot IS NULL AND cost_rate_currency IS NULL)
          OR
          (cost_rate_snapshot IS NOT NULL AND cost_rate_currency IS NOT NULL)
        );
  END IF;
END $$;

-- Index for profitability aggregation queries
CREATE INDEX IF NOT EXISTS idx_time_entries_billing_currency
    ON time_entries (billing_rate_currency)
    WHERE billing_rate_currency IS NOT NULL;

-- Note: The existing rate_cents column is NOT removed in this migration.
-- It is deprecated and will be removed in a future migration once all
-- consumers have migrated to billing_rate_snapshot.
