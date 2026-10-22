# R7 — Matter detail + closure cluster: root-cause dossier

Gaps: G-B9, G-B14, G-A6, G-D2, G-D3, G-D7 (all classified **Pre-existing** by the 617C attribution table, `qa_cycle/evidence/phase87-capstone/gaps.md:99-121`). Read-only archaeology on `main` @ `50b88ed5c`; nothing was run. Paths are repo-relative to `/Users/rakheendama/Projects/2026/b2b-strawman`.

Legend: **CONFIRMED** = read in source at the cited line. **HYPOTHESIS** = inferred, needs a reproduce/test.

---

## Q1 — G-B9: promoted fields inline on the matter header

### How "field promotion" actually works (CONFIRMED)

"Promotion" in this codebase means **a custom-field slug that became a structural column**, not "a pack field pinned to the header":

- `frontend/lib/constants/promoted-field-slugs.ts:45-50` — `PROMOTED_PROJECT_SLUGS = {reference_number, priority, engagement_type, matter_type}`; `:39-43` says `matter_type` maps to the structural `work_type` column (frontend `workType`).
- Backend mirror `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PromotedFieldSlugs.java:52-53` (same four). Used only to **filter** promoted slugs out of `CustomFieldSection` and the template variable picker (`:9-17`).
- The matter page filters them: `frontend/app/(app)/org/[slug]/matters/[id]/page.tsx:326` `projectFieldDefs = defs.filter(d => !PROMOTED_PROJECT_SLUGS.has(d.slug))`.
- There is **no** `promoted` / `pinned` / `showInHeader` attribute in the field-pack schema: `rg promot backend/src/main/resources/field-packs` → 0 hits. There is no `PromotedFieldsResolver`.

Why E.5 passed: the New Matter dialog has **no** `CustomFieldSection` at all (`frontend/components/projects/create-project-dialog.tsx` — `rg CustomFieldSection|customFields` → 0 hits). It renders the structural inputs `referenceNumber`, `priority`, `workType` directly (`:51-60` defaults, `:256-275` Work Type input). "Promoted slugs render as native inputs" is literally those three structural columns (`exit-checkpoints.md:13`).

### Why case_number / court_name are not inline (CONFIRMED)

- `case_number` (sortOrder 1) and `court_name` (sortOrder 2) are ordinary pack custom fields in `backend/src/main/resources/field-packs/legal-za-project.json:13-28` (group `legal_za_matter`, `autoApply: true`). They live in `projects.custom_fields` JSONB and reach the page as `project.customFields` (`page.tsx:476`).
- `MatterHeaderCard` props are only `projectId, projectName, projectStatus, workType, referenceNumber, closedAt, customers, slug, isAdmin` (`frontend/components/projects/matter-header-card.tsx:13-23`). It renders status badge, `workType` (`:53-55` — this is the "LITIGATION" the walk saw, i.e. `matter_type` **is** inline already), `referenceNumber` code chip (`:56-60`), client link (`:71-80`).
- `MatterDetailsTab` renders Description / Priority / Due Date / Created / Tags only (`frontend/components/projects/matter-details-tab.tsx:39-116`).
- Custom fields render only through `MatterFieldsTab` → `CustomFieldSection editable={canEdit}` (`frontend/components/projects/matter-fields-tab.tsx:66-71`) — i.e. the Details › Custom fields inputs the walker saw.

So the scenario line 5.2 ("promoted fields … matter_type, case_number, court_name", `qa/testplan/demos/legal-za-90day-keycloak.md:272`) conflates two things: `matter_type` is promoted (→ Work Type, inline since Phase 63); `case_number` / `court_name` were never promoted and no header-pinning mechanism exists. The gap is real as a product expectation (wow moment 4) but the mechanism the scenario assumes does not exist.

### Data already available at the header (CONFIRMED)

`page.tsx` already holds everything a header strip needs, server-side:
- values: `project.customFields` (`:476`, `Record<slug, unknown>` from `GET /api/projects/{id}` → `MatterResponse`),
- labels/types: `projectFieldDefs` + `projectFieldGroups` (`:317-343`, `getFieldDefinitions("PROJECT")`), already filtered of promoted slugs.
No new endpoint is needed.

### Client header — same question (CONFIRMED, consistent)

- `ClientHeaderCard` props (`frontend/components/customers/client-header-card.tsx:52-77`) carry no custom-field values; it renders name, status/lifecycle badges, email/phone (`:136`), context line (`:144`).
- Client "promoted" slugs are likewise structural (`promoted-field-slugs.ts:11-35`; `clients/[id]/page.tsx:461-473` only builds `promotedFieldValues` for visibility-condition evaluation).
- `id_passport_number` is a plain pack field (`field-packs/legal-za-customer.json:14`), shown in the New Client Step 2 "Additional Information" (capstone `day-00.md:62`), never in the header.

Verdict: the two headers are **consistent with each other** (neither renders pack fields) and both miss the legal "header facts".

### Where a "promoted fields strip" goes — fix shapes

**Option A (recommended, small):** frontend-only "header facts" strip.
- New constant next to the promoted sets, e.g. `frontend/lib/constants/header-field-slugs.ts`: `HEADER_FIELD_SLUGS.PROJECT = ["case_number", "court_name"]`, `HEADER_FIELD_SLUGS.CUSTOMER = ["id_passport_number"]` (legal-za is the only profile since Phase 86, so a constant is honest).
- `page.tsx` computes `headerFields = HEADER_FIELD_SLUGS.PROJECT.map(slug => ({ label: def?.name, value: project.customFields[slug] })).filter(v => v.value)` from the defs it already fetched; pass `headerFields` to `MatterHeaderCard`; render as a `dl`-style strip under the badge row (`matter-header-card.tsx` after `:69`), `data-testid="matter-header-fields"`. Same shape for `ClientHeaderCardWithLifecycle` with `id_passport_number`.
- Needs the unfiltered defs for labels: today `projectFieldDefs` is post-filter but `case_number`/`court_name` are not promoted, so they are still in the list — fine.

**Option B (elegant, pack-driven):** add `"headerDisplay": true` (or `"pinned"`) to the field-pack JSON schema + `FieldDefinition` column + `FieldDefinitionResponse`, and have the header render every applied def with the flag. Needs a V-next-free migration and the pack seeder/upgrader touch (`resources/field-packs` has 0 Phase 87 commits; pack `version` would bump 2→3). Better long-term, larger blast radius.

**Option C (not recommended):** promote `case_number`/`court_name` to structural columns — contradicts the Phase 63 rationale (promotion was for cross-vertical columns) and changes the API.

---

## Q2 — G-B14: `?tab=` pins the panel

**CONFIRMED** `frontend/components/matters/matter-tabs.tsx`:
- `:117-121` `const tabParam = aliasLegacyTabId(searchParams.get("tab")); const urlTab = …; const [userTab, setUserTab] = useState(null); … const requestedTab = urlTab ?? userTab;`
- `:199-201` `handleTabChange = (tabId) => setUserTab(tabId)` — never touches the URL. With `?tab=tasks` in the URL, `urlTab` is always non-null so `userTab` is dead; the group label changes because `GroupedTabBar` tracks its own open group, but `TabsPrimitive.Root value={activeTab}` (`:204`) stays on the URL tab. Exactly the walk's symptom.

**Client tabs do NOT have the bug (CONFIRMED):** `frontend/components/customers/customer-grouped-tabs.tsx:60-61` takes `useRouter()`, `:130-134` resolves the active tab from the URL as the single source of truth (`resolveTabFromUrl`, `lib/constants/tab-group-types.ts:54`), and `:138-142` `handleTabChange` does `params.set("tab", tabId); router.replace(\`?${params}\`)`. That pattern landed in Epic 558 (`bbd8f830e`, 2026-05-30); the matter tabs were never brought in line.

**Fix (recommended):** port the client pattern into `matter-tabs.tsx` — drop `userTab`, add `useRouter`, `handleTabChange` → `router.replace` with the param set (preserves other params; `replace` not `push` so Back leaves the page, matching the client tabs). Optionally reuse `resolveTabFromUrl` instead of the hand-rolled `validTabIds` set (`:60-82`) — the set is pinned by `__tests__/components/matters/matter-tabs.test.tsx:228-255` ("validTabIds == constants"), so keep the set or move the pin.

Alternative (not recommended): `userTab ?? urlTab` — fixes the click but leaves the URL stale, so a refresh/deep-link snaps back; also notification links (`?tab=tasks`) would then be overridden by a stale `userTab` only after a click, which is fine, but the URL/UI divergence is a worse state than the client pattern.

Test hook already exists: the matter-tabs test file mocks `useRouter: () => ({ replace: mockReplace })` (`matter-tabs.test.tsx:12-13`) even though the component never calls it — add "clicking Fees › Time with `?tab=tasks` set calls `replace` with `tab=time` and shows the time panel".

---

## Q3 — G-A6: "Conflict Check — Coming Soon" card

**CONFIRMED** `frontend/components/projects/create-project-dialog.tsx:278-294`: `<ModuleGate module="conflict_check">` → card with `<Badge variant="neutral">Coming Soon</Badge>` and a `disabled` "Run Conflict Check" button. Nothing is wired to it.

**Original intent (CONFIRMED via git):** `git log -S "Coming Soon"` → `b4ebd5eeb` 2026-03-19 "feat(epic-371A): legal stub pages, conditional sections & frontend tests (#764)". The same commit created the stub `app/(app)/org/[slug]/conflict-check/page.tsx`. The real module landed two weeks later — Epic 401 service (`d91851461`, 2026-04-01) and Epic 406 frontend pages (`216da9c37`, 2026-04-01) — and nobody returned to the placeholder. It is a forgotten stub, not a design decision.

**Live API (CONFIRMED):**
- `POST /api/conflict-checks` (`backend/…/verticals/legal/conflictcheck/ConflictCheckController.java:33-35`), also `GET`, `GET /{id}`, `POST /{id}/resolve` (`:41-62`).
- Request `PerformConflictCheckRequest(checkedName*, checkedIdNumber, checkedRegistrationNumber, checkType* ∈ NEW_CLIENT|NEW_MATTER|PERIODIC_REVIEW|LATERAL_HIRE|RELATED_PARTY, clientId, matterId)` (`ConflictCheckService.java:64-75`); response carries `result`, `conflictsFound[]` with adverse-party / customer / project matches (`:79-110`).
- Matching: exact ID / registration number against adverse parties and customers, then name similarity; when `clientId` is given, that customer's own rows are excluded so the subject does not self-match (GAP-L-28, `ConflictCheckService.java:120-150`).
- Frontend action `performConflictCheck()` (`app/(app)/org/[slug]/conflict-check/actions.ts:38-63`); form fields Name to Check*, ID Number, Registration Number, Check Type*, Client (optional), Matter (optional) (`components/legal/conflict-check-form.tsx:117-214`); result component `components/legal/conflict-check-result.tsx`.

**What the dialog has to offer the check:** `name, description, dueDate, customerId, referenceNumber, priority, workType` (`create-project-dialog.tsx:51-60`). There is **no opposing-party input** — `opposing_party` is a pack custom field (`legal-za-project.json:29-36`) and the dialog renders no custom fields. So the only thing the card can check today is the chosen client (and with `clientId` set, its own customer rows are excluded, leaving adverse-party and other-client matches — that is a meaningful "is our prospective client someone else's adverse party?" check).

**Fix shapes:**
- **A — wire it (minimal useful):** enable the button when `customerId` is selected; call `performConflictCheck({ checkedName: customer.name, checkType: "NEW_MATTER", clientId })`; render the result inline (reuse `ConflictCheckResult`); on `CONFLICT`/`POTENTIAL` show a warning and keep Create enabled (blocking is a product call). Drop the badge.
- **A+ — add an "Opposing party" input** to the dialog that (i) is the `checkedName` for the check and (ii) is written to `customFields.opposing_party` on create (needs `createProject` action to accept `customFields`, check `matters/actions.ts`). This is the check a litigator actually wants at intake. Larger scope; touches the create action and backend `CreateProjectRequest` if `customFields` is not already accepted.
- **B — link out:** replace the card with "Run a conflict check first →" linking to `/org/{slug}/conflict-check?name=<client name>` and have the page prefill from the query (small change in `conflict-check-client.tsx`). Zero new UI surface in the dialog.
- **C — remove the card.** Cheapest; loses the "conflict check at intake" prompt that the 371A design wanted.

Recommend B now (demo-safe, one afternoon) with A+ as the real feature once the founder answers "check whom?".

---

## Q4 — G-D2: COMPLETED matter has no Close Matter

**CONFIRMED** three-way mismatch:
- `frontend/components/projects/matter-header-card.tsx:88` `{projectStatus === "ACTIVE" && <MatterClosureAction …/>}`.
- `frontend/components/projects/matter-closure-action.tsx:29` self-gates `projectStatus !== "ACTIVE" && projectStatus !== "COMPLETED" → null` (so it was written to be shown for COMPLETED).
- Backend `backend/…/project/ProjectStatus.java:22` `COMPLETED → {ARCHIVED, ACTIVE, CLOSED}`; `Project.closeMatter` javadoc "Valid from ACTIVE or COMPLETED" (`Project.java:292-306`); `MatterClosureService.performClose` relies on the entity transition (`MatterClosureService.java:242-244`).
- For COMPLETED the header shows `ProjectLifecycleActions` "Archive" + overflow "Reopen Matter" (`project-lifecycle-actions.tsx:84-111`) — the walk's screenshot.

**One-line fix:** `matter-header-card.tsx:88` → `{(projectStatus === "ACTIVE" || projectStatus === "COMPLETED") && (` — or drop the outer guard entirely since the child self-gates (`matter-closure-action.tsx:28-30`). Keep the `MatterReopenAction` guard (`:96`, child also self-gates at `matter-reopen-action.tsx:29`).

**MatterSidebar (CONFIRMED not mounted):** `page.tsx` references `MatterSidebar` only in a TODO comment (`:117`); no import/JSX; `MatterDetailLayout` is not mounted either. `matter-sidebar.tsx:209-221` carries `data-testid="sidebar-lifecycle-action"` with the same ACTIVE-only guard (`:210-212`). It is dead UI since the header-card move (615E recorded "no matter-team UI since Epic 599" as D-6; same orphan family as G-B3). Either mirror the fix there or delete the component + `__tests__/components/projects/matter-sidebar.test.tsx` (pins the testid at `:147`) + `matter-detail-layout.*` — scope decision, not for this PR.

**Does the testid matter for specs?** No executable spec uses `sidebar-lifecycle-action`:
- Playwright uses `close-matter-btn` (`frontend/e2e/helpers/legal-depth-ii.ts:267`, `e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts:226`).
- Only scenario prose is stale: `qa/testplan/demos/legal-za-90day-keycloak.md:493` (75.2) and `legal-za-full-lifecycle-keycloak.md:812` (60.16) say `sidebar-lifecycle-action`, while the same docs' selector tables (`full-lifecycle:42`) and `qa/testplan/MIGRATION-NOTES.md:16,92-94` already say `header-lifecycle-actions`. The 2026-10-19 audit rows 75.2 / 60.16 are class D "update selector" (`qa/testplan/legal-testplan-audit-2026-10-19.md:72,110`). Amending the two lines needs explicit authorisation (quality gate rule 6); the evidence is `matter-header-card.tsx:87` + `matter-closure-action.tsx:38`.

---

## Q5 — G-D3: retention "isn't configured" after closure

### What closure writes (CONFIRMED)

1. `Project.closeMatter` (`backend/…/project/Project.java:300-307`): status CLOSED, `closedAt = now`, and `retentionClockStartedAt = closedAt` **only if null**.
   - But `Project.complete` **also** stamps `retentionClockStartedAt` on first completion (`Project.java:253-265`, comment: "When Phase 67 introduces CLOSED, move this assignment to close(...) — see ADR-249" — never moved; `RetentionClockIntegrationTest.retentionClockStartedAt_setsOnFirstCompletion` pins it, `project/RetentionClockIntegrationTest.java:66-86`).
   - `reopenMatter` preserves the clock (`Project.java:313-323`). Hence the walk's anchor = the earlier Complete at 16:34:39, not the Close at 16:37:28.
2. A **firm-wide** `retention_policies` row via `insertIfAbsent("MATTER", years*365, "MATTER_CLOSED", "ARCHIVE")` (`MatterClosureService.java:308-316`, `:346-363`). The Phase 50 table has no `entity_id` / `retention_start` columns and is `UNIQUE (record_type, trigger_event)` (`backend/src/main/resources/db/migration/tenant/V1__baseline.sql:534-547`; entity `retention/RetentionPolicy.java:18-45`). This is the "1825 days" row the walker saw; it is a policy, not a per-matter date.
3. `retentionEndsAt = closedAt + effectiveYears` is **computed and returned only** in `CloseMatterResponse` (`MatterClosureService.java:319-326`, `dto/CloseMatterResponse.java:11`) — never persisted.

### What the card reads (CONFIRMED)

- `GET /api/projects/{id}` → `ProjectController.java:88` `computeRetentionEndsOn` → `ProjectService.java:153-170`: returns null unless CLOSED **and** `orgSettingsService.getRawLegalMatterRetentionYears()` is present; it deliberately does **not** fall back to the 5-year default ("so the UI can distinguish configured from unconfigured", `:143-147`).
- `OrgSettingsService.getRawLegalMatterRetentionYears` (`settings/OrgSettingsService.java:391-396`) → `Optional.empty()` when the column is null.
- `RetentionCard` State B "isn't configured yet" when `retentionEndsOn == null` (`frontend/components/legal/retention-card.tsx:97-123`); `OverviewTab` passes `project.retentionClockStartedAt/retentionEndsOn` (`page.tsx:495-496`).

### Why the column is null and cannot be set (CONFIRMED)

- Schema default exists: `org_settings.legal_matter_retention_years integer DEFAULT 5` (`V1__baseline.sql:452`, CHECK ≥1 `:464`).
- But the row is created through the entity constructor with explicit values and **no** retention-years argument (`OrgSettingsService.java:120-141` — the data-protection block passes `null` jurisdiction, `false`, `null`, `null`, officer nulls); Hibernate inserts `legal_matter_retention_years = NULL`, bypassing the DB default. `OrgSettingsEmbeddableNullReloadTest:112` even exercises the NULL reload.
- Closure side uses the **effective** value (`DataProtectionSettings.getEffectiveLegalMatterRetentionYears`, `settings/DataProtectionSettings.java:127-131`, default 5 at `:38`) → closure and the read endpoint disagree on the same tenant. That is the whole contradiction.
- No writer: `setLegalMatterRetentionYears` (`DataProtectionSettings.java:133+`) has **zero callers**; `DataProtectionSettingsRequest` has only `dataProtectionJurisdiction` + `informationOfficerEmail` (`settings/dto/DataProtectionSettingsRequest.java:11-22`); `SettingsResponse` does not expose it (`settings/dto/SettingsResponse.java:42-47`).
- No UI: the Data Protection tab renders Jurisdiction, Information Officer, `RetentionPoliciesTable` (the firm-wide `retention_policies` rows), Processing register, PAIA (`app/(app)/org/[slug]/settings/firm/_tabs/data-protection.tsx:4-9`). The card's deep link `/org/{slug}/settings/data-protection` (`retention-card.tsx:115`) resolves through `lib/legacy-redirects.ts:96-97` to `/settings/firm?tab=data-protection` — a page with no years field. The FIXME's diagnosis (`day-75-85-closure.spec.ts:297-306`) is accurate.

### What ADR-249 intended (CONFIRMED, `adr/ADR-249-retention-clock-starts-on-closure.md`)

- Anchor = **closure**, explicitly rejecting completion (Option 3: "each completion restarts the clock, which is wrong … Completion is not a legal act").
- Consequences: a **per-matter** `RetentionPolicy` row (`entityType = PROJECT, entityId, retentionStart = closedAt, retentionYears = orgSettings.legalMatterRetentionYears ?? 5`), soft-cancelled on reopen, **fresh row with a new anchor on re-close**; `OrgSettings.legalMatterRetentionYears` defaults to 5 under legal-za and is per-org configurable. Phase 67 arch repeats it (`architecture/phase67-legal-depth-ii.md:428, 447, 710-711, 1229`).
- Reality: the Phase 50 table is firm-wide policy, so 489C / GAP-L-96 seeded a firm-wide row and moved the per-matter anchor to `projects.retention_clock_started_at` — but left completion stamping it and left the setting unreachable.

### Coherent model (proposal)

Keep the Phase 50 table as firm policy; make the **matter** carry its own obligation; make the **setting** real.

1. **Settings (root fix for the contradiction):** expose `legalMatterRetentionYears` in `DataProtectionSettingsRequest` / `SettingsResponse` / `OrgSettingsService.updateDataProtection*` and add a "Matter retention period (years)" field to `data-protection.tsx` (next to Information Officer). Seed `5` on org-settings creation for legal-za (`OrgSettingsService.java:120-141`) **or** make the read side use the effective value and label it "(firm default)". Either way closure and read agree. `OrgSettingsSchemaSnapshotTest` is snapshot-pinned (memory: "OrgSettings → 10 @Embeddables … snapshot-pinned") — a DTO change will need the snapshot updated deliberately.
2. **Per-matter end date:** two options.
   - **2a compute-on-read (no schema change):** `computeRetentionEndsOn` uses the same effective years as closure; `retentionEndsOn = clock + years`. End date floats if the firm later changes the policy — arguably correct for a retention *policy*. Smallest PR; fixes the walk's 75.10 once the setting exists.
   - **2b stamp at close (ADR-249 literal):** add `projects.retention_ends_on date` (V-next-free; never chase planned numbers), set in `closeMatter` from `closedAt + years`, cleared on reopen, re-stamped on re-close; `MatterResponse.retentionEndsOn` reads the column. Frozen per-matter obligation, survives policy changes, auditable. Needs the reopen soft-cancel semantics and a `RetentionClockIntegrationTest` rewrite.
3. **Anchor:** move the clock stamp out of `Project.complete` into `closeMatter` only (ADR-249 as written), and decide whether re-close resets it (ADR says yes, entity javadoc says "earliest"). Product call (see questions). `RetentionClockIntegrationTest:67,86` pins the current completion behaviour and would change.
4. **Card copy:** the "5 years by default; your firm's setting overrides" sentences (`retention-card.tsx:67-68, 90`) become true once (1) ships.

---

## Q6 — G-D7: Admin is offered the override

**CONFIRMED** chain:
- Backend: `OVERRIDE_MATTER_CLOSURE` is in `Capability.OWNER_ONLY` with `MANAGE_COMPLIANCE_DESTRUCTIVE`, `FINANCIAL_RECONCILE` (`backend/…/orgrole/Capability.java:33-34, 67-71`); `OrgRoleService.resolveCapabilities` gives admin `ALL_NAMES − OWNER_ONLY` (`orgrole/OrgRoleService.java:70-75`), owner gets all (`:67-69`); `GET /api/me/capabilities` returns that resolved set plus `isAdmin/isOwner` flags (`:99-112`). `MatterClosureService.close` calls `requireCapability("OVERRIDE_MATTER_CLOSURE")` before running gates (`MatterClosureService.java:175-178`) → the 403 toast.
- Frontend: `canOverride = hasCapability("OVERRIDE_MATTER_CLOSURE")` (`frontend/components/legal/matter-closure-dialog.tsx:70-71`); `hasCapability = (cap) => isAdmin || isOwner || capSet.has(cap)` (`frontend/lib/capabilities.tsx:119-121`) — the comment calls it "defense-in-depth", but it widens, not narrows, and it does not know about owner-only capabilities. Pinned by `__tests__/capabilities.test.tsx:92-108` ("isAdmin bypasses capability check — hasCapability always true").
- Step routing: Continue → `(!anyFailing || canOverride) ? step 2 : step 3` (`matter-closure-dialog.tsx:213-219`); override toggle `anyFailing && canOverride` (`:328`); Step 3 "Cannot close — resolve gates" (`:417-427`).
- **Second site with the same bug:** the server action `app/(app)/org/[slug]/matters/[id]/matter-closure-actions.ts:51-53` `canOverride = caps.isOwner || caps.isAdmin || caps.capabilities.includes("OVERRIDE_MATTER_CLOSURE")` — lets Bob through to the backend 403 instead of returning the local "forbidden" result.
- The repo already has the correct idiom for owner-only caps: `caps.isOwner || caps.capabilities.includes("FINANCIAL_RECONCILE")` (`app/(app)/org/[slug]/invoices/[id]/page.tsx:145`, `settings/connections/xero/sync-log/page.tsx:27`).

**Fix shapes:**
- **A — local (two lines):** dialog `canOverride = isOwner || capabilities.has("OVERRIDE_MATTER_CLOSURE")`; server action drop `caps.isAdmin` from the override line. Leaves the class open (`MANAGE_COMPLIANCE_DESTRUCTIVE` has no frontend consumer today, so no other live symptom).
- **B — root (recommended):** teach `hasCapability` about owner-only caps: mirror `Capability.OWNER_ONLY` as `OWNER_ONLY_CAPABILITIES` in `lib/capabilities.tsx` (3 names, kept in sync like `promoted-field-slugs`), `hasCapability = isOwner || (isAdmin && !OWNER_ONLY.has(cap)) || capSet.has(cap)`; fix the server action the same way; add a test case to `capabilities.test.tsx`. Keeps the flicker-avoidance the comment wants.
- **C — trust the server:** `hasCapability = capSet.has(cap)` (the backend already resolves everything for admin/owner, `:119` comment). Simplest and most honest; `capabilities.test.tsx:103` and `capability-gating.test.tsx` construct providers with `capabilities={[]} isAdmin` and would need rewriting; any layout that mounts `CapabilityProvider` with an empty list for admins would regress — check `app/(app)/org/[slug]/layout.tsx` before choosing.

With A or B, Bob's Continue lands on Step 3 and `data-testid="matter-closure-override-toggle"` is absent — exactly the FIXME'd spec (`day-75-85-closure.spec.ts:319-345`) and the existing unit test shape (`__tests__/legal/matter-closure-dialog.test.tsx:207-233` covers a non-admin; add `isAdmin: true, capabilities: ["CLOSE_MATTER"]`).

---

## Root causes (one line each)

| Gap | Root cause | Class |
|---|---|---|
| G-B9 | "Promotion" means structural columns; no header-pinning mechanism exists for pack fields; header card has no custom-field props. Scenario assumes a feature that was never built. | Missing feature + scenario wording |
| G-B14 | `matter-tabs.tsx:121` `urlTab ?? userTab` and click never writes the URL; client tabs fixed this in Epic 558, matter tabs not ported. | Bug |
| G-A6 | 371A placeholder card (2026-03-19) never revisited after Epics 401/406 shipped the module (2026-04-01). | Stale stub |
| G-D2 | Header guard `=== "ACTIVE"` narrower than the component and the backend transition table. | Bug (one line) |
| G-D3 | Read side uses raw setting (no fallback) while closure uses effective default; setting is NULL on insert and has no writer/UI; per-matter end date is never persisted; completion stamps the clock contrary to ADR-249. | Design drift + missing settings surface |
| G-D7 | `hasCapability` admin short-circuit ignores `Capability.OWNER_ONLY`; duplicated in the server action. | Bug (capability model mismatch) |

## Recommended PR shapes (one fix per PR, rule 7)

1. **G-D2** — `matter-header-card.tsx:88` guard; vitest for header card COMPLETED; (optional separate PR) delete dead `MatterSidebar`/`MatterDetailLayout`.
2. **G-B14** — port `router.replace` pattern into `matter-tabs.tsx`; vitest with `mockReplace`; Playwright: open `?tab=tasks`, click Fees › Time recording, assert URL + panel.
3. **G-D7** — Option B in `lib/capabilities.tsx` + `matter-closure-actions.ts`; un-fixme `day-75-85-closure.spec.ts:319`.
4. **G-D3 (a)** — settings field + DTO + effective-years on read (snapshot test update); un-fixme `:297`. **G-D3 (b)** — anchor/persist decision as a follow-up after the founder answers.
5. **G-A6** — Option B link-out (or A once "check whom" is decided).
6. **G-B9** — Option A header strip (matter + client in one PR is a same-class cluster; needs explicit authorisation).

## Related gaps

- G-B3 / 615E D-6 — the unmounted `MatterSidebar` family (same orphan as the `sidebar-lifecycle-action` testid).
- G-D8, G-D14 — closure events/emails (`verticals/legal/closure`, same service as G-D3).
- G-C2 — `link-adverse-party-dialog` `fetchProjects()` bug; relevant if G-A6 grows an opposing-party input.
- 611A.2 (Mandate badge, needs backend `templateCategory`) — same header card.
- G-D11/G-D13 `LEAD`/`lead` — the matter page computes `canEdit` from `project.matterRole === "lead"` (`page.tsx:114-116`); if the header strip needs edit affordances, the same case mismatch bites.

## Product questions for the founder

1. **G-B9:** Which fields are "header facts" for a legal matter — `case_number` + `court_name` only, or also `opposing_party` / `date_of_instruction`? Hard-coded legal-za list (fast) or pack-declared flag (durable)? Same for the client header: show `id_passport_number`?
2. **G-A6:** Conflict check at matter creation — check the *client* (what the dialog can do today) or the *opposing party* (needs a new input)? Should a CONFLICT result block "Create Matter" or only warn? Or remove the card and rely on the Day-1 Conflict check page?
3. **G-D2:** Should COMPLETED matters offer Close Matter (backend says yes)? Longer term, is COMPLETED a state a law firm needs at all alongside CLOSED (the walk had to Reopen a completed matter to close it)?
4. **G-D3:** Should the retention end date be frozen at close (ADR-249 literal, schema change) or computed from the current policy (no schema change)? Should completion still start the clock (today yes; ADR says no)? Should re-close reset the anchor (ADR yes; entity "earliest")? Confirm 5 years as the seeded legal-za default and that it is firm-editable.
5. **G-D7:** Confirm override stays owner-only (`Capability.java:34` "owner-only by default") and the UI should match — versus widening Admin.
6. **Scenario wording to authorise:** 90-day `:272` (5.2 "promoted fields" → "header fields"), `:493` and full-lifecycle `:812` (`sidebar-lifecycle-action` → `header-lifecycle-actions` / `close-matter-btn`).

## Tests to run / touch

Frontend (full, not narrowed; plus `pnpm format:check`):
- `__tests__/components/matters/matter-tabs.test.tsx`, `__tests__/components/customers/customer-grouped-tabs.test.tsx`, `__tests__/components/projects/grouped-tab-bar.test.tsx`, `__tests__/lib/constants/matter-tab-groups.test.ts`
- `__tests__/legal/matter-closure-dialog.test.tsx`, `__tests__/capabilities.test.tsx` (`:92` pin changes under option B/C), `__tests__/capability-gating.test.tsx`, `__tests__/customers/customer-tabs-audit-capability.test.tsx`
- `components/legal/__tests__/retention-card.test.tsx`, `__tests__/components/projects/kpi-dashboard.test.tsx` (uses `retention-card` testid), `__tests__/compliance/RetentionPolicyTable.test.tsx`, `__tests__/data-protection/settings-sections.test.tsx`
- `__tests__/components/projects/create-project-dialog.test.tsx`, `__tests__/components/projects/matter-sidebar.test.tsx` (`:147` if sidebar is touched)
- Playwright (worktree, `PLAYWRIGHT_BASE_URL=:3001`, e2e-down/up for gate grade): `e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts` (un-fixme `:297`, `:319`), `e2e/tests/settings/data-protection-settings.spec.ts`, matter detail/tab specs under `e2e/tests/legal*`.

Backend (full `./mvnw verify` via `backend/scripts/verify.sh`; inner loop):
- `verticals/legal/closure/MatterClosureServiceIntegrationTest` (`:193` override-without-capability, `:258` retentionEndsAt, `:709/:740` MATTER policy seed), `MatterClosureControllerIntegrationTest`, `MatterClosureNotificationHandlerTest`
- `project/RetentionClockIntegrationTest`, `project/ProjectStatusTest`, `portal/PortalProjectControllerTest`
- `orgrole/OrgRoleServiceTest`, `CapabilityEndpointTest`, `CapabilityAuthorizationTest`, `CapabilityRegistrationTest`
- `settings/OrgSettingsSchemaSnapshotTest` (snapshot-pinned DTO), `OrgSettingsEmbeddableNullReloadTest` (`:112`), `OrgSettingsIntegrationTest`, `OrgSettingsTest`
- `verticals/legal/conflictcheck/ConflictCheckServiceTest`, `ConflictCheckControllerTest` (if G-A6 option A/A+)
