# Day 3 — Create RAF matter, send FICA info request

**Stack**: dev/Keycloak — frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025
**Date**: 2026-05-13
**Cycle**: bugfix_cycle_2026-05-13 (cycle 1)
**Actor**: Bob Ndlovu (`bob@mathebula-test.local`, Admin) — confirmed via sidebar user pill
**Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` — Day 3

---

## Step-by-step checkpoints

### 3.1 — Sipho's client detail -> click + New Matter
- **PASS** — On `/customers/334bf98f-9f02-4d2f-9ee8-80bbed65ea5b` Matters tab, clicked "New Matter" link. Navigated to `/projects?new=1&customerId=334bf98f-9f02-4d2f-9ee8-80bbed65ea5b`. The `?new=1` query auto-opens the New-from-Template dialog.

### 3.2 — Dialog uses legal-specific matter-type template selector
- **PASS** — Dialog title "New from Template — Select Template". Templates listed: **Collections (Debt Recovery) 9 tasks, Commercial (Corporate & Contract) 9 tasks, Deceased Estate Administration 9 tasks, Litigation (Personal Injury / General) 9 tasks, Litigation (Road Accident Fund -- RAF) 9 tasks, Property Transfer (Conveyancing) 12 tasks**. Six legal-vertical templates confirmed.
- Selected: **Litigation (Road Accident Fund -- RAF)** — 9 tasks.

### 3.3 — Fill matter form
- **PASS** — Configure step showed:
  - Matter name: Changed from template default "Sipho Dlamini - RAF Claim" to **Dlamini v Road Accident Fund**
  - Description: Trimmed from template's 273-char default to fit 255-char backend limit (OBS-301 from prior cycle still present — template prefills 273 chars but backend `@Size(max=255)` rejects it)
  - Client: **Sipho Dlamini** (auto-pre-selected from `?customerId` deep-link)
  - Matter lead: **Bob Ndlovu** (selected from dropdown)
  - Reference Number: **RAF-2026-001**
  - Note: No "Court" or "Case Number" inputs at intake — these are promoted fields rendered inline on the matter detail Overview (per checkpoint 3.6).

### 3.4 — Submit -> matter created, redirected to matter detail
- **PASS** — POST succeeded on first attempt (description was manually trimmed). Redirected to `/projects/c90832a4-c993-4eaa-9ea7-404a259b0e29`. Matter shows:
  - Title: "Dlamini v Road Accident Fund"
  - Status: Active
  - Client: Sipho Dlamini (linked)
  - Ref: RAF-2026-001
  - Created May 13, 2026 · 0 documents · 1 member · 9 tasks

### 3.5 — Matter sidebar tabs
- **PASS** — Actual tabs (left -> right): **Overview, Documents, Members, Clients, Tasks, Time, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity, Audit Trail**.
- Scenario expected set is present. Note: "Expenses" tab from scenario not present as a separate tab (covered by "Disbursements"). "Audit Trail" tab present in addition to "Activity" tab (scenario says no per-matter "Audit" tab, but product has both). Minor scenario-drift, not a code bug — OBS-302 from prior cycle applies.

### 3.6 — Promoted fields render inline, NOT duplicated
- **PASS** — Two field-group cards: **SA Legal — Matter Details** (Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value) and **Project Info** (Category). All fields render inline with their group header. No separate generic "Custom Fields" section duplicating them.

### 3.7 — Navigate to Requests tab -> + New Info Request
- **PASS** — Requests tab opened, empty state "No information requests yet" with "New Request" button. Clicked -> "Create Information Request" dialog opened.

### 3.8 — Select template: FICA Onboarding Pack
- **PASS** — Template dropdown listed 8 templates: Ad-hoc, Tax Return Supporting Docs (5), Monthly Bookkeeping (4), Liquidation and Distribution Account Pack (5), **FICA Onboarding Pack (3 items)**, Conveyancing Intake (SA) (7), Company Registration (4), Annual Audit Document Pack (5). Selected FICA Onboarding Pack.

### 3.9 — Addressee = Sipho Dlamini
- **PASS** — Portal Contact dropdown auto-populated with **Sipho Dlamini (sipho.portal@example.com)** from client record. Single option (auto-provisioned portal contact from Day 2).

### 3.10 — Request items pre-filled from template
- **PASS (inferred)** — Dialog footer shows "Template items: 3" matching FICA Onboarding Pack's 3 items (ID copy, Proof of residence, Bank statement). Individual items are not surfaced inline in the create dialog — they render after creation.

### 3.11 — Due date = Day 10 (7 days from today, 2026-05-20)
- **PASS** — Due Date field set to `2026-05-20`. Reminder Interval = 5 days (default).

### 3.12 — Click Send -> info request status = Sent
- **PASS** — Send Now clicked -> dialog closed, request **REQ-0001** created in Requests tab table:
  - Request: REQ-0001 — Dlamini v Road Accident Fund
  - Contact: Sipho Dlamini
  - Status: **Sent** (teal badge)
  - Progress: 0/3 accepted
  - Sent: May 13, 2026
  - Request ID: `ac2abebd-b08c-4594-b6ff-88717bb4dbc2`

### 3.13 — Portal contact created/linked
- **PASS** — Portal contact `sipho.portal@example.com` was auto-provisioned at customer creation on Day 2 (by `PortalContactAutoProvisioner`). The Requests tab row Contact column shows "Sipho Dlamini" confirming the linkage.

### 3.14 — Mailpit magic-link email
- **PASS** — Mailpit message `CJsf6oPciWqSqzH4EsN6xb`:
  - From: `noreply@docteams.app`
  - To: `sipho.portal@example.com`
  - Subject: **"Information request REQ-0001 from Mathebula & Partners"**
  - Body: "Hi Sipho Dlamini" / "3 item(s) that require your attention" / "View Request" button
  - Magic link URL: `http://localhost:3002/auth/exchange?token=26sbOhJ-bVL1kcKGKBk5Ez-H7Rv7mMuw3EoRJG9GWmU&orgId=mathebula-partners`
- Note: scenario 3.14 expected subject phrases "sign in" / "action required" / "your portal" — actual is "Information request REQ-0001 from Mathebula & Partners" with "View Request" CTA. Functionally correct (magic-link to portal).

---

## Day 3 day-end checkpoints

| # | Check | Result |
|---|-------|--------|
| A | Matter created with reference format RAF-YYYY-NNN | **PASS** — RAF-2026-001 |
| B | Matter-type template instantiated — phase sections present, LSSA tariff linked | **PARTIAL** — 9 RAF-specific tasks instantiated from template. LSSA tariff NOT auto-linked at matter creation — fee estimate is empty until proposal flow on Day 7. Not blocking. |
| C | Promoted matter fields render inline, not duplicated | **PASS** — see 3.6 |
| D | FICA info request dispatched, magic-link email sent | **PASS** — REQ-0001 status Sent, Mailpit email with portal exchange link delivered |

---

## FICA Status Card verification (bonus)

The matter Overview tab now shows a **FICA Status Card** reading:
- Status: **In Progress**
- "Awaiting client response and firm-side review."
- "View request" link: `/org/mathebula-partners/information-requests/ac2abebd-b08c-4594-b6ff-88717bb4dbc2` (correct canonical route)

**Recent Activity** section shows:
- "Information request REQ-0001 sent to Bob Ndlovu" (minor display issue — should reference Sipho Dlamini, not the logged-in user who sent it; noted as OBS-304)
- "Bob Ndlovu created information request REQ-0001"

---

## Gaps / Observations

### OBS-301 (carry-forward) — Frontend allows matter description up to 2000 chars; backend rejects >255
- **Status**: Still present. Template prefills 273 chars; backend `@Size(max=255)` rejects. Workaround: manually trim description. Not blocking when trimmed.

### OBS-302 (carry-forward) — Matter sidebar tab labels drift from scenario
- **Status**: Still present. "Audit Trail" tab exists alongside "Activity" tab. Scenario expected no "Audit" tab at matter level. Minor scenario-drift.

### OBS-304 (new, nit) — Activity feed reads "sent to Bob Ndlovu" instead of portal contact name
- **Where**: Matter Overview > Recent Activity > "Information request REQ-0001 sent to Bob Ndlovu"
- **Expected**: "Information request REQ-0001 sent to Sipho Dlamini" (the portal contact addressee)
- **Impact**: Cosmetic — activity log references the actor who created/sent the request rather than the recipient. Non-blocking.

---

## Console / Logs

- Browser console: **0 new errors** — all errors are pre-existing OBS-203 (`/api/assistant/invocations` 404, 7 occurrences across client + matter detail pages).
- No hydration mismatches, no JS exceptions.

---

## Day 3 Status: **COMPLETE — ready for Day 4**

All 14 checkpoints + 4 day-end checks executed. 0 blockers, 0 new bugs. 2 carry-forward observations (OBS-301, OBS-302), 1 new cosmetic nit (OBS-304). RAF-2026-001 matter created with template tasks; FICA info request REQ-0001 dispatched to portal with magic-link email.

**New entities created (Day 3)**:
- Matter: **RAF-2026-001 — Dlamini v Road Accident Fund** (id `c90832a4-c993-4eaa-9ea7-404a259b0e29`, ACTIVE, lead = Bob Ndlovu, customer = Sipho Dlamini, 9 RAF tasks instantiated)
- Info Request: **REQ-0001** (id `ac2abebd-b08c-4594-b6ff-88717bb4dbc2`, FICA Onboarding Pack template, due 2026-05-20, status Sent, addressed to portal contact Sipho Dlamini)
- Mailpit message: `CJsf6oPciWqSqzH4EsN6xb` (magic-link email for Day 4 portal flow)
