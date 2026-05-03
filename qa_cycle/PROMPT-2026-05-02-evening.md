Read `qa_cycle/HANDOFF-2026-05-02-evening.md` in full before doing anything else. That file is your brief — it captures what shipped today (PR #1265 merged at `cc911f1e0`, ADR-T008, ArchUnit 1.4.2 upgrade), the user mandate, the pending backlog, the open API-shape design decision for PR #2, and the anti-cheat reminders specific to gotchas hit during PR #1.

Also read `adr/ADR-T008-tenant-scoped-runner.md` and the inline comments in `RetainerPortalSyncService.backfillForTenant` + `TrustLedgerPortalSyncService.backfillForTenant` (search for `// NB: this is a 3-binding pattern`) — these capture the design constraints PR #2 inherits from PR #1.

Skim `documentation/tech-debt.md` TD-008 (resolved by the ArchUnit upgrade) and TD-009 (3 controller violations surfaced by it).

After you've read those, run these in this order:

1. `bash compose/scripts/svc.sh status` — confirm stack is up
2. `git log --oneline -5` — main should be at `cc911f1e0` (PR #1265 squash)
3. `cd backend && ./mvnw verify` — confirm baseline 5036 / 0F / 0E / 26 skip
4. `git grep -lP "ScopedValue\.where\(RequestScopes\.TENANT_ID" backend/src/main/java | grep -v multitenancy | sort -u` — should list ~15 sites (13 jobs + 2 backfill helpers); these are PR #2's migration targets

Then tell me what you found and which item to start. The remaining backlog (priority order):

1. **PR #2 — TenantScopedRunner finale** (the big one). Migrate 13 scheduled jobs to a new `TenantScopedRunner.forEachTenant` Spring bean, migrate the 2 backfill helpers to a new `runForTenantAsSystemActor`-style API, ship the companion ArchUnit rule banning direct `ScopedValue.where(TENANT_ID, ...)` outside `..multitenancy..`. **The API shape for the 3-binding case (backfill helpers) is the open design decision — three options A/B/C documented in the handover. Brainstorm with me before writing code.**

2. **#4 — #1228 brand walker extension** (~10 min, frontend).

3. **#7 — Codify "dialog owns button" in `frontend/CLAUDE.md`** (~30 min).

4. **#5 — Task G CI parity workflow** (~1-2 hr).

5. **#9 — ESLint custom rule for `<*Trigger asChild>` adjacency** (~2 hr).

6. **#10 — SSR snapshot harness for the dialog family.**

7. **TD-009 — 3-controller cleanup** (opportunistic, on next touch).

Gated/deferred to me: regression pack audit, #1238 NULL currency.

Phase 69 (Firm Audit View) stays paused until #4, #5, #7 are sufficiently complete.

Do not pick autonomously. Do not start work without my sign-off.

Mandate reminders (full text in handover):

- Quality is king. Not a race.
- No workarounds. Fix actual flows and bugs as found.
- Regression pack provenance is suspect — don't auto-fix regression failures until the pack itself has been audited.
- Full `./mvnw verify` is the merge bar.
- Pre-merge gate hook will block you if you try to skip — don't bypass.
- UI questions go to `vercel:nextjs` / `vercel:react-best-practices` / `vercel:shadcn` skills (or spawn a frontend-specialist agent) — don't reason from first principles.
- Don't trust `gh pr merge`'s apparent success. Always `git fetch origin main && git log origin/main` to verify the merge SHA actually landed.
- **Autonomy granted through merge prep** (commit, complete tasks, create PRs, address review comments). **Merge requires my explicit "merge" approval.**
- Verify ArchUnit rule actually fires on injected violation before trusting it (PR #1's `TenantScopeBindingTest` has the pattern — inject a banned method, run the rule, confirm fail, revert).
