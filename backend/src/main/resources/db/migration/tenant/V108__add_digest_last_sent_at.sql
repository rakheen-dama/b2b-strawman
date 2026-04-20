-- V108__add_digest_last_sent_at.sql
-- Epic 498B: firm-level timestamp tracking when the portal weekly-digest scheduler
-- most recently sent at least one digest email for this tenant. Consumed by the
-- BIWEEKLY cadence code path to skip alternate Mondays (12-day window). WEEKLY
-- ignores this column; OFF never runs. Null until the first successful send.
ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS digest_last_sent_at TIMESTAMPTZ;
