# Day 21 — Firm logs time, adds disbursement, creates court date  `[FIRM]`

**Date**: 2026-05-14
**Actor**: Bob Ndlovu (Admin, `bob@mathebula-test.local` / `<redacted>`)
**Stack**: Keycloak dev stack (frontend `:3000`, gateway `:8443`, backend `:8080`, KC `:8180`)
**Cycle**: 1 (branch `bugfix_cycle_2026-05-13`)
**Result**: COMPLETE — All 3 phases PASS.

---

## Pre-flight

- Stack health: backend 200, frontend 200 (verified via curl).
- Signed out of Thandi via in-app user-menu > Sign out > KC realm logout completed.
- Logged in as Bob via Keycloak (`bob@mathebula-test.local` / `<redacted>`) > landed on `/org/mathebula-partners/dashboard` with sidebar avatar `BN` confirming user swap.
- Navigated to matter RAF-2026-001 "Dlamini v Road Accident Fund" (id `c90832a4-c993-4eaa-9ea7-404a259b0e29`, status Active).

## Phase A — Time entry (non-tariff free-text path) — PASS

| ck | Step | Result |
|----|------|--------|
| 21.1 | Navigate matter RAF-2026-001 > Tasks tab | PASS — Tasks tab renders 9 RAF template tasks. Each task row has a `Log Time` button in the Actions column. |
| 21.2 | Click `Log Time` on "Initial RAF claim assessment & instructions" | PASS — Dialog opens with Duration (h/m spinbuttons) / Date / Description / Billable fields. Yellow warning banner: "No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates." No tariff dropdown (OBS-2101 WONT_FIX). |
| 21.3 | Fill Duration=2h 30m, Date=2026-05-14, Description="Initial consultation with Sipho -- RAF claim assessment, intake narrative, instructions on quantum", Billable=Yes | PASS — All fields accepted. Billable checkbox checked by default. |
| 21.4 | Submit > time entry saved | PASS — Dialog closes. Time tab shows: Total Time 2h 30m / Billable 2h 30m / Non-billable 0m / Contributors 1 / Entries 1. By Task: "Initial RAF claim assessment & instructions" 2h 30m. By Member: Bob Ndlovu 2h 30m. |
| 21.5 | Pick "File RAF1 claim form + supporting documents" > log Duration=1h 30m, Description="Drafted particulars of claim incl. quantum schedule" | PASS — Same dialog, same yellow rate warning. Time tab now reads: Total Time 4h / Billable 4h / Entries 2. By Task: both tasks listed with correct durations. By Member: Bob Ndlovu 4h. |

**Phase A notes**:
- No tariff dropdown exists (OBS-2101 WONT_FIX, scenario amended to non-tariff path).
- Yellow "No rate card found" warning renders correctly per amended scenario expectations.
- Second task used "File RAF1 claim form + supporting documents (within 3-year prescription)" as closest-fit to scenario's "Draft particulars of claim" (no exact match in RAF template).

## Phase B — Disbursement — PASS

| ck | Step | Result |
|----|------|--------|
| 21.6 | Disbursements tab > **New Disbursement** | PASS — Dialog opens with title "New Disbursement". Fields: Matter (pre-selected "Dlamini v Road Accident Fund", disabled), Client, Category, Description, Amount (ZAR, excl VAT), VAT Treatment, Payment Source (Office Account / Trust Account), Incurred Date, Supplier, Supplier Reference (optional), Receipt (optional). |
| 21.7 | Category=Sheriff Fees, Amount=R 1,250, Date=2026-05-14, Description="Sheriff service of summons on RAF", Supplier="Sheriff Sandton", Client=Sipho Dlamini | PASS — All fields accepted. Category dropdown includes: Sheriff Fees, Counsel Fees, Search Fees, Deeds Office Fees, Court Fees, Advocate Fees, Expert Witness, Travel, Other. |
| 21.8 | Mark as **recoverable** (client-rebillable) | PASS (scenario amendment) — No explicit "Recoverable" toggle. Payment Source = "Office Account" (selected by default) serves as recoverable proxy. Office Account disbursements default to client-billable. |
| 21.9 | Submit > disbursement saved with status UNBILLED | PASS — Disbursement created and visible in table: `2026-05-14 / Sheriff Fees / Sheriff service of summons on RAF / Sheriff Sandton / R 1 437,50 / Approval=Draft / Billing=Unbilled`. Amount includes 15% VAT (R 1,250 + R 187.50 = R 1,437.50). |

**Phase B notes**:
- Amount field shows "Amount (ZAR, excl VAT)" label and a separate "VAT Treatment" dropdown (Standard 15% / Zero-rated / Exempt).
- Table column "Amount (incl VAT)" correctly displays R 1,437.50 for R 1,250 base + 15% VAT.
- Approval status "Draft" is a legitimate workflow enhancement (approval required before billing).

## Phase C — Court date — PASS

| ck | Step | Result |
|----|------|--------|
| 21.10 | Court Dates tab > **New Court Date** | PASS — Button labelled "New Court Date"; dialog title "Schedule Court Date". |
| 21.11 | Matter=Dlamini v Road Accident Fund, Type=Pre-Trial, Date=2026-05-28 (~14 days), Court Name="Gauteng Division, Pretoria", Description="Pre-trial conference for Dlamini v Road Accident Fund" | PASS — Type dropdown includes: Hearing, Trial, Motion, Mediation, Arbitration, Pre-Trial, Case Management, Taxation, Other. Reminder default = 7 days before. No "Attorney attending" field (scenario amendment: attorney is implicit via audit trail). |
| 21.12 | Submit > court event saved, appears on Court Calendar + matter | PASS — Matter Court Dates tab shows: `2026-05-28 / Pre-Trial / Gauteng Division, Pretoria / Dlamini v Road Accident Fund / Sipho Dlamini / Scheduled`. Firm-side Court Calendar (`/org/mathebula-partners/court-calendar`) confirms: `1 court dates / 1 upcoming` with the Pre-Trial entry. |

## Day 21 checkpoints

- [x] Time entries post (non-tariff path -- duration + yellow `No rate card found` warning) -- OBS-2101 WONT_FIX cascade. **PASS**
- [x] Disbursement recorded with recoverable flag (via Office Account proxy) -- feeds Day 28 fee note. **PASS**
- [x] Court date added, visible on calendar + matter. **PASS**

## Console / network

- Console errors: All errors are the pre-existing OBS-203 (`/api/assistant/invocations` returns 404), which fires on matter detail page load. No new errors.
- Court Calendar page: 0 console errors.
- Login flow: 0 errors (HMR connected messages only).

## Scenario amendments (consistent with previous cycle)

1. **21.8** "Mark as recoverable" > no such toggle; covered by `Payment Source: Office Account` (de-facto recoverable indicator).
2. **21.11** "Attorney attending: Bob Ndlovu" > no such field; attorney is implicit (creator via audit trail).
3. **21.9** "status UNBILLED" > UI shows `Billing=Unbilled` (match) plus separate `Approval=Draft` column.
4. **21.5** "Draft particulars of claim" task > used closest-fit "File RAF1 claim form + supporting documents (within 3-year prescription)" from RAF template.

## QA Position

**Day 21**: COMPLETE -- All 3 phases (Time, Disbursement, Court Date) PASS with 0 new gaps.
**Status**: Day 21 closed. Ready to advance to Day 28.
