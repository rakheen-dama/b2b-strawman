# 665E OBS-before: Settings › Documents (4 tabs + template sub-pages), task 665.16 "Observe-first"

- **When:** 2026-11-26, 21:23Z to 21:33Z. Discover-pre was at 21:23Z and the captures ran 21:25Z to 21:28Z. The saves ran at 21:29:20Z, the spec run from 21:29:44Z to 21:29:56Z, and discover-post at 21:32:20Z.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. It was not restarted, rebuilt or reseeded.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`; the worktree has none). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code was changed and no SQL was run. Reads went through the API (GET); every write went through the UI, apart from the spec run's own API calls.
- **Raw files:** the raw per-viewport PNGs (82) are in `/tmp/665E-before-raw/`, outside the repo. Only contact sheets and crops are committed (18 PNGs).

## Stack observed (build source)

- **Frontend container:** `docker inspect e2e-frontend` → created **`2026-11-26T21:12:13.351Z`**, image `sha256:2df6072be3d3…`. Its compose `working_dir` label is `/Users/…/worktree-epic-665D/compose` (that worktree is now deleted). 665D's final product head was `5d3430e06`.
- **It equals `main`:** `git diff --stat 5d3430e06 18b95bcec -- frontend packages compose` is **empty** (exit 0). So the observed frontend equals this worktree's base, `main` @ `18b95bcec`.
  - Caveat, as before: uncommitted changes in the 665D worktree at build time can't be ruled out after the fact.
- **Backend:** `e2e-backend`, a fresh seed. `GET /api/org-roles` shows only Owner, Admin and Member, so none of the earlier OBS roles are present.

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities | `/api/email/stats`, `/api/email/delivery-log` |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / false / true | 29 (all) | 200 / 200 |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST | 403 / 403 |

Carol gets 200 on `/api/settings`, `/api/templates`, `/api/clauses` and `/api/clauses/categories`, and on `GET /api/templates/{id}`.

### No custom roles (why)

No Documents surface is gated by a capability. Every gate is either `adminOnly` or an `isAdmin || isOwner` check, so the custom-role step was skipped:

| Where | Gate |
|---|---|
| `lib/settings-nav.ts:196-200` Templates, `:201-205` Clauses | none (member-visible) |
| `lib/settings-nav.ts:206-214` Acceptance | `adminOnly: true` (`:213`) |
| `lib/settings-nav.ts:215-220` Email | `adminOnly: true` (`:219`) |
| `_tabs/templates.tsx:12` | `canManage = caps.isAdmin \|\| caps.isOwner` (Upload, New Template, row actions, Branding) |
| `_tabs/clauses.tsx:11` | `canManage = isAdmin \|\| isOwner` (New Clause, Clause actions, editor sheet) |
| `_tabs/acceptance.tsx:11`, `_tabs/email.tsx:8` | in-page denial, if a non-admin ever reached the loader |
| `templates/[id]/edit/page.tsx:16,38` | `readOnly = !isAdmin \|\| source === "PLATFORM"` |
| `templates/new/page.tsx` | **no gate** |

No roles were created and Carol was not reassigned. `discover-post.json` members match `discover-pre.json`: alice Owner, bob Admin, carol Member.

## Data created / changed (ids for OBS-after)

| What | How | Final state |
|---|---|---|
| `brandColor` null → **`"#000000"`**, `documentFooterText` null → **`""`** | Templates › Branding › "Save Branding" with the displayed values (`#000000` is the fallback the form shows for null) | changed. The UI can't restore null (F-665E-B11). |
| `acceptanceExpiryDays` null → **30** | Acceptance › "Save Settings" with the displayed value (30 is the loader fallback) | changed (F-665E-B11) |
| Template **"E2E Cover Letter fole"** `1081f54f-bfdc-45da-89f4-9977e0dd2ba5` (ORG_CUSTOM) | **spec run** (`template-management.spec` "Create new template") | soft-deactivated by the spec's `afterAll`. It is no longer in `GET /api/templates`. |
| Clone **"Engagement Letter — Litigation (Custom) fole"** `97f07439-aec4-420b-9774-f00b366ed7bf` of PLATFORM `fb2c1395-836f-4a67-8ef0-c839a226e439` | **spec run** ("Clone template", then "Edit template content" renamed and saved it) | soft-deactivated. Its content lost its node attrs (F-665E-B5). `fb2c1395` is now "already cloned", so the next spec run picks another platform template. |
| Client "DocGen Client fole", matter "DocGen Matter fole", and a generated "Engagement Letter — General" document | **spec run** (`document-generation.spec`) | left |
| "Power of Attorney" pack template | **spec run** (`packs.spec`): edited via the API, then reverted | reverted (PUT 200 in-spec) |

The OBS itself created nothing apart from the 3 settings values above. Every dialog, menu and sheet was **cancelled or escaped**, and no destructive action was clicked (`03-interactions.cjs`).

## Tabs and gating (`settings-family-page.tsx:84-118`)

- **Tablist:** `aria-label="Documents settings"`, order Templates | Clauses | Acceptance | Email. At 1024 the strip is 322 px wide with no overflow.
- **Routing:** a bare `/settings/documents` resolves to Templates. The legacy `/settings/templates` redirects to `?tab=templates` (`lib/legacy-redirects.ts`).

| `?tab=` | Label | h1 | Gate | Alice | Carol |
|---|---|---|---|---|---|
| `templates` | Templates | "Templates" (+HelpTip) | none; manage controls `isAdmin\|\|isOwner` | full | **read-only list**: no Upload / New Template / row actions / Branding; no ACTIONS column |
| `clauses` | Clauses | "Clause Library" | none; manage controls `isAdmin\|\|isOwner` | full | list only: no New Clause / Clause actions; Expand works |
| `acceptance` | Acceptance | "Document Acceptance" | `adminOnly` | rendered | notice → Templates: "You don't have access to Acceptance — showing Templates instead." |
| `email` | Email | "Email" | `adminOnly` | rendered | notice: "You don't have access to Email — showing Templates instead." |
| `templates/new` | — | "New Template" | **none** | form | **the full create form** ("Create Template" is disabled until a Name is typed). Not submitted (F-665E-B14). |
| `templates/{PLATFORM}/edit` | — | "Engagement Letter — General" (`TemplateEditorClient.tsx:205`, readOnly branch) **+ "{org.name}" from the body** | readOnly for PLATFORM | read-only | read-only, identical |
| `templates/{ORG_CUSTOM}/edit` (spec clone, inactive) | — | **no page h1**: the name is `<input aria-label="Template name">`. The body yields 6 `<h1>` (F-665E-B4). | editable for admin/owner | editable: input, Save, `contenteditable=true` | read-only: h1 = name + 6 body h1s |

- **Tab bars:** Alice sees Templates | Clauses | Acceptance | Email. Carol sees **Templates | Clauses**, and her rail shows Documents › Templates, Clauses.
- **Global invariants** (`sel-compat.json`, 1024 light, every surface × persona):
  - `main#main-content` = 1 and `<main>` = 1 everywhere.
  - **h1 in `#main-content` = 1 on every tab**, but **2 on the PLATFORM editor** and **6–7 on the custom editor**.
  - Tablist "Documents settings" (exact) = 1 on tabs and 0 on sub-pages.
  - 0 "Something went wrong".
- **Widths:** at 1440 / 1024, `<main>` is 1176 / 760 px and the tab panel 864 / 496 px (dark is +8 px). The sub-pages have no tabpanel and use the full main width.
- **`docHOverflowPx` = 0** on every tab and sub-page, light and dark, for both personas, **except the editable editor at 1024: 203 px** light and dark (F-665E-B3).
- **Titles:**
  - Tabs: "Documents — {Tab} · Settings | Kazi".
  - Sub-pages: **"Kazi — Practice management, built for Africa"** (F-665E-B13).

## Per surface (Alice 1440 light; full probe in `measurements.json` → `personas.alice.surfaces`)

- **Templates:**
  - h1 "Templates" with HelpTip and a subtitle.
  - Toolbar (wraps): the format filter combobox (`format-filter`, "All Formats") with HelpTip, "Upload Word Template" (soft) and the link "New Template".
  - **7 category sections**, each an `h2` plus a shadcn `Table` (`data-slot=table`, `table-container` scroller, relative), not DataTable.
    - h2s: **CLOSURE, COMPLIANCE**, Cover Letter, Engagement Letter, **OTHER**, Matter Summary, **REPORT** (F-665E-B7).
    - Headers NAME | FORMAT | ENTITY TYPE (`md+`) | SOURCE (`lg+`) | STATUS | ACTIONS (`w-12`, admin only).
  - **22 rows**, all Tiptap / Platform / Active. Testids `template-list-item`, `template-dedicated-flow-hint` ×4, `template-hidden-from-menus-hint` ×2. The entity chip includes a raw "ORGANIZATION" ×2.
  - **Row actions:** an unnamed icon button. For a PLATFORM row the menu is View | Clone & Customize | **Deactivate**. Deactivate has **no confirmation** (`TemplateActionsMenu.tsx:114-119`) and was not clicked.
  - **Upload dialog** "Upload Word Template", 512×644:
    - Fields: a drop-zone `role=button`, a hidden `docx-file-input`, Name, Description, and Category / Entity native selects.
    - Buttons: "Cancel" (soft) and "Upload Template" (disabled until a file and a Name are given). It was cancelled.
  - **Branding:** a hand-rolled `div.rounded-lg.border` with h2 "Branding" and a Palette icon.
    - Logo: a dashed placeholder plus "Upload Logo" (soft), which uploads instantly on file pick. The **delete X is rendered only when a logo exists**. It is an **unnamed** `<button>` that deletes immediately, with no dialog (`branding-section.tsx:146-153`). With no logo set it was not present, so there was nothing to open or cancel.
    - Fields: Brand Color (colour input + `#brand-color` "#000000") and Document Footer Text (`#footer-text`, 0/500).
    - Button: "Save Branding", right-aligned.
  - **EmptyState:** none rendered. The data has templates; the empty branches are at `templates-content.tsx:113-127`.
- **Clauses:**
  - h1 "Clause Library" and a subtitle.
  - Toolbar: the search input (placeholder "Search clauses..."), the combobox "Filter by category", and "New Clause".
  - **10 collapsible category groups** (`clause-category`), each a button with an h2 and a count badge. h2s: Commercial, Compliance, Engagement, Legal, **compliance**, conveyancing, **engagement**, fees, general, trust (F-665E-B8).
  - **32 `clause-row`** hand-rolled bordered cards, all "System" / "Active". Each has "Expand clause" and "Clause actions" (aria-labelled), with the menu **View | Clone & Customize** (system clauses).
  - **Sheets / dialogs:**
    - "New Clause" Sheet: right side, 576 px, `sm:max-w-xl`. sr-only SheetTitle plus a visible h2. Fields Title * / Slug / Category * combobox / Description / Tiptap body (toolbar). Buttons Cancel / "Create Clause" (disabled). Closed with "Close".
    - "View" opens a "System Clause" sheet with an amber banner and Clone.
    - "Clone & Customize" opens the alertdialog **"Clone Clause"** ("Clone this clause to create an editable copy?"), Cancel / Clone, which was cancelled.
    - The **"Deactivate Clause" alertdialog is unreachable**: custom clauses only (`clauses-content.tsx:511-516`), and the seed has none.
  - Expand shows a read-only Tiptap body (`contenteditable=false`).
- **Acceptance:**
  - h1 "Document Acceptance", then one hand-rolled card whose h2 is **also "Document Acceptance"**.
  - Field: `#acceptance-expiry-days`, a number field, "Default Expiry Period (days)", 30.
  - "Save Settings" sits inside the card, left-aligned, with the message to its right.
- **Email:**
  - h1 "Email", then inner Tabs (no aria-label) **Overview | Delivery Log**.
  - **Overview:** h3 "Delivery Statistics" and 6 stat tiles: Sent (24h) 13, Bounced 0, Failed 0, Rate Limited 0, Current Hour Usage 13/50, Provider smtp.
  - **Delivery Log:** the status filter is an **unlabelled** combobox showing "All" (F-665E-B12), with options All, Sent, Delivered, Bounced, Failed, Rate Limited. It is followed by "From date" "to" "To date" inputs.
  - The table is a shadcn `Table` with headers DATE | RECIPIENT | TEMPLATE | STATUS | PROVIDER MESSAGE ID | ERROR and 13 rows. There is no pagination at 13 rows (Previous / Next = 0).
- **templates/new:**
  - A back link "‹ Templates", h1 "New Template" and a subtitle.
  - `CreateTemplateForm`: Name, Category (ENGAGEMENT_LETTER), Primary Entity Type (PROJECT), Description, Content (Tiptap toolbar, 14 buttons) and Custom CSS, plus a "Template Variables" h3 side card.
  - Buttons: Cancel / "Create Template" (disabled). There is no tabpanel; the content is 1176 / 760 px.
- **templates/[id]/edit (PLATFORM):**
  - Top bar: "‹ Templates", h1 = name (+HelpTip), "Preview", "Preview with data". The "Settings" disclosure has an editor body with `contenteditable=false`.
  - **Even in read-only it renders "Configure condition" ×8 and "Remove conditional block" ×8 enabled buttons**, for Alice and Carol alike (F-665E-B15). They were not clicked.
  - There is no `field-pack-status`.
- **templates/[id]/edit (ORG_CUSTOM, Alice):**
  - The name is an input, `w-64`, **clipped** at both widths. There is a Save button and `contenteditable=true`.
  - At 1024 the top bar is 731 px of content in a 496 px column, so the document scrolls 203 px sideways and Save is off-screen (crop).

## Save models (`saves.json`, `sheet-saves.png`; Alice 1440 light)

| Model | Button (pristine → during) | Message (text, placement, a11y, lifetime) | Network | API before → after |
|---|---|---|---|---|
| Templates › Branding "Save Branding" (per-card, colour + footer only; the logo is a separate instant upload) | enabled (not dirty-gated; disabled only for an invalid hex) → **"Saving..." disabled** | **"Branding settings saved."** `<p class="text-sm text-teal-600">` **above** the right-aligned button, inside the card. No role / aria-live, no toast. ~129 ms. **Auto-hides after 3 s** (gone at 3.5 s). | server-action POST `?tab=templates` **200** | brandColor null → `#000000`, footer null → `""` |
| Acceptance "Save Settings" (form card) | enabled → **disabled** (spinner, same label) | **"Acceptance settings updated."** `<p class="text-sm text-green-600 dark:text-green-400">` to the right of the button. No role / aria-live, no toast. ~94 ms. **Persists.** | POST `?tab=acceptance` **200** | acceptanceExpiryDays null → 30 |

**Not exercised:**
- Logo upload (instant on file pick; would add data).
- Template editor Save (the spec exercises it).
- Clause create / edit.
- Template Deactivate (no confirmation, destructive).

## SEL-COMPAT before (`sel-compat.json`, 1024 light)

| Spec selector | Alice | Carol |
|---|---|---|
| `packs.spec.ts:127` `locator("h1").filter({hasText:"Templates"})` (strict) | 1 | 1 |
| `packs.spec.ts:130` body "Something went wrong" (expects 0) | 0 | 0 |
| `packs.spec.ts:132` link exact "Power of Attorney" | 1 | 1 |
| `template-management.spec.ts:50` `/settings/templates` → URL `/settings/documents?tab=templates` | yes | yes |
| `:51` heading h1 `/^Templates/` | 1 | 1 |
| `:79` heading h2 "Engagement Letter" | 1 | 1 |
| `:57-60/81` `template-list-item` filter link exact "Engagement Letter — General" → contains Platform / Active | 1 → true/true | 1 → true/true |
| `:125` that row `getByRole("button")` (`.last()` = actions trigger) | 1 | 0 |
| `:84/90` link "New Template" | 1 | 0 |
| `:85` testid `format-filter` | 1 | 1 |
| `:92` heading h1 "New Template" (templates/new) | 1 | 1 |
| `:97` label "Category" (exact) value / `:98` "Primary Entity Type" value | 1 ENGAGEMENT_LETTER / 1 PROJECT | same |
| `:102` label "Name" (exact) / `:105` button "Create Template" | 1 / 1 | 1 / 1 |
| editor h1 named "Engagement Letter — General" (PLATFORM, `TemplateEditorClient.tsx:205`) | 1 (h1 total **2**) | 1 (total 2) |
| `:112/135/150` label "Template name": PLATFORM / custom clone | 0 / **1** | 0 / 0 |
| `:136/154` button "Save" exact: PLATFORM / custom | 0 / **1** | 0 / 0 |
| `:152` `.ProseMirror` first `contenteditable`: PLATFORM / custom | false / **true** | false / false |
| `#main-content h1`: custom editor | **6** (no page h1) | 7 |

- **`document-generation.spec.ts` selectors** act on matter and client pages only, not on this family, so they were not counted.
- **Other specs that touch this family:** `demo-recording.spec.ts:193` and `demo-full-90day.spec.ts:316,543` only navigate to `/settings/templates`. `legal-depth-ii/day-30-45` only has a comment.

## Spec baseline (`specs-baseline.json`)

- **Command:** from the main checkout `frontend/` @ `18b95bcec`, nvm Node 20, `NODE_OPTIONS=""`: `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e e2e/tests/documents/ e2e/tests/settings/packs.spec.ts`. Log: `/tmp/665E-before-specs.log`.
- **Result: 11 passed, 0 failed, 0 skipped** (11.5 s, 3 workers): DOC-01 ×4, DOC-02 ×5, PACK-01 ×2.
- **Tree:** `git status --short` of the main checkout was identical before and after, so there was nothing to `git checkout`. The pre-existing untracked / modified files were left untouched.

## Console (`console.json`)

- **Volume:** 60 page loads in `01`, plus the `03`, `04` and `06` runs.
- **Errors / hydration:** **0 errors, 0 page errors, 0 hydration messages.**
- **Warnings:** **25, all the same K-17 warning**: `[tiptap warn]: Duplicate extension names found: ['link', 'underline']`. It appears on templates/new, both template editors, and the Clauses sheet / expand (F-665E-B16, recorded, not fixed).

## Findings (pre-existing defects observed) → `../found.md`

- **F-665E-B1 (layout, 1024):** the Templates category tables are 666–696 px in a 496 px scroller.
  - SOURCE is cut 22 / 52 px, and **STATUS and ACTIONS are fully out of view** at scroll 0 (Carol: STATUS). There is no scroll affordance.
  - The row actions trigger sits at x=1096 on a 1024 viewport.
  - At 1440, 0 overflow. Crop: `crop-defect-templates-table-1024.png`.
- **F-665E-B2 (layout):** the Email › Delivery Log table is 1045 px.
  - At 1024: scroller 494 px; TEMPLATE cut 103 px; STATUS, PROVIDER MESSAGE ID and ERROR out of view.
  - At 1440: still cut (PROVIDER MESSAGE ID 123 px, ERROR hidden).
  - Crop: `crop-defect-delivery-log-table-1024.png`.
- **F-665E-B3 (layout, 1024):** the editable template editor's top bar is 731 px in a 496 px column.
  - The document scrolls **203 px** sideways and Save is off-screen.
  - The `w-64` name input clips long names at every width.
  - Crops: `crop-defect-editor-topbar-1024*.png`.
- **F-665E-B4 (headings):** editor pages have more than one `h1` in `#main-content`.
  - The template body's level-1 headings render as real `<h1>`: PLATFORM "— General" has 2 (name + "{org.name}"); the custom clone has 6.
  - The **editable** editor has **no page h1** at all, because the name is an input.
- **F-665E-B5 (suspected DATA LOSS, out of 665E scope, needs its own ticket):** after the spec's "Edit template content" (rename + Save in the editor), the clone `97f07439…` content has **lost every node attr**.
  - Lost attrs: `conditionalBlock.fieldKey/operator`, `variable.key` and `heading.level`. Compare the original `fb2c1395…`.
  - The audit shows `template.updated changed_fields:["name","content"]` for a rename-only edit.
  - The editor now shows "Unconfigured — click to set condition" and empty `{}` variables (`crop-defect-editor-topbar-1024-viewport.png`).
  - Root cause **not determined** (editor serialisation, server action or backend); the OBS did not reproduce it itself. Reproduce-before-fix applies.
- **F-665E-B6 (destructive without confirmation / a11y):**
  - Template "Deactivate" fires immediately (`TemplateActionsMenu.tsx:114-119`).
  - The logo delete X deletes immediately and is an unnamed button (`branding-section.tsx:146-153`).
  - The template actions trigger has no accessible name (the spec relies on `.last()`).
- **F-665E-B7 (copy):** raw enum category h2s on Templates (CLOSURE, COMPLIANCE, OTHER, REPORT). `CATEGORY_LABELS` (`templates-content.tsx:39-45`) lacks them, and the entity chip shows a raw "ORGANIZATION".
- **F-665E-B8 (data / UX):** Clause categories are duplicated by case: Compliance / compliance, Engagement / engagement, alongside lowercase conveyancing, fees, general and trust (`/api/clauses/categories`).
- **F-665E-B9 (light contrast < AA 4.5):** dark mode passes everything.
  - `text-slate-400` helpers: 2.67 ("PNG, JPG, or SVG. Max 2 MB.", "0/500 characters", the editor's "No content available"); Delivery Log "to" 2.64.
  - Save messages: `text-teal-600` 3.16 (Branding, editor successMsg) and `text-green-600` 3.22 (Acceptance).
  - Source: `07-message-contrast.cjs`.
- **F-665E-B10 (feedback):** save messages are bare `<p>` with no role / aria-live.
  - Branding's message sits above its button and vanishes after 3 s; Acceptance's sits beside its button and persists.
  - The colours differ (teal vs green).
- **F-665E-B11 (data drift):** saving the unchanged displayed values stores the fallbacks: brandColor `#000000`, footer `""`, acceptanceExpiryDays 30. This is the F-665D-4 class.
- **F-665E-B12 (a11y):** the Delivery Log status filter combobox has no label; its name is "All".
- **F-665E-B13 (metadata):** templates/new and templates/[id]/edit have the generic document title "Kazi — Practice management, built for Africa".
- **F-665E-B14 (gating, inferred):** templates/new has no gate. A member gets the full create form; the backend outcome was not exercised.
- **F-665E-B15 (read-only):** the read-only editor (PLATFORM, or any template for a member) still renders enabled "Configure condition" / "Remove conditional block" buttons (×8). The effect was not exercised, and there is no Save.
- **F-665E-B16 (console):** the K-17 Tiptap duplicate-extension warning, ×25.
- **Observation:** the Acceptance card h2 duplicates the page h1 ("Document Acceptance" twice).

## Files

- **Contact sheets** (cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page):
  - `sheet-{templates,clauses,acceptance,email,template-new,template-edit}-{alice,carol}.png`. In the email sheets, row 2 is the Delivery Log. In the template-edit sheets, row 2 is the ORG_CUSTOM clone.
  - `sheet-interactions.png`, `sheet-saves.png`.
- **Crops (defects):** `crop-defect-templates-table-1024.png`, `crop-defect-delivery-log-table-1024.png`, `crop-defect-editor-topbar-1024.png`, `crop-defect-editor-topbar-1024-viewport.png`.
- **JSON** (single line): `discover-{pre,post}.json`, `measurements.json` (includes `interactions`, `customEditor`, `messageContrast`), `sel-compat.json`, `saves.json`, `console.json`, `specs-baseline.json`.
- **Scripts** (as executed, in this order):
  - `00-discover pre` → `01-capture alice` → `01-capture carol` → `03-interactions` → `04-saves` → spec run → `06-custom-editor` → `07-message-contrast` → `08-defect-crops` → `00-discover post` → `05-assemble`.
  - All of them take `PHASE=before|after|after-fix`. `06` and `08` need the spec run's `template.cloned` audit event; they take the newest one. `04` re-saves the displayed values.
