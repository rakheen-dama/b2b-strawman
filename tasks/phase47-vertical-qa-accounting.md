# Phase 47 — Vertical QA: Small SA Accounting Firm

This phase creates a production-quality accounting vertical profile (pack data, templates, clauses, automation rules), implements vertical-scoped pack filtering infrastructure, scripts a 90-day accelerated lifecycle, and produces a categorised gap analysis via a two-pass (agent + founder) QA methodology.

**Architecture doc**: `architecture/phase47-vertical-qa-accounting.md`

**ADRs**:
- [ADR-181](adr/ADR-181-vertical-profile-structure.md) — Vertical Profile Structure (JSON manifest + existing seeders)
- [ADR-182](adr/ADR-182-terminology-override-mechanism.md) — Terminology Override Mechanism (per-vertical message directory)
- [ADR-183](adr/ADR-183-qa-methodology-vertical-readiness.md) — QA Methodology for Vertical Readiness (two-pass: agent + founder)
- [ADR-184](adr/ADR-184-vertical-scoped-pack-filtering.md) — Vertical-Scoped Pack Filtering (`verticalProfile` on pack JSON + `AbstractPackSeeder` filter)

**Dependencies on prior phases**:
- Phase 11 (Custom Fields): `FieldPackSeeder`, `FieldPackDefinition`, field pack JSON format
- Phase 12 (Templates): `TemplatePackSeeder`, `TemplatePackDefinition`, template pack JSON + content file format
- Phase 14 (Compliance): `CompliancePackSeeder`, `CompliancePackDefinition`, compliance pack JSON format
- Phase 31 (Clauses): `ClausePackSeeder`, `ClausePackDefinition`, clause pack JSON format with Tiptap body
- Phase 37 (Automations): `AutomationTemplateSeeder`, `AutomationTemplatePack`, automation template JSON format
- Phase 38 (Requests): `RequestPackSeeder`, `RequestPackDefinition`, request pack JSON format
- Phase 39 (Org Provisioning): `AccessRequestApprovalService`, `TenantProvisioningService`, `AccessRequest.industry`
- Phase 13 (Dedicated Schema): Schema-per-tenant isolation, `OrgSettings` entity
- Phase 20 (E2E Stack): `compose/docker-compose.e2e.yml`, mock IDP, Playwright fixtures

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 354 | Vertical Profile Infrastructure + Field & Compliance Packs | Backend | — | M | 354A | **Done** (PR #684) |
| 355 | Document Template Pack + Clause Pack | Backend | — | M | 355A | |
| 356 | Automation Templates + Request Pack + Profile Manifest | Backend + Frontend | — | S | 356A | |
| 357 | 90-Day Lifecycle Script + Agent Execution | Process | 354, 355, 356 | L | 357A | |
| 358 | Founder Walkthrough Guide + Gap Consolidation | Process | 357 | M | 358A | |

---

## Dependency Graph

```
INFRASTRUCTURE + PACK CONTENT (parallel tracks)
───────────────────────────────────────────────────────────────

[E354A V70 migration,                [E355A Template pack:
 OrgSettings.verticalProfile,         pack.json + 7 content files,
 AbstractPackSeeder filtering,        clause pack: pack.json
 provisioning chain updates,          + 7 clause bodies,
 field packs x2, compliance pack,     template-clause associations,
 + integration tests]                 + integration tests]
        |                                     |
        |                            [E356A Automation templates
        |                             (3 rules), request pack
        |                             (8 items), profile manifest,
        |                             terminology overrides,
        +─────────────+──────────────+ integration tests]
                      |
PROCESS ARTIFACTS (sequential, after all packs)
───────────────────────────────────────────────────────────────

                      |
               [E357A 90-day lifecycle
                script writing +
                agent-driven execution
                via Playwright MCP +
                agent gap report]
                      |
               [E358A Founder walkthrough
                guide creation +
                gap consolidation report +
                fork readiness assessment]
```

**Parallel opportunities**:
- E354A, E355A, and E356A can all run in parallel (independent pack content). However, E354A must complete first if E355A/E356A packs need vertical profile filtering to work correctly. In practice: E355A and E356A create JSON files with `"verticalProfile": "accounting-za"` but the filtering logic (in E354A) is what makes them vertical-scoped at runtime. The packs are valid JSON regardless. Recommendation: start E354A first, then E355A and E356A in parallel once E354A is in progress.
- E357A requires all three pack slices to be merged and the E2E stack rebuilt with the new packs.
- E358A is sequential after E357A (agent gap report informs founder walkthrough).

---

## Implementation Order

### Stage 0: Infrastructure + Field/Compliance Packs

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 354 | 354A | V70 migration, `OrgSettings.verticalProfile`, `AbstractPackSeeder` filtering, provisioning chain, field packs x2, compliance pack, integration tests (~8). Backend only. | **Done** (PR #684) |

### Stage 1: Template/Clause + Automation/Request Packs (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 355 | 355A | Template pack `accounting-za/pack.json` + 7 Tiptap content files, clause pack with 7 clauses + associations, integration tests (~6). Backend only. | |
| 1b (parallel) | 356 | 356A | Automation templates (3 rules), request pack (8 items), profile manifest, terminology overrides, integration tests (~4). Backend + Frontend. | |

### Stage 2: Agent Execution

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 357 | 357A | Write 90-day lifecycle script, execute via Playwright MCP, produce agent gap report. Process artifact. | |

### Stage 3: Founder Pass + Consolidation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 358 | 358A | Founder walkthrough guide, gap consolidation, fork readiness assessment. Process artifact. | |

---

## Epic 354: Vertical Profile Infrastructure + Field & Compliance Packs

**Goal**: Implement vertical-scoped pack filtering (ADR-184) by threading `AccessRequest.industry` through the provisioning chain to `OrgSettings.verticalProfile` and adding a filter in `AbstractPackSeeder.doSeedPacks()`. Create the two accounting field packs and the FICA compliance pack with `"verticalProfile": "accounting-za"` tags. Verify with integration tests that packs are filtered correctly.

**References**: Architecture doc Sections 47.2.2, 47.2.3, 47.3.1, 47.3.2, 47.3.3, 47.8.3. ADR-184.

**Dependencies**: None (first slice).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **354A** | 354.1--354.14 | V70 migration, `OrgSettings.verticalProfile` field, `AbstractPackSeeder` filtering logic, `TenantProvisioningService` parameter update, `AccessRequestApprovalService` industry-to-profile mapping, 6 pack definition DTO updates, `accounting-za-customer.json`, `accounting-za-project.json`, `fica-kyc-za/pack.json`, integration tests (~8). Backend only. | **Done** (PR #684) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 354.1 | Create V70 tenant migration | 354A | — | New file: `backend/src/main/resources/db/migration/tenant/V70__add_vertical_profile.sql`. DDL: `ALTER TABLE org_settings ADD COLUMN vertical_profile VARCHAR(50)`. No index needed. Pattern: `V69__member_org_role_cleanup.sql`. |
| 354.2 | Add `verticalProfile` field to `OrgSettings` entity | 354A | 354.1 | Modify: `settings/OrgSettings.java`. Add `@Column(name = "vertical_profile", length = 50) private String verticalProfile;` with getter/setter. Nullable, set post-creation. |
| 354.3 | Update `TenantProvisioningService` to accept and store `verticalProfile` | 354A | 354.2 | Modify: `provisioning/TenantProvisioningService.java`. Change `provisionTenant(String, String)` to `provisionTenant(String, String, String)`. Set `orgSettings.setVerticalProfile(verticalProfile)` BEFORE calling seeders. |
| 354.4 | Update `AccessRequestApprovalService.approve()` with industry-to-profile mapping | 354A | 354.3 | Modify: `accessrequest/AccessRequestApprovalService.java`. Add static `Map<String, String>` with `"Accounting" -> "accounting-za"`, `"Legal" -> "law-za"`. Pass mapped profile to `provisionTenant()`. |
| 354.5 | Add `getVerticalProfile()` abstract method to `AbstractPackSeeder` | 354A | 354.2 | Modify: `seeder/AbstractPackSeeder.java`. Add `protected abstract String getVerticalProfile(D pack);`. In `doSeedPacks()`, add filter: `if (packProfile != null && !packProfile.equals(tenantProfile)) { continue; }`. |
| 354.6 | Add `verticalProfile` field to `FieldPackDefinition` record | 354A | 354.5 | Modify: `fielddefinition/FieldPackDefinition.java`. Add `String verticalProfile` component. Implement `getVerticalProfile()` in `FieldPackSeeder`. |
| 354.7 | Add `verticalProfile` field to remaining 5 pack definition DTOs | 354A | 354.5 | Modify 5 DTOs + 5 seeders: `TemplatePackDefinition`, `ClausePackDefinition`, `CompliancePackDefinition`, `RequestPackDefinition`, `AutomationTemplatePack`. Each returns `pack.verticalProfile()`. |
| 354.8 | Create `accounting-za-customer.json` field pack | 354A | 354.6 | New file: `field-packs/accounting-za-customer.json`. 16 fields per architecture doc Section 47.3.1. Include `"verticalProfile": "accounting-za"`. Pattern: `field-packs/common-customer.json`. |
| 354.9 | Create `accounting-za-project.json` field pack | 354A | 354.6 | New file: `field-packs/accounting-za-project.json`. 5 fields per architecture doc Section 47.3.2. Include `"verticalProfile": "accounting-za"`. Pattern: `field-packs/common-project.json`. |
| 354.10 | Create `fica-kyc-za` compliance pack | 354A | 354.7 | New file: `compliance-packs/fica-kyc-za/pack.json`. 9 checklist items per architecture doc Section 47.3.3. Include `"verticalProfile": "accounting-za"`. Pattern: `compliance-packs/sa-fica-company/pack.json`. |
| 354.11 | Write integration test: vertical profile filtering | 354A | 354.5 | New file: `seeder/VerticalProfileFilteringTest.java`. 4 tests: accounting profile gets accounting+universal packs, null profile skips accounting packs, different profile skips accounting packs, universal packs always applied. |
| 354.12 | Write integration test: accounting field pack application | 354A | 354.8, 354.9 | New file: `fielddefinition/AccountingFieldPackTest.java`. 2 tests: customer pack creates 16 fields, project pack creates 5 fields. |
| 354.13 | Write integration test: FICA compliance pack application | 354A | 354.10 | New file: `compliance/FicaKycZaPackTest.java`. 2 tests: checklist template with 9 items, correct required flags. |
| 354.14 | Update `TenantProvisioningService` callers for new signature | 354A | 354.3 | Search all callers of `provisionTenant(String, String)`, update to pass `null` as third parameter where no profile is available. |

### Key Files

**Create:** `V70__add_vertical_profile.sql`, `accounting-za-customer.json`, `accounting-za-project.json`, `fica-kyc-za/pack.json`, `VerticalProfileFilteringTest.java`, `AccountingFieldPackTest.java`, `FicaKycZaPackTest.java`

**Modify:** `OrgSettings.java`, `AbstractPackSeeder.java`, `TenantProvisioningService.java`, `AccessRequestApprovalService.java`, 6 pack definition DTOs + 6 seeders

### Architecture Decisions

- **V70 migration**: Single `ALTER TABLE` — no index, no backfill. Nullable VARCHAR(50). Existing tenants get `null` (universal packs only).
- **Backward compatibility**: Existing pack JSON files have no `verticalProfile` field. Jackson deserializes missing fields as `null`. Filter treats `null` pack profile as universal.
- **Industry mapping is hardcoded**: `Map.of("Accounting", "accounting-za", "Legal", "law-za")`. Acceptable for <5 verticals.
- **OrgSettings creation ordering**: `TenantProvisioningService` must set `verticalProfile` on OrgSettings BEFORE calling seeders.

---

## Epic 355: Document Template Pack + Clause Pack

**Goal**: Create the `accounting-za` template pack (7 document templates with Tiptap JSON content) and the `accounting-za-clauses` clause pack (7 clauses with template-clause associations).

**References**: Architecture doc Sections 47.3.4, 47.3.5.

**Dependencies**: None for file creation. Runtime requires Epic 354 for filtering.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **355A** | 355.1--355.11 | Template pack `accounting-za/pack.json` + 7 Tiptap content files, clause pack `accounting-za-clauses/pack.json` with 7 clauses + 3 template-clause associations, integration tests (~6). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 355.1 | Create template pack manifest `pack.json` | 355A | — | New file: `template-packs/accounting-za/pack.json`. 7 template entries. Include `"verticalProfile": "accounting-za"`. Pattern: `template-packs/common/pack.json`. |
| 355.2 | Create `engagement-letter-bookkeeping.json` | 355A | 355.1 | Tiptap JSON. SAICA-format monthly bookkeeping engagement letter. Variables: `customer.name`, `customer.customFields.company_registration_number`, `org.name`, `org.brandColor`. |
| 355.3 | Create `engagement-letter-tax-return.json` | 355A | 355.1 | Tiptap JSON. Annual tax return engagement letter. Variables: `customer.name`, `customer.customFields.sars_tax_reference`, `project.customFields.tax_year`. |
| 355.4 | Create `engagement-letter-advisory.json` | 355A | 355.1 | Tiptap JSON. Advisory services engagement letter with hourly rate terms. |
| 355.5 | Create `monthly-report-cover.json` | 355A | 355.1 | Tiptap JSON. Professional cover page for monthly deliverables. |
| 355.6 | Create `invoice-za.json` | 355A | 355.1 | Tiptap JSON. SA tax invoice with VAT display, SARS formatting, bank details section. |
| 355.7 | Create `statement-of-account.json` and `fica-confirmation.json` | 355A | 355.1 | Statement-of-account: stub using only `customer.*` variables (invoice aggregation is a known gap). FICA confirmation: letter confirming KYC completion. |
| 355.8 | Create clause pack `pack.json` | 355A | — | New file: `clause-packs/accounting-za-clauses/pack.json`. 7 clauses with Tiptap bodies + `templateAssociations` linking to engagement letter templates. Include `"verticalProfile": "accounting-za"`. Pattern: `clause-packs/standard-clauses/pack.json`. |
| 355.9 | Write integration test: template pack application | 355A | 355.1 | 3 tests: 7 templates created, content is valid Tiptap JSON, verticalProfile set. |
| 355.10 | Write integration test: clause pack application | 355A | 355.8 | 2 tests: 7 clauses created, 3 template-clause associations correct. |
| 355.11 | Verify Tiptap variable references | 355A | 355.2-355.7 | 1 test: render templates with test data, verify variable placeholders resolve. |

### Key Files

**Create:** `template-packs/accounting-za/pack.json`, 7 content files, `clause-packs/accounting-za-clauses/pack.json`, `AccountingTemplatePackTest.java`, `AccountingClausePackTest.java`

### Architecture Decisions

- **Statement-of-account is a stub**: Invoice aggregation variables may not resolve. Pre-documented gap.
- **Clause body is Tiptap JSON**: Same format as templates, using `{ "type": "variable", "attrs": { "key": "..." } }`.
- **Template-clause associations**: Clause pack's `templateAssociations` array links clauses to templates by `templateKey`.

---

## Epic 356: Automation Templates + Request Pack + Profile Manifest

**Goal**: Create the automation rule templates (3 valid rules), year-end information request template, profile manifest, and terminology override file.

**References**: Architecture doc Sections 47.3.6, 47.3.7, 47.2.1, 47.2.4.

**Dependencies**: None for file creation. Runtime requires Epic 354 for filtering.

**Scope**: Backend + Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **356A** | 356.1--356.7 | Automation templates (3 rules), request pack (8 items), profile manifest, terminology overrides, integration tests (~4). Backend + Frontend. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 356.1 | Create automation template pack `accounting-za.json` | 356A | — | 3 rules only (3 invalid triggers excluded): `fica-reminder` (CUSTOMER_STATUS_CHANGED), `budget-alert` (BUDGET_THRESHOLD_REACHED), `invoice-overdue` (INVOICE_STATUS_CHANGED). Include `"verticalProfile": "accounting-za"`. Pattern: `automation-templates/common.json`. |
| 356.2 | Create request pack `year-end-info-request-za.json` | 356A | — | 8 items: trial balance, bank statements, loan agreements, fixed asset register, debtors/creditors age analysis, insurance schedule, payroll summary. Include `"verticalProfile": "accounting-za"`. Pattern: `request-packs/tax-return.json`. |
| 356.3 | Create profile manifest `vertical-profiles/accounting-za.json` | 356A | — | Documentation artifact listing all pack references, rate defaults (ZAR), tax defaults (VAT 15%), terminology override directory. Not consumed at runtime. |
| 356.4 | Create terminology override `en-ZA-accounting/common.json` | 356A | — | Frontend file: Projects→Engagements, Tasks→Work Items, Customers→Clients, Proposals→Engagement Letters, etc. Runtime merge is a known gap per ADR-182. |
| 356.5 | Write integration test: automation template pack | 356A | 356.1 | 2 tests: 3 rules created with correct slugs/triggers, FICA reminder has 7-day delay. |
| 356.6 | Write integration test: request pack | 356A | 356.2 | 2 tests: 8 items created with correct sort order, required flags correct. |
| 356.7 | Validate profile manifest JSON | 356A | 356.3 | Unit test verifying well-formed JSON with correct pack references. |

### Key Files

**Create:** `automation-templates/accounting-za.json`, `request-packs/year-end-info-request-za.json`, `vertical-profiles/accounting-za.json`, `frontend/src/messages/en-ZA-accounting/common.json`, test files

### Architecture Decisions

- **3 rules, not 6**: Three triggers don't exist (`PROPOSAL_SENT`, `FIELD_DATE_APPROACHING`, `CHECKLIST_COMPLETED`). Excluded to avoid Jackson deserialization failures. Pre-logged as gaps.
- **Terminology overrides are aspirational**: File created, runtime loading is a known gap.

---

## Epic 357: 90-Day Lifecycle Script + Agent Execution

**Goal**: Write the 90-day lifecycle script and execute it against the E2E stack via Playwright MCP. Produce the agent gap report.

**References**: Architecture doc Sections 47.5, 47.6.1. Requirements doc Section 2.

**Dependencies**: Epics 354, 355, 356 (all packs merged, E2E stack rebuilt).

**Scope**: Process

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **357A** | 357.1--357.5 | Write lifecycle script, execute via Playwright MCP, produce agent gap report. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 357.1 | Write lifecycle script: Day 0--3 (Setup + Onboarding) | 357A | — | Output: `tasks/phase47-lifecycle-script.md`. Covers firm setup, first client onboarding (Kgosi Construction), 3 additional clients with different entity types/billing models. |
| 357.2 | Write lifecycle script: Day 7--60 (Operations + Billing) | 357A | 357.1 | Time logging, FICA completion, first billing cycle, bulk billing, expenses, profitability review. Include prerequisite data blocks. |
| 357.3 | Write lifecycle script: Day 75--90 (Year-End + Review) | 357A | 357.2 | Year-end engagement, information request, final profitability review, fork readiness question. |
| 357.4 | Execute lifecycle script via Playwright MCP | 357A | 357.1-357.3 | Prerequisites: E2E stack running, all packs deployed. Execute day by day, authenticate as Alice/Bob/Carol per script. Record pass/fail for every checkpoint. |
| 357.5 | Produce agent gap report | 357A | 357.4 | Output: `tasks/phase47-gap-report-agent.md`. Summary statistics, all gaps by day, critical path blockers. Pre-log known gaps from architecture doc. |

### Key Files

**Create:** `tasks/phase47-lifecycle-script.md`, `tasks/phase47-gap-report-agent.md`

---

## Epic 358: Founder Walkthrough Guide + Gap Consolidation

**Goal**: Create the founder walkthrough guide and consolidate both gap reports into a prioritised analysis with fork readiness assessment.

**References**: Architecture doc Sections 47.6.2, 47.7. ADR-183.

**Dependencies**: Epic 357.

**Scope**: Process

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **358A** | 358.1--358.4 | Founder walkthrough guide, gap report template, consolidated analysis. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 358.1 | Write founder walkthrough guide | 358A | — | Output: `tasks/phase47-founder-walkthrough-guide.md`. Conversational, 7 sections: first impressions, client onboarding, daily rhythm, billing, profitability, portal, gap review. |
| 358.2 | Create founder gap report template | 358A | — | Output: `tasks/phase47-gap-report-founder.md`. Blank template with gap format + "agreement with agent gaps" section. |
| 358.3 | Consolidate gap reports | 358A | 358.1, 358.2 | Output: `tasks/phase47-gap-analysis-consolidated.md`. Executive summary, blockers, majors, minors, vertical profile quality, fix phase recommendations, fork readiness assessment. |
| 358.4 | Pre-stage known gaps | 358A | — | Pre-populate consolidated report skeleton with known gaps (3 missing triggers, statement-of-account, terminology overrides). |

### Key Files

**Create:** `tasks/phase47-founder-walkthrough-guide.md`, `tasks/phase47-gap-report-founder.md`, `tasks/phase47-gap-analysis-consolidated.md`

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Pack DTO changes break existing JSON deserialization | Low | High | `verticalProfile` is nullable — Jackson ignores missing fields. Full test suite after DTO changes. |
| Tiptap content files have invalid JSON | Medium | Medium | Integration tests verify each content file. |
| E2E stack doesn't pick up new packs without rebuild | Medium | Low | Run `e2e-rebuild.sh backend` after merging. Document in prerequisites. |
| `TenantProvisioningService` signature change breaks callers | Low | Medium | Task 354.14 updates all callers. Tests verify backward compatibility. |
| OrgSettings created by seeder before provisioning sets `verticalProfile` | Medium | Medium | Create OrgSettings explicitly in provisioning service BEFORE calling seeders. |

---

## Test Summary

| Epic | Slice | New Tests | Coverage |
|------|-------|-----------|----------|
| 354 | 354A | ~8 | Vertical profile filtering (4), field packs (2), compliance pack (2) |
| 355 | 355A | ~6 | Template pack (3), clause pack (2), variable rendering (1) |
| 356 | 356A | ~4 | Automation templates (2), request pack (2) |
| **Total** | | **~18** | Full vertical profile infrastructure + all 6 pack types |
