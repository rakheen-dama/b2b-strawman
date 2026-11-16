# OBS — Phase 92 slice 645A (PR #1786) — interest-run approve/post refused; Interest page notice, no posting control

- **Observed:** 2026-11-16, UI run started 2026-11-16T11:41:35Z, API run started 2026-11-16T11:42:12Z (UTC)
- **Stack head:** `23a55222bcb789fc4a40fcb5e86467af17d342c1` (worktree-epic-645A, clean tree; commit 2026-11-16T11:05:36Z).
  Provenance: `e2e-backend` image created 11:35:57Z and `e2e-frontend` image created 11:28:34Z, both from compose dir `/Users/rakheendama/Projects/2026/worktree-epic-645A/compose`. Both images were built after the head commit.
- **Tenant:** `e2e-test-org` → schema `tenant_7d218705360b`. Trust account `39a2b53b-29c9-49fb-b550-163550fb2699`.
- **Run under test:** `59dcfaa9-8991-4e4f-a6fe-d9488cb00746`. It is DRAFT, period 2026-10-17 to 2026-11-16, calculated with 2 allocations. It was created by Alice Owner (member `089a08e1-…`), and the lifecycle suite made it on day 60.
- **Script:** `obs-645A.cjs` (Node 20.11.1 + `@playwright/test` from the worktree's `frontend/node_modules`, `chromium.launch()`, viewport 1440×900). It runs `node obs-645A.cjs ui` and then `node obs-645A.cjs api <runId>`. The DB snapshots come from `db-snapshot.sh`, which only runs read-only `select`s.
- **Deviation:** I drove a real Chromium from a library script instead of the Playwright MCP, because the user's live session holds the MCP Chrome profile. Login uses the same method as `e2e/fixtures/auth.ts` `loginAs`: a mock-IDP `/token` request, then the `mock-auth-token` cookie.

## Steps
1. For each of Alice (owner), Bob (admin) and Carol (member): open a new browser context, set the mock-auth cookie, and go to `/org/e2e-test-org/trust-accounting/interest` (networkidle).
   - Read `[data-testid=interest-posting-notice]` from the DOM.
   - Take a full-page screenshot.
   - List every button, link, menuitem and tab in the whole page, in `[data-testid=interest-page]`, and in each run row. Row menu triggers (`aria-haspopup=menu`) are opened so their items get listed too.
   - Count `getByRole(button|link|menuitem, {name:/post|approve/i})`.
   - Click the row's "Review" action, list the wizard dialog's controls, and screenshot it.
   - Record console errors.
2. Take a DB snapshot before the API calls (`db-before.txt`).
3. Bob: load the Interest page, then call `fetch('http://localhost:8081/api/interest-runs/<id>/approve', POST)` from inside the page (`page.evaluate`). The bearer token is read from the browser's own `mock-auth-token` cookie. `page.on('request'/'response')` records the exchange to `network.json`. After the call, reload the page and read the row status. Alice then does the same with `…/post`.
4. Take a DB snapshot after the API calls (`db-after.txt`). The concatenated before/after file is `db-proof.txt`.
5. Grep the backend log (`backend-log.txt`). The full 10-minute log is in `backend-full-10m.log`.

## Results

| # | Check | User | Result | Observed value |
|---|-------|------|--------|----------------|
| 1a | Page loads | Alice / Bob / Carol | PASS | HTTP 200 for all three, no redirect, `interest-page` visible |
| 1b | Notice present, verbatim | Alice / Bob / Carol | PASS | `role="alert"`, text exactly: "Calculated interest is not posted. Interest on trust money vests in the Legal Practitioners' Fidelity Fund (section 86(5) of the Legal Practice Act). Record interest from the bank statement when you reconcile." |
| 1c | No Post / Approve control on page or in rows | Alice | PASS | Page-body controls: `New Interest Run`, `Review`, `Delete draft`, `Add Rate`. Row controls: `Review`, `Delete draft`. No row menus. Role queries for /post\|approve/i: button 0, link 0, menuitem 0. Nothing in the app shell matches either (31 controls checked) |
| 1c | same | Bob | PASS | Same as Alice (30 controls checked across the whole page) |
| 1c | same | Carol | PASS | Page-body controls: `Review` only. A member gets no New Interest Run, Delete draft or Add Rate. Role queries: 0 / 0 / 0 |
| 1d | Console errors on page load | Alice / Bob / Carol | PASS | none |
| 2 | Wizard (row "Review") has no Post to Ledger / Approve | Alice / Bob / Carol | PASS | Wizard reads "Step 2 of 2 — Calculate", steps "1 Create, 2 Calculate", status Draft, and shows the allocations table (QuickCollect R 8,01 gross, Moroka R 43,01 gross). Its only controls are `Cancel` and `Close`. `getByRole(button,{name:/approve\|post/i})` count = 0 |
| 3a | `POST /api/interest-runs/{id}/approve` as Bob (run created by Alice), from the browser page | Bob | PASS | **400**, `application/problem+json`, `{"title":"Interest runs are not posted","detail":"Interest on trust money vests in the Legal Practitioners' Fidelity Fund (section 86(5) of the Legal Practice Act). Kazi no longer posts calculated interest. Record interest from the bank statement instead.","status":400,"instance":"/api/interest-runs/59dcfaa9-…/approve"}`. Title and detail match the spec exactly. After reload the UI shows Draft and posted date "—" |
| 3b | `POST /api/interest-runs/{id}/post` as Alice, from the browser page | Alice | PASS | **400**, same title and detail, `instance …/post`. After reload the UI shows Draft and posted date "—" |
| 3c | Network capture | Bob, Alice | PASS | `network.json` holds both request/response pairs, each with status 400 and the ProblemDetail body. The browser logged the expected "Failed to load resource: 400" console line for each call |
| 4 | Backend log | — | OBSERVED: nothing logged | No log line names the run, the refusal, `InvalidStateException`, 86(5) or "not posted". Between 11:41 and 11:42Z the log only has scheduler fan-out lines. The 400 is a handled `InvalidStateException`, which this service maps to a ProblemDetail without logging. That is informative, not a failure. The refusal's evidence is the network capture in 3a–3c |
| 5 | DB: nothing written | — | PASS | Before (11:42:09Z) and after (11:42:16Z) are identical. The run is `DRAFT` with `approved_by` NULL, `posted_at` NULL, and `updated_at` unchanged at 11:38:35.297571Z. 0 `trust_transactions` have a reference containing the run id (`INT-CREDIT-<run>…` / `INT-LPFF-<run>`). The account has 0 `INTEREST_CREDIT`/`INTEREST_LPFF` rows and 6 rows in total, before and after. Both allocations and all allocation lines have `trust_transaction_id` NULL |

**Overall: PASS.** Every gate item was observed in a real browser, over the network and in the database.

## Incidental observation (not a 645A gate item, not compared with main)
At 1440×900 the wizard's Calculate-step content is wider than the dialog. The period card and allocations table extend past the dialog's right edge, the "Client Share" column is clipped, and the `Cancel` button sits on the edge (see `wizard-*.png`). 645A did not change the dialog width (`DialogContent className="sm:max-w-2xl"`), so this is probably pre-existing. It would need a check against main before anyone files it.

## Artefacts (all in /tmp/p92-645A-obs/)
- `obs-645A.cjs`: the observation script. `db-snapshot.sh`: the read-only DB snapshot.
- `interest-page-alice.png`, `interest-page-bob.png`, `interest-page-carol.png`
- `wizard-alice.png`, `wizard-bob.png`, `wizard-carol.png`
- `controls-alice.json`, `controls-bob.json`, `controls-carol.json`: notice text, every control (whole page, page body, per row, menus), role-query counts, wizard controls and text, console errors
- `api-refusals.json`: both API calls with full status and ProblemDetail, plus the UI status after each
- `network.json`: browser request/response events for the two calls (auth header redacted)
- `db-before.txt`, `db-after.txt`, `db-proof.txt`: before/after plus the run creator's identity
- `backend-log.txt` (the grep result, 1 unrelated Flyway line), `backend-full-10m.log`
- `ui-run.log`, `api-run.log`, `ui-run-started.txt`, `api-run-started.txt`
