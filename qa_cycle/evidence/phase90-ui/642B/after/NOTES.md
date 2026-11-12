# 642B "after" OBS (task 642.10)

Captured 2026-11-12, final `obs-after.cjs` run 20:31:15Z, against the e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090). The stack was built from `phase90/42B-client-hero` @ `3c04cb3bb` and then ran `test:e2e:customers`, `legal-lifecycle` and `legal-depth-ii` (logs: `e2e-*.txt`, captured by the orchestrator). It was not torn down or reseeded for this capture.

- **Driver:** `obs-after.cjs`, a Node Playwright library script (`chromium.launch()`, nvm node v20.11.1, `@playwright/test` 1.59.1 from the worktree's `frontend/node_modules`). Playwright MCP was not used because the user's live Chrome holds it.
- **Raw data:** `obs-after.json` (compact). It has `loads[]` (DOM facts, tiles, console, HTTP status per load), `lists`, `probes` (GET-only backend reads per user), `smartActionClicks` and `dialogs`.
- **Theme:** set with `localStorage['kazi-theme']` and a reload. `<html class>` matched the requested theme on all 19 loads.
- **Waits:** the same as before (`load` → first `h1` → active tabpanel → 2.5 s). `?tab=trust` loads also wait for `[data-testid=trust-balance-primary]`.
- **Read-only SQL:** `SELECT`s on `tenant_7d218705360b`, used for ids and for cross-checks only. No `INSERT` or `UPDATE` was run.

## Script diff vs `../before/obs-before.cjs`

`obs-after.cjs` is a copy of `obs-before.cjs` (237 → 336 lines; 19 lines removed or changed, 118 added). These are the hunks (`diff before/obs-before.cjs after/obs-after.cjs`):

| hunk | change |
|---|---|
| `1,7c1,8` | header comment |
| `29,30c30,33`, `32c35` | `CLIENTS`: this stack's ids. Added `vis` (Carol is a member of 1 of its 2 ACTIVE matters) and `full` (all four tiles non-zero). `VIEWPORTS` gained 390 and 768. |
| `43,45c46,55` | `PLAN`: the after matrix below |
| `86,87c96,97`, `89,90c99,105` | `probes`: the fallback trust-account id; loops over all `CLIENTS`; adds `GET /api/matters?customerId=&status=ACTIVE` (the hero's Open-matters source) and compacts the ledger and debtor reads |
| `135a151,173`, `137a176,182` | `domFacts`: hero tile dump (label/value/hint/state/box/overflow, `tilesPerRow`), the `TrustBalanceCard` text, `innerWidth`/`clientWidth` |
| `203a249,250`, `209a257` | `?tab=trust` waits for `TrustBalanceCard`; one tile summary log line per load |
| `215c263` | smart-action clicks limited to `rich` and `fx`, as before |
| `229a278,308` | New Client and Edit Client dialogs (Alice, 1024, light + dark, opened only and closed with Escape) |
| `233c312` | writes `obs-after.json` |
| `237a317,336` | `dialogFacts()` (from 641B's obs-after, trimmed) |

`probe-overflow.cjs` (+ output `probe-overflow.out.txt`) is a second read-only script for the 390/768 overflow diagnosis below.

## Data setup log (`setup-usd-feenote.cjs`, UI only, Alice)

The USD fee note from the before capture is not on this freshly built stack. `setup-usd-feenote.cjs` here is `../before/setup-usd-feenote.cjs` with only three things changed: the `ACME` id, the `WEBSITE_REDESIGN` id and the screenshot dir (`/tmp/642b-setup-after`). Each stage ran **exactly once**, and each result was checked with a read-only SELECT before the next stage started. Nothing was retried and nothing was duplicated.

| stage | result (verified by SELECT) |
|---|---|
| `rate` | billing rate `6e939350-…` Alice × Acme, **USD 100.00**. The hero rendered on the Tariffs page as the stage's panel text. |
| `task` | task `d3f95ebd-…` "642B USD fee note probe" on Website Redesign |
| `time` | the bounded preview check passed: "Billing rate: US$100,00/hr(client override) 1h x US$100,00 = US$100,00". Time entry `6a6e7b74-…`, 60 min, snapshot 100.00 **USD** |
| `feenote` | "1 of 1 selectable", pre-selected. The dialog closed and the page stayed on `?tab=invoices`. Draft `1da48c69-861c-…`, USD 115.00 |
| `send` (`1da48c69-861c-4840-9f2d-422eddc3cc60`) | **INV-0006, SENT, USD 115,00, due 12 Dec 2026** |

Each stage logged one console error, `MockAuthContextProvider: failed to fetch userinfo`, during the login → dashboard navigation. This is the same as in the before capture.

## Clients used (org default currency ZAR; primary trust account `11bd21ba-…` "Mathebula & Partners Trust Account")

| key | id | client | data (SELECT + GET probes) |
|---|---|---|---|
| rich | `14fcc109-3ff2-4abc-a66e-854bbd5761c1` | Sipho Ndlovu mwdzyk4n12b3 | ONBOARDING. 2 linked matters (1 ACTIVE "transition repro", 1 CLOSED "Depth II"). Ledger R 3 750,00. No fee notes. The same profile as before's Sipho. |
| fx | `b64b3f6f-9b1f-4f07-bbc4-f7595fc7cf14` | Acme Corp | ACTIVE. 1 ACTIVE matter. INV-0001 ZAR 8 625,00 SENT, INV-0006 USD 115,00 SENT, ZAR 3 450,00 draft. No ledger card. |
| vis | `d4bc5234-ac81-4148-8b2c-ad83bfd8ee5d` | Sipho Ndlovu mwdzupuo0b2c | ACTIVE. 2 ACTIVE matters, and Carol is a `project_member` of 1 of them ("Ndlovu v RAF"). INV-0003 ZAR 5 482,63 SENT. |
| full | `7643c065-b4ce-4cd2-adec-eb7f340957dd` | Moroka Family Trust mwdzupuo0b2c | ONBOARDING. 1 ACTIVE matter. Ledger R 241 521,50. INV-0002 PARTIALLY_PAID, balance due R 125,00. |

## Hero tiles: per user and client (identical across viewport and theme within each pair)

| user / client | Open matters | Trust balance (hint) | Outstanding (hint) | FICA (hint) |
|---|---|---|---|---|
| Alice / rich | **1** | **R 3 750,00** (Mathebula & Partners Trust Account) | R 0,00 (0 overdue) | In progress (clock icon) |
| Alice / fx | **1** | R 0,00 (Mathebula & Partners Trust Account) | **R 8 625,00** (**Excludes 1 fee note in other currencies**) | Done (Completed 12 Nov 2026) |
| Alice / full | 1 | **R 241 521,50** (…Trust Account) | **R 125,00** (0 overdue) | In progress |
| Alice / vis | **2** | R 0,00 | **R 5 482,63** (0 overdue) | Done (Completed 12 Nov 2026) |
| Carol / rich | **0** | R 3 750,00 (…Trust Account) | *omitted* | In progress |
| Carol / vis | **1** | R 0,00 | *omitted* | Done (Completed 12 Nov 2026) |
| Carol / fx | 0 | R 0,00 | *omitted* | Done (Completed 12 Nov 2026) |

All tiles are in `state=ready`, none is a link, and no value or hint overflows its tile at any captured width.

**Open matters is correct for all 7 user/client pairs.** Expected value = `GET /api/matters?customerId=…&status=ACTIVE` as that user, cross-checked against SQL:

- Alice (owner): rich 1, fx 1, full 1, vis 2. These equal the SQL counts of ACTIVE linked matters, so the CLOSED matter is excluded.
- Carol (member): rich 0, fx 0, vis 1. That is only "Ndlovu v RAF", the matter she belongs to (`project_members`), so ADR-346 visibility holds.
- The tile is not the old status-less `linkedProjects` count (before F1). For example, Alice / rich shows 1 while `client-context` says "2 matters".

**Trust is correct.** The tile equals `TrustBalanceCard` on `?tab=trust`:

- Alice / rich: tile R 3 750,00, card R 3 750,00
- Alice / full: tile R 241 521,50, card R 241 521,50
- Carol / rich: tile R 3 750,00, card R 3 750,00

These also match the ledger GETs. Where the client has no ledger card, the GET returns 200 with balance 0 and the tile shows R 0,00 (before F2).

**Outstanding is correct.** It equals the default-currency sum of the debtor rows: rich `[]` → R 0,00; full 125 → R 125,00; vis 5 482,63. All `daysOverdue` values are -30, so the hint reads "0 overdue".

## Foreign-currency client (Acme)

The debtor read (`GET /api/collections/debtors/b64b…`, Alice, 200) returned `outstandingInvoices` = `[{INV-0001, balanceDue 8625, ZAR, daysOverdue -30}, {INV-0006, balanceDue 115, USD, daysOverdue -30}]`.

| load | Outstanding value | hint |
|---|---|---|
| Alice / fx 1440 light (`alice-fx-1440-light.png`) | **R 8 625,00** | **"Excludes 1 fee note in other currencies"** |
| Alice / fx 1024 dark (`alice-fx-1024-dark.png`) | R 8 625,00 | the same |
| Alice / fx `?tab=invoices` 1440 light (DOM only) | R 8 625,00 | the same |

- The value is the ZAR sum only. It is not the cross-currency R 8 740,00 that before's NOTES warned about.
- The exclusion hint replaces the overdue hint, as specified.
- The ZAR draft (3 450) is not counted, because the debtor read lists only outstanding (sent) fee notes.

**PASS (observed).**

## Carol (member)

- Tiles: **Open matters 0** (rich), **Trust balance R 3 750,00** (VIEW_TRUST + module), **no Outstanding tile** (no INVOICING), **FICA In progress**. There are 3 tiles: one row of 3 at 1440, and 2+1 at 1024.
- On `vis` her Open matters is 1, while Alice's is 2 (visibility-filtered).
- Header actions: "Activate Client" and "More actions", with no Generate Document (unchanged).
- The backend log for the run window (20:31–20:35Z) shows **4 × debtors 403**, all from this script's own Carol GET probes (one per client). Carol's 8 client-page loads made **no** debtor call, which shows the page's INVOICING gate holds.
- Clients list for Carol: "You don't have access to Clients", 0 rows. Unchanged.

## Before → after layout (Alice / Carol)

Header-card height and tab-bar y are page coordinates. Before = `../before/obs-before.json` (the same client profiles on the 642A stack).

| user / client | viewport | header h before → after | tab bar y before → after | tiles per row | tile w | overflow | scrollWidth / vw | console |
|---|---|---|---|---|---|---|---|---|
| Alice / rich | 1024 light | 215 → 463 | **375 → 623** | 2+2 | 333 | no | 1024/1024 | 0 |
| Alice / rich | 1024 dark | 215 → 463 | 375 → 623 | 2+2 | 333 | no | 1024/1024 | 0 |
| Alice / rich | 1440 light | 187 → 305 | **347 → 465** | 4 | 265 | no | 1440/1440 | 0 |
| Alice / rich | 1440 dark | 187 → 305 | 347 → 465 | 4 | 265 | no | 1440/1440 | 0 |
| Alice / rich | 390 light | — → 511 | — → 671 | 2+2 | 152 | no | **515/390** | 0 |
| Alice / rich | 768 light | — → 479 | — → 639 | 2+2 | 205 | no | **779/768** | 0 |
| Alice / fx | 1440 light | 159 → 279 | 319 → 439 | 4 | 265 | no | 1440/1440 | 0 |
| Alice / fx | 1024 dark | — → 389 | — → 549 | 2+2 | 333 | no | 1024/1024 | 0 |
| Alice / full | 1440 light | — → 277 | — → 437 | 4 | 265 | no | 1440/1440 | 0 |
| Alice / vis | 1440 light | — → 307 | — → 467 | 4 | 265 | no | 1440/1440 | 0 |
| Carol / rich | 1024 light | 187 → 395 | 347 → 555 | 2+1 | 333 | no | 1024/1024 | 0 |
| Carol / rich | 1440 light | 187 → 305 | 347 → 465 | 3 | 357 | no | 1440/1440 | 0 |
| Carol / rich | 1440 dark | 187 → 305 | 347 → 465 | 3 | 357 | no | 1440/1440 | 0 |
| Carol / vis, Carol / fx | 1440 light | — / 159 → 307 / 279 | — / 319 → 467 / 439 | 3 | 357 | no | 1440/1440 | 0 |

- The tab bar moves down by the tile strip: +118 px at 1440 and +248 px at 1024 (Alice).
- At 1024 the 4-tile strip is 2×2 and the actions wrap under the identity block. This is DetailHero's documented rule (ADR-373: 4 tiles = 2×2 below `@3xl`; the card content is about 680 px wide at 1024). The rule is shared with the 641B matter hero and is not specific to this slice.
- Carol's 3 tiles at 1024 render 2+1, with an empty cell beside FICA (`TILE_COLUMNS[3] = grid-cols-2 @3xl:grid-cols-3`). This is shared DetailHero behaviour and is noted as information only.

## Responsive probe 390×844 / 768×1024 (Alice / rich, light): **FAIL, page scrolls horizontally**

The two screenshots are `alice-rich-390-light.png` and `alice-rich-768-light.png`. Diagnosis: `probe-overflow.cjs` → `probe-overflow.out.txt`. It hides parts in the live DOM and re-measures, and compares against the matter page (641B, on main) and the Clients list.

| viewport | page scrollWidth | hero hidden | tab bar hidden | hero actions allowed to shrink + tab bar hidden |
|---|---|---|---|---|
| 390 | **515** | 515 | **412** | **390** |
| 768 | **779** | 779 | **768** | 768 |

- **768: the only cause is the grouped tab bar** (the "Activity" trigger ends at x = 779). The hero fits (hero `scrollWidth` 462 ≤ width 464). The tab-bar components are not touched by this PR (`git diff --stat main...HEAD` has no `grouped-tab-bar` / `customer-grouped-tabs` / layout file). **This overflow predates 642B.** The matter page overflows in the same way at 768 (873/768).
- **390: two causes.**
  1. The same tab-bar overflow (515), which predates 642B.
  2. **The hero's actions row** (`[data-slot=detail-hero-actions]`, x 37 → 412, hero `scrollWidth` 395 > card width 358). This one is **NEW for the client page (introduced by this PR)**. DetailHero's actions wrapper is `flex shrink-0 flex-wrap`, so it sizes to its single-line content ("Activate Client · Generate Document · ⋯", 375 px) and cannot shrink.

  Removing `shrink-0` in the live DOM only (and restoring it after) shrinks the hero to `scrollWidth` 356 and, with the tab bar hidden, brings the page to exactly 390. That confirms the root cause.

  Main's client header stacked with `flex-col` below `sm` and had no `shrink-0` on its actions, so its actions would wrap at 390. This is read from main's source (`git show main:frontend/components/customers/client-header-card.tsx`). It is not observed, because this stack only runs the branch.

  The same DetailHero rule already applies on the 641B matter page at 390 (its `detail-hero-actions` right edge is 395 > 390). The root cause is therefore in the shared `frontend/components/shared/detail-hero.tsx` (`shrink-0` on the actions wrapper), not in the client-specific code.

  While the tab-bar overflow remains, the hero's 22 px is hidden inside it, so the visible effect at 390 is a horizontal scroll that the page already had. It is still a real regression of the hero itself. No code was changed here; this is for the orchestrator to triage.

## Dialogs (Alice, 1024, light + dark; opened only and closed with Escape)

| dialog | title / description | section headings | labels (in order) | buttons |
|---|---|---|---|---|
| New Client (Clients list → "New Client") | "Create Client" / "Step 1 of 2 — Add a new client to your organization." | Address, Contact, Business Details | Name, Type, Email, Phone (optional), Tax Number (required to send a fee note; collectable later), Notes (optional), Address Line 1 (optional), Address Line 2 (optional), City, State / Province, Postal Code, Country (required for activation), Contact Name, Contact Email, Contact Phone, Registration Number, Entity Type, Financial Year End | Cancel, Next, Close (×) |
| Edit Client (Acme → ⋯ → "Edit Client") | "Edit Client" / "Update client information." | Address, Contact, Business Details | Name, Type (not editable), Email, Phone (optional), ID Number (optional), Notes (optional), Address Line 1 (optional), Address Line 2 (optional), City, State / Province, Postal Code, Country (required for activation), Contact Name, Contact Email, Contact Phone, Registration Number, Tax Number (required for activation), Entity Type, Financial Year End | Cancel, Save Changes, Close (×) |

- Light and dark were identical in text. Both footers now have a 1 px top border (the new `border-t pt-4`).
- **Labels and buttons are unchanged vs before.** The before capture did not open these dialogs, so the comparison is against main's source: `git diff -w main...HEAD -- frontend/components/customers/{create,edit}-customer-dialog.tsx` changes only wrapper `div`s, classes (`slate-*` → `muted-foreground`, section `border-t` → `space-y` grouping, footer `border-t`) and the JSX line-splitting of "Phone/Notes/ID Number (optional)". No visible string, label, button or field was added, removed or renamed.
- Screenshots: `alice-dialog-{create,edit}-1024-{light,dark}.png`.

## Console

- **0 browser console errors, 0 warnings and 0 `pageerror` events on all 19 client-page loads and all 4 dialog opens**, so there are no hydration messages.
- The only console entry is the existing `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch`. It appeared once per user, on the Clients-list load right after mock login (the userinfo fetch is aborted by the navigation). It is the same message the before capture saw in exploratory runs. **Not new.**
- Backend WARNs in the run window: 8 × `/api/integrations/xero/connection` 403 and 8 × `/api/clients/<id>/unbilled-summary` 403 from Carol's 8 client-page loads. Both are swallowed by the page; this is before F5, unchanged. There were also 4 × debtors 403 from the script's own Carol probes. **No new 403 from the hero reads** (`/api/matters`, ledger, fica-status).

## SEL-COMPAT after: **PASS. No spec edited, no role changed.**

Full output is in `sel-compat-after.txt`.

- `git diff --stat main...HEAD -- frontend/e2e` is empty, so `frontend/e2e` is byte-identical to main.
- Grep hit counts equal before: `client-header-card` 2 (annotation strings), `client-name` 0, `customer-status-badge` 0, `lifecycle-badge` 13, `client-contact` 0, `client-context` 0, `client-header-actions` 1, `smart-primary-action` 3, `client-header-field(s)` 0. The hit locations are unchanged.
- Live DOM: 19/19 loads pass every check:
  - exactly one `h1`, which is `data-testid=client-name` and whose text is exactly the client name, with nothing appended (the badges and tiles sit outside the h1)
  - `client-header-card` present
  - `lifecycle-badge` text is exactly the lifecycle label ("Onboarding" / "Active")
  - `customer-status-badge` absent for ACTIVE records (R-11, as before)
  - `client-contact` and `client-context` present
  - `client-header-actions` present, with `smart-primary-action` as its first control
  - Generate Document inside `client-header-actions` for Alice, absent for Carol
- Header facts before → after are identical per profile (Part 2 of the txt), apart from the client-name suffixes of the fresh stack.
- `smartActionClicks`: "Activate Client" (rich) and "Edit" (fx) still open nothing for both users. This is before F3, unchanged and out of scope.

## Line count

```
$ wc -l "frontend/app/(app)/org/[slug]/clients/[id]/page.tsx"
     380 frontend/app/(app)/org/[slug]/clients/[id]/page.tsx
```

The page went from **756 → 380 (< 550 ✓)**. For reference, the extracted `client-page-data.ts` is 528 lines, `hero-tiles.ts` 216 and `client-header-card.tsx` 228.

## Findings

| id | finding | class |
|---|---|---|
| A1 | **390 px: the hero's actions row overflows the card by about 22 px** (actions x 37 → 412 at vw 390). Root cause: `shrink-0` on DetailHero's `[data-slot=detail-hero-actions]` wrapper. Removing it in the live DOM fits the hero (356 ≤ 358). Main's client header wrapped its actions there (code reading). The same DetailHero rule affects the 641B matter page at 390. | **NEW on the client page (this PR)**. Root cause in shared `detail-hero.tsx` (641A/641B). |
| A2 | 390 / 768 px: the grouped tab bar overflows the page (515/390, 779/768), so the 642.10 "scrollWidth == viewport" check fails at both widths. Tab-bar code is untouched by this PR; the matter page and its tab bar show the same. | Predates 642B |
| A3 | 1024 px: 3 tiles (Carol) render 2+1 with an empty cell; 4 tiles render 2×2 and push the tab bar down 248 px. | Information. DetailHero's documented column rule (ADR-373), shared with 641B. |
| A4 | `client-context` "2 matters" (all linked) sits next to Open matters "1" (Alice / rich). | Information. Before F4 wording, which the slice left as is. |

Before F3 (dead smart action) and F5 (member 403s) still occur and are unchanged. No defect was found in the four tiles' values, gating, ordering or foreign-currency rule.
