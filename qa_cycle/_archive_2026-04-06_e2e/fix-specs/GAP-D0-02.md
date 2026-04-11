# Fix Spec: GAP-D0-02 — Trust Accounting cannot create trust accounts

## Problem
The QA report says Trust Accounting shows a "Coming Soon" stub. Investigation shows the actual page is NOT a stub — it's a fully implemented dashboard (`frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`) with summary cards, alerts, and transaction tables (built in Phase 61). However, when no trust accounts exist, it shows "No Trust Accounts — No trust accounts have been set up yet. Create your first trust account to get started." There is NO "Create Trust Account" button or dialog on this page.

The settings page (`frontend/app/(app)/org/[slug]/settings/trust-accounting/page.tsx`) has an "Add Account" button at line 122-128, but it links to `/trust-accounting` (the dashboard), which also has no creation UI.

The schema exists (`frontend/lib/schemas/trust.ts` — `createTrustAccountSchema`), but no `CreateTrustAccountDialog` component exists anywhere in the frontend.

## Root Cause (hypothesis)
Phase 61 built the trust accounting backend (CRUD for trust accounts, transactions, ledgers, reconciliation, interest, investments) and the frontend pages (dashboard, transactions, ledgers, reconciliation, interest, investments, reports, settings). However, the **Create Trust Account dialog** was never built. The backend endpoint `POST /api/trust-accounts` exists and works, but there's no frontend UI to call it.

## Fix
This is a **WONT_FIX for this QA cycle**. Creating a full trust account creation dialog (with bank name, account number, branch code, account type, primary flag, approval settings) is a feature development task that exceeds the 2-hour scope limit.

**Workaround for QA**: Create a trust account via direct API call:
```bash
curl -X POST http://localhost:8081/api/trust-accounts \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "accountName": "Main Trust Account",
    "bankName": "FNB",
    "branchCode": "250655",
    "accountNumber": "62000000001",
    "accountType": "TRUST_ACCOUNT",
    "isPrimary": true,
    "requireDualApproval": false
  }'
```

## Scope
N/A (WONT_FIX)

## Verification
N/A

## Estimated Effort
L (> 2 hr) — full dialog component with form validation, bank details, approval settings, primary account toggle
