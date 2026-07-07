# Day 21 — Firm logs time, adds disbursement, creates court date `[FIRM]` — 2026-07-06

**Actor**: Bob Ndlovu (Admin). Context swap performed: cleared browser cookies (portal unaffected — Sipho auth is localStorage JWT), fresh Keycloak login as bob@mathebula-test.local.

## Phase A — time entries (non-tariff path, OBS-2101 WONT_FIX)

| # | Result | Evidence |
|---|--------|----------|
| 21.1 | PASS | Matter Work > Tasks (`?tab=tasks`): all 9 RAF-template tasks listed Open, each row has canonical **Log Time** CTA |
| 21.2 | PASS | "Initial RAF claim assessment & instructions" → Log Time dialog: Duration (h/m), Date, Description, Billable — **no tariff dropdown** (as amended) |
| 21.3 | PASS | 2h 30m, today, full consult description, Billable=Yes. Yellow warning rendered: "No rate card found for this combination. This time entry will have no billable rate…" (acceptable path per script) |
| 21.4 | PASS | Entry saved; matter Finance > Time: Total Time 4h / Billable 4h; task row "Initial RAF claim assessment & instructions 2h 30m (1 entry)"; Overview KPI "4.0h Hours logged". Entries carry no billable value (no rate card) — expected R 0 TIME lines on Day 28 |
| 21.5 | PASS (task-name note) | Second entry 1h 30m "Drafted and filed RAF1 claim form incl. quantum schedule" logged on **"File RAF1 claim form + supporting documents (within 3-year prescription)"** — the actual RAF-template task (script's "Draft particulars of claim" doesn't exist in this template; Day 60.7 references the RAF1 task, so this keeps Day 60 consistent). Time tab row 1h 30m (1 entry) |

## Phase B — disbursement

| # | Result | Evidence |
|---|--------|----------|
| 21.6 | PASS | Finance > Disbursements → New Disbursement dialog (matter pre-bound Dlamini v RAF) |
| 21.7 | PASS | Category **Sheriff Fees**, Amount 1250 (ZAR excl VAT, Standard 15%), Description "Sheriff service of summons on RAF", Supplier "Sheriff Pretoria Central", Incurred 2026-07-06, Client Sipho Dlamini |
| 21.8 | PASS (product-shape note) | No explicit "recoverable" checkbox exists — recoverability is the default disbursement model (Billing column tracks Unbilled → billed); recorded as-is |
| 21.9 | PASS | Row: R 1 250,00 · Approval **Draft** · Billing **Unbilled**. NOTE for Day 28 pre-condition (OBS-2104): must Submit for Approval + Approve before the billing wizard |

## Phase C — court date

| # | Result | Evidence |
|---|--------|----------|
| 21.10 | PASS | Schedule tab (single-tab group) → New Court Date dialog. Harness note: synthetic `.click()` on the Schedule tab does not switch panels — needed a real coordinates mouse click (same trusted-event quirk family as day-00) |
| 21.11 | PASS | Matter Dlamini v RAF, Type **Pre-Trial**, Date **2026-07-20** (+14d), Court "Gauteng Division, Pretoria", Description "Pre-trial conference — attorney attending: Bob Ndlovu", reminder 7d → saved, row status **Scheduled** |
| 21.12 | PASS | Firm-side **Court Calendar** page (`/court-calendar`) lists the same row (2026-07-20 · Pre-Trial · Gauteng Division, Pretoria · Dlamini v RAF · Sipho Dlamini · Scheduled) |

## Day 21 day-level checkpoints

- Time entries post via non-tariff path with "No rate card" warning (OBS-2101 WONT_FIX honoured): **PASS**
- Disbursement recorded, feeds Day 28 fee note (Draft/Unbilled; approval step pending Day 28): **PASS**
- Court date added, visible on calendar + matter: **PASS**

## Gaps

None new.
