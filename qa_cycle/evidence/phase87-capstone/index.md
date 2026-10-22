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

## Sections — the one detailed count table

This is the **single detailed count table** for 617C. The closure report (`tasks/phase87-legal-native-shell.md` C.9.2) carries only the grand totals and links here, so the two cannot drift.

Counts come from the files with `grep -cE '^\| [^|]+ \| <STATUS> \|' <file>` (the Result cell is the second column of every evidence table). They were re-run after the 617C review reclassification (2026-10-22).

| Section | Doc lines | Walker | File | Raw rows | Raw P / F / D / B / N/A (as in the file) | Normalised P / F / D / N/A | Normalisation |
|---|---|---|---|---|---|---|---|
| Session 0 | 43–51 | A | `session-0.md` | 2 | 2 / 0 / 0 / 0 / 0 | 2 / 0 / 0 / 0 | — |
| Day 0 Phases A–K (+ 4 A–D checkpoints, 9 Day-0 checkpoints) | 52–208 | A | `day-00.md` | 81 | 78 / 1 / 2 / 0 / 0 | **80** / 1 / **0** / 0 | n1: 0.50 + CP0.5 DEFERRED → PASS (closed by Walker B's back-ref row) |
| Days 1–7 | 209–302 | B | `days-01-07.md` | 60 (59 steps + 1 back-ref) | 44 / 14 / 0 / 2 / 0 | **43** / 14 / **2** / 0 | n1: back-ref row not counted again (−1 PASS); n2: 5.10, 6.4 BLOCKED → DEFERRED |
| Days 8–21 | 303–365 | B | `days-08-21.md` | 39 | 31 / 8 / 0 / 0 / 0 | 31 / 8 / 0 / 0 | — |
| Days 22–35 (incl. Day 33) | 366–410 | C1 | `days-22-35.md` | 23 | 19 / 3 / 1 / 0 / 0 | 19 / 3 / 1 / 0 | — |
| Days 36–60 (incl. Day 30 SoA) | 411–474 | C1 | `days-36-60.md` | 35 | 21 / 9 / 5 / 0 / 0 | 21 / 9 / 5 / 0 | — |
| Days 61–75 (incl. Day 75 closure) | 475–506 | C2 | `days-61-75.md` | 19 | **5** / 4 / **10** / 0 / 0 | 5 / 4 / 10 / 0 | n3: 10 PASS → DEFERRED (617C review) |
| Days 76–90 | 507–564 | C2 | `days-76-90.md` | 33 | 22 / 9 / 1 / 0 / 1 | 22 / 9 / 1 / 1 | — |
| **Walk subtotal (Session 0 → Day 90)** | 43–564 | | | **292 rows / 291 checkbox lines** | **222 / 48 / 19 / 2 / 1** | **223 / 48 / 19 / 1** (= 291) | |
| Exit checkpoints E.1–E.11 (incl. 5 E.11 sub-items) | 565–584 | C2 | `exit-checkpoints.md` | 16 | 5 / 5 / 5 / 0 / 1 | 5 / 5 / 5 / 1 | — |
| **Grand total (all 307 checkbox lines)** | | | | **308 rows / 307 lines** | **227 / 53 / 24 / 2 / 2** | **228 / 53 / 24 / 2** (= 307) | |

Arithmetic:
- Raw walk: PASS 2+78+44+31+19+21+5+22 = **222**; FAIL 0+1+14+8+3+9+4+9 = **48**; DEFERRED 0+2+0+0+1+5+10+1 = **19**; BLOCKED 2; N/A 1 → 222+48+19+2+1 = **292** rows (291 checkbox lines + the back-ref row).
- Normalised walk: PASS 222 + 2 (n1: 0.50, CP0.5) − 1 (n1: back-ref row) = **223**; DEFERRED 19 − 2 (n1) + 2 (n2) = **19**; FAIL **48**; N/A **1** → 223+48+19+1 = **291**.
- Grand: PASS 223+5 = **228**; FAIL 48+5 = **53**; DEFERRED 19+5 = **24**; N/A 1+1 = **2** → **307**.
- Before the 617C review the walk read 233 / 48 / 9 / 1 and the grand total 238 / 53 / 14 / 2. The difference is exactly the 10 reclassified rows (n3).

The doc has **307** checkbox lines (`grep -c -- '- \[ \]'`; the brief's "302" undercounts), and every one has exactly one row; `days-01-07.md` carries one extra back-reference row (0.50).

Normalisations (no earlier walker row is rewritten by n1/n2; n3 rewrites the Result cell and keeps the observed text):
- **n1 — Day 0 back-ref.** Walker B's row "0.50 (back-ref)" (days-01-07.md) observed the deferred check, so 0.50 and CP0.5 count as **PASS**, and the back-ref row is not counted a second time.
- **n2 — BLOCKED → DEFERRED.** Walker B's two BLOCKED rows count as DEFERRED: **5.10 — DEFERRED, blocked by G-B11** (no trust-linked disbursement exists to approve, 5.9) and **6.4 — DEFERRED, blocked by G-B3** (Carol cannot open the matter or task). Walkers C1/C2 already used DEFERRED + gap id.
- **n3 — substituted pre-state (617C review, 2026-10-22).** PASS means observed *as written* (CLAUDE.md §3; memory "No workarounds in QA cycles"). Ten PASS rows in `days-61-75.md` rested on state a walker substituted to get past a product gap. They are now **DEFERRED — blocked by G-xx (ran on substituted pre-state)**, with the observed behaviour kept in the Evidence cell and the note "(reclassified 617C review: …)":
  - **62.1** — G-B3: Carol (Member) was made lead of the collections matter at 61.1 so she could log time.
  - **68.2** — G-D1: the portal session came from the `?orgId=` shim after 68.1 FAILED on the doc's path.
  - **75.3, 75.4, 75.5, 75.6, 75.7, 75.8, 75.9, 75.11** — G-C9: Sipho's sheriff disbursement was billed on INV-0004 and paid instead of written off, so the nine closure gates went green.
  - Kept, with a review note: **61.1** PASS (the lead choice was a G-B3 workaround; 61.1's own observed outcome — client, FICA, matter from template — does not depend on it, so 61.1 stays PASS and the dependent 62.1 is DEFERRED), **65.1** PASS and **80.3** PASS (their outcome does not depend on Carol's entries), **75.10** FAIL (product defect independent of the pre-state).
  - Swept and unaffected: every other file. The sweep grepped `workaround|pre-state|substitut|made Carol|INV-0004|?orgId|instead|shim|walker choice` and read the HANDOFF notes. 22.4 (Bob as lead of the RAF matter he creates as the day's actor), 9.2 / 14.3 (Thandi as lead), 36.3 (sheriff fee left unbilled for the Day-45 step) and the made-up client emails are inputs the doc leaves open, not workarounds. Day 85 (estate matter close + override) does not depend on any substitution.

Exit verdict: **not demo-ready** — E.1, E.2, E.3, E.8, E.9 FAIL; E.11 DEFERRED (Part B / closure agent; no `typecheck` script exists). See `exit-checkpoints.md`.

### Gap summary by severity (`gaps.md`, 59 gaps)

Counted with `grep -cE '^\| G-[A-D][0-9]+ \| [^|]+ \| <SEV> \|' gaps.md`.

| Source | BLOCKER | HIGH | MEDIUM | LOW | Total |
|---|---|---|---|---|---|
| Walker A (G-A1…G-A7) | 0 | 0 | 1 | 6 | 7 |
| Walker B (G-B1…G-B19) | 0 | 2 | 7 | 10 | 19 |
| Walker C1 (G-C1…G-C12) | 0 | 2 | 5 | 5 | 12 |
| Walker C2 (G-D1…G-D14) | 0 | 1 | 9 | 4 | 14 |
| 617C review, from `screenshots.md` (G-D15…G-D21) | 0 | 0 | 2 | 5 | 7 |
| **Total** | **0** | **5** | **24** | **30** | **59** |

HIGH: G-B3 (no way to add matter members; Carol/Member locked out), G-B7 (portal acceptance PDF blocked by X-Frame-Options), G-C9 (no disbursement write-off UI), G-C10 (fee-note lines drop the time-entry narrative), G-D1 (portal bare `/login` cannot resolve the firm; client emails carry no orgId).

Gaps: `gaps.md` (Walker A: G-A1…G-A7; Walker B: G-B1…G-B19; Walker C1: G-C1…G-C12; Walker C2: G-D1…G-D14; 617C review: G-D15…G-D21 from the screenshot agent's observations). Handoffs: `HANDOFF-A.md`, `HANDOFF-B.md`, `HANDOFF-C1.md`, `HANDOFF-C2.md` (for the screenshot agent). Screenshots: `screens/`. Log excerpts: `logs/`.

## `svc.sh status` at Walker B start (15:27Z) and end

Start: backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (same PIDs as Walker A). End: see `HANDOFF-B.md`.

## Walker C1 (doc lines 366–474, 16:00Z–16:22Z)

`svc.sh status` at start (15:58Z) and end: backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (same PIDs as Walkers A and B). Walker C1 marks steps it could not attempt because of an upstream gap as DEFERRED with the gap id (no BLOCKED rows). Days 22–35: 23 rows; Days 36–60: 35 rows; last completed step 58.1 (doc line 474). Log excerpts `logs/walkC1-*`; ephemeral PDFs in `/tmp/617c-c1/`.

## Walker C2 (doc lines 475–584, 16:23Z–16:54Z)

`svc.sh status` at start (16:23Z) and end (16:54Z): backend 60278 · gateway 60449 · frontend 60581 · portal 60640 — all RUNNING/HEALTHY (same PIDs as Walkers A, B and C1; port holders 60426/60574/60624/60683, etime ≈ 1 h 42 m at the end, i.e. never restarted). Stack SHA: main checkout `dac048f12` = worktree HEAD.

Walker C2 used DEFERRED (with reason or gap id) and no BLOCKED rows. Days 61–75: 19 rows; Days 76–90: 33 rows; exit checkpoints: 16 rows; last completed step 90.7 + E.11 (e). Log excerpts `logs/walkC2-*`; ephemeral closure-letter PDF/PNG in `/tmp/617c-c2/`.

Walker-made state changes to note for the closure: Sipho's sheriff disbursement was **billed** on INV-0004 (paid) rather than written off (G-C9) so the Day 75 happy-path pre-state could hold; Carol was made lead of the new collections matter so the Member could log time (G-B3); Sipho's matter was Completed, then Reopened (G-D2), then Closed. Portal 68.1 used the `?orgId=mathebula-partners` shim after the doc path failed (G-D1). The 617C review reclassified the PASS rows that rested on these substitutions to DEFERRED (n3 above).

## Closure (617C.4, 2026-10-22)

The closure report is in `tasks/phase87-legal-native-shell.md` § Closure report. It uses these closure-agent excerpts:
- `logs/closure-metrics.txt`: brief D.2 metrics at `41cb211fb` and HEAD.
- `logs/closure-contract-tests.txt`: 5 contract tests, 24 testcases, 0 F/E/S.
- `logs/closure-vitest-guards.txt`: frontend 7 files / 132 tests; portal 1 / 3.
- `logs/closure-redirects.txt`: 6 frontend + 2 portal 308 probes.
- `logs/closure-attribution.txt`: code archaeology for the gap attribution.

The Phase 87 attribution of all 59 gaps is in `gaps.md` § "Phase 87 attribution" (code archaeology, not observed on the baseline stack). `logs/closure-coderabbit-lines.txt` holds the `## CodeRabbit:` line of all 52 Phase 87 PR bodies (re-verified by the 617C review).
