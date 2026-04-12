# Fix Spec: GAP-S5-04 (REOPENED) — "Link Adverse Party" dialog Customer select is empty

## Priority
HIGH (reopened) — unblocks RAF plaintiff workflow end-to-end in Session 5. This was originally
triaged as a cascade from GAP-S5-03; QA Cycle 5 re-verification proved it is a separate
pre-existing frontend response-shape bug.

## Problem
On the Moroka Estate matter → "Adverse Parties" tab → "Link Adverse Party" dialog, the Customer
select dropdown contains only the placeholder `-- Select customer --` even after GAP-S5-03 was
fixed and `project.customer_id` is now populated. Submit is blocked: no way to link the Road
Accident Fund to the Lerato or Moroka matter via the UI.

QA confirmed (Cycle 5 turn 1, day-05-cycle5.md):
```
count: 1
opts: [{ "value": "", "text": "-- Select customer --" }]
```

## Root Cause
File: `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts`

The helper that feeds the dialog is (line 140-144):

```ts
export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result =
    await api.get<PaginatedResponse<{ id: string; name: string }>>("/api/customers?size=200");
  return result.content;
}
```

This assumes a Spring paginated envelope `{ content: [...], page: {...} }`. But the backend
endpoint `GET /api/customers` returns a **flat array** `ResponseEntity<List<CustomerResponse>>`
(confirmed via `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java`
line 88):

```java
@GetMapping
public ResponseEntity<List<CustomerResponse>> listCustomers(
    @RequestParam(required = false) LifecycleStatus lifecycleStatus) {
  ...
}
```

Result: `result.content` is `undefined` at runtime. The TypeScript cast hides this; the returned
value from `fetchCustomers()` is `undefined`, which the dialog treats as an empty array.

The same file uses the correct pattern for `fetchProjects()` (line 134-138) because
`/api/projects` DOES return a paginated envelope — that's why only the Customer select is broken,
not the Project select. The `proposals/actions.ts::fetchCustomersAction` correctly handles both
shapes with `Array.isArray(result) ? result : result.content ?? []`.

## Fix
Single-function edit. Mirror the defensive handling from `proposals/actions.ts`.

File: `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts`

**Before** (line 140-144):
```ts
export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result =
    await api.get<PaginatedResponse<{ id: string; name: string }>>("/api/customers?size=200");
  return result.content;
}
```

**After**:
```ts
export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/customers?size=200");
  // /api/customers returns a flat List<CustomerResponse> (not a paginated envelope).
  // Handle both shapes defensively in case the backend is ever paginated.
  return Array.isArray(result) ? result : (result.content ?? []);
}
```

No other changes needed. The Customer select in the Link Adverse Party dialog will now populate
from the full customer list (matching the pattern already used in `proposals/actions.ts` and
elsewhere).

## Scope
**Frontend only.**

Files to modify:
- `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts` (single function, 2-line logical change)

Files to create: none
Migration needed: no

## Verification
1. Restart is not needed — HMR picks up the server action change.
2. QA re-opens the "Link Adverse Party" dialog on the Moroka Estate matter. The Customer select
   should now list all customers in the tenant (Moroka Family Trust, Sipho Dlamini, Lerato
   Mthembu, Ndlovu Family Trust).
3. Select Moroka Family Trust + Road Accident Fund + Relationship=Opposing Party → Link Party.
4. Verify: matter detail "Adverse Parties" tab now shows the linked row.
5. DB check: `SELECT * FROM tenant_*.project_adverse_parties WHERE project_id = '095529c5-...'`
   returns the new link.

## Estimated Effort
**S** (< 15 min). Literally one function body to replace + manual re-verification.
