# Day 3 — RAF matter creation + FICA info request (Bob)

**Stack**: dev/Keycloak — frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025
**Date**: 2026-04-30
**Actor**: Bob Ndlovu (`bob@mathebula-test.local`, Admin) — confirmed via sidebar user pill
**Branch**: `bugfix_cycle_2026-04-30` (PR #1225 OBS-102 + PR #1226 OBS-201 merged via main)

---

## OBS-201 Verification (pre-Day-3)

| # | Check | Result |
|---|-------|--------|
| 1 | Step 1 of Create Client wizard has NO `idNumber` / generic "ID Number" field | **PASS** — Step 1 fields: Name, Type, Email, Phone (optional), Tax Number, Notes, Address (lines 1–2, City, State/Province, Postal Code, Country), Contact (Name/Email/Phone), Business Details (Registration Number, Entity Type, Financial Year End). No generic ID Number input. |
| 2 | Step 2 SA Legal group accordion is OPEN by default | **PASS** — On entering Step 2 (after Name + Email), the "SA Legal — Client Details" group renders with `▾` chevron and **all four optional fields visible**: ID / Passport Number, Postal Address, Preferred Correspondence, Referred By. No collapsed state. |
| 3 | Inner "Additional Information (N)" toggle is gone or flattened | **PASS** — No nested accordion. Fields render flat under the SA Legal group header. (The dialog's own step-header reads "Additional Information — Step 2 of 2" but that is the wizard step title, not the inner accordion.) |
| 4 | Console errors during the wizard | **PASS** — 0 errors (1 unrelated warning, browser session). |
| 5 | Conflict Check pre-fill still works | **PASS** — Sipho's detail still renders `Run Conflict Check` link with `?customerId=…&checkedName=Sipho+Dlamini&checkedIdNumber=8501015800088`. SA Legal `id_passport_number` round-tripped to entity column on Day-2 customer create. |

Verification approach: Cancel-without-submit (preferred to keep dataset clean for Day 3). Filled Name + Email on Step 1, advanced to Step 2, captured both screenshots, then closed the dialog without creating a client.

**Evidence**:
- `qa_cycle/evidence/day-03/obs-201-verify-step1.png`
- `qa_cycle/evidence/day-03/obs-201-verify-step2.png`

**Verdict**: **OBS-201 → VERIFIED** (FIXED → VERIFIED). Both bullets of the fix (Step-1 dedupe + Step-2 auto-open accordion) confirmed in browser. Proceeding to Day 3.

---

## Step-by-step checkpoints

### 3.1 — Sipho's client detail → click + New Matter
- **PASS** — On `/customers/a30bb16b-743c-45a5-9fb5-13167fb92fde` Matters tab, "+ New Matter" link navigated to `/projects?new=1&customerId=a30bb16b-743c-45a5-9fb5-13167fb92fde`. The `?new=1` query auto-opens the New-from-Template dialog.

### 3.2 — Dialog uses legal-specific matter-type template selector
- **PASS** — Dialog title "New from Template — Select Template". Templates listed: **Collections (Debt Recovery), Commercial (Corporate & Contract), Deceased Estate Administration, Litigation (Personal Injury / General), Litigation (Road Accident Fund -- RAF), Property Transfer (Conveyancing)**. Each shows task count. Legal-vertical pack confirmed.
- Selected: **Litigation (Road Accident Fund -- RAF)** — 9 tasks.
- Evidence: `day-03-1-template-selector.png`

### 3.3 — Fill matter form
- Matter name: **Dlamini v Road Accident Fund** (overrode pattern preview "Sipho Dlamini - RAF Claim")
- Description: shortened to <255 chars (see GAP OBS-301 below — backend `@Size(max=255)` mismatch with frontend's `maxLength=2000`)
- Client: **Sipho Dlamini** (auto-pre-selected from `?customerId` deep-link)
- Matter lead: **Bob Ndlovu**
- Reference Number: **RAF-2026-001**
- Priority: not set (optional)
- Work Type: not set (optional)
- Note: dialog has no "Court" or "Case Number" inputs at intake — these are promoted matter fields rendered on the Overview/SA-Legal field-group section after creation (per scenario 3.6 expectation that they render inline, not in a generic Custom Fields section). PASS — see 3.6.
- Evidence: `day-03-3-matter-config.png`

### 3.4 — Submit → matter created
- **FIRST ATTEMPT BLOCKED** by validation: backend returned HTTP 400 "1 field(s) have validation errors" because the **template-prefilled description** was 273 characters but `InstantiateTemplateRequest.description` is `@Size(max=255)` server-side while the frontend textarea allows `maxLength=2000`. Logged as **OBS-301** below. Worked around by trimming the description.
- **SECOND ATTEMPT (after trim)** — POST /api/project-templates/{id}/instantiate succeeded; redirected to `/projects/b7e319f7-fd7e-4526-a8b3-b40b1f85b34b`. **PASS**.
- Evidence: `day-03-4-matter-detail.png`

### 3.5 — Matter sidebar tabs
- **PARTIAL / SCENARIO-DRIFT** — Actual tabs (left→right): **Overview, Documents, Members, Clients, Tasks, Time, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity**.
- Scenario 3.5 expected **Overview, Tasks, Documents, Time, Fee Notes, Trust, Activity, Audit**.
- Differences:
  - **"Fee Estimate"** instead of **"Fee Notes"** — terminology drift (legal-vertical UI uses "Fee Estimate" for matter-level estimates; "Fee Notes" is a separate concept, see 7.x).
  - **No "Audit" tab** — closest is **"Activity"** (Recent Activity panel exists on Overview; full activity tab present). Audit log is reachable via Settings sidebar, not matter-level.
  - All other expected tabs present (superset).
- Logging as **OBS-302** (scenario amendment, not a code bug — UI is the canonical naming).

### 3.6 — Promoted matter fields render inline, NOT duplicated in Custom Fields
- **PASS** — Matter detail renders TWO field-group cards under "FIELD GROUPS": **SA Legal — Matter Details** (Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value) and **Project Info** (Category). All fields render inline with their group header. There is **no separate generic "Custom Fields" section** that duplicates them. ✓ matches scenario expectation.

### 3.7 — Navigate to Requests tab → + New Info Request
- **PASS** — Requests tab opened, "No information requests yet" empty state with **"+ New Request"** button. Clicked → dialog "Create Information Request" rendered.

### 3.8 — Select template "FICA Onboarding Pack"
- **PASS** — Template dropdown listed **8 active templates** in the legal-za pack: Tax Return Supporting Docs (5 items), Monthly Bookkeeping (4), Liquidation and Distribution Account Pack (5), **FICA Onboarding Pack (3 items)**, Conveyancing Intake (SA) (7), Company Registration (4), Annual Audit Document Pack (5), plus "Ad-hoc". Selected FICA Onboarding Pack.

### 3.9 — Addressee = Sipho Dlamini
- **PASS** — Portal Contact dropdown auto-populated with **Sipho Dlamini (sipho.portal@example.com)**. Single option (the customer's auto-provisioned portal contact from Day 2 — `PortalContactAutoProvisioner` log line confirms this happened at customer creation).

### 3.10 — Request items pre-filled from template
- **PASS (inferred)** — Dialog footer shows "Template items: 3" matching the FICA Onboarding Pack's 3 items. Individual items are not surfaced inline in the create dialog (they're rendered after the request is created). Scenario expects ID copy / Proof of residence / Bank statement — will be validated firm-side on Day 5 and portal-side on Day 4.

### 3.11 — Due date = Day 10 (7 days from today, 2026-05-07)
- **PASS** — Due Date field set to `2026-05-07`. Reminder Interval = 5 days (default).
- Evidence: `day-03-7-info-request-dialog.png`

### 3.12 — Click Send → status = Sent
- **PASS** — Send Now → dialog closed, request **REQ-0001** created in Requests tab table:
  - Request: REQ-0001 — Dlamini v Road Accident Fund
  - Contact: Sipho Dlamini
  - Status: **Sent** (teal badge)
  - Progress: 0/3 accepted
  - Sent: Apr 30, 2026
- Evidence: `day-03-12-request-sent.png`

### 3.13 — Portal contact created/linked
- **PASS** — `sipho.portal@example.com` portal contact already auto-provisioned at customer creation on Day 2 (per backend log `PortalContactAutoProvisioner`: `c99db0e9-6745-465e-a542-3c842e829758` for customer `a30bb16b-…-13167fb92fde`). The Requests row's Contact column shows "Sipho Dlamini" linking to that portal contact.

### 3.14 — Mailpit magic-link email
- **PASS** — Mailpit message `WVVCHF6KxLFodNmUpcRWoG`:
  - From: `noreply@docteams.app`
  - To: `sipho.portal@example.com`
  - Subject: **"Information request REQ-0001 from Mathebula & Partners"**
  - Body contains "Information Request" / "Hi Sipho Dlamini" / "3 item(s) that require your attention" / **"View Request"** button
  - Magic link URL: `http://localhost:3002/auth/exchange?token=ep0gaG5qc0V6JZaLEMh4HGz-nrwgVkF0dsd7xWqKKBI&orgId=mathebula-partners`
- Note: scenario 3.14 expected subject phrases "sign in" / "action required" / "your portal" — actual is "Information request" with "View Request" CTA. Functionally equivalent (magic-link to portal); minor copy-drift, not a bug.

---

## Day 3 day-end checkpoints

| # | Check | Result |
|---|-------|--------|
| A | Matter created with reference format RAF-YYYY-NNN | **PASS** — RAF-2026-001 |
| B | Matter-type template instantiated — phase sections present, LSSA tariff linked | **PARTIAL** — 9 RAF-specific tasks instantiated (Initial RAF claim assessment / File RAF1 / Statutory medical reports / Insurer correspondence — RAF tariff schedule / Settlement negotiation / Court action — Section 24 / Trial / Settlement payout & costs / Prescription monitoring). Phase sections = the 9 tasks. **LSSA tariff is NOT auto-linked at matter creation** — fee estimate is empty until proposal flow on Day 7. The "RAF tariff schedule" task name implies the linkage is procedural (the firm picks tariff lines on the proposal/fee estimate). Not blocking; will revisit Day 7. |
| C | Promoted matter fields render inline, not duplicated | **PASS** — see 3.6 |
| D | FICA info request dispatched, magic-link email sent | **PASS** — REQ-0001 status Sent, Mailpit email with portal exchange link delivered |

---

## Gaps / Observations filed today

### OBS-301 — Frontend allows matter description up to 2000 chars; backend rejects >255 with generic "1 field(s) have validation errors"  `bug` (severity: bug — workaround = keep description short)

- **Where**: `frontend/components/templates/NewFromTemplateDialog.tsx` line 314 sets `<Textarea maxLength={2000}>` for the matter description.
- **Backend**: `backend/.../projecttemplate/dto/InstantiateTemplateRequest.java` line 11 declares `@Size(max = 255) String description`.
- **Repro**: New from Template → Litigation (RAF). Template's prefilled description is 273 chars (longer than 255). Click Create → backend returns 400 "1 field(s) have validation errors" with no field-level message surfaced to the user. The form gives no inline feedback — the user has no idea which field, what limit.
- **Impact**: User-facing UX bug — confusing failure with no guidance. Also affects ANY template whose default description exceeds 255 chars.
- **Suggested fix** (Dev): Either (a) raise backend `@Size(max=255)` to match frontend (e.g. `max=2000`), OR (b) lower frontend `maxLength` to `255` and add Zod schema validation. Surface field-level errors on the dialog (currently swallows them — only the generic message reaches the UI via `error.message`).
- **Repro evidence**: `day-03-3-matter-config.png` shows the prefilled 273-char description that the form accepted client-side but the backend rejected.

### OBS-302 — Matter sidebar tab labels drift from scenario expectations  `nit` (Product/Scenario)

- Scenario 3.5 expected: Overview, Tasks, Documents, Time, **Fee Notes**, Trust, Activity, **Audit**.
- Actual: Overview, Documents, Members, Clients, Tasks, Time, **Fee Estimate**, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, **Activity** (no "Audit").
- Likely WONT_FIX — UI is the canonical product surface. Recommend amending scenario 3.5 to read "tabs include Overview, Tasks, Documents, Time, Fee Estimate, Trust, Activity (and others — full superset acceptable); explicit 'Fee Notes' and 'Audit' tabs are not present at matter level — Fee Notes are a separate firm-level concept, audit is via Settings."

---

## Console / Logs

- Browser console: **0 errors** during the entire Day 3 flow (1 transient warning, unrelated to matter create).
- Backend log: clean — `Created project b7e319f7-fd7e-4526-a8b3-b40b1f85b34b` from template, no warnings beyond the pre-existing Hikari housekeeper-clock-leap warnings (the laptop slept).

---

## Day 3 Status: **COMPLETE — ready for Day 4**

All 14 checkpoints + 4 day-end checks executed. 1 real bug filed (OBS-301), 1 cosmetic / scenario-drift observation (OBS-302). OBS-201 fix verified clean. RAF-2026-001 matter created with template tasks; FICA info request REQ-0001 dispatched to portal with magic-link email. Day 4 (portal context — Sipho first portal login + FICA upload) ready.

**New entities created (Day 3)**:
- Matter: **RAF-2026-001 — Dlamini v Road Accident Fund** (id `b7e319f7-fd7e-4526-a8b3-b40b1f85b34b`, ACTIVE, lead = Bob Ndlovu, customer = Sipho Dlamini, 9 RAF tasks instantiated)
- Info Request: **REQ-0001** (FICA Onboarding Pack template, due 2026-05-07, status Sent, addressed to portal contact Sipho Dlamini)
- Portal Contact: `c99db0e9-6745-465e-a542-3c842e829758` (Day-2 auto-provision, used today)
- Mailpit message: `WVVCHF6KxLFodNmUpcRWoG` (magic-link email — required for Day 4)
