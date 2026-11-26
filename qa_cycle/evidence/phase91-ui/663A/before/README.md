# 663A OBS-before — EmptyState surfaces (task 663.2)

Captured 2026-11-26 on the running E2E mock-auth stack (frontend :3001, backend :8081, mock IDP :8090).
Its frontend code equals `main` @ `c82c3c1c2`. It still holds the data left by the 662A walk: no teardown, rebuild or reseed.
Personas: **Alice** (owner) and **Carol** (member). Viewports: 1024×768 and 1440×900. Themes: light and dark, set through localStorage `kazi-theme` as in the 661B/662A scripts.
Reduced motion is on and the timezone is Africa/Johannesburg.

**Deviation:** the captures came from a Playwright *library* script (`scripts/*.cjs`, `chromium.launch()`), not from Playwright MCP. MCP shares the user's Chrome profile.
Login reuses `661B/walk/scripts/common.cjs` (through `require`, unedited):
- Alice signs in through the `/mock-login` UI.
- Carol uses the `mock-auth-token` cookie, as `e2e/fixtures/auth.ts loginAs()` does.

Raw tiles live in `.tmp/663A-before-raw/`, which git ignores.

## Data created through the UI (no SQL, no REST writes)
| What | How | Id |
|---|---|---|
| Task "663A OBS empty-state task" on matter "Team spec mwwndiru39b8" (Carol is a member) | Matter › Tasks › **New Task** dialog (Alice) — `01-create-task.cjs` | `cb6f89af-638c-4233-837b-a6a03cb00fe7` |
| Matter "663A OBS empty-fields matter" (no client) | Matters › **New Matter** dialog. On its Fields tab, removed "SA Legal — Matter Details" and "Matter Info" with the chips' **Remove** buttons. On its Team tab, **Add Member** → Carol (`02-setup-fields.cjs`) | `f34b1ffa-3338-42c7-90d4-7b2b2f88be08` |
| Client "663A OBS empty-fields client" (Prospect) | Clients › **New Client** dialog (2 steps). On its Fields tab, removed "SA Legal — Client Details" (`02-setup-fields.cjs`) | `facb8d69-8802-4585-9d82-4e6bffdff792` |

`scripts/00-discover.cjs` made read-only GET calls with each persona's own token to see what the stack holds. The calls returned:
- field-definition counts: TASK 1, PROJECT 24, CUSTOMER 9, INVOICE 0;
- My Work task counts;
- AI gates;
- the matter and fee-note ids.

The fee-note id was read from the `/invoices` list links (INV-0005, `58d3c026…`).

## Sheets
Each sheet has 2 rows (Alice, Carol) × 4 tiles (1024 light, 1024 dark, 1440 light, 1440 dark), shown at 0.5×. The caption of each tile names the EmptyState it shows, with its variant, height and title tag.
For each capture, the script scrolled the target box into view before taking the viewport screenshot. My Work is the exception: it is captured full-page and clipped at 2400 px.

| File | Surface | What it shows |
|---|---|---|
| `sheet-task-sheet-time-entries.png` | Task Sheet › Time Entries | `TimeEntryList` "No time logged yet": `default`, 248 px, `<h2>`, inside the 527 px Sheet |
| `sheet-task-sheet-comments.png` | Task Sheet › Comments | `CommentSectionClient` "No comments yet": `default`, 268 px, `<h2>` |
| `sheet-matter-fields-tab.png` | Matter › Fields | `CustomFieldSection` "No custom fields configured": `default`, 320 px, `<h2>`, "Configure Fields" button. Carol sees the same box, ungated |
| `sheet-client-fields-tab.png` | Client › Fields | Same box: 320 px, `<h2>`. Carol sees it too, with "No field groups applied" above it |
| `sheet-fee-note-custom-fields.png` | Fee-note detail | Alice: same box, 320 px, `<h2>`, below the fee note. Carol: an error page (see below) |
| `sheet-my-work.png` | `/my-work` | `UrgencyTaskList` "No tasks assigned" under the "My Tasks" h2: `default`, 248 px. `TodayTimeEntries` "No time recordings yet": `default`, 268 px at 1440 and 288 px at 1024. "Available Tasks" is populated |
| `sheet-ai-reviews-pending.png` / `sheet-ai-reviews-history.png` | `/ai/reviews` | Alice: the **local look-alike** `EmptyState` in `reviews-client.tsx` (dashed border, ShieldCheck icon), 214 px, title is an `<h3>`. Carol: "You do not have permission to review AI actions." (no empty state) |
| `crops-measured-boxes-1440-light.png` | — | 1:1 element crops of every measured box at 1440 light, Alice and Carol |

## Measured heights — 1440×900 light, `getBoundingClientRect()` in the real container (`measurements.json`)
| Surface | Title | Variant | Title tag | Container | Height | Width | > 212 px? |
|---|---|---|---|---|---|---|---|
| Task Sheet › Time Entries | No time logged yet | default | h2 | Sheet | 248 | 527 | yes |
| Task Sheet › Comments | No comments yet | default | h2 | Sheet | 268 | 527 | yes |
| Matter › Fields | No custom fields configured | default | h2 | tabpanel | 320 | 1080 | yes |
| Client › Fields | No custom fields configured | default | h2 | tabpanel | 320 | 1128 | yes |
| Fee-note custom fields | No custom fields configured | default | h2 | page | 320 | 1128 | yes |
| My Work › My Tasks | No tasks assigned | default | h2 | page (`tasks-panel`) | 248 | 670 | yes |
| My Work › Today | No time recordings yet | default | h2 | page (card) | 268 | 408 | yes |
| AI reviews › Pending | No pending reviews | local look-alike | h3 | tabpanel | 214 | 1128 | yes |
| AI reviews › History | No review history | local look-alike | h3 | tabpanel | 214 | 1128 | yes |
| Home `my-tasks-widget` (Carol) | Nothing assigned to you. | compact | h2 | card | 164 | 514 | no |

Alice and Carol measure the same where both see the box.
Alice's Home renders **no** `my-tasks-widget` (the owner Home layout leaves it out), so the widget was measured as Carol.

## Surfaces that could not be emptied through the UI (recorded, not faked)
- **Hand-rolled `matter-fields-tab` `py-12` block** (`matter-fields-tab.tsx:37-48`). This block renders only when the *org* has no PROJECT field definitions and no PROJECT field groups. The stack has 24 definitions and 3 auto-apply groups.
  - Reaching the block would mean deleting org settings on the shared stack, which was not done.
  - The matter Fields tab was captured instead with every group removed, so it shows the `CustomFieldSection` empty box *inside* `matter-fields-tab`.
  - Unit tests are the only place that can show the org-empty branch.
- **Task Sheet custom-fields empty state.** A new task automatically gets the "Task Info" group, which has 1 active field, so the section renders a field ("Category"), not an empty state.
  - If a task has no groups applied, the Sheet hides the whole section (`task-detail-sheet.tsx:212,316`).
  - The Sheet has no group selector, so the `CustomFieldSection` empty box cannot be reached on a task through the UI.
  - The sheets show the populated "Custom Fields" section above Time Entries.
- **My Work page-level empty** (`my-work/page.tsx:200`, shown when a persona has no tasks at all). The page also has an `AvailableTaskList` empty state.
  - Neither can be reached: Alice has 82 unassigned tasks in her matters and Carol has 18.
  - Emptying them would mean assigning or closing about 100 tasks on the shared stack.
  - The assigned/urgent lists *are* empty for both personas.
- **`AssignedTaskList`** (`assigned-task-list.tsx:65`, one of 663.5's named offenders) has **no production importer**. Only its own test uses it: `/my-work` renders `UrgencyTaskList` (`my-work-tasks-client.tsx:85`). There is nothing to observe in the browser.
- **Carol › fee-note detail:** the app's "Something went wrong" error boundary, with a 404 console error. Carol has no fee-note access, so `/api/invoices` returns 403 for her. This is an existing behaviour, recorded only and outside 663A's scope.
- **Carol › AI reviews:** a permission line instead of the tabs. This is intentional (`ai/reviews/page.tsx` capability check).

## SEL-COMPAT before (`sel-compat.json`)
Grep of `frontend/e2e/tests` and `frontend/e2e/helpers`:

| Selector | Hits |
|---|---|
| `matter-fields-tab` | `day-01-03-onboarding.spec.ts:264`, `day-14-conveyancing.spec.ts:95`, `day-14-conveyancing.spec.ts:300`, as expected |
| `"No time tracked yet"` | `project-crud.spec.ts:144` (heading role) |
| "My Tasks" | **0 e2e hits** |
| "No pending reviews", "No review history" | 0 hits, as expected |
| "No custom fields configured", "Configure Fields" | 0 hits |

Hits outside e2e (vitest):
- "My Tasks": `__tests__/components/my-work/assigned-task-list.test.tsx:53` and `__tests__/components/tasks/task-list-panel.test.tsx:259`
- "No custom fields configured": `__tests__/field-definitions/CustomFieldSection.test.tsx:250`

Live DOM: every shared EmptyState title is an implicit-heading `<h2>`, and the AI-reviews look-alike title is an `<h3>`. No EmptyState root carries a testid.
Testids present at 1440 light:
- Matter Fields: `matter-fields-tab`, `field-group-selector`
- Client Fields: `client-fields-tab`, `field-group-selector`
- Task Sheet: `custom-field-section` (the populated Task Info group)
- My Work: `tasks-panel` (ancestor) and `extended-widgets`
- Home (Carol): `my-tasks-widget`

## Console (`console.json`)
There were 66 captures, with **0 hydration warnings** and 0 warnings. The 4 errors all come from Carol's fee-note detail at every viewport and theme: "Failed to load resource: … 404 (Not Found)".

## Scripts
Run them from the worktree root with `NODE_OPTIONS=""`, in this order:
1. `00-discover.cjs` (read-only)
2. `01-create-task.cjs`
3. `02-setup-fields.cjs`
4. `03-capture.cjs` (`FORCE=1` re-shoots)
5. `04-assemble.cjs`
