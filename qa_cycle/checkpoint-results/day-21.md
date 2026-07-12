# Day 21 — Firm logs time, adds disbursement, creates court date `[FIRM]` — Cycle 2026-07-12

**Actor**: Bob Ndlovu (Admin) — cookies cleared, fresh Keycloak login (Sipho's :3002 localStorage session unaffected).

## Phase A — time entries (non-tariff path, OBS-2101 WONT_FIX)

| # | Result | Evidence |
|---|--------|----------|
| 21.1 | PASS | Matter Work > Tasks (`?tab=tasks`): all 9 RAF-template tasks Open, each row with canonical **Log Time** CTA |
| 21.2 | PASS | "Initial RAF claim assessment & instructions" → Log Time dialog: Duration (h/m), Date (pre-filled today), Description, Billable checkbox — no tariff dropdown (as amended) |
| 21.3 | PASS | 2h 30m, 2026-07-12, full consult description, Billable on. Yellow warning rendered: "No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates." (acceptable path per script) |
| 21.4 | PASS | Entry saved; matter Finance > Time: TOTAL 4h / BILLABLE 4h / ENTRIES 2; By Task "Initial RAF claim assessment & instructions 2h 30m (1)"; By Member Bob Ndlovu 4h |
| 21.5 | PASS (same task-name note as prior cycle) | Second entry 1h 30m "Drafted and filed RAF1 claim form incl. quantum schedule" on **"File RAF1 claim form + supporting documents (within 3-year prescription)"** (script's "Draft particulars of claim" doesn't exist in the RAF template; RAF1 task keeps Day 60 consistent). Time tab row 1h 30m (1 entry) |

## Phase B — disbursement (Phase 67)

| # | Result | Evidence |
|---|--------|----------|
| 21.6 | PASS | Finance > Disbursements (`?tab=disbursements`) → New Disbursement dialog (matter pre-bound Dlamini v RAF) |
| 21.7 | PASS | Category **Sheriff Fees**, Client Sipho Dlamini, Amount 1250 (ZAR excl VAT, Standard 15%), Description "Sheriff service of summons on RAF", Supplier "Sheriff Pretoria Central", Incurred 2026-07-12, Payment Source Office Account |
| 21.8 | PASS (same product-shape note) | No explicit "recoverable" checkbox — recoverability is the default model (Billing column Unbilled → billed) |
| 21.9 | PASS | Row: 2026-07-12 · Sheriff Fees · R 1 250,00 · Approval **Draft** · Billing **Unbilled**. Day 28 pre-condition note (OBS-2104): Submit for Approval + Approve before the billing wizard |

## Phase C — court date

| # | Result | Evidence |
|---|--------|----------|
| 21.10 | PASS (harness note) | Schedule tab is client-side only (no `?tab=` param routes to it; unknown params fall back to Overview). Single `page.mouse.click` did not switch panels; a separated `mouse.move → mouse.down → pause → mouse.up` sequence did (`aria-selected=true`). Panel: "Court Dates / New Court Date" |
| 21.11 | PASS | Schedule Court Date dialog: Matter Dlamini v RAF, Type **Pre-Trial**, Date **2026-07-26** (+14d), Court "Gauteng Division, Pretoria", Description "Pre-trial conference — attorney attending: Bob Ndlovu", reminder 7d → saved, row **Scheduled** |
| 21.12 | PASS | Firm **Court Calendar** (`/court-calendar`): "1 court dates / 1 upcoming" — 2026-07-26 · Pre-Trial · Gauteng Division, Pretoria · Dlamini v Road Accident Fund · Sipho Dlamini · Scheduled |

## Day 21 day-level checkpoints

- Time entries post via non-tariff path with "No rate card" warning (OBS-2101 WONT_FIX honoured): PASS
- Disbursement recorded, feeds Day 28 fee note (Draft/Unbilled; approval step pending Day 28): PASS
- Court date added, visible on calendar + matter Schedule tab: PASS

Console: 0 errors.

## Gaps

- None new.
