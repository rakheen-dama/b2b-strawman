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
