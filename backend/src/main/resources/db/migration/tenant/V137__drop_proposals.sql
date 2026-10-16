-- V137__drop_proposals.sql
-- Phase 86 (Epic 597A, ADR-331 / ADR-336): remove the Phase 32 proposal pipeline tables and the
-- proposal values of two CHECK constraints. Throwaway migration — squashed into tenant V1 at zero
-- tenants (ADR-331); no production data exists. The engagement letter is now a GeneratedDocument
-- sent through Phase 28 acceptance (ADR-336).

-- 1. Enum-string row cleanup BEFORE the Java constants disappear (595B §4).
--    automation_rules.trigger_type is @Enumerated(STRING) TriggerType (AutomationRule.java:35-37).
--    PROPOSAL_SENT rows were seeded from automation-templates/{legal-za,common,consulting-za}.json —
--    those rule objects are removed in the same PR, so the pack installer cannot re-create them.
--    automation_actions / automation_executions cascade from automation_rules (V60:23,42); the
--    explicit actions delete is redundant but harmless.
DELETE FROM automation_actions
 WHERE rule_id IN (SELECT id FROM automation_rules WHERE trigger_type = 'PROPOSAL_SENT');
DELETE FROM automation_rules WHERE trigger_type = 'PROPOSAL_SENT';

--    field_definitions.required_for_contexts is jsonb text (not @Enumerated); V53:18 back-filled
--    PROPOSAL_SEND on pre-existing common-customer rows. CustomerReadinessService already skips
--    unknown names, so this is defensive. (@> rather than the ? operator, which JDBC can mistake
--    for a bind placeholder.)
UPDATE field_definitions
   SET required_for_contexts = required_for_contexts - 'PROPOSAL_SEND'
 WHERE required_for_contexts @> '["PROPOSAL_SEND"]'::jsonb;

-- 2. Proposal tables (children first). proposals.deal_id + idx_proposals_deal (V130:64,80) and
--    proposals_fee_model_check / contingency CHECKs (V90, V91) go with the table.
DROP TABLE IF EXISTS proposal_team_members;
DROP TABLE IF EXISTS proposal_milestones;
DROP TABLE IF EXISTS proposals;
DROP TABLE IF EXISTS proposal_counters;

-- 3. document_templates.category: TemplateCategory.PROPOSAL removed. No pack seeds it.
--    Re-type (not delete) any user-created PROPOSAL template: generated_documents.template_id
--    references document_templates(id) without ON DELETE (V27:48), so a DELETE would fail on a
--    volume where such a template was ever generated from.
UPDATE document_templates SET category = 'OTHER' WHERE category = 'PROPOSAL';

--    Latest definition: V102:25-37, minus 'PROPOSAL' (order preserved).
ALTER TABLE document_templates DROP CONSTRAINT IF EXISTS chk_dt_category;
ALTER TABLE document_templates
    ADD CONSTRAINT chk_dt_category
    CHECK (category IN (
        'ENGAGEMENT_LETTER',
        'STATEMENT_OF_WORK',
        'COVER_LETTER',
        'PROJECT_SUMMARY',
        'NDA',
        'REPORT',
        'COMPLIANCE',
        'CLOSURE',
        'OTHER'
    ));

-- 4. email_delivery_log.reference_type: PortalEmailService's PORTAL_NEW_PROPOSAL /
--    PORTAL_PROPOSAL_EXPIRED senders are removed. Delete their log rows first, otherwise ADD
--    CONSTRAINT fails on any volume that ever sent a portal proposal email (the lifecycle seed did).
--    PORTAL_RETAINER stays until V138 (Epic 598A).
DELETE FROM email_delivery_log
 WHERE reference_type IN ('PORTAL_NEW_PROPOSAL', 'PORTAL_PROPOSAL_EXPIRED');

--    Latest definition: V135:15-36, minus the two proposal values (order preserved).
ALTER TABLE email_delivery_log
    DROP CONSTRAINT IF EXISTS chk_email_delivery_reference_type;
ALTER TABLE email_delivery_log
    ADD CONSTRAINT chk_email_delivery_reference_type
    CHECK (reference_type IN (
        'NOTIFICATION',
        'INVOICE',
        'MAGIC_LINK',
        'TEST',
        'ACCEPTANCE_REQUEST',
        'INFORMATION_REQUEST',
        'PORTAL_DIGEST',
        'PORTAL_TRUST_ACTIVITY',
        'PORTAL_DEADLINE',
        'PORTAL_RETAINER',
        'PORTAL_DOCUMENT_READY',
        'COLLECTION_REMINDER',
        'CASH_DIGEST'
    ));
