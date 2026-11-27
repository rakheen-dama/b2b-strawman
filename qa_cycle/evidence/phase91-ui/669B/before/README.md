# 669B OBS-before — team, AI reviews, platform-admin (task 669.4)

## Run and environment

**When (2026-11-27, UTC):** inventory + seed 10:37:20–10:37:36Z (one run). First capture 10:38Z; SEL-COMPAT suites 10:41Z; second, committed capture 10:43:29–10:45Z (re-run after adding the read-only row-hover interaction to `01-capture.cjs`; same data); assembled 10:45Z.

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`. Not rebuilt, restarted, reseeded or torn down.
- Ports: frontend :3001, backend :8081, mock IDP :8090, Mailpit :8026.
- All `e2e-*` containers created **2026-11-27T09:50:22Z** (compose `project.working_dir` = `worktree-epic-669A/compose`).
- Frontend image `docteams-e2e-frontend` **`sha256:4c66916ceafb`**, created **2026-11-27T09:50:21Z**, built from 669A's head `d61e5dd4e`. `git diff --stat d61e5dd4e c54a4cba9 -- frontend packages compose` is **empty**, so the frontend is product-identical to main **`c54a4cba9`**, this worktree's HEAD.
- Backend image `sha256:e7fd89e47741` (created 2026-11-25T16:31:26Z). Mock IDP image `sha256:43583952b3ce` (2026-11-12T02:45:16Z).

**Browser:** Playwright library (the worktree's `frontend/node_modules`), not the MCP. Node 20.11.1 (nvm), DPR 1, reduced motion, TZ Africa/Johannesburg, rail expanded, theme via localStorage `kazi-theme`. Logins go through `/mock-login`:
- **Alice:** owner, `isAdmin false`, `isOwner true`, has `AI_REVIEW`, member of `platform-admins`.
- **Bob:** admin, has `AI_REVIEW`.
- **Carol:** member. Her capabilities are `AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST`: no `AI_REVIEW`, and she is not a platform admin.

**Committed files (25):**
- 9 contact sheets and 3 defect crops.
- 5 single-line JSON files: `measurements`, `sel-compat` (includes the suite results), `console`, `discover`, `seed`.
- 7 script files and this README.

Raw tiles stay in `/tmp/669B-before-raw/` and are not committed.

**OBS-after (no re-seed):** run from `before/scripts/`, with the scripts unedited:
`PHASE=after node 01-capture.cjs && PHASE=after bash 03-sel-compat.sh && PHASE=after STACK="<frontend image + SHA>" node 02-assemble.cjs`
The capture makes no writes. In the after run, the access-request sheet has no pre-seed rows.

## Data before the seed (`seed.json` › `dataBefore`)

- Members: 3 (Alice Owner / owner, Bob Admin / admin, Carol Member / member). The subscription reports `currentMembers 3`, `maxMembers 10`.
- AI gates: **0**.
- Access requests: **0**.
- Billing tenants: 1. "E2E Test Organization": TRIALING, MANUAL, `verticalProfile null`, **`memberCount 0`**.
- Demo tenants (PILOT + COMPLIMENTARY): **0**.

## Seed (`scripts/00-seed.cjs`). Browser and Mailpit REST only: no SQL, no app REST writes, nothing approved.

1. **Before any write**, `/platform-admin/access-requests` was probed and shot on all four tabs at 1024 and 1440 light (`seed.json › preSeedEmpty`; rows 1–2 of `sheet-pa-access-requests-alice.png`):
   - Every tab showed EmptyState variant **`default`**, **248 px** tall (976 wide at 1024, 1 104 wide at 1440), with h2 "No access requests".
   - The descriptions were: "No access requests have been submitted yet." (All), "No pending access requests.", "No approved …" and "No rejected …".
   - **K-01 reproduced.**
2. **Two access requests through the public `/request-access` form** (anonymous context; country South Africa, industry Legal Services). Each 6-digit code was read from Mailpit (`/api/v1/search`, then `/api/v1/message/{id}`), typed in, then "Verify".
   - "OBS 669B Alpha Attorneys" (`obs-669b-alpha@alpha-attorneys.e2e-test.local`): **`d3a13bd0-ad57-4753-993d-17ecb1119819`**, PENDING. Mailpit message `RunWyMZzFcC3EP8DGWXEDg`.
   - "OBS 669B Beta Inc" (`obs-669b-beta@beta-inc.e2e-test.local`): **`286c2094-29ea-4b64-8762-9154e4661e0d`**. Mailpit message `NT6iB8xVqGAdUgnpTAamzQ`.
3. **Beta rejected through the UI.** Path: Pending tab › row › "Reject" › AlertDialog "Reject". Result: **REJECTED**. Alpha stays **PENDING**. Approve was never clicked.

AI gates and demo tenants were not seeded (DA-19): there is no AI configuration, and provisioning a demo tenant creates a tenant. Pending invitations on the mock stack always render the mock empty state. The invitations table and the Keycloak member list can only be exercised in vitest.

## 1. `/team` (`sheet-team-alice.png`, `sheet-team-carol.png`, `crop-team-admin-row-hover-1024-dark.png`)

- **Header (K-60):**
  - Hand-rolled `<h1>` "Team", class `font-display … text-3xl`, **30 px / 400 Sora**, not a PageHeader.
  - The HelpTip button ("Help: Team management") is inside the h1.
  - The "3 members" pill is a sibling span outside the h1. Light: #3a4049 on #dee1e7, 7.98. Dark: #cbd0d7 on #20242b, 10.04.
  - 1 level-1 heading in `#main-content`, 1 `main#main-content`, doc h-overflow 0 at every variant, for both personas.
- **Members table:**
  - Hand-rolled `<table>`, headers MEMBER · EMAIL · ROLE.
  - Widths: **712 at 1024 light / 720 at 1024 dark**, 1 128 / 1 136 at 1440. `main` inner width is 760 at 1024. **No overflow** (the scroller's scroll width equals its client width).
  - 3 rows `member-row-<email>`, 57 px each. Cells "Alice Owner | alice@e2e-test.local | Owner" and likewise for Bob and Carol. Badges carry `member-role-badge`.
- **Contrast:**

  | Text | Light | Dark |
  |---|---|---|
  | Header | #4d545f on #fdfeff, **7.57** | #989faa on #07080b, **7.51** |
  | Email cell | **7.57** | **7.51** |
  | Name | 17.79 | 17.71 |
  | "Owner" badge | **4.52** | 6.26 |

- **Admin rows (Alice):**
  - The `tr` has **`role="button"` and `tabindex="0"` on all 3 rows**, with no button inside `tbody`.
  - Computed cursor is **`auto`**. The class-concatenation bug at `member-list.tsx:239` is confirmed: the class ends `…dark:hover:bg-slate-900/30cursor-pointer`.
  - **Click** on Bob's row opens `MemberDetailPanel`: a dialog, 448 px wide, "Member Detail … Bob Admin". Escape closes it.
  - **Keyboard:** from the Members tab, Tab ×2 (tabpanel, then the first `tr`) focuses Alice's row (outline `auto 1px`). Enter opens the panel. After Escape, **focus goes to `<body>`**; it is not returned to the row.
- **Defect, dark hover (Alice only):** the same concatenation bug also breaks `dark:hover:bg-slate-900/30`, so the light `hover:bg-slate-50` applies in dark mode.

  | Bob's row on hover at 1024 dark | Contrast |
  |---|---|
  | Name, #eff1f5 on #f8f9fb | **1.07** |
  | Email, #989faa on #f8f9fb | **2.53** |

  The name is unreadable (crop). For Carol, whose rows don't get the suffix, the same hover in dark is 17.32 / 7.34, correct. The bug goes away when the slice converts the table to DataTable.
- **Carol:** 0 `tr[role=button]`, 0 `tr[tabindex]`, no invite card, no Actions/Revoke column. Clicking a row opens nothing. Tab from the tabpanel skips the rows.
- **Pending Invitations tab (mock):**
  - EmptyState **`default`**, **248 px** (1 128 wide at 1440 light, 720 at 1024 dark).
  - h2 "No pending invitations" + "Invitations are managed through the auth provider".
  - Same for Alice and Carol.

## 2. `/ai/reviews` (`sheet-ai-reviews-{alice,bob,carol}.png`)

- **Allowed (Alice, and Bob at 1440 light):**
  - Hand-rolled `<h1>` "AI Reviews", **30 px / 400 Sora**, not a PageHeader. Subtitle "Review and approve AI-proposed actions before they are executed."
  - Tabs: Pending Review\* · History.
  - Pending: EmptyState **`default`**, **268 px**, "No pending reviews …". History: EmptyState **`default`**, 268 px, "No review history …".
  - 1 level-1 heading, 1 `main#main-content`, h-overflow 0.
- **Denied (Carol, every variant):**
  - Same h1, 30 px / 400 Sora.
  - Below it, `<p>` "You do not have permission to review AI actions. Contact your administrator." (20 px tall). Light: #4d545f on #fdfeff, 7.57. Dark: #989faa on #07080b, 7.51.
  - No tabs.

## 3. `/platform-admin/*`, light only (`sheet-pa-*-alice.png`, `sheet-pa-carol.png`, `crop-pa-*.png`)

- **K-98 reproduced:**
  - **0 `<main>` and 0 `#main-content`** on every platform-admin page. The probe scope falls back to `<body>`.
  - The `<nav>` is a 45 px white bar (`bg-white`) with no `aria-label`. Its links are "Access Requests", "Billing", "Demo", each `text-slate-700` #3a4049 at weight 500 (10.45 : 1).
  - **No link has `aria-current`** on any page. No active style: `::after` is `none`, border-bottom 0, text-decoration none (`crop-pa-nav-1440-light.png`).
- **K-99 reproduced (content width and h1 style):**

  | Page | Width at 1440 (inner) | Width at 1024 (inner) | h1 |
  |---|---|---|---|
  | access-requests | `max-w-6xl` **1 152** (1 104) | 1 024 (976) | **24 px / 700 ui-sans-serif** (`text-2xl font-bold tracking-tight`) |
  | billing | `max-w-7xl` **1 280** (1 232) | 1 024 (976) | **24 px / 700 ui-sans-serif** |
  | demo | `max-w-4xl` **896** (848) | 896 (848) | **24 px / 700 Sora** (`font-display text-2xl font-bold`) |

  Each page has exactly 1 h1.
- **Access requests (after the seed):**
  - Tabs: All · Pending\* · Approved · Rejected (Radix, `pending-tab` testid). `access-requests-page` wrapper.
  - Table headers: Org Name · Email · Name · Country · Industry · Submitted · Status · Actions.
  - Pending row `request-row-OBS 669B Alpha Attorneys` carries a raw "PENDING" badge (4.52), "1 minute ago", and Approve (`approve-btn`) + Reject.
  - The Approved tab shows EmptyState `default`, **248 px** (976 / 1 104 wide).
  - **Table overflow (in-slice surface, measured):**

    | Tab | At 1024 | At 1440 |
    |---|---|---|
    | Pending | table **1 188 px** in a **976** scroller: **212 px hidden**, "Actions" not in view (`crop-pa-access-table-1024-light.png`) | 1 188 in 1 104: **84 px hidden**, the Reject button is clipped |
    | All | 1 195 in 976: **219 px hidden** | 1 195 in 1 104: **91 px hidden** |
    | Rejected (no action buttons) | 983 in 976: 7 px hidden | 1 104 in 1 104 |

    The cause is the 330 px email column plus the 169 px Actions column inside `max-w-6xl`.
  - Light contrast: the "Approve" accent button is white on #00a38f, **3.16**.
- **Billing:**
  - 1 row: "E2E Test Organization | (blank Profile) | Trialing | Manual | 11 Dec 2026 | 0 | 27 Nov 2026 | Open".
  - Table width 976 at 1024 and 1 232 at 1440. **No overflow.**
  - A search with no match shows EmptyState **`default`, 248 px**, h2 "No tenants found" + "No tenants match the current filters." The slice target is `inline`.
  - "Open" opens `BillingDetailSheet`: a dialog, 512 px, "E2E Test Organization". Its labels (Trial Ends, Period End, Grace Ends, Created, Members) are `text-slate-500` on #f4f6f8, **4.36**.
- **Demo:**
  - Form (Organisation name and admin email inputs, 622 px wide), then h2 "Demo Tenants".
  - EmptyState **`default`, 248 px** (848 wide), h2 "No demo tenants". The slice target is `compact` (≤ 212). `demo-tenants-table` is absent because there are 0 tenants.
- **Carol:** HTTP **404** on all three pages. Next's built-in not-found ("404 · This page could not be found.", h1 "404" 24 px / 500 system-ui). These are the only console messages: 3 × "Failed to load resource: 404".

## 4. Console (`console.json`)

45 page loads plus interactions: **3 errors** (Carol's three expected 404s), 0 warnings, **0 hydration** messages.

## 5. SEL-COMPAT before (`sel-compat.json`)

**Suites** (`03-sel-compat.sh`; `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001`; specs unchanged; 10:41Z):

| Suite | Specs | Result | Time |
|---|---|---|---|
| `pnpm test:e2e:navigation` | `command-palette.spec.ts` + `sidebar-navigation.spec.ts` | **17 passed**, 0 failed / flaky / skipped | 6.0 s |
| `pnpm test:e2e e2e/tests/auth/rbac-capabilities.spec.ts` | rbac-capabilities | **8 passed**, 0 failed | 3.0 s |

`frontend/test-results` and `documentation/screenshots` were restored afterwards. `git status` shows only `669B/` as new.

**Live DOM:**
- Exactly 1 level-1 heading on every captured page and branch: Team (Alice and Carol), AI Reviews (allowed and denied), and each platform-admin page.
- The team table shows "Alice", "Bob" and "Carol".
- `member-row-*` ×3 and `member-role-badge` ×3 for both personas. `request-row-*`, `approve-btn` (Pending/All), `pending-tab`, `access-requests-page`, `billing-tenants-page` and `sheet-org-name` (sheet open) are all present.
- `demo-tenants-table`, `demo-tenant-row-*`, `pending-invite-row` and `override-count` are absent (no data on the mock stack).
- `rbac-capabilities.spec.ts` has no team, AI or platform-admin selector (DA-18). It is run as regression only.

## 6. Findings

On a slice surface; the slice is expected to change these:

| Finding | Detail |
|---|---|
| K-60 Team | Hand-rolled h1, 30 px / 400. |
| AI reviews | Hand-rolled h1 on both branches, 30 px / 400. |
| K-98 | 0 `main#main-content`; no `aria-current`; no active style. |
| K-99 | Widths 1 152 / 1 280 / 896; h1 font family differs (Sora on demo only). |
| K-01 | Access-request and demo empties are `default`, 248 px; billing no-match is `default`, 248 px (target `inline`). |
| Admin `tr` | `role="button"`. |
| Dark admin-row hover | Unreadable, 1.07 : 1. |
| Admin-row cursor | `auto`. |

**For the builder and orchestrator** (on slice surfaces, not in the slice task text):
- **Access-request table overflow:** 212 px at 1024 and 84 px at 1440. The K-99 width change alone (6xl → 7xl, inner 1 232 at 1440) would only fix 1440. Whether the DataTable conversion reflows this is the orchestrator's call.
- **Focus is lost after closing `MemberDetailPanel`:** it goes to `<body>`. Compare the after run on the new name button.

Outside the slice: see `../found.md`.
