-- V26__drop_portal_proposals.sql
-- Phase 86 (Epic 597A, ADR-331 / ADR-336): drop the Phase 32 portal read-model for proposals
-- (global V12). Its only writer (proposal/ProposalPortalSyncService) and reader
-- (proposal/PortalProposalService) are deleted in the same PR. Throwaway migration — squashed
-- into global V1 (ADR-331). Indexes idx_portal_proposals_contact / _customer go with the table.
DROP TABLE IF EXISTS portal.portal_proposals;
