# ADR-T008: Tenant-Scoped Runner — Canonical API for Binding Tenant Scope Outside a Request

**Status:** Accepted

**Date:** 2026-05-02

**Series:** Tenancy foundation (T-prefix)

**Related:** ADR-T002 (ScopedValues over ThreadLocal — primitive choice mandate); ADR-204 (virtual-thread ScopedValue rebinding — adjacent precedent for static-method-on-`RequestScopes` shape, proposed `withCurrentScopes`, deferred).

## Context

By 2026-05-02 the codebase had **14 near-identical private `handleInTenantScope(String tenantId, String orgId, Runnable action)` helpers** scattered across notification handlers, portal sync services, and the legal-vertical trust handler. Each was the canonical pattern for binding `RequestScopes.TENANT_ID` (and optionally `ORG_ID`) on a fresh `ScopedValue` carrier when running work outside the originating request — typically inside an `@TransactionalEventListener(phase = AFTER_COMMIT)` listener.

Two motivations:

1. **Latent silent-corruption hazard.** Eight of the helpers fell through to `action.run()` when `tenantId == null`; the other six logged a WARN and returned. In practice unreachable for AFTER_COMMIT listeners (the publisher guarantees a non-null `tenantId` from the originating transaction), but the duplication encoded the hazard 14 times. The bug-class catalogue (`qa_cycle/bug-classes.md`, Class 1 — notification-pipeline gaps) documents this category as the dominant bug class shipped through the 2026-04 / 2026-05 QA cycle.

2. **Maintenance smell.** Any change to the binding contract — e.g. capturing `MEMBER_ID` for audit attribution, changing blank-string semantics — would need to touch 14 places.

Adjacent precedent: **ADR-204** proposes a `RequestScopes.withCurrentScopes()` capture-and-rebind utility for virtual-thread executors but defers implementation. This ADR ships a sibling utility — explicit-input rather than capture — for the AFTER_COMMIT use case.

A separate but related pattern lives in **13+ scheduled jobs** (`RecurringScheduleExecutor`, `TimeReminderScheduler`, `ProposalExpiryProcessor`, `CourtDateReminderJob`, `DormancyScheduledJob`, etc.) that iterate `OrgSchemaMappingRepository.findAll()` and bind `ScopedValue.where(TENANT_ID, schema).where(ORG_ID, orgId).call(...)` inline. Those don't migrate in this PR — they get a sibling abstraction (`TenantScopedRunner` Spring bean) in PR #2.

## Decision

Two surfaces, single source of truth.

### Surface 1 — `RequestScopes.runForTenant` / `callForTenant` (this PR)

Static methods on the existing `RequestScopes` class, co-located with the `ScopedValue<String> TENANT_ID` / `ORG_ID` field declarations:

```java
public static void runForTenant(String tenantId, @Nullable String orgId, Runnable action);
public static <T> T callForTenant(String tenantId, @Nullable String orgId, Callable<T> action);
```

Semantics:

- **Null or blank `tenantId` → `IllegalArgumentException`.** No fall-through. Replaces the silent hazard with fail-fast at the call site.
- **`orgId` nullable.** Some events carry no org context. Matches the migrated helpers' actual behaviour.
- **No re-binding of `MEMBER_ID` / `ORG_ROLE` / `CAPABILITIES`.** Handlers are system-level dispatch; actor scope ends at the originating request's commit boundary. Re-binding actor identity across `AFTER_COMMIT` would be a category mistake — audit attribution would lie.
- **`Callable<T>` exceptions** rethrown via `RuntimeException` wrapping per JDK Callable convention. Java 25's `ScopedValue.Carrier.call` takes `CallableOp<T, X>` not `Callable<T>`, so the implementation adapts via method reference.

### Surface 2 — `TenantScopedRunner` Spring bean (PR #2)

For scheduled jobs that fan out to all tenants. Distinct shape: iteration over `OrgSchemaMappingRepository`, per-tenant exception isolation, success-count return:

```java
@Component
public class TenantScopedRunner {
  public int forEachTenant(BiConsumer<String, String> action);
}
```

Lands in PR #2; this ADR will be amended at that point with the bean's full contract.

### Regression guard (PR #1)

`backend/src/test/java/.../architecture/TenantScopeBindingTest.java` fails the build if any class outside `..multitenancy..` declares a method named `handleInTenantScope`, `runInTenantScope`, `executeInTenantScope`, or `withTenantScope`. This catches copy-paste regressions of the consolidated pattern.

**Implementation note:** the regression guard scans `target/classes/` directly using JDK 25's `java.lang.classfile` API rather than ArchUnit's DSL. ArchUnit 1.3.0's class-location resolution silently imports zero classes on JDK 25 in this project's test setup — which would let any rule pass vacuously and defeat its purpose. The codebase's existing `LayerDependencyRulesTest` and `TestConventionsTest` have the same latent issue (they pass-through `allowEmptyShould(true)`); fixing the underlying ArchUnit-on-JDK-25 setup is out of scope for this PR.

### Companion regression guard (PR #2)

PR #2 will add a sibling test that bans direct `ScopedValue.where(RequestScopes.TENANT_ID, ...)` calls outside `..multitenancy..`. It cannot ship now because the 13 unmigrated scheduled jobs would build-break the moment PR #1 merges. The two-phase rollout is an artifact of the migration order, not a permanent split.

## Consequences

### Positive

- The only sanctioned way to bind tenant scope outside the `multitenancy` package is the canonical static API. Reduces the surface area for Class-1 (notification-pipeline gap) bugs.
- `~110` net lines of duplicate code removed across 14 files.
- Future binding-contract changes (e.g. adding `ORG_ID` blank-handling, new ScopedValue captures) are one-place edits.
- Regression guard prevents future copy-paste regression.
- Surfaces a separate latent issue (ArchUnit-on-JDK-25 silent vacuous passes) that should be addressed in follow-up work.

### Negative / behaviour change

- One observable behaviour change: null/blank `tenantId` now throws `IllegalArgumentException` instead of either falling through to `action.run()` (8 helpers) or silently dropping the event with a WARN (6 helpers). The pre-migration audit (in this PR's description) confirms no caller relies on the old fall-through and no test exercises the null path; both old behaviours were dead code.
- The `TenantScopedRunner` bean (PR #2) introduces a small DI surface — not yet justified by handlers but justified by jobs.
- The regression guard is name-based — a contributor could circumvent by inventing a new helper name. Mitigated by PR #2's companion guard which catches the underlying primitive (`ScopedValue.where(TENANT_ID, ...)`) regardless of wrapper name.

### Known limitations

- The future PR #2 guard won't catch indirect access via local variable — `var key = RequestScopes.TENANT_ID; ScopedValue.where(key, ...)`. Not present in current code. Tightenable if it becomes a real evasion pattern.
- `TenantTransactionHelper.executeInTenantTransaction(...)` is intentionally not consolidated. It does meaningfully more (forces `search_path` for raw SQL during provisioning); the concerns are different. Documented as deliberate non-consolidation.

## Alternatives Considered

1. **Single Spring bean fronting both shapes (handler bind + job iteration).** Rejected — would force a 14-class constructor-injection cascade for the handlers with no benefit. The bind primitive belongs next to `RequestScopes.TENANT_ID` so the relationship is structural and visible (matches ADR-T002 spirit: explicit scope boundaries, no hidden magic).

2. **Drop-in replacement preserving the null fall-through.** Rejected — the fall-through is a Class-1 hazard and the cheapest moment to remove it is during the consolidation. The pre-migration audit confirmed no caller relies on the old semantics. Per the user mandate "no workarounds, fix actual flows and bugs as found."

3. **Capture-current-scope variant only (the ADR-204 `withCurrentScopes` shape).** Rejected for this use case — handlers receive `(tenantId, orgId)` from the event payload, not from current scope. ADR-204's variant remains valid for its target use case (virtual-thread executors that inherit the parent request's scope) and is a sibling, not a substitute.

4. **ArchUnit-based regression guard.** Attempted, rejected. ArchUnit 1.3.0 fails class-location resolution on JDK 25; the rule would pass vacuously and provide no protection. Switched to the JDK 25 native `java.lang.classfile` scanner.

## References

- Design spec: `docs/superpowers/specs/2026-05-02-tenant-scoped-runner-design.md`
- Implementation plan: `docs/superpowers/plans/2026-05-02-tenant-scoped-runner-handlers.md`
- Bug-class catalogue: `qa_cycle/bug-classes.md` (Class 1 — notification-pipeline gaps)
- Quality Gates: top of `CLAUDE.md` (#1, #5, #7)
- Adjacent ADRs: `adr/ADR-T002-scopedvalues-over-threadlocal.md`, `adr/ADR-204-virtual-thread-scoped-value-rebinding.md`
