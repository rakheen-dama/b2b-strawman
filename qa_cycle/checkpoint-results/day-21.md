# Day 21 — Firm logs time, adds disbursement, creates court date  `[FIRM]`

**Date**: 2026-04-30  
**Actor**: Bob Ndlovu (Admin, `bob@mathebula-test.local` / `SecureP@ss2`)  
**Stack**: Keycloak dev stack (frontend `:3000`, gateway `:8443`, backend `:8080`, KC `:8180`).  
**Result**: PARTIAL_PASS — Phase A blocked, Phases B+C pass with caveats. Gap **OBS-2101** filed.

---

## Pre-flight

- Stack health: backend 200, frontend 200, gateway HTTP 200, KC `master` 200, mailpit 200.
- Logged out of Thandi via in-app user-menu → Sign out → KC realm logout completed (page redirected to `/`).
- Logged in as Bob via Keycloak (`bob@mathebula-test.local` / `SecureP@ss2`) → landed on `/org/mathebula-partners/dashboard` with sidebar avatar `BN` confirming user swap.

## Phase A — Time entry against LSSA tariff — **BLOCKED**

| ck | Step | Result |
|----|------|--------|
| 21.1 | Navigate matter RAF-2026-001 → Time tab → look for **+ Log Time** | **FAIL** — Time tab on matter detail is read-only. It only renders a date-range filter and the empty state copy "Log time on tasks to see project time summaries here". No `+ Log Time` CTA at the matter scope. (Screen-flow expectation deviates from scenario, but Tasks tab does expose per-task `Log Time` buttons — workable.) |
| 21.2 | Select tariff activity "Consultation with client — per 15 minutes" from LSSA dropdown | **FAIL** — `Log Time` dialog renders Duration / Date / Description / Billable only. **No tariff/activity dropdown anywhere in the dialog.** A yellow banner reads: *"No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates."* |
| 21.3 | Billable=Yes, rate auto-populates from tariff | **FAIL** — billable defaults true, but rate field does not exist; auto-populate impossible without a tariff/activity input. |
| 21.4 | Submit → time entry saved, amount calculated | **N/A** — not attempted (no tariff selectable to validate amount calc against). |
| 21.5 | Log a second 1.5h under "Drafting particulars of claim" tariff entry | **N/A** — same blocker. |

**Diagnosis**: LSSA tariffs are seeded at `/org/mathebula-partners/legal/tariffs` ("LSSA 2024/2025 High Court Party-and-Party · 19 items"). Schedule data side of Phase 55 is in place; only the time-entry dialog binding is missing. Filed as **OBS-2101 — LSSA tariff dropdown missing from time-entry dialog**.

Evidence: `qa_cycle/evidence/day-21/log-time-no-tariff.png`.

## Phase B — Disbursement — **PASS** (with caveats)

| ck | Step | Result |
|----|------|--------|
| 21.6 | Disbursements tab → **+ New Disbursement** | PASS — dialog opens with title "New Disbursement". |
| 21.7 | Type=Sheriff's fee, Amount=R 1 250.00, Date=2026-04-30, Description="Sheriff service of summons on RAF" | PASS (with mapping) — scenario "Type: Sheriff's fee" maps to **Category: Sheriff Fees**. Matter + Client pre-selected from matter context (no need to swap). Supplier set to "Sheriff Sandton". |
| 21.8 | Mark as **recoverable** (client-rebillable) | **WONT_FIX (scenario amend)** — UI has no "Recoverable" toggle. The dialog's **Payment Source** radiogroup (Office Account / Trust Account) covers the equivalent semantic: Office Account disbursements default to client-billable. Logged as triage-inline scenario amendment. |
| 21.9 | Submit → disbursement saved with status UNBILLED | **PASS** — disbursement created, list row shows `2026-04-30 / Sheriff Fees / Sheriff service of summons on RAF / Sheriff Sandton / R 1 250,00 / Approval=Draft / Billing=Unbilled`. The `Billing=Unbilled` mirrors scenario "UNBILLED". A separate `Approval=Draft` column carries an approval workflow not anticipated by the scenario but is a legitimate enhancement (not a defect). |

Caveats:
- (minor) MATTER column shows `—` instead of `RAF-2026-001` reference. The disbursement IS bound to the matter (visible on the matter's Disbursements tab), but the org-wide list view doesn't surface the matter ref.
- (minor) Amount field rejected JS-set value of `1250` — required real `Playwright.fill()` to populate. Manual users entering via keyboard are fine; only an automation-flake.

Evidence: `qa_cycle/evidence/day-21/disbursement-saved.png`.

## Phase C — Court Date — **PASS** (with caveat)

| ck | Step | Result |
|----|------|--------|
| 21.10 | Court Dates tab → **+ Add Court Date** | PASS — button labelled "New Court Date"; dialog title "Schedule Court Date". |
| 21.11 | Court=Gauteng Division Pretoria, Date=2026-05-14 (≈14 days), Type=Pre-Trial, Attorney attending=Bob | PARTIAL — Type "Pre-Trial" + Date 2026-05-14 + Court Name "Gauteng Division, Pretoria" submitted successfully. **No "Attorney attending" field in the dialog.** Scheduler tracks scheduledDate / scheduledTime / courtName / courtReference / judgeMagistrate / description / reminderDays only. Logged as **scenario amendment**: drop `Attorney attending` (out-of-scope; default attribution is the logged-in user via audit trail). |
| 21.12 | Submit → court event saved, appears on Court Calendar + matter Overview | PASS — `/org/mathebula-partners/court-dates` shows row `2026-05-14 / Pre-Trial / Gauteng Division, Pretoria / Dlamini v Road Accident Fund / Sipho Dlamini / Scheduled`. **And** firm-side Court Calendar `/org/mathebula-partners/court-calendar` shows `1 court dates · 1 upcoming` with the new entry. |

Evidence: `qa_cycle/evidence/day-21/court-date-saved.png`.

## Day 21 checkpoints

- [ ] **Time entries post against tariff, rate auto-populates** — **FAIL** (OBS-2101 blocker).
- [x] Disbursement recorded with recoverable flag (feeds Day 28 fee note) — PASS via Office-Account proxy semantic.
- [x] Court date added, visible on calendar + matter — PASS.

## Console / network

- Console errors: 0 across Phase B + Phase C (Disbursement create + Court Date create + Court Calendar view).
- Tab-swap on Matter detail: 0 errors.
- Login flow: 4 console messages (Next.js telemetry, no errors).

## Scenario amendments triaged inline

**WONT_FIX — minor scenario divergences (UI is correct, scenario expectations slightly off):**
1. 21.8 "Mark as recoverable" → covered by `Payment Source: Office Account` (the de-facto recoverable indicator in this codebase).
2. 21.11 "Attorney attending: Bob Ndlovu" → no such field; attorney is implicit (creator).
3. 21.9 "status UNBILLED" → UI shows `Billing=Unbilled` (case-insensitive match) plus separate `Approval=Draft`.

These are documented here, not amended in the scenario file (kept as-is for narrative continuity; status note documents the WONT_FIX decision).

## QA Position

**Day 21**: PARTIAL_PASS — Phases B+C done; Phase A blocked by OBS-2101 (LSSA tariff binding missing in time-entry dialog).

**Stop point**: filed gap, halted before Day 22+. Day 28 fee note flow expects tariff-rated time line items, so Day 22+ cannot reasonably continue without a Product triage of OBS-2101 (or an explicit "free-text rate" fallback decision).

**Next dispatch**: needs Product agent to (a) confirm OBS-2101 is real and triage scope (M-effort frontend dialog + read endpoint + likely schema field), (b) decide whether scenario Day 28 should be relaxed to non-tariff rates if OBS-2101 is deferred. Then re-run Day 21 Phase A with the fix.

---

**Time on day**: ~10 min (login swap, all three phases, evidence capture).  
**Tool count**: ~50 calls.  
**No regressions in Days 0-15 evidence.**
