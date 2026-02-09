# ScopedValue Migration — Implementation Plan

**Branch**: `feat/scoped-values-migration`
**Prereq**: [tech-evolution/scoped-values-evaluation.md](../tech-evolution/scoped-values-evaluation.md)
**Java**: 25 (ScopedValue is final — JEP 506, no flags needed)
**Scope**: Replace `ThreadLocal` in `TenantContext` and `MemberContext` with `java.lang.ScopedValue`

---

## Overview

Migrate our two ThreadLocal-based context holders to ScopedValue in two slices. Each slice is independently shippable and testable. A third optional slice enables virtual threads.

| Slice | Scope | Files Changed | Estimated Tests Affected |
|-------|-------|---------------|--------------------------|
| **A** | TenantContext → ScopedValue | 7 production + 3 test | ~10 tests |
| **B** | MemberContext → ScopedValue | 5 production + 1 test | ~5 tests |
| **C** | Enable virtual threads | 1 config | Full regression |

---

## Slice A — TenantContext Migration

### A1. Create `RequestScopes` utility class

**New file**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java`

```java
package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.UUID;

/**
 * Request-scoped values for multitenancy and member identity.
 * Bound by servlet filters, read by controllers/services/Hibernate.
 *
 * <p>These replace the former ThreadLocal-based TenantContext and MemberContext.
 * Values are immutable within their scope and automatically unbound when the
 * binding lambda (run/call) exits.
 */
public final class RequestScopes {

  /** Tenant schema name (e.g. "tenant_a1b2c3d4e5f6"). Bound by TenantFilter. */
  public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  /** Current member's UUID within the tenant. Bound by MemberFilter. */
  public static final ScopedValue<UUID> MEMBER_ID = ScopedValue.newInstance();

  /** Current member's org role ("owner", "admin", "member"). Bound by MemberFilter. */
  public static final ScopedValue<String> ORG_ROLE = ScopedValue.newInstance();

  public static final String DEFAULT_TENANT = "public";

  private RequestScopes() {}
}
```

**Why a central class**: The ScopedValue fields must be `static final` and accessible to both filters (writers) and controllers/services/Hibernate (readers). A single `RequestScopes` class avoids circular package dependencies — `multitenancy` package owns it since tenant ID is the foundational scope, while member fields are co-located for simplicity.

### A2. Create `ScopedFilterChain` helper for checked exception wrapping

**New file**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ScopedFilterChain.java`

```java
package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Bridges ScopedValue's lambda-based API with the servlet FilterChain's
 * checked exceptions (IOException, ServletException).
 */
public final class ScopedFilterChain {

  private ScopedFilterChain() {}

  /**
   * Wraps filterChain.doFilter() for use inside ScopedValue.where().run().
   * Catches the RuntimeException wrapper and re-throws the original checked exception.
   */
  public static void doFilter(
      FilterChain chain, HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException e) {
      // When called from inside run(), these get wrapped in a RuntimeException
      // by the JVM. We re-throw here so the outer catch can unwrap.
      throw e;
    }
  }

  /**
   * Executes a ScopedValue.Carrier.run() and properly propagates checked exceptions
   * from doFilter() back to the filter contract.
   */
  public static void runScoped(
      ScopedValue.Carrier carrier,
      FilterChain chain,
      HttpServletRequest request,
      HttpServletResponse response)
      throws ServletException, IOException {
    try {
      carrier.run(() -> {
        try {
          chain.doFilter(request, response);
        } catch (IOException e) {
          throw new WrappedIOException(e);
        } catch (ServletException e) {
          throw new WrappedServletException(e);
        }
      });
    } catch (WrappedIOException e) {
      throw e.wrapped;
    } catch (WrappedServletException e) {
      throw e.wrapped;
    }
  }

  // Package-private wrapper exceptions for checked → unchecked → checked bridging
  static final class WrappedIOException extends RuntimeException {
    final IOException wrapped;
    WrappedIOException(IOException e) { super(e); this.wrapped = e; }
  }

  static final class WrappedServletException extends RuntimeException {
    final ServletException wrapped;
    WrappedServletException(ServletException e) { super(e); this.wrapped = e; }
  }
}
```

**Why this helper**: `ScopedValue.Carrier.run(Runnable)` cannot throw checked exceptions. `filterChain.doFilter()` throws `IOException` and `ServletException`. Every filter that binds a ScopedValue needs this bridge. Centralising it avoids duplicating the wrapping logic in `TenantFilter` and `MemberFilter`.

### A3. Rewrite `TenantFilter`

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java`

**Change summary**: Replace `TenantContext.setTenantId()` / `TenantContext.clear()` with `ScopedValue.where().run()` via the `ScopedFilterChain` helper.

**Current** (lines 31-52):
```java
try {
  // ... resolve schemaName ...
  TenantContext.setTenantId(schemaName);
  filterChain.doFilter(request, response);
} finally {
  TenantContext.clear();
}
```

**Target**:
```java
@Override
protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {
  Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

  if (authentication instanceof JwtAuthenticationToken jwtAuth) {
    Jwt jwt = jwtAuth.getToken();
    String orgId = extractOrgId(jwt);

    if (orgId != null) {
      String schemaName = resolveSchema(orgId);
      if (schemaName != null) {
        ScopedFilterChain.runScoped(
            ScopedValue.where(RequestScopes.TENANT_ID, schemaName),
            filterChain, request, response);
        return;
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Organization not provisioned");
        return;
      }
    }
  }

  // No JWT or no org claim — continue unbound (actuator, unauthenticated paths)
  filterChain.doFilter(request, response);
}
```

**Key differences**:
- No try-finally — `ScopedValue` auto-unbinds when `run()` exits
- `return` after `runScoped()` — the filter chain already completed inside the lambda
- Fallback path still calls `filterChain.doFilter()` directly (no tenant bound)

**Remove import**: `io.b2mash.b2b.b2bstrawman.multitenancy.TenantContext` (no longer used here)

### A4. Update `TenantIdentifierResolver`

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java`

**Current** (line 11-12):
```java
String tenantId = TenantContext.getTenantId();
return tenantId != null ? tenantId : TenantContext.DEFAULT_TENANT;
```

**Target**:
```java
@Override
public String resolveCurrentTenantIdentifier() {
  return RequestScopes.TENANT_ID.isBound()
      ? RequestScopes.TENANT_ID.get()
      : RequestScopes.DEFAULT_TENANT;
}
```

**Also update `isRoot()`**:
```java
@Override
public boolean isRoot(String tenantId) {
  return RequestScopes.DEFAULT_TENANT.equals(tenantId);
}
```

**Remove import**: `TenantContext`

### A5. Update `TenantLoggingFilter` — tenant reading

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java`

**Current** (line 32-35):
```java
String tenantId = TenantContext.getTenantId();
if (tenantId != null) {
  MDC.put(MDC_TENANT_ID, tenantId);
}
```

**Target**:
```java
if (RequestScopes.TENANT_ID.isBound()) {
  MDC.put(MDC_TENANT_ID, RequestScopes.TENANT_ID.get());
}
```

**Remove import**: `TenantContext` (will still import `MemberContext` until Slice B)

### A6. Update `MemberFilter` — tenant reading

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`

**Current** (line 39):
```java
String tenantId = TenantContext.getTenantId();
```

**Target**:
```java
String tenantId = RequestScopes.TENANT_ID.isBound()
    ? RequestScopes.TENANT_ID.get()
    : null;
```

Also update the `lazyCreateMember` log statement (line 115):
```java
// Current:
TenantContext.getTenantId()
// Target:
RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : "unknown"
```

**Replace import**: `TenantContext` → `RequestScopes`

### A7. Update `MemberSyncService` — manual tenant scoping

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java`

**`syncMember()` method** — current (lines 43-65):
```java
try {
  TenantContext.setTenantId(schemaName);
  return txTemplate.execute(status -> { ... });
} finally {
  TenantContext.clear();
}
```

**Target**:
```java
return ScopedValue.where(RequestScopes.TENANT_ID, schemaName).call(() ->
    txTemplate.execute(status -> {
      // ... unchanged business logic ...
    })
);
```

**`deleteMember()` method** — current (lines 70-94):
```java
try {
  TenantContext.setTenantId(schemaName);
  boolean deleted = Boolean.TRUE.equals(txTemplate.execute(status -> { ... }));
  if (deleted) {
    memberFilter.evictFromCache(schemaName, clerkUserId);
  }
  return deleted;
} finally {
  TenantContext.clear();
}
```

**Target**:
```java
return ScopedValue.where(RequestScopes.TENANT_ID, schemaName).call(() -> {
  boolean deleted = Boolean.TRUE.equals(txTemplate.execute(status -> {
    // ... unchanged business logic ...
  }));
  if (deleted) {
    memberFilter.evictFromCache(schemaName, clerkUserId);
  }
  return deleted;
});
```

**Note**: `ScopedValue.Carrier.call()` accepts checked exceptions via `CallableOp`, so no wrapping needed here (unlike filters).

**Remove import**: `TenantContext`

### A8. Delete `TenantContext.java`

**File to delete**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantContext.java`

After all references are migrated, this class is dead code. Delete entirely.

### A9. Update tests

#### `TenantContextTest.java` → `RequestScopesTest.java`

**File**: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantContextTest.java`

**Rename to**: `RequestScopesTest.java`

**Target** (complete replacement):
```java
package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class RequestScopesTest {

  @Test
  void tenantIdBoundWithinScope() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_a1b2c3d4e5f6").run(() -> {
      assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("tenant_a1b2c3d4e5f6");
      assertThat(RequestScopes.TENANT_ID.isBound()).isTrue();
    });
  }

  @Test
  void tenantIdUnboundOutsideScope() {
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    assertThatThrownBy(() -> RequestScopes.TENANT_ID.get())
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void tenantIdAutoUnboundsAfterScope() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_a1b2c3d4e5f6").run(() -> {
      assertThat(RequestScopes.TENANT_ID.isBound()).isTrue();
    });
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
  }

  @Test
  void nestedScopeShadowsOuter() {
    ScopedValue.where(RequestScopes.TENANT_ID, "outer").run(() -> {
      assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("outer");

      ScopedValue.where(RequestScopes.TENANT_ID, "inner").run(() -> {
        assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("inner");
      });

      assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("outer");
    });
  }
}
```

**Key changes**:
- No `@AfterEach` cleanup needed — ScopedValue is self-cleaning
- Tests verify auto-unbinding and nested scoping (new capabilities)
- No `setTenantId()` / `clear()` calls

#### `MemberFilterIntegrationTest.java`

**File**: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/member/MemberFilterIntegrationTest.java`

Replace all `try { TenantContext.setTenantId(...); ... } finally { TenantContext.clear(); }` blocks with `ScopedValue.where(RequestScopes.TENANT_ID, ...).run(() -> { ... })`.

**Lines 73-81** (shouldLazyCreateMemberForUnknownUser):
```java
// Current:
try {
  TenantContext.setTenantId(schemaName);
  var member = memberRepository.findByClerkUserId("user_lazy_create");
  assertThat(member).isPresent();
  assertThat(member.get().getOrgRole()).isEqualTo("member");
  assertThat(member.get().getEmail()).isEqualTo("user_lazy_create@placeholder.internal");
} finally {
  TenantContext.clear();
}

// Target:
ScopedValue.where(RequestScopes.TENANT_ID, schemaName).run(() -> {
  var member = memberRepository.findByClerkUserId("user_lazy_create");
  assertThat(member).isPresent();
  assertThat(member.get().getOrgRole()).isEqualTo("member");
  assertThat(member.get().getEmail()).isEqualTo("user_lazy_create@placeholder.internal");
});
```

**Lines 116-122** (shouldCacheMemberAndNotDuplicateOnSecondRequest):
```java
// Current:
try {
  TenantContext.setTenantId(schemaName);
  var member = memberRepository.findByClerkUserId("user_cache_test");
  assertThat(member).isPresent();
} finally {
  TenantContext.clear();
}

// Target:
ScopedValue.where(RequestScopes.TENANT_ID, schemaName).run(() -> {
  var member = memberRepository.findByClerkUserId("user_cache_test");
  assertThat(member).isPresent();
});
```

**Remove import**: `TenantContext`
**Add import**: `RequestScopes`

#### `DocumentIntegrationTest.java`

**File**: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/document/DocumentIntegrationTest.java`

This file only has a comment mentioning `MemberContext` (line 53). No `TenantContext` code to change. **No changes needed in Slice A**.

---

## Slice B — MemberContext Migration

### B1. Update `MemberFilter` — bind ScopedValues instead of ThreadLocal

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`

**Current** (lines 38-47):
```java
try {
  String tenantId = TenantContext.getTenantId();
  if (tenantId != null) {
    resolveMember(tenantId);
  }
  filterChain.doFilter(request, response);
} finally {
  MemberContext.clear();
}
```

Where `resolveMember()` calls (lines 88-91):
```java
MemberContext.setCurrentMemberId(memberId);
if (orgRole != null) {
  MemberContext.setOrgRole(orgRole);
}
```

**Target**: Resolve member and role first, then bind via ScopedValue:
```java
@Override
protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {

  String tenantId = RequestScopes.TENANT_ID.isBound()
      ? RequestScopes.TENANT_ID.get()
      : null;

  if (tenantId != null) {
    MemberInfo info = resolveMember(tenantId);
    if (info != null) {
      var carrier = ScopedValue.where(RequestScopes.MEMBER_ID, info.memberId());
      if (info.orgRole() != null) {
        carrier = carrier.where(RequestScopes.ORG_ROLE, info.orgRole());
      }
      ScopedFilterChain.runScoped(carrier, filterChain, request, response);
      return;
    }
  }

  // No tenant or member resolution failed — continue unbound
  filterChain.doFilter(request, response);
}
```

**Refactor `resolveMember()`**: Change it from void (setting ThreadLocal) to returning a record:
```java
private record MemberInfo(UUID memberId, String orgRole) {}

private MemberInfo resolveMember(String tenantId) {
  // ... existing resolution logic ...
  // Instead of MemberContext.setCurrentMemberId()/setOrgRole(),
  // return new MemberInfo(memberId, orgRole);
  // Return null on failure (current: just returns without setting context)
}
```

**Remove import**: `MemberContext`

### B2. Update `TenantLoggingFilter` — member reading

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java`

**Current** (lines 42-45):
```java
UUID memberId = MemberContext.getCurrentMemberId();
if (memberId != null) {
  MDC.put(MDC_MEMBER_ID, memberId.toString());
}
```

**Target**:
```java
if (RequestScopes.MEMBER_ID.isBound()) {
  MDC.put(MDC_MEMBER_ID, RequestScopes.MEMBER_ID.get().toString());
}
```

**Remove import**: `MemberContext`

### B3. Update `ProjectController`

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java`

Replace all `MemberContext.getCurrentMemberId()` / `MemberContext.getOrgRole()` calls with `RequestScopes.MEMBER_ID.get()` / `RequestScopes.ORG_ROLE.get()`.

**Pattern** — every method follows this shape:

```java
// Current:
UUID memberId = MemberContext.getCurrentMemberId();
String orgRole = MemberContext.getOrgRole();
if (memberId == null) {
  return ResponseEntity.of(memberContextMissing()).build();
}

// Target:
if (!RequestScopes.MEMBER_ID.isBound()) {
  return ResponseEntity.of(memberContextMissing()).build();
}
UUID memberId = RequestScopes.MEMBER_ID.get();
String orgRole = RequestScopes.ORG_ROLE.isBound() ? RequestScopes.ORG_ROLE.get() : null;
```

**Methods to update**: `listProjects()`, `getProject()`, `createProject()`, `updateProject()`

**`deleteProject()`** does NOT use MemberContext — no change needed.

**Replace import**: `MemberContext` → `RequestScopes`

### B4. Update `DocumentController`

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentController.java`

Same pattern as ProjectController. Replace in these methods:
- `initiateUpload()` (lines 46-47)
- `confirmUpload()` (lines 73-74)
- `cancelUpload()` (lines 87-88)
- `listDocuments()` (lines 111-112)
- `presignDownload()` (lines 129-130)

**Replace import**: `MemberContext` → `RequestScopes`

### B5. Update `ProjectMemberController`

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberController.java`

Same pattern. Replace in these methods:
- `addMember()` (line 45)
- `removeMember()` (lines 59, 63)
- `transferLead()` (line 75)

**Replace import**: `MemberContext` → `RequestScopes`

### B6. Delete `MemberContext.java`

**File to delete**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberContext.java`

After all references are migrated, delete entirely.

### B7. Add `RequestScopes` member tests

**File**: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopesTest.java` (created in A9)

**Add member-related tests**:
```java
@Test
void memberIdBoundWithinScope() {
  UUID id = UUID.randomUUID();
  ScopedValue.where(RequestScopes.MEMBER_ID, id).run(() -> {
    assertThat(RequestScopes.MEMBER_ID.get()).isEqualTo(id);
  });
}

@Test
void multipleBindingsWorkTogether() {
  UUID id = UUID.randomUUID();
  ScopedValue.where(RequestScopes.TENANT_ID, "tenant_abc")
      .where(RequestScopes.MEMBER_ID, id)
      .where(RequestScopes.ORG_ROLE, "admin")
      .run(() -> {
        assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("tenant_abc");
        assertThat(RequestScopes.MEMBER_ID.get()).isEqualTo(id);
        assertThat(RequestScopes.ORG_ROLE.get()).isEqualTo("admin");
      });
}
```

---

## Slice C — Enable Virtual Threads (Optional / Future)

### C1. Add configuration property

**File**: `backend/src/main/resources/application.yml`

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### C2. Full regression test

Run the entire test suite (`./mvnw test`) on virtual threads. Spring Boot 4 automatically uses virtual threads for Tomcat and `@Async` when this property is set.

### C3. Verify no InheritableThreadLocal leaks

Ensure no third-party libraries use `InheritableThreadLocal` in ways that break under virtual threads. MDC (SLF4J) is the main concern — Logback 1.4+ supports virtual threads via `MDCAdapter`.

---

## Update backend/CLAUDE.md

After implementation, update these sections:

### Multitenancy section
Replace:
> **Tenant resolution**: JWT `o.id` claim → `org_schema_mapping` lookup → `TenantContext` ThreadLocal → Hibernate `search_path`

With:
> **Tenant resolution**: JWT `o.id` claim → `org_schema_mapping` lookup → `RequestScopes.TENANT_ID` ScopedValue → Hibernate `search_path`

### Anti-Patterns section
Add:
> - Never use `ThreadLocal` for request-scoped context — use `ScopedValue` via `RequestScopes` (guaranteed cleanup, virtual thread safe)
> - Never call `RequestScopes.TENANT_ID.get()` without checking `isBound()` first (or accepting `NoSuchElementException`)

### Multitenancy in Tests section
Replace:
```java
try {
  TenantContext.setTenantId("tenant_test123");
  // perform operations
} finally {
  TenantContext.clear();
}
```

With:
```java
ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test123").run(() -> {
  // perform operations — auto-cleans up
});
```

---

## File Impact Matrix

| File | Slice | Action | Changes |
|------|-------|--------|---------|
| `multitenancy/RequestScopes.java` | A1 | **CREATE** | New — central ScopedValue declarations |
| `multitenancy/ScopedFilterChain.java` | A2 | **CREATE** | New — checked exception bridge for filters |
| `multitenancy/TenantFilter.java` | A3 | **MODIFY** | Replace try-finally with `ScopedValue.where().run()` |
| `multitenancy/TenantIdentifierResolver.java` | A4 | **MODIFY** | Read from `RequestScopes.TENANT_ID` instead of `TenantContext` |
| `multitenancy/TenantLoggingFilter.java` | A5, B2 | **MODIFY** | Read tenant from `RequestScopes` (A5), read member from `RequestScopes` (B2) |
| `member/MemberFilter.java` | A6, B1 | **MODIFY** | Read tenant from `RequestScopes` (A6), bind member via `ScopedValue` (B1) |
| `member/MemberSyncService.java` | A7 | **MODIFY** | Replace try-finally with `ScopedValue.where().call()` |
| `multitenancy/TenantContext.java` | A8 | **DELETE** | Dead code after migration |
| `project/ProjectController.java` | B3 | **MODIFY** | Read from `RequestScopes` instead of `MemberContext` |
| `document/DocumentController.java` | B4 | **MODIFY** | Read from `RequestScopes` instead of `MemberContext` |
| `member/ProjectMemberController.java` | B5 | **MODIFY** | Read from `RequestScopes` instead of `MemberContext` |
| `member/MemberContext.java` | B6 | **DELETE** | Dead code after migration |
| `multitenancy/TenantContextTest.java` | A9 | **RENAME+REWRITE** | → `RequestScopesTest.java` |
| `member/MemberFilterIntegrationTest.java` | A9 | **MODIFY** | Replace `TenantContext` try-finally with `ScopedValue.where().run()` |
| `backend/CLAUDE.md` | Post | **MODIFY** | Update multitenancy docs, anti-patterns, test patterns |

**Total**: 2 new files, 10 modified files, 2 deleted files, 1 renamed

---

## Verification Checklist

### After Slice A
- [ ] `./mvnw test` — all 160+ tests pass
- [ ] `TenantContext.java` has zero references in codebase (grep confirms deletion is safe)
- [ ] `TenantIdentifierResolver` returns correct schema for API requests (existing integration tests cover this)
- [ ] `MemberSyncService` sync/delete operations work via integration tests
- [ ] Internal endpoints (`/internal/**`) still function (no tenant bound — Hibernate falls back to "public")

### After Slice B
- [ ] `./mvnw test` — all 160+ tests pass
- [ ] `MemberContext.java` has zero references in codebase
- [ ] All controllers correctly read member ID and org role from `RequestScopes`
- [ ] No `null` member ID reaches controllers (filter binds it, controller checks `isBound()`)

### After Slice C (if pursued)
- [ ] `./mvnw test` with `spring.threads.virtual.enabled=true`
- [ ] No `ThreadLocal` leaks under sustained load (verify via heap dump or logging)
- [ ] MDC propagation works correctly on virtual threads

---

## Agent Delegation Strategy

This plan supports two delegation approaches:

### Option 1: Single Agent (Sequential)
One agent implements Slice A → runs tests → implements Slice B → runs tests → updates CLAUDE.md.

### Option 2: Two Agents (Parallel, then Sequential)
- **Agent 1**: Implements A1 (RequestScopes) + A2 (ScopedFilterChain) — these are new files with no conflicts
- **Agent 2**: Waits for Agent 1, then implements A3-A9 + B1-B7 sequentially (depends on new files)

Option 1 is recommended because the changes are tightly coupled (each step feeds the next) and the total surface area is small (~15 files). Parallel agents risk merge conflicts on shared files like `TenantLoggingFilter` and `MemberFilter`.
