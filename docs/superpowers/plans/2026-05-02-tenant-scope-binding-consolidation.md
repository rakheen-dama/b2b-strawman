# Tenant Scope Binding Consolidation Implementation Plan

> **For agentic workers:** This plan executes inline in the worktree at `.worktrees/tenant-scope-binding-consolidation` on branch `feat/tenant-scope-binding-consolidation`. Steps use checkbox (`- [ ]`) syntax for tracking. See spec at `docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md`.

**Goal:** Migrate 36 direct `ScopedValue.where(RequestScopes.TENANT_ID, ...)` sites outside `..multitenancy..` to canonical APIs (`RequestScopes.runForTenant`, `callForTenant`, new `runForTenantWithMember`, new `TenantScopedRunner.forEachTenant`); ship companion ArchUnit rule with structured exemption set; amend ADR-T008.

**Architecture:** Two new API surfaces added first (TDD), then mechanical per-file migrations grouped by pattern, then ArchUnit rule with conditional fallback (precise DSL → broader rule → name-only → defer to TD-010), then ADR amendment.

**Tech Stack:** Java 25, Spring Boot 4.0.2, Maven 3.x, JUnit 5, ArchUnit 1.4.2, Mockito, Spotless, Hibernate 7

---

## Task 0: Verify worktree baseline

**Files:** none

- [ ] **Step 1: Confirm worktree wd + HEAD**

  Run from worktree root:
  ```bash
  pwd && git log --oneline -1 && git status
  ```
  Expected: `.worktrees/tenant-scope-binding-consolidation`, HEAD at `cc911f1e0`, clean tree.

- [ ] **Step 2: Confirm baseline test count carries from main**

  ```bash
  # Run from repo root (use $(git rev-parse --show-toplevel) if you're elsewhere).
  find backend/target/surefire-reports -maxdepth 1 -name "TEST-*.xml" -exec grep -c "<testcase " {} \; | awk '{s+=$1} END {print s}'
  ```
  Expected: 5038 (carries from main verify; same SHA, same code).

---

## Task 1: Add `RequestScopes.runForTenantWithMember` (TDD)

**Files:**
- Test: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopesTest.java` (add 3 test methods)
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` (add 1 method, ~25 lines)

- [ ] **Step 1: Add failing tests to RequestScopesTest**

  Append these test methods to `RequestScopesTest`:

  ```java
  @Test
  void runForTenantWithMember_bindsTenantOrgAndMember() {
    UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    AtomicReference<String> tenantInside = new AtomicReference<>();
    AtomicReference<String> orgInside = new AtomicReference<>();
    AtomicReference<UUID> memberInside = new AtomicReference<>();

    RequestScopes.runForTenantWithMember("tenant_abc", "org_xyz", actorId, () -> {
      tenantInside.set(RequestScopes.TENANT_ID.get());
      orgInside.set(RequestScopes.ORG_ID.get());
      memberInside.set(RequestScopes.MEMBER_ID.get());
    });

    assertThat(tenantInside.get()).isEqualTo("tenant_abc");
    assertThat(orgInside.get()).isEqualTo("org_xyz");
    assertThat(memberInside.get()).isEqualTo(actorId);
  }

  @Test
  void runForTenantWithMember_nullTenant_throwsIllegalArgumentException() {
    UUID actorId = UUID.randomUUID();
    assertThatThrownBy(
            () -> RequestScopes.runForTenantWithMember(null, "org_xyz", actorId, () -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runForTenantWithMember_blankTenant_throwsIllegalArgumentException() {
    UUID actorId = UUID.randomUUID();
    assertThatThrownBy(
            () -> RequestScopes.runForTenantWithMember("  ", "org_xyz", actorId, () -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runForTenantWithMember_nullActor_throwsNullPointerException() {
    assertThatThrownBy(
            () -> RequestScopes.runForTenantWithMember("tenant_abc", "org_xyz", null, () -> {}))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void runForTenantWithMember_nullOrgId_skipsOrgBinding() {
    UUID actorId = UUID.randomUUID();
    AtomicBoolean orgBound = new AtomicBoolean(true);
    RequestScopes.runForTenantWithMember("tenant_abc", null, actorId, () -> {
      orgBound.set(RequestScopes.ORG_ID.isBound());
    });
    assertThat(orgBound.get()).isFalse();
  }
  ```

  Add necessary imports if missing (`AtomicReference`, `AtomicBoolean`).

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  cd backend && ./mvnw -q -pl . test -Dtest=RequestScopesTest 2>&1 | tail -30
  ```
  Expected: 5 new test methods FAIL (`cannot find symbol: method runForTenantWithMember`).

- [ ] **Step 3: Implement runForTenantWithMember in RequestScopes.java**

  Add after the existing `callForTenant` method:

  ```java
    /**
     * Variant of {@link #runForTenant} that additionally binds {@link #MEMBER_ID} to a system-actor
     * sentinel UUID. Use only from internal admin-authenticated callers (currently the two portal
     * read-model backfill helpers) where downstream services read {@link #requireMemberId()} for
     * audit attribution and would throw without a bound member.
     *
     * <p>Tenant-isolation guard remains caller responsibility: the caller MUST verify the supplied
     * tenantId/orgId is authorised for the current request scope before invoking this method, since
     * this method binds whatever values it is handed without an authorization check.
     *
     * @throws IllegalArgumentException if tenantId is null or blank.
     * @throws NullPointerException if action or actorId is null.
     */
    public static void runForTenantWithMember(
        String tenantId, @Nullable String orgId, UUID actorId, Runnable action) {
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(actorId, "actorId");
      requireValidTenantId(tenantId);
      bindTenantScope(tenantId, orgId).where(MEMBER_ID, actorId).run(action);
    }
  ```

- [ ] **Step 4: Re-run tests, confirm pass**

  ```bash
  cd backend && ./mvnw -q test -Dtest=RequestScopesTest 2>&1 | tail -20
  ```
  Expected: all `RequestScopesTest` tests pass.

- [ ] **Step 5: Spotless + commit**

  ```bash
  cd backend && ./mvnw -q spotless:apply
  cd .. && git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopesTest.java
  git commit -m "feat(multitenancy): add RequestScopes.runForTenantWithMember for 3-binding system-actor pattern

Adds the API needed by the 2 portal read-model backfill helpers
(RetainerPortalSyncService, TrustLedgerPortalSyncService) which bind
TENANT_ID + ORG_ID + MEMBER_ID=SYSTEM_ACTOR_ID — a 3-binding pattern
that PR #1's runForTenant doesn't support. Migration of the 2 callers
follows in subsequent commits.

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  ```

---

## Task 2: Add `TenantScopedRunner` Spring bean (TDD)

**Files:**
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java`
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunnerTest.java`

- [ ] **Step 1: Write failing test**

  Create `TenantScopedRunnerTest.java` with this content:

  ```java
  package io.b2mash.b2b.b2bstrawman.multitenancy;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.Mockito.mock;
  import static org.mockito.Mockito.when;

  import io.b2mash.b2b.b2bstrawman.provisioning.OrgSchemaMapping;
  import io.b2mash.b2b.b2bstrawman.provisioning.OrgSchemaMappingRepository;
  import java.util.List;
  import java.util.concurrent.atomic.AtomicInteger;
  import org.junit.jupiter.api.Test;

  class TenantScopedRunnerTest {

    private static OrgSchemaMapping mapping(String schema, String orgId) {
      OrgSchemaMapping m = mock(OrgSchemaMapping.class);
      when(m.getSchemaName()).thenReturn(schema);
      when(m.getClerkOrgId()).thenReturn(orgId);
      return m;
    }

    @Test
    void forEachTenant_invokesActionWithTenantAndOrgBound_perMapping() {
      OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
      when(repo.findAll()).thenReturn(List.of(
          mapping("tenant_aaa", "org_one"),
          mapping("tenant_bbb", "org_two")));

      TenantScopedRunner runner = new TenantScopedRunner(repo);
      AtomicInteger calls = new AtomicInteger();

      int succeeded = runner.forEachTenant((tenantId, orgId) -> {
        calls.incrementAndGet();
        // Inside the action, scope is bound:
        assertThat(RequestScopes.TENANT_ID.get()).isEqualTo(tenantId);
        assertThat(RequestScopes.ORG_ID.get()).isEqualTo(orgId);
      });

      assertThat(calls.get()).isEqualTo(2);
      assertThat(succeeded).isEqualTo(2);
    }

    @Test
    void forEachTenant_perTenantExceptionDoesNotAbortIteration() {
      OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
      when(repo.findAll()).thenReturn(List.of(
          mapping("tenant_aaa", "org_one"),
          mapping("tenant_bbb", "org_two"),
          mapping("tenant_ccc", "org_three")));

      TenantScopedRunner runner = new TenantScopedRunner(repo);
      AtomicInteger calls = new AtomicInteger();

      int succeeded = runner.forEachTenant((tenantId, orgId) -> {
        calls.incrementAndGet();
        if ("tenant_bbb".equals(tenantId)) {
          throw new RuntimeException("simulated failure");
        }
      });

      assertThat(calls.get()).isEqualTo(3);   // all three were attempted
      assertThat(succeeded).isEqualTo(2);     // two succeeded
    }

    @Test
    void forEachTenant_emptyMappingList_returnsZero() {
      OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
      when(repo.findAll()).thenReturn(List.of());

      TenantScopedRunner runner = new TenantScopedRunner(repo);
      AtomicInteger calls = new AtomicInteger();

      int succeeded = runner.forEachTenant((t, o) -> calls.incrementAndGet());

      assertThat(calls.get()).isEqualTo(0);
      assertThat(succeeded).isEqualTo(0);
    }

    @Test
    void forEachTenant_nullAction_throwsNullPointerException() {
      OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
      TenantScopedRunner runner = new TenantScopedRunner(repo);
      org.junit.jupiter.api.Assertions.assertThrows(
          NullPointerException.class, () -> runner.forEachTenant(null));
    }
  }
  ```

- [ ] **Step 2: Run test to confirm it fails (class doesn't exist)**

  ```bash
  cd backend && ./mvnw -q test -Dtest=TenantScopedRunnerTest 2>&1 | tail -10
  ```
  Expected: compilation error (`TenantScopedRunner` not found).

- [ ] **Step 3: Implement TenantScopedRunner**

  Create `TenantScopedRunner.java`:

  ```java
  package io.b2mash.b2b.b2bstrawman.multitenancy;

  import io.b2mash.b2b.b2bstrawman.provisioning.OrgSchemaMappingRepository;
  import java.util.Objects;
  import java.util.function.BiConsumer;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.stereotype.Component;

  /**
   * Iterates every active tenant schema and runs an action once per tenant with TENANT_ID +
   * ORG_ID bound on a fresh ScopedValue carrier. The canonical replacement for the inline
   * {@code for (mapping : repo.findAll()) { ScopedValue.where(...).run(...) }} pattern that
   * appeared in 13+ scheduled jobs prior to PR #2 (ADR-T008 Surface 2).
   *
   * <p>Per-tenant exception isolation: failures inside {@code action} for one tenant are caught,
   * logged at ERROR with {@code tenantId} / {@code orgId} in the message, and do NOT abort the
   * iteration. Returns the count of tenants for which {@code action} completed without throwing.
   */
  @Component
  public class TenantScopedRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantScopedRunner.class);

    private final OrgSchemaMappingRepository mappingRepository;

    public TenantScopedRunner(OrgSchemaMappingRepository mappingRepository) {
      this.mappingRepository = mappingRepository;
    }

    /**
     * Run {@code action} once per tenant schema with {@link RequestScopes#TENANT_ID} and
     * {@link RequestScopes#ORG_ID} bound. The action receives {@code (tenantId, orgId)}.
     *
     * @return the count of tenants for which {@code action} completed without throwing.
     * @throws NullPointerException if {@code action} is null.
     */
    public int forEachTenant(BiConsumer<String, String> action) {
      Objects.requireNonNull(action, "action");
      int succeeded = 0;
      for (var mapping : mappingRepository.findAll()) {
        String tenantId = mapping.getSchemaName();
        String orgId = mapping.getClerkOrgId();
        try {
          RequestScopes.runForTenant(tenantId, orgId, () -> action.accept(tenantId, orgId));
          succeeded++;
        } catch (Exception e) {
          log.error(
              "Per-tenant action failed: tenant={} org={}: {}",
              tenantId,
              orgId,
              e.getMessage(),
              e);
        }
      }
      return succeeded;
    }
  }
  ```

- [ ] **Step 4: Re-run test, confirm pass**

  ```bash
  cd backend && ./mvnw -q test -Dtest=TenantScopedRunnerTest 2>&1 | tail -15
  ```
  Expected: all 4 tests pass.

- [ ] **Step 5: Spotless + commit**

  ```bash
  cd backend && ./mvnw -q spotless:apply
  cd .. && git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunnerTest.java
  git commit -m "feat(multitenancy): add TenantScopedRunner.forEachTenant Spring bean

Centralises the per-tenant fan-out pattern used by 13 scheduled jobs:
iterate OrgSchemaMappingRepository.findAll(), bind TENANT_ID + ORG_ID
on a fresh ScopedValue carrier, invoke per-tenant action, isolate
exceptions at the per-tenant boundary (ERROR log, continue iteration).
Returns count of successful invocations.

Migration of the 13 existing callers follows in subsequent commits.

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  ```

---

## Task 3: Migrate 13 fan-out sites to `TenantScopedRunner.forEachTenant`

**Files:** see per-file checkboxes below.

For each file, the migration shape is:

```java
// REMOVE: OrgSchemaMappingRepository injection if no other use in same class
// REPLACE inline for-loop:
for (var mapping : orgSchemaMappingRepository.findAll()) {
  try {
    ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
        .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
        .run(() -> doWork(mapping));   // or doWork(schema, orgId)
  } catch (Exception e) {
    log.error("Failed for tenant {}: {}", mapping.getSchemaName(), e.getMessage(), e);
  }
}

// WITH:
tenantScopedRunner.forEachTenant((tenantId, orgId) -> doWork(tenantId, orgId));
```

Per-file procedure for each:
- Read current pattern (verify it matches the shape; if it differs significantly, note divergence).
- Add `TenantScopedRunner` constructor parameter; initialize field.
- Remove `OrgSchemaMappingRepository` parameter if no other use remains in the class (grep within file).
- Replace the for-loop with `tenantScopedRunner.forEachTenant(...)`.
- Run targeted test for the file's package.

Files (each is a sub-checkbox):

- [ ] `acceptance/AcceptanceExpiryProcessor.java:37`
- [ ] `automation/AutomationScheduler.java:64`
- [ ] `automation/FieldDateScannerJob.java:69`
- [ ] `billing/SubscriptionExpiryJob.java:203`
- [ ] `compliance/DormancyScheduledJob.java:48`
- [ ] `informationrequest/RequestReminderScheduler.java:64`
- [ ] `portal/MagicLinkCleanupService.java:45`
- [ ] `portal/notification/PortalDigestScheduler.java:151`
- [ ] `proposal/ProposalExpiryProcessor.java:66`
- [ ] `schedule/RecurringScheduleExecutor.java:41`
- [ ] `schedule/TimeReminderScheduler.java:70`
- [ ] `template/LegacyContentImportRunner.java:85`
- [ ] `verticals/legal/courtcalendar/CourtDateReminderJob.java:63`

After all 13 files migrated:

- [ ] **Run targeted tests for the affected packages**

  ```bash
  cd backend && ./mvnw -q test -Dtest='*ExpiryProcessor*,*Scheduler*,*Job*,*Cleanup*,*Runner*,*Executor*' 2>&1 | tail -20
  ```

- [ ] **Spotless + single commit**

  ```bash
  cd backend && ./mvnw -q spotless:apply
  cd .. && git add -u
  git commit -m "refactor(multitenancy): migrate 13 fan-out scheduled jobs to TenantScopedRunner.forEachTenant

Replaces inline for-loop + ScopedValue.where + per-tenant try-catch with
TenantScopedRunner.forEachTenant injection. Per-tenant exception isolation
semantics preserved (ERROR log, continue iteration). Removes 13 inline
copies of the same pattern.

Files migrated:
- AcceptanceExpiryProcessor, AutomationScheduler, FieldDateScannerJob,
  SubscriptionExpiryJob, DormancyScheduledJob, RequestReminderScheduler,
  MagicLinkCleanupService, PortalDigestScheduler, ProposalExpiryProcessor,
  RecurringScheduleExecutor, TimeReminderScheduler, LegacyContentImportRunner,
  CourtDateReminderJob

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  ```

---

## Task 4: Migrate 2 backfill helpers to `runForTenantWithMember`

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/RetainerPortalSyncService.java:364-373` (replace 6 lines with 1; drop NB-comment block)
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java:332-341` (same)

- [ ] **Step 1: Edit RetainerPortalSyncService.java**

  Replace lines 364-373 (the NB-comment block + `ScopedValue.where(...).where(...).where(MEMBER_ID, SYSTEM_ACTOR_ID).run(...)`) with:

  ```java
      RequestScopes.runForTenantWithMember(
          schema,
          orgId,
          SYSTEM_ACTOR_ID,
          () -> {
            // Delegate to syncSummary so backfill behaves identically to the event-driven path:
            // agreements without an OPEN period get a terminal snapshot (via toTerminalSummaryView)
            // rather than being silently skipped. syncSummary manages its own portal transaction
            // per row via portalTxTemplate, so no outer tx wrapper is needed here.
            for (var agreement : agreementRepository.findAll()) {
              syncSummary(agreement.getId());
              counts[0]++;
            }
          });
  ```

  (Keep the inner business-logic comment; drop the NB-comment about PR #2 deferral.)

- [ ] **Step 2: Edit TrustLedgerPortalSyncService.java**

  Replace lines 332-341 (NB-comment block + 3-binding chain) with:

  ```java
      RequestScopes.runForTenantWithMember(
          schema,
          orgId,
          SYSTEM_ACTOR_ID,
          () -> {
            // Load every trust transaction once, filter portal-eligible + portal-status inline
            // and bucket by matter. (Previously this looped over [RECORDED, APPROVED] with
            // a findAll() call inside — O(2N) heap load + silent duplicates.)
            for (var txn : trustTransactionRepository.findAll()) {
              if (!PORTAL_STATUSES.contains(txn.getStatus())) {
                continue;
              }
              if (!hasPortalScope(txn) || !isPortalEligible(txn)) {
                continue;
              }
              UUID matterId = txn.getProjectId();
              rollups
                  .computeIfAbsent(matterId, id -> new MatterRollup(id, txn.getCustomerId()))
                  .add(txn);
            }
            // Snapshot the tenant's customer id set inside the same scope binding — needed for
            // the wipe-and-rewrite drift repair below.
            for (var customer : customerRepository.findAll()) {
              tenantCustomerIds.add(customer.getId());
            }
          });
  ```

  (Keep the inner business-logic comments; drop the NB-comment about PR #2 deferral.)

- [ ] **Step 3: Verify imports — `RequestScopes` already imported, `ScopedValue` import may be removable per file**

  ```bash
  grep -n "import.*ScopedValue\|import.*RequestScopes" backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/RetainerPortalSyncService.java backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java
  ```
  Remove `import java.lang.ScopedValue;` if no other `ScopedValue` reference remains in the file (some files use `ScopedValue.isBound()` etc. — check before removing).

- [ ] **Step 4: Run targeted tests**

  ```bash
  cd backend && ./mvnw -q test -Dtest='*RetainerPortalSync*,*TrustLedgerPortalSync*' 2>&1 | tail -20
  ```
  Expected: all pass.

- [ ] **Step 5: Spotless + commit**

  ```bash
  cd backend && ./mvnw -q spotless:apply
  cd .. && git add -u
  git commit -m "refactor(multitenancy): migrate 2 backfill helpers to runForTenantWithMember

RetainerPortalSyncService and TrustLedgerPortalSyncService backfillForTenant
methods now use the canonical 3-binding API (TENANT + ORG + MEMBER=SYSTEM_ACTOR_ID)
instead of inline ScopedValue.where(...).where(...).where(MEMBER_ID, SYSTEM_ACTOR_ID)
chains. Inline NB-comments referencing the PR #2 deferral are removed.

The cross-tenant guard in RetainerPortalSyncService.backfillForTenant (require
ORG_ID bound + match) is preserved unchanged. The asymmetric absence of the same
guard in TrustLedgerPortalSyncService is documented as a separate security-class
follow-up (see spec §Out of scope).

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  ```

---

## Task 5: Migrate ~21 single-tenant sites to `runForTenant` / `callForTenant`

**Files:** see per-file checkboxes below. Migration shape:

```java
// Before (run flavour):
ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .where(RequestScopes.ORG_ID, orgId)
    .run(() -> doWork());

// After:
RequestScopes.runForTenant(schema, orgId, () -> doWork());

// Before (call flavour):
T result = ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .where(RequestScopes.ORG_ID, orgId)
    .call(() -> compute());

// After:
T result = RequestScopes.callForTenant(schema, orgId, () -> compute());
```

For 1-binding sites (TENANT only, no ORG), pass `null` for the orgId arg.

Per-file checkboxes (line numbers from spec; verify before editing):

- [ ] `acceptance/AcceptanceService.java` — lines 770, 791, 812, 860, 889 (5 sites; mix of run/call)
- [ ] `audit/InternalAuditController.java` — lines 55, 68 (2 sites; both `callForTenant`)
- [ ] `customerbackend/service/PortalResyncService.java:72` (pre-bound carrier — convert to inline `runForTenant`)
- [ ] `integration/email/EmailWebhookService.java:157` (1-binding `runForTenant`, orgId=null)
- [ ] `integration/email/UnsubscribeService.java:97` (1-binding `runForTenant`, orgId=null)
- [ ] `integration/payment/PaymentWebhookController.java:75` (conditional org binding — use null when orgId is null)
- [ ] `invoice/InvoiceEmailEventListener.java:38` (**behaviour-change footnote — see below**)
- [ ] `member/MemberSyncService.java` — lines 80, 154, 189 (3 sites)
- [ ] `packs/PackInstallService.java:124` (1-binding `runForTenant`, orgId=null)
- [ ] `portal/CustomerAuthFilter.java:89` (inner read; outer line 80 STAYS exempt)
- [ ] `portal/PortalAuthService.java` — lines 62, 123, 133 (3 sites; all 1-binding `callForTenant`, orgId=null)
- [ ] `portal/PortalBrandingController.java:68` (1-binding `callForTenant`, orgId=null)
- [ ] `provisioning/PackReconciliationRunner.java:159` (1-binding `callForTenant`, orgId=null)

### InvoiceEmailEventListener behaviour change

Replace the entire `onInvoiceSent` method:

```java
// REMOVE:
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onInvoiceSent(InvoiceSentEvent event) {
  if (event.tenantId() != null) {
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, event.tenantId());
    if (event.orgId() != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, event.orgId());
    }
    carrier.run(() -> handleInvoiceSent(event));
  } else {
    handleInvoiceSent(event);
  }
}

// REPLACE WITH:
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onInvoiceSent(InvoiceSentEvent event) {
  if (event.tenantId() == null) {
    log.warn("InvoiceSentEvent has null tenantId, dropping: invoice={}", event.entityId());
    return;
  }
  RequestScopes.runForTenant(event.tenantId(), event.orgId(), () -> handleInvoiceSent(event));
}
```

This converts a silent fall-through-to-unbound-scope (which then fails inside Hibernate, swallowed by the surrounding try-catch as a WARN) into an explicit drop-and-warn at the entry point. Same audit class as PR #1's 6 fail-closed→fail-fast handler conversions; pre-migration audit confirms every `InvoiceSentEvent` publisher is downstream of `TenantFilter`, so non-null tenantId is the empirical reality.

After all single-tenant sites migrated:

- [ ] **Run targeted tests across affected packages**

  ```bash
  cd backend && ./mvnw -q test -Dtest='*Acceptance*,*Audit*,*PortalResync*,*EmailWebhook*,*Unsubscribe*,*PaymentWebhook*,*InvoiceEmail*,*MemberSync*,*PackInstall*,*CustomerAuth*,*PortalAuth*,*PortalBranding*,*PackReconciliation*' 2>&1 | tail -25
  ```

- [ ] **Spotless + single commit**

  ```bash
  cd backend && ./mvnw -q spotless:apply
  cd .. && git add -u
  git commit -m "refactor(multitenancy): migrate 21 single-tenant sites to RequestScopes.runForTenant/callForTenant

Replaces ScopedValue.where(TENANT_ID,...).run/call patterns in 14 files with
the canonical RequestScopes.runForTenant / callForTenant API. Includes one
behaviour change in InvoiceEmailEventListener (null-tenant path becomes an
explicit log+drop instead of silent fall-through-to-Hibernate-failure;
pre-migration audit confirms no caller publishes a null-tenant event).

Files migrated:
- AcceptanceService (5 sites), MemberSyncService (3), PortalAuthService (3),
  InternalAuditController (2), PortalResyncService, PackInstallService,
  CustomerAuthFilter (inner read; line 80 outer multi-binding stays exempt),
  InvoiceEmailEventListener, PaymentWebhookController, EmailWebhookService,
  UnsubscribeService, PortalBrandingController, PackReconciliationRunner

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  ```

---

## Task 6: Add ArchUnit rule with exemption catalogue

**Files:**
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingTest.java`

- [ ] **Step 1: Read existing TenantScopeBindingTest to understand the precedent**

  ```bash
  cat backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingTest.java
  ```
  Note the existing rule's DSL pattern (PR #1 used name-based matching).

- [ ] **Step 2: Try the precise DSL** (banning `ScopedValue.where(...)` whose first arg is a reference to `RequestScopes.TENANT_ID` field)

  Add a new `@ArchTest` rule alongside the existing one:

  ```java
  @ArchTest
  static final ArchRule no_direct_tenant_scope_binding_outside_multitenancy =
      noClasses()
          .that().resideOutsideOfPackage("..multitenancy..")
          .and().areNotAssignableTo(CustomerAuthFilter.class)
          .and().areNotAssignableTo(AssistantController.class)
          .and().resideOutsideOfPackage("..dev..")
          .and().areNotAssignableTo(MockPaymentController.class)
          .and().areNotAssignableTo(AcceptanceService.class)
          .should(new ArchCondition<JavaClass>("not call ScopedValue.where with RequestScopes.TENANT_ID as first argument") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
              clazz.getAccessesFromSelf().stream()
                  .filter(access -> access instanceof JavaMethodCall)
                  .map(access -> (JavaMethodCall) access)
                  .filter(call -> call.getTarget().getName().equals("where"))
                  .filter(call -> call.getTarget().getOwner().isAssignableTo(ScopedValue.class))
                  .filter(call -> firstArgIsTenantIdField(call))
                  .forEach(call -> events.add(SimpleConditionEvent.violated(
                      call,
                      "Class " + clazz.getName() + " calls ScopedValue.where(RequestScopes.TENANT_ID, ...) at "
                          + call.getSourceCodeLocation())));
            }

            private boolean firstArgIsTenantIdField(JavaMethodCall call) {
              // ArchUnit JavaCall doesn't expose argument values directly — fall through to
              // accessing field-access events from the same source line as a heuristic
              return clazzAccessesTenantIdFieldAtLine(call.getOriginOwner(), call.getLineNumber());
            }
            // ... helper method to check field accesses on the same source line
          })
          .because("Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
                + "runForTenantWithMember or TenantScopedRunner.forEachTenant. See ADR-T008. "
                + "Adding a new exemption requires explicit ADR-T008 amendment.");
  ```

  **Time-box: 90 minutes maximum.** If the field-reference matching is intractable, drop to Step 3 (Fallback A).

- [ ] **Step 3 (Fallback A — broader rule): If precise DSL is intractable, simplify to "no `ScopedValue.where(...)` calls at all outside multitenancy + exemption set"**

  ```java
  @ArchTest
  static final ArchRule no_direct_scopedvalue_binding_outside_multitenancy =
      noClasses()
          .that().resideOutsideOfPackage("..multitenancy..")
          .and().areNotAssignableTo(CustomerAuthFilter.class)
          .and().areNotAssignableTo(AssistantController.class)
          .and().resideOutsideOfPackage("..dev..")
          .and().areNotAssignableTo(MockPaymentController.class)
          .and().areNotAssignableTo(AcceptanceService.class)
          .should().notCallMethod(ScopedValue.class, "where", ScopedValue.class, Object.class)
          .because("Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
                + "runForTenantWithMember or TenantScopedRunner.forEachTenant. See ADR-T008. "
                + "(Fallback A: rule bans all ScopedValue.where outside multitenancy; "
                + "no current code binds non-TENANT_ID ScopedValues outside multitenancy/, so "
                + "the broader rule has identical semantics today. Tightening to TENANT_ID-specific "
                + "is tracked as TD-011 / spec §ArchUnit Conditional deferral.)");
  ```

- [ ] **Step 4: Verify rule fires on injected violation**

  Add a temporary test fixture class outside `..multitenancy..`:

  ```java
  // In a test-only package, e.g., backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/_violations/InjectedViolation.java
  package io.b2mash.b2b.b2bstrawman.architecture._violations;

  import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;

  public class InjectedViolation {
    public void violate() {
      ScopedValue.where(RequestScopes.TENANT_ID, "tenant_x").run(() -> {});
    }
  }
  ```

  Run the rule: `./mvnw -q test -Dtest=TenantScopeBindingTest 2>&1 | tail -10` — expected FAIL with the injected class named.

  Delete the fixture file. Re-run: expected PASS.

- [ ] **Step 5: If both Step 2 (precise DSL) and Step 3 (broader fallback) fail, skip to Step 6 (defer)**

- [ ] **Step 6 (Fallback C — defer): Add TD-010 to tech-debt.md and skip the rule entirely**

  Append to `documentation/tech-debt.md`:

  ```markdown
  ### TD-010: ArchUnit rule banning direct ScopedValue.where(TENANT_ID, ...) outside multitenancy — DEFERRED

  **Introduced**: 2026-05-02 PR #2 (tenant scope binding consolidation).
  **Severity**: Low — the regression guard is the only thing missing; all migrations are complete.

  **Problem**: PR #2 migrated ~36 direct `ScopedValue.where(RequestScopes.TENANT_ID, ...)` sites to canonical APIs but couldn't ship the companion ArchUnit rule because the ArchUnit DSL on JDK 25 / version 1.4.2 didn't support either the precise "first-argument is field reference" matching or the simpler broader-rule fallback within the time budget.

  **Why acceptable now**: Migration is complete; the only thing missing is the regression guard. The exemption set documenting which patterns can't migrate yet (CustomerAuthFilter, AssistantController, dev controllers, MockPaymentController, AcceptanceService cross-tenant search) is captured in ADR-T008.

  **Fix when needed**: Re-attempt with newer ArchUnit version, or with custom JDK-25-native classfile-walking approach. Trigger: ADR-204's `withCurrentScopes()` lands AND ArchUnit (or a maintained alternative) approximates `JavaCall.withFirstArgument(field)` cleanly.
  ```

- [ ] **Step 7: Spotless + commit (in whichever branch you ended up in)**

  ```bash
  cd backend && ./mvnw -q spotless:apply
  cd .. && git add -u
  # Commit message depends on outcome:
  # - If precise rule shipped:
  git commit -m "test(multitenancy): add ArchUnit rule banning direct ScopedValue.where(TENANT_ID) outside multitenancy

The companion regression guard for ADR-T008 Surface 2 (PR #2). Bans direct
ScopedValue.where(RequestScopes.TENANT_ID, ...) calls outside the
..multitenancy.. package. Exemption set: CustomerAuthFilter (servlet
filter boundary), AssistantController (5-binding capture-rebind for SSE
virtual thread, awaits ADR-204), DevPortalController (dev-profile gated),
MockPaymentController (dev mock + cross-tenant search), AcceptanceService
(cross-tenant token search).

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  # - If Fallback A: \"...add ArchUnit rule banning all ScopedValue.where outside multitenancy (Fallback A)...\"
  # - If deferred: commit just the TD-010 entry and a note in the spec.
  ```

---

## Task 7: Amend ADR-T008

**Files:**
- Modify: `adr/ADR-T008-tenant-scoped-runner.md`

- [ ] **Step 1: Read current ADR-T008 to identify edit anchors**

  ```bash
  cat adr/ADR-T008-tenant-scoped-runner.md
  ```

- [ ] **Step 2: Apply edits per spec §ADR-T008 amendment**

  Make these specific edits:

  1. **"Surface 2 (PR #2)"** subsection: replace forward-reference language with the actual final API:
     - `runForTenantWithMember(String tenantId, @Nullable String orgId, UUID actorId, Runnable action)` — full Javadoc summary.
     - `TenantScopedRunner.forEachTenant(BiConsumer<String, String>)` — Spring bean, exception isolation, success-count return.
  2. **"Decision"** opening paragraph: soften "the only sanctioned way to bind tenant scope outside this class" to "the sanctioned APIs for the consolidatable patterns; legitimate boundary-binders (servlet filters, capture-rebind controllers, dev tooling, cross-tenant search) are in the rule's exemption set and tracked as awaiting ADR-204."
  3. **"Companion regression guard (PR #2)"** subsection: replace the forward-looking "cannot ship in PR #1 because all 15+ direct-binding sites would build-break" with the actual outcome — either:
     - Precise rule shipped: describe the rule + reference the exemption catalogue table.
     - Fallback A shipped: describe the broader rule + note the precise-rule trade-off.
     - Deferred: reference TD-010.
  4. **"Alternatives Considered"**: append a new entry recording the API-shape decision (Option A: explicit `runForTenantWithMember` over Option B map-of-bindings or Option C capture-and-rebind).
  5. **"Follow-ups"**: add explicit follow-up: "When ADR-204 lands a sanctioned `withCurrentScopes()` API, migrate `CustomerAuthFilter:80`, `AssistantController:50`, `DevPortalController` (× 6 sites), `MockPaymentController` (× 2 sites), `AcceptanceService:736` (cross-tenant search may need its own future API), and remove the corresponding entries from `TenantScopeBindingTest`'s exemption set."

  Add the exemption catalogue table directly in the ADR (mirror spec §D).

- [ ] **Step 3: Commit**

  ```bash
  git add adr/ADR-T008-tenant-scoped-runner.md
  git commit -m "docs(adr-T008): amend with PR #2's final API + exemption catalogue + ADR-204 dependency

Records the actual outcome of Surface 2 (PR #2): runForTenantWithMember
+ TenantScopedRunner.forEachTenant. Documents the exemption catalogue for
the boundary-binders (filters, capture-rebind controllers, dev tooling)
that cannot use the canonical static APIs and await ADR-204's
withCurrentScopes(). Records the API-shape decision (Option A explicit
method over Map-of-bindings or capture-and-rebind variants).

Spec: docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md"
  ```

---

## Task 8: Final verify, push, open PR

**Files:**
- Modify: `.claude/markers/verify-backend.json`

- [ ] **Step 1: Run full backend verify**

  ```bash
  cd backend && ./mvnw verify 2>&1 | tee /tmp/pr2-verify.log | tail -50
  ```
  Expected: BUILD SUCCESS. Test count target: 5038 ± 5 testcases / 0F / 0E / 26 skip (use `<testcase>` element counting from `feedback_surefire_nested_count.md` memory).

- [ ] **Step 2: Aggregate test counts via testcase elements**

  ```bash
  total_cases=$(find target/surefire-reports -maxdepth 1 -name "TEST-*.xml" -exec grep -c "<testcase " {} \; | awk '{s+=$1} END {print s}')
  total_skipped=$(find target/surefire-reports -maxdepth 1 -name "TEST-*.xml" -exec grep -c "<skipped" {} \; | awk '{s+=$1} END {print s}')
  total_failures=$(find target/surefire-reports -maxdepth 1 -name "TEST-*.xml" -exec grep -c "<failure " {} \; | awk '{s+=$1} END {print s}')
  total_errors=$(find target/surefire-reports -maxdepth 1 -name "TEST-*.xml" -exec grep -c "<error " {} \; | awk '{s+=$1} END {print s}')
  echo "tests=$total_cases failures=$total_failures errors=$total_errors skipped=$total_skipped"
  ```
  Expected: tests in [5033, 5043], failures=0, errors=0, skipped=26.

- [ ] **Step 3: Update verify-backend marker**

  ```bash
  HEAD_SHA=$(git rev-parse HEAD)
  cat > .claude/markers/verify-backend.json <<EOF
  {
    "commit": "$HEAD_SHA",
    "command": "./mvnw verify",
    "exit": 0,
    "ts": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "summary": "Pre-PR verify on feat/tenant-scope-binding-consolidation: <COUNT> testcases / 0F / 0E / 26 skip. PR #2 (ADR-T008 Surface 2 + companion ArchUnit rule)."
  }
  EOF
  git add .claude/markers/verify-backend.json
  git commit -m "chore: update verify-backend marker post-PR-2 verify"
  ```

- [ ] **Step 4: Push branch**

  ```bash
  git push -u origin feat/tenant-scope-binding-consolidation
  ```

- [ ] **Step 5: Open PR**

  ```bash
  gh pr create --title "feat(multitenancy): tenant scope binding consolidation (backlog #8 / PR #2 / ADR-T008 Surface 2)" --body "$(cat <<'EOF'
## Summary

PR #2 of the ADR-T008 series. Consolidates ~36 direct `ScopedValue.where(RequestScopes.TENANT_ID, ...)` call sites outside `..multitenancy..` into the canonical `RequestScopes` APIs, adds two new API surfaces (`runForTenantWithMember` + `TenantScopedRunner.forEachTenant` Spring bean), ships a companion ArchUnit rule with structured exemption set, and amends ADR-T008.

The handover's original "13 jobs + 2 backfills" framing was incomplete. Pre-migration audit (see spec) found ~21 additional single-tenant sites and ~10 boundary-binder sites that needed different treatment. The rescoped PR migrates everything that fits a sanctioned API and exempts the legitimate boundary-binders (servlet filter, SSE capture-rebind, dev tooling, cross-tenant search) until ADR-204's `withCurrentScopes()` lands.

## Migration breakdown

- **13 fan-out scheduled jobs** → `TenantScopedRunner.forEachTenant`
- **2 backfill helpers** → `runForTenantWithMember` (3-binding TENANT + ORG + MEMBER=SYSTEM_ACTOR_ID)
- **~21 single-tenant sites** → `runForTenant` / `callForTenant` (includes one behaviour change in `InvoiceEmailEventListener`: null-tenant path now logs + drops instead of falling through to Hibernate failure)
- **~10 documented exemptions** in the ArchUnit rule (CustomerAuthFilter, AssistantController, DevPortalController × 6, MockPaymentController × 2, AcceptanceService cross-tenant search)

## ADR-T008 changes

- Surface 2 final API recorded.
- "Only sanctioned way" wording softened to "sanctioned for the consolidatable patterns; exemptions await ADR-204."
- API-shape Alternatives entry: chose Option A (explicit method) over Option B (Map-of-bindings) and Option C (capture-and-rebind).
- Follow-up: when ADR-204 lands, migrate exemption set and remove from rule.

## Verification

- Full `./mvnw verify`: <COUNT> testcases / 0F / 0E / 26 skip (using `<testcase>`-element counting per `feedback_surefire_nested_count.md` — Surefire `<testsuite tests=N>` aggregate is unreliable with @Nested classes).
- ArchUnit rule verified by inject-violation pattern (per PR #1's `TenantScopeBindingTest` precedent).
- ScopedValue grep post-migration shows only the documented exemption set + `..multitenancy..` package.

## Out of scope (deliberately deferred)

- TD-009 controller refactoring touched files (`InternalAuditController`, `PortalBrandingController`, `MockPaymentController`); touch-it-don't-fix-it.
- `TrustLedgerPortalSyncService.backfillForTenant` cross-tenant guard asymmetry (separate security-class fix).
- ADR-204's `withCurrentScopes()` implementation.

## Test plan

- [ ] `./mvnw verify` clean
- [ ] ArchUnit rule fires on injected violation (verified inline)
- [ ] No direct `ScopedValue.where(TENANT_ID, ...)` outside multitenancy + exemption set
- [ ] CodeRabbit review obtained and addressed
- [ ] User merge approval obtained

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
  ```

- [ ] **Step 6: STOP at merge gate**

  Per user mandate: "Autonomy granted through merge prep, with merge requiring explicit user approval." Do NOT `gh pr merge`. Report the PR URL and await user instruction.
