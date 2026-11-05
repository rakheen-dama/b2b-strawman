# 633C — E2E-PORTAL (builder, 2026-11-05)

Worktree `/Users/rakheendama/Projects/2026/worktree-epic-633C`, branch
`phase89/04C-portal-deadlines-copy-guard` (working tree = the PR's portal changes). The KC dev
portal on :3002 was stopped first (`b2b-strawman/compose/scripts/svc.sh stop portal` — the main
checkout's svc.sh, so the restore starts main's portal again) and restored at the end.

## Gate run — fresh stack

```
bash compose/scripts/e2e-down.sh && docker builder prune -f && bash compose/scripts/e2e-up.sh   # 06:35, from the worktree
  → new volumes docteams-e2e_e2e_postgres_data / _localstack_data; backend healthy; portal.portal_deadline_view = 0 rows
```

| Suite | Command | Result |
|---|---|---|
| portal-client-90day | `cd portal && pnpm test:e2e:portal-client-90day --reporter=line` (06:36) | **13 passed / 0 skipped** (17.2s) — was 12 passed / 1 skipped (Day 75 fixme) at 632C |
| responsive | (≥5½ min later + `docker restart e2e-backend`) `pnpm test:e2e:responsive --reporter=line` (06:49) | **21 passed** (16.4s) |
| portal-data-isolation | (≥5½ min later + restart) `cd frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 KGOSI_EMAIL=contact@acme.example.com pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/portal/portal-data-isolation.spec.ts --reporter=line` (06:56) | **2 passed** (0 skipped) |

90-day per-test lines (gate run):

```
[1/13]  day-00-07.spec.ts:118  Day 0: Client lands on /home and sees the pending info requests and the sent fee note
[2/13]  day-00-07.spec.ts:140  Day 0: Client opens the new info request from Home and sees its upload slot      ("0/1 submitted • Requested")
[3/13]  day-00-07.spec.ts:178  Day 3: Client uploads the document and submits the info request                  ("Submitted", "1/1 submitted • In progress")
[4/13]  day-00-07.spec.ts:216  Day 7: Diary dates page renders for the client (empty — Day 75 removes its rows)
[5/13]  day-14-30.spec.ts:77   Day 21 [legal-za]: … balance and the new deposit                                 (Type cell "Deposit", no "DEPOSIT")
[6/13]  day-14-30.spec.ts:149  Day 30 [legal-za]: Client downloads the receipt for a part payment on a fee note
[7/13]  day-45-75.spec.ts:60   Day 45: Client opens the seeded SENT fee note
[8/13]  day-45-75.spec.ts:92   Day 45: SENT fee note offers no online payment without a PSP
[9/13]  day-45-75.spec.ts:124  Day 60 [legal-za]: Client downloads the Statement of Account from the matter page
[10/13] day-45-75.spec.ts:181  Day 75 [legal-za]: Client sees the court date and the prescription on Diary dates, sanitised, until the firm removes them
[11/13] day-85-90.spec.ts:57   Day 85: Profile shows the client's contact details (read-only)
[12/13] day-85-90.spec.ts:86   Day 85: Notifications page shows the firm's bi-weekly digest cadence (read-only)
[13/13] day-85-90.spec.ts:110  Day 90: Activity trail shows this run's submit and download
  13 passed (17.2s)
```

After the 90-day gate run (Day 75 cleanup held): `select count(*) from portal.portal_deadline_view`
→ **0**; firm rows left by the run: court date `CANCELLED | Johannesburg High Court 1zuet`, tracker
`INTERRUPTED | GENERAL_3Y` (the documented per-run residue). The Day 75 captured frame
(`test.info().outputPath("day-75-deadline-detail.png")`, D4) is copied to
`e2e-day-75-deadline-detail.png`.

## Baseline capture (screenshot baselines are not committed)

1. **Capture stack** (fresh `e2e-down` / prune / `e2e-up`, 06:25): 90-day run 1 → 2 passed / 11
   failed, **all 11 failures `A snapshot doesn't exist … writing actual`** (no other error; Day 75
   passed on its first run). Responsive run 1 (after cooldown + backend restart) → 21 failed, all
   `writing actual`.
2. **Gate stack** (fresh again, 06:35): 90-day 13 passed against the captured baselines.
   Responsive first attempt: 1 failed / 1 passed (serial mode stops at the first failure) —
   `sm/trust-matter-list.png`: "Expected an image 375px by 885px, received 375px by 799px": the
   **masked** run-dependent transactions region (the spec masks it — "change with every 90-day Day
   21 deposit") was two rows tall on the capture stack and one row (the single Day 21 deposit) on
   the gate stack; the unmasked copy is identical in both ("Website Redesign" balance card). Both
   stacks ran this branch's code. Remediation: deleted the three `*-trust-matter-list.png`
   baselines, recaptured them on the gate stack with `--update-snapshots=changed` (only the three
   missing ones were written — the log shows no other snapshot rewritten; 21 passed), then after the
   cooldown + backend restart the clean gate run above: **21 passed** with no update flag.
3. All captured PNGs under `portal/e2e/screenshots/portal-v2/**` were deleted afterwards (only the
   tracked `.gitkeep` / README remain); `portal/test-results` is git-ignored. `git status` shows no
   stray artefacts.

## Environment notes

- Magic-link caps respected: every suite started ≥5½ min after the previous token mint and after
  `docker restart e2e-backend` (health UP before the run); no 429 observed.
- KC portal restored: `svc.sh status` → `portal 78548 yes yes 3002` (cwd
  `/Users/rakheendama/Projects/2026/b2b-strawman/portal`).
