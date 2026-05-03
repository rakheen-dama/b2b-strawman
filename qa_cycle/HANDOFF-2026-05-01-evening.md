# Handover — 2026-05-01 (evening)

Continuation of `qa_cycle/HANDOFF-2026-05-01.md` (morning handoff). The previous orchestrator session ran out of context after working through Tasks D, F-first-iteration, and E. This file captures what shipped, what's pending, and the anti-cheat reminders for the next agent.

**Do not trust this document blindly — verify before acting.**

---

## What shipped in this session (verified on `main`)

3 PRs merged sequentially, in this order:

| PR | Title | Merge SHA | What it does |
|---|---|---|---|
| #1254 | `fix(OBS-2107 follow-up)` | `672bbb0af` | OrgSettings constructor seeds canonical `portal_notification_doc_types` default. Closes the latent bug from PR #1247 where every newly provisioned tenant got `[]` (Hibernate field initializer overrode V117's SQL DEFAULT). |
| #1256 | `docs(audits)` | `0555ab3ae` | Lands the 32 slop-hunt audit files (PRs #1225–#1250) + the OBS-2107 implementation note. The slop-hunt was completed mid-session; this PR persists the artefacts so future agents can reference them. |
| #1255 | `fix(OBS-AUDIT-N1)` | `823646c59` | Wires `portal-proposal-expired.html` email into `ProposalExpiredEventHandler` (extends existing handler, no new `handleInTenantScope` duplicate). V119 also adds `PORTAL_NEW_PROPOSAL` + `PORTAL_PROPOSAL_EXPIRED` to the `chk_email_delivery_reference_type` constraint (schema-code consistency; the OBS-703 path turned out NOT to actually write delivery-log rows in the integration test, so the V119 change is defensive, not closing an active bug — see the spec for the empirical finding). |

**Backend `./mvnw verify` runs clean** post-merge — 5012 tests / 0F / 0E / 26 skip — verified at commit `672bbb0af`.

---

## What's now on main (all PRs merged)

The 4 doc-only PRs that were in flight when this handover was first drafted are all merged. The user authorised the merges after CodeRabbit confirmed all comments were addressed and CI was green. main HEAD is `37a641834`. Full session merge log:

| PR | Merge SHA | What it does |
|---|---|---|
| #1254 | `672bbb0af` | OrgSettings constructor seeds canonical `portal_notification_doc_types` default (real fix for the OBS-2107 latent bug). |
| #1256 | `0555ab3ae` | Persists 32 slop-hunt audit files (PRs #1225–#1250) + OBS-2107 implementation note. |
| #1255 | `823646c59` | Wires `portal-proposal-expired` email + V119 register reference types. |
| #1257 | `27e9eda18` | Known-failures tracker with regression-pack provenance caveat. |
| #1258 | `439c17823` | qa-cycle skill lockdown (E2E sister of qa-cycle-kc). |
| #1260 | `cd9c01a06` | template-epic + fix-tests + regression skill lockdown. |
| #1259 | `37a641834` | epic + phase skill lockdown (last in, includes the SHA-capture-before-cd + exact-brief-command fixes). |

The working tree carries some pre-existing noise — leave alone:
- `.arch-context.md` (auto-regenerated)
- `frontend/test-results/*` (Playwright artefacts from the regression run)
- `portal/next-env.d.ts` (auto-regenerated)
- `tasks/insights/*.stats.json` (telemetry)
- `.claude/markers/verify-backend.json` (local agent artefact, not gitignored but not tracked)

Plus this handover file itself (`qa_cycle/HANDOFF-2026-05-01-evening.md`) is untracked. Decide whether to commit it as a small doc PR or just leave it as a session-end artefact.

---

## Mandate (verbatim, NON-NEGOTIABLE — same as morning)

> No workarounds, fix actual flows and bugs as they are found. Follow the Per-Day Workflow section in qa_cycle/status.md. After Day N walk: triage every gap, fix every spec, PR the bugfix branch into main, address review findings, merge, retest each fix on main with the QA agent, only then advance QA Position. Do not skip the retest.

> All builds must be green. Reviews must be considered. Merge gates and agent contract are tightened. **Claude is on thin ice — needs to improve or be replaced.** Agents should not look for loopholes or ways to be lazy and careless. Pride should be taken. This is not a race, quality is king.

> Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired in. No workarounds besides Mailpit API and dev-only Keycloak issues.

> **No production data.** All data is disposable. Backward data compat is not a priority — break-and-rebuild is acceptable. Backfill migrations are still worth doing when cheap.

> **Regression pack provenance is suspect** (user note 2026-05-01): the pack was built when bugs were live, so test failures may encode buggy old behaviour rather than real regressions. **Don't auto-fix regression failures** until the pack itself has been audited. See `qa_cycle/known-failures-2026-05-01.md`.

If a rule blocks you, raise it; don't bypass.

---

## Backlog (priority order from user, 2026-05-01)

The user explicitly endorsed this order:

1. ✅ **Task D** (slop hunt of PRs #1225–#1250) — done. Audits live at `qa_cycle/audits/slop-hunt-{PR-N,BATCH-X}.md`.
2. ✅ **Task F first iteration** (OBS-AUDIT-N1) — shipped as PR #1255.
3. ✅ **Task E** (lockdown propagation to 6 skills) — shipped as 3 PRs (#1258 / #1259 / #1260, awaiting merge).
4. ⏳ **Task F continuation** (cleanup PRs from slop-hunt findings). Concrete queue:
   - **#1247 follow-up** is DONE (was actually the FIRST Task F item, shipped as #1254).
   - **#1246 root cause**: `GeneratedDocumentService` event ordering. The OBS-2106 PR shipped a workaround that emits a SECOND `DocumentGeneratedEvent` to paper over an event-ordering bug. Real fix is to publish the event AFTER the visibility flip commits. Medium effort.
   - **OBS-704 v3**: replace the mount-gate hydration workaround with a deterministic-ID fix. Pin Radix to a version that uses `useId()` for `aria-controls`, OR pass an explicit `id` prop on the trigger. **Do NOT use `suppressHydrationWarning`** (CodeRabbit caught this in #1256 — that's itself a cover-up). Larger frontend effort.
   - **OBS-AUDIT-N1**: DONE in #1255.
   - **Audit-03 sweep**: 4 frontend files still carry `*Trigger asChild` siblings that haven't had the dialog-owns-button pattern applied: `customer-rates-tab.tsx`, `project-rates-tab.tsx`, `expense-list.tsx`, `comment-item.tsx`. Per-file 5-min eyeball each; bug class is dormant, not eliminated.
   - **#1228 guardrail extension**: walk roots `hooks/`, `middleware.ts`, `e2e/` (currently only walks `app/components/lib`). ~10 min frontend.
   - **#1238 NULL currency**: decide whether to backfill + NOT NULL or accept the workaround. Architectural decision — defer to user.
   - **TenantScopedRunner extraction**: 8+ duplicates of `handleInTenantScope` across notification handlers. Big consolidation PR + ADR + ArchUnit rule. Defer to a dedicated session.
5. ⏳ **Task G** (CI parity workflow): `.github/workflows/quality-gate.yml` enforcing the same gates as the local merge-gate hook so the rule can't be bypassed via the GitHub UI. ~1–2 hours including branch-protection. Also worth folding in: fix the `scripts/run-regression-test.sh` exit-code masking bug (caller's `| tail -N` swallows non-zero exit). Tracked in `qa_cycle/known-failures-2026-05-01.md`.
6. ⏳ **Task H** (bug-class tracker): `qa_cycle/bug-classes.md` documenting the 5 recurring classes seen this cycle (notification-pipeline gaps, schema-data drift, Radix asChild collisions, SQL Cartesian aggregates, test-scope drift). ~30 min.
7. ⏳ **Task: Regression pack audit** — gated on user direction. The current pack pre-dates the cycle-22 fixes, so failures may be assertions encoding old buggy behaviour. Until audited, don't blindly fix regression failures.

**Phase 69 (Firm Audit View)** stays paused until Task F-continuation + G are sufficiently complete (per the user's explicit "Phase 69 stays paused until D + E + F are done").

---

## Anti-cheat reminders (specific to my session's gotchas)

These are lessons I learned the hard way during this session — pay attention:

- **Audit hypotheses are NOT findings until an integration test confirms them.** I bundled a "fix" for a presumed CHECK-constraint violation in PR #1233's email-delivery log, and my own integration test contradicted the hypothesis (the path doesn't fire at all in tests). I had to retroactively soften the OBS-AUDIT-N1 spec from "fixes latent bug" to "schema-code consistency". Memory entry: `feedback_audit_hypothesis_needs_integration_test.md`. **Apply this rule when bundling.**
- **Marker file path matters when worktrees are involved.** `gh pr merge` reads `.claude/markers/` relative to its cwd. Builder runs in a worktree; merge runs in main repo. Marker must be written to **main repo's** `.claude/markers/` — `cd` there explicitly. CodeRabbit caught this on PR #1259; my fix-ups now use absolute `cd` paths.
- **The post-merge regression hook fires on every merge.** It runs `bash scripts/run-regression-test.sh`. The script's exit code is masked by the hook's `| tail -N` pipe — it always reports exit 0 even when the script reports FAIL. Until that's fixed (Task G), trust the bordered SUMMARY block in the script's stdout, not the apparent exit code.
- **Quality Gate rule #6 (scenario amendment authorization)** is not theoretical. Slop-hunt batch E surfaced 9 OBS items disposed via scenario amendments in #1244 — all individually justified, but the cumulative pattern is worth watching. If you find yourself reaching for "amend the scenario" instead of "fix the bug", stop and check rule #6.
- **Don't trust CodeRabbit walkthroughs alone.** CodeRabbit's "X actionable comments posted" lives in the issue-comments stream; the per-line details live in the inline-comments stream. Use both `gh pr view --comments` and `gh api .../pulls/{N}/comments` to see everything. I missed comments on a first pass and had to re-check.
- **Don't trust gh pr merge's apparent success.** The `gh pr merge` command pipes through and the runtime captures the pipe's exit, not the hook's exit. Always `git fetch origin main && git log origin/main` to verify the merge SHA actually landed.
- **Subagent rate limits matter.** CodeRabbit was at 4/5 reviews remaining when I opened PR #1258; by the end of the session I'd opened 4 PRs through it. Plan accordingly.
- **The `superpowers:code-reviewer` subagent works well for slop-hunt parallel review.** Dispatched 5 in parallel for the 26-PR slop hunt in batches of ~5 PRs each; took ~5–10 min, returned cohesive batch summaries. Reuse that pattern.
- **Memory file `feedback_audit_hypothesis_needs_integration_test.md` was added this session.** Read it before any future bundling decision.
- **The user said "Claude is on thin ice."** Don't be cute, don't bypass, don't over-claim PASS. If the verify command's output is ambiguous, re-run; don't infer green from the absence of red.

---

## Key files index (additions to the morning handoff)

| Path | Purpose |
|---|---|
| `qa_cycle/known-failures-2026-05-01.md` | Tracker for the 4 pre-existing Playwright regression failures, with the user's regression-pack-provenance caveat |
| `qa_cycle/fix-specs/OBS-2107-followup.md` + `.implementation-note.md` | OrgSettings entity-init fix (PR #1254) |
| `qa_cycle/fix-specs/OBS-AUDIT-N1.md` | Portal-proposal-expired email + V119 (PR #1255) |
| `qa_cycle/audits/slop-hunt-{BATCH-A..E,PR-1225..1250}.md` | 31 slop-hunt audit outputs (5 batch summaries + 26 per-PR audits) |
| `~/.claude/projects/-Users-rakheendama-Projects-2026-b2b-strawman/memory/feedback_audit_hypothesis_needs_integration_test.md` | New memory entry — verify hypotheses with integration tests before bundling |

---

## How to actually start

1. Read `CLAUDE.md` (top "Quality Gates" section).
2. Read `qa_cycle/HANDOFF-2026-05-01.md` (morning) AND this file.
3. Run `bash compose/scripts/svc.sh status` — confirm the stack is up.
4. Run `git log --oneline -5` — confirm `823646c59` is the most recent merge or that the open PRs have landed.
5. Check `gh pr list --state open` — see if the 4 doc PRs are still open or merged.
6. Run `cd backend && ./mvnw verify` — confirm main is still green (baseline 5012 tests / 0F / 0E / 26 skip).
7. Ask the user which Task F item to start, OR which open PR to merge first. Don't pick on your own.
