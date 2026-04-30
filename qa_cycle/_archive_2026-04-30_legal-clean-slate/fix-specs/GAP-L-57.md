# Fix Spec: GAP-L-57 — Disbursement dialog's Matter combobox empty / disabled (response-shape bug)

## Problem

Day 21 Checkpoints 21.6–21.9 (per `qa_cycle/checkpoint-results/day-21.md`
lines 37–46) halt because the **New Disbursement** dialog renders with the
Matter combobox **permanently disabled / empty**:

- 21.6 — button exists (PASS).
- 21.7 — dialog opens; Customer combobox lists Sipho / Test Client / Moroka (3
  options). Selecting any customer does NOT populate the Matter combobox —
  it stays disabled with only the placeholder `-- Select matter --`.
- 21.8 — dialog has no "recoverable" / "client-rebillable" checkbox. Scenario
  21.8 asks to "Mark as recoverable".
- 21.9 — BLOCKED. Submit requires a Matter selection which is unreachable.

QA originally hypothesised this was a missing Next.js API route at
`/api/projects?customerId=<id>`. Investigation (code read across the dialog
component, list-client, and backend) **rejects** that hypothesis:

- The dialog **does not filter Matter by Customer client-side** (there is no
  `onCustomerChange` fetch in
  `frontend/components/legal/create-disbursement-dialog.tsx`).
- The dialog calls server actions `fetchProjects()` + `fetchCustomers()` from
  `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts`. These
  server actions resolve via `api.get()` → backend through BFF → backend
  `GET /api/projects?size=200` and `GET /api/customers?size=200`. Both
  endpoints exist and return 200.
- The `GET /api/projects?customerId=…` 404 QA logged in the browser console
  is not produced by this dialog. No client-side code in the frontend
  repository issues that URL (confirmed via repo-wide grep:
  `customerId=\{`, `projects\?customerId`, `fetch.*projects`). It is likely a
  stray log line from a different component or tooling, not causative.
- The similar `POST /api/disbursements` 404 QA probed is a probe against a
  wrong path — the actual submit target is `POST /api/legal/disbursements`
  (handled by `DisbursementController.java:33 @RequestMapping("/api/legal/disbursements")`)
  and works.

The **actual** root cause is a response-shape mismatch in the server action
`fetchProjects`. Cousin-class of GAP-L-48 only at the "missing data layer"
level — NOT a missing Next.js proxy route (that framing was wrong).

## Root Cause (confirmed, not hypothesised)

### Primary blocker: response-shape mismatch in `fetchProjects`

**File:** `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts` lines 266–270

```typescript
export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  const result =
    await api.get<PaginatedResponse<{ id: string; name: string }>>("/api/projects?size=200");
  return result.content;
}
```

The generic type `PaginatedResponse<{...}>` is a lie. Backend
`GET /api/projects` returns a **flat `List<ProjectResponse>`** — see
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java:67-185`:

```java
@GetMapping
public ResponseEntity<List<ProjectResponse>> listProjects(...) {
  ...
  return ResponseEntity.ok(projects);  // line 185 — flat List
}
```

Unlike `/api/invoices` (which returns `Page<InvoiceResponse>` and serialises
via `PageSerializationMode.VIA_DTO` to `{content: [...], page: {...}}`),
`/api/projects` is a flat array and `/api/customers` is also a flat array.

At runtime, `result` is `ProjectResponse[]` (a JS array). `result.content` is
`undefined`. `fetchProjects()` returns `undefined`. In the dialog at
`frontend/components/legal/create-disbursement-dialog.tsx` lines 132–143:

```typescript
Promise.all([fetchProjects(), fetchCustomers()])
  .then(([allProjects, allCustomers]) => {
    setProjects(allProjects ?? []);
    setCustomers(allCustomers ?? []);
  })
```

`allProjects` is `undefined`, so `setProjects([])` — the Matter combobox
renders only the placeholder option. Separately, `projectsLoading` flips to
`false` so `disabled={projectsLoading || !!defaultProjectId}` at
`create-disbursement-dialog.tsx:349` resolves to `false` when the dialog is
opened without a `defaultProjectId` (org-level page). But even with the
combobox enabled, there are zero options to choose from.

**Why QA reported "disabled"**: when the dialog is opened from the matter
detail's Disbursements tab, `defaultProjectId` is passed (see
`project-disbursements-tab.tsx:46`), which permanently disables the Matter
combobox by design (user is already on the matter). The QA probe was at the
org-level `/disbursements` page where `defaultProjectId` is undefined, so the
combobox is not disabled — but it is still empty (no options). Either way,
the user cannot pick a Matter.

The `fetchCustomers()` server action at lines 272–277 is already defensive:

```typescript
const result = await api.get<
  { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
>("/api/customers?size=200");
return Array.isArray(result) ? result : (result.content ?? []);
```

That is why the Customer combobox has 3 options but the Matter combobox is
empty — exactly matching QA's observation.

### Secondary gap: `recoverable` flag missing

The DisbursementController does not accept `recoverable`. Backend entity
(`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/`)
has no `recoverable` column (grep returned zero matches). This requires a
Flyway migration + entity field + DTO extension + frontend schema + form
field — **>2 hour scope, exceeds the cycle budget**. This spec **defers**
the recoverable flag to a follow-up LOW gap (call it **GAP-L-59** when
logged). Day 28 fee-note generation does NOT depend on recoverable (the
unbilled-disbursements endpoint bills everything that is UNBILLED regardless
of rebill-ability; that is a product-policy conversation, not a Day 28
blocker).

## Fix

### Primary fix (unblocks 21.6–21.9): one-line response-shape defence in `fetchProjects`

**File:** `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts` lines 266–270

Replace with the defensive pattern already used by `fetchCustomers` on the
next lines:

```typescript
export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/projects?size=200");
  return Array.isArray(result) ? result : (result.content ?? []);
}
```

This pattern handles both current backend shapes: flat `List` (the truth
today) and future `Page` serialization (if `/api/projects` is ever migrated
to paginated output). No backend change; no migration; no new Next.js API
route.

The `ProjectResponse` backend DTO includes more fields than `{id, name}`,
but the return type is narrowed to `{id, name}[]` — JS shape-compatible with
the richer response, so destructuring works at call sites.

### Other known call sites of `/api/projects?size=200` (for awareness — do NOT fix in this spec)

Per repo grep:
- `frontend/app/(app)/org/[slug]/conflict-check/actions.ts:104`
- `frontend/app/(app)/org/[slug]/court-calendar/actions.ts:193`
- `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts:136`

The court-calendar and adverse-parties actions already use a flat-array
type (`{ id: string; name: string }[]`) and don't access `.content`, so they
are not broken. The conflict-check action uses the same buggy
`PaginatedResponse<...>` pattern — that's GAP-L-29's territory and should be
bundled into a sweep when L-29 is picked up, NOT here. L-57 scope stays
minimal.

### Do NOT fix in this spec

- **Recoverable flag** — defer to follow-up (log as **GAP-L-59**). Scope is
  backend migration + entity field + controller + DTO + frontend schema +
  form field — exceeds 2-hour budget. Not required to unblock Day 28.
- **Conflict-check sibling bug** — same root cause at
  `conflict-check/actions.ts:104`, but that gap is already tracked as L-29
  (OPEN/deferred). Do NOT expand L-57 scope to fix L-29 opportunistically.
- **Missing Next.js proxy routes at `/api/projects` / `/api/disbursements`**
  — these were QA's original hypothesis and are incorrect. The architecture
  is server-action → BFF → backend; no Next.js proxy route is needed.
  L-48 (which DOES claim a proxy route is missing) remains separately OPEN
  and deferred.

### Test changes

Extend
`frontend/components/legal/__tests__/create-disbursement-dialog.test.tsx` to
cover the new behaviour. Add one test:

```typescript
it("renders Matter options when fetchProjects returns a flat array", async () => {
  // Mock fetchProjects to resolve with a flat array of {id, name} objects
  // Mock fetchCustomers similarly
  // Open the dialog, wait for the projects-loading state to clear
  // Assert the Matter <select> has at least the mocked <option> rendered
});
```

The existing test harness (`create-disbursement-dialog.test.tsx:23`) already
imports the component; extend the mock setup to intercept the server actions
(Vitest `vi.mock(...)`) with the flat-array shape.

## Scope

- **Frontend only.**
- Files to modify:
  - `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts` (lines
    266–270, ~5 lines changed)
  - `frontend/components/legal/__tests__/create-disbursement-dialog.test.tsx`
    (add one test, ~30 lines)
- Files to create: none (specifically, NO new Next.js API route files)
- Migration needed: no
- Backend change: no
- Env / config: none

## Verification

1. **No backend restart** required — pure frontend change. Next.js HMR picks
   up the edit automatically; if Turbopack gets stuck, restart frontend via
   `bash compose/scripts/svc.sh restart frontend`.
2. Run the frontend unit test suite on the affected file:
   ```bash
   cd frontend && pnpm vitest run components/legal/__tests__/create-disbursement-dialog.test.tsx
   ```
   Expect all tests (including the new flat-array case) to pass.
3. Run `pnpm lint` and `pnpm tsc --noEmit` (or the project's equivalent
   `pnpm check`) — no errors.
4. QA re-runs **Day 21 Phase B**:
   - **21.6** — matter Disbursements tab → `+ New Disbursement` clickable
     (already PASS, should remain PASS).
   - **21.7** (**BLOCKER — primary assertion**): open the dialog from the
     org-level `/disbursements` page. Matter combobox should NOT be disabled
     (since no `defaultProjectId`). Dropdown should list **at least 2
     ACTIVE matters** (Dlamini v RAF, Estate Late Peter Moroka). Select
     "Dlamini v Road Accident Fund".
   - **21.8** — dialog **still missing** the `recoverable` checkbox. Log as
     FAIL with cross-ref to the new GAP-L-59 follow-up. Do NOT block
     verification of L-57 on this.
   - **21.9** — fill Category = Sheriff Fees, Description, Amount = 850.00,
     VAT Treatment = STANDARD_15, Payment Source = Office Account, Incurred
     Date = 2026-04-22, Supplier = "Sheriff Sandton". Submit. Dialog closes
     cleanly. DB probe:
     ```sql
     SELECT count(*), sum(amount)
     FROM tenant_5039f2d497cf.legal_disbursements
     WHERE project_id = (SELECT id FROM projects WHERE name LIKE 'Dlamini%');
     ```
     Expect 1 row. List view shows the new disbursement. Matter Disbursements
     tab also shows it (SWR revalidates via `onSuccess: mutate`).
5. **Regression spot-check** on sibling pages:
   - Court Calendar `+ New Court Date` dialog — project dropdown populates
     (it already uses the flat-array pattern, so should be unaffected).
   - Adverse-parties page project dropdown — same.
6. Confirm the **matter-scoped** variant still works: open the dialog from
   a matter detail's Disbursements tab (`defaultProjectId` is passed) —
   Matter combobox is disabled as designed (pre-filled with the current
   matter; disabled to prevent cross-matter leakage). This behaviour is
   unchanged.

## Estimated Effort

**S (< 30 min)** — five-line defensive shape-check + one Vitest test. No
migration, no backend restart, no new routes.

## Parallelisation Notes

**SAFE for parallel Dev execution alongside GAP-L-56.** No file overlap:
- L-57 touches only `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts`
  + one frontend test file.
- L-56 touches only `backend/.../compliance/CustomerLifecycleGuard.java`
  + one backend test file.

Two Dev agents can work in separate worktrees simultaneously without merge
conflicts. Both fixes can land independently; no cross-dependency. Neither
fix requires a restart of the service the other agent is rebuilding (L-56
needs backend restart; L-57 only needs frontend HMR).

## Status Triage

**SPEC_READY.** Surgical, on-path for Day 28 fee-note disbursement recovery.
Root cause confirmed by code read:
- Backend: `ProjectController.java:67-185` returns flat `List<ProjectResponse>`.
- Frontend bug: `legal/disbursements/actions.ts:266-270` assumes
  `PaginatedResponse` and dereferences `.content`.
- `fetchCustomers` at lines 272–277 is the already-correct pattern.

Recoverable flag deliberately deferred to follow-up GAP-L-59 to keep this
fix within the <30 min bar. Scenario 21.8 will continue to log as a
separate FAIL until L-59 ships — does NOT block Day 28 billable-disbursement
generation.
