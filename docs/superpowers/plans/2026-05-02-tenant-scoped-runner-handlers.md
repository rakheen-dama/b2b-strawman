# TenantScopedRunner — PR #1 (Handlers Migration) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate 14 duplicate `handleInTenantScope` helpers across notification handlers into a single static API on `RequestScopes`, with an ArchUnit regression guard and ADR.

**Architecture:** Add `RequestScopes.runForTenant(String, String, Runnable)` and `callForTenant(String, String, Callable<T>)` static methods. Migrate the 14 handler classes to use them. Add `TenantScopeBindingRule` ArchUnit test that bans the duplicate helper-method names anywhere outside the `multitenancy` package. Document in `adr/ADR-T008-tenant-scoped-runner.md`. Spec at `docs/superpowers/specs/2026-05-02-tenant-scoped-runner-design.md`.

**Tech Stack:** Spring Boot 4 / Java 25 / Maven. JUnit 5 + AssertJ for tests. ArchUnit 1.3.0. `ScopedValue` for tenant-context binding.

**Branch:** `feat/tenant-scoped-runner-handlers` (already created, spec already committed at `2efc82aa7`).

**Working directory:** `/Users/rakheendama/Projects/2026/b2b-strawman` (main repo, not a worktree — single-session work).

---

## File Structure

**Files modified:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — add two static methods.
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopesTest.java` — add tests for the new methods.
- 14 handler files (full list in Task 2) — remove private `handleInTenantScope`, switch call sites to `RequestScopes.runForTenant(...)`.

**Files created:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingRule.java` — ArchUnit rule.
- `adr/ADR-T008-tenant-scoped-runner.md` — architectural decision record.

**Files NOT touched:** any `multitenancy/TenantTransactionHelper.java`, frontend, migrations, or scheduled-job classes (those are PR #2).

---

## Task 0: Audit null-tenant call sites

**Goal:** Confirm that no call site of `handleInTenantScope` plausibly passes a null `tenantId`. If any do, halt and re-spec; the upstream publisher (event payload or scheduler) is the right place to fix.

**Files:** none modified — this is a manual investigation. Output goes into the eventual PR description.

- [ ] **Step 1: Enumerate all call sites of the 14 helpers**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
git grep -nP "handleInTenantScope\(" src/main/java | grep -v "private void handleInTenantScope" | tee /tmp/tenantscope-call-sites.txt
wc -l /tmp/tenantscope-call-sites.txt
```

Expected: ~100 lines (one per call site, across the 14 handlers).

- [ ] **Step 2: For each call site, identify the source of the first argument**

For each line in `/tmp/tenantscope-call-sites.txt`, the first argument is one of:
- `event.tenantId()` — record accessor; trace the publisher
- `event.getTenantId()` — same
- `tenantId` — local variable; trace its assignment
- a literal — should not exist

The dominant pattern is `event.tenantId()` from a `@TransactionalEventListener(phase = AFTER_COMMIT)`. Confirm by skimming each handler's listener method.

- [ ] **Step 3: Verify each event publisher writes a non-null tenantId**

For each unique event type referenced (e.g. `CommentCreatedEvent`, `DocumentGeneratedEvent`, `ProposalAcceptedEvent`):

```bash
git grep -n "new <EventName>(" src/main/java
```

Inspect each construction site. The `tenantId` argument should be either `RequestScopes.requireTenantId()` (throws on missing) or a hard-coded path inside provisioning that never publishes domain events. If a publisher uses `RequestScopes.getTenantIdOrNull()` or any other null-tolerant accessor, **stop and re-spec** — the migration cannot proceed until that publisher is fixed.

- [ ] **Step 4: Record the audit findings in `/tmp/tenantscope-audit.md`**

Format:

```markdown
# Null-tenant audit — TenantScopedRunner PR #1

## Call site enumeration
- Total call sites: N
- All pass `event.tenantId()` from an AFTER_COMMIT listener: yes/no
- Exceptions:
  - file:line — source — note

## Publisher audit per event type
- EventName — publisher uses requireTenantId() at file:line — non-null guaranteed
- ...

## Conclusion
- All call sites guaranteed non-null tenantId: yes/no
- If no: list of upstream publishers requiring fix before migration proceeds
```

This file gets pasted into the PR description in Task 5.

- [ ] **Step 5: Decision gate**

If audit conclusion is "all guaranteed non-null tenantId": proceed to Task 1.

If any publisher is null-tolerant: stop. Open a separate fix PR for the publisher first. Document and pause.

**Commit:** none — Task 0 is investigation only.

---

## Task 1: Add `RequestScopes.runForTenant` + `callForTenant` (TDD)

**Goal:** Add the two static methods with full test coverage. TDD: tests first, verify they fail, implement minimally, verify they pass.

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java`
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopesTest.java`

- [ ] **Step 1: Add the failing tests**

Append the following block to `RequestScopesTest.java` (just before the final `}`). Includes the necessary imports — add them at the top of the file in dedup-aware fashion (some imports like `assertThat`, `assertThatThrownBy` already exist):

```java
// add to existing imports:
import java.util.concurrent.Callable;
```

```java
// runForTenant tests
@Test
void runForTenant_bindsTenantId() {
  RequestScopes.runForTenant(
      "tenant_acme",
      null,
      () -> assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_acme"));
}

@Test
void runForTenant_bindsOrgIdWhenProvided() {
  RequestScopes.runForTenant(
      "tenant_acme",
      "org_123",
      () -> {
        assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_acme");
        assertThat(RequestScopes.getOrgIdOrNull()).isEqualTo("org_123");
      });
}

@Test
void runForTenant_omitsOrgIdWhenNull() {
  RequestScopes.runForTenant(
      "tenant_acme",
      null,
      () -> {
        assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_acme");
        assertThat(RequestScopes.getOrgIdOrNull()).isNull();
      });
}

@Test
void runForTenant_rejectsNullTenant() {
  Runnable action = () -> {};
  assertThatThrownBy(() -> RequestScopes.runForTenant(null, "org_123", action))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("tenantId");
}

@Test
void runForTenant_rejectsBlankTenant() {
  Runnable action = () -> {};
  assertThatThrownBy(() -> RequestScopes.runForTenant("", "org_123", action))
      .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> RequestScopes.runForTenant("   ", "org_123", action))
      .isInstanceOf(IllegalArgumentException.class);
}

@Test
void runForTenant_rejectsNullAction() {
  assertThatThrownBy(() -> RequestScopes.runForTenant("tenant_acme", null, null))
      .isInstanceOf(NullPointerException.class);
}

@Test
void runForTenant_propagatesRuntimeException() {
  assertThatThrownBy(
          () ->
              RequestScopes.runForTenant(
                  "tenant_acme",
                  null,
                  () -> {
                    throw new IllegalStateException("boom");
                  }))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("boom");
}

// callForTenant tests
@Test
void callForTenant_returnsResult() {
  String result =
      RequestScopes.callForTenant(
          "tenant_acme",
          "org_123",
          () -> RequestScopes.requireTenantId() + ":" + RequestScopes.getOrgIdOrNull());
  assertThat(result).isEqualTo("tenant_acme:org_123");
}

@Test
void callForTenant_propagatesRuntimeException() {
  Callable<String> failing =
      () -> {
        throw new IllegalStateException("boom");
      };
  assertThatThrownBy(() -> RequestScopes.callForTenant("tenant_acme", null, failing))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("boom");
}

@Test
void callForTenant_wrapsCheckedException() {
  Callable<String> failing =
      () -> {
        throw new java.io.IOException("disk full");
      };
  assertThatThrownBy(() -> RequestScopes.callForTenant("tenant_acme", null, failing))
      .isInstanceOf(RuntimeException.class)
      .hasCauseInstanceOf(java.io.IOException.class);
}

@Test
void callForTenant_rejectsNullTenant() {
  Callable<String> action = () -> "x";
  assertThatThrownBy(() -> RequestScopes.callForTenant(null, "org_123", action))
      .isInstanceOf(IllegalArgumentException.class);
}

@Test
void callForTenant_rejectsBlankTenant() {
  Callable<String> action = () -> "x";
  assertThatThrownBy(() -> RequestScopes.callForTenant("", "org_123", action))
      .isInstanceOf(IllegalArgumentException.class);
}

@Test
void callForTenant_rejectsNullAction() {
  assertThatThrownBy(() -> RequestScopes.callForTenant("tenant_acme", null, null))
      .isInstanceOf(NullPointerException.class);
}
```

- [ ] **Step 2: Run the tests, expect compilation failures**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw -q test -Dtest='RequestScopesTest' 2>&1 | tail -30
```

Expected: `BUILD FAILURE` with compile errors like `cannot find symbol method runForTenant` and `cannot find symbol method callForTenant` on `RequestScopes`.

- [ ] **Step 3: Implement `runForTenant` and `callForTenant` in `RequestScopes.java`**

Add the following imports at the top of `RequestScopes.java` (dedup against existing imports):

```java
import jakarta.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.Callable;
```

Add these methods inside the `RequestScopes` class, just before the existing `private RequestScopes() {}` line:

```java
/**
 * Run {@code action} with {@link #TENANT_ID} (and optionally {@link #ORG_ID}) bound on a fresh
 * ScopedValue carrier. The only sanctioned way to bind tenant scope outside this class; see
 * ArchUnit rule {@code TenantScopeBindingRule} and ADR-T008.
 *
 * <p>Replaces the duplicated private {@code handleInTenantScope} helpers that previously lived
 * in 14 notification handlers (PR #1, 2026-05-02).
 *
 * @throws IllegalArgumentException if {@code tenantId} is null or blank.
 * @throws NullPointerException if {@code action} is null.
 */
public static void runForTenant(String tenantId, @Nullable String orgId, Runnable action) {
  Objects.requireNonNull(action, "action");
  requireValidTenantId(tenantId);
  bindTenantScope(tenantId, orgId).run(action);
}

/**
 * Variant of {@link #runForTenant} that returns a value. Checked exceptions thrown by the
 * Callable are wrapped in {@link RuntimeException} per JDK Callable convention.
 *
 * @throws IllegalArgumentException if {@code tenantId} is null or blank.
 * @throws NullPointerException if {@code action} is null.
 */
public static <T> T callForTenant(String tenantId, @Nullable String orgId, Callable<T> action) {
  Objects.requireNonNull(action, "action");
  requireValidTenantId(tenantId);
  try {
    return bindTenantScope(tenantId, orgId).call(action);
  } catch (RuntimeException e) {
    throw e;
  } catch (Exception e) {
    throw new RuntimeException(e);
  }
}

private static void requireValidTenantId(String tenantId) {
  if (tenantId == null || tenantId.isBlank()) {
    throw new IllegalArgumentException("tenantId must be non-null and non-blank");
  }
}

private static ScopedValue.Carrier bindTenantScope(String tenantId, @Nullable String orgId) {
  ScopedValue.Carrier carrier = ScopedValue.where(TENANT_ID, tenantId);
  if (orgId != null && !orgId.isBlank()) {
    carrier = carrier.where(ORG_ID, orgId);
  }
  return carrier;
}
```

- [ ] **Step 4: Run the tests, expect all pass**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw -q test -Dtest='RequestScopesTest' 2>&1 | tail -20
```

Expected: `Tests run: NN, Failures: 0, Errors: 0, Skipped: 0` (NN = existing count + 13 new tests). `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java
git add backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopesTest.java
git commit -m "$(cat <<'EOF'
feat(multitenancy): add RequestScopes.runForTenant / callForTenant

Static API for binding TENANT_ID (+ optional ORG_ID) on a fresh ScopedValue
carrier. Replaces the 14 duplicated private handleInTenantScope helpers
across notification handlers — those migrations land in the next commit.

Null or blank tenantId now throws IllegalArgumentException (was: silent
fall-through to action.run() with no scope bound). See ADR-T008 (next
commit).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Migrate 14 handlers to `RequestScopes.runForTenant`

**Goal:** Remove the 14 duplicate `handleInTenantScope` private methods. Switch every call site from `handleInTenantScope(tid, oid, () -> ...)` to `RequestScopes.runForTenant(tid, oid, () -> ...)`.

**Files modified (the canonical 14 from the Explore audit):**

| # | File |
|---|------|
| 1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` |
| 2 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java` |
| 3 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalDocumentNotificationHandler.java` |
| 4 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalSentEmailHandler.java` |
| 5 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalPortalSyncEventHandler.java` |
| 6 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiredEventHandler.java` |
| 7 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalAcceptedEventHandler.java` |
| 8 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailEventListener.java` |
| 9 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestNotificationEventListener.java` |
| 10 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` |
| 11 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/DeadlinePortalSyncService.java` |
| 12 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/TrustLedgerPortalSyncService.java` |
| 13 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/RetainerPortalSyncService.java` |
| 14 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandler.java` |

- [ ] **Step 1: Confirm the 14 declarations are still present (defensive — main may have advanced)**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
git grep -nP "private void handleInTenantScope\(" src/main/java | sort
```

Expected: 14 lines, one per file in the table above. If count != 14, stop — investigate before migrating.

- [ ] **Step 2: For each of the 14 files, perform two surgical edits — remove the helper, switch call sites**

For each file, do BOTH of these in one Edit pass:

**Edit A** — delete the private helper. The exact block to remove (byte-for-byte identical across all 14 files):

```java
private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
  if (tenantId != null) {
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    carrier.run(action);
  } else {
    action.run();
  }
}
```

(Note: actual whitespace/javadoc may differ slightly per file — read the file first, copy the exact block, delete it via Edit. The signature line is the unique anchor.)

**Edit B** — replace every call to `handleInTenantScope(...)` with `RequestScopes.runForTenant(...)`:

Use `Edit` with `replace_all: true` on the call-site method name. The argument list doesn't change (same `String tenantId, String orgId, Runnable action`). Example:

Before: `handleInTenantScope(event.tenantId(), event.orgId(), () -> doWork(event));`
After:  `RequestScopes.runForTenant(event.tenantId(), event.orgId(), () -> doWork(event));`

If `RequestScopes` isn't already imported in the file, add `import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;` (it likely is — the existing helper references `RequestScopes.TENANT_ID`).

After Edit B, the import of `ScopedValue` may now be unused (check the file for any remaining `ScopedValue.` references; if none, remove the `import java.lang.ScopedValue;` or whatever import path the file uses).

- [ ] **Step 3: Per-file sanity build**

After completing edits on each file, run a quick compile to catch typos before moving on:

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw -q compile 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`. If it fails, fix the file before moving on.

- [ ] **Step 4: Verify all 14 helpers are gone**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
git grep -nP "private void handleInTenantScope" src/main/java
```

Expected: empty output. If any remain, finish migrating those files.

```bash
git grep -nP "handleInTenantScope\(" src/main/java
```

Expected: empty output (no call sites either).

- [ ] **Step 5: Run the full backend test suite**

This is the merge bar (Quality Gates #1, #5). Targeted runs do not catch cross-package breakage.

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw verify 2>&1 | tee /tmp/mvnw-verify-task2.log | tail -20
```

Expected: `BUILD SUCCESS` and `Tests run: 5014, Failures: 0, Errors: 0, Skipped: 26` (the baseline) — possibly +13 tests from Task 1 = `Tests run: 5027`. Failures or errors must be zero.

If any test fails: do NOT proceed to commit. Investigate the failure (it should be a regression caused by the migration). Fix at the right layer. Re-run.

- [ ] **Step 6: Commit**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git add backend/src/main/java
git commit -m "$(cat <<'EOF'
refactor(multitenancy): migrate 14 notification handlers to RequestScopes.runForTenant

Removes 14 byte-for-byte-identical private handleInTenantScope helpers
from notification handlers + portal sync services + the legal-vertical
trust handler, replacing each call site with RequestScopes.runForTenant
from the canonical static API added in the previous commit.

Net: -~110 lines of duplicated binding code.

Behaviour change: null or blank tenantId now throws IllegalArgumentException
instead of falling through to action.run() with no scope bound. The audit
in this PR's description confirms no caller passes null in practice — the
original fall-through was defensive copy-paste that masked a Class-1
silent-corruption hazard (qa_cycle/bug-classes.md).

ArchUnit regression guard banning the duplicate helper names lands in the
next commit. See ADR-T008.

Files migrated:
  notification/NotificationEventHandler
  portal/notification/PortalEmailNotificationChannel
  portal/PortalDocumentNotificationHandler
  proposal/Proposal{Sent,PortalSync,Expired,Accepted}{Email,Event}Handler
  informationrequest/InformationRequest{Email,Notification}EventListener
  customerbackend/handler/PortalEventHandler
  customerbackend/service/{Deadline,TrustLedger,Retainer}PortalSyncService
  verticals/legal/trustaccounting/event/TrustNotificationHandler

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add ArchUnit rule banning the duplicate helper names

**Goal:** Prevent regression. Future contributors who paste a 15th `handleInTenantScope` get a build failure.

**Files:**
- Create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingRule.java`

- [ ] **Step 1: Read existing ArchUnit test class to confirm conventions**

```bash
cat backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TestConventionsTest.java
```

Note: package, `@AnalyzeClasses` annotation, `@ArchTest` field style, `noClasses()...because()` form. The new file will follow the same shape.

- [ ] **Step 2: Create the rule file**

Create `backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingRule.java`:

```java
package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

/**
 * Bans the duplicated tenant-scope-binding helper method names that were consolidated into
 * {@code RequestScopes.runForTenant} / {@code callForTenant} on 2026-05-02.
 *
 * <p>If a future contributor reintroduces a private helper named {@code handleInTenantScope},
 * {@code runInTenantScope}, etc., this rule fails the build. Bind tenant scope via the canonical
 * static API on {@code RequestScopes} — see ADR-T008.
 *
 * <p>Scoped to non-production code under {@code io.b2mash.b2b.b2bstrawman}, excluding the
 * {@code multitenancy} package (where {@code RequestScopes} itself lives) and tests
 * (which may use the names freely in fixtures).
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class TenantScopeBindingRule {

  private static final Set<String> BANNED_HELPER_NAMES =
      Set.of("handleInTenantScope", "runInTenantScope", "executeInTenantScope", "withTenantScope");

  @ArchTest
  static final ArchRule no_private_tenant_scope_helpers_outside_request_scopes =
      noClasses()
          .that()
          .resideOutsideOfPackage("..multitenancy..")
          .should(declareMethodWithBannedName())
          .because(
              "Bind tenant scope via RequestScopes.runForTenant / callForTenant. "
                  + "Adding a private helper recreates the duplication this rule prevents. "
                  + "See ADR-T008.");

  private static ArchCondition<JavaClass> declareMethodWithBannedName() {
    return new ArchCondition<>("declare a method with a banned tenant-scope helper name") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        for (JavaMethod method : clazz.getMethods()) {
          if (BANNED_HELPER_NAMES.contains(method.getName())) {
            String message =
                String.format(
                    "Class %s declares banned helper method '%s' in (%s)",
                    clazz.getName(),
                    method.getName(),
                    method.getSourceCodeLocation());
            events.add(SimpleConditionEvent.violated(method, message));
          }
        }
      }
    };
  }
}
```

- [ ] **Step 3: Run the rule, expect pass**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw -q test -Dtest='TenantScopeBindingRule' 2>&1 | tail -10
```

Expected: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. The rule passes because Task 2 removed all 14 helpers.

- [ ] **Step 4: Sanity-check the rule actually fires on a violation**

Temporarily reintroduce a banned helper to confirm the rule fails on it (then revert). Use a one-shot test class so we don't pollute production code:

Create a temp scratch file (outside the repo, e.g. as a string change in a throwaway location). Easiest: add a method `private void handleInTenantScope() {}` to any class under `notification/` momentarily, run the rule, then `git restore` to undo:

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
# Pick any file we just edited in Task 2 — re-add the banned method:
echo "  private void handleInTenantScope() {}" >> /tmp/sanity-check-method.txt
# Manually paste that line into NotificationEventHandler.java just before the closing brace
./mvnw -q test -Dtest='TenantScopeBindingRule' 2>&1 | tail -15
```

Expected after pasting: `Architecture Violation` reported, `BUILD FAILURE`. Then restore:

```bash
git restore src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java
./mvnw -q test -Dtest='TenantScopeBindingRule' 2>&1 | tail -5
```

Expected after restore: `BUILD SUCCESS` — rule passes again.

If the rule did NOT fire when the violation was added, the rule is broken; debug before proceeding.

- [ ] **Step 5: Run the full backend test suite once more**

The new ArchUnit test class is part of `verify`. Confirm baseline:

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw verify 2>&1 | tee /tmp/mvnw-verify-task3.log | tail -20
```

Expected: `BUILD SUCCESS`. Test count = baseline + Task 1 tests (13) + 1 ArchUnit test = `Tests run: 5028` (or similar, depending on existing baseline drift).

- [ ] **Step 6: Commit**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git add backend/src/test/java/io/b2mash/b2b/b2bstrawman/architecture/TenantScopeBindingRule.java
git commit -m "$(cat <<'EOF'
test(architecture): ban duplicated tenant-scope helper method names

ArchUnit rule fails the build if any class outside the multitenancy package
declares a method named handleInTenantScope / runInTenantScope /
executeInTenantScope / withTenantScope. This is the regression guard for
the consolidation in the previous commit — without it, a future
contributor copy-pasting from older patterns reintroduces the duplication.

Test sources are excluded so test fixtures may use those names freely.

The companion rule banning direct ScopedValue.where(TENANT_ID, ...) calls
ships with PR #2 (jobs migration) — it cannot ship now without breaking
the 13 unmigrated scheduled jobs.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Write `ADR-T008-tenant-scoped-runner.md`

**Goal:** Record the decision so future contributors and ArchUnit failure messages have a referent.

**Files:**
- Create: `adr/ADR-T008-tenant-scoped-runner.md`

- [ ] **Step 1: Read an adjacent ADR for the convention**

```bash
cat adr/ADR-T002-scopedvalues-over-threadlocal.md | head -80
```

Note: the front-matter (date, status), section headers (Context, Decision, Consequences, Alternatives Considered, References), tone (technical, decision-led), and reference style.

- [ ] **Step 2: Create the ADR**

Write `adr/ADR-T008-tenant-scoped-runner.md`. Tailor wording if the existing T-series uses different headings; the section names below match the existing T-series convention as much as possible.

```markdown
# ADR-T008 — Tenant-Scoped Runner: Canonical API for binding tenant scope outside a request

**Status:** Accepted
**Date:** 2026-05-02
**Series:** Tenancy foundation (T-prefix)
**Supersedes:** N/A
**Related:** ADR-T002 (ScopedValues over ThreadLocal), ADR-204 (virtual-thread ScopedValue rebinding — proposed `withCurrentScopes`, deferred)

## Context

By 2026-05-02 the codebase had **14 byte-for-byte identical private `handleInTenantScope(String tenantId, String orgId, Runnable action)` helpers** scattered across notification handlers, portal sync services, and the legal-vertical trust handler. Each was the canonical pattern for binding `RequestScopes.TENANT_ID` (and optionally `ORG_ID`) on a fresh `ScopedValue` carrier when running work outside the originating request — typically inside an `@TransactionalEventListener(phase = AFTER_COMMIT)` listener.

Two problems with the duplication:

1. **Latent silent-corruption hazard.** The shared shape included an `else action.run();` fall-through when `tenantId == null`. If reached, queries hit the wrong schema or fail. In practice unreachable for AFTER_COMMIT listeners (the publisher guarantees a non-null `tenantId` from the originating transaction), but encoded the hazard 14 times. The bug-class catalogue (`qa_cycle/bug-classes.md`, Class 1 — notification-pipeline gaps) documents this category as the dominant bug class shipped through the 2026-04 / 2026-05 QA cycle.
2. **Maintenance smell.** Any change to the binding contract — e.g. adding `MEMBER_ID` capture, changing blank-string semantics — would need to touch 14 places.

Adjacent precedent: **ADR-204** proposes a `RequestScopes.withCurrentScopes()` capture-and-rebind utility for virtual-thread executors but defers the implementation. This ADR ships a sibling utility — explicit-input rather than capture — for the AFTER_COMMIT use case.

## Decision

Two surfaces, single source of truth.

### Surface 1 — `RequestScopes.runForTenant` / `callForTenant` (this ADR)

Static methods on the existing `RequestScopes` class, co-located with the `ScopedValue<String> TENANT_ID` / `ORG_ID` field declarations:

```java
public static void runForTenant(String tenantId, @Nullable String orgId, Runnable action);
public static <T> T callForTenant(String tenantId, @Nullable String orgId, Callable<T> action);
```

Semantics:

- **Null or blank `tenantId` → `IllegalArgumentException`.** No fall-through. Replaces the silent hazard with fail-fast at the call site.
- **`orgId` nullable.** Some events carry no org context. Matches the migrated helpers' actual behaviour.
- **No re-binding of `MEMBER_ID` / `ORG_ROLE` / `CAPABILITIES`.** Handlers and jobs are system-level dispatch; actor scope ends at the originating request's commit boundary. Re-binding actor identity across `AFTER_COMMIT` would be a category mistake (audit attribution would lie).
- **`Callable<T>` exceptions** rethrown via `RuntimeException` wrapping per JDK Callable convention.

### Surface 2 — `TenantScopedRunner` Spring bean (PR #2, separate ADR update)

For scheduled jobs that fan out to all tenants — distinct shape, distinct concerns (iteration over `OrgSchemaMappingRepository`, per-tenant exception isolation, success-count return). Lands in PR #2; this ADR will be amended when it ships.

### ArchUnit enforcement

- **Rule #1 (ships with this ADR / PR #1):** No class outside `io.b2mash.b2b.b2bstrawman.multitenancy..` may declare a method named `handleInTenantScope`, `runInTenantScope`, `executeInTenantScope`, or `withTenantScope`. Future regression caught at build time.
- **Rule #2 (ships with PR #2):** No class outside `multitenancy..` may call `ScopedValue.where(RequestScopes.TENANT_ID, ...)` directly. Defers until the 13 scheduled jobs migrate to `TenantScopedRunner` — shipping it now would build-break unmigrated jobs.

## Consequences

### Positive

- The only sanctioned way to bind tenant scope outside the `multitenancy` package is the canonical static API. Reduces the surface area for Class-1 (notification-pipeline gap) bugs.
- `~110` lines of duplicate code removed.
- Future binding-contract changes (e.g. adding ORG_ID validation or new ScopedValue captures) are one-place edits.
- ArchUnit regression guard prevents future copy-paste regression.

### Negative

- One behaviour change: null/blank `tenantId` now throws instead of running unscoped. Mitigated by the pre-migration audit (PR #1 description) which confirmed no caller relies on the old fall-through.
- The `TenantScopedRunner` bean (PR #2) introduces a small DI surface; not yet justified by handlers but justified by jobs.
- ArchUnit Rule #1 is name-based — a contributor could circumvent by inventing a new helper name. Mitigated by Rule #2 (PR #2) which catches the underlying primitive (`ScopedValue.where(TENANT_ID, ...)`) regardless of wrapper name.

### Known limitations

- The future Rule #2 (PR #2) won't catch indirect access via local variable — `var key = RequestScopes.TENANT_ID; ScopedValue.where(key, ...)`. Not present in current code. Tightenable if it becomes a real evasion pattern.
- `TenantTransactionHelper.executeInTenantTransaction(...)` is intentionally not consolidated. It does meaningfully more (forces `search_path` for raw SQL during provisioning); the concerns are different. Documented as deliberate non-consolidation.

## Alternatives Considered

1. **Single Spring bean fronting both shapes** (handler bind + job iteration). Rejected — would force a 14-class constructor-injection cascade for the handlers, with no benefit. The bind primitive belongs next to `RequestScopes.TENANT_ID` so the relationship is structural and visible (matches ADR-T002 spirit: explicit scope boundaries, no hidden magic).
2. **Drop-in replacement preserving the null fall-through.** Rejected — the fall-through is a Class-1 hazard. The cheapest moment to remove it is during the consolidation. The pre-migration audit confirmed no caller relies on the old semantics.
3. **Capture-current-scope variant only** (the ADR-204 `withCurrentScopes` shape). Rejected for this use case — handlers receive `(tenantId, orgId)` from the event payload, not from current scope. ADR-204's variant remains valid for its target use case (virtual-thread executors that inherit the parent request's scope) and is a sibling, not a substitute.

## References

- Design spec: `docs/superpowers/specs/2026-05-02-tenant-scoped-runner-design.md`
- Implementation plan: `docs/superpowers/plans/2026-05-02-tenant-scoped-runner-handlers.md`
- Bug-class catalogue: `qa_cycle/bug-classes.md`
- Quality Gates: top of `CLAUDE.md` (Quality Gates #1, #5, #7)
- Adjacent ADRs: ADR-T002, ADR-204
```

- [ ] **Step 3: Verify ADR renders sanely (no broken markdown)**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
head -10 adr/ADR-T008-tenant-scoped-runner.md
wc -l adr/ADR-T008-tenant-scoped-runner.md
```

Expected: front-matter renders with proper status/date; ~100 lines.

- [ ] **Step 4: Commit**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git add adr/ADR-T008-tenant-scoped-runner.md
git commit -m "$(cat <<'EOF'
docs(adr): ADR-T008 TenantScopedRunner — canonical API for binding tenant scope

Records the decision behind the RequestScopes.runForTenant / callForTenant
consolidation shipped in this PR. Documents PR #2 follow-up (jobs +
TenantScopedRunner bean + companion ArchUnit rule), known limitations,
and alternatives considered.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Final verification + open PR

**Goal:** Final full-suite verification, marker file update, push branch, open PR with the audit report from Task 0 in the description.

**Files:**
- Update: `.claude/markers/verify-backend.json` (per the merge-gate hook contract)

- [ ] **Step 1: Run the full backend `./mvnw verify` once more on the final tree**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend
./mvnw verify 2>&1 | tee /tmp/mvnw-verify-final.log | tail -30
```

Expected: `BUILD SUCCESS`. Test count = baseline + ~14 (13 from Task 1 + 1 ArchUnit) ≈ `Tests run: 5028, Failures: 0, Errors: 0, Skipped: 26`.

If anything fails: STOP. Investigate. Do not push.

- [ ] **Step 2: Update the verify-backend marker for the merge-gate hook**

The pre-merge gate hook (`.claude/hooks/pre-pr-merge-gate.sh`) checks the marker. Inspect its current contents to maintain shape:

```bash
cat .claude/markers/verify-backend.json
```

Update with the current branch HEAD SHA, fresh timestamp, and exit code 0:

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
SHA=$(git rev-parse HEAD)
TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
cat > .claude/markers/verify-backend.json <<EOF
{
  "sha": "$SHA",
  "timestamp": "$TS",
  "exitCode": 0,
  "command": "./mvnw verify",
  "tests": "5028 / 0F / 0E / 26 skip"
}
EOF
cat .claude/markers/verify-backend.json
```

(Adjust the JSON shape if the existing file uses different keys — the hook is the source of truth, not the spec.)

- [ ] **Step 3: Push the branch**

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git push -u origin feat/tenant-scoped-runner-handlers
```

Expected: branch pushed, no force needed.

- [ ] **Step 4: Open the PR**

The PR description must include the Task 0 audit findings.

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
gh pr create --title "feat(multitenancy): consolidate 14 tenant-scope helpers into RequestScopes.runForTenant (backlog #8 / PR #1)" --body "$(cat <<'EOF'
## Summary

PR #1 of a two-PR refactor (backlog item #8 from `qa_cycle/HANDOFF-2026-05-02.md`).

- Adds `RequestScopes.runForTenant(String, String, Runnable)` and `callForTenant(String, String, Callable<T>)` static methods.
- Migrates 14 notification handlers / portal sync services / the legal-vertical trust handler to use them. Removes 14 byte-for-byte identical `handleInTenantScope` private helpers (~110 net lines deleted).
- Adds `TenantScopeBindingRule` ArchUnit test that bans the duplicate helper-method names anywhere outside `..multitenancy..`.
- Adds `adr/ADR-T008-tenant-scoped-runner.md`.

PR #2 (later session) handles the 13+ scheduled jobs and the companion ArchUnit rule that bans direct `ScopedValue.where(TENANT_ID, ...)` calls outside `RequestScopes`.

## Behaviour change

`null` or blank `tenantId` now throws `IllegalArgumentException`. The original 14 helpers fell through to `action.run()` with no scope bound — a Class-1 silent-corruption hazard (see `qa_cycle/bug-classes.md`). The pre-migration audit below confirms no caller relies on the old semantics.

## Pre-migration null-tenant audit (Task 0)

<!-- paste the contents of /tmp/tenantscope-audit.md here -->

## Verification

- Full backend `./mvnw verify`: PASS — `Tests run: NNNN, Failures: 0, Errors: 0, Skipped: 26` on commit `<HEAD>`.
- All 14 `private void handleInTenantScope` declarations removed (`git grep` returns empty).
- `TenantScopeBindingRule` ArchUnit rule passes locally; manually verified it fails when a banned helper is reintroduced and passes once removed.
- Pre-merge gate marker updated.

## Test plan

- [ ] CI green on this branch.
- [ ] CodeRabbit review addressed.
- [ ] No unrelated diffs in the PR (only the 4 commits: spec, runForTenant + tests, handler migration, ArchUnit, ADR).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

The `gh pr create` command outputs the PR URL. Capture it.

- [ ] **Step 5: Edit the PR description to inline the audit report**

The audit content from Task 0's `/tmp/tenantscope-audit.md` needs to replace the placeholder. Use `gh pr edit`:

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
PR_URL=$(gh pr view --json url -q .url)
PR_NUMBER=$(echo "$PR_URL" | grep -oE '[0-9]+$')
# Build the final body with audit inlined; this is one-shot — re-run if it needs updates:
gh pr edit "$PR_NUMBER" --body "$(awk '/<!-- paste/{system("cat /tmp/tenantscope-audit.md");next}1' <(gh pr view "$PR_NUMBER" --json body -q .body))"
```

(If the awk substitution gets fiddly, just do the swap manually with two `gh pr view --json body` + Edit + `gh pr edit --body-file` instead.)

- [ ] **Step 6: Wait for CI + CodeRabbit, address comments, do not merge**

User has authorised PR creation + review-fix loop, but the merge gate is the user's call. Stop here. Report PR URL and status.

If CodeRabbit posts review comments:

1. For each comment: read it, decide whether it's a real issue or a false positive.
2. Genuine issues: fix in a new commit on the branch (do not amend — Quality Gate forbids amend after CI). Push. Re-run `./mvnw verify` if the change is functional.
3. False positives: reply on the PR explaining why the comment doesn't apply (memory `feedback_coderabbit_reviews.md` — engage with every comment; don't skip).
4. Once review settles, update the marker if any code changed and report final state to user.

**Commit:** Task 5 only commits the marker file change (Step 2). Push happens once.

---

## Self-review

**Spec coverage check:**
- [x] Architecture (two surfaces, single source of truth) — Tasks 1 + Task 4 ADR.
- [x] API spec (`runForTenant`, `callForTenant` semantics, null-rejection) — Task 1.
- [x] Migration plan steps 1–6 from spec → mapped to Tasks 0–5.
- [x] ArchUnit rule #1 — Task 3.
- [x] ADR — Task 4.
- [x] Testing approach (unit tests, full verify as merge bar, no testcontainers, jakarta.annotation.Nullable) — Task 1 + Task 2 Step 5.
- [x] Risks (audit-first ordering, ArchUnit timing) — Tasks 0 and 3.
- [ ] PR #2 — explicitly out of scope for this plan; covered in spec only.

**Placeholder scan:** Task 5 Step 1 shows `~5028` not the literal expected count, because Task 1's exact test additions (13) plus the ArchUnit class (1 test) plus baseline drift make the precise number variable. The plan instructs to verify zero failures/errors and calls out the approximate count rather than a hard assertion — acceptable.

**Type consistency:** `runForTenant` / `callForTenant` signatures used identically in Task 1 (definition + tests), Task 2 (call sites), Task 3 (ArchUnit references), Task 4 (ADR). Field constants `TENANT_ID` / `ORG_ID` referenced consistently. No drift.

**Scope check:** This plan is PR #1 only. PR #2 (jobs + TenantScopedRunner bean + companion ArchUnit rule) needs its own plan in a future session — explicit in the spec.
