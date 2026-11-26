# 665C OBS-after: Settings › Matters (8 tabs, take-on wizard frame, sub-pages): layout merge gate

- **When:** 2026-11-26, 19:03Z to 19:20Z.
- **PR / head:** #1872, `140320f9c` (branch `epic-665C/settings-matters`).
- **Verdict:** **FAIL** on two rows: check 7 (dark mode, F-665C-10) and check 9 (layout, F-665C-12). Both defects were introduced by this slice. Every other row is PASS. F-665C-2 is closed: the document overflow at 1024 is **0** on Templates and on Checklists.
- **What changed:** no product code was changed and no SQL was run. Reads went through `GET`; every write went through the UI.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **Raw files:** the raw per-viewport PNGs are in `/tmp/665C-after-raw/`, outside the repo. Only contact sheets, crops and JSON are committed.

## Provenance

- **Frontend:** `e2e-frontend` was created `2026-11-26T19:00:43.893Z`, image `sha256:a740451130fec835bc95c84ad8c7524570873ca665cb64442f81e9302d2badd9`. Its compose `working_dir` label is `/Users/…/worktree-epic-665C/compose`.
- **Backend:** `e2e-backend` was created `19:00:43.865Z`, image `sha256:e7fd89e47741…` (the same image as before). Same `working_dir`.
- **Worktree:** HEAD `140320f9c5daf76e6cb5192cbcecdc4325c8b985`. `git status --short frontend packages` was **empty**.
- **Stack state:** a fresh seed, `/actuator/health` UP, `/mock-login` 200. The stack was not restarted, rebuilt or reseeded. No e2e spec suite was run (the brief forbids it, so `sel-compat.json` → `specRunBefore` is 05-assemble's fixed command string with `lines: []`).

## Personas (`GET /api/me/capabilities`, recorded by 01 / 02)

| Key | Login | Role | Capabilities | `GET /api/take-on/datasets` |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner | 29 | 200 |
| `carol` | `mock-auth-token` cookie | Member | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST | 403 |
| `carol-cr-holder` | Carol's cookie while holding "665C OBS Take-on" | 665C OBS Take-on | MANAGE_TAKE_ON | 200 |
| `carol-cr-lacker` | Carol's cookie while holding "665C OBS No Take-on" | 665C OBS No Take-on | PROJECT_MANAGEMENT | 403 |

- **Carol was restored to Member at the end.** She has `overrides: []` and the same 5 capabilities (`discover-pre` = `discover-post` on members).
- **Both custom roles were left on the stack** with 0 members.

## Scripts (`scripts/`)

**Copied unchanged** from `../before/scripts/` (`diff` is empty): `lib.cjs`, `00-discover.cjs`, `01-capture.cjs`, `02-roles.cjs`, `03-data.cjs`, `04b-naming-restore.cjs`, `05-assemble.cjs`, `06-overflow-probe.cjs`.

- They were run with `PHASE=after`, which `lib.cjs` already supports. Output went to `../after/`, raw tiles to `/tmp/665C-after-raw/`.
- `03-data.cjs` reads its fixture at `../fixtures/665c-obs-clients.csv`, relative to its own folder. So the fixture was copied **unchanged** to `after/fixtures/` rather than editing the script.

**Edited (selector superset only): `04-saves.cjs`.**

- **Why:** the Naming and Request-settings cards are now `SettingsSection`s. The save message moved from a `<p>` beside the button into the CardFooter `status` slot (`[data-slot="card-footer"] p`).
- **Effect without the edit:** the before selector (`btn.parentElement`'s `<p>` child) would never find the message. The 15 s wait would time out silently and record `message: null`.
- **The superset:** the before branch is tried first, so the script still matches the before DOM.

```diff
--- before/scripts/04-saves.cjs
+++ after/scripts/04-saves.cjs
@@ MSG
-  const m = row ? [...row.children].find((c) => c !== btn && c.tagName === "P") : null;
+  const sec = btn && btn.closest('[data-slot="settings-section"]');
+  const m = (row ? [...row.children].find((c) => c !== btn && c.tagName === "P") : null) || (sec && sec.querySelector('[data-slot="card-footer"] p')) || null;
@@ saveSettings waitForFunction
-  ... const m = b && [...b.parentElement.children].find((c) => c !== b && c.tagName === "P"); return !!m; }
+  ... const m = b && ([...b.parentElement.children].find((c) => c !== b && c.tagName === "P") || (b.closest('[data-slot="settings-section"]') && b.closest('[data-slot="settings-section"]').querySelector('[data-slot="card-footer"] p'))); return !!m; }
```

**New, after-only:**

| Script | What |
|---|---|
| `07-after-checks.cjs` | Per Alice surface (8 tabs, the wizard, 9 sub-pages) × 1024/1440 × light/dark: h1/h2 lists; SettingsSection / card / DataTable counts; nested frames; bordered boxes inside sections; each table's scroller widths and whether its last header is reachable; white boxes in dark; text below 4.5:1 against its composited background. **K-15:** creates the tags "OBS 665C Dark" `#1E293B` and "OBS 665C Light" `#FDE68A` through Add Tag, measures the chip contrast and the tag SEL-COMPAT keys in 4 variants, then deletes both through the UI. Writes `after-checks.json` and `sheet-tags-rows-k15-alice.png` |
| `07b-destructive-probe.cjs` | Read-only. The computed colour of the row Edit / Duplicate / Delete / Deactivate icon buttons on Templates / Checklists / Request templates (1440, light and dark), plus a 3× crop. Writes `destructive-probe.json` and `crop-templates-actions-dark-3x.png` |
| `07c-crop-tags-dark.cjs` | A crop of 07's raw Tags tile at 1440 dark → `crop-defect-tags-delete-grey-dark.png` |
| `07d-crop-checklist-items.cjs` | Crops of 01's raw `checklists/{id}` (PLATFORM) tiles → `crop-defect-checklist-items-{1440,1024}-light.png`. Its 1024 clip missed the table |
| `07e-recrop-checklist-items-1024.cjs` | Re-crops 1024 only (`y` 560 → 380) and overwrites `crop-defect-checklist-items-1024-light.png`. A new file, so 07d is kept as executed |
| `08-compare.cjs` | Offline. Compares before and after `measurements.json` / `sel-compat.json` → `compare.json` |

- **Executed, in this order:** `00 pre` → `02 create` → `03` → `01 alice` → `01 carol` → `02 assign "665C OBS Take-on"` → `01 carol-cr-holder` → `02 assign "665C OBS No Take-on"` → `01 carol-cr-lacker` → `02 assign Member` → `04` → `04b` → `06` → `07` → `00 post` → `05` → `08` → `07b` → `07c` → `07d` → `07e`.
- Every script exited 0 and ran once.

## Data created (all through the UI as Alice; ids discovered by name)

| What | id | State |
|---|---|---|
| Role "665C OBS Take-on" ([MANAGE_TAKE_ON]) | `86260e26-a120-4a1f-9e5d-f944a41f623c` | left, 0 members |
| Role "665C OBS No Take-on" ([PROJECT_MANAGEMENT]) | `8f22e263-abea-43f5-975e-a9f4b2b7d976` | left, 0 members |
| Checklist "OBS 665C Checklist" (ORG_CUSTOM, Company, 1 item) | `561c8fea-38c1-48f9-b1ac-23c6d2dc29ef` | left |
| Request template "665C OBS Request Template" (CUSTOM) | `f7a7aa3b-4a58-45d5-a4f5-2d29e71ace4c` | left |
| Take-on batch "665C OBS take-on (mid-wizard)" (CLIENTS, 3 rows) | **`48cc36fe-1cfb-4d3d-836a-a5b5e4f53b36`** | **left at Validation, VALIDATED.** Cards: Rows 3 / Valid 2 / Errors 1 / Duplicates 0. Rows 2 and 3 Valid, row 4 Error. Identical to before |
| Tags "OBS 665C Tag", "OBS 665C Dark", "OBS 665C Light"; field group "OBS 665C Group" | — | created and deleted through the UI. Tags end as `[]`; Matters groups end as 3 |

- **The checklist digit-first probe (F-665C-1) reproduces unchanged.** It shows the same slug-pattern message (`crop-defect-checklist-digit-name-slug-error.png`).
- **The Naming pattern is left at `" "`**, the same as after before's 04b (F-665C-3 reproduces). `discover-*.json` shows `settings` as null only because of the known mis-keyed recorder.
- **Data drift from before:**
  - The fresh seed has **0 tags** (before had 2 spec-made tags). So 01-capture's Tags tab shows the EmptyState, and 07 adds the rows view.
  - The Clients custom fields number 9 (before: 10, including the spec-made "Test Field pewa").

## Checks (before → after)

| # | Check | Before | After | Result | Evidence |
|---|---|---|---|---|---|
| 1 | Tabs and gating per persona | Alice 8 tabs. Carol and the lacker: Templates \| Tags. Holder: Templates \| Tags \| Take-on. Forbidden deep links fall back to Templates with `role="status"` "You don't have access to {Tab} — showing Templates instead." | **Identical**: url, selected tab, tab bar, notice and "Something went wrong" match on **216** persona × surface × variant records. All **52** legacy-route records match byte for byte | **PASS** | `compare.json` → `gate`, `legacy` |
| 2 | One h1 in `#main-content`; one `<main id="main-content">` | 1 everywhere except the known zeros | **216/216 h1 text and count identical.** `main#main-content` = 1 and `<main>` = 1 everywhere. **0-h1 (known, DA-5):** Alice's wizard `?tab=take-on&batch={id}` ×4 variants (F-665C-5); Carol's `request-templates/{id}`, custom ×4 and PLATFORM ×4 (F-665C-6). `templates/[id]` not-found was not captured, before or after. No 2+ anywhere | **PASS** | `compare.json` → `h1` |
| 3 | F-665C-2 document h-overflow | Templates **47** (1071 px), Checklists **117** (1141 px) at 1024. Everything else 0 | **0 on every surface** × 4 personas × 1024/1440 × light/dark. Templates `docScrollWidth` **1024** and Checklists **1024** at 1024×768 (06 probe). The scroller is now `position: relative` and the sr-only names stay inside it | **PASS** | `measurements.json` → `overflowProbe`; `crop-defect-{templates,checklists}-overflow-1024.png` now show the fixed state |
| 3b | Table scroller widths, visible / full (Alice light; D-44) | see the table below | Every last header (ACTIONS etc.) can be reached by scrolling its own scroller, and no columns are hidden. But see F-665C-12 | info | `after-checks.json` |
| 4 | Save models | Naming "Matter naming pattern updated." ~115 ms; Request settings red "…API not available yet…" ~80 ms; tags / group dialogs: no message, no toast | **Same text and the same timings** (Naming 111 / 124 ms; Request 81 / 74 ms). Button disabled during the save in both. No toasts. Tags: create 683 / edit 711 / delete 384 ms. Group: create 330 / delete 528 ms; groups back to 3 | **PASS** | `saves.json`, `sheet-saves.png` |
| 4b | Where the message renders | `<p class="text-sm text-green-600 …">` to the right of "Save Settings", under the card | **The `status` slot of the section's CardFooter.** It is left-aligned, with "Save Settings" right-aligned in the same bordered footer. The `<p>` is now `text-success-foreground` (error: `text-destructive`). Still no `role` / `aria-live` (F-665C-9, unchanged) | info | `saves.json` → `message.cls` |
| 5 | Headings / COPY-FREEZE | see `measurements.json` | **h2 diffs are all card-title promotions of existing text:** "Go-live checklist" (was the `CardTitle`), "Checklist Items (n)" (was the card title), "Take-on history" (already h2). Plus the EmptyState h2 "No tags yet": existing `tags.list.heading` copy, now visible because the fresh stack has no tags. **Visible-string diffs:** K-107 field-type labels (DROPDOWN/TEXT/DATE/NUMBER/CURRENCY → Dropdown/Text/…) and K-111 "Add tag" (approved); everything else is data drift (tags / fields). **No other new string** | **PASS** | `compare.json` → `headings`, `strings` |
| 6 | SEL-COMPAT browser keys | `before/sel-compat.json` | **703 keys compared, 0 missing.** 12 changed: 11 are data drift (the "pitx" tag cells/buttons 1→0, Clients `field-row` 10→9). Alice's empty Tags page has `button 'Add Tag'` = **2**: the non-exact role name also matches the EmptyState CTA "Add tag" (K-111). The spec uses `.first()`, which is the PageHeader button; with rows the count is **1** (07). Every take-on testid is present (`take-on-tab`, `take-on-header`, `take-on-go-live-card`, `take-on-history` = 1, wizard ids). The `field-row` / `field-group-legal_za_matter` / `checklist-template-row` (6) / `request-template-row` (4) / `checklist-item-row` / `checklist-item-required` (4) counts match. "Field Definitions" is unique (1) | **PASS** | `sel-compat.json`, `compare.json` → `selCompat`, `after-checks.json` → `tags.variants.*.selCompat` |
| 7 | Dark mode | — | 0 white / near-white boxes ≥ 40×40 on every surface in dark. **K-15 chips:** "OBS 665C Dark" `#ffffff` on `#1e293b` **14.63:1**; "OBS 665C Light" `#07080b` on `#fde68a` **16.08:1**, in all 4 variants. **But** the row Delete icon on **Templates** and **Tags** renders slate-400 grey in dark (`lab(65.1 …)`, the same as Edit). It was red (`dark:text-red-400`) before → **F-665C-10**. Text below 4.5:1 occurs only in components this slice did not change (listed below) | **FAIL** | `destructive-probe.json`, `crop-templates-actions-dark-3x.png`, `crop-defect-tags-delete-grey-dark.png`, `sheet-tags-rows-k15-alice.png` |
| 8 | Console | 268 loads: 0 errors / 0 warnings / 0 hydration | **268 loads + the 02/03/04/04b runs: 0 / 0 / 0.** 07's 78 loads: 0 errors, 0 hydration | **PASS** | `console.json`, `after-checks.json` → `console` |
| 9 | Layout eyeball | — | One new layout defect: the `checklists/{id}` **Checklist Items** table no longer wraps (**F-665C-12**). Others: nits / orchestrator calls, below | **FAIL** | `crop-defect-checklist-items-{1440,1024}-light.png` |

### 3b. Table scrollers, visible / full width (px), Alice light

| Surface | 1024 before → after | 1440 before → after | Notes |
|---|---|---|---|
| Templates | 494/607 → **446/503** | 862/862 → 814/814 | All 6 headers. At 1024 ACTIONS is cut at the section edge; it scrolls inside the frame (Duplicate visible, Delete after scroll) |
| Custom fields (Matters) | 496/996 → **446/983** | 864/996 → 814/983 | Only NAME is visible at 1024, the same as before |
| Tags (2 rows, 07) | 496/521 → **446/446** | 864/864 → 814/814 | Fits at both widths |
| Checklists | 494/677 → **446/623** | 862/862 → 814/814 | ITEMS / ACTIONS after scroll at 1024 (the same as before, minus the document overflow) |
| Request templates | 494/538 → **446/450** | 862/862 → 814/814 | ACTIONS now visible at 1024 (an improvement) |
| Take-on history | 494/611 → **446/611** | 862/862 → 814/814 | |
| Wizard validation rows | 494/1142 → 494/1142 | 862/1142 → 862/1142 | Unchanged (R7) |
| `checklists/{id}` PLATFORM | **446/452 → 446/1009** | **814/814 → 814/1009** | **F-665C-12** |
| `checklists/{id}` custom | 446/446 → 446/497 | 814/814 → 814/814 | The same cause; DOCUMENT REQUIRED is cut at 1024 |

### 7b. Text below 4.5:1 (all in components outside the 665C diff, so pre-existing)

- **`TemplateEditor` (light):** placeholder token chips `text-slate-500` on `bg-slate-100`, 4.18:1. The `—` placeholders `text-slate-400`, 2.64:1.
- **Checklist item editor and request-template item cards (light):** the `text-slate-400` row index and "Accepted:", 2.67:1.
- **Dark:** the take-on "Start" / wizard teal buttons, white on `#00a38f`, 3.16:1 (DatasetPicker / wizard, unchanged).
- **Dark:** "(optional)" and "No items yet…" in `text-slate-500`, 3.80:1.
- These are K-57-class backlog items, not introduced here.

## Per-sheet eyeball (every after sheet opened next to its before sheet)

| Sheet | What changed; defects |
|---|---|
| `sheet-templates-alice` | "Create Template" moved into the PageHeader row. The table sits in a SettingsSection card. At 1024 the canvas no longer scrolls: the table clips at the card edge (ACTIONS reachable by scrolling). **Dark: the trash icon is grey, not red (F-665C-10).** Nothing else clipped or overlapping |
| `sheet-naming-alice` | The card is now a SettingsSection; "Save Settings" moved into the card footer (right). The h1 is the PageHeader size. Nit: the placeholder `<code>` chips are `bg-muted` on a `bg-muted` box, so they no longer stand out (F-665C-11). Dark OK |
| `sheet-custom-fields-alice` | "Field Definitions" and "Field Groups" are each a SettingsSection; the type labels are title case (K-107) and the group badges moved under the group title (K-107). The bordered group cards now sit inside the Field Groups section (nested frame, as the plan specifies). At 1024 the 2-column group grid is narrower, so "SA Conveyancing — Matter Details" wraps to 4 lines (D-44 narrowing). Dark OK |
| `sheet-tags-alice` | Empty state on the fresh stack: h1 + "Add Tag" in the PageHeader; EmptyState "No tags yet" + CTA "Add tag" (K-111). Two add buttons, as before ("Add Tag" + "Create tag"). Dark OK |
| `sheet-tags-rows-k15-alice` (new) | The table in an untitled section; K-15 chips legible on dark and light colours in both themes. **Dark Delete icon grey (F-665C-10).** The 1024 tab strip starts at "…plates", the same as before |
| `sheet-checklists-alice` | "Create Template" in the PageHeader; table in a section; the 1024 document overflow is gone (F-665C-2 fixed). Names still wrap word-per-line at 1024, as before. Dark OK |
| `sheet-request-templates-alice` | The same frame change; ACTIONS now fit at 1024. Deactivate is red in dark (unchanged component). OK |
| `sheet-request-settings-alice` | SettingsSection with a footer save. OK in both themes |
| `sheet-take-on-alice` | "Take-on history" is now a titled section card around the history DataTable (frameless table inside); the go-live card is a SettingsSection that looks identical. Nothing clipped. Dark OK |
| `sheet-take-on-batch-alice` | Pixel-for-pixel the same as before (frame only, behaviour untouched). Still 0 h1 (F-665C-5) |
| `sheet-sub-templates-detail-alice` | The editor form is wrapped in one SettingsSection; the bordered task cards are now inside it (nested frame). `templates/new` the same. No clipping; dark task cards are darker than the section, so readable |
| `sheet-sub-checklists-new-alice` | Form in a section; the item box is nested inside it. OK |
| `sheet-sub-checklists-detail-alice` | The h1 is the PageHeader size; "Checklist Items (n)" is an h2 section title. **The PLATFORM items table no longer wraps its description: at 1440 the "Required" badges are cut ("Requir") and DOCUMENT REQUIRED is off-screen; at 1024 only #, NAME and a truncated DESCRIPTION show (F-665C-12).** Custom: DOCUMENT REQUIRED cut at 1024 |
| `sheet-sub-checklists-edit-alice` | Form in a section. OK |
| `sheet-sub-request-templates-new-alice` | Form in a section. OK |
| `sheet-sub-request-templates-detail-alice` | The source badge moved from beside the h1 to under it (PageHeader children); the form is in a section; the PLATFORM item cards are nested inside. No clipping. Dark OK |
| `sheet-tabs-visible-carol` | Templates without ACTIONS, the same as before; Tags EmptyState with the member description (K-111) and no CTA. OK |
| `sheet-tabs-forbidden-carol` | The notice now uses `bg-muted` tokens and sits in the same place; the rest is the same as Carol's Templates. OK |
| `sheet-sub-pages-carol` | The same redirects and branches. `checklists/{id}` shows F-665C-12. The request-templates member branch still has 0 h1 (F-665C-6) |
| `sheet-custom-role-take-on` | The holder's take-on page: history is a section with the "No take-on batches yet" EmptyState inside; the batch notice is unchanged. OK |
| `sheet-custom-role-no-take-on` | The same fallback notices as before. OK |
| `sheet-data-created` | The same flow and outcome. The full-page captures paint the fixed top bar mid-page in both before and after (a capture artifact) |
| `sheet-saves` | The messages render in the footer status slot, left of a right-aligned "Save Settings". Same text. OK |
| `sheet-roles` | Firm › Roles and Team are unchanged pages; identical |
| `crop-defect-checklist-digit-name-slug-error` | F-665C-1 reproduces; the form now sits in a section card |
| `crop-defect-{templates,checklists}-overflow-1024` | Now show the **fixed** state: the page edge is intact and the table clips inside its card |

### Nested frames (orchestrator call, not counted as FAIL)

These bordered list items sit inside a SettingsSection card, where before they sat on the page:

- the Field Groups cards
- the matter-template task cards
- the checklist item editor box
- the request-template item cards

Plan 665.10/665.11 asks for "field groups and fields in sections" and for sub-page forms in sections, so this follows the spec. Nothing is clipped, and contrast is fine in both themes. A second frame around the same content (a card inside a card around one block) was **not** found: the 07 `nestedFrames` count is 0 on every surface.

## New findings (appended to `../found.md`)

| id | Introduced? | Summary | Suggested owner |
|---|---|---|---|
| **F-665C-10** | **yes (665C)** | **Delete icon grey in dark on Templates and Tags.** `TemplateList.tsx:177` and `tags-content.tsx:127` replaced `text-red-600 … dark:text-red-400` with `text-destructive`. The ghost variant's `dark:text-slate-400` (`packages/ui/src/button.tsx:21-22`) wins in dark: computed `lab(65.1 -0.85 -6.68)`, the same as Edit. Light is still red. Hover also goes to `dark:hover:text-slate-100` | 665C fix round (add `dark:text-destructive` / `dark:hover:text-destructive`, or a destructive-ghost variant) |
| **F-665C-11** | yes (nit) | **Naming placeholder `<code>` chips are `bg-muted` on a `bg-muted` box** (`project-naming-settings.tsx`), so they lose their chip background. Still legible | 665C nit / follow-up |
| **F-665C-12** | **yes (665C)** | **`checklists/{id}` Checklist Items DataTable cells inherit `whitespace-nowrap`** (`components/ui/table.tsx:84`). The description column has no `whitespace-normal` (`checklist-items-table.tsx:35`; TemplateList's name column has it at `:109`). The table grows to 1009 px: at 1440 the REQUIRED badge is cut and DOCUMENT REQUIRED is off-screen (before 814/814); at 1024 REQUIRED is hidden (before 446/452) | 665C fix round |
