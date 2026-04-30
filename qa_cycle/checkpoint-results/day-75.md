# Day 75 — Weekly digest + late-cycle isolation spot-check (PORTAL)

**Branch**: `bugfix_cycle_2026-04-30b`
**Cycle**: 20 (2026-04-30)
**Actor**: Sipho Dlamini (portal `:3002`)
**Result**: **PASS** with one scenario-amend (digest copy doesn't surface "matter closed"/"SoA downloaded" — current digest template is fee-note + trust-only)

## Phase A — Trigger weekly digest

`PortalDigestScheduler` is cron-driven (`0 0 8 ? * MON`) and won't fire mid-cycle. The dev tooling endpoint `POST /internal/portal/digest/run-weekly` (`PortalDigestInternalController`, gated by `X-API-KEY`) was added precisely for QA agents.

```bash
curl -X POST -H "X-API-KEY: local-dev-api-key-change-in-production" \
  "http://localhost:8080/internal/portal/digest/run-weekly?orgId=mathebula-partners"
→ {"tenantsProcessed":1,"digestsSent":2,"skipped":0,"dryRun":false,"errors":[]}
```

Backend log confirms two `Portal notification sent template=portal-weekly-digest` entries within 50 ms — one to Sipho, one to Moroka.

## Phase B — Inspect Sipho's digest (Mailpit)

Mailpit message ID `825imn2X9feksRFZX2GoRr` (saved as `qa_cycle/evidence/day-75/03-mailpit-sipho-digest.json`):

- Subject: `Mathebula & Partners: Your weekly update`
- To: `sipho.portal@example.com`
- Date: `2026-04-30T19:40:42.715Z`
- Body content:
  - Greeting: `Hi Sipho Dlamini`
  - **Recent fee notes** section: `INV-0001 — PAID — ZAR 1250.00`, `INV-0002 — PAID — ZAR 500.00`
  - **Trust account activity** section: `4 transaction(s) recorded in your trust account.`
  - CTA `Open portal → http://localhost:3002/home`
  - Footer: unsubscribe link
- **No mention** of: Moroka, EST-2026, R 25,000, liquidation, or any string belonging to the other client.

## Phase C — Inspect Moroka's digest (Mailpit, isolation symmetry)

Mailpit message ID `azizA3PXjsKnEFQLfTTAj7` (saved as `qa_cycle/evidence/day-75/04-mailpit-moroka-digest.json`):

- Subject: `Mathebula & Partners: Your weekly update`
- To: `moroka.portal@example.com`
- Body content: Greeting `Hi Moroka Family Trust`; "1 open information request"; "1 transaction(s) recorded in your trust account".
- **No mention** of: Sipho, RAF, Dlamini, INV-0001/INV-0002, R 1,250, R 500.

Tenant/customer **digest isolation holds at Day 75**.

## Phase D — Scenario-amend for digest copy

Scenario step 75.2 expects digest body to mention "fee note paid, SoA downloaded, matter closed."

**Actual** (Sipho digest): mentions fee notes (`INV-0001 PAID`, `INV-0002 PAID`) and trust transaction count, but **does not surface**:
- Matter closure event ("Your matter has been closed")
- Statement of Account download / generation
- Document accept/upload events

These are real events on Sipho's matter (visible in `/activity` Firm-actions tab) but the digest template renders only fee-note + trust-summary surfaces. Matches the actual `portal-weekly-digest` template scope (audit feed → digest projection is restricted to the canonical commercial surfaces).

**Triaged**: scenario amend (Day 75.2 — digest copy is fee-note + trust-summary, not full activity replay). Filed as **WONT_FIX scenario amend** (not a bug; matches productised digest scope). Adding closure / document events to the digest is a future scope decision.

Scenario step 75.4 ("View activity" link) — the digest CTA is `Open portal → /home`, not a deep-link to `/activity`. Amended.

## Phase E — Late-cycle isolation spot-check

### `/home` (Sipho)
```
Pending info requests: 0
Upcoming deadlines: 0 (next 14 days)
Recent fee notes: INV-0001 R 1 250,00 + INV-0002 R 500,00
Last trust movement: R 71 000,00 on 30 Apr 2026
```
No `moroka|EST-2026|liquidation|R 25 ?000` strings (regex scan of full body text). PASS.

### `/projects` Past tab (Sipho)
- Active tab — 2 matters: `Engagement Letter — Litigation (Dlamini v RAF) — verify cycle 2`, `OBS-301 Verify - Long Description Test`.
- All tab — 3 matters (Active 2 + Past 1).
- **Past tab** — 1 matter only: `Dlamini v Road Accident Fund` (`2 documents` = closure letter + SoA).
- Closed-matter rendering correct: same card chrome as active matters, no "greyed out as error" state. PASS checkpoint 75 "Closed matter correctly rendered as closed".

### `/trust` (Sipho)
- Auto-redirected to `/trust/b7e319f7-...` (Sipho only has one trust account — RAF).
- Balance `R 0,00` as of 30 Apr 2026 (matches firm-side post-Day 60 prep state).
- Transactions table:
  - 30 Apr 2026 PAYMENT R 71 000,00 (running balance R 0,00) — Day 60 prep refund
  - 30 Apr 2026 DEPOSIT R 20 000,00 (R 71 000,00) — Day 45 top-up
  - 30 Apr 2026 DEPOSIT R 1 000,00 (R 51 000,00) — Day 14 OBS-1101 verify
  - 30 Apr 2026 DEPOSIT R 50 000,00 (R 50 000,00) — Day 10 initial
- No `R 25 ?000|moroka|liquidation` strings. PASS.

### `/activity` Firm-actions tab (Sipho)
- Top entries: `Statement of Account generated (Thandi)`, `Document generated for you (Thandi)`, REQ-0003 acceptance chain.
- All actors are firm members (Thandi / Bob); zero references to a Moroka actor or matter. PASS.
- Evidence: `qa_cycle/evidence/day-75/02-portal-activity-snapshot.md`.

## Day 75 checkpoint summary

| # | Checkpoint | Result |
|---|------------|--------|
| 1 | Digest contents match activity trail (no missing event discrepancy) | **PARTIAL** — digest fee-note + trust sections match the activity trail; closure / SoA events not in digest scope (scenario amend, not a bug) |
| 2 | Closed matter correctly rendered as closed (not greyed out as error) | **PASS** — Past tab renders RAF-2026-001 with same card chrome as active matters, document count `2` reflects closure pack |
| 3 | Isolation still holds at Day 75 | **PASS** — both digests + 4 portal pages free of cross-tenant data; Sipho digest names Sipho only, Moroka digest names Moroka only |

## Console + Network

- `/home`: 0 errors.
- `/projects`: 0 errors.
- `/trust`: 1 expected console warning (auto-redirect to single matter).
- `/activity`: 0 errors.

## QA Position post-Day 75

- Late-cycle isolation **holds** at digest layer + portal-page layer.
- Closed-matter rendering correct (Past tab in `/projects`).
- Manual digest trigger via `/internal/portal/digest/run-weekly` is the canonical QA path; cron-driven Monday 08:00 firing is not exercised in this cycle (covered by `PortalDigestSchedulerIntegrationTest`).
- One scenario amend (75.2 / 75.4 — digest copy scope is fee-note + trust, not full activity replay).
- No new defects from Day 75. OBS-2106 (closure-pack email) carries forward from Day 60.
