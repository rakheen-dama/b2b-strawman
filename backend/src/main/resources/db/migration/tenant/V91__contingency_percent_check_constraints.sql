-- V92: Enforce 0-25% range on contingency_percent and contingency_cap_percent.
--
-- Defense in depth for the contingency fee model introduced in V90 (GAP-S5-01).
-- The Contingency Fees Act 66 of 1997 caps contingency fees at 25% of the recovery;
-- the Zod schema and Java DTO annotations enforce this on input, but we want the
-- database to reject any code path that bypasses those layers.
--
-- Idempotent: DROP IF EXISTS lets the migration rerun safely.

ALTER TABLE proposals
    DROP CONSTRAINT IF EXISTS proposals_contingency_percent_range;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_contingency_percent_range
    CHECK (
        contingency_percent IS NULL
        OR (contingency_percent >= 0 AND contingency_percent <= 25)
    );

ALTER TABLE proposals
    DROP CONSTRAINT IF EXISTS proposals_contingency_cap_percent_range;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_contingency_cap_percent_range
    CHECK (
        contingency_cap_percent IS NULL
        OR (contingency_cap_percent >= 0 AND contingency_cap_percent <= 25)
    );
