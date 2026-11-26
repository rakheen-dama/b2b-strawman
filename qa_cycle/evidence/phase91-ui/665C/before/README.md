# 665C OBS-before: Settings › Matters (8 tabs incl. Take-on, the wizard frame, 6 sub-page routes), task 665.9

- **When:** 2026-11-26, 18:00Z to 18:20Z.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026).
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`, v1.59.1; the worktree has none). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code was changed and no SQL was run. Reads went through the API (GET); every write went through the UI.
- **Raw files:** the raw per-viewport PNGs (268 page loads) are in `/tmp/665C-before-raw/`, outside the repo. Only contact sheets and defect crops are committed.

## Stack observed (build source)

- **Frontend container:** `docker inspect e2e-frontend` → created `2026-11-26T17:20:00.395Z`, image `sha256:116fe413f67b…` (image created `17:19:59.836Z`). Its compose `working_dir` label is `/Users/…/worktree-epic-665B/compose`, the same container 665B's after-fix README recorded.
  - That README records the 665B worktree HEAD as `a8720ea0c` (committed 17:17:43Z, before the build) with a clean `frontend packages` tree.
- **It equals `main`:** `git diff --stat a8720ea0c f926ebf22 -- frontend packages compose` is **empty**, and so is `a8720ea0c..cccc20cde` (the 665B squash-merge). So the observed frontend equals this worktree's base, `main` @ `f926ebf22`.
  - Same caveat as 665B: uncommitted changes in the 665B worktree at build time can't be ruled out after the fact.
- **Backend container:** `e2e-backend`, created `17:20:00.317Z` (image `sha256:e7fd89e4…`).
- **Data:** a fresh seed from that recreate. Only Owner / Admin / Member roles existed, so **665A's custom roles were gone** (see Custom roles). The tags "E2E Tag pitx" / "Applied Tag pitx" and the field "Test Field pewa" are from an earlier spec run.

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities | `GET /api/take-on/datasets` |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / false / true | 29 (all, incl. MANAGE_TAKE_ON, SIGN_OFF_TRUST_TAKE_ON) | 200 |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST | 403 |
| `carol-cr-holder` | Carol's cookie while holding **"665C OBS Take-on"** | 665C OBS Take-on / false / false | MANAGE_TAKE_ON | 200 |
| `carol-cr-lacker` | Carol's cookie while holding **"665C OBS No Take-on"** | 665C OBS No Take-on / false / false | PROJECT_MANAGEMENT | 403 |

### Custom roles + assignment order (`02-roles.cjs`, through the UI as Alice)

- **Why new roles:** the brief said to reuse "665A OBS No Oversight" / "665A OBS Oversight". `discover-pre.json` shows they **no longer exist**, because the 17:20Z recreate reseeded the database. Conservative call: create two 665C roles through the UI.
- **Roles created:** through Settings › Firm › Roles › New Role → Create Role. MANAGE_TAKE_ON *can* be granted alone (checkbox "Manage take-on").
  - "665C OBS Take-on" → id `238b506b-eea4-4e94-9289-ea773edb34c7`, capabilities exactly `[MANAGE_TAKE_ON]`.
  - "665C OBS No Take-on" → id `89401d9d-2bb9-48e9-88a9-1604f46b0591`, capabilities exactly `[PROJECT_MANAGEMENT]`.
  - Both were verified by `GET /api/org-roles`.
- **Assignment** went through Team › Carol's row › Member Detail › Role › Save, in this order:
  1. Member → **665C OBS Take-on** (18:11:22Z)
  2. → **665C OBS No Take-on** (18:12:33Z)
  3. → **Member** (18:13:34Z)
- **After each step:** `GET /api/members/{id}/capabilities` and Carol's own `/api/me/capabilities` were recorded (`measurements.json` → `customRoles`).
- **Final state:** Member, `overrides: []`, the same 5 capabilities as before. In `discover-post.json`, members are identical to `discover-pre.json`. The two roles are **left on the stack** (0 members) for OBS-after.

## Data created (all through the UI as Alice; ids for OBS-after)

| What | id | How | State |
|---|---|---|---|
| Checklist template **"OBS 665C Checklist"** (ORG_CUSTOM, Company, 1 item "Proof of address") | `ad6c257a-90f0-4a30-997a-09f59e60e63c` | `checklists/new` → Create Template → lands on `?tab=checklists` | left (needed for `checklists/{id}` and `/edit`; only PLATFORM checklists existed) |
| Request template **"665C OBS Request Template"** (CUSTOM, 0 items) | `5af28742-901d-421d-a4f8-68ee95393994` | `request-templates/new` → Create Template → lands on `request-templates/{id}` | left (only PLATFORM templates existed) |
| Take-on batch **"665C OBS take-on (mid-wizard)"** (CLIENTS, 3 rows) | **`870fa5ec-dbb1-4def-86d0-52f83f1af6e0`** | Take-on › Clients › Start → batch name + `fixtures/665c-obs-clients.csv` → Upload → wizard at **Mapping** → Save mapping → **Validation** → Run dry run | **left at Validation, status VALIDATED** (dry run done; not attested; not committed). Cards: Rows 3 / Valid 2 / Errors 1 / Duplicates 0 / Blocks 0. Row 2 Valid, row 3 Valid, row 4 Error (the company row has no CDD data). |

- **Take-on wizard URL (OBS-after):** `/org/e2e-test-org/settings/matters?tab=take-on&batch=870fa5ec-dbb1-4def-86d0-52f83f1af6e0`
  - On reload the wizard resumes at step 4 **Validation** (`takeOnStepFor`: VALIDATED, no attestation).
  - Stepper: Upload · Sheet · Mapping · **Validation** · Attestation · Results.
- **No matter template was created.** The 6 SEEDER templates exist; `templates/{id}` was observed on "Collections (Debt Recovery)" `123ba6bf-c713-408e-9ff3-2c161dafe704`. `templates/new` is the same route with id `new`.
- **The fixture is authored.** No small clients CSV existed: `qa/fixtures/take-on/` has the 23-row two-sheet `clients.xlsx`, and the capstone needs a fresh stack. Header copied from `qa/fixtures/take-on/xlsx-src/clients-sheet.csv`; names / emails `*obs665c@example.test`.
- **Created by the spec run** (after the captures, see SEL-COMPAT): tags "E2E Tag ikx6" and "Applied Tag ikx6", the client field "Test Field ikx3", a matter "Tag Matter ikx6", and clients "CF Temp …". So OBS-after's Tags table has 4 rows, not 2.

## Tabs and gating (`frontend/lib/settings-nav.ts:118-188` + `settings-family-page.tsx:84-118`)

Tablist `aria-label="Matters settings"`, order as listed. The settings rail lists the same permitted tabs.

| `?tab=` | Label | h1 (Alice) | Gate | Alice | Carol | Holder (MANAGE_TAKE_ON) | Lacker |
|---|---|---|---|---|---|---|---|
| `templates` | Templates | "Matter Templates" | — | rendered | rendered | rendered | rendered |
| `naming` | Naming | "Matter Naming" | `adminOnly` | rendered | notice + Templates | notice | notice |
| `custom-fields` | Custom fields | "Custom Fields" (+HelpTip) | `adminOnly` | rendered | notice | notice | notice |
| `tags` | Tags | "Tags" (+HelpTip) | — | rendered | rendered | rendered | rendered |
| `checklists` | Checklists | "Checklists" | `adminOnly` | rendered | notice | notice | notice |
| `request-templates` | Request templates | "Request Templates" | `adminOnly` | rendered | notice | notice | notice |
| `request-settings` | Request settings | "Request Settings" | `adminOnly` | rendered | notice | notice | notice |
| `take-on` | Take-on | "Take-on" (PageHeader, `take-on-header`) | `requiredCapability: MANAGE_TAKE_ON` | rendered | **notice** | **rendered** | **notice** |
| `take-on&batch={id}` | — | **none** (F-665C-5) | same | wizard at Validation | notice | Take-on tab + `take-on-batch-notice` "That take-on batch is not available." | notice |

- **Tab bars:**
  - Alice: Templates | Naming | Custom fields | Tags | Checklists | Request templates | Request settings | Take-on.
  - Carol and the lacker: **Templates | Tags**.
  - The holder: **Templates | Tags | Take-on**.
- **Forbidden deep link:** e.g. `?tab=take-on` for Carol or the lacker.
  - The URL is unchanged, the tab bar has no Take-on, and the Templates panel renders (h1 "Matter Templates").
  - Above it is `<p role="status">` **"You don't have access to Take-on — showing Templates instead."** The same pattern holds for every adminOnly tab, e.g. "You don't have access to Naming — showing Templates instead."
- **Holder:**
  - Sees all 4 dataset cards with Start, the go-live checklist and an **empty history**: the compact EmptyState "No take-on batches yet" (184 px; section 224 px). Alice's clients batch is not listed for her, and its `?batch=` link shows "That take-on batch is not available." This is the per-dataset backend check (see `found.md`, Observations).
- **Sub-pages as Carol:**
  - `templates/{id}` and `templates/new` → redirect to `?tab=templates`.
  - `checklists/{id}/edit` → redirects to `checklists/{id}`.
  - `checklists/{id}` (both) → read-only page (no Edit link).
  - **`checklists/new` → the full create form** (F-665C-7).
  - `request-templates/new` → h1 "New Request Template" + "You do not have permission to create request templates. …".
  - `request-templates/{id}` → **no h1**, "You do not have permission to edit request templates." (F-665C-6).
- **Legacy routes:** all 200, 0 "Something went wrong". Each lands on its new URL and gates like the tab.
  - `/settings/project-templates`, `project-naming`, `custom-fields`, `tags`, `checklists`, `request-templates`, `request-settings` → `/settings/matters?tab=…`.
  - `/settings/checklists/new` and `/settings/request-templates/new` → the sub-pages.
  - `/settings/project-templates/{id}` → `/settings/matters/templates/{id}`.
  - A PLATFORM checklist's `/edit` → redirects to `checklists/{id}`.
  - `?tab=take-on&batch=not-a-uuid` and an unknown UUID → h1 "Take-on" + "That take-on batch is not available.".
- **Global invariants** (`sel-compat.json`, every surface × persona at 1024 light; `measurements.json` variants for all 4):
  - `main#main-content` = 1 and `<main>` = 1 everywhere.
  - h1 in `#main-content` = 1 everywhere **except** the wizard frame (Alice, 0) and Carol's `request-templates/{id}` (0).
  - Tablist "Matters settings" (exact) = 1 on every tab.

## Per tab (Alice 1440 light; full probe in `measurements.json` → `personas.alice.surfaces`)

- **Templates:**
  - Subtitle, then "Create Template" (link button, admin) right-aligned on its own row.
  - A hand-rolled bordered `div.overflow-x-auto` with a raw `<table>` (no `data-slot`). Headers NAME | SOURCE | TASKS | TAGS | STATUS | ACTIONS; 6 SEEDER rows, badge "Pack", "Active".
  - Icon actions with sr-only names: Edit / Duplicate / Delete {name}. Delete is an AlertDialog.
- **Naming:**
  - One hand-rolled card (h2 "Naming Pattern"): `#project-naming-pattern` "Pattern" plus a placeholders list.
  - "Save Settings" sits under the card, not inside it.
- **Custom fields:**
  - Inner Tabs **Matters | Tasks | Clients | Fee notes** (Matters selected).
  - h2 "Field Definitions": "Add Field", a `data-slot=table` (NAME | TYPE | REQUIRED | REQUIRED FOR | PACK | STATUS | ACTIONS), 24 Matters rows (10 on Clients at capture time), `field-row` testid, row menu "Actions for {name}".
  - h2 "Field Groups": "Add Group" plus 3 hand-rolled group cards (`field-group-{slug}`) with an "Actions for {group}" menu (Edit / Delete).
- **Tags:**
  - "Add Tag" (header) plus a raw `<table>` in a bare `div.overflow-x-auto`. Headers NAME | SLUG | COLOR | ACTIONS; tag chips with inline colours; sr-only "Edit {name}" / "Delete {name}".
  - **No search input** (F-665C-8).
- **Checklists:**
  - "Create Template" plus a raw table: NAME | CLIENT TYPE | SOURCE | AUTO-INSTANTIATE | ITEMS | ACTIONS. 6 rows: 5 PLATFORM plus "OBS 665C Checklist".
  - Per-row "View {name}" / "Clone {name}", plus "Deactivate {name}" for the custom one (`window.confirm`; not exercised).
- **Request templates:**
  - "New Template" plus a raw table: NAME | SOURCE | ITEMS | STATUS | ACTIONS. 4 rows: 3 PLATFORM plus "665C OBS Request Template".
  - Actions: Edit / Duplicate / Deactivate for custom; Duplicate only for PLATFORM.
- **Request settings:** one hand-rolled card, h2 "Request Reminders", with `#reminder-interval-days` (shows 7; stored null) and "Save Settings" inside the card.
- **Take-on (frame vs behaviour, 665.9 re-scout):**
  - **Frame:**
    - `PageHeader` "Take-on" with its description (`take-on-header`).
    - The guidance line.
    - The dataset picker: 4 `data-slot=card` (Clients, Matters, Trust opening position, Debtor opening balances), each with "Start" and its template link(s).
    - The go-live checklist card (`take-on-go-live-card`): 4 items, each with "Note (optional)" and "Acknowledge".
    - The h2 "Take-on history" with `BatchHistoryTable`. This is **already a DataTable** (`data-slot=table`, `table-container`; headers BATCH | DATASET | STATUS | ROWS (VALID / ERRORS) | UPLOADED BY | UPLOADED; `take-on-history-link-{id}`, `take-on-history-status-{id}`).
    - Picker and go-live sit side by side from `xl`: side by side at 1440 (main 1176, content 864), stacked at 1024 (content 496).
  - **Wizard frame** (`?batch=`):
    - "← Back to take-on" (`take-on-back`), the **h2** batch name, the "Clients" + status badge, "Cancel take-on", and the stepper `take-on-step-*`.
  - **Behaviour (untouched in 665C, R7):** Mapping, Validation (cards `take-on-card-*`, filters, the rows table `take-on-rows` = `data-slot=table`), Attestation, Commit dialog, Results, Sign-off.
- **Sub-pages (Alice):**
  - h1 = the template / checklist name, or "New Matter Template" / "New Checklist Template" / "Edit Checklist Template" / "New Request Template". A custom request template's h1 is "Edit Template"; a PLATFORM one's is its name.
  - Back links: "← Matter Templates", "← Checklists", "← Request Templates".
  - `checklists/{id}` has a raw `<table>` (`overflow-x-auto`, 446 px at 1024). The checklist edit form uses the same native `<select>` as new.

## Measurements at 1024×768 (`measurements.json` → `at1024`, Alice light)

- **Widths:** `<main>` is **760 px** (the settings rail is beside the content). The tab panel / content column is **496 px** (left 496). Sub-pages have no rail split: their content is 760.
- **h1 count:** 1 per tab, 0 on the wizard frame.

| Surface | Table scroller visible / scroll (px) | Document h-overflow | Empty state rendered |
|---|---|---|---|
| templates | 494 / 607 (raw, hand-rolled) | **47** (F-665C-2) | — |
| naming | — | 0 | — |
| custom-fields | 496 / 996 (`data-slot=table`) | 0 | — |
| tags | 496 / 521 (raw) | 0 | — |
| checklists | 494 / 677 (raw) | **117** (F-665C-2) | — |
| request-templates | 494 / 538 (raw) | 0 | — |
| request-settings | — | 0 | — |
| take-on | 494 / 611 (history DataTable) | 0 | — (Alice has a batch). Holder: "No take-on batches yet" 184 / 224 px |
| take-on&batch | 494 / 1142 (validation rows DataTable) | 0 | — |
| templates/new | — | 0 | "No tasks yet. Add a task…" 20 / 68 px |
| request-templates/{id} (custom) | — | 0 | "No items yet. Add items…" 84 / 132 px |

- No tab's own empty state was observable, because every list has rows.
- At 1440 every document h-overflow is 0.

## Save models (`saves.json`, `sheet-saves.png`; Alice 1440 light)

| Model | Button (pristine → during) | Action | Message | Result |
|---|---|---|---|---|
| Naming, per-card "Save Settings" | enabled (not dirty-gated) → `disabled` while saving | "" → `OBS-{YYYY}-{SEQ}` → Save | **"Matter naming pattern updated."** `<p class="text-sm text-green-600 …">`, no role / aria-live, ~115 ms; it stays until the next save | stored. Restore "" → Save shows the same text, **but the pattern stays** (F-665C-3). `04b` restored it to `" "` (a blank pattern is not applied), so the stack is left at `" "`, not null. |
| Request settings, per-card "Save Settings" | same | 7 → 8 → Save, then → 7 → Save | **"Request reminder settings API not available yet. This feature is coming in a future update."** (red, ~80 ms), both times | nothing saved; still null (F-665C-4) |
| Tags, dialog create / edit / AlertDialog delete | "Add Tag" → dialog "Create Tag" (Name, "Color (optional)") → "Create"; "Edit {name}" → "Edit Tag" → "Save"; "Delete {name}" → alertdialog "Delete Tag" → "Delete" | "OBS 665C Tag" #0D9488 → edited → deleted | **no message, no toast**; the dialog closes and the row appears / changes / goes (585 / 747 / 475 ms) | tags back to the 2 seeds |
| Custom fields, field group dialog / AlertDialog delete | "Add Group" → dialog "Add Field Group" → "Create Group"; "Actions for {group}" → Delete → alertdialog "Delete Field Group" ("…This will deactivate the group…") → "Delete" | "OBS 665C Group" on the Matters tab | **no message, no toast** (368 / 569 ms) | Matters groups back to 3 (deactivated, not listed) |

- **Mis-keyed recorder:** `00-discover.cjs` and `04-saves.cjs` read the API key `projectNamingPattern`, but the response key is `matterNamingPattern`. Their `settings.projectNamingPattern` / `apiBefore` / `apiAfter*` values for Naming are therefore always null.
  - The real values: input "" before the change (stored null), and `OBS-{YYYY}-{SEQ}` after both saves, recorded by `04b` (`namingRestore04b.steps`).
  - The scripts are kept as executed.

## Console

- **268 page loads** across the 4 personas, plus the role, data, save and probe runs.
- **0 errors, 0 warnings, 0 hydration messages** (`console.json`).

## SEL-COMPAT before (`sel-compat.json`)

| Spec selector | Count today |
|---|---|
| `custom-fields.spec.ts:24` heading h1 `/^Custom Fields/` | 1 |
| `:38`/`:46`/`:58` tab "Clients" | 1 (inner tablist Matters / Tasks / Clients / Fee notes) |
| `:39` heading "Field Definitions" (after Clients) | 1 |
| `:40` `field-row` filter "ID / Passport Number" | 1 |
| `:48` button "Add Field" | 1 |
| `tags.spec.ts:23` heading h1 `/^Tags/` | 1 |
| `:36/43` button "Add Tag" | 1 (spec uses `.first()`) |
| `:52` cell (exact tag name; "E2E Tag pitx") | 1 |
| `:73` placeholder "Search tags..." | **0 on the settings page.** The spec uses it on the matter detail TagInput, so it is not in 665C's surface (F-665C-8). |

- **Take-on testids:**
  - On `?tab=take-on`: `take-on-tab`, `take-on-header`, `take-on-dataset-picker`, `take-on-guidance`, `take-on-start-{clients,matters,trust-position,debtor-balances}`, `take-on-dataset-*` ×4, `take-on-go-live-card`, `take-on-go-live-item-*` ×4, `take-on-history-link-*` ×1 = **1 each**.
  - On the wizard frame: `take-on-wizard`, `take-on-back`, `take-on-batch-status`, `take-on-step-upload`, `take-on-step-validation`, `take-on-validation`, `take-on-validate`, `take-on-cards` + `take-on-card-*`, `take-on-filter-status`, `take-on-to-attestation` = 1; `take-on-row-status-*` = 3.
  - Step-specific ids (upload form, mapping, sheet picker, commit, results, sign-off) aren't on the observed frames. The full present-set per frame is under `"all take-on*/takeon* testids present"`, and `take-on-mapping` was seen at Mapping (`data.json` step `take-on-upload`).
- **Spec run before:**
  - Command, from the main checkout @ `f926ebf22`: `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 pnpm test:e2e e2e/tests/settings/custom-fields.spec.ts e2e/tests/settings/tags.spec.ts`.
  - Result: **7 passed, 0 failed** (7.0 s). The tree was not dirtied.
  - The take-on spec (`legal-lifecycle/take-on.spec.ts`) is **not in the default config**. It runs alone on a fresh stack (`pnpm test:e2e:take-on`), and test 0 requires "no take-on batch". So it was not run here: it would fail on the deliberate mid-wizard batch.

## Files

- **Contact sheets, Alice:**
  - `sheet-{tab}-alice.png` ×8 and `sheet-take-on-batch-alice.png`.
  - `sheet-sub-{templates-detail,checklists-new,checklists-detail,checklists-edit,request-templates-new,request-templates-detail}-alice.png`.
- **Contact sheets, Carol:** `sheet-tabs-visible-carol.png`, `sheet-tabs-forbidden-carol.png`, `sheet-sub-pages-carol.png`.
- **Contact sheets, custom roles:** `sheet-custom-role-take-on.png`, `sheet-custom-role-no-take-on.png`.
- **Contact sheets, other:** `sheet-data-created.png`, `sheet-saves.png`, `sheet-roles.png`.
- **Defect crops:** `crop-defect-checklist-digit-name-slug-error.png`, `crop-defect-{templates,checklists}-overflow-1024.png`.
- **JSON** (single line): `discover-{pre,post}.json`, `data.json`, `measurements.json`, `sel-compat.json`, `saves.json`, `console.json`.
- **Scripts** (as executed, run in this order):
  - `00-discover pre` → `02-roles create` → `03-data` → `01-capture alice`, `carol` → `02-roles assign "665C OBS Take-on"` → `01-capture carol-cr-holder` → `02-roles assign "665C OBS No Take-on"` → `01-capture carol-cr-lacker` → `02-roles assign Member` → `04-saves` → `04b-naming-restore` → `06-overflow-probe` → `00-discover post` → spec run → `05-assemble`.
  - `03-data` was edited and re-run twice before it completed. Its first run stopped at the checklist's Create (the digit-first name, F-665C-1) and created nothing.
- **Fixture:** `fixtures/665c-obs-clients.csv`.

## Defects seen (pre-existing) → `../found.md`

- **F-665C-1:** a checklist name starting with a digit is refused, and the raw slug-pattern error is shown.
- **F-665C-2:** at 1024, the Templates and Checklists documents scroll sideways (47 / 117 px), because the sr-only action names escape the static scroller.
- **F-665C-3:** the Naming pattern can't be cleared, although "updated" is shown. The stack is left at `" "`.
- **F-665C-4:** Request settings Save always fails, because the endpoint is missing.
- **F-665C-5:** the wizard frame has no h1.
- **F-665C-6:** `request-templates/{id}` non-admin branch has no h1.
- **F-665C-7:** `checklists/new` is ungated for members.
- **F-665C-8:** the plan's "existing 'Search tags...' input" does not exist on the Tags tab.
- **F-665C-9:** the Naming / Request settings messages are green `<p>` with no role / aria-live.
