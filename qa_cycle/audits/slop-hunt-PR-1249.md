# Slop hunt — PR #1249: qa(cycle 22): verify OBS-2107 + Day 90 remaining exit gates

**Batch**: E — bookkeeping/test-fix
**Reviewed**: 2026-05-01
**Verdict**: NIT (E.15 claim was the seam that PR #1250 caught)

## PR description vs diff

Description claims OBS-2107 VERIFIED post-V118 + Day 90 remaining exit gates run (E.1, E.2, E.3, E.5, E.7, E.8, E.15, E.16). Diff matches: 1 new day-90-exit.md (111 lines), 8 evidence files (4 dialog JSONs + 3 OBS-2107 verify artefacts + 1 status.md update). No production code touched. Scope honest.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | MEDIUM | Evidence-free PASS claim | `qa_cycle/checkpoint-results/day-90-exit.md:91-99` (E.15 row) | E.15 ("Test suite gate") is reported as PASS for the front-end + portal sub-gates and **"backend verify in flight at writeup time (see bash output beebf7apv)"** for the backend sub-gate. The summary table at line 113 then reports E.15 as overall PASS. This is the precise "PASS-with-note: long-running, deferred" pattern that the new Quality Gate rule #3 forbids ("PASS-with-note: long-running, deferred is not PASS. Mark it DEFERRED and finish later."). The fresh verify run AFTER this PR landed was the one that caught the OBS-2108 regression — the exact reason the rule was tightened. | Already addressed by Quality Gate lockdown (PR #1251) and the corrective PR #1250. No retrospective fix needed; lessons captured. |
| 2 | LOW | Evidence-free PASS-WITH-NOTE claim | `qa_cycle/checkpoint-results/day-90-exit.md:38-39` (E.2 row) | E.2 reports PASS-WITH-NOTE: "Cycle 21 did not run the Phase 68 Epic 500B Playwright visual-baseline suite — visual-regression check is implicitly covered by the frontend-test-suite gate (E.15) but not by per-screenshot diff. No new visual regressions reported." Implicit-coverage-by-another-gate is weak — the wow-moment per-screenshot diff has not actually been run. The "no new visual regressions reported" claim has no artefact backing (other than the absence of a complaint). | Track for future cycle: actually run the visual-baseline suite, or remove E.2 from the exit gate list as not-yet-implemented. |

## (Bookkeeping PRs only) Scenario amendment audit

No scenario file edits in this PR. All work is checkpoint-result + evidence + status.md.

## (Bookkeeping PRs only) Evidence audit

Sampled 5 PASS claims for evidence backing.

| Gate | Claim | Evidence backing | Verdict |
|---|---|---|---|
| OBS-2107 VERIFIED | V118 backfill + live SoA generation triggered email | Flyway log line `Migrating schema "tenant_5039f2d497cf" to version "118" ... Successfully applied 1 migration`; backend log lines with PID + request UUID + tenant + template; Mailpit msg `SpJuVnSwWUzLdyWcy9RbSu` with header MessageID, Subject, To, Created timestamp; Mailpit total went 16 → 17 | Strong — full pipeline trace |
| E.1 (per-step triage) | "Tracker contains 33 OBS rows with explicit triage" | Tracker visible in status.md with concrete OBS-row dispositions | Backed |
| E.5 (field promotion) | 4 dialogs verified, zero "Custom Fields" sections | Per-dialog field counts + named groups + 4 evidence JSON files (`e5-{client,matter,task,feenote}-dialog.json`) | Strong — concrete field counts + JSON dumps |
| E.7 (Keycloak flow) | Real KC realm `docteams`, no mock IDP | `curl -sIL` on `:3000` → `307` to `/oauth2/authorization/keycloak`; `curl -s http://localhost:8180/realms/docteams` returns realm payload; DOM after sign-in has user identity, no mock-jwt strings | Strong — exact curl traces + DOM check |
| E.8 (portal magic-link) | 9× magic-link days + Day 90 expired-token rejection | Cleared localStorage; visited `/auth/exchange?token=…` from Mailpit msg `Kg7nxNYnkEermT2pvLfJMC`; expected rejection with "Login Failed — Link expired or invalid"; 17 Mailpit emails listed | Strong — concrete message ID + reproduction |

Evidence quality is high — Mailpit message IDs, Flyway log lines, curl outputs, DOM checks, file artefacts all present. Only soft spot is E.15 (Finding #1) and E.2 (Finding #2).

## Notes

This PR is the cycle 22 follow-up that flipped E.15 from "PASS-with-note: backend verify in flight" to (apparent) all-green. It is the precursor to the OBS-2108 catch — fresh post-merge `./mvnw verify` revealed the test regression that PR #1250 fixed.

The work in this PR is honest in disclosure: E.15 explicitly says backend verify is in flight, and the summary still claims PASS. This was the seam. The Quality Gate lockdown (PR #1251 hook + marker contract) explicitly addresses this seam by requiring `.claude/markers/verify-backend.json` with `exit:0` to merge a backend PR.

OBS-2107 verification is unambiguously strong: V118 migration logged, backend handler logged, Mailpit message captured. That part is exemplary.

This PR's E.15 should retroactively be classed PASS-WITH-NOTE → MERGED-AWAITING-VERIFY, reconciled by PR #1250 (which actually completed the verify and caught the regression). The status tracker (after PR #1250) reads "Backend verify clean — 5011 / 0F / 0E / 26 skip" — that's the correct state, but it required two PRs and a cycle 23 to get there.

No DISPOSED_BUG amendments. No weakened tests. Just one PASS claim that was outrunning its evidence.
