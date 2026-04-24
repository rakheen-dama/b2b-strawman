-- ============================================================
-- V110__add_section_86_trust_account_type.sql
-- GAP-L-25: Extend TrustAccountType to include SECTION_86
-- (Legal Practice Act §86) alongside GENERAL and INVESTMENT.
--
-- Index strategy: keep the existing one-primary-GENERAL uniqueness
-- intact. SECTION_86 accounts do not participate in the "primary"
-- constraint — a firm may hold multiple §86 trust accounts with
-- distinct mandates. If a firm-specific one-primary-SECTION_86
-- rule is needed later, it can be added as a follow-up migration.
-- ============================================================

-- Relax the CHECK constraint to allow SECTION_86.
ALTER TABLE trust_accounts
    DROP CONSTRAINT IF EXISTS chk_trust_account_type;

ALTER TABLE trust_accounts
    ADD CONSTRAINT chk_trust_account_type
        CHECK (account_type IN ('GENERAL', 'INVESTMENT', 'SECTION_86'));
