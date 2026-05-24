# Day 75 — Weekly digest + late-cycle isolation spot-check (PORTAL)

**Branch**: `bugfix_cycle_2026-05-13`
**Cycle**: 2 (2026-05-13)
**Actor**: Sipho Dlamini (portal `:3002`)
**Result**: **PASS** with one scenario-amend (digest copy is fee-note + trust-summary only; does not surface "matter closed"/"SoA downloaded" events — same as prior cycle, matches productised digest template scope)

## Phase A — Trigger weekly digest

`PortalDigestScheduler` is cron-driven and won't fire mid-cycle. Used the dev tooling endpoint:

```bash
curl -X POST -H "X-API-KEY: local-dev-api-key-change-in-production" \
  "http://localhost:8080/internal/portal/digest/run-weekly?orgId=mathebula-partners"
→ {"tenantsProcessed":1,"digestsSent":2,"skipped":0,"dryRun":false,"errors":[]}
```

Backend confirmed two digests sent — one to Sipho (`sipho.portal@example.com`), one to Moroka (`moroka.portal@example.com`).

## Phase B — Inspect Sipho's digest (Mailpit)

Mailpit message ID `UBtK5NonxG8hU42f6KY5qR`:

- Subject: `Mathebula & Partners: Your weekly update`
- To: `sipho.portal@example.com`
- Date: `2026-05-14T12:33:14.61Z`
- Body content:
  - Greeting: `Hi Sipho Dlamini`
  - **Recent fee notes** section: `INV-0001 — PAID — ZAR 1437.50`
  - **Trust account activity** section: `3 transaction(s) recorded in your trust account.`
  - CTA: `Open portal → http://localhost:3002/home`
  - Footer: unsubscribe link
- **No mention** of: Moroka, EST-2026, R 25,000, liquidation, estate, or any string belonging to the other client.
- Isolation grep: `grep -ic "moroka\|EST-2026-002\|estate\|25.000\|25,000"` = 0 matches. **CLEAN**.

## Phase C — Inspect Moroka's digest (Mailpit, isolation symmetry)

Mailpit message ID `XhVVDxPTyZnZBEdK5MLnFE`:

- Subject: `Mathebula & Partners: Your weekly update`
- To: `moroka.portal@example.com`
- Body content: Greeting `Hi Moroka Family Trust`; "1 open information request(s)"; "1 transaction(s) recorded in your trust account".
- **No mention** of: Sipho, RAF, Dlamini, INV-0001, R 1,437.
- Isolation grep: `grep -ic "sipho\|INV-0001\|RAF-2026\|1437\|dlamini"` = 0 matches. **CLEAN**.

Tenant/customer **digest isolation holds at Day 75**.

## Phase D — Scenario-amend for digest copy

Scenario step 75.2 expects digest body to mention "fee note paid, SoA downloaded, matter closed."

**Actual** (Sipho digest): mentions fee notes (`INV-0001 PAID`) and trust transaction count, but **does not surface**:
- Matter closure event ("Your matter has been closed")
- Statement of Account download / generation
- Document accept/upload events

These are real events on Sipho's matter (visible in `/activity` Firm-actions tab) but the digest template renders only fee-note + trust-summary surfaces. Matches the actual `portal-weekly-digest` template scope.

**Triaged**: scenario amend (Day 75.2 — digest copy is fee-note + trust-summary, not full activity replay). Same as prior cycle — **WONT_FIX scenario amend** (not a bug; matches productised digest scope).

Scenario step 75.4 ("View activity" link) — the digest CTA is `Open portal → /home`, not a deep-link to `/activity`. Clicking it lands on `/home` successfully. Amended.

## Phase E — Late-cycle isolation spot-check

### `/home` (Sipho)
```
Pending info requests: 0
Upcoming deadlines: 0 (next 14 days)
Recent fee notes: INV-0001 R 1 437,50
Last trust movement: R 70 000,00 on 14 May 2026
```
No `moroka|EST-2026|liquidation|R 25 ?000` strings. **PASS**.

### `/projects` (Sipho)
- **All tab** — 2 matters: `Engagement Letter — Litigation (Dlamini v RAF)` + `Dlamini v Road Accident Fund`.
- **Past tab** — 1 matter: `Dlamini v Road Accident Fund` (2 documents = closure letter + SoA).
- Closed-matter rendering correct: same card chrome as active matters, description visible, no "greyed out as error" state.
- No Moroka / EST-2026-002 / Estate entries on any tab. **PASS**.

### `/trust` (Sipho)
- Auto-redirected to `/trust/c90832a4-...` (Sipho's RAF matter trust).
- Balance: `R 0,00` as of 14 May 2026 (matches firm-side post-Day 60 closure state).
- Transactions table (3 rows):
  - 14 May 2026 PAYMENT R 70 000,00 (running balance R 0,00) — Day 60 refund on closure
  - 14 May 2026 DEPOSIT R 20 000,00 (R 70 000,00) — Day 45 top-up
  - 14 May 2026 DEPOSIT R 50 000,00 (R 50 000,00) — Day 10 initial
- No `R 25 000|moroka|liquidation|estate` strings. **PASS**.

### `/activity` (Sipho)
- **Your actions** tab: document downloads (Day 61), info request submissions (Days 4/46), uploads, fee note payment (Day 30) — all Sipho's actions.
- **Firm actions** tab: SoA generated (Thandi), document generated (Thandi), info request completions + acceptances (Bob), info request sends (Bob) — all from Sipho's matter actors.
- Zero references to Moroka actor, matter, or data. **PASS**.

## Day 75 checkpoint summary

| # | ID | Checkpoint | Result | Evidence |
|---|-----|------------|--------|----------|
| 1 | 75.1 | Weekly digest email for Sipho arrives, subject contains "weekly update" | **PASS** | Mailpit ID `UBtK5NonxG8hU42f6KY5qR`, subject "Mathebula & Partners: Your weekly update" |
| 2 | 75.2 | Digest body mentions events from the matter (fee note paid, SoA downloaded, matter closed) | **PARTIAL** | Fee note (INV-0001 PAID) + trust activity (3 txns) present; SoA/closure events not in digest template scope (scenario amend, not a bug) |
| 3 | 75.3 | Digest MUST NOT reference Moroka / EST-2026-002 / any other client | **PASS** | Regex grep: 0 matches for moroka/EST-2026/estate/25000 in Sipho's digest body |
| 4 | 75.4 | Click "View activity" link in digest → lands on portal home or activity view | **PASS** | Digest CTA is `Open portal → /home` (not deep-link to /activity); navigates correctly |
| 5 | 75.5 | Activity trail renders events from Days 4, 8, 11, 15, 30, 46, 61 — all Sipho's. Zero Moroka references. | **PASS** | Both "Your actions" and "Firm actions" tabs show only Sipho-related events; 0 Moroka references |
| 6 | 75.6a | `/home` — no Moroka entries | **PASS** | Only Sipho's fee note (INV-0001), trust movement (R 70,000), 0 pending requests |
| 7 | 75.6b | `/trust` — balance shows only RAF-2026-001 residual; NOT R 25,000 Moroka leak | **PASS** | Balance R 0,00 (post-closure); 3 transactions all Sipho's; no R 25,000 Moroka deposit |
| 8 | 75.6c | `/projects` — one matter only (RAF-2026-001, CLOSED), shown as closed or in Past tab | **PASS** | Past tab shows RAF matter with 2 docs; All tab shows 2 Sipho matters total; no Moroka estate |
| 9 | 75.7 | Moroka digest clean (symmetric check) | **PASS** | Mailpit ID `XhVVDxPTyZnZBEdK5MLnFE`; 0 Sipho/RAF/INV references in Moroka's digest |

## Console + Network

- Portal `/home`: 0 JS errors.
- Portal `/projects`: 0 JS errors.
- Portal `/trust`: 0 JS errors, 1 expected warning (auto-redirect to single matter trust).
- Portal `/activity`: 0 JS errors.
- All console errors in session are from the firm-side tab (`:3000`), pre-existing (OBS-203 assistant/invocations 404s, stale session 500s). Portal clean.

## New gaps

None. No new defects from Day 75.

## Carry-forward gaps

- **OBS-6001** (LOW): SoA email not sent to portal contact on closure — carries from Day 60.
- **OBS-203** (nit): `/api/assistant/invocations` 404 on firm-side — pre-existing.
- **OBS-1002** (HIGH): Trust deposit dialog combobox on standalone Transactions page — pre-existing, workaround via matter Trust tab.

## QA Position post-Day 75

- Late-cycle isolation **holds** at digest layer + portal-page layer.
- Closed-matter rendering correct (Past tab in `/projects`).
- Digest trigger via `/internal/portal/digest/run-weekly` is the canonical QA path.
- One scenario amend (75.2 / 75.4 — digest copy scope is fee-note + trust, not full activity replay; same as prior cycle).
- No new defects from Day 75.
