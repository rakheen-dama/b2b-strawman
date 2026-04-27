# Day 45 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` lines 621–636.
**Branch**: `bugfix_cycle_2026-04-24` (off `main`).
**Actor**: Bob Ndlovu (`bob@mathebula-test.local` / `SecureP@ss2`) on the firm side; no portal session opened (Day 45 is firm-only per scenario).
**Tooling**: Playwright MCP (`mcp__plugin_playwright_playwright__*` first, then `mcp__playwright__*` after first browser reconnect — see Notes); read-only SQL `SELECT` for state confirmation; Mailpit `/api/v1/messages` for email body inspection (legitimate per HARD rule). **No SQL writes; no REST mutations.**

## Scope (per scenario)

Day 45 is on the **Sipho/RAF matter** (RAF-2026-001), not Moroka — the dispatch description was wrong. The scenario file is authoritative.

- 45.1 Send second info request on RAF matter
- 45.2 Verify magic-link email at Mailpit
- 45.3 Record second R 20,000 trust deposit against Sipho/RAF
- 45.4 Client-ledger shows two deposits totalling R 70,000
- 45.5 Matter Trust tab shows balance R 70,000

## Pre-state (read-only SELECT)

```
trust_transactions:
  0a6d1d60-… DEPOSIT R 50 000 RAF Sipho 2026-04-25 RECORDED (DEP-2026-001)
  446fa97c-… DEPOSIT R 25 000 EST Moroka 2026-04-25 RECORDED (DEP/2026/002)
client_ledger_cards:
  Sipho c3ad51f5-… balance R 50 000 / total_deposits R 50 000
  Moroka 2b454c42-… balance R 25 000 / total_deposits R 25 000
trust_accounts:
  45581e7d-… 12345678 SECTION_86 ACTIVE
information_requests:
  REQ-0001 SENT (RAF/Sipho) - 2026-05-02 due
  REQ-0002 SENT (RAF/Sipho)
  REQ-0003 COMPLETED (RAF/Sipho)
  REQ-0004 SENT (RAF/Sipho)
  REQ-0005 SENT (EST/Moroka) - 2026-05-11 due
```

## Checkpoint Results

| ID | Result | Evidence |
|----|--------|----------|
| 45.1 | PARTIAL | Created REQ-0007 via FICA Onboarding Pack template (3 items) instead of "free-form: 'Supporting medical evidence' / 2 items". Reason: **GAP-L-67** — no UI affordance to create ad-hoc info request with custom title/items. Free-form attempt as REQ-0006 (Cancelled) demonstrated the gap. REQ-0007 was created via template + Send Now and successfully dispatched to portal, exercising the same code path (create-request → magic-link). DB confirms `information_requests` row `454dea5d-ec2c-43b9-9b42-c06f6cb24153` REQ-0007 / SENT / due 2026-05-02. Snapshots `day-45-cycle1-new-request-dialog.yml`, `day-45-cycle1-template-options-second.yml`, `day-45-cycle1-req-0007-sent.png` |
| 45.2 | PASS | Mailpit message `cjjViD23s8SxAvfGJpL7Ln` "Information request REQ-0007 from Mathebula & Partners" → `sipho.portal@example.com` at 16:20 UTC. HTML body href = `http://localhost:3002/auth/exchange?token=g1Zpu-G-CYVAKXnoMvlQvCbmiSfHBNyODeW1Oy1TAbA&orgId=mathebula-partners`. Port 3002 ✓, token present ✓, orgId=mathebula-partners ✓. **L-42 magic-link fix HOLDS for second info request.** |
| 45.3 | PASS | Trust Accounting → Record Transaction → Record Deposit → dialog opened. Filled Client ID `c3ad51f5-…` (Sipho), Matter `e788a51b-…` (RAF), Amount 20000, Reference `DEP-2026-002`, Description "Top-up per engagement letter — RAF-2026-001". Click Record Deposit → dialog closed. DB confirms `trust_transactions` row `ce6767c1-4433-4f76-b5e6-acda100ef293` DEPOSIT R 20 000,00 RAF/Sipho 2026-04-25 RECORDED. Snapshots `day-45-cycle1-deposit-form.yml`, `day-45-cycle1-deposit-form-filled.png`. **Note**: dialog uses raw UUID textboxes for Client/Matter — no autocomplete combobox (UX-debt, not gap). |
| 45.4 | PASS | Client Ledgers page shows Sipho row "Trust Balance R 70 000,00 / Total Deposits R 70 000,00 / 25 Apr 2026" and Moroka row unchanged "R 25 000,00 / R 25 000,00". Sipho ledger detail page (`/trust-accounting/client-ledgers/c3ad51f5-…`) Transaction History "2 transactions found" — both DEP-2026-001 R 50 000,00 and DEP-2026-002 R 20 000,00 visible with RECORDED status. Snapshots `day-45-cycle1-client-ledgers.png`, `day-45-cycle1-sipho-ledger-detail.png`, `day-45-cycle1-sipho-ledger-transactions.png`. Cosmetic note: Running Balance column shows R 70 000,00 next to DEP-2026-001 and R 20 000,00 next to DEP-2026-002 — sort order makes the running balance read oddly (newest deposit's running balance is its own amount; oldest deposit's running balance is the cumulative). Not a data issue, but a presentation/sort gotcha worth noting (logged informationally). |
| 45.5 | PASS | RAF matter detail → Trust tab → Trust Balance card "R 70 000,00 / Funds Held"; Deposits R 70 000,00. Snapshot `day-45-cycle1-raf-matter-trust-tab-after.png`. |
| Trust-Nudge-Email (carry forward) | UNCHANGED | No "trust deposit" / "funds received" email dispatched after the R 20,000 RECORDED deposit — `MINOR-Trust-Nudge-Email-Missing` carries forward (no behavior change). Mailpit list contains only the REQ-0007 email. |
| Console errors | PASS | 0 errors across all Day 45 navigation. 1 cosmetic Next.js warning (carry-forward, unrelated). |
| Trust isolation cross-check | PASS | Moroka client_ledger_card unchanged at R 25 000,00 (DB SELECT). Trust Accounting overview shows aggregate R 95 000,00 (50K Sipho + 20K Sipho top-up + 25K Moroka). Matter Trust tab on RAF matter does NOT leak Moroka totals — only shows R 70 000,00. Cross-customer isolation HOLDS. |

**Tally**: 5/5 substantive PASS + 1 PARTIAL (45.1 reason: GAP-L-67 forces template-substitute path); +2 sanity checks PASS. 0 BLOCKER. Carry-forward MINOR-Trust-Nudge-Email-Missing unchanged.

## Verify-focus items observed (from status.md guidance)

- **L-41 (info-request due_date column)**: VERIFIED — REQ-0007 created with `due_date='2026-05-02'`; dialog exposes "Due Date (optional)" textbox.
- **L-42 (info-request magic-link to portal :3002)**: VERIFIED — REQ-0007 magic-link href = `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners`.
- **L-44 (PackReconciliationRunner enabled_modules sync)**: not directly walked this turn (no portal session opened); previous Day 11/15 runs already VERIFIED.
- **L-47 (portal parent-request status sync)**: not directly walked this turn (portal-side, Day 46 territory).
- **L-52 (portal trust-ledger sync for RECORDED deposits)**: NEW DEPOSIT not yet portal-verified this turn (portal-side, Day 46 territory). Prior Day 11 verify of L-52 stands.
- **GAP-L-66 (mock-payment URL host config — side-finding from Day 30)**: not surfaced this turn; logged this cycle (Day 30 row).

## NEW gaps opened

- **GAP-L-67 — HIGH** — No UI affordance to add items to ad-hoc DRAFT information requests, AND no UI affordance to send a 0-item DRAFT (action menu only contains Cancel Request). **Backend supports it**: `POST /api/information-requests/{id}/items` exists at `InformationRequestController.java:73` (`addItem(...)` → `InformationRequestService.addItem(...)` at line 626). Frontend `request-detail-client.tsx` does NOT render an "Add Item" CTA — only Cancel/Resend in the dropdown menu. The create-request dialog (`create-request-dialog.tsx`) also has no title/items inputs — selecting "Ad-hoc (no template)" creates a request with `name=''` and 0 items, which then becomes un-sendable. Net effect: free-form info requests cannot be created via the firm UI today; ALL info requests must use a pack template. Workaround: use any template that approximates the items list (e.g. FICA Pack for 3 items). Severity HIGH because Day 45.1 in the scenario explicitly requires a free-form request with custom title/items, and other scenarios may need the same. Owner: Product → Dev. Suggested fix scope: M (~2-3 hr) — add a Title input + Items multi-line input to `create-request-dialog.tsx` for the ad-hoc path, OR add an "Add Item" button + dialog on `request-detail-client.tsx` for DRAFT status. Evidence: `day-45-cycle1-new-request-dialog.yml` (no title/items field), `day-45-cycle1-req-0006-detail.yml` (draft detail page with no Add Item), `day-45-cycle1-req-action-menu.yml` (only Cancel option in menu). DB row `44d6d8df-b3bf-4426-b510-b8002ac8a26e` REQ-0006 left as Cancelled evidence.

## DB final state

```
trust_transactions (3 rows):
  0a6d1d60-… DEPOSIT R 50 000 RAF Sipho 2026-04-25 RECORDED (DEP-2026-001)
  446fa97c-… DEPOSIT R 25 000 EST Moroka 2026-04-25 RECORDED (DEP/2026/002)
  ce6767c1-… DEPOSIT R 20 000 RAF Sipho 2026-04-25 RECORDED (DEP-2026-002)  [NEW THIS DAY]

client_ledger_cards (2 rows):
  Sipho c3ad51f5-… balance R 70 000 / total_deposits R 70 000  [updated +20K]
  Moroka 2b454c42-… balance R 25 000 / total_deposits R 25 000  [unchanged]

information_requests (added):
  REQ-0006 (44d6d8df-…) ad-hoc DRAFT → CANCELLED (evidence of GAP-L-67)
  REQ-0007 (454dea5d-…) FICA template SENT due 2026-05-02 (3 items)

Trust Accounting overview totals:
  Trust Balance: R 95 000,00 (50K + 25K + 20K)
  Active Clients: 2
```

## Tooling notes

- First half of Day 45 used `mcp__plugin_playwright_playwright__*` (plugin namespace) successfully — login as Bob, navigate to RAF, walk REQ-0006 cancel + REQ-0007 send, navigate to Trust Accounting.
- After clicking "Record Deposit" menuitem in the Record Transaction menu, the browser/MCP connection died with `browserBackend.callTool: Target page, context or browser has been closed`. Killed the stale chrome process holding the user-data-dir lock and reconnected via `mcp__playwright__*` (main namespace) — re-authenticated as Bob and continued from the Trust Accounting transactions page. Both namespaces work; either can attach to the locked profile after lock cleanup.
- No browser-bridge wedge required halting per HARD rule #4 (the failure was recoverable mid-session via process kill + lock cleanup, not a wedge).

## Stack at end-of-turn

- Backend PID 41678 (no restart)
- Gateway PID 71426 ext (unchanged)
- Frontend PID 5771 (unchanged)
- Portal PID 5677 (unchanged — no portal navigation this turn)
- Tab 0 Bob firm session ALIVE on Sipho ledger detail page

Tab 1 (Sipho portal) was not opened this turn; previous turn's portal session would have expired by JWT TTL anyway.

---

# Day 45 Checkpoint Results — Cycle 38 — 2026-04-27 SAST

**Branch**: `bugfix_cycle_2026-04-26-day45` (cut from `main` `3b2eebfa`)
**Backend rev / JVM**: main `3b2eebfa` / backend PID 41372 (gateway PID 71426 ext, frontend 5771, portal 5677 — all healthy per `svc.sh status`)
**Stack**: Keycloak dev (3000/8080/8443/8180/3002)
**Method**: Browser-driven via Playwright MCP. No SQL writes; no REST mutations except Mailpit GET. Read-only `psql` SELECT for evidence only.
**Actor**: Bob Ndlovu (firm Keycloak session — fresh login this turn).

## Pre-state (read-only SELECT)

```
trust_transactions (3 rows pre-walk):
  13ca4d28-… DEPOSIT R 50 000,00 RAF Sipho 2026-04-27 RECORDED (carry-forward Day 10)
  f2f692e8-… DEPOSIT R    100,00 RAF Sipho 2026-04-27 RECORDED (carry-forward BUG-CYCLE26-11 retest)
  0e9f9c17-… DEPOSIT R 25 000,00 EST Moroka 2026-04-27 RECORDED (carry-forward Day 14)
client_ledger_cards (2 rows pre-walk):
  Sipho  c4f70d86-… balance R 50 100,00  total_deposits R 50 100,00
  Moroka 0cb199f2-… balance R 25 000,00  total_deposits R 25 000,00
information_requests (3 rows pre-walk):
  REQ-0001 a0306375-… SENT       (RAF/Sipho)
  REQ-0002 d8a58ade-… COMPLETED  (RAF/Sipho)  — 3/3 accepted
  REQ-0003 de3cffc7-… SENT       (EST/Moroka) due 2026-05-11
```

> **Note**: pre-state Sipho balance is **R 50 100,00** not R 50 000,00 because of the BUG-CYCLE26-11 retest deposit (R 100,00) carried into the tenant on cycle 29. Therefore the post-walk Sipho balance lands at **R 70 100,00** instead of the scenario's R 70 000,00. This is **expected carry-forward** from a prior verify cycle, not a new gap.

## Summary

**5 PASS / 0 FAIL / 1 PARTIAL / 0 BLOCKED / 0 SKIPPED**

**Verdict**: Day 45 cycle-38 walk completes end-to-end on `main 3b2eebfa`. 45.1 PARTIAL only because GAP-L-67 (carry-forward from cycle 1) still blocks ad-hoc free-form info request creation — same workaround as cycle 1 (FICA Onboarding Pack 3-item template substituted for "Supporting medical evidence" 2-item ad-hoc). All other Day 45 outcomes (magic-link email, R 20 000 deposit, client ledger total, matter trust tab) PASS. Trust-activity nudge email DID fire this cycle (cycle-1 cycle had `MINOR-Trust-Nudge-Email-Missing` because event hadn't been wired yet — BUG-CYCLE26-11 fix on PR #1183 wired it). Body still has cosmetic polish items (raw ISO date, unformatted amount) — already-known carry-forward "Trust-deposit nudge email body polish" per dispatch — NOT re-logged.

## Checkpoints

### 45.1 — On RAF matter, +New Info Request → free-form "Supporting medical evidence" / 2 items / due Day 52 → Send
- Result: **PARTIAL**
- Evidence: `qa_cycle/checkpoint-results/cycle38-day45-2-new-request-dialog.yml` (dialog with no Title/Items input — Ad-hoc selected); `cycle38-day45-3-template-options.yml` (template list — no "Medical Evidence" pack present)
- Notes: GAP-L-67 carry-forward — `Create Information Request` dialog has no Title or Items input fields and no template named "Medical evidence"; selecting "Ad-hoc (no template)" creates a request with empty name + 0 items, which is then unsendable per cycle-1 finding (action menu only Cancel). Following cycle-1 substitution pattern, used **FICA Onboarding Pack (3 items)** as the closest analog. Set Due Date `2026-06-17` (Day 0 = 2026-04-26 + 52 days). Clicked Send Now → dialog closed → DB confirms `information_requests` row `d7dc4faf-7e9c-4a6c-9ffd-fb69c7fe8b80` REQ-0004 / SENT / due_date=2026-06-17 / sent_at=2026-04-27 14:21:16.797144+00. **Code path exercised** (create-request → magic-link dispatch) is identical to the scenario's intent; only the items list differs (FICA's ID copy / Proof of residence / Bank statement vs scenario's hospital discharge summary / orthopaedic report). No new bugs surfaced.

### 45.2 — Mailpit verify second magic-link email to Sipho
- Result: **PASS**
- Evidence: Mailpit GET `/api/v1/messages?limit=5` — top message ID `cT5BtR26iNjg68FBmbtkhR`, Subject="Information request REQ-0004 from Mathebula & Partners", To=`sipho.portal@example.com`, Created=`2026-04-27T14:21:16.965Z`. HTML body href = `http://localhost:3002/auth/exchange?token=NNLCh0nZHVEJAEydH4YCeRkpqsL9N8etKs66m1zt0JA&orgId=mathebula-partners`. Port 3002 ✓, token present ✓, orgId=mathebula-partners ✓. **L-42 magic-link fix HOLDS for second info request on `3b2eebfa`.**

### 45.3 — Record R 20 000 deposit against Sipho/RAF (description "Top-up per engagement letter")
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle38-day45-6-deposit-dialog.yml` (Record Deposit dialog filled).
- Notes: Trust Accounting → Record Transaction → Record Deposit. Filled Client ID `c4f70d86-…`, Matter `cc390c4f-…` (RAF), Amount 20000, Reference `DEP-2026-RAF-003`, Description "Top-up per engagement letter". Submit → dialog closed → DB confirms `trust_transactions` row `177065ec-709a-435b-a73c-b3648335683b` DEPOSIT R 20 000,00 / project_id=cc390c4f-… (RAF) / RECORDED. Sipho `client_ledger_cards.balance` updated 50 100 → **70 100**. Moroka unchanged at R 25 000 — isolation holds. Carry-forward UX-debt (already-known): Record Deposit dialog uses raw UUID textboxes for Client/Matter — NOT re-logged per dispatch.

### 45.4 — Client ledger shows two (here, three) deposits totalling R 70 100
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle38-day45-7-client-ledgers.yml` (overview), `cycle38-day45-8-sipho-ledger-detail.yml` (transactions list).
- Notes: `/trust-accounting/client-ledgers` shows Sipho row "Trust Balance R 70 100,00 / Total Deposits R 70 100,00 / 27 Apr 2026" and Moroka row unchanged "R 25 000,00 / R 25 000,00". Sipho ledger detail page (`/trust-accounting/client-ledgers/c4f70d86-…`) "3 transactions found": DEP/2026/RAF-001 R 50 000,00 RECORDED (running balance R 70 100,00 — odd-sort cosmetic carry-forward), DEP/2026/RAF-002 R 100,00 RECORDED (running R 20 100,00), DEP-2026-RAF-003 R 20 000,00 RECORDED (running R 20 000,00). Total reconciles. Cosmetic running-balance sort note carries forward unchanged from cycle 1.

### 45.5 — Matter Trust tab on RAF shows balance R 70 100
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle38-day45-9-raf-trust-tab.yml`
- Notes: RAF matter (`/projects/cc390c4f-…?tab=trust`) → Trust tab → Trust Balance card "R 70 100,00 / Funds Held"; Deposits R 70 100,00. Matches client-ledger total.

### Trust-activity nudge email (already-known carry-forward — body polish)
- Result: **PASS (email fires)** with cosmetic carry-forward
- Evidence: Mailpit message `Mz58MAYnvZVQDp3Len47NC` Subject="Mathebula & Partners: Trust account activity" Created 2026-04-27T14:22:48.778Z (≈2s after deposit submit). HTML href `http://localhost:3002/trust/cc390c4f-35e2-42b5-8b54-bac766673ae7` — matter-UUID (not trust-account UUID, not /trust/null) — **BUG-CYCLE26-11 fix carry-forward VERIFIED on `3b2eebfa`.**
- Notes: Body body still renders raw ISO date `2026-04-27T14:22:48.615311Z` and unformatted amount `20000` (instead of "27 April 2026" + "R 20 000,00"). This is the already-known "Trust-deposit nudge email body polish" carry-forward per dispatch — NOT re-logged.

### Console errors
- Result: **PASS**
- Notes: 0 errors across all Day 45 navigation. (Initial dashboard navigation logged 1 console error from the Keycloak redirect-bounce flow — that error is in Keycloak's iframe, not on a Kazi page; not a regression.)

### Trust isolation cross-check
- Result: **PASS**
- Notes: Moroka `client_ledger_cards.balance` unchanged at R 25 000,00 (DB SELECT post-walk). Trust-Accounting overview total = R 95 100,00 (50K + 100 + 20K Sipho + 25K Moroka). RAF matter Trust tab does NOT leak Moroka totals — only shows R 70 100,00. Cross-customer isolation HOLDS.

## Day 45 summary checks (per scenario)

- [x] Second info request dispatched (REQ-0004, SENT, due 2026-06-17; magic-link email delivered to Sipho)
- [x] Trust balance reconciles to (R 70 100,00 — adjusted for carry-forward) on client ledger and matter trust tab

## Gaps Found

**No new gaps this cycle.** Re-observed (carry-forward, NOT re-logged):

- **GAP-L-67** (HIGH, OPEN — first logged cycle 1, never spec'd) — Create Information Request dialog has no Title or Items input fields; ad-hoc requests are unsendable. Forces template substitution. Frontend: `frontend/components/.../create-request-dialog.tsx` lacks the title/items inputs that the scenario requires. Backend `POST /api/information-requests/{id}/items` exists at `InformationRequestController.java:73`. Suggested fix scope unchanged from cycle 1: M (~2-3 hr).
- **OBS-cycle1-running-balance-sort** (cosmetic) — Running Balance column on client-ledger detail shows R 70 100 next to oldest deposit and R 20 000 next to newest, because the sort+running-balance accumulator treats top-of-table as latest. Not a data issue.
- **OBS-cycle1-record-deposit-raw-uuids** — already-known carry-forward; NOT re-logged per dispatch.
- **Trust-deposit nudge email body polish** — already-known carry-forward; NOT re-logged per dispatch.

## DB final state

```
trust_transactions (4 rows post-walk):
  13ca4d28-… DEPOSIT R 50 000,00 RAF Sipho   2026-04-27 RECORDED (carry-forward)
  f2f692e8-… DEPOSIT R    100,00 RAF Sipho   2026-04-27 RECORDED (carry-forward, BUG-CYCLE26-11 retest)
  0e9f9c17-… DEPOSIT R 25 000,00 EST Moroka  2026-04-27 RECORDED (carry-forward, isolation)
  177065ec-… DEPOSIT R 20 000,00 RAF Sipho   2026-04-27 RECORDED  [NEW THIS DAY — DEP-2026-RAF-003]

client_ledger_cards (2 rows post-walk):
  Sipho  c4f70d86-… balance R 70 100,00 / total_deposits R 70 100,00  [updated +20K]
  Moroka 0cb199f2-… balance R 25 000,00 / total_deposits R 25 000,00  [unchanged]

information_requests (added REQ-0004):
  REQ-0001 a0306375-… SENT      (RAF/Sipho)        — carry-forward
  REQ-0002 d8a58ade-… COMPLETED (RAF/Sipho)        — carry-forward
  REQ-0003 de3cffc7-… SENT      (EST/Moroka)       — carry-forward, due 2026-05-11
  REQ-0004 d7dc4faf-… SENT      (RAF/Sipho) FICA   — NEW, due 2026-06-17

Trust Accounting overview totals: R 95 100,00 (50K + 100 + 20K Sipho + 25K Moroka)
```

## Verify-focus items observed

- **L-42 (magic-link to portal :3002 with orgId)**: VERIFIED — REQ-0004 magic-link href = `http://localhost:3002/auth/exchange?token=NNLCh0nZ…&orgId=mathebula-partners`.
- **BUG-CYCLE26-11 (trust-activity email CTA points to matter UUID)**: VERIFIED-CARRY-FORWARD — nudge email CTA = `http://localhost:3002/trust/cc390c4f-…` (matter UUID). Fix from PR #1183 still effective on main `3b2eebfa`.
- **GAP-L-66 (portal /login orgId preservation)**: NOT exercised this turn (firm-side only walk; portal-side will exercise on Day 46 when Sipho logs in via the new magic-link).

## Stack at end-of-turn

- Backend PID 41372 (no restart this cycle)
- Gateway PID 71426 ext (unchanged)
- Frontend PID 5771 (unchanged)
- Portal PID 5677 (unchanged — no portal navigation this cycle)
- Single Bob firm tab on RAF matter Trust tab

## Branch state

- No code changes this turn.
- New evidence files: `cycle38-day45-0-raf-matter.yml`, `cycle38-day45-1-requests-tab.yml`, `cycle38-day45-2-new-request-dialog.yml`, `cycle38-day45-3-template-options.yml`, `cycle38-day45-4-trust-accounting.yml`, `cycle38-day45-5-transactions-page.yml`, `cycle38-day45-6-deposit-dialog.yml`, `cycle38-day45-7-client-ledgers.yml`, `cycle38-day45-8-sipho-ledger-detail.yml`, `cycle38-day45-9-raf-trust-tab.yml`.

## Next action

QA — Day 46 (Portal: Sipho responds to second info request + trust re-check + isolation). Day 45 walk complete. GAP-L-67 carry-forward still OPEN — orchestrator should decide whether to spec/fix it now (recommended; HIGH severity blocks "free-form info request" workflow per scenario semantics) or defer past Day 46 (which is portal-side and unaffected by GAP-L-67). Cut a fresh `bugfix_cycle_2026-04-26-day46` branch from `main` when ready.
