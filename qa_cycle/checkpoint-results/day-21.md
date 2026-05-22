# Day 21 Checkpoint Results — Legal ZA Lifecycle (Keycloak)

**Date**: 2026-05-21
**Actor**: Bob Ndlovu (bob@mathebula-test.local)
**Stack**: Keycloak dev stack (:3000 / :8080 / :8443 / :8180)
**Matter**: RAF-2026-001 — Dlamini v Road Accident Fund (ACTIVE)

---

## Phase A: Time Entry (non-tariff free-text path)

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 21.1 | Navigate to Work > Tasks tab on RAF-2026-001 | PASS | Grouped tab bar Work > Tasks showed 9 RAF template tasks; URL param `?tab=tasks` |
| 21.2 | Click Log Time on "Initial RAF claim assessment & instructions" — dialog shows Duration / Date / Description / Billable (no tariff dropdown) | PASS | Dialog opened with Duration (h/m spinners), Date (2026/05/21), Description (textarea), Billable (checkbox, pre-checked). No tariff dropdown — expected per OBS-2101 WONT_FIX |
| 21.3 | Enter Duration=2h 30m, Date=today, Description="Initial consultation with Sipho — RAF claim assessment, intake narrative, instructions on quantum", Billable=Yes. Rate-preview banner shows yellow "No rate card found" warning | PASS | All fields filled. Yellow warning: "No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates." — expected behavior |
| 21.4 | Submit — time entry saved on task. Entry saved at R 0 (no rate card) | PASS | Dialog closed without error. Finance > Time tab confirmed: Total Time 4h, Billable 4h, 2 entries, 1 contributor (Bob Ndlovu). Task "Initial RAF claim assessment & instructions" shows 2h 30m |
| 21.5 | Log time on second task "File RAF1 claim form + supporting documents" — Duration=1h 30m, Description="Drafted particulars of claim incl. quantum schedule" | PASS | Dialog opened, filled, submitted. Finance > Time tab confirmed second entry: 1h 30m on "File RAF1 claim form + supporting documents". Total now 4h across 2 entries |

**Phase A summary**: Both time entries posted successfully via non-tariff path. "No rate card found" warning displayed correctly — product warns user as designed. OBS-2101 WONT_FIX confirmed (no tariff-time-entry integration). Overview metric updated to 4.0h HOURS LOGGED.

---

## Phase B: Disbursement

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 21.6 | Navigate to Finance > Disbursements > + New Disbursement | PASS | Disbursement dialog opened with fields: Matter (pre-filled), Client, Category, Description, Amount, VAT Treatment, Payment Source, Incurred Date, Supplier, Supplier Reference, Receipt |
| 21.7 | Fill: Category=Sheriff Fees, Amount=R 1,250.00, Date=today, Description="Sheriff service of summons on RAF", Supplier="Sheriff Sandton" | PASS | All fields filled. Category auto-switched VAT Treatment to "Zero-rated pass-through" for Sheriff Fees (correct: sheriff fees are pass-through costs) |
| 21.8 | Mark as recoverable (client-rebillable) | PARTIAL | No explicit "recoverable" checkbox in the dialog. Client disbursements are implicitly recoverable (recorded against client/matter). BILLING status shows "Unbilled" confirming it will feed into Day 28 fee note. Not a blocker — functional behavior is correct. |
| 21.9 | Submit — disbursement saved, appears in Disbursements tab with status UNBILLED | PASS | Disbursement row: Date 2026-05-21, Category Sheriff Fees, Description "Sheriff service of summons on RAF", Supplier "Sheriff Sandton", Amount R 1,250.00, Approval Draft, Billing Unbilled |

**Phase B summary**: Disbursement created successfully. Sheriff Fees category with R 1,250.00. BILLING=Unbilled confirms it will be available for Day 28 fee note. No explicit "recoverable" toggle — implicit via client/matter association.

---

## Phase C: Court Date

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 21.10 | Navigate to Schedule tab > + New Court Date | PASS | Schedule tab shows "Court Dates" section with "+ New Court Date" button. Single-tab group (no dropdown) |
| 21.11 | Fill: Court=Gauteng Division, Pretoria, Date=2026-06-04 (Day 35), Type=Pre-Trial, Description="Pre-trial conference - Bob Ndlovu attending" | PASS | All fields filled. Type dropdown includes PRE_TRIAL (maps to "Pre-Trial"). Reminder defaults to 7 days before. Note: no explicit "Attorney attending" field — added Bob Ndlovu in Description |
| 21.12 | Submit — court event saved, visible on Court Calendar page and matter Overview | PASS | (a) Matter Schedule tab: row with Date 2026-06-04, Type Pre-Trial, Court Gauteng Division Pretoria, Matter Dlamini v Road Accident Fund, Client Sipho Dlamini, Status Scheduled (green). (b) Court Calendar page: same entry, "1 court dates, 1 upcoming". (c) Matter Overview: "Upcoming Deadlines" section shows "Court - Gauteng Division, Pretoria - Jun 4" |

**Phase C summary**: Court date created and visible on all three surfaces: matter Schedule tab, firm-wide Court Calendar page, and matter Overview "Upcoming Deadlines".

---

## Console Errors

Zero JavaScript/Next.js console errors observed throughout Day 21 execution.

---

## Day 21 Checkpoint Summary

| Checkpoint | Status |
|-----------|--------|
| Time entries post (non-tariff path — duration + yellow "No rate card found" warning) — OBS-2101 WONT_FIX | PASS |
| Disbursement recorded with UNBILLED status (feeds Day 28 fee note) | PASS |
| Court date added, visible on calendar + matter | PASS |
| Zero console errors | PASS |

**Overall Day 21 Result: PASS** — all three phases completed successfully. Zero new gaps. One minor observation: no explicit "recoverable" toggle on disbursement dialog (checkpoint 21.8 PARTIAL) — functional behavior is correct since client disbursements are implicitly recoverable.

---

## New Gaps

None. No new OBS-* gaps filed for Day 21.
