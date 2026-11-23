# Phase 92 — handover (2026-11-20)

For the next orchestrating agent. Read this, then `tasks/phase92-decisions.md`, then the exit register. Do not
re-derive anything below; verify a fact only before acting on it.

## Closed (2026-11-23)

- **Phase 92 exit: PASS.** The exit gate was re-run on `main` `ef0563fe3`. BE-VERIFY 7 557 / 0 / 0 / 26, harness
  70 / 70, FE 4 633, portal 292, and all five E2E suites green, each on its own fresh stack. 0 open HIGH/MEDIUM in the
  register. Closure report: `tasks/phase92-trust-books-provably-right.md` § C.0. Evidence:
  `qa_cycle/evidence/phase92-exit/closure-2026-11-23/README.md`.
- **All follow-ups are done:** #1823–#1842. Every HIGH/MEDIUM register row is closed, with its PR and merge SHA in
  `qa_cycle/evidence/phase92-exit/defect-register.md`.
- **What remains is founder decisions and backlog follow-ups only.** Nothing here is HIGH or MEDIUM. Sources are in
  `tasks/phase92-decisions.md`:
  - extend the back-dated cover check to PAYMENT, REFUND and TRANSFER_OUT (F-646D-1 D-1);
  - interest reversal writes a fixed reason, unlike every other trust reversal (648B F-1 D-1);
  - auto-match should apply tier 1 (reference) to every line before tiers 2–4 (FU-16 D-2);
  - the wizard pane offers contras as manual-match candidates (F-652B-1; FU-16 D-2);
  - a draft reconciliation can't be abandoned in the UI (FU-6b D-3 (b));
  - `generateDocument` builds the trust data twice, as two snapshots (FU-3 D-1);
  - the FU-5 leftovers: transfer-in reversal and unidentified-allocation reversal still check cover as at today only (FU-5 D-1);
  - FU-16 (c): the statement-only type set on the type-registry wire (F-652H-2, F-652B-1/5);
  - `backend/db/BASELINE.md` next-free numbers (FU-26; 652F D-4);
  - the LOW review follow-ups recorded in the D-rows (CF-3 D-1, FU-6a D-1, FU-6b D-3, FU-8 D-1, FU-12 D-1, 648D D-1,
    F-646E-2 D-1, 646C-O2 D-1, FU-27 D-1, F-11 D-1), plus the still-open LOW FU-7, FU-10, FU-14 and FU-15.

Sections 1 onwards below are the 2026-11-20 handover, kept as history.

## 1. Where things stand

- **Phase 92 (trust books provably right) is COMPLETE**: 36/36 slices merged, PRs #1786–#1822 (652H #1820 was inserted
  mid-phase by 652E's scout). Closure report: `tasks/phase92-trust-books-provably-right.md` § Closure report;
  evidence `qa_cycle/evidence/phase92-exit/`.
- **Exit verdict: FAIL on exit-bar item (3)** — "0 open HIGH or MEDIUM trust defects". Everything else passed
  (BE 7 420, FE 4 591, portal 292, E2E exit-month 16/16 with difference 0,00, lifecycle 105/105, depth-ii 40/4,
  default 168, portal 34; harness 24/24 paths; markers OK on all PRs).
- **All 3 HIGH follow-ups are now FIXED and merged (2026-11-20):**

  | FU | Defect | PR | Merge |
  |---|---|---|---|
  | FU-2 | R-2 trust reports readable without `VIEW_TRUST` | #1823 | c3646bccc |
  | FU-13 (widened) | 649B F1 / F-652E-5 + F-650C-2: R&P and Section 35 dropped reversed pairs | #1824 | c4f66f9f6 |
  | FU-9 (+FU-1, closes FU-11) | F-6 / F-6a cashbook in debit; approval-time cover check + `CASHBOOK_IN_DEBIT` flag | #1825 | 0c01b72b5 |

- **Remaining to reach exit PASS: 15 MEDIUM trust defects** (source of truth:
  `qa_cycle/evidence/phase92-exit/defect-register.md`, rows marked OPEN). Re-count against the register before
  starting — FU-11 is now closed and some MEDIUMs may have been touched by #1825.
  - With a follow-up already specced in `tasks/phase92-decisions.md` (Follow-up PRs table):
    - FU-3 — R-1: statement reads general + invested balances in separate queries (double count); use a separate
      read-only `REQUIRES_NEW` REPEATABLE_READ sub-transaction (reads currently run inside a writing tx — 650A note).
    - FU-4 — W1: firm client-ledger-statement report applies the "reversal reasons off client surfaces" rule.
    - FU-5 — S2: deposit-reversal approval checks client/matter balance as at `reversalDate` AND today.
    - FU-6 — F-646C-1 + F-647C + F-652C-1: refuse duplicate same-end-date statements unless replacing; refuse
      statement lines dated after today.
    - FU-12 — F-650C-1: report `REPORT_GENERATED` / `REPORT_EXPORTED` audit rows never flushed.
    - FU-16 — 652H M-1 / F-652H-2 / HF-7: auto-match pair rules for reversed originals.
  - **Unassigned MEDIUMs (8)** — no FU yet; spec each from the register row before building: CF-3a, CF-3b
    (auto-match reads statement + lines without a lock and overwrites `matched_count` — concurrency), F-646D-1,
    F-646E-2, 648B F-1, 648B F-11, 648D F-4, 648D F-5. Suggested grouping: CF-3a+CF-3b one PR (same lock).
  - Non-blocking follow-ups also in the table: FU-7 (SoA label relabel, pack bump), FU-8 (CI Surefire heap OOM,
    F-17 — Backend CI OOMs intermittently; ruling: retry once and note in PR), FU-10 (DB-side ordering for the
    after-lock boundary), FU-14 (postings before `opened_date`), FU-15 (merge-gate hook `gh pr diff --name-only`
    fails >300 files). FU-17..FU-25 are 652F's **proposals** for the founder, not rulings.

## 2. How to run a follow-up (the lean flow that worked — ~1–3 % weekly usage each)

1. **Scope** with a few greps (≤5 tool calls). Read the register row + the slice's `found.md`.
2. **One builder** agent, `isolation: "worktree"`, brief = defect + rule + design + "red test first, save to
   `qa_cycle/evidence/phase92-followups/FU-n/red-run.txt`" + Tier 2 = the package's whole test tree + every class
   using changed code (`./mvnw spotless:apply` first) + "no full verify, open PR, don't merge". Sonnet builders are
   fine for small/mechanical fixes; **use Opus for locking, money or concurrency**.
3. **Full backend verify yourself** in the builder's worktree, detached, while reviewing:
   `cd <wt>/backend && nohup bash -c './mvnw -q spotless:check > /tmp/x-spotless.log 2>&1; echo $? > /tmp/x-spotless.rc; bash scripts/verify.sh -q > /tmp/x-verify.log 2>&1; echo $? > /tmp/x-verify.rc' >/dev/null 2>&1 &`
   then poll the `.rc` file in bounded loops (≤9 min per Bash call). Count `<testcase>` in
   `target/surefire-reports/TEST-*.xml` and grep for `<failure|<error`. ~35 min. ERROR log lines are routine.
   If the PR touches `frontend/`: run the FE gate AFTER the backend verify (vitest times out under load), node 20,
   `/opt/homebrew/bin/pnpm`, `NODE_OPTIONS=""`: `rm -rf .next && pnpm install --frozen-lockfile && pnpm lint &&
   pnpm typecheck && pnpm format:check && pnpm test && pnpm build`.
4. **Two reviews**: you (read the diff + changed files in context) and an independent
   `feature-dev:code-reviewer` with **`model: "opus"`** (founder: Sonnet reviewers are weak). Give the reviewer the
   code and the intent, not your conclusion; ask for file:line + `## Verdict: APPROVE @<sha>`. After any fix push,
   a **delta review** of `git diff <reviewed>..<head>` (Opus for anything non-trivial).
5. **CodeRabbit: one round.** Fix its first review's actionable findings; never re-trigger it; later rounds fix only
   Critical/security/tenant/data/money, rest → found.md. Rate-limited → `## CodeRabbit: DEFERRED — …`.
6. **Audit trail** appended to the PR body: two `## Verdict: APPROVE @<head-sha>` lines (7+ hex, must prefix the
   current head) + one `## CodeRabbit: REVIEWED|DEFERRED …` line + gates table.
7. **Markers** in the MAIN checkout's working tree before merge (hook reads them): `.claude/markers/verify-backend.json`
   (and `verify-frontend.json` if FE) — `commit` must be an ancestor of the PR head; format in
   `.claude/markers/README.md`.
8. **Merge** as a single plain command (the hook refuses anything compound):
   `gh pr merge <N> --squash --match-head-commit <full 40-char head sha>` — all CI checks must be pass/skipping.
9. **Bookkeeping from a temp worktree** (never commit in the shared main checkout): copy the markers aside,
   `git checkout -- .claude/markers/` in main, `git pull`, `git worktree add --detach ../wt-x origin/main`, put the
   markers back, mark the FU **DONE — PR #N (sha)** in `tasks/phase92-decisions.md`, commit, `git push origin
   HEAD:main`, remove the worktree, `git pull` in main, then `bash scripts/check-verify-markers.sh <N>` must PASS.
   Remove the builder worktree + branches. **Use a quoted heredoc (`<<'EOF'`) for any text containing backticks**
   (an unquoted one executed `CASHBOOK_IN_DEBIT` as a command and garbled a row once).

## 3. Rules that bind (founder rulings — do not re-ask)

- **No founder questions during execution.** Apply a ruling or the documented default (else the most conservative
  safe option), log a `DEFAULT` row in `tasks/phase92-decisions.md`, continue. Hard stop only for real-tenant data
  loss, a security hole, or legal compliance with no safe default. Report to the founder as ONE consolidated list
  with recommendations they can accept in one reply.
- Legal questions → a Fable legal-adviser agent with cited sources first; founder signs off. F-6 opinion:
  `qa_cycle/evidence/phase92/legal/F-6-opinion.md` (signed off; open point: trust auditor / LPC to confirm the
  Rule 54.14.10 reading before the `CASHBOOK_IN_DEBIT` wording is relied on).
- One fix per PR; reproduce-first (red test on `main`) before any production change; full verify before merge;
  never bypass the merge-gate hook.
- Global trust lock order is in `TrustPostingService`'s class Javadoc (650A F-1/F-10/F-11 + FU-9): own rows → item
  (unidentified) → client anchors → account rows. Older notes saying "row → account → anchor" are stale.
- Size bound is dropped; slices/PRs size to the work.

## 4. Environment / gotchas

- Usage: the founder watches weekly Claude usage closely; keep orchestration turns few and delegate detail.
- Backend CI OOM (F-17): `java.lang.OutOfMemoryError: Java heap space` with no failing test → `gh run rerun <id>
  --failed` once, note it in the PR; a second OOM = stop and report (FU-8 becomes the blocker).
- Keep the Mac on AC; for long unattended runs use overlapping `caffeinate -dimsu -t 180` every 60 s (now built
  into `scripts/run-phase.sh`). Something kills lone `caffeinate` processes.
- `run-phase.sh` honours a `<!-- run-order: … -->` line and counts `STRUCK` rows as complete; slice timeout 260 min.
- Process docs updated this phase: `/architecture` (Cross-cutting Decisions X1–X5 + Founder decisions table),
  `/breakdown` (fan-out sizing, ~90 min/slice cost model, run-order line, open-question Status column),
  `/phase_v2` (pre-flight + no founder questions), `/epic_v2` (delta re-reviews, one CodeRabbit round, decision
  protocol).
- Memory notes worth reading: `project_phase92_trust_books_correctness.md`, `feedback_no_founder_questions_mid_phase.md`,
  `feedback_reviewers_use_opus.md`, `feedback_coderabbit_reviews.md`.

## 5. Suggested next step

Run the remaining MEDIUMs in severity × size order with the lean flow: CF-3a+CF-3b (auto-match lock, Opus builder),
FU-5, FU-3, FU-6, FU-4, FU-12, FU-16, then the other unassigned rows. When the register shows 0 open HIGH/MEDIUM,
re-run the 652F exit gate (suites on a fresh stack + register re-count) and flip the closure verdict to PASS.
