# G89 exit re-score: targeted Keycloak re-walk on a fresh tenant (evidence index)

- **Brief:** `tasks/g89-fixes/exit-rescore-brief.md`.
- **Recipe:** 636B (`qa_cycle/evidence/phase89-rewalk/README.md`, `HANDOFF.md`). D-1 is option **A**.
- **Status vocabulary:** PASS / FAIL / PARTIAL / DEFERRED / NOTE / SKIP.
- **Sessions:**
  - **W1:** stack, fresh tenant, Days 0–45, 634E(1). Steps in `steps/W1.md`, handoff in `HANDOFF-W1.md`.
  - **W2:** Days 46–90, D28 setup, portal rows, P-E.8 (incl. the Day 7 digest), deferred items. Steps in `steps/W2.md`.

## Build and stack (W1, 2026-11-11 13:07 SAST)

| Item | Value |
|---|---|
| Build | main `819034db5c101c617b52e1f153ee16e608f582ed`. origin/main `8ca82c935` is the same code plus the brief doc commit. `$MAIN` status: only untracked Phase 90 ideation and requirements files. |
| Services at identity (svc PID / port holder / start) | backend 56461 / **56639** (13:04:34)<br>gateway 56661 / **56786** (13:04:49)<br>frontend 56804 / **56847** (13:04:54)<br>portal 56853 / **56896** (13:04:59)<br>KC :8180 Docker (since 16 Oct). All cwd under `$MAIN`. |
| Backend after the D-1 reset | stopped 13:08:56; restarted by `svc.sh start backend`. :8080 holder **60902** (started 13:09:41), health UP, "Running tenant migrations for 1 schemas". |
| Health | 8080 UP · 8443 UP · 3000 200 · 3002/login 200 · KC realm `docteams` 200 · backend ERROR lines 0 (before and after W1) |
| Compiled migrations | tenant ends `V14__g89_contains_trust_data.sql`; global ends `V5__portal_strip_matter_type_suffix.sql` (target/classes 13:04) |
| Power | AC, 92 %; `caffeinate -dims` PID 58540 (W1) |
| Mailpit | 33 → cleared at the reset → 12 at W1 end (`logs/mailpit-w1.txt`) |

Detail: `logs/stack-identity.txt`, `logs/session0-reset.txt`.

## Tenant (fresh, D-1 option A)

| Item | Value |
|---|---|
| Org | "Mathebula & Partners", slug `mathebula-partners` |
| Schema | **`tenant_5039f2d497cf`**. The name is deterministic, so the old schema was dropped and re-created under the same name. |
| KC org id | **`fe76c0d4-2174-4001-ae58-af2175b0fb1d`** (the old org `2cc9bcfd-…` was deleted) |
| `public.organizations.id` | `36d051c2-6c71-45c7-8e90-2c0b8759491c` (created 11:12:16Z) |
| Flyway | tenant **V14** (14 rows, installed 13:12:16–13:12:18 SAST) · public **V5** · `org_phase86_squash_smoke` (`tenant_56399d06a773`) V14, untouched |
| Profile / packs | `vertical_profile = legal-za`; "Trust report pack seeded … 7 definitions" (`report_definitions` total 10) |
| Old tenant backup | Scratch: `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/backup/g89-w1-tenant-backup-202611111308.sql` (842 153 B) and `…-portal-rows.csv.txt` (24 376 B). The scratch area is session-local, so copy these out if they must be kept. |

## W1 results

PASS 7 · FAIL 0 · PARTIAL 0 · DEFERRED 0. The rows:
- S1-02
- S1-29
- S1-30
- S1-65
- S1-67
- S1-76
- 634E(1)

There is one new candidate, W1-c1 (LOW, scenario mismatch: 47.2 banking details). See `steps/W1.md`.

## Files

| Path | What |
|---|---|
| `steps/W1.md` | The W1 row table (scored + NOTE) and candidates |
| `HANDOFF-W1.md` | Tenant ids, users, matters, trust, fee notes and open items for W2 |
| `screenshots/` | 31 frames (W1) |
| `logs/` | stack, reset, provisioning, read-only SELECTs, PDFs + extracted text, Mailpit list, console log, `harness/` (the Playwright snippets used) |

## W2 (2026-11-11 13:51–14:27 SAST)

- **Stack:** the same as W1. Backend :8080 holder 60902 throughout; backend ERROR lines 0.
- **Portal:** swapped to a production build for the portal rows (636B §2.5), then restored to `next dev` (`logs/portal-swap.txt`).
- **caffeinate:** 69192 (killed at the end).
- **Navigate-hook marker:** `/tmp/.e2e-keycloak-mode`, created and then removed.

**Results:** PASS 14 · FAIL 0 · PARTIAL 0 · DEFERRED 0 · N/A 1 (S2-59, removed by D24). The rows:
- S2-03
- S2-12
- S2-13
- S2-14
- S2-15
- S2-25
- S2-28
- S2-41
- S2-45
- S2-50
- S2-55
- S2-56
- S2-57
- S2-64

P-E.8 is PASS. Deferred items:
- 632C PASS
- F44 PASS
- G88-M9 PASS (both W1 fee notes Paid)
- F66 N/A
- F56/F58 N/A (VERIFIED-BY-TEST, D22)

There are 4 new LOW candidates, W2-c1…c4 (see `steps/W2.md`).

## Full exit tally (W1 + W2)

| Row | Step | Gap(s) | 636B | Exit re-score | Walker |
|---|---|---|---|---|---|
| S1-02 | 0.5 | "Submitting…" transient | PARTIAL | **PASS** | W1 |
| S1-29 | 5.3 | F4 (amended "Comments") | FAIL as written | **PASS** | W1 |
| S1-30 | 5.4 | F5 | PASS | **PASS** | W1 |
| S1-65 | 30.6-soa | full layout, F57 | PARTIAL | **PASS** | W1 |
| S1-67 | F16-c/d | F6 | PASS | **PASS** | W1 |
| S1-76 | 45.4 + 47.1–47.2 | wow-6 | PARTIAL | **PASS** (banking via NOTE D-W1-b; W1-c1) | W1 |
| S2-03 | INT-60 | F11, F10 | PASS | **PASS** | W2 |
| S2-12 | PRE-75-refund + APPROVE-75 | F12 | FAIL | **PASS** | W2 |
| S2-13 | 75.2–75.8 | F13, F14, closure | PASS (after CORR) | **PASS** (no corrective entry) | W2 |
| S2-14 | 75.9 | F16, F21, F15 | PASS | **PASS** | W2 |
| S2-15 | C5 | F17 | PASS | **PASS** | W2 |
| S2-25 | TR-90 | F18, F19, F50, F47, F48 | FAIL | **PASS** (open LOW F69 cited) | W2 |
| S2-28 | C3 | F20 | PASS | **PASS** | W2 |
| S2-41 | P-3.4 | F27 (amended) | FAIL as written | **PASS** | W2 |
| S2-45 | P-CP21.2 + portal trust | F22, F39 | FAIL | **PASS** | W2 |
| S2-50 | P-60.2 | F26 | PASS | **PASS** | W2 |
| S2-55 | P-75.1 | F24 (amended, D27/D28) | FAIL as written | **PASS** | W2 |
| S2-56 | P-75.2 | F24 (amended) | FAIL as written | **PASS** | W2 |
| S2-57 | P-75.3 | sanitised detail | PASS | **PASS** | W2 |
| S2-59 | P-75.6 | F25 (removed, D24) | FAIL as written | **N/A (removed)** | W2 |
| S2-64 | P-90.5 | F23 | PASS | **PASS** | W2 |
| 634E(1) | >50-comment thread | — | owed | **PASS** | W1 |

**Rows:** 21 PASS · 0 FAIL · 0 PARTIAL · 0 DEFERRED · 1 N/A.

### Exit checkpoints

| Checkpoint | Result | Basis |
|---|---|---|
| **E.3 / P-E.3** (0 BLOCKER/HIGH in the register) | **PASS** | No HIGH is open in `phase89-rewalk/gaps.md`:<br>- F11, F12, F22: VERIFIED on KC, and re-observed here (S2-03, S2-12, S2-45).<br>- F52, F53, F54: VERIFIED.<br>- F56, F58: VERIFIED-BY-TEST (D22).<br>**MEDIUM:** F16 and F18, which were MERGED-AWAITING-VERIFY, were **observed PASS** here (S2-14, S2-25). Every other MEDIUM is VERIFIED.<br>**Still unrated:** F28 (D7 backlog), F32–F36 (backlog), F71 (reproduce-first, out of scope). These are outside the HIGH/MEDIUM count but are unruled. |
| **P-E.8** (Day 0/7/21 email follow-through) | **PASS** | Day 0 magic link → `/matters`. Day 7 digest, triggered by hand through `POST /internal/portal/digest/run-weekly` → Open portal → `/home`. Day 21 trust email → `/trust/<matter>`. None needed a re-login. Day 75 was dropped by D26. |

### Deferred items

| Item | Result |
|---|---|
| 634E(1) | PASS (W1) |
| 632C | PASS |
| F44 | PASS |
| G88-M9 | PASS |
| F66 | N/A (not 00–02 SAST) |
| F56 / F58 | N/A (VERIFIED-BY-TEST, D22) |

### New candidates

All are LOW proposals, for the orchestrator to rule:
- **W1-c1:** scenario mismatch, 47.2 banking.
- **W2-c1:** an interest period ending today is accepted.
- **W2-c2:** the digest prints raw "PAID" / "ZAR 5335.00".
- **W2-c3:** the SoA trust Payments "Type" column prints the raw enum.
- **W2-c4:** the Overview task-completion tile is stale until the cache TTL.

**Exit bar as observed:** 0 HIGH + 0 MEDIUM open. Every amended scenario line was walked as amended. Still open for the orchestrator:
- the rulings on the unrated backlog ids;
- the five LOW candidates.
