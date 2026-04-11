# Fix Spec: GAP-S5-05 — Legacy `legal-za-client-onboarding` pack still auto-instantiates alongside new typed variants

## Problem
Every new client (TRUST or INDIVIDUAL) created in a legal-za tenant now receives **TWO** onboarding checklists: the new type-specific pack (9-item INDIVIDUAL or 12-item TRUST from PR #996) AND the legacy `legal-za-client-onboarding` pack (customer_type=ANY, 11 items). QA confirmed this via the Ndlovu Family Trust test: DB query on `tenant_5039f2d497cf.checklist_instances` showed both `legal-za-client-onboarding` (ANY, 11 items) and `legal-za-trust-client-onboarding` (TRUST, 12 items) for the same customer; backend log reads `Instantiated 2 checklist(s) for customer 703b4ff0-8182-4724-ba0f-4ad38a7bef13 (type=TRUST)`.

## Root Cause
PR #996 (`fix(GAP-S4-02): split legal-za onboarding pack by customer type`) **renamed** the old pack directory `compliance-packs/legal-za-onboarding/` → `compliance-packs/legal-za-individual-onboarding/` and changed the packId from `legal-za-onboarding` to `legal-za-individual-onboarding`. However:

1. The pack seeder (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java`) tracks applied packs by **packId** in `OrgSettings.compliancePackStatus`. Since the packId changed, the seeder treats `legal-za-individual-onboarding` as a brand-new pack.
2. For tenants that were seeded **before** PR #996, the row `checklist_templates(slug='legal-za-client-onboarding', customer_type='ANY', active=true, auto_instantiate=true, pack_id='legal-za-onboarding')` still exists in the tenant schema.
3. PR #996 did not add any cleanup migration/code path to deactivate or delete the legacy template row.
4. `ChecklistInstantiationService.instantiateForCustomer` (line 41–43) queries `templateRepository.findByActiveAndAutoInstantiateAndCustomerTypeIn(true, true, List.of(customerType, "ANY"))` — so it picks up BOTH the legacy ANY row AND the new typed row and instantiates both.

Confirmed via grep:
- `CompliancePackSeeder.applyPack` (L117–184) creates checklist_templates + items but never deactivates any existing template.
- `ChecklistInstantiationService` (L35–65) is the only caller of `findByActiveAndAutoInstantiateAndCustomerTypeIn` and it iterates results with no most-specific-match filter.
- There is no source file for `legal-za-client-onboarding` in `backend/src/main/resources/` anymore — it exists only as a DB row from prior seeding.

## Fix
**Approach (a) — one-shot Flyway cleanup migration.** Deactivate any legacy `legal-za-client-onboarding` row on startup. This is the simplest, lowest-risk fix that works both for existing tenants and any future tenant where the legacy pack might still exist.

### Step 1 — Add tenant migration V92

Create `backend/src/main/resources/db/migration/tenant/V92__deactivate_legacy_legal_client_onboarding.sql`:

```sql
-- GAP-S5-05: Deactivate the legacy legal-za-client-onboarding pack.
-- Superseded by PR #996 (legal-za-individual-onboarding + legal-za-trust-onboarding).
-- The new packs have typed customer_type filters. The legacy ANY row was left
-- active by PR #996, causing every new client to receive both the legacy pack
-- and the new typed pack (QA Cycle 5 GAP-S5-05).

UPDATE checklist_templates
   SET active = false,
       auto_instantiate = false
 WHERE slug = 'legal-za-client-onboarding'
   AND customer_type = 'ANY';

-- Cancel any IN_PROGRESS instances of the legacy pack that were auto-instantiated
-- on customers created between PR #996 merge and this fix. Users who had already
-- ticked items on the legacy pack can reference them via the audit log; the
-- typed pack is the canonical onboarding checklist going forward.
UPDATE checklist_instances ci
   SET status = 'CANCELLED',
       updated_at = now()
  FROM checklist_templates ct
 WHERE ci.template_id = ct.id
   AND ct.slug = 'legal-za-client-onboarding'
   AND ct.customer_type = 'ANY'
   AND ci.status = 'IN_PROGRESS';
```

### Step 2 — Add defensive guard in `ChecklistInstantiationService`

As a belt-and-braces safeguard (and to prevent similar regressions if another future pack creates an `ANY` + typed pair), update `ChecklistInstantiationService.instantiateForCustomer`:

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationService.java`

Replace the body of `instantiateForCustomer` (lines 36–65) with a most-specific-match filter:

```java
@Transactional
public List<ChecklistInstance> instantiateForCustomer(Customer customer) {
  UUID customerId = customer.getId();
  String customerType = customer.getCustomerType().name();

  var matchingTemplates =
      templateRepository.findByActiveAndAutoInstantiateAndCustomerTypeIn(
          true, true, List.of(customerType, "ANY"));

  // Most-specific-match: if any template matches the exact customer type for a
  // given packId (or templateGroup), skip the ANY fallback for that group.
  // For now we use a coarser rule: if ANY type-specific templates exist at all,
  // skip the ANY templates entirely. This matches the intent of PR #996 (typed
  // packs fully replace the legacy ANY pack) and prevents future duplicate-pack
  // regressions from naive seeders.
  boolean hasTypedTemplate =
      matchingTemplates.stream().anyMatch(t -> customerType.equals(t.getCustomerType()));
  var finalTemplates =
      hasTypedTemplate
          ? matchingTemplates.stream()
              .filter(t -> customerType.equals(t.getCustomerType()))
              .toList()
          : matchingTemplates;

  List<ChecklistInstance> created = new ArrayList<>();
  for (var template : finalTemplates) {
    if (instanceRepository.existsByCustomerIdAndTemplateId(customerId, template.getId())) {
      log.debug(
          "Skipping template '{}' — instance already exists for customer {}",
          template.getName(),
          customerId);
      continue;
    }
    var instance = instanceService.createFromTemplate(template.getId(), customerId, customer);
    created.add(instance);
    log.info("Auto-instantiated checklist '{}' for customer {}", template.getName(), customerId);
  }

  log.info(
      "Instantiated {} checklist(s) for customer {} (type={}, typedAvailable={})",
      created.size(),
      customerId,
      customerType,
      hasTypedTemplate);
  return created;
}
```

Rationale: the coarse rule "if any typed template exists, skip ALL ANY templates" is intentional. Per PR #996's intent, typed packs fully replace the legacy ANY pack. Mixing a typed pack with an ANY pack has no valid product use case today. If a future feature needs both (e.g., a universal "Sanctions Screening" pack alongside typed packs), that should be modelled via a different mechanism (multiple typed variants or a separate universal pack-type).

### Step 3 — Test updates

Update `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationServiceTest.java`:

Add a new test case `skipsAnyTemplateWhenTypedTemplateExists`:
- Seed two active auto-instantiate templates: one with `customer_type=ANY` (legacy) and one with `customer_type=INDIVIDUAL` (new).
- Call `instantiateForCustomer(individualCustomer)`.
- Assert: only the INDIVIDUAL template was instantiated; the ANY template was skipped.
- Assert: log message includes `typedAvailable=true`.

Also verify the existing test case `instantiatesAllMatchingTemplates` still passes — it should, as long as the existing seed data does not mix ANY + typed for the same customer type.

## Scope
**Backend only.**

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationServiceTest.java`

Files to create:
- `backend/src/main/resources/db/migration/tenant/V92__deactivate_legacy_legal_client_onboarding.sql`

Migration needed: **yes** (tenant scope, V92).

## Verification
1. **Rebuild backend** (Flyway runs V92 against all 4 existing tenant schemas on startup, deactivating legacy rows and cancelling orphaned instances).
2. QA re-runs GAP-S4-02 re-verification:
   - Create new INDIVIDUAL client → exactly **one** `legal-za-individual-client-onboarding` pack (9 items) instantiates.
   - Create new TRUST client → exactly **one** `legal-za-trust-client-onboarding` pack (12 items) instantiates.
   - Backend log should read `Instantiated 1 checklist(s) for customer ... (type=TRUST, typedAvailable=true)`.
3. DB query `SELECT slug, customer_type, active FROM checklist_templates WHERE slug='legal-za-client-onboarding'` → should show `active=false`.
4. Existing Ndlovu/Moroka duplicate instances should show `status=CANCELLED` for the legacy pack.

## Estimated Effort
**M** (30 min – 2 hr). Migration + service logic + test = ~45 min.
