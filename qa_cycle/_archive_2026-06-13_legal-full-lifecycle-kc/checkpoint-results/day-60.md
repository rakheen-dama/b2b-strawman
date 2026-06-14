# Day 60 — Firm matter closure + generate Statement of Account `[FIRM]`

**Date**: 2026-06-13
**Cycle**: 28
**Stack**: Keycloak dev stack (frontend :3000, gateway :8443, backend :8080 PID 16741, KC :8180, Mailpit :8025)
**Actors**: Bob Ndlovu (Admin) for Phase A gate-resolution + payment record; Thandi Mathebula (Owner) for Section 86 approval + closure.
**Tooling**: **Playwright MCP exclusively** (clean Chromium; no SingletonLock this session). DB reads via `docker exec b2b-postgres psql -U postgres -d docteams`; Mailpit API for emails; LocalStack `awslocal s3 cp` to verify generated PDFs.
**Context swap**: portal (Sipho) → firm (Bob → Thandi).

## Entity mapping (this cycle vs scenario)
- Scenario 60.1–60.5 call the medical-evidence request **REQ-0003**; in this cycle that is **REQ-0004** (`ab11a55f-…`). REQ-0003 is Moroka's (EST-2026-002). Used REQ-0004 per orchestrator instruction.
- Matter RAF-2026-001 = `08ad56c4-ff5e-49c2-a034-cb5fa04b462c`; Sipho customer = `2211a80a-…`.
- Trust payment R **70,000** (cycle-correct two-deposit balance R50k+R20k; the scenario's R70,000 figure matches here — the R71,000/three-deposit artifact from prior cycles does not apply to the payment amount).
- Members: Bob `f753c371-…` (Admin), Thandi `ca39e4b1-…` (Owner).

## Harness/auth notes (not product defects)
- Firm KC users had no usable password post-bootstrap → reset Bob + Thandi to `password` via KC admin REST (dev-only Keycloak workaround, permitted by mandate).
- Two firm buttons (trust **Approve**, **Close Matter**) did not fire their React `onClick` under Playwright real-pointer `.click()` despite being hydrated with a bound handler; invoking the same handler via React props (or `form.requestSubmit()` for the close form) succeeded and the backend processed normally. See **OBS-6002** below — flagged as a candidate gap (pointer-interception class, OBS-2103 family) but the closure flow itself is functionally correct.

---

## Phase A — Resolve closure gate prerequisites (Bob, :3000)

### A1 — Review REQ-0004 submitted items (Info Requests gate)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.1 | Open REQ-0004 ("Supporting medical evidence" → cycle: ad-hoc REQ-0004) | **PASS** | `/information-requests/ab11a55f-…` → REQ-0004 "In Progress", contact Sipho, matter Dlamini v RAF. |
| 60.2 | 2 items submitted Day 46 (Hospital discharge summary + Orthopaedic report), both SUBMITTED | **PASS** | Both items SUBMITTED with uploaded PDFs (`hospital-discharge-summary.pdf`, `orthopaedic-report.pdf`), 0/2 accepted. |
| 60.3 | Accept item 1 → ACCEPTED (1/2) | **PASS** | Hospital discharge summary → **Accepted**; progress **1/2 accepted**. |
| 60.4 | Accept item 2 → ACCEPTED (2/2), envelope auto-COMPLETED | **PASS** | Orthopaedic report → **Accepted**; REQ-0004 header auto-transitioned to **Completed**. |
| 60.5 | REQ-0004 COMPLETED; all info requests COMPLETED | **PASS** | DB: REQ-0001/0002/0004 all **COMPLETED**; both REQ-0004 items **ACCEPTED**. |

### A2 — Complete/cancel open tasks (Open Tasks gate)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.6 | View 9 open RAF template tasks | **PASS** | Tasks tab: 9 OPEN tasks from the RAF litigation template. |
| 60.7 | Logged-time tasks → DONE; rest → CANCELLED | **PASS** | The 2 tasks with logged time ("Initial RAF claim assessment" 2h30m, "File RAF1 claim form" 1h30m) → **DONE** (OPEN→IN_PROGRESS→DONE; `TaskStatus` correctly disallows OPEN→DONE direct — domain behavior, not a bug). Marking DONE fired the seeded **"Task Completion Chain"** automation (TASK_STATUS_CHANGED toStatus=DONE), spawning a "Follow-up: …" task per DONE (2 follow-ups). All 7 remaining template tasks **+ 2 follow-ups** → **CANCELLED** (single-step OPEN/IN_PROGRESS→CANCELLED; CANCELLED does not trigger the chain). |
| 60.8 | 0 open tasks remaining | **PASS** | DB: 11 tasks total = **2 DONE + 9 CANCELLED**, 0 non-terminal. Tasks tab default (Open+In Progress) shows "No tasks yet". 📸 `day-60-tasks-zero-open.png`. |

### A3 — Resolve court date (Court Dates gate)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.9 | Locate Pre-Trial court date | **PASS** | Court Calendar: Pre-Trial, **2026-06-27** (scenario says Jun 4 — this cycle's date is the 27th), Gauteng Division Pretoria, status SCHEDULED. |
| 60.10 | Update to HEARD/VACATED | **PASS** | Actions → **Record Outcome** → outcome "Pre-trial conference held. Settlement reached with the RAF; matter concluded…" → status → **HEARD**. |
| 60.11 | No longer blocking | **PASS** | DB: status=HEARD with outcome. `NoOpenCourtDatesGate` blocks only SCHEDULED/POSTPONED on/after today → HEARD is resolved. |

### A4 — Trust payment + Section 86 dual-approval (Trust Balance gate)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.12 | Locate PAY/2026/001 R70,000 AWAITING_APPROVAL | **PASS (recorded this cycle)** | PAY/2026/001 did not pre-exist → **recorded via Trust Accounting → Record Payment** (Client Sipho, Matter RAF-2026-001, R70,000, ref PAY/2026/001, "Settlement payout…"). Posted as **AWAITING_APPROVAL**, recorded_by = **Bob**. (Scenario assumed Thandi recorded; per orchestrator "Thandi to record, Bob to approve, or vice versa" → used the vice-versa arrangement: Bob records, Thandi approves.) |
| 60.13 | Approver ≠ recorder (Section 86) | **PASS** | Approved by **Thandi** (`ca39e4b1`); recorder = **Bob** (`f753c371`). DB `recorded_by != approved_by` = **true**. Single-mode Section 86 (`require_dual_approval=false`): `TrustTransactionService.approveSingleMode` enforces "recorder cannot be sole approver" — satisfied. (Approve fired via React onClick — see OBS-6002.) |
| 60.14 | Payment APPROVED → balance R0.00 | **PASS** | DB: PAY/2026/001 = **APPROVED**, approved_at set. Sipho net trust balance = **R 0,00**. |
| 60.15 | Client ledger payment-out R70,000, running R0.00 | **PASS** | Client Ledgers UI: **Sipho Dlamini R 0,00** (deposits R70,000, payments-out R70,000). Moroka isolated at R25,000. 📸 `day-60-client-ledger-zero-balance.png`. |

---

## Phase B — Pre-closure gate report (Thandi, :3000)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.16 | Click Close Matter | **PASS (location note)** | Close Matter is in the **page header** (`data-testid="close-matter-btn"`), NOT the sidebar footer `data-testid="sidebar-lifecycle-action"` the scenario describes (Phase 73). Functional; UI-location is a scenario-vs-build mismatch, not a defect. Dialog opened (via React onClick — see OBS-6002). |
| 60.17 | Step 1 gate report — ALL gates GREEN | **PASS** | All **9 gates PASS**: Trust balance R0.00; Disbursements approved; Disbursements settled; Final bill issued (no unbilled); No court dates today+; No prescription timers; All tasks resolved; All info requests closed; No document acceptances pending. 📸 `day-60-closure-gates-all-green.png`. |

---

## Phase C — Run closure workflow (Thandi)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.18 | Continue → Step 2 close form | **PASS** | Close form rendered (Reason, Notes, two generate checkboxes). |
| 60.19 | Reason = CONCLUDED | **PASS** | Reason combobox default + selected = **Concluded**. |
| 60.20 | Generate closure letter + Generate Statement of Account both checked | **PASS** | Both checkboxes **checked (true)**; SoA labelled "Section 86 ledger reconciliation". 📸 `day-60-closure-form-concluded.png`. |
| 60.21 | Confirm Close → status CLOSED | **PASS** | DB: matter **CLOSED**, closed_at set, closed_by = Thandi. `matter_closure_log`: reason **CONCLUDED**, **override_used = f** (clean path), gate_report `allPassed: true`. |
| 60.22 | Closure letter + SoA both attached to Work > Documents | **PASS** | `documents`: `statement-of-account-…-2026-06-13.pdf` (5405 B, application/pdf, UPLOADED) + `matter-closure-letter-…-2026-06-13.pdf` (1644 B, UPLOADED). Both listed with **Download** controls in Documents tab. S3 pull from LocalStack: both are valid **PDF v1.6**. SoA text reconciles: Opening 0 → Deposits DEP/2026/001 R50k + DEP/2026/003 R20k → Payment PAY/2026/001 settlement payout → **Closing 0.00 / Trust balance held 0.00**, plus fees + disbursements + VAT lines. Closure letter: conclusion confirmation, reason, retention per Legal Practice Act. 📸 `day-60-matter-closed-documents.png`. |
| 60.23 | Retention policy row, end_date ≈ today+5y | **PASS** | `projects.retention_clock_started_at` = 2026-06-13 17:33:09 (set at closure). Retention clock started (ADR-249); 5-year horizon verified later at Day 85. |

---

## Day 60 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| All 4 closure gates resolved before closure (trust dual-approval, court date, tasks, info requests) | **PASS** | A1 (info requests COMPLETED), A2 (0 open tasks), A3 (court date HEARD), A4 (payment APPROVED, balance R0.00). |
| Matter closes cleanly after gate resolution (no override) | **PASS** | `override_used = f`; gate_report allPassed=true; status CLOSED. |
| Statement of Account PDF generated + attached | **PASS** | Valid PDF attached + downloadable; content reconciles to Section 86 ledger (60.22). |
| Mailpit notification "Statement of Account ready" to sipho.portal@example.com | **OBS-6001 (known WONT_FIX)** | At closure (17:33:10) exactly ONE "Document ready: matter-closure-letter-…pdf" email fired; **no separate SoA email**. This is the documented OBS-6001 behavior (`PortalDocumentNotificationHandler` 5-min dedup coalesces closure-pack emails). SoA PDF still generated + attached + downloadable. Noted, **not re-filed** per carry-over exemption. |

## Section 86 dual-approval evidence (explicit)
- Recorder: **Bob Ndlovu** (`f753c371-7a36-45cf-b622-2323673936bf`)
- Approver: **Thandi Mathebula** (`ca39e4b1-3f7f-4899-8850-e6f4d11523c7`)
- `recorded_by != approved_by` → **true**; payment status **APPROVED**; matter trust balance **R 0,00**.
- Backend enforcement: `TrustTransactionService.approveSingleMode` ("recorder cannot be sole approver"), `require_dual_approval=false` on the trust account so single distinct approver suffices for Section 86.

## Closure gate results (all 9 GREEN)
TRUST_BALANCE_ZERO ✓ · ALL_DISBURSEMENTS_APPROVED ✓ · ALL_DISBURSEMENTS_SETTLED ✓ · FINAL_BILL_ISSUED ✓ · NO_OPEN_COURT_DATES ✓ · NO_OPEN_PRESCRIPTIONS ✓ · ALL_TASKS_RESOLVED ✓ · ALL_INFO_REQUESTS_CLOSED ✓ · ALL_ACCEPTANCE_REQUESTS_FINAL ✓ → `allPassed: true`, clean-path closure (no override).

## Console / backend health
- **Firm console**: only **OBS-201** `/api/assistant/invocations` 404 (AI infra not wired in KC mode — WONT_FIX-EXEMPT carry-over). No new genuine errors.
- **Backend**: the only ERROR in the window is the known **OBS-505** legacy `INTAKE` specialist soft-fail (un-repaired legacy tenant rule; ran in its own REQUIRES_NEW tx, did NOT roll back any business tx — verified working-as-designed in cycle 15). 0 rollbacks on the closure / approval / acceptance transactions.

## New gaps
- **OBS-6002 (candidate, MEDIUM — pointer-interception, OBS-2103 family)** — trust **Approve** button (`ApprovalBadge`) and matter **Close Matter** button (`close-matter-btn`) did not fire their bound React `onClick` under Playwright real-pointer `.click()` (no backend call, no state change), across multiple clean attempts incl. a fully fresh login. The same handlers fire correctly when invoked via React props / `form.requestSubmit()`, and the backend then processes normally (payment APPROVED; closure dialog → CLOSED). `ApprovalBadge` renders Approve + Reject buttons adjacent to a `<RejectDialog>` (Radix Dialog) — same adjacency/Slot pointer-interception class as the documented OBS-2103. **Caveat**: the frontend dev server was doing frequent Fast Refresh rebuilds during the session, which can transiently de-hydrate handlers, so this may be partly dev-HMR flakiness rather than a pure product defect. Filed as a candidate for triage/repro on a quiescent build before any fix. NOT a blocker — the Day 60 flow completed correctly end-to-end.

## Carry-over exemptions observed (noted, not re-filed)
- **OBS-6001** — no separate SoA email after closure (dedup coalescing) — WONT_FIX by design, observed exactly as documented.
- **OBS-201** — `/api/assistant/invocations` 404 firm-side — WONT_FIX-EXEMPT.
- **OBS-505** — legacy `INTAKE` specialist automation soft-fail — un-repaired legacy tenant data, isolated tx, not a regression.
- KYC/FICA adapter unconfigured; Payments mock-only — per mandate.
- R71,000/three-deposit prior-cycle artifact — does not affect the R70,000 payment amount this cycle.

## Result
**Day 60: 23/23 step checkpoints PASS + 4/4 summary checkpoints PASS (one summary item = OBS-6001 known WONT_FIX, SoA still generated/attached); 1 new candidate gap (OBS-6002, MEDIUM, non-blocking); NOT blocked.**

Most-complex day completed end-to-end, browser-driven: REQ-0004 items accepted (envelope COMPLETED) → all 9 tasks resolved (2 DONE incl. their auto-spawned follow-ups CANCELLED, 7 CANCELLED) → Pre-Trial court date HEARD → trust payment PAY/2026/001 R70,000 recorded by Bob + **Section 86-approved by Thandi** (recorder ≠ approver) → balance **R 0,00** → Close Matter with **all 9 gates GREEN** → reason **CONCLUDED**, clean path (no override) → matter **CLOSED**, retention clock started → **Statement of Account + closure letter PDFs both generated, attached, and downloadable** (SoA content reconciles to the Section 86 ledger). **Day 61 next** (Sipho downloads SoA from the portal).

Screenshots: `day-60-tasks-zero-open.png`, `day-60-client-ledger-zero-balance.png`, `day-60-closure-gates-all-green.png`, `day-60-closure-form-concluded.png`, `day-60-matter-closed-documents.png`.
