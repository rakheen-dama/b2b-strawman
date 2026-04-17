# Verification Results — REOPENED Gaps (Retry 2)

Date: 2026-04-14
Verified by: QA Agent
Stack: Keycloak dev (localhost:3000/8080/8443/8180)
Branch: `bugfix_cycle_2026-04-14`
PRs under test: #1033 (backend — pack reconciliation), #1034 (frontend — tabs restructure)

---

## GAP-D0-07 (MED, retry 2) — FICA checklist auto-instantiation: VERIFIED

**PR #1033**: Added `reconcileExistingPack()` hook to `AbstractPackSeeder`, overridden in `CompliancePackSeeder` to sync `autoInstantiate` from pack.json to existing DB records on startup.

### Pre-condition checks

1. **Backend restarted** after PR #1033 merge. Reconciliation runner executed:
   - Log: `"Compliance pack fica-kyc-za already applied for tenant tenant_4a171ca30392, reconciling settings"`
   - Log: `"Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed"`

2. **Database template updated** — verified directly:
   ```
   SELECT slug, name, auto_instantiate FROM tenant_4a171ca30392.checklist_templates WHERE pack_id = 'fica-kyc-za';
   slug: fica-kyc-za-accounting | name: FICA KYC — SA Accounting | auto_instantiate: TRUE
   ```

### End-to-end test

1. **Created new customer** "FICA Verification Client" (INDIVIDUAL type) via `POST /api/customers` through gateway session as Thandi (owner). Customer ID: `116db09d-5ad4-46b7-a453-be4cf50e8fe0`. Status: PROSPECT.

2. **Transitioned to ONBOARDING** via `POST /api/customers/{id}/transition` with `{"targetStatus": "ONBOARDING"}`. Response: HTTP 200, `lifecycleStatus: "ONBOARDING"`.

3. **Checklist auto-instantiated** — verified via database:
   ```
   SELECT * FROM tenant_4a171ca30392.checklist_instances WHERE customer_id = '116db09d-...';
   template_name: FICA KYC — SA Accounting | slug: fica-kyc-za-accounting | status: IN_PROGRESS
   ```

4. **11 checklist items created**, all in PENDING status:
   - Certified ID Copy (required, document)
   - Proof of Residence (required, document)
   - Company Registration (CM29/CoR14.3) (required, document)
   - Tax Clearance Certificate (required, document)
   - Bank Confirmation Letter (required, document)
   - Proof of Business Address (optional, document)
   - Resolution / Mandate (optional, document)
   - Beneficial Ownership Declaration (required, document)
   - Source of Funds Declaration (optional, document)
   - Letters of Authority (Master's Office) (required, document)
   - Trust Deed (Certified Copy) (required, document)

5. **Backend log confirmation**:
   - `"Auto-instantiated checklist 'FICA KYC — SA Accounting' for customer 116db09d-..."`
   - `"Instantiated 1 checklist(s) for customer ... (type=INDIVIDUAL, typedAvailable=false)"`
   - `"Customer ... lifecycle transitioned from PROSPECT to ONBOARDING ... (checklistsInstantiated=1)"`

**Verdict**: VERIFIED. The pack reconciliation correctly synced `autoInstantiate=true` to the existing DB template, and the instantiation service now creates the FICA checklist on ONBOARDING transition.

---

## GAP-D0-01 (LOW, retry 2) — Access requests tab switching: REOPENED (retry 3 needed)

**PR #1034**: Restructured Radix Tabs to include `TabsPrimitive.Content` inside `TabsPrimitive.Root`, restored `motion.span` animated indicator, extracted `RequestTable` helper.

### Test procedure

1. Logged in as platform admin (`padmin@docteams.local`) via Keycloak. Navigated to `/platform-admin/access-requests`.

2. **Page renders correctly**: 4 tabs visible (All, Pending, Approved, Rejected). Default tab is "Pending" (selected). Tabpanel shows "No pending access requests." The Radix Tabs DOM structure is correct — 4 `TabsPrimitive.Content` elements exist inside `TabsPrimitive.Root`.

3. **Tab clicks do not switch tabs**: Performed a systematic test clicking all 4 tabs after a fresh page load:
   - Initial: Pending = active
   - Click Approved: Pending = active (no change)
   - Click Rejected: Pending = active (no change)
   - Click All: Pending = active (no change)
   - Click Pending: Pending = active (no change)

4. **DOM inspection**: All Radix Tabs attributes are correctly set (`aria-controls`, `data-state`, trigger IDs). The `TabsPrimitive.Root` has `value={activeTab}` and `onValueChange` handler. But `onValueChange` never fires.

5. **Hydration confirmed**: The component has `__reactFiber` on the DOM node, confirming React hydrated it. No console errors or warnings.

### Analysis

The code structure in `access-requests-table.tsx` (lines 153-187) looks correct:
- `TabsPrimitive.Root` with controlled `value` and `onValueChange`
- `TabsPrimitive.List` with `TabsPrimitive.Trigger` children
- `TabsPrimitive.Content` children inside Root

Despite the correct structure, the `onValueChange` callback is not being invoked when tabs are clicked. This is not a hydration failure (React fiber exists) and not a DOM structure issue (Radix attributes are correct). The root cause may be:
- Radix UI version incompatibility with the unified `radix-ui` package
- A subtle event handling issue (Radix uses `pointerdown` for activation, not `click`)
- The `motion.span` inside each trigger may be interfering with pointer event propagation

**Evidence**: Screenshot at `qa_cycle/checkpoint-results/gap-d0-01-v2-tabs-still-broken.png`

**Verdict**: REOPENED. The structural fix (moving Content inside Root) was correct but insufficient. The tab switching still does not work in the browser. Needs further investigation of event handling.

---

## Summary

| GAP ID | PR | Severity | Previous Status | New Status | Verdict |
|--------|-----|----------|----------------|------------|---------|
| GAP-D0-07 | #1033 | MED | FIXED | VERIFIED | Checklist auto-instantiated on ONBOARDING |
| GAP-D0-01 | #1034 | LOW | FIXED | REOPENED | Tabs still don't switch (retry 3 needed) |

**1 VERIFIED, 1 REOPENED**
