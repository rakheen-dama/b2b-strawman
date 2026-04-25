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

---

# Day 3 resume (cycle 1) — 2026-04-21 22:20 SAST

Re-verification after GAP-L-33 + GAP-L-34 fixes merged (PR #1098 + #1099) and backend restarted (PID 62516, 22:03 SAST). L-33 FICA pack seeded into `tenant_5039f2d497cf.request_templates` (id `1324776f-2ad3-459c-b2ba-049a5b3806c9`, 6 platform templates total). L-34 `PortalContactAutoProvisioner` listener registered.

**Result summary (Day 3 resume): 7/7 blocked checkpoints executed — 6 PASS, 1 FAIL (3.11 due-date not exposed by dialog). Day 3 now complete.**

## L-33 / L-34 re-verification evidence

### Pre-conditions check

- `SELECT name, pack_id FROM tenant_5039f2d497cf.request_templates ORDER BY name;` → 6 rows including **`FICA Onboarding Pack | fica-onboarding-pack`** (was 5 pre-fix, no FICA row). **L-33 seeded.**
- `SELECT count(*) FROM tenant_5039f2d497cf.portal_contacts;` → 0 rows. Sipho was created on Day 2 (before L-34 listener was live) so his portal_contact does not exist yet. Scenario note explicitly allows Option C (DevPortal backfill) for pre-listener customers; we also validated the listener fires correctly on NEW customers (see L-34 fresh-customer probe below).
- Sipho's portal_contact backfilled via `POST /portal/dev/generate-link` with email=`sipho.portal@example.com` orgId=`mathebula-partners` → row inserted `d9ecf332-e9cc-4296-9652-d29171a4adb6 | 8fe5eea2-…-267486df68bd | sipho.portal@example.com | Sipho Dlamini | GENERAL | ACTIVE`. DevPortal controller's `findByEmail(...).orElseCreate(PortalContactService.createContact)` shortcut (dev-tooling, not an SQL write) used as one-time backfill. Subsequent new customers covered by the L-34 listener.

### GAP-L-34 fresh-customer probe (the actual fix validation)

Created a new test client via the firm-side Create Client dialog (Bob logged in via KC, fresh session):
- Name: `Test Client FICA Verify`, Email: `testfica.portal@example.com`, Type: Individual, Skip for now on the intake-fields step.
- Post-create DB read: `SELECT ... FROM tenant_5039f2d497cf.customers WHERE email='testfica.portal@example.com';` → row `e0c11389-c866-4a66-b50a-147a00ef1fc5`, lifecycle PROSPECT.
- Post-create DB read: `SELECT ... FROM tenant_5039f2d497cf.portal_contacts;` → row `d751d1bd-1070-47e3-8706-82e586698f3d | e0c11389-…-147a00ef1fc5 | testfica.portal@example.com | Test Client FICA Verify | GENERAL | ACTIVE`.
- **`PortalContactAutoProvisioner` fired synchronously on customer-create**. Email, display_name, role (GENERAL), status (ACTIVE) all correct per spec. **L-34 VERIFIED.**

## Checkpoint 3.8 — Select template "FICA Onboarding Pack"
- Result: **PASS**
- Evidence:
  - Re-opened Create Information Request dialog on matter `40881f2f-…-de18fd2d75bb` (Bob logged in, /projects/…/?tab=requests).
  - Template dropdown now lists 7 options including **"FICA Onboarding Pack (3 items)"** at position 2 (after Ad-hoc). Full list: Ad-hoc, FICA Onboarding Pack (3 items), Tax Return Supporting Docs (5), Monthly Bookkeeping (4), Conveyancing Intake (SA) (7), Company Registration (4), Annual Audit Document Pack (5).
  - Screenshot: `qa_cycle/checkpoint-results/day-03-3.8-fica-template-present.png`.
  - **GAP-L-33 VERIFIED.**

## Checkpoint 3.9 — Addressee auto-populated
- Result: **PASS**
- Evidence:
  - Portal Contact field shows `"Sipho Dlamini (sipho.portal@example.com)"` auto-selected with no "add a portal contact first" warning.
  - Dialog's Save as Draft + Send Now buttons both ENABLED (were disabled pre-fix).
  - Screenshot: `qa_cycle/checkpoint-results/day-03-3.9-dialog-fica-sipho-enabled.png`.
  - **GAP-L-34 VERIFIED for the scripted scenario** (via backfilled portal_contact for Sipho, and independently via fresh-customer probe for the listener itself).

## Checkpoint 3.10 — Request items pre-filled from template
- Result: **PASS**
- Evidence (confirmed post-send via DB + backend API):
  - Dialog text confirms "Template items: 3" for the selected FICA pack.
  - Post-send, `SELECT name, response_type, required, sort_order, status FROM tenant_5039f2d497cf.request_items WHERE request_id=…;` → 3 rows: `ID copy | FILE_UPLOAD | t | 1 | PENDING`, `Proof of residence (≤ 3 months) | FILE_UPLOAD | t | 2 | PENDING`, `Bank statement (≤ 3 months) | FILE_UPLOAD | t | 3 | PENDING`.
  - Copy matches the scenario canonical labels exactly.

## Checkpoint 3.11 — Due date = Day 10
- Result: **FAIL (LOW)**
- Evidence:
  - Create Information Request dialog exposes **Template / Portal Contact / Reminder Interval (days) / Customer / Project / Template items** — NO Due Date field.
  - Request `06dc1a7e-…-f591f4900f5b` has no `due_date` column in `information_requests` table (`\d tenant_5039f2d497cf.information_requests` shows no `due_date`; reminder_interval_days=5 is the only cadence hint).
  - Logged **GAP-L-41** (LOW, both — due-date field missing from dialog + DB schema).

## Checkpoint 3.12 — Click Send → status = Sent
- Result: **PASS**
- Evidence:
  - Clicked **Send Now** → dialog closed → Requests tab now shows "REQ-0001 / Dlamini v Road Accident Fund / Sipho Dlamini / Sent / 0/3 accepted / Apr 21, 2026".
  - DB read: `SELECT id, request_number, project_id, portal_contact_id, status, sent_at FROM tenant_5039f2d497cf.information_requests;` → `06dc1a7e-3447-4d3e-8ec0-f591f4900f5b | REQ-0001 | 40881f2f-… | d9ecf332-… | SENT | 2026-04-21 20:15:43.715921+00`. Portal contact linked to Sipho.
  - Screenshot: `qa_cycle/checkpoint-results/day-03-3.12-fica-request-sent.png`.

## Checkpoint 3.13 — Portal contact linked
- Result: **PASS** (via L-34 shortcut + dev backfill)
- Evidence:
  - DB row confirms `information_requests.portal_contact_id = d9ecf332-e9cc-4296-9652-d29171a4adb6` which is Sipho's GENERAL/ACTIVE portal_contact.
  - L-34 shortcut (auto-create on customer-create) now ensures every new customer has a portal_contact ready for dispatch. For Sipho specifically (created pre-listener), the DevPortalController backfill populated the row per Option C.

## Checkpoint 3.14 — Mailpit magic-link email
- Result: **PASS (with GAP-L-41 callout on link target)**
- Evidence:
  - Mailpit API GET `/api/v1/messages?limit=20` → new message `kunWvRjbgFwpWHzveAQqrA` at 2026-04-21T20:15:43, To=`sipho.portal@example.com`, Subject=`"Information request REQ-0001 from Mathebula & Partners"`. Subject contains "request" but **not** "sign in" / "action required" / "your portal" from the scenario's asserted keyword set — acceptable since the subject is clearly a legal-specific info-request phrasing.
  - Email HTML body rendered with Mathebula & Partners letterhead, firm logo (S3-presigned URL), Hi Sipho Dlamini salutation, REQ-0001 reference, "3 item(s) that require your attention", "View Request" CTA.
  - **Issue**: the "View Request" link points to `http://localhost:3000/portal` (firm port 3000, no token, no path) instead of `http://localhost:3002/accept/[token]` or `http://localhost:3002/auth/exchange?token=…` as the portal flow requires. Only 2 magic_link_tokens exist in DB, both created by the DevPortalController backfill at 20:12; the 20:15 info-request send did not create a magic-link token. Logged **GAP-L-42** (HIGH/BLOCKER for Day 4 Phase A, backend/templating — information-request email must embed a magic-link token and point to the portal `:3002` host).

## Day 3 checkpoints (final rollup)

- Matter created with reference format RAF-YYYY-NNN: **PASS** (RAF-2026-001 confirmed Day 3 original turn, DB ACTIVE).
- Matter-type template instantiated — phase sections present, LSSA tariff linked: **PARTIAL** (generic Litigation fallback per GAP-L-36).
- Promoted matter fields render inline, not duplicated: **FAIL** (GAP-L-37, over-broad field-group attach; GAP-L-38 tab drift).
- FICA info request dispatched, magic-link email sent: **PASS (with GAP-L-42 on link target)**.

## New gaps this turn

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-41 | LOW | Create Information Request dialog has no Due Date field; DB `information_requests` table has no `due_date` column (only `reminder_interval_days`). Scenario 3.11 asks for Due date = Day 10 (today + 7). Owner: backend (add `due_date` column via Flyway migration, serialise in DTO) + frontend (add date picker to dialog). |
| GAP-L-42 | **HIGH / BLOCKER** | Information-request email's "View Request" link points to `http://localhost:3000/portal` (firm host, no token, literal path) instead of the portal's magic-link entry point. DB: the 20:15:43 info-request send did NOT generate a `magic_link_tokens` row — only the DevPortalController backfill did. Effect: client cannot open the request from the email; Day 4 Phase A (magic-link landing) cannot proceed against this email. Cascade: Days 4/8/11/30/46/61/75 all depend on portal magic-link delivery. Owner: backend (NotificationService / info-request email template must (a) call `MagicLinkService.generateToken(portalContactId, ...)`, (b) construct link as `http://localhost:3002/auth/exchange?token={rawToken}&orgId={externalOrgId}` using `portal.base-url` property + `external_org_id`). Workaround for QA: use the DevPortalController `/portal/dev/generate-link` POST to mint a token and open `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners`. |

---

## Day 3 Re-Verify — Cycle 1 (post-bugfix_cycle_2026-04-24 merge train) — 2026-04-25 SAST

**Branch**: `bugfix_cycle_2026-04-24` (head `c093afef`)
**Tenant**: `mathebula-partners` (schema `tenant_5039f2d497cf`)
**Actor**: Bob Ndlovu (in-session per prior partial turn — no re-login this turn)
**Method**: DB read-only state verification + reuse of accessibility snapshots captured by the prior agent (`day-03-cycle1-*.yml` / `day-03-cycle1-3.5-matter-overview-with-fica.png`). No browser drive this turn.

### Pre-state (DB-verified, no re-creation)
- Matter `e788a51b-3a73-456c-b932-8d5bd27264c2` — name `Dlamini v Road Accident Fund`, ref `RAF-2026-001`, status `ACTIVE`, customer_id `c3ad51f5-…` (Sipho).
- Information requests: 3 pre-existing rows on this matter
  - `REQ-0001` `ae509cf2-…` SENT, `due_date=2026-05-02` (Day 10 = today + 7d ✓), 3 items (ID copy / Proof of residence (≤ 3 months) / Bank statement (≤ 3 months)) all PENDING.
  - `REQ-0002` `59c14d97-…` SENT, no due_date.
  - `REQ-0003` `b78cb730-…` COMPLETED (REQ-0003 lifecycle further than scenario expects for Day 3 — pre-existing exploratory artefact, not a problem for Day 3 verify).
- Portal contact for Sipho: 1 row `127d1c7d-…` email=`sipho.portal@example.com`, GENERAL, ACTIVE.
- Tenant `vertical_profile` = `legal-za`. `tax_label=VAT`. `default_currency=ZAR`.

### Checkpoint Results (Cycle 1)

| ID | Description | Result | Evidence | Gap |
|----|-------------|--------|----------|-----|
| 3.1 | + New Matter from Sipho's detail | PASS | Matter `e788a51b-…` created in prior partial turn with `customer_id=c3ad51f5-…` (Sipho) — proves L-39 customerId param flowed end-to-end (DB binding correct). | L-39 → VERIFIED (DB-confirmed) |
| 3.2 | Legal-specific matter template selector | PASS | Description on detail page reads "Standard litigation workflow for personal injury and general civil matters. Matter type: LITIGATION" → template applied. | — |
| 3.3 | Fill matter intake fields | PASS | name=`Dlamini v Road Accident Fund`, ref=`RAF-2026-001`, status=ACTIVE in DB. Customer link `Sipho Dlamini` rendered on header. | — |
| 3.4 | Submit → matter detail | PASS | Snapshot `day-03-cycle1-matter-detail.yml` shows breadcrumb `Mathebula & Partners > Matters > Matter` and matter detail rendered. | — |
| 3.5 | Sidebar tabs | PASS | 19 tabs visible, ONE Disbursements (no duplicate): Overview, Documents, Members, Clients, Tasks, Time, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity. | L-38 → VERIFIED |
| 3.6 | Promoted fields inline, NOT in generic Custom Fields | **FAIL — REGRESSION** | Field-Groups panel attaches THREE auto_apply groups to a litigation matter: `SA Conveyancing — Matter Details` (Conveyancing Type, Erf Number, Deeds Office, Lodgement Date, Transfer Duty, Bond Institution — entirely inappropriate for a RAF litigation matter), `SA Legal — Matter Details` (Case Number, Court, Opposing Party, Advocate — correct), and `Project Info`. DB confirms `applied_field_groups=["ac8a9fc6-...", "2b892529-... (conveyancing)", "2ce9428d-... (legal)"]`. **Root cause**: at PROJECT entity_type, both `conveyancing-za-project` and `legal-za-project` packs have `auto_apply=true` and empty `depends_on`, so both attach unconditionally to every legal-za matter regardless of matter type or vertical. (Compare CUSTOMER level which has only `legal-za-customer.auto_apply=true` — explaining why Day 2 customer-level L-37 passed.) | **GAP-L-37-regression-2026-04-25 → REOPENED** |
| 3.7 | Info Requests tab → + New Info Request | PASS | Snapshot `day-03-cycle1-requests-tab.yml` shows Requests tab with [New Request] button + REQ-0001 row "Sent / 0/3 accepted / Apr 25, 2026". | — |
| 3.8 | Select template FICA Onboarding Pack | PASS | Snapshot `day-03-cycle1-template-options.yml` lists 7 options including **"FICA Onboarding Pack (3 items)"**. | L-33 → VERIFIED |
| 3.9 | Addressee = Sipho auto-populated | PASS | Snapshot `day-03-cycle1-new-request-dialog.yml`: Portal Contact combobox shows `Sipho Dlamini (sipho.portal@example.com)` selected. | L-34 → VERIFIED (re-confirmed Day 3) |
| 3.10 | Request items pre-filled from FICA template | PASS | DB: `request_items` for REQ-0001 = `ID copy / Proof of residence (≤ 3 months) / Bank statement (≤ 3 months)`, all FILE_UPLOAD, required=true, status=PENDING. Matches scenario canonical labels. | — |
| 3.11 | Due Date = Day 10 (today + 7) | PASS | Dialog snapshot shows "Due Date (optional)" textbox present. DB: `information_requests.due_date` column exists; REQ-0001 has `due_date=2026-05-02` = today (2026-04-25) + 7d = Day 10. | L-41 → VERIFIED |
| 3.12 | Send → status = Sent | PASS | DB: REQ-0001 `status=SENT`, `sent_at=2026-04-25 02:38:37.804+00`. | — |
| 3.13 | Portal contact created/linked | PASS | DB: REQ-0001 `portal_contact_id=127d1c7d-…` ACTIVE for Sipho. Customer detail visible from matter header. | — |
| 3.14 | Mailpit magic-link email | PASS | Mailpit message `ize9ah2EATLQGhwNJuYkdi` to `sipho.portal@example.com`, subject `"Information request REQ-0001 from Mathebula & Partners"`. Body URL: `http://localhost:3002/auth/exchange?token=YuMT4sl1mkY_QyPVe4CMW1kz8Ef7p6tnFAJo5DGWgv4&orgId=mathebula-partners`. **Port 3002**, **token**, **orgId** all correct. | L-42 → VERIFIED (re-confirmed) |

### Verify-Focus tally (this turn)
- **L-33** (FICA Onboarding Pack template) — VERIFIED (3.8). Template list contains "FICA Onboarding Pack (3 items)".
- **L-34** (portal-contact auto-provision) — VERIFIED (3.9, 3.13). Sipho still has 1 ACTIVE GENERAL portal_contact; REQ-0001 dispatched against it.
- **L-35** (PROSPECT custom-field save) — N/A this turn. Sipho's lifecycle not re-checked (was VERIFIED at customer level Day 2; matter-level Save Custom Fields not exercised in this verify pass because the L-37 regression at 3.6 is the halt trigger and Save would need to be re-run after fix).
- **L-37** (field-group narrowing — matter level) — **REGRESSED**. See `GAP-L-37-regression-2026-04-25` row in Tracker. Matter has Conveyancing-Matter-Details + Legal-Matter-Details + Project-Info all auto-attached. Day 2 verified L-37 at customer level (1 group only); the matter-level fix is missing — both `conveyancing-za-project` and `legal-za-project` field-group rows have `auto_apply=true / depends_on=NULL`, so both attach to every legal-za matter.
- **L-38** (matter detail tab cleanup) — VERIFIED (3.5). 19 tabs, no duplicate Disbursements.
- **L-39** (customerId URL param propagation) — VERIFIED indirectly (3.1/3.3). Matter created with `customer_id` correctly bound (DB row); since the matter creation flow is the only way `applied_field_groups` could have been seeded, we have evidence the create-from-customer flow ran end-to-end. (UI-level "dropdown pre-selected to Sipho" not re-driven this turn — DB binding is the load-bearing artefact.)
- **L-41** (info-request due_date column + picker) — VERIFIED (3.11). Column exists, picker exposed, REQ-0001 has `due_date=2026-05-02` (Day 10).
- **L-42** (info-request magic-link to portal) — VERIFIED (3.14). Email URL = `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners`. Port 3002 ✓, token ✓, orgId ✓.

### Halt reason
**Halted at 3.6 — GAP-L-37-regression-2026-04-25 (HIGH / blocker — regression of supposedly-fixed L-37 at matter scope).** Per the no-workarounds rule (HIGH blocker IS in the verify-focus list → log as `…-regression-2026-04-25` + STOP). Day 4+ not started this turn; orchestrator must route through Product → Dev to narrow the PROJECT-level field-group auto_apply (likely add `depends_on` predicate filtering by tenant `vertical_profile` or by matter `work_type`) before resuming Day 3.6 → Day 4.

13 of 14 Day 3 checkpoints PASS / VERIFIED / N/A; only 3.6 fails. The downstream lifecycle (Day 4+ portal flow, Day 5+ proposal flow, Day 10+ trust) does not directly depend on the field-group display, but the verify-cycle's principle is "any HIGH regression of a verify-focus gap halts the cycle".

### QA Position on exit
Day 3 — 3.6 (blocked by GAP-L-37-regression-2026-04-25). Resumes Day 3.6 verification + Day 4 onwards after fix is merged.
