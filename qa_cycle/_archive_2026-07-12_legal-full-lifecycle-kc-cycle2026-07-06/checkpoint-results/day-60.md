# Day 60 — Firm matter closure + Statement of Account `[FIRM]` — 2026-07-06

**Actors**: Thandi Mathebula (Owner) + Bob Ndlovu (Admin). Three context swaps (cookie clear + fresh KC login each): Thandi (record payout) → Bob (gates A1–A4, first approval) → Thandi (second approval + closure).

**Pre-step (scenario gap, not in script)**: 60.12 presumes PAY/2026/001 already "recorded by Thandi". No prior day records it, so Thandi recorded it first: Trust Accounting → Record Transaction → Record Payment — Client Sipho Dlamini, Matter Dlamini v RAF, **R 70 000** (cycle-local figure; script's R 71 000 includes the phantom cycle-15 R 1 000), ref **PAY/2026/001**, desc "Trust payout to client on matter conclusion…" → row **AWAITING APPROVAL**.

## Phase A — gate resolution (Bob)

| # | Result | Evidence |
|---|--------|----------|
| 60.1 | PASS | Matter → Client > Requests: REQ-0001 Completed 3/3, REQ-0003 In Progress 0/2 accepted → opened REQ-0003 detail |
| 60.2 | PASS | Both items **Submitted** with files `hospital-discharge-summary.pdf` / `orthopaedic-report.pdf` (Day 46 uploads), Accept/Reject buttons per item |
| 60.3 | PASS | Accept item 1 → "Hospital discharge summary — Accepted" |
| 60.4 | PASS | Accept item 2 → both Accepted, envelope auto-transitioned: header badge **Completed**, "2/2 accepted", "Completed on 6 Jul 2026" |
| 60.5 | PASS | REQ-0003 = COMPLETED; REQ-0001 already COMPLETED (Day 5). Mailpit: 2× "Item accepted" + "Request REQ-0003 completed" emails to sipho.portal@ (15:09Z) |
| 60.6 | PASS | Work > Tasks: all 9 RAF-template tasks listed Open |
| 60.7 | PASS + **LZKC-013** | Task status is a staged dropdown (Open → In Progress → Done; Done not offered from Open; Cancelled offered from any). Set both time-logged tasks Done ("Initial RAF claim assessment & instructions" 2h30m, "File RAF1 claim form…" 1h30m), cancelled the other 7. **Product surprise**: each Done fired the default-on `task-completion-chain` automation (`backend/src/main/resources/automation-templates/common.json` slug `task-completion-chain`, trigger TASK_STATUS_CHANGED→DONE, action CREATE_TASK "Follow-up: {{task.name}}" assigned PROJECT_OWNER) → 2 auto-spawned IN_PROGRESS "Follow-up:" tasks that would block the closure gate. Cancelled both via UI |
| 60.8 | PASS | DB (read-only): 11 tasks — 2 DONE, 9 CANCELLED, zero OPEN/IN_PROGRESS. UI tasks tab shows empty list under default Open+In Progress filter (copy quirk: generic "No tasks yet" empty state) |
| 60.9 | PASS (cycle-local date) | Schedule tab: Pre-Trial **2026-07-20** Gauteng Division, Pretoria, Scheduled (script's "Jun 4 2026" is stale text) |
| 60.10 | PASS | Row Actions → menu (Edit / Postpone / Cancel / **Record Outcome**) → Record Outcome dialog → outcome text recorded → status **Heard** |
| 60.11 | PASS | Row no longer Scheduled (Heard); closure gate later confirms "No court dates scheduled for today or later" |
| 60.12 | PASS (with pre-step) | PAY/2026/001 R 70 000,00 AWAITING APPROVAL in transactions list (recorded by Thandi — DB `recorded_by`=Thandi) |
| 60.13 | PASS (product shape: TWO approvers) | Bob clicked Approve → approval registered (DB `approved_by`=Bob 15:16:31Z) — **but with zero UI feedback** (row stayed AWAITING APPROVAL, no toast, no 1/2 indicator → LZKC-016). Bob's second click surfaced inline "Second approver must be different from the first" — R 70 000 payment requires dual approvers, recorder-not-sole-approver honoured |
| 60.14 | PASS | Thandi (second approver, recorder but not sole) clicked Approve → row **APPROVED** (action column now "Reverse"). Sipho trust balance → R 0,00 |
| 60.15 | PASS | Client Ledgers: Sipho Dlamini — Trust Balance **R 0,00**, Total Deposits R 70 000,00, Total Payments R 70 000,00. Moroka untouched R 25 000,00 |

## Phase B — gate report (Thandi)

| # | Result | Evidence |
|---|--------|----------|
| 60.16 | PASS (location note) | Close Matter is a **matter-header button** (alongside Complete Matter), not the scripted sidebar-footer `sidebar-lifecycle-action` — scenario text stale; button found and worked |
| 60.17 | PASS — **all 9 gates GREEN** | Dialog "Close matter": trust balance R0.00 · all disbursements approved · approved disbursements settled · final bill issued no unbilled items · no court dates today or later · no prescription timers running · all tasks resolved · all info requests closed · no document acceptances pending. 📸 `day-60-closure-gates-green.png` |

## Phase C — closure

| # | Result | Evidence |
|---|--------|----------|
| 60.18 | PASS | Continue → Step 2 close form |
| 60.19 | PASS | Reason **Concluded** (default; options Concluded / Client terminated / Referred out / Other), notes added |
| 60.20 | PASS | Both flags present and checked: "Generate closure letter" + "Generate Statement of Account (Section 86 ledger reconciliation)" |
| 60.21 | PASS | Confirm → matter header **Closed** badge, Reopen Matter button, closure history "6 Jul 2026 · Concluded" |
| 60.22 | PASS + **LZKC-014** | Work > Documents: `matter-closure-letter-dlamini-v-road-accident-fund-2026-07-06.pdf` (1.6 KB) + `statement-of-account-dlamini-v-road-accident-fund-2026-07-06.pdf` (5.0 KB), both Uploaded, both `visibility=PORTAL` (DB). Closure history renders "Closed by 0768ccd3-8ebd-4d35-880f-ecb0bcf9f0d8" — **raw member UUID instead of "Thandi Mathebula"** |
| 60.23 | PASS (product shape) | DB: `projects.retention_clock_started_at = 2026-07-06 15:19:26`; active retention policy MATTER / **1825 days (5y)** / MATTER_CLOSED / ARCHIVE (ADR-249 mechanism present). No computed end-date because org `legal_matter_retention_years` is unset — Overview shows accurate guidance banner "retention period isn't configured… Configure retention period →". Scenario's literal "end_date = today + 5 years" row doesn't exist as such; clock+policy model instead |

## Day 60 day-level checkpoints

- All 4 closure gates resolved before closure (trust dual-approval, court date, tasks, info requests): **PASS**
- Matter closes cleanly, no override: **PASS**
- Statement of Account PDF generated + attached: **PASS**
- Notification email "Your Statement of Account is ready": **FAIL → LZKC-015** — only the closure letter got a "Document ready: matter-closure-letter-…" email (15:19:26Z, Mailpit `TVipfc75zA7KqXbLid62Be`); no email for the sibling SoA document (confirmed after 20s wait). Also present: "Trust account activity" (payout) email 15:18:13Z |

## New gaps

- **LZKC-013 (Low)** — `task-completion-chain` automation is default-on: every task marked Done auto-spawns an IN_PROGRESS "Follow-up:" task assigned to project owner, directly fighting the closure Open-Tasks gate (user must cancel each follow-up to close a matter).
- **LZKC-014 (Low)** — Matter closure history shows raw member UUID ("Closed by 0768ccd3-…") instead of member name.
- **LZKC-015 (Low)** — SoA generated at closure gets no "Document ready" email while the closure letter does — inconsistent sibling-document notification; scenario expects an SoA-ready email.
- **LZKC-016 (Medium)** — Trust dual-approval UX: first Approve registers silently (no toast, no status change, no "1/2 approvals" indicator, no confirm dialog); row still shows AWAITING APPROVAL with an active Approve button; the only feedback is an inline error when the same user clicks again.

## Notes for Day 61

- No SoA email exists — Day 61.1 must use the closure-letter "Document ready" email link, or navigate the portal directly to the matter Documents.
- SoA doc: `statement-of-account-dlamini-v-road-accident-fund-2026-07-06.pdf`, 5.0 KB firm-side, PORTAL visibility.
- Trust: 2 deposits (50k, 20k) + 1 payment out (70k), closing balance R 0.00; fee note INV-0001 R 1 250 PAID (Day 30).
