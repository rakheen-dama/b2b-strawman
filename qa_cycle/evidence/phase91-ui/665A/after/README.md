# 665A OBS-after: Settings › Firm I (General, Roles, Compliance, Features, Subscription), PR #1870

Captured 2026-11-26, 15:48Z–15:59Z, on the e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090).

- Browser: Playwright library 1.59.1, the worktree's own `frontend/node_modules`. Not the MCP; no playwright-mcp process was touched.
- Settings: headless Chromium, DPR 1, reduced motion, timezone Africa/Johannesburg, `sidebar_state=true`, theme via localStorage `kazi-theme`. Node 20.11.1 (nvm).
- This replays `../before/` (frozen, not edited).

## Provenance

| Check | Value |
|---|---|
| Worktree HEAD | `git -C worktree-epic-665A log -1 --format=%H` → `bf2b15f512db96e713fab398b481f67ddadb83ac` (PR #1870 head, committed 15:44:10Z) |
| `git status --short frontend` | empty (clean). The only untracked path is this `after/` directory. |
| `docker inspect e2e-frontend --format '{{.Created}}'` | `2026-11-26T15:46:02.895641343Z` |
| Frontend image | `sha256:cf0194316993…`, created `15:46:02.517Z`, after `bf2b15f51`. `ed34140d1..bf2b15f51` changes `roles.tsx`, `RetentionPolicyTable.tsx`, `role-card.tsx` (+ tests, found.md); this rebuild picked them up. |
| Compose label `com.docker.compose.project.working_dir` | `/Users/…/worktree-epic-665A/compose` |
| Backend, postgres, mock-idp, localstack, mailpit | created 15:24:49Z. This is the fresh stack built at `ed34140d1`; legal-lifecycle, legal-depth-ii, test:e2e:settings and rbac ran on it before this capture. |
| Concurrent runs | `ps` showed only playwright-mcp processes; no e2e suite or Maven was running. |

## Personas

These are the same four as before. Login is identical: Alice through `/mock-login`, Carol through the `mock-auth-token` cookie.

| Key | role / isAdmin / isOwner | Capabilities |
|---|---|---|
| `alice` | Owner / false / true | 29 |
| `carol` | Member / false / false | AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST |
| `carol-cr-holder` | "665A OBS Oversight" | TEAM_OVERSIGHT |
| `carol-cr-lacker` | "665A OBS No Oversight" | PROJECT_MANAGEMENT |

The stack is fresh, so both custom roles were **re-created through the Roles UI** (`02 create`).
- "665A OBS Oversight" was created at **1280×720** and "665A OBS No Oversight" at **1024×768**.
- Both used a **real Playwright click** on "Create"; no `dispatchEvent` was needed.
- Each role was assigned to Carol through Team › member panel. Carol was then **restored to "Member"** with no overrides.
- Each step was verified by `GET /api/members/{id}/capabilities` and Carol's `/api/me/capabilities` (`measurements.json` → `customRoles`).
- `discover-post.json` vs `discover-pre.json` is SAME for personas, members, modules, retention policies, subscription and org documents. Two things differ:
  - `orgRoles`: the two custom roles, 0 members each, left on the stack as before.
  - `settings.enabledModules`: **order only**. `bulk_billing` and `information_requests` swapped after the Features off→on toggle; the set is the same.

## Data differences vs before (seeded by the suites, not by 665A)

| Item | Before | After |
|---|---|---|
| `brandColor` | `null` | `#1B3A4B`. The form now shows the stored value; F-665A-1's `#000000` is not visible on this stack. |
| Compliance packs | 5 | 6 (`compliance-za@1` added) |
| Retention policies | 2 | 5. Three use values the Compliance table has no option for (`TIME_ENTRY`, action `delete`); see F-665A-7. |
| Features card order | Information Requests, Bulk Billing | Bulk Billing, Information Requests (API module order) |
| Custom roles | created on the old stack | re-created here (same names and capabilities) |

## Scripts (`scripts/`, copied from `../before/scripts/`, run in this order; every adaptation listed)

| Script | Adaptation vs before |
|---|---|
| `lib.cjs` | `PHASE` defaults to `after`; header comment. Nothing else. |
| `00-discover.cjs` | unchanged |
| `01-capture.cjs` | **A1** FULL probe: `data-slot="settings-section"` roots are counted as `settingsSection` and excluded from `handRolled`, since every SettingsSection is a `div.rounded-lg.border`. **A2** COMPACT probe, per variant: adds `mainCount`, `h1Count` and a `layout` probe. That probe records each section's painted background (canvas composite), clipped or outside text in card headers, footers, the action bar, the page header and the firm tablist (with an `inScroller` flag), and the General SettingsActionBar placement. Tabs, viewports, shots, SEL-COMPAT selectors and legacy routes are unchanged. |
| `02-roles.cjs` | **B1** K-102: the dialog is measured at 1024×768 light and dark, 1280×720 and 1440×900 (before: 1280 and 1440). It also records the scroll body, the Cancel button and an elementFromPoint hit test on Create. At 1024 and 1280 the body is wheel-scrolled and re-measured. **B2** Roles are created at 1280 and at 1024 with a real click; the dispatchEvent fallback stays only as a recorded failure path and was not used. **B3** The Custom Roles text is read from its SettingsSection (before: `.space-y-4`). |
| `03-saves.cjs` | **C1** General "Save Settings": Brand Color is cleared and saved (stored null), then a second save types `#1B3A4B` back. Both saves are recorded and the value is restored. **C2** For each message, its `data-slot` ancestor chain and box. **C3** The Subscription probe reads `settings-section`; badge `truncated` is recorded. **C4** `probes`: K-104 light and dark; K-106; K-39 contrast light and dark; Retention table geometry at 1024/1440 × light/dark plus rows shown vs GET; Retention Days focus test; Delete → confirm → dismiss. |
| `04-assemble.cjs` | **D1** `measurements.json.probes`. **D2** `sheet-roles-ui` becomes `sheet-k102-create-role`; adds `sheet-k104-roles-1024`; `sheet-saves` gains the k39 and retention crops. **D3** writes `verdicts.json`, comparing against `../before/measurements.json` and `../before/sel-compat.json`. |

Executed order: `00 pre` → `01 alice` → `01 carol` → `02 create` → `02 assign "665A OBS Oversight"` → `01 carol-cr-holder` → `02 assign "665A OBS No Oversight"` → `01 carol-cr-lacker` → `02 assign "Member"` → `03` → `00 post` → `STACK="…" 04`.
- All exited 0.
- `01 alice` ran twice. The first run exposed that 664A's firm tab strip (a horizontal scroller at 1024) was being counted as "clipped". The probe gained `inScroller` and `nextSectionInDoc`, and Alice was re-run. No script was edited after its final run.
- Raw tiles are in `/tmp/665A-after-raw/`.

## Sheets

| File | Contents |
|---|---|
| `sheet-{general,roles,compliance,features,subscription}-{alice,carol}.png` | cols 1024 light / 1024 dark / 1440 light / 1440 dark; row 1 viewport, row 2 full page. Roles/Alice adds the expanded Capability Reference. |
| `sheet-{…}-custom-role.png` | rows 1–2 "665A OBS Oversight", rows 3–4 "665A OBS No Oversight" |
| `sheet-k102-create-role.png` | The Create Role dialog: <br>• opened at 1024 light and dark, 1280 and 1440 <br>• body wheel-scrolled at 1024 and 1280 <br>• filled before the real click (1280, 1024) <br>• Roles tab after creation <br>• Team panel for each of the 3 assignments |
| `sheet-k104-roles-1024.png` | Roles 1024 light and dark: viewport scrolled to the Member card, plus a crop of that card |
| `sheet-saves.png` | One crop per save at message time (8). Also: FICA fieldset light/dark, K-39 Branding light/dark, Retention Policies 1024 light/dark, Retention Days focus test. |

JSON (single line): `discover-pre.json`, `discover-post.json`, `measurements.json`, `sel-compat.json`, `console.json`, `saves.json`, `verdicts.json`.

## Verdicts (`verdicts.json`)

| # | Check | Verdict | Evidence |
|---|---|---|---|
| 1 | One `main#main-content`, one h1, headings, gating | **PASS** | 4 personas × 5 tabs × 4 variants: main 1, h1 1 everywhere. h1 identical to before except Subscription "Billing" → "Subscription" (K-106) for all 4 personas. Heading diffs are only the expected ones (below). Tab bars and "You don't have access to …" notices are identical per persona. |
| 2 | SEL-COMPAT | **PASS** | 0 count differences across every before selector × tab × persona. Legacy routes have the same final URL, error flag and denial regex. `/settings/billing` h1 "Billing" → "Subscription" is compared as K-106. |
| 3 | Save models (Alice) | **PASS** | 8 saves, 0 stalls, every server action 200, 0 console messages. `restored: true` (`GET /api/settings` + modules before = after). Detail below. |
| 4 | Retention table focus + delete confirm | **PASS** | Row 1 "2555" + `1`,`2`,`3` → `25551`, `255512`, `2555123`. The **same DOM node** (marker attribute) was focused after every key. "2555" was retyped (value back to 2555) and nothing saved. Delete opened `window.confirm` "Delete this retention policy?", which was dismissed: 5 rows before and after, GET unchanged. |
| 5 | K-104 Roles 1024 | **PASS** | Page horizontal overflow **0 / 0 px** light/dark (before 81 / 86). Member card 521–967 (light) / 521–975 (dark); the last badge ends at 933, all inside, none truncated. "Generate statement of account", "View legal registers" and "Manage disbursements" are shown; 0 raw enum labels. |
| 6 | K-106 copy; K-39 contrast | **PASS** | h1 "Subscription"; toolbar chips "Trial period", "Invoiced manually"; h2 "Managed Account"; body "Your Kazi subscription is managed by b2mash. Contact support to change your plan or billing details."; all in viewport at 1024. K-39 (canvas-composited WCAG): Branding hint and counter, Tax Label hint, Letterhead counter and Client Portal hint measure **7.57:1** light (`#4d545f` on `#fdfeff`) and **6.74:1** dark (`#989faa` on `#14171c`). |
| 7 | Layout sanity, 1024/1440 × light/dark, every tab and persona | **PASS** (on the defined checks) | Page horizontal overflow 0 everywhere. 0 clipped or outside texts in section headers, footers, the action bar or the page header; only 664A's tab strip, which is its own scroller, overflows. The General SettingsActionBar is static, directly after Branding (gap 32 px) and before Letterhead, its right edge = section right edge (992/1000/1408/1416), with a single "Save Settings". All dark section backgrounds are `#14171c` (no white surfaces). **See F-665A-8:** the Retention Policies table at 1024 hides ACTION/ACTIVE/ACTIONS (row Save/Delete) behind an in-card horizontal scroll. That is pre-existing and worsened by about 50 px; it needs an orchestrator ruling. |
| 8 | Console | **PASS** | 108 capture page loads + roles + saves runs: **0 errors, 0 warnings, 0 hydration** |
| K-102 | Create Role dialog | **PASS** | Detail below. |

**K-102 measurements:**

| Viewport | Dialog height | Top → bottom | Create button | Body (visible / total) | Scroll test |
|---|---|---|---|---|---|
| 1024×768 | 691 | 38 → 730 | 669–705 | 519/814 | scrolled to 295; title and Create stayed in place |
| 1280×720 | 648 | 36 → 684 | 623–659 | 476/814 | scrolled to 338 |
| 1440×900 | 810 | — | 794–830 | — | — |

- In every case the title and the Create button are in the viewport, and the hit test lands on the button.
- Before: the dialog was 986 px tall, Create was off-screen at 1280 and 1440, and `dispatchEvent` was needed.
- Both roles were created with a real click.
- Note: `max-height` reads `none` on `[role=dialog]`, because the kit puts `className` on the inner panel. The geometry (691 = 90vh) is the evidence.

### Save models (`saves.json`, Alice 1440 light)

| Model | Message | Where (data-slot chain) | ms |
|---|---|---|---|
| General "Save Settings" 1/2 (Brand Color cleared → null) | "Settings saved successfully." `p.text-success-foreground` | `settings-action-bar` status slot, left (x 544) of the right-aligned button | 1 426 |
| General "Save Settings" 2/2 (`#1B3A4B` retyped → restored) | "Settings saved successfully." | same | 1 432 |
| General "Save letterhead" (one space in Address line 1 → null) | "Letterhead saved." `p[role=status]` teal | `card-footer` of section "Letterhead" | 1 980 |
| General portal cadence Bi-weekly → Weekly → Bi-weekly | "Portal digest cadence updated." both times | `card-content` of "Client Portal" (body, no footer) | 306 / 2 624 (the second includes waiting out the 3 s auto-dismiss) |
| Compliance "Save retention period" (5, unchanged) | "Matter retention period updated." `p.text-success-foreground` | `card-footer` of "Matter retention" | 111 |
| Features "Bulk Billing Runs" on → off → on | no message by design; `aria-checked` true → false → true | — | 990 / 930 |

- Before vs after timings are equivalent: 1 419 / 1 983 / 264 / 2 616 / 110 / 951 / 950 ms.
- The before message elements were unstyled `p.text-green-600`; they now sit in the status slots (DA-5).
- Compliance › General Settings "Save Settings" was **not clicked**, as before (F-665A-2).

## Before → after diffs

- **h1:** Subscription "Billing" → "Subscription" for all 4 personas (K-106, DA-1). Everything else is identical.
- **Headings:**
  - Org Documents empty title `h3` → `h2` "No organization documents yet", on every General render (Alice, and Carol and the custom roles on every gated tab). This is expected: compact `EmptyState`, PR judgment call 4.
  - Subscription adds `h2` "Managed Account" (DA-15; before it was a `CardTitle` div).
  - No other heading changed.
- **Cards** (`measurements.json` → `tabs.*.cards`): hand-rolled `div.rounded-lg.border` cards became `settingsSection` roots on every tab. Features `feature-card-*` are now untitled SettingsSections (testids kept).
- **Tables:**
  - Retention Policies is a DataTable (`data-slot=table`).
  - The Capability Reference is a kit table.
  - Org Documents uses DataTable when documents exist; 0 here, so it shows the empty state.
- **Roles 1024 overflow:** 81/86 → 0/0 px.
- **Gating:** identical per persona. Custom roles get Carol's gate. The holder's tab bar is still General, Audit trail, Subscription; the lacker's is General, Subscription.

## Anything odd (reported, not fixed)

1. **F-665A-7 (new, pre-existing):** the Compliance retention table shows TIME_ENTRY as "Client" and action `delete` as "FLAG". Its option lists don't contain the values the suites stored, so the controlled `<select>` falls back to the first option.
2. **F-665A-8 (new, pre-existing, worsened):** at 1024 the Retention Policies table scroller is 446/718 px (light) and 454/718 (dark). ACTION, ACTIVE and ACTIONS, including every row's Delete, are off-screen; the header is cut at "AC". Before, about 504 px was available and ACTION was partly visible.
3. **Element-screenshot artifact:** in the `retention__focus-test` crop, the sticky app header overlaps the top of the section, because the crop was taken after Playwright scrolled the input into view. It is not a layout defect.
4. **K-103 is unchanged** (backlog): Owner and Admin cards still say "No default capabilities".
