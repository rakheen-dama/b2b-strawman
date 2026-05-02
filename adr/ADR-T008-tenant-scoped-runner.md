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

### Surface 2 — `TenantScopedRunner` Spring bean + `runForTenantWithMember` (PR #2, shipped 2026-05-02 PM)

PR #2's pre-migration audit found the original "13 jobs + 2 backfills" framing was incomplete. The shipped scope:

1. **`TenantScopedRunner` Spring bean for per-tenant fan-out** (11 scheduled jobs migrated; 1 originally listed job — `SubscriptionExpiryJob` — reclassified as single-tenant per-subscription rebind; 1 originally listed job — `PortalDigestScheduler` — migrated to direct `RequestScopes.callForTenant` calls inside its existing manual for-loop, since its per-tenant `RunResult.errors` aggregation requires preserving per-call exceptions that `forEachTenant`'s BiConsumer contract throws away):
   ```java
   @Component
   public class TenantScopedRunner {
     /** Per-tenant exception isolation: failures logged at ERROR, iteration continues. */
     public int forEachTenant(BiConsumer<String, String> action);
   }
   ```

2. **`RequestScopes.runForTenantWithMember` static method** for the 2 backfill helpers (3-binding TENANT + ORG + MEMBER=SYSTEM_ACTOR_ID):
   ```java
   public static void runForTenantWithMember(
       String tenantId, @Nullable String orgId, UUID actorId, Runnable action);
   ```
   Tenant-isolation guard remains caller responsibility (see `RetainerPortalSyncService.backfillForTenant` for the canonical guard pattern: assert `ORG_ID` bound and matches the supplied `orgId` before calling).

3. **Single-tenant `runForTenant` / `callForTenant` migrations** in 14 additional files (~22 sites) that the original handover scope missed:
   - `AcceptanceService` (5 sites: portal markViewed, brand-color, document lookup, PDF stream, accept idempotency)
   - `MemberSyncService` (3 sites: syncMember, deleteMember, findStaleMembers)
   - `PortalAuthService` (3 sites: requestMagicLink, exchangeToken verify, exchangeToken contact load)
   - `InternalAuditController` (2 sites: listAuditEvents, getStats)
   - `PortalResyncService`, `PackInstallService.internalInstall`, `CustomerAuthFilter` inner read,
     `InvoiceEmailEventListener` (with behaviour change — see "Negative" below),
     `PaymentWebhookController`, `EmailWebhookService`, `UnsubscribeService`,
     `PortalBrandingController`, `PackReconciliationRunner`, `SubscriptionExpiryJob` audit helper.

**API-shape decision for the 3-binding pattern:** Option A — explicit `runForTenantWithMember` static method (mirrors PR #1's `runForTenant` shape). Considered and rejected: Option B (typed Map of additional bindings — type-unsafe, runtime cast required, weaker contract documentation), Option C (capture-and-rebind from a synthetic context — overlaps with ADR-204's `withCurrentScopes` and adds machinery without a clear second consumer).

### Regression guard (PR #1)

`backend/src/test/java/.../architecture/TenantScopeBindingTest.java` fails the build if any class outside `..multitenancy..` declares a method named `handleInTenantScope`, `runInTenantScope`, or `executeInTenantScope`. Uses the standard ArchUnit DSL (`methods().that().haveName(X).should().beDeclaredInClassesThat().resideInAPackage(Y)`), matching the convention of `LayerDependencyRulesTest` and `TestConventionsTest`. Verified to fire on injected violations and pass on clean trees.

**Note on `withTenantScope`:** intentionally NOT in the banned set. ADR-204 reserves that name for a proposed (deferred) `RequestScopes.withCurrentScopes()` capture-and-rebind utility, so banning it would collide with a known legitimate future use case.

**ArchUnit version note:** during the PR's development, ArchUnit 1.3.0 was found to silently import zero classes on JDK 25 — meaning all existing rules (`LayerDependencyRulesTest`, `TestConventionsTest`) were passing vacuously rather than enforcing anything. Resolved by upgrading `archunit-junit5` to `1.4.2` in the same PR. Documented in `documentation/tech-debt.md` as TD-008 (resolved).

### Companion regression guard (PR #2, shipped)

`backend/src/test/java/.../architecture/TenantScopeBindingTest.java` now also bans direct `ScopedValue.where(...)` calls outside `..multitenancy..` and outside a documented exemption set. Per spec §ArchUnit Conditional deferral, ships as **Fallback A**: the broader form (banning ALL `ScopedValue.where` outside the exemption set) rather than the precise TENANT_ID-first-arg form. Trade-off: the precise form requires first-argument-value introspection that ArchUnit's DSL doesn't expose cleanly; the broader form is robust to JDK changes and (because it caught 3 unaudited non-TENANT_ID boundary-binders on its first run) more disciplined. Verified empirically: the rule fired on `MemberFilter` / `PlatformAdminFilter` / `AutomationActionExecutor` before they were added to the exemption set, then passed once the set was complete.

**Exemption catalogue** (each entry MUST stay in sync with the test class — adding a new exemption requires explicit ADR amendment):

| Exemption | Type | Rationale |
|---|---|---|
| `..multitenancy..` | package | Owns the canonical APIs (`RequestScopes`, `TenantScopedRunner`) and the boundary-binding `TenantFilter`. |
| `..dev..` | package | Profile-gated dev test harness (`DevPortalController` with 6 binding sites). |
| `CustomerAuthFilter` | class | Servlet filter; multi-binding from JWT (CUSTOMER_ID + TENANT_ID + ORG_ID + conditional PORTAL_CONTACT_ID). The binding IS the request boundary. |
| `AssistantController` | class | 5-binding capture-and-rebind to bridge servlet thread → virtual thread for SSE LLM streaming. Awaits ADR-204's `withCurrentScopes()`. |
| `MockPaymentController` | class | Profile-gated dev payment mock; site at line 182 is also a cross-tenant invoice search. |
| `MemberFilter` | class | Servlet filter; binds `MEMBER_ID` + `ORG_ROLE` from a tenant-scoped lookup. Filter boundary, like `CustomerAuthFilter`. |
| `PlatformAdminFilter` | class | Servlet filter; binds `GROUPS` from JWT claims. Filter boundary. |
| `AutomationActionExecutor` | class | Binds `AUTOMATION_EXECUTION_ID` on the scheduler→action-execution boundary. Same boundary-binder pattern as filters. |

## Consequences

### Positive

- The sanctioned APIs (`RequestScopes.runForTenant` / `callForTenant` / `runForTenantWithMember`, `TenantScopedRunner.forEachTenant`) cover every consolidatable binding pattern outside the `multitenancy` package. The exemption catalogue captures the residual boundary-binders awaiting ADR-204 — explicit and bounded rather than implicit. Reduces the surface area for Class-1 (notification-pipeline gap) bugs.
- PR #1: ~110 net lines of duplicate code removed across 14 helper files. PR #2: ~24 sites migrated across 27 files (net negative LOC; final tally in PR #2 description).
- Future binding-contract changes (e.g. adding `ORG_ID` blank-handling, new ScopedValue captures) are one-place edits.
- Regression guard (PR #1 method-name + PR #2 call-site) prevents copy-paste regression of either pattern.
- PR #2's broader-than-precise rule choice surfaced 3 unaudited non-TENANT_ID boundary-binders (`MemberFilter`, `PlatformAdminFilter`, `AutomationActionExecutor`) — net win for architectural visibility.
- Surfaces a separate latent issue (ArchUnit-on-JDK-25 silent vacuous passes) addressed in PR #1 via upgrade.

### Negative / behaviour change

- **PR #1 behaviour change:** null/blank `tenantId` now throws `IllegalArgumentException` instead of either falling through to `action.run()` (8 helpers) or silently dropping the event with a WARN (6 helpers). The pre-migration audit (in PR #1265's description) confirms no caller relies on the old fall-through and no test exercises the null path; both old behaviours were dead code.
- **PR #2 behaviour change** in `InvoiceEmailEventListener.onInvoiceSent`: a null-tenant event previously fell through to `handleInvoiceSent(event)` without scope binding (which then failed inside Hibernate; the surrounding try-catch swallowed it as a WARN). The migration converts this to an explicit `log.warn("InvoiceSentEvent has null tenantId, dropping...")` + early return. Same audit class as PR #1's 6 fail-closed handler conversions; pre-migration audit confirms every `InvoiceSentEvent` publisher is downstream of `TenantFilter`, so non-null tenantId is the empirical reality.
- **PR #2 behaviour change** in `MagicLinkCleanupService.cleanupExpiredTokens`: previously bound only `TENANT_ID`; now binds `TENANT_ID + ORG_ID` via the bean. The inner `tokenRepository.deleteByExpiresAtBefore` doesn't read `ORG_ID`, so no observable behaviour change.
- The `TenantScopedRunner` bean (PR #2) introduces a small DI surface — not yet justified by handlers but justified by jobs.
- The PR #1 regression guard is name-based — a contributor could circumvent by inventing a new helper name. The PR #2 companion guard catches the underlying primitive (`ScopedValue.where`) regardless of wrapper name.
- PR #2's broader Fallback A rule means a contributor binding a NEW non-TENANT_ID ScopedValue from a non-exempt class will fail the build, even if the binding is for a non-tenant-isolation purpose. The right fix in that case is to add to the exemption catalogue (with rationale + ADR amendment), not bypass the rule.

### Known limitations

- The PR #2 guard catches all `ScopedValue.where` calls outside the exemption set, regardless of which ScopedValue is bound. The precise alternative (catch only TENANT_ID-first-arg) was rejected in PR #2 (see Companion regression guard) — Fallback A's broader form has identical effect for tenant-isolation purposes today and is robust to JDK changes. The empirical pre-migration grep verified zero indirect-aliasing patterns (`var key = RequestScopes.TENANT_ID; ScopedValue.where(key, ...)`) in current code; if such a pattern emerges, the broader rule still catches it via the `ScopedValue.where` call regardless of how the field is referenced.
- `TenantTransactionHelper.executeInTenantTransaction(...)` is intentionally not consolidated. It does meaningfully more (forces `search_path` for raw SQL during provisioning); the concerns are different. Documented as deliberate non-consolidation.
- **Blank-orgId behaviour change.** The new `runForTenant` skips `ORG_ID` binding when `orgId.isBlank()` is true; the original 14 helpers would have bound the empty/whitespace string. Judged a bug since blank values are never legitimate input. Documented in `RequestScopes.runForTenant` Javadoc.
- **`withTenantScope` is NOT in the banned-name set** even though it would have been a plausible variant. ADR-204 reserves the name for a proposed (deferred) `RequestScopes.withCurrentScopes()` capture-and-rebind utility, so banning it would collide with a legitimate future use case.

### Follow-ups

- **ArchUnit-on-JDK-25 silent vacuous passes** — RESOLVED in PR #1 via upgrade to ArchUnit 1.4.2. See `documentation/tech-debt.md` TD-008.
- **`@EventListener` (synchronous) handlers in `NotificationEventHandler`.** `onBillingRunCompleted` and `onBillingRunFailures` are `@EventListener`, not `@TransactionalEventListener(AFTER_COMMIT)` — they fire inside the originating transaction. Their publishers (`BillingRunGenerationService.generate` etc.) are reached only via request-driven controllers (`@PostMapping("/{id}/generate")`), so `TENANT_ID` is bound by `TenantFilter`. The audit holds for these too.
- **ADR-204 dependency — exemption-set cleanup follow-up.** When ADR-204 lands a sanctioned `RequestScopes.withCurrentScopes()` capture-and-rebind utility, migrate the boundary-binders that today require exemption: `CustomerAuthFilter:80`, `AssistantController:50`, `DevPortalController` (× 6 sites), `MockPaymentController:117`, `MemberFilter:63`, `PlatformAdminFilter:39`, `AutomationActionExecutor:115`. Then remove the corresponding entries from `TenantScopeBindingTest`'s exemption set. (`MockPaymentController:182`, `AcceptanceService:736`, and `PortalDigestScheduler:151` are not `withCurrentScopes`-shaped — they need their own targeted abstractions for cross-tenant search and dual-mode dispatch respectively, or stay exempt indefinitely.) The exemption catalogue in this ADR is the cleanup TODO; removing entries requires this ADR's amendment.
- **`TrustLedgerPortalSyncService.backfillForTenant` cross-tenant guard asymmetry.** `RetainerPortalSyncService.backfillForTenant` requires `RequestScopes.ORG_ID.isBound() && ORG_ID.get().equals(orgId)` before binding the system-actor scope (preventing an authenticated org-A request from triggering a backfill of org-B). The Trust ledger sibling has no equivalent guard. Surfaced during PR #2's pre-migration audit; deliberately not bundled into PR #2 (separate security-class fix). Track separately.

## Alternatives Considered

1. **Single Spring bean fronting both shapes (handler bind + job iteration).** Rejected — would force a 14-class constructor-injection cascade for the handlers with no benefit. The bind primitive belongs next to `RequestScopes.TENANT_ID` so the relationship is structural and visible (matches ADR-T002 spirit: explicit scope boundaries, no hidden magic).

2. **Drop-in replacement preserving the null fall-through.** Rejected — the fall-through is a Class-1 hazard and the cheapest moment to remove it is during the consolidation. The pre-migration audit confirmed no caller relies on the old semantics. Per the user mandate "no workarounds, fix actual flows and bugs as found."

3. **Capture-current-scope variant only (the ADR-204 `withCurrentScopes` shape).** Rejected for this use case — handlers receive `(tenantId, orgId)` from the event payload, not from current scope. ADR-204's variant remains valid for its target use case (virtual-thread executors that inherit the parent request's scope) and is a sibling, not a substitute.

4. **JDK 25 native `java.lang.classfile` regression guard.** Initially adopted because ArchUnit 1.3.0 silently imported zero classes on JDK 25, making the standard DSL pass vacuously. Once we upgraded to ArchUnit 1.4.2 (which fixes the JDK 25 issue), reverted to the standard ArchUnit DSL — matches the convention of `LayerDependencyRulesTest` and `TestConventionsTest`, no external scanner code to maintain.

## References

- PR #1 design spec: `docs/superpowers/specs/2026-05-02-tenant-scoped-runner-design.md`
- PR #1 implementation plan: `docs/superpowers/plans/2026-05-02-tenant-scoped-runner-handlers.md`
- PR #2 design spec: `docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md`
- PR #2 implementation plan: `docs/superpowers/plans/2026-05-02-tenant-scope-binding-consolidation.md`
- Bug-class catalogue: `qa_cycle/bug-classes.md` (Class 1 — notification-pipeline gaps)
- Quality Gates: top of `CLAUDE.md` (#1, #5, #7)
- Adjacent ADRs: `adr/ADR-T002-scopedvalues-over-threadlocal.md`, `adr/ADR-204-virtual-thread-scoped-value-rebinding.md`
