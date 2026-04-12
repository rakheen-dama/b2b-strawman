# Fix Spec: GAP-S5-06 — Proposal "New Proposal" dialog Customer combobox is empty

## Priority
MEDIUM — blocks end-to-end engagement-letter creation for **all** fee models including the newly
added Contingency, meaning GAP-S5-01 cannot be fully validated by QA end-to-end.

## Problem
On `/org/{slug}/proposals`, clicking "New Proposal" opens the `CreateProposalDialog`. The Customer
combobox contains only the placeholder text and no options, even though the tenant has 4
customers (Moroka Family Trust, Sipho Dlamini, Lerato Mthembu, Ndlovu Family Trust) — with
several in `ONBOARDING` lifecycle state.

QA Cycle 5 evidence (day-05-cycle5.md): "Could NOT submit end-to-end because the dialog's
Customer dropdown was empty."

## Root Cause
File: `frontend/app/(app)/org/[slug]/proposals/page.tsx` (line 37–59)

The page server-side fetches customers via `api.get<Customer[]>("/api/customers?size=200")` (line
40, inside `Promise.allSettled`), then filters with (line 57):

```ts
.filter((c) => c.lifecycleStatus !== "OFFBOARDED" && c.lifecycleStatus !== "PROSPECT")
```

The filter excludes `PROSPECT` customers. Today, **all** legal-za test customers in the tenant
are stuck in `PROSPECT` or `ONBOARDING` because `GAP-S3-03` blocks the FICA document-upload path
that is required to reach `ACTIVE`. Specifically:
- Moroka Family Trust → transitioned to ONBOARDING (should pass filter)
- Sipho Dlamini → PROSPECT (filter excludes)
- Lerato Mthembu → PROSPECT or ONBOARDING
- Ndlovu Family Trust → ONBOARDING (should pass filter)

However, QA observed the dropdown is **fully empty**, not partially populated. That means even
the ONBOARDING customers are being excluded. The likely reason: `Customer.lifecycleStatus` is an
**optional** field in the TypeScript type (`lib/types/customer.ts` line 33:
`lifecycleStatus?: LifecycleStatus`), and the JSON response from `/api/customers` (list endpoint)
may omit it OR return it under a different serialization nuance. When `lifecycleStatus` is
`undefined`, `undefined !== "PROSPECT"` is `true` — so they SHOULD pass. Unless: the
`customersResult` `Promise.allSettled` entry is rejected (silent), producing an empty array via
the `: []` branch on line 59.

Either way, both failure modes produce the same end-user symptom: empty dropdown. There are TWO
independent issues, and we should fix both.

### Issue 1 (primary): Wrong filter policy
Engagement letters in a legal-za practice are **sent during onboarding**, not after. An
engagement letter signature is itself a prerequisite for activation (see FICA checklist item
"Engagement Letter Signed"). Filtering out PROSPECT and ONBOARDING customers is backwards — you
cannot create a proposal/engagement letter for a client that is already ACTIVE in most real
workflows.

### Issue 2 (secondary): Silent swallow
If the `/api/customers` fetch fails (auth, network, 500), `allSettled` returns a rejected promise
and the ternary at line 52–59 silently substitutes `[]`. There is no console log, no toast, no
way for QA to tell the difference between "no matching customers" and "fetch failed".

## Fix

### Step 1 — Fix the filter policy
File: `frontend/app/(app)/org/[slug]/proposals/page.tsx`

**Before** (line 51–59):
```ts
const customers: Array<{ id: string; name: string; email: string }> =
  customersResult.status === "fulfilled"
    ? (Array.isArray(customersResult.value)
        ? customersResult.value
        : ((customersResult.value as unknown as { content: Customer[] }).content ?? [])
      )
        .filter((c) => c.lifecycleStatus !== "OFFBOARDED" && c.lifecycleStatus !== "PROSPECT")
        .map((c) => ({ id: c.id, name: c.name, email: c.email }))
    : [];
```

**After**:
```ts
if (customersResult.status === "rejected") {
  console.error("Failed to fetch customers for proposal dialog:", customersResult.reason);
}
const customers: Array<{ id: string; name: string; email: string }> =
  customersResult.status === "fulfilled"
    ? (Array.isArray(customersResult.value)
        ? customersResult.value
        : ((customersResult.value as unknown as { content: Customer[] }).content ?? [])
      )
        // Engagement letters can be issued at any lifecycle stage except OFFBOARDED /
        // ANONYMIZED. In a legal-za practice, proposals are typically drafted during
        // PROSPECT / ONBOARDING (the signed letter is itself a FICA checklist item).
        .filter(
          (c) =>
            c.lifecycleStatus !== "OFFBOARDED" &&
            c.lifecycleStatus !== "OFFBOARDING" &&
            c.lifecycleStatus !== "ANONYMIZED"
        )
        .map((c) => ({ id: c.id, name: c.name, email: c.email }))
    : [];
```

### Step 2 — Mirror the same change in `proposals/actions.ts::fetchCustomersAction`
File: `frontend/app/(app)/org/[slug]/proposals/actions.ts` (line 106–121)

`fetchCustomersAction` is used by client-side revalidation flows inside the dialog and currently
has the same overly-restrictive filter. Update to match:

**Before** (line 115–117):
```ts
return customers
  .filter((c) => c.lifecycleStatus !== "OFFBOARDED" && c.lifecycleStatus !== "PROSPECT")
  .map((c) => ({ id: c.id, name: c.name, email: c.email }));
```

**After**:
```ts
return customers
  .filter(
    (c) =>
      c.lifecycleStatus !== "OFFBOARDED" &&
      c.lifecycleStatus !== "OFFBOARDING" &&
      c.lifecycleStatus !== "ANONYMIZED"
  )
  .map((c) => ({ id: c.id, name: c.name, email: c.email }));
```

Also replace the silent `catch { return []; }` (line 118–120) with:
```ts
} catch (error) {
  console.error("fetchCustomersAction failed:", error);
  return [];
}
```

### Step 3 — Same fix for the Fee Notes / Invoice dialog
**Cross-check (do this as part of the same PR if grep turns up hits):** grep for
`lifecycleStatus !== "PROSPECT"` across `frontend/` — any other dialogs (invoices, retainers)
using the same pattern should get the same fix for consistency.

```bash
grep -rn '!== "PROSPECT"' frontend/app frontend/components
```

## Scope
**Frontend only.**

Files to modify:
- `frontend/app/(app)/org/[slug]/proposals/page.tsx` (filter policy + error log)
- `frontend/app/(app)/org/[slug]/proposals/actions.ts` (same filter policy + non-silent catch)
- Any other files found via the grep in Step 3 (expect 0–2 additional hits).

Files to create: none
Migration needed: no

## Verification
1. HMR picks up the change (no restart).
2. QA re-opens `/proposals` → "New Proposal" dialog. The Customer combobox lists all 4
   customers (Moroka Family Trust, Sipho Dlamini, Lerato Mthembu, Ndlovu Family Trust).
3. Select Moroka Family Trust → Fee Model = Contingency → fill contingency fields → submit.
4. Verify row in `tenant_*.proposals` with `fee_model='CONTINGENCY'`, `contingency_percent=25`,
   `contingency_cap_percent=25`, `customer_id=ac433c2c-...`.
5. Engagement Letters list page now shows the new proposal.
6. End-to-end GAP-S5-01 verification can now complete.

## Estimated Effort
**S** (< 30 min). Two small files, logical change is identical in both places.
