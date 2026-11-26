# 665D OBS-before: Settings › Fees & Trust (6 tabs), task 665.13 "Observe-first"

- **When:** 2026-11-26, 20:05Z to 20:18Z (captures 20:09Z to 20:17Z).
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`, v1.59.1; the worktree has none). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code was changed and no SQL was run. Reads went through the API (GET); every write went through the UI.
- **Raw files:** the raw per-viewport PNGs (126) are in `/tmp/665D-before-raw/`, outside the repo. Only contact sheets and crops are committed (15 PNGs).

## Stack observed (build source)

- **Frontend container:** `docker inspect e2e-frontend` → created `2026-11-26T19:45:52.457Z`, image `sha256:d20ff9430acd…`. Its compose `working_dir` label is `/Users/…/worktree-epic-665C/compose` (that worktree is now deleted). 665C's final product head was `b87d6d742`.
- **It equals `main`:** `git diff --stat b87d6d742 1a867a784 -- frontend packages compose` is **empty**. The only commits in `b87d6d742..1a867a784` are the 665C squash-merge `582ad0511` and the docs commit `1a867a784`. So the observed frontend equals this worktree's base, `main` @ `1a867a784`.
  - Caveat, as in 665B / 665C: uncommitted changes in the 665C worktree at build time can't be ruled out after the fact.
- **Backend:** `e2e-backend`, up about 1 h. The data comes from the 19:01Z seed: the legal-za compliance packs were applied at 19:01Z. The 665C roles "665C OBS Take-on" / "665C OBS No Take-on" (0 members) and the tax rate "E2E Rate v0n1" (from an earlier `tax-settings.spec` run) were already present.

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities | `GET /api/cost-rates` |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / false / true | 29 (all) | 200 |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST | 403 |
| `carol-cr-holder` | Carol's cookie while holding **"665D OBS Financial"** | 665D OBS Financial / false / false | FINANCIAL_VISIBILITY | 200 |
| `carol-cr-lacker` | Carol's cookie while holding **"665D OBS No Financial"** | 665D OBS No Financial / false / false | INVOICING | 403 |

### Custom roles + assignment order (`02-roles.cjs`, through the UI as Alice)

- **Why these two roles:** the Fees family has one capability gate, the Rates (`tariffs`) loader's in-page `FINANCIAL_VISIBILITY` check (`_tabs/tariffs.tsx:14-31`). Every other Fees tab is `adminOnly`, and Fee runs / Trust accounting are also module-gated (`lib/settings-nav.ts:224-282`). No Fees tab has a `requiredCapability`. So the roles split on FINANCIAL_VISIBILITY. The lacker holds INVOICING ("Billing"), a capability next door to finance, to show that it doesn't unlock Rates.
- **Roles created:** through Settings › Firm › Roles › New Role → Create Role, at 20:11:27Z.
  - **"665D OBS Financial"** → id **`ed1125a5-65cd-43eb-ac0c-c26c76430a08`**, capabilities exactly `[FINANCIAL_VISIBILITY]` (checkbox "Financial Visibility").
  - **"665D OBS No Financial"** → id **`ed18669b-7730-483d-aeb0-5cab46f02a33`**, capabilities exactly `[INVOICING]` (checkbox "Billing").
  - Both were verified by `GET /api/org-roles`.
- **Assignment** went through Team › Carol's row › Member Detail › Role › Save, in this order:
  1. Member → **665D OBS Financial** (run 20:11:35Z)
  2. → **665D OBS No Financial** (20:12:17Z)
  3. → **Member** (20:12:58Z)
- **After each step:** `GET /api/members/{id}/capabilities`, Carol's own `/api/me/capabilities`, and her status on `/api/billing-rates`, `/api/cost-rates` and `/api/settings` were recorded (`measurements.json` → `customRoles`).
- **Final state:** Carol is **Member**, `overrides: []`, the same 5 capabilities as before. The `discover-post.json` members match `discover-pre.json`. Both 665D roles are **left on the stack** (0 members) for OBS-after.

## Module toggles (`03-modules.cjs`, UI as Alice; `measurements.json` → `modules`; `sheet-modules.png`)

- **Features page:** Settings › Firm › Features has two switches only: `information_requests` ("Toggle Information Requests") and `bulk_billing` ("Toggle Bulk Billing Runs"). These are the horizontal modules from `GET /api/settings/modules`.
- **`bulk_billing` OFF** (switch clicked at **20:14:49Z**; API `bulk_billing:false`, `enabledModules` without it):
  - `/settings/fees` (1440 and 1024): tabs **Rates | Tax | Time recording | Collections | Trust accounting**. The settings rail lists the same 5 under Fees & Trust. **No Fee runs.**
  - `?tab=fee-runs`: the URL is unchanged and the Rates panel renders (h1 "Rates & Currency"). Above it is `<p role="status">` **"You don't have access to Fee runs — showing Rates instead."** (F-665D-6). The title is "Fees & Trust — Rates · Settings | Kazi".
  - `?tab=trust-accounting` is still rendered (h1 "Trust Accounting Settings").
- **Restored ON** (switch clicked at **20:14:58Z**).
  - API GET right after: `information_requests:true`, `bulk_billing:true`, `enabledModules` = court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing. This is identical to `discover-pre.json`.
  - `?tab=fee-runs` renders "Batch Billing" again. `discover-post.json` (20:16:20Z) confirms both modules are ON.
- **`trust_accounting` was NOT toggled:** it is a legal-za **vertical** module, and the Features page has no switch for it (`#feature-switch-trust_accounting` count 0). `ModuleSettingsController` says vertical modules "are NOT exposed here". The alternatives, SQL or `PATCH /api/settings/vertical-profile`, are outside the UI-only rule. So "trust_accounting off → Trust accounting tab absent" is **unobserved** (F-665D-5). Its gate is `requiredModule: "trust_accounting"`, pinned in `settings-nav.test.ts:55`.

## Data created / changed (ids for OBS-after)

| What | How | Final state |
|---|---|---|
| Roles "665D OBS Financial" `ed1125a5-…` / "665D OBS No Financial" `ed18669b-…` | Roles UI | left, 0 members |
| Billing rate for Carol Member (550 ZAR from 2026-11-26 → edited 560) `e0429d7c-b109-4ded-8b9f-a3b64aaae933` | Rates › Add Rate / Edit / Delete | **deleted**; billing rates back to `[]` |
| Tax rate **"OBS 665D Rate"** 5% `909fe373-7b1c-4243-b196-e967ea857f2b` | Tax › Add Tax Rate → Create, then Deactivate | **left, Inactive** (the UI only deactivates). Its row now shows "Inactive" with only an Edit button. |
| `taxRegistrationLabel` null → **"Tax Number"** | Tax › Save Settings with the displayed values | changed. The UI can't restore null (F-665D-4). |
| `timeReminderDays` null → **"MON,TUE,WED,THU,FRI"**, `timeReminderTime` null → **"17:00"** | Time recording › Save Settings with the displayed values | changed. The UI can't restore null (F-665D-4). |
| `defaultCurrency` ZAR, Fee runs (50 / 5 / null / 30), Collections (off, 7 / 21 / 45 / 60) | re-saved with the current values | unchanged |
| `bulk_billing` | off → on | ON (as before) |
| Tax rate **"E2E Rate t3b9"** 10% `ce24bde9-521f-433a-b805-5ec9ce0e2791` | **created by the spec run** (`tax-settings.spec` "Create tax rate") | left, Active |

**Tax rates for OBS-after (6):** VAT — Standard (default), E2E Rate v0n1, OBS 665D Rate (inactive), E2E Rate t3b9, Zero-rated, Exempt. At capture time (before the saves) the table had 4 rows.

## Tabs and gating (`lib/settings-nav.ts:224-282` + `settings-family-page.tsx:84-118`)

The tablist is `aria-label="Fees & Trust settings"`, in the order listed. The settings rail (`nav[aria-label="Settings navigation"]`) lists the same permitted tabs. A bare `/settings/fees` resolves to Rates.

| `?tab=` | Label | h1 (Alice) | Gate | Alice | Carol | Holder (FIN_VIS) | Lacker (INVOICING) |
|---|---|---|---|---|---|---|---|
| `tariffs` | Rates | "Rates & Currency" (+HelpTip) | ungated tab; in-page FINANCIAL_VISIBILITY | full | **in-page denial** | **full UI** (F-665D-7) | **in-page denial** |
| `tax` | Tax | "Tax Settings" | `adminOnly` | rendered | notice → Rates | notice | notice |
| `time-recording` | Time recording | "Time Tracking" | `adminOnly` | rendered | notice | notice | notice |
| `fee-runs` | Fee runs | "Batch Billing" | `adminOnly` + `bulk_billing` | rendered | notice | notice | notice |
| `collections` | Collections | "Collections" | `adminOnly` | rendered | notice | notice | notice |
| `trust-accounting` | Trust accounting | "Trust Accounting Settings" | `adminOnly` + `trust_accounting` | rendered | notice | notice | notice |

- **Tab bars:**
  - Alice: Rates | Tax | Time recording | Fee runs | Collections | Trust accounting (strip 538 px in a 496 px ScrollArea at 1024).
  - Carol, the holder and the lacker: **Rates** only. The rail shows "Fees & Trust › Rates".
- **Forbidden deep link:** the URL is unchanged, the Rates panel renders, and above it is `<p role="status">` "You don't have access to {Tab} — showing Rates instead." (e.g. "…to Trust accounting — showing Rates instead.").
- **Carol's / the lacker's Rates panel:** the h1 "Rates & Currency" plus `<p>` "You do not have permission to manage rates and currency settings. Only admins and owners can access this page." No buttons, tables or testids.
- **Global invariants** (`sel-compat.json`, every tab × persona at 1024 light; `measurements.json` variants for all 4):
  - `main#main-content` = 1 and `<main>` = 1 everywhere.
  - **h1 in `#main-content` = 1 everywhere.**
  - Tablist "Fees & Trust settings" (exact) = 1.
  - 0 "Something went wrong".

## Per tab (Alice 1440 light; full probe in `measurements.json` → `personas.alice.surfaces`)

- **Rates (`tariffs`):**
  - h1 "Rates & Currency" with HelpTip and a subtitle.
  - Hand-rolled card, h2 "Default Currency" (+HelpTip), with the CurrencySelector combobox "ZAR — South African Rand". It saves instantly on select.
  - A hand-rolled info box: "Rate changes only affect new time entries…".
  - Inner Tabs **Billing Rates | Cost Rates** (no aria-label). In the Cost tab the h3 is "Cost Rates" (+HelpTip).
  - Both tables are shadcn `Table` (`data-slot=table`, `table-container` scroller), not DataTable. Headers MEMBER | HOURLY RATE / HOURLY COST | CURRENCY | EFFECTIVE FROM | EFFECTIVE TO | ACTIONS. 3 rows, all "Not set".
  - Row testids `billing-rate-{owner,admin,member}` / `cost-rate-…`. Buttons "Add rate for {name}" (plain, "+ Add Rate"); once a rate exists, "Edit billing rate for {name}" and "Delete billing rate for {name}" (red trash).
- **Tax:**
  - h1 "Tax Settings" and its subtitle.
  - Hand-rolled card h2 "Tax Configuration": `#tax-registration-number` (empty), `#tax-registration-label` ("Tax Number", the loader fallback; stored null before the save), `#tax-label` "VAT", and the switch `#tax-inclusive` off. "Save Settings" is **inside** the card.
  - Hand-rolled card h2 "Tax Rates" with "Add Tax Rate" and a raw `<table>` in `div.mt-4.overflow-x-auto`. Headers NAME | RATE | DEFAULT | STATUS | ACTIONS. 4 rows (`tax-rate-row` / `-name` / `-value` / `-default`). Badges "Default" (success) and "Exempt" (neutral); Status is green "Active" text.
  - Actions are sr-only "Edit {name}" and "Deactivate {name}" (red Ban icon). There is no Deactivate on the default rate or on inactive rates.
- **Time recording:**
  - h1 "Time Tracking".
  - Hand-rolled card h2 "Time Reminders": the switch "Enable time reminders" (off), checkboxes Mon–Sun (Mon–Fri checked), `#reminder-time` 17:00 "Reminder Time (SAST)", `#min-hours` 4.
  - Hand-rolled card h2 "Default Disbursement Markup": `#expense-markup` (empty, "No markup").
  - One form-level "Save Settings" **below** both cards.
- **Fee runs:**
  - h1 "Batch Billing".
  - Hand-rolled card h2 "Fee note defaults": `#default-payment-terms-days` 30.
  - Hand-rolled card h2 "Batch Billing Configuration": `#async-threshold` 50, `#email-rate-limit` 5, `#default-billing-currency` empty.
  - Form-level "Save Settings" below.
- **Collections:**
  - h1 "Collections".
  - One hand-rolled card h2 "Overdue fee-note reminders": the switch "Enable collections reminders" (off) and stages 7 / 21 / 45 / escalation 60.
  - Form-level "Save Settings" below.
- **Trust accounting:**
  - `data-testid="trust-settings-page"`, h1 "Trust Accounting Settings", **no h2/h3**.
  - Three `data-slot=card` cards, titles as `CardTitle` divs:
    - **Trust Accounts** (`trust-accounts-section`): "Add Account" (`add-trust-account-button`), 1 account "Mathebula & Partners Trust Account" with Primary / ACTIVE badges, `trust-account-section`, and the §86(6) advisory `role=status`.
    - **Approval Settings** (`approval-settings-section`): 1 row, "Single".
    - **Reminder Settings** (`reminder-settings-section`): 3 static rows with badges.

## Save models (`saves.json`, `sheet-saves.png`; Alice 1440 light)

| Model | Button (pristine → during) | Action | Message (text, placement, a11y) | API before → after |
|---|---|---|---|---|
| Rates › Default Currency (instant on select) | combobox → Command list | re-picked "ZAR — South African Rand" | **"Default currency updated."** `<p class="mt-2 text-sm text-green-600">`, inside the card under the header row, no role / aria-live, ~213 ms | ZAR → ZAR |
| Rates › billing rate dialogs | "Add rate for Carol Member" → dialog **"Add Rate"** (Billing / Cost toggle, Hourly Rate, Currency preselected ZAR, Effective From / To) → **"Create Rate"**; "Edit billing rate for …" → **"Edit Billing Rate"** → **"Save Changes"**; "Delete billing rate for …" → alertdialog **"Delete Billing Rate"** ("…This action cannot be undone.") → **"Delete"** | 550 → 560 → deleted | **no message, no toast**; the dialog closes and the row changes (419 / 717 / 430 ms). Row text "R 550,00 ZAR 26 Nov 2026 Ongoing". | `[]` → 1 rate → `[]` |
| Tax › Tax Configuration "Save Settings" (per-card) | enabled (not dirty-gated) → `disabled` while saving | current values | **"Tax settings updated."** green `<p>` right of the button **inside the card**, no role / aria-live, ~74 ms | label null → **"Tax Number"** (F-665D-4) |
| Tax › tax rate dialogs | "Add Tax Rate" → dialog **"Add Tax Rate"** (Name, Rate (%), "Set as default tax rate", "Tax exempt") → **"Create"**; "Deactivate {name}" → alertdialog **"Deactivate Tax Rate"** ("…will no longer be available for new fee note lines.") → **"Deactivate"** | "OBS 665D Rate" 5% | **no message, no toast** (580 / 427 ms); the row stays as "Inactive" with Edit only | + 1 inactive rate |
| Time recording "Save Settings" (form-level) | same | current values | **"Time tracking settings updated."** green `<p>` right of the button below the cards, no role / aria-live | days / time null → MON–FRI / 17:00 (F-665D-4) |
| Fee runs "Save Settings" (form-level) | same | current values | **"Batch billing settings updated."** same style, ~102 ms | unchanged |
| Collections "Save Settings" (form-level) | same | current values | **"Collections settings updated."** green `<p role="alert" aria-live="polite">`, ~91 ms | unchanged |

## SEL-COMPAT before (`sel-compat.json`, 1024 light)

| Spec selector | Alice | Carol | Holder | Lacker |
|---|---|---|---|---|
| `rate-cards.spec.ts:14/26` `#main-content` heading h1 `/^Rates & Currency/` | 1 | 1 | 1 | 1 |
| `day-00-firm-setup.spec.ts:58` heading h1 "Rates & Currency" | 1 | 1 | 1 | 1 |
| `day-90-review.spec.ts:392/448` heading h1 `/Rates & Currency/` | 1 | 1 | 1 | 1 |
| `rate-cards.spec.ts:39` / `day-90:397` "You do not have permission to manage rates and currency settings." | 0 | 1 | 0 | 1 |
| `day-90:401` buttons `/^(Add\|Edit) (billing )?rate for/` | 3 | 0 | 3 | 0 |
| `day-00:145` tab "Billing Rates" / `:146,164` tab "Cost Rates" | 1 / 1 | 0 / 0 | 1 / 1 | 0 / 0 |
| `day-00:165` heading "Cost Rates" (after the click; 0 before) | 1 | — | 1 | — |
| `day-90:451/476` `billing-rate-owner` | 1 | 0 | 1 | 0 |
| `day-00:73` `billing-rate-{owner,admin,member}` | 1/1/1 | 0 | 1/1/**0** (Carol's row is `billing-rate-665d-obs-financial`) | 0 |
| `tax-settings.spec.ts:24` heading h1 "Tax Settings" | 1 | 0 (notice) | 0 | 0 |
| `tax-settings.spec.ts:37-40` row with cell "VAT — Standard" containing "15.00%" | 1 / yes | 0 | 0 | 0 |
| `tax-settings.spec.ts:41/47` button "Add Tax Rate" | 1 | 0 | 0 | 0 |
| `tax-settings.spec.ts:57` row with cell (existing "E2E Rate v0n1") | 1 | 0 | 0 | 0 |
| `day-00:174` `tax-rate-row` | 4 | 0 | 0 | 0 |
| `day-00:183-188` row filter `tax-rate-name` "VAT — Standard" → `tax-rate-value` / `tax-rate-default` | 1 → "15.00%" / "Default" | 0 | 0 | 0 |
| `day-90:483` `trust-settings-page` (`:414` Carol expects 0) | 1 | 0 | 0 | 0 |
| `day-00:250` / `day-90:484` `trust-accounts-section` (+ exact text "Mathebula & Partners Trust Account") | 1 (1) | 0 | 0 | 0 |
| `day-90:409` status "You don't have access to Trust accounting — showing Rates instead." | 0 | 1 | 1 | 1 |
| `day-90:415` button "Add Account" | 1 | 0 | 0 | 0 |

- **Spec run before:**
  - Command, from the main checkout @ `1a867a784` at 20:16Z: `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 npx playwright test --config e2e/playwright.config.ts e2e/tests/settings/rate-cards.spec.ts e2e/tests/settings/tax-settings.spec.ts --reporter=list`.
  - Result: **6 passed, 0 failed** (3.4 s). The frontend tree was not dirtied. It created tax rate "E2E Rate t3b9".
  - The lifecycle specs (`day-00`, `day-90`) were not run: they are serial, run in the legal-lifecycle config, and create data. Their selectors are covered by the counts above.

## Console

- **100 page loads** across the 4 personas, plus the roles, modules, saves and probe runs.
- **0 errors, 0 warnings, 0 hydration messages** (`console.json`).

## Measurements at 1024×768 (`measurements.json` → `at1024`, Alice light unless noted)

- **Widths:** `<main>` is **760 px** and the tab panel / content column is **496 px** (light). In dark, both are 8 px wider (main 768, panel 504), and the same holds at 1440 (1176 / 864 light, 1184 / 872 dark). The tab strip is 538 px in a 496 px ScrollArea viewport (`06b`).
- **h1:** exactly 1 in `#main-content` on every tab, for every persona and variant.

| Tab | h1 | `docHOverflowPx` light / dark | Table scroller visible / scroll | What is cut at scroll position 0 (`overflowProbe`) | EmptyState |
|---|---|---|---|---|---|
| tariffs | Rates & Currency | **0 / 0** | 494 / 725 (Billing), 494 / 728 (Cost); `table-container`, relative | EFFECTIVE TO + ACTIONS fully hidden, 3/3 actions out of view (F-665D-1) | none |
| tax | Tax Settings | **0 / 0** | 446 / 519, hand-rolled, static | ACTIONS cut 75 px, 8/8 actions partly out of view (F-665D-2) | none |
| time-recording | Time Tracking | **0 / 0** | — | — | none |
| fee-runs | Batch Billing | **0 / 0** | — | — | none |
| collections | Collections | **0 / 0** | — | — | none |
| trust-accounting | Trust Accounting Settings | **0 / 0** | — (list of divs, not a table) | — | none |

- Every tab's `docHOverflowPx` is 0 at both 1024 and 1440, light and dark, for all 4 personas.
- **No EmptyState was rendered anywhere**, so there is no outerHTML hash or box height to record. The empty branches in the code are listed in `../found.md`, Observations.

## Files

- **Contact sheets:**
  - `sheet-{tariffs,tax,time-recording,fee-runs,collections,trust-accounting}-alice.png`
  - `sheet-carol.png`
  - `sheet-custom-role-{financial,no-financial}.png`
  - `sheet-modules.png`, `sheet-saves.png`, `sheet-roles.png`
- **Crops:**
  - `crop-defect-rates-billing-table-1024.png`, `crop-defect-tax-rates-table-1024.png` (defects)
  - `crop-tabstrip-fees-1024.png` (observation)
- **JSON** (single line): `discover-{pre,post}.json`, `measurements.json`, `sel-compat.json`, `saves.json`, `console.json`.
- **Scripts** (as executed, in this order):
  - `00-discover pre` → `01-capture alice`, `carol` → `02-roles create` → `02-roles assign "665D OBS Financial"` → `01-capture carol-cr-holder` → `02-roles assign "665D OBS No Financial"` → `01-capture carol-cr-lacker` → `02-roles assign Member` → `04-saves` → `03-modules` → `06-overflow-probe` → `06b-tabstrip-probe` → spec run → `00-discover post` → `05-assemble`.
  - Every script except `06b` takes `PHASE=before|after|after-fix`, so OBS-after re-runs them unchanged with `PHASE=after`.
  - `04-saves` will add a second inactive "OBS 665D Rate" if re-run.

## Defects seen (pre-existing) → `../found.md`

- **F-665D-1:** at 1024, the Rates tables hide EFFECTIVE TO and ACTIONS (the row actions are off-screen).
- **F-665D-2:** at 1024, the Tax rates ACTIONS column is cut by 75 px.
- **F-665D-3:** save messages are bare green `<p>` with no role / aria-live; Collections alone uses `role="alert"`.
- **F-665D-4:** saving unchanged Tax / Time recording values stores the display fallbacks ("Tax Number", MON–FRI, 17:00).
- **F-665D-5:** `trust_accounting` can't be toggled in the UI, so the "module off" state was not observed (brief / plan gap).
- **F-665D-6:** when a module is off, the deep-link notice says "You don't have access to Fee runs".
- **F-665D-7:** a FINANCIAL_VISIBILITY-only role gets the Rates editing controls, but the writes are admin-only (inferred, not exercised).
