# Day 1 — First Client Onboarding: Kgosi Construction
## Executed: 2026-03-15T21:20Z
## Actor: Bob (Admin), then attempted Alice (Owner)

### Checkpoint 1.1 — Create Kgosi Construction as a new customer
- **Result**: PASS
- **Evidence**: Customer "Kgosi Construction (Pty) Ltd" created successfully via New Customer dialog (Step 1: Name, Type=Company, Email, Phone; Step 2: skipped field intake — only "Contact & Address" shown, no FICA fields per GAP-008B). Customer appears in list with status PROSPECT. ID: `5756c017-0998-4d88-877f-da8314431a3c`.
- **Gap**: GAP-008B (confirmed — FICA field groups not auto-attached during customer creation wizard Step 2)

### Checkpoint 1.1a — Populate all 16 accounting custom fields
- **Result**: PASS
- **Evidence**: Navigated to customer detail page. Added "SA Accounting — Client Details" field group via "Add Group" button (searchable dropdown found the group immediately). All 16 fields populated: Company Registration Number (2019/347821/07), Trading As (Kgosi Construction), VAT Number (4830291746), SARS Tax Reference (9301234567), SARS eFiling Profile (blank), Financial Year-End (2025-02-28), Entity Type (Pty Ltd), Industry SIC Code (41000), Registered Address, Postal Address, Primary Contact Name (Thabo Kgosi), Primary Contact Email, Primary Contact Phone, FICA Verified (Not Started), FICA Verification Date (blank), Referred By (Existing client referral). Saved successfully. Customer Readiness updated to "Required fields filled (7/7)".
- **Gap**: —

### Checkpoint 1.2 — Initiate FICA checklist for Kgosi Construction
- **Result**: PARTIAL
- **Evidence**: No "Checklists" tab available on PROSPECT customers. Transitioned customer to ONBOARDING via Change Status > Start Onboarding. An "Onboarding" tab appeared with a "Generic Client Onboarding" checklist (4 items), NOT the expected "FICA/KYC — SA Accounting" checklist with 9 items. The "Manually Add Checklist" dialog only shows the generic template — no FICA template available. Marked 2 of 4 generic items complete (Confirm client engagement, Verify contact details) as a workaround. Backend API confirms 2/4 items COMPLETED.
- **Gap**: GAP-026 (confirmed — FICA/KYC checklist template not seeded by accounting-za pack; only generic 4-item checklist exists)

### Checkpoint 1.3 — Verify customer lifecycle state
- **Result**: PASS
- **Evidence**: Customer transitioned from PROSPECT to ONBOARDING successfully. Customer detail page shows "Onboarding" badge. Backend API confirms `lifecycleStatus: ONBOARDING`. The lifecycle progression path is visible via the Change Status dropdown and the "Customer Readiness" section.
- **Gap**: —

### Checkpoint 1.4 — Send information request via portal
- **Result**: FAIL (BLOCKER)
- **Evidence**: Cannot access the Requests tab on the customer detail page. After the lifecycle transition to ONBOARDING, the customer detail page (and ALL customer-related pages including the customer list) crash with `TypeError: Cannot read properties of null` during server-side rendering. The page renders as an empty/broken table. This is a persistent crash that affects all customer pages and prevents further testing. Multiple page reloads and re-authentication attempts did not resolve the issue.
- **Gap**: GAP-027 (NEW — Customer detail page SSR crash after ONBOARDING transition; cascades to all customer pages)

### Checkpoint 1.5 — Alice creates engagement letter
- **Result**: FAIL (BLOCKED by GAP-027)
- **Evidence**: Cannot navigate to customer detail page to generate engagement letter. All customer pages crash with TypeError null reference.
- **Gap**: GAP-027

### Checkpoint 1.6 — Send engagement letter for acceptance
- **Result**: FAIL (BLOCKED by GAP-027)
- **Evidence**: Blocked by customer page crash.
- **Gap**: GAP-027

### Checkpoint 1.7 — Accept engagement letter via portal
- **Result**: FAIL (BLOCKED by GAP-027)
- **Evidence**: Blocked by customer page crash.
- **Gap**: GAP-027

### Checkpoint 1.8 — Create engagement (project) from accepted engagement letter
- **Result**: FAIL (BLOCKED by GAP-027)
- **Evidence**: Blocked by customer page crash. Note: Project creation via the Projects page (/org/e2e-test-org/projects) may still work independently, but cannot link to customer or test engagement flow.
- **Gap**: GAP-027

### Checkpoint 1.9 — Set up retainer for Kgosi Construction
- **Result**: FAIL (BLOCKED by GAP-027)
- **Evidence**: Blocked by customer page crash. Retainers page may be accessible separately but linking to customer is blocked.
- **Gap**: GAP-027

## Summary

| Checkpoint | Result | Gap |
|-----------|--------|-----|
| 1.1 — Create customer | PASS | GAP-008B (confirmed) |
| 1.1a — Populate custom fields | PASS | — |
| 1.2 — Initiate FICA checklist | PARTIAL | GAP-026 (confirmed) |
| 1.3 — Verify lifecycle state | PASS | — |
| 1.4 — Information request | FAIL | GAP-027 (NEW, BLOCKER) |
| 1.5 — Engagement letter | FAIL | GAP-027 (BLOCKED) |
| 1.6 — Send engagement letter | FAIL | GAP-027 (BLOCKED) |
| 1.7 — Accept engagement letter | FAIL | GAP-027 (BLOCKED) |
| 1.8 — Create project | FAIL | GAP-027 (BLOCKED) |
| 1.9 — Set up retainer | FAIL | GAP-027 (BLOCKED) |

**Totals**: 2 PASS, 1 PARTIAL, 7 FAIL (6 blocked by GAP-027)

## New Issues Found

### GAP-027 — Customer pages SSR crash after ONBOARDING lifecycle transition (BLOCKER)
- **Severity**: blocker
- **Reproduction**: Create customer > fill custom fields > Change Status > Start Onboarding > all customer-related pages (list, detail, any customer) crash with `TypeError: Cannot read properties of null` in SSR
- **Impact**: All customer pages are unusable. Cannot interact with any customer, view customer lists, or perform any customer-related actions
- **Error**: `TypeError: Cannot read properties of null (reading ...)` in server-side rendering at multiple call sites
- **Console errors**: `Minified React error #418` (hydration mismatch) + multiple TypeErrors
- **Note**: The customer detail page DID render correctly once immediately after the transition (showed ONBOARDING status, Onboarding tab, checklist), but crashed on ALL subsequent page loads. The backend API continues to work correctly — this is purely a frontend rendering bug.

### GAP-028 — Customer detail page intermittent render crash (non-cascading, pre-existing)
- **Severity**: major
- **Reproduction**: Navigate to any customer detail page — sometimes renders empty table instead of customer detail
- **Impact**: Unreliable page rendering. Sometimes works on retry (especially after fresh login via mock-login)
- **Note**: This may be the same root cause as GAP-008C (Projects page JS error). The intermittent crash was observed BEFORE the ONBOARDING transition, but became persistent AFTER it.

## Blocker Assessment

**GAP-027 is a complete blocker for the QA cycle.** All remaining Day 1 checkpoints (1.4-1.9) and all subsequent days (Day 2, 3, 7, etc.) require customer interaction. The frontend is in a crashed state for all customer-related pages. The backend is healthy — this is a frontend SSR rendering bug that needs to be fixed before QA can continue.
