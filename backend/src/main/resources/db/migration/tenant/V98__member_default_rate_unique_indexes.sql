-- Guarantee at-most-one open MEMBER_DEFAULT billing rate and at-most-one open cost rate per
-- member at the DB level. This closes the concurrent-insert race in MemberRateSeedingService:
-- two JIT sync calls racing for the same member would both see "no existing rate" and both
-- insert, producing duplicate open-ended defaults. The unique partial indexes (plus the
-- service-level try/catch on DataIntegrityViolationException) make the seeding truly
-- idempotent under concurrency.
--
-- Scope:
--   * Applies to all tenants regardless of vertical profile — duplicate open-ended member
--     defaults are never desirable in any profile.
--   * Uses partial-index predicates that match the exact "member default, still active" shape:
--       billing_rates: project_id IS NULL AND customer_id IS NULL AND effective_to IS NULL
--       cost_rates:    effective_to IS NULL
--   * Idempotent (CREATE UNIQUE INDEX IF NOT EXISTS).
--
-- Pre-existing duplicates: none are expected on any tenant because the seeding service itself
-- is brand-new (introduced in the same branch as this migration). If a duplicate DOES exist,
-- this migration will fail at index-creation time with a clear error — that is the desired
-- behaviour; operators should investigate before re-running.

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_rates_member_default_open
    ON billing_rates (member_id)
    WHERE member_id IS NOT NULL
      AND project_id IS NULL
      AND customer_id IS NULL
      AND effective_to IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cost_rates_member_default_open
    ON cost_rates (member_id)
    WHERE effective_to IS NULL;
