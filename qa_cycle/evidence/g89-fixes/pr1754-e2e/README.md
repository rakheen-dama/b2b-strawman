# PR #1754 (G89 Batch I: G89-F71, portal deadline status derived at read time): pre-merge e2e gate

**Verdict: PASS at PR head `80931ef69`. No e2e regression is attributable to the PR.**

- **Every required suite met its baseline at the PR head, with an empty per-test diff against the #1753 gate.**
  - Legal-lifecycle got there on its one permitted re-run, on a fresh stack (L). Its first scored run (G1) failed.
- **One failure, a transient that did not persist.** G1 legal-lifecycle `day-07-14-work-trust.spec.ts:78` "Bob: Log 120min on Sipho — letter of demand" failed, and five serial tests failed downstream as a cascade: 68/6/31.
  - The re-run on fresh stack L passed **105/0/0**, with an empty diff.
  - Bob's time entry was committed in the DB. The Fees → Time-recording summary panel was stale and showed only Carol's entry. The run was at load average about 22 on 10 cores, while the concurrent g89-i full verify was running.
  - The PR touches no code on that path (frontend time summary, `TimeEntryService`).
  - The failure did not persist on re-run, so recipe step 2 (reproduce on main) was not triggered. It is therefore **not proven class (c)**. It is classed **transient, not attributable**. Details are under C1 below.
- **G89-F71 observed live.** On the G2 stack, a firm-API probe created 8 deadline rows across every status boundary. For each row, the API status, the `?status=` filter (the new SQL `CASE`) and the `PortalDeadlineStatus` rule recomputed from `due_date` + SA today all agree. Every portal deadline spec passed: 90-day Day 7 and Day 75, and responsive `deadlines-list` ×3.
- **An environmental block was found and fixed before any suite ran.** The Docker Desktop VM disk was 100 % full (58.4G), and `e2e-postgres` initdb failed twice with "No space left on device".
  - **Freed:**
    - dangling images (`docker image prune -f`);
    - build cache older than 72 h (`docker builder prune -f --filter until=72h`, 14.51 GB).
  - **Not touched:** volumes, tagged images and recent build cache.
  - Afterwards the VM disk was 22.0G used, 33.4G free (`raw/docker-disk-remediation.txt`).

## Source and recipe

- **Worktree:** detached `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp7` at `80931ef6940b633d926408c444ca740a0e54108d`, created fresh.
  - `pwd` was confirmed, and the tree was clean (`dirty=0`) before every stack.
  - The merge base is origin/main `1bf85f0b1`. origin/main has since gained only docs commits, now at `c946c78f5`.
- **What changed vs main (runtime):** backend only. The changed files:
  - `PortalDeadlineStatus` (new);
  - `PortalDeadlineViewRepository.findByCustomer(..., today)` (SQL `CASE` status filter);
  - `PortalDeadlineService` (`effective()` on every response);
  - `DeadlinePortalSyncService` (uses `derive()`);
  - `PortalDigestContentAssembler` (passes `today`);
  - `PortalEmailNotificationChannel` (constant).

  **No migrations, and no frontend or portal code.**
- **Branch moved during the gate:** `fix/g89-f71-deadline-status` advanced to `389085d49`. The only change is `test(portal): F71 staleness test … (leap-day safe)`, which touches 1 file, `PortalDeadlineStatusStalenessIntegrationTest.java`, and nothing at runtime. This gate is for `80931ef69`. The new commit is backend-test-only, so the e2e result carries over, but it needs its own `./mvnw verify`.
- **Recipe:** the #1753 recipe (`../pr1753-e2e/README.md` and `raw/`), with these differences:
  - `E2E_VISUAL` was unset (every run logs `E2E_VISUAL=unset`), and there was **no capture-then-score**. Every scored run was a single run.
  - The F46 font probes and the VIS opt-in checks were dropped, because they are not relevant here. The F71 deadline probe was added.
- **Stacks:**
  - Every stack was built fresh with `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached with a marker. No reseed was run anywhere.
  - The foreign-stack guard reported "no foreign e2e stack" before every build. Only one e2e stack ran at a time.
  - `caffeinate -dims` was held for every driver.
- **Tooling:**
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before Playwright flags.
  - node v20.11.1 was first on PATH, and every run logs `node=v20.11.1 envnode=v20.11.1`.
  - `pnpm install --frozen-lockfile` returned rc 0.
- **Portal suites:**
  - spaced at least 330 s apart, measured from the newest magic-link token: 331 / 334 / 332 / 330 s;
  - `docker restart e2e-backend` plus a health wait before each;
  - `:3002` was free each time;
  - cumulative magic-link exchanges were 1 / 2 / 4 / 5, the same as #1753.
- **Concurrency:**
  - the g89-i full `./mvnw verify` ran alongside the gate, and I never touched it or its JVMs;
  - by 15:48 SAST no g89-i Maven or Surefire process remained, so it ended on its own;
  - before the gate, the KC app services were stopped with `svc.sh stop frontend portal gateway backend`. The `b2b-*` infra containers were untouched.
- **Timing:** every run was on 2026-11-11, 15:07–15:48 SAST. The UTC and SAST dates were equal throughout.
- **Drivers in `raw/`:**
  - `lib.sh`: the #1753 lib with WT, RAW and W changed and fontprobe dropped;
  - `run-g1.sh`;
  - `rerun-lifecycle.sh`;
  - `run-g2.sh`;
  - `deadline-probe.sh`.

## Verdict table

- Counts are passed / failed / skipped from Playwright JSON `.stats`. No run had any did-not-run (dnr) tests.
- The per-test diff is `diff <(cut -f1,3,4,5 prior.tsv | sort) <(cut -f1,3,4,5 run.tsv | sort)` against `../pr1753-e2e/raw/` (`G1-*`, `R-default`, `G2-*`).

| Suite | Stack | p / f / s (dnr) | Baseline | Per-test diff vs #1753 | Status |
|---|---|---|---|---|---|
| portal vitest | — | 51 files / **284 passed** | 284 | — | **PASS** |
| legal-lifecycle | G1 | 68 / 6 / 31 (0) | 105/0/0 | 1 root failure (`day-07-14-work-trust:78`) + 5 cascade failures + 31 cascade skips | transient (C1) |
| legal-lifecycle, re-run | L (fresh) | **105 / 0 / 0** | 105/0/0 | **empty** | **PASS** |
| legal-depth-ii | G1 | **40 / 0 / 4** | 40/0/4 | **empty** | **PASS** |
| default `test:e2e` | G1 | **165 / 0 / 14** | 165/0/14 | **empty** vs #1753 `R-default`. Vs #1753 `G1-default`, only the F73 invoice-lifecycle ×8 rows differ, and they now pass | **PASS** |
| portal 90-day | G2 | **13 / 0 / 0** | 13/0/0 | **empty** | **PASS** |
| portal responsive | G2 | **21 / 0 / 0** | 21/0/0 | **empty** | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | **2 / 0 / 0**, both ran | 2/0/0 | **empty** | **PASS** |
| login-bare | G2 | **1 / 0 / 0** | 1/0/0 | **empty** | **PASS** |

- **Snapshots, with compares off:**
  - every run had 0 "writing actual", 0 snapshot mentions and 0 baseline files written;
  - curated documentation screenshots: L lifecycle 16, depth-ii 10 (as in #1753), G1 lifecycle 9 (the cascade skipped the rest);
  - all were new untracked files, with `tracked-modified=0`. Each was archived to the scratchpad and restored, leaving `after-restore git-status-lines=0`.
- G89-F73 (`invoices/invoice-lifecycle.spec.ts:88`) **did not fire** in this gate.

## Stack table

Every scored stack:
- was at sha `80931ef69`;
- had rc 0 and backend `UP`, with frontend / mock-idp / mailpit 200 and `E2E Boot-Seed Complete!`;
- had tenant `tenant_7d218705360b`, with `Website Redesign|ACTIVE` and `portal_deadline_view=0` at start;
- had compose `working_dir` `…/b2b-strawman-g89-cp7/compose`;
- removed the previous stack's volumes;
- ran **Flyway tenant `14|g89 contains trust data` / global `5|portal strip matter type suffix`**, which is **14 / 5** as expected.

| Stack | Purpose | Build (SAST) | rc | Backend image | Frontend image | Flyway tenant/global |
|---|---|---|---|---|---|---|
| G1 attempt 1 | — | 15:08:23 → 15:09:32 | **1** (e2e-postgres initdb ENOSPC) | built; mvnw package BUILD SUCCESS 42.9 s | built | — |
| G1 attempt 2 | — | 15:09:32 → 15:09:42 | **1** (same) | — | — | — |
| G1 | scored legal + default | 15:11:17 → 15:11:56 | 0 | `c0be72bbcc0a` | `87797a505c15` | 14 / 5 |
| L | lifecycle re-run | 15:18:48 → 15:19:29 | 0 | `c0be72bbcc0a` | `87797a505c15` | 14 / 5 |
| G2 | scored portal + F71 probe | 15:23:04 → 15:23:44 | 0 | `c0be72bbcc0a` | `87797a505c15` | 14 / 5 |

- **Backend provenance:**
  - On the G1 relaunch every backend layer was `CACHED`. The cache was populated minutes earlier by attempt 1, which ran `COPY src src` and `mvnw clean package` (BUILD SUCCESS) from this worktree. Only `e2e-postgres` failed in attempt 1.
  - The running jar contains `customerbackend/model/PortalDeadlineStatus.class`, compiled 13:08Z. That class is new in the PR, so the jar is the PR backend (`raw/G1-backend-jar-provenance.txt`). L and G2 ran the same image, `c0be72bbcc0a`.
- **Teardown:** `e2e-down.sh` at 15:47:53 returned rc 0, leaving 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.

## Deadline observation (G89-F71)

### What the specs assert, all passed on the PR head

- **`portal-client-90day/day-00-07.spec.ts:220`, Day 7:** `/deadlines` renders the "Diary dates" h1 and `deadline-list-empty` "No deadlines in this view". It asserts no status.
- **`portal-client-90day/day-45-75.spec.ts:181`, Day 75:**
  - The firm schedules a HEARING at today+14 and a GENERAL_3Y prescription due at today+21 on Website Redesign.
  - `/portal/deadlines` (default window) returns both ids.
  - The `deadline-row-COURT_DATE-*` and `deadline-row-PRESCRIPTION_TRACKER-*` rows render with the right accessible names and type chips.
  - The detail panels show the court reference and "General 3 years", with no firm-only judge or notes text, and no mark-read control.
  - Cancel / interrupt removes both rows from the API and the page.
  - Both dates are 14 and 21 days out, so both are `UPCOMING`. The spec asserts row presence, not status values.
- **`responsive/portal-pages.spec.ts:236` `deadlines-list`**, at 3 viewports: `deadline-list-empty` is visible.
- **The home Deadlines card:** no portal e2e spec asserts on it. The 90-day and responsive home specs passed, and the API under it is the same `findByCustomer` path the probe exercised.
- **Legal suites:** they reach the portal read model only through the lifecycle Day 45 prescription tracker. G1 left one row: `PRESCRIPTION_TRACKER | 2029-09-12 | UPCOMING` (stored at 13:13:50Z). Its status is correct for that date.

### Read-only DB vs API, G2 after the scored runs (`raw/deadline-probe.txt`)

- **How the rows were made:** the rows were created through the firm API as Alice (`POST /api/court-dates`, `POST /api/prescription-trackers`), with no SQL writes.
  - **DB read:** `select … due_date, status from portal.portal_deadline_view` (read-only).
  - **API read:** `GET /portal/deadlines` as `contact@acme.example.com`, using a magic-link exchange taken after the spacing.
- **Dates:** today (SA) was 2026-11-11, and the UTC date was the same.

| Source | due_date | Stored (DB) | API `/portal/deadlines` | Rule (`derive(due, today)`) | `?status=` filter returns it under |
|---|---|---|---|---|---|
| PRESCRIPTION_TRACKER (tracker EXPIRED) | 2026-11-08 | OVERDUE | OVERDUE | OVERDUE | OVERDUE |
| COURT_DATE | 2026-11-09 | OVERDUE | OVERDUE | OVERDUE | OVERDUE |
| COURT_DATE (today) | 2026-11-11 | DUE_SOON | DUE_SOON | DUE_SOON | DUE_SOON |
| COURT_DATE (+3) | 2026-11-14 | DUE_SOON | DUE_SOON | DUE_SOON | DUE_SOON |
| PRESCRIPTION_TRACKER (+5, tracker WARNED) | 2026-11-16 | DUE_SOON | DUE_SOON | DUE_SOON | DUE_SOON |
| COURT_DATE (+7, inclusive cutoff) | 2026-11-18 | DUE_SOON | DUE_SOON | DUE_SOON | DUE_SOON |
| COURT_DATE (+8) | 2026-11-19 | UPCOMING | UPCOMING | UPCOMING | UPCOMING |
| COURT_DATE (+30) | 2026-12-11 | UPCOMING | UPCOMING | UPCOMING | UPCOMING |

- **The default window** (today..+60) returns the 6 rows from today onward. A wide window (`from=today-30`) returns all 8.
- **The new SQL `CASE` filter** returns exactly the right partition:
  - `status=OVERDUE` → 2 rows;
  - `status=DUE_SOON` → 4 rows, including today and the +7 boundary;
  - `status=UPCOMING` → 2 rows, starting at +8;
  - `status=COMPLETED` → `[]`.

  Every call returned http 200.
- **Cleanup:** I cancelled the 6 court dates and interrupted the 2 trackers (all 200). That left `portal_deadline_view rows=0` and the API `[]`.
- **What this does and does not show:**
  - Stored == API for every row, because the rows were synced on the same day they were read.
  - The bug F71 fixes is a stored snapshot going stale as the calendar moves. That needs the clock to advance (or a SQL edit of `status`, which QA rules forbid), so the stale-row case **cannot be observed in e2e**.
  - It is covered by the PR's backend `PortalDeadlineStatusStalenessIntegrationTest` and `PortalDeadlineViewRepositoryStatusFilterTest`.
  - The e2e evidence shows that the read path and the new filter are live on the PR backend, return the rule's status at every boundary, and break nothing that reads deadlines.

## Failure classification

| ID | Failure | Where / when | Class | Proof |
|---|---|---|---|---|
| C1 | `legal-lifecycle/day-07-14-work-trust.spec.ts:78` "Bob: Log 120min on Sipho — letter of demand": `expect(getByRole('tabpanel')).toContainText("Bob Admin")` received a summary with only Carol. Cascade failures: `day-30-billing:175`, `day-45-recon-prescription:132`, `day-60-interest:97`, `day-90-review:95` and `:326`, plus 31 skips | G1, 15:11:58–15:15:10 SAST | **Transient, did not persist; not attributable to the PR.** Not proven (c), because step 2 was not triggered | Re-run on fresh stack L (15:19:30–15:22:31 SAST): **105/0/0**, empty diff |
| (a) | PR regression | — | **none** | — |
| (b) | intended change needing a spec update | — | **none** | — |

### C1 detail

- **The write happened.** The G1 DB has Bob Admin's 120-minute "Letter of demand" entry, `created_at 13:12:58.61Z`, 2 s after Carol's. The backend logged `TimeEntryService Created time entry 56d78acb… by member 53c86710…` at 13:12:58.62Z, with no WARN or ERROR around it.
- **The read was stale.** The failure screenshot (`raw/failure-artifacts/G1-lifecycle/day-07-14-work-trust-…/test-failed-1.png`) shows Fees · Time recording with Total 1h 30m, Contributors 1, Entries 1, and By Member listing only Carol. The spec switches tabs on the client right after the log-time action, with no reload. The panel did not reflect the committed entry within the 5 s poll.
- **The cascade is mechanical.** The later serial tests in the same describe were skipped, including "Alice: Create court date for Sipho", "Alice: Log 60min on Moroka" and "Alice: Record trust FEE_TRANSFER R8,500". Each downstream failure follows from that missing state:
  - no Motion row on Day 45;
  - no `morokaFeeNote` run-state;
  - interest averages without the R8,500 transfer (received R 8 064,52, expected 7 790,32);
  - the Day 90 fee-transfer dialog did not close.
- **Not the PR:**
  - The PR changes only portal-deadline backend classes. The failing path is the firm frontend matter time-summary and `TimeEntryService`, and none of it is touched.
  - The same image passed the identical suite 105/0/0 on L.
- **Conditions:** the load average was about 22 (5-min) on 10 cores during G1 lifecycle, with the concurrent g89-i verify at about 186 % CPU. It was 8.7 / 14.2 / 16.3 after L.
- **History:** I found no earlier failure of `:78` in `qa_cycle` evidence.
- **Suggested follow-up (not applied; the orchestrator decides):** log a LOW candidate: "matter Time-recording summary can lag a just-committed time entry under load (stale RSC/router data after the log-time action)". This would sit next to the known Next revalidation-ping class.

## Backend log (`raw/backend-log-scan.txt`)

Counts are JSON-level WARN/ERROR by logger:
- **G1:** **0 ERROR**. The WARN classes are as before: PDFBox font fallback, draft invoice without tax number, and `Forbidden … /time-summary/by-member` (the same restriction WARN as #1753). 0 deadline-related.
- **L:** **0 ERROR**, 0 deadline-related.
- **G2**, including the probe: **0 ERROR**, 0 deadline-related. The single `security.auth_failed … /api/clients` is the isolation spec's expected "Portal JWT blocked from org API".

## Raw files (`raw/`)

- **Scored runs:**
  - `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}`, `L-lifecycle.*` and `G2-{90day,responsive,isolation,login-bare}.*`;
  - a `*.dirty.txt` per run, listing the paths written before restore;
  - `portal-vitest.txt`, `pnpm-install.txt`.
- **F71:** `deadline-probe.txt` (and `deadline-probe.sh`).
- **Stacks:**
  - `stacks.txt` (checks, accounting, `portal_deadline_view` snapshots after every suite, and spacing);
  - `timeline.txt`;
  - `stack-{G1,L,G2}.log`;
  - `stack-G1-diskfull-attempt{1,2}.log` and `stacks-G1-diskfull.txt`;
  - `docker-disk-remediation.txt`;
  - `G1-backend-jar-provenance.txt`;
  - `e2e-down.txt`;
  - `backend-log-scan.txt`.
- **`failure-artifacts/G1-lifecycle/`:** the C1 screenshot and error-context, plus the error-context for each cascade failure.
- **`kc-svc-status-after.txt`:** the KC service status after the restart.
- **Drivers:** `lib.sh`, `run-g1.sh`, `rerun-lifecycle.sh`, `run-g2.sh`, `deadline-probe.sh`.

## Environment left behind

- **The e2e stack is down:** 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.
- **The KC app services are restarted and all healthy** (15:48:30 SAST, `raw/kc-svc-status-after.txt`):
  - backend 8080 (PID 16719);
  - gateway 8443 (16867);
  - frontend 3000 (17005);
  - portal 3002 (17054).

  The `b2b-keycloak`, `b2b-postgres`, `b2b-localstack` and `b2b-mailpit` containers were up and healthy throughout.
- **The worktree is removed.** `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp7` was at `80931ef69` with `git status --porcelain --untracked-files=all` empty, then removed with `git worktree remove`.
  - The per-run dirty archives (curated PNGs) and full test-results copies are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1754/`.
- **Docker VM disk:** 33.4G free after the remediation (dangling images and build cache older than 72 h removed; nothing else).
- Nothing was committed or merged.
