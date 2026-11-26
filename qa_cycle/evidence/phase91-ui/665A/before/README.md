# 665A OBS-before — Settings › Firm I: General, Roles, Compliance, Features, Subscription (task 665.1)

Captured 2026-11-26 (14:43Z–15:05Z) on the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090). Playwright library (headless Chromium 1.59.1 from the main checkout's `frontend/node_modules`; the worktree has none), not the MCP. DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`. Node 20.11.1 (nvm).

## Stack observed

- `docker inspect e2e-frontend --format '{{.Created}}'` → `2026-11-26T14:18:53.865528923Z` (container); image `sha256:4fefc41e4e76…`, created `2026-11-26T14:03:40Z`. Compose label `working_dir` = `worktree-epic-664B/compose` (that worktree has since been removed).
- 664B's after-fix README records that image as built from `worktree-epic-664B` @ `9ef3ae9c9` (committed 14:00:52Z). PR #1869 head `97ca159f2`; its branch commits after the image (`1e5a0b548`, `97ca159f2`) are evidence only.
- `git log --format='%h %cI' -1 d90376640` → `d90376640 2026-11-26T16:34:23+02:00` (the 664B squash-merge).
- `git diff --stat <c> d90376640 -- frontend packages compose` is **empty** for `9ef3ae9c9`, `1e5a0b548` and `97ca159f2`. `d90376640..02eab076f` is also empty for those paths. So the frontend observed equals `main` @ `02eab076f` (this worktree's base), assuming the 664B worktree had no uncommitted frontend changes at build time. That cannot be proven after the fact. (`7a41694fd..d90376640` is not empty, 4 files, but the image is younger than `9ef3ae9c9`.)
- Backend and data: the container was created at 14:18Z, so this is a fresh e2e seed plus 664B's gate runs. Org modules enabled: `court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing`. Toggleable (Features tab) are only `information_requests` and `bulk_billing`.

## Personas (`GET /api/me/capabilities`)

| Key | Login | role / isAdmin / isOwner | Capabilities |
|---|---|---|---|
| `alice` | `/mock-login` "Sign In" | Owner / **false** / true | 29 (all) |
| `carol` | `mock-auth-token` cookie (`e2e/fixtures/auth.ts`) | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST |
| `carol-cr-holder` | Carol's cookie while she held custom role **"665A OBS Oversight"** | 665A OBS Oversight / false / false | TEAM_OVERSIGHT |
| `carol-cr-lacker` | Carol's cookie while she held custom role **"665A OBS No Oversight"** | 665A OBS No Oversight / false / false | PROJECT_MANAGEMENT |

## Gating (from `frontend/lib/settings-nav.ts:49-112` + the tab loaders)

| Tab id (`?tab=`) | Label | Registry gate | Loader's own check |
|---|---|---|---|
| `general` | General | none | `general.tsx:14`: non-admin gets h1 + Org Documents (read-only) only |
| `roles` | Roles | `adminOnly` | `roles.tsx:18`: `PermissionDenied` |
| `compliance` | Compliance | `adminOnly` | `compliance.tsx:14`: inline denial |
| `features` | Features | `adminOnly` | `features.tsx:13`: `canManage` → read-only status text |
| `subscription` | Subscription | none | none |

**No 665A tab is capability-gated.** "adminOnly" means `isAdmin || isOwner`, and both are derived from the org role (`OrgRoleService.java:124-125`, `"admin".equals(orgRole)`), never from capabilities. A custom role therefore cannot open Roles, Compliance or Features. The only capability-gated Firm tab is **Audit trail** (`requiredCapability: "TEAM_OVERSIGHT"`, 665B's tab), so it was used as the with/without capability for the two custom roles.

## Custom-role decision

- **Created through the UI** (Alice, Settings › Firm › Roles › "New Role", `02-roles.cjs create`): "665A OBS Oversight" (Team Oversight) and "665A OBS No Oversight" (Matter Management). The mock IDP has only Alice, Bob and Carol, so there is no spare user to assign them to.
- **Temporary assignment to Carol through the UI** (Team › Carol's row › member panel › Role › Save, `02-roles.cjs assign`):
  - Carol → Oversight, then 01-capture `carol-cr-holder`
  - → No Oversight, then 01-capture `carol-cr-lacker`
  - → **restored to "Member"** with no overrides
- Each assignment was verified by `GET /api/members/{id}/capabilities` and Carol's own `/api/me/capabilities`. No e2e suite was running; `ps` showed only playwright-mcp processes. After the restore Carol matches the pre-state exactly: `discover-pre.json` vs `discover-post.json` → personas, members, settings, modules, retention policies, subscription and org documents are all **SAME**.
- **Left on the stack (documented mutation):** the two custom roles (0 members each). The only diff in `orgRoles` is these two. They were kept for the 665A after-capture, and `02-roles.cjs create` is idempotent. No spec asserts the custom-roles empty state (`roles-capabilities.spec.ts` uses run-unique names and is `fixme`). Delete them via Roles › "Delete …" if a clean list is wanted.
- **Result:** both custom roles get exactly Carol's gate on the five 665A tabs (General + Subscription rendered; Roles, Compliance and Features → notice + General). The only difference is the tab bar: the holder sees **General, Audit trail, Subscription**; the lacker and Carol see **General, Subscription**.

## Scripts (`scripts/`, run in this order; nvm node 20; each takes `PHASE=before|after`)

| Script | What |
|---|---|
| `lib.cjs` | Shared helpers: Playwright resolution, `$RAW=/tmp/665A-$PHASE-raw`, `apiGet`, `TABS`. Re-exports `661B/walk/scripts/common.cjs`. |
| `00-discover.cjs <label>` | GET-only baseline → `discover-<label>.json`. Run as `pre` (before anything) and `post` (after the saves and the Carol restore). |
| `01-capture.cjs <persona>` | 5 tabs × 1024/1440 × light/dark, viewport + full-page shots. Compact probe per variant, full probe at 1440 light, SEL-COMPAT at 1024 light, legacy routes. One click only: Roles › "Capability Reference". |
| `02-roles.cjs create \| assign "<role>"` | Custom-role creation and the Team-page role assignment (UI only). |
| `03-saves.cjs` | One save per save model (Alice), FICA probe, register probes K-103/K-104/K-106. Verifies the restore with `GET /api/settings`. |
| `04-assemble.cjs` | Merges the raw run files into `measurements.json`, `sel-compat.json`, `console.json`, `saves.json`, then builds the 17 sheets. |

Executed: `00 pre` → `01 alice` → `01 carol` → `02 create` → `02 assign "665A OBS Oversight"` → `01 carol-cr-holder` → `02 assign "665A OBS No Oversight"` → `01 carol-cr-lacker` → `02 assign "Member"` → `03` → `00 post` → `STACK="…" 04`. All exited 0, with 0 failures.

- `03-saves.cjs` received one edit before its first run: the portal step waits for the previous message to clear. It was never edited after running.
- `00-discover.cjs`'s output filename was parameterised before its first run.

## Sheets

| File | Contents |
|---|---|
| `sheet-{general,roles,compliance,features,subscription}-alice.png` | Alice: cols 1024 light / 1024 dark / 1440 light / 1440 dark; row 1 viewport, row 2 full page. Roles adds the expanded Capability Reference (1440 light, full page). |
| `sheet-{…}-carol.png` | Carol, same grid |
| `sheet-{…}-custom-role.png` | Rows 1–2: Carol as "665A OBS Oversight" (viewport / full). Rows 3–4: as "665A OBS No Oversight". Same columns. |
| `sheet-roles-ui.png` | Create Role dialog at 1280×720 and 1440×900 (2 filled), Roles tab with both custom roles, Team › Carol's member panel for each of the 3 assignments |
| `sheet-saves.png` | Element crops taken the moment each save message appeared (7), plus the FICA fieldset in light and dark |

JSON (single line): `discover-pre.json`, `discover-post.json`, `measurements.json` (per persona → `tabs` full probe @1440 light, `variants` compact ×4, `capabilityReference`; `customRoles`; `kinks`), `sel-compat.json`, `console.json`, `saves.json`.

## Key facts

### Per tab × persona (1440 light; h1 identical in all 4 variants)

| Tab | Alice (gate: rendered) | Carol / both custom roles |
|---|---|---|
| general | h1 **"General"** + HelpTip button "Help: Organization settings". h2: Currency, Tax Configuration, Branding, Letterhead, Client Portal, Org Documents; h3 "No organization documents yet". 6 hand-rolled `div.rounded-lg.border` cards, 0 `data-slot=card`, 0 tables. Testids `default-currency`, `org-documents-section`. Buttons: Help, currency combobox, tax-inclusive switch (named only through `<label for>`), Upload Logo, **Save Settings**, **Save letterhead** (disabled while pristine), cadence combobox "Bi-weekly", Upload Document. Doc height 3 345 (1024: 3 609). | rendered. h1 "General" + HelpTip. h2 Org Documents, h3 "No organization documents yet". 1 card; testid `org-documents-section` |
| roles | h1 **"Roles & Permissions"**, no HelpTip. h2 System Roles, h3 Owner / Admin / Member, h2 Custom Roles. 4 hand-rolled cards (3 system + the dashed empty state "No custom roles yet…"). 0 tables until "Capability Reference" is expanded; then 1 `<table>` with no `data-slot`, headers Capability / Description, 12 rows. Buttons: New Role, Capability Reference. | **forbidden notice** "You don't have access to Roles — showing General instead." + the General (non-admin) panel |
| compliance | h1 **"Compliance"**. h2: Matter retention, General Settings, Retention Policies, Compliance Packs. 4 hand-rolled cards. 1 table (`data-slot=table`): RECORD TYPE / TRIGGER EVENT / RETENTION DAYS / ACTION / ACTIVE / ACTIONS, 2 rows (CUSTOMER/CUSTOMER_OFFBOARDED 1825 FLAG, AUDIT_EVENT/RECORD_CREATED 2555 FLAG), 862 in 862. Testids: `fica-review-intervals`, `fica-review-months-{low,medium,high}`. Buttons: Save retention period, Save Settings, Add Row, Toggle active / Save / Delete ×2, Run Retention Check, and 5 compliance-pack buttons ("generic-onboarding v1.0.0 Applied 26 Nov 2026" …). The pack list has no table until a pack is opened (not clicked). Retention-row selects and inputs have **no label**. | forbidden notice "…Compliance…" + General |
| features | h1 **"Features"**, no h2. 2 `data-slot=card` (`feature-card-information_requests`, `feature-card-bulk_billing`), switches "Toggle Information Requests" / "Toggle Bulk Billing Runs", both on. | forbidden notice "…Features…" + General |
| subscription | h1 **"Billing"** + HelpTip "Help: Billing". Chips "Trial", "Manual". 1 `data-slot=card` "Managed Account" with "Your account is managed by your administrator." | rendered, identical |

- `<main id="main-content">` = 1 and h1 in `#main-content` = 1 on every tab, for every persona and variant.
- Tab bars (`tablist "Firm settings"`):
  - Alice: 8 tabs
  - Carol / lacker: General, Subscription
  - holder: General, Audit trail, Subscription
- Horizontal page overflow: only **Roles at 1024 for Alice**, 81 px light and 86 px dark (K-104).
- Unreachable branches (the tab gate falls back to General first): `roles.tsx`'s `PermissionDenied`, `compliance.tsx`'s inline denial, and `features.tsx`'s read-only branch ("Only admins and owners can change these settings." + `feature-status-*`). They are reachable by no persona through `/settings/firm?tab=…` or the legacy routes, so `feature-status-*` = 0 everywhere.

### Save models (`saves.json`, `sheet-saves.png`; Alice 1440 light; 0 stalls, 0 console messages, all server actions 200)

| Model | Action (no-op / restored) | Message (element) | Time |
|---|---|---|---|
| General: page-level **"Save Settings"** (`general-settings-form`) | Brand Color field cleared first (it showed `#000000` for a stored `null`), then saved | **"Settings saved successfully."** (`p.text-sm.text-green-600`, no role, auto-clears after 3 s). Two server actions (settings PUT + tax PATCH). | 1 419 ms |
| General: per-card **"Save letterhead"** | one space typed into "Address line 1" (trimmed to `null` = stored) | **"Letterhead saved."** (`p[role=status].text-teal-600`); button disabled again after the save | 1 983 ms |
| General: instant Select "Portal digest cadence" | Bi-weekly → Weekly → Bi-weekly | **"Portal digest cadence updated."** (`p.text-xs.text-teal-600`, no role) both times | 264 ms / 2 616 ms (the second includes waiting out the 3 s auto-dismiss of the first) |
| Compliance: per-card **"Save retention period"** | value 5 unchanged | **"Matter retention period updated."** (`p.text-sm.text-green-600`) | 110 ms |
| Features: instant Switch "Bulk Billing Runs" | on → off → on | **no message by design** (optimistic switch + `router.refresh`); `aria-checked` true → false → true | 951 / 950 ms |

- **Not clicked:** Compliance › General Settings "Save Settings". Dormancy and deadline are `null` in the store, and the form would persist its 365 / 30 display fallbacks (`compliance.tsx:80-81`; see `../found.md` F-665A-2).
- Restore proof: `saves.json` → `restored: true` (`GET /api/settings` + modules identical before and after), and `discover-post.json` matches `discover-pre.json`.

### Phase 93 FICA review-interval inputs (Compliance › "General Settings" card)

- Container: `fieldset[data-testid="fica-review-intervals"]`, legend "FICA review intervals".
- Description: "Your firm's choice in its Risk Management and Compliance Programme (RMCP). Changes apply to clients taken on after you save."
- Inputs:

| Label | id = testid | Value |
|---|---|---|
| "Low risk (months)" | `fica-review-months-low` | **36** |
| "Medium risk (months)" | `fica-review-months-medium` | **24** |
| "High risk (months)" | `fica-review-months-high` | **12** |

- Each input is `type=number`, `inputMode=numeric`, min 1, max 120, step 1.
- Error testids `fica-review-months-{key}-error` appear only when the value is invalid. None were shown.
- Saved by the card's single "Save Settings", together with dormancy and deadline.

### SEL-COMPAT before (`sel-compat.json`, 1024 light, every tab × persona)

- **`general-settings.spec`:**
  - General as Alice: `/^General/` h1 = 1, "Save Settings" = 1, h2 "Currency" = 1, label "Brand Color" exact = 1, "Document Footer Text" = 1.
  - Carol: h1 = 1, the rest 0.
- **`day-00-firm-setup`:** `main` = 1; `getByText(/ZAR/)` = 1 (strict OK); `getByLabel("Brand Color")` non-exact = 1 (strict OK).
- **`roles-capabilities` (Alice, Roles):**
  - h1 "Roles & Permissions" = 1, "System Roles" = 1, h3 Owner / Admin / Member exact = 1 each, the "Built-in roles…" text = 1, "Capability Reference" button = 1, "New Role" = 1.
  - cell "Financial Visibility" exact = **0 collapsed → 1 after the click** (visible).
- **`retention-card` / `retention-card-scheduled-deletion`:** 0 on every tab. The component is `components/legal/retention-card.tsx` on the closed-matter overview (`day-75-85-closure.spec.ts:293,316`), so it is not part of the 665A DOM.
- **`rbac-capabilities` routes (`legacyRoutes`):**
  - `/settings` → `/settings/firm` (h1 General); `/settings/roles` → `/settings/firm?tab=roles`; `/settings/rates` → `/settings/fees?tab=tariffs`. 0 "Something went wrong" anywhere.
  - Carol on `/settings/roles`: the "don't have access" notice matches `/don.t have access|permission/i`.
  - Note: the same regex also matches **Alice's** page (h1 "Roles & Permissions"), so the member-denial assertion would pass without a denial.
  - Old routes `/settings/{general,compliance,features,billing}` redirect to the matching `?tab=`.

### Register rows owned by 665A, re-observed (`measurements.json` → `kinks`, `customRoles`)

- **K-102 (HIGH):** the Create Role dialog is **986 px** tall, `overflow-y: visible`, `max-height: none`.
  - 1280×720: top −133, bottom 853, Create button at 792–828 is off-screen, title off-screen.
  - 1440×900: top −43, bottom 943, Create at 882–918 is off-screen.
  - Both roles were created with `dispatchEvent("click")` on Create (as 662A did for K-47).
- **K-103:** the Owner and Admin cards say "No default capabilities", while `/api/org-roles` returns 22 / 20 capabilities.
- **K-104:** the Member card at 1024: the badges are `nowrap`, "GENERATE STATEMENT OF ACCOUNT" ends at x = 1 105 against a card right edge of 992. Page overflow is 81 / 86 px (the register measured 1 121 and 97 / 102 before 664A's rail change). The raw enum labels GENERATE STATEMENT OF ACCOUNT, VIEW LEGAL and MANAGE DISBURSEMENTS are still shown.
- **K-106:** tab "Subscription" but h1 "Billing"; chips "Trial" / "Manual"; "Your account is managed by your administrator."

### Console (`console.json`)

108 page loads across the 4 personas, plus the roles and saves runs: **0 errors, 0 warnings, 0 hydration messages.**

## Anything odd (not fixed)

1. **Brand colour null → `#000000`** in the General form; a plain "Save Settings" would persist black. Recorded in `../found.md` as F-665A-1.
2. **Dormancy null → form shows 365, while the backend uses 90**; saving the Compliance card would persist 365. Recorded in `../found.md` as F-665A-2.
3. Alice is `isAdmin: false, isOwner: true`, so "Save retention period" is enabled for her (`canEdit={caps.isOwner}`). The admin read-only state (Bob) was not captured; it was not in the brief.
4. The retention-policy rows' selects and inputs have no accessible label. This is the before-state for 665A's DataTable conversion.
5. The tax-inclusive switch has no `aria-label`. Its name comes from `<label for="tax-inclusive">`, which my button-name probe does not read, so `buttons` shows `""`. `controls` has the label.
6. Erratum: the note string in `03-saves.cjs` (and so `saves.json` → `complianceSettingsSaveNotClicked`) cites `compliance.tsx:79-80`; the fallback lines are **80-81**. The script was not edited after running.
