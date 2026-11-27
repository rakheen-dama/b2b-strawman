# 669B OBS-after — team, AI reviews, platform-admin (task 669.6)

## Run and environment

**When (2026-11-27, UTC):** frontend rebuilt 11:10:00–11:10:48Z. Capture 11:11:20–11:13:08Z, SEL-COMPAT 11:13:16–11:13:29Z, assembled 11:13:36Z.

**Stack:** the same running e2e mock-auth stack and data as OBS-before. It was not torn down or reseeded, and `00-seed.cjs` was not re-run.
- **Frontend** image `docteams-e2e-frontend` **`sha256:85da31d86b9d`**, created **2026-11-27T11:10:48Z**. It was built from `worktree-epic-669B` at the PR head **`810b8708f`**, with a fresh `pnpm --filter frontend build` step (`next build`, 44.5 s, not cached). The container's compose `working_dir` is now `worktree-epic-669B/compose`.
  - Proof: the served chunk `.next/server/chunks/ssr/_1th4a5k._.js` contains the new platform-admin nav. It sets `"aria-current":a?.startsWith(d)?"page":void 0` with `TAB_UNDERLINE_CURRENT`.
- **Backend, mock IDP, Postgres, LocalStack and Mailpit were untouched.** The backend is still `sha256:e7fd89e47741`.
- **Rebuild command:** `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps frontend`. This is `e2e-rebuild.sh frontend` plus `--no-deps`. A `--dry-run` of the plain script showed it would also rebuild the backend and mock-idp images and **recreate `e2e-localstack`**, because its `./data/s3` bind mount pointed at the deleted 669A worktree. `--no-deps` keeps the rebuild to the frontend only.

**Scripts:** `before/scripts/`, run unedited (Node 20.11.1, nvm). Nothing was copied to `after/scripts/`.

```
PHASE=after node 01-capture.cjs && PHASE=after bash 03-sel-compat.sh && PHASE=after STACK="…" node 02-assemble.cjs
```

Raw tiles are in `/tmp/669B-after-raw/` and are not committed. The access-request sheet has no pre-seed rows in this phase, by design.

**Committed files (17):** 9 contact sheets, the 3 crops re-shot at the same spots for comparison, 4 JSON files (`measurements`, `sel-compat`, `console`, `discover`) and this README.

## Checks, before → after

Numbers are from `before/measurements.json` and `after/measurements.json`.

| # | Check | Before | After | Result |
|---|---|---|---|---|
| 1 | `main#main-content` per page | Team/AI 1. **PA 0** (scope fell back to `<body>`) | **1 on every page** (Team, AI, all 12 PA tiles). Carol's 404s have 0 (Next built-in, unchanged) | **PASS** |
| 2 | Exactly one h1 in main, per branch | 1 | 1 on every tile, including AI denied and every PA tab | **PASS** |
| 3 | h1 font + PageHeader | Team/AI 30/400 Sora, no PageHeader. PA 24/700 ui-sans (access, billing), 24/700 Sora (demo) | **24 px / 600 Sora**, `font-display text-foreground text-2xl font-semibold tracking-tight`, `PageHeader true` on Team, AI (allowed and denied), access, billing and demo | **PASS** |
| 4 | K-99: PA width equal + h1 style equal | 1152 / 1280 / 896 at 1440; 1024 / 1024 / 896 at 1024 | **`max-w-7xl` 1280 (inner 1232) on all three at 1440; 1024 (inner 976) on all three at 1024**. h1 identical | **PASS** |
| 5 | K-98: nav `aria-current` + underline | No `aria-current`, no active style | The active link has `aria-current="page"` (Access Requests, Billing or Demo per page) and a 2 px `::after` `bg-foreground` underline. Nav 45 px. First label at x 24 (`crop-pa-nav-1440-light.png`). Active link 17.96:1, others 7.64:1 | **PASS** |
| 6 | EmptyState: Team invitations (mock) | `default` 248 | `default` 248 (720 / 1128 wide) | **PASS** (unchanged, as expected) |
| 7 | EmptyState: access requests, Approved tab | `default` 248 | `default` 248 (976 / **1232** wide) | **PASS** |
| 8 | EmptyState: billing no-match | `default` 248 | **`inline` 44 px**, one row | **PASS** |
| 9 | EmptyState: demo | `default` 248 (848 wide) | **`compact` 164 px** (≤ 212), 976 / 1232 wide | **PASS** |
| 10 | EmptyState: AI reviews (Pending, History) | `default` 268 | `default` 268. Denied branch: `<p>`, 20 px, unchanged | **PASS** |
| 11 | **B-8: access-requests overflow** | Pending 1195 in 976 (**219 hidden**) at 1024, 91 at 1440. All 226 / 98. Rejected 14 / 0 | **0 px hidden on every tab at both widths.** Frame 974 at 1024 and 1230 at 1440 (scroll width = client width). All 8 columns shown and `fullyInView`. Approve + Reject in view. Rows wrap to 57 px at 1024 and stay 49 px on one line at 1440 (`crop-pa-access-table-1024-light.png`) | **PASS** |
| 12 | Other tables overflow | Team 0, billing 0 | Team 710/718 (1024) and 1126/1134 (1440), overflow 0. Billing 974 / 1230, overflow 0, all 8 columns | **PASS** |
| 13 | Admin row: no `tr role=button` | 3 × `role=button` + `tabindex=0` | `role`/`tabindex` = 0. 3 name `<button>`s in tbody | **PASS** |
| 14 | Name button by Tab; Enter opens panel | Tab reached the `tr` | Tab ×2 from the tabpanel focuses `button "Alice Owner"` inside `member-row-alice@…` with a visible teal `focus-visible:ring-2`. **Enter opens `MemberDetailPanel`** (dialog, 448 px). Click on Bob's row also opens it (1440 light, 1024 dark) | **PASS** |
| 15 | Admin-row cursor | `auto` | `pointer` | **PASS** |
| 16 | Dark hover on Bob's row (1024 dark) | **Name #eff1f5 on #f8f9fb, 1.07**. Email 2.53 | Row bg `bg-muted/50`. **Name #ffffff on #1a1d24, 16.87**. Email #989faa, 6.32 (`crop-team-admin-row-hover-1024-dark.png`). Light hover 16.87 / 7.18 | **PASS** |
| 17 | Carol on Team | No row button, invite card or revoke column | 0 tbody buttons, 0 `tr[role]`/`tr[tabindex]`, no invite card, no Revoke column, cursor `auto`. Click opens nothing. Tab skips the rows (same Tab sequence as before) | **PASS** |
| 18 | Team contrast | Header/email 7.57 (L) / 7.51 (D). Pill 7.98 / 10.04 | Header/email 7.57 (L) / **6.74** (D). Dark sits on the kit frame #14171c. Pill now in PageHeader actions (`bg-muted`/`text-muted-foreground`): **6.76** (L) / **5.84** (D). All ≥ 4.5 | **PASS** |
| 19 | Console | 3 errors (Carol's 404s), 0 warnings, 0 hydration | **3 errors (Carol's 3 × 404), 0 warnings, 0 hydration** over 45 loads | **PASS** |
| 20 | SEL-COMPAT suites | navigation 17/17, rbac 8/8 | **navigation 17/17** (7.4 s), **rbac-capabilities 8/8** (3.2 s). 0 failed / flaky / skipped | **PASS** |
| 21 | SEL-COMPAT live DOM | (see before) | Every selector count is identical to before. The only diff is `main#main-content`, which now also counts 1 on every PA tile | **PASS** |

## Defects

**None.** No regressions and no new out-of-scope findings, so `found.md` is unchanged.

Notes (not defects):
- **The billing-sheet label contrast (4.36, F-669B-B-5) no longer appears in `lowContrast`.** That is a probe-scope effect, not a fix. The probe now scopes to `#main-content`, and the Sheet is portalled outside it. `billing-detail-sheet.tsx` is not in the PR diff, so B-5 stands.
- **Still present, as routed:**
  - Approve button 3.16 (B-5).
  - Raw "PENDING" / "REJECTED" badges (B-9).
  - Billing Profile cell blank and Members 0 (B-4, B-3).
  - Carol gets Next's built-in 404 (B-6).
  - **Focus goes to `<body>` after Escape** closes `MemberDetailPanel` (F-669B-B-7, → 670A), for both Alice tiles. The new name button does not fix this.
