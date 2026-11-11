# G89 fix initiative — handoff (2026-11-08, ~03:40 SAST)

Resume from here. First read `TRACKER.md` (decisions D1–D22, batch table, log) and the "G89 fix initiative" section of `tasks/lessons.md`.

## State

**Merged and verified on KC:** B #1745, A #1746, D #1747, C1 #1748, C2 #1749, H #1750, G #1751.

**Exit bar:**
- Every HIGH is closed. F56 and F58 are VERIFIED-BY-TEST (D22).
- Two MEDIUMs are left, **F17 and F50**. Both are in Batch E: built, not yet merged.

**Batch E (in progress):**
- Builder worktree: `.claude/worktrees/agent-a8c780f7d1e0f9b9a`, branch `fix/g89-e-layout-copy`.
- About 49 commits, each red test before its fix.
- Covers:
  - G89: F17, F50, F5, F7, F8, F9, F15, F20, F40, F41, F44, F47, F48, F49, F57, F62, F64, F65
  - carried items: G-D10a, G88-F2/F24/F27/M9, 634A
  - the #1746 follow-ups
- Pack bumps: legal-za v13→v14 (F57) and trust-reports v2→v3 (F48).
- **No PR existed when this handoff was written.** If the builder died:
  1. `cd` into the worktree.
  2. Check `git status`, and commit any work in progress with a clear message.
  3. `git push -u origin fix/g89-e-layout-copy`.
  4. Open the PR. The body lists the gaps and their red/fix commits, plus a "Wording for sign-off" section for F57: "Fees billed in period" / "Disbursements billed in period".

**Next free migration numbers:** tenant V15 (confirm with `ls`), global V6.

## Remaining steps (in order)

1. **Batch E gates** (the same gates as every PR):
   - Full `caffeinate -is ./mvnw verify` in the worktree. Do not use verify.sh while the KC stack is up.
   - FE gate, plus the portal gate if portal was touched.
   - Two bound `feature-dev:code-reviewer` reviews.
   - Read CodeRabbit's inline comments BEFORE writing the `## CodeRabbit:` line.
   - Pre-merge e2e on fresh stacks, all suites. Recipe: `qa_cycle/evidence/g89-fixes/pr1750-e2e/README.md`. Layout changes shift screenshots, so capture before scoring.
   - Founder signs off the F57 wording.
2. **Merge E** with the plain single-line merge command, `--squash --match-head-commit <full sha>`. Never put that command text inside a heredoc: the merge-gate hook text-matches it.
3. **Restart KC** with `svc.sh` and run the KC check:
   - layout at 1440 and 1024
   - F17: the reconciliation list loads
   - F50: Section 35 lists several runs
   - F44: portal feed
   - F64: no 500
4. **Batch F**, following `batch-F-brief.md`:
   - self-hosted fonts (F46)
   - opt-in visual compares
   - embedded-PG working directory
   - F66: e2e uses the SA date
   - the §6 scenario amendments
   - file F28–F67 in `gaps.md`

   Same gates as above.
5. **Exit re-score:**
   - Run the suites on fresh stacks.
   - Re-walk on a fresh KC tenant: the 8 FAIL rows plus the side rows (see the plan's Verification section).
   - Also observe the deferred items:
     - 632C: receipt filename contains `-r1-` (needs a new payment)
     - 634E(1): a thread with more than 50 comments (seed it through the UI with Playwright)
   - Target: 0 HIGH and 0 MEDIUM open.
   - Then update `gaps.md`, the defects doc and the memory closure note.

## Gotchas (all recorded in lessons.md)

- `verify.sh` preflight kills the KC launchers. Use `caffeinate -is ./mvnw verify`, after checking for stale surefire processes and that port :13025 is free.
- A `timezone_abbreviations "Default"` failure means the macOS dirhelper deleted the embedded-PG extraction. Run `rm -rf $TMPDIR/embedded-pg/PG-*` and re-run.
- While e2e stacks run, stop the KC app services to free memory: `svc.sh stop frontend portal gateway backend`.
- e2e runs between 00:00 and 02:00 SAST hit date-window failures (F66). Classify every failure against main.
- Google Fonts build flake (F46): clear `.next` and retry.
- CodeRabbit hourly limit: record REVIEWED on the head it actually reviewed and DEFERRED on the final head, with the bound re-reviews as cover.
- Bookkeeping commits go from the detached worktree `../b2b-strawman-g89-docs` with `git push origin HEAD:main`. Never commit in the main checkout.

## UPDATE 2026-11-08 ~04:45: Batch E PR is open

**PR #1752**, head `da212c11265ea2e9b452e3790796cf95b148ac1c`. Worktree and branch are unchanged. All gaps are fixed and committed red-first, including F17 and F50 (both MEDIUM). 2573 targeted backend tests pass, and the FE gate is green (3742 tests).

Still to do before merge:
- **Full verify.** Not run yet.
- **Two bound reviews.**
- **CodeRabbit.** Read its inline comments first.
- **Pre-merge e2e.** Many screenshot frames shift; the builder report lists them (Section 35, interest posted date, SoA, audit-log, disbursement Billed, KPI grid, 1024 tables, Team).
- **Founder sign-off on the F57 wording.** In the Summary lines, "Total fees: " becomes "Fees billed in period: " and "Total disbursements: " becomes "Disbursements billed in period: ".
- **KC check** at 1440 and 1024.

Deferred and needing a decision:
- three 634A items:
  - Slug read-only on edit;
  - the card menu reopens after a dialog closes;
  - 212 px cards at 1024;
- interest-posted notification raw amounts;
- sentence-casing about 20 audit labels.

The builder judgment calls are in the PR body.

## UPDATE 2026-11-10: Batch E still in flight; Batch F being started by a separate agent

**Batch E (PR #1752) is not merged.**
- Full verify @da212c112: 6753 / 0 / 0 / 26, BUILD SUCCESS. CI green.
- Founder **D23**: F57 wording signed off.
- CodeRabbit raised 12 actionable comments. A builder is fixing them in the worktree `.claude/worktrees/agent-a8c780f7d1e0f9b9a` (branch `fix/g89-e-layout-copy`). Its findings file is in the old session scratchpad.
- **Still owed after that round:**
  - full verify on the new head;
  - 2 bound reviews, and a re-read of CodeRabbit;
  - pre-merge e2e (all suites, fresh stacks, outside 00:00–02:00 SAST);
  - merge;
  - KC check (1440/1024, F17 list loads, F50 multiple runs, F44 portal feed, F64 no 500).

**Batch F runs in parallel at the founder's request (2026-11-10).** This is an exception to "no parallel development". For the F agent:
- Cut `fix/g89-f-tooling-docs` from fresh `origin/main`, and work in its own worktree.
- **Do not touch files Batch E changes.** List them with `git diff --stat origin/main...origin/fix/g89-e-layout-copy`.
- Likely overlap: frontend layout and components, `report-results.tsx`, the trust-report pack, and the legal-za pack (E bumps legal-za to v14 and trust-reports to v3).
- F must not bump packs.
- If F needs a tenant migration, take V15 only after confirming E added none. E added none at `da212c112`.
- The F e2e gate and the E e2e gate must **not run at the same time**: one e2e stack fits on this machine, alongside a Maven verify at most. Coordinate through `TRACKER.md` before starting a stack.
- Whichever merges second rebases on main and re-runs the affected suites.
- F's opt-in visual compares change the e2e gate recipe. If F merges first, E's e2e gate runs without capture-then-score; record that.

The brief is still `tasks/g89-fixes/batch-F-brief.md`. Tenant V15 / global V6 are next free (confirm with `ls`).

## Note for Batch F (2026-11-11): exact G89-F66 locations

These specs compute "today" as UTC via `new Date().toISOString().split("T")[0]`. Replace each with the SA helper `isoDate()` from `e2e/helpers/legal-depth-ii.ts:98`, which builds on `todayInSA()`:
- `frontend/e2e/tests/projects/project-time.spec.ts:172`
- `frontend/e2e/tests/projects/project-time.spec.ts:211`
- `frontend/e2e/tests/projects/project-crud.spec.ts:250`
- `frontend/e2e/tests/projects/project-budget.spec.ts:123`

The product is SA-time throughout (KaziTime). Founder direction, 2026-11-11: gates do not wait out 00:00–02:00 SAST. Any failure in that window is classified against G89-F66.

## UPDATE 2026-11-11: Batch E merged and verified, Batch F next (no longer parallel)

**Batch E is done.** PR #1752 merged as `6e510dc23` and was VERIFIED on KC. Every HIGH and MEDIUM is now closed.

**Batch F starts from current `origin/main`**, which includes E. The collision rules in the update above no longer apply.
- F must not bump the legal-za pack (now v14) or the trust-reports pack (now v3).
- Next free migrations: tenant V15, global V6.

**Add to the F register (gaps.md §7):**
- **G89-F68 (LOW):** fee-note lines for same-date time entries have no deterministic sort order.
- **G89-F69 (LOW):** amounts in the Section 35 PDF wrap mid-number. The fix needs a trust-reports v3→v4 bump, so ship it as a separate small follow-up PR. It is not in F.
- **G89-F70 (LOW):** raw labels:
  - "REPORT EXPORTED" in the audit trail;
  - `comment.created` in the portal Your-actions feed;
  - `edited a comment on entity "entity"` in Activity;
  - "ORGANIZATION" in the templates table.

**Gates for every remaining PR:**
- Do not wait out the 00:00–02:00 SAST window.
- A failure in that window is a class (c) result under G89-F66 only if the spec uses a UTC date.
- G89-F67 (flaky fee-note screenshot frame) fails on main too, so compare against main before blaming the PR.
