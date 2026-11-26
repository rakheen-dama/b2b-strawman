# 665F OBS-after: Settings › Connections on PR #1875 @ `c8dd227ea` (branch head `f3c298e43`)

- **When:** 2026-11-26, 23:22Z to 23:40Z. Rebuild 23:22:50Z–23:23:45Z; discover-pre 23:24:06Z; captures 23:24–23:29Z; interactions 23:29:27Z; spec run 23:30:03–23:30:13Z; crops 23:30:30Z; acceptance probes 23:33:14Z; K-114 focus diagnosis 23:35:15Z; MCP round trip 23:36:07–23:36:19Z; discover-post 23:36:32Z.
- **Verdict: FAIL on one item (K-114 keyboard focus).** Pressing Space on a rule switch drops focus to `<body>`. The cause is **pre-existing**: the switch is disabled while the toggle is pending, and the same code was there before this PR. Every other item passes. The gating diff is empty (0 of 204). Specs: 17/17. Console: 0.
- **Stack:** the same e2e mock-auth stack and data as OBS-before (frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026), org `e2e-test-org`. Only the frontend was rebuilt. Nothing was reseeded or torn down, and no SQL was run.
- **Browser:** the Playwright library (headless Chromium) from the worktree's `frontend/node_modules`. Not the MCP.
- **Settings:** DPR 1, reduced motion, timezone Africa/Johannesburg, rail expanded, theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **Raw files:** 204 raw PNGs in `/tmp/665F-after-raw/`, outside the repo. Committed: 25 PNGs (19 contact sheets + 6 crops), 7 JSON files, 11 scripts and this README.

## Provenance (step 1)

- **Worktree:** `/Users/rakheendama/Projects/2026/worktree-epic-665F`. `git rev-parse HEAD` = **`f3c298e432214422183f79b3152b316c6ee11203`**, which is product `c8dd227ea` plus one docs-only commit (`kinks-register.md`). Before the build, `git status --short frontend packages compose` was **empty**. The only ignored files in `frontend/` were `.next/`, `next-env.d.ts` and `tsconfig.tsbuildinfo`, and `.dockerignore` excludes `frontend/.next`.
- **Rebuild:** `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend`, run from the worktree with nohup and a done-marker. It ended with `exit=0`.
- **`docker inspect e2e-frontend`:**
  - created **`2026-11-26T23:23:45.749Z`**
  - image `sha256:7b5c3570c15a2d431b25908b917fffc10121811338efd4ce2fdb5e7ff440d9ec`
  - compose `working_dir` = **`/Users/rakheendama/Projects/2026/worktree-epic-665F/compose`**
  - healthy, and `GET /` returned 200 at 23:23:59Z.
  - Before the rebuild it was the 665E build (`78aedbc96084…`, `worktree-epic-665E/compose`).
- **Backend untouched:** `e2e-backend` is still created `2026-11-26T21:57:58.420Z` with image `sha256:e7fd89e47741…`. Compose re-resolved the backend image (from cache), but the container was **not** recreated.
- **The new build is the one served:** the Notifications h1 reads "Notifications", the MCP h1 reads "MCP", and the rules table has `data-slot="table"` (DataTable).
- **Same data:** `discover-pre.json` (after) equals OBS-before's `discover-post.json` field for field, apart from `capturedAt`:
  - 10 executions, 1 page
  - roles Owner(1), Admin(1), Member(1), "665F OBS AI Automations"(0), "665F OBS No Integration"(0)
  - Carol is Member
  - MCP is off, with no consent

## What was run (in order, all with `PHASE=after` from `after/scripts/`)

`00-discover pre` → `01-capture alice` → `02-roles create` (idempotent; both roles exist: `15c0df12-…` = `[AUTOMATIONS, AI_MANAGE]`, `7c993e75-…` = `[CUSTOMER_MANAGEMENT]`) → `01-capture carol` → `02-roles assign "665F OBS AI Automations"` → `01-capture carol-cr-holder` → `02-roles assign "665F OBS No Integration"` → `01-capture carol-cr-lacker` → `02-roles assign Member` → `03-interactions` → spec run → `08-defect-crops` → **`06-acceptance`** → **`06b-k114-focus-diagnose`** → **`07-mcp-roundtrip`** → `00-discover post` → `05-assemble` → **`09-verify`**.

- `00`, `01`, `02`, `03`, `05`, `08` and `lib.cjs` are **byte-identical copies** of `../before/scripts/`. They already take `PHASE` from the environment, so they needed no edit.
- Because the copies are unchanged, the old K-124 selectors in `01`'s `selCompat` stay as they were. They now count 0, which is the expected change. The new selectors are counted in `06`.
- **New scripts:** `06` (acceptance probes), `06b` (focus diagnosis, plus restoring the rule), `07` (MCP round trip) and `09` (the before/after compare, `verify.json` and 2 sheets).
- **Replay crops:** `08` re-shoots the regions of OBS-before's 7 defect crops. `09` renames the 5 it found to **`crop-replay-*.png`**, because they show the after state, not new defects.
  - "notifications-other" is **missing**: the OTHER group is gone (K-115).
  - "carol-integrations-alert" is **missing**: its `ancestor::*[@data-slot="card"]` locator no longer matches, because SettingsSection sets `data-slot="settings-section"`. The alert itself is still there: `role=alert` "Insufficient permissions for this operation" (K-113 backlog, unchanged).

## Acceptance (step 3) — `verify.json`

| Item | Result | Key numbers |
|---|---|---|
| **Layout** | **PASS** | h1 in `#main-content` = 1, `main#main-content` = 1 and `<main>` = 1 on all 164 surface × viewport × theme variants (4 personas). The h1s are: Integrations "Integrations", MCP **"MCP"** (was "Claude / MCP Connector"), AI "Set Up AI" (unchanged), Notifications **"Notifications"** (was "Notification Preferences"), Packs "Packs", Automations "Automations", Sync Log "Sync Log", AI history "Execution History", Execution Log "Execution Log". |
| **K-100 / F-664A-1** | **PASS** | At 1024, Integrations has a 496 px panel (504 in dark) and a 496 / 504 px `@container` with a **1-column** grid. 0 elements spill past the panel edge, and `scrollWidth` = **1024** in light and dark (before: 28 / 32 px sideways). AI is the same: 496 / 504 px, 1 column, 0 spill, 1024 / 1024. At 1440, Integrations has an 864 / 872 px container with **2 columns** (same as before) and AI has **3 columns**; 0 spill. Breakpoint `@3xl` = 768 px against measured containers of 496–504 and 864–872 px. |
| **K-114** | **FAIL** (layout PASS, focus FAIL) | **Layout:** the table is on DataTable. At 1024 it is **542 px in a 494 px scroller** (before 1 092 in 496); only ACTIONS is cut (~48–51 px). At 1440 it is **862 in 862** (before 1 092 in 864). The Name cell has `white-space: normal` and `max-width: 448px`; it is 109 px wide at 1024 (description up to 8 lines) and 428 px at 1440 (2 lines). The **Enabled switch** sits at x = 784 at scroll 0 and is inside the scroller (before x = 1330, off-screen); at the scroll end (scrollLeft 48) it is still inside. **Focus:** Tab ×4 from the h1 reaches "Toggle Investment Maturity Reminder", then Space toggles it. `document.activeElement` is **`body`** 60 ms later and after the write settles. `06b` shows it is the **same DOM node** (no remount). `blur` / `focusout` fire with `disabled === true`, and then `disabled` is set (pending transition). See F-665F-A1. |
| **K-33** | **PASS** | Light: "View Execution Log →" ×2, "View execution history →" and "Manage integrations →" are all `rgb(0,119,106)` on `rgb(253,254,255)` = **5.41:1** (before 3.13 / 3.16). Dark: Automations links teal-400 on `rgb(7,8,11)` = 10.74 (unchanged). AI links on the card background `rgb(20,23,28)` = 9.63. |
| **K-85** | **PASS** | Notification category headers are `rgb(77,84,95)` on `rgb(248,249,251)` = **7.25:1** in light (before 4.48), the same across all 9 bands. Dark: `rgb(152,159,170)` on `rgb(20,23,28)` = 6.74 (unchanged). |
| **K-115** | **PASS** | The bands are TASKS, MATTERS, COLLABORATION, BILLING, CLIENT REQUESTS, TIME TRACKING, SECURITY, **DIARY, TRUST**, with **no OTHER**; 44 rows. **13 new labels:** Diary = Court date reminder, Prescription warning. Trust = Trust payment awaiting approval / approved / rejected, Trust reconciliation overdue, Trust investment maturing, Trust approval waiting too long, Trust reconciliation completed. Matters = Matter closed, Matter reopened. Billing = Collection escalated, Daily cash summary. **The 31 existing labels are byte-identical** to before (diffed by `inapp-<TYPE>` id; 0 changed, 0 moved category). The switches line up: in-app x = **823** and email x = **919** for all 44 rows at 1024 (1239 / 1335 at 1440). Document overflow at 1024 = 0. |
| **K-121** | **PASS** | The legal-za badges are `18 templates / Document Templates / **Legal (South Africa)**`, and automation-legal-za is also "Legal (South Africa)" (before "Legal - Za"). The exact text "Kazi Packs — extend your workspace with pre-built content" now appears **0** times; the page-header description "Extend your workspace with pre-built content from Kazi Packs." stays. All 5 Available and 5 Installed cards have an `h3` and the `pack-card-<id>` testids. The automation packs are still titled with the raw packId (backend, backlog). |
| **K-122** | **PASS** | The Execution Log Trigger column shows only **"Client Status"** on all 10 rows (before "CustomerStatusChangedEvent"). The detail sheet's Trigger row also says "Client Status"; no `…Event` class name appears in it. |
| **Execution Log sheet** | **PASS** | There are 10 Rule-cell `<button>`s. **Click** on "Matter Onboarding Reminder" opens "Execution Detail". **Keyboard:** Tab ×4 from the h1 focuses that button, and Enter opens it. A click on the **Status** or **Duration** cell of the same row does **not** open it (the known narrowing, F-665F-3). No pager text and Previous / Next = 0/0, as before (1 page). Note: after Escape, focus goes to `<body>`, not back to the Rule button (O-2). |
| **Log paging** | **PASS** (≥2 pages not reachable) | Sync Log has 0 rows ("No sync entries found"); API 0 / 0 pages. AI history has 0 rows ("No executions yet"); 0 / 0. Execution Log has 10 rows; API totalElements 10, totalPages 1. No pager on any of the three, as before. Paging at 2 or more pages **can't be observed in this seed**; no data was made up. |
| **Destructive sections** | **PASS** | **Disconnect Xero:** unreachable (Xero not connected; the `/connection` endpoint returns 404; 0 Disconnect buttons). **MCP:** enabled through the UI consent dialog (`07`). The destructive section is untitled: border `border-destructive/40` = destructive at 40 % alpha in both themes, with no header wash because there is no header. **"Revoke access"** is `lab(48.45 77.43 61.55)` in light and `lab(63.71 60.75 31.31)` in dark, **= `var(--destructive)` in both** (red, not grey). The alertdialog "Revoke MCP connector access?" was opened and **cancelled** in light and in dark; the API stayed enabled. Then MCP was **disabled through the UI** (switch → same dialog → "Revoke access"): `effectivelyEnabled` false. **Uninstall Pack** alertdialog opened and cancelled (`03`). |
| **Dark ghost-destructive** | **PASS** | The only visible outline button with `text-destructive` is **"Revoke access"**, which equals the destructive token in dark. **Uninstall** has no `text-destructive` class: it is a neutral `outline` button, foreground in light and `lab(100 0 0)` in dark, unchanged from before. So it is not in this class. |
| **Gating** | **PASS** | **0 diffs out of 204** comparisons: url, notice, selected tab, tab bar, rail, HTTP status and gateOutcome for 164 persona × surface × variant captures, plus 40 legacy redirects (4 personas × 10). There are also 0 diffs on the in-page denial selectors (sync-log, ai-history, integrations, `You don't have access`). Alice sees all 6 tabs. Carol and both custom roles see **Integrations \| Notifications**. The cr-holder still opens `ai/history` by URL. `automations/executions` still redirects non-admins. |
| **SEL-COMPAT** | **PASS** | Out of 596 key comparisons, only 3 keys changed (9 persona × surface entries), all expected: (1) h1 `/^Notification Preferences/` 1 → 0 for all 4 personas, while the new `/^Notifications/` = 1 (spec line 55 as edited); (2) h1 "Claude / MCP Connector" 1 → 0 for Alice, while "MCP" exact = 1 and "Claude / MCP Connector" is now an `h2`; (3) the extra OBS probe `tabpanel getByText('Other')` 1 → 0 for all 4 personas. That is K-115's intended effect, and spec line 96 only checks Tasks / Matters / Billing / Time Tracking. All other spec selectors are unchanged (`pack-card-*`, h1 "Packs" / "Automations" / "Execution Log", `automation-row` ×10, "Toggle {name}", the "All Rules" / "All Statuses" comboboxes, "View Execution Log" ×2, "Save preferences", `#email-TIME_REMINDER`). |
| **Console** | **PASS** | 204 page loads in `01` plus the roles, interactions, acceptance, diagnosis and MCP runs: **0** messages (0 errors, 0 warnings, 0 page errors, 0 hydration). |
| **Specs** | **PASS** | Run from the **worktree** `frontend/` (so the edited `notification-preferences.spec.ts` is used): **17 passed, 0 failed, 0 skipped** (7.7 s), the same as before (17). |

### Other measurements (not acceptance gates)

- **Execution Log table:** 679 px in 494 at 1024 (before 897). CONDITIONS is cut by 28 px, and ACTIONS / DURATION are out of view. At 1440 it is 862 in 862 (before 897 in 862). The 1024 residue is the D-44 class (found.md F-665F-B3).
- **Packs @1024:** unchanged (panel 503 / 496, badges spill 7–14 px). This is F-665F-B4, which is out of scope.
- **Notifications save message:** now `text-success-foreground` (`lab(36.5 -36.5 24.4)`) in the SettingsActionBar. Still no role or aria-live (F-665F-B14).
- **Rule switch at 1024** (`03`): x = 784, visible in the scroller (before 1330, off-screen).
- **Titles:** tabs read "Connections — {Tab} · Settings | Kazi". The 3 sub-pages still use the generic title (F-665F-B12).

## Defects and observations

- **F-665F-A1 (FAIL, K-114 focus; pre-existing mechanism):** a keyboard Space on a rule switch toggles it, but focus falls to `<body>`. A second Space then does nothing.
  - **Hypothesis:** `frontend/components/automations/rule-list.tsx:140`, `disabled: !canManage || isPending`, which reaches the Switch at `:103`. It disables the focused switch during `startTransition`, and Chromium blurs a focused control that becomes disabled.
  - `06b` shows `blur` → `focusout` (relatedTarget null) with `disabled === true` on the **same node** (no remount). So the comment at `:62-64` ("focus stays on it") holds for remounts but not for this.
  - **It is pre-existing:** the before code had `disabled={!canManage || isPending}` (`839d21165:frontend/components/automations/rule-list.tsx:107`). The before OBS never tested the keyboard path.
  - **Possible fix:** don't disable the row's switch while pending (keep `!canManage`; guard re-entry in `handleToggle`), or use `aria-disabled`.
  - Crop: `crop-defect-k114-focus-lost-1440.png` (the switch is OFF with no focus ring). Diagnosis tile: `sheet-acceptance.png`.
- **O-1 (layout, 1024):** the rules-table Name column gets only **109 px** at 1024. Descriptions wrap to as many as 8 lines, so the rows are tall (see `sheet-acceptance.png`, K-114 1024 tile). ACTIONS is still 48 px off. `max-w-[28rem]` is only a ceiling, and the other columns take their natural width (D-44 class; column hiding is off, `columnVisibility={false}`).
- **O-2 (a11y):** when the Execution Detail sheet closes (Escape), focus goes to `<body>`, not back to the Rule button. The sheet is controlled with no Radix trigger (`components/automations/execution-log.tsx` `setDetailOpen`). Before, there was no keyboard path at all.
- **O-3 (visual):** while a rule toggles, `aria-checked` flips true → false → true (`06b` log, about 10 ms). The optimistic state is cleared (`rule-list.tsx:45-50`) before the refreshed `rules` prop arrives. This is pre-existing logic.
- **O-4:** the "Revoke MCP connector access?" confirm button is `variant=default`, not destructive. The dialog was not changed by this PR.

## Data / role changes left in place

| What | How | Final state |
|---|---|---|
| Roles "665F OBS AI Automations" `15c0df12-296b-4c75-8fd4-7a6bd0937d37`, "665F OBS No Integration" `7c993e75-e8f1-44c1-a42f-cfd6297a7140` | already present (create skipped) | **left**, 0 members |
| Carol: Member → AI Automations (23:26:55Z) → No Integration (23:28:05Z) → **Member** (23:29:16Z) | Team UI | restored: Member, `overrides: []`, the same 5 capabilities |
| Rule "Investment Maturity Reminder" `68d17580-…` | `03`: mouse OFF → ON. Spec: Disable / Enable. `06`: keyboard Space OFF (the second Space hit `<body>`). `06b`: keyboard Space ON | **enabled = true**. `updatedAt` 22:53:15Z → **23:35:17Z** |
| Notification pref `TASK_ASSIGNED` in-app | `03`: OFF + Save, ON + Save | true / email false (44 prefs, unchanged) |
| Notification pref `TIME_REMINDER` email | spec NOTIF-02 (enable → disable, restore) | false (unchanged) |
| **MCP connector** | `07`: enabled through the consent dialog (23:36:10Z), then revoked through the UI (23:36:17Z) | `effectivelyEnabled` false (as before), but now **with history**: the latest consent is `REVOKED` (`popia-egress-v1`, by Alice `2f728e3b-…`), and the `MCP` integration row has `providerSlug` **"kazi"**, enabled false (was null). The MCP tab shows the same "Disabled" state. |
| Matter **"Bell Matter qfkv"** `dbd59b84-acbb-4de7-891c-ba63bafa1814`, plus tasks "Bell Task Opened qfkv" / "Bell Task Listed qfkv" assigned to Carol (and their notifications, read) | spec NOTIF-01 `beforeAll` | left. OBS-before's "Bell Matter exfa" `a230d129-…` is also still there. |
| "Power of Attorney" pack template | spec PACK-01 (edit → revert) | reverted |

Between discover-pre and discover-post, the only diffs are the MCP row and consent fields above and the rule's `updatedAt`. Nothing destructive was confirmed apart from the MCP revoke, which was authorised. Every other dialog was cancelled or escaped. All 5 packs are still installed.

**Tree:** the spec run created `frontend/test-results/.last-run.json`, a new ignored directory that wasn't there before. It was removed. `git status --short` now shows only this untracked `after/` directory. No tracked file was touched, and `documentation/screenshots/` was not touched.

## Files

- **Contact sheets** (the same layout as before; cols 1024 light | 1024 dark | 1440 light | 1440 dark): `sheet-{xero,mcp,ai,notifications,packs,automations,sync-log,ai-history,executions}-alice.png`, `sheet-tabs-{carol,carol-cr-holder,carol-cr-lacker}.png`, `sheet-subpages-{…}.png`, `sheet-interactions.png` and `sheet-roles.png`. New: **`sheet-acceptance.png`** (K-114 tables + focus + diagnosis, K-115 Diary / Trust, K-121, Uninstall light / dark, Execution Log sheet by mouse and keyboard) and **`sheet-mcp-roundtrip.png`**.
- **Crops:** `crop-defect-k114-focus-lost-1440.png` (the one after defect), and `crop-replay-{integrations-grid-1024,automations-table-1024,executions-table-1024,packs-grid-1024,packs-installed-1440}.png` (the before-defect regions, after state).
- **JSON** (single line): `verify.json` (per-item verdicts with numbers, the gating / SEL-COMPAT / K-115 diffs), `measurements.json`, `sel-compat.json`, `console.json`, `specs-baseline.json`, `discover-{pre,post}.json`.
