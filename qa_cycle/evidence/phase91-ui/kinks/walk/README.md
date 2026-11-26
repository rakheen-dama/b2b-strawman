# 662A kinks walk — capture record (tasks 662.1 + 662.2)

Capture only. The register (`../../kinks-register.md`), triage and the PR come later.

| | |
|---|---|
| Head | `phase91/62A-kinks-register` @ **`16423ca4b`** (= `main`, 661B merged as `023ddb315`) |
| Stack | e2e mock-auth `:3001` / `:8081`, **fresh** from this worktree: `e2e-down` (volumes removed) → `docker builder prune -f` → `e2e-up` (rc=0, seed exited 0), 2026-11-25 ≈21:36 UTC |
| Data | `test:e2e:legal-lifecycle` **105 passed / 0 failed** (3.0 m), then `test:e2e:legal-depth-ii` **40 passed / 4 skipped / 0 failed** (1.9 m; the 4 skips are the opt-in null-prefetch probes). Tree restored afterwards (`git status` clean) |
| Personas | Alice (owner, `/mock-login` "Sign In") and Carol (member, mock-IDP `mock-auth-token` cookie) |
| Method | Playwright **library** scripts in `../scripts/` (never the MCP; no existing Chrome touched), `load` + `#main-content h1` + 1.8 s settle, never `networkidle`. Rail expanded (`sidebar_state=true`), `reducedMotion: reduce`, Africa/Johannesburg, theme via `localStorage["kazi-theme"]`. Helpers re-used by `require` from `../../661B/walk/scripts/{common,audit}.cjs` |
| Captured | 2026-11-25 21:47–22:27 UTC |
| Raw shots | `.tmp/662A-walk-raw/` in the worktree (gitignored, **not committed**): `<set>/<slug>__<persona>-<w>-<theme>.{png,json}` |
| Committed | `../contact/` 111 contact sheets, `../walk-index.json` (one line, 1 433 captures with probe data + `flags`), `../console.json` (one line), `../scripts/` (as executed; `00-stack-and-data.txt` logs every invocation) |

## Matrix: planned vs captured

| Set | Surfaces | Planned | Captured (PNG) | Note |
|---|---|---|---|---|
| S1 routes | 49 app/sub-page routes + 32 `?tab=` panels (+ 2 top-up detail pages) × 1024/1440 × L/D × owner/member | 648 + 16 | **668** | + `/settings` redirect (owner and member, 1440 light; lands on `?tab=general`) + member on the owner's matter (not-on-team branch, 1440 L/D) |
| S2 `$PA` | access-requests, billing, demo — light only | 9 | **9** | owner 1024 + 1440; member 1440 = expected 404 (`$PA/layout.tsx`) |
| S3 task sheet | matter `?tab=tasks&taskId=` × 8 combos + `/my-work?taskId=` × 1440 L/D × 2 | 12 | **12** | |
| S4 detail tabs | matter 22 + client 14 tabs × (owner 1024 L, 1440 L/D; member 1440 L/D) | 180 | **166** | 14 = tabs the member does not have (matter audit/financials/rates; client audit/financials/invoices/rates) — the page falls back, recorded as skip |
| S5 390 shell | /dashboard, /matters, /matters/[id] × closed / Sheet open / Sheet scrolled × L/D × 2 | 36 | **36** | |
| S6 390 settings | 32 tabs + 11 sub-pages + `/settings` × L/D × 2, family-switch probe on each | 176 | **176** | |
| S7 dialogs | open-only (`aria-haspopup="dialog"` harvest on every S1/S4 shot + a named-opener pass, 02c) | ≈ 240 | **288** (73 distinct dialogs) | never clicked inside; Escape-closed; 64 JSON-only records = named clicks that navigated / opened inline UI / timed out |
| S8 checks | K-11, K-07, K-05/I-02/I-05, I-06, K-06, K-09, K-10, K-12, X5, K-04/K-21/I-03/I-04, K-20 | 11 checks | **11** (32 PNG) | `.tmp/662A-walk-raw/checks.json`, copied into `walk-index.json` → `checks` |

**Route files: 58 / 58 accounted for.** 52 loaded directly = 40 route files outside settings (incl. the two top-up detail pages) + the `/settings` redirect + the 11 settings sub-pages; 5 family hosts via their 32 tabs; **1 skipped**: `/trust-accounting/reconciliation/lists/[listId]` — no completed reconciliation on a lifecycle stack (D-14: may be skipped). Tabs **32 / 32**, sub-pages **11 / 11** (`/settings/matters/checklists/[id]/edit`: all 5 checklists are PLATFORM, so it redirects to the read-only detail, as 661B W-9 — the edit form itself is unobserved; the S1/S6 captures labelled `checklists/[id]/edit` show the detail page). `walk-index.json` → `skipped` also lists `compliance/requests/[id]` and `billing-runs/[id]` from the read-only pass; both were then captured after the top-ups (`captures[].topup = true`).

## End-of-walk UI top-ups (D-14; through the UI, never SQL; after S1–S8)

1. **Data subject request** — `/compliance/requests` → "New Request" → client Acme Corp, Access Request, description → "Create Request" (id `92f1d962…`). Detail captured × 8.
2. **Fee run** — `/invoices/billing-runs/new` configure step only (period 2026-11-01…30) → "Next" (id `3247ec6c…`, status PREVIEW, 0 items; wizard not continued, so no fee notes generated). Detail captured × 8.
3. **K-10 150-char deposit — not done** (D-14 covers only the empty detail pages). Max trust reference on the stack: 23 chars.

The only other write in the walk is K-11's saved view (`662A K-11 view`, created through the Save View dialog after S1).

## K-11 reproduction (FD-11 condition) — **REPRODUCED**

**`K-11 REPRODUCED on 16423ca4b: Client th = 16 px at 1024`** (light and dark; all 8 Client name links measure **0 px**, 8 / 8 truncated). At 1440 the Client column is **76 px** light / **84 px** dark with every link 60–68 px and 8 / 8 truncated. On the default "All clients" view the same column is 203 px (1024) / 284 px (1440). Table 1 066 px in a 710 px container at 1024. Same as 642A's baseline (16 px / 0 px). Sheet: `contact/t1-check-K-11-saved-view-client-column.png`.

## Other scripted checks (numbers in `walk-index.json` → `checks`)

- **K-07** palette focus return: header button and sidebar pill, light and dark — palette opens, after Escape focus goes to `<body>`, **not** the trigger (4 / 4).
- **X5** tab switch with unsaved edits: typed `662A-X5` into "Tax Registration Number" (Settings › Firm › General), clicked "Roles", clicked back → value **lost** (empty). Reload confirms nothing was saved. → the X5 row applies.
- **K-12** forbidden-tab notice (member, `?tab=roles`): light `#3a4049` on `#f8f9fb` 9.92:1; dark `#cbd0d7` on `#14171c` 11.58:1 (no contrast failure; the hard-coded slate pair itself is the K-12 item).
- **K-04 / I-04** custom-fields row menu, focused items (firm light): "Edit" white on `#00776a` 5.46:1; "Delete" `#e7000b` on `#00776a` **1.144:1** (I-04 re-observed). The items carry no icon, so I-03 (icon 1.40:1) is not measurable on this menu. Fee-note accent buttons light: white on `#00776a` 5.46:1. **Dark (K-21 re-observed):** accent buttons white on `#00a38f` **3.165:1**; focused "Edit" white on `#00c2b1` **2.246:1**, focused "Delete" `#ff6467` on `#00c2b1` **1.286:1**.
- **K-06** `/matters` 1024 light: table **1 054 px** in a **710 px** container (`overflow-x: auto`).
- **K-09** `/my-work` 1440: owner task table 1 011 px in a 668 px card (scrolls); member 684 in 668.
- **K-05 / I-02 / I-05** 1024×768 rail collapsed: 17 rail items below the rail fold (FEES…Settings, footer); breadcrumb "E2E Test Organization" wraps (40 px tall) on the matter page, not on Home. 1440×900 expanded (default groups): only the footer items sit below the scroll area; with **every rail group opened**, owner: Team, Reports, Audit trail, Settings (+ footer) fall below the fold; member: Team, Settings (+ footer).
- **I-06** 768×1024 matter detail: `scrollWidth` **873** in light and dark (re-observed).
- **K-20** matter `?tab=activity`: selected tab text "Activity · Activity" (re-observed).
- **K-10** not reproducible on lifecycle data (see top-ups).

## Console (sink probe captured on all 12 persona×theme×viewport contexts: `probeCaptured: true`)

**0 hydration warnings, 0 page errors.** 29 errors, 41 warnings over 1 433 captures:

- 20 × "Failed to load resource: 404" — member on the gated `/invoices`, `/invoices/[id]`, `/invoices/collections`, `/invoices/unbilled`, `/compliance` (the page's own 404 response; these pages render **"Something went wrong"** error UI with no h1 for the member, not a not-found page) + 3 × member on `$PA/*` (expected 404).
- **4 × "Minified React error #441"** — member on `/reports/timesheet` (`/reports/[reportSlug]`), all 4 combos.
- 2 × 404 resource on `/settings/firm?tab=audit-trail` (owner 1440; 661B W-10 re-observed).
- 30 × tiptap "Duplicate extension names found: ['link', 'underline']" (template editor, new template, New Clause / New Template; K-17).
- 11 × Radix "Missing `Description` or `aria-describedby`" (template Preview dialog, fee-note "Add Tariff Items" dialog).

## Probe notes for triage (read before trusting a flag)

- `flags` in `walk-index.json` (and the manifest) are hints. The **dark slab** probe flags the dark-mode primary button (white fill by design, 149 of 189 hits) — ignore `button #ffffff` unless the image shows a light block that should not be there.
- `fold.hidden` counts rail **footer** items (Notifications, Platform Admin) as "below the fold" because it compares against the scroll area's bottom; use the S5 `before.groups` bottoms and the images.
- "no control reaches another settings family without the Sheet (K-02)" is set on every 390 settings capture where no in-page link/control leads to another family and the settings rail is hidden — i.e. K-02 holds on every 390 settings page (172 / 176; the rest are skips/sub-pages with links).
- `clipped/ellipsis` includes intended `truncate` cells; `narrow th` (< 40 px with text) is the K-11 pattern.

## Dialog checklist (brief § Org routes / 32 tabs) — not captured, with reason

Not opened because the trigger is a link, a menu or an excluded verb (open-only rule): Generate Document / Generate Statement of Account (menu/action), Download All Data, Send for acceptance, Approve Disbursement, Write off, Link Trust Transaction, Pay to the Fidelity Fund, `$PA` Approve/Reject, Uninstall Pack, Acknowledge POPIA, trust "Record Deposit/Payment/Transfer/Fee Transfer/Refund" on `/trust-accounting` and `/transactions` ("Record Transaction" is a link; Record Deposit/Payment were captured from the client and matter Trust tabs). Not present or inline on this data: Edit Matter, Edit Client, KYC verification on the client, Resolve Conflict (needs a check run = write), team Invite (inline "Send Invite" form), Member Detail sheet, Add Item (information request), Add Tariff Items on `/legal/tariffs`, Upload receipt / Edit Disbursement, Withdraw Investment, Log Time on `/my-work` and timesheet, diary row Edit / Postpone / Record Outcome (row menus), Set Data Protection Jurisdiction, Clone Clause, Add Rate, Set API Key, Execution Detail sheet, `$PA` billing detail sheet. The custom-fields row menu was opened by the K-04 check.

## Deviations

1. **Smoke run discarded.** A 60 s smoke run of `02-walk-routes.cjs` used the first `lib.cjs` revision, whose overflow probe listed 1×1 sr-only spans as clipped. Its raw output was deleted and `lib.cjs` was edited once (skip elements ≤ 2 px) **before** the real walk; every committed run used the committed `lib.cjs` (logged in `00-stack-and-data.txt`).
2. **`02b-dialogs-named.cjs` kept as executed with a bug** (tested `button.type`, which is `"submit"` for any `<button>` without a type attribute, so it found almost nothing); **`02c` is the fixed copy** (lesson 9: a fix is a new file). 02b's few records stay in the raw set.
3. Sheets: 17 of 111 exceed the 700 KB target (max 1.25 MB: 390 settings and dialog sheets); `contact/` totals 45 MB.
4. K-10 top-up not done (not covered by D-14); reconciliation monthly list skipped (D-14).
5. Carol has no client or matter-template id of her own on this stack: her `/clients/[id]` and `/settings/matters/templates/[id]` captures use Alice's ids (she sees the gated branch, e.g. "You don't have access to Clients").
   - **Correction (register K-62):** on `/clients/[id]` Carol does **not** see a gated branch — all 4 member captures are HTTP 200 with h1 "Acme Corp" and the full client page (the list `/clients` is the page that denies her).
6. **Re-run harvest on member skip tabs (found in CodeRabbit round 1).** On a resumed run, `02-walk-routes.cjs` reloaded and harvested the 14 member tab URLs that had been recorded as skips (fallback panel) and wrote their `S7/.done-*--{audit,financials,rates,invoices}__carol-*` markers. Every one of those harvests recorded **0** new dialogs and no S7 record names a skipped tab, so no dialog is attributed to a tab the member does not have. The other walk-script failure modes CodeRabbit raised did not occur in this run (checked on the raw set and the stack; see the PR comment).
