# Day 3 — Bob creates RAF matter, sends FICA Onboarding Pack info request
Cycle: 1 | Date: 2026-04-21 | Auth: Keycloak | Frontend: :3000 | Actor: Bob Ndlovu (Admin, no context swap)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 3 (checkpoints 3.1–3.14).

**Result summary (Day 3): 14/14 checkpoints executed — 5 PASS (3.1, 3.2 partial, 3.3 partial, 3.4, 3.7), 3 FAIL (3.5, 3.6, 3.13), 1 PARTIAL (3.3 custom-field save blocked by PROSPECT gate), 5 BLOCKED (3.8, 3.9, 3.10, 3.11, 3.12, 3.14 — halt at 3.8 per rules).** First BLOCKER reached at **3.8 — FICA Onboarding Pack template missing**. Stopped subsequent checkpoints per QA cycle rules.

New gaps: **GAP-L-33** (HIGH, backend/product — FICA Onboarding Pack request template missing from legal-za request-packs; only 5 accounting-focused templates exist), **GAP-L-34** (HIGH, backend/frontend — no firm-side UI or API path to create a portal contact; `/api/customers/{id}/portal-contacts` exposes GET only, create-request-dialog has no "Add Portal Contact" action, PortalContactService.createContact is only invoked by DevPortalController magic-link generator), **GAP-L-35** (MED, backend — matter save blocks custom-field updates on PROSPECT lifecycle with error "Cannot create project for customer in PROSPECT lifecycle status" AFTER the matter was already inserted, leaving Court/Opposing Party fields unsaved; scenario doesn't transition Sipho out of PROSPECT until Day 7), **GAP-L-36** (MED, backend — "Litigation — Road Accident Fund" matter-type template missing; only 5 generic legal templates shipped per Phase 64/66; scenario specifies RAF-specific template), **GAP-L-37** (LOW, frontend — matter detail Field Groups auto-attaches SA Conveyancing — Matter Details to a litigation matter regardless of template; over-broad field-group attachment), **GAP-L-38** (LOW, frontend — matter sidebar tabs mismatch scenario: no dedicated "Tasks" or "Fee Notes" or "Audit" tab; tabs are Overview/Documents/Members/Clients/Action Items/Time/Disbursements/Fee Estimate/Financials/Staffing/Rates/Generated Docs/Requests/Client Comments/Court Dates/Adverse Parties/Trust/Disbursements/Statements/Activity — 20 tabs, Disbursements appears twice), **GAP-L-39** (LOW, frontend — customer_id URL query param `?customerId=<id>` when clicking "+ New Matter" from client detail is lost by the "New from Template" dialog Configure step: Client dropdown defaults to "None" even though URL carries a customer ID).

## Session prep

Bob was logged in at end of Day 2; session held cleanly into Day 3 (no re-auth required). Sidebar shows "BN / Bob Ndlovu / bob@mathebula-test.local".

## Checkpoint 3.1 — Navigate to Sipho's client detail → click "+ New Matter"
- Result: **PASS**
- Evidence:
  - On `/customers/8fe5eea2-75fc-4df2-b4d0-267486df68bd` → Matters tab (default) → two CTAs present: "New Matter" link (navigates to `/projects?new=1&customerId=<id>`) and "Link Matter" button (for linking existing).
  - Clicked "New Matter" → navigated to `/projects?new=1&customerId=8fe5eea2-75fc-4df2-b4d0-267486df68bd` → **"New from Template — Select Template"** dialog auto-opened.

## Checkpoint 3.2 — Legal-specific matter-type template selector
- Result: **PASS (with scope gap)**
- Evidence:
  - Template dialog lists 5 legal matter templates (from Phase 64/66 matter templates):
    1. Collections (Debt Recovery) — 9 tasks
    2. Commercial (Corporate & Contract) — 9 tasks
    3. Deceased Estate Administration — 9 tasks
    4. Litigation (Personal Injury / General) — 9 tasks
    5. Property Transfer (Conveyancing) — 12 tasks
  - **Scenario-specified "Litigation — Road Accident Fund" template is NOT present** — nearest match is generic Litigation (Personal Injury / General). Selected that as workaround.
  - Confirmed in DB: `SELECT count(*) FROM tenant_5039f2d497cf.project_templates` (all names listed above, no RAF-specific one).
  - Logged **GAP-L-36** (MED).

## Checkpoint 3.3 — Fill matter fields
- Result: **PASS on core fields / PARTIAL on Court+Case Number save**
- Evidence:
  - Configure step fields filled:
    - Matter name: `Dlamini v Road Accident Fund`
    - Client (select): `Sipho Dlamini` (manually re-selected; dropdown defaulted to "None" despite `customerId=` query param on the URL — logged as GAP-L-39)
    - Matter lead (select): `Bob Ndlovu`
    - Reference Number: `RAF-2026-001`
    - Work Type: `Litigation — Road Accident Fund`
  - Configure form does NOT expose Court / Case Number / Accident Date / Insurer fields at create-time — these are custom fields that appear on the detail page post-create.
  - After matter created, populated `SA Legal — Matter Details` custom fields on detail page:
    - Court: `Gauteng Division, Pretoria`
    - Opposing Party: `Road Accident Fund`
  - Clicked "Save Custom Fields" → 3× POST returned 200 at network level, but UI error rendered: **"Cannot create project for customer in PROSPECT lifecycle status"**. Matter itself remains ACTIVE in DB; custom field values did not persist to the matter row (follow-up DB probe needed to confirm).
  - Logged **GAP-L-35** (MED) — custom-field saves blocked by PROSPECT gate despite matter creation succeeding; scenario doesn't transition to ONBOARDING/ACTIVE until Day 7.

## Checkpoint 3.4 — Submit → matter created, redirected to matter detail
- Result: **PASS**
- Evidence:
  - Clicked "Create Matter" → POST returned 200, auto-redirect to `/projects/40881f2f-7cfc-45d9-8619-de18fd2d75bb`. Matter detail renders: header "Dlamini v Road Accident Fund / Active", client link "Sipho Dlamini", Ref badge "RAF-2026-001", Type "Litigation — Road Accident Fund", "Created Apr 21, 2026 · 0 documents · 1 member · 9 tasks".
  - DB read-only: `SELECT id, name, reference_number, work_type, customer_id, status FROM tenant_5039f2d497cf.projects ORDER BY created_at DESC LIMIT 1;` → `40881f2f-…-de18fd2d75bb | Dlamini v Road Accident Fund | RAF-2026-001 | Litigation — Road Accident Fund | 8fe5eea2-…-267486df68bd | ACTIVE`. Row persisted correctly.
  - Screenshot: `qa_cycle/checkpoint-results/day-03-3.4-matter-raf-created.png`.

## Checkpoint 3.5 — Verify matter sidebar tabs
- Result: **FAIL** (tab set doesn't match scenario spec)
- **Scenario expects**: Overview, Tasks, Documents, Time, Fee Notes, Trust, Activity, Audit (8 tabs)
- **Actual**: Overview, Documents, Members, Clients, Action Items, Time, Disbursements, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, **Disbursements** (duplicate!), Statements, Activity (20 tabs total)
- Deltas:
  - No dedicated **"Tasks"** tab (Action Items appears to be the replacement, but label differs)
  - No dedicated **"Fee Notes"** tab (Fee Estimate / Financials / Statements cover adjacent concerns)
  - No dedicated **"Audit"** tab (Activity is the closest, but not labelled Audit; GAP-L-11 carry-forward still OPEN)
  - **Disbursements duplicated** (two tabs with the same label) — UI bug
- Logged **GAP-L-38** (LOW). Scenario Days 21, 28, 60, 75, 85, 88 all reference Tasks/Fee Notes/Audit tabs; those checkpoints will have similar drift.

## Checkpoint 3.6 — Promoted matter fields render inline, NOT duplicated
- Result: **FAIL**
- Evidence:
  - Field Groups section auto-attaches THREE groups to a litigation matter: **SA Conveyancing — Matter Details**, SA Legal — Matter Details, Project Info.
  - SA Conveyancing is irrelevant to a RAF litigation matter and exposes Conveyancing Type / Erf Number / Deeds Office / Purchase Price / Transfer Duty / Bond Institution — all inappropriate for this matter type.
  - Over-broad auto-attachment. Logged **GAP-L-37** (LOW).
  - Additionally the Overview tab card shows the matter header info (Name, Client, Ref, Type) correctly but the "promoted inline fields" (scenario's matter_type / court_name / case_number) render only inside the Field Groups region (below header), not on the Overview tab body. Scenario's "NOT duplicated in a generic Custom Fields section" ask is technically met (no standalone "Custom Fields" section), but the intent (promoted inline on Overview) is not.

## Checkpoint 3.7 — Navigate to Requests tab → click "+ New Request"
- Result: **PASS**
- Evidence:
  - Clicked "Requests" tab → empty state "No information requests yet" + "New Request" button visible.
  - Clicked "+ New Request" → dialog "Create Information Request" opened with subtitle "Request documents or information from Sipho Dlamini for Dlamini v Road Accident Fund."

## Checkpoint 3.8 — Select template "FICA Onboarding Pack"
- Result: **BLOCKED (BLOCKER hit — halts subsequent Day 3 checkpoints)**
- Evidence:
  - Template dropdown lists 5 options + Ad-hoc:
    1. Ad-hoc (no template)
    2. Tax Return Supporting Docs (5 items) — accounting
    3. Monthly Bookkeeping (4 items) — accounting
    4. Conveyancing Intake (SA) (7 items) — legal (conveyancing subset)
    5. Company Registration (4 items) — cross-vertical
    6. Annual Audit Document Pack (5 items) — accounting
  - **"FICA Onboarding Pack" not present.**
  - DB read-only: `SELECT name, source, pack_id FROM tenant_5039f2d497cf.request_templates ORDER BY name;` → 5 rows (Annual Audit Document Pack, Company Registration, Conveyancing Intake (SA), Monthly Bookkeeping, Tax Return Supporting Docs). No FICA template.
  - Filesystem read-only: `ls backend/src/main/resources/request-packs/` → annual-audit.json, company-registration.json, consulting-za-creative-brief.json, conveyancing-intake-za.json, monthly-bookkeeping.json, tax-return.json, year-end-info-request-za.json. **No `fica-onboarding-pack.json`**.
  - Template dialog also shows **"Portal Contact: No portal contacts found for this customer. Please add a portal contact first."** and BOTH "Save as Draft" + "Send Now" buttons are disabled. Screenshot: `qa_cycle/checkpoint-results/day-03-3.8-no-fica-template-no-portal-contact.png`.
  - This compounds into **GAP-L-34** — even if the template existed, no way to create a portal contact from firm-side UI (`/api/customers/{id}/portal-contacts` is GET-only; no POST endpoint; no "Add Portal Contact" button in dialog; `PortalContactService.createContact` is only invoked by DevPortalController for dev magic-link generation).
- Logged **GAP-L-33** (HIGH, template missing) + **GAP-L-34** (HIGH, portal-contact creation path missing).

## Checkpoints 3.9–3.14 — BLOCKED
- 3.9 Addressee auto-populated — **BLOCKED** (GAP-L-34: no portal contacts exist to populate)
- 3.10 Request items pre-filled from template — **BLOCKED** (GAP-L-33: template doesn't exist)
- 3.11 Due date = Day 10 — **BLOCKED** (dialog's Send Now / Save as Draft both disabled)
- 3.12 Click Send → status = Sent — **BLOCKED** (dialog's Send Now button disabled)
- 3.13 Portal contact auto-linked — **BLOCKED/FAIL** (0 portal_contacts rows in tenant schema; no auto-link happens on client-create)
- 3.14 Mailpit magic-link email — **BLOCKED** (no request sent → no email dispatched)

## Day 3 summary checks
- Matter created with reference format RAF-YYYY-NNN: **PASS** — RAF-2026-001 persisted, ACTIVE status.
- Matter-type template instantiated — phase sections present, LSSA tariff linked: **PARTIAL** — generic Litigation template used (GAP-L-36 for RAF-specific); 9 tasks auto-generated from template; LSSA tariff not verified on matter detail (no visible tariff badge/section — will probe on Day 21 time-log).
- Promoted matter fields render inline, not duplicated: **FAIL** — SA Conveyancing field group incorrectly auto-attached (GAP-L-37); matter_type / court_name / case_number live inside Field Groups section, not "promoted inline" on Overview (GAP-L-38 adjacent).
- FICA info request dispatched, magic-link email sent: **BLOCKED** — FICA template doesn't exist (GAP-L-33) + portal contact creation path missing (GAP-L-34); no request sent, no email dispatched, 0 portal_contacts rows, 0 information_requests rows.

## Carry-Forward watch-list verifications this turn

| Prior gap | Re-observed? | Notes |
|---|---|---|
| GAP-L-22 (post-registration session handoff) | Not triggered | Bob's session held cleanly through Day 3; no re-auth, no cross-session leak. |
| GAP-L-07 (matter closure gates, Day 60) | Not triggered | Day 3 creates matter; not at closure stage. But matter detail Actions bar shows "Close Matter" / "Complete Matter" buttons — gate behaviour untested. Deferred to Day 60. |
| GAP-L-10 (acceptance-eligible manifest flag) | Not triggered | Day 3 doesn't generate engagement letter; scenario 3 doesn't ask for it. |
| "Project" / "customer" terminology leak (Day 75 note carry) | **Re-observed** | Error string displayed verbatim: "Cannot create project for customer in PROSPECT lifecycle status". Also dialog footer: "Customer: Sipho Dlamini / Project: Dlamini v Road Accident Fund". Not re-logged (already tracked at Day 75 in legal-za archive); wave of terminology leaks in backend error messages + UI strings documented. |
| GAP-L-26 (sidebar bg / logo not consumed) | **Re-observed** | Bob's sidebar still slate-950 default, no logo image. Not re-logged. |

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-33 | HIGH | FICA Onboarding Pack request template missing for legal-za vertical. DB has 5 platform templates (Tax Return Supporting Docs, Monthly Bookkeeping, Conveyancing Intake (SA), Company Registration, Annual Audit Document Pack); filesystem has 7 request-pack JSONs but none is `fica-onboarding-pack.json`. Scenario 3.8/3.10 FICA request flow is unreachable. Phase 64 legal-za pack installed 16 items + automation-legal-za 5 items (per Day 0 verification) but the FICA-specific request pack was not authored. Owner: backend/product — author `backend/src/main/resources/request-packs/fica-onboarding-pack.json` with items "ID copy" + "Proof of residence (≤ 3 months)" + "Bank statement (≤ 3 months)" + wire it into legal-za pack.json. |
| GAP-L-34 | HIGH | No firm-side path to create a portal contact. `CustomerController.java` exposes `@GetMapping("/{id}/portal-contacts")` only — no POST; `PortalContactService.createContact` is only invoked by `DevPortalController.generate-link` (dev tooling). Create Information Request dialog detects 0 contacts and shows "No portal contacts found for this customer. Please add a portal contact first." but offers no "Add Portal Contact" action — Save as Draft + Send Now both disabled. Scenario 3.9's "portal contact auto-populated from client record" assumes one of: (a) auto-create on customer create from `customers.email`, (b) auto-create on first request dispatch, (c) firm-side dialog with "Add Portal Contact" CTA. None exist. Owner: backend (POST `/api/customers/{id}/portal-contacts`) + frontend ("Add Portal Contact" button in Create Information Request dialog or on client detail page). Severity HIGH because every portal-dependent day (4/8/11/30/46/61/75 magic-link, 15 isolation probe, 30 PayFast pay) is blocked without at least one portal contact. |
| GAP-L-35 | MED | Custom-field save on matter detail returns "Cannot create project for customer in PROSPECT lifecycle status" error AFTER the matter itself has been inserted successfully. Sequence: Create Matter → ACTIVE matter row in DB → navigate to detail → fill SA Legal — Matter Details fields (Court, Opposing Party) → Save Custom Fields → error rendered next to button, 3× POST returned 200 at network level but custom fields don't appear to persist. Scenario expects Court / Case Number / etc. to be editable immediately at matter creation time. PROSPECT customers should be allowed to have matters with editable promoted fields; the gate should relax to "ONBOARDING+" or be removed entirely for custom-field PATCH. Owner: backend (likely `MatterCustomFieldService` or `ProjectService#updateCustomFields` — check for `customer.lifecycleStatus != PROSPECT` guard). Error string also leaks "project" / "customer" terminology (Project terminology from prior Day 75 observation). |
| GAP-L-36 | MED | "Litigation — Road Accident Fund" matter-type template missing. "New from Template" dialog lists 5 legal templates (Collections, Commercial, Deceased Estate Administration, Litigation (Personal Injury / General), Property Transfer) — no RAF-specific template. Scenario specifies RAF template with RAF-specific task checklist (accident date, third-party, insurer, summons, quantum). Owner: backend/product — either add `project-template-packs/legal-za-raf.json` (or a RAF preset nested inside the existing `legal-za.json` pack), OR formally reclassify the scenario to use generic Litigation template + note RAF-specific fields live in SA Legal — Matter Details field group. |
| GAP-L-37 | LOW | Matter detail auto-attaches "SA Conveyancing — Matter Details" field group to a Litigation matter regardless of template selection. Wrong-vertical over-attachment — conveyancing fields (Erf Number, Deeds Office, Purchase Price, Transfer Duty, Bond Institution) are all irrelevant for a RAF litigation matter. Scenario 3.6 expects promoted fields specific to the chosen template, not every field group in the tenant's vertical profile. Owner: backend — matter creation path should only attach field groups declared in the selected project-template-pack, OR frontend — client-side filter the Field Groups list by `fieldGroup.appliesToMatterTypes` if that metadata exists. |
| GAP-L-38 | LOW | Matter detail tab bar shows 20 tabs (including **Disbursements twice**) and does not match scenario-specified 8 tabs (Overview/Tasks/Documents/Time/Fee Notes/Trust/Activity/Audit). Missing by exact label: "Tasks" (Action Items used instead), "Fee Notes" (Fee Estimate / Financials / Statements cover adjacent concerns), "Audit" (Activity is closest, GAP-L-11 still OPEN). Duplicate: "Disbursements" listed twice (refs e646 + e657 in snapshot). Owner: frontend — consolidate tabs, rename Action Items → Tasks for legal-za vertical, add Audit tab (couples to GAP-L-11). |
| GAP-L-39 | LOW | "+ New Matter" CTA on client detail navigates to `/projects?new=1&customerId=<customerId>` but the "New from Template — Configure" dialog's Client dropdown defaults to "None", not to the customer referenced in the URL. Minor UX polish — user has to re-select the client manually. Owner: frontend — Configure step should read `customerId` from URL searchParams and pre-select it. |

## Halt reason

Day 3 halted at **Checkpoint 3.8** on BLOCKER rule — FICA Onboarding Pack template is missing (GAP-L-33) AND firm-side portal-contact creation path is missing (GAP-L-34). Both are independent HIGH-severity gaps; either alone would block 3.8–3.14. Day 4 (Sipho first portal login via magic-link) is entirely dependent on this dispatch, so Day 3 BLOCKER cascades into Day 4–88 portal POVs unless a workaround is accepted (e.g. dev-tooling magic-link generator from DevPortalController to seed a portal session for Day 4 testing).

## QA Position on exit

`Day 3 — 3.8 (blocked pending GAP-L-33 + GAP-L-34 fixes)`. Next-turn recommendation: **Dev fix** (1) create `backend/src/main/resources/request-packs/fica-onboarding-pack.json` with the three canonical FICA items; wire it into legal-za pack reconciliation, (2) add POST `/api/customers/{id}/portal-contacts` endpoint + "Add Portal Contact" button to Create Information Request dialog's Portal Contact section when no contacts exist, OR (easier shortcut) auto-create a PortalContact row on customer-create with email from `customers.email`. Both fixes unblock 3.8–3.14 and the downstream portal POV days. Deferred: GAP-L-35/36/37/38/39 are all LOW/MED cosmetic or workflow gaps that don't block Day 3 re-execution once L-33 and L-34 are addressed.
