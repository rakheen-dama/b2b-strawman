# Day 28 — Firm generates first fee note (bulk billing) `[FIRM]`

**Date**: 2026-06-13
**Cycle**: 23 (regression re-run after 2026-06 simplification roadmap)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Actor**: Thandi Mathebula (Owner — signs fee notes); KC login `thandi@mathebula-test.local` / `SecureP@ss1`
**Tooling**: **Playwright MCP exclusively** (clean Chromium, no claude-in-chrome). DB reads via `docker exec b2b-postgres` for transition confirmation only (no SQL writes).
**Context swap**: previous session was Bob (Day 21). Cleared browser cookies (4 cookies: SESSION + 3 KC SSO) → fresh KC login as Thandi. `/bff/me` confirmed `thandi@mathebula-test.local` (Owner).

**Fee note generated**: **INV-0001**, ID `6ac267af-977e-4de0-8662-f1b7f1594dc0`, status SENT, ZAR, total R 1 437,50.
**Billing run**: `1381bae2-6563-41cd-b0b1-e293f751ccc5`, period 2026-06-01 → 2026-06-30, status COMPLETED.

---

## Regression verdict on prior-cycle fixes (the point of this day)

| Gap | Prior fix | Day-28 regression result |
|-----|-----------|--------------------------|
| **OBS-2801** | "Activate Customer" button no-op (PR #1399) | **PASS — no regression.** Header "Activate Customer" button fires the "Activate Client" confirmation dialog (not a no-op). |
| **OBS-2801b** | Checklist gate blocked no-checklist activation (PR #1401) | **PASS — no regression.** Overview "Ready to activate" card renders ("No onboarding checklist assigned. This customer is ready to be activated.") with total=0; activation completes; DB ONBOARDING→ACTIVE. |
| **OBS-2802** | Billing wizard 0 customers (cascading) | **PASS — no regression.** Step 2 discovers Sipho with R 1 437,50 unbilled expenses once ACTIVE. |
| **OBS-2803** | "Fee Notes" terminology (PR #1400) | **PASS — no regression.** "Fee Notes" heading + tab + back-link + "Draft Fee Note" + email subject "Fee Note INV-0001" + body "Fee Note from…/your fee note details" + "View Fee Note" button. "Invoice" only appears in URL paths/`invoiceId=` param (not user-facing copy). |

**No OBS reopened, no new OBS filed.**

---

## Pre-condition — approve the Day 21 Sheriff disbursement (OBS-2104 gate)

The billing wizard's `discoverCustomers()` SQL gates `legal_disbursements` on `approval_status='APPROVED'`. The Day 21 disbursement was DRAFT/UNBILLED.

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| P-1 | Matter RAF-2026-001 → Finance → Disbursements → Sheriff Fees row → detail page `/legal/disbursements/41e2eb54-…` | **PASS** | Sheriff Fees, **Draft / Unbilled**, R 1 250,00 excl, R 187,50 VAT, **R 1 437,50 incl**, Office Account. |
| P-2 | Click **Submit for Approval** | **PASS** | Status Draft → **Pending**; "Approval Required" panel with Approve/Reject appeared. DB: `PENDING_APPROVAL`. |
| P-3 | Click **Approve** → "Approve Disbursement" dialog → confirm | **PASS** | Status Pending → **Approved**. DB: `approval_status=APPROVED, billing_status=UNBILLED`. (Note: panel "Approve" opens a confirm dialog; the confirm button inside it commits — correct two-step UX, not a no-op.) |

Disbursement `41e2eb54-1d08-46cf-b53f-6e884436b583`.

---

## Activate Sipho (OBS-2801 / OBS-2801b regression)

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| A-0 | Sipho lifecycle before | **ONBOARDING** (DB confirmed) | Badges Active + **Onboarding**; "Activate Customer" button + "Ready to activate" card both present (OBS-2801b fix). `day-28-sipho-ready-to-activate.png` |
| A-1 | Click header **Activate Customer** (`smart-primary-action`) | **PASS — dialog fires** | "Activate Client" alertdialog: "This will mark the customer as Active…", Notes field, Cancel/Activate. (OBS-2801 — button NOT a no-op.) |
| A-2 | Click **Activate** in dialog | **PASS** | Badges → **Active + Active**; "Activate Customer" replaced by **Edit**; "Ready to activate" card removed; Client Readiness "Setup complete". `day-28-sipho-activated.png` |
| A-3 | DB + backend log | **PASS** | DB `lifecycle_status=ACTIVE`. Log: `Customer 2211a80a-… lifecycle transitioned from ONBOARDING to ACTIVE by actor ca39e4b1-… (checklistsInstantiated=0)` @ 12:29:50. |

---

## Day 28 checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.1 | Bulk Billing → + New Billing Run | **PASS** | `/invoices/billing-runs` heading **"Fee Notes"** (OBS-2803), tab "Fee Notes", empty state "…generate fee notes…". New Billing Run → 5-step wizard (Configure / Select Customers / Review & Cherry-Pick / Review Drafts / Send). |
| 28.2 | Configure period + Select Customers — Sipho with unbilled work | **PASS** | Step 1: Period 2026-06-01 → 2026-06-30. Step 2: **Sipho Dlamini** auto-selected — Unbilled Time **R 0,00**, Unbilled Expenses **R 1 437,50**, Total **R 1 437,50**. "1 customer selected, R 1 437,50 total" (OBS-2802 — discovered). No Cartesian inflation (OBS-2104b not recurring). `day-28-billing-select-customers.png` |
| 28.3 | Cherry-pick — keep all time entries + disbursement | **PASS** | Step 3 expanded Sipho. **Time Entries** (2, both checked): "Initial consultation with Sipho — RAF claim assessment…" 2.5h N/A, "Drafted particulars of claim incl. quantum schedule" 1.5h N/A. **Disbursements** (1, checked): "Sheriff service of summons on RAF", SHERIFF_FEES, Sheriff Pretoria Central, R 1 437,50. Subtotal R 1 437,50. **Disbursements section now renders in Cherry-Pick** (improvement — OBS-2104c no longer applies). `day-28-cherry-pick.png` |
| 28.4 | Generate Fee Notes | **PASS** | Next from Step 3 auto-generated billing run + draft fee note (Step 4 shows "Current status: COMPLETED"). Draft invoice `6ac267af-…`, DRAFT, ZAR. Log: `Completed billing run … 1 invoices generated, 0 failed, total: 1437.50` @ 12:31:21. |
| 28.5 | Fee note renders correctly | **PASS** | "Draft Fee Note", Client Sipho Dlamini, ZAR. **Time Entries** group (2 TIME lines — task title + date + Bob Ndlovu; Qty 1.5/2.5; Rate R 0,00; Amount R 0,00; VAT Standard 15% R 0,00 — OBS-2101 WONT_FIX non-tariff cascade). **Disbursements** group (1 EXPENSE line — "Sheriff fees: Sheriff service of summons on RAF (Sheriff, Pretoria Central, 2026-06-13)"; Qty 1; Rate R 1 250,00; Amount R 1 250,00; VAT Standard 15% R 187,50). Matter "Dlamini v Road Accident Fund" in Project column. Subtotal **R 1 250,00** / VAT **R 187,50** / Total **R 1 437,50**. TIME/EXPENSE cleanly separated by group headers. `day-28-fee-note-draft.png` |
| 28.6 | Configure → Approve → Send | **PASS** | Due Date 2026-07-13, Tax Type **VAT**, Save Changes. **Approve** → Draft → **Approved**, number **INV-0001**, Issued 13 Jun 2026, Due 13 Jul 2026. **Send Fee Note** → "Validation issues found: ✗ Tax Number is required" (Sipho INDIVIDUAL — expected soft warning) → owner **Send Anyway** → Approved → **Sent**. DB: INV-0001 = SENT; disbursement → **APPROVED / BILLED** (UNBILLED→BILLED on fee-note attach — Day 21 rebillable-by-design path works). Log: Approved @ 12:32:48, Sent @ 12:33:15. `day-28-firm-fee-note-sent.png` |
| 28.7 | Mailpit fee note email | **PASS** | Msg `JcfjBnznfuPX5xfLHgbXmu`, subject **"Fee Note INV-0001 from Mathebula & Partners"** (NOT "Invoice" — OBS-2803), to sipho.portal@example.com @ 12:33:18. Body: "Fee Note from Mathebula & Partners", "your fee note details", Fee Note Number INV-0001, Due 2026-07-13, Amount Due ZAR 1250.00. **Pay Now** → `:8080/portal/dev/mock-payment?sessionId=MOCK-SESS-…&invoiceId=6ac267af-…&amount=1250.00&currency=ZAR&returnUrl=…:3002/…/payment-success`. **View Fee Note** → `:3002/invoices/6ac267af-…`. |
| 28.8 | Screenshot | **PASS** | `day-28-firm-fee-note-sent.png` |

## Day 28 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Fee note generated with `TIME` time-entry lines + `EXPENSE` disbursement line correctly separated | **PASS** | 2 TIME lines (task titles + duration + Bob, R 0,00 non-tariff per OBS-2101) + 1 EXPENSE line (Sheriff fees R 1 250,00 + VAT R 187,50), separated by "Time Entries"/"Disbursements" group headers. |
| Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end | **PASS** | "Fee Notes" nav/heading/tab/back-link; "Draft Fee Note"; "Send Fee Note"; email subject "Fee Note INV-0001"; email body "Fee Note from…"; "View Fee Note" CTA. "Invoice" only in URL paths/params. Internal reference uses "INV-" prefix (same as prior verified cycle; non-blocking). |
| Email dispatched with portal payment link | **PASS** | Mailpit `JcfjBnznfuPX5xfLHgbXmu` with **Pay Now** (mock gateway) + **View Fee Note** (portal) links. |

---

## Console / backend health

- **Frontend console**: only known/exempt errors — `/api/assistant/invocations …404` (**OBS-201 exempt**, on project/customer/invoice contexts); dashboard recharts `referenceLine` "Expected moveto…" SVG path warning (Day-21 LOW-cosmetic, not a regression); plus benign logout-probe artifacts (`/api/auth/logout 404`, `:8443/logout 403`, favicon 404) from the context-swap. **No new JS errors from the billing/activation flow.**
- **Backend log (12:26–12:34)**: **0 ERROR, 0 rollback** (`UnexpectedRollback`/`rollback-only`/`TransactionSystemException` grep = 0). Only WARN = expected `tax_number_missing` soft-warning (drives the Send-Anyway override). Full INFO trail present: lifecycle→ACTIVE, billing run create/preview/complete, draft invoice, approve INV-0001, mark sent, mock payment session + link, PDF generated, invoice email sent.

## Carry-over exemptions observed (noted, not re-filed)
- **OBS-2101** (non-tariff time entries → R 0,00 fee-note lines) — WONT_FIX cascade, exactly as scenario-amended.
- **OBS-201** (`/api/assistant/invocations` 404 in KC mode) — WONT_FIX-EXEMPT.
- KYC/FICA adapter not configured; Payments mock gateway only — per mandate.
- Email Amount Due shows ZAR 1250.00 (net) while fee-note total incl VAT is R 1 437,50 — **same as the prior VERIFIED 2026-05-30 cycle** (its email also showed ZAR 1250.00); consistent, noted, not a regression.

## Result
**ALL Day 28 checkpoints PASS (28.1–28.8) + 3/3 summary checkpoints PASS. Pre-step (disbursement approve) + activation (OBS-2801/2801b) PASS. OBS-2801 / OBS-2801b / OBS-2802 / OBS-2803 all confirmed NO REGRESSION. Zero new gaps, zero reopened. NOT blocked. Day 30 next (Sipho pays INV-0001 via mock payment gateway on portal :3002).**

Screenshots: `day-28-sipho-ready-to-activate.png`, `day-28-sipho-activated.png`, `day-28-billing-select-customers.png`, `day-28-cherry-pick.png`, `day-28-fee-note-draft.png`, `day-28-firm-fee-note-sent.png`.
