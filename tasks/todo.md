# Epic 596 — Remove `crm` (Phase 80) + Pipeline UI (branch phase86/02-remove-crm, worktree ../worktree-epic-596)

- [x] Step 0 validate (no prior 596 work; tag horizontal-platform-final on origin; V136 next-free)
- [x] Step 1 worktree created
- [x] Step 2 scout → .epic-brief.md
- [x] Step 3 builder (PR #1565, c080b48db) → PR at Tier-2 green (backend targeted + frontend lint/build/test/format)
- [x] Step 4.1 two code reviews (A: APPROVE 0C/0H, B: APPROVE 0C/0H) (bug/quality + architecture) + CodeRabbit poll
- [x] Step 4.3/4.4 fixer cycles: 0 (one-line Medium cleanup only)
- [x] Step 4.5 audit trail in PR body
- [x] Step 4.6 full verify: backend GREEN 6009/0/0/26 (marker c080b48db); frontend GREEN (marker d29c39556)
- [x] Step 5 merged 632782c81 (PR #1565, squash); 596/596A/596B/596C marked Done in phase file + TASKS.md

## Review (2026-10-16)
- PR #1565 merged as 632782c81. 132 files: backend 55 deleted / 2 created / 27 modified; frontend 31 deleted / 17 modified. V136 drops deals, pipeline_stages, deal_counters, org_settings.deal_pipeline_pack_status + defensive DEAL/capability row deletes.
- Gates observed: reviews A+B APPROVE (0C/0H), CodeRabbit no actionable findings, local verify.sh 6009/0/0/26, local frontend lint/build/test/format green, Backend CI + Frontend CI pass, markers written.
- Orchestrator calls: deal_pipeline key removed from all 4 profile JSONs; entity_tags/saved_views DEAL cleanup kept in V136.
- Incident: first frontend build hit stale-.next Turbopack next/font/google error; clean rebuild green → lessons.md.
- Follow-ups (non-blocking): 596C.4 proof is module-graph not render; notifications rows of type DEAL_WON not cleaned by V136 (candidate for 605 squash or a later slice).
- Next: /epic_v2 597 (remove proposal + legacy portal + portal proposals; tenant V137, global V26).

# QA scenario coherence after Phase 86 (pre-87 docs PR) — spec `tasks/qa-scenario-amendments-post86.md`

- [x] Audit copied into repo: `qa/testplan/legal-testplan-audit-2026-10-19.md`
- [x] Founder decisions recorded (D1–D5) + ideation log `.claude/ideas/qa-demo-plans-post86-ideation-2026-10-19.md`
- [x] Flows verified against code: Start Onboarding / Mark Complete + Confirm + document picker / Activate Customer; Send for Acceptance → `/accept/<token>` "I Accept"; real portal routes + `/portal/*` endpoints; RAF + estate template task names; billing run selects ACTIVE only
- [x] Worktree + branch `qa/scenario-coherence-post86`
- [x] Apply §3 hunks to `legal-za-90day-keycloak.md` (A deletions, A-trim, B-86, B-pre, new 4.3a–c; C and D untouched)
- [x] Apply §4 hunks to `legal-za-full-lifecycle-keycloak.md` (5.7, Day 7/8, 10.1a–d, 15.x retarget, R70k, 60.11a, 21.5, names)
- [x] MIGRATION-NOTES: 90-day row + "Post-86 coherence amendments" + "Known-stale for Phase 87" lists
- [x] Master doc → legal-only scripts table
- [x] §2.5 verification: grep → only removal annotations remain; wow counts 7/7; §6 chains re-read. Days 0–4 Keycloak smoke **DEFERRED** (dev stack down at build time; `svc.sh status` all stopped)
- [x] PR #1586: per-hunk class table; two code-reviewer passes (REQUEST_CHANGES → fixes → APPROVE ×2) + CodeRabbit 10 comments addressed (spec §8–§8.2)
- [x] Founder acknowledged M2 / M4 / 80.5-revert and merged 2026-10-19 → `eb0100614`; worktree + branch removed
- [ ] Follow-ups: first observed Days 0–4 run of the amended 90-day plan (next QA cycle / Epic 617C); 617B.1 text "12 stale Pipeline refs" → "verify zero"; depth-ii fixme auto-activation assertion is right (lift when stable)

# Merge gate: agents may merge on agent reviews + CI green — DONE 2026-10-19 (PR #1587)

- [x] `.claude/settings.json`: `Bash(gh pr merge:*)` allow (founder) + hook timeout 60s
- [x] Hook: CI green on every PR (`gh pr checks` buckets pass/skipping; empty → deny); single plain `gh pr merge <N> [strategy] [--delete-branch] --match-head-commit <full-sha>` (compound / --admin / --repo / --auto / $(…) refused); review trail for EVERY PR with verdicts bound to the head (`## Verdict: APPROVE @<sha>`); layer markers (packages/ → fe+portal); fail-closed (deny() via jq, ERR trap before stdin, empty diff/head → deny); bash 3.2 safe
- [x] Docs: markers README, skills epic/epic_v2/phase/qa-cycle/qa-cycle-kc (two-step merge, Step 7 produces the trail), template-epic cross-repo note
- [x] Reviews: 2× feature-dev:code-reviewer (9 fix rounds, final `APPROVE @3263245` each) + CodeRabbit (2 heads, all addressed); matrix observed under /bin/bash 3.2 and bash 5
- [x] Merged via the gate itself with the pinned two-step command
- [ ] Residual (accepted): trail is self-attested; identical bound lines count twice. Follow-up idea: require distinct reviewer headings.
