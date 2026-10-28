-- =====================================================================================
-- Phase 88 — demo-ready root-cause closure, §7 U24 (comment attachments), Slice 627H, A13.
-- V4 is closed (applied to the Keycloak dev DB 2026-10-24); V5 (624F), V6 (625C), V7 (626A)
-- and V8 (627B, §5) are taken, and V8 was applied to the Keycloak dev DB on 2026-10-28, so §7
-- takes the next free tenant file instead of being appended to V8 (OQ #9). Idempotent:
-- CREATE TABLE / CREATE INDEX IF NOT EXISTS resolve in current_schema(); the PK and both FKs
-- are inline in the CREATE TABLE, so a re-run adds nothing. No data is written.
-- =====================================================================================

-- ---------- §7 U24: comment attachments ------------------------------------------------
CREATE TABLE IF NOT EXISTS comment_attachments (
    comment_id  uuid NOT NULL,
    document_id uuid NOT NULL,
    sort_order  integer NOT NULL DEFAULT 0,
    created_at  timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT comment_attachments_pkey PRIMARY KEY (comment_id, document_id),
    CONSTRAINT comment_attachments_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT comment_attachments_document_id_fkey FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);
-- Reverse lookup: "which comment is this document attached to" (Documents list chip, delete guard).
CREATE INDEX IF NOT EXISTS idx_comment_attachments_document ON comment_attachments (document_id);
