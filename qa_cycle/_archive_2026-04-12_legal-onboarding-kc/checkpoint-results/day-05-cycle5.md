# Cycle 5 Turn 1 — Re-verification (QA)

**Date**: 2026-04-11
**QA Agent**: general-purpose
**Branch**: `bugfix_cycle_kc_2026-04-11`
**Services**: backend (8080), gateway (8443), frontend (3000), portal (3002) — all UP at start of cycle
**Authenticated user**: Thandi Mathebula (owner) / thandi@mathebula-test.local

## Summary

| Gap | Priority | Status | Notes |
|-----|----------|--------|-------|
| GAP-S4-01 (Trust Account Creation) | HIGH | **VERIFIED PASS** | Dialog works, DB row + audit event confirmed. |
| GAP-S4-02 (FICA TRUST Variant) | HIGH | **PARTIAL PASS + NEW REGRESSION** | TRUST pack (12 items) attaches correctly. But legacy `legal-za-client-onboarding` (ANY, 11 items) ALSO still auto-attaches — customer gets BOTH packs. New GAP-S5-05 filed. |
| GAP-S5-01 (Contingency Fee Model) | HIGH | **VERIFIED PASS (dialog)** | Contingency option present, LPC Rule 59 disclosure renders, contingency fields show. Persistence not tested end-to-end (blocked by GAP-S5-06 below). |
| GAP-S5-02 (Court Date NPE) | HIGH | **VERIFIED PASS** | New court date saved successfully on matter with populated `customer_id`. DB row + no backend error. |
| GAP-S5-03 (Matter customer_id null) | HIGH | **VERIFIED PASS** | New matter `095529c5-afdc-4714-8a7f-589136280cad` has `customer_id` populated. Template instantiation fix works. |
| GAP-S5-04 (Adverse Party Customer Select) | MEDIUM | **NOT CASCADE-FIXED** | Dropdown still empty — separate pre-existing frontend issue in `fetchCustomers()` action. Backend fix alone insufficient. |
| Session 4 Phase F (Trust Deposit) | — | **PASS** | R50,000 DEPOSIT recorded via `/trust-accounting/transactions` → "Record Transaction" → "Record Deposit". |

**New gaps filed**:
- **GAP-S5-05** (HIGH): Legacy `legal-za-client-onboarding` (ANY) pack still auto-instantiates alongside new variants — duplicate onboarding checklists for every new client.
- **GAP-S5-06** (MEDIUM): Proposal dialog Customer dropdown is empty — `fetchCustomersAction` silently failing or returning empty. Blocks end-to-end Contingency proposal creation via UI. Same symptom as GAP-S5-04 (likely common server-action auth/routing issue).
- **GAP-S4-05** (LOW–MEDIUM): Trust Accounting dashboard has no link to "Transactions" sub-page. Record Transaction is only reachable via direct URL `/trust-accounting/transactions`. Purely a nav gap.
- **GAP-S4-06** (MEDIUM): Project Trust tab throws 500 error `No clientledgercard found with id ...` for matters whose customer has no ledger card yet. Should gracefully return 0/empty state instead of 500.

---

## GAP-S4-01 — Trust Account Creation

**Result**: PASS

**Evidence**:
1. Navigated to `/org/mathebula-partners/settings/trust-accounting` as Thandi (owner).
2. "Add Account" button visible next to "Trust Accounts" heading.
3. Clicked button → `CreateTrustAccountDialog` opened with fields: Account Name, Bank Name, Branch Code, Account Number, Account Type (GENERAL default), Opened Date, Primary toggle, Dual Approval toggle, Approval Threshold, Notes.
4. Filled form with: Name=`Mathebula Trust Account`, Bank=`Standard Bank`, Branch=`051001`, Account=`9876543210`, Threshold=`50000`, Notes=`QA Cycle 5 verification trust account`.
5. Clicked "Create Account" → dialog closed → account immediately visible in "Trust Accounts" card labelled **Primary / ACTIVE**.
6. Section 86(6) LPFF advisory text rendered below the account row.
7. Approval Settings section now shows `Single approval above R 50,000`.

**DB verification** (tenant_5039f2d497cf):
```
id                                   | account_name            | bank_name     | branch_code | account_number | account_type | status | is_primary
d8035d50-8433-4cf8-be30-7cdb1c4539cc | Mathebula Trust Account | Standard Bank | 051001      | 9876543210     | GENERAL      | ACTIVE | t
```

**Audit event**:
```
trust_account.created | 2026-04-11 16:43:10.034349+00
```

**Screenshot**: `qa_cycle/screenshots/cycle5-s4-01-trust-account-created.png`

---

## GAP-S4-02 — FICA Pack TRUST Variant

**Result**: PARTIAL PASS + NEW REGRESSION

**Expected**: New TRUST client gets 12-item `legal-za-trust-client-onboarding` pack (Trust Deed, Letters of Authority, Trustee IDs, Trust Banking, SARS Trust Tax etc). New INDIVIDUAL client gets 9-item `legal-za-individual-client-onboarding` pack.

**Steps**:
1. Created new client `Ndlovu Family Trust` via Clients → New Client (type=TRUST, email=`trustees@ndlovu-trust.co.za`).
2. DB confirms creation: `703b4ff0-8182-4724-ba0f-4ad38a7bef13, Ndlovu Family Trust, TRUST, PROSPECT`.
3. Opened client detail page → clicked "Change Status" → "Start Onboarding" → confirm alertdialog → Status changed to ONBOARDING.
4. Opened `?tab=onboarding` on client page.

**Actual DB state** — TWO checklists auto-instantiated for the new TRUST client:

```sql
SELECT ci.id, ct.slug, ct.customer_type, items
FROM tenant_5039f2d497cf.checklist_instances ci
JOIN tenant_5039f2d497cf.checklist_templates ct ON ci.template_id=ct.id
WHERE ci.customer_id='703b4ff0-8182-4724-ba0f-4ad38a7bef13';

id                                   | slug                             | customer_type | items
5e2430c5-a819-4041-85af-cd3d98ecfdda | legal-za-client-onboarding       | ANY           | 11
1d4eb749-febd-4ea9-a25a-e3f84acc47eb | legal-za-trust-client-onboarding | TRUST         | 12
```

Backend log confirms:
```
Instantiated 2 checklist(s) for customer 703b4ff0-8182-4724-ba0f-4ad38a7bef13 (type=TRUST)
```

**TRUST pack items (all 12 present, matching spec)**:
- Trust Deed (required)
- Letters of Authority (required)
- Trustee 1 ID (required)
- Trustee 2 ID (optional)
- Proof of Trust Banking (required)
- SARS Trust Tax Number (required)
- Beneficial Ownership Declaration (required)
- Source of Funds Declaration (required)
- Engagement Letter Signed (required)
- Conflict Check Performed (required)
- FICA Risk Assessment (required)
- Sanctions Screening (required)

**INDIVIDUAL pack items verified via DB query (9 items)**:
- Proof of Identity, Proof of Address, Beneficial Ownership Declaration, Source of Funds, Engagement Letter, Conflict Check, Power of Attorney (optional), FICA Risk Assessment, Sanctions Screening.

**Verdict**: Core fix works — TRUST variant auto-instantiates with correct 12 items. HOWEVER a new regression was introduced: the seeder leaves the legacy `legal-za-client-onboarding` (customer_type=ANY, 11 items) pack active AND auto_instantiate=true. Every new client (TRUST or INDIVIDUAL) will therefore receive BOTH the legacy pack AND the new type-specific pack. The UI renders both sequentially on the customer detail "Onboarding" tab.

**Screenshots**:
- `qa_cycle/screenshots/cycle5-s4-02-trust-onboarding.png` — Client detail header
- `qa_cycle/screenshots/cycle5-s4-02-trust-onboarding-duplicate-packs.png` — Onboarding tab showing BOTH "Legal Client Onboarding" (11 items) and "Legal Trust Client Onboarding" (12 items)

**New gap filed**: GAP-S5-05 HIGH — seeder should either deactivate the legacy ANY-type pack on upgrade OR the auto-instantiation logic should prefer the most-specific `customer_type` match and skip `ANY` when a typed variant exists.

---

## GAP-S5-01 — Contingency Fee Model

**Result**: PASS (dialog verified; persistence blocked by unrelated issue)

**Steps**:
1. Navigated to `/org/mathebula-partners/proposals` → clicked "New Proposal" → dialog opened.
2. Filled title=`Moroka Estate Administration — Contingency Fee`.
3. Opened Fee Model combobox (Radix Select).

**Fee Model options enumerated via DOM**:
```
[
  { "text": "Fixed Fee" },
  { "text": "Hourly" },
  { "text": "Retainer" },
  { "text": "Contingency" }
]
```
**Contingency option present** — this is the core GAP-S5-01 fix.

4. Selected Contingency → dialog repopulated with contingency-specific fields:
   - "LPC Rule 59 (Contingency Fees Act 66 of 1997) caps contingency fees at 25% of amounts recovered. Ensure a written agreement is in place with the client." (disclosure banner)
   - Contingency Percent (%) — numeric input, default `25`
   - Contingency Cap (%) — numeric input, default `25`
   - Description (optional) — placeholder `e.g. RAF plaintiff claim — 25% contingency per LPC Rule 59`
   - Retainer-specific fields correctly hidden.
5. **Could NOT submit end-to-end** because the dialog's Customer dropdown was empty (see GAP-S5-06 below — separate frontend bug). Closed dialog without submitting.

**Backend schema confirmation** (migration V90):
```
tenant_5039f2d497cf.proposals columns:
- contingency_cap_percent numeric
- contingency_percent numeric
- contingency_description character varying
```

**Verdict**: Backend `FeeModel.CONTINGENCY` enum + migration V90 + frontend schema/dialog branch all wired correctly. End-to-end persistence not testable today due to GAP-S5-06 (separate Customer select population issue in the proposals dialog), but the fix-specific surface area is confirmed working.

**Screenshot**: `qa_cycle/screenshots/cycle5-s5-01-contingency-dialog.png` — full Contingency branch of dialog with LPC Rule 59 banner.

---

## GAP-S5-03 — Matter customer_id null (CASCADING)

**Result**: PASS

**Steps**:
1. Pre-state DB query confirmed existing matters (Sipho + Lerato) still have `customer_id=NULL` — expected, pre-fix.
2. Went to `/projects` → clicked "New from Template" → selected "Deceased Estate Administration" → Next.
3. On configure step, filled project name and selected Moroka Family Trust from Customer dropdown → Create Project.

**DB verification** immediately after save:
```
id                                   | customer_id                          | name
095529c5-afdc-4714-8a7f-589136280cad | ac433c2c-cbe5-47f5-826d-602989e7f099 | Moroka Family Trust — Estate Administration
```

`customer_id` is now populated on the new template-instantiated matter.

Matter detail page shows:
- `Client: Moroka Family Trust` (linked to the customer detail page)
- Overview tab: `Customer: Moroka Family Trust`
- `customer_projects` join table row also created (linked_by set).

**Verdict**: `ProjectTemplateService.instantiateTemplate` PR #993 fix is working — new template-created matters have `customer_id` set correctly.

---

## GAP-S5-02 — Court Date NPE (CASCADE from S5-03)

**Result**: PASS

**Steps**:
1. Navigated to the new Moroka Estate matter → clicked "Court Dates" tab.
2. Empty state: "No court dates found for this period." + "New Court Date" button.
3. Clicked "New Court Date" → `ScheduleCourtDateDialog` opened with Matter dropdown pre-populated with 3 matters (including the new Moroka one).
4. Selected Matter=Moroka Estate, Type=HEARING (default), Date=2026-05-20, Court Name=`Johannesburg High Court`, Description=`QA Cycle 5 GAP-S5-02 re-verification`.
5. Clicked "Schedule Court Date" → dialog closed without error.

**DB verification**:
```
id                                   | project_id                           | customer_id                          | date_type | scheduled_date | court_name              | description                          | status
219594f1-2642-4d7a-9df0-7700a36a8bfd | 095529c5-afdc-4714-8a7f-589136280cad | ac433c2c-cbe5-47f5-826d-602989e7f099 | HEARING   | 2026-05-20     | Johannesburg High Court | QA Cycle 5 GAP-S5-02 re-verification | SCHEDULED
```

`customer_id` is correctly populated on the court_dates row (was the null-FK source of the original NPE). Status=SCHEDULED, no transaction rollback, no backend exception.

**Backend log scan** — no new `InvalidDataAccessApiUsageException`, no `NullPointerException` in `CourtCalendarService.toResponse` since Cycle 4.

**Verdict**: The cascade fix from GAP-S5-03 (populating customer_id at matter creation) resolves the GAP-S5-02 crash fully. The court date flow is now end-to-end functional for matters created after the fix.

---

## GAP-S5-04 — Link Adverse Party Customer Select Empty

**Result**: NOT CASCADE-FIXED (still broken, separate root cause)

**Steps**:
1. On the new Moroka Estate matter → "Adverse Parties" tab → "Link Adverse Party" button.
2. Dialog opened with Adverse Party select (populated with "Road Accident Fund") and Customer select.
3. Enumerated Customer select options via DOM:
```
count: 1
opts: [{ "value": "", "text": "-- Select customer --" }]
```
4. Only the placeholder — no actual customers listed.
5. Selected Road Accident Fund anyway — no effect on Customer select; still empty 2 seconds later.

**Diagnosis**: This is a separate, pre-existing frontend issue. `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts::fetchCustomers()` assumes the `/api/customers?size=200` endpoint returns a `PaginatedResponse<{id,name}>` and accesses `result.content`. The backend `CustomerController.listCustomers` returns a **flat array** `ResponseEntity<List<CustomerResponse>>`, not a paginated envelope. Result: `result.content` is undefined, so `setCustomers(undefined ?? [])` → empty array.

Note that `proposals/actions.ts::fetchCustomersAction` already handles both shapes (`Array.isArray(result) ? result : result.content`) — which suggests this mismatch has been silently broken in the legal/adverse-parties action for a while but was only exposed when GAP-S5-03 unblocked the upstream matter creation flow.

**Verdict**: The CASCADE-FIX assumption in the Cycle 4 tracker note was wrong. GAP-S5-04 is NOT automatically fixed by PR #993. It needs a separate 1-line fix in `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts::fetchCustomers()` to handle the flat-array response shape.

**Filed as**: GAP-S5-04 re-opened with `Status: REOPENED` + root cause note.

---

## Session 4 Phase E — Estates Matter From Template

**Result**: PASS (executed as part of GAP-S5-03 verification)

- Moroka Estate matter `095529c5-afdc-4714-8a7f-589136280cad` created from Deceased Estate Administration template.
- 9 tasks auto-created from template (`Tasks 0/9 complete` in Overview).
- `customer_id` correctly populated (the GAP-S5-03 regression check is clean for this matter).

**Note**: Skipped FICA tick-through per scenario (still blocked by GAP-S3-03). Moroka customer remains in ONBOARDING lifecycle but the matter creation path is unblocked.

---

## Session 4 Phase F — First Trust Deposit

**Result**: PASS

**Prerequisites**: Trust account created (GAP-S4-01) + Moroka Estate matter created (Session 4 Phase E).

**Discovery**: The `/trust-accounting` dashboard does not link to the transactions sub-page. The "Record Transaction" button is only found at `/trust-accounting/transactions` (direct URL). Filed as GAP-S4-05 LOW-MEDIUM.

**Steps**:
1. Navigated directly to `/org/mathebula-partners/trust-accounting/transactions`.
2. Clicked "Record Transaction" button → dropdown menu appeared with 5 transaction types (Record Deposit / Payment / Transfer / Fee Transfer / Refund).
3. Clicked "Record Deposit" → `RecordDepositDialog` opened.
4. Filled form with raw UUIDs (the dialog uses plain text inputs for customer/matter, not selectors):
   - Client ID = `ac433c2c-cbe5-47f5-826d-602989e7f099` (Moroka Family Trust)
   - Matter ID = `095529c5-afdc-4714-8a7f-589136280cad` (Moroka Estate)
   - Amount = `50000`
   - Reference = `DEP/2026/001`
   - Description = `Initial trust deposit for estate administration — QA Cycle 5 Session 4 Phase F`
   - Transaction Date = `2026-04-11`
5. Clicked "Record Deposit" → dialog closed → transaction immediately visible in Transaction History list (1 transaction found).

**UI verification**: Transaction row shows "11 Apr 2026 · DEP/2026/001 · Deposit · R 50,000.00 · RECORDED".

**DB verification**:
```
id                                   | trust_account_id                     | transaction_type | amount   | customer_id                          | project_id                           | reference    | status
efcb5b9e-9942-4a96-bbc4-561b797ae02e | d8035d50-8433-4cf8-be30-7cdb1c4539cc | DEPOSIT          | 50000.00 | ac433c2c-cbe5-47f5-826d-602989e7f099 | 095529c5-afdc-4714-8a7f-589136280cad | DEP/2026/001 | RECORDED
```

**Screenshot**: `qa_cycle/screenshots/cycle5-session4f-trust-deposit-recorded.png`

**Session 4 is now COMPLETE** to the extent that Phase E + Phase F are now executable with the merged fixes.

---

## New Gaps Filed This Turn

### GAP-S5-05 — Legacy FICA pack still auto-instantiates alongside new variants
- **Severity**: HIGH
- **Where**: `CompliancePackSeeder` / `ChecklistInstantiationService`
- **Symptom**: Every new client (TRUST or INDIVIDUAL) receives BOTH the legacy `legal-za-client-onboarding` (customer_type=ANY, 11 items) AND the new type-specific pack. Duplicate onboarding checklists.
- **Root cause (likely)**: Seeder adds new variants as `active=true, auto_instantiate=true` but does not set the legacy pack's `auto_instantiate=false` (or `active=false`) during upgrade migration. The auto-instantiation query `WHERE active=true AND auto_instantiate=true AND customer_type IN (ANY, <type>)` picks up both rows.
- **Fix options**:
  1. Seeder should set `active=false` on the legacy `legal-za-client-onboarding` row (deprecation).
  2. Or change the instantiation logic to prefer most-specific customer_type match: if a pack exists for customer_type=TRUST, skip the ANY variant.
- **Blast radius**: Affects Sessions 3/4/5 onboarding UX on new clients. Existing (Cycle 3/4) customers unaffected.
- **Workaround**: Users can ignore/skip the legacy checklist manually.

### GAP-S5-06 — Proposal dialog Customer select is empty
- **Severity**: MEDIUM
- **Where**: `frontend/app/(app)/org/[slug]/proposals/actions.ts::fetchCustomersAction()` or its caller
- **Symptom**: Customer combobox in "New Proposal" dialog only shows "Select a customer..." placeholder. No actual customers populate even though tenant has 4 customers in ONBOARDING.
- **Reproduction**: Login as owner → `/proposals` → "New Proposal" → open Customer dropdown.
- **Related**: GAP-S5-04 has a similar symptom (Customer select empty) but different root cause (paginated vs flat array handling). This proposals case already handles both shapes, so the fetch may be failing silently via the catch block. Inspection needed.
- **Blast radius**: Blocks end-to-end engagement letter creation for all fee models including the newly-added Contingency.
- **Workaround**: None via UI.

### GAP-S4-05 — Trust Accounting dashboard missing transactions link
- **Severity**: LOW-MEDIUM
- **Where**: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`
- **Symptom**: Dashboard shows Trust Balance / Active Clients / Pending Approvals KPIs plus Recent Transactions preview, but no link to the transactions management sub-page. The only way to reach `/trust-accounting/transactions` (where Record Transaction button lives) is by direct URL.
- **Blast radius**: Discoverability — users who land on the dashboard have no path to record a deposit/payment without knowing the URL.
- **Fix**: Add "View All Transactions" / "Manage Transactions" link to the Recent Transactions card, and optionally a top-right "Record Transaction" shortcut on the dashboard.

### GAP-S4-06 — Project Trust tab throws 500 when customer has no ledger card
- **Severity**: MEDIUM
- **Where**: Backend trust-balance service (`No clientledgercard found with id ...` error) + frontend handling
- **Symptom**: On the Moroka Estate matter → Trust tab → panel shows "Unable to load trust balance". Frontend logs show `ApiError: No clientledgercard found with id ac433c2c-cbe5-47f5-826d-602989e7f099` returning HTTP 500.
- **Expected**: Backend should return 0 balance (or 200 with an empty response) when a client's ledger card does not yet exist. Throwing ResourceNotFoundException→500 in this path is inappropriate — every client without a trust deposit is in this state.
- **Blast radius**: Every matter whose customer has not yet received a trust deposit shows an error on the Trust tab.

---

## Backend Log Scan (post-cycle)

- Pre-existing harmless WARN from `scheduling-4` thread: `column p1_0.contingency_cap_percent does not exist` for schemas not yet migrated when the scheduler first ran. Confirmed V90 migration is now applied across all 4 tenant schemas — this WARN does not recur after the startup race window.
- Pre-existing WARN: GAP-S6-06 `SEND_NOTIFICATION ... No recipients resolved for type: ORG_ADMINS` (Matter Onboarding Reminder automation rule). Unchanged from Cycle 4.
- No new 500 / NullPointerException / InvalidDataAccessApiUsageException tied to Cycle 5 activity.
- One security.auth_failed on `/api/customers` GET — from my own direct curl probe, not from the UI traffic.

## Outstanding Known Issues (not tested this turn)
- GAP-S3-03 (FICA document upload) — still blocks PROSPECT→ACTIVE activation for all legal clients.
- GAP-S3-04 (SA Legal field group not auto-attached to matters from template) — re-confirmed on the Moroka Estate matter (tab shows "No custom fields configured").
- GAP-S3-05 (`/projects/new?customerId` route 404) — not re-tested but still expected.
- GAP-S1-01 / GAP-S3-01 (Radix click flakiness) — used pointer-event workaround throughout this turn. WONT_FIX.

## Budget Used
~50/70 MCP tool calls. Stopping at a clean boundary after Session 4 Phase F verification.
