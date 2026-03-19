-- V79: Add jurisdiction snapshot and deadline override to DSAR
ALTER TABLE data_subject_requests
    ADD COLUMN IF NOT EXISTS jurisdiction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS deadline_days_override INTEGER;

-- Index for jurisdiction reporting
CREATE INDEX IF NOT EXISTS idx_dsr_jurisdiction
    ON data_subject_requests (jurisdiction)
    WHERE jurisdiction IS NOT NULL;
