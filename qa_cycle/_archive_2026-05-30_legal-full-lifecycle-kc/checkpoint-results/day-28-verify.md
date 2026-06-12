# Day 28 — Verification of OBS-2801, OBS-2802, OBS-2803 (Cycle 17)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180)
**Executed by**: QA Agent
**Branch**: `bugfix_cycle_2026-05-30`
**Fixes under test**: PR #1399 (OBS-2801), PR #1400 (OBS-2803)

---

## OBS-2801: "Activate Customer" button — NOT VERIFIED (still broken)

### Test Steps

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| V-1 | Navigate to `http://localhost:3000` — auto-logged in as Thandi Mathebula | **PASS** | Dashboard loaded. Sidebar confirmed: "TM", "Thandi Mathebula", thandi@mathebula-test.local. |
| V-2 | Navigate to Clients > Sipho Dlamini detail page | **PASS** | URL: `/org/mathebula-partners/customers/d74963c8-...`. Header: "Sipho Dlamini", badges: Active + **Onboarding**. "Activate Customer" button visible. |
| V-3 | Click "Activate Customer" button | **FAIL** | Button clicked (data-testid `smart-primary-action`). No dialog appeared. No network requests to `/transition`, `/lifecycle`, or `/prerequisite` endpoints. Badges remain: Active + Onboarding. Button is still a no-op. |
| V-4 | Check console errors | **PASS (no new errors)** | Only known OBS-201 errors (AI invocations 404, WONT_FIX-EXEMPT). No JavaScript errors from the button click. |
| V-5 | Check "More actions" overflow menu | **PASS (no lifecycle option)** | Menu contains: Edit Client, Generate Document, Run Conflict Check, Export Data, Anonymize, Archive. No "Change Status" or lifecycle transition option. |

### Root Cause Analysis

The OBS-2801 fix (PR #1399) created `ClientHeaderCardWithLifecycle` which correctly wires `TransitionConfirmDialog` when `targetLifecycleStatus` is non-null. However, the fix is **incomplete** because:

1. **`lifecycleActionPrompt` in page.tsx (lines 453-467)** gates ONBOARDING -> ACTIVE on:
   ```
   customerReadiness?.checklistProgress?.completed === customerReadiness?.checklistProgress?.total
   && (customerReadiness?.checklistProgress?.total ?? 0) > 0
   ```

2. **Sipho has no onboarding checklist assigned** — the `checklistProgress` is either `null` or has `total = 0`. The `total > 0` condition fails, so `lifecycleActionPrompt` evaluates to `null`.

3. **`targetLifecycleStatus` is `null`** (from `lifecycleActionPrompt?.targetStatus ?? null`), so `ClientHeaderCardWithLifecycle` passes `onPrimaryAction={undefined}` to `ClientHeaderCard`.

4. **The button still renders** because `getSmartPrimaryAction()` in `client-header-card.tsx` (line 38) returns `{ label: "Activate Customer", variant: "accent" }` for ANY ONBOARDING lifecycle status — it has no checklist gate. This creates a **visible but non-functional** button.

5. **The `LifecycleTransitionDropdown` component** exists (renders a "Change Status" dropdown) but is **not imported or used** on the customer detail page.

### Gap Filed

**OBS-2801b**: The OBS-2801 fix is incomplete. The `lifecycleActionPrompt` in `page.tsx` requires `checklistProgress.total > 0` to activate the transition, but Sipho has no onboarding checklist (total = 0). Either:
  - (a) The checklist gate should be relaxed to allow activation when no checklist is assigned (total = 0 or null), OR
  - (b) The `LifecycleTransitionDropdown` ("Change Status" button) should be added to the customer detail page as an alternative activation path, OR
  - (c) The `getSmartPrimaryAction` function should not show "Activate Customer" when the transition is not actually wired.

**Severity**: HIGH (BLOCKER — same cascade as OBS-2801)

**Screenshot**: `day-28-verify-obs2801-still-broken.png`

---

## OBS-2802: Billing wizard 0 customers — NOT VERIFIED (cascading)

OBS-2802 was filed as CASCADING from OBS-2801. Since OBS-2801 is still not fixed (Sipho remains ONBOARDING), OBS-2802 remains unverified. The billing wizard `discoverCustomers()` correctly filters on `lifecycle_status = 'ACTIVE'`. No code change is needed for OBS-2802 — it auto-resolves when Sipho is activated.

**Status**: CASCADING (still blocked by OBS-2801)

---

## OBS-2803: Billing Runs heading says "Invoices" — VERIFIED

### Test Steps

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| V-1 | Navigate to Finance > Billing Runs | **PASS** | URL: `/org/mathebula-partners/invoices/billing-runs`. Page loaded with 2 existing billing runs (Preview, from previous Day 28 attempt). |
| V-2 | Verify page heading says "Fee Notes" (not "Invoices") | **PASS** | Page heading (h1-equivalent): **"Fee Notes"** (was "Invoices" before fix). Terminology correctly applied via `TerminologyHeading`. |
| V-3 | Verify tab label says "Fee Notes" | **PASS** | Tab: "Fee Notes" link at `/org/mathebula-partners/invoices`. Terminology correctly applied. |
| V-4 | Verify sidebar nav says "Fee Notes" | **PASS** | Sidebar Finance group: "Fee Notes" (confirmed in previous cycles). |

### Minor Observation

The billing runs table column header still says "Invoices" (not "Fee Notes"). This is a separate terminology gap but low severity — the fix targeted the heading, tab label, and empty state description. Table column headers may need a separate pass.

**Screenshot**: `day-28-verify-obs2803-fee-notes-heading.png`

**Status**: **VERIFIED**

---

## Day 28 Retry — BLOCKED

Day 28 checkpoints 28.1–28.8 remain BLOCKED. Sipho cannot be activated (OBS-2801 still broken), so the billing wizard cannot discover him (OBS-2802 cascading). The fee note generation flow cannot be exercised.

| Checkpoint | Result |
|-----------|--------|
| 28.1 Navigate to Billing Runs > New Billing Run | PASS (page accessible, heading correct per OBS-2803 fix) |
| 28.2 Select Sipho in wizard | BLOCKED (OBS-2801/2802) |
| 28.3 Cherry-pick time entries + disbursement | BLOCKED |
| 28.4 Generate Fee Notes | BLOCKED |
| 28.5 Verify fee note renders | BLOCKED |
| 28.6 Approve & Send | BLOCKED |
| 28.7 Mailpit email | BLOCKED |
| 28.8 Screenshot | BLOCKED |

---

## Console Errors

Only known OBS-201 (WONT_FIX-EXEMPT): `/api/assistant/invocations` 404 — AI infra proxy not wired for KC mode. Zero new JavaScript errors during verification.

---

## Summary

| Gap ID | Fix PR | Verdict | Notes |
|--------|--------|---------|-------|
| OBS-2801 | #1399 | **NOT VERIFIED** | Button still no-op. Fix incomplete: `lifecycleActionPrompt` requires `checklistProgress.total > 0` which fails for Sipho (no checklist). New gap: OBS-2801b. |
| OBS-2802 | N/A | **NOT VERIFIED** | Still cascading from OBS-2801. |
| OBS-2803 | #1400 | **VERIFIED** | Heading and tab correctly say "Fee Notes". |
