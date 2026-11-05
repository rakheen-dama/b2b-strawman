-- =====================================================================================
-- Phase 89 634C — comment notification deep link (G88-M6), architecture §14.7.1 §2.
-- Opened as V11 because V10__phase89_go_live.sql (632B, §1) was already applied on the Keycloak
-- dev stack (OQ #9; evidence qa_cycle/evidence/phase89-rewalk/634C/before/migrations.md).
-- Idempotent; no data is rewritten (pre-existing rows stay NULL and keep the entity-level link).
-- =====================================================================================

-- No FK: a deleted comment must not cascade to the notification; the link degrades to the tab.
ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS reference_comment_id uuid;
-- No index: the column is read, never searched.
