# Phase 66 — `consulting-za` Vertical Profile (Pack-Only Agency Content)

> Pack-only vertical profile: no new entities, no migrations, no new backend services.
> Architecture doc: `architecture/phase66-consulting-vertical-profile.md`
> ADRs: [ADR-244](../adr/ADR-244-pack-only-vertical-profiles.md), [ADR-245](../adr/ADR-245-localized-profile-derivatives.md), [ADR-246](../adr/ADR-246-profile-gated-dashboard-widgets.md)
> Starting epic: 480 · Last completed: 479 (Phase 65)
> Migration high-water: global V18, tenant V95 — **unchanged** this phase.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 480 | Profile Manifest + Field Packs | Backend | -- | S | 480A | **Done** (PR #1047) |
| 481 | Rate Pack + Project Template Pack | Backend | 480 | M | 481A, 481B | **Done** (PR #1048) |
| 482 | Automation Pack + Document Template Pack | Backend | 481 | L | 482A, 482B | **Done** (PR #1049) |
| 483 | Clause Pack + Request Pack | Backend | 482 | M | 483A, 483B | **Done** (PR #1050) |
| 484 | Terminology Key + Team Utilization Widget | Frontend | 480 | M | 484A, 484B | **Done** (PR #1051) |
| 485 | QA Lifecycle Retarget + Screenshot Baselines | E2E/Process | 480–484 | M | 485A, 485B | |

Slice count: **10 slices across 6 epics**. Every slice pairs pack JSON (or frontend code) with the integration/component test that exercises it.

---

## Dependency Graph

```
PHASE 49 (vertical profiles), PHASE 65 (pack installer pipeline),
PHASE 23 (conditional field visibility), PHASE 31 (VariableResolver),
PHASE 37/48 (automation triggers), PHASE 38 (utilization),
PHASE 53 (dashboard primitives), generic-onboarding compliance pack
— all complete
                        |
        ┌───────────────┴───────────────┐
        |                               |
[E480A  Profile manifest                |
 consulting-za.json +                   |
 field-packs/consulting-za-customer     |
 + consulting-za-project +              |
 auto-apply wiring +                    |
 provisioning integration test          |
 (~5 tests)]                            |
        |                               |
        +──────────────┐                |
        |              |                |
[E481A  Rate pack     [E484A  Terminology key
 consulting-za.json    en-ZA-consulting in
 (8 roles) +           terminology-map.ts
 rate-pack seeder      + useProfile() hook
 integration test]     + unit tests]
        |              |
[E481B  Project       [E484B  TeamUtilizationWidget
 template pack         component + dashboard
 consulting-za.json    page integration +
 (5 templates) +       component tests +
 template seeder       loading/empty/error states]
 integration test]
        |
        +──────────────┐
        |              |
[E482A  Automation     |
 pack consulting-za    |
 (6 rules) routed      |
 via Phase 65          |
 AutomationPackInstaller
 + integration test]
        |
[E482B  Document       |
 template pack         |
 template-packs/       |
 consulting-za/        |
 (pack.json + 4        |
 Tiptap docs) via      |
 Phase 65              |
 TemplatePackInstaller |
 + variable resolution
 test]
        |
        +──────────────┐
        |              |
[E483A  Clause pack   [E483B  Request pack
 consulting-za-        consulting-za-
 clauses/pack.json     creative-brief.json
 (8 clauses)           (10 questions) +
 + template            auto-assignment
 associations +        integration test]
 association test]
        |              |
        +──────────────+
                |
[E485A  Retarget consulting-agency-90day-keycloak.md
 from consulting-generic → consulting-za;
 verify each day checkpoint exercises
 new pack content]
                |
[E485B  Playwright screenshot baselines
 (e2e/screenshots/consulting-lifecycle/) +
 curated captures (documentation/screenshots/
 consulting-vertical/)]
```

**Parallel opportunities**:
- After E480A: **E481A** (rate + project templates) and **E484A** (terminology + hook) can run in parallel.
- **E484B** (widget) only depends on E484A — it can run while E482/E483 are in flight.
- **E483A** (clauses) and **E483B** (request pack) are independent within Epic 483 and can run in parallel after Epic 482 completes.
- **E485** runs last — requires all pack content installed.

---

## Implementation Order

### Stage 0: Profile + Field Packs (foundation)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 0a | 480 | 480A | Profile manifest + 2 field packs + auto-apply wiring + provisioning integration test — **Done** (PR #1047) |

### Stage 1: Rate + Templates, Terminology + Hook (parallel)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 1a (parallel) | 481 | 481A | Rate pack JSON (8 roles) + seeder integration test — **Done** (PR #1048) |
| 1b (parallel) | 484 | 484A | Terminology key + useProfile() hook + unit tests — **Done** (PR #1051) |

### Stage 2: Project Template Pack + Widget (parallel)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 2a | 481 | 481B | Project template pack (5 templates) + seeder integration test — **Done** (PR #1048) |
| 2b (parallel) | 484 | 484B | TeamUtilizationWidget + dashboard integration + component tests — **Done** (PR #1051) |

### Stage 3: Automation + Document Templates (sequential, same epic)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 3a | 482 | 482A | Automation pack (6 rules) via Phase 65 installer + test — **Done** (PR #1049) |
| 3b | 482 | 482B | Document template pack (4 Tiptap docs + manifest) via Phase 65 installer + variable resolution test — **Done** (PR #1049) |

### Stage 4: Clauses + Requests (parallel)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 4a (parallel) | 483 | 483A | Clause pack (8 clauses + template associations) + association test — **Done** (PR #1050) |
| 4b (parallel) | 483 | 483B | Request pack (10-question creative brief) + auto-assignment test — **Done** (PR #1050) |

### Stage 5: QA Retarget + Screenshots

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 5a | 485 | 485A | Retarget consulting-agency-90day-keycloak.md to consulting-za |
| 5b | 485 | 485B | Playwright screenshot baselines + curated captures |

### Timeline

```
Stage 0:  [480A]                                          <- profile + field packs
Stage 1:  [481A] // [484A]                                <- rate pack + terminology/hook
Stage 2:  [481B] // [484B]                                <- project templates + widget
Stage 3:  [482A] -> [482B]                                <- automation + doc templates
Stage 4:  [483A] // [483B]                                <- clauses + request pack
Stage 5:  [485A] -> [485B]                                <- QA retarget + screenshots
```

---

## Parallel Tracks

- **Content track** (480 → 481 → 482 → 483): Pack authoring proceeds sequentially because of content dependencies (field slugs before templates; templates before clause associations).
- **Frontend track** (484A → 484B): Once the profile is registered (480A), the frontend engineer can ship the terminology entry and widget without waiting on any later pack content.
- **QA track** (485A → 485B): Blocks on full pack content being installable in a test tenant, but the lifecycle script update (485A) can be drafted as soon as 484B completes.

A realistic day-by-day cadence: Epic 480 sets foundations day 1–2; Epic 481 + Epic 484 run concurrently days 2–4; Epic 482 days 4–6; Epic 483 day 6–7; Epic 485 day 7–8.

---

## Epic 480: Profile Manifest + Field Packs

**Goal**: Register the `consulting-za` profile with the vertical profile registry and author the two field packs (customer + project) the rest of the phase depends on. Field packs carry the `campaign_type`, `retainer_tier`, MSA tracking, and industry custom fields that every subsequent pack references.

**References**: Architecture Sections 66.3 (pack inventory row 1–3), 66.4 (activation flow), 66.9.1 (profile manifest), 66.9.2 (customer field pack), 66.9.3 (project field pack). Requirements Sections 1 and 2.

**Dependencies**: None (first epic). Relies on existing Phase 49 profile registry, Phase 23 conditional visibility, existing `FieldPackSeeder`.

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **480A** | 480.1–480.6 | `vertical-profiles/consulting-za.json` (profile manifest with empty `enabledModules`, 6 pack references, `rateCardDefaults`, `taxDefaults` VAT 15%, `terminologyOverrides: "en-ZA-consulting"`). `field-packs/consulting-za-customer.json` (5 fields, `autoApply: true`). `field-packs/consulting-za-project.json` (5 fields, `autoApply: true`, conditional visibility on `retainer_tier`). Integration test provisioning a test tenant with profile and asserting profile+fields installed. **Done** (PR #1047) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 480.1 | Author `vertical-profiles/consulting-za.json` | 480A | -- | New file: `backend/src/main/resources/vertical-profiles/consulting-za.json`. Mirror `backend/src/main/resources/vertical-profiles/accounting-za.json` structure. Content per architecture Section 66.9.1: `profileId: "consulting-za"`, `enabledModules: []` (empty — pack-only), `packs` block references the 6 packs this phase authors (`consulting-za-customer`, `consulting-za-project`, template key `consulting-za`, clause `consulting-za-clauses`, automation `automation-consulting-za`, request `consulting-za-creative-brief`, compliance `generic-onboarding`), `rateCardDefaults` with Owner/Admin/Member fallbacks (R1800/1200/750 billing, R850/550/375 cost), `taxDefaults: [VAT 15%]`, `terminologyOverrides: "en-ZA-consulting"`. Verify `VerticalProfileRegistry` classpath scan picks it up (no code change expected). |
| 480.2 | Author `field-packs/consulting-za-customer.json` | 480A | 480.1 | New file. Mirror `field-packs/accounting-za-customer.json` for overall shape; `entityType: "CUSTOMER"`, `group.autoApply: true`, `verticalProfile: "consulting-za"`. 5 fields per architecture Section 66.9.2 / requirements Section 2.1: `industry` ENUM (10 options), `company_size` ENUM (5 options), `primary_stakeholder` TEXT, `msa_signed` BOOLEAN, `msa_start_date` DATE with conditional visibility `msa_signed = true` (Phase 23 pattern). None required. |
| 480.3 | Author `field-packs/consulting-za-project.json` | 480A | 480.1 | New file. Mirror `field-packs/accounting-za-project.json`. `entityType: "PROJECT"`, `group.autoApply: true`, `verticalProfile: "consulting-za"`. 5 fields per architecture Section 66.9.3 / requirements Section 2.2: `campaign_type` ENUM **required** (9 options), `channel` ENUM (8 options), `deliverable_type` ENUM (8 options), `retainer_tier` ENUM (4 options) with conditional visibility `campaign_type IN (SOCIAL_MEDIA_RETAINER, CONTENT_MARKETING)`, `creative_brief_url` URL. |
| 480.4 | Verify `VerticalProfileRegistry` picks up the new profile | 480A | 480.1 | No new code — confirm via existing `VerticalProfileRegistry` / `ModuleRegistry` behavior. If an explicit allow-list exists, add `consulting-za`. Pattern: check Phase 49 how `accounting-za` is registered; add to any enum/constant in `provisioning/` if one gates profile selection during tenant setup. |
| 480.5 | Verify profile is selectable during platform-admin provisioning | 480A | 480.4 | Read-only check: grep for `consulting-generic` / `accounting-za` in the platform-admin provisioning form or DTO. If a hardcoded enum exists on the frontend (e.g., in an admin provisioning component), add `consulting-za` to it. This is most likely a no-op but must be confirmed. |
| 480.6 | Integration test: provisioning with `consulting-za` applies profile + fields | 480A | 480.1–480.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaProfileProvisioningTest.java`. ~5 tests: (1) profile registry returns `consulting-za` manifest with expected fields, (2) provisioning a tenant with `consulting-za` creates FieldDefinitions for all 5 customer + 5 project slugs, (3) `campaign_type` is marked required, (4) `retainer_tier` has conditional visibility rule, (5) `OrgSettings.verticalProfile` equals `"consulting-za"` post-provisioning. Pattern: `backend/src/test/java/.../provisioning/TenantProvisioningServiceTest.java` and `VerticalProfileFilteringTest.java`. |

### Key Files

**Create:**
- `backend/src/main/resources/vertical-profiles/consulting-za.json`
- `backend/src/main/resources/field-packs/consulting-za-customer.json`
- `backend/src/main/resources/field-packs/consulting-za-project.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaProfileProvisioningTest.java`

**Read for context:**
- `backend/src/main/resources/vertical-profiles/accounting-za.json` — manifest shape
- `backend/src/main/resources/vertical-profiles/consulting-generic.json` — nearest sibling
- `backend/src/main/resources/field-packs/accounting-za-customer.json` — customer field pack shape incl. conditional visibility
- `backend/src/main/resources/field-packs/accounting-za-project.json` — project field pack shape
- `backend/src/test/java/.../provisioning/TenantProvisioningServiceTest.java` — integration test baseline

### Architecture Decisions

- **Empty `enabledModules` is the pack-only signal** ([ADR-244](../adr/ADR-244-pack-only-vertical-profiles.md)). The manifest never activates backend modules; it composes entirely from packs + custom fields + terminology.
- **`autoApply: true` on both field groups** so the fields appear on tenant projects and customers without manual group activation (architecture Section 66.9.2/3).
- **Conditional visibility reuses Phase 23** for both `msa_start_date` and `retainer_tier` — no new visibility primitive.

### Non-scope

- No migrations (tenant stays at V95).
- No new entity, service, repository, controller.
- No changes to `FieldPackSeeder` or `VerticalProfileRegistry`.
- No graduation of `campaign_type` to a native column.
- No Project → Engagement terminology override in the manifest (deliberately omitted per architecture Section 66.7).

---

## Epic 481: Rate Pack + Project Template Pack

**Goal**: Ship the ZAR-denominated agency rate pack (8 roles) and the 5 agency project templates that drive every demo-flow project creation. Project templates must reference the `campaign_type` and `retainer_tier` slugs authored in Epic 480.

**References**: Architecture Sections 66.3 (rows 4–5), 66.9.4 (rate pack), 66.9.5 (project template pack). Requirements Sections 3 and 4.

**Dependencies**: Epic 480 (field packs must exist so templates can set `campaign_type` as a custom-field default).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **481A** | 481.1–481.3 | `rate-packs/consulting-za.json` (8 SA agency roles with ZAR billing + cost rates). Seeder integration test confirming 8 rates seeded. **Done** (PR #1048) |
| **481B** | 481.4–481.9 | `project-template-packs/consulting-za.json` (5 templates: Website Build, Social Media Retainer, Brand Identity, SEO Campaign, Content Marketing Retainer). Each template sets `campaign_type` (and `retainer_tier` for retainers) as default custom-field values, seeds task list with priority + suggested role. Integration test verifying all 5 templates + task counts + campaign_type defaulting. **Done** (PR #1048) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 481.1 | Author `rate-packs/consulting-za.json` | 481A | -- | New file: `backend/src/main/resources/rate-packs/consulting-za.json`. Mirror `rate-packs/accounting-za.json` shape. 8 roles per architecture Section 66.9.4 / requirements Section 3.2: Creative Director (1800/850), Strategist (1600/750), Art Director (1400/650), Account Manager (1200/550), Senior Designer/Developer (1100/500), Copywriter (950/450), Designer/Developer (850/400), Producer/Junior (600/300). Currency ZAR. `verticalProfile: "consulting-za"`. |
| 481.2 | Integration test: rate pack seeds 8 roles | 481A | 481.1 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaRatePackTest.java`. ~3 tests: (1) `RatePackSeeder` creates 8 rate-card entries for a tenant provisioned with `consulting-za`, (2) all rates in ZAR, (3) Creative Director has billing R1800 / cost R850. Pattern: existing rate-pack seeder test in `backend/src/test/java/.../rate/`. |
| 481.3 | Verify existing `RatePackSeeder` handles the new pack without changes | 481A | 481.1 | Read-only verification — no code modification expected. Confirm classpath scan picks up `consulting-za.json`. |
| 481.4 | Author Website Build + Brand Identity + SEO templates in `project-template-packs/consulting-za.json` | 481B | 481.1 (role names) | New file: `backend/src/main/resources/project-template-packs/consulting-za.json`. Mirror `project-template-packs/accounting-za.json` and `legal-za.json`. Pack metadata: `packId: "consulting-za-project-templates"`, `verticalProfile: "consulting-za"`, `version: 1`. Add 3 templates per architecture Section 66.9.5 / requirements Section 4.2: **Website Design & Build** (`campaign_type: WEBSITE_BUILD`, 9 tasks, 120h budget), **Brand Identity** (`campaign_type: BRAND_IDENTITY`, 9 tasks, 80h budget), **SEO Campaign** (`campaign_type: SEO_CAMPAIGN`, 7 tasks, 60h budget). Each task: name, priority (HIGH/MEDIUM/LOW), suggested assignee role (matching rate-pack role names), `sortOrder`. |
| 481.5 | Add Social Media Retainer + Content Marketing Retainer templates | 481B | 481.4 | Same file. 2 retainer-shaped templates: **Social Media Management Retainer** (`campaign_type: SOCIAL_MEDIA_RETAINER`, `retainer_tier: GROWTH`, 6 recurring monthly tasks, 40h/month bank), **Content Marketing Retainer** (`campaign_type: CONTENT_MARKETING`, `retainer_tier: STARTER`, 6 recurring tasks, 25h/month bank). Each sets BOTH `campaign_type` and `retainer_tier` as custom-field defaults. Content per requirements Section 4.2 templates 2 and 5. |
| 481.6 | Verify template-to-custom-field default wiring | 481B | 481.5 | Same file — ensure each template's `customFieldDefaults` block uses the slugs defined in Epic 480 (`campaign_type`, `retainer_tier`). Pattern: same mechanism legal uses for `matter_type` in `project-template-packs/legal-za.json` (Phase 64). |
| 481.7 | Integration test: 5 templates + task counts | 481B | 481.4–481.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaProjectTemplatePackTest.java`. ~4 tests: (1) seeder creates exactly 5 `ProjectTemplate` rows, (2) task counts: Website Build 9, Social Retainer 6, Brand Identity 9, SEO Campaign 7, Content Retainer 6, (3) each template's `campaign_type` default matches its template name, (4) retainer templates have `retainer_tier` default. Pattern: `LegalProjectTemplatePackTest.java`. |
| 481.8 | Integration test: "use template" sets custom-field values on created project | 481B | 481.7 | Same test file, 1 additional test: create a project from the "Website Design & Build" template, assert `Project.customFields.campaign_type = "WEBSITE_BUILD"` in JSONB. Pattern: legal matter-type test in Phase 64. |
| 481.9 | Verify existing `ProjectTemplatePackSeeder` handles the new pack without changes | 481B | 481.4 | Read-only verification. Confirm `TenantProvisioningService` and `PackReconciliationRunner` already call `ProjectTemplatePackSeeder.seedPacksForTenant()` and no wiring changes are needed. |

### Key Files

**Create:**
- `backend/src/main/resources/rate-packs/consulting-za.json`
- `backend/src/main/resources/project-template-packs/consulting-za.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaRatePackTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaProjectTemplatePackTest.java`

**Read for context:**
- `backend/src/main/resources/rate-packs/accounting-za.json`
- `backend/src/main/resources/project-template-packs/accounting-za.json`
- `backend/src/main/resources/project-template-packs/legal-za.json` — matter-type defaulting pattern
- `backend/src/test/java/.../packs/LegalProjectTemplatePackTest.java` (Phase 64)

### Architecture Decisions

- **Retainer-shaped templates do not auto-create `RetainerAgreement` rows** (architecture Section 66.6). Templates carry seed content; `RetainerAgreement` remains a manual step. Parked for a future phase.
- **Role names in tasks must match rate-pack roles** so time entries default-bill at the right rate; this is why 481A precedes 481B.
- **`campaign_type` default is the connective tissue** (architecture Section 66.5). The template sets the enum value on project creation, the automation pack in Epic 482 matches on it, and the document templates in Epic 482 interpolate it — no variable-registry changes needed.

### Non-scope

- No new `ProjectTemplate` entity fields.
- No auto-create `RetainerAgreement` from retainer templates.
- No new rate-card tiers or entities.
- No new template pack type (reuses existing `ProjectTemplatePackSeeder`).

---

## Epic 482: Automation Pack + Document Template Pack

**Goal**: Author the 6 agency automation rules and 4 Tiptap document templates. Both packs route through the Phase 65 install pipeline (`AutomationPackInstaller` + `TemplatePackInstaller`) — no new installer code. Document templates interpolate custom-field values set by the Epic 481 project templates.

**References**: Architecture Sections 66.3 (rows 6–7), 66.9.6 (automation pack), 66.9.7 (document template pack), 66.10 (variable registry check). Requirements Sections 5 and 6.

**Dependencies**: Epic 481 (project templates drive project creation for test-data; automation rules match on `campaign_type`).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **482A** | 482.1–482.4 | `automation-templates/consulting-za.json` (6 rules: budget 80%, budget exceeded, retainer closing, task blocked 7d, unbilled time 30d, proposal follow-up). Routes via Phase 65 `AutomationPackInstaller`. Integration test verifying 6 rules installed + content_hash populated + source_pack_install_id tagged. — **Done** (PR #1049) |
| **482B** | 482.5–482.10 | `template-packs/consulting-za/pack.json` manifest + 4 Tiptap JSON documents: `creative-brief.json`, `statement-of-work.json`, `engagement-letter.json`, `monthly-retainer-report.json`. Routes via Phase 65 `TemplatePackInstaller`. Integration test verifying 4 templates installed, variable resolution succeeds against seeded project/retainer fixtures. — **Done** (PR #1049) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 482.1 | Author `automation-templates/consulting-za.json` — rules 1–3 | 482A | -- | New file: `backend/src/main/resources/automation-templates/consulting-za.json`. Mirror `automation-templates/accounting-za.json` shape. `packId: "automation-consulting-za"`, `verticalProfile: "consulting-za"`, `version: 1`. Rules 1–3 per architecture Section 66.9.6 / requirements Section 5.2: budget 80%, budget exceeded (100%), retainer closing 3 days (trigger `FIELD_DATE_APPROACHING`). Only use Phase 37/48 trigger verbs — NO new trigger types. |
| 482.2 | Add rules 4–6 to automation pack | 482A | 482.1 | Same file. Rules 4–6: task blocked 7d (trigger `TASK_STATUS_UNCHANGED`), unbilled time 30d (trigger `FIELD_DATE_APPROACHING` on time-entry age — documented as the nearest available trigger; gap noted in architecture Section 66.9.6), proposal follow-up 5d (trigger `PROPOSAL_SENT`). Variables use existing `{{project.name}}`, `{{customer.name}}`, `{{proposal.total}}`, etc. Do NOT invent new trigger types. |
| 482.3 | Integration test: automation pack installs 6 rules via Phase 65 pipeline | 482A | 482.1, 482.2 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaAutomationPackTest.java`. ~4 tests: (1) `AutomationPackInstaller.install("automation-consulting-za", tenantId, null)` creates 1 `PackInstall` row with `packType = AUTOMATION_TEMPLATE` and `itemCount = 6`, (2) all 6 `AutomationRule` rows have non-null `source_pack_install_id` + `content_hash`, (3) each rule's trigger type is one of the existing Phase 37/48 enum values (no novel types), (4) provisioning a tenant with `consulting-za` profile auto-installs this pack (`TenantProvisioningService` routes it via `PackInstallService.internalInstall`). Pattern: Phase 65 `PackInstallServiceTest.java`. |
| 482.4 | Document the rule-5 trigger gap in the pack's metadata or a comment | 482A | 482.2 | Same automation JSON — add a `_comment` field (or equivalent metadata the loader ignores) noting that rule 5 uses `FIELD_DATE_APPROACHING` as the nearest-fit trigger for time-entry age, and a future phase should introduce a `TIME_ENTRY_AGE` trigger. This keeps the gap visible without polluting architecture docs. |
| 482.5 | Author `template-packs/consulting-za/pack.json` manifest | 482B | 482A | New directory + file: `backend/src/main/resources/template-packs/consulting-za/pack.json`. Mirror `template-packs/accounting-za/pack.json`. Fields: `packId: "consulting-za"`, `verticalProfile: "consulting-za"`, `version: 1`, `name: "Agency Templates"`, `templates: []` listing 4 entries (key, name, description, file reference). Do NOT reference clauses in `templateAssociations` yet — that is Epic 483's job. |
| 482.6 | Author `creative-brief.json` Tiptap document | 482B | 482.5 | New file: `backend/src/main/resources/template-packs/consulting-za/creative-brief.json`. Tiptap JSON doc (Phase 31 schema) — NOT HTML/Thymeleaf. Variables per architecture Section 66.9.7: `{{customer.name}}`, `{{project.name}}`, `{{campaign_type}}`, `{{creative_brief_url}}`, `{{project.startDate}}`, `{{project.dueDate}}`, `{{org.name}}`. Pattern: `template-packs/accounting-za/engagement-letter-advisory.json`. |
| 482.7 | Author `statement-of-work.json` and `engagement-letter.json` | 482B | 482.6 | 2 new files in same directory. SOW variables: `{{customer.name}}`, `{{project.name}}`, `{{deliverable_type}}`, `{{project.budgetTotal}}`, `{{project.startDate}}`, `{{project.dueDate}}`, owner + client signature blocks. Engagement letter variables: `{{customer.name}}`, `{{customer.address}}`, `{{project.name}}`, `{{org.name}}`, `{{org.vatNumber}}`, owner + client signature blocks. Signature blocks use existing Tiptap signature node. |
| 482.8 | Author `monthly-retainer-report.json` | 482B | 482.7 | New file. Variables per architecture Section 66.9.7: `{{customer.name}}`, `{{retainer.periodStart}}`, `{{retainer.periodEnd}}`, `{{retainer.hoursUsed}}`, `{{retainer.hourBank}}`, `{{project.name}}`, activity summary table. Do NOT emit `{{retainer.hoursRemaining}}` — compose inline as `{{retainer.hourBank}} − {{retainer.hoursUsed}}` or render in the activity table (architecture Section 66.10). |
| 482.9 | Integration test: document template pack installs 4 templates via Phase 65 pipeline | 482B | 482.5–482.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaTemplatePackTest.java`. ~4 tests: (1) `TemplatePackInstaller.install("consulting-za", tenantId, null)` creates 1 `PackInstall` row with `itemCount = 4`, (2) all 4 `DocumentTemplate` rows have non-null `source_pack_install_id` + `content_hash`, (3) provisioning a tenant with `consulting-za` profile auto-installs this pack, (4) each template's Tiptap JSON parses cleanly through the existing schema validator. Pattern: Phase 65 `TemplatePackInstallerTest.java`. |
| 482.10 | Integration test: variable resolution against seeded project fixtures | 482B | 482.9, 481B | Same test file, 2 additional tests: (1) create a project from the Website Build template (so `campaign_type = WEBSITE_BUILD` is set), render `creative-brief.json` through `VariableResolver` — assert `{{campaign_type}}` resolves to `"WEBSITE_BUILD"` (via Phase 31 custom-field flattening), `{{customer.name}}` resolves, `{{project.name}}` resolves; (2) create a `RetainerAgreement` fixture, render `monthly-retainer-report.json`, assert all retainer variables resolve without error. **No new registry entries** — if anything is unresolvable, that's a test failure, not a registry extension. |

### Key Files

**Create:**
- `backend/src/main/resources/automation-templates/consulting-za.json`
- `backend/src/main/resources/template-packs/consulting-za/pack.json`
- `backend/src/main/resources/template-packs/consulting-za/creative-brief.json`
- `backend/src/main/resources/template-packs/consulting-za/statement-of-work.json`
- `backend/src/main/resources/template-packs/consulting-za/engagement-letter.json`
- `backend/src/main/resources/template-packs/consulting-za/monthly-retainer-report.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaAutomationPackTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaTemplatePackTest.java`

**Read for context:**
- `backend/src/main/resources/automation-templates/accounting-za.json` — nearest-in-shape automation pack
- `backend/src/main/resources/template-packs/accounting-za/pack.json` — document template manifest
- `backend/src/main/resources/template-packs/accounting-za/engagement-letter-advisory.json` — Tiptap variable interpolation reference
- Phase 65 `PackInstaller` / `TemplatePackInstaller` / `AutomationPackInstaller` source — installer contract

### Architecture Decisions

- **Routes through Phase 65 install pipeline, not direct seeder** (architecture Section 66.3 install-path routing). Both packs use the Phase 65 `PackInstaller` contract so `PackInstall` rows, `source_pack_install_id` tagging, and `content_hash` tracking all land correctly.
- **No new trigger types** (architecture Section 66.9.6 and ADR-244). Rule 5's time-entry age scenario is approximated with `FIELD_DATE_APPROACHING`; the gap is documented but not filled.
- **No new variable-registry entries** (architecture Section 66.10). Where `{{retainer.hoursRemaining}}` might be wanted, template authors compose from `hourBank` − `hoursUsed` inline.

### Non-scope

- No new `AutomationTrigger` enum values.
- No new `VariableResolver` registry additions.
- No changes to `AutomationPackInstaller` or `TemplatePackInstaller` code.
- No Tiptap schema extensions (signature node already exists).
- No HTML/Thymeleaf fallback templates.

---

## Epic 483: Clause Pack + Request Pack

**Goal**: Author the 8 agency SOW clauses and the 10-question creative-brief request pack. Clause pack includes `templateAssociations` binding required clauses (payment terms, IP ownership) into the SOW and engagement-letter templates shipped in Epic 482.

**References**: Architecture Sections 66.3 (rows 8–9), 66.9.8 (clause pack), 66.9.9 (request pack). Requirements Sections 7 and 8.

**Dependencies**: Epic 482 (clause template associations reference the SOW + engagement-letter template keys).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **483A** | 483.1–483.5 | `clause-packs/consulting-za-clauses/pack.json` with 8 clauses (ip-ownership, revision-rounds, kill-fee, nda-mutual, payment-terms, change-requests, third-party-costs, termination). `templateAssociations` binds required clauses to SOW + engagement-letter templates. Integration test for 8 clauses + association resolution. — **Done** (PR #1050) |
| **483B** | 483.6–483.9 | `request-packs/consulting-za-creative-brief.json` with 10 questions (brand desc, audience, goals, competitive, deliverables, constraints, assets, tone, stakeholders, dates). Integration test for question count + auto-assignment on new customer. — **Done** (PR #1050) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 483.1 | Create `clause-packs/consulting-za-clauses/pack.json` with clauses 1–4 | 483A | -- | New directory + file: `backend/src/main/resources/clause-packs/consulting-za-clauses/pack.json`. Mirror `clause-packs/accounting-za-clauses/pack.json` (single-file pack with inline Tiptap clause bodies). `packId: "consulting-za-clauses"`, `verticalProfile: "consulting-za"`. First 4 clauses per architecture Section 66.9.8 / requirements Section 7.2: `ip-ownership`, `revision-rounds`, `kill-fee`, `nda-mutual`. Each is a Tiptap JSON doc with optional variable placeholders (e.g., `{{org.name}}`, `{{customer.name}}`). |
| 483.2 | Add clauses 5–8 to the pack | 483A | 483.1 | Same file. Clauses 5–8: `payment-terms`, `change-requests`, `third-party-costs`, `termination`. All self-contained, insertable via the Phase 27 clause library mechanism. |
| 483.3 | Add `templateAssociations` to the clause pack | 483A | 483.2, 482B | Same file. Bind clauses to templates from Epic 482: `statement-of-work` requires `payment-terms`, `ip-ownership`, `change-requests`; optional: `revision-rounds`, `kill-fee`, `third-party-costs`, `termination`. `engagement-letter` requires `payment-terms`, `termination`, `nda-mutual`; optional: others. Pattern: `clause-packs/accounting-za-clauses/pack.json` — look for its `templateAssociations` block or equivalent. |
| 483.4 | Integration test: clauses seeded + associations resolve | 483A | 483.1–483.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaClausePackTest.java`. ~4 tests: (1) `ClausePackSeeder` creates 8 clauses for a `consulting-za` tenant, (2) each clause has correct slug + Tiptap body, (3) creating a document from the SOW template pre-includes `payment-terms` and `ip-ownership` clauses, (4) engagement letter template pre-includes `payment-terms` + `termination`. Pattern: existing accounting or legal clause pack test. |
| 483.5 | Verify existing `ClausePackSeeder` handles the new pack without changes | 483A | 483.1 | Read-only verification — no code modification expected. Classpath scan picks up `consulting-za-clauses/pack.json`. |
| 483.6 | Create `request-packs/consulting-za-creative-brief.json` with questions 1–5 | 483B | -- | New file: `backend/src/main/resources/request-packs/consulting-za-creative-brief.json`. Mirror `request-packs/year-end-info-request-za.json`. `packId: "consulting-za-creative-brief"`, `verticalProfile: "consulting-za"`. First 5 questions per architecture Section 66.9.9 / requirements Section 8.3: brand/company description (long text), target audience (long text), business goals (long text), competitive landscape (long text), must-have deliverables (checkbox list). |
| 483.7 | Add questions 6–10 to the request pack | 483B | 483.6 | Same file. Questions 6–10: known constraints (file upload), existing assets (file upload), tone of voice (short text + optional file), key stakeholders (structured), launch/milestone dates (date fields). |
| 483.8 | Integration test: 10-question request pack + auto-assignment | 483B | 483.6, 483.7 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaRequestPackTest.java`. ~3 tests: (1) `RequestPackSeeder` creates 1 `RequestTemplate` with 10 questions for a `consulting-za` tenant, (2) question types match spec (4 long text, 1 checkbox, 2 file, 1 short text + file, 1 structured, 1 date), (3) creating a new customer in a `consulting-za` tenant auto-assigns the creative-brief request (or surfaces it as a suggested action, per the existing auto-assignment mechanism). Pattern: existing request pack seeder test. |
| 483.9 | Verify existing `RequestPackSeeder` handles the new pack without changes | 483B | 483.6 | Read-only verification. |

### Key Files

**Create:**
- `backend/src/main/resources/clause-packs/consulting-za-clauses/pack.json`
- `backend/src/main/resources/request-packs/consulting-za-creative-brief.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaClausePackTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConsultingZaRequestPackTest.java`

**Read for context:**
- `backend/src/main/resources/clause-packs/accounting-za-clauses/pack.json`
- `backend/src/main/resources/clause-packs/legal-za-clauses/pack.json` — `templateAssociations` reference
- `backend/src/main/resources/request-packs/year-end-info-request-za.json`

### Architecture Decisions

- **`templateAssociations` binds clauses to the Epic 482 templates** — this is why Epic 483 follows Epic 482 in the dependency graph.
- **Single-file clause pack** (same pattern as `accounting-za-clauses`) — keeps the 8 clauses maintainable without a subdirectory of individual Tiptap files.
- **Request pack auto-assigns to new customers** via existing Phase 27 mechanism — no new assignment primitive.

### Non-scope

- No new clause pack loader type.
- No new request-template entity or question type.
- No new compliance pack (agencies reuse `generic-onboarding`).
- No clause → document template auto-embedding beyond `templateAssociations`.

---

## Epic 484: Terminology Key + Team Utilization Widget

**Goal**: Add the `en-ZA-consulting` terminology key (3 overrides: Customer→Client, Time Entry→Time Log, Rate Card→Billing Rates) and ship the `TeamUtilizationWidget` that surfaces Phase 38 utilization data on the company dashboard when the active profile is `consulting-za`.

**References**: Architecture Sections 66.7 (terminology), 66.8 (widget). Requirements Sections 10 and 11. [ADR-246](../adr/ADR-246-profile-gated-dashboard-widgets.md).

**Dependencies**: Epic 480 (profile must be registered so the profile check resolves correctly).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **484A** | 484.1–484.4 | Add `en-ZA-consulting` entry to `frontend/lib/terminology-map.ts` (3 overrides × 4 case variants = 12 keys). Add `useProfile()` hook in `frontend/lib/hooks/useProfile.ts` reading `OrgProfileProvider` context. Update `frontend/__tests__/terminology.test.tsx` and `terminology-integration.test.tsx` with `en-ZA-consulting` assertions. — **Done** (PR #1051) |
| **484B** | 484.5–484.10 | `TeamUtilizationWidget.tsx` component (profile-gated to `consulting-za`, 4-week trend via 4 sequential `/api/utilization/team` calls, sparkline, KPI card, CTA link). Dashboard page integration. Component tests (profile gating, loading, empty, error, happy path). — **Done** (PR #1051) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 484.1 | Add `en-ZA-consulting` entry to `terminology-map.ts` | 484A | -- | Modify: `frontend/lib/terminology-map.ts`. Add entry `"en-ZA-consulting": { ... }` per architecture Section 66.7. 3 overrides × 4 case variants each: `Customer → Client`, `Customers → Clients`, `customer → client`, `customers → clients`; `Time Entry → Time Log`, `Time Entries → Time Logs`, `time entry → time log`, `time entries → time logs`; `Rate Card → Billing Rates`, `Rate Cards → Billing Rates`, `rate card → billing rates`, `rate cards → billing rates`. **Do NOT** add Project→Engagement or Task→Deliverable (deliberately omitted). Pattern: existing `en-ZA-accounting` entry. |
| 484.2 | Create `useProfile()` hook | 484A | -- | New file: `frontend/lib/hooks/useProfile.ts` (the directory `frontend/lib/hooks/` does not yet exist — check first; if other hooks live inline in `frontend/lib/`, place it at `frontend/lib/use-profile.ts` to match convention). Reads `OrgProfileProvider` context (`frontend/lib/org-profile.tsx`) and returns `profileId: "consulting-za" \| "legal-za" \| "accounting-za" \| "consulting-generic" \| null`. Pattern: similar context hooks in `frontend/lib/` (e.g., capability or terminology hooks). Verify the hook doesn't already exist — if it does, skip creation and just re-export. |
| 484.3 | Update `terminology.test.tsx` with new key assertions | 484A | 484.1 | Modify: `frontend/__tests__/terminology.test.tsx`. Add ~5 test cases: `t('Customer')` → 'Client' for `en-ZA-consulting`, `t('Time Entry')` → 'Time Log', `t('Rate Card')` → 'Billing Rates', passthrough for `t('Project')` (not overridden), passthrough for `t('Task')`. Mirror accounting-za variant-test block. |
| 484.4 | Update `terminology-integration.test.tsx` | 484A | 484.1 | Modify: `frontend/__tests__/terminology-integration.test.tsx`. Add ~2 integration tests asserting `<TerminologyText>` resolves correctly under `en-ZA-consulting` for Customer→Client and Time Entry→Time Log. Pattern: existing `en-ZA-legal` / `en-ZA-accounting` integration tests. |
| 484.5 | Create `TeamUtilizationWidget.tsx` — gating + fetch | 484B | 484.2 | New file: `frontend/app/(app)/org/[slug]/(dashboard)/components/TeamUtilizationWidget.tsx`. `"use client"` component. Uses `useProfile()` — returns `null` if profile !== `"consulting-za"` (architecture Section 66.8 gating). Issues 4 sequential calls to `GET /api/utilization/team?weekStart=...&weekEnd=...` (current week back to 3 weeks prior), using existing `lib/api/capacity.ts` client. Pattern: existing dashboard widgets under `frontend/app/(app)/org/[slug]/(dashboard)/components/`. |
| 484.6 | Add KPI card rendering | 484B | 484.5 | Same file. Renders Phase 53 `<KpiCard>` primitive: label "Team Billable Utilization", value `${teamAverages.avgBillableUtilizationPct}%` from current week, delta vs prior week ("+4 pp" / "−2 pp"). Pattern: other KPI widgets in the same directory. |
| 484.7 | Add 4-week sparkline | 484B | 484.6 | Same file. Use existing `sparkline-chart.tsx` primitive; plots the 4 `avgBillableUtilizationPct` values from the 4 calls. CTA link "Team utilization →" → `/org/[slug]/resources/utilization`. |
| 484.8 | Integrate widget into dashboard page | 484B | 484.5–484.7 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx` (or the Phase 53 dashboard grid component it delegates to). Add `<TeamUtilizationWidget />` in the KPI row. The widget self-gates — no `<ModuleGate>` wrapper needed (architecture Section 66.8, ADR-246). |
| 484.9 | Component tests: gating + render states | 484B | 484.8 | New file: `frontend/__tests__/dashboard/TeamUtilizationWidget.test.tsx`. ~6 tests: (1) renders KPI card when profile = `consulting-za`, (2) returns null when profile = `legal-za`, (3) null when profile = `accounting-za`, (4) null when profile = `consulting-generic`, (5) loading state (4 API calls pending), (6) empty/error state. Mock `useProfile()` and `lib/api/capacity.ts`. Include `afterEach(() => cleanup())` for Radix/Shadcn leak prevention (per frontend CLAUDE.md). |
| 484.10 | Component test: 4-week trend renders sparkline | 484B | 484.9 | Same test file, 1 additional test: with mocked `/api/utilization/team` returning 4 incrementing values, assert `<Sparkline>` receives a 4-element `data` prop. Verify prior-week delta calculation. |

### Key Files

**Create:**
- `frontend/lib/hooks/useProfile.ts` (or `frontend/lib/use-profile.ts` per existing convention)
- `frontend/app/(app)/org/[slug]/(dashboard)/components/TeamUtilizationWidget.tsx`
- `frontend/__tests__/dashboard/TeamUtilizationWidget.test.tsx`

**Modify:**
- `frontend/lib/terminology-map.ts` — add `en-ZA-consulting` key
- `frontend/__tests__/terminology.test.tsx` — add new key assertions
- `frontend/__tests__/terminology-integration.test.tsx` — add integration assertions
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — render `<TeamUtilizationWidget />`

**Read for context:**
- `frontend/lib/org-profile.tsx` — `OrgProfileProvider` context shape
- `frontend/lib/api/capacity.ts` — existing utilization API client
- `frontend/components/sparkline-chart.tsx` (or similar) — Phase 53 chart primitive
- Existing dashboard widget for shape reference

### Architecture Decisions

- **Terminology is minimal — 3 overrides, not 20+** (architecture Section 66.7). Keeps the noun-swap risk surface small and avoids the deep-breakage Phase 64 had to clean up for legal.
- **Widget is profile-gated, not module-gated** ([ADR-246](../adr/ADR-246-profile-gated-dashboard-widgets.md)). Utilization is a horizontal Phase 38 feature; `<ModuleGate>` would be the wrong primitive.
- **4 sequential API calls, not a bulk endpoint** (architecture Section 66.8). Bulk variant is explicitly parked; simpler to ship.
- **No backend changes for the widget** — all data from existing Phase 38 `/api/utilization/team` endpoint.

### Non-scope

- No new backend endpoints.
- No bulk utilization endpoint.
- No `<ModuleGate>` wrapper (pattern is `useProfile()` check inside the component).
- No mobile-specific widget variant.
- No new sparkline or KPI primitive.
- No Project → Engagement or Task → Deliverable terminology override.

---

## Epic 485: QA Lifecycle Retarget + Screenshot Baselines

**Goal**: Retarget the existing 90-day Zolani Creative lifecycle script from `consulting-generic` to `consulting-za` so every checkpoint exercises the new pack content, and add Playwright screenshot baselines + curated captures following the Phase 64 pattern.

**References**: Architecture Sections 66.13 (testing strategy), requirements Section 12 (QA lifecycle script update + screenshot baselines).

**Dependencies**: Epics 480–484 (entire pack content + widget must be installed in a test tenant for the script to exercise).

**Scope**: E2E / Process

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **485A** | 485.1–485.4 | Retarget `qa/testplan/demos/consulting-agency-90day-keycloak.md` from `consulting-generic` to `consulting-za`. Update Day 0 provisioning step, Days 1–3 customer archetypes (new MSA + industry fields), Days 3–7 project creation (all 5 templates), Day 14 creative brief, Day 30 retainer report, Day 45 automation triggers, Day 60 SOW generation, Day 75+ utilization widget. |
| **485B** | 485.5–485.7 | Playwright screenshot baselines under `frontend/e2e/screenshots/consulting-lifecycle/`. Curated captures under `documentation/screenshots/consulting-vertical/`. New E2E spec(s) or extend existing `demo-recording.spec.ts` pattern. |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 485.1 | Retarget Day 0 provisioning + Days 1–7 (customer + project creation) | 485A | Epics 480–484 | Modify: `qa/testplan/demos/consulting-agency-90day-keycloak.md`. Day 0: change provisioning profile from `consulting-generic` to `consulting-za`; verify customer + project field packs, rate pack (8 roles), terminology overrides (Client, Time Log, Billing Rates), and `TeamUtilizationWidget` all present. Days 1–3: verify customer creation surfaces industry, company size, primary stakeholder, MSA signed + MSA start date (conditional). Days 3–7: create 5 projects using all 5 templates — assert `campaign_type` is set automatically, `retainer_tier` surfaces on retainer templates. |
| 485.2 | Retarget Days 14–45 (request pack, retainer report, automations) | 485A | 485.1 | Same file. Day 14: send creative-brief request — assert 10 questions from Epic 483 appear. Day 30: close first retainer period — generate monthly retainer report, assert all 4 retainer variables resolve. Day 45: trigger automation rules (budget 80%, retainer closing, unbilled time) — assert notifications fire using rule names from Epic 482. |
| 485.3 | Retarget Days 60–90 (SOW + utilization widget) | 485A | 485.2 | Same file. Day 60: generate SOW for second billing cycle — assert clauses `payment-terms`, `ip-ownership`, `change-requests` are pre-included from Epic 483's `templateAssociations`. Day 75+: utilization widget shows 4-week trend; verify sparkline renders, CTA link navigates. Day 90: gap report covering any inconsistencies. |
| 485.4 | Review + sanity-check the retargeted script | 485A | 485.3 | Read through the full script end-to-end. Confirm every new pack is exercised at least once and every widget/terminology override is verified. Remove any leftover `consulting-generic` references. |
| 485.5 | Add Playwright screenshot directory + baseline spec | 485B | 485A | New directory: `frontend/e2e/screenshots/consulting-lifecycle/` (baselines). New file: `frontend/e2e/tests/consulting-lifecycle/screenshots.spec.ts` (or extend existing `demo-recording.spec.ts`). Capture baseline screenshots using `toHaveScreenshot()`: dashboard with utilization widget, project list with `campaign_type` column, creative brief request form, SOW with agency clauses inserted, monthly retainer report. Pattern: Phase 64 `legal-lifecycle/` screenshot spec. |
| 485.6 | Add curated screenshots for documentation | 485B | 485.5 | New directory: `documentation/screenshots/consulting-vertical/`. Curated PNG captures (non-regression, for marketing/demo use): dashboard with utilization widget, project with campaign_type, monthly retainer report PDF preview, SOW with agency clauses, creative brief request flow. Mirror Phase 64's `documentation/screenshots/legal-vertical/` structure. |
| 485.7 | Run full lifecycle E2E end-to-end against a fresh `consulting-za` tenant | 485B | 485.5, 485.6 | Execute the retargeted script via Playwright (or equivalent harness). Goal: green run, all screenshot baselines match, no regressions vs `consulting-generic`. If anything fails, triage whether it's a pack-content bug (fix in the relevant earlier epic) or a lifecycle-script bug (fix here). |

### Key Files

**Create:**
- `frontend/e2e/tests/consulting-lifecycle/screenshots.spec.ts` (or extend existing spec)
- `frontend/e2e/screenshots/consulting-lifecycle/` (directory of baselines)
- `documentation/screenshots/consulting-vertical/` (directory of curated captures)

**Modify:**
- `qa/testplan/demos/consulting-agency-90day-keycloak.md` — retarget to `consulting-za`

**Read for context:**
- `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` — closest-in-shape lifecycle
- `qa/testplan/demos/legal-za-90day-keycloak.md` — reference
- `frontend/e2e/playwright.legal-lifecycle.config.ts` — Phase 64 screenshot config
- Phase 64 task file section on screenshot infrastructure (Epic 467)

### Architecture Decisions

- **Retarget, don't rewrite**: The 90-day lifecycle already exists for `consulting-generic`. Phase 66 reuses its structure — only the profile, pack-specific checkpoints, and assertions change.
- **Screenshot baselines under `e2e/screenshots/consulting-lifecycle/`** (regression) separate from `documentation/screenshots/consulting-vertical/` (curated) — same split Phase 64 uses.
- **Blocks last because every prior epic must be installable** in a provisioned test tenant for the script to pass end-to-end.

### Non-scope

- No new QA infrastructure (screenshot mechanism, Playwright fixtures, mock auth) — reuses Phase 64 patterns unchanged.
- No new demo tenant archetype beyond the existing Zolani Creative fiction.
- No regression-test coverage beyond the lifecycle script and screenshot baselines.

---

## Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|------------|--------|------------|
| 1 | **Variable-registry gap**: A document template in Epic 482 references a variable (e.g., `{{retainer.hoursRemaining}}`) that doesn't exist in Phase 31 `VariableResolver`. | Medium | High — blocks template rendering, forces either registry extension (out of scope) or template rewrite. | Architecture Section 66.10 explicitly pre-audits every variable and mandates inline composition for gaps. Slice 482B's test (task 482.10) renders every template against seeded fixtures and fails hard on unresolved variables. If a gap is discovered, rewrite the template — do NOT add a registry entry. |
| 2 | **Clause-template association mismatch**: Epic 483's `templateAssociations` references template keys (`statement-of-work`, `engagement-letter`) that must match exactly what Epic 482 authored. A typo silently breaks pre-included clauses. | Medium | Medium — SOW generation would ship without the required clauses, easy to miss in manual QA. | Epic 483 explicitly depends on Epic 482 so keys are known at authoring time. Slice 483A's test 483.4 asserts that creating a document from the SOW template pre-includes `payment-terms` + `ip-ownership`. Include a test for the engagement-letter template too. |
| 3 | **Playwright screenshot flakiness**: Timing, font rendering, or seeded-data drift causes `toHaveScreenshot()` to fail intermittently. | Medium | Medium — noisy CI, engineer fatigue, false confidence in baselines. | Follow Phase 64 screenshot-infrastructure pattern exactly. Use deterministic seed data, fixed viewport sizes, and a dedicated Playwright config (`playwright.consulting-lifecycle.config.ts` mirror of Phase 64's). First 485B green run establishes baselines; subsequent runs are comparisons. |
| 4 | **Terminology-map test coverage gap**: Adding `en-ZA-consulting` without touching every integration test leaves incomplete coverage and lets UI regressions slip. | Low | Low-Medium — UI shows mixed terminology, easy to catch in lifecycle QA. | Slices 484A tasks 484.3 and 484.4 explicitly extend both `terminology.test.tsx` and `terminology-integration.test.tsx`. Because only 3 nouns change (vs. ~25 for legal), coverage surface is small and tractable. |
| 5 | **Automation rule-5 trigger gap**: Architecture Section 66.9.6 flags that `TIME_ENTRY_AGE` is the ideal trigger but doesn't exist; rule 5 uses `FIELD_DATE_APPROACHING` as a near-fit. Automation may not actually fire correctly in the lifecycle script. | Medium | Medium — unbilled-time notification is a demo visible feature; silent failure erodes confidence. | Task 482.4 documents the gap in the pack metadata. Lifecycle script Day 45 (task 485.2) explicitly exercises rule 5 — failure would surface in the QA run. If the fallback trigger genuinely can't fire, mark the rule `enabled: false` in the pack and flag for a future automation-trigger phase (not this one). |
| 6 | **Profile provisioning order**: Field packs must install before project templates so `campaign_type` defaults don't dangle. `TenantProvisioningService` ordering is existing behavior but must not regress. | Low | High — silent pack-content breakage that takes days of QA to find. | Architecture Section 66.4 documents the ordering. Slice 480A's test 480.6 asserts fields exist post-provisioning. Slice 481B's test 481.8 asserts "use template" sets custom-field values correctly (would fail if order regressed). |

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/vertical-profiles/consulting-za.json`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/template-packs/consulting-za/pack.json`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/automation-templates/consulting-za.json`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/terminology-map.ts`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/(dashboard)/components/TeamUtilizationWidget.tsx`
