# 665E OBS-after: Settings › Documents family on PR #1874 @ `5ebc6be31` (task 665.18)

- **When:** 2026-11-26, 22:05Z to 22:17Z. Discover-pre ran at 22:05:26Z, the Word upload at 22:06:54Z, the captures from 22:07Z to 22:12Z, discover-post at 22:14:36Z and the Word-editor probe at 22:15Z.
- **Verdict: FAIL on one gate (check 3, K-117 column alignment).** The alignment holds only while every table is Tiptap-only. A table with a **Word row is out of line**: by 9–10 px at 1024 and by 4 px at 1440 (F-665E-A1, **introduced by this PR**).
  - Every other gate passes.
  - B2 is closed at 1440 but **not at 1024**.
  - B1 is still clipped at 1024 and has got worse; it is accepted under D-44.
  - A pre-existing backend 500 makes Word templates impossible to open (F-665E-A2).
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. It was not restarted, rebuilt or reseeded.
- **Browser:** the Playwright library 1.59.1 (headless Chromium). `lib.cjs` takes the worktree's `frontend/node_modules`, which is now installed.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm). The Playwright MCP browser was not used.
- **What changed:** no product code was changed and no SQL was run. Reads went through the API (GET); every write went through the UI.
- **Raw files:** the raw tiles (88 PNGs) are in `/tmp/665E-after-raw/`, outside the repo. 21 PNGs are in this directory.

## Stack / build source

- **`docker inspect e2e-frontend`:**
  - Created **`2026-11-26T21:57:58.457Z`**
  - Image `sha256:a633577d891e4b0b9e8f1e927cd8bac6753edb1ee9ec93e844cfb6354fdc1f08`
  - `com.docker.compose.project.working_dir` = **`/Users/rakheendama/Projects/2026/worktree-epic-665E/compose`**
  - Running and healthy; `GET /` → 200.
- **Worktree:** `git -C /Users/rakheendama/Projects/2026/worktree-epic-665E rev-parse HEAD` = **`5ebc6be31388fa3b6ea28220fe89435349a7b95b`**. `git status --short` shows only this untracked `after/` directory.
- **The new build is the one served:** the Acceptance h1 reads "Acceptance" (K-124), `data-testid="page-header"` is present, and the templates are on DataTable.
- **Data differs from OBS-before.** The whole stack was rebuilt, so the backend was reseeded. Since the build, the orchestrator ran:
  - legal-lifecycle (105 passed)
  - legal-depth-ii (40 passed, 4 skipped)
  - `e2e/tests/documents/` + `settings/packs.spec.ts` (11 passed)
- **State at discover-pre:**

  | | OBS-before | This run (discover-pre) |
  |---|---|---|
  | `brandColor` | null | **`#1B3A4B`** |
  | `documentFooterText`, `acceptanceExpiryDays` | — | null, null |
  | Templates | 22 | 22, all PLATFORM / TIPTAP / active, **no Word template** |
  | Clauses | 32 | 32, same 10 categories |
  | Delivery log rows | 13 | **7** |
  | Email stats | 13/50 | sent24h 7, 5/50 |
  | Members | alice Owner, bob Admin, carol Member | same |

  - The spec run's clone **`851e67db-dfd3-41d6-867a-33c52f81eb65`** ("Engagement Letter — Litigation (Custom) mxix", ORG_CUSTOM, inactive) is the editable-editor subject. Its source is PLATFORM `e7e223de-…`.

## What was run (in order)

All of these ran with `PHASE=after` from `after/scripts/`:

1. `00-discover pre`
2. **`02-upload-docx`** (new)
3. `01-capture alice`
4. `01-capture carol`
5. `03-interactions`
6. `04-saves`
7. `06-custom-editor`
8. `07-message-contrast`
9. `08-defect-crops`
10. **`09-compare`** (new)
11. `00-discover post`
12. **`10-docx-editor`** (new)
13. **`11-clause-heading`** (new)
14. `05-assemble`

`00`, `03` and `lib.cjs` are unchanged copies; `03` only gets a header line. Each changed copy starts with a header that lists its changes.

### Selector / script diffs vs `before/scripts/` (D-50 precedent)

| Script | Change | Why |
|---|---|---|
| `01-capture` | (1) `__tableMeasure` also records per-column `x`, `innerW` and `maxContentW`, plus the table's section heading. (2) It counts `[data-slot=settings-section]` cards. (3) COMPACT adds `pageHeader` and `sectionHeads`. (4) selCompat for acceptance keeps the before key "heading h1 'Document Acceptance'" (now expected 0) and **adds "K-124 heading h1 'Acceptance' exact"** and "heading h2 'Document Acceptance' (section title)". | (1)–(3) are additive measurements. (4) is K-124. |
| `04-saves` | (1) The message class regex also matches **`text-success-foreground`** (Acceptance's message was token-mapped). (2) MSG also reports `inCardFooter`, `side` and the in-situ contrast. | (1) is a legitimate DOM change. `closest("div.rounded-lg.border")` still matches, because the kit Card carries both classes. |
| `06-custom-editor` | It also records the Save button's box and whether it is inside the viewport, and the top-bar group boxes. Alice also runs at **390×844 light**. It adds viewport shots. | K-118 check. `found.md` routing asked for 390. |
| `07-message-contrast` | (1) It uses the after classes. (2) It injects the probe `<p>` into the card's `[data-slot=card-footer]` status slot, where the real message renders. (3) It adds **in-situ** cases: the two Branding hints, the section descriptions, the Acceptance hint and the Delivery Log "to". (4) It finds the card via `closest('[data-slot=settings-section]')`. | Branding's success `<p>` is now `text-teal-600` in a `text-sm` status div, and Acceptance's is `text-success-foreground`. |
| `08-defect-crops` | (1) The Templates crop is now the "Engagement Letter" `settings-section`. The before selector `h2.first()/..` would now crop only the CardHeader. (2) It adds the **K-117 alignment clips** at 1024 and 1440. (3) The Delivery Log crop is now its `settings-section`. | Legitimate DOM changes (SettingsSection wrappers). |
| `05-assemble` | `specs-baseline.json` is written only if a spec log exists. It adds an editor-sheet row (390 / viewport shots), copies the K-117 and Word-editor crops, and embeds `upload` and `docxEditor` in `measurements.json`. | This OBS ran no spec. |
| `02-upload-docx`, `09-compare`, `10-docx-editor`, `11-clause-heading` | **New.** | See check 3, the gates, F-665E-A2 and F-665E-6 below. |

## Gate results (checks 1–10)

| # | Check | Result | Numbers |
|---|---|---|---|
| 1 | **Invariants** (2 personas × 9 surfaces × 1024/1440 × light/dark = 60 variants, plus the custom editor runs) | **PASS** | `main#main-content` = 1 and `<main>` = 1 in all 60 variants. **h1 in `#main-content` = 1** on every tab, `templates/new`, the bare route and the legacy redirect. `docHOverflowPx` = **0 in all 60**, and 0 on the custom editor at 1024, 1440 and 390. The editor exception is unchanged from before (next table). |
| 2 | **Gating parity** (`selectedTab`, `tabs`, `rail`, `notice`, final `url`, HTTP status and `main` counts, compared with `before/measurements.json` over all 60 variants) | **PASS** | **0 diffs.** The only h1-text change is Alice › Acceptance, "Document Acceptance" → "Acceptance" (K-124), in 4 variants. See the next list for each persona. |
| 3 | **K-117** | **FAIL** (alignment with a Word row). The labels, sort and "Firm" pass. | See "K-117 detail" below. |
| 4 | **B1:** Templates tables at 1024 | Still clipped. **Accepted under D-44** (measured only). | **Alice:** scroller 446 (dark 454) for tables of 666 / 676. SOURCE is cut 72 / 82 px. STATUS (146 / 156) and ACTIONS (220 / 230) are hidden. The row trigger is at x=1131 (`inViewport:false`). **Carol:** 446 / 592–602, with SOURCE cut 72 / 82 and STATUS hidden. At 1440 everything fits: 814/814 (dark 822/822). |
| 5 | **B2:** Delivery Log | **1440: fits. 1024: still clipped.** | **1440:** 814/814 (dark 822/822), with 0 cut. Before it was 862/1045 with ERROR hidden. **1024:** **446/540** (dark 454/540): PROVIDER MESSAGE ID cut 34 px, **ERROR hidden** (94 px). Before it was 494/1045. |
| 6 | **B3 / K-118:** editable editor (Alice, clone `851e67db`) | **PASS** | **`docHOverflowPx` 0** at 1024 light and dark (before: **203**), at 1440, and at 390. Top-bar `scrollWidth` = `clientWidth` everywhere. The **Save button is inside the viewport** everywhere: x 781–845 at 1024, 1344–1408 at 1440, 301–365 at 390. **The header wraps cleanly:** 2 rows at 1024 (97 px; back link + name input + HelpTip, then Preview / Preview with data / Save), 1 row at 1440 (53 px), and 3 rows at 390 (133 px). The `w-64` name input still clips the long name, which is unchanged and expected. Crops: `crop-defect-editor-topbar-1024*.png`. |
| 7 | **Saves** | **PASS** | Next table. |
| 8 | **Contrast**, composited over the canvas, light and dark | **PASS** (no regression below AA; **3 light fixes**). The Branding message is still below AA in light; this is pre-existing B9 and was kept by design. | Contrast table below. The full-page `lowContrast` probe (every visible text < AA in `#main-content`) over all 60 variants is empty, except the light editor text **"No content available" 2.67**: F-665E-8, pre-existing, Tiptap internals. Before, it also listed "PNG, JPG…" 2.67, "0/500 characters" 2.67 and "to" 2.64. **Dark: 0 entries, before and after.** |
| 9 | **Console** (60 capture loads plus 03, 04 and 06) | **PASS** | **0 errors, 0 page errors, 0 hydration messages.** There are 26 warnings, all the K-17 `[tiptap warn]: Duplicate extension names found: ['link', 'underline']`, on templates/new, the editors and the Clauses sheet. Before: 25. The extra one is the 390 editor run. |
| 10 | **SEL-COMPAT** (`sel-compat.json`, 1024 light; 319 key comparisons, including the custom editor) | **PASS** | Every count equals before apart from the following. **K-124:** "heading h1 'Document Acceptance'" Alice 1 → **0**; the new "Acceptance" h1 = 1 (Carol 0, fallback). **Data:** `template-list-item` 22 → 23 (the uploaded Word row; also Carol's fallback "table rows" 22 → 23) and Delivery Log "table rows" 13 → 7. Every spec selector is unchanged, including the strict ones: `h1 /^Templates/` 1, h2 "Engagement Letter" 1, "Engagement Letter — General" row 1 → Platform / Active, the row's buttons (`.last()` = trigger) 1 / 0, "New Template" 1 / 0, `format-filter` 1, "Power of Attorney" 1, the templates/new labels, and the editor's "Template name" / "Save" / `contenteditable`. |

**Gating parity per persona:**
- **Alice:** Templates \| Clauses \| Acceptance \| Email.
- **Carol:** Templates \| Clauses, and her rail shows Documents › Templates, Clauses.
  - Acceptance → "You don't have access to Acceptance — showing Templates instead."
  - Email → "You don't have access to Email — showing Templates instead."
  - Read-only Templates: no Upload, New Template, row actions, Branding or ACTIONS column. Clauses: no New Clause or Clause actions.
  - templates/new shows the full form (K-120, unchanged). Both editors are read-only.
- **Full-probe parity at 1440 light:** 0 testids lost (only `page-header` is new), 0 button names lost or added, and 0 control ids or labels lost or added on every surface. Titles and tab bars are unchanged.

**Editor h1 exception** (`gates.json` → `invariants.editor`):

| Page | Before | After |
|---|---|---|
| PLATFORM "Engagement Letter — General" (Alice and Carol, all 4 variants) | 2 (name + body "{org.name}") | **2**, same |
| Editable ORG_CUSTOM (Alice; 1024, 1440, 390) | 6 (**0 page h1**, 6 body) | **6** (0 page h1, 6 body), same. F-665E-1 / K-43 are still open. |
| ORG_CUSTOM read-only (Carol, 1024) | 7 (name + 6 body) | **7**, same |

### K-117 detail (`gates.json` → `k117`; crops `crop-k117-align-{1024,1440}.png`)

- **Category h2s: PASS.** They read Closure, Compliance, Cover Letter, Engagement Letter, Matter Summary, Other, Report (+ Branding), in **label order**. Before, the enums CLOSURE / COMPLIANCE / OTHER / REPORT were in key order.
- **Entity chips: PASS.** Firm ×2 (was ORGANIZATION ×2), Matter ×15, Fee note ×3, Client ×3. 0 raw-enum chips.
- **A Word (DOCX) row exists:** "OBS 665E Word", uploaded through the UI by `02` (no Word template existed and the repo has no .docx fixture). It sits in **Engagement Letter**.
- **Header left x per table** (Alice light; dark is identical at 1024 and +8 at 1440):

| Viewport | Six Tiptap-only tables (spread 0) | Engagement Letter (with the Word row) | Spread |
|---|---|---|---|
| 1024 | NAME 521, FORMAT 777 (w 75), ENTITY TYPE 852, SOURCE 952, STATUS 1039, ACTIONS 1113 | NAME 521, FORMAT 777 (**w 85**), ENTITY TYPE **862**, SOURCE **961**, STATUS **1049**, ACTIONS **1123** | **10 / 9 / 10 / 10 px → FAIL** |
| 1440 | NAME 569 (w 366), FORMAT 935, ENTITY TYPE 1015, SOURCE 1127, STATUS 1223, ACTIONS 1303 | NAME 569 (w **362**), FORMAT **931** (w **85**), then the same | **FORMAT 4 px → FAIL**; the others are 0 |

- **Carol** (no ACTIONS column): the same pattern. At 1024 ENTITY TYPE, SOURCE and STATUS are off by 10 / 9 / 10 px. At 1440 FORMAT is off by 4 px (1011 vs 1015).
- **Cause:** the Format column is `w-20` (80 px, 64 px inner). The "Word" badge, an icon plus text, is **68.5 px** of content, so that one column grows to 85 px.
  - The "Tiptap" badge is 59 px and fits.
  - The reviewer's concern is confirmed.
- **At 1024 the declared widths are not honoured anyway:** these tables overflow their 446 px scroller. Format is 75 (`w-20`), Entity 100 (`w-28`), Source 87 (`w-24`) and Status / Actions 74.
  - A wider Format class therefore needs re-measuring at 1024 as well as at 1440.

### Saves (`saves.json`, `sheet-saves.png`; Alice 1440 light, displayed values)

| Model | Button (pristine → during) | Message, before → after | Placement after | Network | API before → after |
|---|---|---|---|---|---|
| Templates › Branding "Save Branding" | enabled → "Saving..." disabled (same as before) | "Branding settings saved." → **same text**. Class `text-sm text-teal-600` → `text-teal-600`, inside the status slot's `text-muted-foreground mr-auto text-sm`. No role / aria-live (B10). **Still auto-hides** (gone at 3.5 s). 110 ms. | **The card footer (`card-footer`) status slot, left of the button on the same row.** Before, it was above the button row. | POST `?tab=templates` **200** | brandColor `#1B3A4B` → `#1B3A4B` (displayed = stored); footer **null → `""`** (B11) |
| Acceptance "Save Settings" | enabled → disabled (spinner) | "Acceptance settings updated." → **same text**. Class `text-green-600 dark:text-green-400` → `text-success-foreground`. No role. **Persists.** 81 ms. | **The card-footer status slot, left of the button** (the button is right-aligned in the footer). Before, the message was to the right of a left-aligned button. | POST `?tab=acceptance` **200** | acceptanceExpiryDays **null → 30** (B11) |

### Contrast (`07-message-contrast`, in-situ or injected into the real status slot; 1440, Alice)

| Text | Light, before → after | Dark, before → after |
|---|---|---|
| Branding hint "PNG, JPG, or SVG. Max 2 MB." | 2.67 → **7.57** | 7.51 → 6.74 |
| "0/500 characters" | 2.67 → **7.57** | → 6.74 |
| Delivery Log "to" | 2.64 → **7.57** | → 6.74 |
| "Acceptance settings updated." | 3.22 → **7.27** | 11.26 → 11.85 |
| "Branding settings saved." (`text-teal-600`) | 3.16 → **3.13** (still < AA; B9 rest, kept) | 6.33 → **5.68** (still ≥ AA) |
| Error lines (`text-destructive`) | 4.77 → 4.72 | 6.93 → 6.22 |
| Section descriptions / Acceptance hint (muted) | — | 7.57 / 6.74 |

The templates table text has no entry below AA in either theme (the 01 probe over all variants), as before. The dark values fall a little because the section background moved from `slate-950` to `bg-card` rgb(20,23,28). Nothing crosses below AA.

## New findings (`F-665E-A*`)

| id | Severity | Introduced by #1874? | What | Where (guess) |
|---|---|---|---|---|
| **F-665E-A1** | MEDIUM (the K-117 gate) | **Yes.** It is the PR's new fixed-width scheme. Before, there was no alignment at all. | With a Word row, the Format column grows from 75 / 80 px to 85 px. Every later header in that table moves 9–10 px at 1024, and FORMAT moves 4 px at 1440. The "Word" badge (68.5 px) doesn't fit the `w-20` inner 64 px. | `frontend/app/(app)/org/[slug]/settings/documents/_tabs/templates-table.tsx:25` `const FORMAT_WIDTH = "w-20"`. Widening it (e.g. `w-24`) is the obvious candidate; re-measure 1024, where the widths are shrunk. |
| **F-665E-A2** | HIGH-suspect (functional; out of 665E scope; own ticket) | **No.** Backend, unchanged by the PR (`git diff 18b95bcec HEAD -- backend` is empty). The code dates from #611. | **Any Word template fails to open.** `GET /api/templates/{docx id}` → **500** for Alice and Carol: NPE `Cannot invoke "java.util.Map.get(Object)" because "node" is null` at `DocumentTemplateService.extractClauseIds(DocumentTemplateService.java:829)` ← `getById(:122)`. DOCX templates have null `content`. The row link opens the editor's failure branch, "Failed to load template. It may have been deleted or you don't have access.", with no h1 (F-665E-2). | `backend/…/template/DocumentTemplateService.java:122,829`. Crop: `crop-defect-docx-editor-1440.png`. `measurements.json` → `docxEditor`. |
| **F-665E-A3** | LOW (residual of B2) | No. It is an incomplete fix of the pre-existing B2 (`found.md` predicted B2 would close). | At 1024 the Delivery Log needs **540 px** in a 446 px scroller. Its min-content is DATE 148 (nowrap) + RECIPIENT 84 + TEMPLATE 98 + STATUS 70 + PMID 81 + ERROR 60, so this holds whatever the data. ERROR is hidden at scroll 0. Recipients wrap to about 6 lines (`wrap-anywhere`) at that width. | `frontend/components/email/DeliveryLogTable.tsx` `COLUMNS`. Crop: `crop-defect-delivery-log-table-1024.png`. |
| F-665E-B5 (re-observed) | HIGH-suspect, pre-existing | No | On this build, the spec run's clone `851e67db` again **lost every node attr**: variable `key` 0/17, conditionalBlock `fieldKey` 0/10 and heading `level` 0/6, against 17/17, 10/10 and 6/6 on its source `e7e223de`. This was a read-only GET. The editor shows "Unconfigured — click to set condition" and `{}` chips (`crop-defect-editor-topbar-1024-viewport.png`). | `found.md` F-665E-B5 (own ticket, reproduce-first). `gates.json` → `b5Recheck`. |
| F-665E-6 (follow-up) | — | — | In Playwright's role engine, the Clauses category `h2` inside the `<button>` **is exposed** as a heading: 10 of 10 buttons, ARIA snapshot `button "Commercial 2" > heading "Commercial" [level=2]`. Chromium's own AX tree was not probed. Not confirmed as a defect. | `gates.json` → `clauseHeading` |

## Before → after: B1, B2, B3, B7, B9

| id | Before (`18b95bcec`) | After (`5ebc6be31`) | Status |
|---|---|---|---|
| **B1** Templates tables at 1024 | scroller 496; tables 666–696; SOURCE cut 22 / 52; STATUS (96 / 126) and ACTIONS (170 / 200) hidden; trigger at x=1096 | scroller **446**; tables 666 / 676; SOURCE cut **72 / 82**; STATUS (146 / 156) and ACTIONS (220 / 230) hidden; trigger at x=1131. At 1440: 814/814 | **Open and worse by 50 px** (the SettingsSection padding), as `found.md` predicted. D-44 / 669A. |
| **B2** Delivery Log | 1045 px. At 1024: 494, with TEMPLATE cut 103 and STATUS / PMID / ERROR hidden. At 1440: 862, with PMID cut 123 and ERROR hidden. | At 1024: **540 in 446**, with PMID cut 34 and ERROR hidden. At **1440: 814/814, fits**. | **Closed at 1440; open at 1024** (F-665E-A3) |
| **B3** Editable editor top bar | 731 px in 496; doc h-overflow **203**; Save off-screen | 0 at 1024, 1440 and 390; Save in viewport; wraps to 2 rows (1024) / 3 rows (390) | **Closed** (K-118). The `w-64` input still clips (unchanged). |
| **B7** Category labels / ORGANIZATION | CLOSURE, COMPLIANCE, OTHER, REPORT in key order; ORGANIZATION ×2 | Labels in label order; Firm ×2 | **Closed**, but the K-117 alignment part fails with a Word row (A1) |
| **B9** Light contrast | Hints 2.67, "to" 2.64, Acceptance message 3.22, Branding message 3.16 | Hints / "to" **7.57**, Acceptance **7.27**, Branding **3.13** | **Closed except** the Branding / editor `text-teal-600` message (kept; 669A / 670A) and "No content available" 2.67 (F-665E-8) |

## Data created / changed by this OBS (all through the UI)

| What | How | Final state |
|---|---|---|
| Template **"OBS 665E Word"** **`97f9a4ff-5e6f-42a9-a2a8-3162308a5be1`**: ORG_CUSTOM, DOCX, ENGAGEMENT_LETTER, PROJECT, file `obs-665e-after.docx` (957 B, a minimal one-paragraph OOXML generated in `/tmp/665E-after-fixture/`) | `02-upload-docx`: Templates › "Upload Word Template" → Name → "Upload Template" (2 server-action POSTs, both 200) | **Left active.** It is needed to re-verify A1 after a fix. It shows in Engagement Letter, and probably in Matter Generate menus. Its detail GET returns 500 (A2). Deactivating it would be a Deactivate click with no confirmation, so it was not done. |
| `documentFooterText` null → **`""`** (brandColor unchanged, `#1B3A4B`) | Branding "Save Branding" with the displayed values | changed (B11 class) |
| `acceptanceExpiryDays` null → **30** | Acceptance "Save Settings" with the displayed value | changed (B11 class) |

- Every dialog, menu and sheet in `03` was cancelled or escaped. Template "Deactivate" was **not** clicked.
- **Discover-post:** 23 templates (22 + the Word one); members are unchanged (alice owner, bob admin, carol member); no roles were created.

## Files

- **Sheets** (cols 1024 light \| 1024 dark \| 1440 light \| 1440 dark, full page):
  - `sheet-{templates,clauses,acceptance,email,template-new,template-edit}-{alice,carol}.png`. In the email sheets, row 2 is the Delivery Log. In the template-edit sheets, row 2 is the ORG_CUSTOM clone. Alice's row 3 is the 390 editor and the 1024 / 390 viewport shots.
  - `sheet-interactions.png`, `sheet-saves.png`.
- **Crops:**
  - `crop-k117-align-{1024,1440}.png` (A1)
  - `crop-defect-templates-table-1024.png` (B1 + A1)
  - `crop-defect-delivery-log-table-1024.png` (A3)
  - `crop-defect-editor-topbar-1024.png`, `crop-defect-editor-topbar-1024-viewport.png` (B3 closed; B5 visible)
  - `crop-defect-docx-editor-1440.png` (A2)
- **JSON** (single line):
  - `discover-{pre,post}.json`
  - `measurements.json` (+ `interactions`, `customEditor`, `messageContrast`, `upload`, `docxEditor`)
  - **`gates.json`**: invariants, gating parity, full-probe parity, K-117, B1, B2, SEL-COMPAT diff, `clauseHeading`, `b5Recheck`
  - `sel-compat.json`, `saves.json`, `console.json`
  - There is no `specs-baseline.json`, because this OBS ran no spec; see Stack for the orchestrator's runs.
- **Scripts:** `scripts/`, as executed (see "What was run").
