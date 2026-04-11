-- V93: Broaden V92 predicate to catch legacy legal-za-client-onboarding rows
-- regardless of the customer_type sentinel value.
--
-- V92 only matched customer_type = 'ANY' but some seed-era tenants (e.g.
-- tenant_555bfc30b94c) stored the same legacy row with customer_type = 'ALL'.
-- Different seed-era tenants have divergent sentinel values. The slug is
-- unambiguous within a tenant, so widen the predicate to match on slug alone.
--
-- Functionally a no-op at runtime: ChecklistInstantiationService already
-- filters customerTypeIn(type, 'ANY'), so ALL rows cannot match during
-- instantiation. This migration closes the on-disk seed-state inconsistency
-- surfaced by Infra Cycle 5.

UPDATE checklist_templates
   SET active = false,
       auto_instantiate = false,
       updated_at = now()
 WHERE slug = 'legal-za-client-onboarding'
   AND active = true;

-- Cancel any IN_PROGRESS instances of the now-deactivated rows. Idempotent
-- with V92 for the ANY variant; new coverage for the ALL variant. We do NOT
-- delete rows so the audit trail is preserved.
UPDATE checklist_instances ci
   SET status = 'CANCELLED',
       updated_at = now()
  FROM checklist_templates ct
 WHERE ci.template_id = ct.id
   AND ct.slug = 'legal-za-client-onboarding'
   AND ci.status = 'IN_PROGRESS';
