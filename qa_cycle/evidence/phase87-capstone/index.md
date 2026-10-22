# Phase 87 capstone (617C) — evidence index

Scenario: `qa/testplan/demos/legal-za-90day-keycloak.md` (307 checkbox lines by grep; the brief said 302). Stack: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, Keycloak :8180, Mailpit :8025, portal :3002), served from the main checkout at **`dac048f12`** (= worktree HEAD) after a fresh `./mvnw -q clean compile`. Walk date 2026-10-22.

## `svc.sh status` at start (Walker A, 15:13Z)

```
  SERVICE      PID      RUNNING  HEALTHY  PORT
  -------      ---      -------  -------  ----
  backend      60278    yes      yes      8080
  gateway      60449    yes      yes      8443
  frontend     60581    yes      yes      3000
  portal       60640    yes      yes      3002
```
(verbatim block + port-holder etimes in `session-0.md`)

## Sections

| Section | Doc lines | Checkbox lines | Walker | PASS | FAIL | DEFERRED | BLOCKED | N/A | File |
|---|---|---|---|---|---|---|---|---|---|
| Session 0 | 43–51 | 2 | A | 2 | 0 | 0 | 0 | 0 | `session-0.md` |
| Day 0 Phases A–K (+ 4 A–D checkpoints, 9 Day-0 checkpoints) — as recorded | 52–208 | 81 | A | 78 | 1 | 2 | 0 | 0 | `day-00.md` |
| Days 1–7 (59 steps + 1 back-ref row closing 0.50 / CP0.5; counts as recorded incl. the back-ref) | 209–302 | 59 | B | 44 | 14 | 0 | 2 | 0 | `days-01-07.md` |
| Days 8–21 | 303–365 | 39 | B | 31 | 8 | 0 | 0 | 0 | `days-08-21.md` |
| Days 22–35 (incl. Day 33) | 366–410 | 23 | C1 | 19 | 3 | 1 | 0 | 0 | `days-22-35.md` |
| Days 36–60 (incl. Day 30 SoA) | 411–474 | 35 | C1 | 21 | 9 | 5 | 0 | 0 | `days-36-60.md` |
| Days 61–75 (incl. Day 75 closure) | 475–506 | 19 | C2 | 15 | 4 | 0 | 0 | 0 | `days-61-75.md` |
| Days 76–90 | 507–564 | 33 | C2 | 22 | 9 | 1 | 0 | 1 | `days-76-90.md` |
| Exit checkpoints E.1–E.11 (incl. 5 E.11 sub-items) | 565–584 | 16 | C2 | 5 | 5 | 5 | 0 | 1 | `exit-checkpoints.md` |

The doc has **307** checkbox lines (`grep -c -- '- \[ \]'`; the brief's "302" undercounts), and every one has exactly one row; `days-01-07.md` carries one extra back-reference row (0.50).

### Normalised totals (for the closure report)

Two normalisations, without rewriting the earlier walkers' rows:
1. **Day 0:** Walker B's back-ref row "0.50 (back-ref)" (days-01-07.md) observed the deferred check → 0.50 and CP0.5 count as **PASS**. Day 0 = **80 PASS · 1 FAIL · 0 DEFERRED**; the back-ref row itself is not counted a second time (Days 1–7 = 43 PASS on its 59 step rows).
2. **BLOCKED → DEFERRED:** Walker B's two BLOCKED rows count as DEFERRED: **5.10 — DEFERRED, blocked by G-B11** (no trust-linked disbursement exists to approve, 5.9) and **6.4 — DEFERRED, blocked by G-B3** (Carol cannot open the matter or task). Walkers C1/C2 already used DEFERRED + gap id.

| Section | PASS | FAIL | DEFERRED | N/A | Rows |
|---|---|---|---|---|---|
| Session 0 | 2 | 0 | 0 | 0 | 2 |
| Day 0 | 80 | 1 | 0 | 0 | 81 |
| Days 1–7 | 43 | 14 | 2 | 0 | 59 |
| Days 8–21 | 31 | 8 | 0 | 0 | 39 |
| Days 22–35 | 19 | 3 | 1 | 0 | 23 |
| Days 36–60 | 21 | 9 | 5 | 0 | 35 |
| Days 61–75 | 15 | 4 | 0 | 0 | 19 |
| Days 76–90 | 22 | 9 | 1 | 1 | 33 |
| **Walk subtotal (Session 0 → Day 90)** | **233** | **48** | **9** | **1** | **291** |
| Exit checkpoints | 5 | 5 | 5 | 1 | 16 |
| **Grand total (all 307 checkbox lines)** | **238** | **53** | **14** | **2** | **307** |

Exit verdict: **not demo-ready** — E.1, E.2, E.3, E.8, E.9 FAIL; E.11 DEFERRED (Part B / closure agent; no `typecheck` script exists). See `exit-checkpoints.md`.

### Gap summary by severity (`gaps.md`, 52 gaps)

| Walker | BLOCKER | HIGH | MEDIUM | LOW | Total |
|---|---|---|---|---|---|
| A (G-A1…G-A7) | 0 | 0 | 1 | 6 | 7 |
| B (G-B1…G-B19) | 0 | 2 | 7 | 10 | 19 |
| C1 (G-C1…G-C12) | 0 | 2 | 5 | 5 | 12 |
| C2 (G-D1…G-D14) | 0 | 1 | 9 | 4 | 14 |
| **Total** | **0** | **5** | **22** | **25** | **52** |

HIGH: G-B3 (no way to add matter members; Carol/Member locked out), G-B7 (portal acceptance PDF blocked by X-Frame-Options), G-C9 (no disbursement write-off UI), G-C10 (fee-note lines drop the time-entry narrative), G-D1 (portal bare `/login` cannot resolve the firm; client emails carry no orgId).

Gaps: `gaps.md` (Walker A: G-A1…G-A7; Walker B: G-B1…G-B19; Walker C1: G-C1…G-C12; Walker C2: G-D1…G-D14). Handoffs: `HANDOFF-A.md`, `HANDOFF-B.md`, `HANDOFF-C1.md`, `HANDOFF-C2.md` (for the screenshot agent). Screenshots: `screens/`. Log excerpts: `logs/`.

## `svc.sh status` at Walker B start (15:27Z) and end

Start: backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (same PIDs as Walker A). End: see `HANDOFF-B.md`.

## Walker C1 (doc lines 366–474, 16:00Z–16:22Z)

`svc.sh status` at start (15:58Z) and end: backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (same PIDs as Walkers A and B). Walker C1 marks steps it could not attempt because of an upstream gap as DEFERRED with the gap id (no BLOCKED rows). Days 22–35: 23 rows; Days 36–60: 35 rows; last completed step 58.1 (doc line 474). Log excerpts `logs/walkC1-*`; ephemeral PDFs in `/tmp/617c-c1/`.

## Walker C2 (doc lines 475–584, 16:23Z–16:54Z)

`svc.sh status` at start (16:23Z) and end (16:54Z): backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (same PIDs as Walkers A, B and C1; port holders 60426/60574/60624/60683, etime ≈ 1 h 42 m at the end, i.e. never restarted). Stack SHA: main checkout `dac048f12` = worktree HEAD.

Walker C2 used DEFERRED (with reason or gap id) and no BLOCKED rows. Days 61–75: 19 rows; Days 76–90: 33 rows; exit checkpoints: 16 rows; last completed step 90.7 + E.11 (e). Log excerpts `logs/walkC2-*`; ephemeral closure-letter PDF/PNG in `/tmp/617c-c2/`.

Walker-made state changes to note for the closure: Sipho's sheriff disbursement was **billed** on INV-0004 (paid) rather than written off (G-C9) so the Day 75 happy-path pre-state could hold; Carol was made lead of the new collections matter so the Member could log time (G-B3); Sipho's matter was Completed, then Reopened (G-D2), then Closed. Portal 68.1 used the `?orgId=mathebula-partners` shim after the doc path failed (G-D1).

## Closure (617C.4, 2026-10-22)

The closure report is in `tasks/phase87-legal-native-shell.md` § Closure report. It uses these closure-agent excerpts:
- `logs/closure-metrics.txt`: brief D.2 metrics at `41cb211fb` and HEAD.
- `logs/closure-contract-tests.txt`: 5 contract tests, 24 testcases, 0 F/E/S.
- `logs/closure-vitest-guards.txt`: frontend 7 files / 132 tests; portal 1 / 3.
- `logs/closure-redirects.txt`: 6 frontend + 2 portal 308 probes.
- `logs/closure-attribution.txt`: code archaeology for the gap attribution.

The Phase 87 attribution of the HIGH and the Phase-87-touching MEDIUM gaps is appended to `gaps.md` § "Phase 87 attribution".
