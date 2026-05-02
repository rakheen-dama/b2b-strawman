# Tenant Scope Binding Consolidation — PR #2 Specification

**Date:** 2026-05-02 (PM)
**Author:** orchestrator session (Opus 4.7)
**Status:** Draft — awaiting sign-off
**Builds on:** PR #1265 (handler consolidation, ADR-T008 Surface 1, merged at `cc911f1e0`)
**Related:** ADR-204 (deferred `withCurrentScopes()`), TD-008 (resolved), TD-009 (separate)

---

## Why

PR #1265 consolidated 14 `handleInTenantScope`-named handler helpers into the canonical `RequestScopes.runForTenant(tenantId, orgId, action)` static method. The handover for PR #2 specified the natural follow-up scope as "13 scheduled jobs + 2 backfill helpers" plus a companion ArchUnit rule banning all direct `ScopedValue.where(RequestScopes.TENANT_ID, ...)` outside `..multitenancy..`.

A pre-migration audit (this spec) found that handover scope is incomplete:

- **InvoiceEmailEventListener:38** is structurally identical to PR #1's 14 helpers but inlined the `ScopedValue.Carrier` instead of wrapping in a `handleInTenantScope`-named helper, so PR #1's consolidation grep missed it.
- **~21 single-tenant `runForTenant` / `callForTenant` migration sites** exist in service methods, controllers, webhook receivers, and the customer-portal auth filter's inner read. None were in PR #1's scope (helper-name consolidation) and none in the handover's PR #2 scope (jobs + backfills). All fit PR #1's existing `runForTenant` / `callForTenant` API.
- **~10 boundary-binder sites** cannot be expressed as `runForTenant` because their binding shape is inherently multi-binding from a request boundary (`CustomerAuthFilter:80`), N-binding capture-and-rebind for thread bridging (`AssistantController:50` — 5 ScopedValues for SSE virtual thread), production-mimicking dev tooling (`DevPortalController` × 6, `MockPaymentController` × 2), or cross-tenant discovery search (`AcceptanceService:736` — early-return scan of all tenant schemas).
- The handover's "ban all direct binding outside multitenancy" rule cannot ship cleanly because the boundary-binders have no sanctioned API to migrate to. ADR-204's deferred `withCurrentScopes()` is the long-term home for capture-and-rebind; until it lands, those sites need exemption.

Reframe: PR #2 migrates everything that fits a sanctioned API; documents and exempts the legitimate boundary-binders; ships an ArchUnit rule with a structured exemption catalogue. The exemption catalogue IS the future-cleanup TODO when ADR-204 lands.

Not a bug fix. A code-quality refactor + regression guard. Same Class-1 (notification-pipeline gap) prevention rationale as PR #1.

---

## Goals

1. Eliminate ~36 direct `ScopedValue.where(RequestScopes.TENANT_ID, ...)` call sites outside `..multitenancy..` by migrating them to canonical APIs.
2. Add two new API surfaces that PR #1's `runForTenant` doesn't cover:
   - `RequestScopes.runForTenantAsSystemActor(tenantId, orgId, actorId, action)` — 3-binding for the 2 backfill helpers.
   - `TenantScopedRunner.forEachTenant(BiConsumer<String, String>)` Spring bean — per-tenant fan-out for the 13 scheduled jobs.
3. Ship a companion ArchUnit rule with structured exemption set that:
   - Catches re-introduction of direct binding in non-exempt code immediately.
   - Names each exemption with rationale (boundary / awaiting-ADR-204 / dev-only / cross-tenant-search).
   - Forms the natural cleanup TODO when ADR-204 lands.
4. Amend ADR-T008 with final API surface, exemption catalogue, ADR-204 dependency.

**Non-goals (explicitly excluded):**

- Migrating the boundary-binders. Awaits ADR-204's `withCurrentScopes()`.
- TD-009 controller refactoring (`InternalAuditController`, `PortalBrandingController`, `MockPaymentController` per Controller Discipline). Touch-it-don't-fix-it; opportunistic.
- The cross-tenant-guard asymmetry between `RetainerPortalSyncService.backfillForTenant` (has `ORG_ID.equals(orgId)` guard) and `TrustLedgerPortalSyncService.backfillForTenant` (no guard). Separate security-class fix; do not bundle with API consolidation.
- Regression pack audit (gated to user).
- ADR-204's `withCurrentScopes()` implementation (its own initiative).

---

## New API surfaces

### `RequestScopes.runForTenantAsSystemActor`

```java
/**
 * Variant of {@link #runForTenant} that additionally binds {@link #MEMBER_ID} to a system-actor
 * sentinel UUID. Use only from internal admin-authenticated callers (currently the two portal
 * read-model backfill helpers) where downstream services read {@link #requireMemberId()} for
 * audit attribution and would throw without a bound member.
 *
 * <p>Tenant-isolation guard remains caller responsibility: see
 * {@link io.b2mash.b2b.b2bstrawman.customerbackend.service.RetainerPortalSyncService#backfillForTenant(String)}
 * for the current pattern (require {@link #ORG_ID} bound by an authenticated request, assert it
 * matches the supplied orgId before calling this method).
 *
 * @throws IllegalArgumentException if tenantId is null or blank.
 * @throws NullPointerException if action or actorId is null.
 */
public static void runForTenantAsSystemActor(
    String tenantId, @Nullable String orgId, UUID actorId, Runnable action);
```

Implementation reuses private `bindTenantScope(tenantId, orgId)`, then chains `.where(MEMBER_ID, actorId).run(action)`.

No `callForTenantAsSystemActor` variant. Both backfill helpers return values via captured-array mutation (`int[] counts = {0}`); they don't need a value-returning variant. YAGNI per CLAUDE.md.

### `TenantScopedRunner` Spring bean

```java
package io.b2mash.b2b.b2bstrawman.multitenancy;

@Component
public class TenantScopedRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantScopedRunner.class);

  private final OrgSchemaMappingRepository mappingRepository;

  public TenantScopedRunner(OrgSchemaMappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  /**
   * Iterate every active tenant schema and run {@code action} once per tenant with TENANT_ID +
   * ORG_ID bound. Per-tenant exceptions are caught, logged at ERROR with tenantId/orgId in MDC,
   * and do NOT abort the iteration. Returns the count of tenants for which action completed
   * without throwing.
   */
  public int forEachTenant(BiConsumer<String, String> action) {
    Objects.requireNonNull(action, "action");
    int succeeded = 0;
    for (var mapping : mappingRepository.findAll()) {
      try {
        RequestScopes.runForTenant(
            mapping.getSchemaName(),
            mapping.getClerkOrgId(),
            () -> action.accept(mapping.getSchemaName(), mapping.getClerkOrgId()));
        succeeded++;
      } catch (Exception e) {
        log.error(
            "Per-tenant action failed: tenant={} org={}: {}",
            mapping.getSchemaName(),
            mapping.getClerkOrgId(),
            e.getMessage(),
            e);
      }
    }
    return succeeded;
  }
}
```

The 13 fan-out call sites currently each implement this isolation-by-try-catch inline. The bean centralizes it.

---

## Migration catalogue

### A. Per-tenant fan-out → `TenantScopedRunner.forEachTenant` (13 sites)

Migration shape:

```java
// Before:
for (var mapping : orgSchemaMappingRepository.findAll()) {
  try {
    ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
        .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
        .run(() -> doWork(mapping));
  } catch (Exception e) {
    log.error("Failed for tenant {}: {}", mapping.getSchemaName(), e.getMessage(), e);
  }
}

// After:
tenantScopedRunner.forEachTenant((tenantId, orgId) -> doWork(tenantId, orgId));
```

Files (one task per file in the plan):

- `acceptance/AcceptanceExpiryProcessor.java:37`
- `automation/AutomationScheduler.java:64`
- `automation/FieldDateScannerJob.java:69`
- `billing/SubscriptionExpiryJob.java:203`
- `compliance/DormancyScheduledJob.java:48`
- `informationrequest/RequestReminderScheduler.java:64`
- `portal/MagicLinkCleanupService.java:45`
- `portal/notification/PortalDigestScheduler.java:151`
- `proposal/ProposalExpiryProcessor.java:66`
- `schedule/RecurringScheduleExecutor.java:41`
- `schedule/TimeReminderScheduler.java:70`
- `template/LegacyContentImportRunner.java:85`
- `verticals/legal/courtcalendar/CourtDateReminderJob.java:63`

The 13 sites currently each inject `OrgSchemaMappingRepository` directly. After migration, the repository injection can be removed where it's no longer used elsewhere in the same class — verify per file.

### B. Single-tenant → `runForTenant` / `callForTenant` (~21 sites)

Migration shape:

```java
// Before (run flavour):
ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .where(RequestScopes.ORG_ID, orgId)
    .run(() -> doWork());

// After:
RequestScopes.runForTenant(schema, orgId, () -> doWork());

// Before (call flavour):
T result = ScopedValue.where(RequestScopes.TENANT_ID, schema).call(() -> compute());

// After:
T result = RequestScopes.callForTenant(schema, /*orgId*/ null, () -> compute());
```

Files (line counts in parens):

- `acceptance/AcceptanceService.java:770, 791, 812, 860, 889` (5 sites)
- `audit/InternalAuditController.java:55, 68` (2 sites)
- `customerbackend/service/PortalResyncService.java:72` (pre-bound carrier `var carrier = ScopedValue.where(...)...; carrier.run(...)`)
- `integration/email/EmailWebhookService.java:157`
- `integration/email/UnsubscribeService.java:97`
- `integration/payment/PaymentWebhookController.java:75`
- `invoice/InvoiceEmailEventListener.java:38` *(behaviour-change footnote — see §B.1)*
- `member/MemberSyncService.java:80, 154, 189` (3 sites)
- `packs/PackInstallService.java:124`
- `portal/CustomerAuthFilter.java:89` (inner read; outer multi-binding at line 80 STAYS exempt)
- `portal/PortalAuthService.java:62, 123, 133` (3 sites)
- `portal/PortalBrandingController.java:68`
- `provisioning/PackReconciliationRunner.java:159`

#### B.1 Behaviour change — `InvoiceEmailEventListener`

**Today:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onInvoiceSent(InvoiceSentEvent event) {
  if (event.tenantId() != null) {
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, event.tenantId());
    if (event.orgId() != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, event.orgId());
    }
    carrier.run(() -> handleInvoiceSent(event));
  } else {
    handleInvoiceSent(event);  // null-tenant: invoked WITHOUT scope; fails inside Hibernate
  }
}
```

**After:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onInvoiceSent(InvoiceSentEvent event) {
  if (event.tenantId() == null) {
    log.warn("InvoiceSentEvent has null tenantId, dropping: invoice={}", event.entityId());
    return;
  }
  RequestScopes.runForTenant(event.tenantId(), event.orgId(), () -> handleInvoiceSent(event));
}
```

**Audit (pre-migration verification):** `InvoiceSentEvent` is published from `BillingRunGenerationService` and `InvoiceEmailService.send`. Both reachable only via authenticated request paths downstream of `TenantFilter`. Empirically every published event carries a non-null `tenantId`. The new explicit drop-and-warn is observably equivalent to the old WARN-and-fail-inside-Hibernate path. No test exercises the null-tenant branch.

Same pattern as PR #1's 6 fail-closed→fail-fast handler conversions; same audit class.

### C. 3-binding system actor → `runForTenantAsSystemActor` (2 sites)

```java
// Before:
ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .where(RequestScopes.ORG_ID, orgId)
    .where(RequestScopes.MEMBER_ID, SYSTEM_ACTOR_ID)
    .run(() -> doBackfill());

// After:
RequestScopes.runForTenantAsSystemActor(schema, orgId, SYSTEM_ACTOR_ID, () -> doBackfill());
```

Files:

- `customerbackend/service/RetainerPortalSyncService.java:370` (drop the inline NB-comment too)
- `customerbackend/service/TrustLedgerPortalSyncService.java:338` (drop the inline NB-comment too)

### D. Documented exemptions (no migration; encoded in ArchUnit rule)

| File | Site(s) | Rationale |
|---|---|---|
| `portal/CustomerAuthFilter.java` | line 80 | Servlet filter; multi-binding from JWT (CUSTOMER_ID + TENANT_ID + ORG_ID + conditional PORTAL_CONTACT_ID). The binding IS the boundary; no `runForTenant`-shaped sanctioned API exists yet. |
| `assistant/AssistantController.java` | line 50 | 5-binding capture-and-rebind to bridge servlet thread → virtual thread for SSE LLM streaming. Awaits ADR-204's deferred `withCurrentScopes()`. |
| `dev/DevPortalController.java` | lines 97, 160, 202, 206, 259, 263 | Dev-profile-gated test harness (`@Profile({"local","dev"})`). Mimics production CustomerAuthFilter behaviour; some sites are single-tenant `callForTenant`-shaped but the file is exempt as a class because it's non-production. |
| `integration/payment/MockPaymentController.java` | lines 117, 182 | Dev-profile-gated payment mock. Site at :117 is single-tenant `runForTenant`-shaped but the file is exempt as a class. Site at :182 is an early-return cross-tenant search (find-which-owns-this-invoice) — doesn't fit `forEachTenant`. |
| `acceptance/AcceptanceService.java` | line 736 | Cross-tenant discovery search — `for (mapping : findAll()) { ... if (found != null) return ... }`. Same shape as MockPaymentController:182. No sanctioned "tenant-discovery" API exists; this is a one-off-per-flow pattern that doesn't justify a generic abstraction yet. |

---

## ArchUnit rule

### Rule

```java
// backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingTest.java
// Adds to the existing class shipped by PR #1.

@ArchTest
static final ArchRule no_direct_tenant_scope_binding_outside_multitenancy =
    noClasses()
        .that().resideOutsideOfPackage("..multitenancy..")
        .and().areNotAssignableTo(CustomerAuthFilter.class)
        .and().areNotAssignableTo(AssistantController.class)
        .and().resideOutsideOfPackage("..dev..")
        .and().areNotAssignableTo(MockPaymentController.class)
        .and().areNotAssignableTo(AcceptanceService.class)
        .should().notCallMethodWhere(
            target(name("where"))
                .and(declaredIn(ScopedValue.class))
                .and(/* first arg is field reference RequestScopes.TENANT_ID */))
        .because("Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
              + "runForTenantAsSystemActor or TenantScopedRunner.forEachTenant. See ADR-T008. "
              + "Adding a new exemption requires explicit ADR-T008 amendment.");
```

### Verification

Per PR #1's `TenantScopeBindingTest` precedent: inject a violation in a test fixture (e.g., a temporary class outside the exemption set that does `ScopedValue.where(RequestScopes.TENANT_ID, "x").run(...)`); run the rule; confirm fail; revert; confirm pass. Captured as a separate `@Test` in the same test class.

### Conditional deferral (per user mandate "attempt the addition; if troublesome, defer")

The ArchUnit DSL for "method call whose first argument is a specific static field reference" is fiddly — the `JavaCall` / `JavaAccess` / `accessOf` API doesn't expose argument-value introspection cleanly. PR #1's existing `TenantScopeBindingTest` uses name-based method matching (`methods().that().haveName(X)`) which sidesteps this.

Time budget: **≤90 minutes** spent on the precise DSL. If still intractable at that point, fall back to:

1. **Fallback A (preferred):** broader rule banning ALL `ScopedValue.where(ScopedValue<?>, ?)` calls outside multitenancy + the same exemption set. Trade-off: also catches non-TENANT_ID ScopedValue bindings; verify no false positives in current code (the other ScopedValues — MEMBER_ID, ORG_ID, etc. — are bound only in `multitenancy/` filters today, so the broader rule should still pass on the migrated tree).
2. **Fallback B:** name-based check only (`callMethod(ScopedValue.class, "where", ...)`) and rely on the exemption set + code review for first-argument discrimination.
3. **Defer entirely (last resort):** drop the rule from PR #2; create TD-010 in `documentation/tech-debt.md` with trigger "ADR-204's `withCurrentScopes()` lands AND ArchUnit DSL approximates `JavaCall.withFirstArgument(field)` cleanly." Amend ADR-T008 to note the deferral. The migrations still ship; the regression guard is the only thing missing.

---

## ADR-T008 amendment

Edit `adr/ADR-T008-tenant-scoped-runner.md` after the migrations land:

- **"Surface 2 (PR #2)"** — replace forward-reference language with the actual final API: `runForTenantAsSystemActor` static method (3-binding) + `TenantScopedRunner.forEachTenant` Spring bean (per-tenant fan-out).
- **"Decision"** — soften "the only sanctioned way to bind tenant scope outside this class" to "the sanctioned APIs for the consolidatable patterns; legitimate boundary-binders are in the rule's exemption set and tracked as awaiting ADR-204."
- **"Companion regression guard (PR #2)"** — replace "cannot ship in PR #1 because all ~15 direct-binding sites would build-break" with the actual outcome: shipped in PR #2 with the exemption catalogue. Reference the catalogue table.
- **"Alternatives Considered"** — record the API-shape decision (Option A: explicit `runForTenantAsSystemActor` over Option B map-of-bindings or Option C capture-and-rebind). Note that Option C is what ADR-204 will eventually provide for the boundary-binders.
- **"Follow-ups"** — name the deferred-rule cleanup explicitly: "When ADR-204 lands a sanctioned `withCurrentScopes()` API, migrate `CustomerAuthFilter:80`, `AssistantController:50`, `DevPortalController` (× 6), `MockPaymentController` (× 2), `AcceptanceService:736` to it (the cross-tenant search may need its own future API), and remove the corresponding entries from `TenantScopeBindingTest`'s exemption set."
- If the rule was deferred under §ArchUnit/Conditional deferral, replace the regression-guard subsection with a TD-010 reference and the deferral rationale.

---

## Risks & mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| ArchUnit DSL doesn't support precise "first arg = field ref" matching | Medium | Tiered fallbacks per §ArchUnit; budgeted ≤90 min |
| `InvoiceEmailEventListener` null-tenant audit incomplete | Low | Same audit class as PR #1's 6 fail-closed handlers; pre-migration audit verifies every `InvoiceSentEvent` publisher is downstream of `TenantFilter` |
| `TenantScopedRunner.forEachTenant` exception isolation differs subtly from inline patterns | Low | Each migrating site's existing try-catch is lifted into the bean; verified by `TenantScopedRunnerTest` (failing-action test asserts continuation + ERROR log) |
| Removing `OrgSchemaMappingRepository` injection where the bean replaces it might break unrelated code paths in same class | Low | Per-file verification; only remove if no other use remains |
| Spotless reformat on `mvn verify` after Java edits | Trivial | Run `./mvnw -q spotless:apply` before each commit (per PR #1 anti-cheat note) |
| Surefire `@Nested` test count drift confusing the baseline check | Trivial | Use `<testcase>`-element counting per `feedback_surefire_nested_count.md` memory; baseline is 5038 tests / 0F / 0E / 26 skip on `cc911f1e0` |

---

## Out of scope

- TD-009 controller refactoring — `InternalAuditController`, `PortalBrandingController`, `MockPaymentController` directly inject repositories. Three of those controllers are migration targets (or exempt) in this PR; do not refactor them while editing. Touch-it-don't-fix-it.
- `TrustLedgerPortalSyncService.backfillForTenant` cross-tenant guard asymmetry. Separate small security PR.
- Regression pack audit (gated to user).
- Sanctioned API for cross-tenant discovery searches (`AcceptanceService:736`, `MockPaymentController:182`). Future PR if a third site emerges.
- ADR-204's `withCurrentScopes()` implementation.

---

## Verification & merge bar

Per `CLAUDE.md` Quality Gates:

- Backend: `./mvnw verify` clean. Test count must equal `5038 ± 5` (within parameterised-test slop) using `<testcase>`-element counting.
- ArchUnit rule (or fallback / deferral note) verified by inject-violation pattern.
- ADR-T008 amendment committed in same PR as the rule.
- Pre-merge gate hook satisfied; no `--admin` / `--no-verify` bypass.
- CodeRabbit review obtained; comments addressed before merge.
- Merge requires explicit user approval ("merge").
