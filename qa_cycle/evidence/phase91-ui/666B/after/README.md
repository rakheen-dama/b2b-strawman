# 666B OBS-after: Fee-note detail hero `/invoices/[id]` — task 666.10

- **When:** 2026-11-27, 02:18Z to 02:34Z. Rebuild 02:18–02:24Z; discover-pre 02:24:22Z; `01b-capture alice` 02:26:00–02:27:01Z; `01b-capture carol` ~02:28Z; `11-interact` 02:29:28–02:29:58Z (the INV-0009 payment); discover-post 02:31:34Z; `12-focus-crops` ~02:31Z; `13-assemble-after` 02:32:15Z; `14-nav-sentinel` 02:33:18Z (second INV-0009 payment).
- **Stack:** the same e2e mock-auth stack OBS-before used (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`, same data. Only the frontend was rebuilt; no e2e-down / e2e-up / reseed.
- **Browser:** Playwright library (headless Chromium from the main checkout's `frontend/node_modules`), Node 20.11.1 (nvm). DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded, theme via localStorage `kazi-theme` — as OBS-before.
- **What changed:** no product code, no SQL, no API writes. Writes, all through the UI: 2 payments on INV-0009 (R 500,00 ref `666B-OBS-AFTER`, R 100,00 ref `666B-OBS-AFTER-2`). The draft's line editors were opened and **cancelled** (draft `updatedAt` still 01:40:57Z — the draft form was never saved, F-666B-B1).
- **Raw files:** `/tmp/666B-after-raw/` (~110 PNGs + run JSON), outside the repo. Committed: 14 contact sheets, 10 crops, JSON.

## Stack observed (build source)

- `cd worktree-epic-666B && SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` → exit 0 (`/tmp/666B-after-raw/rebuild.log`).
- **`e2e-frontend`:** created **`2026-11-27T02:23:57Z`** from image `docteams-e2e-frontend` built **`02:23:55Z`**; compose label `working_dir` = **`/Users/…/worktree-epic-666B/compose`**. Before the build `git status --short` of the worktree (and of `frontend/`) was empty; HEAD = **`495c948c6`** on `epic-666B/fee-note-hero`. So the frontend observed = `495c948c6`.
- **Untouched:** `e2e-backend`, `e2e-postgres`, `e2e-mock-idp`, `e2e-mailpit` still created 01:40:27Z (OBS-before's). **Caveat:** compose also recreated **`e2e-localstack`** (02:23:55Z, its config path moved from the 666A to the 666B worktree). Stored S3 objects (generated PDFs) may be gone; no download was attempted. DB rows are unaffected: Generated Documents still lists them.
- **Data:** 26 fee notes at discover-pre (`discover-pre.json`) — OBS-before's 8 targets + the spec run's 18. Mailpit 8 → 8 (Record Payment sends no email).

## Personas

Alice (Owner, 29 caps, INVOICING) — every target. Carol (Member, 5 caps, no INVOICING) — INV-0008's URL. Same as before.

## Script changes vs OBS-before (scripts are not edited after running; a fix = a new copy)

- `00-discover`, `01-capture`, `02-setup`, `02b-setup`, `03-lifecycle`, `05-assemble`, `08-defect-crops`, `lib`, `probes` are byte-identical copies (they take `PHASE=after`). Only **`00-discover`** was run as-is. `02/02b/03` create OBS-before's data, which already exists. `05/08` need the setup / lifecycle / spec files. So they were not run.
- **`01b-capture.cjs`** = `01-capture.cjs` with (1) DRAFT pinned to `de6ac48f-…`: the spec run left 9 more drafts, and the "first DRAFT not created by the OBS" rule now hits a spec draft; (2) the new probes added. Same targets, viewports, themes and SEL-COMPAT.
- New: `probes2.cjs` (HERO / TABLES / EMPTIES / HEADINGS), `11-interact.cjs`, `12-focus-crops.cjs`, `13-assemble-after.cjs`, `14-nav-sentinel.cjs`. `setup-02b.json` (the fee-note ids) was copied from `/tmp/666B-before-raw/` to `/tmp/666B-after-raw/`.

## Per status (`measurements.json` → `variants.<target>.<w>-<theme>.hero`)

Every target × 1024/1440 × light/dark: HTTP 200, `main#main-content` 1, h1 in main **1**, h1 page-wide **1**, `[data-slot=detail-hero]` 1, identifier chip **0**, document h-overflow **0**. The h1 is in the hero, with the HelpTip inside it ("Help: Fee note numbering").

| Target | h1 | Status slot | Meta row (1 row at 1024 and 1440) | Actions (variant, all `sm`) | Tiles (label value [hint]) | Grid 1024 / 1440 |
|---|---|---|---|---|---|---|
| DRAFT de6ac48f | Draft fee note | Draft/neutral | Client: Acme Corp · Currency: ZAR | Preview soft · Approve accent · Delete Draft destructive | Total R 3 450,00 | 1×1 / 1×1 |
| APPROVED INV-0008 | INV-0008 | Approved/lead | Client · Issued: 27 Nov 2026 · Due: 27 Dec 2026 · Currency: ZAR | Preview · Send fee note accent · Void destructive | Total R 1 437,50 · Due 27 Dec 2026 (no hint) | 2×1 / 2×1 |
| SENT overdue INV-0009 | INV-0009 | Sent/lead | … Due: 1 Nov 2026 … | Preview · Record Payment · Void | Total R 1 875,00 · Amount paid R 0,00 · Balance due R 1 875,00 · Due 1 Nov 2026 **[⚠ 26d overdue]** | 2×2 / 4×1 |
| SENT INV-0001 | INV-0001 | Sent/lead | … Due: 27 Dec 2026 … | same | Total R 8 625,00 · R 0,00 · R 8 625,00 · Due 27 Dec 2026 [30d until due] | 2×2 / 4×1 |
| SENT take-on PS-1001 | PS-1001 | Sent/lead + **Brought forward — take-on** (`fee-note-takeon-badge`) | Client: Thandiwe Mokoena · Issued: 15 Jul 2026 · Due: 14 Aug 2026 · Currency: ZAR | same | Total R 4 600,00 · R 0,00 · R 4 600,00 · Due 14 Aug 2026 **[⚠ 105d overdue]** | 2×2 / 4×1 |
| PARTIALLY_PAID INV-0010 | INV-0010 | Partially Paid/warning | … | Preview · Record Payment | Total R 2 875,00 · Amount paid R 1 000,00 · Balance due R 1 875,00 · Due 27 Dec 2026 [30d until due] | 2×2 / 4×1 |
| PAID INV-0011 | INV-0011 | Paid/success | … | Preview | Total R 718,75 · R 718,75 · R 0,00 · Due 27 Dec 2026 (no hint) | 2×2 / 4×1 |
| VOID INV-0012 | INV-0012 | Void/destructive | … | Preview | Total R 958,24 · Due 27 Dec 2026 (no hint) | 2×1 / 2×1 |

- **Tile ↔ totals block** (`hero.agree`): Total equal on all 8; Amount paid and Balance due equal on PARTIALLY_PAID and PAID. On SENT with no payment the totals block has no Amount paid / Balance due rows. There the tiles show R 0,00 and Balance = Total, which matches the API (`amountPaid 0`, `balanceDue = total`). INV-0009's Total R 1 875,00 is the VAT-dropped figure (F-666B-B1, data from OBS-before): the tile and totals agree on it.
- **Action buttons:** text, variant and size are identical to `before/measurements.json` for all 8 statuses.
- **Testids:** `fee-note-hero-tile-{total,paid,balance,due}`; count 1 / 2 / 4 / 4 / 4 / 4 / 4 / 2 in table order.
- **Danger icon:** on the two overdue notes only (`text-danger size-3.5`, `aria-hidden`). The hint text carries the meaning.
- **Hint:** "until due" only while SENT / PARTIALLY_PAID; none on APPROVED, PAID or VOID.

## Single-instance counts (1024 light; `variants.*.counts`, `sel-compat.json`)

| Count | draft | approved | sent ×3 | partial | paid | void | before |
|---|---|---|---|---|---|---|---|
| `invoice-balance-due` | 0 | 0 | 0 | **1** | 0 | 0 | same |
| `totals-balance-due` | 0 | 0 | 0 | **1** | **1** | 0 | same |
| "Subtotal" exact text | 1 | 1 | 1 | 1 | 1 | 1 | 1 (tiles never say Subtotal) |
| `record-payment-form` / `payment-method-select` closed → opened | 0 | 0 | 0 → 1/1 (INV-0009) | 0 | 0 | 0 | same |
| placeholder "Line item description" closed → Add Line → Cancel | **0 → 1 → 0** | 0 | 0 | 0 | 0 | 0 | same |

**SEL-COMPAT diff vs before:** every selector count is identical on all targets except `text /overdue/i in main`: INV-0009 1 → 2, PS-1001 0 → 1 (the new hint, F-666B-B2). This covers `main` rows, row Edit/Delete, the h1 "Draft fee note" substring and the status texts.

## Record payment on INV-0009 (`measurements.json` → `interact.payment`, `sentinel.json`)

| | Before (SENT) | After Confirm, NO reload | After reload |
|---|---|---|---|
| Badge | Sent/lead | **Partially Paid/warning** | same |
| Actions | Preview, Record Payment, Void | **Preview, Record Payment** | same |
| Tiles | Total R 1 875,00 · Amount paid R 0,00 · Balance due R 1 875,00 · Due 1 Nov 2026 (26d overdue) | Total R 1 875,00 · **Amount paid R 500,00 · Balance due R 1 375,00** · Due (26d overdue) | same |
| Totals block | Subtotal · VAT · Total R 1 875,00 | **+ Amount paid R 500,00 · Balance due R 1 375,00** (tiles equal) | same |
| `invoice-balance-due` / `totals-balance-due` | 0 / 0 | **R 1 375,00 / R 1 375,00** | same |
| Payment History | "No payment events yet." | **1 row** Completed · Manual · EFT · 666B-OBS-AFTER · R 500,00 · 27 Nov 2026 · Receipt INV-0009/R1 | same |
| Indicator | — | Part payment received Paid R 500,00 · Balance due R 1 375,00 | same |

- The form defaulted to Amount 1875 (the balance) and method EFT; "Balance due R 1 875,00" was shown.
- `sameAfterReload: true`. API: SENT 0 / 1875 → PARTIALLY_PAID 500 / 1375.
- The save made 4 server-action POSTs, the same as OBS-before.
- **No reload, proven** (`14-nav-sentinel`): `window.__obsSentinel` set before a second payment (R 100,00) survived; 0 `load` events. The tiles went to Amount paid R 600,00 / Balance due R 1 275,00 and still equal the totals. The one same-URL `framenavigated` event is a same-document history update.
- **State left:** INV-0009 is PARTIALLY_PAID, paid R 600,00, balance R 1 275,00 (was SENT).

## Tile strip + hero layout (`measurements.json` → `tileGrid`)

- **Grid:** 4 tiles → **2×2 at 1024** (tiles 329 px light / 333 dark, grid 670 / 678) and **4×1 at 1440** (263 / 265 px, grid 1086 / 1094). 2 tiles → 2×1 at both widths (329 / 537 px). 1 tile (draft) → 1×1, one tile **670 / 1086 px wide** (F-666B-A3).
- **No overflow inside tiles:** `scrollWidth` ≤ `clientWidth` for every tile's value and label at every variant. Hero overflow elements 0. Values are 1 line.
- **Hero card:** 1440 → 1128 × 224 (1–2 tiles, or 4 tiles without a hint) / 246–249 (4 tiles with a hint). 1024 → 712 × 224 (draft, void), 272 (approved), 312 (paid), 382–385 (sent ×3, partial).
- **Header row inside the hero:** 90 px at 1440. At 1024 it is 138 px when 2–3 buttons: the meta row now stays on **one line** (F-666B-B4: was "Currency" alone on line 2), and the actions wrap onto their own row under the meta, left-aligned (`crop-hero-overdue-1024.png`). 90 px for paid / void / draft.
- **Content frame:** unchanged (712 / 1128 light, 720 / 1136 dark). Document h-overflow 0 everywhere.

## Tables (`variants.*.tables`, `interact.draft`)

| Table | 1024 table / scroller | 1440 | Columns at 1024 | Notes |
|---|---|---|---|---|
| Line items, non-draft (7 targets) | **710 / 710** | 1126 / 1126 | DESCRIPTION 225–304 px, MATTER, QTY, RATE, AMOUNT, TAX 92–119 px | Description 2 lines (was 3); **TAX 4 lines (was 3)** — F-666B-A2 |
| Line items, **draft** | **767 / 710 — overflow 57 px** (dark 767/718, 49 px) | 1126 / 1126 | DESCRIPTION 224, …, TAX 92, **ACTIONS cut 57 px** | **F-666B-A1**: Edit (x 980–1004) / Delete (1008–1032) outside the visible scroller (right edge 990). Before: 712 / 712, ACTIONS 88 px visible |
| Payment History (partial, paid; INV-0009 after the payment) | 710 / 710 | 1126 / 1126 (7 cols) | STATUS, REFERENCE (+ "Manual · EFT" compact line), AMOUNT, DATE, RECEIPT — Provider / Method hidden below 67rem | all cells 1 line; row count and row testids (none) same as before; before 678 / 678 inside a card |
| Collection activity | — | — | — | **no rows anywhere**: `GET /api/collections/activities?invoiceId=` → 0 for all 26 fee notes. Only the empty state seen |
| Generated Documents (not in PR) | 712 / 712 | 1128 / 1128 | 7 cols | unchanged |

- **Empty states:** "No payment events yet." is `EmptyState` inline, 44 px (was a grey line in a bordered card). "No collection activity for this fee note yet." is inline, 44 px (`collections-history-empty`). The custom-fields one-liner is 56 px, unchanged. "No documents generated yet" (py-12) is unchanged (F-666B-1, builder).
- **Section headings (K-75):** Payment History, Fee note details, Line Items, Generated Documents, Collection activity and Audit trail are all **Sora 18 px / 400** (was two recipes). The indicator titles (Part payment received, Payment Received) are 16 px / 500.
- **Draft:** "Add Tariff Items" + "Add Disbursements" sit on **one row** (K-75). Row Edit / Delete are present: "Edit Consultation — …" opened "Edit Line Item"; Add Line opened the form with the placeholder; both cancelled, and the API draft is unchanged (total 3450, tax 450, `updatedAt` 01:40:57Z). The audit trail renders on every target.

## Dark mode contrast (composited on the canvas)

- Tile label **6.74**, value **17.96**, hint **6.74**, danger icon **5.74:1** vs the tile (light: 7.57 / 17.79 / 7.57 / icon 4.72).
- Badges dark: Sent 6.42, Partially Paid 6.26, take-on 5.84.
- The low-contrast list is **identical to before**: accent buttons 3.16 (F-666B-B7), relative times 4.11–4.24, "No documents generated yet" 4.24.
- **D-55:** no grey-on-destructive ghost buttons. Void and Delete Draft are solid destructive (4.77). The line Delete icon is slate-500 with a red hover, unchanged.

## Keyboard (`interact.keyboard`, `sheet-focus-alice.png`)

- **Tab order:** HelpTip → Preview → Approve / Send / Record Payment → Void / Delete Draft → (draft form) → Add Line → row Edit → row Delete. On INV-0010 it goes on to the Payment History receipt link, Configure Fields, the Generated Documents buttons and the audit rows.
- **Focus ring:** every stop shows one (kit 3 px ring, light and dark; the HelpTip uses the native outline).
- **No focus lost to `<body>` inside the page.** After the last focusable on INV-0010, Tab passes `<body>` once and wraps to "Skip to content" — normal end-of-document behaviour.
- **Draft at 1024:** the row Edit button takes focus but stays **partly clipped** (scrollLeft 0). Delete focus scrolls the table 57 px (F-666B-A1).

## Carol and DetailHero consumers

- **Carol:** HTTP **404**, h1 "Page not found", 4 console errors = the 404 document × 4 variants. Same as before (`sheet-carol.png`).
- **Consumers at 1440 light** (`sheet-consumers-alice.png`), all with document overflow 0 and no visual break:
  - **Matter** Website Redesign: h1 1, hero 1, 4×1 tiles (Unbilled WIP R 833,33 · Trust balance R 0,00 · Outstanding R 11 875,00 · Next court date —), no `fee-note-hero-tile-*` testids.
  - **Client** Acme Corp: h1 1, hero 1, 4×1 tiles (Open matters 1 · Trust balance · Outstanding · FICA Done).
  - **Dashboard:** h1 "Home", no DetailHero (it is not a consumer), 4 stat cards.

## Console (`console.json`)

36 capture page loads + the interaction / focus / sentinel runs: **4 errors, 0 warnings, 0 page errors, 0 hydration**. All 4 errors are Carol's 404 document × 4 variants, the same as before. Alice: 0.

## PASS / FAIL per check

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Hero tiles per status; tile = totals figure | **PASS** | `sheet-hero-alice.png`, `measurements.json` → `variants.*.hero.tiles/agree` |
| 2 | One h1 + HelpTip, no identifier chip, meta row, badges, take-on badge, actions = before | **PASS** | `sheet-hero-alice.png`, `crop-hero-takeon-1440-dark.png`, `variants.*.hero` |
| 3 | Single-instance counts | **PASS** | `sel-compat.json`, `variants.*.counts`, `interact.draft.editors` |
| 4 | Record payment updates hero / status / totals without reload; same after reload | **PASS** | `sheet-record-payment-alice.png`, `interact.payment`, `sentinel.json` |
| 5 | Tile grid 2×2 @1024 / 4×1 @1440, no tile overflow, doc overflow 0 | **PASS** (single-tile width: F-666B-A3, LOW) | `tileGrid`, `crop-hero-overdue-1024.png`, `crop-hero-overdue-1440.png`, `crop-hero-draft-1440.png` |
| 6 | Payment History / collection activity on DataTable, no overflow @1024, empties inline | **PASS** (collection rows not observable — no data) | `crop-payment-history-1024.png`, `crop-payment-history-empty-1024.png`, `crop-collection-activity-empty-1024.png` |
| 7 | Line table: inline edit/delete, Description min-w, tariff/disbursement row, headings, custom fields, audit | **FAIL** — F-666B-A1 (draft Actions column off-canvas @1024, MEDIUM, introduced); F-666B-A2 (TAX 4 lines, LOW) | `crop-defect-draft-lines-1024.png`, `crop-defect-draft-lines-1024-scrolled.png`, `crop-partial-lines-1024.png`, `sheet-draft-lines-alice.png` |
| 8 | Dark contrast of tiles / hint / icon; no grey-on-destructive | **PASS** | `variants.*.1440-dark.hero.tiles[].contrast/icon`, `lowContrast` |
| 9 | Carol → 404 | **PASS** | `sheet-carol.png` |
| 10 | Console 0 errors / no hydration | **PASS** (only Carol's expected 404 ×4) | `console.json` |
| 11 | Keyboard: focus visible, not lost | **PASS** with the A1 note (draft Edit clipped while focused @1024) | `sheet-focus-alice.png`, `interact.keyboard` |
| 12 | DetailHero consumers unchanged | **PASS** | `sheet-consumers-alice.png`, `interact.consumers` |

**Overall: FAIL** on item 7 — one MEDIUM layout regression introduced by this PR (F-666B-A1). Everything the hero itself owns passes.

## OBS limits

- Collection-activity rows: no fee note has any, and no UI path creates them on this stack. Only the empty state was observed.
- Spec suite: not re-run here (not in the OBS-after brief).
- `send-override-dialog`: not exercised (no send in this OBS).
- LocalStack was recreated by the rebuild (see Stack).

## Files

- **Contact sheets:** `sheet-{draft,approved,sent-overdue,sent,sent-takeon,partial,paid,void}-alice.png` (full page, 4 variants), `sheet-hero-alice.png` (hero card × 8 targets × 4 variants), `sheet-carol.png`, `sheet-record-payment-alice.png`, `sheet-draft-lines-alice.png`, `sheet-consumers-alice.png`, `sheet-focus-alice.png`.
- **Crops:** `crop-defect-draft-lines-1024.png`, `crop-defect-draft-lines-1024-scrolled.png`, `crop-partial-lines-1024.png`, `crop-payment-history-1024.png`, `crop-payment-history-empty-1024.png`, `crop-collection-activity-empty-1024.png`, `crop-hero-overdue-1024.png`, `crop-hero-overdue-1440.png`, `crop-hero-takeon-1440-dark.png`, `crop-hero-draft-1440.png`.
- **JSON:** `measurements.json`, `sel-compat.json`, `console.json`, `discover-{pre,post}.json`, `sentinel.json`.
- **Defects:** `../found.md` → F-666B-A1…A3.
