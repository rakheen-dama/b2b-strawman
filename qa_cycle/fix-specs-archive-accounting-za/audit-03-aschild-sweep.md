# Fix Spec: Audit-03 sweep — propagate dialog-owns-button to remaining adjacency sites

**Severity**: dormant bug class → eliminated (per `slop-hunt-BATCH-B.md` recommendation #2)
**Surface**: Frontend — 4 files audited, 6 dialog components migrated, 2 new components extracted
**Effort**: M (~1.5h end-to-end including review)

## Problem

PR #1242 introduced the "dialog owns button" pattern for `EditCustomerDialog` + `ArchiveCustomerDialog` to fix the OBS-2103 / OBS-2103b bug class:

> Two adjacent `<*Trigger asChild>{children}</*Trigger>` siblings collide under React 19 + Radix Slot reconciliation — both `cloneElement` the inner child at the same unkeyed sibling position, so only one trigger wires up its `onClick`. Symptom: visible button, unresponsive click.

The structural fix in #1242: dialog renders its own `<Button>` directly, no `<DialogTrigger asChild>` Slot wrapping. Eliminates the bug class by elimination, not detection.

The slop-hunt audit (`qa_cycle/audits/slop-hunt-BATCH-B.md`, audit-03 status) named four files that still carry the unfixed pattern:

| File | Adjacent triggers |
|---|---|
| `frontend/components/comments/comment-item.tsx` | `<EditCommentDialog>` (DialogTrigger asChild) + inline `<AlertDialog>` (AlertDialogTrigger asChild) inside same flex row |
| `frontend/components/rates/customer-rates-tab.tsx` | `<EditCustomerRateDialog>` (DialogTrigger asChild) + `<DeleteCustomerRateDialog>` (AlertDialogTrigger asChild) inside `flex justify-end gap-1` table-row container |
| `frontend/components/rates/project-rates-tab.tsx` | Same pattern as customer-rates-tab — `EditProjectRateDialog` + `DeleteProjectRateDialog` |
| `frontend/components/expenses/expense-list.tsx` | `<LogExpenseDialog>` (DialogTrigger asChild internal) + inline `<AlertDialog>` (AlertDialogTrigger asChild) inside same flex row |

Audit verdict: **bug class is dormant, not eliminated**. The collision could re-surface on the next React reconciliation tweak or render-tree perturbation.

## Fix

Apply PR #1242's "dialog owns button" pattern to each adjacency site. Each dialog component receives `triggerLabel` / `triggerVariant` / `triggerSize` / `triggerClassName` / `triggerIcon` / `triggerAriaLabel` props and renders a `<Button>` directly inside the `<Dialog>` / `<AlertDialog>` — no `<DialogTrigger asChild>` Slot wrapping at the call-site adjacency.

### Comment row (`comments/`)

- **`edit-comment-dialog.tsx`** — migrated to `triggerLabel` props API.
- **`comments/delete-comment-dialog.tsx`** — new file. Extracted from the inline `<AlertDialog>` previously rendered inside `comment-item.tsx`. Owns its own `useState` for `open`, its own `useTransition`-style state, and its own trigger Button.
- **`comment-item.tsx`** — drops the inline AlertDialog, the delete state machine, and the `cloneElement`-style `children` props on EditCommentDialog. Now passes `triggerLabel="Edit"` / `"Delete"` to each.

### Rates tabs (`rates/`)

- **`customer-rates-tab.tsx`** — three local dialogs (`AddCustomerRateDialog`, `EditCustomerRateDialog`, `DeleteCustomerRateDialog`) migrated to dialog-owns-button. Call sites within the file updated to pass `triggerLabel` / `triggerIcon`. The non-adjacent `AddCustomerRateDialog` is migrated for consistency (single trigger pattern across the file).
- **`project-rates-tab.tsx`** — two local dialogs (`EditProjectRateDialog`, `DeleteProjectRateDialog`) migrated. The external `<AddProjectRateDialog>` (imported from `rates/add-project-rate-dialog.tsx`) is **left as-is** — its single-trigger usage at the table-header level isn't adjacent to other triggers, so the `children` API stays valid for that call site. Migrating it would broaden scope to other consumers without addressing a real collision.

### Expense list (`expenses/`)

- **`expenses/delete-expense-dialog.tsx`** — new file. Extracted from the inline `<AlertDialog>` previously rendered inside `expense-list.tsx`'s `.map()` row. Owns its own `useTransition` state and calls `deleteExpense(...)` directly.
- **`expense-list.tsx`** — drops the inline AlertDialog + the `handleDelete` helper + the AlertDialog imports + the `deleteExpense` import. Now uses `<DeleteExpenseDialog />`.
- **`LogExpenseDialog`** is **left as-is** — it uses the `children` API and `<DialogTrigger asChild>{children}</DialogTrigger>` internally. It has 4 call sites across the codebase; migrating the API would broaden scope. With `DeleteExpenseDialog` extracted, the row now contains: `LogExpenseDialog` (one Slot) + `DeleteExpenseDialog` (no Slot) — only **one** asChild Slot in the adjacency, so the OBS-2103 collision pattern (two adjacent Slots) cannot trigger.

## Tests

The existing `__tests__/comments/edit-comment-dialog.test.tsx` is updated to use the new `triggerLabel` API (3 tests, still pass). No new test added beyond the API update — the `EditCommentDialog` tests already cover open/close + form submission + visibility-toggle behaviour, which is preserved across the API change.

The OBS-2103 collision itself doesn't reproduce reliably in vitest+happy-dom (PR #1239's regression test passed in vitest but failed in RSC because jsdom can't simulate the lazy/RSC `cloneElement.props === undefined` shape). A reproducer-level regression test for the collision class is out of scope for this sweep; the audit's recommendation #2 (an ESLint rule that flags two `<*Trigger asChild>` siblings in the same parent JSX block) is the right structural defence and is also out of scope here — it's a separate ~2h custom-rule effort, deferred.

## Scope

- Migrated to dialog-owns-button: 6 dialogs (EditCommentDialog, AddCustomerRateDialog, EditCustomerRateDialog, DeleteCustomerRateDialog, EditProjectRateDialog, DeleteProjectRateDialog).
- Extracted into new components: 2 (DeleteCommentDialog, DeleteExpenseDialog).
- Left as-is with rationale: AddProjectRateDialog (not adjacent), LogExpenseDialog (extraction reduces row to one Slot, eliminating collision without API change).
- Updated test: 1 (`__tests__/comments/edit-comment-dialog.test.tsx` API update).

Out of scope per Quality Gate #7 ("one fix per PR" with explicit same-bug-class authorisation):

- The audit's recommendation #2 — custom ESLint rule for `<*Trigger asChild>` adjacency. ~2h separate effort.
- The audit's recommendation #3 — codify "dialog owns button" pattern in `frontend/CLAUDE.md`. ~30 min separate effort.
- The audit's recommendation #4 — SSR snapshot harness for dialog component family. Larger refactor.
- Tooltip-only files (4 hits in expense-list.tsx). Per audit, TooltipTriggers are hover-only and outside the click-loss class.

## Verification

- `pnpm lint` — 0 errors, 98 pre-existing warnings (none introduced).
- `pnpm test` — 2130 / 0F / 2 skip (340 test files), including the 3 EditCommentDialog tests updated to the new API.
- `pnpm build` — succeeds, full route tree renders.
- `pnpm run format:check` — whole-tree clean.
- Browser-driven verify deferred: port 3000 (Keycloak) is not agent-auth-reachable per `CLAUDE.md`. The behavioural contract (open dialog, submit, close) is covered by vitest; the structural change (dialog-owns-button) is purely a refactor of how the trigger renders.

## Implemented As

PR #N — see `audit-03-aschild-sweep.implementation-note.md` once merged.
