# Handover — 2026-05-02 evening (post PR #1265 merge)

Continuation of `HANDOFF-2026-05-02.md`. The 2026-05-02 daytime session shipped backlog item #8 PR #1 (TenantScopedRunner consolidation for handlers). This file captures what's still pending, the design decisions PR #2 needs to make, and the anti-cheat reminders specific to gotchas hit during PR #1.

**Do not trust this document blindly — verify before acting.**

---

## What shipped this session (verified on `main` at `cc911f1e0`)

- **PR #1265 merged** — `feat(multitenancy): consolidate 14 tenant-scope helpers into RequestScopes.runForTenant (backlog #8 / PR #1)`. Squash SHA on main: `cc911f1e0`.

| Artifact | Path |
|---|---|
| Static API | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — new `runForTenant(String, @Nullable String, Runnable)` and `callForTenant(String, @Nullable String, Callable<T>)` methods |
| ArchUnit regression guard | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingTest.java` — bans `handleInTenantScope` / `runInTenantScope` / `executeInTenantScope` outside `..multitenancy..` |
| Null-tenant resilience test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/NullTenantListenerResilienceTest.java` — proves fail-fast + AFTER_COMMIT non-poisoning |
| ADR | `adr/ADR-T008-tenant-scoped-runner.md` |
| Design spec | `docs/superpowers/specs/2026-05-02-tenant-scoped-runner-design.md` |
| Implementation plan | `docs/superpowers/plans/2026-05-02-tenant-scoped-runner-handlers.md` |

**ArchUnit upgrade:** `archunit-junit5` 1.3.0 → **1.4.2** in `backend/pom.xml`. Fixed TD-008 (silent vacuous passes on JDK 25). Surfaced 3 pre-existing controller violations now tracked as TD-009.

**Test count baseline on main:** `5036 / 0F / 0E / 26 skip` post-merge. Verified at commit `cc911f1e0` via `./mvnw verify` in 13:00.

---

## Mandate (verbatim, NON-NEGOTIABLE — same as morning)

> No workarounds, fix actual flows and bugs as they are found. Follow the Per-Day Workflow section in qa_cycle/status.md. After Day N walk: triage every gap, fix every spec, PR the bugfix branch into main, address review findings, merge, retest each fix on main with the QA agent, only then advance QA Position. Do not skip the retest.

> All builds must be green. Reviews must be considered. Merge gates and agent contract are tightened. **Claude is on thin ice — needs to improve or be replaced.** Agents should not look for loopholes or ways to be lazy and careless. Pride should be taken. This is not a race, quality is king.

> Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired in. No workarounds besides Mailpit API and dev-only Keycloak issues.

> **No production data.** All data is disposable. Backward data compat is not a priority — break-and-rebuild is acceptable. Backfill migrations are still worth doing when cheap.

> **Regression pack provenance is suspect** (user note 2026-05-01): the pack was built when bugs were live, so test failures may encode buggy old behaviour rather than real regressions. **Don't auto-fix regression failures** until the pack itself has been audited. See `qa_cycle/known-failures-2026-05-01.md`.

> **UI questions go to Vercel/Next.js expert skills or agents** (user note 2026-05-01 evening). Memory `feedback_ui_questions_use_vercel_experts.md` lists which skill maps to which question. Don't reason from first principles when an expert-mode skill exists.

> **User has granted autonomy through merge prep, with merge requiring explicit user approval.** Specifically: commit, complete tasks, create PRs, address review comments. Stop at merge gate unless user says "merge".

If a rule blocks you, raise it; don't bypass.

---

## What's pending (priority order)

### 1. Backlog #8 PR #2 — TenantScopedRunner finale (HIGH PRIORITY)

This is the natural follow-up to PR #1. PR #1 consolidated AFTER_COMMIT handler binding. PR #2 finishes the consolidation by handling the *other* code paths in the codebase that still bind tenant scope directly via `ScopedValue.where(RequestScopes.TENANT_ID, ...)`.

**Three deliverables:**

#### 1a. `TenantScopedRunner` Spring bean for scheduled jobs

Migrate 13 scheduled jobs from inline `for (mapping : repo.findAll()) { ScopedValue.where(...).run(...) }` loops to a canonical bean. Sites:

```
backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleExecutor.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeReminderScheduler.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessor.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceExpiryProcessor.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestReminderScheduler.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateReminderJob.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/field/FieldDateScannerJob.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/subscription/SubscriptionExpiryJob.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/DormancyScheduledJob.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupService.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java
backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java
```

(Verify the list via `git grep -lP "ScopedValue\.where\(RequestScopes\.TENANT_ID" backend/src/main/java | grep -v multitenancy | sort -u` before starting — the count was 13 at PR #1 review time but main may have advanced.)

Bean shape (per `adr/ADR-T008-tenant-scoped-runner.md` "Surface 2"):

```java
@Component
public class TenantScopedRunner {
  private final OrgSchemaMappingRepository mappingRepository;
  public TenantScopedRunner(OrgSchemaMappingRepository mappingRepository) { ... }

  /**
   * Iterate every active tenant schema and run {@code action} once per tenant
   * with TENANT_ID + ORG_ID bound. Continues on per-tenant failure; logs at
   * ERROR with tenantId/orgId in MDC. Returns count of successful invocations.
   */
  public int forEachTenant(BiConsumer<String, String> action);
}
```

Internally calls `RequestScopes.runForTenant(tenantId, orgId, () -> action.accept(tenantId, orgId))` per tenant.

#### 1b. `runForTenantAsSystemActor` (or equivalent) for the 2 backfill helpers

The two `backfillForTenant` methods need a 3-binding pattern (TENANT_ID + ORG_ID + MEMBER_ID = SYSTEM_ACTOR_ID) that PR #1's `runForTenant` does not support:

```
backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/RetainerPortalSyncService.java:backfillForTenant
backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java:backfillForTenant
```

Both files have an inline comment at the `ScopedValue.where(...)` site referencing PR #2 — go read those for context.

**THIS IS THE OPEN DESIGN DECISION FOR PR #2.** Three plausible API shapes, no decision yet — brainstorm with the user before coding:

| Option | Shape | Pros | Cons |
|---|---|---|---|
| A | `RequestScopes.runForTenantAsSystemActor(String tenantId, String orgId, UUID actorId, Runnable action)` | Smallest delta on PR #1's API. Mirrors `runForTenant` shape. | Adds a third static method; explicit "system actor" semantics in name might be too narrow if there's ever a non-system-actor 3-binding case. |
| B | `RequestScopes.runForTenant(String tenantId, String orgId, Map<ScopedValue<?>, Object> additionalBindings, Runnable action)` overload | General — supports any future ScopedValue binding without API churn. | Type-unsafe map; runtime cast required on values; weaker contract documentation. |
| C | Capture-and-rebind utility per ADR-204's deferred `RequestScopes.withCurrentScopes()` proposal | Aligns with ADR-204's longer-term design. Backfill methods would capture a snapshot from a synthetic system context. | More machinery; needs a way to construct a "synthetic" system context outside a request. Architectural overlap with ADR-204 — may want to ship them together. |

Recommendation: lead with A (smallest delta, mirrors PR #1's shape). Reserve C for if a third 3-binding pattern emerges. But brainstorm first.

#### 1c. Companion ArchUnit rule banning direct `ScopedValue.where(TENANT_ID, ...)` outside `..multitenancy..`

This is the rule that couldn't ship in PR #1 because all 15+ direct-binding sites would have build-broken the moment PR #1 landed. It must ship with PR #2 *only after* the last direct-binding site is migrated.

ArchUnit 1.4.2 (already in `pom.xml`) supports this pattern. Add to `TenantScopeBindingTest.java`:

```java
@ArchTest
static final ArchRule no_direct_tenant_scope_binding_outside_multitenancy =
    noClasses()
        .that().resideOutsideOfPackage("..multitenancy..")
        .should()
        .callMethodWhere(...)  // ScopedValue.where with first arg = RequestScopes.TENANT_ID
        .because("Bind tenant scope via RequestScopes.runForTenant / callForTenant or "
            + "TenantScopedRunner.forEachTenant. See ADR-T008.")
        .allowEmptyShould(true);
```

The ArchUnit DSL for "method call where the first argument is field X" is fiddly — see ArchUnit's `JavaCall` / `accessOf` API. Test by injecting a violation and confirming the rule fires (same pattern PR #1 used).

#### 1d. Update ADR-T008 with PR #2 realisation

Once PR #2 ships, amend `adr/ADR-T008-tenant-scoped-runner.md`:
- "Surface 2 (PR #2)" — replace the "lands in PR #2" forward reference with the actual final API shape.
- "Follow-ups" — mark TD-008 / TD-009 follow-up items resolved where applicable.
- "Alternatives Considered" — record the API-shape decision (A / B / C) and reasoning.

### 2. Other pending backlog items (from the morning handover, unchanged)

These are smaller items the morning handover queued; PR #2 can be done in parallel or sequenced after.

- **#4 — #1228 guardrail extension** (~10 min). Extend `portal/lib/__tests__/brand.test.ts` walker to include `hooks/`, `middleware.ts`, `e2e/`, root-level files. Audit context `qa_cycle/audits/slop-hunt-PR-1228.md` finding 1.
- **#7 — Codify "dialog owns button" pattern in `frontend/CLAUDE.md`** (~30 min). Audit-03 recommendation #3. Cheap insurance against the Class 3 (Radix asChild) bug class regressing.
- **#5 — Task G CI parity workflow** (~1-2 hr). `.github/workflows/quality-gate.yml` mirroring the local `pre-pr-merge-gate.sh` so the rule can't be bypassed via the GitHub merge UI. Folds in fixing `scripts/run-regression-test.sh` exit-code masking. Tracked in `qa_cycle/known-failures-2026-05-01.md`.
- **#9 — ESLint custom rule for `<*Trigger asChild>` adjacency** (~2 hr). Audit-03 recommendation #2. Prevents Class 3 going forward.
- **#10 — SSR snapshot harness for the dialog component family.** Audit-03 recommendation #4. PR #1262 added one snapshot for `CreateProposalDialog`; a reusable harness would cover the family.
- **TD-009 — opportunistic cleanup of 3 controllers** (`MockPaymentController`, `PortalBrandingController`, `PortalDigestInternalController`). Extract services, controllers delegate per `backend/CLAUDE.md` "Controller Discipline". Documented in `documentation/tech-debt.md`. Do on next touch, not as a standalone PR.

### 3. Gated / deferred to user

- **#1238 NULL currency** — architectural decision (backfill + NOT NULL vs accept the workaround). Defer to user.
- **Regression pack audit** — gated. The pack was built when bugs were live; failures may encode old buggy behaviour. Don't auto-fix regression failures until the pack itself has been audited.

### 4. Phase work (paused)

**Phase 69 (Firm Audit View)** — paused per user gate "Phase 69 stays paused until D + E + F are done". The F-continuation queue closed with PR #1262/3/4/5. Remaining gates for Phase 69: items #4, #5, #7. None blocking; user discretion when to unpause.

---

## Anti-cheat reminders (specific to PR #1's gotchas)

These were learned the hard way during the 2026-05-02 daytime session:

- **The pre-migration audit's strict halt criterion got tripped but the migration proceeded.** PR #1's plan said "if any caller plausibly passes null tenantId, halt and re-spec." 11+ publishers use `RequestScopes.getTenantIdOrNull()` (contract permits null), tripping the rule. The migration proceeded after a structural-invariant proof: every entry point in the codebase that reaches an event publisher binds `TENANT_ID` first (HTTP via `TenantFilter`, jobs via per-tenant loop, listeners via event payload, runners via per-tenant binding, filters via `TenantFilter` itself). For PR #2, **don't repeat this argument from scratch** — the audit was completed exhaustively (all 69 `getTenantIdOrNull()` sites mapped to entry points). Just verify the migration's specific call sites against this invariant; don't re-prove the invariant.

- **ArchUnit 1.3.0 silently imported zero classes on JDK 25.** Existing `LayerDependencyRulesTest` and `TestConventionsTest` were passing vacuously via `allowEmptyShould(true)`. Fixed in PR #1 by upgrading to 1.4.2. **For PR #2's ArchUnit rule, verify it actually fires** by injecting a violation, running the rule, confirming it fails, then reverting. The ArchUnit DSL also has subtle behaviour where `methods().that().haveName(X).should()...allowEmptyShould(true)` *passes* when zero methods match X — this is fine for the regression-guard use case (no match = no violation = OK), but it means the rule will trivially pass the moment the migration is done. Use the inject-and-revert sanity test to prove the rule is actually wired.

- **The 6 fail-closed handlers in PR #1 were converted to fail-fast.** Six of the original 14 handlers had a "drop event with WARN, return" pattern on null tenantId — explicit author intent ("Fail closed: schema-per-tenant means an unbound tenant scope would run repository operations against the default `public` search_path"). The migration converted them to throw `IllegalArgumentException` via `runForTenant`. The audit confirmed no null-tenant event is reachable in production, so the change is observably equivalent. PR #2's jobs migration won't have this concern (jobs bind `TENANT_ID` from `OrgSchemaMappingRepository.findAll()` results, never null). The backfill helpers also bind from a resolved schema, never null. **No fail-closed → fail-fast collapsing in PR #2.**

- **`callForTenant` adapts `Callable<T>` → `ScopedValue.CallableOp<T, X>`** via method-reference (`ScopedValue.CallableOp<T, Exception> op = action::call`). Don't pass `Callable<T>` directly to `ScopedValue.Carrier.call()` — Java 25 changed the API.

- **The merge-gate hook checks marker mtime + exit:0, NOT SHA.** So the marker doesn't strictly need to point at HEAD — but updating it for honesty is good practice. The hook reads `.claude/markers/verify-backend.json`; ensure the JSON keys match (`commit`, `command`, `exit`, `ts`, `summary`). If you add new markers (e.g. `verify-frontend.json`, `verify-portal.json`), they're checked when the PR touches those subtrees.

- **Sed-based migration pitfalls.** PR #1 used `sed -i '' '/^  private void handleInTenantScope(/,/^  }$/d'` to delete the 14 helpers. Worked but left orphan javadocs in 4 files (the section dividers + helper-description comments stayed because they weren't in the helper body). For PR #2, prefer per-file `Edit` calls if the migration is non-uniform, OR follow up the sed pass with a `git grep` for orphan references. Don't trust the sed exit code as proof of clean migration.

- **CodeRabbit reviews once per PR, not per commit.** If you ship multiple commits to a PR, CodeRabbit may not re-review. Stale inline-comment lines after a fix-up commit are normal. Trust CI status for "should I merge", not the inline-comment freshness.

- **`gh pr merge` returns success even when the PR is not yet mergeable.** Always `git fetch origin main && git log origin/main -1` to verify the merge SHA actually landed. Checked this for PR #1265 — confirmed `cc911f1e0`.

- **Spotless will reformat your files on `mvn verify`.** Run `./mvnw -q spotless:apply` before committing if you've edited Java files; otherwise the merge-gate verify will fail on format violations.

- **Backend dev server on port 8080 binds to GreenMail's port 13025.** No — GreenMail singleton is test-only on 13025; backend dev uses 8080 for HTTP. But running concurrent `./mvnw verify` in the same machine has tripped on GreenMail port collision. Don't run two verifies concurrently.

---

## How to start (next-agent checklist)

1. Read `CLAUDE.md` (top "Quality Gates" section).
2. Read this file in full.
3. Read `adr/ADR-T008-tenant-scoped-runner.md` — the design heuristic for PR #2.
4. Read the inline comments in `RetainerPortalSyncService.backfillForTenant` and `TrustLedgerPortalSyncService.backfillForTenant` (around the `ScopedValue.where(...).where(...).where(MEMBER_ID, SYSTEM_ACTOR_ID).run(...)` block).
5. Read `documentation/tech-debt.md` TD-008 (resolved) and TD-009 (active).
6. Read PR #1265's description for the audit narrative + behaviour-change documentation: `gh pr view 1265 --comments`.
7. Run `bash compose/scripts/svc.sh status` — confirm the stack is up.
8. Run `git log --oneline -5` — confirm `cc911f1e0` is the merge.
9. Run `cd backend && ./mvnw verify` — confirm main is at the new baseline (5036 / 0F / 0E / 26 skip).
10. **Brainstorm with the user about the API-shape decision (A / B / C above) before writing code.** Then write a spec at `docs/superpowers/specs/2026-05-02-tenant-scoped-runner-jobs.md`, a plan at `docs/superpowers/plans/2026-05-02-tenant-scoped-runner-jobs.md`, and execute task-by-task.

Quality is king. Not a race. The user has granted autonomy through merge prep, but stops you at the merge gate.
