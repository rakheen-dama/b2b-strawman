# Day 60 — Firm matter closure + Statement of Account `[FIRM]` — cycle 2026-07-12 (executed 2026-07-13)

**Actors**: Bob Ndlovu (gates A1–A3, sole trust approval) + Thandi Mathebula (payment record, closure). Three KC context swaps (cookie-clear + fresh two-step login each): Bob (standing session, A1–A3) → Thandi (record PAY/2026/001) → Bob (approve) → Thandi (closure).

**Pre-step (scenario gap, same as prior cycle)**: 60.12 presumes PAY/2026/001 already recorded. Thandi recorded it: Trust Accounting → Transactions → Record Transaction → Record Payment — Client Sipho Dlamini, Matter Dlamini v Road Accident Fund, **R 70 000** (cycle-correct figure, not scripted R 71 000), ref **PAY/2026/001** → row AWAITING APPROVAL. DB `recorded_by` = Thandi Mathebula (`619a21e7…`).

## Phase A — gate resolution

| # | Result | Evidence |
|---|--------|----------|
| 60.1 | PASS | Matter → Client > Requests: REQ-0001 Completed 3/3 accepted, REQ-0003 In Progress 0/2 accepted → opened REQ-0003 detail (`9deb64e0…`) |
| 60.2 | PASS | Both items **Submitted** with Day-46 files `hospital-discharge-summary.pdf` / `orthopaedic-report.pdf`, Accept/Reject per item |
| 60.3 | PASS | Accept item 1 → "Hospital discharge summary — Accepted", progress 1/2 accepted |
| 60.4 | PASS | Accept item 2 → both Accepted, envelope auto-transitioned: header **Completed**, "2/2 accepted", "Completed on 13 Jul 2026" |
| 60.5 | PASS | DB: REQ-0001 + REQ-0003 both COMPLETED. Mailpit: 2× "Item accepted" (00:58:57Z, 00:59:15Z) + "Request REQ-0003 completed" (00:59:15Z) to sipho.portal@. Note: these client emails link to `http://localhost:3000/portal` — carried-forward latent find h (InformationRequestEmailService legacy embedded /portal), re-observed, NOT re-filed |
| 60.6 | PASS | Work > Tasks: all 9 RAF-template tasks listed, all Open |
| 60.7 | PASS — **LZKC-013 fix HOLDS** | Staged dropdown confirmed (from Open: Open/In Progress/Cancelled — no Done; from In Progress: +Done). "Initial RAF claim assessment & instructions" (2h30m) and "File RAF1 claim form…" (1h30m) → Open→In Progress→Done; other 7 → Cancelled. **Zero "Follow-up:" tasks auto-spawned on either Done transition** (DB checked after each: task count stayed 9). Post-fix tenant behaves correctly |
| 60.8 | PASS | DB: 2 DONE + 7 CANCELLED, zero OPEN/IN_PROGRESS. UI default Open+In Progress filter shows empty list (known "No tasks yet" generic empty-state copy quirk, re-observed) |
| 60.9 | PASS | Schedule tab: Pre-Trial **2026-07-26** Gauteng Division, Pretoria, **Scheduled** (harness: tab needs separated mouse down/pause/up AND a URL without `?tab=` param — with `?tab=tasks` present the param kept re-forcing the tasks panel) |
| 60.10 | PASS | Row Actions → Edit/Postpone/Cancel/**Record Outcome** → dialog → outcome text recorded → status **Heard**. DB: status HEARD, outcome populated |
| 60.11 | PASS | Row no longer Scheduled; closure gate later shows "No court dates scheduled for today or later" green |
| 60.12 | PASS (with pre-step) | Bob's view: PAY/2026/001 · Payment · R 70 000,00 · AWAITING APPROVAL with Approve/Reject |
| 60.13 | PARTIAL → **LZKC-029** | Bob clicked Approve → toast "Transaction approved" → row went **directly to APPROVED** (action column "Reverse") on a SINGLE approval. Root cause (DB, read-only): this cycle's trust account has `require_dual_approval = false`, `payment_approval_threshold = NULL` — scenario Day 1 step 1.5 never instructs enabling dual approval; prior cycle's QA had enabled it beyond-script ("dual approval enabled" in archive day-01.md), this cycle's Day 1 followed the script literally. Product behaved correctly for its config. Consequence: Section 86 dual-approval leg (two approvers, 1-of-2 feedback) NOT exercisable this cycle |
| 60.14 | PASS (single-approval path) | Payment APPROVED (approved_by = Bob `24257f0c…` 01:11:55Z, second_approved_by NULL). Trust balance → R 0,00 |
| 60.15 | PASS | Client Ledgers: Sipho Dlamini — Balance **R 0,00**, Total Deposits R 70 000,00, Total Payments R 70 000,00, last transaction 13 Jul 2026. Moroka Family Trust untouched R 25 000,00. 📸 `day-60-client-ledgers-zero-balance.png` |

## Phase B — gate report (Thandi)

| # | Result | Evidence |
|---|--------|----------|
| 60.16 | PASS (location note, same as prior cycle) | Close Matter is a matter-header button (alongside Complete Matter), not the scripted sidebar-footer — scenario text still stale |
| 60.17 | PASS — **all 9 gates GREEN** | "Close matter" dialog: trust balance R0.00 · all disbursements approved · approved disbursements settled · final bill issued no unbilled items · no court dates today or later · no prescription timers · all tasks resolved · all info requests closed · no document acceptances pending — every gate green check. 📸 `day-60-closure-gates-green.png` |

## Phase C — closure

| # | Result | Evidence |
|---|--------|----------|
| 60.18 | PASS | Continue → Step 2 close form |
| 60.19 | PASS | Reason **Concluded** (default; options Concluded / Client terminated / Referred out / Other), notes filled |
| 60.20 | PASS | Both flags present and pre-checked: "Generate closure letter" + "Generate Statement of Account (Section 86 ledger reconciliation)" |
| 60.21 | PASS | Close matter → header **Closed** badge, **Reopen Matter** button, closure history "13 Jul 2026 · Concluded". 📸 `day-60-matter-closed.png` |
| 60.22 | PASS — **LZKC-014 fix HOLDS** | Work > Documents: `matter-closure-letter-…-2026-07-13.pdf` (2.2 KB) + `statement-of-account-…-2026-07-13.pdf` (5.5 KB), both Uploaded, both `visibility=PORTAL` (DB). Closure history renders "**Closed by Thandi Mathebula**" — name, no raw UUID |
| 60.23 | PASS (product shape, unchanged) | DB `projects.retention_clock_started_at = 2026-07-13 01:15:27`; status CLOSED. Overview banner: "Retention clock started on 13 Jul 2026. Your firm's matter-retention period isn't configured yet…" + "Configure retention period →" link (org `legal_matter_retention_years` unset — same accurate guidance as prior cycle) |

## Day-level checkpoints

- All closure gates resolved before closure attempt: **PASS** (trust approval single-leg per config — see LZKC-029; court date Heard; tasks resolved; info requests completed)
- Matter closes cleanly, no override: **PASS**
- Statement of Account PDF generated + attached: **PASS**
- Notification emails: **PASS — LZKC-015 fix HOLDS**: TWO "Document ready" emails at 01:15:27Z — closure letter (`nuTgde32whYBwfg3Xs6pnk`) AND statement of account (`9F7ec4bYThN66Rfpode8EH`), both to sipho.portal@example.com

## Fix re-verifications

| Fix | Status |
|-----|--------|
| LZKC-013 (no auto Follow-up on Done) | **HOLDS** — verified on both Done transitions, DB-confirmed |
| LZKC-014 (closure history shows name) | **HOLDS** — "Closed by Thandi Mathebula" |
| LZKC-015 (two Document-ready emails) | **HOLDS** — closure letter + SoA emails both fired |
| LZKC-016 (dual-approval 1-of-2 feedback) | **NOT TESTABLE this cycle** — dual approval not configured on the account (LZKC-029); single-approval path gave correct "Transaction approved" toast |
| LZKC-022 (email deep links) | Day-60 leg: all 3 info-request emails → known latent find h (:3000/portal, carried-forward); trust-activity email → correct `:3002/trust/{matterId}`; both doc-ready emails → correct `:3002/projects/{matterId}` portal links. No firm-side notification email fired this day. Click-through due Day 61.1 |

## New gaps

- **LZKC-029 (Medium)** — Section 86 dual-approval never engages on a script-faithful run: scenario Day 1 (step 1.5) omits enabling dual approval on the trust account, and the product defaults `require_dual_approval=false` / no threshold even for SECTION_86 accounts, so the R 70 000 trust payout was approvable by a single approver (Bob) — the scripted "Section 86 dual-approval" constraint (60.13) and the LZKC-016 approval-feedback UX are silently skipped. Two decisions needed (Product): (a) amend scenario Day 1 to enable dual approval + set threshold (scenario amendment — requires authorization); (b) consider whether SECTION_86-type accounts should default dual approval on (compliance posture, LPA s86). Filed OPEN / Owner Product.

## Notes for Day 61

- SoA doc: `statement-of-account-dlamini-v-road-accident-fund-2026-07-13.pdf`, 5.5 KB firm-side, PORTAL visibility.
- This cycle a real **SoA "Document ready" email exists** (LZKC-015 fixed) — use it for 61.1 click-through (Mailpit `9F7ec4bYThN66Rfpode8EH`).
- Trust: 2 deposits (50k Day 10, 20k Day 45) + 1 payment out (70k Day 60), closing balance R 0.00; INV-0001 R 1 250 PAID (Day 30).
- SoA content: expect LZKC-017 pt1 fix (letterhead logo + uniform ZAR locale); Payment Instructions/banking + VAT Reg blank = known deferred pt2, do NOT re-file.
- Closure letter: expect LZKC-018 fix (Date, Reason, fees, disbursements, duration populated).

## Console

0 application errors across all Day-60 interactions (only `localhost:8080/favicon.ico` 401 — off-app origin, cosmetic).
