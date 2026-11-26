# 663A OBS-after — EmptyState surfaces (task 663.2, PR #1865)

Captured 2026-11-26 on the E2E mock-auth stack (frontend :3001, backend :8081, mock IDP :8090), **built from this slice's product code**.
After the build, the legal-lifecycle and legal-depth-ii suites ran on the stack, so it holds suite data, the same kind of data the BEFORE capture had. There was no teardown, rebuild or reseed.
Personas: **Alice** (owner) and **Carol** (member). Viewports: 1024×768 and 1440×900. Themes: light and dark, set through localStorage `kazi-theme`. Reduced motion is on and the timezone is Africa/Johannesburg. All of this matches `../before/`.

**Method (same deviation as before):** the captures came from a Playwright *library* script (`chromium.launch()`), not Playwright MCP, because MCP holds the user's Chrome profile. The scripts in `scripts/01..05-*.cjs` are **copies** of `../before/scripts/00..04-*.cjs`.
- **Changed in the copies:** the paths (`.tmp/663A-after-raw`), the "Team spec" matter id, and the sheet titles.
- **Added to the probe in `04-capture.cjs`:** visible actions, the inline row count, per-`<p>` line counts, secondary-link decoration, and the `main#main-content` / `h1` invariants.
- **Added to `05-assemble.cjs`:** a Home `my-tasks-widget` sheet covering the full 1024/1440 × light/dark grid (before had 1440 light only), plus a few extra e2e greps.

Nothing in `../before/` or `scripts/00-my-work-fresh.cjs` was edited. Raw tiles live in `.tmp/663A-after-raw/`, which git ignores.

## Data created through the UI (no SQL, no REST writes)
| What | How | Id |
|---|---|---|
| Task "663A OBS empty-state task" on matter "Team spec mwx9or7w4e4e" (Carol is a member) | Matter › Tasks › **New Task** dialog (Alice). Script: `02-create-task.cjs` | `ccec0472-0cb2-47f3-b487-d1714eecda8e` |
| Matter "663A OBS empty-fields matter" (no client) | **New Matter** dialog. On its Fields tab, removed "SA Legal — Matter Details" and "Matter Info" with the chip **Remove** buttons. On its Team tab, **Add Member** → Carol. Script: `03-setup-fields.cjs` | `e355793a-2f34-487a-ad2d-b8c4641af74b` |
| Client "663A OBS empty-fields client" | **New Client** dialog (2 steps). On its Fields tab, removed "SA Legal — Client Details". Script: `03-setup-fields.cjs` | `ca36f657-9925-4a08-966f-c64e7dcce501` |

`01-discover.cjs` made read-only GET calls only. The stack is in the same shape as before: INVOICE field definitions 0, My Work assigned 0 / unassigned 82 (Alice) and 18 (Carol), AI gates 0 (403 for Carol).
The fee note is INV-0005 (`80b617a7…`, SENT), the first detail link on `/invoices`. The org has **no INVOICE custom fields**, so the fee note has no custom fields applied, and its custom-fields section shows the empty state.

## Sheets
Each sheet has rows Alice and Carol × tiles 1024 light, 1024 dark, 1440 light, 1440 dark, shown at 0.5×. Each caption names the EmptyState it shows, with its variant, height and title tag.
| File | Surface | What it shows |
|---|---|---|
| `sheet-task-sheet-time-entries.png` | Task Sheet › Time Entries | "No time logged yet": `compact`, 164 px, `<h2>`, in the 527 px Sheet. Same for both personas, every viewport and theme |
| `sheet-task-sheet-comments.png` | Task Sheet › Comments | "No comments yet": `compact`, 184 px (two-line description), `<h2>` |
| `sheet-matter-fields-tab.png` | Matter › Fields | The `inline` row: icon, "No custom fields configured" (`<p>`), description, **Configure Fields** button. 56 px at 1440 (one line), 80 px at 1024 (two lines). Alice and Carol both see the button |
| `sheet-client-fields-tab.png` | Client › Fields | Same inline row, 56 / 80 px. Carol also sees "No field groups applied" (selector, unchanged) |
| `sheet-fee-note-custom-fields.png` | Fee-note detail | Alice: same inline row, 56 / 80 px, between the totals and Generated Documents. Carol: the existing "Something went wrong" error page (see Deviations) |
| `sheet-my-work.png` | `/my-work`, full page clipped at 2400 px | `UrgencyTaskList` "No tasks assigned" under the "My Tasks" h2: `compact`, 164 px. The Today card is **populated** (the suites logged time today for both personas), so its empty state is not on this stack; see the fresh-stack figures below |
| `sheet-ai-reviews-pending.png` / `sheet-ai-reviews-history.png` | `/ai/reviews` | Alice: the shared `EmptyState` `default` with the ShieldCheck icon tile, 268 px, title `<h2>`. The local dashed look-alike is gone. Carol: "You do not have permission to review AI actions." (unchanged, intentional) |
| `sheet-dashboard-my-tasks-widget.png` | Home `my-tasks-widget` | Carol: "Nothing assigned to you." `compact`, 164 px, `<h2>` (unchanged by 663A). Alice: the owner Home has no widget |
| `crops-measured-boxes-1440-light.png` | — | 1:1 element crops of every measured box at 1440 light, Alice and Carol |
| `sheet-my-work-fresh.png` + `my-work-fresh.json` | `/my-work` on the **fresh** stack (orchestrator, `00-my-work-fresh.cjs`) | The page-level no-tasks empty and the Today card empty, both reachable only before the suites ran |

## Before → after heights — 1440×900 light, `getBoundingClientRect()` in the real container (`measurements.json`)
| Surface | Title | Before (variant, tag, h) | After (variant, tag, h) | Width | Δ | Compact > 212 px? |
|---|---|---|---|---|---|---|
| Task Sheet › Time Entries | No time logged yet | default, h2, 248 | **compact, h2, 164** | 527 | −84 | no |
| Task Sheet › Comments | No comments yet | default, h2, 268 | **compact, h2, 184** | 527 | −84 | no |
| Matter › Fields | No custom fields configured | default, h2, 320 | **inline, `<p>`, 56** (1 line) | 1080 | −264 | n/a |
| Client › Fields | No custom fields configured | default, h2, 320 | **inline, `<p>`, 56** (1 line) | 1128 | −264 | n/a |
| Fee-note custom fields (Alice) | No custom fields configured | default, h2, 320 | **inline, `<p>`, 56** (1 line) | 1128 | −264 | n/a |
| My Work › My Tasks | No tasks assigned | default, h2, 248 | **compact, h2, 164** | 670 | −84 | no |
| My Work › Today | No time recordings yet | default, h2, 268 | compact, h2, **184** (fresh stack; populated here) | 408 | −84 | no |
| My Work page-level no-tasks | No tasks assigned to you | not reachable before | compact, h2, **232** (fresh stack) | 670 | — | **YES, +20 (F-663A-2)** |
| AI reviews › Pending | No pending reviews | look-alike, **h3**, 214 | **shared default, h2, 268** | 1128 | +54 | n/a (default) |
| AI reviews › History | No review history | look-alike, **h3**, 214 | **shared default, h2, 268** | 1128 | +54 | n/a (default) |
| Home `my-tasks-widget` (Carol) | Nothing assigned to you. | compact, h2, 164 | compact, h2, 164 | 514 | 0 | no |

Alice and Carol measure the same wherever both see the box.

**All viewports and themes (`measurements.json` › `compactAllViewports`).** Every compact and inline box is the same height at 1024 and 1440, light and dark:
- Time Entries 164, Comments 184, My Tasks 164, widget 164.
- Inline rows: 56 px at 1440, 80 px at 1024 in both themes.

From the fresh stack (`my-work-fresh.json`):
- The page-level no-tasks box is 232 px at every viewport and theme.
- Today is 184 px at 1440 and **204** px at 1024, inside budget.
- "No time logged this period." is 144 px.

**Inline one-liner wrap.**
- At 1440 the whole row sits on one line: `flexRows` = 1, height 56 = 24 px padding + the 32 px button.
- At 1024 it wraps to **two lines** (`flexRows` = 2, 80 px):
  - Matter and client tabs (664 px): title on line 1; description and button on line 2.
  - Fee note: title and description on line 1; button on line 2.
- Neither `<p>` wraps internally (`pLines` [1, 1]), so the row never exceeds the 2-line limit.

**Compact boxes over 212 px at 1440 light on this stack: none.** The one compact box over budget is the `/my-work` page-level no-tasks empty at **232 px**. It was measured on the fresh stack and is already recorded as F-663A-2 / D-21, with a 669A/670A follow-up.

**Title tag changes:**
- The custom-fields empty (matter, client, fee note) went from `h2` to `<p>` (the inline variant). Its heading role is gone.
- The AI reviews title went from `h3` to `h2`.
- Every compact title stays `h2`.

## SEL-COMPAT after (`sel-compat.json`)
| Selector | e2e hits (`frontend/e2e/tests` + `frontend/e2e/helpers`) | Live DOM after |
|---|---|---|
| `matter-fields-tab` (testid) | `day-01-03-onboarding.spec.ts:264`, `day-14-conveyancing.spec.ts:95`, `:300` (same as before) | **present**: 1 per page for Alice and Carol, as the ancestor of the inline row |
| `client-fields-tab`, `field-group-selector` | — | present |
| "No custom fields configured" | 0 (before 0) | `<p>` (no heading role). Vitest: `CustomFieldSection.test.tsx:255-256` |
| "Configure Fields" | 0 (before 0) | visible `<a>` (Button asChild) for Alice and Carol |
| "No time tracked yet" | `project-crud.spec.ts:144` (heading role), unchanged | not on these surfaces |
| "My Tasks" | 0 | `h2:My Tasks` still on `/my-work`. Vitest: `assigned-task-list.test.tsx:53,61,64`, `task-list-panel.test.tsx:259`, `my-work-dashboard.test.tsx:159,161` |
| "No pending reviews" / "No review history" | 0 | `h2`. Vitest: `reviews-client.test.tsx:31,50` (new) |
| "No time logged yet", "No comments yet", "No tasks assigned", "No time recordings yet", "Nothing assigned to you" | 0 each (new greps) | all `h2` |
| `empty-state"` / `data-variant` | `audit-log-smoke.spec.ts:19`, which is a **false positive** (the words "or empty-state" in a test title, not a selector) | every shared root carries `data-slot="empty-state"` + `data-variant` |

No e2e selector targets a title whose tag changed, so the `h2` → `<p>` change on the custom-fields empty breaks nothing in e2e.
`custom-field-section` (the task Sheet's populated Task Info group) is still present.

**Global invariants (72 captures):**
- Exactly one `main#main-content` on every page.
- Exactly one `h1` inside `#main-content` on 68 of 72 pages.
- The 4 exceptions are Carol › fee-note detail at every viewport and theme. That page is the app error boundary ("Something went wrong", rendered as an `h2`), with no `h1`. It was the same page before (the before probe did not record invariants). This is existing behaviour, not caused by 663A.

## Secondary link
No captured surface renders a `secondaryLink`. The probe found 0 `text-primary` direct-child anchors.
The only call site is `reports/profitability/page.tsx:81`, and its empty branch is not reachable on this stack. A one-off read-only page view as Alice showed `/reports/profitability` with data and no EmptyState.
The new `text-primary underline underline-offset-4` class is therefore verified only by the unit test, not in the browser.

## Console (`console.json`)
72 captures: **0 hydration warnings, 0 warnings, 4 errors**. All 4 errors are Carol's fee-note detail ("Failed to load resource: … 404 (Not Found)") at each viewport and theme. This matches before exactly (66 captures, the same 4 errors). There are 6 more captures than before because the Home widget now covers the full grid.

## Deviations / could not capture
- **`/my-work` Today card empty and page-level no-tasks empty.** Not reachable on this stack: both personas have time entries today and tasks from the suites. Both were measured on the fresh stack (`my-work-fresh.json`).
- **Hand-rolled `matter-fields-tab` org-empty branch**, now an inline `EmptyState` with the title "No custom field groups have been configured for this organisation.". Not reachable: the org has 24 PROJECT definitions and 3 auto-apply groups, as before. Only unit tests cover it (`matter-fields-tab.test.tsx`).
- **Task Sheet custom-fields empty.** Not reachable through the UI, the same as before. A new task automatically gets "Task Info".
- **`AssignedTaskList`.** Has no production importer (F-663A-1).
- **`AvailableTaskList` empty.** Not reachable (83 / 19 unassigned tasks).
- **Carol › fee note.** The existing error page (403 → 404, outside 663A scope).
- **Carol › AI reviews.** The permission line, by design.

## Defects / observations
- **No 663A defect found.** There is no layout break and no clipped text. Dark mode was checked at 1:1 on the matter and client inline rows: the title is `text-foreground` and the description `text-muted-foreground` on the dark canvas, both legible. The outline button is visible.
- **No link or gate lost.** "Configure Fields" is still rendered for both personas on matter and client (and for Alice on the fee note), exactly as it was ungated before.
- **Observation (existing, not caused by 663A).**
  - **Wrong settings tab:** `CustomFieldSection.tsx:533` (`main:532`) hard-codes `actionHref={settingsHref(slug, "matters", "custom-fields")}`, so the client and fee-note "Configure Fields" links open the *matters* custom-fields settings tab.
  - **No gate for members:** the button is shown to members (Carol) as well.
  - Neither line is in the 663A diff. Recorded for triage only.
- **Expected visual change:** the AI reviews empty grew by 54 px (214 → 268) and lost its dashed border, because it now uses the shared `default` variant as the spec requires.

## Scripts
Run from the worktree root with `NODE_OPTIONS=""`, in this order:
1. `00-my-work-fresh.cjs` (orchestrator, fresh stack only)
2. `01-discover.cjs` (read-only)
3. `02-create-task.cjs`
4. `03-setup-fields.cjs`
5. `04-capture.cjs` (`FORCE=1` re-shoots)
6. `05-assemble.cjs`
