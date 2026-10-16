-- V27__drop_portal_retainers.sql
-- Phase 86 (Epic 598A, ADR-331 / ADR-336): drop the portal retainer read-model (global V20). Its
-- only writer (customerbackend RetainerPortalSyncService) and readers (PortalRetainerService,
-- PortalDigestContentAssembler retainer section) are deleted in the same PR. Throwaway migration —
-- squashed into global V1 (ADR-331). Indexes idx_portal_retainer_summary_customer_status /
-- idx_portal_retainer_entry_retainer_occurred and the inline CHECKs go with the tables.
DROP TABLE IF EXISTS portal.portal_retainer_consumption_entry;
DROP TABLE IF EXISTS portal.portal_retainer_summary;
