# TenantScopedRunner Extraction — Design Spec

**Date:** 2026-05-02
**Author:** orchestrator session (Opus 4.7)
**Status:** Approved for implementation
**Scope:** Backlog item #8 from `qa_cycle/HANDOFF-2026-05-02.md`. Two-PR refactor consolidating duplicated tenant-scope binding code across notification handlers (PR #1) and scheduled jobs (PR #2).

## Why

Not a bug fix. A code-quality refactor with two specific defensive aims:

1. **Class-1 prevention.** `qa_cycle/bug-classes.md` documents notification-pipeline gaps as the dominant bug class shipped this cycle (5 of the recent fix PRs were Class 1). Every Class 1 mechanism involves "something downstream of tenant context dispatch went wrong silently." A canonical entry point with fail-fast on null + a regression guard against copy-paste is direct prevention for that class.
2. **Maintenance smell.** 14 copies of an 8-line helper means any future change to the binding contract (e.g. add `MEMBER_ID` capture, change blank-string handling) touches 14 places.

What this refactor does NOT fix: any failing test, observed defect, or user-facing behaviour. It is prophylactic for a class of bug we have evidence of, not for a confirmed live defect today.

## Current State (verified by Explore audit, 2026-05-02)

- **14 classes** declare a private `handleInTenantScope(String tenantId, String orgId, Runnable action)` with byte-for-byte identical bodies. Files: `notification/NotificationEventHandler`, `portal/notification/PortalEmailNotificationChannel`, `portal/PortalDocumentNotificationHandler`, `proposal/ProposalSentEmailHandler` + 3 sibling proposal handlers, `informationrequest/{InformationRequestEmailEventListener, InformationRequestNotificationEventListener}`, `customerbackend/handler/PortalEventHandler`, three `customerbackend/service/*PortalSyncService` classes, and `verticals/legal/trustaccounting/event/TrustNotificationHandler`. Total invocations: 100+.
- **13+ scheduled jobs** use a related but distinct pattern: iterate `OrgSchemaMappingRepository.findAll()` and bind `ScopedValue.where(TENANT_ID, schema).where(ORG_ID, orgId).call(...)` inline. Examples: `RecurringScheduleExecutor`, `TimeReminderScheduler`, `ProposalExpiryProcessor`, `CourtDateReminderJob`, `DormancyScheduledJob`, `MagicLinkCleanupService`, `PortalDigestScheduler`.
- **Existing primitive:** `io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes` declares `ScopedValue<String> TENANT_ID` and `ScopedValue<String> ORG_ID`. No public method exists today for binding them; callers do `ScopedValue.where(...)` inline.
- **ADR-T002** mandates `ScopedValue` over `ThreadLocal` for tenant context. **ADR-204** proposes (but defers) a `RequestScopes.withCurrentScopes()` capture-and-rebind utility — adjacent precedent for the static-method-on-RequestScopes shape.
- **Existing related helper:** `TenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, action)` lives in `multitenancy/`. Used only by provisioning code paths because it forces `search_path` for raw SQL. Out of scope for this refactor (different concern).
- **Existing ArchUnit test conventions:** `LayerDependencyRulesTest`, `TestConventionsTest` in `backend/src/test/java/.../architecture/`. Uses ArchUnit 1.3.0.

The current shape of the duplicated helper:

```java
private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
  if (tenantId != null) {
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    carrier.run(action);
  } else {
    action.run();   // fall-through: runs without tenant scope bound
  }
}
```

The `else` branch is a latent silent-corruption hazard: if reached, queries hit the wrong schema or fail. In practice it is believed unreachable (AFTER_COMMIT handlers have a non-null `tenantId` from the event payload by construction), but this design rejects null at the new entry point and makes the audit step explicit.

## Design

### Architecture overview — two surfaces, single source of truth

**Surface 1 — `RequestScopes` (existing class, gains static methods).** The bind primitive lives next to the `ScopedValue` field declarations it operates on. No DI. Used directly by the 14 event handlers and any one-off call site that already has `(tenantId, orgId)` in hand.

**Surface 2 — `TenantScopedRunner` (new Spring bean, `multitenancy/`).** The iteration helper. Injects `OrgSchemaMappingRepository`. Exposes `forEachTenant(BiConsumer<String, String>)` — internally loops mappings and delegates per-tenant to `RequestScopes.runForTenant`. This is what the 13+ scheduled jobs migrate to in PR #2.

Why two surfaces, not one bean fronting everything:
- Handlers don't need DI; forcing a 14-class constructor cascade for a bean is overkill.
- The bind primitive belongs next to `RequestScopes.TENANT_ID` so the relationship is structural and visible.
- The bean has a genuinely different job (iterate over a repository) that benefits from injection + mockability.

What stays unchanged:
- `ScopedValue<String> TENANT_ID` / `ORG_ID` field declarations on `RequestScopes`.
- `TenantTransactionHelper` (provisioning-only helper) — out of scope.
- All other `RequestScopes` static getters (`requireTenantId()`, etc.).

**Boundary rule (enforced via ArchUnit):** the only legal way to bind tenant scope outside `RequestScopes` itself is via `RequestScopes.runForTenant` / `callForTenant` — directly, or transitively via `TenantScopedRunner.forEachTenant`.

### API specification

**`RequestScopes` additions (PR #1):**

```java
/**
 * Run {@code action} with TENANT_ID (and optionally ORG_ID) bound on a fresh
 * ScopedValue carrier. The only sanctioned way to bind tenant scope outside
 * {@code RequestScopes} itself; see ArchUnit rule TenantScopeBindingRule.
 *
 * @throws IllegalArgumentException if tenantId is null or blank.
 * @throws NullPointerException if action is null.
 */
public static void runForTenant(String tenantId, @Nullable String orgId, Runnable action);

public static <T> T callForTenant(String tenantId, @Nullable String orgId, Callable<T> action);
// @Nullable here = jakarta.annotation.Nullable (matches existing convention in
// non-Spring-bean code under multitenancy/). Spring beans use
// org.springframework.lang.Nullable; both annotations exist in this codebase.
```

Semantics:
- **Null or blank tenantId → `IllegalArgumentException`.** Empty-string is the silent-corruption variant of null and is rejected the same way.
- **OrgId nullable.** Some handlers genuinely don't have it (system events). Matches current 14-helper behaviour.
- **No re-binding of `MEMBER_ID` / `ORG_ROLE` / `CAPABILITIES`.** Handlers and jobs are system-level dispatch — actor scope ends at the originating request's commit boundary. Re-binding actor identity across AFTER_COMMIT would be a category mistake.
- **`Callable<T>` exceptions** rethrown via `RuntimeException` wrapping per JDK Callable convention, mirroring `ScopedValue.callWhere` semantics.

**`TenantScopedRunner` (new bean, PR #2):**

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

Semantics:
- **Continue-on-error.** One tenant's failure must not abort the rest of the batch. Mirrors existing job pattern.
- **Logs each per-tenant failure at ERROR with tenantId/orgId in MDC** for grep-ability.
- **Returns success count** so callers can metric / alert.
- **No filter parameter in v1.** Some existing jobs filter dormant tenants inline; v1 ships the simple case. Add `forEachActiveTenant` (or similar) when 2+ jobs need the same filter.

Method names — `runForTenant` / `callForTenant` — chosen to mirror `Runnable.run` / `Callable.call`, non-confusable with `withTenantScope` (existing `ScopedValue.where(...)` lingo).

### Migration plan

**PR #1 — handlers consolidation**

| Step | Action | Verification |
|---|---|---|
| 1 | Audit null-tenant call sites: `git grep -n "handleInTenantScope" backend/src/main` → for every match, trace the `tenantId()` source. Document each in PR description. | If any caller plausibly passes null, halt and re-spec — fix upstream first |
| 2 | Add `RequestScopes.runForTenant` and `callForTenant` per spec above | Unit tests in `RequestScopesTest`: tenant-bound, org-bound, null-tenant rejected, blank rejected, exception propagation |
| 3 | Migrate 14 handlers — replace each private helper + each call site | Per-handler integration tests stay green |
| 4 | Add ArchUnit rule #1 (banned helper method names) | Rule passes after migration |
| 5 | Write `ADR-T008-tenant-scoped-runner.md` — next number in the `ADR-T` (tenancy foundation) series after T001–T007 | Lives in `adr/` at repo root |
| 6 | Full `./mvnw verify` — merge bar (Quality Gate #1) | 5014/0F/0E/26 baseline preserved |

PR scope is one-bug-class cluster (Quality Gate #7 explicitly authorises). Net diff estimate: +~150 / -~150.

**PR #2 — jobs migration + ArchUnit rule #2** (separate session)

| Step | Action | Verification |
|---|---|---|
| 1 | Add `TenantScopedRunner` bean + `forEachTenant(BiConsumer<String, String>)` | Unit tests with mocked `OrgSchemaMappingRepository` |
| 2 | Migrate 13+ scheduled jobs to `tenantScopedRunner.forEachTenant(...)` | Each job's existing tests stay green |
| 3 | Add ArchUnit rule #2 (ban direct `ScopedValue.where(TENANT_ID, ...)` outside `RequestScopes`) | Rule passes only after the last job is migrated |
| 4 | Update ADR with PR #2 realisation | |
| 5 | Full `./mvnw verify` | Same baseline |

**Why ArchUnit rule timing matters:** if rule #2 shipped with PR #1, the 13 unmigrated jobs would be a build break the moment PR #1 lands. Rule #2 must ship with PR #2 only. Rule #1 has no such ordering issue because the 14 declarations it bans are removed in PR #1 itself.

### ArchUnit rule mechanics

**Rule #1 (PR #1) — banned helper method names**

```java
@AnalyzeClasses(packages = "io.b2mash.b2b.b2bstrawman", importOptions = DoNotIncludeTests.class)
class TenantScopeBindingRule {

  private static final Set<String> BANNED_HELPER_NAMES = Set.of(
      "handleInTenantScope", "runInTenantScope", "executeInTenantScope", "withTenantScope"
  );

  @ArchTest
  static final ArchRule no_private_tenant_scope_helpers_outside_request_scopes =
      noClasses()
          .that().resideOutsideOfPackage("..multitenancy..")
          .should(declareMethodWithBannedName(BANNED_HELPER_NAMES))
          .because("Bind tenant scope via RequestScopes.runForTenant / callForTenant. "
              + "Adding a private helper recreates the duplication this rule prevents. "
              + "See ADR-T008.");
}
```

Walks every class outside `multitenancy`; fails if any declares a method whose name matches the banned set. Test sources excluded so test fixtures may use those names freely.

**Rule #2 (PR #2) — banned direct `ScopedValue.where(TENANT_ID, ...)` outside `RequestScopes`**

```java
@ArchTest
static final ArchRule tenant_scope_value_only_bound_in_request_scopes =
    noClasses()
        .that().resideOutsideOfPackage("..multitenancy..")
        .should().callMethodWhere(targetIs("java.lang.ScopedValue", "where")
            .and(arg(0).is("io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID")))
        .because("Tenant scope must be bound via RequestScopes.runForTenant / callForTenant "
            + "or TenantScopedRunner.forEachTenant. Direct ScopedValue.where(TENANT_ID, ...) "
            + "outside RequestScopes is forbidden. See ADR-T008.");
```

ArchUnit's `JavaCall` API exposes the call target + argument field reference. Verified against ArchUnit 1.3.0 docs (the version `LayerDependencyRulesTest` uses).

**Package-level exclusion (vs class-level):** rule excludes the whole `multitenancy` package, not just `RequestScopes` class. Reasoning: `TenantScopedRunner` lives in `multitenancy` and may need to bind directly internally. If we want tighter enforcement later we can swap to `belongTo(RequestScopes.class)`.

**Known limitation documented in ADR:** `var key = RequestScopes.TENANT_ID; ScopedValue.where(key, ...)` (indirect via local) is not caught. Not present in current code; tighten rule if it ever appears in practice.

### Testing approach

**PR #1 — `RequestScopes` additions** — `RequestScopesTest` (new or extended):

| Test | What it verifies |
|---|---|
| `runForTenant_bindsTenantId` | Inside `runForTenant("acme", null, ...)`, `RequestScopes.requireTenantId()` returns `"acme"` |
| `runForTenant_bindsOrgIdWhenProvided` | Inside `runForTenant("acme", "org-1", ...)`, both bound correctly |
| `runForTenant_omitsOrgIdWhenNull` | `getOrgIdOrNull()` returns `null` |
| `runForTenant_rejectsNullTenant` | Throws `IllegalArgumentException`, action never runs |
| `runForTenant_rejectsBlankTenant` | Same for `""` and `"   "` |
| `runForTenant_rejectsNullAction` | NPE on null Runnable, before any binding |
| `runForTenant_propagatesExceptions` | Action `RuntimeException` propagates unchanged |
| `callForTenant_returnsResult` | Returns the Callable's `T` |
| `callForTenant_wrapsCheckedException` | Callable checked → wrapped in unchecked per JDK convention |

**PR #1 — Handler migration coverage:** no new handler-level tests for the migration. Each of the 14 handlers already has integration tests that exercise the AFTER_COMMIT path. The migration is mechanical — if existing tests pass, behaviour is preserved. Bar is full `./mvnw verify` (Quality Gate #5: targeted runs do not catch cross-package breakage; OBS-2102→OBS-2108 was this exact failure mode).

**PR #1 — ArchUnit rule self-test:** trust ArchUnit's framework; manually add a temp violation locally before commit to confirm the rule fires, delete after. ArchUnit's rule semantics are well-tested upstream.

**PR #2 — `TenantScopedRunner` tests:**

| Test | What it verifies |
|---|---|
| `forEachTenant_invokesActionPerMapping` | Mock repo returns 3 mappings; action called 3× |
| `forEachTenant_bindsScopeForEachInvocation` | Action can call `RequestScopes.requireTenantId()` and gets the right schema |
| `forEachTenant_isolatesPerTenantFailures` | Tenant 2 throws; tenants 1 and 3 still run; error logged |
| `forEachTenant_returnsSuccessCount` | 3 mappings, 1 throws → returns 2 |
| `forEachTenant_handlesEmptyRepo` | Returns 0, action never invoked |

**Test conventions adhered to:** no Testcontainers (memory `feedback_no_testcontainers.md`); embedded Postgres / mocks; full suite as merge bar (Quality Gate #5).

### Risks and mitigations

| Risk | Mitigation |
|---|---|
| Null-tenant audit misses a real null path → production `IllegalArgumentException` | Audit is step 1 of PR #1; PR cannot land if audit finds an unfixed null path |
| ArchUnit rule #2 ships before all jobs migrate → CI breaks on `main` | Rule #2 ships with PR #2 by design |
| Variant name (`runInTenantScope`, etc.) survives migration | All four banned names are checked by `git grep` before commit; rule #1 catches future variants |
| Handler test that depends on the fall-through behaviour | If found during audit, escalate before migrating that one site |
| `ScopedValue.where` indirect-via-local-variable bypass | Documented limitation; not present in current code; tighten rule if it appears |

### Open questions deliberately deferred

1. **Should `forEachTenant` filter dormant tenants?** v1 ships unfiltered. If 2+ jobs need the same filter, add `forEachActiveTenant` in v2.
2. **Should there be a `Callable<T>` variant on `forEachTenant`?** Returning per-tenant results. No current job uses this shape.
3. **Should `MEMBER_ID` / `ORG_ROLE` / `CAPABILITIES` ever be re-bound?** No — explicit non-goal in ADR.
4. **Should `TenantTransactionHelper` be unified later?** Probably not — different concern (forces `search_path`). Documented as intentional non-consolidation.

### Out of scope

- Frontend changes (none).
- DB migration files (none — pure refactor).
- Re-binding actor scope across AFTER_COMMIT.
- `TenantTransactionHelper` provisioning helper.
- Per-job inline filters / scheduling concerns.
- New event payloads or contract changes — events still carry `tenantId()` / `orgId()` as today.

## Success criteria

- PR #1 lands with full `./mvnw verify` green (5014 / 0F / 0E / 26 skip baseline preserved).
- 14 `handleInTenantScope` declarations gone, replaced by `RequestScopes.runForTenant` / `callForTenant` calls.
- `adr/ADR-T008-tenant-scoped-runner.md` written.
- ArchUnit rule #1 passing.
- Pre-merge gate hook satisfied.
- Net diff approximately +150 / -150.
- PR #2 lands later with the 13 jobs migrated, the bean introduced, and ArchUnit rule #2 active.

## References

- `qa_cycle/HANDOFF-2026-05-02.md` — backlog item #8 source.
- `qa_cycle/bug-classes.md` — Class 1 (notification-pipeline gaps) framing.
- `adr/ADR-T002-scopedvalues-over-threadlocal.md` — primitive choice mandate.
- `adr/ADR-204-virtual-thread-scoped-value-rebinding.md` — adjacent precedent (proposed `withCurrentScopes`, deferred).
- `CLAUDE.md` Quality Gates #1, #5, #7 — merge bar, scope discipline, full verify.
- Memory `feedback_fix_right_layer.md` — null fall-through fix belongs in upstream publisher, not in 14 listeners.
- Memory `feedback_no_testcontainers.md` — test convention.
