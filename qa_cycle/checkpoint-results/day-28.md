# Day 28 — Firm generates first fee note (bulk billing)  `[FIRM]`

**Date**: 2026-04-30 (cycle 14)  
**Actor**: Thandi Mathebula (Owner, `thandi@mathebula-test.local` / `SecureP@ss1`)  
**Stack**: Keycloak dev stack (frontend `:3000`, gateway `:8443`, backend `:8080`, KC `:8180`).  
**Result**: BLOCKED — Gap **OBS-2102** filed.

---

## Pre-flight

- Stack health: backend 8080 healthy, gateway 8443 healthy, frontend 3000 healthy, KC `master` healthy (`svc.sh status`).
- User-swap Bob → Thandi via in-app user-menu Sign Out → KC realm logout → Sign In as Thandi. Sidebar avatar `TM` confirms swap.
- Day 21 retry successful: 2 time entries on RAF-2026-001 (4h total, all billable) + 1 disbursement (R 1 250,00) on Sipho's matter.

## Phase 1 — Bulk Billing wizard configure step (28.1, 28.2 partial) — **BLOCKED at step 2**

| ck | Step | Result |
|----|------|--------|
| 28.1 | `Finance > Billing Runs > New Billing Run` | PASS — wizard opens at step 1 ("Configure Billing Run"). Steps shown: Configure / Select Customers / Review & Cherry-Pick / Review Drafts / Send. |
| 28.2 (partial) | Period From=2026-04-01, Period To=2026-05-31 → Next | **FAIL at step 2**: "Select Customers" panel reads *"No customers with unbilled work found for this period."* — Sipho is **not listed**, despite having 4h of billable time entries + a R 1 250 sheriff disbursement on RAF-2026-001 within the period. |

Note: the breadcrumb header reads `Fee Notes > billing-runs > new` so terminology partially correct.

Evidence: `qa_cycle/evidence/day-28/billing-run-no-customers.png`.

## Diagnosis — OBS-2102

Backend SQL in `BillingRunSelectionService.discoverCustomers(...)` filters `WHERE c.lifecycle_status = 'ACTIVE'`. Sipho's lifecycle_status is `ONBOARDING` (Day 2 onboarding never advanced him). API confirms via `/api/customers`:
```json
{ "id": "a30bb16b-...", "name": "Sipho Dlamini", "status": "ACTIVE", "lifecycleStatus": "ONBOARDING", "customerType": "INDIVIDUAL", "taxNumber": null, ... }
```

Two stacked blockers prevent activation via UI:

1. **Edit Customer button missing on Sipho's customer page**. Source `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx:606` should render the `EditCustomerDialog` trigger when `customer.status === "ACTIVE" && customer.lifecycleStatus !== "ANONYMIZED"`. For Sipho the Edit button does not paint, even though the Archive button (which sits in the same fragment) does. For Moroka (TRUST) the Edit button **does** paint. Reproducible across hard reload, multiple sessions, with no console or server-side errors. Distinct UI bug.

2. **Activate prereq requires `tax_number`**. `Change Status > Activate` opens a Prerequisites dialog reading *"Tax Number is required for Customer Activation. Fill the Tax Number field on the customer profile."* `Check & Continue` does not override. The only UI surface to set `tax_number` is the missing Edit dialog. SA Legal field group on the customer profile only carries ID/Postal/Correspondence/Referred By — no tax_number field. Onboarding tab also does not surface tax_number for retro-fill.

Compounded effect: **no UI path to set Sipho's `tax_number`** → cannot activate Sipho → cannot include him in Bulk Billing.

Per QA mandate ("No SQL shortcuts. APIs and browser UI only" + "QA must drive browser, not REST"), QA cannot work around this via direct backend mutation.

Filed as **OBS-2102 — Sipho activation blocked: Edit dialog button missing + tax_number prereq unsettable**. Spec at `qa_cycle/fix-specs/OBS-2102.md`.

## Day 28 checkpoints

- [ ] **Fee note generated with TIME line-type time entries + EXPENSE disbursement line correctly separated** — **BLOCKED** (cannot select Sipho in bulk billing wizard).
- [ ] Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end — **N/A** (cannot reach generation step). Note: the `New Billing Run` page header still reads `Invoices` in the breadcrumb but `Fee Notes` further up — partial terminology pass.
- [ ] Email dispatched with portal payment link — **BLOCKED** (no fee note generated).

## Console / network

- Console errors: 0 across user-swap + bulk billing wizard navigation + customer detail navigation.
- 5 network 404s logged on `/api/customers/...` and `/api/billing-runs/eligible-customers` — these are diagnostic curl probes from QA, not product errors. Production wizard uses Next.js server actions (POST `/org/.../billing-runs/new`).

## QA Position

**Day 28**: BLOCKED — cannot proceed past wizard step 2. Two stacked product bugs (Edit dialog non-render + INDIVIDUAL tax_number prereq) prevent customer activation.

**Cascading impact**:
- Day 30 (PayFast payment) — depends on Day 28 fee note → BLOCKED.
- Day 45 (second info request + trust deposit) — independent, can continue.
- Day 60 (matter closure SoA) — partial impact; depends on settled fee notes.

**Stop point**: filed gap OBS-2102 at HIGH severity. Halted before Day 30. Day 22-27 have no scenario steps (skipped per scenario file structure).

**Next dispatch**: needs Dev or Product agent triage of OBS-2102 — either (a) fix the missing Edit button rendering for INDIVIDUAL ZA customers, or (b) relax the LIFECYCLE_ACTIVATION tax_number prereq for INDIVIDUAL customers.

---

**Time on day**: ~30 min (login swap, wizard probing, customer page diagnostics, gap filing, evidence capture).  
**Tool count**: ~80 calls.  
**No regressions in Days 0-21 evidence.**
