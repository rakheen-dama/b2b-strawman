# Fix Spec: GAP-L-29-regression — Conflict-check Customer dropdown empty

## Problem

QA Day 2 verify run (2026-04-25 04:30 SAST, branch `bugfix_cycle_2026-04-24`)
checkpoint 2.5/2.6 FAILED: the conflict-check form's **Customer (optional)**
dropdown showed only `-- None --` even though Sipho Dlamini was just created.
Without a `customerId`, the L-28 self-exclusion fix (PR #1118) cannot trigger,
so running the check on `Sipho Dlamini` + `8501015800088` returned
`CONFLICT_FOUND` with two 100% self-matches against his own freshly-created
client record. The L-28 backend fix may be working, but it is **unreachable
through the UI** until the dropdown hydrates. This blocks scenario 2.6's CLEAR
outcome and end-to-end verification of GAP-L-28.

Evidence: `qa_cycle/checkpoint-results/day-02.md` §"Day 2 Re-Run — Cycle 1
Verify — 2026-04-25 04:30 SAST" and §"GAP-L-29-regression — Conflict-check
Customer dropdown empty (REOPENED)".

QA in-browser API probe: `fetch('/api/customers?size=200')` returned a raw JSON
array `[{id, name, email, ...}, ...]` with Sipho present — the row IS there;
the frontend just throws it away.

## Root Cause (verified)

A **response-shape mismatch** between backend and frontend:

- **Backend** —
  `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java:92-93`
  ```java
  @GetMapping
  public ResponseEntity<List<CustomerResponse>> listCustomers(...)
  ```
  Returns a raw `List<CustomerResponse>` JSON array. **Not** a Spring Data
  `Page` / `PaginatedResponse` envelope. There is no `Pageable` parameter; the
  `?size=200` query param is silently ignored. This contract is shared by every
  other caller of `/api/customers` in the codebase (see "Counter-evidence
  audit" below).

- **Frontend** —
  `frontend/app/(app)/org/[slug]/conflict-check/actions.ts:108-112`
  ```ts
  export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
    const result =
      await api.get<PaginatedResponse<{ id: string; name: string }>>("/api/customers?size=200");
    return result?.content ?? [];
  }
  ```
  Annotates the response as `PaginatedResponse<{id,name}>` and reads
  `result.content`. Because the backend returns a raw array, `result.content`
  is `undefined`, and `result?.content ?? []` always evaluates to `[]`. The
  `initialCustomers` prop on `ConflictCheckClient` is therefore always empty,
  so the Customer dropdown only shows `-- None --`.

- **Sibling bug** —
  `frontend/app/(app)/org/[slug]/conflict-check/actions.ts:102-106`
  `fetchProjects()` has the **same shape mismatch**.
  `ProjectController.listProjects` at
  `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java:67-68`
  also returns `ResponseEntity<List<ProjectResponse>>` (raw array, no
  `Pageable`). The Matter dropdown is empty for the same reason. QA only
  flagged the customer side, but the project side will fail the moment a Day 3+
  check uses it.

This is a regression from PR #1122 ("L-29 dropdowns hydrated"): the server
action was added with the wrong assumed response shape. The TypeScript compiler
accepted it because `api.get<T>` is a generic with no runtime check, so the
mismatch was invisible.

## Why option 1 (frontend defensive parse) is the correct fix

Three candidates were on the table:

1. **Frontend one-line** — make `fetchCustomers` (and `fetchProjects`) tolerant
   of both shapes: `Array.isArray(result) ? result : (result?.content ?? [])`.
2. **Backend wrap** — change `CustomerController.listCustomers` to return
   `PaginatedResponse<CustomerResponse>`.
3. **Dedicated endpoint** — add `/api/customers/combobox` returning
   `{id,name}[]`.

### Counter-evidence audit (why option 2 is wrong)

A grep for `"/api/customers` across `frontend/` finds **15 callers** that all
expect a raw `Customer[]` array today:

- `app/(app)/org/[slug]/customers/page.tsx:127` — `Customer[]`
- `app/(app)/org/[slug]/settings/templates/template-support-actions.ts:229` — `Customer[]`
- `app/(app)/org/[slug]/retainers/page.tsx:52` — `Customer[]`
- `app/(app)/org/[slug]/invoices/page.tsx:91` — `Customer[]`
- `app/(app)/org/[slug]/projects/actions.ts:225` — `Customer[]`
- `app/(app)/org/[slug]/projects/page.tsx:135` — `Customer[]`
- `app/(app)/org/[slug]/projects/[id]/actions.ts:104` — `Customer[]`
- `app/(app)/org/[slug]/compliance/requests/actions.ts:44` — `Customer[]`
- `app/(app)/org/[slug]/schedules/page.tsx:47` — `Customer[]`
- `app/(app)/org/[slug]/legal/disbursements/actions.ts:273-278` — defensive (Array OR Paginated)
- `app/(app)/org/[slug]/legal/adverse-parties/actions.ts:140-147` — defensive (Array OR Paginated)
- `app/(app)/org/[slug]/proposals/actions.ts:110` — `Customer[]`
- `app/(app)/org/[slug]/proposals/page.tsx:40` — `Customer[]`
- `app/(app)/org/[slug]/reports/[reportSlug]/actions.ts:94` — `Customer[]`
- `lib/compliance-api.ts:99` — `Array<{id,name,...}>`

Switching the backend to a `PaginatedResponse` envelope would break all 13
non-defensive call-sites at once — a 100× larger blast radius than the actual
bug. The two existing defensive callers
(`legal/disbursements/actions.ts` and `legal/adverse-parties/actions.ts`)
**already use the exact same `Array.isArray(result) ? result : (result.content ?? [])`
pattern** with an inline comment:
> `// /api/customers returns a flat List<CustomerResponse> (not a paginated envelope).`
> `// Handle both shapes defensively in case the backend is ever paginated.`

This is the established convention. The conflict-check action is the outlier
that didn't follow it.

Option 3 (dedicated `/combobox` endpoint) adds new backend surface area for no
benefit — the existing endpoint already returns the data the form needs, and
no other caller wants it.

**Conclusion:** option 1. One-line change, matches established convention,
zero backend churn, zero migration, zero blast radius on the other 14 callers.

## Fix

Edit
`frontend/app/(app)/org/[slug]/conflict-check/actions.ts`
to mirror the defensive pattern already used in `legal/disbursements/actions.ts`
and `legal/adverse-parties/actions.ts`. Apply the fix to **both** `fetchProjects`
and `fetchCustomers` (the project bug is latent — fix it preemptively in the
same change).

### Before (lines 100–112)

```ts
// -- Projects & Customers (for form selectors) --

export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  const result =
    await api.get<PaginatedResponse<{ id: string; name: string }>>("/api/projects?size=200");
  return result?.content ?? [];
}

export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result =
    await api.get<PaginatedResponse<{ id: string; name: string }>>("/api/customers?size=200");
  return result?.content ?? [];
}
```

### After

```ts
// -- Projects & Customers (for form selectors) --
// Both /api/projects and /api/customers return a flat List<...Response> (no
// pagination envelope). We accept both shapes defensively in case those
// endpoints are ever paginated. See backend CustomerController.listCustomers
// and ProjectController.listProjects (both return ResponseEntity<List<...>>).

export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/projects?size=200");
  return Array.isArray(result) ? result : (result?.content ?? []);
}

export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/customers?size=200");
  return Array.isArray(result) ? result : (result?.content ?? []);
}
```

No other files require changes. The page server component
(`conflict-check/page.tsx:45-50`) already calls `fetchCustomers()` and
`fetchProjects()` via `Promise.allSettled`, then forwards the resulting arrays
to `ConflictCheckClient` as `initialCustomers` / `initialProjects` — that flow
becomes correct automatically once the actions return populated arrays.

## Scope

- **Files to modify**:
  - `frontend/app/(app)/org/[slug]/conflict-check/actions.ts` (lines 100–112,
    `fetchProjects` + `fetchCustomers`)
  - `frontend/__tests__/legal/conflict-check.test.tsx` — add a focused test
    suite for `fetchProjects` / `fetchCustomers` that exercises BOTH response
    shapes (raw array AND paginated wrapper) using the real action code (not
    the global mock at lines 46–47, which short-circuits the action entirely).
    Recommend a separate test file or `describe` block that imports the
    actions directly and stubs `api.get` per assertion. Do not delete or alter
    the existing component-level mocks — they are still needed for the
    component-render tests.
- **Files to create**: none required, but at the dev's discretion a new
  `frontend/app/(app)/org/[slug]/conflict-check/actions.test.ts` is acceptable
  if mixing the action-level test into the existing `legal/conflict-check.test.tsx`
  conflicts with the global `vi.mock("@/app/(app)/org/[slug]/conflict-check/actions", ...)`
  at line 29. (The existing mock will swallow direct calls to the real
  `fetchCustomers`, so a new test file with no module-level mock of `actions`
  is the cleaner option.)
- **Backend changes**: none.
- **Migration needed**: no.
- **Realm-export / config changes**: none.
- **Restart required**: no — frontend uses HMR. Backend (PID 25298) does not
  need to restart.

## Verification

### 1. Targeted Vitest (forward-compat regression cover)

Add tests covering both response shapes for `fetchCustomers` and `fetchProjects`:

```ts
// Pseudocode — exact framework idioms per existing patterns
import { fetchCustomers, fetchProjects } from "@/app/(app)/org/[slug]/conflict-check/actions";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({ api: { get: vi.fn() }, ApiError: class extends Error {} }));

it("fetchCustomers handles raw array shape (current backend contract)", async () => {
  vi.mocked(api.get).mockResolvedValueOnce([{ id: "c1", name: "Sipho Dlamini" }]);
  await expect(fetchCustomers()).resolves.toEqual([{ id: "c1", name: "Sipho Dlamini" }]);
});

it("fetchCustomers handles paginated wrapper shape (forward-compat)", async () => {
  vi.mocked(api.get).mockResolvedValueOnce({
    content: [{ id: "c2", name: "Nontando Zulu" }],
    page: { totalElements: 1, totalPages: 1, size: 200, number: 0 },
  });
  await expect(fetchCustomers()).resolves.toEqual([{ id: "c2", name: "Nontando Zulu" }]);
});

it("fetchCustomers returns [] on null/undefined response", async () => {
  vi.mocked(api.get).mockResolvedValueOnce(null);
  await expect(fetchCustomers()).resolves.toEqual([]);
});

// Repeat the same three cases for fetchProjects
```

Run with `pnpm test -- conflict-check` (or whatever path matches the new test
file). Expect 6/6 pass.

### 2. End-to-end re-run of scenario 2.5 / 2.6 on the Keycloak dev stack

Acceptance criteria, checked as Bob (`bob@mathebula-test.local` / `SecureP@ss2`):

- Navigate to `/org/mathebula-partners/conflict-check` → form's **Customer
  (optional)** dropdown lists `Sipho Dlamini` (and any other tenant clients).
- Select Sipho → enter Name `Sipho Dlamini` + ID `8501015800088` + Check Type
  `New Client` → Run Conflict Check.
- Result: **NO_CONFLICT** with green confirmation (L-28 self-exclusion fires
  because `customerId` is now sent to `POST /api/conflict-checks`).
- Control probe: re-run with `Nontando Zulu` + `9001019999088`, no Customer
  selected → still **NO_CONFLICT** (sanity check that we didn't break the
  unselected path).
- Negative probe: enter Name `Sipho Dlamini` + ID `8501015800088` but DO NOT
  select Sipho in the dropdown → result returns **CONFLICT_FOUND** (proves
  L-28's self-exclusion is gated on `customerId`, not on name match — i.e. the
  fix didn't accidentally start excluding by name).
- Capture screenshot at `qa_cycle/checkpoint-results/day-02-2.7-conflict-check-clear.png`
  showing the green CLEAR state for Sipho.

### 3. Smoke check — confirm we didn't regress the other 14 callers

The fix only changes `conflict-check/actions.ts`. The other 14 callers are
untouched. No retesting required, but the dev should grep-confirm before PR:

```bash
grep -rn '"/api/customers' frontend --include="*.ts" --include="*.tsx" | wc -l
# expect: 15 (unchanged from pre-fix count)
```

If the dev has spare cycles, a fast manual smoke of `/customers` and
`/projects` page renders in the running stack confirms the raw-array path
still works for the bulk of callers.

## Estimated Effort

**S** — under 30 minutes. Two-file change (actions.ts + new test file), no
backend, no migration, no service restart. Pattern already established in the
codebase, so the dev has a verbatim reference in
`legal/disbursements/actions.ts:273-278`.
