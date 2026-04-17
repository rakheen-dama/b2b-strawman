# Fix Spec: GAP-C-09 — Conditional field visibility (msa_start_date) not honored on Create Customer

## Scope Clarification

GAP-C-09 in `status.md` is a CLUSTER covering three distinct issues:

| Issue | Severity | Fix complexity | Included here? |
|-------|----------|----------------|----------------|
| (a) `campaign_type` not auto-filled from template's `matterType` | MED | L (schema + service + UI) | DEFERRED — see below |
| (b) Budget defaults (hours/amount) not auto-filled from template | MED | L (schema + service + UI + template-pack JSON change) | DEFERRED — see below |
| (c) `msa_start_date` visible when `msa_signed == false` on Create Customer Step 2 | MED | S (DTO field addition) | YES |

This spec addresses (c). (a) and (b) require a new `matter_type` column on `project_templates`, new `template_budget_hours` and `template_budget_amount` columns, new project-creation logic, and UI polish. That is a focused half-day project-template-metadata epic, not a 2-hour QA fix. See "Deferred — (a) and (b)" section at the bottom.

## Problem — (c) msa_start_date always visible

Day 1 checkpoint 1.4: Create Customer Step 2 renders `msa_start_date` (DATE field) from the start — even when `msa_signed` (BOOLEAN) is unchecked. The field-pack JSON defines `visibilityCondition` so that `msa_start_date` is hidden unless `msa_signed == true`. Frontend supports `visibilityCondition` (logic in `IntakeFieldsSection`). The condition is silently dropped by the backend.

## Root Cause (confirmed via grep)

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/dto/IntakeFieldGroupResponse.java` lines 26–48 define `IntakeFieldResponse` with fields:

```java
public record IntakeFieldResponse(
    UUID id,
    String name,
    String slug,
    String fieldType,
    boolean required,
    String description,
    List<Map<String, String>> options,
    Map<String, Object> defaultValue,
    List<String> requiredForContexts) { ... }
```

No `visibilityCondition` property. The `from(FieldDefinition fd)` mapper on line 37 also doesn't include it.

Meanwhile:
- `FieldDefinition` entity (file `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java`) DOES have a `visibilityCondition` property.
- `FieldDefinitionResponse` (file `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/dto/FieldDefinitionResponse.java`) line 26 DOES serialize it.
- Frontend `IntakeField` TS interface (file `frontend/components/prerequisite/types.ts` line 46) expects `visibilityCondition: VisibilityCondition | null`.
- Frontend `isFieldVisible()` (file `frontend/components/customers/intake-fields-section.tsx` lines 13–36) wires the logic correctly.
- Field pack JSON (file `backend/src/main/resources/field-packs/consulting-za-customer.json` lines 70–75) defines the condition.

So the chain is: JSON → FieldDefinition entity → (FieldDefinitionResponse ✓ / **IntakeFieldResponse ✗**) → FE. The intake-field endpoint drops the condition; other endpoints preserve it.

## Fix

Add `visibilityCondition` to `IntakeFieldResponse` and pass it through the `from()` mapper.

### Exact change

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/dto/IntakeFieldGroupResponse.java`

1. Extend the `IntakeFieldResponse` record (lines 26–35) — add `Map<String, Object> visibilityCondition` as the last positional field:

   ```java
   public record IntakeFieldResponse(
       UUID id,
       String name,
       String slug,
       String fieldType,
       boolean required,
       String description,
       List<Map<String, String>> options,
       Map<String, Object> defaultValue,
       List<String> requiredForContexts,
       Map<String, Object> visibilityCondition) {
   ```

2. In the `from(FieldDefinition fd)` mapper (lines 37–48), pass `fd.getVisibilityCondition()` as the 10th argument:

   ```java
   public static IntakeFieldResponse from(FieldDefinition fd) {
     return new IntakeFieldResponse(
         fd.getId(),
         fd.getName(),
         fd.getSlug(),
         fd.getFieldType().name(),
         fd.isRequired(),
         fd.getDescription(),
         fd.getOptions(),
         fd.getDefaultValue(),
         fd.getRequiredForContexts(),
         fd.getVisibilityCondition());
   }
   ```

## Scope — (c) only

Backend
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/dto/IntakeFieldGroupResponse.java`
Files to create: none
Migration needed: no

## Verification — (c)

1. Restart backend.
2. As Bob, open Create Customer dialog → Step 2. Expect `msa_start_date` to be HIDDEN by default.
3. Tick the "MSA Signed" checkbox. `msa_start_date` should appear immediately (no page reload).
4. Untick → it hides again.
5. Re-run Day 1 checkpoint 1.4 for conditional-visibility assertion and close the (c) portion of GAP-C-09. Leave (a) and (b) open (see below).

## Estimated Effort

S (< 20 min). 2-line backend DTO change; frontend already consumes the field.

---

## Deferred — (a) and (b): Template metadata not flowing into created projects

### Why deferred

The `ProjectTemplate` entity (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java`) does not currently store:
- `matterType` / `campaignType` — mapped into `campaign_type` custom field on project creation
- `defaultBudgetHours` — template default hours
- `defaultBudgetAmount` — template default amount
- `defaultBudgetCurrency`

The `ProjectTemplatePackDefinition.TemplateEntry` DTO record (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/ProjectTemplatePackDefinition.java`) has a `matterType` string that is parsed from JSON but never written anywhere — the `ProjectTemplatePackSeeder.applyPack()` method only persists `name`, `namePattern`, `description`, `billableDefault` on the entity.

The project template pack JSON (`backend/src/main/resources/project-template-packs/consulting-za.json`) has `matterType` but no budget block. The 120h / R120,000 defaults are only in the free-text `description`.

### Rough scope of the fix

Full fix requires:
1. Flyway migration adding 4 columns to `project_templates` (`matter_type VARCHAR`, `default_budget_hours NUMERIC`, `default_budget_amount NUMERIC`, `default_budget_currency CHAR(3)`).
2. Extend `ProjectTemplate` entity with 4 new fields + constructor params.
3. Extend `ProjectTemplatePackDefinition.TemplateEntry` with a `budget` nested record.
4. Extend `ProjectTemplatePackSeeder.applyPack()` to persist all 4 new fields.
5. Edit `backend/src/main/resources/project-template-packs/consulting-za.json` to add explicit `budget: {hours: 120, amount: 120000, currency: "ZAR"}` blocks on each template (Website Build = 120h/R120k, Brand Identity = 80h/R110k, SEO = 60h/R65k, Social Media/Content Marketing = retainer-month hour banks).
6. Modify project-creation service (likely in `ProjectService` or `ProjectTemplateService` — Dev to confirm exact call site) to: (a) set the `campaign_type` custom-field value to `matterType` on project create; (b) auto-create a `ProjectBudget` row from the template's default hours/amount/currency.
7. Decide conditional — skip budget creation when `matterType` is retainer (SOCIAL_MEDIA_RETAINER / CONTENT_MARKETING) since those use hour banks, not fixed budgets.
8. Update `ProjectTemplateResponse` DTO and frontend template picker to surface the new fields.
9. ~8 new tests.

Rough estimate: 4–6 hours (L). Downgrade status to "deferred: needs scoping" and defer to a follow-up fix cycle or a dedicated template-metadata epic.

### Severity triage for (a) and (b)

- (a) `campaign_type` can be set manually in 5 clicks by the user after project creation (QA workaround was exactly this on Day 2). Not a cascade blocker.
- (b) Budget is also a one-screen manual config (QA did this on Day 5.2 successfully). Not a cascade blocker.
- (c) Conditional visibility IS a UX blocker for data quality — users will enter an `msa_start_date` for clients where there's no MSA signed, producing garbage data that will need cleaning later.

Therefore: ship (c) now, defer (a) + (b) to a follow-up epic. Do NOT conflate them — (c) is a 20-minute DTO fix; (a)+(b) is a half-day project.
