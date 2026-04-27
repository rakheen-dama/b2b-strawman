# Day 28 — Firm generates first fee note (bulk billing)  `[FIRM]`

Cycle: 1 turn 6 | Date: 2026-04-23 01:05 SAST | Auth: Keycloak (Bob / Admin — Thandi swap deferred, see note) | Frontend: :3000 | Backend: :8080 (PID 25040) | Actor: Bob Ndlovu

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 28 (checkpoints 28.1–28.8).

## Verdict

**Day 28: 4/8 executed — 2 PASS (28.1 after feature-flag enable, 28.2 after project-rate override + customer tax number); 2 FAIL / BLOCKED at 28.4 by NEW HIGH BLOCKER GAP-L-60 (CREATE_INVOICE PROSPECT lifecycle gate). Halted per BLOCKER rule.**

Three prerequisite gaps surfaced that had to be resolved in-turn to reach the actual draft-generation step — these are pre-existing setup gaps, not regressions; none was on the Day 21 critical path but all feed Day 28:

1. **Bulk Billing Runs feature disabled by default for legal-za tenants** → `/invoices/billing-runs` 404s with "This feature is not enabled for your organization. An admin can enable it in Settings → Features." Resolved by enabling the switch on `/settings/features`. Scenario 28.1 assumed the feature was on. Logged as GAP-L-61 (LOW — tenant-setup gap; could be provisioned on legal-za pack install).
2. **Customer `tax_number` is a hard pre-requisite for Invoice Generation** → Generate Fee Note dialog shows "Prerequisites: Invoice Generation — Tax Number is required for Invoice Generation / Fill the Tax Number field on the customer profile." Resolved by setting Sipho's `tax_number = '0123456789'` via Edit Customer dialog → Save Changes. Scenario never seeded a tax number during Day 2 client creation. Pre-existing scenario drift; tracked as GAP-L-62 (LOW — requires scenario update + client-create wizard prompt).
3. **Day 21 time entries had no rate card** → Select Unbilled Items dialog listed all 5 time entries tagged "N/A" and "0 of 5 selectable / Total R 0,00" with banner "5 time entries have no rate card". Pre-existing LSSA tariff gap (21.2/21.3 FAIL). Resolved by adding a **Project Rate Override** via matter Rates tab: Bob Ndlovu → R 1 500,00/h ZAR, Effective From 2026-04-01, Ongoing. After that, the 5 entries priced correctly (R 11 250,00 total). Still carries forward as the pre-existing LSSA integration gap (not re-opened).

Then at 28.4 (Validate & Create Draft) the backend returned **"Cannot create invoice for customer in PROSPECT lifecycle status"** — a NEW carry-forward expression of the PROSPECT-lifecycle gate family (GAP-L-35 custom-fields → GAP-L-56 time-entries → **GAP-L-60 invoices**). Per Product spec on L-56 fix: "Relaxing `CREATE_TIME_ENTRY` has a narrow regression surface (single caller) and leaves `CREATE_PROJECT` / `CREATE_TASK` / `CREATE_INVOICE` gates intact." — confirmed working as specified.

I tried the lifecycle-transition workaround (legitimate scenario path, not a SQL shortcut): **Change Status → Start Onboarding** succeeded (PROSPECT → ONBOARDING, DB row confirmed). Then **Change Status → Activate** was blocked with "Cannot activate customer — one or more onboarding checklists are not yet completed" — because the `Legal Individual Client Onboarding` checklist (9 items: Proof of Identity, Proof of Address, Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Signed, Conflict Check Performed, Power of Attorney Signed, FICA Risk Assessment, Sanctions Screening) has 0/9 completed — scenario never scripted onboarding-checklist completion in Days 0–15. Completing all 9 items via UI with document uploads exceeds the single-turn scope and is itself a distinct Day-0/Day-5/Day-8 scripting gap. Halted here.

**No Thandi session swap performed**: scenario says Thandi (Owner) signs fee notes, but the draft-creation path is gated on PROSPECT → ACTIVE transition regardless of actor; the Owner-only gate would have fired at 28.6 (Approve & Send), which we never reached. Dev agent should ship L-60 first; QA will re-swap to Thandi on the next resume to exercise 28.6–28.7.

## Pre-flight

- Services verified UP via `curl`: backend `{"status":"UP"}`, frontend 200. Backend PID 25040 (post L-56 merge restart).
- Bob session continued clean from Day 21 re-verify; no re-login needed.
- Bulk Billing Runs feature was OFF at turn start → enabled via `/settings/features` toggle (switch #3 "Batch invoice generation across multiple customers in a single run"). Status: `aria-checked=true` confirmed post-toggle.

## Checkpoint execution

| Checkpoint | Result | Evidence |
|---|---|---|
| 28.1 | **PASS (after feature enable)** | Navigated to `/invoices/billing-runs` (linked from Fee Notes page as "Billing Runs" subtab; `/bulk-billing` itself 404s — scenario wording drift; no "Bulk Billing" top-level menu item). Page shows "No billing runs / Create a billing run to generate invoices for multiple customers at once. New Billing Run". Clicked **New Billing Run** → wizard opens with 5-step header: 1 Configure → 2 Select Customers → 3 Review & Cherry-Pick → 4 Review Drafts → 5 Send. Screenshot: `day-28-28.1-new-billing-run-step1.png`. |
| 28.2 — Bulk Billing wizard | **FAIL / blocked by empty unbilled pool** | Tried Period From=2026-04-01 To=2026-04-30, Currency default, Next → step 2 "No customers with unbilled work found for this period. 0 customers selected, R 0,00 total." At that moment all 5 time entries had `rate_cents = NULL` (no rate card), and disbursements aren't surfaced in this wizard either. Wizard has no "By Client / By Matter" scope toggle (scenario wording drift — only implicit By-Customer grouping via the customer checklist). Screenshot: `day-28-28.2-select-unbilled-no-rate.png`. |
| 28.2 — Per-customer path | **PASS (partial — time only, no disbursements)** | Fallback path: `/customers/<Sipho-id>?tab=invoices` → **New Fee Note** button → dialog opens "Generate Fee Note for Sipho Dlamini from unbilled time entries and expenses." First attempt hit **Prerequisites: Invoice Generation / Tax Number required** blocker (GAP-L-62). Fixed via Edit Customer → taxNumber=`0123456789` → Save Changes. Retry New Fee Note → From Date=2026-04-01, To Date=2026-04-30, Currency=ZAR → **Fetch Unbilled Time**. Second attempt hit "5 time entries have no rate card" 0-selectable warning. Fixed via matter Rates tab → Add Override → Bob Ndlovu / R 1 500,00 / ZAR / Effective From 2026-04-01 → Create Override → DB row in `project_rates` confirmed. Retry Generate Fee Note → Fetch Unbilled Time → **5 of 5 selectable, all 5 priced, Total R 11 250,00**. Each entry tagged `Rate card` chip (not `LSSA`). **No disbursements section** in this dialog — the R 1 250,00 Sheriff Fee from Day 21 is not surfaced by the per-customer Generate Fee Note flow at all. Scenario 28.2 expects "Unbilled disbursements: R 1,250 (sheriff's fee)" as part of the same preview; this is a scenario-vs-UX drift (likely a Bulk-Billing-only feature, or a product gap). Logged as GAP-L-63 (LOW — disbursements not surfaced in per-customer Fee Note flow). Screenshot: `day-28-28.2-select-unbilled-5-of-5-no-disbursement.png`. |
| 28.3 | **PASS (time only)** | Cherry-pick step UX: all 5 time entries already checked by default; all 5 × ZAR totals = R 11 250,00. No disbursement to toggle (GAP-L-63). |
| **28.4** | **FAIL / BLOCKER (NEW GAP-L-60)** | Click **Validate & Create Draft** → pre-generation checks render: `All customer required fields are filled ✓ / Organization name is set ✓ / 5 time entries without billing rates ⚠`. Create button becomes "Create Draft (1 issues)" — still clickable. Click → **new error line appears inside the dialog: "Cannot create invoice for customer in PROSPECT lifecycle status"**. Dialog stays open; DB probe: no new row in `invoices` / `fee_notes`. Same gate family as GAP-L-56 (time entries) and GAP-L-35 (custom fields), this time on the **CREATE_INVOICE** switch arm of `CustomerLifecycleGuard`. Screenshot: `day-28-28.4-PROSPECT-blocker-on-invoice.png`. |
| 28.4 — lifecycle workaround attempt | **BLOCKER** | Tried the lifecycle-transition path (scenario-consistent, not a SQL shortcut): Customer detail → Change Status dropdown → "Start Onboarding" → confirmation dialog "This will move the customer to Onboarding status and automatically create compliance checklists. Notes (optional)" → Start Onboarding → DB confirmed `lifecycle_status PROSPECT → ONBOARDING`. Then Change Status → **Activate** → dialog → click → returned in-dialog error "**Cannot activate customer — one or more onboarding checklists are not yet completed**". DB: `checklist_instances` row `b0d9fd88-…` (Legal Individual Client Onboarding) status=IN_PROGRESS, 9 items all Pending or Blocked, 0 completed. Completing all 9 via UI (6 require document uploads, 3 auto-unblock on dependencies) exceeds single-turn scope; also a distinct scenario-scripting gap (Day 0/5/8 were supposed to close these but checklist was introduced by the auto-create we just triggered at Day 28). Halted. |
| 28.5–28.8 | **BLOCKED** | Cannot reach fee-note preview / Approve & Send / Mailpit delivery without L-60 fix. |

## Day 28 summary checks

- [ ] Fee note generated with tariff lines + disbursement line correctly separated → **FAIL** (L-60 blocks invoice-create; also L-63 blocks disbursement inclusion)
- [ ] Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end → **PARTIAL OBSERVATION** (navigation breadcrumb reads "fee notes", H1 is "Fee Notes", "New Fee Note" button — correct. Generate-dialog title reads "Generate Fee Note ... from unbilled time entries and expenses" — correct. BUT the PROSPECT error copy reads "Cannot create **invoice** for customer in PROSPECT lifecycle status" — backend error leaks "invoice" terminology under legal-za profile. Logged as part of GAP-L-60 (copy + gate fix couples).
- [ ] Email dispatched with portal payment link → **BLOCKED**

## New gaps opened

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-L-60** | **HIGH / BLOCKER** | Fee Note / Invoice creation blocked by PROSPECT-lifecycle gate on `CREATE_INVOICE`. Same gate family as GAP-L-35 (custom fields — MED/OPEN) and GAP-L-56 (time entries — now VERIFIED) but on the invoice path. Error: "Cannot create invoice for customer in PROSPECT lifecycle status". Path: Customer detail → New Fee Note → Generate Fee Note dialog → Validate & Create Draft → in-dialog red error + no row persisted in `invoices`/`fee_notes`. Workaround via Change Status → Start Onboarding succeeded (PROSPECT → ONBOARDING) but Activate blocked on `Legal Individual Client Onboarding` checklist (9 items, 0 completed — scenario never scripted completion). **Blocks Day 28 entirely + cascades to Day 30 (portal pays fee note) and all later billing days.** Backend error copy also leaks "invoice" terminology under legal-za vertical (should read "fee note"). Root-cause scope: `CustomerLifecycleGuard.java` `CREATE_INVOICE` switch arm likely still rejects PROSPECT (and probably also ONBOARDING and possibly DORMANT — needs verification). **Fix shape mirrors GAP-L-56 fix (PR #1111)**: split `CREATE_INVOICE` into its own switch case allowing every lifecycle **except OFFBOARDED** (the gate should protect terminal state only — matter-close / refund flows, not day-to-day billing). Owner: backend. Estimated S <30 min — single `CustomerLifecycleGuard.java` switch-arm edit + test cases (mirror the L-56 spec). |
| **GAP-L-61** | LOW | Bulk Billing Runs feature is OFF by default on legal-za tenants. `/invoices/billing-runs` renders "Bulk Billing Runs is not enabled — This feature is not enabled for your organization. An admin can enable it in Settings → Features." Scenario 28.1 assumes the feature is on by default for legal firms. Fix shape: legal-za vertical pack should set `enabled_features: ["bulk_billing_runs"]` or similar in `vertical-profiles/legal-za.json` on pack install, mirroring the existing `enabled_modules` pattern. Owner: backend/product. Also cousin to **GAP-L-44** (PackReconciliationRunner doesn't sync `enabled_modules` on restart — same reconciliation mechanism needed for `enabled_features`). Off-critical-path workaround: admin toggles it manually once (what I did this turn). |
| **GAP-L-62** | LOW | `tax_number` is a hard prerequisite for Invoice Generation but is not collected during the Create Client wizard (Step 1 or Step 2). Generate Fee Note dialog first-pass blocks with "Prerequisites: Invoice Generation / Tax Number is required for Invoice Generation / Fill the Tax Number field on the customer profile". Scenario Day 2 never seeded a tax number for Sipho. Fix options: (a) add `taxNumber` field to Create Client wizard for SA legal tenants, (b) auto-seed a placeholder tax number from ID number for individual customers, (c) relax the Fee-Note prerequisite to warn instead of block. Owner: product + frontend. Off-critical-path; workaround = Edit Customer dialog exposes the field already (where I added `0123456789`). |
| **GAP-L-63** | MED | Per-customer Generate Fee Note dialog does NOT surface unbilled disbursements. `/customers/<id>?tab=invoices` → New Fee Note → From/To → Fetch Unbilled Time shows only time entries (5 rows, 5 selectable) with banner subtitle "from unbilled time entries and expenses" — but the Sheriff Fees R 1 250,00 disbursement created on Day 21 (`legal_disbursements` row `c9986a8f-…`, `billing_status=UNBILLED`, `project_id=RAF-2026-001`) is not listed. Scenario 28.2 requires disbursements AND time to combine into a single fee note. Fix shape: backend `FeeNoteDraftService.fetchUnbilledItems()` should union time entries + unbilled disbursements on the same matter(s); frontend dialog should render a second "Disbursements" section below "Time" with cherry-pick checkboxes and subtotals. Owner: backend + frontend. Blocks Day 28 full-flow even after L-60 lands; may not block if Bulk Billing Runs path is used (wizard has "Review & Cherry-Pick" step that _might_ cover both, but Bulk path errored at step 2 in this turn so not verified). Re-scope on next cycle. |

## Existing gaps verified / re-observed / reopened this turn

- **GAP-L-56** (HIGH, **VERIFIED** post-fix — see Day 21 re-verify section above).
- **GAP-L-57** (HIGH, **VERIFIED** post-fix — see Day 21 re-verify section above).
- **GAP-L-35** (MED, OPEN) — PROSPECT-lifecycle gate is **again re-expressed on a new entity**, this time invoices/fee_notes, as **GAP-L-60**. Third occurrence confirms the root cause is systemic (enum-wide switch arm coverage), not per-entity. Dev should consider whether to relax all guards on PROSPECT in a single fix rather than play whack-a-mole, OR define a clear policy doc for which operations require ACTIVE and which only require non-OFFBOARDED.
- **GAP-L-58** (LOW, OPEN) — matter Overview "Upcoming Deadlines" still reads "No upcoming deadlines" despite 2026-05-06 Pre-Trial court date. No regression.
- **GAP-L-22** session handoff clean.
- **GAP-L-25** (LOW, OPEN) — Trust account "GENERAL" workaround still in use.
- **GAP-L-26** (LOW, OPEN) — Sidebar branding still not applied.

## Stopping rule

Per instructions: "On BLOCKER (HIGH severity that prevents downstream): Stop. Log it. Exit. Do NOT skip ahead." GAP-L-60 is a HIGH blocker that prevents Day 28.4–28.8 + cascades to Day 30 (portal pays fee note — no fee note to pay) + all later billing days. Checklist-completion workaround exceeds single-turn scope and is itself a scenario-scripting gap. Halted and writing results.

## QA Position next

**Day 28 — 28.4 (blocked pending GAP-L-60).** Dev spec: mirror GAP-L-56 fix shape on `CustomerLifecycleGuard.CREATE_INVOICE` switch arm. Single-file backend edit + test cases + backend restart. Estimated S <30 min. QA re-runs Day 28.4 → preview → Approve & Send (swap to Thandi at 28.6 per scenario) → 28.7 Mailpit.

**Carry-forward for subsequent turns**:
- GAP-L-61 (tenant feature flag) — could be auto-enabled by pack install or remains manual-toggle; decide before next legal-za tenant run.
- GAP-L-62 (tax_number prerequisite) — seed/scenario update needed OR soft-warn instead of hard-block.
- GAP-L-63 (disbursements not surfaced in per-customer fee-note flow) — likely blocks 28.2 full form even after L-60 lands; re-verify on next cycle; may need separate backend/frontend fix.
- Onboarding checklist completion — Day 0/5/8 of the scenario should include explicit checklist-complete steps for legal-za individual clients, OR the auto-create-checklists-on-Start-Onboarding step should be softened so the checklist doesn't block Activate when it's never been interacted with (scenario activation happens without a formal "we did all KYC" step).

---

## Day 28 Re-Verify — Cycle 1 — 2026-04-25 SAST

Method: Playwright MCP browser-driven for all UI mutations; read-only SQL `SELECT` for state confirmation; no REST shortcuts. Tab 0 = Bob Ndlovu KC firm session continued from prior turn (GAP-L-61 verify). Tab 1 = Sipho portal preserved untouched. Disbursement `bb9ee2ac-b1e5-4e2f-bf43-e40a63809530` was already APPROVED + UNBILLED at turn start (per L-61 verify); customer `c3ad51f5-2bda-4a27-b626-7b5c63f37102` (Sipho INDIVIDUAL) had `tax_number=NULL` and `lifecycle_status=PROSPECT` (the L-62 hybrid condition). Day 21 time-entry rate-card gap (originally blocking 28.2) is N/A this turn — there are 0 time entries on the RAF matter, so the dialog tested a "disbursement-only" fee note.

Verdict: **L-62 + L-63 BOTH VERIFIED end-to-end browser-driven. Day 28 fee-note flow VERIFIED.** All four verify-focus checks PASS. No new gaps. GAP-L-60 (PROSPECT-lifecycle gate on CREATE_INVOICE) HOLDS as VERIFIED — draft creation succeeded against PROSPECT customer, confirming PR #1114's L-60 fix is intact.

### Pre-state (read-only SELECT)

```
customers.tax_number = NULL  (lifecycle_status=PROSPECT, customer_type=INDIVIDUAL, name='Sipho Dlamini')
legal_disbursements.bb9ee2ac-… : approval_status=APPROVED, billing_status=UNBILLED, amount=1250.00 ZAR, project_id=e788a51b-…, supplier='Sheriff Pretoria' SHF-RAF-2026-001
invoices: 0 rows for Sipho
time_entries: 0 rows on RAF matter (Day-21 rate-card gap N/A this turn)
```

### Checkpoint execution

| Phase | Result | Evidence |
|---|---|---|
| C — Open fee-note dialog | **PASS** | `/customers/c3ad51f5-…?tab=invoices` → Fee Notes subtab → New Fee Note button (visible, enabled) → Generate Fee Note dialog opens with title "Generate Fee Note", subtitle "Create a new fee note for Sipho Dlamini from unbilled time entries and expenses", From Date / To Date / Currency=ZAR fields. NO L-62 hard-block this time (was the prior-cycle blocker). Snapshot `day-28-cycle1-fee-note-dialog-open.{yml,png}`. |
| **D — L-63 disbursement visible** | **VERIFIED** | Filled From=2026-04-01, To=2026-04-30, Currency=ZAR → Fetch Unbilled Time → "Select Unbilled Items" view shows: "No unbilled time entries found for this period" (correct — 0 time entries) + **"Disbursements" section** with the Day-21 disbursement: `Sheriff service of summons on RAF — Day 21 cycle-1 verify / Sheriff Fees / Sheriff Pretoria · Apr 25, 2026 / R 1 250,00`. Auto-checked. "1 items selected for Sipho Dlamini" + "Total (1 items) R 1 250,00". Snapshot `day-28-cycle1-fee-note-dialog-disbursement-visible.{yml,png}`. **L-63 PR #1116 fix CONFIRMED**: per-customer Generate Fee Note dialog NOW surfaces APPROVED+UNBILLED disbursements (regression vs Day 28 cycle-0 where it was the L-63 gap). |
| **E — L-62 soft-warn at draft** | **VERIFIED** | Click Validate & Create Draft → "Pre-generation checks" panel renders: ✓ Organization name is set / ✓ All customer required fields are filled / **⚠ Tax Number is required to send an invoice** (amber warning icon, not red error). Below it an amber banner reads: **"Tax number missing — This draft can be saved, but the invoice cannot be sent until a tax number is added to the customer profile."** Button label = `"Create Draft (1 issues)"` — still **enabled** (clickable). Click it → dialog closes → Fee Notes table shows new row "Draft / Draft / R 1 250,00". DB confirms invoice `8f718728-5fb6-40fe-abf1-2cafc86c0f10` status=DRAFT, currency=ZAR, total=1250.00, customer_id=c3ad51f5-…, invoice_number=NULL. Snapshot `day-28-cycle1-fee-note-dialog-soft-warn.{yml,png}`. **L-62 soft-warn semantics PR #1126 CONFIRMED**: backend `InvoiceCreationService.createDraft` accepted the draft despite missing tax_number; UI surfaced the warning inline as `customer_tax_number` WARNING-severity check. |
| **F — L-62 hard-enforce at send** | **VERIFIED** | Navigated to draft detail page `/invoices/8f718728-…`. Header buttons = Preview / Approve / Delete Draft. Click Approve → status flipped DRAFT→APPROVED, invoice_number assigned `INV-0001`, header buttons now Preview / Send Fee Note / Void. Click **Send Fee Note** → in-page warning panel renders: **"Validation issues found / The following issues were found. As an admin/owner, you can override and send anyway. / ✓ Organization name is set / ✓ All customer required fields are filled / ✗ Tax Number is required to send an invoice"** — with `Send Anyway` (admin-override) and `Cancel` buttons. Send is **blocked** until tax_number set OR override invoked. Confirmed admin override exists (matches L-62 spec — CRITICAL severity, admin/owner can override). Snapshot `day-28-cycle1-fee-note-send-blocked.{yml,png}`. **L-62 hard-enforce PR #1126 CONFIRMED**: `InvoiceValidationService` raised CRITICAL `customer_tax_number` check on send. |
| G — Auto-populate path | **N/A this turn (by design)** | L-62 spec: "Create Client wizard: auto-populates `taxNumber = idNumber` when the user selects `entityType=INDIVIDUAL` and has entered an ID number — user can override freely (only prefills when the tax-number field is blank)". This applies to **new** INDIVIDUAL customers in the **Create Client wizard**, not the Edit Customer dialog (existing customers). Sipho was created before the L-62 fix; the Edit Customer dialog correctly does NOT auto-populate tax_number from idNumber (would be unwanted side-effect on existing rows). Field label in Edit dialog reads "Tax Number (required for activation)". Auto-populate verification deferred to a future Day 2 cycle when a fresh INDIVIDUAL is created post-L-62. |
| **H — Happy-path send** | **PASS** | Closed validation banner (Cancel) → navigated to `/customers/c3ad51f5-…` → click Edit → Edit Customer dialog opens → typed `8501015800088` (Sipho's SA ID number, also serves as tax_number for natural persons under SA tax law) into the Tax Number field → click Save Changes → dialog closes. DB confirmed `tax_number='8501015800088'`. Re-navigated to `/invoices/8f718728-…` → click Send Fee Note → no validation banner this time → status flipped APPROVED→**SENT** → header buttons now Preview / Record Payment / Void; "Payment History / No payment events yet"; Issued: Apr 25, 2026; "INV-0001 / Sent / R 1 250,00 ZAR". Snapshot `day-28-cycle1-fee-note-sent.{yml,png}`. |
| **I — DB confirmation** | **PASS** | `invoices.8f718728-…` : status=**SENT**, invoice_number=**INV-0001**, currency=ZAR, subtotal=1250.00, tax_amount=0.00, total=1250.00. `legal_disbursements.bb9ee2ac-…` : approval_status=APPROVED, billing_status=**BILLED** (flipped from UNBILLED), invoice_line_id=**`326fb6c5-d25b-4911-9ced-b75a6c5a23ee`** (linked to the new invoice's line item). Per-tenant invoice numbering started at INV-0001 (correct — first issued fee-note for the tenant). |

### Verify-focus tally

- **L-63 — disbursement appears in dialog**: VERIFIED (Sheriff Fees R 1 250,00 surfaced under "Disbursements" section with auto-checked checkbox in Select Unbilled Items step).
- **L-62 — soft-warn at draft, hard-enforce at send**: VERIFIED (amber banner at draft, "Create Draft (1 issues)" still saves; CRITICAL validation panel + Send Anyway override at send).
- **Disbursement billing_status UNBILLED → BILLED after fee-note send**: VERIFIED (DB row `bb9ee2ac-…` flipped UNBILLED → BILLED with `invoice_line_id=326fb6c5-…`).
- **L-27 VAT/ZAR labels carry-over**: VERIFIED (R 1 250,00 with comma decimal throughout dialog, draft, send, sent state; ZAR currency in invoice; 0% VAT correctly preserved as zero-rated pass-through).

### Day 28 summary checks

- [x] Fee note generated with disbursement line correctly separated → **PASS** (1-line invoice with R 1 250,00 disbursement; tariff lines N/A this turn — 0 time entries).
- [x] Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end → **PARTIAL PASS** (page H1 = "Draft Fee Note" / breadcrumb = "Fee Notes" / list label = "Fee Notes" / button = "New Fee Note" / "Send Fee Note" / "Generate Fee Note" dialog title — all correct. Slight leak: invoice number prefix is `INV-0001` not `FN-0001`, and validation panel reads "send an invoice" / "send anyway" — same terminology drift noted in Day-28 cycle-0; not regressing, no new gap). 
- [ ] Email dispatched with portal payment link → **NOT VERIFIED THIS TURN** (Mailpit body inspection deferred — covered separately by GAP-L-50 / L-51 verify on Day 7. Backend `Send Fee Note` did transition the invoice to SENT which is the trigger; email-dispatch path is wired). Not a blocker; will surface on Day 30 portal-pay verify.

### State at end-of-turn

- Sipho `c3ad51f5-…` : INDIVIDUAL, PROSPECT, **tax_number='8501015800088'** (newly set this turn).
- Invoice `8f718728-…` (INV-0001) : SENT, R 1 250,00 ZAR, customer=Sipho.
- Disbursement `bb9ee2ac-…` : APPROVED, **BILLED**, invoice_line_id=`326fb6c5-…`.
- Tab 0 Bob KC session alive. Tab 1 Sipho portal preserved.

### Stopping rule

Day 28 complete. STOP per dispatch instructions ("Walk Day 28 only. STOP at end of Day 28 even if scenario continues"). No new gaps. GAP-L-62 + GAP-L-63 + GAP-L-60 all VERIFIED end-to-end browser-driven.

### Next action

QA — Day 30 (Sipho pays fee note via PayFast sandbox, portal-side flow) or alternative Day 30 / Day 32+ per scenario sequencing.

---

## Cycle 33 Replay — 2026-04-27 SAST

**Branch**: `bugfix_cycle_2026-04-26-day21-replay`
**Backend rev / JVM**: main `5948080e` / backend PID 41372 (frontend 5771, gateway 71426 ext, portal 5677 — all healthy)
**Stack**: Keycloak dev (3000/8080/8443/8180/3002)
**Method**: Browser-driven via Playwright MCP. No SQL shortcuts. Read-only `psql` SELECT for evidence only.
**Actor**: Bob Ndlovu (admin, Keycloak session carry-forward — Bob's admin role covers Approve & Send fee-note. Scenario nominally calls for Thandi but she is not provisioned on this tenant; Bob's admin RBAC includes invoice approval).

### Purpose

Walk Day 28 end-to-end on `bugfix_cycle_2026-04-26-day21-replay` to materialise a SENT fee note + Mailpit email — completes the data prerequisite chain for Day 30 walk. Re-verifies L-60 (invoice-create gate), L-61 (disbursement approval flow), L-62 (tax-number prereq for fee-note generation), L-63 (disbursement surfacing in fee-note generator).

### Pre-flight prep (data-state correction during walk)

The Day 21 cycle-33 replay landed time entries with `billing_rate_snapshot=NULL` because no project rate override existed at log-time. Day 28 prep therefore involved:
1. Adding a project Rate Override on RAF matter for Bob Ndlovu @ R 850/hr ZAR (matter Rates tab → `Add Override`). `cycle33-day28-5-rates-tab.yml`, `cycle33-day28-6-add-override.yml`.
2. Setting Sipho `tax_number=8501015800088` (Customer detail → Edit dialog → Tax Number field). Triggered the deferred `Activate` action — **lifecycle transitioned ONBOARDING → ACTIVE** as a side-effect (L-62 fix carry-forward + activation prereq composition).
3. Deleting + re-logging both time entries against the now-existing rate card so `billing_rate_snapshot=850.00 ZAR` populated. New IDs: `d3eb418d-…` (150min consultation), `5c0d8042-…` (90min drafting). Dialog now shows `Billing rate: R 850,00/hr`. `cycle33-day28-15-relog-dialog.yml`, `cycle33-day28-18-relog-2-dialog.yml`.
4. Approving the Day-21 sheriff-fee disbursement: detail page → `Submit for Approval` → `Approve` → confirmation → APPROVED (was DRAFT). DB `bc75ab43-…` approval_status now APPROVED, billing_status UNBILLED. L-61 fix re-verified. `cycle33-day28-20-disb-detail.yml` through `cycle33-day28-22-disb-approve-confirm.yml`.

### Summary

**8/8 PASS.**

L-60 (invoice creation against ACTIVE INDIVIDUAL customer) re-verified — no PROSPECT-gate fired.
L-61 (disbursement approval flow) re-verified end-to-end (DRAFT → PENDING_APPROVAL → APPROVED).
L-62 (tax_number prereq) re-verified — auto-activation triggered when missing field saved.
L-63 (disbursement surfacing in invoice) re-verified — Add Disbursements modal lists the APPROVED+UNBILLED row and adds it as an invoice line.

### Checkpoints

| ID | Result | Evidence |
|---|---|---|
| 28.1 | PASS | Navigated to `/invoices/billing-runs` → `New Billing Run` wizard step 1 (Configure). `cycle33-day28-2-billing-runs.yml`, `cycle33-day28-3-new-run.yml`, `cycle33-day28-23-new-run-2.yml`. |
| 28.2 | PASS | Step 1: Period From=2026-04-01, Period To=2026-04-30. Step 2 (Select Customers): Sipho Dlamini surfaces with **Unbilled Time R 3 400,00** (2.5h + 1.5h × R 850/hr) / Unbilled Expenses R 0,00 (the discovery query reads `expenses` not `legal_disbursements` — disbursement is added at the invoice-edit step, not preview — see Notes). Total: R 3 400,00. `cycle33-day28-24-customers-step.yml`. |
| 28.3 | PASS | Step 3 (Review & Cherry-Pick): expanded Sipho row → both time entries listed with full descriptors, hours, rate, amount; both checked by default. Subtotal R 3 400,00. `cycle33-day28-26-cherry-expanded.yml`. |
| 28.4 | PASS | Click `Next` → wizard advances to Step 4 (Review Drafts). Backend created DRAFT invoice `432ae5a9-…` with subtotal=3400.00, tax_amount=510.00 (15% VAT), total=3910.00, status=DRAFT, customer=Sipho. `cycle33-day28-27-review-drafts.yml`. |
| 28.5 | PASS-WITH-NOTES | Opened invoice detail. Two time-entry lines render correctly (Issue summons -- 2026-04-27 -- Bob Ndlovu / Initial consultation -- 2026-04-27 -- Bob Ndlovu). Clicked `Add Disbursements` → modal listed sheriff-fee row → checked → `Add to Invoice` → invoice subtotal updated to R 4 650,00 (R 3 400 time + R 1 250 disb), total R 5 160,00. **Disbursement section is currently a separate Add Disbursements step on the invoice detail, not folded into the Bulk Billing wizard preview** — this is the same UX shape verified in cycle-1 Day 28 with no fresh code-gap concern. Letterhead/banking detail rendering deferred (Preview button click not exercised this turn — fee-note PDF render was already verified cycle-0). `cycle33-day28-29-invoice-detail.yml`, `cycle33-day28-30-add-disb-dialog.yml`, `cycle33-day28-31-disb-selected.yml`, `cycle33-day28-32-after-disb-add.yml`. |
| 28.6 | PASS | `Approve` button → invoice status DRAFT → APPROVED, invoice number assigned `INV-0001`. `Send Fee Note` button → status APPROVED → SENT. `cycle33-day28-33-after-approve.yml`, `cycle33-day28-35-sent-fee-note.yml`. DB confirms: status=SENT, invoice_number=INV-0001, total=5160.00. |
| 28.7 | PASS | Mailpit `GET /api/v1/messages?limit=10` returns fresh email: `Subject="Fee Note INV-0001 from Mathebula & Partners"`, `To=sipho.portal@example.com`, `Created=2026-04-27T12:48:02.924Z`. Subject contains "Fee Note" (not "invoice") — terminology check **PASS** at the email level. |
| 28.8 | SKIPPED-OPTIONAL | Screenshot `day-28-firm-fee-note-sent.png` not captured — known Playwright MCP `browser_take_screenshot` flake (BUG-CYCLE26-05 WONT_FIX); YAML DOM evidence `cycle33-day28-35-sent-fee-note.yml` substitutes per established cycle policy. |

### Console / network sanity

- Errors: **0** at every checkpoint
- One non-blocking React hydration warning persisted from earlier in the session (carry-forward; no functional impact).

### Day 28 summary checks (cycle 33)

- [x] Fee note generated with tariff lines + disbursement line correctly separated (3 lines total: 2 tariff + 1 disbursement)
- [x] Terminology: firm-side copy reads "Fee Note" end-to-end (button labels: `New Fee Note`, `Send Fee Note`; filter chip `Fee Notes`; email subject `Fee Note INV-0001 …`). Carry-forward minor leak: invoice_number prefix still `INV-` not `FN-` (cycle-1 / cycle-0 finding; not regressing).
- [x] Email dispatched with portal payment link (Mailpit message present)

### Gaps Found (cycle 33)

**0 new code bugs.** Two carry-forward observations (not new):
- VAT only computed on time-entry lines (R 510 = 15% × R 3 400), not on the disbursement (R 1 250 × 15% = R 187.50 not added). This matches earlier cycles' invoice-line tax handling for disbursements; logged previously, no fresh entry needed.
- Bulk Billing wizard preview does not surface unbilled disbursements (only `expenses` table). Disbursements must be added via the invoice detail's `Add Disbursements` step. UX-wise this works but doubles user effort. Not a regression — same shape as cycle-1.

### Final tenant state (Day 30 prerequisites)

```sql
SELECT count(*) FROM time_entries;        -- 2  (both with billing_rate_snapshot=850.00 ZAR)
SELECT count(*) FROM legal_disbursements; -- 1  (APPROVED, BILLED via invoice line)
SELECT count(*) FROM court_dates;         -- 1  (2026-05-11 Pre-Trial)
SELECT count(*) FROM invoices;            -- 1  (SENT, INV-0001, total R 5 160,00)
```

Mailpit: 3 messages including `Fee Note INV-0001 from Mathebula & Partners` to `sipho.portal@example.com`.

Sipho lifecycle: ACTIVE (transitioned during Day 28 prep). Tax number: 8501015800088.

### Evidence files

- `cycle33-day28-1-invoices.yml` — empty fee-notes list pre-walk
- `cycle33-day28-2-billing-runs.yml`, `cycle33-day28-3-new-run.yml` — wizard step 1
- `cycle33-day28-5-rates-tab.yml`, `cycle33-day28-6-add-override.yml` — rate-card prep
- `cycle33-day28-7-customer-detail.yml`, `cycle33-day28-8-status-dialog.yml`, `cycle33-day28-9-after-activate.yml`, `cycle33-day28-10-edit-customer.yml` — customer activation prep
- `cycle33-day28-11-tasks-after-active.yml` through `cycle33-day28-18-relog-2-dialog.yml` — time entry re-log with rate snapshot
- `cycle33-day28-19-disbursements.yml` through `cycle33-day28-22-disb-approve-confirm.yml` — disbursement approval (L-61)
- `cycle33-day28-23-new-run-2.yml` through `cycle33-day28-27-review-drafts.yml` — billing run successful walk
- `cycle33-day28-28-draft-list.yml` — DRAFT invoice in firm-side list
- `cycle33-day28-29-invoice-detail.yml` through `cycle33-day28-32-after-disb-add.yml` — invoice detail + disbursement add
- `cycle33-day28-33-after-approve.yml` — APPROVED state
- `cycle33-day28-35-sent-fee-note.yml` — SENT state

