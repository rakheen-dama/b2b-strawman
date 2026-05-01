# Audit 03 — Radix `asChild` Trigger Survey

## Hypothesis (from OBS-2103)
Adjacent `<Trigger asChild>{children}</Trigger>` siblings collide under React 19 + Radix Slot reconciliation — both `cloneElement` the inner child at the same unkeyed sibling position, so only one trigger wires up its `onClick`. Symptom: visible button, unresponsive click.

## Method
```
grep -rEn "DialogTrigger asChild|AlertDialogTrigger asChild|PopoverTrigger asChild|DropdownMenuTrigger asChild|TooltipTrigger asChild" frontend/components/ frontend/app/ portal/components/ portal/app/
```

196 hits across the codebase. The risk pattern is NOT every `asChild` declaration — it's specifically two `Trigger asChild` siblings rendered in the same flex/grid container. The common safe case is one trigger inside a dialog file, used standalone.

## Findings

### Already fixed
- **`edit-customer-dialog.tsx` + `archive-customer-dialog.tsx`** — the OBS-2103 surface. PR #1242 refactored both to render the trigger button directly inside the dialog (own-button pattern) instead of `cloneElement(children, { onClick })`. Status: VERIFIED.

### Declaration sites surveyed (by container family)

| Family | Files | Pattern |
|---|---|---|
| **Customer dialogs** | `edit-customer-dialog`, `archive-customer-dialog`, `link-project-dialog`, `anonymize-customer-dialog`, `data-export-dialog`, `create-customer-dialog` | Mostly `{children}` cloneElement. The fixed pair is Edit + Archive; the other dialogs are not rendered adjacent to each other in the customer detail page action row, but a future page that put two of them side-by-side could regress. |
| **Rates dialogs** | `add-rate-dialog`, `edit-rate-dialog`, `delete-rate-dialog`, `add-project-rate-dialog`, `customer-rates-tab` (3×), `project-rates-tab` (2×) | `customer-rates-tab.tsx:276,437,558` and `project-rates-tab.tsx:256,377` — same file has multiple Dialog/AlertDialog Triggers with `asChild`. **Worth eyeballing whether these render as adjacent siblings.** |
| **Tasks** | `create-task-dialog`, `edit-time-entry-dialog`, `delete-time-entry-dialog`, `log-time-dialog`, `task-detail-header` (DropdownMenuTrigger), `task-list-table-row` (TooltipTrigger), `time-entry-list` (TooltipTrigger ×2) | TooltipTrigger pairs in `time-entry-list` are adjacent — Tooltips don't have onClick handlers (they're hover-only), so the OBS-2103 click-loss class doesn't apply. Lower risk. |
| **Expenses** | `log-expense-dialog`, `expense-list` (TooltipTrigger ×2 + AlertDialogTrigger ×1 + TooltipTrigger ×1) | Adjacent triggers in `expense-list.tsx:329, 341, 369, 397` — mix of Tooltip and AlertDialog with `asChild`. **Worth eyeballing for click-loss.** |
| **Other** | `help-tip` (PopoverTrigger), `assistant/token-usage-badge` (TooltipTrigger), `auth/mock-user-button` (DropdownMenuTrigger), `comments/comment-item` (AlertDialogTrigger), `comments/edit-comment-dialog` (DialogTrigger), `invoices/create-invoice-button` (PopoverTrigger), `retainers/*` (multiple), `settings/*` (multiple) | Mostly single-trigger files. Low risk. |

### Concrete sites worth a manual eyeball pass (Pass B work, not auto-fix)

1. `frontend/components/rates/customer-rates-tab.tsx:276, 437, 558` — three Triggers in one file. If two render as adjacent siblings in the same render tree, OBS-2103 risk.
2. `frontend/components/rates/project-rates-tab.tsx:256, 377` — same pattern.
3. `frontend/components/expenses/expense-list.tsx:329, 341, 369, 397` — four Triggers in one file (mix of Tooltip + AlertDialog). The AlertDialog click loss would matter; Tooltips are safe.
4. `frontend/components/comments/comment-item.tsx:108` (AlertDialogTrigger) — check whether the comment row also has an Edit Dialog adjacent.

## Action items

1. **Defer to slop-hunt Pass B** — the manual eyeball survey at the four sites above. Each is a 5-min check.
2. **Lint rule candidate**: an ESLint custom rule that flags two `<*Trigger asChild>` siblings in the same parent JSX block. Implementation effort: ~2 hours. Prevents the regression class going forward.
3. **Document the dialog-owns-button pattern** (PR #1242 outcome) in `frontend/CLAUDE.md` as the canonical replacement for `cloneElement(children, { onClick })` when adjacent triggers are needed.
