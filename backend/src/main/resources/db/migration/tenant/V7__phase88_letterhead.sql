-- =====================================================================================
-- Phase 88 — demo-ready root-cause closure, §4 U8 (firm letterhead), Slice 626A, ADR-353.
-- V4 is closed (applied to the Keycloak dev DB 2026-10-24); V5 (624F) and V6 (625C) are taken,
-- so this section takes the next free tenant file (OQ #9). Sixteen nullable columns on
-- org_settings backing the LetterheadSettings embeddable. Idempotent: ADD COLUMN IF NOT EXISTS
-- resolves in current_schema(); no data is written.
-- =====================================================================================

-- ---------- §4 U8: firm letterhead -----------------------------------------------------
ALTER TABLE org_settings
  ADD COLUMN IF NOT EXISTS letterhead_address_line1 varchar(255),
  ADD COLUMN IF NOT EXISTS letterhead_address_line2 varchar(255),
  ADD COLUMN IF NOT EXISTS letterhead_address_line3 varchar(255),
  ADD COLUMN IF NOT EXISTS letterhead_city varchar(100),
  ADD COLUMN IF NOT EXISTS letterhead_postal_code varchar(20),
  ADD COLUMN IF NOT EXISTS letterhead_country varchar(100),
  ADD COLUMN IF NOT EXISTS letterhead_phone varchar(50),
  ADD COLUMN IF NOT EXISTS letterhead_email varchar(255),
  ADD COLUMN IF NOT EXISTS letterhead_website varchar(255),
  ADD COLUMN IF NOT EXISTS letterhead_bank_name varchar(100),
  ADD COLUMN IF NOT EXISTS letterhead_bank_branch_code varchar(20),
  ADD COLUMN IF NOT EXISTS letterhead_bank_account_number varchar(50),
  ADD COLUMN IF NOT EXISTS letterhead_bank_account_type varchar(30),
  ADD COLUMN IF NOT EXISTS letterhead_payment_reference_note text,
  ADD COLUMN IF NOT EXISTS letterhead_signatory_name varchar(255),
  ADD COLUMN IF NOT EXISTS letterhead_signatory_designation varchar(255);
