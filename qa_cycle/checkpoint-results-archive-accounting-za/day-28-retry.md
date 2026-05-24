# Day 28 — Retry (cycle 15) — Bulk Billing Fee Note

**Date**: 2026-04-30 (cycle 15, post OBS-2102 fix)
**Actor**: Thandi Mathebula (Owner, `thandi@mathebula-test.local` / `<redacted>`)
**Stack**: Keycloak dev stack (frontend `:3000`, gateway `:8443`, backend `:8080`, KC `:8180`).
**Result**: BLOCKED — gap **OBS-2104** filed; OBS-2102 verified PARTIAL_PASS.

---

## Pre-flight

- Stack health verified: backend, gateway, frontend, portal all healthy.
- Frontend `.next/` cache cleared and frontend restarted to ensure OBS-2102 fix is loaded.
- Already authenticated as Thandi (TM avatar in sidebar).

## Step 1 — OBS-2102 Verification

### Bug 2 — `tax_number` INDIVIDUAL skip (PASS — VERIFIED)

| ck | Step | Result |
|----|------|--------|
| 1 | `Customers > Sipho Dlamini > Change Status > Activate` opens "Activate Client" alert dialog | PASS — no Prerequisites blocking dialog appears. |
| 2 | Click `Activate` confirm → backend processes transition | PASS — `CustomerLifecycleService` log: *"Customer a30bb16b-… lifecycle transitioned from ONBOARDING to ACTIVE by actor 0946d0b2-…"*. No error toast. |
| 3 | Refresh customer detail page → confirm both badges read `Active` | PASS — `Sipho Dlamini Active Active` (status badge + lifecycle badge). |

Evidence: `qa_cycle/evidence/day-28-retry/obs-2102-verify-sipho-active.png`.

Backend `StructuralPrerequisiteCheck` per-field skip for INDIVIDUAL @ LIFECYCLE_ACTIVATION works as designed — Sipho activates without `tax_number`.

### Bug 1 — Edit button rendering on Sipho's customer page (FAIL — REOPENED as **OBS-2103**)

After OBS-2102 fix moved `EditCustomerDialog` out of the `customer.status === "ACTIVE"` clause and gated it only on `customer.lifecycleStatus !== "ANONYMIZED"`, the Edit button **still does not render** on Sipho's customer page action row, even after Sipho is now ACTIVE/ACTIVE.

| Customer | status | lifecycleStatus | customerType | Action row buttons (rendered in DOM) |
|----------|--------|-----------------|--------------|---------------------------------------|
| Sipho Dlamini | ACTIVE | ACTIVE | INDIVIDUAL | Change Status, Run Conflict Check, Generate Document, Export Data, Anonymize, **Archive** (no Edit) |
| Moroka Family Trust | ACTIVE | PROSPECT | TRUST | Change Status, Run Conflict Check, Generate Document, Export Data, Anonymize, **Edit** (no Archive) |

Probed via `document.querySelector('#lifecycle-transition').children` → exactly 6 children, missing one of Edit/Archive depending on customer.

RSC payload contains both buttons (`__next_f.push` raw stream defines `49c:[..."button"..."Edit"]` referenced by `EditCustomerDialog`'s children — confirmed via JS introspection). Hydration drops one. No console errors logged. The two render mutually exclusively across customers — strongly suggesting a Radix UI/React 19 Dialog ↔ AlertDialog Slot hydration collision when adjacent triggers wrap `<Button>` via `asChild`.

Filed as **OBS-2103 — Edit and Archive buttons render mutually exclusively on customer detail action row** (MEDIUM, not a workflow blocker since activation now uses Change Status → Activate, not Edit). Spec at `qa_cycle/fix-specs/OBS-2103.md`.

Evidence: `qa_cycle/evidence/day-28-retry/obs-2102-verify-edit-button.png` (Sipho ONBOARDING, no Edit), `obs-2102-verify-sipho-active.png` (Sipho ACTIVE/ACTIVE, still no Edit).

**OBS-2102 disposition**:
- Bug 1 — REOPENED as OBS-2103 (cosmetic; not a workflow blocker).
- Bug 2 — VERIFIED FIX. Sipho activates without tax_number. SARS-correct invoice path remains gated on `INVOICE_SEND_ONLY` `tax_number` requirement (unchanged).

## Step 2 — Day 28 Bulk Billing Wizard

| ck | Step | Result |
|----|------|--------|
| 28.1 | `Finance > Billing Runs > New Billing Run` | PASS — wizard opens at step 1 ("Configure Billing Run"). |
| 28.2 (config) | Period From=2026-04-01, Period To=2026-05-31 → Next | PASS — wizard advances to step 2. Backend creates billing run `abca60e3-9f16-46c8-a402-0920a49ec42f`. |
| 28.2 (select) | "Select Customers" panel reads | **FAIL**: *"No customers with unbilled work found for this period."* Sipho is **still not listed** despite being ACTIVE. |

Backend log: `BillingRunSelectionService — Loaded preview for billing run abca60e3-... — 0 customers, total unbilled: 0`.

Evidence: `qa_cycle/evidence/day-28-retry/billing-run-still-empty-post-activation.png`.

### Diagnosis — OBS-2104

`BillingRunSelectionService.discoverCustomers(...)` SQL has two cascade defects:

**(1) Time entries with NULL `billing_rate_currency` are excluded.** Per OBS-2101 WONT_FIX cascade, when no rate card is configured, `RateSnapshotService.snapshotRates(...)` does NOT call `entry.snapshotBillingRate(...)` — leaving `billing_rate_currency` as NULL. The Bulk Billing query line 498 filters `te.billing_rate_currency = :currency`, which excludes NULL rows. Sipho's 4h time entries (logged Day 21 with the yellow no-rate warning) are dropped.

**(2) `legal_disbursements` are not joined.** The query joins only `expenses e ON e.project_id = p.id` (line 501). Sipho's R 1,250 sheriff fee is in the `legal_disbursements` table (separate vertical model), not `expenses`. The query never sees it.

Combined effect: Sipho has 0 candidate `time_entries` AND 0 candidate `expenses` → `HAVING COUNT > 0 OR COUNT > 0` is false → Sipho is excluded.

Filed as **OBS-2104 — Bulk Billing wizard excludes Sipho even after activation: legal_disbursements not in expenses table + time entries with NULL currency** (HIGH severity, blocks Day 28 fee-note generation). Spec at `qa_cycle/fix-specs/OBS-2104.md`.

Per QA mandate ("No SQL shortcuts"), QA cannot work around via direct backend mutation, rate-card seeding, or disbursement re-keying.

## Day 28 checkpoints

- [ ] **Fee note generated with TIME line-type time entries + EXPENSE disbursement line correctly separated** — **BLOCKED** (cannot select Sipho in bulk billing wizard).
- [ ] Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end — **N/A** (cannot reach generation step). Note: breadcrumb header reads `Fee Notes` further up but `Invoices` slug; acceptable partial pass.
- [ ] Email dispatched with portal payment link — **BLOCKED** (no fee note generated).

## Console / network

- Console errors: 0 across post-activation customer detail navigation, billing-run wizard step 1 → step 2 (legitimate Next.js HMR/CORS warnings only).
- Backend logs clean — billing run created successfully, preview returned 0 customers as designed by current SQL.

## QA Position

**OBS-2102**: PARTIAL_PASS_VERIFIED.
- Bug 2 (tax_number INDIVIDUAL skip) → VERIFIED FIX.
- Bug 1 (Edit button missing) → REOPENED as OBS-2103 (cosmetic, not blocking).

**Day 28**: BLOCKED — cannot proceed past wizard step 2. New gap **OBS-2104** filed (HIGH).

**Cascading impact**:
- Day 30 (PayFast payment) — depends on Day 28 fee note → BLOCKED.
- Day 45 (second info request + trust deposit) — independent, can continue.
- Day 60 (matter closure SoA) — partial impact; depends on Day 28+30 fee notes settled.

**Stop point**: 2 gaps filed at OBS-2102 retry — OBS-2103 (MEDIUM, cosmetic) + OBS-2104 (HIGH, Day 28 blocker). Halted before Day 30. Days 22-27 have no scenario steps (skipped per scenario file structure).

**Next dispatch**: needs Dev or Product agent triage of OBS-2104 — choose between
- (a) loosen `te.billing_rate_currency = :currency` to also accept NULL (treat as run currency, render TIME line at R 0,00 with note),
- (b) UNION `legal_disbursements` into the SQL alongside `expenses` (preferred — sheriff fees are real recoverable disbursements that should be billed),
- (c) both.

OBS-2103 can be triaged separately (lower priority, no workflow impact).

---

**Time on day**: ~30 min (verify OBS-2102 Bug 1+2, billing-run wizard probing, root-cause analysis via backend SQL inspection, gap filing for OBS-2103 + OBS-2104).
**Tool count**: ~70 calls.
**No regressions in Days 0-21 evidence.**
