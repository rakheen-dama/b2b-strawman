# Day 28 — Firm generates first fee note (bulk billing) `[FIRM]` — 2026-07-06

**Actor**: Thandi Mathebula (Owner). Fresh Keycloak login (session had expired); identity confirmed in sidebar (`thandi@mathebula-test.local`).

## Dead-session recovery (pre-flight findings, all read-only-verified)

A prior QA session had started Day 28 after writing day-21.md and died without recording:

- **Sheriff disbursement already Approved** — DB (read-only): `approval_status=APPROVED`, `approved_by=0768ccd3` (Thandi), `approved_at=2026-07-06 09:36:57Z`, `created_by=b6fb3a54` (Bob) → dual-user submit/approve per OBS-2104 already satisfied. UI confirms row `R 1 250,00 · Approved · Unbilled`.
- **2 stale empty PREVIEW billing runs** (`995c767f`, `f6d99540`, created 09:37–09:38Z by Thandi, 0 customers/0 invoices) — residue of the dead run's wizard attempts. Cancelled via UI Cancel Run (backend log: "Cancelled and deleted billing run … (PREVIEW)"). A third empty run (`e51b2ac4`, minted by this session's first wizard pass pre-activation) was cancelled the same way. No SQL writes.

## Pre-condition 2 (not in script, required by product): Activate Sipho

First wizard pass returned **"No customers with unbilled work found for this period"** (backend: "0 customers, total unbilled: 0"). Root cause (code, `BillingRunSelectionService.discoverCustomers`): customer filter is `c.lifecycle_status = 'ACTIVE'` — Sipho was still **ONBOARDING** (Day 10 win-nudge moved him Prospect→Onboarding; nothing since activated him). Per OBS-2102 (cycle 16) the INDIVIDUAL tax-number skip makes activation possible; canonical UI path used: client detail → **Activate Customer** → confirm dialog → badge **Active** (DB read-only: `lifecycle_status=ACTIVE`). Scenario has no explicit "activate Sipho" step — flagged as a scenario-note, not a product bug.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| PRE (OBS-2104) | PASS (pre-satisfied) | Disbursement APPROVED/UNBILLED before wizard — see recovery notes above |
| 28.1 | PASS | Finance > Billing Runs (`/invoices/billing-runs`) → New Billing Run → 5-step wizard (Configure / Select Customers / Review & Cherry-Pick / Review Drafts / Send) |
| 28.2 | PASS (product-shape notes) | Step 2 after activation: row "Sipho Dlamini · Unbilled Time R 0,00 · Unbilled Expenses R 1 250,00 · Total R 1 250,00", pre-selected. Time = R 0 per OBS-2101 no-rate-card WONT_FIX. **OBS-2104b Cartesian inflation NOT reproduced** — expenses show correct R 1 250 (not R 11 250). No "By Client" scope selector exists — wizard auto-discovers customers with unbilled work; only Sipho listed (Moroka has none) |
| 28.3 | PASS | Step 3 expanded: **Time Entries** table (2.5h consult + 1.5h RAF1, Rate/Amount N/A, both checked) AND **Disbursements** table (Sheriff R 1 250,00, SHERIFF_FEES, checked). **OBS-2104c no longer true** — step 3 DOES render a Disbursements section now |
| 28.4 | PARTIAL → **LZKC-006** | Clicking Next from step 3 generated the fee note (backend 13:18:45Z: "Created draft invoice dcc26611…", "Completed billing run 15000265 — 1 invoices generated, total: 1250.00") **but the wizard rendered the error "Only billing runs in PREVIEW status can be generated. Current status: COMPLETED"** instead of step 4 Review Drafts. Steps 4–5 unreachable (Back→Next reproduces the error; run now COMPLETED). Recovered via Billing Run detail page (Completed · 1 invoice · R 1 250,00 · "Approve All Generated" · View Invoice link) |
| 28.5 | PARTIAL → **LZKC-007** | Fee-note detail (`/invoices/dcc26611…`): TIME ENTRIES section — 2 lines "task title -- date -- Bob Ndlovu", qty 1.5/2.5, R 0,00, VAT Standard 15%; DISBURSEMENTS section — "Sheriff fees: Sheriff service of summons on RAF (Sheriff Pretoria Central, 2026-07-06)" R 1 250,00 Zero-rated 0%; Subtotal/Total R 1 250,00 ZAR. DB: `line_type` = TIME×2 + DISBURSEMENT×1 (product uses DISBURSEMENT, more specific than script's EXPENSE — fine). VAT summary rows render; VAT total R 0,00 is data-correct (time has no rate; disbursement stored `ZERO_RATED_PASS_THROUGH`, vat_amount 0 — SA pass-through treatment; script's "VAT 15% line" assumption doesn't apply to this data). **Missing vs script**: no letterhead logo, no firm/banking details, no RAF-2026-001 matter code on the rendered preview (shows matter *name* only); preview header reads "Invoice: DRAFT". Cover-letter generator ("Invoice Cover Letter") also has no logo/banking details and renders **"Invoice Number:" blank** (LZKC-010) |
| 28.6 | PASS (with **LZKC-008** note) | Approve → **INV-0001 · Approved**. Send Fee Note → validation panel "✗ Tax Number is required to send an invoice" (INDIVIDUAL client — OBS-2102-class inconsistency; also says "invoice") → owner override **Send Anyway** → status **Sent**; Online Payment Link created (provider `mock`, MOCK-SESS-d999f5cb…); Payment History row Created/mock/R 1 250,00. DB: `INV-0001 | SENT \| 1250.00 \| ZAR` |
| 28.7 | PASS | Mailpit `FbM5JuniKthFkSG9NokDgo` → `sipho.portal@example.com`, subject **"Fee Note INV-0001 from Mathebula & Partners"** (no "invoice" in subject). Body: "Fee Note Number INV-0001", Amount Due ZAR 1250.00, **Pay Now** (mock-payment session link, returnUrl → portal payment-success) + **View Fee Note** (`localhost:3002/invoices/dcc26611…`). "invoice" appears only inside URLs/param names |
| 28.8 | PASS | Screenshots: `day-28-firm-fee-note-sent.png`, `day-28-draft-preview.png`, `day-28-cover-letter-dialog.png` |

## Day 28 day-level checkpoints

- Fee note generated with TIME lines + disbursement line correctly separated (tariff descriptors out of scope per OBS-2101): **PASS**
- Terminology firm-side reads "Fee Note" end-to-end: **PARTIAL** — nav, list, detail buttons, breadcrumb and email all say Fee Note; leaks logged as LZKC-009 (document preview "Invoice: DRAFT", send-validation copy, "Invoice Cover Letter" template, audit-trail entity label "Invoice")
- Email dispatched with portal payment link: **PASS**

## New gaps

- **LZKC-006 (Medium, OPEN)** — Billing-run wizard dead-ends after successful generation: step-3 "Next" generates the invoices, but the wizard then renders "Only billing runs in PREVIEW status can be generated. Current status: COMPLETED" and steps 4 (Review Drafts) / 5 (Send) are unreachable; retry reproduces. Only one generate call hit the backend (log-verified) → the success response is mishandled / a duplicate generate is issued on advance. Workaround exists (run detail page → View Invoice), but the canonical wizard flow is broken.
- **LZKC-007 (Medium, OPEN)** — Rendered fee-note document (Preview + cover letter) is not client-payment-ready: no letterhead logo (Day-1 branding logo exists), no firm details, no banking details, no matter reference code (RAF-2026-001); header reads "Invoice: DRAFT".
- **LZKC-008 (Medium, OPEN)** — Send validation demands "Tax Number" for an INDIVIDUAL client (RAF claimant); owner/admin must "Send Anyway". Inconsistent with OBS-2102's INDIVIDUAL tax-number skip at activation; non-admin members presumably cannot send at all.
- **LZKC-009 (Low, OPEN)** — Firm-side "Invoice" terminology leaks despite legal-za substitution: document preview title/heading "Invoice: DRAFT", validation copy "…required to send an invoice", Generate Document menu "Invoice Cover Letter", audit-trail entity "Invoice".
- **LZKC-010 (Low, OPEN)** — "Invoice Cover Letter" generated document renders the "Invoice Number:" field blank for INV-0001 (template variable not populated).
- **LZKC-011 (Low, OPEN)** — Console error on firm `/dashboard` after login: `Error: <path> attribute d: Expected moveto path command ('M' or 'm'), " L 2,20 L 2,20 Z"` — malformed SVG sparkline path (likely single-data-point chart edge case).

## Notes for later days

- Payment provider is **mock** (not PayFast sandbox) — Day 30 exercises the mock gateway per the Day-61 amendment ("PAID Day 30 via mock payment gateway"). Pay Now link: `http://localhost:8080/portal/dev/mock-payment?sessionId=MOCK-SESS-d999f5cb…`.
- Fee note ID `dcc26611-86de-44e2-90a7-64ead82f379a`, number INV-0001, R 1 250,00 ZAR, due date not set (email shows "Due Date N/A").
- Billing run `15000265-d930-491a-a788-d68a664840d7` COMPLETED (kept — it is the real Day-28 artefact). The 3 cancelled empty runs are deleted (hard-delete on cancel per backend log).
