# Phase 87 — Legal-Native Shell: Matter/Client Vocabulary on the Wire, One Diary, Settings Consolidation

Phase 87 is a **re-shelling phase**: the data model is untouched and the presentation contract is rebuilt for one audience, a South African law firm. In a fixed order it (1) renames exactly two nouns on the wire — `project → matter`, `customer → client` — in REST paths, DTO records/fields, JSON, TypeScript types, API modules, route segments and portal paths while every JPA entity, repository, service, table and column keeps its name ([ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md)); (2) deletes the runtime terminology layer and writes the legal word into the copy ([ADR-338](../adr/ADR-338-terminology-layer-removal-single-vocabulary.md)); (3) re-shells navigation, Diary, home, lists, detail tabs and settings around an attorney's week ([ADR-339](../adr/ADR-339-attorney-week-navigation-and-one-diary.md), [ADR-340](../adr/ADR-340-disbursement-is-the-face-of-matter-costs.md), [ADR-341](../adr/ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md)); (4) pins what must not move — audit/EntityTag strings and the MCP tool contract — and re-runs the legal-za 90-day lifecycle through the new shell with scenario amendments authorised for URL/label/nav only ([ADR-342](../adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md), [ADR-343](../adr/ADR-343-mcp-contract-pinning-test.md), [ADR-344](../adr/ADR-344-qa-scenario-amendment-authorisation-phase87.md)). Architecture: [`architecture/phase87-legal-native-shell.md`](../architecture/phase87-legal-native-shell.md) (Section 12); requirements: [`requirements/claude-code-prompt-phase87.md`](../requirements/claude-code-prompt-phase87.md).

**Baseline the closure report measures against (§12.1, re-confirmed by `ls`/`find` on 2026-10-18 at HEAD `41cb211fb`):** 62 backend top-level packages, 121 `@RestController`s (32 carrying `/projects` or `/customers`), 103 `@Entity` classes, 29-entry audit catalogue, 27 `Capability` constants, 8 vertical modules, one profile (`legal-za`); **100** frontend `page.tsx` (**86** under `org/[slug]`, **44** under `settings/`), **18** portal `page.tsx`; `NAV_GROUPS` 6 zones / 25 items; terminology layer 262 call sites / 82 files (73 frontend + 10 portal non-test files contain a terminology symbol today); `/api/projects` literals in 30 frontend files, `/api/customers` in 32, 17 `/portal/projects` hits in portal, 27 hits in `scripts/regression-test.sh`; full backend verify ≈ 12.5 min.

**Migrations: none in this phase.** Tenant high-water is `V3__normalise_work_types.sql`, global `V1__baseline.sql` (confirmed by `ls … | sort -V | tail -1`). Next free tenant number is **V4**, global **V2** — reserved for Phase 84. `BaselineSchemaParityTest` must pass unchanged in every epic; any PR that adds a `V*.sql` file is a scope violation.

**Epic → architecture slice mapping (one epic per §12.12 capability slice, PR rule preserved):** S1→607, S2→608, S3→609, S4→610, S5→611, S6a→612, S6b→613, S7→614, S8→615, S9→616, S10→617. Each epic lands as **one PR** (609 may land as three — finance / legal / portal — see its slices); slices inside an epic are builder-sized commits on the epic branch and are single-scope (backend-only, frontend-only, portal-only, or external-callers/Playwright-only). Branch convention: `phase87/<nn>-<slug>`.

**Path shorthands used below:** `$B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `$T` = `backend/src/test/java/io/b2mash/b2b/b2bstrawman`, `$R` = `backend/src/main/resources`, `$F` = `frontend`, `$FA` = `frontend/app/(app)/org/[slug]`, `$P` = `portal`. **Gate shorthands:** **BE-VERIFY** = `cd backend && ./mvnw spotless:apply && bash scripts/verify.sh` with **no positional goals** (lesson 2026-10-16: `verify.sh clean` runs `verify clean` and wipes the reports; for a clean run use `bash scripts/verify-preflight.sh && caffeinate -is ./mvnw clean verify -q`), run detached with a marker file (memory `background_bash_timeout_full_verify`), one verify at a time; **FE-GATE** = `cd frontend && rm -rf .next && SHELL=/bin/bash NODE_OPTIONS="" pnpm lint && pnpm format:check && pnpm test && pnpm build` (clean `.next` before marker-grade builds — lesson 2026-10-16); **PORTAL-GATE** = `cd portal && pnpm lint && pnpm test && pnpm build && pnpm exec prettier --check .` (portal has **no** `format:check` script until 610F adds it); **E2E-LEGAL** = `bash compose/scripts/e2e-up.sh` (or `e2e-reseed.sh`) then `cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle` **and** `pnpm test:e2e:legal-depth-ii`, run in a worktree and `git checkout -- frontend/test-results documentation/screenshots` after (memory `env_playwright_legal_configs_dirty_tree`); **REGRESSION** = `bash scripts/regression-test.sh` against the e2e stack. **Review:** every PR — docs-only included — gets CodeRabbit (bounded wait; `DEFERRED` only on genuine no-show) plus the two `feature-dev:code-reviewer` passes and the audit-trail block in the PR body; no self-granted exemptions (lesson 2026-07-09).

---

## Open Questions

Carried from §12.14. "Owner" is the epic whose builder must resolve or record the item; closed items are listed so nobody re-opens them.

| # | Item (§12.14) | Recommended resolution | Owner | State |
|---|---|---|---|---|
| 1 | Profitability cost leg still reads `Expense`; new matters show zero cost | Follow-up phase (financial-report behaviour change). 615E adds the one-line note on the profitability page only. | 615E (note) / follow-up | Deferred by design |
| 2 | `/api/calendar` retirement — retained one release | 613A adds `@Deprecated(since = "Phase 87", forRemoval = true)` + javadoc "delete in the first phase after 87" on `calendar/CalendarController`; package deletion is a follow-up. | 613A | Open (annotate only) |
| 3 | Phase 84 take-on settings placement | Recommend Matters → `take-on` tab (`settings/matters?tab=take-on`); route count stays 17. Decided at Phase 84 `/architecture`. | — (Phase 84) | Deferred |
| 4 | Stale "next free V2" notes in `TASKS.md:5`, `backend/db/BASELINE.md` §5, `requirements/claude-code-prompt-phase84.md:14` | Corrected to tenant V4 / global V2 in 607A's docs commit. | 607A | Open → closes in 607 |
| 5 | Portal `/acceptance` nav item points at a page that does not exist | Drop the item; Home's pending-acceptance rows link to `/accept/{token}`; heading "Engagement letters" (ADR-336 "mandate" wording). | 617A | Decided (build) |
| 6 | `legal-za.json` orphan `"deadlines"` module id | Leave as is (portal "Diary dates" depends on it); follow-up registers it or folds it into `court_calendar`. | — | Deferred |
| 7 | Inherited 606C DEFERRED items — **seven failing spec locations in five files**: `legal-lifecycle/day-00-firm-setup.spec.ts:326`, `day-30-billing.spec.ts:32` and `:159`, `day-45-recon-prescription.spec.ts:41`, `day-90-review.spec.ts:61` and `:177`; `legal-depth-ii/day-05-disbursements.spec.ts:51`; plus AI-core walk (needs a human-funded key), uncommitted screenshot baselines, `frontend/test-results/` tracked (9 files) | Carried, never fixed by spec edit (reproduce-before-fix, one fix per PR). Every IA PR names them in its body and shows by run log the failing set did not grow. `day-00:326` / `day-30:32` may resolve as side-effects of 616 / 612+615 — confirm by running. `frontend/test-results/` untracked in 610E. | every IA epic; 617C reports | Carried |
| 8 | Fees zone width | Six items; Profitability under Firm → Reports; nav test "≤ 6 per zone except Trust (7)". | 612A | **Closed** |
| 9 | AI reviews as fifth Matters item | Accepted (otherwise `/ai/reviews` is unreachable). | 612A | **Closed** |
| 10 | `settings/billing` → Firm › Subscription; `settings/compliance` → Firm › Compliance | Accepted; ADR-341 aligned. | 616A | **Closed** |
| 11 | Legacy `/legal/*` route prefix survives | Out of the two-noun rule; unchanged (breadcrumb `NON_ROUTABLE_PREFIXES` handles it since #1579). | — | Deferred |
| 12 | Per-matter trust balance on `MatterListRow` is client-level | 615A confirms whether `trust_transactions.project_id` exists and is populated; column header reads "Client trust" until a follow-up. Record the finding in the PR body. | 615A | Open (confirm at build) |
| 13 | Remaining Phase 86 deferrals (stale `V<n>` comments, dead-code list, `PROFILE_FILTER_OPTIONS` bug, …) | Unchanged; tracked in `tasks/phase86-subtraction.md` §7.8. | — | Deferred |
| 14 | `ProjectUpcomingDeadlinesService` keeps its own union beside `DiaryService` | Untouched here (607B renames DTO + path only); fold into `DiaryService` when `/api/calendar` is deleted. | — | Deferred |
| 15 | Unbilled work page period — can `GET /api/invoices/unbilled-summary` take an open-ended period? | 612B confirms at build; if not, keep the 24-month default and say so in the page helper text. | 612B | Open (confirm at build) |
| 16 | **(breakdown-found)** `frontend/lib/api/client.ts` already exists (the HTTP client). The new S4 module for the Clients context is `lib/api/clients.ts` (plural) — the two names are one letter apart. | Name the new module `clients.ts`; add a header comment to both; the orchestrator may instead rename the HTTP client to `http-client.ts` in 610A (one IDE rename, out of the two-noun rule — flag, don't decide). | 610A | Open (orchestrator) |
| 17 | **(breakdown-found)** Prescription trackers: `components/legal/prescription-tab.tsx` is deleted in S6b (§12.6.2) but the matter's Court dates tab gains prescription trackers only in S8 (§12.6.5). Between 613 and 615 prescriptions are reachable only via `/diary?types=PRESCRIPTION`. | Recommend mounting the prescription list in the matter's Court dates tab in **613D** (same PR as the page deletion) so no surface is lost; 615E then only relabels. | 613D | Open (orchestrator) |
| 18 | **(breakdown-found)** `frontend/e2e/tests/projects/project-expenses.spec.ts` exercises the `projects/[id]/expenses` page that 615E deletes (ADR-340). Retiring a spec is outside ADR-344's URL/label/nav scope. | Needs explicit authorisation in the 615 PR body citing ADR-340 §Decision; recommended: delete the spec with the page and add a redirect assertion (`/matters/:id/expenses` → `?tab=disbursements`) to `projects/project-crud.spec.ts`. | 615E | Open (orchestrator) |
| 19 | **(breakdown-found)** Architecture names "5" portal 90-day spec files; the tree has **4** (`portal/e2e/tests/portal-client-90day/day-{00-07,14-30,45-75,85-90}.spec.ts`) plus `responsive/portal-pages.spec.ts` and `frontend/e2e/tests/portal/portal-data-isolation.spec.ts`. | Amend what exists; report the actual count in the closure report. | 617B | Open (count only) |

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 607 | Wire Rename Batch 1 — Matters Core + Contract Tests (+ caller edits) | Backend + Frontend + External callers (split) | Phase 86 merged (done) | L | 607A, 607B, 607C, 607D, 607E, 607F | In progress — 607A **Done** (PR #1588), 607B **Done** (PR #1589), 607C **Done** (PR #1590), 607D **Done** (PR #1591) |
| 608 | Wire Rename Batch 2 — Clients Core (+ caller edits) | Backend + Frontend + External callers (split) | 607 | M | 608A, 608B, 608C, 608D | |
| 609 | Wire Rename Batches 3–5 — Finance, Legal Verticals, Portal API (+ caller edits) | Backend + Frontend + Portal + External callers (split; may ship as 3 PRs) | 608 | L | 609A, 609B, 609C, 609D, 609E, 609F, 609G | |
| 610 | Frontend + Portal TS Types, API Modules, Route Segments, Redirects | Frontend + Portal (split) | 609 | L | 610A, 610B, 610C, 610D, 610E, 610F, 610G | |
| 611 | Terminology Layer Removal + Single-Vocabulary Lint | Frontend + Portal + packages (split) | 610 | L | 611A, 611B, 611C, 611D, 611E, 611F | |
| 612 | Navigation IA (Six Zones) + Palette `requiredModule` + Unbilled Work Page | Frontend | 611 | M | 612A, 612B, 612C | |
| 613 | One Diary — `GET /api/diary` + `/diary` Page, Calendars Deleted | Backend + Frontend (split) | 612 | L | 613A, 613B, 613C, 613D | |
| 614 | Legal-Native Home — Three Dashboard Reads + Widget Matrix | Backend + Frontend (split) | 613 | M | 614A, 614B, 614C | |
| 615 | Dense Lists (`MatterListRow`/`ClientListRow`), System Views, Tab Groups, Disbursements | Backend + Frontend (split) | 614 | L | 615A, 615B, 615C, 615D, 615E | |
| 616 | Settings Consolidation — Five Families, 44 → 17 Routes, 43 Redirects | Frontend | 615 | L | 616A, 616B, 616C, 616D, 616E, 616F | |
| 617 | Portal Vocabulary + Nav, QA Capstone, Screenshots, Closure Report | Portal + QA/Process | 616 | M | 617A, 617B, 617C | |

**Slice count: 54** (eleven architecture slices expanded by layer and by the ≤ 8–12-files / ≤ ~800-lines rule; rename slices exceed the file cap only where every change is a mechanical IDE substitution and say so).

---

## Dependency Graph

```
FROZEN (never edited by any slice — the contract tests landed in 607A guard them):
  $B/mcp/** (tools, mcp/dto/*, resources, tool names, input schemas), audit entityType strings
  ("project"/"customer"), EntityTag/SavedView/field_definitions strings ("PROJECT"/"CUSTOMER"),
  every @Entity/@Repository/@Service name, every table/column, Flyway V1–V3 + global V1,
  AuditEventTypeRegistryTest hasSize(29), McpReadOnlyRegistryTest, LayerDependencyRulesTest,
  TenantScopeBindingTest, BaselineSchemaParityTest, packages/ui (@b2mash/ui), gateway routes.

  Stage 1 — wire rename (backend batches + the caller edits that keep main green)
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │ 607A contract tests ──► 607B project/member/projecttemplate/setupstatus     │
  │   ──► 607C dashboard/activity/comment/budget/task/document/mywork/calendar  │
  │   ──► 607D FE field+path edits (lib) ──► 607E FE components + e2e API code  │
  │   ──► 607F seed scripts + regression script  ═══ PR #1 (S1)                 │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │ 608A customer/ ──► 608B informationrequest/checklist/datarequest/corresp.   │
  │   ──► 608C FE field+path edits ──► 608D customers.sh + regression ═══ PR #2 │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │ 609A finance BE ──► 609B finance FE ──► 609C finance ext. callers ═ PR #3a  │
  │ 609D legal+integrations BE ──► 609E legal FE                     ═ PR #3b  │
  │ 609F portal API BE (+ contract test = "nothing outside mcp/")              │
  │   ──► 609G portal caller edits (api-client.ts, types.ts fields) ═ PR #3c  │
  │ (3a → 3b → 3c strictly serial even when split; one PR is also allowed)     │
  └──────────────────────────────────┬──────────────────────────────────────────┘
                                     ▼
  Stage 2 — TypeScript names, routes, terminology
  610A types+modules ──► 610B app/ literals ──► 610C components+lib literals
    ──► 610D route dirs + legacy-redirects.ts ──► 610E hrefs/breadcrumbs/recents/scan
    ──► 610F portal TS + format:check ──► 610G Playwright URL amendments  ═══ PR #4
                                     ▼
  611A call sites G1 ──► 611B G2 ──► 611C G3 + copy JSON + 17 plain assertions
    ──► 611D FE mechanism deletion + ESLint + scan ──► 611E portal mechanism + shared map
    ──► 611F Playwright label amendments                                  ═══ PR #5
                                     ▼
  Stage 3 — IA
  612A NAV_GROUPS ──► 612B palette + /invoices/unbilled ──► 612C specs+scenario ═ PR #6
                                     ▼
  613A diary/ BE ──► 613B Diary page core ──► 613C Day/Month/actions/a11y
    ──► 613D calendars deleted + agenda + redirects + specs                ═══ PR #7
                                     ▼
  614A dashboard reads BE ──► 614B widget matrix FE ──► 614C specs + gate  ═══ PR #8
                                     ▼
  Stage 4 — lists / tabs / disbursements
  615A MatterListRow BE ──► 615B ClientListRow BE ──► 615C Matters table
    ──► 615D Clients table ──► 615E tab groups + Disbursements + specs     ═══ PR #9
                                     ▼
  Stage 5 — settings
  616A settings-nav.ts + Firm ──► 616B Matters ──► 616C Documents + Fees & Trust
    ──► 616D Connections ──► 616E sidebar/palette/redirect table/tests
    ──► 616F Playwright settings + day-00 + scenario                      ═══ PR #10
                                     ▼
  Stage 6 — portal + capstone
  617A portal nav/routes ──► 617B portal specs + scenario docs ──► 617C capstone ═ PR #11
```

**Parallel tracks: none.** Every architecture slice depends on the previous one (S1→S2→…→S10) and every intra-epic slice depends on its predecessor (B edits what A renamed; frontend slices compile against the backend their epic renamed). The **only** genuinely independent work is 607A's docs-only V2→V4 correction (three files, no code), which could be cherry-picked into its own docs PR if 607 is delayed — it still requires a review pass. 609's three optional PRs are serial (finance → legal → portal), not parallel: `WireVocabularyContractTest`'s scope widens in 609F and the portal API reads finance DTOs.

---

## Implementation Order

### Stage 1 — Wire rename: 607 → 608 → 609

| Epic | PR | What must be green before merge | Notes |
|------|----|----------------------------------|-------|
| 607 | `phase87/01-rename-matters-core` | BE-VERIFY (incl. `WireVocabularyContractTest` Batch 1 scope, `EntityTypeLiteralStabilityTest`, `McpToolContractTest` with committed `tool-contract.json`); gateway `cd gateway && ./mvnw spotless:check && ./mvnw test` (no edit expected — proves it); FE-GATE; PORTAL-GATE (no portal edit expected — proves it); REGRESSION green on the e2e stack; `AuditEventTypeRegistryTest` 29 unchanged | 607A lands first so 607B/607C are checked against the contract tests; `WireVocabularyContractTest` is **expected red** after 607A and green after 607C — its offender list is 607B/607C's worklist. No TS type/module renames, no route changes, no nav/pages/settings edits. |
| 608 | `phase87/02-rename-clients-core` | BE-VERIFY (contract test widened to Batch 2); FE-GATE; PORTAL-GATE; REGRESSION | Same rules. `CustomerController.listCustomers` in-controller filtering is **not** moved here (615B). |
| 609 | `phase87/03a-rename-finance`, `03b-rename-legal`, `03c-rename-portal-api` (or one `phase87/03-rename-finance-legal-portal`) | BE-VERIFY per PR; FE-GATE; PORTAL-GATE (609G edits portal); REGRESSION; after 609F `WireVocabularyContractTest` has **no** scope list and `McpToolContractTest` snapshot is byte-identical | Three PRs are allowed, never two batches in one PR. `tool-contract.json` must not change in 609D (Batch 4 renames fields the MCP tools map *from* — the test proves the boundary held). |

### Stage 2 — TypeScript names, routes, terminology: 610 → 611

| Epic | PR | What must be green before merge | Notes |
|------|----|----------------------------------|-------|
| 610 | `phase87/04-ts-types-routes-redirects` | FE-GATE; PORTAL-GATE (now with `format:check`); `no-legacy-hrefs.test.ts`, `breadcrumbs-routes.test.ts` green; E2E-LEGAL + `projects/`, `customers/`, `navigation/` specs green except the seven carried locations (named in the PR body) | Frontend + portal only, no Java. Old URLs 308 to new via `lib/legacy-redirects.ts`. Orchestrator option: split into 4a (610A–610C, types + modules, no route change) and 4b (610D–610G, routes + hrefs) — both keep `main` green. |
| 611 | `phase87/05-terminology-removal` | FE-GATE with ESLint `no-restricted-imports` active; `no-terminology-remnants.test.ts` + portal twin green; PORTAL-GATE; both apps' vitest (packages/shared touched); E2E-LEGAL green except carried locations | Call sites are rewritten **before** the mechanism is deleted so every slice builds. Labels are Playwright locators → 611F amends them in the same PR. |

### Stage 3 — IA: 612 → 613 → 614

| Epic | PR | What must be green before merge | Notes |
|------|----|----------------------------------|-------|
| 612 | `phase87/06-navigation-ia` | FE-GATE; `nav-items.test.ts` zone rule (≤ 6, `trust` ≤ 7; 28 items / 6 zones); `command-palette-dialog.test.tsx` `requiredModule`; E2E-LEGAL + `navigation/*.spec.ts` | Firm → Audit trail/Settings point at **current** settings routes; Today zone carries interim Calendar + Court calendar items. Never links to a 616 route. |
| 613 | `phase87/07-one-diary` | BE-VERIFY (`DiaryServiceTest`, `DiaryControllerIntegrationTest`); FE-GATE; E2E-LEGAL with `/court-calendar` (3 literals) → `/diary` amendments | `/api/calendar` retained (annotated deprecated); court-date write endpoints unchanged. |
| 614 | `phase87/08-legal-home` | BE-VERIFY (`DashboardServiceUnbilledWipTest`, `FicaStatusProjectorTest`, `TrustPositionIntegrationTest`; `team-workload` 404); FE-GATE with widget tests per audience row; E2E-LEGAL (`day-90-review` if it asserts dashboard copy) | — |

### Stage 4 — Lists, tabs, disbursements: 615

| Epic | PR | What must be green before merge | Notes |
|------|----|----------------------------------|-------|
| 615 | `phase87/09-lists-tabs-disbursements` | BE-VERIFY (`MatterListRowIntegrationTest` query cap ≤ 7 for 50 matters, `ClientListRowIntegrationTest`); FE-GATE (`system-views.test.ts`, tab-group pins 7/21 and 6/14, table tests); E2E-LEGAL (`day-05-disbursements`, `day-30-billing` tab labels) | **Stop and re-spec** if either detail page needs more than relabelling + orphan removal. Open question #18 (expenses spec) decided in the PR body. |

### Stage 5 — Settings: 616

| Epic | PR | What must be green before merge | Notes |
|------|----|----------------------------------|-------|
| 616 | `phase87/10-settings-five-families` | FE-GATE (`settings-nav.test.ts`, `settings-sidebar.test.tsx`, `breadcrumbs-routes.test.ts`); `find $FA/settings -name page.tsx \| wc -l` = **17**; 43 settings rows in `legacy-redirects.ts`; E2E-LEGAL + `settings/*.spec.ts` (8) + `day-00-firm-setup` | Controls stay, pages go. Each family slice appends its own redirect rows so the old sidebar still resolves mid-branch. |

### Stage 6 — Portal + capstone: 617

| Epic | PR | What must be green before merge | Notes |
|------|----|----------------------------------|-------|
| 617 | `phase87/11-portal-capstone` | PORTAL-GATE; portal `test:e2e:portal-client-90day` + `responsive` on the e2e stack; `frontend/e2e/tests/portal/portal-data-isolation.spec.ts`; capstone **observed** on the Keycloak stack (`svc.sh start all`) via Playwright MCP; closure report with evidence paths; both legal Playwright configs re-run on :3001 | PASS only when run end-to-end (CLAUDE.md §3). Seven carried locations reported with file:line and run-log evidence. |

### Merge-conflict hotspots and PR rules

PR rules restated from §12.12 (non-negotiable):
- One PR per slice-epic; 607–609 may split by batch but **never merge two batches in one PR**; each batch PR carries, for its bounded context, the frontend/portal path-literal + JSON-field edits and the external-caller edits (seed scripts, regression script, API-calling Playwright code) so `main` is green at every merge.
- **Never mix a rename with an IA change in one PR.** 607–611 touch no `nav-items.ts` content, no pages' structure, no settings; 612–617 rename nothing on the wire.
- Any Playwright spec a slice breaks is amended **in the same PR** under ADR-344 — URL, label and navigation edits only; behaviour steps, amounts and assertions untouched; each amendment annotated with the ADR it cites.
- The seven inherited failing spec locations (Open Question #7) are named in every IA PR body as carried, shown by run log not to have grown, and never fixed by spec edit.
- Full BE-VERIFY for any slice touching Java; FE-GATE for any slice touching `frontend/`; PORTAL-GATE for `portal/`; targeted `-Dtest=` runs are inner-loop only.
- IDE rename only for Java symbols (§12.10.8): `rename_refactoring` one symbol per call; path literals, REST-Docs snippet ids and JSON fixtures by hand; `git diff --stat` must stay inside the batch's packages + their callers.

| Hotspot | Slices that edit it | Rule |
|---------|---------------------|------|
| `$F/lib/nav-items.ts` + `lib/__tests__/nav-items.test.ts` | 610E (hrefs), 611B (labels), 612A (re-author), 613D (Diary item), 616E (Firm hrefs, `SETTINGS_ITEMS` deleted) | serial; rebase before each PR; content re-authored once (612A) — earlier slices change hrefs/labels only |
| `$F/lib/legacy-redirects.ts` + `next.config.ts` `redirects()` | 610D (created; `/projects`, `/customers`), 612A (`/profitability`), 613D (`/calendar`, `/court-calendar` ×2), 615E (`/matters/:id/expenses`), 616A–616D (43 settings rows) | append-only; `has` rule precedes the plain `/court-calendar` rule; `settings-nav.test.ts` reads settings rows only |
| `$F/lib/types/*.ts` + `lib/types/index.ts` | 607D, 608C, 609B, 609E (field names), 610A (file + interface renames), 615C/615D (`MatterListRow`/`ClientListRow`) | field renames before type renames; barrel edited last in each slice |
| `$FA/dashboard/page.tsx`, `components/dashboard/**` | 607D/607E (field names), 611A (copy), 613D (`todays-agenda`), 614B (matrix) | serial |
| `$FA/projects/[id]/page.tsx` → `matters/[id]/page.tsx` | 607D, 608C, 609B, 609E, 610D (move), 611A, 615E | the single most-edited file; rebase every slice |
| `$F/components/breadcrumbs.tsx` + `breadcrumbs-routes.test.ts` | 610E, 612A (`unbilled`), 613D (`diary`), 616E (families) | test fails on any pageless prefix left unlisted (#1579) |
| `$F/components/command-palette-dialog.tsx` | 610E (hrefs), 612B (`requiredModule`), 616E (`SETTINGS_FAMILIES`) | serial |
| `frontend/e2e/tests/{legal-lifecycle,legal-depth-ii,navigation,projects,customers,settings}/**`, `playwright.legal-*.config.ts` | 607E/608C/609B (API code only), 610G (URLs), 611F (labels), 612C, 613D, 614C, 615E, 616F | same-PR amendments; never edit the seven carried locations' assertions |
| `qa/testplan/demos/{legal-za-90day-keycloak,legal-za-full-lifecycle-keycloak,portal-client-90day-keycloak}.md` | 612C (0.23/0.24/0.59/90.3), 613D, 615E, 616F, 617B (final sweep + 12 stale "Pipeline" refs) | URL/label/nav only; annotate each amendment "(Phase 87, ADR-344)" |
| `$T/project/WireVocabularyContractTest.java` scope constant | 607A (Batch 1 packages), 608B (+ Batch 2), 609F (scope removed) | the only three edits allowed |
| `scripts/regression-test.sh`, `compose/seed/lib/*.sh`, `compose/seed/seed.sh` | 607F, 608D, 609C | per bounded context; REGRESSION green on each batch PR |
| `$P/lib/nav-items.ts`, `portal-sidebar.tsx` | 611E (`t()` → direct lookup), 617A (labels, `acceptance` dropped, `/matters`) | labels and routes are **617**, not 611 |

---

## Epic 607: Wire Rename Batch 1 — Matters Core + Contract Tests (+ caller edits)

**Goal**: Land the three contract tests first (`WireVocabularyContractTest`, `EntityTypeLiteralStabilityTest`, `McpToolContractTest` + `tool-contract.json`) so every later slice is checked against them, then rename Batch 1 — `project/`, `member/`, `projecttemplate/`, `setupstatus/`, `dashboard/dto`, `activity/`, `comment/`, `budget/`, `task/`, `document/`, `mywork/`, `calendar/` — to `/api/matters/**`, `/api/matter-templates/**`, `/api/dashboard/matter-health` and `Matter*` records with `matterId`/`matterName`/`matterRole`/`clientId` components, and carry the caller edits that keep `main` green: frontend `lib/api`/`lib/types`/`actions.ts` path literals and JSON-field accesses for this context only, API-calling Playwright fixtures, `compose/seed/lib/*.sh` and `scripts/regression-test.sh`. No TypeScript type or module names, no route segments, no nav/pages/settings change.

**References**: Architecture §12.2.1 (seam), §12.2.2 Batch 1 table, §12.2.3 (path table rows for `ProjectController` … `CalendarController`), §12.2.4, §12.2.5 (exclusions), §12.3.1 (rename mechanics, three contract tests), §12.9 (V2→V4 doc fix), §12.10.1 Batch 1 + Stability pins + Verify-no-literal checklist + Docs rows, §12.10.2 rows 1–2, §12.10.5 (`MatterResponse`, `WireVocabularyContractTest` skeleton, MCP snapshot approach), §12.10.6–12.10.8; [ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md), [ADR-342](../adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md), [ADR-343](../adr/ADR-343-mcp-contract-pinning-test.md).

**Dependencies**: Phase 86 merged (done, HEAD `41cb211fb`).

**Scope**: Backend (607A, 607B, 607C) + Frontend (607D, 607E) + External callers (607F).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **607A** | 607A.1–607A.5 | 3 new tests + 1 JSON snapshot + ~42 one-line comments + 3 docs | Contract tests + MCP snapshot; ADR-342 comment convention; gateway/security no-literal proof; V2→V4 docs. `WireVocabularyContractTest` expected red until 607C. **Done** (PR #1588) — `WireVocabularyContractTest` shipped as a **ratchet** (baseline `backend/src/test/resources/wire/batch1-known-offenders.txt`, 109 lines; fails on new offenders and stale lines) instead of expected-red, so `main` verifies clean: 607B/607C delete baseline lines as they rename, never edit the test file. `tool-contract.json` also pins `mcp.dto` output record shapes. |
| **607B** | 607B.1–607B.4 | ~12 main + ~26 test files (mechanical IDE rename; capped by package) | `project/`, `member/`, `projecttemplate/`, `setupstatus/` paths + records; `MatterResponse` extracted to `project/dto`. **Done** (PR #1589) — actual blast radius 21 main + 86 test files (58 cross-package tests used `/api/projects` for fixture setup, incl. `testutil/TestEntityHelper`); ratchet baseline 109 → 55. Ratchet-forced extra renames: `MatterWithRole.project→matter`, `MatterTemplateResponse.sourceProjectId→sourceMatterId`, `MatterSetupStatus.customerAssigned→clientAssigned`; request records renamed too (`Create/Update/CompleteMatterRequest{clientId}`, `SaveFromMatterRequest`, `InstantiateTemplateRequest{clientId, matterLeadMemberId}`); `?customerId=` query params left; `MatterMemberResponse` has no `matterId` (never had `projectId`). **607D must follow** JSON: `clientId`, `matterRole`, `sourceMatterId`, `clientAssigned`, `byMatter` (also on `GET /api/customers/{id}/unbilled-summary`). 607B.4 no-op (no REST-Docs/JSON fixtures in scope). |
| **607C** | 607C.1–607C.4 | ~15 main + ~24 test files (mechanical; capped by package) | `dashboard/`, `activity/`, `comment/`, `budget/`, `task/`, `document/`, `mywork/`, `calendar/`; contract test green; BE-VERIFY. **Done** (PR #1590) — actual blast radius 26 main + 47 test files (25 cross-package incl. `testutil/TestEntityHelper.createTask`); ratchet baseline 55 → 4 (residue owned elsewhere: `TeamWorkloadEntry#projects` → 614A, `DocumentResponse#customerId` + `POST /api/customers/{customerId}/documents/upload-init` → 608B, `MissingFieldSummary#customerCount` → 608A). Ratchet-forced extras: `KpiValues.activeProjectCount→activeMatterCount`, `MyWorkTimeEntryItem{matterId, matterName}`, `MyWorkProjectSummary→MyWorkMatterSummary`, `MyWorkTimeSummaryResponse.byProject→byMatter`; `TeamWorkloadEntry.projects` kept but items are now `MatterHoursEntry{matterId, matterName, hours}`; `mcp/dto/McpDocumentDto.from()` accessor only (snapshot unchanged). Decisions: `?projectId=` query params on `/api/my-work/tasks` + `/api/calendar` **left** (as 607B D2); calendar `itemType` emits `"MATTER"` and `?type=` accepts `TASK|MATTER` (`type=PROJECT` now 400). BE-VERIFY observed: 5345 testcases / 0 F / 0 E / 26 skipped on PR head 4c9a02e94. **607D/607E must follow** the JSON/path list in the PR #1590 body — CodeRabbit's 8 findings on #1590 are exactly those frontend callers (activity, budget, comments, dashboard health/matter-health, team-workload `matterName`, documents + upload-init, tasks) |
| **607D** | 607D.1–607D.3 | ~10–14 lib files (+ `actions.ts` in `$FA/projects/**`, 18 files, mechanical) | Frontend `lib/api`/`lib/types`/`dashboard-types` field + path edits for Batch 1; `projects/**` server actions. **Done** (PR #1591) — actual blast radius 73 files (72 modified + 1 new). **Scope decision (orchestrator):** 607D absorbed 607E.1's tsc-forced consumers (23 components/pages) + the 13 runtime-failing vitest files (43 tests), because 607D.3's `lint && build` gate is impossible without them and CLAUDE.md §1 forbids merging with red tests; FE-GATE observed green (vitest 355 files / 2246 passed / 2 skipped). New `entityApiPrefix` helper in `lib/api/tags.ts` (`PROJECT`→`matters`) for dynamic tag/field-group/custom-field URLs; 608 adds `CUSTOMER`→`clients` (consider moving helper to `lib/api/entity-prefix.ts` then). **607E keeps:** ~40 test files whose fixtures still carry old field names at the type level only (vitest passes; total tsc errors 202 vs 166 on main), 607E.2 Playwright API code, 607E.3. Follow-up bug (not fixed, pre-existing, CodeRabbit on #1591): `legal/adverse-parties/actions.ts:136` `fetchProjects()` reads `.content` from flat `List<MatterResponse>` → `undefined` in `link-adverse-party-dialog.tsx`. Runtime: MERGED-AWAITING-VERIFY until 607F REGRESSION. |
| **607E** | 607E.1–607E.3 | components consuming renamed fields (grep-driven; mechanical property renames) + e2e API fixtures | Components + vitest fixtures + API-calling Playwright code; FE-GATE. |
| **607F** | 607F.1–607F.3 | 7 shell scripts | Seed scripts + regression script; REGRESSION observed green; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 607A.1 | `WireVocabularyContractTest` (Batch 1 scope) | NEW `$T/project/WireVocabularyContractTest.java` | itself (two `@Test`s: route enumeration, DTO reflection scan) | annotations copied verbatim from `$T/mcp/McpHandshakeIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(TestcontainersConfiguration.class)`, `@ActiveProfiles("test")`) so it **reuses that context-cache key**; skeleton §12.10.5 | `LEGACY_SEGMENT = (^\|/)(projects\|customers\|project-templates)(/\|$)`; also fail on `{projectId}`, `{customerId}`, `/api/(matters\|clients)/{id}`, `import-customers`; exclude handler beans whose package starts with `io.b2mash.b2b.b2bstrawman.mcp`. DTO scan: `ClassPathScanningCandidateComponentProvider(false)` + `AssignableTypeFilter(Record.class)`, exclude `.mcp.`, `.event.`, `.demo.`, `.seeder.`, names ending `Projection`/`Spec`; keep `.dto` packages or names ending `Controller$…`, `Request`, `Response`, `Dtos$…`, `Summary`, `Row`, `Item`, `Entry`; `FORBIDDEN_COMPONENTS` = `{projectId, projectIds, projectName, projectRole, customerId, customerIds, customerName, customerEmail, customerAddress, customerType, activeProjectCount, projectBreakdown, byProject}` as `static final Set<String>`. Add a `static final Set<String> SCOPED_PACKAGES` = Batch 1 packages (`project, member, projecttemplate, setupstatus, dashboard, activity, comment, budget, task, document, mywork, calendar`) applied to **both** assertions in 607; 608B adds Batch 2; 609F deletes the constant. Failure message prints offending handler method / `Class#component`. Not in `$T/architecture/` (ArchUnit-only per `backend/CLAUDE.md`). **Expected red after this slice** — its output is 607B/607C's worklist; record that in the commit message. |
| 607A.2 | `EntityTypeLiteralStabilityTest` + ADR-342 comment convention | NEW `$T/audit/EntityTypeLiteralStabilityTest.java`; comments `// ADR-342: storage key, do not rename` on each `"project"`/`"customer"` audit literal (×10 + ×10 in `$B/project/ProjectService`, `$B/customer/CustomerService` et al.) and each `"PROJECT"`/`"CUSTOMER"` literal (×10 + ×12 in `$B/tag/*`, `$B/view/ViewFilterHelper`, `$B/project/ProjectController`, `$B/customer/CustomerController`, `$B/task/TaskController`, `$B/fielddefinition/*`) | itself (plain JUnit, no Spring context) | sits beside `$T/audit/AuditEventTypeRegistryTest.java`; reads `src/main/java` as text | Asserts `ProjectController`, `CustomerController`, `TaskController`, `EntityTagService`, `ViewFilterHelper`, `fielddefinition/` still contain `"PROJECT"`/`"CUSTOMER"` and never `"MATTER"`/`"CLIENT"`; `ProjectService`/`CustomerService` still emit `"project"`/`"customer"`; message cites ADR-342. ~42 comment-only edits are mechanical and allowed above the file cap. `AuditEventTypeRegistryTest` `hasSize(29)` untouched. |
| 607A.3 | `McpToolContractTest` + snapshot | NEW `$T/mcp/McpToolContractTest.java`, NEW `backend/src/test/resources/mcp/tool-contract.json` | itself | `McpHandshakeIntegrationTest` client flow (`initialize` → `tools/list` → `resources/list`); same four annotations | Serialise `[{name, description, inputSchema}]` for the 21 tools (`attach_document, file_correspondence, get_audit_events, get_client, get_correspondence, get_document_url, get_invoice, get_matter, get_matter_activity, get_trust_balance, get_unbilled_time, kazi_ping, list_clients, list_compliance_gaps, list_correspondence, list_invoices, list_matters, list_trust_transactions, propose_task, resolve_matter_by_email, search_documents`) + 3 resources (`kazi://matter/{id}`, `kazi://client/{id}`, `kazi://firm-profile`) with `ORDER_MAP_ENTRIES_BY_KEYS` + pretty print; compare to the committed file; on mismatch write `target/mcp/tool-contract.actual.json` and fail with a diff-style message; regenerate only with `-Dmcp.contract.update=true` (the generating PR must cite an ADR). Assert 21 + 3 counts explicitly. Does **not** edit `McpReadOnlyRegistryTest`. |
| 607A.4 | Verify-no-literal checklist (no edit expected) + docs V2→V4 | READ `gateway/src/main/resources/application.yml`, `gateway/…/GatewaySecurityConfig.java`, `$B/security/SecurityConfig.java`, `$B/portal/CustomerAuthFilter.java`; EDIT `TASKS.md:5`, `backend/db/BASELINE.md` §5, `requirements/claude-code-prompt-phase84.md:14` | `cd gateway && ./mvnw spotless:check && ./mvnw test` | — | `grep -nE 'projects\|customers'` over the four files must return nothing — paste the empty result into the PR body. Docs: "next free V2" → "next free tenant **V4**, global **V2**" (Open Question #4). |
| 607A.5 | Slice gate | — | targeted: `-Dtest='McpToolContractTest,EntityTypeLiteralStabilityTest,McpHandshakeIntegrationTest,McpReadOnlyRegistryTest'` green; `WireVocabularyContractTest` compiles and prints its offender list | — | Commit message lists the offender count as the Batch 1 worklist. |
| 607B.1 | `project/` rename | `$B/project/ProjectController.java` (`@RequestMapping("/api/matters")`, `{id}` → `{matterId}` on all sub-paths incl. `/archive`, `/complete`, `/reopen`, `/field-groups`, `/tags`, `/setup-status`, `/unbilled-summary`, `/upcoming-deadlines`), NEW `$B/project/dto/MatterResponse.java` (nested `ProjectResponse` extracted; components `clientId` ← `customerId`, `matterRole` ← `projectRole`; four `from(Project …)` overloads delegate as today — parameter stays `Project project`), `$B/project/ProjectUpcomingDeadline` → `MatterUpcomingDeadline` (service **untouched**), `$B/project/ProjectWithRole` → `MatterWithRole{matterRole}` | `$T/project/**` (12 files reference `/api/projects`/`ProjectResponse`): paths, `jsonPath("$.clientId")`, class names where they carry the noun | `rename_refactoring` one symbol per call (§12.10.8): type → components → `@PathVariable` names → path literals by hand; `$B/customer/dto/CustomerDtos` nested-record shape for the extraction target | List method keeps `List<MatterResponse>` until 615A. `"PROJECT"` EntityTag literals in this controller **stay** (607A.2 comment marks them). `ProjectPriority` enum name unchanged. `spotless:apply` after the batch; `build_project` after each rename. |
| 607B.2 | `member/`, `projecttemplate/`, `setupstatus/` rename | `$B/member/ProjectMemberController.java` (`/api/matters/{matterId}/members[/{memberId}[/role]]`), `ProjectMemberResponse` → `MatterMemberResponse{matterId, matterRole}`; `$B/projecttemplate/ProjectTemplateController.java` (`/api/matter-templates`, `/from-matter/{matterId}`, `/{id}/required-client-fields`), `dto/ProjectTemplateResponse` → `MatterTemplateResponse` (`workType` stays); `$B/setupstatus/{ProjectSetupStatus→MatterSetupStatus{matterId}, ProjectUnbilledBreakdown→MatterUnbilledBreakdown{matterId, matterName}, UnbilledTimeSummary.byProject→byMatter}` | `$T/member/**` (5), `$T/projecttemplate/**` (5), `$T/setupstatus/**` (4) | same procedure | Internal `member/{ProjectMemberInfo, ProjectAccess}` are **not** wire records — leave (§12.2.5). `CustomerReadiness` in `setupstatus/` is **608A**. |
| 607B.3 | Spotless + build + targeted | — | `./mvnw spotless:apply`; `-Dtest='io.b2mash.b2b.b2bstrawman.project.**,…member.**,…projecttemplate.**,…setupstatus.**'` | — | `git diff --stat` must touch only these four packages + their tests. |
| 607B.4 | REST-Docs / fixture literals | any `src/test/resources/**` JSON fixtures or snippet ids under the four packages carrying `/api/projects` or `projectId` | covered by 607B.3 | hand edit (IDE cannot see string contents) | Only one test file in the tree uses REST-Docs `document(` — confirm whether it is in scope; otherwise this task is a no-op recorded as such. |
| 607C.1 | `dashboard/` rename | `$B/dashboard/DashboardController.java` (`/api/matters/{matterId}/{health,member-hours,task-summary}`; `/api/dashboard/matter-health`; `team-workload` **untouched** — deleted in 614A), `$B/dashboard/dto/{ProjectHealth→MatterHealth, ProjectHealthDetail→MatterHealthDetail, ProjectHealthMetrics→MatterHealthMetrics}{matterId, matterName, clientName}`, `{ProjectBreakdownEntry→MatterBreakdownEntry, ProjectHoursEntry→MatterHoursEntry}{matterId, matterName}`, `CrossProjectActivityItem→CrossMatterActivityItem`, `KpiResponse.activeProjectCount→activeMatterCount`, `PersonalDashboard.projectBreakdown→matterBreakdown` + nested `UpcomingDeadline.projectName→matterName` | `$T/dashboard/**` (3) | same procedure | `dashboard/{ProjectHealthInput, ProjectHealthResult}` are internal — leave. `KpiResponse.withFinancialsRedacted()` behaviour unchanged. |
| 607C.2 | `activity/`, `comment/`, `budget/`, `task/`, `document/` rename | `$B/activity/ActivityController` (`/api/matters/{matterId}/activity`), `$B/comment/CommentController` (`/api/matters/{matterId}/comments[/{commentId}]`), `$B/budget/ProjectBudgetController` (`/api/matters/{matterId}/budget[/status]`; class name may stay — not a wire record), `$B/task/TaskController` (`/api/matters/{matterId}/tasks`; `/api/tasks/**` unchanged), `$B/document/DocumentController` (`/api/matters/{matterId}/documents[/upload-init]`; the `/api/customers/{customerId}/documents/upload-init` mapping is **608B**); every response/request record in these packages carrying `projectId` → `matterId` | `$T/activity/**` (2), `$T/comment/**` (2), `$T/budget/**` (2), `$T/task/**` (9), `$T/document/**` (3) | same procedure | `"TASK"` EntityTag literal in `TaskController` stays (607A.2). Audit `entityType "task"`/`"document"` untouched. |
| 607C.3 | `mywork/`, `calendar/` rename | `$B/mywork/MyWorkTaskItem{matterId, matterName}`, `$B/timeentry/MyWorkProjectTimeSummaryProjection` (naming only if it is a wire record — else leave), `$B/calendar/CalendarService.CalendarItemDto{matterId, matterName}` + `itemType "PROJECT" → "MATTER"` (value, `CalendarItemDto` only — never `SavedView`/`EntityTag`) | `$T/mywork/**` (2), `$T/calendar/**` (1) | same procedure | Endpoint `/api/calendar` retained (613A annotates it deprecated). |
| 607C.4 | Contract test green + BE-VERIFY | — | `WireVocabularyContractTest` (Batch 1 scope) **green**; `EntityTypeLiteralStabilityTest`, `McpToolContractTest` unchanged snapshot; then **BE-VERIFY** full | — | **Gate**: full verify clean (observed), marker written. `AuditEventTypeRegistryTest` 29, `McpReadOnlyRegistryTest`, `LayerDependencyRulesTest`, `TenantScopeBindingTest`, `BaselineSchemaParityTest` must pass **unedited**. |
| 607D.1 | Frontend `lib/types` field renames for Batch 1 (no interface/file renames) | `$F/lib/types/project.ts` (`customerId→clientId`, `projectRole→matterRole` on the response shape **only**; interface names unchanged), `task.ts` (`projectId→matterId`), `document.ts`, `matter-sidebar.ts`, `common.ts` if it carries `projectId`, `$F/lib/dashboard-types.ts` (`projectId/projectName → matterId/matterName`, `activeProjectCount→activeMatterCount`, `projectBreakdown→matterBreakdown`, `ProjectHealth` **type name stays** until 610A) | `pnpm tsc --noEmit` (via `pnpm build`) as the oracle — every forced property access surfaces as a type error | — | Only fields whose JSON changed in 607B/607C. `EntityType = "PROJECT" \| …` in `lib/types/field.ts` / `lib/constants/entity-types.ts` **unchanged** (ADR-342). |
| 607D.2 | Frontend `lib/api` + server-action path literals for Batch 1 | `$F/lib/api/{setup-status,templates,tags,fields,ai}.ts` and any `lib/api/*.ts` with `/api/projects` or `/api/project-templates` literals (grep: 30 files contain `/api/projects` app-wide — take only the Batch 1 contexts: matters core, tasks, documents, comments, members, budget, activity, dashboard, my-work, calendar, templates); `$FA/projects/**/*actions.ts` (18 files), `$FA/dashboard/**` actions, `$FA/my-work/**` actions, `lib/actions/dashboard.ts` | `pnpm test` fixtures that mock `/api/projects…` URLs updated in the same slice | inline `api.get("/api/projects…")` → `api.get("/api/matters…")` (string edit; module routing is **610**) | Keep `revalidatePath("/org/…/projects…")` strings as they are (route segments move in 610D). Request bodies that send `customerId` to `POST /api/matters` → `clientId`. |
| 607D.3 | Slice gate | — | `pnpm lint && pnpm build` (type-check) — `pnpm test` may still be red on component tests until 607E | — | — |
| 607E.1 | Components consuming renamed fields | grep-driven: `$F/components/{tasks,documents,comments,projects,dashboard,my-work,templates}/**` property accesses `x.projectId → x.matterId`, `project.customerId → project.clientId`, `member.projectRole → member.matterRole`, `kpis.activeProjectCount → activeMatterCount`; `components/projects/project-members-panel.tsx`, `upcoming-deadlines-tile.tsx`, `kpi-dashboard.tsx`, `todays-agenda.tsx`, `my-week-column.tsx`, `project-health-widget.tsx` | the colocated `*.test.tsx` and `__tests__/dashboard/*`, `__tests__/projects/*`, `__tests__/tasks/*` fixtures | mechanical property rename; may exceed 12 files — allowed because each edit is an identifier substitution forced by 607D's types; cap: if > 40 files, split a 607E′ and report | Vitest fixture JSON objects (`{ projectId: … }`) renamed too. **No** label, href or UI change. |
| 607E.2 | API-calling Playwright code for Batch 1 | `frontend/e2e/tests/admin-audit-30day/fixtures.ts`, `projects/project-crud.spec.ts`, `project-tasks.spec.ts`, `project-budget.spec.ts` (API calls only), `legal-lifecycle/*.spec.ts` + `legal-depth-ii/*.spec.ts` where `request.post("/api/projects…")` or JSON `projectId` appears (15 e2e files contain `/api/projects` or `/api/customers` — take Batch 1 hits only) | spec compile (`pnpm exec tsc -p e2e` if configured; else `pnpm test:e2e --list`) | — | **No UI locators, no `page.goto` URLs** (610G). Annotate each edit `// Phase 87 S1 (ADR-337): API path`. |
| 607E.3 | FE-GATE | — | **FE-GATE** all four; PORTAL-GATE (no portal edit expected — proves it) | — | — |
| 607F.1 | Seed scripts | `compose/seed/lib/{projects,tasks,documents,comments,rates-budgets,common}.sh`, `compose/seed/seed.sh` — `/api/projects…` → `/api/matters…`, `/api/project-templates` → `/api/matter-templates`, JSON `customerId`→`clientId` in matter create bodies, `projectId`→`matterId` in task/document/comment bodies and `jq` extractions | `bash compose/scripts/e2e-reseed.sh` completes without a 404 | `compose/seed/lib/customers.sh` is **608D**; `invoices.sh`/`time-entries.sh` are **609C** (budget paths in `rates-budgets.sh` are Batch 1; billing-rate paths in it are Batch 3 — edit only the budget lines here) | — |
| 607F.2 | Regression script | `scripts/regression-test.sh` (27 hits across all batches — Batch 1 lines only) | **REGRESSION** against the e2e stack: green | — | Record the pass/fail summary line in the PR body. |
| 607F.3 | Epic gate + PR | — | BE-VERIFY marker (607C.4) + FE-GATE marker (607E.3) + REGRESSION log; PR body: contract-test status, `tool-contract.json` committed, gateway grep empty, seven carried spec locations **not applicable** (no spec behaviour touched) | — | Open PR `phase87/01-rename-matters-core`; CodeRabbit + two reviewer passes + audit-trail block. |

### Key Files

**Create (backend):** `$T/project/WireVocabularyContractTest.java`, `$T/audit/EntityTypeLiteralStabilityTest.java`, `$T/mcp/McpToolContractTest.java`, `backend/src/test/resources/mcp/tool-contract.json`, `$B/project/dto/MatterResponse.java`.
**Modify (backend):** `$B/project/{ProjectController,ProjectUpcomingDeadline→MatterUpcomingDeadline,ProjectWithRole→MatterWithRole}`, `$B/member/{ProjectMemberController,ProjectMemberResponse→MatterMemberResponse}`, `$B/projecttemplate/{ProjectTemplateController,dto/ProjectTemplateResponse→MatterTemplateResponse}`, `$B/setupstatus/{ProjectSetupStatus→MatterSetupStatus,ProjectUnbilledBreakdown→MatterUnbilledBreakdown,UnbilledTimeSummary}`, `$B/dashboard/{DashboardController,dto/*}`, `$B/{activity,comment,budget,task,document}/*Controller` + DTOs, `$B/mywork/MyWorkTaskItem`, `$B/calendar/CalendarService`, ~42 ADR-342 comment sites, matching `$T/**`.
**Modify (frontend):** `lib/types/{project,task,document,matter-sidebar}.ts`, `lib/dashboard-types.ts`, `lib/api/*.ts` (Batch 1 literals), `$FA/projects/**/*actions.ts`, `$FA/{dashboard,my-work}/**` actions, consuming components + vitest fixtures, e2e API-calling code.
**Modify (external):** `compose/seed/lib/{projects,tasks,documents,comments,rates-budgets,common}.sh`, `compose/seed/seed.sh`, `scripts/regression-test.sh`.
**Docs:** `TASKS.md:5`, `backend/db/BASELINE.md` §5, `requirements/claude-code-prompt-phase84.md:14`.
**Read for context:** `$T/mcp/McpHandshakeIntegrationTest.java`, `$T/mcp/McpReadOnlyRegistryTest.java`, `$B/customer/dto/CustomerDtos.java` (nested-record shape), `backend/CLAUDE.md` test-coupling rules.

### Architecture Decisions
- **Entities stay; `from(Project)` factories are the seam** — rename only on the right-hand side ([ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md), §12.2.1).
- **No `@JsonProperty`** — renaming the record component *is* the wire rename (§12.3.1).
- **Contract tests land first and share `McpHandshakeIntegrationTest`'s context-cache key** (§12.3.1; [ADR-343](../adr/ADR-343-mcp-contract-pinning-test.md)).
- **Storage-key strings never move; the inverse test pins them** ([ADR-342](../adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md)).
- **Backend 404s old paths — no API redirect** (§12.4.7).

### Non-scope
- `customer/`, `setupstatus/CustomerReadiness`, `/api/customers/{customerId}/documents/upload-init` (608). Finance, legal, portal API (609). TS type/module names, routes, `app/api/customers/route.ts` rename (610). `team-workload` deletion (614A). `MatterListRow` (615A). Any nav, page or settings edit. Any migration.

---

## Epic 608: Wire Rename Batch 2 — Clients Core (+ caller edits)

**Goal**: Rename the clients bounded context — `customer/`, `setupstatus/CustomerReadiness`, `informationrequest/`, `checklist/`, `datarequest/`, `correspondence/` — to `/api/clients/**`, `/api/matters/{matterId}/clients[/{clientId}]`, `ClientDtos`, `ClientReadiness`, `clientId`/`clientType`/`clientIds`; widen `WireVocabularyContractTest` to Batch 2; carry the frontend path/field edits for this context, the `customers/*.spec.ts` API calls, `compose/seed/lib/customers.sh` and the regression script.

**References**: Architecture §12.2.2 Batch 2, §12.2.3 rows `CustomerController` … `DataExportController`, `CorrespondenceController`, `InformationRequestController`, `DocumentController` (client upload-init), §12.4.2, §12.10.1 Batch 2, §12.10.2 rows 1–2, §12.12 S2; [ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md).

**Dependencies**: 607.

**Scope**: Backend (608A, 608B) + Frontend (608C) + External callers (608D).

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **608A** | 608A.1–608A.3 | ~6 main + ~8 test (mechanical) | `customer/` + `ProjectCustomerController` + `CustomerReadiness`. |
| **608B** | 608B.1–608B.3 | ~8 main + ~18 test (mechanical) | `informationrequest/`, `checklist/`, `datarequest/`, `correspondence/`, client `upload-init`; contract test widened; BE-VERIFY. |
| **608C** | 608C.1–608C.3 | ~10–14 lib/action files + consuming components (mechanical) | Frontend field/path edits; BFF proxy target; e2e API code; FE-GATE. |
| **608D** | 608D.1–608D.2 | 3 scripts | `customers.sh` + `common.sh`/`seed.sh` + regression; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 608A.1 | `customer/` rename | `$B/customer/CustomerController.java` (`/api/clients`; `{id}`→`{clientId}`; `/{clientId}/matters[/{matterId}]`; `/{clientId}/unbilled-summary`; `/completeness-summary?clientIds=`; keep `/lifecycle-summary`, `/dormancy-check`, `/{clientId}/{collections-exemption,fica-status,field-groups,lifecycle,portal-contacts,readiness,tags,transition,unarchive}`), `$B/customer/dto/CustomerDtos.java` → `ClientDtos.java` (`CustomerResponse→ClientResponse{clientType}`, `CustomerProjectResponse→ClientMatterResponse{clientId, matterId}`, `LinkedProjectResponse→LinkedMatterResponse`, `Create/UpdateCustomerRequest→Create/UpdateClientRequest{clientType}`), `$B/customer/ProjectCustomerController.java` (`/api/matters/{matterId}/clients[/{clientId}]`), `$B/setupstatus/CustomerReadiness` → `ClientReadiness{clientId}` | `$T/customer/**` (6), `$T/setupstatus/**` readiness tests | §12.10.8 procedure | `List<Customer> filtered` at `CustomerController.java:102` maps to `ClientResponse` before returning — **in-controller filtering stays here** (moves to the service in 615B, not now). `"CUSTOMER"` EntityTag literal stays (607A.2 comment). Audit `"customer"` stays. |
| 608A.2 | Completeness map + `customerIds` param | `$B/customer/**/AggregatedCompletenessResponse` (name stays), query param `customerIds → clientIds` | `$T/customer/**` completeness tests | — | — |
| 608A.3 | Spotless + targeted | — | `-Dtest='…customer.**,…setupstatus.**'` | — | — |
| 608B.1 | `informationrequest/`, `checklist/`, `datarequest/`, `correspondence/`, client `upload-init` | `$B/informationrequest/InformationRequestController` (`/api/matters/{matterId}/information-requests`, `/api/clients/{clientId}/information-requests`), `dto/InformationRequestDtos.FicaStatusResponse{clientId}`; `$B/checklist/ChecklistInstanceController` (`/api/clients/{clientId}/checklists`); `$B/datarequest/{AnonymizationController, DataExportController}` (`/api/clients/{clientId}/anonymize[/preview]`, `/data-export`); `$B/correspondence/CorrespondenceController` (`/api/matters/{matterId}/correspondence`, `/api/clients/{clientId}/correspondence`); `$B/document/DocumentController` `/api/clients/{clientId}/documents/upload-init`; all DTOs in these packages carrying `customerId`/`projectId` | `$T/informationrequest/**` (5), `$T/checklist/**` (2), `$T/datarequest/**` (7), `$T/correspondence/**` (4), document upload-init tests | same procedure | `FICA_ONBOARDING_PACK_ID` logic untouched (614A extracts the projector). Correspondence MCP read-back tools (`list/get_correspondence`) read `mcp/dto/*` — unaffected; `McpToolContractTest` proves it. |
| 608B.2 | Widen contract test | `$T/project/WireVocabularyContractTest.java` `SCOPED_PACKAGES` += `customer, informationrequest, checklist, datarequest, correspondence` | itself green | — | Second of the three permitted edits to this constant. |
| 608B.3 | BE-VERIFY | — | **BE-VERIFY** full; `McpToolContractTest` snapshot unchanged | — | **Gate**: observed clean, marker written. |
| 608C.1 | Frontend `lib/types` + `lib/api` + actions for Batch 2 | `$F/lib/types/customer.ts` (`customerType→clientType`; `CustomerProject.projectId→matterId`, `customerId→clientId` on response shapes; **interface names stay** until 610A), `lib/types/{fica,correspondence,data-protection}.ts` (`customerId→clientId`), `lib/api/{information-requests,correspondence,compliance-audit,setup-status}.ts` path literals (`/api/customers…` → `/api/clients…`; 32 files contain `/api/customers` app-wide — Batch 2 contexts only), `$FA/customers/**/*actions.ts` (12 files), `$FA/compliance/**` actions, `$F/app/api/customers/route.ts` (**proxied target** `/api/customers` → `/api/clients`; the file/route rename is 610D) | `pnpm build` type oracle; vitest fixtures | string edit; mechanical | `?customerIds=` → `?clientIds=` on completeness calls. `getFieldDefinitions("CUSTOMER")` **unchanged** (ADR-342). |
| 608C.2 | Consuming components + e2e API code | `$F/components/{customers,compliance,correspondence,information-requests,data-protection}/**` property accesses (`customer.customerType→clientType`, `x.customerId→x.clientId`); `frontend/e2e/tests/customers/{customer-crud,customer-lifecycle,customer-data-protection}.spec.ts`, `compliance/**`, `information-requests/**`, `admin-audit-30day/fixtures.ts` **API calls only** | colocated tests + `__tests__/customers/*` fixtures | mechanical | No locators, no `page.goto` URLs (610G). |
| 608C.3 | FE-GATE | — | **FE-GATE**; PORTAL-GATE (no edit expected) | — | — |
| 608D.1 | Seed + regression | `compose/seed/lib/customers.sh` (`/api/customers` → `/api/clients`, `customerType→clientType` bodies, `jq` keys), `common.sh`/`seed.sh` customer hits, `scripts/regression-test.sh` Batch 2 lines | `e2e-reseed.sh` clean; **REGRESSION** green | — | — |
| 608D.2 | Epic gate + PR | — | BE-VERIFY + FE-GATE markers + REGRESSION log | — | `phase87/02-rename-clients-core`; reviews + audit-trail block. |

### Key Files

**Modify (backend):** `$B/customer/{CustomerController,ProjectCustomerController,dto/CustomerDtos→ClientDtos}`, `$B/setupstatus/CustomerReadiness→ClientReadiness`, `$B/informationrequest/{InformationRequestController,dto/InformationRequestDtos}`, `$B/checklist/ChecklistInstanceController`, `$B/datarequest/{AnonymizationController,DataExportController}`, `$B/correspondence/CorrespondenceController`, `$B/document/DocumentController` (client upload-init), `$T/project/WireVocabularyContractTest` (scope), matching `$T/**`.
**Modify (frontend):** `lib/types/{customer,fica,correspondence,data-protection}.ts`, `lib/api/{information-requests,correspondence,compliance-audit,setup-status}.ts`, `$FA/customers/**/*actions.ts`, `$FA/compliance/**` actions, `app/api/customers/route.ts` (target only), consuming components, e2e API code.
**Modify (external):** `compose/seed/lib/customers.sh`, `common.sh`, `seed.sh`, `scripts/regression-test.sh`.

### Architecture Decisions
- **Inward-out batch order confines compile errors to one context** (§12.3.1).
- **`GET /api/clients` stays ungated exactly like `/api/customers`; the Clients page keeps `CUSTOMER_MANAGEMENT`** (§12.4.2, §12.11.3).
- **In-controller filtering moves to the service only with the list projection (615B)** — a behaviour move, not a rename.

### Non-scope
- Finance/legal/portal DTOs carrying `customerId` (609). `lib/types/customer.ts → client.ts`, `Customer→Client` interfaces, `/clients` route (610). `FicaStatusProjector` (614A). `ClientListRow` (615B).

---

## Epic 609: Wire Rename Batches 3–5 — Finance, Legal Verticals + Integrations, Portal API (+ caller edits)

**Goal**: Finish the wire rename. Batch 3 (finance: `timeentry/`, `invoice/`, `billingrun/`, `expense/`, `budget/` params, `billingrate/`, `report/`, `collections/`) renames path variables, query params and the `customerId/customerName/customerEmail/customerAddress/projectId` components inside invoice/debtor/unbilled DTOs while `/api/invoices`, `/api/billing-runs`, `/api/time-entries`, `/api/reports/*` stay. Batch 4 (`verticals/legal/**`, `integration/accounting/**`) renames `{projectId}` → `{matterId}` on the already-legal closure/statement paths, `/api/matters/{matterId}/adverse-parties`, `client-ledgers/{clientId}`, `/import-clients`, and DTO fields — with `McpToolContractTest` proving the `mcp/` boundary held. Batch 5 (`portal/`, `customerbackend/`) renames `/portal/projects/**` → `/portal/matters/**` and `PortalProject*` → `PortalMatter*`, and `WireVocabularyContractTest` becomes "nothing outside `mcp/`". Each batch carries its own frontend/portal field + path edits and external-caller edits. **May ship as three PRs** (finance → legal → portal), strictly serial.

**References**: Architecture §12.2.2 Batches 3–5, §12.2.3 rows `TimeEntryController` … `ClientLedgerController`, portal rows, "Already legal-native — variable rename only", §12.2.5, §12.4.3–12.4.5, §12.10.1 Batches 3–5, §12.10.2 rows 1–2, §12.10.3 row 3, §12.12 S3; [ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md), [ADR-343](../adr/ADR-343-mcp-contract-pinning-test.md).

**Dependencies**: 608.

**Scope**: Backend (609A, 609D, 609F) + Frontend (609B, 609E) + External callers (609C) + Portal (609G).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **609A** | 609A.1–609A.3 | ~12 main + ~53 test (mechanical, capped by the 7 packages) | Batch 3 finance backend; BE-VERIFY. **PR 3a backend half.** |
| **609B** | 609B.1–609B.2 | ~12 lib/action files + consuming components (mechanical) | Finance frontend field/path edits + e2e API code; FE-GATE. |
| **609C** | 609C.1–609C.2 | 3 scripts | `invoices.sh`, `time-entries.sh`, `rates-budgets.sh` billing-rate lines, regression; **PR 3a gate.** |
| **609D** | 609D.1–609D.3 | ~10 main + ~52 test (mechanical; `verticals/legal/**` is wide but every edit is a field/variable substitution) | Batch 4 legal + integrations backend; MCP snapshot byte-identical; BE-VERIFY. **PR 3b backend half.** |
| **609E** | 609E.1–609E.2 | ~8 lib/action files + consuming components | Legal frontend field/param edits (`?matterId=`, `?clientId=`), `[customerId]` ledger reads; e2e API code; FE-GATE; **PR 3b gate.** |
| **609F** | 609F.1–609F.3 | ~6 main + ~23 test | Batch 5 portal API backend; contract test scope removed; BE-VERIFY. **PR 3c backend half.** |
| **609G** | 609G.1–609G.3 | ~6 portal files + portal e2e API code | Portal `api-client.ts` paths + `types.ts` **fields** (type names stay until 610F); PORTAL-GATE; **PR 3c gate.** |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 609A.1 | Finance paths + params | `$B/timeentry/TimeEntryController` (`/api/matters/{matterId}/time-entries/{id}/billable`), `timeentry/ProjectTimeSummaryController` (`/api/matters/{matterId}/time-summary[/by-member\|/by-task]`; `ProjectTimeSummaryResponse→MatterTimeSummaryResponse{matterId}`), `$B/expense/ExpenseController` (`/api/matters/{matterId}/expenses[/{id}[/write-off\|/restore]]`; `/api/expenses/mine` unchanged), `$B/budget/ProjectBudgetController` params, `$B/billingrate/BillingRateController` (`?matterId=&clientId=`; 7 + 5 params), `$B/invoice/InvoiceController` (`?clientId=&matterId=`), `$B/report/ReportController` (`/api/matters/{matterId}/profitability`, `/api/clients/{clientId}/profitability`; `/api/reports/*` unchanged), `$B/collections/CollectionsController` (`/debtors/{clientId}`) | `$T/timeentry/**` (8), `$T/invoice/**` (21), `$T/billingrun/**` (10), `$T/expense/**` (1), `$T/billingrate/**` (2), `$T/report/**` (5), `$T/collections/**` (6) | §12.10.8 procedure | `ProjectBudgetController`/`ProjectTimeSummaryController` **class** names are not wire records — rename optional; records are mandatory. `/api/expenses/**` retained without UI entry (ADR-340; 615E hides the UI). |
| 609A.2 | Finance DTO fields | `report/ReportController.{ProjectProfitabilityResponse→MatterProfitabilityResponse, ProjectProfitabilitySummary→MatterProfitabilitySummary}{matterId, matterName, clientName}`, `CustomerProfitabilityResponse→ClientProfitabilityResponse{clientId, clientName}`; `billingrun/dto/BillingRunDtos.CustomerUnbilledSummary→ClientUnbilledSummary{clientId, clientName, clientEmail}`; `collections/CollectionsReadService.{DebtorResponse, DebtorDetailResponse}{clientId, clientName}`; every `invoice/*` response record `customerId/customerName/customerEmail/customerAddress/projectId → clientId/clientName/clientEmail/clientAddress/matterId`; `expense/*` records `matterId`; `UtilizationResponse`/`MemberUtilizationRecord`/`MemberValueBreakdown` unchanged | same test packages | — | `BillingRunSelectionService.CustomerDiscoveryRow` is internal — leave. `InvoiceLineType.EXPENSE`/`DISBURSEMENT` untouched. Billing-run unbilled-expenses leg (`BillingRunWithoutRetainersIntegrationTest`) must still pass unedited in behaviour. |
| 609A.3 | Spotless + BE-VERIFY | — | **BE-VERIFY** full; `McpToolContractTest` unchanged | — | **Gate** (PR 3a backend). |
| 609B.1 | Frontend finance field/path edits | `$F/lib/types/{invoice,expense,billing}.ts` (field names only), `lib/api/{billing-runs,collections,reports}.ts`, `$FA/invoices/**/*actions.ts`, `$FA/profitability/**` + `$FA/reports/**` actions, `$FA/projects/[id]/expenses/actions.ts` (page still exists until 615E), time-entry/expense/budget components (`components/{invoices,billing-runs,collections,expenses,time,budget}/**`), `components/projects/time-summary-panel.tsx` | `pnpm build` oracle; `__tests__/{billing-runs,invoices,customer-financials,customer-profitability}*` fixtures | mechanical | `?customerId=` → `?clientId=` and `?projectId=` → `?matterId=` on `/api/invoices`, `/api/billing-rates` reads. |
| 609B.2 | e2e API code + FE-GATE | `frontend/e2e/tests/invoices/*.spec.ts`, `finance/**`, `projects/project-{time,expenses,budget}.spec.ts` API calls | **FE-GATE** | — | No locators/URLs. |
| 609C.1 | Seed + regression (finance) | `compose/seed/lib/{invoices,time-entries}.sh`, `rates-budgets.sh` billing-rate lines, `scripts/regression-test.sh` Batch 3 lines | `e2e-reseed.sh`; **REGRESSION** green | — | — |
| 609C.2 | PR 3a gate | — | BE-VERIFY (609A.3) + FE-GATE (609B.2) + REGRESSION | — | `phase87/03a-rename-finance` (or continue on the single 609 branch). |
| 609D.1 | Legal verticals paths + params | `$B/verticals/legal/closure/MatterClosureController` + `statement/StatementController` (`{projectId}` → `{matterId}` **variable only**), `conflictcheck/AdversePartyController` (`/api/matters/{matterId}/adverse-parties`; `/api/adverse-parties/**` unchanged), `trustaccounting/ledger/ClientLedgerController` (`client-ledgers[/{clientId}[/history\|/statement]]`), `courtcalendar/CourtCalendarController` + `PrescriptionTrackerController` + `disbursement/DisbursementController` (`?matterId=&clientId=`) | `$T/verticals/**` (part of the 52) | §12.10.8 | `/portal/trust/matters/{matterId}` already correct. Trust domain logic untouched. |
| 609D.2 | Legal + integration DTO fields | `courtcalendar/CourtCalendarService.{CourtDateResponse, UpcomingCourtDateItem, UpcomingPrescriptionWarningItem, PrescriptionTrackerResponse}{matterId, matterName, clientId, clientName}`, `disbursement/*` records `{matterId, clientId}`, `trustaccounting/ledger/ClientLedgerService.ClientLedgerCardResponse{clientId}`, `statement/dto/StatementResponse.MatterRef` + `closure/MatterClosureReport` `customerId→clientId`, `conflictcheck/*` adverse-party DTOs `matterId`; `$B/integration/accounting/CustomerSyncRequest→ClientSyncRequest{clientId}`, `xero/CustomerImportSummary→ClientImportSummary`, `xero/XeroIntegrationController` `/api/integrations/xero/import-clients` | `$T/verticals/**` + `$T/integration/**` (52 total) | — | `integration/ai/skill/*` (`MatterIntakeRequest`, `MatterIntakeOutput`) already legal — **no edit**. `mcp/dto/*` untouched — `McpToolContractTest` must be byte-identical; if it is not, the slice strayed. |
| 609D.3 | BE-VERIFY | — | **BE-VERIFY** full; `tool-contract.json` unchanged (`git diff --exit-code backend/src/test/resources/mcp/tool-contract.json`) | — | **Gate** (PR 3b backend). |
| 609E.1 | Frontend legal field/param edits | `$F/lib/types/{legal,trust}.ts` fields, `lib/api/{legal-disbursements,matter-closure,statement-of-account,integrations}.ts`, `$FA/legal/**` + `$FA/court-calendar/**` + `$FA/conflict-check/**` + `$FA/trust-accounting/**` actions (incl. `client-ledgers/[customerId]` reads → `/{clientId}` path; the **directory** rename is 610D), `components/legal/**` + `components/trust/**` property accesses (`courtDate.projectId→matterId`, `d.customerId→clientId`), `settings/integrations/xero` import action → `/import-clients` | `pnpm build` oracle; `components/legal/__tests__/*` fixtures | mechanical | — |
| 609E.2 | e2e API code + FE-GATE (PR 3b gate) | `legal-depth-ii/*.spec.ts`, `legal-lifecycle/*.spec.ts` API calls for court dates/disbursements/trust | **FE-GATE**; REGRESSION (legal lines of `regression-test.sh` if any) | — | `phase87/03b-rename-legal`. |
| 609F.1 | Portal API rename | `$B/portal/PortalProjectController` (`/portal/matters`, `/{matterId}`, `/{matterId}/tasks`), `portal/PortalDocumentController` (`/portal/matters/{matterId}/documents`), `$B/customerbackend/controller/{PortalCommentController, PortalSummaryController}` (`/portal/matters/{matterId}/{comments,summary}`); every record under `portal/` + `customerbackend/` whose simple name contains `Project` → `PortalMatter*` with `matterId`/`matterName` (exact names enumerated at build — grep `record .*Project` in the two packages and list them in the PR body) | `$T/portal/**` + `$T/customerbackend/**` (23) | §12.10.8 | `customerbackend/service/PortalResyncService.ProjectionData` internal — leave. `PortalDeadlineService.MODULE_ID = "deadlines"` and `/portal/deadlines/**` untouched. |
| 609F.2 | Contract test = everything outside `mcp/` | `$T/project/WireVocabularyContractTest.java` — delete `SCOPED_PACKAGES`; both assertions now cover every non-`mcp` handler and wire record | itself green with **no exemptions** | — | Third and last permitted edit to the scope. If a straggler surfaces outside Batches 1–5, rename it here by the same rule (§12.2.4 "the reflection scan is the arbiter"). |
| 609F.3 | BE-VERIFY | — | **BE-VERIFY** full | — | **Gate** (PR 3c backend). |
| 609G.1 | Portal caller edits | `$P/lib/api-client.ts` call sites (17 `/portal/projects` hits → `/portal/matters`), `$P/lib/types.ts` **field** renames only (`projectId→matterId`, `projectName→matterName` inside `PortalProject*` shapes — type names stay until 610F), `$P/app/(authenticated)/projects/**` pages' property accesses, `$P/components/{project-card,task-list,comment-section,document-list}.tsx` accesses | `pnpm build` oracle; `$P/**/__tests__` fixtures | mechanical | Portal **page routes stay `/projects`** until 617A. |
| 609G.2 | Portal e2e API code | `portal/e2e/tests/portal-client-90day/*.spec.ts` (4), `responsive/portal-pages.spec.ts`, `frontend/e2e/tests/portal/portal-data-isolation.spec.ts` — API-calling lines only | spec compile | — | No `page.goto` URL edits (617B). |
| 609G.3 | PR 3c gate | — | **PORTAL-GATE**; FE-GATE (unchanged — proves it); REGRESSION portal lines | — | `phase87/03c-rename-portal-api`. Closure of the wire rename: PR body states "no `/projects`/`/customers` outside `mcp/`" with the contract-test run log. |

### Key Files

**Modify (backend):** `$B/timeentry/{TimeEntryController,ProjectTimeSummaryController}`, `$B/invoice/InvoiceController` + DTOs, `$B/billingrun/dto/BillingRunDtos`, `$B/expense/ExpenseController`, `$B/budget/ProjectBudgetController`, `$B/billingrate/BillingRateController`, `$B/report/ReportController`, `$B/collections/{CollectionsController,CollectionsReadService}`, `$B/verticals/legal/{closure,statement,conflictcheck,courtcalendar,disbursement,trustaccounting/ledger}/**` DTOs + controllers, `$B/integration/accounting/{CustomerSyncRequest→ClientSyncRequest,xero/*}`, `$B/portal/{PortalProjectController,PortalDocumentController}`, `$B/customerbackend/controller/{PortalCommentController,PortalSummaryController}` + records, `$T/project/WireVocabularyContractTest` (scope removed), matching `$T/**`.
**Modify (frontend):** `lib/types/{invoice,expense,billing,legal,trust}.ts`, `lib/api/{billing-runs,collections,reports,legal-disbursements,matter-closure,statement-of-account,integrations}.ts`, finance/legal/trust actions + components, e2e API code.
**Modify (portal):** `lib/api-client.ts`, `lib/types.ts` (fields), `app/(authenticated)/projects/**`, `components/{project-card,task-list,comment-section,document-list}.tsx`, portal e2e API code.
**Modify (external):** `compose/seed/lib/{invoices,time-entries,rates-budgets}.sh`, `scripts/regression-test.sh`.

### Architecture Decisions
- **`invoice`, `expense`, `timeEntry`, `rateCard`, `budget` stay on the wire** — label-level only ([ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md)).
- **`mcp/` is the boundary in both directions**: the wire test excludes it, the contract test pins its inside ([ADR-343](../adr/ADR-343-mcp-contract-pinning-test.md)).
- **Generic `Expense` endpoints retained** without UI entry ([ADR-340](../adr/ADR-340-disbursement-is-the-face-of-matter-costs.md)).

### Non-scope
- Portal page routes `/projects` → `/matters`, `PortalProject*` type names, portal nav labels (610F, 617A). `trust-accounting/client-ledgers/[customerId]` directory rename (610D). Profitability cost-leg switch (follow-up #1). `/api/calendar` deletion (#2).

---

## Epic 610: Frontend + Portal TS Types, API Modules, Route Segments, Redirects

**Goal**: With every JSON field already renamed (607–609), rename the TypeScript **names**: `lib/types/{project,customer}.ts` → `{matter,client}.ts`, `Project→Matter`, `Customer→Client` and their derivatives, `lib/dashboard-types.ts`; introduce `lib/api/matters.ts` and `lib/api/clients.ts` and route every inline `api.get("/api/matters…")` / `/api/clients…` literal through them (74 + 57 pre-rename literals → ~0); move route segments `$FA/projects/** → matters/**`, `customers/** → clients/**`, `trust-accounting/client-ledgers/[customerId] → [clientId]`, `app/api/customers → app/api/clients`; create `lib/legacy-redirects.ts` consumed by `next.config.ts` `redirects()` (`/projects/:path*`, `/customers/:path*`, 308 permanent); rewrite the 463 hard-coded `/projects|/customers` hrefs in 163 files with a `no-legacy-hrefs.test.ts` scan; update breadcrumbs, recent items, setup status, palette hrefs; portal `PortalProject*→PortalMatter*`, `project-card.tsx→matter-card.tsx`, add portal `format:check`; untrack `frontend/test-results/`; amend Playwright **URLs**.

**References**: Architecture §12.3.1 (S4 shrinks to names + routes), §12.5.2, §12.6.5 (breadcrumbs, recents, setup status, BFF), §12.10.2 rows 3–7, 9, 18, §12.10.3 row 4, §12.10.5 (`legacy-redirects.ts` shape), §12.10.6–12.10.7, §12.12 S4, §12.14 #7 (untrack `test-results/`); [ADR-337](../adr/ADR-337-wire-rename-two-nouns-entities-stay.md), [ADR-344](../adr/ADR-344-qa-scenario-amendment-authorisation-phase87.md).

**Dependencies**: 609.

**Scope**: Frontend (610A–610E, 610G) + Portal (610F). No Java.

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **610A** | 610A.1–610A.3 | 2 file renames + barrel + `dashboard-types.ts` + 2 new modules; identifier rename across importers (mechanical IDE rename) | Types + modules created; no literal routing yet. |
| **610B** | 610B.1–610B.2 | `$FA/**` pages + `actions.ts` (grep-driven, ~60 files, mechanical) | Route `app/` API literals through the two modules. |
| **610C** | 610C.1–610C.2 | `components/**` + `lib/**` literals (~40 files, mechanical) | Remaining literals; scan = 0. |
| **610D** | 610D.1–610D.3 | 3 `git mv` trees + `legacy-redirects.ts` + `next.config.ts` + `revalidatePath`/`redirect()` strings | Route segments + redirects + BFF proxy. |
| **610E** | 610E.1–610E.4 | hrefs in 163 files (mechanical), `breadcrumbs.tsx`, `recent-items-provider.tsx`, `lib/api/setup-status.ts`, `command-palette-dialog.tsx`, new scan test, `.gitignore` | Hrefs + navigation plumbing + `no-legacy-hrefs.test.ts`; FE-GATE. |
| **610F** | 610F.1–610F.2 | ~6 portal files + `package.json` | Portal type names + `matter-card.tsx` + `format:check`. |
| **610G** | 610G.1–610G.2 | Playwright specs (URL literals: `/projects` 32, `/customers` 12) | URL amendments; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 610A.1 | Type file + interface renames | `git mv $F/lib/types/project.ts lib/types/matter.ts`, `customer.ts → client.ts`; interfaces `Project→Matter`, `ProjectStatus→MatterStatus`, `ProjectRole→MatterRole`, `ProjectSetupStatus→MatterSetupStatus`, `ProjectUnbilledBreakdown→MatterUnbilledBreakdown`, `MyWorkProjectSummary→MyWorkMatterSummary`, `Customer→Client`, `CustomerProject→ClientMatter`, `CustomerReadiness→ClientReadiness`, …; `lib/types/index.ts` barrel; `lib/dashboard-types.ts` (`ProjectHealth→MatterHealth` etc., 121 lines); `lib/constants/project-status.ts` → `matter-status.ts` only if its exports carry the noun | `pnpm build` | IDE rename (`rename_refactoring` via `idea` MCP works for TS too) one symbol per call; importers update automatically — that is why > 12 files is acceptable here | `EntityType = "PROJECT" \| "TASK" \| "CUSTOMER" \| "INVOICE"` **unchanged** (ADR-342). `lib/types/expense.ts`, `invoice.ts` keep their names. |
| 610A.2 | New API modules | NEW `$F/lib/api/matters.ts` (list/get/create/update/lifecycle/tags/field-groups/setup-status/unbilled-summary/upcoming-deadlines + nested helpers `matterTasks`, `matterDocuments`, …), NEW `lib/api/clients.ts` (list/get/create/update/lifecycle/transition/readiness/fica-status/portal-contacts/matters/completeness/…), `lib/api/index.ts` barrel | unit tests for URL building (`__tests__/lib/api/{matters,clients}.test.ts`) | `lib/api/legal-disbursements.ts` / `matter-closure.ts` as the module shape | **Open Question #16**: `lib/api/client.ts` (HTTP client) already exists — name the new module `clients.ts`, header-comment both; orchestrator may rename the HTTP client instead. |
| 610A.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | No literals routed yet — modules exist and compile. |
| 610B.1 | Route `app/` literals through modules | `$FA/matters/**` (still at `projects/**` on disk until 610D) pages + `*actions.ts` (18), `$FA/customers/**` (12), `$FA/{dashboard,my-work,compliance,invoices,legal,trust-accounting,reports}/**` actions with `/api/matters` or `/api/clients` literals | `pnpm build`; vitest mocks switch from URL strings to module mocks where tests exist | `vi.mock("@/lib/api/matters")` pattern already used for other modules in `__tests__` | Mechanical; grep `grep -rlE '"/api/(matters\|clients)' frontend/app` must trend to 0 by 610C. |
| 610B.2 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 610C.1 | Route `components/` + `lib/` literals | `$F/components/**`, `$F/lib/**` (non-`api`) remaining literals; BFF `app/api/customers/route.ts` proxies via module | NEW assertion in `__tests__/lib/api/no-inline-matter-client-literals.test.ts`: zero `"/api/matters` / `"/api/clients` outside `lib/api/{matters,clients}.ts` | — | — |
| 610C.2 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 610D.1 | Route segment moves | `git mv "$FA/projects" "$FA/matters"`, `customers → clients`, `trust-accounting/client-ledgers/[customerId] → [clientId]` (+ `params.customerId → params.clientId` reads), `app/api/customers/route.ts → app/api/clients/route.ts`; `revalidatePath("/org/[slug]/projects…")` strings in the moved `actions.ts`; `redirect()` in `matters/actions.ts` (ex `projects/actions.ts`) | `pnpm build` (route manifest) | — | Directory names only — page bodies untouched except the strings named. |
| 610D.2 | Redirect table | NEW `$F/lib/legacy-redirects.ts` exporting `LEGACY_REDIRECTS` (`as const`): existing `/org/:slug/settings/team → /org/:slug/team` row moved in; `/org/:slug/projects/:path* → /org/:slug/matters/:path*`, `/org/:slug/customers/:path* → /org/:slug/clients/:path*`, both `permanent: true`; `$F/next.config.ts` `async redirects() { return [...LEGACY_REDIRECTS]; }` | `__tests__/lib/legacy-redirects.test.ts` (unique sources; every destination starts with `/org/:slug/`) | §12.10.5 shape; existing `/settings/team` precedent in `next.config.ts` | Append-only table for 612A/613D/615E/616. Query strings survive on `:path*` (Next.js). |
| 610D.3 | Slice gate | — | `pnpm lint && pnpm build`; `curl -I http://localhost:3001/org/e2e-test-org/projects` → 308 on the e2e stack (observed, pasted into PR) | — | — |
| 610E.1 | Hard-coded hrefs | 463 `/org/…/(projects\|customers)` literals in 163 files (top: `matters/[id]/page.tsx` 27, `matters/actions.ts` 18, `clients/[id]/page.tsx` 16) → `/matters`, `/clients`; `lib/nav-items.ts` hrefs only (labels/zones untouched — 611/612) | NEW `$F/__tests__/no-legacy-hrefs.test.ts` — walks `app`, `components`, `lib` (excluding tests/e2e) and asserts zero `/org/…/(projects\|customers)` literals and zero `href="/projects` / `/customers` template fragments | mechanical href substitution; `no-terminology-remnants` scan shape (§12.10.5) | > 12 files allowed: pure string substitution; verify with the scan, not by reading. |
| 610E.2 | Breadcrumbs, recents, setup status, palette hrefs | `$F/components/breadcrumbs.tsx` (`SEGMENT_LABELS` += `matters`, `clients`; `PARENT_SEGMENT_FALLBACKS` `projects→"Project"` → `matters→"Matter"`, `customers→"Customer"` → `clients→"Client"`; `NON_ROUTABLE_PREFIXES` re-derived against the new tree), `components/__tests__/breadcrumbs-routes.test.ts` (#1579 — fails on any pageless prefix left unlisted), `components/recent-items-provider.tsx` (regex `(projects\|customers)` → `(matters\|clients)`; label = `document.title` segment instead of title-cased UUID), `lib/api/setup-status.ts` + `components/projects/*setup*` links (`/matters/{id}?tab=…`, `/clients/{id}`), `components/command-palette-dialog.tsx` hrefs | `breadcrumbs-routes.test.ts`, recent-items tests | — | `t()` prefix rewrite in breadcrumbs stays until 611 deletes it. |
| 610E.3 | Untrack `frontend/test-results/` | `.gitignore` += `frontend/test-results/`; `git rm -r --cached frontend/test-results` (9 files) | — | §12.14 #7 | Playwright legal configs rewrite this dir — this ends the dirty-tree problem (memory `env_playwright_legal_configs_dirty_tree`). |
| 610E.4 | FE-GATE | — | **FE-GATE**; `no-legacy-hrefs.test.ts`, `no-inline-matter-client-literals.test.ts`, `breadcrumbs-routes.test.ts` green | — | — |
| 610F.1 | Portal type + component names + `format:check` | `$P/lib/types.ts` (`PortalProject→PortalMatter`, `PortalProjectDetail→PortalMatterDetail`, `PortalProjectSummary→PortalMatterSummary`), `git mv $P/components/project-card.tsx matter-card.tsx` (+ importers), `$P/package.json` `"format:check": "prettier --check ."` (match `frontend/package.json`) | `$P/**/__tests__` | IDE rename | Portal **routes and nav labels unchanged** (617A). |
| 610F.2 | PORTAL-GATE | — | `cd portal && pnpm lint && pnpm format:check && pnpm test && pnpm build` (new script) | — | — |
| 610G.1 | Playwright URL amendments | `frontend/e2e/tests/projects/*.spec.ts` (5), `customers/*.spec.ts` (3), `legal-lifecycle/*.spec.ts` (8), `legal-depth-ii/*.spec.ts` (4), `navigation/*.spec.ts` (2), `admin-audit-30day/**`, `audit-log/**`, `documents/**`, `finance/**`, `compliance/**`, `information-requests/**` — `page.goto("/org/…/projects…")` → `/matters…`, `/customers…` → `/clients…`, `expect(page).toHaveURL(/\/projects\//)` → `/matters/`; `/projects` 32 + `/customers` 12 literals | spec compile | ADR-344 scope: **URLs only**; annotate `// Phase 87 S4 (ADR-344): route rename` | Labels (611F), nav (612C), `/court-calendar` (613D), settings (616F) are **not** touched here. The seven carried locations' assertions are not edited. |
| 610G.2 | E2E-LEGAL + epic gate | — | **E2E-LEGAL** + `pnpm test:e2e -- projects customers navigation`; failing set == the seven carried locations (named with file:line in the PR body, run log attached) | — | `phase87/04-ts-types-routes-redirects`. Orchestrator option: split 4a (610A–C) / 4b (610D–G). |

### Key Files

**Create (frontend):** `lib/api/matters.ts`, `lib/api/clients.ts`, `lib/legacy-redirects.ts`, `__tests__/no-legacy-hrefs.test.ts`, `__tests__/lib/api/no-inline-matter-client-literals.test.ts`, `__tests__/lib/legacy-redirects.test.ts`.
**Rename (frontend):** `lib/types/project.ts→matter.ts`, `customer.ts→client.ts`, `$FA/projects/**→matters/**`, `customers/**→clients/**`, `trust-accounting/client-ledgers/[customerId]→[clientId]`, `app/api/customers→app/api/clients`.
**Modify (frontend):** `lib/types/index.ts`, `lib/dashboard-types.ts`, `lib/api/index.ts`, `next.config.ts`, `components/{breadcrumbs,recent-items-provider,command-palette-dialog}.tsx`, `lib/api/setup-status.ts`, `lib/nav-items.ts` (hrefs), 163 href files, `.gitignore`, e2e specs (URLs).
**Modify (portal):** `lib/types.ts`, `components/project-card.tsx→matter-card.tsx`, `package.json`.

### Architecture Decisions
- **Identifier blast radius spread over S1–S4** — S4 is names and routes only (§12.3.1).
- **Two modules so a future path change is one file** (§12.10.2 row 4).
- **308 permanent redirects for one release, from one committed table** (§12.7.4 policy applied to routes; §12.10.5).
- **Storage-plane `EntityType` strings untouched** ([ADR-342](../adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md)).

### Non-scope
- Any label/copy change, `useTerminology` removal (611). `NAV_GROUPS` content (612). Portal routes/labels (617). `legacy-redirects.ts` rows for calendars/expenses/settings (613/615/616). `matter-tab-groups.ts` rename (615E).

---

## Epic 611: Terminology Layer Removal + Single-Vocabulary Lint

**Goal**: Delete the runtime terminology layer and write the legal word into the copy. Call sites are rewritten **first** (three groups, ~73 frontend files) so every slice builds, then the frontend mechanism goes (`lib/terminology.tsx`, `TerminologyHeading`, `TerminologyText`, `withIndefiniteArticle`, `auditTabLabel`, the `TerminologyProvider` mount, `vertical-profile-section` + its `settings/general` mount, four mechanism tests), then the portal mechanism and `packages/shared/src/terminology-map.ts`. Enforcement: ESLint `no-restricted-imports` in both apps, `no-terminology-remnants.test.ts` + portal twin, a `frontend/CLAUDE.md` rule. Copy pass on `help.json`, `empty-states.json`, `getting-started.json`; 17 word-assertion tests made plain; "Tasks" stay Tasks; "Mandate accepted" badge on the accepted engagement letter. Playwright label locators amended in the same PR.

**References**: Architecture §12.3.6 (deletion list, rewrite approach, enforcement, copy pass, portal mirror), §12.6.6 (sentence case), §12.10.2 row 10, §12.10.3 row 5, §12.10.4, §12.10.5 (remnant scan), §12.12 S5; [ADR-338](../adr/ADR-338-terminology-layer-removal-single-vocabulary.md), [ADR-336](../adr/ADR-336-engagement-letter-document-plus-acceptance.md) (mandate wording), [ADR-332](../adr/ADR-332-single-vertical-profile-mechanism-retained.md) (`OrgSettings.verticalProfile` + `isModuleEnabled` stay).

**Dependencies**: 610.

**Scope**: Frontend (611A–611D, 611F) + Portal + packages (611E).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **611A** | 611A.1–611A.2 | ~25 files: `$FA/{matters,clients,my-work,dashboard}/**` + `components/{projects,customers,tasks,documents,comments}/**` | Call-site group 1 (hand-rewritten copy); "Mandate accepted" badge. |
| **611B** | 611B.1–611B.2 | ~25 files: `$FA/{invoices,legal,trust-accounting,reports,profitability,compliance,settings}/**` + `components/{invoices,billing-runs,collections,legal,trust,settings,compliance}/**`, `lib/nav-items.ts` labels, `nav-zone.tsx`, `settings-sidebar.tsx` `translateNavLabel` | Call-site group 2. |
| **611C** | 611C.1–611C.3 | remaining ~23 files + 3 JSON + 17 tests | Group 3 (shared components: breadcrumbs, empty states, dialogs, help tips), copy JSON pass, word-assertion tests plain. |
| **611D** | 611D.1–611D.4 | 7 deletions + 2 mounts + `eslint.config.mjs` + 1 new test + `frontend/CLAUDE.md` | Frontend mechanism deletion, ESLint, remnant scan, rule. |
| **611E** | 611E.1–611E.3 | 10 portal call-site files + 3 deletions + `packages/shared` 2 files + eslint + twin test | Portal mechanism + shared map deletion. |
| **611F** | 611F.1–611F.2 | Playwright label locators (legal-lifecycle, legal-depth-ii, navigation, projects, customers, settings) | Label amendments; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 611A.1 | Rewrite call sites — matters/clients/tasks/dashboard | `grep -rlE 'useTerminology\|TerminologyText\|TerminologyHeading\|withIndefiniteArticle\|auditTabLabel' $FA/{matters,clients,my-work,dashboard} $F/components/{projects,customers,tasks,documents,comments}` — rewrite each `t("Project")`→"Matter", `t("Customer")`→"Client", `t("Invoice")`→"Fee note", `t("Expense")`→"Disbursement", `t("Budget")`→"Fee estimate", `t("Time Entry")`→"Time recording", `t("Rate Card")`→"Tariff schedule", `withIndefiniteArticle(t("project"))`→"a matter", `auditTabLabel()`→"Audit trail", `<TerminologyHeading>`/`<TerminologyText>`→ plain JSX; placeholders "e.g. Dlamini v Road Accident Fund" / "e.g. RAF-2026-001"; remove `verticalProfile` props that only fed terminology | colocated `*.test.tsx` updated to plain assertions | §12.3.6 dictionary; sentence case ("Fee notes", not "Fee Notes") | **Per file, by hand, not a codemod** — the right word depends on the sentence. `Task → Action Item` is **not** carried (Tasks are Tasks). The 53 `createMessages().t("ns.key")` i18n calls are a different `t` — untouched. Mechanism files still exist so the tree builds. |
| 611A.2 | "Mandate accepted" badge | `components/documents/**` accepted-engagement-letter badge text → "Mandate accepted" (the one place `Retainer → Mandate` lands) | its test | ADR-336 B1 | — |
| 611B.1 | Rewrite call sites — finance/trust/legal/settings/nav | `$FA/{invoices,legal,trust-accounting,reports,profitability,compliance,settings}/**`, `components/{invoices,billing-runs,collections,legal,trust,settings,compliance}/**`, `lib/nav-items.ts` (**labels only** — "Fee notes", "Matters", "Clients"…; zones/hrefs untouched until 612A), `components/nav-zone.tsx` (drop `t()`), `components/settings/settings-sidebar.tsx` (`translateNavLabel()` + `useTerminology` import removed; literal labels), `components/breadcrumbs.tsx` `t()` prefix rewrite removed | colocated tests; `lib/__tests__/nav-items.test.ts`, `components/__tests__/{nav-zone,settings-sidebar}.test.tsx` | same | — |
| 611B.2 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 611C.1 | Rewrite call sites — group 3 | remaining files from the grep (shared dialogs, `components/empty-state.tsx` consumers, `help-tip.tsx`, `getting-started-card.tsx`, onboarding, templates, automations, AI pages) | colocated tests | same | After this task `grep -rlE 'useTerminology\|TerminologyText\|TerminologyHeading\|withIndefiniteArticle\|auditTabLabel' frontend/{app,components,lib} \| grep -v terminology` must list only the mechanism files themselves. |
| 611C.2 | Copy JSON pass | `$F/lib/messages/en/help.json` (15 namespaces / 56 keys: tokens removed, legal sentences, "INV-001" → "FN-2026-001"), `empty-states.json` (`{projects}`/`{customers}` tokens removed; "No matters yet. Open your first matter to start recording time."), `getting-started.json` (steps re-ordered to the attorney's first week: open a matter, record time, issue a fee note, receipt into trust) | `__tests__/{empty-state-pages,empty-state,help-tip-integration,getting-started-card}.test.tsx` | — | "charge your clients" stays — already legal English. |
| 611C.3 | 17 word-assertion tests made plain | tests asserting `screen.getByText(t("Matter"))`-style or `renders Matter for legal-za` → plain `getByText("Matter")`; no profile parameterisation | `pnpm test` | §12.10.2 row 10 "17 word-assertion tests" | Count the exact files in the PR body (grep `legal-za` in `frontend/__tests__` for candidates; 52 test files mention Matter/Client — only those asserting *via* terminology count). |
| 611D.1 | Delete frontend mechanism | DELETE `$F/lib/terminology.tsx`, `components/terminology-heading.tsx`, `components/terminology-text.tsx`, `components/settings/vertical-profile-section.tsx`, `__tests__/{terminology,terminology-integration,lzkc-terminology-cluster,lzkc-031-terminology-residuals}.test.tsx`; EDIT `$FA/layout.tsx:119` (remove `TerminologyProvider` mount), `$FA/settings/general/page.tsx` (remove "Apply Profile" mount — closure F-B3) | build | — | `OrgSettings.verticalProfile`, `VerticalProfileController`, `useOrgProfile().isModuleEnabled` **stay** (ADR-332). `lib/hooks/useProfile.ts` kept — `isModuleEnabled` consumers remain. |
| 611D.2 | ESLint rule | `$F/eslint.config.mjs` `no-restricted-imports` paths `@/lib/terminology`, `@/components/terminology-heading`, `@/components/terminology-text`, `@b2mash/shared/terminology-map` with message "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)" | `__tests__/eslint-rules/*` (existing dir) gains a case | existing `eslint-rules` tests | — |
| 611D.3 | Remnant scan + CLAUDE.md rule | NEW `$F/__tests__/no-terminology-remnants.test.ts` (walks `app`, `components`, `lib`; excludes tests/e2e; `BANNED = /useTerminology\|TerminologyText\|TerminologyHeading\|terminology-map\|withIndefiniteArticle\|auditTabLabel\|PORTAL_TERMINOLOGY/`; second assertion: zero `/\b(Projects?\|Customers?)\b/` in JSX text nodes under `app/(app)/org/**` with an allow-list file `__tests__/no-terminology-remnants.allowlist.json` for legitimate uses, e.g. a Xero field name); `frontend/CLAUDE.md` paragraph *"Single vocabulary: write the legal word (matter, client, fee note, disbursement, tariff, time recording). There is no runtime substitution layer; do not add one."* and remove the line 173 "Terminology … come from `@b2mash/shared`" | itself | §12.10.5 vitest scan (Node 22 `globSync` or `fast-glob` — confirm at build) | — |
| 611D.4 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 611E.1 | Portal call sites + mechanism | 10 portal files (34 usages — `grep -rlE 'useTerminology\|PORTAL_TERMINOLOGY\|TerminologyText' portal/{app,components,lib}`) rewritten with **today's** labels as literals; `$P/components/portal-sidebar.tsx:70` `t(PORTAL_NAV_LABELS[labelKey])` → direct `PORTAL_NAV_LABELS[labelKey]`; DELETE `$P/lib/terminology.tsx`, `$P/lib/__tests__/terminology-provider.test.tsx`; EDIT `$P/app/(authenticated)/layout.tsx:38` (provider mount removed); `portal/hooks/use-profile` kept only if a non-terminology consumer remains (confirm; record) | `$P/components/__tests__/portal-sidebar.test.tsx`, `lib/__tests__/nav-items.test.ts` (assert today's labels — new labels are **617A**) | — | New nav labels and `/matters` routes are **617**, not here. |
| 611E.2 | Shared map deletion + portal lint/scan | DELETE `packages/shared/src/terminology-map.ts`; EDIT `packages/shared/src/index.ts` (drop export; `format.ts` stays); `$P/eslint.config.mjs` `no-restricted-imports` (same paths, portal aliases); NEW `$P/__tests__/no-terminology-remnants.test.ts` (twin, roots `portal/{app,components,lib}`) | both apps' vitest (shared package touched) | — | `packages/ui` **untouched**. |
| 611E.3 | PORTAL-GATE | — | `cd portal && pnpm lint && pnpm format:check && pnpm test && pnpm build`; **FE-GATE** re-run (shared package changed) | — | — |
| 611F.1 | Playwright label amendments | `frontend/e2e/tests/{legal-lifecycle,legal-depth-ii,navigation,projects,customers,settings,invoices}/**` — `getByRole("heading", { name: "Projects" })` → "Matters", `getByRole("link", { name: "Invoices" })` → "Fee notes", "Customers" → "Clients", "Action Item" → "Task", "Expenses" labels where the copy changed; sentence-case label fixes | spec compile | ADR-344: labels are locators; annotate `// Phase 87 S5 (ADR-344): label` | **Do not** edit the seven carried locations' assertions (`day-00:326`, `day-30:32/:159`, `day-45:41`, `day-90:61/:177`, `day-05:51`) beyond a label string they already target. |
| 611F.2 | E2E-LEGAL + epic gate | — | **E2E-LEGAL**; failing set == seven carried (run log in PR) | — | `phase87/05-terminology-removal`. |

### Key Files

**Delete:** `frontend/lib/terminology.tsx`, `components/terminology-{heading,text}.tsx`, `components/settings/vertical-profile-section.tsx`, 4 frontend mechanism tests, `portal/lib/terminology.tsx`, `portal/lib/__tests__/terminology-provider.test.tsx`, `packages/shared/src/terminology-map.ts`.
**Create:** `frontend/__tests__/no-terminology-remnants.test.ts` (+ allow-list), `portal/__tests__/no-terminology-remnants.test.ts`.
**Modify:** 73 frontend + 10 portal call-site files, `lib/messages/en/{help,empty-states,getting-started}.json`, `frontend/eslint.config.mjs`, `portal/eslint.config.mjs`, `frontend/CLAUDE.md`, `$FA/layout.tsx`, `$FA/settings/general/page.tsx`, `portal/app/(authenticated)/layout.tsx`, `portal/components/portal-sidebar.tsx`, `packages/shared/src/index.ts`, 17 tests, Playwright label locators.

### Architecture Decisions
- **Single vocabulary, literal copy, no runtime substitution; lint + scan + CLAUDE.md enforce it** ([ADR-338](../adr/ADR-338-terminology-layer-removal-single-vocabulary.md)).
- **Tasks are Tasks; "Mandate" lands once, on the accepted engagement letter** (ADR-338, [ADR-336](../adr/ADR-336-engagement-letter-document-plus-acceptance.md)).
- **Profile mechanism retained, picker removed** ([ADR-332](../adr/ADR-332-single-vertical-profile-mechanism-retained.md)).
- **Portal: mechanism only in S5; labels/routes in S10** (§12.3.6 "Portal mirror").

### Non-scope
- `NAV_GROUPS` zones/items/hrefs (612A). Portal nav labels, `/matters` routes (617A). Any wire or route change. Design changes beyond sentence case.

---

## Epic 612: Navigation IA (Six Zones) + Palette `requiredModule` + Unbilled Work Page

**Goal**: Re-author `NAV_GROUPS` to the attorney's week — Today · Matters · Clients · Fees · Trust · Firm, 28 items / 6 zones (3 + 5 + 3 + 6 + 7 + 4), sentence-case labels, Lucide icons per §12.6.1 — with the Today zone carrying interim Calendar + Court calendar items (replaced in 613) and Firm → Audit trail / Settings pointing at the **current** settings routes (switched in 616); `UTILITY_ITEMS` = Notifications only; mobile sheet reuses `SidebarUtilityFooter`; command palette honours `requiredModule`; `/invoices/unbilled` page over the existing unbilled-summary read; `/profitability` → `/reports/profitability` redirect; `nav-items.test.ts` zone-size rule; `navigation/*.spec.ts` + legal specs + scenario steps 0.23/0.24/0.59/90.3 amended.

**References**: Architecture §12.6.1 (zone table, Unbilled work page, `UTILITY_ITEMS`, palette re-index), §12.6.6, §12.10.2 rows 11, 14, §12.11.1, §12.12 S6a, §12.14 #8/#9/#15; [ADR-339](../adr/ADR-339-attorney-week-navigation-and-one-diary.md), [ADR-170](../adr/ADR-170-sidebar-zone-structure.md), [ADR-171](../adr/ADR-171-command-palette-scope.md).

**Dependencies**: 611.

**Scope**: Frontend only.

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **612A** | 612A.1–612A.3 | `nav-items.ts`, `mobile-sidebar.tsx`, `desktop-sidebar.tsx`, `nav-zone.tsx`, `legacy-redirects.ts`, `breadcrumbs.tsx`, 5 tests | `NAV_GROUPS` six zones; footer dedupe; `/profitability` redirect; zone-size test. |
| **612B** | 612B.1–612B.3 | `command-palette-dialog.tsx` + test, NEW `invoices/unbilled/{page,actions}.tsx`, 1 test | Palette `requiredModule`; Unbilled work page. |
| **612C** | 612C.1–612C.2 | Playwright nav/legal specs + 2 scenario docs | Amendments; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 612A.1 | Re-author `NAV_GROUPS` | `$F/lib/nav-items.ts` — zones `today` (Home `/dashboard` exact `house`; My work `/my-work` exact `list-checks`; **interim** Calendar `/calendar` + Court calendar `/court-calendar` until 613D), `matters` (Matters `/matters` `scale`; New matter `/matters/new` exact `PROJECT_MANAGEMENT` `plus`; Conflict check `/conflict-check` module `conflict_check` `search-check`; Adverse parties `/legal/adverse-parties` module `conflict_check` `users`; AI reviews `/ai/reviews` `AI_REVIEW` `sparkles`), `clients` (Clients `/clients` `CUSTOMER_MANAGEMENT` `contact`; FICA & compliance `/compliance` exact `shield-check`; Information requests `/compliance/requests` + module `information_requests` `inbox`), `fees` (collapsed: Unbilled work `/invoices/unbilled` `INVOICING` `hourglass`; Fee notes `/invoices` exact `receipt`; Fee runs `/invoices/billing-runs` module `bulk_billing` `layers`; Debtors `/invoices/collections` exact `banknote`; Disbursements `/legal/disbursements` module `disbursements` `wallet`; Tariffs `/legal/tariffs` module `lssa_tariff` `book-open`), `trust` (collapsed, 7 items `VIEW_TRUST` + module `trust_accounting`: Overview, Transactions, Client ledgers, Reconciliation, Interest, Investments, Trust reports — icons per §12.6.1), `firm` (collapsed: Team `/team` `users-round`; Reports `/reports` `FINANCIAL_VISIBILITY` `bar-chart-3`; Audit trail `/settings/audit-log` **interim** `isAdmin` `scroll-text`; Settings `/settings/general` **interim** `settings`); `UTILITY_ITEMS` = Notifications `/notifications` only; `NAV_ITEMS` flat export kept only if a consumer remains (grep; record); `SETTINGS_ITEMS` **kept** until 616E | `lib/__tests__/nav-items.test.ts`: 6 zones, 28 items, each zone ≤ 6 except `trust` ≤ 7, no item has children, every href resolves to a `page.tsx` (fs check), labels sentence case | Phase 44 zone system (`NAV_GROUPS` → `desktop-sidebar.tsx` → `nav-zone.tsx`) — content only | Profitability is **not** a Fees item (Firm → Reports). No "AI" word in a zone label. |
| 612A.2 | Footer dedupe + redirect + breadcrumb | `components/mobile-sidebar.tsx:166` imports the same `SidebarUtilityFooter` as `desktop-sidebar.tsx` (extract if needed); `lib/legacy-redirects.ts` += `/org/:slug/profitability → /org/:slug/reports/profitability` (permanent); `components/breadcrumbs.tsx` `SEGMENT_LABELS` += `unbilled`, `reports`, `profitability`, `compliance`, `legal`, `adverse-parties`, `disbursements`, `tariffs`, `ai`, `reviews`, `notifications`, `timesheet`, `information-requests` (those not already present) | `components/__tests__/{desktop-sidebar,mobile-sidebar,nav-zone}.test.tsx`, `breadcrumbs-routes.test.ts` | — | `/profitability/page.tsx` stays on disk one release? **No** — the redirect replaces it only if `/reports/profitability` exists as a page; confirm the route exists (`ls $FA/reports`) before deleting `$FA/profitability/page.tsx`; otherwise keep both and only add the nav entry under Reports. Record the finding. |
| 612A.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 612B.1 | Palette `requiredModule` | `components/command-palette-dialog.tsx:30-38` — page list applies `isModuleEnabled(item.requiredModule)` from `useOrgProfile()` exactly as `nav-zone.tsx`; settings entries still from `SETTINGS_ITEMS` (616E switches) | `components/__tests__/command-palette-dialog.test.tsx`: module-off hides "Disbursements"/"Trust reports" | `nav-zone.tsx` filter | Three-line fix + test. |
| 612B.2 | Unbilled work page | NEW `$FA/invoices/unbilled/page.tsx` + `actions.ts` over `GET /api/invoices/unbilled-summary?periodFrom&periodTo&currency` via `lib/api/billing-runs.ts` or the existing unbilled-summary action; renders `components/invoices/unbilled-summary.tsx`; `DateRangeFilter` (`components/views/DateRangeFilter.tsx`) + currency `Select`; defaults `periodFrom` = today − 24 months, `periodTo` = today, currency = org default (`OrgSettings`) | `__tests__/invoices/unbilled-page.test.tsx` (renders rows; defaults) | existing `$FA/invoices/page.tsx` server-component shape | **Open Question #15**: confirm whether the endpoint accepts an open-ended period; if not, keep 24 months and add helper text. `INVOICING` page gate as the nav item. |
| 612B.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 612C.1 | Playwright + scenario amendments | `frontend/e2e/tests/navigation/{sidebar-navigation,command-palette}.spec.ts` (zone labels, item order, 28 items), `legal-lifecycle/*.spec.ts` + `legal-depth-ii/*.spec.ts` nav-click locators ("Invoices" → "Fee notes", "Billing Runs" → "Fee runs", "Court Calendar" stays until 613); `qa/testplan/demos/legal-za-90day-keycloak.md` steps **0.23, 0.24, 0.59, 90.3** (+ the same steps in `legal-za-full-lifecycle-keycloak.md`) — nav path wording only, annotated "(Phase 87, ADR-344)" | spec compile | ADR-344 | Behaviour steps frozen. |
| 612C.2 | E2E-LEGAL + epic gate | — | **FE-GATE**; **E2E-LEGAL** + `navigation`; failing set == seven carried | — | `phase87/06-navigation-ia`. |

### Key Files

**Create:** `$FA/invoices/unbilled/{page,actions}.tsx`, `__tests__/invoices/unbilled-page.test.tsx`.
**Modify:** `lib/nav-items.ts`, `components/{desktop-sidebar,mobile-sidebar,nav-zone,command-palette-dialog,breadcrumbs}.tsx`, `lib/legacy-redirects.ts`, `lib/__tests__/nav-items.test.ts`, `components/__tests__/{desktop-sidebar,mobile-sidebar,nav-zone,command-palette-dialog}.test.tsx`, Playwright nav/legal specs, 2 scenario docs.

### Architecture Decisions
- **Six zones, one level deep, AI has no zone; Trust is the only seven-item zone** ([ADR-339](../adr/ADR-339-attorney-week-navigation-and-one-diary.md), §12.14 #8/#9).
- **S6a never links to a route S9 creates** (§12.6.1 palette paragraph) — interim hrefs by design.
- **Palette and sidebar apply the same gates** (`requiredModule` fix).

### Non-scope
- Diary item and calendar deletion (613). Settings routes (616). `SETTINGS_ITEMS` deletion (616E). Any wire change. Home page (614).

---

## Epic 613: One Diary — `GET /api/diary` + `/diary` Page, Calendars Deleted

**Goal**: One server-side aggregated read, `GET /api/diary` (new `diary/` package: `DiaryController`, `DiaryService`, `DiaryQuery`, `DiaryKind`, `DiaryEventResponse`), unions task due dates, matter due dates, court dates and prescription warnings for a date window ≤ 92 days with fee-earner fallback to the matter lead, overdue band, fixed ordering and the `X-Diary-Types` header; one `/diary` page with Day / Week (default) / Month / List views, kind chips, fee-earner and matter filters, kind rails + icons, row actions on the retained court-date dialogs; `/calendar` and `/court-calendar` pages deleted and redirected (incl. the `?tab=prescription` `has` rule); `todays-agenda` rebuilt on `DiaryEventResponse`; Today zone's interim items replaced by Diary; `/api/calendar` retained one release and annotated deprecated.

**References**: Architecture §12.2.6 (`DiaryEventResponse` table), §12.3.2 (aggregation, queries, filters, ordering, paging, tenant boundary, permission), §12.4.6–12.4.7, §12.5.1, §12.6.2 (page), §12.6.6, §12.10.1 "New — Diary", §12.10.2 row 12, §12.10.5 (`DiaryEventResponse` record), §12.10.6, §12.11.3, §12.12 S6b, §12.14 #2/#14/#17; [ADR-339](../adr/ADR-339-attorney-week-navigation-and-one-diary.md).

**Dependencies**: 612.

**Scope**: Backend (613A) + Frontend (613B, 613C, 613D).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **613A** | 613A.1–613A.4 | 5 new main + 2 new tests + 1 annotation | `diary/` package; unit + integration tests; BE-VERIFY. |
| **613B** | 613B.1–613B.3 | 4 new route files + 3–4 new components + types + tests | Page shell, actions, Week + List views, filter row, kind encoding, skeletons, empty states. |
| **613C** | 613C.1–613C.2 | 2–3 new components + row-action menus + header buttons + a11y + tests | Day + Month views, row actions on retained dialogs, `role="grid"` keyboard nav. |
| **613D** | 613D.1–613D.4 | 2 route dirs deleted, 3 components deleted, `todays-agenda.tsx`, `nav-items.ts`, `legacy-redirects.ts`, `breadcrumbs.tsx`, matter Court dates tab, specs, scenario | Deletions, redirects, agenda, Diary nav item, Playwright; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 613A.1 | `diary/` package | NEW `$B/diary/{DiaryController, DiaryService, DiaryQuery, DiaryKind, DiaryEventResponse}.java` — `GET /api/diary?from&to&types&feeEarnerId&matterId&includeOverdue`; `DiaryQuery(from, to, Set<DiaryKind> types, UUID feeEarnerId, UUID matterId, boolean includeOverdue)`; `DiaryKind {TASK_DUE, MATTER_DUE, COURT_DATE, PRESCRIPTION}`; `DiaryEventResponse` `@JsonInclude(NON_NULL)` record per §12.2.6 with `fromTask/fromMatterDue/fromCourtDate/fromPrescription` factories; `href` computed relative to `/org/{slug}` (`/matters/{id}?taskId=`, `/matters/{id}`, `/matters/{id}?tab=court-dates&courtDateId=`, `…&prescriptionId=`) | `DiaryServiceTest` (unit, repositories mocked): ordering `date, time NULLS LAST, kindRank (COURT_DATE, PRESCRIPTION, MATTER_DUE, TASK_DUE), title`; fee-earner fallback; overdue flag; window cap | `$B/calendar/CalendarService` for the accessible-matter scoping helper ("no new access path"); `$B/project/ProjectUpcomingDeadlinesService` for the court-date + matter-due union shape (left untouched — #14) | Reads `TaskRepository`, `ProjectRepository`, `CourtDateRepository.findByStatusInAndScheduledDateBetween` (SCHEDULED, POSTPONED), `PrescriptionTrackerRepository` finder on `prescription_date` (name confirmed at build; `CourtCalendarService.getUpcoming` derives the same list), `ProjectMemberRepository` (`project_role = 'lead'` batched), `CustomerRepository`, `MemberRepository`. Owns no table. No native SQL bypassing `ScopedValue`. |
| 613A.2 | Filters, gates, header, errors | `DiaryController`: `to − from > 92` → `400` problem detail `diary.window-too-large` (semantic exception from `$B/exception/`, never `ProblemDetail` in the controller); unknown `types` value → `400`; `matterId` not accessible → `404`; `COURT_DATE`/`PRESCRIPTION` only when module `court_calendar` enabled **and** the same gate `CourtCalendarController` applies (confirm capability at build; never widen); response header `X-Diary-Types` lists kinds actually served; **no** `@RequiresCapability` on the endpoint (as `/api/calendar`) | `DiaryControllerIntegrationTest` (MockMvc): window cap 400, types gate (module off → kinds omitted + header), fee-earner fallback, overdue band, ordering, 404 on inaccessible matter | existing `*ControllerIntegrationTest` under `$T/verticals/legal/courtcalendar/` + `TestEntityHelper`, `TestJwtFactory`, `TestCustomerFactory` (ACTIVE customer) | `TenantScopeBindingTest` covers the package automatically. Don't assert exact global counts over shared tenant state — id containment (backend/CLAUDE.md test-coupling rule). |
| 613A.3 | `/api/calendar` deprecation note | `$B/calendar/CalendarController.java` `@Deprecated(since = "Phase 87", forRemoval = true)` + javadoc "no frontend caller after Phase 87 S6b; delete in the first phase after 87 (§12.14 #2)" | compile | — | Endpoint behaviour unchanged. |
| 613A.4 | BE-VERIFY | — | **BE-VERIFY** full; `WireVocabularyContractTest` still green (new paths carry no legacy noun) | — | **Gate.** |
| 613B.1 | Diary route + actions + types | NEW `$FA/diary/{page.tsx, diary-page-client.tsx, diary-actions.ts, diary-types.ts}`; `page.tsx` reads `?view=` (default `week`), `?from=` (only persisted range param), `?types=`; `diary-actions.ts` server action `getDiary({from,to,types?,feeEarnerId?,matterId?})` → `DiaryEventResponse[]` + `typesServed` from `X-Diary-Types`; members list for the fee-earner `Select`; `GET /api/matters?mine=true` short list for the matter `Combobox` (plain list until 615A adds `mine` — use the current list endpoint and filter client-side; note it) | `__tests__/diary/diary-actions.test.ts` (query building, header parsing) | `$FA/calendar/calendar-actions.ts` shape (to be deleted in 613D); SWR-wrapping-server-actions pattern (`frontend/CLAUDE.md`) | `"use client"` only on `diary-page-client.tsx` and interactive children. |
| 613B.2 | Week + List views, filter row, kind encoding | NEW `components/diary/{diary-week-view, diary-list-view, diary-filter-row, diary-event-row}.tsx`; Week = Mon–Sun columns (`Card` cells); List = overdue band then date-grouped rows (`components/calendar/calendar-list-view.tsx` pattern, then that file is deleted in 613D); filter row = kind chips (hidden when `typesServed` omits them), fee-earner `Select` ("Mine" preselected for non-admins), matter `Combobox` (`Command` + `Popover`); client-side filtering within the loaded window except fee earner on ranges > 31 days (server param); kind rails: court date `teal-600` 3-px rail + `gavel`, prescription `destructive` rail + `alarm-clock`, matter due dashed `slate-400` rail + `flag`, task no rail + `circle-check` muted; "Overdue" `Badge destructive`; time in `font-mono`; `Skeleton` rows in view shape; empty states via `components/empty-state.tsx` ("Nothing in the diary for this week." + "Add a court date" when module on; "No events match these filters." + "Clear filters") | `__tests__/diary/{week-view,list-view,filter-row}.test.tsx` | `@b2mash/ui` `Button`, `Badge`, `Card`, `Skeleton`, `Separator`; `components/ui` `Tabs`, `Select`, `Command`/`Popover`, `Table` | No new tokens, nothing added to `packages/ui`. Load `frontend-design` + `vercel:shadcn` skills before building (requirements "Design discipline"). |
| 613B.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | Page reachable at `/diary` (no nav item yet). |
| 613C.1 | Day + Month views, row actions, header buttons | NEW `components/diary/{diary-day-view, diary-month-view}.tsx` (Day grouped by kind, court dates first; Month grid with ≤ 3 single-line chips + "+n", click → Day); `Tabs` `?view=` switch; range nav previous / today / next + native date input; row `DropdownMenu`: court date → Postpone / Record outcome / Cancel (mount retained `components/legal/{postpone-dialog, outcome-dialog, cancel-court-date-dialog}.tsx`), task → Mark done; header `Button`s "New court date" (`create-court-date-dialog`) + "New prescription tracker" (`create-prescription-dialog`), module-gated | `__tests__/diary/{day-view,month-view,row-actions}.test.tsx` | retained dialogs' existing props | Writes keep their existing endpoints (`/api/court-dates/**`, `/api/tasks/**`, `/api/prescription-trackers/**`). |
| 613C.2 | A11y + slice gate | Day/Week grid `role="grid"` with arrow-key navigation; kind markers never colour-only (icon + text); `prefers-reduced-motion` respected; visible focus | a11y assertions in the view tests | Phase 36 standards | `pnpm lint && pnpm build && pnpm test`. |
| 613D.1 | Delete calendars, rewire agenda + nav + redirects | DELETE `$FA/calendar/**`, `$FA/court-calendar/**`, `components/legal/{court-calendar-view, court-date-list-view, prescription-tab}.tsx`, `components/calendar/**` (after 613B copied the list pattern), `lib/recurrence.ts` (**if** unconsumed — grep; delete regardless per §12.3.2(d)); EDIT `components/dashboard/todays-agenda.tsx` → `getDiary({from: today, to: today})` on `DiaryEventResponse`; `lib/nav-items.ts` Today zone: interim Calendar + Court calendar → **Diary** `/diary` `calendar-days` (28 items holds: 3 + 5 + 3 + 6 + 7 + 4); `lib/legacy-redirects.ts` += `/org/:slug/calendar → /org/:slug/diary`, `/org/:slug/court-calendar` with `has: [{type:"query", key:"tab", value:"prescription"}]` → `/org/:slug/diary?types=PRESCRIPTION&view=list` (**must precede**), `/org/:slug/court-calendar → /org/:slug/diary?types=COURT_DATE,PRESCRIPTION`; `components/breadcrumbs.tsx` `SEGMENT_LABELS` += `diary`, remove `calendar`/`court-calendar`; `NON_ROUTABLE_PREFIXES` re-derived | `lib/__tests__/nav-items.test.ts`, `breadcrumbs-routes.test.ts`, `__tests__/dashboard/todays-agenda*.test.tsx`, `__tests__/lib/legacy-redirects.test.ts` | — | **Open Question #17**: mount the prescription-tracker list (ex `prescription-tab.tsx` content) inside `components/legal/project-court-dates-tab.tsx` here so prescriptions keep a matter-level surface; orchestrator decides — default is **yes, here**. |
| 613D.2 | Matter Court dates tab (if #17 = yes) | `components/legal/project-court-dates-tab.tsx` gains the prescription section + `create-prescription-dialog` mount; `?tab=court-dates&prescriptionId=` deep link from `href` resolves | `components/legal/__tests__/*` | `prescription-tab.tsx` (being deleted) is the source | Tab **id** `court-dates` unchanged (deep-link stability). |
| 613D.3 | Playwright + scenario | `legal-lifecycle/*.spec.ts` + `legal-depth-ii/*.spec.ts` — `/court-calendar` (3 literals) → `/diary?types=…`, "Court calendar" nav → "Diary", calendar-view locators → Diary view locators (URL/label/nav only); `navigation/sidebar-navigation.spec.ts`; `qa/testplan/demos/legal-za-90day-keycloak.md` + `legal-za-full-lifecycle-keycloak.md` court-calendar steps → Diary wording | spec compile | ADR-344 | `day-45-recon-prescription.spec.ts:41` (bank CSV) is carried — do not touch its assertion. |
| 613D.4 | FE-GATE + E2E-LEGAL + epic gate | — | **FE-GATE**; **E2E-LEGAL**; failing set == seven carried (run log) | — | `phase87/07-one-diary`. |

### Key Files

**Create (backend):** `$B/diary/{DiaryController,DiaryService,DiaryQuery,DiaryKind,DiaryEventResponse}.java`, `$T/diary/{DiaryServiceTest,DiaryControllerIntegrationTest}.java`.
**Modify (backend):** `$B/calendar/CalendarController` (annotation only).
**Create (frontend):** `$FA/diary/{page,diary-page-client,diary-actions,diary-types}.ts(x)`, `components/diary/{diary-week-view,diary-list-view,diary-day-view,diary-month-view,diary-filter-row,diary-event-row}.tsx`, `__tests__/diary/*`.
**Delete (frontend):** `$FA/calendar/**`, `$FA/court-calendar/**`, `components/legal/{court-calendar-view,court-date-list-view,prescription-tab}.tsx`, `components/calendar/**`, `lib/recurrence.ts`.
**Modify (frontend):** `components/dashboard/todays-agenda.tsx`, `lib/nav-items.ts`, `lib/legacy-redirects.ts`, `components/breadcrumbs.tsx`, `components/legal/project-court-dates-tab.tsx`, Playwright legal/nav specs, 2 scenario docs.
**Read for context:** `$B/calendar/CalendarService.java`, `$B/verticals/legal/courtcalendar/CourtCalendarService.java`, `$B/project/ProjectUpcomingDeadlinesService.java`, `$FA/calendar/calendar-actions.ts`, `components/calendar/calendar-list-view.tsx`.

### Architecture Decisions
- **One server-side composition, date window not pages (≤ 92 days), fee earner falls back to matter lead** ([ADR-339](../adr/ADR-339-attorney-week-navigation-and-one-diary.md), §12.3.2).
- **Diary keeps `/api/calendar`'s visibility semantics exactly; court/prescription kinds gated as today** (§12.3.2 Permission, §12.11.3).
- **Writes stay on their controllers; dialogs retained** (§12.6.2).
- **`/api/calendar` retained one release** (§12.4.6).

### Non-scope
- Deleting `calendar/` (follow-up #2). Folding `ProjectUpcomingDeadlinesService` (#14). `mine=true` list param (615A). Home widget matrix (614) — only `todays-agenda` is rebuilt here. New `packages/ui` primitives or tokens.

---

## Epic 614: Legal-Native Home — Three Dashboard Reads + Widget Matrix

**Goal**: Three new reads in the existing `dashboard/` package — `GET /api/dashboard/unbilled-wip` (by fee earner, `withFinancialsRedacted()` without `FINANCIAL_VISIBILITY`), `GET /api/dashboard/fica-outstanding?limit=` (`CUSTOMER_MANAGEMENT`; batched FICA status through a `FicaStatusProjector` extracted from `InformationRequestService.getFicaStatus` so single and batch share one rule), `GET /api/dashboard/trust-position?accountId=` (`VIEW_TRUST` + module `trust_accounting`; composes cashbook balance, client-ledger total, latest reconciliation, pending approvals) — and the deletion of `GET /api/dashboard/team-workload` + `TeamWorkloadEntry` + `timeentry/TeamWorkloadProjection`. The home page becomes the capability-keyed widget matrix of §12.6.3 with four new widgets, `project-health-widget → matter-health-widget`, and the agency widgets deleted.

**References**: Architecture §12.2.6 (`UnbilledWipResponse`, `FicaOutstandingResponse`, `TrustPositionResponse`), §12.3.3 (reads, SQL, redaction), §12.4.3 (`team-workload` deleted), §12.4.6–12.4.7, §12.5.3, §12.6.3 (widget matrix), §12.6.6, §12.10.1 "New — dashboard reads", §12.10.2 row 13, §12.10.6, §12.11.2, §12.12 S7; [ADR-339](../adr/ADR-339-attorney-week-navigation-and-one-diary.md).

**Dependencies**: 613.

**Scope**: Backend (614A) + Frontend (614B, 614C).

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **614A** | 614A.1–614A.4 | 3 new DTOs + 1 new projector + 2 repos + controller/service (+3, −1) + 3 deletions + 3 new tests | Reads, projector, deletion; BE-VERIFY. |
| **614B** | 614B.1–614B.3 | `dashboard/page.tsx`, `lib/actions/dashboard.ts`, `dashboard-types.ts`, 4 new widgets, 1 rename, 5 deletions, widget tests | Widget matrix. |
| **614C** | 614C.1–614C.2 | Playwright + `__tests__/dashboard/*` | Specs; FE-GATE; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 614A.1 | `FicaStatusProjector` + repository aggregates | NEW `$B/informationrequest/FicaStatusProjector.java` (`project(requests, items)` → `NOT_STARTED \| IN_PROGRESS \| DONE` + `completedAt`; `InformationRequestService.getFicaStatus(customerId)` delegates to it); `$B/timeentry/TimeEntryRepository` gains the by-member unbilled aggregate (`billable = true AND invoice_id IS NULL`, `SUM(duration_minutes)`, `SUM(duration_minutes/60.0 * COALESCE(billing_rate_snapshot,0))`, `MIN(date)`, grouped by `member_id, billing_rate_currency`); `$B/verticals/legal/trustaccounting/**/TrustReconciliationRepository` gains `findTopByTrustAccountIdOrderByPeriodEndDesc` (or equivalent) | NEW `$T/informationrequest/FicaStatusProjectorTest` (shared rule: batch result == single-client result for the same fixtures; `CANCELLED` ignored; `DONE` only when every item `ACCEPTED`) | `InformationRequestService.getFicaStatus` (`FICA_ONBOARDING_PACK_ID`) is the source of truth — extract, don't re-derive | Time entries join matters through `tasks` (no `project_id` on `time_entries`). |
| 614A.2 | Three reads | `$B/dashboard/DashboardController` (+3 `@GetMapping`: `/api/dashboard/unbilled-wip` no annotation; `/fica-outstanding` `@RequiresCapability("CUSTOMER_MANAGEMENT")`; `/trust-position` `@RequiresCapability("VIEW_TRUST")` + module guard `trust_accounting`), `DashboardService` (+3 methods: `getUnbilledWip(actor)` full list for `FINANCIAL_VISIBILITY` or `isAdminOrOwner(orgRole)` else `withFinancialsRedacted()` = caller's row, null values, `redacted = true`; `getFicaOutstanding(limit)` over clients `lifecycle_status IN ('ONBOARDING','ACTIVE')`, three batched queries + projector, `daysOutstanding` = today − `lifecycle_status_changed_at` (or `created_at`); `getTrustPosition(accountId?)` = primary account or `?accountId=`, `TrustTransactionService` cashbook balance, `ClientLedgerCardRepository.calculateTotalTrustBalance`, latest reconciliation `DRAFT/COMPLETED` + `is_balanced`, pending-approval count, `accounts` list), NEW `$B/dashboard/dto/{UnbilledWipResponse(+FeeEarnerWip), FicaOutstandingResponse(+FicaOutstandingClient), TrustPositionResponse(+TrustAccountRef)}` per §12.2.6; disbursement leg reuses the query behind `GET /api/legal/disbursements/unbilled` (approved + unbilled — confirm the billing-status enum value at build) | NEW `$T/dashboard/DashboardServiceUnbilledWipTest` (redaction: member sees own row, null values; admin sees all), NEW `$T/dashboard/TrustPositionIntegrationTest` (composite equals `/cashbook-balance` + `/total-balance` + `/pending-approvals` for the same account; `VIEW_TRUST` 403; module off 404/403 as the parts) | `DashboardService.getKpis` + `KpiResponse.withFinancialsRedacted()` precedent | Currency from `OrgSettings` default. No new SQL for trust-position. |
| 614A.3 | Delete `team-workload` | DELETE `GET /api/dashboard/team-workload` mapping + service method, `$B/dashboard/dto/TeamWorkloadEntry`, `$B/timeentry/TeamWorkloadProjection` + its repository method if sole use; `$T/dashboard/**` team-workload tests deleted; `DashboardWithoutPipelineTest`/`DashboardWithoutCapacityTest` (Phase 86) still pass unedited | `GET /api/dashboard/team-workload` → 404 asserted in `TrustPositionIntegrationTest` or a sibling | Phase 86 deletion shape | Founder lean: no legal consumer. |
| 614A.4 | BE-VERIFY | — | **BE-VERIFY** full; `WireVocabularyContractTest` green (new DTOs use `clientId`/`clientName`) | — | **Gate.** |
| 614B.1 | Fan-out action + types | `$F/lib/actions/dashboard.ts` (`Promise.all` fan-out, each call only if its gate passes: diary today (+ `feeEarnerId=me` for members), `my-work/tasks`, `unbilled-wip`, `trust-position` (`VIEW_TRUST` + module), `fica-outstanding?limit=5` (`CUSTOMER_MANAGEMENT`), `collections/debtors?size=5` (`INVOICING`), `activity?limit=15`); `lib/dashboard-types.ts` += `UnbilledWipResponse`, `FicaOutstandingResponse`, `TrustPositionResponse`; `lib/api/*` entries | `__tests__/dashboard/dashboard-actions.test.ts` (gate → call matrix) | capabilities from the layout's `CapabilityProvider` (`GET /api/me/capabilities`, fetched once) | — |
| 614B.2 | Widget matrix | `$FA/dashboard/page.tsx` rows per §12.6.3 (fee earner: diary → tasks + my unbilled time → matter health + activity; bookkeeper/admin: unbilled WIP + debtors + trust → diary + FICA → activity + sensitive; owner: diary + WIP → debtors + trust → FICA + health + activity + sensitive; present-or-absent, never greyed); NEW `components/dashboard/{unbilled-wip-widget, trust-position-widget, fica-outstanding-widget, debtors-widget}.tsx` (12-col grid `Card`s, compact density, `font-mono` tabular figures, one headline + one owner link: `/invoices/unbilled`, `/trust-accounting/reconciliation`, `/invoices/collections`, `/clients?view=system:fica-outstanding`; aging buckets via `micro-stacked-bar`; trust `difference` + `Badge` "Balanced"/"Difference R x"); `git mv project-health-widget.tsx matter-health-widget.tsx`; FICA widget footer carries the `incomplete-profiles` + `information-requests` links; `getting-started-card` until first matter; DELETE `team-workload-widget.tsx`, `metrics-strip.tsx`, `kpi-card-row.tsx`, `admin-stats-column.tsx`, `my-week-column.tsx` (`activeMatterCount` becomes the Matters list header count in 615C) | `__tests__/dashboard/*` widget tests **per audience row** (fee earner, bookkeeper/admin, owner) + redaction rendering ("My unbilled time" hours only) | existing `components/dashboard/{sparkline,donut-chart,horizontal-bar-chart,micro-stacked-bar,radial-gauge}` primitives; `@b2mash/ui` `Card`/`Badge` | Load `frontend-design` + `vercel:shadcn` before building. No new tokens. |
| 614B.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 614C.1 | Playwright | `legal-lifecycle/day-90-review.spec.ts` dashboard copy/locators **if** it asserts dashboard widgets (URL/label only; `:61` and `:177` are carried — do not edit their assertions); `legal-lifecycle/day-01-03-onboarding.spec.ts` getting-started locators if changed | spec compile | ADR-344 | — |
| 614C.2 | FE-GATE + E2E-LEGAL + epic gate | — | **FE-GATE**; **E2E-LEGAL**; failing set == seven carried | — | `phase87/08-legal-home`. |

### Key Files

**Create (backend):** `$B/informationrequest/FicaStatusProjector.java`, `$B/dashboard/dto/{UnbilledWipResponse,FicaOutstandingResponse,TrustPositionResponse}.java`, `$T/dashboard/{DashboardServiceUnbilledWipTest,TrustPositionIntegrationTest}.java`, `$T/informationrequest/FicaStatusProjectorTest.java`.
**Modify (backend):** `$B/dashboard/{DashboardController,DashboardService}`, `$B/informationrequest/InformationRequestService`, `$B/timeentry/TimeEntryRepository`, `TrustReconciliationRepository`.
**Delete (backend):** `$B/dashboard/dto/TeamWorkloadEntry`, `$B/timeentry/TeamWorkloadProjection`, team-workload mapping + tests.
**Frontend:** create 4 widgets; rename `project-health-widget→matter-health-widget`; delete `team-workload-widget`, `metrics-strip`, `kpi-card-row`, `admin-stats-column`, `my-week-column`; modify `dashboard/page.tsx`, `lib/actions/dashboard.ts`, `lib/dashboard-types.ts`, `__tests__/dashboard/*`.

### Architecture Decisions
- **One endpoint with redaction rather than two** — `withFinancialsRedacted()` precedent (§12.3.3).
- **Trust position composes existing computations; reveals nothing the parts do not** (§12.3.3).
- **Audiences are capability bundles, not roles; widgets present-or-absent** (§12.6.3, §12.11).
- **Team workload dropped** (founder lean, §12.4.3).

### Non-scope
- `MatterListRow`/`ClientListRow` (615). Settings. Any new chart primitive in `@b2mash/ui`. KPI strip resurrection.

---

## Epic 615: Dense Lists (`MatterListRow`/`ClientListRow`), System Views, Tab Groups, Disbursements

**Goal**: Replace the card-grid Matters page (per-card N+1: `/budget/status`, `/customers`) and the hand-rolled Clients table with dense `Table`s over two server-side row projections — `GET /api/matters` → `List<MatterListRow>` (client, lead fee earner, unbilled minutes/value, client trust balance, next diary date/kind, open tasks, tags, custom fields; batched `IN` queries, ≤ 7 queries for a 50-row page) and `GET /api/clients` → `List<ClientListRow>` (FICA status, outstanding fee notes, trust balance, open matters, completeness) — with new list params (`mine`, `hasUnbilled`, `inactiveSince`; `ficaStatus`, `hasTrustBalance`), code-defined system views merged ahead of saved views, saved-view `columns` honoured for the first time; relabel `matter-tab-groups.ts` (7 groups / **21** tabs — `expenses` removed) and `client-tab-groups.ts` (6 / 14) with a `LEGACY_TAB_IDS` alias map; remove the orphan `members` panel from `MatterTabs`; delete the standalone expenses page; one Disbursements tab (ADR-340); profitability note.

**References**: Architecture §12.2.6 (`MatterListRow`, `ClientListRow`), §12.3.4 (N+1, SQL, params, system views), §12.3.5 (disbursements), §12.4.1–12.4.2, §12.6.4 (tables, columns), §12.6.5 (tabs, orphan, alias map), §12.10.1 "Lists", §12.10.2 rows 8, 15–16, §12.10.6, §12.11.3, §12.12 S8, §12.14 #1/#12/#18; [ADR-340](../adr/ADR-340-disbursement-is-the-face-of-matter-costs.md), [ADR-342](../adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md), [ADR-055](../adr/ADR-055-saved-view-filter-execution.md), [ADR-287](../adr/ADR-287-grouped-tabs-dense-navigation.md), [ADR-298](../adr/ADR-298-shared-grouped-tab-bar.md).

**Dependencies**: 614.

**Scope**: Backend (615A, 615B) + Frontend (615C, 615D, 615E).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **615A** | 615A.1–615A.3 | 1 new DTO + `ProjectService`/`ProjectController` + 4 repository methods + 1 native query + 1 new test | `MatterListRow` + params + query-count test. |
| **615B** | 615B.1–615B.3 | 1 new DTO + `CustomerService`/`CustomerController` + 3 repository methods + 1 new test | `ClientListRow` + params + filtering moved into the service; BE-VERIFY. |
| **615C** | 615C.1–615C.3 | `matters/page.tsx` + 2–3 components, `lib/constants/system-views.ts`, `components/views/{ViewSelectorClient,SavedViewSelector,constants}`, types, tests | Matters table + system views + `columns`. |
| **615D** | 615D.1–615D.2 | `clients/page.tsx` + 1–2 components, types, tests | Clients table. |
| **615E** | 615E.1–615E.4 | 2 constants renames, `matter-tabs.tsx`, `matters/[id]/page.tsx`, `clients/[id]/page.tsx`, 1 page deleted, redirect row, My Work mount, profitability note, specs | Tab groups, Disbursements, specs; FE-GATE; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 615A.1 | `MatterListRow` + aggregates | NEW `$B/project/dto/MatterListRow.java` (§12.2.6 fields); `$B/project/ProjectService.listProjects` → `listMatterRows(actor, filters, viewId, …)`: existing view-filtered base query → ids → one batched query per aggregate merged in Java: client names (`CustomerRepository.findByIdIn`), lead fee earner (`ProjectMemberRepository`: `project_role = 'lead'` first by `created_at` + `members.name`), unbilled minutes/value (`TimeEntryRepository` via `tasks.project_id`, `billable = true AND invoice_id IS NULL`, + approved unbilled `legal_disbursements.amount`), trust balance (`ClientLedgerCardRepository` batch `SUM(balance)` by `customer_id` — client-level), next diary date/kind (native `UNION ALL` query in `$B/diary/` over tasks/projects/court_dates, `MIN(d)`), open task count, tags (batched as today), caller's `matterRole`; `ProjectController` list returns `List<MatterListRow>` (detail keeps `MatterResponse`); financial redaction: `unbilledValue` null without `FINANCIAL_VISIBILITY`, `trustBalance` null without `VIEW_TRUST`; minutes/counts never redacted | NEW `$T/project/MatterListRowIntegrationTest`: Hibernate statistics query count ≤ 7 for a 50-matter page; redaction by capability; `mine`/`hasUnbilled`/`inactiveSince` params; row shape | `ProjectService.listProjects` + `ViewFilterHelper.applyViewFilter` (ADR-055, untouched); `ProjectController` tag batching | **Open Question #12**: confirm whether `trust_transactions.project_id` exists/is populated; record in PR; column stays client-level. Hibernate statistics: enable via test property already in `application-test.yml` if present, else `@TestPropertySource` is **not** allowed — use `SessionFactory.getStatistics()` with `hibernate.generate_statistics` set in the yml (backend/CLAUDE.md context-cache rule). |
| 615A.2 | New list params | `ProjectService`: `mine=true` (caller has a `project_members` row), `hasUnbilled=true` (unbilled minutes > 0), `inactiveSince=<date>` (no `projects.updated_at`, `time_entries.date`, task change after the date — exact predicate confirmed at build and recorded) — applied in SQL after the view filter, never in the controller | covered by 615A.1 test | — | `-90d` is resolved to a date by the **frontend** server action (615C). |
| 615A.3 | Targeted | — | `-Dtest='…project.**,…diary.**'` + `WireVocabularyContractTest` | — | — |
| 615B.1 | `ClientListRow` + service filtering | NEW `$B/customer/dto/ClientListRow.java` (§12.2.6); `CustomerService.listClientRows(…)`: **move `CustomerController.listCustomers` in-controller filtering (`:102` `List<Customer> filtered`) into the service**; batched aggregates: `ficaStatus`/`ficaLastVerifiedAt` via `FicaStatusProjector` (614A) over one query for FICA template ids + one for non-cancelled requests + one for items; `outstandingFeeNoteTotal/Count/currency` (`InvoiceRepository`: `status = 'SENT'` grouped by `customer_id, currency` — same definition as `CollectionsReadService`), `trustBalance` (`ClientLedgerCardRepository.sumBalancesForCustomer` generalised to a batch), `openMatterCount` (`projects` ACTIVE by `customer_id`), `completenessPercent` (existing completeness map folded in), tags, custom fields; new params `ficaStatus=OUTSTANDING\|DONE`, `hasTrustBalance=true`; redaction: fee-note total needs `FINANCIAL_VISIBILITY`, trust `VIEW_TRUST`; endpoint stays ungated | NEW `$T/customer/ClientListRowIntegrationTest` (query cap, redaction, params, `showIncomplete` survives) | `CollectionsReadService` debtor definition; `InformationRequestService` FICA pack | `CUSTOMER_MANAGEMENT` stays a **page** gate. |
| 615B.2 | BE-VERIFY | — | **BE-VERIFY** full | — | **Gate.** |
| 615B.3 | PR-body evidence | — | Hibernate statistics output for both lists pasted | — | — |
| 615C.1 | System views + view selector | NEW `$F/lib/constants/system-views.ts` (`SystemView {id: "system:…", entityType: "PROJECT"\|"CUSTOMER", name, params, system: true}`; `MATTER_SYSTEM_VIEWS` = My matters `{mine, status: ACTIVE}`, Open matters `{status: ACTIVE}`, Awaiting fee note `{status: ACTIVE, hasUnbilled}`, Dormant `{status: ACTIVE, inactiveSince: "-90d"}`; `CLIENT_SYSTEM_VIEWS` = Active, FICA outstanding, With trust balance); `components/views/{ViewSelectorClient,SavedViewSelector,constants}.tsx`: system views first (fixed order), separator, then `GET /api/views?entityType=` rows; system views not editable/deletable (menu hides actions when `system`); "Save as view" from a system view pre-fills params; default selection "Open matters" / "Active"; `-90d` → date in the server action so URLs stay bookmarkable | NEW `__tests__/lib/system-views.test.ts` (ids never UUIDs; `entityType` strings are storage-plane; merge order; default selection) | `SavedViewResponse` shape; ADR-055 execution path untouched | `entityType: "PROJECT"` is deliberate (ADR-342). |
| 615C.2 | Matters table | `$FA/matters/page.tsx` → `Table` (`components/ui/table.tsx`, no TanStack): header (title, count from row total — replaces `activeMatterCount` KPI, primary `Button` "New matter" → existing `create-project-dialog`), toolbar (`ViewSelectorClient`, `SearchInput`, status pills `ACTIVE\|COMPLETED\|ARCHIVED\|CLOSED`, `TagFilter`, `CustomFieldFilter`), columns per §12.6.4 (Reference `font-mono` link · Matter + tag chips · Client link `/clients/{clientId}` · Work type humanised · Fee earner initials + name · Status `Badge` · Opened · Unbilled "21h · R 42 000" (value redacted → hours only) · Trust (empty when module off) · Next in diary date + kind marker), `columns` from a saved view honoured, server-param sorting (default `nextDiaryDate asc nulls last`; Dormant: `createdAt asc`), row click → detail, trailing `DropdownMenu` = today's overflow actions, "—" for null financials, footer count; per-card `/budget/status` + `/customers` fetches **removed**; `lib/types/matter.ts` += `MatterListRow`; `lib/api/matters.ts` list params | `__tests__/matters/matters-table.test.tsx` (columns, redaction "—", row actions, view switch params) | `components/ui/table.tsx`; `components/customers` existing table as the nearest shape; kind markers from `components/diary/diary-event-row.tsx` | `<th scope="col">` + visually hidden caption; `Sheet` for mobile filters. |
| 615C.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 615D.1 | Clients table | `$FA/clients/page.tsx` → `Table`: columns Client (+ `clientType` muted suffix) · Lifecycle (`LifecycleStatusBadge`) · FICA (`Badge` Done / In progress outline / Not started destructive text, + `ficaLastVerifiedAt`) · Open matters · Outstanding fee notes "R 18 400 · 2" · Trust balance · Completeness (`CompletenessBadge`, keeps `sortBy=completeness`) · Created · Contact (muted, truncated, not sortable); `showIncomplete` toolbar toggle (linked from `incomplete-profiles-widget`); `CLIENT_SYSTEM_VIEWS`; "New client" → existing dialog; `lib/types/client.ts` += `ClientListRow`; `lib/api/clients.ts` params | `__tests__/clients/clients-table.test.tsx` | 615C table | — |
| 615D.2 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 615E.1 | Tab groups + alias map + orphan | `git mv lib/constants/project-tab-groups.ts matter-tab-groups.ts` (`MATTER_TAB_GROUPS`: Details [Details, Custom fields] · Overview · Work [Tasks, Documents, Correspondence, Generated documents] · **Fees** [Time recording, **Disbursements**, Fee estimate, Tariffs, Financials, Statements of account, Trust] · Client [Clients (id `customers`→`clients`), Information requests, Client comments, Adverse parties] · **Diary** [Court dates] · Activity [Activity, Audit trail] = 7 groups / **21** tabs; stale "23" comment fixed), `customer-tab-groups.ts → client-tab-groups.ts` (`CLIENT_TAB_GROUPS`: Details [Details, Custom fields, Tags] · Overview · **Matters** [Matters (id `projects`→`matters`), Documents, Generated documents] · Fees [Fee notes, Tariffs, Financials, Trust] · FICA & compliance [FICA onboarding, Information requests] · Activity [Audit trail] = 6 / 14); `git mv components/projects/project-tabs.tsx components/matters/matter-tabs.tsx` (`MatterTabs`; delete `membersPanel` prop, `"members"` union member, `<TabsPrimitive.Content value="members">` at `:19,45,252`; `expensesPanel` + `components/expenses/*` mount removed); `LEGACY_TAB_IDS = { customers: "clients", projects: "matters", expenses: "disbursements" }` applied inside `resolveTabFromUrl` in `MatterTabs` + `ClientGroupedTabs`; `$FA/matters/[id]/page.tsx:502` `disbursementsEnabled={false} // TODO` → `isModuleEnabled("disbursements")` | NEW/updated `__tests__/lib/constants/{matter-tab-groups,client-tab-groups}.test.ts` pinning **7/21** and **6/14**, ids `court-dates`, `disbursements`, `statements`, `trust`, `time`, `documents` stable, alias map resolves old `?tab=` | `GroupedTabBar` (ADR-298) unchanged | **Stop and re-spec** if either page needs more than relabelling + orphan removal (decisions file). |
| 615E.2 | Disbursements resolution | DELETE `$FA/matters/[id]/expenses/page.tsx` (+ its `actions.ts`); `lib/legacy-redirects.ts` += `/org/:slug/matters/:id/expenses → /org/:slug/matters/:id?tab=disbursements` (old `/projects/:id/expenses` chains through the 610D rule); remove "Log expense" entry points; `lib/actions/expense-actions.ts#getMyExpenses` mount removed from `$FA/my-work/**`; profitability page (`$FA/reports/profitability` or `$FA/profitability`) one-line note "Costs reflect logged expenses only; disbursements are shown as fee-note lines" | `__tests__/lib/legacy-redirects.test.ts`; `breadcrumbs-routes.test.ts` (`expenses` prefix gone) | ADR-340 | `Expense` entity/endpoints/`lib/types/expense.ts`/`InvoiceLineType.EXPENSE` **retained**. |
| 615E.3 | Playwright + scenario | `legal-depth-ii/day-05-disbursements.spec.ts` (tab labels only; `:51` carried), `legal-lifecycle/day-30-billing.spec.ts` (tab labels; `:32`/`:159` carried), `?tab=customers` → `?tab=clients`, `?tab=projects` → `?tab=matters` in any spec; `projects/project-crud.spec.ts` list locators (table rows instead of cards — locator shape only); **Open Question #18**: `projects/project-expenses.spec.ts` retirement needs explicit authorisation in the PR body citing ADR-340 (recommended: delete + add a redirect assertion to `project-crud.spec.ts`); scenario docs tab wording | spec compile | ADR-344 (+ ADR-340 for #18) | — |
| 615E.4 | FE-GATE + E2E-LEGAL + epic gate | — | **FE-GATE**; **E2E-LEGAL**; failing set == seven carried | — | `phase87/09-lists-tabs-disbursements`. |

### Key Files

**Create (backend):** `$B/project/dto/MatterListRow.java`, `$B/customer/dto/ClientListRow.java`, `$T/project/MatterListRowIntegrationTest.java`, `$T/customer/ClientListRowIntegrationTest.java`.
**Modify (backend):** `$B/project/{ProjectService,ProjectController}`, `$B/customer/{CustomerService,CustomerController}`, `$B/timeentry/TimeEntryRepository`, `$B/member/ProjectMemberRepository`, `ClientLedgerCardRepository`, `$B/invoice/InvoiceRepository`, `$B/diary/` (next-diary-date query).
**Create (frontend):** `lib/constants/system-views.ts`, `__tests__/lib/system-views.test.ts`, table components + tests.
**Rename (frontend):** `lib/constants/project-tab-groups.ts→matter-tab-groups.ts`, `customer-tab-groups.ts→client-tab-groups.ts`, `components/projects/project-tabs.tsx→components/matters/matter-tabs.tsx`.
**Delete (frontend):** `$FA/matters/[id]/expenses/**`.
**Modify (frontend):** `$FA/matters/page.tsx`, `$FA/clients/page.tsx`, `$FA/matters/[id]/page.tsx`, `$FA/clients/[id]/page.tsx`, `components/views/{ViewSelectorClient,SavedViewSelector,constants}.tsx`, `lib/types/{matter,client}.ts`, `lib/api/{matters,clients}.ts`, `lib/legacy-redirects.ts`, `$FA/my-work/**`, profitability page, specs, scenario docs.

### Architecture Decisions
- **Server-side row projection, one batched query per aggregate** (§12.3.4).
- **System views are code-defined — no migration, no mutable tenant rows; keyed `"PROJECT"`/`"CUSTOMER"`** (§12.3.4, [ADR-342](../adr/ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md)).
- **Disbursement is the face of matter costs; `Expense` retained without UI; profitability cost leg deferred** ([ADR-340](../adr/ADR-340-disbursement-is-the-face-of-matter-costs.md), §12.3.5).
- **Tab ids stable where deep-linked; aliases for the two renamed ids** (§12.6.5).

### Non-scope
- Profitability cost-leg switch (#1). Per-matter trust balance (#12). Deleting `expense/` or its table. Settings. TanStack or any new table library.

---

## Epic 616: Settings Consolidation — Five Families, 44 → 17 Routes, 43 Redirects

**Goal**: "Controls stay, pages go." The 44 settings `page.tsx` (43 leaf + index) become **17** (5 family pages + 11 kept editor sub-routes + index): Firm (8 tabs) · Matters (7) · Documents (4) · Fees & Trust (6) · Connections (6) = 31 tabs addressed by `?tab=`; one source `lib/settings-nav.ts` (`SETTINGS_FAMILIES`, `settingsHref`) feeds the settings rail and the command palette, replacing both `SETTINGS_ITEMS` (28) and `SETTINGS_NAV_GROUPS` (7/28); every old URL gets a `permanent: true` row in `lib/legacy-redirects.ts` — **43 rows = 32 tab-kind + 11 route-kind**; the Firm zone's Audit trail / Settings items switch to `/settings/firm?tab=audit-trail` / `/settings/firm`; a11y per §12.7.4. Each family slice creates its page, moves its sub-routes, deletes its old pages and appends **its own** redirect rows so the old rail still resolves mid-branch.

**References**: Architecture §12.7 (rule), §12.7.1 (families → tabs → sub-routes), §12.7.2 (44-row before/after table), §12.7.3 (one nav source, `SettingsFamily`/`SettingsTab` shape, test), §12.7.4 (redirect policy, tab addressing, a11y), §12.10.2 row 17, §12.10.5 (`legacy-redirects.ts`), §12.11.4, §12.12 S9, §12.14 #3/#10; [ADR-341](../adr/ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md), [ADR-335](../adr/ADR-335-manual-firm-level-billing-only.md), [ADR-172](../adr/ADR-172-settings-layout-pattern.md).

**Dependencies**: 615.

**Scope**: Frontend only.

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **616A** | 616A.1–616A.3 | NEW `lib/settings-nav.ts` + Firm `page.tsx` + 8 tab loaders; 8 old pages deleted; 8 redirect rows; `settings/page.tsx` index | Nav source + Firm family. |
| **616B** | 616B.1–616B.2 | Matters `page.tsx` + 7 tabs; 6 sub-routes `git mv`; 7 old pages deleted; 13 redirect rows | Matters family. |
| **616C** | 616C.1–616C.3 | Documents `page.tsx` + 4 tabs + 2 sub-routes; Fees & Trust `page.tsx` + 6 tabs; 10 old pages deleted; 12 redirect rows | Documents + Fees & Trust families. |
| **616D** | 616D.1–616D.2 | Connections `page.tsx` + 6 tabs + 3 sub-routes; 9 old pages deleted; 10 redirect rows | Connections family; route count = 17. |
| **616E** | 616E.1–616E.4 | `settings-sidebar.tsx`, `settings-nav-groups.ts` deleted, `nav-items.ts` (`SETTINGS_ITEMS` deleted, Firm hrefs), `command-palette-dialog.tsx`, `breadcrumbs.tsx`, `settings/layout.tsx`, 3 tests | One nav source wired everywhere; a11y; tests. |
| **616F** | 616F.1–616F.2 | 8 `settings/*.spec.ts`, `day-00-firm-setup.spec.ts`, scenario docs | Amendments; FE-GATE; E2E-LEGAL; epic gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 616A.1 | `lib/settings-nav.ts` | NEW `$F/lib/settings-nav.ts` — `SettingsTab {id, label, description?, adminOnly?, requiredModule?, requiredCapability?, keywords?}`, `SettingsFamily {id: "firm"\|"matters"\|"documents"\|"fees"\|"connections", label, href(slug), icon, tabs}`, `SETTINGS_FAMILIES` with the §12.7.1 content (all 31 tabs declared now, even before their pages exist — the fs test in 616E tolerates missing family pages until then only via a `pending` flag removed in 616D), `settingsHref(slug, family, tab?)`; `adminOnly`/`requiredModule`/`requiredCapability` reproduce today's `SettingsItem`/`SettingsNavItem` flags one-to-one | NEW `lib/__tests__/settings-nav.test.ts` (tab ids unique within a family; 5 families; 31 tabs; flags match the old `SETTINGS_NAV_GROUPS` entries — assert against a frozen copy, then delete the copy in 616E) | `lib/nav-items.ts` `NavItem` shape; `components/settings/settings-nav-groups.ts` as the flag source | `Security` (`comingSoon`, `href "#"`) dropped — no page. |
| 616A.2 | Firm family page | NEW `$FA/settings/firm/page.tsx` (`searchParams.tab`; missing/unknown → first permitted tab, never 404; `generateMetadata` "Firm — {Tab} · Settings"; `<Tabs value={tab}>` from `SETTINGS_FAMILIES`; `router.replace` with `scroll: false` on change; each panel starts with its former page's `<h2>`; loads **only** the active tab's data — each former page's loader becomes a tab loader in `settings/firm/_tabs/{general,roles,audit-trail,data-protection,data-requests,compliance,features,subscription}.tsx`); tabs ← `general` (minus the profile picker — already removed in 611D), `roles`, `audit-log`, `data-protection`, `data-protection/requests`, `compliance`, `features`, `billing` (→ **Subscription**, ADR-335); DELETE the 8 old `page.tsx` (+ colocated `actions.ts` moved beside the tab loaders); `$FA/settings/page.tsx` index → `firm` | `__tests__/settings/firm-family.test.tsx` (tab resolution, admin-only tabs absent not disabled, metadata) | `settings/layout.tsx` + `SettingsSidebar` (ADR-172, URL preservation) — layout untouched here | `lib/legacy-redirects.ts` += 8 rows (`general`, `features`, `roles`, `audit-log`, `data-protection`, `data-protection/requests`, `compliance`, `billing` → `firm?tab=…`). |
| 616A.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test`; `curl -I …/settings/general` → 308 on the e2e stack | — | Old rail still resolves through redirects. |
| 616B.1 | Matters family | NEW `$FA/settings/matters/page.tsx` + `_tabs/{templates,naming,custom-fields,tags,checklists,request-templates,request-settings}.tsx` ← `project-templates`, `project-naming`, `custom-fields`, `tags`, `checklists`, `request-templates`, `request-settings`; `git mv` sub-routes `project-templates/[id] → matters/templates/[id]`, `checklists/{new,[id],[id]/edit} → matters/checklists/…`, `request-templates/{new,[id]} → matters/request-templates/…`; DELETE 7 old leaf pages; `legacy-redirects.ts` += 7 tab rows + 6 route rows (`:id` segments) | `__tests__/settings/matters-family.test.tsx` | 616A.2 | Phase 84's future `take-on` tab lands here (#3) — leave a comment, no code. |
| 616B.2 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 616C.1 | Documents family | NEW `$FA/settings/documents/page.tsx` + `_tabs/{templates,clauses,acceptance,email}.tsx` ← `templates`, `clauses`, `acceptance`, `email`; `git mv templates/{new,[id]/edit} → documents/templates/…`; DELETE 4 old pages; `legacy-redirects.ts` += 4 tab + 2 route rows | `__tests__/settings/documents-family.test.tsx` | 616A.2 | — |
| 616C.2 | Fees & Trust family | NEW `$FA/settings/fees/page.tsx` + `_tabs/{tariffs,tax,time-recording,fee-runs,collections,trust-accounting}.tsx` ← `rates`, `tax`, `time-tracking`, `batch-billing`, `collections`, `trust-accounting` (`requiredModule` `bulk_billing` on fee-runs, `trust_accounting` on trust-accounting); DELETE 6 old pages; `legacy-redirects.ts` += 6 tab rows | `__tests__/settings/fees-family.test.tsx` | 616A.2 | — |
| 616C.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 616D.1 | Connections family | NEW `$FA/settings/connections/page.tsx` (default tab `xero`) + `_tabs/{xero,mcp,ai,notifications,packs,automations}.tsx` ← `integrations` + `integrations/xero` (both → `xero`), `integrations/mcp`, `ai`, `notifications`, `packs`, `automations`; `git mv integrations/xero/sync-log → connections/xero/sync-log`, `ai/history → connections/ai/history`, `automations/executions → connections/automations/executions`; DELETE 7 old leaf pages; `legacy-redirects.ts` += 7 tab rows (`integrations` and `integrations/xero` both → `connections?tab=xero`) + 3 route rows; remove the `pending` flag from `SETTINGS_FAMILIES` | `__tests__/settings/connections-family.test.tsx` | 616A.2 | After this slice `find $FA/settings -name page.tsx \| wc -l` must print **17** and `LEGACY_REDIRECTS` must hold **43** settings rows (32 tab + 11 route) — paste both into the PR body. |
| 616D.2 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 616E.1 | Rail + palette + Firm zone from one source | `components/settings/settings-sidebar.tsx` rewritten over `SETTINGS_FAMILIES` (families + active family's tabs; shared filtering helper with `nav-zone.tsx` for `adminOnly`/`requiredModule`/`requiredCapability`; `aria-current="page"` on the active family); DELETE `components/settings/settings-nav-groups.ts`; `lib/nav-items.ts`: DELETE `SETTINGS_ITEMS`; Firm zone Audit trail → `/settings/firm?tab=audit-trail`, Settings → `/settings/firm`; `components/command-palette-dialog.tsx` settings entries = `families × tabs` flattened to "Settings › Fees & Trust › Tariffs" with `href?tab=` (filtered by the same helper); `components/breadcrumbs.tsx` `SEGMENT_LABELS` += the five families, `NON_ROUTABLE_PREFIXES` re-derived; `settings/layout.tsx` unchanged unless the rail props change | `lib/__tests__/settings-nav.test.ts` extended: every family page exists on the filesystem; every **settings** row of `LEGACY_REDIRECTS` (sources under `/org/:slug/settings/`) resolves to a listed family+tab or kept route; `components/__tests__/settings-sidebar.test.tsx` rewritten; `command-palette-dialog.test.tsx` settings entries; `breadcrumbs-routes.test.ts`; `nav-items.test.ts` (28 items unchanged, hrefs updated) | `nav-zone.tsx` filter → extract `lib/nav-filter.ts` shared by both | Scoped to settings rows so 610D/612A/613D/615E rows cannot fail it. |
| 616E.2 | A11y | Shadcn `Tabs` roles (`tablist`/`tab`/`tabpanel`, arrow keys, `aria-selected`); first focusable control focused only on user-initiated tab change; admin-only tabs absent; Firm's 8-tab strip scrolls horizontally on narrow widths via the existing mobile pill row | a11y assertions in the family tests | §12.7.4 | — |
| 616E.3 | Slice gate | — | `pnpm lint && pnpm build && pnpm test` | — | — |
| 616F.1 | Playwright + scenario | `frontend/e2e/tests/settings/{custom-fields,data-protection-settings,general-settings,packs,rate-cards,roles-capabilities,tags,tax-settings}.spec.ts` → `/settings/<family>?tab=<id>` URLs + rail labels; `legal-lifecycle/day-00-firm-setup.spec.ts` settings navigation (`:326` carried — confirm by running whether the relabel resolves it; report, don't edit); `/settings/` 16 literals across legal specs; `qa/testplan/demos/legal-za-90day-keycloak.md` + `legal-za-full-lifecycle-keycloak.md` settings steps → family/tab wording | spec compile | ADR-344 | — |
| 616F.2 | FE-GATE + E2E-LEGAL + epic gate | — | **FE-GATE**; **E2E-LEGAL** + `pnpm test:e2e -- settings`; failing set == seven carried (or fewer — report which resolved and why, with run log) | — | `phase87/10-settings-five-families`. |

### Key Files

**Create:** `lib/settings-nav.ts`, `lib/nav-filter.ts`, `$FA/settings/{firm,matters,documents,fees,connections}/page.tsx` + `_tabs/*`, `lib/__tests__/settings-nav.test.ts`, `__tests__/settings/*-family.test.tsx`.
**Move:** 11 editor sub-routes (`matters/templates/[id]`, `matters/checklists/{new,[id],[id]/edit}`, `matters/request-templates/{new,[id]}`, `documents/templates/{new,[id]/edit}`, `connections/xero/sync-log`, `connections/ai/history`, `connections/automations/executions`).
**Delete:** 32 old leaf `page.tsx` (+ colocated `actions.ts` relocated), `components/settings/settings-nav-groups.ts`, `SETTINGS_ITEMS` in `lib/nav-items.ts`.
**Modify:** `$FA/settings/page.tsx`, `components/settings/settings-sidebar.tsx`, `lib/nav-items.ts`, `components/{command-palette-dialog,breadcrumbs}.tsx`, `lib/legacy-redirects.ts` (+43), tests, 8 settings specs, `day-00-firm-setup.spec.ts`, 2 scenario docs.

### Architecture Decisions
- **Five families; controls stay, pages go; ≤ 25 routes from 44 → 17** ([ADR-341](../adr/ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md)).
- **One nav source feeds rail, page tabs and palette — a tab cannot exist in one and not the other** (§12.7.3).
- **`?tab=` addressing; unknown tab → first permitted, never 404; permanent redirects for one release** (§12.7.4).
- **`billing` → Firm › Subscription; `compliance` → Firm › Compliance** (§12.7.1, #10, ADR-335).

### Non-scope
- Any control, form or table change inside a tab (relocation only). Phase 84 `take-on` tab. Portal settings. Removing redirects (next release).

---

## Epic 617: Portal Vocabulary + Nav, QA Capstone, Screenshots, Closure Report

**Goal**: Finish the portal's half of the shell — labels Home · Matters · Trust statement · Diary dates · Fee notes · Documents & requests · Activity, `acceptance` item dropped with Home's pending-acceptance rows linking to `/accept/{token}` under an "Engagement letters" heading, `/projects/**` → `/matters/**` route segments with the portal's first `next.config.ts` `redirects()`, `profiles` dead code removed, **no visual change** — then run the capstone: both legal-za scenario docs and `portal-client-90day` amended (incl. the 12 stale "Pipeline" refs), the 90-day legal-za lifecycle **observed** on the Keycloak stack through the new shell via Playwright MCP, both legal Playwright configs re-run on :3001, a screenshot set committed under `marketing/decks/` and referenced from `exec-deck.html`, and the closure report with route/settings/terminology deltas, evidence paths and the seven carried spec locations.

**References**: Architecture §12.8 (portal table, `/acceptance` gap, routes/types/API), §12.10.3 rows 1–2, 6–7, §12.10.6 (capstone row), §12.10.7 (capstone command), §12.12 S10, §12.14 #5/#7/#19; [ADR-344](../adr/ADR-344-qa-scenario-amendment-authorisation-phase87.md), [ADR-336](../adr/ADR-336-engagement-letter-document-plus-acceptance.md), [ADR-252](../adr/ADR-252-portal-slim-left-rail-nav.md) (untouched), [ADR-256](../adr/ADR-256-polymorphic-portal-deadline-view.md).

**Dependencies**: 616.

**Scope**: Portal (617A, 617B) + QA/Process (617C).

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **617A** | 617A.1–617A.3 | `nav-items.ts`, `portal-sidebar.tsx`, 2 route dirs `git mv`, `next.config.ts`, `home/page.tsx`, ~5 page headings, 2 tests | Portal labels, routes, redirect, acceptance gap; PORTAL-GATE. |
| **617B** | 617B.1–617B.2 | 4 + 1 + 1 portal specs, 3 scenario docs | Portal Playwright + scenario sweep; portal e2e run. |
| **617C** | 617C.1–617C.4 | Keycloak stack run, screenshots, `exec-deck.html`, closure report, `TASKS.md` | Observed capstone + evidence + closure. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 617A.1 | Nav labels + routes + redirect | `$P/lib/nav-items.ts` (§12.8 table: `projects` → `matters` id/label/`/matters`; `trust` label "Trust statement"; `deadlines` label "Diary dates" (path `/deadlines` kept — `deadline` is not one of the two nouns; `PortalDeadlineService.MODULE_ID = "deadlines"` stays); `invoices` → "Fee notes"; `requests` → "Documents & requests"; **`acceptance` dropped**; `profiles: ["legal-za"]` dead code removed); `$P/components/portal-sidebar.tsx` (direct label lookup since 611E); `git mv "$P/app/(authenticated)/projects" "$P/app/(authenticated)/matters"` (`page.tsx`, `[id]/page.tsx`); `$P/next.config.ts` first `redirects()` block `/projects/:path* → /matters/:path*` permanent; page headings "Your matters", "Fee notes", "Trust statement", "Diary dates", "Documents & requests" | `$P/lib/__tests__/nav-items.test.ts` (new labels, no `acceptance`, 7 items), `components/__tests__/portal-sidebar.test.tsx` | ADR-252 rail untouched | **No visual change**: same rail, cards, spacing. |
| 617A.2 | `/acceptance` gap | `$P/app/(authenticated)/home/page.tsx:124` link → each pending-acceptance row links to its own `/accept/{token}`; `components/pending-acceptances-list.tsx` heading "Engagement letters"; accepted-state wording "Engagement letter — mandate accepted on {date}" | `components/__tests__/pending-acceptances-list.test.tsx` | ADR-336 | No new `/acceptance` page (no-new-features phase). |
| 617A.3 | PORTAL-GATE | — | `cd portal && pnpm lint && pnpm format:check && pnpm test && pnpm build` | — | — |
| 617B.1 | Portal Playwright + scenario docs | `portal/e2e/tests/portal-client-90day/day-{00-07,14-30,45-75,85-90}.spec.ts` (4 — #19), `portal/e2e/tests/responsive/portal-pages.spec.ts`, `frontend/e2e/tests/portal/portal-data-isolation.spec.ts` — `/projects` → `/matters`, labels; `qa/testplan/demos/portal-client-90day-keycloak.md`; final sweep of `legal-za-90day-keycloak.md` + `legal-za-full-lifecycle-keycloak.md` for leftover old URLs/labels and the **12 stale "Pipeline" refs** (Phase 86 leftovers — removal is a URL/nav amendment, authorised) | spec compile | ADR-344 | Behaviour steps frozen. |
| 617B.2 | Portal e2e run | — | e2e stack: `cd portal && pnpm test:e2e:portal-client-90day` + responsive; `frontend` `pnpm test:e2e -- portal`; green (observed) | — | — |
| 617C.1 | Capstone — Keycloak stack walk | `bash compose/scripts/svc.sh start all` (3000/8080/8443/3002); walk `qa/testplan/demos/legal-za-90day-keycloak.md` end-to-end with **Playwright MCP** in the browser (never REST except Mailpit API; memory `feedback_qa_use_playwright_not_chrome_ext`); capture per-day evidence (logs, Mailpit message IDs, DB checks via API, screenshots) | PASS only when observed end-to-end (CLAUDE.md §3); any step that cannot be completed is `DEFERRED` with reason, never PASS-with-note | 606C capstone shape (`tasks/phase86-subtraction.md` §7) | AI-core walk stays DEFERRED (human-funded key). Hold `caffeinate` for the unattended parts (memory `env_macos_sleep_kills_overnight_agents`). |
| 617C.2 | Playwright configs on :3001 + carried set | `bash compose/scripts/e2e-down.sh && e2e-up.sh`; **E2E-LEGAL** both configs; portal + navigation + settings + projects + customers suites | failing set reported with file:line against the seven carried locations; any that now pass are reported as resolved-by-side-effect with the slice that did it | — | — |
| 617C.3 | Screenshot set + exec deck | screenshots of the new shell (home per audience, Diary week, Matters table, Clients table, matter detail Fees group, each settings family, portal Matters) → `marketing/decks/screens/phase87/*.png`; `marketing/decks/exec-deck.html` `<img>` refs updated | visual check in browser | §12.10.6 capstone row; ADR-344 §5 | Capture during the Keycloak run (real data), not the e2e stack. |
| 617C.4 | Closure report + TASKS.md | closure section appended to **this file** (`tasks/phase87-legal-native-shell.md`, "Closure report" below the checklist) with: route counts (frontend `page.tsx` 100 → N, `org/[slug]` 86 → N, settings 44 → **17**, portal 18 → 18), `NAV_GROUPS` 25 → 28 items, terminology files 73 + 10 → **0** (remnant scans), `/api/projects\|customers` literals → 0 outside `mcp/` (contract test log), `WireVocabularyContractTest`/`EntityTypeLiteralStabilityTest`/`McpToolContractTest` run logs, `tool-contract.json` unchanged since 607A, migrations tenant V3 / global V1 unchanged, redirect-table row count, seven carried locations file:line + status, capstone evidence paths, screenshot paths, deferred items (#1, #2, #6, #11, #13, #14, AI walk); `TASKS.md` Phase 87 rows → **Done** with PR numbers; `architecture/ARCHITECTURE.md` index line for Section 12 if not already present | — | `tasks/phase86-subtraction.md` §7 closure shape | Review pass required even though docs-only. `phase87/11-portal-capstone`. |

### Key Files

**Modify (portal):** `lib/nav-items.ts`, `components/portal-sidebar.tsx`, `components/pending-acceptances-list.tsx`, `app/(authenticated)/home/page.tsx`, `app/(authenticated)/projects/**→matters/**`, `next.config.ts`, page headings, `lib/__tests__/nav-items.test.ts`, `components/__tests__/portal-sidebar.test.tsx`, portal e2e specs.
**Modify (QA/docs):** `qa/testplan/demos/{legal-za-90day-keycloak,legal-za-full-lifecycle-keycloak,portal-client-90day-keycloak}.md`, `frontend/e2e/tests/portal/portal-data-isolation.spec.ts`, `marketing/decks/exec-deck.html` (+ `screens/phase87/`), `tasks/phase87-legal-native-shell.md` (closure), `TASKS.md`.

### Architecture Decisions
- **Portal: vocabulary and nav only; Phase 68 visuals untouched** (§12.8, ADR-252).
- **`/acceptance` dropped; Home rows link to `/accept/{token}`** (§12.8, #5, ADR-336).
- **Capstone is observed on the Keycloak stack via Playwright MCP; specs run on :3001 because the configs require it; AI walk stays DEFERRED** ([ADR-344](../adr/ADR-344-qa-scenario-amendment-authorisation-phase87.md) §5).
- **Carried failures are owned, not papered** (ADR-344 §4; CLAUDE.md §1/§9).

### Non-scope
- Fixing any of the seven carried spec locations (own one-fix PRs). New portal pages. Portal visual redesign. Deleting redirects.

---

## Closure checklist

S10 (617C.4) must produce a closure report containing, with evidence paths for every number:

1. **Baseline → after** (§12.1): frontend `page.tsx` 100 → N (86 under `org/[slug]` → N; settings **44 → 17**: 16 leaf + index); portal `page.tsx` 18 → 18; backend packages 62 → 63 (`diary/` added, none removed); `@RestController`s 121 → 122 (`DiaryController`); `@Entity` 103 (unchanged); audit catalogue **29** (unchanged, `AuditEventTypeRegistryTest`); capabilities 27 (unchanged); modules 8 (unchanged); `NAV_GROUPS` 6 zones / 25 items → 6 / **28** (3 + 5 + 3 + 6 + 7 + 4); settings nav sources 2 → 1 (`SETTINGS_FAMILIES`); tabs 31; detail tab groups 7/22 → 7/**21** (matter), 6/14 → 6/14 (client).
2. **Wire**: `WireVocabularyContractTest` run log (no scope list; zero offenders); 32 controllers that carried `/projects|/customers` → 0 outside `mcp/`; `grep -rE '"/api/(projects|customers)|"/portal/projects' backend/src/main/java` → only `mcp/` hits or none; `/api/calendar` still present and annotated `@Deprecated`.
3. **Stability pins**: `EntityTypeLiteralStabilityTest` green; `McpToolContractTest` green with `backend/src/test/resources/mcp/tool-contract.json` **unchanged since 607A** (`git log --oneline -- backend/src/test/resources/mcp/tool-contract.json` shows one commit); 21 tools + 3 resources.
4. **Terminology**: `no-terminology-remnants.test.ts` + portal twin green; `grep -rlE 'useTerminology|TerminologyText|TerminologyHeading|withIndefiniteArticle|auditTabLabel|PORTAL_TERMINOLOGY|terminology-map' frontend/{app,components,lib} portal/{app,components,lib} packages/shared/src` → **0** files (was 73 + 10 + 1); ESLint rule present in both `eslint.config.mjs`; `frontend/CLAUDE.md` rule present.
5. **Routes/redirects**: `LEGACY_REDIRECTS` row count (expected 1 existing + 2 routes + 1 profitability + 3 calendars + 1 expenses + **43** settings = 51); `no-legacy-hrefs.test.ts` green; sample `curl -I` 308s for `/projects/x`, `/customers/x`, `/calendar`, `/court-calendar?tab=prescription`, `/settings/general`, `/settings/project-templates/x`, portal `/projects`.
6. **Migrations**: `ls backend/src/main/resources/db/migration/{tenant,global} | sort -V | tail -1` → `V3__normalise_work_types.sql` / `V1__baseline.sql`; `BaselineSchemaParityTest` green; next free tenant **V4**, global **V2**.
7. **Verify evidence**: paths to the eleven BE-VERIFY / FE-GATE / PORTAL-GATE marker files (`.claude/markers/verify-*.json`) per PR; PR numbers #… for 607–617 (and 609's three if split); CodeRabbit status per PR (`REVIEWED`/`DEFERRED` with reason).
8. **Playwright**: both legal configs' run summaries on :3001 (passed / failed / skipped) before and after; the **seven carried locations** listed with file:line and status (`still failing — carried` or `resolved by <slice> — confirmed by run`): `legal-lifecycle/day-00-firm-setup.spec.ts:326`, `day-30-billing.spec.ts:32`, `:159`, `day-45-recon-prescription.spec.ts:41`, `day-90-review.spec.ts:61`, `:177`, `legal-depth-ii/day-05-disbursements.spec.ts:51`; the `project-expenses.spec.ts` decision (#18); portal suites' summaries (4 + 1 + 1 files, #19).
9. **Capstone**: per-day evidence table for `legal-za-90day-keycloak.md` on the Keycloak stack (step → PASS/DEFERRED, log path, Mailpit message id, screenshot path); AI-core walk DEFERRED with reason; `svc.sh status` output at start.
10. **Screenshots**: `marketing/decks/screens/phase87/*.png` list and the `exec-deck.html` lines that reference them.
11. **Open questions disposition**: table of §12.14 #1–#15 + breakdown-found #16–#19 with final state; follow-ups handed to the next phase (`/api/calendar` deletion, `ProjectUpcomingDeadlinesService` fold, profitability cost leg, `deadlines` module id, `/legal/*` prefix, per-matter trust balance, redirect removal next release, Phase 84 `take-on` tab under Matters).
12. **Lessons**: `tasks/lessons.md` entries for anything that surprised the build (e.g. a batch that needed splitting, a contract-test false positive).

---

## Closure report

_To be written by 617C.4._
