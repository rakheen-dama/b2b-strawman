# Cycle 5 Turn 2 — Fix Re-verification

**Date**: 2026-04-11
**Branch**: `bugfix_cycle_kc_2026-04-11`
**Actor**: Thandi Mathebula (owner, thandi@mathebula-test.local / SecureP@ss1)
**Stack**: Keycloak dev stack (ports 3000 / 8080 / 8443 / 8180), all 4 services UP
**Tenant under test**: `tenant_5039f2d497cf` (mathebula-partners)
**Scope**: Re-verify 5 Cycle 5 Dev fixes (PRs #998/#999/#1000/#1001/#1002 + V93 follow-up #1003) and close out GAP-S5-01 Contingency end-to-end

---

## Summary

All 5 FIXED items re-verified as **VERIFIED**. GAP-S5-01 promoted from VERIFIED_DIALOG to **VERIFIED end-to-end** (Contingency engagement letter persisted to DB). No regressions observed, no new HIGH blockers filed.

| ID | Prior Status | New Status | PR |
|----|--------------|------------|----|
| GAP-S5-05 | FIXED | **VERIFIED** | #998 + V93 #1003 |
| GAP-S5-04 | FIXED | **VERIFIED** | #999 |
| GAP-S5-06 | FIXED | **VERIFIED** | #1000 |
| GAP-S4-06 | FIXED | **VERIFIED** | #1001 |
| GAP-S4-05 | FIXED | **VERIFIED** | #1002 |
| GAP-S5-01 | VERIFIED (dialog only) | **VERIFIED (end-to-end)** | #995 |

---

## GAP-S5-05 — PASS (Duplicate FICA packs eliminated)

### 1. DB sanity check (V92 + V93 migrations applied)

```sql
SELECT schemaname, slug, customer_type, active FROM ...
```

Result:
```
 tenant_5039f2d497cf | legal-za-client-onboarding            | ANY        | f  <-- legacy, deactivated
 tenant_5039f2d497cf | legal-za-individual-client-onboarding | INDIVIDUAL | t  <-- typed, active
 tenant_5039f2d497cf | legal-za-trust-client-onboarding      | TRUST      | t  <-- typed, active
 tenant_555bfc30b94c | legal-za-client-onboarding            | ALL        | f  <-- legacy (V93), deactivated
 tenant_555bfc30b94c | legal-za-individual-client-onboarding | INDIVIDUAL | t
 tenant_555bfc30b94c | legal-za-trust-client-onboarding      | TRUST      | t
```

Both tenants, both legacy customer_type variants (`ANY` and `ALL`), all `active=false`. Typed variants active.

### 2. INDIVIDUAL client — Thembi Khumalo (new)

- Created via New Client dialog: `id=3b3ab147-6d73-44cb-9f6c-234600826ccc`, customer_type=INDIVIDUAL
- Change Status → Start Onboarding → confirm
- DB verification:
  ```
  customer lifecycle_status: ONBOARDING
  checklist_instances: 1 row
    id=3ea50378-5486-489f-8df3-4d53c7d8b36a
    slug=legal-za-individual-client-onboarding
    customer_type=INDIVIDUAL
    item count: 9
  ```
- Backend log:
  ```
  Auto-instantiated checklist 'Legal Individual Client Onboarding' for customer 3b3ab147...
  Instantiated 1 checklist(s) for customer 3b3ab147... (type=INDIVIDUAL, typedAvailable=true)
  ```
- UI verification: `/customers/3b3ab147.../?tab=onboarding` shows a single "Legal Individual Client Onboarding" section (9 items). No legacy `legal-za-client-onboarding` pack attached.

**Screenshot**: `qa_cycle/screenshots/cycle5-t2-thembi-checklist.png`

### 3. TRUST client — Sibiya Family Trust (new)

- Created via New Client dialog: `id=18ff39fc-9ae3-4103-b811-56085917ecbb`, customer_type=TRUST
- Change Status → Start Onboarding → confirm
- DB verification:
  ```
  customer lifecycle_status: ONBOARDING
  checklist_instances: 1 row
    id=96811bb1-86a5-4720-97ca-24aeaf790e95
    slug=legal-za-trust-client-onboarding
    customer_type=TRUST
    item count: 12
  ```
- Backend log:
  ```
  Instantiated 1 checklist(s) for customer 18ff39fc... (type=TRUST, typedAvailable=true)
  ```
- Item names (sort_order 1..12):
  ```
  Trust Deed
  Letters of Authority
  Trustee 1 ID
  Trustee 2 ID
  Proof of Trust Banking
  SARS Trust Tax Number
  Beneficial Ownership Declaration
  Source of Funds Declaration
  Engagement Letter Signed
  Conflict Check Performed
  FICA Risk Assessment
  Sanctions Screening
  ```
  All 4 trust-specific items present (Trust Deed, Letters of Authority, Trustee IDs, SARS Trust Tax).

**Result**: VERIFIED — duplicate packs eliminated, exactly 1 typed checklist per new client.

---

## GAP-S5-04 — PASS (Adverse Party Customer dropdown populated)

- Navigated to Moroka Estate matter `095529c5-afdc-4714-8a7f-589136280cad` → Adverse Parties tab → clicked "Link Adverse Party" button.
- Dialog opened with 3 selects:
  1. Adverse party select — populated with "Road Accident Fund" (previously-registered adverse party).
  2. **Customer select** — populated with 6 customers: Sipho Dlamini, Moroka Family Trust, Lerato Mthembu, Ndlovu Family Trust, Thembi Khumalo, Sibiya Family Trust. (Previously empty pre-fix.)
  3. Relationship select — populated with Opposing Party / Witness / Co-Accused / Related Entity / Guarantor.
- Selected: adverse party = Road Accident Fund (`e22bd841-7a99-4f0f-8f90-b3c8dbccf283`), customer = Moroka Family Trust (`ac433c2c-cbe5-47f5-826d-602989e7f099`), relationship = Opposing Party (default).
- Clicked "Link Party".
- DB verification:
  ```
  adverse_party_links row:
    id=300068f0-b1cf-41a2-98ca-2505e3b90137
    adverse_party_id=e22bd841-7a99-4f0f-8f90-b3c8dbccf283 (Road Accident Fund)
    project_id=095529c5-afdc-4714-8a7f-589136280cad (Moroka Estate)
    customer_id=ac433c2c-cbe5-47f5-826d-602989e7f099 (Moroka Family Trust)
    relationship=OPPOSING_PARTY
    created_at=2026-04-11 19:15:43+00
  ```

**Result**: VERIFIED — fetchCustomers() fix (flat-array + paginated shapes) confirmed working end-to-end. Link persisted with correct customer_id.

---

## GAP-S5-06 — PASS (Proposal Customer combobox populated)

- Navigated to `/proposals` → clicked "New Proposal".
- Dialog opened with Customer combobox (Radix Popover + cmdk).
- MCP note: opening the Radix popover via programmatic events failed as usual — fell back to calling `onOpenChange(true)` via React fiber inspection. This is the established QA workaround for MCP popover friction.
- After opening, popover listed **6 customers** (all PROSPECT/ONBOARDING + any ACTIVE in tenant):
  ```
  Sipho Dlamini        (sipho.dlamini@email.co.za)
  Moroka Family Trust  (trustees@morokatrust.co.za)
  Lerato Mthembu       (lerato.mthembu@email.co.za)
  Ndlovu Family Trust  (trustees@ndlovu-trust.co.za)
  Thembi Khumalo       (thembi.khumalo@email.co.za)
  Sibiya Family Trust  (trustees@sibiya-trust.co.za)
  ```
- Pre-fix all 6 clients would have been filtered out (all PROSPECT/ONBOARDING for legal-za). Fix inverted the filter to keep PROSPECT/ONBOARDING and exclude only OFFBOARDED/OFFBOARDING/ANONYMIZED.

**Screenshot**: `qa_cycle/screenshots/cycle5-t2-proposal-customer-list.png` (popover renders in a portal that captures offscreen; DOM inspection confirms content)

**Result**: VERIFIED — customer list populates, no empty state.

---

## GAP-S5-01 — VERIFIED END-TO-END (Contingency engagement letter persisted)

**Promotes from**: VERIFIED (dialog-level, Cycle 5 turn 1) → **VERIFIED (end-to-end, Cycle 5 turn 2)**

- From the same New Proposal dialog (GAP-S5-06 flow), selected **Lerato Mthembu** from the customer combobox (verifies GAP-S5-06 downstream behaviour as well).
- Note: this dialog does NOT have a separate "Matter" selector — proposals are customer-level, not matter-level. That matches `CreateProposalRequest` schema in `proposals/actions.ts`.
- Filled Title = "Lerato Mthembu RAF Claim — Contingency Engagement Letter".
- Switched Fee Model combobox (Radix Select) to **Contingency** — the Contingency-specific fields appeared (Contingency Percent, Contingency Cap %, Description).
- Values submitted:
  - Contingency Percent = 25
  - Contingency Cap Percent = 25
  - Description = "Standard LPC Rule 59 contingency for RAF plaintiff matter"
- Clicked "Create Proposal". Redirected to `/proposals/8d6bfda1-c2f4-431c-9896-1e60eba3a609`.
- DB verification:
  ```
  id                     = 8d6bfda1-c2f4-431c-9896-1e60eba3a609
  title                  = Lerato Mthembu RAF Claim — Contingency Engagement Letter
  fee_model              = CONTINGENCY
  contingency_percent    = 25.00
  contingency_cap_percent = 25.00
  contingency_description = Standard LPC Rule 59 contingency for RAF plaintiff matter
  status                 = DRAFT
  customer_id            = b0134b4d-3985-441b-9e37-669f53fd3290 (Lerato Mthembu)
  ```

**Note on scope**: The task brief asked for a monetary cap of 1000000.00, but the dialog only exposes `contingencyCapPercent` (percent, not amount) — this matches the underlying `CreateProposalRequest` schema. The current backend model stores contingency caps as percentages (`contingency_cap_percent`), not absolute amounts. This is pre-existing product design, not a regression. Test used 25% cap (the upper bound under LPC Rule 59).

**Result**: VERIFIED end-to-end — contingency engagement letter persists cleanly with all contingency fields populated.

---

## GAP-S4-06 — PASS (Trust tab clean empty state)

- Navigated to Lerato RAF matter `/projects/d04d80a6-c9d5-4256-9121-1d9496c77d09?tab=trust`. Customer has zero trust deposits (never had a ledger card).
- Trust tab panel rendered with:
  ```
  Trust Balance [No Funds]
  R 0,00
  Deposits    R 0,00
  Payments    R 0,00
  Fee Transfers  R 0,00
  [Record Deposit] [Record Payment] [Fee Transfer]
  ```
- Body text checked for regression indicators:
  - `hasUnableToLoad: false`
  - `hasNoClientLedger: false`
  - No 500 error, no "No clientledgercard found with id ..." text
- Backend log: grep for `ClientLedger|500|clientledgercard` in last 150 lines returned **no matches**. Clean.

**Screenshot**: `qa_cycle/screenshots/cycle5-t2-trust-tab-empty.png` (captured overview; trust tab content verified via DOM inspection)

**Result**: VERIFIED — `ClientLedgerService.getClientLedger` now returns zero-balance response instead of throwing ResourceNotFoundException.

---

## GAP-S4-05 — PASS (Trust dashboard nav links present)

- Navigated to `/trust-accounting` dashboard.
- Header anchors (hrefs):
  ```
  <a href="/org/mathebula-partners/trust-accounting/client-ledgers">Client Ledgers</a>
  <a href="/org/mathebula-partners/trust-accounting/transactions">Record Transaction</a>
  ```
- Recent Transactions card contains:
  ```
  <a href="/org/mathebula-partners/trust-accounting/transactions">View all</a>
  ```
- Navigation verification:
  - `/trust-accounting/client-ledgers` loads, heading "Client Ledgers" rendered.
  - `/trust-accounting/transactions` loads, heading "Transactions" rendered.

**Screenshot**: `qa_cycle/screenshots/cycle5-t2-trust-dashboard-nav.png` — clearly shows "Client Ledgers" + "Record Transaction" buttons in the top-right header and "View all" link in the Recent Transactions card.

**Result**: VERIFIED — all 3 nav targets reachable via header/card links.

---

## Evidence Artifacts

- `qa_cycle/screenshots/cycle5-t2-thembi-checklist.png` — INDIVIDUAL 9-item pack, no duplicate
- `qa_cycle/screenshots/cycle5-t2-proposal-combobox.png` — New Proposal dialog shape
- `qa_cycle/screenshots/cycle5-t2-proposal-customer-list.png` — dialog with Customer combobox open
- `qa_cycle/screenshots/cycle5-t2-trust-tab-empty.png` — Lerato RAF matter with clean trust state
- `qa_cycle/screenshots/cycle5-t2-trust-dashboard-nav.png` — Trust Accounting dashboard showing header + Recent Transactions nav

## New Gaps Filed

None. No regressions observed. No new HIGH blockers.

## Notes

- MCP Radix popover friction continues (GAP-S1-01 / GAP-S3-01) — popovers opened via React fiber `onOpenChange` fallback, not real clicks. This is established QA workaround and does not affect the product verdict.
- GAP-S3-03 (FICA doc upload) remains the only remaining LONG-TERM blocker for PROSPECT→ACTIVE lifecycle completion. Out of Cycle 5 scope.
- All 5 Cycle 5 Dev fixes take effect; V93 (PR #1003) successfully caught the customer_type='ALL' legacy row in tenant_555bfc30b94c that V92 missed.
