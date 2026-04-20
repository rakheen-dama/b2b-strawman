-- V105__add_portal_retainer_member_display.sql
-- Epic 496A: privacy toggle for how firm member names appear on the customer portal's
-- retainer consumption list (ADR-255). Firm-wide setting; default balances transparency
-- with privacy by showing first name + role (e.g., "Alice (Attorney)").
ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS portal_retainer_member_display VARCHAR(20)
        DEFAULT 'FIRST_NAME_ROLE'
        CHECK (portal_retainer_member_display IN ('FULL_NAME','FIRST_NAME_ROLE','ROLE_ONLY','ANONYMISED'));
