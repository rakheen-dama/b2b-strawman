# 633C — KC-stack post-merge observation (PR #1732)

- **Observed:** 2026-11-05 07:47–07:50 SAST
- **main HEAD:** `2c6e6fd39ecf7739c8267eb0d9e313ce4c5fbb93` (633C merge)
- **Backend:** `./mvnw -q clean compile -DskipTests` exit 0; old :8080 holder PID 28024 killed via `svc.sh stop backend`, port confirmed free; new :8080 holder **PID 86551** (svc wrapper 86354), started 07:46:15 SAST (etime 04:03 at 07:50:18). `/actuator/health` UP.
- **Portal:** old :3002 holder PID 83564 (svc 83521) stopped, port confirmed free; new :3002 holder **PID 86801** (`next-server v16.3.8`, svc wrapper 86758), started 07:46:47 SAST (etime 03:31 at 07:50:18).
- **Gateway** (PID 334, ext) and **frontend** (PID 83710): healthy, left alone.
- **Viewports:** 1440×900 and 1024×768
- **Browser:** Playwright MCP (`mcp__plugin_playwright_playwright__*`, Chromium); KC portal auth via magic link pulled from Mailpit (`http://localhost:8025`)
- **Tenant:** `tenant_5039f2d497cf` (Mathebula & Partners, org `mathebula-partners`). No data was mutated; SQL was SELECT-only.

## Boot evidence (`kc-boot-log.txt`)

The deadline backfill line matches the expected one exactly, and the task backfill reported 2 skipped (already marked). 0 ERROR lines since boot. The log has no "Migrating schema" line: both tenant schemas were already at V10 ("Migrated schema … — 0 migrations applied").

```
Portal contact directory backfill: 0 tenants scanned, 2 skipped (already marked), 0 failed, 0 rows upserted
Portal task backfill: 0 tenants scanned, 2 skipped (already marked), 0 failed, 0 rows upserted
Portal deadline backfill: 2 tenants scanned, 0 skipped (already marked), 0 failed, 1 rows upserted
```

## DB evidence (`kc-db.txt`)

- `portal.portal_deadline_view`: 1 row. `COURT_DATE | COURT_DATE | Case management — Gauteng High Court | 2026-12-13 | UPCOMING | Court date — Case management — Gauteng High Court | d3f6be49-…` (Lerato's customer).
- `portal.portal_backfill_markers`: `portal_deadline_backfill` for both tenants (5039… row_count 1, 5639… row_count 0, both at 05:46:27Z, which is this boot); `portal_task_backfill` from the earlier 633A boot (26 / 0).
- Firm-side `court_dates`: `CASE_MANAGEMENT | 2026-12-13 | SCHEDULED | Gauteng High Court | (ref empty) | (judge empty) | Case Management Conference`. The firm-only string is **"Case Management Conference"**. Judge/magistrate is empty on KC, so the judge redaction cannot be tested with this data.
- Sipho: `portal_requests` REQ-0001 `IN_PROGRESS`, project_id NULL (client-level), 1/1 submitted; item "Certified copy of ID document" `SUBMITTED`. `portal_tasks` for matter d42337ad: `DONE` × 9.

## A. Lerato Mthembu (`lerato.mthembu@email.co.za`): §5b row 19

| Check | Result | Observed |
|---|---|---|
| `/deadlines` court date row visible at 1440 | PASS | Row innerText: `Case management — Gauteng High Court` / `Court date` / `UPCOMING` / `13 Dec 2026`, grouped under `WEEK OF 7 DEC 2026` (default window) |
| `/deadlines` row visible at 1024 | PASS | Same innerText; `scrollWidth 1024 == clientWidth 1024` (no horizontal overflow). Also 1440 == 1440 |
| Detail panel (opened at both widths) | PASS | `Deadline details / LABEL Case management — Gauteng High Court / DUE DATE 13 Dec 2026 / STATUS UPCOMING / DESCRIPTION Court date — Case management — Gauteng High Court / SOURCE From: Court date` |
| Firm-only description absent | PASS | `document.documentElement.outerHTML.includes('Case Management Conference')` → `false`, with the panel open at 1440 and at 1024 |
| Judge absent | PASS (vacuous) | `/judge\|magistrate/i` not in body text. The judge column is empty on KC, so redaction is not exercised by this data |
| Status badge text | Recorded (known D2) | textContent `UPCOMING`, `text-transform: none`, so this is the raw enum. Known D2 follow-up, not a 633C defect |
| Home "Upcoming deadlines" | Shown | `Upcoming deadlines 1 — Next 14 days`. See finding F-1 |

Screenshots: `kc-portal-1440-lerato-deadlines.png`, `kc-portal-1024-lerato-deadlines.png`, `kc-portal-1440-lerato-deadline-detail.png`, `kc-portal-1024-lerato-deadline-detail.png`, `kc-portal-1024-lerato-home.png`.

## B. Sipho Dlamini (`sipho.dlamini@email.co.za`): G88-F20/F29/F30/F31

| Check | Result | Observed (both widths unless noted) |
|---|---|---|
| `/requests` list title (F30) | PASS | `REQ-0001` / **`Request REQ-0001`** / `In progress` / `1/1 submitted` |
| `/requests` status label (F29) | PASS | Status leaf textContent `In progress` (DB `IN_PROGRESS`), text-transform none. Raw-enum leaf scan found nothing |
| `/requests/2c92a127-…` h1 (F30) | PASS | h1 textContent **`Request REQ-0001`** |
| Request status line (F29) | PASS | **`1/1 submitted • In progress`** |
| Item status (F29) | PASS | Item `Certified copy of ID document` (required) → **`Submitted`** (DB `SUBMITTED`). No `SUBMITTED`/`PENDING` leaf |
| `/matters/d42337ad-…` Tasks badges (F20) | PASS (Done only) | Tasks table Status cells: `Done` × **9** (count = 9 as expected). Raw-enum leaf scan (`OPEN/DONE/IN_PROGRESS/IN PROGRESS/…`) found nothing. KC data only has DONE tasks, so the `Open`/`In progress` labels are not exercised here |
| `/trust` → `/trust/d42337ad-…` balance card (F31) | PASS | `/trust` redirects to the single matter. Card: `Trust balance / R 0,00 / As of 29 Oct 2026 /` **`Sipho Dlamini v. Standard Bank (civil)`**. `/Matter [0-9a-f]{8}/` not present |
| Transactions Type column (F31) | PASS | Type cell textContent: `Refund`, `Payment`, `Deposit` (text-transform none). No raw enum |
| Overflow | PASS | scrollWidth == clientWidth on every Sipho page at both 1024 and 1440 |

Screenshots: `kc-portal-{1440,1024}-sipho-{requests,request-detail,matter-tasks,trust}.png` (8 files).

## Verdicts

- **§5b row 19 (KC /deadlines court date via backfill, sanitised): PASS.** The backfill upserted 1 row at boot. The court date was visible at both widths, the detail panel showed only the sanitised description, and the firm-only "Case Management Conference" was absent from the DOM. Judge redaction is not exercised because the judge field is empty on KC.
- **G88-F20 task badges: PASS.** 9 tasks, all `Done`, no raw enum. Only the DONE state exists on KC.
- **G88-F29 request status copy: PASS.** `In progress` (list and detail), `Submitted` (item).
- **G88-F30 request h1 fallback: PASS.** `Request REQ-0001` in the list title and the detail h1.
- **G88-F31 trust matter name + type labels: PASS.** `Sipho Dlamini v. Standard Bank (civil)`; `Deposit`/`Payment`/`Refund`.

### Known exceptions
- The deadline status badge renders the raw `UPCOMING` in the row and the panel. This is the D2 follow-up, not a 633C defect.
- Prescription deadlines are not observed on KC by design (D5: KC is the re-walk tenant, so no tracker was created). They were observed on the e2e stack in PR #1732.

### New observations (outside 633C scope, not fixed, flagged for triage)
- **F-1 (home tile window mismatch):** `portal/app/(authenticated)/home/page.tsx:143` calls `/portal/deadlines?within=14d`, but `PortalDeadlineController` only accepts `from`/`to`/`status` and defaults to today..+60d. As a result, the tile labelled "Next 14 days" shows `1` for a court date that is +38 days away (13 Dec 2026). This predates 633C (the last home/page.tsx change was 617A, #1639), but 633C's backfill is what makes it visible on KC.
- **F-2 (dev-mode double exchange):** on both logins the backend logged one successful `Exchanged magic link for portal JWT`, and the browser then logged a second `POST /portal/auth/exchange` → 401. For Lerato the page showed "Login Failed — Link expired or invalid", even though the session cookie was set: navigating to `/deadlines` was authenticated. For Sipho it redirected to `/home` normally. This looks like a React StrictMode double effect in `next dev`. It was not investigated further and is not 633C.
- **F-3 (raw enum in firm text):** the matter description reads "… Matter type: LITIGATION". This is firm-authored template description text, not a status enum. Cosmetic, pre-existing.
- **F-4 (cosmetic):** in full-page screenshots taller than the viewport, the sidebar background ends at the viewport height (`kc-portal-1024-sipho-trust.png`). This is a full-page-capture artefact of a viewport-height sidebar and is pre-existing.

## Side effects
- Restarted backend (svc.sh; `clean compile` wiped and rebuilt `backend/target/classes`) and portal (svc.sh). Gateway and frontend were untouched.
- 2 magic links were requested (1 for Lerato, 1 for Sipho) and both were consumed. Both portal sessions created audit/login records as normal for a login. No tenant data was mutated.
- `portal/next-env.d.ts` was already modified before this run. Left as is.
- Playwright MCP wrote snapshots/console logs to `.playwright-mcp/` (gitignored).
