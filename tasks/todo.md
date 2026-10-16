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
