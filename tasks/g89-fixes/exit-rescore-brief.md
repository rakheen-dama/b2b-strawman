# G89 exit re-score brief (row 9): targeted Keycloak re-walk on a fresh tenant

**Goal:** re-score the Phase 89 exit after every G89 fix batch merged (A–H, E, F; last merge `be5b06ecb`, PR #1753). **Exit bar: 0 HIGH + 0 MEDIUM open**, every scenario mismatch ruled and walked as amended. PASS means observed in the browser (root `CLAUDE.md` §3). Status vocabulary: PASS / FAIL / PARTIAL / DEFERRED / NOTE / SKIP. There is no PASS-with-note.

**Build:** main `819034db5` (= `be5b06ecb` + docs). The KC stack was restarted on it on 2026-11-11 at about 13:10 SAST (clean compile, `pnpm install --frozen-lockfile`, `svc.sh start all`). Tenant Flyway is V14 and global is V5.

## Recipe: same as 636B

Read `qa_cycle/evidence/phase89-rewalk/README.md` (stack, tenant, walk-list format) and `HANDOFF.md` (rulings 0.R–0.R5; **D-1 → option A**).

**Fresh tenant.** Take a `pg_dump` backup of the current `mathebula-partners` tenant schema plus its portal rows. Then re-create the tenant through the scenario's own Session 0, exactly as 636B did. Record the new schema name, and confirm Flyway: tenant 14, global 5.

**Driving.** Browser only, through **Playwright MCP** (`mcp__playwright__*`), never claude-in-chrome.
- Read-only SELECTs and the Mailpit API are allowed as evidence.
- **No SQL writes, no REST shortcuts for UI steps.**
- Prerequisite steps are driven quickly and recorded as NOTE, not scored.

**Scenarios, as amended:**
- `qa/testplan/demos/legal-za-90day-keycloak.md`
- `qa/testplan/demos/portal-client-90day-keycloak.md`

Read the founder notes (D3, D24, D26, D27, D28) on the amended lines. **D28** gives the Day 75 setup on Lerato's RAF matter: a Hearing within 7 days and a Delict prescription at about +20 days. **D27** makes Lerato the Day 75 actor.

**Time-dependent flows.** Interest runs need an ended period (G89-F11 guard). Follow the Batch A walk (`qa_cycle/evidence/g89-fixes/batch-A/RESULT.md`): a back-dated LPFF rate and deposits, then an interest run for a past period.

**Evidence** goes to `qa_cycle/evidence/g89-fixes/exit-rescore/` in `/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs`. Do **not** commit; the orchestrator commits. Layout:
- `README.md`
- `steps/<session>.md`, using the 636B row table format: id · step · status · observation · evidence
- `screenshots/`
- `logs/`

## Rows to score (636B row id → scenario step → gap)

| Row | Step | Gap(s) to re-check | 636B result |
|---|---|---|---|
| S1-02 | 0.5 | PARTIAL ("Submitting…" transient not captured) | PARTIAL |
| S1-29 | 5.3 | **F4**: amended to "Comments" | FAIL as written |
| S1-30 | 5.4 | F5 (hours update without reload) | PASS |
| S1-65 | 30.6-soa | PARTIAL (compare the full PDF/preview layout, not only the upper half); F57 wording "Fees billed in period" | PARTIAL |
| S1-67 | F16-c/d | F6 (Legal Practice Act / s86 wording) | PASS |
| S1-76 | 45.4 + 47.1–47.2 | PARTIAL (wow-6) | PARTIAL |
| S2-03 | INT-60 | **F11** (future period refused), F10 (wizard client names, "LPFF arrangement rate") | PASS |
| S2-12 | PRE-75-refund + APPROVE-75 | **F12** (refund over the matter balance refused, matter-named) | FAIL |
| S2-13 | 75.2–75.8 | F13, F14 (ledger totals; matter Trust tab figure); closure gate on the main path, with no CORR prerequisite needed now | PASS |
| S2-14 | 75.9 | **F16** (closure SoA excludes written-off), F21 (closure-letter date), F15 | PASS |
| S2-15 | C5 | **F17** (reconciliation list loads) | PASS |
| S2-25 | TR-90 | **F18** (Section 35 pack populated), F19, **F50** (every interest run in the FY), F47, F48 | FAIL |
| S2-28 | C3 | F20 | PASS |
| S2-41 | P-3.4 | **F27**: amended (item "Submitted", request "In progress") | FAIL as written |
| S2-45 | P-CP21.2 + portal trust | **F22** (portal balance = firm client ledger, after an interest post); **F39** (transaction dates) | FAIL |
| S2-50 | P-60.2 | F26 | PASS |
| S2-55 | P-75.1 | **F24**: amended, D27/D28 (Lerato, `/deadlines`, no email) | FAIL as written |
| S2-56 | P-75.2 | **F24**: amended (Due soon flag) | FAIL as written |
| S2-59 | P-75.6 | **F25**: removed (D24 also removed the download checkpoint) | FAIL as written |
| S2-64 | P-90.5 | **F23** (internal-draft documents not announced) | PASS |

**Exit checkpoints:** E.3 / P-E.3 (0 HIGH in the register) and P-E.8 (email follow-through on Day 0/7/21; Day 75 dropped by D26).
- Observe the **Day 7 weekly digest**. Trigger it by hand through `PortalDigestInternalController` (find the endpoint and auth in the code), then check Mailpit and the follow-through link without re-login.

## Deferred observations (owed from earlier batches)

| Item | What to observe |
|---|---|
| **632C** | A new payment's receipt PDF file name contains `-r1-`, and two receipts no longer share a file name (D10). |
| **634E(1)** | A thread with **more than 50 comments**: seed 52 through the UI with Playwright (a loop of real UI posts). All 52 are reachable (pagination or load-more), and the oldest is visible. |
| **F44** | The portal Firm actions feed shows the **interest-credit** event, for an interest run posted on this fresh tenant (deposits were verified in Batch E). |
| **G88-M9** | Both fee notes reach Paid, and the surface named in G88-M9 reflects it. Find M9's wording in `qa_cycle/evidence/phase88-rewalk/full/gaps.md`. |
| **F66** | Only if a run falls in 00:00–02:00 SAST: the projects specs use the SA date. Otherwise N/A. |
| **F56 / F58** | VERIFIED-BY-TEST (D22): AI is not configured on KC. Record N/A unless a provider is configured. |

## Not in scope

- **G89-F71** (portal deadline status never refreshed): it needs a controlled clock. The orchestrator reproduces it separately with an integration test.
- **Do not fix anything.** Any new defect goes to `steps/` as a candidate with file:line and evidence. The orchestrator files it.

## Sessions (run sequentially, one walker each)

- **W1:** stack identity + fresh tenant (Session 0) → Days 0–45 firm rows (S1-02 … S1-76, S2-03 if reached; INT-60 is Day 60, so it may fall in W2) + the 634E(1) seeding. Write `HANDOFF-W1.md` with tenant ids, users, matters, and anything W2 needs.
- **W2:** Days 46–90 firm rows (S2-03, S2-12 … S2-28) + the D28 setup + portal rows (S2-41 … S2-64) + P-E.8 / weekly digest + 632C, F44, G88-M9.

## Gotchas

These are in `tasks/lessons.md` and the memory notes.

- **Portal magic links:**
  - a double exchange gives 401 (dev quirk);
  - links are rate-limited (429), so space them out.
- **Cookies:** clear the Playwright context between firm and portal actors.
- **Machine:** stay on AC power, and hold `caffeinate -dims` during the walk.
- **Stale classes:** `svc.sh restart` can race. If the backend misbehaves, kill the port holder first and verify the PID and etime.
