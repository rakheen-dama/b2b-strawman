# 669A OBS after-fix @d61e5dd4e (PR #1881) — disbursement header casing (OBS-after D-1)

**Fix under test:** `[&_button]:uppercase` on the sortable disbursement headers (`frontend/components/legal/disbursement-list-view.tsx:127`, `SORT_HEAD`).

## Environment
- Fresh e2e mock-auth stack built from worktree-epic-669A at `d61e5dd4e`: `e2e-frontend` created `2026-11-27T09:50:22.175226805Z`, image `sha256:4c66916ceafb023e852f59c9b11f5055a16fc5d714be121657145341795ba429`; frontend :3001, backend :8081. Not rebuilt, restarted or reseeded.
- Data: 4 disbursements left by the legal-depth-ii suite (day-05), found via `GET /api/legal/disbursements` as Alice (read-only). Matter-tab surface = matter `059ffe6b-c83c-4a57-8187-5b9573cc623d` (2 disbursements). No SQL, no seeding.
- Chromium via `@playwright/test` (lib.cjs resolution), node 20, Alice via `/mock-login`, theme via localStorage `kazi-theme`, cookie `sidebar_state=true`, reducedMotion, DPR 1.
- Run at 2026-11-27 (see `measurements.json` `at`).

## Script
`scripts/05-header-case-fix.cjs` (`PHASE=after-fix node 05-header-case-fix.cjs`), using `../before/scripts/lib.cjs`. Surfaces: `/org/e2e-test-org/legal/disbursements` and `/org/e2e-test-org/matters/<id>?tab=disbursements`, each at 1024×768 and 1440×900 in light and dark (8 runs). Outputs `measurements.json` (single line) and `sheet-disbursement-headers.png` (thead crops, 8 labelled tiles).

## Results (8/8 runs)

| Check | Result | Numbers |
|---|---|---|
| Every visible `<th>` innerText is uppercase | **PASS** | 8/8 runs. 1440: `DATE, MATTER, DESCRIPTION · SUPPLIER, AMOUNT (INCL VAT), APPROVAL, BILLING, ACTIONS` (7 th). 1024: `DATE, DESCRIPTION · SUPPLIER, AMOUNT (INCL VAT), APPROVAL, ACTIONS` (5 th; MATTER/BILLING hidden by the 66rem/58rem container tiers, by design) |
| Computed `text-transform` of each label element | **PASS** | `uppercase` on all sort buttons (Date, Amount, Approval, Supplier control) and plain th labels (before fix: `none` on the 3 kit buttons — `after/header-case.json`) |
| Sort-button `letter-spacing` = plain-th `letter-spacing` | **PASS** | `0.6px` / `0.6px` on every header, every run |
| Sort icon in its slot (button + icon right ≤ th right) | **PASS** | `inSlot: true` for all 4 sortable headers in all 8 runs |
| No overlap with the next th | **PASS** | `noOverlapNext: true` for every header with a successor, all 8 runs |
| Table no overflow (scrollWidth ≤ clientWidth) | **PASS** | list 1024: 710/710 (light), 718/718 (dark); list 1440: 1126/1126, 1134/1134; matter-tab 1024: 662/662, 670/670; matter-tab 1440: 1078/1078, 1086/1086 |
| Document no horizontal overflow | **PASS** | 1024/1024 and 1440/1440 in all runs |
| Click "Date" → `aria-sort` toggles | **PASS** | `descending` → `ascending` in all 8 runs (others stay `none`) |
| Header text after the click | **PASS** | `DATE`; all headers still uppercase (`allUpperAfter: true`) in all 8 runs |

**Verdict: OBS-after D-1 CLOSED — PASS.**

## Note (not a defect of this fix)
Dark-theme tables measure 8px wider than light on every surface/width (e.g. 710 → 718). Both fit without overflow; observed only, not investigated.
