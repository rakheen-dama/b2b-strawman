# 665D OBS-after: Settings › Fees & Trust (6 tabs) on PR #1873 @ `5d3430e06`

- **When:** 2026-11-26, 20:42Z to 20:56Z (captures 20:43Z to 20:52Z).
- **Verdict: PASS.** No defect introduced by 665D was observed. F-665D-2 is **closed**. F-665D-1 is **open and worse at 1024**, as the routing note in `../found.md` predicted (orchestrator ruling 1 → 669A / 670A). Numbers are below.
- **Stack:** the same running e2e mock-auth stack as OBS-before (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. Only the frontend container was rebuilt. The backend and its data were not touched, so OBS-before's roles, tax rates and settings were still in place.
- **Browser:** the Playwright library (headless Chromium v1.59.1), DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded, theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code was changed and no SQL was run. Reads went through the API (GET); every write went through the UI.
- **Raw files:** the raw tiles are in `/tmp/665D-after-raw/` (outside the repo). 16 PNGs are committed.

## Provenance (step 1)

- **Worktree:** `/Users/rakheendama/Projects/2026/worktree-epic-665D`, branch `phase91/65D-settings-fees`.
  - HEAD = `5d3430e064fb0a0e1ac885e6c22d35b3610efc21`.
  - `git status --short frontend packages` was **empty** before the build.
- **Rebuild:** `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend`, run from the worktree with nohup and a done-marker. It ended with `exit=0`.
- **`docker inspect e2e-frontend`:**
  - Created **`2026-11-26T20:42:33.402Z`**
  - image `sha256:2df6072be3d3634994088e4c75dee67daac2b25391828fc555bcf8fbcb2cf6a5`
  - `com.docker.compose.project.working_dir` = **`/Users/rakheendama/Projects/2026/worktree-epic-665D/compose`**
  - Before the rebuild it was the 665C build (`d20ff9430acd…`, `worktree-epic-665C/compose`).
- **Health:** healthy, and `GET /` → 200 at 20:42:45Z.
- **The new build is the one served:** the h1s render the K-124 text ("Rates", "Tax", …).
- **Baseline:** `discover-pre.json` (20:43Z) matches OBS-before's end state: 6 tax rates, "OBS 665D Rate" inactive, billing / cost rates `[]`, Carol is Member, both 665D roles have 0 members, and `bulk_billing` is ON.

## What was replayed (in order)

Scripts run unchanged from `../before/scripts/` with `PHASE=after`:
- `00-discover pre`
- `02-roles create` (idempotent: both roles exist; ids `ed1125a5-…` and `ed18669b-…` with capabilities `[FINANCIAL_VISIBILITY]` / `[INVOICING]`)
- `02-roles assign "665D OBS Financial"`, `"665D OBS No Financial"` and `Member`
- `03-modules`
- `06-overflow-probe` and `06b-tabstrip-probe`
- `00-discover post`
- `05-assemble`

Copies in `after/scripts/`, each with a header that lists its only changes:

| Script | Change | Why |
|---|---|---|
| `01-capture.cjs` | The lib require path, plus the **K-124 heading names** in `selCompat`: `/^Rates & Currency/` → `/^Rates/`, "Rates & Currency" → "Rates", `/Rates & Currency/` → `/Rates/`, "Tax Settings" → "Tax", "Time Tracking" → "Time recording", "Batch Billing" → "Fee runs". | These mirror the 6 e2e lines PR #1873 changed. Nothing else changed. |
| `04-saves.cjs` | (1) The lib path. (2) MSG also looks for the message in the `card-footer` / `settings-action-bar` status slot; it tries the old sibling lookup first. (3) The new tax-rate row is the same-name row that still has a Deactivate button. (4) The tax-rate name comes from `TAX_RATE_NAME`. | (2) is structural: 665D moved the message out of the button's flex row on purpose. (3) and (4) are about the data, see the run notes below. |
| `04b-taxdup-probe.cjs`, `04c-deactivate-leftover.cjs` | One-off scripts, kept as executed. | See the 04-saves run notes. |
| `07-dark-probe.cjs`, `07b-trust-dialog.cjs`, `08-dark-assemble.cjs` | **New.** | Step 4 dark-mode / contrast / nowrap checks, the trust and deactivate dialogs, and the sheet. |

**04-saves run notes** (the brief expected "one more inactive OBS 665D Rate"; the backend does not allow it):
1. **Run 1 (20:46Z), with the name "OBS 665D Rate":**
   - The Add Tax Rate dialog stayed open with **"A tax rate with this name already exists."** (`04b`; last tile of `sheet-dark-and-dialogs.png`). Name uniqueness covers inactive rates too.
   - Its first three saves (currency, billing rate add / edit / delete, Tax Configuration) had already succeeded. The rate was deleted again.
2. **Run 2 (20:47Z), with "OBS 665D Rate after":**
   - The create worked.
   - Then the script crashed before Deactivate: a script bug, a closure variable inside `evaluateAll`, since fixed.
   - The rate was left **active**. `04c` deactivated it through the UI at 20:48:01Z, so it ends **Inactive**.
3. **Run 3 (20:48Z), with "OBS 665D Rate after-2":** a clean, complete replay. This is the `saves.json` committed here.

## Per-check results (step 3)

| Check | Result |
|---|---|
| **Tab visibility / gating** (4 personas × 7 URLs × 1024 / 1440 × light / dark, 100 page loads) | **Identical to before.** The script compared `selectedTab`, `tabs`, `rail`, `notice`, h1 count, `main` counts, HTTP status and final URL: **0 differences**. Alice sees Rates \| Tax \| Time recording \| Fee runs \| Collections \| Trust accounting. Carol, the holder and the lacker see Rates only. Forbidden deep links show "You don't have access to {Tab} — showing Rates instead." The holder gets the full Rates UI (F-665D-7 unchanged); Carol and the lacker get the in-page denial. |
| **Exactly one h1, with K-124 text** | **1 h1 in `#main-content` on every page, persona and variant.** Rates & Currency → **Rates**, Tax Settings → **Tax**, Time Tracking → **Time recording**, Batch Billing → **Fee runs**, Collections → Collections, Trust Accounting Settings → **Trust accounting**. The h1 sits in `data-testid="page-header"` (a new testid). Tab titles are unchanged ("Fees & Trust — {Tab} · Settings \| Kazi"). |
| **Headings / sections** (Alice 1440 light) | Rates: h2 Default Currency; the Cost tab h3 "Cost Rates" = 1 after the click. Tax: h2 Tax Configuration, h2 Tax Rates. Time recording: h2 Time Reminders, h2 Default Disbursement Markup. Fee runs: h2 Fee note defaults, h2 **Fee run processing** (was "Batch Billing Configuration"). Collections: h2 Overdue fee-note reminders. Trust: **h2 Trust Accounts / Approval Settings / Reminder Settings** (before: no h2; the titles were CardTitle divs). Fee-run labels: "Process in the background above (fee notes)", "Emails per second", "Default fee-run currency", placeholder "e.g. ZAR". Trust status badge **"Active"** (was `ACTIVE`). These match the PR body. |
| **Testids / controls / buttons** (Alice 1440 light, full probe) | **0 testids lost** on any tab; only `page-header` is new. Control ids are unchanged; only the 3 K-124 fee-run labels changed. 0 buttons lost. The tax-rate counts went 4 → 6 because OBS-before added 2 rows. |
| **SEL-COMPAT** (`sel-compat.json`, 1024 light, 4 personas) | **Every selector has the same count as before**, with the K-124 heading renames. The only difference is `tax-rate-row` 4 → 6 (data). Carol's row as holder is still `billing-rate-665d-obs-financial` (0 for `billing-rate-member`, as before). |
| **Spec run** (from the worktree, `frontend/` @ `5d3430e06`, 20:51Z) | `rate-cards.spec.ts` + `tax-settings.spec.ts`: **6 passed, 0 failed** (3.1 s). It created tax rate "E2E Rate 2zch" (active), as the spec does. `git status` was clean after (only `after/`, untracked). |
| **Saves** (`saves.json`, run 3) | **Every model saves with the same message text.** Details are in the next table. |
| **Dialogs** | Billing rate Add / Edit / Delete: OK (04 and 07). Tax rate Add / Deactivate: OK. The **Deactivate Tax Rate** alertdialog was also opened and cancelled ("E2E Rate v0n1" is still Active). **Add Trust Account**: the dialog "Add Trust Account" opened with Cancel / Create Account / Close, closed on **Cancel**, and the account count stayed 1 → 1 (API). |
| **`docHOverflowPx`** | **0 on every page, persona, viewport and theme** (max over all 100 variants = 0). |
| **Table scrollers at 1024** | See the measurements below. F-665D-2 is **closed**; F-665D-1 is **still there**. |
| **EmptyState** | None rendered, as before (every list has rows). |
| **Console** | **0 errors, 0 warnings, 0 hydration messages** over the 100 capture loads plus the roles, modules, saves and dark-probe runs (`console.json`, `dark-probe.json` → `consoleTotals`). |
| **`bulk_billing` off** (step 5) | The UI switch went OFF at 20:48:30Z. At 1440 and 1024, `/settings/fees` shows tabs and rail **Rates \| Tax \| Time recording \| Collections \| Trust accounting** with **no Fee runs**. `?tab=fee-runs` shows Rates plus "You don't have access to Fee runs — showing Rates instead." (F-665D-6 unchanged). `?tab=trust-accounting` still renders "Trust accounting". **Restored ON** at 20:48:38Z; the API GET then showed `bulk_billing:true` and the same `enabledModules` as `discover-pre`. |
| **`trust_accounting` off** | **Skipped.** There is no UI switch for it (`#feature-switch-trust_accounting` count 0). See F-665D-5. It is pinned only by vitest (`fees-family.test.tsx`, `settings-nav.test.ts:55`). |

### Saves (Alice 1440 light)

| Model | Message (before → after) | Placement after | API |
|---|---|---|---|
| Rates › Default Currency (instant) | "Default currency updated." → **same** | `<p>` under the selector, in the section body. Class `text-green-600` → `text-success-foreground`. No role. | ZAR → ZAR |
| Rates › billing rate dialogs | none → none | "Add Rate" / "Create Rate" (353 ms); "Edit Billing Rate" / "Save Changes" (713 ms); "Delete Billing Rate" / "Delete" (388 ms). No toast. | `[]` → 1 → `[]` |
| Tax › Tax Configuration (per-card) | "Tax settings updated." → **same** | **card-footer status slot**, left of the button. No role (F-665D-3). | unchanged ("Tax Number" since before) |
| Tax › tax rate dialogs | none → none | "Add Tax Rate" / "Create" (629 ms); "Deactivate Tax Rate" / "Deactivate" (406 ms); the row stays as Inactive with Edit only | + "OBS 665D Rate after-2" (inactive) |
| Time recording (page-level) | "Time tracking settings updated." → **same** | **settings-action-bar** status slot, below the sections | unchanged |
| Fee runs (page-level) | "Batch billing settings updated." → **same** | settings-action-bar status slot | unchanged |
| Collections (per-card) | "Collections settings updated." → **same** | card-footer status slot, still `role="alert" aria-live="polite"` | unchanged |

In every Save Settings model the button is enabled before the click and `disabled` during the save, as before.

## Dark-mode checks (step 4; `07-dark-probe.cjs` → `dark-probe.json`, `sheet-dark-and-dialogs.png`)

**(a) Destructive icons**, measured at 1440 and 1024 with identical values. The resolved `--destructive` is `lab(48.45% 77.43 61.55)` = rgb(231, 0, 11) in light and `lab(63.71% 60.75 31.31)` = rgb(255, 100, 103) in dark. Slate-400, the 665C failure colour, is rgb(144, 161, 185).

| Icon | Light: svg colour / contrast on its background | Dark: svg colour / contrast |
|---|---|---|
| Tax "Deactivate {name}" (`DESTRUCTIVE_GHOST`) | button and svg **rgb(231,0,11)** = token, 4.72:1 on rgb(253,254,255) | button and svg **rgb(255,100,103)** = token, 6.22:1 on rgb(20,23,28) |
| Rates "Delete billing rate for Carol Member" (plain button, svg `text-destructive`) | svg **rgb(231,0,11)**, 4.72:1 (the button's own colour is slate-700; it carries no text) | svg **rgb(255,100,103)**, 6.22:1 (the button's own colour is slate-400; the svg overrides it) |
| Deactivate alertdialog action (`variant="destructive"`, not edited) | white on rgb(231,0,11), 4.77:1 | same, 4.77:1 |

**(b) nowrap clipping at 1024:**
- **Tax table:** 446/446 light (454/454 dark), with 37 of 45 cells nowrap.
  - Name cells are `whitespace-normal`: "OBS 665D Rate after-2" wraps to 2 lines.
  - **0 cells past the visible edge and 0 prose clipped.** The only "clipped" nodes are the 1 px `sr-only` Edit / Deactivate labels, which is intended.
- **Rates tables:** every cell is nowrap (24/24), and no text is clipped inside a cell. The member name and email `truncate` didn't trigger.
  - Columns are pushed past the scroller instead: EFFECTIVE FROM is cut 50 px (53 px for Cost; 42 / 45 px in dark), and EFFECTIVE TO and ACTIONS are fully hidden. This is F-665D-1.

**(c) Contrast** (composited on the canvas):

| Item | Light | Dark |
|---|---|---|
| Tax Status "Active" (`text-success-foreground`) | 7.27:1 | 11.85:1 |
| "Inactive" (muted) | 7.57:1 | 6.74:1 |
| Save message "…updated." (`text-success-foreground`) | 7.27:1 | 13.21:1 |
| Badge neutral (Exempt, Single, Monthly …) | 6.76:1 | 5.84:1 |
| Badge success (Default, Primary, Trust **"Active"**) | **4.496:1** | 6.46:1 |

- The light success badge is **K-13**, already registered and backlogged by the founder on 2026-11-26: `packages/ui/src/badge.tsx:18`, `bg-green-100 text-green-700`. `packages/` is unchanged in this PR (`git diff --stat 1a867a784..5d3430e06 -- packages` is empty), so it is pre-existing and not a 665D finding.
- Everything else is ≥ 4.5:1.
- The colours that 665D tokenised (`text-success-foreground` and the destructive token) pass in both themes.

## Measurements before → after (Alice, 1024×768)

| Measure | Before (`1a867a784`) | After (`5d3430e06`) |
|---|---|---|
| `<main>` / tab panel width, light (dark) | 760 / 496 (768 / 504) | 760 / 496 (768 / 504), unchanged |
| `docHOverflowPx`, all tabs × personas × themes | 0 | **0** |
| Rates › Billing scroller (light / dark) | 494/725 / 502/725 | **446/725 / 454/725** |
| Rates › Billing, cut at scrollLeft 0 | EFF. FROM 2 px; EFF. TO + ACTIONS hidden; 3/3 actions out | **EFF. FROM 50 px**; EFF. TO + ACTIONS hidden; 3/3 actions out (F-665D-1, open) |
| Rates › Cost scroller / cut | 494/728; EFF. FROM 5 px; 3/3 out | **446/728; EFF. FROM 53 px**; 3/3 out |
| Tax › rates scroller (light / dark) | 446/521, static / 454/519 | **446/446, relative / 454/454** |
| Tax › cut / actions out of view | ACTIONS 75 px cut; 8/8 out | **none; 0/12** (F-665D-2 **closed**) |
| Fees tab strip (ScrollArea viewport) | 496/538 | 496/538, unchanged (shared `SettingsFamilyTabs`) |
| h1 per page | 1 | 1 |
| Console errors / hydration | 0 / 0 | 0 / 0 |
| Rates / Tax tables at 1440 | 862/862 / 814/814 | 814/814 / 814/814 (fit) |

## Restoration (`discover-post.json`, 20:52Z)

- **Carol:** Member again, `overrides: []`, the same 5 capabilities. Both 665D roles have 0 members.
- **`bulk_billing`:** ON. `information_requests`: ON. `enabledModules` is identical to `discover-pre`.
- **Billing / cost rates:** `[]` (both the 04 rates and the 07 rates were deleted).
- **Unchanged:** currency ZAR; tax settings, time recording, fee runs and collections are unchanged from `discover-pre` (F-665D-4's stored fallbacks date from OBS-before).
- **Tax rates (9):** VAT — Standard (default), E2E Rate v0n1, E2E Rate t3b9, Zero-rated, Exempt, OBS 665D Rate (inactive, from before).
  - **Added by this run:** "OBS 665D Rate after" (inactive), "OBS 665D Rate after-2" (inactive), and the spec's "E2E Rate 2zch" (active).
  - The UI can only deactivate, so these remain.

## Files

- **Sheets:**
  - `sheet-{tariffs,tax,time-recording,fee-runs,collections,trust-accounting}-alice.png`
  - `sheet-carol.png`
  - `sheet-custom-role-{financial,no-financial}.png`
  - `sheet-modules.png`, `sheet-saves.png`, `sheet-roles.png`
  - `sheet-dark-and-dialogs.png` (new)
- **Crops:** `crop-defect-rates-billing-table-1024.png` (F-665D-1, still open), `crop-defect-tax-rates-table-1024.png` (F-665D-2, now fits), `crop-tabstrip-fees-1024.png`.
- **JSON:** `discover-{pre,post}.json`, `measurements.json`, `sel-compat.json`, `saves.json`, `console.json`, `dark-probe.json`.

## Defects

- **Introduced by 665D: none observed.**
- **F-665D-1 is worse, as predicted:** the Rates scroller shrank from 494 to 446 px inside the SettingsSection, so "EFFECTIVE FROM" now loses 50 px (the date is partly hidden) where it lost 2 px before. The routing note anticipated this and orchestrator ruling 1 accepted it (no column hidden → 669A / 670A). It is recorded under F-665D-1, not as a new row.
- Pre-existing and already registered: **K-13** (light success badge 4.496:1).
- Notes are in `../found.md` → "OBS-after".
