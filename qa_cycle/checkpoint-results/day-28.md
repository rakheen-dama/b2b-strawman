# Day 28 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Thandi Mathebula (Owner) + Bob Ndlovu (Admin, for pre-condition)

---

## Pre-condition: Approve sheriff disbursement (OBS-2104 fix requirement)

Per the scenario pre-condition, the R 1,250 sheriff disbursement must be Approved before the billing wizard can discover it.

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| Pre-1 | Navigate to matter RAF-2026-001 > Finance > Disbursements (as Bob) | **PASS** | Disbursement row visible: 2026-05-30, Sheriff Fees, R 1 250,00, Approval=Draft, Billing=Unbilled. |
| Pre-2 | Click disbursement row -> navigate to detail page | **PASS** | URL: `/org/mathebula-partners/legal/disbursements/28f09dc4-a419-4dc8-86d7-1def7dc1da07`. Heading: "Sheriff Fees", badges: Draft, Unbilled. Financial summary: Amount (excl VAT) R 1 250,00, VAT R 0,00 (Zero-rated pass-through), Total R 1 250,00. |
| Pre-3 | Click "Submit for Approval" | **PASS** | Status transitioned Draft -> **Pending**. "Approval Required" section appeared with Approve/Reject buttons and message: "This disbursement is pending approval." |
| Pre-4 | Click "Approve" -> Approve Disbursement dialog -> confirm | **PASS** | Dialog: "Approve Disbursement — Optionally add notes explaining the approval decision." Clicked "Approve Disbursement". Status transitioned Pending -> **Approved**. Billing remains **Unbilled**. |

**Pre-condition satisfied**: Sheriff disbursement is Approved + Unbilled, ready for billing wizard discovery.

---

## Login as Thandi Mathebula (Owner)

Signed out Bob's session. Navigated to Keycloak login at `:8180`. Entered `thandi@mathebula-test.local` / `SecureP@ss1`. Logged in successfully, landed on `/org/mathebula-partners/dashboard`. Sidebar confirmed: "TM", "Thandi Mathebula", thandi@mathebula-test.local.

---

## Pre-condition: Activate Sipho Dlamini (lifecycle_status ONBOARDING -> ACTIVE)

Before attempting the billing wizard, discovered that Sipho's `lifecycle_status` is `ONBOARDING` (not `ACTIVE`). The billing wizard `discoverCustomers()` SQL requires `lifecycle_status = 'ACTIVE'` to include a customer. Attempted activation via UI.

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| Act-1 | Navigate to Sipho's client detail page | **PASS** | URL: `/org/mathebula-partners/customers/d74963c8-...`. Header: "Sipho Dlamini", badges: Active + **Onboarding**. "Activate Customer" button visible. |
| Act-2 | Click "Activate Customer" button | **FAIL** | Button click sends a Next.js server action POST to the page route (HTTP 200 OK), but **no API call reaches the gateway or backend**. `lifecycle_status` remains `ONBOARDING` in the database. The button is a no-op. No JS errors in console. No error toast or feedback shown to user. Button remains in same state after click. |
| Act-3 | Retry "Activate Customer" button (2 additional attempts) | **FAIL** | Same behavior on each attempt: POST 200 OK, no gateway/backend call, no state change, no user feedback. DB confirmed: `lifecycle_status = 'ONBOARDING'` after all attempts. |

**Database verification** (read-only, for evidence):
```
SELECT lifecycle_status FROM customers WHERE id = 'd74963c8-...';
-- Result: ONBOARDING (unchanged after 3 button clicks)
```

**Screenshot**: `day-28-blocker-activate-button-noop.png` — client detail page showing "Activate Customer" button with "Onboarding" badge still displayed.

---

## Checkpoint 28.1: Navigate to Bulk Billing -> + New Billing Run

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.1 | Navigate to Billing Runs -> + New Billing Run | **PASS** | Finance nav group > "Billing Runs" link. Page: `/org/mathebula-partners/invoices/billing-runs`. Heading: "Invoices" (NOTE: heading says "Invoices" not "Fee Notes" — terminology gap, see OBS-2803). "No billing runs" empty state with "New Billing Run" CTA. Clicked "New Billing Run" -> wizard opened at Step 1: "Configure Billing Run". 5-step wizard: Configure, Select Customers, Review & Cherry-Pick, Review Drafts, Send. |

---

## Checkpoint 28.2: Configure billing run and select Sipho

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 28.2a | Step 1: Configure — set Period From/To | **PASS** | Set Period From = 2026-05-01, Period To = 2026-05-30 via native input value setter (date inputs). Clicked Next. |
| 28.2b | Step 2: Select Customers — Sipho should appear | **FAIL (BLOCKER)** | Step 2 heading: "Select Customers". Message: **"No customers with unbilled work found for this period."** Footer: "0 customers selected, R 0,00 total". Next button disabled. Backend log confirms: `Loaded preview for billing run ... — 0 customers, total unbilled: 0`. |

**Root cause**: The billing wizard `discoverCustomers()` query filters on `lifecycle_status = 'ACTIVE'`. Sipho's `lifecycle_status` is `ONBOARDING` because the "Activate Customer" button (OBS-2801) does not work. The time entries (4h billable) and approved disbursement (R 1,250) exist and are unbilled, but the customer is excluded from discovery due to lifecycle status.

---

## Remaining checkpoints 28.3–28.8: BLOCKED

All remaining Day 28 checkpoints are blocked by OBS-2801 + OBS-2802. Cannot proceed past Step 2 of the billing wizard.

| ID | Checkpoint | Result |
|----|-----------|--------|
| 28.3 | Cherry-pick: keep all time entries + disbursement | **BLOCKED** |
| 28.4 | Click Generate Fee Notes -> preview opens | **BLOCKED** |
| 28.5 | Verify fee note renders with letterhead, TIME lines, EXPENSE line, VAT 15%, ZAR total | **BLOCKED** |
| 28.6 | Click Approve & Send -> status transitions to SENT | **BLOCKED** |
| 28.7 | Mailpit -> fee note email to sipho.portal@example.com | **BLOCKED** |
| 28.8 | Screenshot: day-28-firm-fee-note-sent.png | **BLOCKED** |

---

## Day 28 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Fee note generated with TIME time-entry lines + EXPENSE disbursement line correctly separated | **BLOCKED** | Cannot reach fee note generation step — billing wizard shows 0 customers. |
| Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end | **PARTIAL** | Sidebar nav says "Fee Notes" (PASS), but Billing Runs page heading says "Invoices" (FAIL — OBS-2803). Cannot verify fee note document terminology as document was never generated. |
| Email dispatched with portal payment link | **BLOCKED** | Cannot reach send step. |

---

## Console Errors

Only known OBS-201 (WONT_FIX-EXEMPT): `/api/assistant/invocations` 404 — AI infra proxy not wired for KC mode. Zero new JavaScript errors during Day 28 execution.

---

## Gaps Filed

| Gap ID | Summary | Severity | Status | Day | Notes |
|--------|---------|----------|--------|-----|-------|
| OBS-2801 | "Activate Customer" button on client detail page is a no-op | **HIGH (BLOCKER cascade)** | NEW | 28 | Click sends Next.js server action POST (200 OK) to the page route but does NOT call the gateway/backend `PATCH /api/customers/{id}/lifecycle` API. `lifecycle_status` remains `ONBOARDING` in the DB. No user-facing error, no toast, no feedback. Button remains clickable in the same state. Blocks OBS-2802 (billing discovery). The scenario amendment (OBS-2102 fix, PR #1237, cycle 16) mentions INDIVIDUAL clients can now activate, but the frontend button handler appears to be broken or disconnected from the backend API. |
| OBS-2802 | Billing wizard discoverCustomers() returns 0 customers — lifecycle_status gate | **HIGH (BLOCKER)** | NEW | 28 | The `BillingRunSelectionService.discoverCustomers()` SQL filters on `lifecycle_status = 'ACTIVE'`. Sipho is `ONBOARDING` because OBS-2801 prevents activation. Backend log confirms: "0 customers, total unbilled: 0". This blocks the entire Day 28 fee note generation flow. Cascades to Day 30 (payment) and Day 60 (closure "final bill issued" gate). |
| OBS-2803 | Billing Runs page heading says "Invoices" instead of "Fee Notes" | **LOW** | NEW | 28 | Route `/org/{slug}/invoices/billing-runs` renders heading `<h1>Invoices</h1>`. Legal-za terminology should show "Fee Notes" (sidebar nav correctly says "Fee Notes", but the page heading uses the generic term). Non-blocking — cosmetic terminology gap. |

---

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf` (unchanged)
- **Matter ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d` (unchanged)
- **Matter Reference**: RAF-2026-001 (unchanged)
- **Disbursement ID**: `28f09dc4-a419-4dc8-86d7-1def7dc1da07`
- **Disbursement Status**: Approved + Unbilled
- **Sipho lifecycle_status**: `ONBOARDING` (should be `ACTIVE` — blocked by OBS-2801)

## Screenshots

- `day-28-blocker-activate-button-noop.png` — client detail page with "Activate Customer" button and "Onboarding" badge after failed activation attempts
