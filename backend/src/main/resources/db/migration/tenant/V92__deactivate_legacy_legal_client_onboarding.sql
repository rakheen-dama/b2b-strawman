-- GAP-S5-05: Deactivate the legacy legal-za-client-onboarding pack.
-- Superseded by PR #996 (legal-za-individual-onboarding + legal-za-trust-onboarding).
-- The new packs have typed customer_type filters. The legacy ANY row was left
-- active by PR #996, causing every new client to receive both the legacy pack
-- and the new typed pack (QA Cycle 5 GAP-S5-05).

UPDATE checklist_templates
   SET active = false,
       auto_instantiate = false,
       updated_at = now()
 WHERE slug = 'legal-za-client-onboarding'
   AND customer_type = 'ANY';

-- Cancel any IN_PROGRESS instances of the legacy pack that were auto-instantiated
-- on customers created between PR #996 merge and this fix. Users who had already
-- ticked items on the legacy pack can reference them via the audit log; the
-- typed pack is the canonical onboarding checklist going forward. We do NOT
-- delete the rows so the audit trail is preserved.
UPDATE checklist_instances ci
   SET status = 'CANCELLED',
       updated_at = now()
  FROM checklist_templates ct
 WHERE ci.template_id = ct.id
   AND ct.slug = 'legal-za-client-onboarding'
   AND ct.customer_type = 'ANY'
   AND ci.status = 'IN_PROGRESS';
