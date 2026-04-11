# Fix Spec: GAP-S4-01 — Trust account creation UI is broken (navigation loop)

## Priority
HIGH — blocks Session 4 Phase F entirely. Entire Trust Accounting vertical feature inaccessible.

## Problem
`/settings/trust-accounting` exposes an "Add Account" element that is actually a `<Link>` routing
to `/trust-accounting` (the dashboard). The dashboard has no create CTA. End result: navigation
loop with no path to create a trust account through the product. Backend endpoint
`POST /api/trust-accounts` exists and works.

## Root Cause (confirmed via grep)
Files:
- `frontend/app/(app)/org/[slug]/settings/trust-accounting/page.tsx:122-128` — The "Add Account"
  element:
  ```tsx
  <Link
    href={`/org/${slug}/trust-accounting`}
    className="inline-flex items-center gap-1.5 rounded-md bg-slate-900 ..."
  >
    <Plus className="size-3.5" />
    Add Account
  </Link>
  ```
  This is a navigation Link to the dashboard, not a dialog trigger.
- `frontend/app/(app)/org/[slug]/trust-accounting/actions.ts` — only `fetchTrustAccounts()`
  exists; no `createTrustAccount()` server action wrapping the existing
  `POST /api/trust-accounts` endpoint.
- `backend/.../trustaccounting/TrustAccountingController.java:47` — backend endpoint already
  accepts `CreateTrustAccountRequest` with fields: accountName, bankName, branchCode,
  accountNumber, accountType (GENERAL default), isPrimary, requireDualApproval,
  paymentApprovalThreshold, openedDate, notes.

## Fix Steps
1. Add a server action to `frontend/app/(app)/org/[slug]/trust-accounting/actions.ts`:
   ```ts
   export interface CreateTrustAccountInput {
     accountName: string;
     bankName: string;
     branchCode: string;
     accountNumber: string;
     accountType?: "GENERAL" | "INVESTMENT" | "SECTION_78_2A";
     isPrimary?: boolean;
     requireDualApproval?: boolean;
     paymentApprovalThreshold?: number | null;
     openedDate?: string; // ISO date
     notes?: string;
   }
   export async function createTrustAccount(input: CreateTrustAccountInput): Promise<TrustAccount> {
     return api.post<TrustAccount>("/api/trust-accounts", input);
   }
   ```
2. Create a new client component
   `frontend/components/trust/CreateTrustAccountDialog.tsx` — a Shadcn `Dialog` with
   react-hook-form + zod schema matching `CreateTrustAccountInput`. Follow the pattern in
   existing trust dialogs like `RecordDepositDialog.tsx`. Fields:
   - Account Name (required, text)
   - Bank Name (required, text)
   - Branch Code (required, text)
   - Account Number (required, text)
   - Account Type (select: GENERAL / INVESTMENT, default GENERAL)
   - Is Primary (checkbox, default true — first account should be primary)
   - Require Dual Approval (checkbox, default false)
   - Payment Approval Threshold (optional, number input, ZAR)
   - Opened Date (date input, default today)
   - Notes (optional, textarea)
   On submit, call `createTrustAccount()`, close the dialog, and call `router.refresh()` to
   re-fetch the parent page.
3. Create the zod schema at `frontend/lib/schemas/trust-account.ts` (follow customer.ts
   pattern).
4. In `frontend/app/(app)/org/[slug]/settings/trust-accounting/page.tsx`, replace the "Add
   Account" `<Link>` (lines 122-128) with the new dialog trigger. Because the page is a Server
   Component, pass control to a small "use client" wrapper that mounts the button + dialog:
   ```tsx
   // New file: frontend/components/trust/AddTrustAccountButton.tsx ("use client")
   // Imports CreateTrustAccountDialog and a Button, handles `open` state.
   ```
   Then in `page.tsx`:
   ```tsx
   <AddTrustAccountButton />
   ```
   instead of the `<Link>`.
5. Also add the same dialog trigger to the empty-state of
   `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` — "No trust accounts" state
   should show the Add Account button so users who land on the dashboard first can create one.

## Scope
- Frontend only
- Files to modify:
  - `frontend/app/(app)/org/[slug]/trust-accounting/actions.ts` (add server action)
  - `frontend/app/(app)/org/[slug]/settings/trust-accounting/page.tsx` (replace Link with
    dialog trigger)
  - `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` (add button to empty state)
- Files to create:
  - `frontend/components/trust/CreateTrustAccountDialog.tsx`
  - `frontend/components/trust/AddTrustAccountButton.tsx`
  - `frontend/lib/schemas/trust-account.ts`
- Migration needed: no

## Verification
1. Re-run Session 4 step 4.5–4.7:
   - Navigate to `/settings/trust-accounting`. Click "Add Account".
   - Dialog opens. Fill: Account Name = "Mathebula Trust Account", Bank = "Standard Bank",
     Branch Code = "051001", Account Number = "1234567890", Account Type = GENERAL, Is Primary
     on. Click Save.
   - Dialog closes, page refreshes, row appears in the Trust Accounts section.
2. Also verify: `/trust-accounting` dashboard now shows the account's cashbook balance card.
3. DB check: `SELECT * FROM trust_accounts;` shows 1 row.

## Estimated Effort
M (~1.5 hr — new dialog component, schema, two page changes, button wrapper)
