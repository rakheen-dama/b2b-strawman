-- Backfill member-default billing and cost rates for tenants on the consulting-za vertical
-- profile whose members were synced before MemberRateSeedingService was introduced.
--
-- Idempotent:
--   * Gracefully no-ops for tenants not on consulting-za (the WHERE clause filters by
--     org_settings.vertical_profile).
--   * Skips members that already have a matching member-default billing rate or any cost rate
--     (NOT EXISTS guards).
--   * Skips roles that don't match the three role-default slugs (owner/admin/member).
--
-- Amounts mirror rateCardDefaults in vertical-profiles/consulting-za.json so the backfill state
-- matches what new members will get from MemberRateSeedingService going forward.

INSERT INTO billing_rates (
    id, member_id, project_id, customer_id, currency, hourly_rate, effective_from,
    effective_to, created_at, updated_at)
SELECT gen_random_uuid(), m.id, NULL, NULL, 'ZAR',
       CASE LOWER(orl.slug)
            WHEN 'owner'  THEN 1800.00
            WHEN 'admin'  THEN 1200.00
            WHEN 'member' THEN  750.00
       END,
       CURRENT_DATE, NULL, NOW(), NOW()
FROM members m
JOIN org_roles orl ON orl.id = m.org_role_id
CROSS JOIN org_settings s
WHERE s.vertical_profile = 'consulting-za'
  AND LOWER(orl.slug) IN ('owner', 'admin', 'member')
  AND NOT EXISTS (
    SELECT 1 FROM billing_rates br
    WHERE br.member_id = m.id
      AND br.project_id IS NULL
      AND br.customer_id IS NULL
  );

INSERT INTO cost_rates (
    id, member_id, currency, hourly_cost, effective_from, effective_to, created_at, updated_at)
SELECT gen_random_uuid(), m.id, 'ZAR',
       CASE LOWER(orl.slug)
            WHEN 'owner'  THEN 850.00
            WHEN 'admin'  THEN 550.00
            WHEN 'member' THEN 375.00
       END,
       CURRENT_DATE, NULL, NOW(), NOW()
FROM members m
JOIN org_roles orl ON orl.id = m.org_role_id
CROSS JOIN org_settings s
WHERE s.vertical_profile = 'consulting-za'
  AND LOWER(orl.slug) IN ('owner', 'admin', 'member')
  AND NOT EXISTS (
    SELECT 1 FROM cost_rates cr WHERE cr.member_id = m.id
  );
