# Phase 64 — Legal Vertical QA: Terminology, Matter Templates & 90-Day Lifecycle

This phase corrects and extends the legal-za terminology map so the UI feels purpose-built for law firms, seeds 4 matter-type project templates (Litigation, Estates, Collections, Commercial) with pre-populated task lists, adds Playwright `toHaveScreenshot()` infrastructure for regression baselines and curated walkthrough captures, and scripts + executes a 90-day lifecycle QA plan for a fictional Johannesburg law firm ("Mathebula & Partners") exercising every legal module end-to-end.

**Architecture doc**: `architecture/phase64-legal-vertical-qa.md`

**ADRs**: None new. Extends ADR-185 (Terminology Override Mechanism).

**Dependencies on prior phases**:
- Phase 61 (Legal Compliance): Must be complete. Last PR is #963. Confirmed done in TASKS.md.
- Phase 49 (Vertical Architecture): `OrgProfileProvider`, `TerminologyProvider`, `ModuleGate` infrastructure.
- Phase 47 (Accounting QA): Format reference for lifecycle script, gap report, task breakdown structure.
- Phase 16 (Project Templates): `ProjectTemplate`, `TemplateTask`, `ProjectTemplateService`, `ProjectTemplateRepository`.
- Phase 20 (E2E Stack): `compose/docker-compose.e2e.yml`, mock IDP, Playwright fixtures, `loginAs()` helper.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 465 | Legal Terminology Map Fix & Extension | Frontend | -- | S | 465A | **Done** (PR #964) |
| 466 | Matter-Type Project Template Seeder | Backend | -- | M | 466A, 466B | **Done** (PR #965) |
| 467 | Screenshot Infrastructure + Regression Baselines | E2E/Frontend | -- | S | 467A | **Done** (PR #966) |
| 468 | 90-Day Lifecycle Script Writing | Process | 465, 466 | M | 468A | **Done** (PR #967) |
| 469 | Lifecycle Execution + Gap Report | Process/E2E | 467, 468 | L | 469A, 469B | |

---

## Dependency Graph

```
TERMINOLOGY FIX          MATTER TEMPLATES          SCREENSHOT INFRA
(independent)            (independent)             (independent)
──────────────           ──────────────            ──────────────

[E465A                   [E466A                    [E467A
 Fix terminology-map.ts,  ProjectTemplatePackSeeder  Playwright config
 update ~25 mappings,      + pack definition DTO,    toHaveScreenshot(),
 update unit tests,        create seeder,            screenshot helper,
 update integration        integration tests]        directory structure,
 tests for legal-za]                |                 sample baseline test]
        |                [E466B                            |
        |                 4 legal template pack            |
        |                 JSON files (Litigation,          |
        |                 Estates, Collections,            |
        |                 Commercial) + int. tests]        |
        |                          |                       |
        +─────────+────────────────+                       |
                  |                                        |
           LIFECYCLE SCRIPT                                |
           ──────────────                                  |
                  |                                        |
           [E468A                                          |
            Write 90-day lifecycle                         |
            script for Mathebula &                         |
            Partners, 4 clients,                           |
            9 matters, all legal                           |
            modules exercised]                             |
                  |                                        |
                  +────────────────────────────────────────+
                  |
           LIFECYCLE EXECUTION
           ──────────────
                  |
           [E469A
            Execute lifecycle Days 0--45
            via Playwright E2E,
            regression baselines +
            curated screenshots]
                  |
           [E469B
            Execute lifecycle Days 60--90,
            produce gap report,
            final screenshots]
```

**Parallel opportunities**:
- E465A, E466A, and E467A are fully independent and can run in parallel (Stage 0).
- E466B depends on E466A (seeder must exist before pack JSON is written).
- E468A depends on E465A (needs correct terminology to write meaningful steps) and E466B (needs templates to reference in setup steps).
- E469A depends on E467A (needs screenshot infrastructure) and E468A (needs the script).
- E469B is sequential after E469A.

---

## Implementation Order

### Stage 0: Independent Foundation (3 parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a (parallel) | 465 | 465A | Fix legal-za terminology map (~25 mappings), update unit + integration tests. Frontend only. | **Done** (PR #964) |
| 0b (parallel) | 466 | 466A | Create `ProjectTemplatePackSeeder` + `ProjectTemplatePackDefinition` following `AbstractPackSeeder` pattern. Backend only. | **Done** (PR #965) |
| 0c (parallel) | 467 | 467A | Playwright `toHaveScreenshot()` config, screenshot helper utility, directory structure, sample baseline. E2E only. | **Done** (PR #966) |

### Stage 1: Template Pack Content (after 466A)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 466 | 466B | 4 legal project template pack JSON files with pre-populated task lists + integration tests. Backend only. | **Done** (PR #965) |

### Stage 2: Lifecycle Script (after 465A + 466B)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 468 | 468A | Review and finalize the existing 658-line lifecycle script. Process artifact. | **Done** (PR #967) |

### Stage 3: Lifecycle Execution (after 467A + 468A)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 469 | 469A | Execute lifecycle Days 0--45 via Playwright, capture regression baselines + curated screenshots. E2E. | |
| 3b | 469 | 469B | Execute lifecycle Days 60--90, produce gap report, final screenshots. E2E + Process. | |

---

## Epic 465: Legal Terminology Map Fix & Extension

**Goal**: Correct the existing `legal-za` entry in `frontend/lib/terminology-map.ts` by removing incorrect mappings (`Time Entry -> Fee Note`, `Document -> Pleading`), adding missing mappings (Expense -> Disbursement, Budget -> Fee Estimate, Retainer -> Mandate, etc.), and updating all affected test files.

**References**: Architecture doc Section 1 (Terminology Pack Fix & Extension), table in Section 1.2.

**Dependencies**: None (first slice, no backend changes needed).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **465A** | 465.1--465.4 | Update `terminology-map.ts` with ~25 corrected mappings, update unit test assertions, update integration test assertions (fix `Document -> Pleading` test), verify no regressions. Frontend only. | **Done** (PR #964) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 465.1 | Update `legal-za` entry in `terminology-map.ts` | 465A | -- | Modify: `frontend/lib/terminology-map.ts`. Remove `"Time Entry": "Fee Note"`, `"Time Entries": "Fee Notes"`, `"Document": "Pleading"`, `"Documents": "Pleadings"`. Change `"Task": "Work Item"` to `"Task": "Action Item"` (all case variants). Add new mappings: `Invoice/invoice/Invoices/invoices -> Fee Note/fee note/Fee Notes/fee notes`, `Expense/expense/Expenses/expenses -> Disbursement/disbursement/Disbursements/disbursements`, `Budget -> Fee Estimate`, `Retainer/retainer/Retainers/retainers -> Mandate/mandate/Mandates/mandates`, `"Time Entry" -> "Time Recording"`, `"Time Entries" -> "Time Recordings"`. Total ~25 key-value pairs. Pattern: existing `accounting-za` entry in same file. |
| 465.2 | Update unit tests in `terminology.test.tsx` | 465A | 465.1 | Modify: `frontend/__tests__/terminology.test.tsx`. Add test case: `t('Invoice') returns 'Fee Note' for legal-za`. Add test case: `t('Expense') returns 'Disbursement' for legal-za`. Add test case: `t('Retainer') returns 'Mandate' for legal-za`. Add test case: `t('Budget') returns 'Fee Estimate' for legal-za`. Add test case: `t('Task') returns 'Action Item' for legal-za`. Add "all case variants" test for legal-za (mirror the accounting-za variant test). ~6 new test assertions. |
| 465.3 | Update integration tests in `terminology-integration.test.tsx` | 465A | 465.1 | Modify: `frontend/__tests__/terminology-integration.test.tsx`. Fix test at line 237-252: change `t('Document') returns 'Pleading'` to `t('Document') returns 'Document'` (passthrough, mapping removed). Add new integration test: `t('Invoice') returns 'Fee Note' for legal-za` using `TerminologyProvider`. Add new integration test: `t('Task') returns 'Action Item' for legal-za`. ~3 test changes/additions. |
| 465.4 | Run full test suite and verify no regressions | 465A | 465.2, 465.3 | Run `pnpm test` in frontend directory. Verify all terminology tests pass. Verify no other tests reference the removed `Document -> Pleading` mapping. Search for `Pleading` in frontend test files to ensure no breakage. |

### Key Files

**Modify:** `frontend/lib/terminology-map.ts`, `frontend/__tests__/terminology.test.tsx`, `frontend/__tests__/terminology-integration.test.tsx`

### Architecture Decisions

- **Content-only change**: No changes to `TerminologyProvider`, `useTerminology()` hook, or any infrastructure code. Only the static `TERMINOLOGY` map object is modified.
- **Removing Document -> Pleading**: "Pleading" is litigation-specific. A general practice also handles estates, commercial, and collections where documents are just "documents". Removing the override means `t('Document')` returns 'Document' for legal-za (passthrough).
- **Task -> Action Item over Work Item**: Architecture doc specifies "Action Item" as more common in SA legal practice management. The existing `Work Item` mapping is replaced.
- **Invoice -> Fee Note**: This is the correct legal-za mapping. The previous incorrect mapping of `Time Entry -> Fee Note` is removed and replaced with the correct `Time Entry -> Time Recording`.
- **Case variant coverage**: Every mapping includes uppercase, lowercase, singular, and plural variants to match all UI usage contexts (nav labels, breadcrumbs, empty states, buttons, headings).

---

## Epic 466: Matter-Type Project Template Seeder

**Goal**: Create a new `ProjectTemplatePackSeeder` following the `AbstractPackSeeder` pattern, then seed 4 legal matter-type project templates (Litigation, Estates, Collections, Commercial) with pre-populated task lists of 9 action items each.

**References**: Architecture doc Section 2 (Matter-Type Workflow Templates), all 4 template tables.

**Dependencies**: None for 466A (seeder infrastructure). 466B depends on 466A.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **466A** | 466.1--466.7 | Create `ProjectTemplatePackDefinition` record, `ProjectTemplatePackSeeder` extending `AbstractPackSeeder`, register in `TenantProvisioningService`, OrgSettings tracking. Backend only. ~8 files. | **Done** (PR #965) |
| **466B** | 466.8--466.13 | Create `project-template-packs/legal-za.json` with 4 templates + 36 tasks, integration tests verifying template creation + task counts. Backend only. ~4 files. | **Done** (PR #965) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 466.1 | Create `ProjectTemplatePackDefinition` record | 466A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/ProjectTemplatePackDefinition.java`. Record with fields: `String packId`, `String verticalProfile`, `int version`, `List<TemplateEntry> templates`. Inner record `TemplateEntry`: `String name`, `String namePattern`, `String description`, `boolean billableDefault`, `String matterType` (nullable, for linking to custom field), `List<TaskEntry> tasks`. Inner record `TaskEntry`: `String name`, `String description`, `String priority`, `String assigneeRole`, `boolean billable`, `BigDecimal estimatedHours` (nullable). Pattern: `SchedulePackDefinition.java`. |
| 466.2 | Create `ProjectTemplatePackSeeder` extending `AbstractPackSeeder` | 466A | 466.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/ProjectTemplatePackSeeder.java`. Extends `AbstractPackSeeder<ProjectTemplatePackDefinition>`. Pack location: `classpath:project-template-packs/*.json`. In `applyPack()`: iterate `pack.templates()`, create `ProjectTemplate` entity (source = "SEEDER"), then for each `TaskEntry` create a `TemplateTask` entity with `sortOrder` based on list index. Use `SEEDER_CREATED_BY` sentinel UUID. Pattern: `SchedulePackSeeder.java`. |
| 466.3 | Add `projectTemplatePackStatus` field to `OrgSettings` | 466A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "project_template_pack_status", columnDefinition = "jsonb") private List<Map<String, Object>> projectTemplatePackStatus;` with getter and `recordProjectTemplatePackApplication(String packId, int version)` method. Pattern: existing `schedulePackStatus` / `recordSchedulePackApplication()` in same file. |
| 466.4 | Create V88 tenant migration for `project_template_pack_status` column | 466A | 466.3 | New file: `backend/src/main/resources/db/migration/tenant/V88__add_project_template_pack_status.sql`. DDL: `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS project_template_pack_status JSONB;`. Pattern: `V70__add_vertical_profile.sql`. |
| 466.5 | Wire `isPackAlreadyApplied` and `recordPackApplication` in seeder | 466A | 466.2, 466.3 | In `ProjectTemplatePackSeeder`: implement `isPackAlreadyApplied()` checking `settings.getProjectTemplatePackStatus()`, implement `recordPackApplication()` calling `settings.recordProjectTemplatePackApplication()`. Pattern: `SchedulePackSeeder` idempotency checks. |
| 466.6 | Register seeder in `TenantProvisioningService` and `PackReconciliationRunner` | 466A | 466.2 | Modify: `TenantProvisioningService.java` — inject `ProjectTemplatePackSeeder`, call `seedPacksForTenant()` in the provisioning chain BEFORE `SchedulePackSeeder` (schedules reference templates by name). Modify: `PackReconciliationRunner.java` — add `projectTemplatePackSeeder.seedPacksForTenant()` call. |
| 466.7 | Write integration test: seeder infrastructure | 466A | 466.2, 466.5 | New file: `ProjectTemplatePackSeederTest.java`. 3 tests: (1) seeder creates templates + tasks from valid JSON, (2) seeder is idempotent (second call creates no duplicates), (3) seeder skips packs for wrong vertical profile. Provision test tenant with `legal-za` profile. Pattern: `VerticalProfileFilteringTest.java`. |
| 466.8 | Create `project-template-packs/legal-za.json` — Litigation template | 466B | 466A | New file: `backend/src/main/resources/project-template-packs/legal-za.json`. Pack metadata: `packId: "legal-za-project-templates"`, `verticalProfile: "legal-za"`, `version: 1`. First template: name = "Litigation", namePattern = "{customer} — Litigation", description = "General litigation / personal injury matter template", billableDefault = true, matterType = "LITIGATION". 9 tasks per architecture doc Section 2.2 Litigation table. |
| 466.9 | Add Estates template to `legal-za.json` | 466B | 466.8 | Same file. Template: name = "Deceased Estate", namePattern = "{customer} — Estate", matterType = "ESTATES". 9 tasks per architecture doc Section 2.2 Estates table. |
| 466.10 | Add Collections template to `legal-za.json` | 466B | 466.8 | Same file. Template: name = "Collections", namePattern = "{customer} — Collections", matterType = "COLLECTIONS". 9 tasks per architecture doc Section 2.2 Collections table. |
| 466.11 | Add Commercial template to `legal-za.json` | 466B | 466.8 | Same file. Template: name = "Commercial", namePattern = "{customer} — Commercial", matterType = "COMMERCIAL". 9 tasks per architecture doc Section 2.2 Commercial table. |
| 466.12 | Write integration test: legal template pack application | 466B | 466.8-466.11 | New file: `LegalProjectTemplatePackTest.java`. 4 tests: (1) 4 project templates created with correct names, (2) each template has exactly 9 tasks, (3) task sort orders are 1-9, (4) task priorities and assignee roles match spec. Provision tenant with legal-za profile, run seeder, query repositories. Pattern: `AccountingFieldPackTest.java`. |
| 466.13 | Write integration test: template names resolve for schedule pack | 466B | 466.12 | In `LegalProjectTemplatePackTest.java`: 1 additional test verifying `ProjectTemplateRepository.findAllByOrderByNameAsc()` returns the 4 legal templates. |

### Key Files

**Create:** `ProjectTemplatePackDefinition.java`, `ProjectTemplatePackSeeder.java`, `V88__add_project_template_pack_status.sql`, `project-template-packs/legal-za.json`, `ProjectTemplatePackSeederTest.java`, `LegalProjectTemplatePackTest.java`

**Modify:** `OrgSettings.java`, `TenantProvisioningService.java`, `PackReconciliationRunner.java`

### Architecture Decisions

- **New seeder, not API calls**: Project templates are currently created via the API or `saveFromProject()`. For seeded vertical content, we follow the same `AbstractPackSeeder` pattern used by all other pack types. This keeps vertical content as declarative JSON files.
- **V88 migration**: Adds `project_template_pack_status` JSONB column to `org_settings`. Nullable. Follows the exact pattern of `schedule_pack_status`, `rate_pack_status`, etc. **Note**: Phase 63 (Custom Field Graduation) also needs a migration. Phase 64 runs first per scheduling, so V88 is reserved here. Phase 63 will use V89+.
- **Ordering in provisioning chain**: `ProjectTemplatePackSeeder` must run BEFORE `SchedulePackSeeder` because schedule packs reference project templates by name.
- **Source = "SEEDER"**: Seeder-created templates use source = "SEEDER" (not "USER" or "PROJECT"). This distinguishes them from user-created templates in the UI.
- **matterType field is informational**: Stored on the template for potential future auto-suggestion when a matter type custom field is selected. No runtime linking is implemented in this phase.
- **SEEDER_CREATED_BY sentinel**: Uses `UUID.fromString("00000000-0000-0000-0000-000000000001")` consistent with `SchedulePackSeeder`.

---

## Epic 467: Screenshot Infrastructure + Regression Baselines

**Goal**: Add Playwright `toHaveScreenshot()` configuration, create a screenshot capture helper utility, establish directory structure for regression baselines and curated walkthrough shots, and produce a sample baseline test to validate the pipeline.

**References**: Architecture doc Section 3 (Screenshot Infrastructure).

**Dependencies**: None (independent of terminology and templates).

**Scope**: E2E/Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **467A** | 467.1--467.6 | Playwright config updates, screenshot helper utility, directory structure, sample baseline test. E2E only. ~5 files touched. | **Done** (PR #966) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 467.1 | Create legal-lifecycle Playwright config | 467A | -- | New file: `frontend/e2e/playwright.legal-lifecycle.config.ts`. Extends base config. Adds `expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.01, animations: 'disabled' } }`. Sets `use: { ...devices['Desktop Chrome'], deviceScaleFactor: 2 }` for retina captures. Test dir: `./tests/legal-lifecycle`. Pattern: existing `frontend/e2e/playwright.config.ts`. |
| 467.2 | Create screenshot helper utility | 467A | -- | New file: `frontend/e2e/helpers/screenshot.ts`. Exports `captureScreenshot(page, name, options?)` function. Options: `{ curated?: boolean, fullPage?: boolean, locator?: Locator }`. Regression: calls `expect(target).toHaveScreenshot(name + '.png')`. Curated: calls `page.screenshot()` or `locator.screenshot()` and saves to `documentation/screenshots/legal-vertical/`. Naming convention: descriptive names for curated, `day-{DD}-{feature}-{state}.png` for regression. Pattern: `frontend/e2e/fixtures/auth.ts` for export style. |
| 467.3 | Create directory structure | 467A | -- | Create directories (via `.gitkeep`): `frontend/e2e/tests/legal-lifecycle/` (test files), `frontend/e2e/screenshots/legal-lifecycle/` (regression baselines — committed to git), `documentation/screenshots/legal-vertical/` (curated shots). |
| 467.4 | Create sample baseline test | 467A | 467.1, 467.2 | New file: `frontend/e2e/tests/legal-lifecycle/day-00-setup.spec.ts`. Minimal test: login as Alice, navigate to dashboard, call `captureScreenshot(page, 'day-00-dashboard-initial')`. Validates the toHaveScreenshot() pipeline works. Uses `loginAs('alice')` from `fixtures/auth.ts`. |
| 467.5 | Add npm script for legal-lifecycle tests | 467A | 467.1 | Modify: `frontend/package.json`. Add script: `"test:e2e:legal-lifecycle": "playwright test --config e2e/playwright.legal-lifecycle.config.ts"`. Pattern: existing `test:e2e` script. |
| 467.6 | Document screenshot conventions in README | 467A | 467.2 | Modify or create: `frontend/e2e/README.md`. Add section "Legal Lifecycle Screenshots" documenting: naming convention, regression vs curated distinction, how to update baselines (`--update-snapshots`), directory locations. |

### Key Files

**Create:** `frontend/e2e/playwright.legal-lifecycle.config.ts`, `frontend/e2e/helpers/screenshot.ts`, `frontend/e2e/tests/legal-lifecycle/day-00-setup.spec.ts`, `.gitkeep` files for directories

**Modify:** `frontend/package.json`, `frontend/e2e/README.md`

### Architecture Decisions

- **Separate Playwright config**: Legal lifecycle tests use a separate config file to avoid polluting the main test suite with `toHaveScreenshot()` settings and retina deviceScaleFactor. Run independently via dedicated npm script.
- **Dual-purpose capture helper**: Single function handles both regression baselines (via Playwright's built-in snapshot comparison) and curated documentation shots (via `page.screenshot()` to file). The `curated` flag triggers the high-res save.
- **Regression baselines committed to git**: The `e2e/screenshots/legal-lifecycle/` directory is committed. Playwright auto-generates baseline PNGs on first run; subsequent runs compare against them.
- **No changes to existing tests**: Purely additive. The main Playwright config remains unchanged.

---

## Epic 468: 90-Day Lifecycle Script Finalization

**Goal**: Review and finalize the existing 658-line lifecycle test script for Mathebula & Partners, ensuring correct terminology, template references, and screenshot markers.

**References**: Architecture doc Section 4 (90-Day QA Lifecycle Test Plan). Existing file `tasks/qa-legal-lifecycle-test-plan.md`.

**Dependencies**: Epics 465 (terminology fix — ensures script references correct UI labels) and 466B (templates — ensures script can reference matter templates in setup steps).

**Scope**: Process

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **468A** | 468.1--468.4 | Review and finalize the existing lifecycle script, add screenshot step markers, add checkpoint verification lists, ensure template references are correct. Process artifact. | **Done** (PR #967) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 468.1 | Review existing lifecycle script for terminology correctness | 468A | -- | Read: `tasks/qa-legal-lifecycle-test-plan.md` (658 lines). Verify all UI label references use corrected terminology from Epic 465: "Matters" not "Projects", "Action Items" not "Tasks", "Fee Notes" not "Invoices", "Disbursements" not "Expenses", "Mandates" not "Retainers", "Fee Estimates" not "Budgets", "Time Recordings" not "Time Entries". Update any incorrect references. |
| 468.2 | Verify template references in Day 0-3 setup steps | 468A | 468.1 | Check that matter creation steps reference the 4 templates by correct name: "Litigation", "Deceased Estate", "Collections", "Commercial". Verify `matterType` custom field values: LITIGATION, ESTATES, COLLECTIONS, COMMERCIAL. |
| 468.3 | Add screenshot markers to lifecycle script | 468A | 468.1 | Ensure all key steps have screenshot names following the naming convention from architecture doc Section 3.2. Add markers for: trust dashboard, conflict check results, fee note with tariff lines, bank reconciliation, interest run, Section 35 report. |
| 468.4 | Add end-of-day checkpoint summaries | 468A | 468.1 | Review each day section for completeness. Ensure checkpoints cover: data integrity (balances, totals), UI correctness (labels, modules visible), module functionality. Add Day 90 "Final Verdict" section with fork-readiness assessment criteria. |

### Key Files

**Modify:** `tasks/qa-legal-lifecycle-test-plan.md`

### Architecture Decisions

- **Existing script is the base**: The 658-line lifecycle test plan already exists from the architecture phase. This epic refines it rather than writing from scratch.
- **Script format matches accounting precedent**: Same checklist format as `tasks/qa-lifecycle-test-plan.md` (accounting vertical) and `tasks/phase47-lifecycle-script.md`.

---

## Epic 469: Lifecycle Execution + Gap Report

**Goal**: Execute the 90-day lifecycle script against the E2E stack using Playwright tests, capture regression baselines and curated screenshots at each camera-marked step, and produce a gap report documenting all failures, blockers, and visual anomalies.

**References**: Architecture doc Section 4 (full day-by-day outline). Phase 47 Epic 357 for format precedent.

**Dependencies**: Epic 467 (screenshot infrastructure), Epic 468 (finalized lifecycle script).

**Scope**: Process/E2E

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **469A** | 469.1--469.5 | Execute lifecycle Days 0-45: firm setup, client onboarding, first work week, trust deposits, conflict detection, first billing cycle, reconciliation. Capture baselines. E2E test files. | |
| **469B** | 469.6--469.10 | Execute lifecycle Days 60-90: interest run, second billing, complex engagement, adverse parties, quarter review, Section 35 report. Produce gap report. E2E + Process. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 469.1 | Write Playwright test: Day 0 Firm Setup | 469A | -- | New file: `frontend/e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts`. Login as Alice. Verify legal-za profile active (sidebar labels). Set currency ZAR, brand colour, billing/cost rates for 3 users, VAT 15%, verify custom fields loaded, verify 4 matter templates, create trust account, set LPFF rate. Capture screenshot: `day-00-dashboard-legal-nav.png`. ~15 test steps. Uses `loginAs('alice')`, `captureScreenshot()`. |
| 469.2 | Write Playwright test: Days 1-3 Client Onboarding | 469A | 469.1 | New file: `frontend/e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts`. Create 4 clients (Sipho Ndlovu/Individual, Apex Holdings/Company, Moroka Family Trust/Trust, QuickCollect Services/Company). Run conflict checks (all clear). Create 4 initial matters from templates. Set custom fields. Capture: `conflict-check-clear-result.png`. ~20 test steps. |
| 469.3 | Write Playwright test: Day 7-14 First Work + Trust | 469A | 469.2 | New file: `frontend/e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts`. Log time as 3 users. Create court date for Sipho. Add comments. Navigate My Work. Deposit R50,000 into trust (Moroka estate). Approve deposit. Verify client ledger balance. Run conflict check with adverse party match. Capture: `trust-dashboard-overview.png`, `conflict-check-adverse-match.png`. ~18 test steps. |
| 469.4 | Write Playwright test: Day 30 First Billing | 469A | 469.3 | New file: `frontend/e2e/tests/legal-lifecycle/day-30-billing.spec.ts`. Create 4 fee notes: (1) Sipho — hourly + tariff lines, (2) Apex — fixed fee + disbursements, (3) Moroka — trust fee transfer, (4) QuickCollect — collections billing. Verify LSSA tariff line amounts. Verify VAT calculation. Capture: `day-30-fee-note-tariff-lines.png`, `fee-note-tariff-disbursement.png`. ~16 test steps. |
| 469.5 | Write Playwright test: Day 45 Reconciliation + Prescription | 469A | 469.4 | New file: `frontend/e2e/tests/legal-lifecycle/day-45-recon-prescription.spec.ts`. Upload bank CSV (Standard Bank format). Run 3-way reconciliation. Verify match. Check prescription tracking for Sipho. Verify court date lifecycle. Record payment on fee notes. Capture: `bank-reconciliation-matched.png`. ~12 test steps. |
| 469.6 | Write Playwright test: Day 60 Interest + Investment | 469B | 469.5 | New file: `frontend/e2e/tests/legal-lifecycle/day-60-interest.spec.ts`. Run interest calculation with LPFF split for Moroka trust balance. Verify interest posted correctly. Place investment distinguishing §86(3) (firm discretion) vs §86(4) (client instructed). Create second billing cycle fee notes. Capture: `interest-run-lpff-split.png`. ~14 test steps. |
| 469.7 | Write Playwright test: Day 75 Complex Engagement | 469B | 469.6 | New file: `frontend/e2e/tests/legal-lifecycle/day-75-complex.spec.ts`. Create additional matters for existing clients (multi-matter per client). Add adverse parties to registry. Run conflict stress test (multiple potential matches). Progress estate matter (Moroka). ~12 test steps. |
| 469.8 | Write Playwright test: Day 90 Quarter Review + Section 35 | 469B | 469.7 | New file: `frontend/e2e/tests/legal-lifecycle/day-90-review.spec.ts`. Navigate portfolio review. Generate Section 35 report. Run trust reports. Check profitability dashboard. Verify role-based access (Carol cannot approve trust transactions). Capture: `section-35-report.png`. ~14 test steps. |
| 469.9 | Execute all lifecycle tests and record results | 469B | 469.1-469.8 | Run `pnpm test:e2e:legal-lifecycle` against E2E stack. Record pass/fail for every test step. Note any test that requires workarounds or skipping. Collect all regression baselines. Copy curated screenshots to `documentation/screenshots/legal-vertical/`. |
| 469.10 | Produce gap report | 469B | 469.9 | New file: `tasks/phase64-gap-report.md`. Format: executive summary, statistics (passed/failed/skipped), gaps by day, severity classification (blocker/major/minor), affected module, recommended fix phase. Pre-log known gaps from architecture doc: no conveyancing template, no matter closure workflow, no smart deadline-to-calendar scheduling, terminology limited to ~30-40 high-visibility locations. Pattern: `tasks/phase47-gap-report-agent.md`. |

### Key Files

**Create:** `frontend/e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts`, `day-01-03-onboarding.spec.ts`, `day-07-14-work-trust.spec.ts`, `day-30-billing.spec.ts`, `day-45-recon-prescription.spec.ts`, `day-60-interest.spec.ts`, `day-75-complex.spec.ts`, `day-90-review.spec.ts`, `tasks/phase64-gap-report.md`

### Architecture Decisions

- **One spec file per day segment**: Each lifecycle day segment gets its own Playwright spec file. This allows partial re-runs when debugging failures and keeps each file under the 800-line builder limit.
- **Sequential execution**: Tests run sequentially (`workers: 1` in the legal-lifecycle config) because later days depend on data created in earlier days.
- **Known gaps pre-logged**: The gap report pre-populates known limitations from the architecture doc's "Out of Scope" section. Additional gaps discovered during execution are added alongside.
- **Curated screenshots are a manual step**: The `captureScreenshot()` helper saves curated shots during test execution. Final copies to `documentation/screenshots/legal-vertical/` are part of the execution task.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Terminology map changes break existing frontend tests | Low | Medium | Epic 465 explicitly updates both test files. `pnpm test` run as final task. |
| `ProjectTemplatePackSeeder` ordering conflict with `SchedulePackSeeder` | Medium | Medium | 466.6 explicitly wires seeder BEFORE schedule pack seeder in provisioning chain. Integration test in 466.7 validates ordering. |
| V88 migration conflicts with concurrent Phase 63 migrations | Low | High | Phase 64 runs first per scheduling. Phase 63 will use V89+. |
| E2E stack does not have legal-za profile activated for seed org | Medium | High | Day 0 test step verifies profile is active. If not, script must include profile activation step in settings. |
| Lifecycle tests are flaky due to page load timing | Medium | Medium | Use Playwright auto-waiting. Add explicit `waitForSelector()` before screenshot captures. Set generous timeout in legal-lifecycle config (60s). |
| toHaveScreenshot() baselines differ across CI vs local environments | Medium | Low | Legal-lifecycle config pins browser to Chromium, deviceScaleFactor: 2. Baselines generated locally. |
| ProjectTemplate entity lacks a `matterType` or `verticalProfile` field | Medium | Low | matterType is stored in pack JSON only. No DB column added in this phase. |

---

## Test Summary

| Epic | Slice | New Tests | Coverage |
|------|-------|-----------|----------|
| 465 | 465A | ~9 | Terminology map: 6 new unit assertions for legal-za + 3 integration test changes |
| 466 | 466A | ~3 | Seeder infrastructure: creation, idempotency, profile filtering |
| 466 | 466B | ~5 | Legal templates: 4 templates created, 9 tasks each, correct priorities/roles |
| 467 | 467A | ~1 | Sample baseline screenshot test (validates pipeline) |
| 469 | 469A | ~5 specs | Lifecycle Days 0-45: ~80 test steps across 5 spec files |
| 469 | 469B | ~4 specs | Lifecycle Days 60-90: ~50 test steps across 3 spec files + gap report |
| **Total** | | **~18 unit/integration + 9 E2E specs** | Full legal vertical terminology, template seeding, and 90-day lifecycle coverage |
