# Technical Evaluation: ScopedValue vs ThreadLocal for Tenant & Member Context

**Date**: 2026-02-09
**Java Version**: 25
**Spring Boot Version**: 4.0.2
**Status**: Evaluation only — no code changes proposed

---

## Executive Summary

**ScopedValue is final in Java 25** (JEP 506) and is a strong candidate to replace our `TenantContext` and `MemberContext` ThreadLocal usage. The migration is **feasible and recommended**, but should be staged carefully because Hibernate's `CurrentTenantIdentifierResolver` reads from a static context holder, which requires a bridging strategy.

The primary motivation — Virtual Thread compatibility — is valid. ThreadLocal is the #1 footgun when adopting virtual threads at scale, and our multitenancy architecture is the exact use case ScopedValue was designed for.

---

## 1. Current ThreadLocal Architecture

### Context Classes

| Class | ThreadLocal Fields | Set By | Read By |
|-------|-------------------|--------|---------|
| `TenantContext` | `CURRENT_TENANT` (String) | `TenantFilter`, `MemberSyncService` | `TenantIdentifierResolver`, `TenantLoggingFilter`, `MemberFilter` |
| `MemberContext` | `CURRENT_MEMBER_ID` (UUID), `CURRENT_ORG_ROLE` (String) | `MemberFilter` | Controllers (`ProjectController`, `DocumentController`, `ProjectMemberController`), `TenantLoggingFilter` |

### Request Lifecycle (Current)

```
HTTP Request
  → ApiKeyAuthFilter (internal only)
  → BearerTokenAuthFilter + ClerkJwtAuthenticationConverter
  → TenantFilter
      TenantContext.setTenantId(schema)        ← ThreadLocal SET
      try { filterChain.doFilter() }
      finally { TenantContext.clear() }        ← ThreadLocal CLEAR
        → MemberFilter
            MemberContext.setCurrentMemberId()  ← ThreadLocal SET
            MemberContext.setOrgRole()          ← ThreadLocal SET
            try { filterChain.doFilter() }
            finally { MemberContext.clear() }   ← ThreadLocal CLEAR
              → TenantLoggingFilter (reads both contexts → MDC)
                → DispatcherServlet → Controller → Service → Repository
                    ↑ TenantIdentifierResolver.resolveCurrentTenantIdentifier()
                      reads TenantContext.getTenantId()
                      → Hibernate sets search_path on connection
```

### Internal Endpoint Pattern (Webhooks)

```
POST /internal/orgs/webhook
  → ApiKeyAuthFilter (authenticates via API key)
  → TenantFilter SKIPPED (path excluded)
  → MemberFilter SKIPPED (path excluded)
  → Controller → MemberSyncService
      resolveSchema(clerkOrgId)               ← queries public schema
      TenantContext.setTenantId(schema)        ← MANUAL ThreadLocal SET
      try {
        transactionTemplate.execute(...)       ← REQUIRES_NEW transaction
      } finally {
        TenantContext.clear()                  ← MANUAL ThreadLocal CLEAR
      }
```

### Key Observations

1. **No cross-thread propagation** — all context usage is single-threaded, synchronous
2. **try-finally cleanup everywhere** — disciplined but fragile (one missed `clear()` = data leak)
3. **Two patterns**: filter-based (automatic) and manual (internal endpoints)
4. **Hibernate coupling** — `TenantIdentifierResolver` reads `TenantContext` statically

---

## 2. ScopedValue in Java 25

### Status: Final (JEP 506)

| JDK | JEP | Status |
|-----|-----|--------|
| 20 | 429 | Incubator |
| 21 | 446 | 1st Preview |
| 22 | 464 | 2nd Preview |
| 23 | 481 | 3rd Preview |
| 24 | 487 | 4th Preview |
| **25** | **506** | **Final** |

Package: `java.lang.ScopedValue` — no special modules, flags, or `--enable-preview` needed.

### Core API

```java
// Declare (static final, immutable)
private static final ScopedValue<String> TENANT = ScopedValue.newInstance();

// Bind + execute
ScopedValue.where(TENANT, "tenant_abc123").run(() -> {
    TENANT.get();        // "tenant_abc123"
    TENANT.isBound();    // true
});
// TENANT is unbound here — automatic cleanup

// Multiple bindings
ScopedValue.where(TENANT, "abc")
           .where(USER_ID, "user_42")
           .run(() -> { ... });

// Nested rebinding (inner scope shadows outer)
ScopedValue.where(TENANT, "outer").run(() -> {
    ScopedValue.where(TENANT, "inner").run(() -> {
        TENANT.get(); // "inner"
    });
    TENANT.get(); // "outer" — automatically restored
});
```

### What ScopedValue Cannot Do

| Operation | ThreadLocal | ScopedValue |
|-----------|-------------|-------------|
| `set()` mid-execution | ✅ `threadLocal.set(v)` | ❌ No `set()` method exists |
| Mutable values | ✅ Any code can write | ❌ Immutable binding |
| Bind outside lambda | ✅ `set()` anywhere | ❌ Must use `where().run()` or `where().call()` |
| AutoCloseable pattern | ✅ try-with-resources | ❌ Lambda-only (by design) |
| `orElse(null)` | N/A | ❌ Throws `NullPointerException` in Java 25 |

---

## 3. Why ThreadLocal Is Problematic for Virtual Threads

### Memory Amplification

ThreadLocal stores values in a per-thread `ThreadLocalMap`. With platform threads (~100s), this is fine. With virtual threads (~millions), every thread gets its own map:

| Scenario | ThreadLocal Memory | ScopedValue Memory |
|----------|-------------------|-------------------|
| 10,000 virtual threads, 3 context values | ~2.4 MB of maps | ~0 (shared bindings) |
| 1,000,000 virtual threads, 3 context values | ~240 MB of maps | ~0 (shared bindings) |

ScopedValue bindings are **shared by reference** across all child threads forked within a `StructuredTaskScope`. No copying occurs.

### InheritableThreadLocal Is Worse

If we ever used `InheritableThreadLocal` for context propagation to child threads, each child would **deep-copy** the parent's map. With virtual threads, this is O(n × m) where n = thread count and m = context entries.

ScopedValue inheritance via `StructuredTaskScope` is O(1) — it's a pointer to the parent's binding chain.

### Lifecycle Risks

| Risk | ThreadLocal | ScopedValue |
|------|-------------|-------------|
| Forgotten `remove()` | Silent memory/data leak | Impossible — auto-cleanup |
| Stale data from pooled thread | Possible if `remove()` missed | Impossible — scope-bounded |
| Cross-request data bleed | Possible | Impossible |

---

## 4. Impact Analysis for Our Codebase

### 4A. Filter Chain — Natural Fit ✅

Our `TenantFilter` and `MemberFilter` already wrap `filterChain.doFilter()` in try-finally. This maps directly to `ScopedValue.where().run()`:

**Current (ThreadLocal):**
```java
// TenantFilter.java
try {
    TenantContext.setTenantId(schemaName);
    filterChain.doFilter(request, response);
} finally {
    TenantContext.clear();
}
```

**Proposed (ScopedValue):**
```java
// TenantFilter.java
ScopedValue.where(TENANT_ID, schemaName).run(() -> {
    try {
        filterChain.doFilter(request, response);
    } catch (ServletException | IOException e) {
        throw new RuntimeException(e);  // or use call() with CallableOp
    }
});
```

**Complication**: `filterChain.doFilter()` throws checked exceptions (`IOException`, `ServletException`), but `Runnable.run()` does not. Requires wrapping in unchecked exceptions or using `call()` with a custom `CallableOp`.

### 4B. Hibernate TenantIdentifierResolver — Requires Bridge ⚠️

`TenantIdentifierResolver` implements Hibernate's `CurrentTenantIdentifierResolver<String>` interface:

```java
@Override
public String resolveCurrentTenantIdentifier() {
    String tenantId = TenantContext.getTenantId();  // reads ThreadLocal
    return tenantId != null ? tenantId : "public";
}
```

Hibernate calls this method on its own schedule (when opening sessions, getting connections). It reads from a static context. With ScopedValue, the binding would need to be accessible from this call site.

**Bridge strategy**: `TenantIdentifierResolver` would read from the ScopedValue directly:

```java
@Override
public String resolveCurrentTenantIdentifier() {
    return RequestScopes.TENANT_ID.isBound()
        ? RequestScopes.TENANT_ID.get()
        : "public";
}
```

This works because:
- OSIV is disabled (`spring.jpa.open-in-view: false`) — no premature EntityManager creation
- Hibernate obtains connections within the request thread, inside the filter chain
- The `ScopedValue.where().run()` from `TenantFilter` is still on the call stack when Hibernate asks for the tenant

### 4C. Controllers — Simplified ✅

Controllers currently read `MemberContext` statically and handle null defensively:

```java
UUID memberId = MemberContext.getCurrentMemberId();
if (memberId == null) {
    return ResponseEntity.status(500).build();
}
```

With ScopedValue:

```java
UUID memberId = RequestScopes.MEMBER_ID.get();  // throws NoSuchElementException if unbound
```

The null check becomes unnecessary — if the filter chain ran correctly, the value is always bound. If it didn't, `NoSuchElementException` is clearer than a silent null. A global `@ExceptionHandler` could map this to 500.

### 4D. Internal Endpoints (MemberSyncService) — Cleaner ✅

The manual set/clear pattern becomes a scoped block:

**Current:**
```java
try {
    TenantContext.setTenantId(schemaName);
    return txTemplate.execute(status -> { ... });
} finally {
    TenantContext.clear();
}
```

**Proposed:**
```java
return ScopedValue.where(RequestScopes.TENANT_ID, schemaName).call(() ->
    txTemplate.execute(status -> { ... })
);
```

No `finally` block needed. No risk of forgotten `clear()`.

### 4E. Tests — Minor Updates ⚠️

Tests that manually set `TenantContext` for assertions would need to wrap in `ScopedValue.where().run()`:

**Current:**
```java
try {
    TenantContext.setTenantId(schemaName);
    var member = memberRepository.findByClerkUserId("user_xyz");
    assertThat(member).isPresent();
} finally {
    TenantContext.clear();
}
```

**Proposed:**
```java
ScopedValue.where(RequestScopes.TENANT_ID, schemaName).run(() -> {
    var member = memberRepository.findByClerkUserId("user_xyz");
    assertThat(member).isPresent();
});
```

### 4F. MDC Logging — Unchanged ✅

`TenantLoggingFilter` reads the context and writes to MDC. MDC itself uses ThreadLocal (SLF4J's implementation). Our filter would just read from ScopedValue instead:

```java
String tenantId = RequestScopes.TENANT_ID.isBound()
    ? RequestScopes.TENANT_ID.get()
    : null;
if (tenantId != null) MDC.put("tenantId", tenantId);
```

---

## 5. Spring Framework Considerations

### Spring's Own ThreadLocal Usage

Spring Framework uses ThreadLocal extensively in its own internals. **None of these will change**:

| Spring Component | Uses ThreadLocal? | Impact |
|-----------------|-------------------|--------|
| `SecurityContextHolder` | Yes | No impact — we don't replace this |
| `RequestContextHolder` | Yes | No impact — we don't use this directly |
| `TransactionSynchronizationManager` | Yes | No impact — Spring manages this |
| `LocaleContextHolder` | Yes | No impact — we don't use this directly |

Our migration only affects **our own** context classes (`TenantContext`, `MemberContext`). Spring's internal ThreadLocal usage is orthogonal.

### Spring Framework ScopedValue Support

- **Spring has no built-in ScopedValue support** as of Spring Boot 4.0.x
- **`HandlerInterceptor` is incompatible** with ScopedValue (Spring issue #32837, closed as "NOT PLANNED")
- **Servlet `Filter` works perfectly** — we already use `OncePerRequestFilter`, which is the correct pattern
- No `@ScopedValue` annotation or auto-configuration exists in Spring

### Virtual Thread Support in Spring Boot 4

Spring Boot 4 supports virtual threads via `spring.threads.virtual.enabled=true`. When enabled:
- Tomcat uses virtual threads for request handling
- `@Async` methods run on virtual threads
- Scheduled tasks run on virtual threads

Our ScopedValue migration would be a prerequisite for safely enabling this property.

---

## 6. Proposed Architecture

### Central Scopes Declaration

```java
/**
 * Request-scoped values for multitenancy and member identity.
 * Bound by servlet filters, read by controllers/services/Hibernate.
 */
public final class RequestScopes {
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<UUID>   MEMBER_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> ORG_ROLE  = ScopedValue.newInstance();

    private RequestScopes() {}
}
```

Bundling into a record is also an option (reduces cache pressure if >16 ScopedValues):

```java
public record RequestContext(String tenantId, UUID memberId, String orgRole) {}
public static final ScopedValue<RequestContext> CTX = ScopedValue.newInstance();
```

However, since `TenantFilter` binds `TENANT_ID` before `MemberFilter` binds `MEMBER_ID` and `ORG_ROLE`, separate ScopedValues may be more natural than a single record.

### Filter Chain (Nested Binding)

```
TenantFilter:
  ScopedValue.where(TENANT_ID, schema).run(() ->
    filterChain.doFilter(...)
      MemberFilter:
        ScopedValue.where(MEMBER_ID, id).where(ORG_ROLE, role).run(() ->
          filterChain.doFilter(...)
            TenantLoggingFilter reads TENANT_ID, MEMBER_ID → MDC
              Controller reads MEMBER_ID, ORG_ROLE
                Service → Repository
                  Hibernate → TenantIdentifierResolver reads TENANT_ID
  )  ← MEMBER_ID, ORG_ROLE automatically unbound
)    ← TENANT_ID automatically unbound
```

---

## 7. Migration Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Checked exception wrapping in `run()` | Low | Use a helper method or `call()` with `CallableOp` |
| Hibernate calling resolver outside filter scope | Medium | Verify with OSIV disabled; add defensive `isBound()` check with "public" fallback |
| Test code refactoring | Low | Mechanical change: try-finally → `ScopedValue.where().run()` |
| Spring Security context still on ThreadLocal | None | Orthogonal — we don't replace Spring's contexts |
| Performance regression | None expected | ScopedValue `get()` is ≥ as fast as `ThreadLocal.get()` due to JIT optimizations on immutable values |
| Future Spring Boot updates breaking pattern | Low | Servlet filter pattern is stable; no Spring-specific ScopedValue integration to break |

---

## 8. Pros and Cons

### Pros

| Benefit | Details |
|---------|---------|
| **Virtual Thread ready** | Primary motivation. Eliminates ThreadLocal memory amplification (O(n) → O(1) per thread) and data leak risks with ephemeral virtual threads |
| **Guaranteed cleanup** | No `finally { clear() }` blocks. Binding ends when `run()`/`call()` exits — even on exceptions, including `StackOverflowError` |
| **Immutability** | No code can accidentally `set()` a different tenant mid-request. Eliminates an entire class of bugs |
| **Structured lifetime** | Binding scope is visible in code structure (the lambda). Easier to reason about "where is this value alive?" |
| **Future-proof** | ScopedValue + StructuredTaskScope is the intended path for context propagation in modern Java. Aligns with Project Loom's design |
| **Cleaner internal endpoint pattern** | `ScopedValue.where(TENANT_ID, schema).call(...)` replaces manual set/clear/try-finally |
| **Better for structured concurrency** | If we ever adopt `StructuredTaskScope` for parallel operations, ScopedValue bindings propagate automatically to forked subtasks |

### Cons

| Drawback | Details |
|----------|---------|
| **Lambda wrapping overhead** | Every binding requires a lambda (`run()` / `call()`). Slightly more verbose for simple cases. Checked exception wrapping adds noise in filters |
| **Cannot set mid-execution** | No `set()` method. If we ever needed to change tenant mid-request (unlikely but possible), we'd need a nested `where().run()` block |
| **Spring has no native support** | No `@ScopedValue` injection, no auto-configuration. We manage bindings ourselves in filters |
| **Hibernate bridge required** | `TenantIdentifierResolver` must be updated to read from ScopedValue. Must verify Hibernate never calls it outside the scoped binding |
| **Test helper changes** | All tests using `TenantContext.setTenantId()` need refactoring to use `ScopedValue.where().run()` pattern |
| **Team learning curve** | ScopedValue is a new concept. Lambda-scoping paradigm differs from the imperative set/get/clear pattern |
| **Partial migration** | Spring's own contexts (Security, Transaction, Request) remain on ThreadLocal. We'd have a hybrid: ScopedValue for ours, ThreadLocal for Spring's |

---

## 9. Recommendation

### Verdict: **Recommended — Stage the Migration**

The migration is feasible, the API is final, and the benefits directly address the stated goal (Virtual Thread compatibility). The codebase is well-suited because:

1. **Filter-based context** — maps 1:1 to `ScopedValue.where().run()`
2. **No async/cross-thread usage** — simplest possible migration path
3. **OSIV disabled** — Hibernate won't call resolver outside the scoped binding
4. **Small surface area** — only 2 context classes, ~12 files to change

### Suggested Staging

| Phase | Scope | Risk |
|-------|-------|------|
| **Phase 1** | Migrate `TenantContext` → ScopedValue. Update `TenantFilter`, `TenantIdentifierResolver`, `MemberSyncService`, `TenantLoggingFilter`, and related tests. Keep `MemberContext` on ThreadLocal. | Low — TenantContext is the simpler of the two (1 field). Validates the pattern with Hibernate. |
| **Phase 2** | Migrate `MemberContext` → ScopedValue. Update `MemberFilter`, all controllers, `TenantLoggingFilter`. | Low — purely read-oriented. No Hibernate coupling. |
| **Phase 3** | Enable `spring.threads.virtual.enabled=true`. Run full test suite on virtual threads. Benchmark under load. | Medium — virtual threads change scheduling behavior. May surface issues in Spring's own ThreadLocal usage or third-party libraries. |
| **Phase 4** (future) | Explore `StructuredTaskScope` for parallel operations (e.g., parallel document processing, batch member sync). ScopedValue context will propagate automatically. | N/A — only when needed |

### Pre-Migration Checklist

- [ ] Verify Hibernate 7 does not call `CurrentTenantIdentifierResolver` during session factory startup (outside any request scope)
- [ ] Confirm no `@Async` or `CompletableFuture` usage that would break the scoped binding chain
- [ ] Create a `RequestScopes` utility class with the ScopedValue declarations
- [ ] Write a helper for checked-exception wrapping in filter `run()` calls
- [ ] Update integration test base classes to use `ScopedValue.where().run()` instead of try-finally

---

## 10. References

- [JEP 506: Scoped Values (Final)](https://openjdk.org/jeps/506)
- [ScopedValue Javadoc (Java SE 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html)
- [Spring Framework Issue #32837: HandlerInterceptor + ScopedValue](https://github.com/spring-projects/spring-framework/issues/32837)
- [Java 25: Virtual Threads with Structured Task Scopes and Scoped Values](https://javapro.io/2025/12/23/java-25-getting-the-most-out-of-virtual-threads-with-structured-task-scopes-and-scoped-values/)
