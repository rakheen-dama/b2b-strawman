# Fix Spec: BUG-CYCLE26-08 — Trust transactions filter shows "Interest Lpff" instead of "Interest LPFF"

## Problem

Evidence: `qa_cycle/checkpoint-results/cycle21-day10-10.4-transactions-page.yml:140-141`

The Type filter row on `/trust-accounting/transactions` (and the same filter row on `/trust-accounting/client-ledgers/{customerId}`, plus the type cell in transaction history tables) renders the `INTEREST_LPFF` enum as **"Interest Lpff"**. All other enum values (Refund, Reversal, Interest Credit, Fee Transfer, Transfer In, Transfer Out, Deposit, Payment) render correctly because their lowercased forms are real English words. **LPFF** is an acronym for *Legal Practitioners' Fidelity Fund* — the trust-account interest stream paid to LPFF under s.86 of the South African Legal Practice Act — and must remain uppercase.

## Root Cause (verified)

There are **three duplicates** of the same `transactionTypeLabel()` helper, all using a generic title-case algorithm that uppercases the first letter of each whitespace-separated word and lowercases the rest — destroying the LPFF acronym:

- `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx:62-67`
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx:40-45`
- `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page.tsx:39-44`

Current implementation (identical at all three sites):

```ts
function transactionTypeLabel(type: TrustTransactionType): string {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
```

For `"INTEREST_LPFF"` this yields `"Interest Lpff"`. For every other enum (`DEPOSIT`, `PAYMENT`, `TRANSFER_IN`, `TRANSFER_OUT`, `FEE_TRANSFER`, `REFUND`, `INTEREST_CREDIT`, `REVERSAL`) the result happens to look correct because no acronym is involved.

## Fix

Replace the three duplicate helpers with a **single** lookup-table-backed helper exported from `frontend/lib/types/trust.ts` (the same module that defines `TrustTransactionType`). This eliminates duplication and gives us a single source of truth for type labels.

### Step 1 — Add the helper to `frontend/lib/types/trust.ts`

After the `TrustTransactionType` union declaration (currently around lines 38-48), add:

```ts
const TRUST_TRANSACTION_TYPE_LABELS: Record<TrustTransactionType, string> = {
  DEPOSIT: "Deposit",
  PAYMENT: "Payment",
  TRANSFER_IN: "Transfer In",
  TRANSFER_OUT: "Transfer Out",
  FEE_TRANSFER: "Fee Transfer",
  REFUND: "Refund",
  INTEREST_CREDIT: "Interest Credit",
  INTEREST_LPFF: "Interest LPFF",
  REVERSAL: "Reversal",
};

export function transactionTypeLabel(type: TrustTransactionType): string {
  return TRUST_TRANSACTION_TYPE_LABELS[type] ?? type;
}
```

The `?? type` fallback preserves graceful degradation if a future enum value lands in the API response before the frontend is updated.

### Step 2 — Delete the three duplicate inline helpers

In each of these files, delete the local `function transactionTypeLabel(...)` declaration and add `transactionTypeLabel` to the existing `import` from `@/lib/types/trust` (or `@/lib/types`):

- `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` — delete lines 62-67
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx` — delete lines 40-45
- `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page.tsx` — delete lines 39-44

Each file already imports from `@/lib/types` (transactions/page.tsx) or `@/lib/types/trust` (the others) — extend the existing import statement to also pull in `transactionTypeLabel`.

### Step 3 — Add unit test (optional but recommended)

Create `frontend/lib/types/__tests__/trust.test.ts`:

```ts
import { describe, it, expect } from "vitest";
import { transactionTypeLabel } from "@/lib/types/trust";

describe("transactionTypeLabel", () => {
  it("preserves the LPFF acronym", () => {
    expect(transactionTypeLabel("INTEREST_LPFF")).toBe("Interest LPFF");
  });

  it("title-cases multi-word enums", () => {
    expect(transactionTypeLabel("FEE_TRANSFER")).toBe("Fee Transfer");
    expect(transactionTypeLabel("INTEREST_CREDIT")).toBe("Interest Credit");
    expect(transactionTypeLabel("TRANSFER_IN")).toBe("Transfer In");
  });

  it("handles single-word enums", () => {
    expect(transactionTypeLabel("DEPOSIT")).toBe("Deposit");
    expect(transactionTypeLabel("REVERSAL")).toBe("Reversal");
  });
});
```

## Scope

**Frontend only.**

Files to modify:
- `frontend/lib/types/trust.ts` — add label map + exported helper
- `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` — remove duplicate helper, import from shared module
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx` — remove duplicate helper, import from shared module
- `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page.tsx` — remove duplicate helper, import from shared module

Files to create:
- `frontend/lib/types/__tests__/trust.test.ts` (optional unit test)

Migration needed: **no** (frontend-only label change).

## Verification

Re-capture `qa_cycle/checkpoint-results/cycle21-day10-10.4-transactions-page.yml`. Line 140 should now read:

```yaml
- link "Interest LPFF" [ref=...] [cursor=pointer]:
  - /url: /org/mathebula-partners/trust-accounting/transactions?type=INTEREST_LPFF
```

Also re-capture `cycle21-day10-10.7-sipho-ledger-detail.yml` to confirm the same fix lands in the per-customer ledger filter row. No other strings should change — every other enum label remains identical.

Browser verification: navigate to `http://localhost:3000/org/mathebula-partners/trust-accounting/transactions` (Keycloak stack), inspect the Type filter pill row, confirm "Interest LPFF" renders. Repeat at `/trust-accounting/client-ledgers/{any-customer-id}`.

## Estimated Effort

S (< 30 min)
