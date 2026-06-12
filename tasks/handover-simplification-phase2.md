# Handover: Simplification Roadmap — Phase 2

**From**: orchestrator session 2026-06-10 → 2026-06-12 (22 PRs, #1403–#1431, all merged to main)
**Plan of record**: `~/.claude/plans/tranquil-fluttering-patterson.md` (approved). **Live tracker**: `tasks/todo.md`. **Durable context**: memory files `project_simplification_roadmap_2026-06.md`, `feedback_debt_register_entries_are_hypotheses.md`. **Hard-won process rules**: `tasks/lessons.md` (read the three entries dated 2026-06-11/12 before dispatching anything).

## State of the repo (verified at handover)
- main is green; last merge #1431. All agent worktrees removed; no stray branches from this effort.
- pnpm workspace: `docs`, `portal`, `frontend`, `packages/*` (`@b2mash/ui`, `@b2mash/shared`), one root lockfile, `packageManager: pnpm@10.26.1`. Root `.dockerignore` is a deny-by-default ALLOW-LIST — any new package/file a Docker build needs must be `!`-allowed explicitly.
- ArchUnit `LayerDependencyRulesTest` enforces no-repo-injection with ONE permanent exemption (MockPaymentController, justified in-code). `PortalReadModelOrgScopingGuardTest` enforces tenant scoping over `customerbackend/repository` (two discriminators: `org_id`, `customer_id`; 18 justified exemptions).
- Scheduling is OFF in tests via `kazi.scheduling.enabled` (SchedulingConfig); opt back in per test class only when genuinely needed.
- deploy-staging has failed on AWS credential loading since 2026-05-30 — **pre-existing, explicitly out of scope per user**; do not chase it.

## Remaining work items (priority order)

### P1 — `defaultExpenseMarkupPercent` backend write path (real product gap)
The staff settings form collects a markup percentage that CANNOT be persisted: `OrgSettings.setDefaultExpenseMarkupPercent` has zero callers and no endpoint exists. The field IS read in `ExpenseService:385`, `InvoiceCreationService:925`, `UnbilledTimeService:327` and exposed in `SettingsResponse`. Work: new `PATCH /api/settings/expense` (or fold into an existing settings PATCH — read `OrgSettingsController`'s endpoint family and match conventions), DTO in `settings/dto/`, service method, `@RequiresCapability` consistent with siblings, integration tests, then rewire the frontend form (`time-tracking-settings-form.tsx` still renders the dead input; `updateTimeTrackingSettings` in `app/(app)/org/[slug]/settings/time-tracking/actions.ts` was rewired for reminders in #1431 — markup needs the same treatment against the new endpoint). Reproduce-before-fix: failing integration test (markup PATCH persists + is returned) and a failing frontend wiring test.

### P2 — Wave 3.3: OrgSettings entity reorganization (approved in plan)
`backend/.../settings/OrgSettings.java` (~1,060 LOC, 60+ fields) → per-module `@Embeddable` groups (BrandingSettings, BillingSettings, TimeReminderSettings, CompliancePackStatus…) using `@AttributeOverride` so there is **ZERO schema change** (same table/columns — assert generated schema unchanged; Hibernate ddl validation runs against real schemas). Callers move from `settings.getX()` to `settings.getBilling().getX()` — wide but mechanical. Plan note: judge PR granularity at implementation time (one PR per embeddable group vs one reviewed PR); flag the choice to the user before opening if it's one big PR. Explicitly NOT: separate tables/FKs (declined as gold-plating). `OrgSettingsService` (~1,113 LOC) restructure is NOT in scope.

### P3 — Wave 4.1: `packages/api-types` (recommended, user not yet committed — confirm before building)
Types-only OpenAPI generation: springdoc already exposes the spec; generate via `openapi-typescript` into `packages/api-types`; hand-written fetch wrappers in `frontend/lib/api/` import generated types (do NOT replace the fetch wrappers — full client codegen was explicitly declined). Add a CI step that regenerates and fails on drift. Prereq questions to settle first: where the spec is served from in CI (boot the backend? commit the spec?).

### P4 — Opportunistic / parked (do only when touched anyway, or on user request)
- 3.2b PortalReadModelRepository split — DEFERRED with rationale (guard test already kills the leak class). Revisit only when next adding portal queries.
- Remaining thin-controller violators (PortalAuthController, ReportingController, DataRequestController, RetainerAgreementController) — beyond the enforceable rule; clean when next touched.
- 4.2 docs/ deployable keep-vs-static-export — USER DECISION, do not assume.

## How to run this (proven working process)
1. **Orchestrator + one implementation subagent at a time** (Opus, `isolation: "worktree"`), sequential merges, one fix per PR. Use wait windows (CI ~30 min for backend) to implement the NEXT item, but hold its PR until the current one merges; expect rebase conflicts in shared doc files (CLAUDE.md violator list, tech-debt.md) — resolve by combining both sides' removals.
2. **Subagent prompts MUST include**: read backend/CLAUDE.md (or frontend/CLAUDE.md) first; reproduce-before-fix with observed failing output; full `./mvnw verify` (backend) or `pnpm lint+test+build --filter <app>` (frontend) as BLOCKING FOREGROUND — and the explicit instruction that if the harness auto-backgrounds it, POLL THE OUTPUT FILE INLINE and never end the turn waiting on a monitor (five agents died that way; their verifies get SIGTERMed — surefire exit 143 + "VM crash" on a random test = killed run, not a real failure). One local verify at a time (GreenMail port 13025). Push branch, NO PR (orchestrator opens it).
3. **Review gate protocol**: open PR → wait for CI + CodeRabbit → fetch top-level review comments in a SEPARATE step from the merge (never chain `merge` after a comment-count query — that exact mistake let a Major through once). Address every comment: apply if right (most were), decline with file:line evidence if it conflicts with codebase convention (e.g. field `@Autowired` is the test convention, 498 files; filter-chain 401s have no ProblemDetail body). CodeRabbit's "pass" check status ≠ no comments — gate on the comments API.
4. **Treat every register/tech-debt/audit claim as a hypothesis** — seven were wrong this cycle; two would have broken prod. Verify the mechanism (who reads the field, who calls the setter) before applying any prescribed fix. Premise disproven → the deliverable is the register correction + a pinning test.
5. **Lockfile changes**: never let pnpm re-resolve (caret floats broke builds three times). Graft exact resolutions from the existing lockfile, then validate with `--frozen-lockfile` ("resolution step is skipped" = success); diff `version:` lines to prove zero drift.
6. **Machine quirks**: pnpm at `/opt/homebrew/bin/pnpm`; `SHELL=/bin/bash` prefix for docker (zoxide breaks cd); `NODE_OPTIONS=""` for Next builds; zombie-maven check before backend verify; `gh pr checks --watch` dies on network resets — use `until`-loop polls.

## Merge-gate non-negotiables (from repo CLAUDE.md — read it)
Full verify observed (not inferred) per backend PR; frontend bar is lint+test+build; CodeRabbit comments addressed before merge; the pre-pr-merge-gate hook is enforcement, not advice; PASS means you ran it; one fix per PR; scenario/product decisions go to the user.
