# 665F OBS-before: Settings › Connections (6 tabs + 3 log sub-pages), task 665.19 "Observe-first + log-read re-scout"

- **When:** 2026-11-26, 22:42Z to 22:56Z. Discover-pre 22:42:24Z; Alice capture 22:45–22:46Z; roles created 22:47:04Z; Carol / custom-role captures 22:47–22:50Z; Carol restored to Member 22:50:54Z; interactions 22:52:19–22:52:50Z; spec run 22:53:08–22:53:16Z; crops 22:54:17Z; discover-post 22:54:40Z; assemble 22:55:38Z.
- **Stack:** the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. Not restarted, rebuilt, reseeded or torn down.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`; the worktree has none). Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **What changed:** no product code, no SQL. Reads through the API (GET); every OBS write went through the UI (roles, role assignment, one rule toggle and one preference toggle, both reverted). The spec run made its own API calls.
- **Raw files:** 188 raw PNGs in `/tmp/665F-before-raw/`, outside the repo. Committed: 17 contact sheets + 7 defect crops.

## Stack observed (build source)

- **Frontend container:** `docker inspect e2e-frontend` → created **`2026-11-26T22:25:20.444Z`**, image `sha256:78aedbc96084…`, compose `working_dir` label `/Users/…/worktree-epic-665E/compose`. 665E's final product head was `092a21309`.
- **It equals `main`:** `git diff --stat 092a21309 d10aa6d3e -- frontend packages compose` is **empty** (exit 0). So the observed frontend equals this worktree's base, `main` @ `d10aa6d3e`. Caveat as before: uncommitted changes in the 665E worktree at build time can't be ruled out after the fact.
- **Backend:** `e2e-backend` created `2026-11-26T21:57:58Z`, image `sha256:e7fd89e47741…`, a fresh seed (`GET /api/org-roles` = Owner, Admin, Member only before this OBS).

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities | Connections API (GET) |
|---|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / false / true | 29 (all, incl. INTEGRATION_MANAGE, INTEGRATION_VIEW_SYNC_STATUS, FINANCIAL_RECONCILE, AI_MANAGE, AUTOMATIONS, MCP_ACCESS) | all 200 (`/api/integrations/xero/connection` 404 = not connected) |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST | 403 everywhere except `/api/notifications/preferences` 200 |
| `carol-cr-holder` | Carol while holding **"665F OBS AI Automations"** | that / false / false | AI_MANAGE, AUTOMATIONS | 200: ai/profile, ai/executions, automation-rules, automation-executions, notification prefs; 403: integrations, mcp/status, sync/entries, packs |
| `carol-cr-lacker` | Carol while holding **"665F OBS No Integration"** | that / false / false | CUSTOMER_MANAGEMENT | as Carol |

### Custom roles (`02-roles.cjs`, through the UI as Alice)

- **Why these two:** INTEGRATION_MANAGE, INTEGRATION_VIEW_SYNC_STATUS, FINANCIAL_RECONCILE and MCP_ACCESS are **not offered** by the Create Role dialog (it lists `CAPABILITY_META`, `lib/capabilities.tsx:23-87`). So **no custom role can hold INTEGRATION_MANAGE through the UI**, and the "custom role WITH INTEGRATION_MANAGE / INTEGRATION_VIEW_SYNC_STATUS / FINANCIAL_RECONCILE" personas are **unobservable** under the UI-only rule (API `POST /api/org-roles` not used). The only UI-grantable Connections gates are AI_MANAGE ("AI Management") and AUTOMATIONS ("Automations"):
  - **"665F OBS AI Automations"** → id **`15c0df12-296b-4c75-8fd4-7a6bd0937d37`**, exactly `[AUTOMATIONS, AI_MANAGE]` (holds the UI-grantable gates, lacks INTEGRATION_MANAGE).
  - **"665F OBS No Integration"** → id **`7c993e75-e8f1-44c1-a42f-cfd6297a7140`**, exactly `[CUSTOMER_MANAGEMENT]` (lacks every Connections capability).
  - Created via Settings › Firm › Roles › New Role at 22:47:04Z; verified by `GET /api/org-roles`.
- **Assignment** (Team › Carol's row › Member Detail › Role › Save): Member → AI Automations (22:48:24Z) → No Integration (22:49:39Z) → **Member** (22:50:54Z). After each step `GET /api/members/{id}/capabilities`, Carol's `/api/me/capabilities` and her GET status on 9 Connections endpoints were recorded (`measurements.json` → `customRoles`).
- **Final:** Carol is **Member**, `overrides: []`, the same 5 capabilities. `discover-post.json` members equal `discover-pre.json`. Both roles are **left on the stack** (0 members) for OBS-after; `02-roles.cjs create` is idempotent.

## Data created / changed (ids for OBS-after)

| What | How | Final state |
|---|---|---|
| Roles "665F OBS AI Automations" `15c0df12-…`, "665F OBS No Integration" `7c993e75-…` | Roles UI | **left**, 0 members |
| Carol's role Member → 2 custom roles → Member | Team UI | restored (Member, no overrides) |
| Rule "Investment Maturity Reminder" `68d17580-e182-42fe-9a11-65485482f80a` | Automations switch OFF → ON (OBS, 22:52:33Z / 22:52:38Z), then the spec run's Disable / Enable | **enabled = true** (as before). `updatedAt` 21:58:27Z → 22:53:15Z (the only `discover-pre`/`post` diff) |
| Notification pref `TASK_ASSIGNED` in-app true → false → true | Notifications switch + "Save preferences" ×2 (OBS) | true / email false (as before); 44 prefs before and after |
| Notification pref `TIME_REMINDER` email | **spec run** (NOTIF-02 enable → disable; before/afterAll restore) | email false (as before) |
| Matter **"Bell Matter exfa"** `a230d129-4732-49a3-a1b8-fc50d69659ca` + tasks "Bell Task Opened exfa", "Bell Task Listed exfa" assigned to Carol (+ their notifications, read) | **spec run** (NOTIF-01 `beforeAll`) | left |
| "Power of Attorney" pack template | **spec run** (PACK-01 edit → revert) | reverted |

Nothing destructive was confirmed: every dialog was cancelled or escaped (`03-interactions.cjs`). MCP stayed disabled (`effectivelyEnabled:false`, consent not granted); all 5 packs still installed; no provider / key / Xero connection was set.

## Tabs and gating

Tablist `aria-label="Connections settings"`; order Integrations | MCP | AI | Notifications | Packs | Automations. A bare `/settings/connections` resolves to Integrations. All 10 legacy routes (`lib/legacy-redirects.ts:247-298`) redirect as specified, for every persona (`measurements.json` → `personas.*.legacy`).

| `?tab=` / route | Label | h1 (Alice) | Gate (file:line) | Alice | Carol | cr-holder (AI_MANAGE+AUTOMATIONS) | cr-lacker |
|---|---|---|---|---|---|---|---|
| `xero` | Integrations | "Integrations" (+HelpTip) | none (`settings-nav.ts:293`); Xero section `INTEGRATION_MANAGE` in-page (`_tabs/xero.tsx:76-77,190-217`) | full | rendered; Xero section → "You do not have permission to manage integrations." **but the 6 integration cards render with enabled provider comboboxes, "Configure", "Send Test Email"**, and Email Delivery shows a red `role=alert` "Insufficient permissions for this operation" | = Carol | = Carol |
| `mcp` | MCP | **"Claude / MCP Connector"** | `requiredCapability: "INTEGRATION_MANAGE"` (`:303`); in-page denial `_tabs/mcp.tsx:11-24` (unreachable) | rendered | notice → Integrations ("You don't have access to MCP — showing Integrations instead.") | notice | notice |
| `ai` | AI | **"Set Up AI"** (`coldStartCompleted=false`; "AI Configuration" otherwise, `_tabs/ai.tsx:75`) | `adminOnly` (`:310`); in-page AI_MANAGE (`_tabs/ai.tsx:27`) **unreachable** | rendered | notice | **notice — AI_MANAGE does not open the tab** | notice |
| `notifications` | Notifications | **"Notification Preferences"** (+HelpTip) | none (`:314`) | rendered | rendered | rendered | rendered |
| `packs` | Packs | "Packs" | `adminOnly` (`:322`); `canManage` `_tabs/packs.tsx:14` | rendered | notice | notice | notice |
| `automations` | Automations | "Automations" | `adminOnly` (`:328`); in-page AUTOMATIONS (`_tabs/automations.tsx:16`) **unreachable** | rendered | notice | **notice — AUTOMATIONS does not open the tab** | notice |
| `xero/sync-log` | — | "Sync Log" | `isAdmin\|\|INTEGRATION_VIEW_SYNC_STATUS` (`xero/sync-log/page.tsx:29`); row actions `FINANCIAL_RECONCILE` (`:27`) | 0 entries → h2 "No sync entries found", 3 filters | in-page denial "You do not have permission to view the sync log." | denial | denial |
| `ai/history` | — | "Execution History" | `isAdmin\|\|AI_MANAGE` (`ai/history/page.tsx:21`) | 0 executions → h3 "No executions yet" | denial "You do not have permission to view execution history. Contact your administrator." | **rendered (empty state) — reachable by URL although the AI tab is denied** | denial |
| `automations/executions` | — | "Execution Log" | `isAdmin` → `redirect(?tab=automations)` (`automations/executions/page.tsx:27-29`) | 10 rows | redirect → `?tab=automations` → notice → Integrations | same | same |

- **Tab bars:** Alice sees all 6; Carol and both custom roles see **Integrations | Notifications** (rail: Connections › Integrations, Notifications).
- **K-124 h1 vs tab label:** MCP → "Claude / MCP Connector", AI → "Set Up AI", Notifications → "Notification Preferences"; Integrations / Packs / Automations match.
- **Global invariants** (`sel-compat.json`, 1024 light, every surface × persona): `main#main-content` = 1, `<main>` = 1, **h1 in `#main-content` = 1 everywhere**, tablist exact = 1 on tabs / 0 on sub-pages, 0 "Something went wrong".
- **Widths:** `<main>` 1176 / 760 px at 1440 / 1024; tab panel 864 / 496 px; sub-pages use the full main width (no tabpanel).
- **Titles:** tabs "Connections — {Tab} · Settings | Kazi"; the 3 sub-pages **"Kazi — Practice management, built for Africa"** (generic).

## Log pages: rows and paging

| Page | Rows (Alice) | API `page` | Pager | Page 2 |
|---|---|---|---|---|
| `xero/sync-log` | 0 (empty state) | totalElements 0, totalPages 0 | none (renders only when totalPages > 1, `page.tsx:144`) | n/a. Not linked from anywhere while Xero is disconnected (the "View Sync Log" link lives in `XeroSyncSummary`, connected only) — URL / legacy route only |
| `ai/history` | 0 (empty state "No executions yet") | 0 / 0 | none ("Previous page"/"Next page" = 0) | n/a |
| `automations/executions` | **10** (all "Matter Onboarding Reminder", trigger `CustomerStatusChangedEvent`, Completed) | totalElements 10, totalPages 1, size 20 | none (Previous / Next = 0) | n/a |

## Per surface (Alice 1440 light; full probe in `measurements.json` → `personas.alice.surfaces`)

- **Integrations:** h1 + subtitle; a 2-col grid of 7 cards (Accounting [Configure Xero link], AI Assistant [provider combobox `noop|anthropic`], Document Signing, Email Delivery [Active, Platform Email, 3 stat tiles, Send Test Email, BYOAK disclosure], Payment Gateway [provider `None|noop|Stripe|PayFast`], KYC Verification [Configure], Claude / MCP Connector [Disabled, Configure Connector]); then h2 "Xero Integration" + Xero Connection card "Not Connected" with **"Connect to Xero"** (not clicked). No switches (no provider configured).
- **MCP:** h1 + subtitle; McpConnectorCard (Disabled badge, switch "Enabled" = false). No "Revoke access" (only when enabled).
- **AI:** h1 "Set Up AI"; 3-col grid: AiProfileForm (2 cols) + cost summary + "Execution History" card (link "View execution history →") + "AI Integration" card (link "Manage integrations →").
- **Notifications:** one hand-rolled bordered table-like list: header row "Notification Type | In-App | Email"; 8 category bands (uppercase `p.text-xs.text-slate-500` on `bg-slate-50`): TASKS, MATTERS, COLLABORATION, BILLING, CLIENT REQUESTS, TIME TRACKING, SECURITY, **OTHER**; 44 rows × 2 switches (88). The in-app switch is named by its `<Label for>`; **the 44 email switches are unnamed**. "Save preferences" button, left.
- **K-115 — the 13 raw enum names in "OTHER"**, as rendered: `COURT_DATE_REMINDER, PRESCRIPTION_WARNING, TRUST_PAYMENT_AWAITING_APPROVAL, TRUST_PAYMENT_APPROVED, TRUST_PAYMENT_REJECTED, TRUST_RECONCILIATION_OVERDUE, TRUST_INVESTMENT_MATURING, TRUST_APPROVAL_AGING, TRUST_RECONCILIATION_COMPLETED, MATTER_CLOSED, MATTER_REOPENED, COLLECTION_ESCALATED, CASH_DIGEST` (crop).
- **Packs:** h1 + subtitle; inner Tabs (line) **Available | Installed (5)**; Available has "Show all packs" switch (toggling it showed the same 5 cards); 3-col card grid, `[data-testid=pack-card-{id}]` with an `h3`. Available cards show a disabled "Installed" button; Installed cards show "Installed on 26 Nov 2026" + Uninstall (enabled for `common`, `automation-common`; disabled with a tooltip blockingReason for `compliance-za` "1 template has been cloned", `legal-za` "7 templates have been used to generate documents; 1 template has been cloned", `automation-legal-za` "1 rule has been executed").
- **K-121:** the two automation packs render **h3 = raw packId "automation-common" / "automation-legal-za"**, **no description** (`/api/packs/catalog` name = packId, description null), plus a "Legal - Za" badge. Also "**1 templates**" (no singular).
- **Automations:** h1 + subtitle + "View Execution Log →" (teal); shadcn Table NAME | TRIGGER | ENABLED | LAST UPDATED | ACTIONS, 10 `automation-row`s; switch "Toggle {name}" per row; a second "View Execution Log →" under the table, right.
- **Execution Log:** back link "‹ Automations"; h1 + subtitle; filters "All Rules" (11 options) / "All Statuses" (All Statuses, Running, Completed, Failed, Skipped), both **unlabelled** comboboxes, + Refresh; table RULE | TRIGGER | TRIGGERED AT | STATUS | CONDITIONS | ACTIONS | DURATION.
- **K-122:** the Execution Log TRIGGER column renders the badge **"CustomerStatusChangedEvent"** (Java event class; `TriggerTypeBadge` falls back to the raw value) where the rules list says **"Client Status"** for the same rule. CONDITIONS cells are empty.
- **Sync Log:** back link "‹ Xero Settings"; Activity icon + h1 + subtitle "View and manage Xero sync entries."; 3 unlabelled filter comboboxes "All States" (All States, Pending, In Flight, Completed, Retrying, Dead Letter, Blocked (Trust), Reconcile Drift), "All Entity Types", "All Directions"; empty state h2 "No sync entries found".
- **AI history:** back link "‹ AI Configuration" (the tab's h1 is "Set Up AI"); h1 + subtitle; empty state h3 "No executions yet".

## Interactions (`03-interactions.cjs`, `sheet-interactions.png`; Alice 1440 light)

| Control | Result |
|---|---|
| KYC "Configure" | dialog "Configure KYC Verification" 512×380: Provider combobox, API Key (password), Test Connection [disabled], Cancel, Save [disabled], Close → **Escape** |
| Payment / AI provider comboboxes | options `None, noop, Stripe, PayFast` / `noop, anthropic` → Escape, value unchanged |
| MCP switch "Enabled" (false) | opens dialog **"Acknowledge POPIA responsibility"** (Cancel / "I acknowledge & enable" / Close) → **Cancel**; switch still false, API unchanged |
| Packs › Installed › Common Templates › Uninstall | alertdialog **"Uninstall Pack"** "This will remove 3 templates created by this pack. Only allowed because none have been used or edited. Continue?" Cancel / Uninstall(destructive) → **Cancel** |
| Packs › SA Legal Templates Uninstall (disabled) hover | tooltip "7 templates have been used to generate documents; 1 template has been cloned" |
| Automations "Toggle Investment Maturity Reminder" | true → **false** (toast "Rule toggled successfully", API enabled false) → **true** (toast, API true). Instant write, no confirmation |
| Notifications "Task Assigned" In-App | true → false → "Save preferences" → **"Preferences saved successfully."** `<p class="text-sm text-green-600 dark:text-green-400">`, no role / aria-live, ~115 ms → true → Save (~99 ms). API true before / after |
| Execution Log / Sync Log filters | opened, options recorded, Escape |
| Rule switch at 1024 | at scroll 0 it sits at x=1330 (viewport 1024; scroller right edge 992) — **off-screen** |

**Unreachable (recorded, not forced):** Xero "Disconnect" + its alertdialog "Disconnect from Xero?" (not connected — real Xero not connected); MCP "Revoke access" + alertdialog "Revoke MCP connector access?" (MCP not enabled); "Remove Key" / API-key delete and Payment "Remove"/disconnect (no key / provider); IntegrationCard / Payment / KYC "Enabled" switches (render only with a provider). "Connect to Xero" and "Send Test Email" were not clicked.

## Measurements (`measurements.json` → `at1024` / `at1440`)

- **Integrations @1024 (K-100 / F-664A-1):** 2-col grid in a 496 px panel → panel `scrollWidth` **556 vs clientWidth 496 (+60)**; **document h-overflow 28 px light / 32 px dark**. Every card is 236 px wide and its header row (title + status badge) spills: Accounting 59 px, AI Assistant 39, Document Signing 49, **Payment Gateway 75** ("Manual Payments Only"), KYC 60, MCP 13. At 1440: 0 overflow. Same for all 4 personas (also behind the forbidden-tab fallbacks).
- **Automations rules table (K-114):** table **1092 px** in a **496 px** scroller at 1024 (NAME cut 159 px; TRIGGER, ENABLED, LAST UPDATED, ACTIONS fully out of view) and **still 1092 in 864 at 1440** (ENABLED cut 38 px, LAST UPDATED / ACTIONS hidden). Document h-overflow 0 (the scroller contains it).
- **Execution Log table:** 897 px in 494 at 1024 (TRIGGERED AT cut 46; STATUS…DURATION hidden); 897 in 862 at 1440 (DURATION cut 35).
- **Packs @1024:** 3-col grid in 496 px → panel scrollWidth 503 (+7); cards 155 px wide, the badges spill 7 px (Document Templates) / 14 px (Automation Templates). 0 at 1440.
- **AI, MCP, Notifications, sub-pages:** 0 overflow at both widths, light and dark.
- **Contrast (light; dark passes everything probed):**
  - **K-33** "View Execution Log →" `rgb(0,163,143)` (teal-600) on `rgb(253,254,255)` = **3.13:1** (×2 on Automations); "View execution history →" / "Manage integrations →" on AI = **3.16:1**. Dark teal-400 on `rgb(7,8,11)` = 10.74.
  - **K-85** Notifications category headers `rgb(107,116,130)` (slate-500) on `rgb(248,249,251)` (slate-50) = **4.48:1** (all 8 bands; 12 px semibold uppercase). Dark 6.74.
  - **StatusBadges:** neutral `rgb(77,84,95)` on `rgb(239,241,245)` 6.76; success `rgb(0,130,54)` on `rgb(219,252,231)` **4.50**; warning `rgb(187,77,0)` on `rgb(254,243,198)` 4.52; lead 4.75; outline 10.35.
  - **Pack card h3** `rgb(7,8,11)` on `rgb(253,254,255)` 19.83.
  - Notifications save message `text-green-600` (same class as 665E's Acceptance, 3.22:1 there).

## SEL-COMPAT before (`sel-compat.json`, 1024 light)

| Spec selector | Alice | Carol / custom roles |
|---|---|---|
| `packs.spec.ts:111` `locator("h1").filter({hasText:"Packs"})` (strict) | 1 | 0 (notice → Integrations) |
| `packs.spec.ts:116/167/196` tab `/Installed/i` | 1 ("Installed (5)") | 0 |
| `[data-testid^=pack-card-]` visible / with an `h3` | 5 / 5 | 0 |
| `packs.spec.ts:120/169` `pack-card-legal-za` / `:122/198` its h3 | 1 / "SA Legal Templates" | 0 |
| `packs.spec.ts:172` legal-za "Uninstall" (Installed tab) | 1, disabled | — |
| `notification-preferences.spec.ts:55` h1 `/^Notification Preferences/` | 1 | 1 |
| `:94` `getByRole("tabpanel")` / `:96` text Tasks, Matters, Billing, Time Tracking (+ Other) exact | 1 / 1 each | — |
| `:99` switch "Time Reminders" / `:66` `#email-TIME_REMINDER` | 1 true / 1 false | — |
| `:74` button "Save preferences" | 1 | — |
| `automation-crud.spec.ts:46` / `automation-triggers.spec.ts:43` h1 "Automations" | 1 | 0 |
| `automation-crud.spec.ts:85` `automation-row` | 10 | 0 |
| `:88` row / switch "Toggle {name}" / triggers `:49` badge text — for the 4 legal-za rules | 1/1/1 each | — |
| `automation-crud.spec.ts:108` link `/View Execution Log/` (`.first()`) | 2 | 0 |
| `automation-crud.spec.ts:112` / `automation-triggers.spec.ts:31` h1 "Execution Log" | 1 | 0 (redirect) |
| `automation-triggers.spec.ts:55` combobox hasText "All Rules" / `:65` "All Statuses" | 1 / 1 | 0 |
| h1 "Sync Log" (sync-log) | 1 | 1 (denial) |
| h1 "Execution History" (ai/history) | 1 | 1 (denial; holder: content) |

`notification-bell.spec.ts` acts on the dashboard bell and `/notifications`, not on this family; it was run because it sits in `e2e/tests/notifications/`.

## Spec baseline (`specs-baseline.json`)

- **Command:** from the **main checkout** `frontend/` @ `d10aa6d3e` (the worktree has no node_modules; spec files identical), nvm Node 20: `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 SHELL=/bin/bash NODE_OPTIONS="" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/settings/packs.spec.ts e2e/tests/notifications/ e2e/tests/automations/`. `--config` is needed (there is no root `frontend/playwright.config.*`). Log: `/tmp/665F-before-raw/spec-run.log`.
- **Result: 17 passed, 0 failed, 0 skipped** (7.9 s, 5 workers): PACK-01 ×2, NOTIF-01 (bell) ×5, NOTIF-02 ×3, AUTO-01 ×4, AUTO-02 ×3.
- **Tree:** `git status --short` of the main checkout was identical before and after; `git clean -n frontend/test-results documentation/screenshots` listed nothing. Nothing to restore; pre-existing untracked / modified files untouched.

## Console (`console.json`)

204 page loads in `01` (4 personas × 51) plus the roles, interactions and crops runs: **0 errors, 0 warnings, 0 page errors, 0 hydration messages.**

## Defects seen (pre-existing, not fixed)

- **F-665F-B1 (layout, 1024; K-100 / F-664A-1):** Integrations card grid — card header badges spill 13–75 px past 236 px cards; panel scrolls 60 px, the document 28 / 32 px sideways. Crop `crop-defect-integrations-grid-1024.png`.
- **F-665F-B2 (layout; K-114):** Automations rules table 1092 px — in 496 px at 1024 (only NAME partly visible; the ENABLED switch at x=1330) and in 864 px at 1440 (ENABLED cut, LAST UPDATED / ACTIONS hidden). No scroll affordance. Crop `crop-defect-automations-table-1024.png`.
- **F-665F-B3 (layout):** Execution Log table 897 px (494 / 862 visible at 1024 / 1440). Crop `crop-defect-executions-table-1024.png`.
- **F-665F-B4 (layout, 1024):** Packs 3-col grid in 496 px; badges spill 7–14 px, panel +7. Crop `crop-defect-packs-grid-1024.png`.
- **F-665F-B5 (copy; K-115):** 13 raw enum names in Notifications "OTHER". Crop `crop-defect-notifications-other-1440.png`.
- **F-665F-B6 (copy; K-121):** automation pack cards titled with the raw packId, no description, badge "Legal - Za"; "1 templates". Crop `crop-defect-packs-installed-1440.png`.
- **F-665F-B7 (copy; K-122):** Execution Log TRIGGER = "CustomerStatusChangedEvent" vs "Client Status" in the rules list (crop B3).
- **F-665F-B8 (contrast; K-33 / K-85):** teal-600 links 3.13–3.16:1; notification category headers 4.48:1; success badge exactly 4.50.
- **F-665F-B9 (gating / member UX):** members and both custom roles see the Integrations manage cards (enabled provider comboboxes, Configure, Send Test Email, Configure Xero / Configure Connector links) while every integrations API answers 403; the Email Delivery card shows a red `role=alert` "Insufficient permissions for this operation" (crop `crop-defect-carol-integrations-alert-1440.png`). Effects of those controls not exercised.
- **F-665F-B10 (gating consistency):** AI_MANAGE opens `ai/history` by URL but not the AI tab (adminOnly); AUTOMATIONS opens nothing in the UI (tab adminOnly, executions admin-only redirect) although `/api/automation-rules` / `-executions` answer 200 for it. The in-page AI_MANAGE / AUTOMATIONS branches of the tab loaders are dead code. INTEGRATION_MANAGE / INTEGRATION_VIEW_SYNC_STATUS / FINANCIAL_RECONCILE can't be granted to a custom role in the UI at all.
- **F-665F-B11 (a11y):** 44 unnamed email switches (Notifications); unlabelled filter comboboxes on Execution Log (2) and Sync Log (3).
- **F-665F-B12 (metadata):** the 3 log sub-pages have the generic title "Kazi — Practice management, built for Africa".
- **F-665F-B13 (navigation):** the Sync Log is unreachable from the UI while Xero is disconnected (link only inside `XeroSyncSummary`); a non-admin following `automations/executions` lands on Integrations (redirect → adminOnly fallback) with no explanation of the redirect.
- **F-665F-B14 (feedback):** "Preferences saved successfully." is a bare `<p>` (no role / aria-live), green-600; rule toggles use a toast instead — two feedback models on one family.
- **Observation:** AI back link says "AI Configuration" while the tab h1 is "Set Up AI" (cold start).

## Files

- **Contact sheets** (cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page): `sheet-{xero,mcp,ai,notifications,packs,automations,sync-log,ai-history,executions}-alice.png` (packs: row 2 = Installed); `sheet-tabs-{carol,carol-cr-holder,carol-cr-lacker}.png` (one row per requested tab), `sheet-subpages-{…}.png` (sync-log, ai-history, executions); `sheet-interactions.png`; `sheet-roles.png`.
- **Crops:** `crop-defect-{integrations-grid-1024,automations-table-1024,executions-table-1024,packs-grid-1024,notifications-other-1440,packs-installed-1440,carol-integrations-alert-1440}.png`.
- **JSON** (single line): `discover-{pre,post}.json`, `measurements.json` (incl. `interactions`, `customRoles`, `crops`), `sel-compat.json`, `console.json`, `specs-baseline.json`.
- **Scripts** (as executed, in this order): `00-discover pre` → `01-capture alice` → `02-roles create` → `01-capture carol` → `02-roles assign "665F OBS AI Automations"` → `01-capture carol-cr-holder` → `02-roles assign "665F OBS No Integration"` → `01-capture carol-cr-lacker` → `02-roles assign Member` → `03-interactions` → spec run → `08-defect-crops` → `00-discover post` → `05-assemble`. All take `PHASE=before|after|after-fix`; `01-capture` checks Carol's current role name before a `carol-cr-*` run.
