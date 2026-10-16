-- V28__drop_portal_notification_retainer_pref.sql
-- Phase 86 (Epic 598B, ADR-331): drop the per-contact "retainer updates" opt-out (global V22:11).
-- PortalNotificationPreference.retainerUpdatesEnabled is removed in the same PR — the entity maps
-- the column NOT NULL, so field and column must go together. Throwaway migration — squashed into
-- global V1 (ADR-331). Number: next free at merge (§11.8.4); Epic 602A renumbers on rebase.
ALTER TABLE portal.portal_notification_preference DROP COLUMN IF EXISTS retainer_updates_enabled;
