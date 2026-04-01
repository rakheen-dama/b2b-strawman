# ScopedValue Over ThreadLocal: Preparing for Virtual Threads in Spring Boot 4

*Part 2 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

In a multi-tenant system, every request needs to carry context: which tenant is this for? Which user is making it? What permissions do they have?

The traditional answer in Java is `ThreadLocal`. Set the tenant ID at the start of the request, read it wherever you need it, clear it at the end. It's simple, it's well-understood, and it's been the standard approach for 20 years.

It's also a trap if you're planning to use virtual threads.

Java 25 ships `ScopedValue` as a final feature (JEP 506). It's the replacement for `ThreadLocal` in structured concurrency. And for multi-tenant applications, it solves problems you might not realize you have.

## The ThreadLocal Problem

Here's what multi-tenant context typically looks like with `ThreadLocal`:

```java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

And in a servlet filter:

```java
try {
    TenantContext.setTenantId(resolvedSchema);
    filterChain.doFilter(request, response);
} finally {
    TenantContext.clear();
}
```

This works. Until it doesn't.

**Problem 1: Forgetting to clear.** If `filterChain.doFilter()` throws an unexpected exception and your `finally` block doesn't execute (rare, but possible with `Error` subclasses), the ThreadLocal leaks to the next request on the same thread. In a pooled thread model, the next request inherits the previous tenant's context. This is a data breach.

**Problem 2: Virtual threads don't reuse.** With virtual threads (`spring.threads.virtual.enabled=true`), the JVM creates millions of lightweight threads. Each virtual thread gets its own `ThreadLocal` storage. But virtual threads are mounted on carrier threads, and `ThreadLocal` can leak across mount/unmount boundaries in certain edge cases with structured concurrency.

**Problem 3: No inheritance control.** `ThreadLocal` values are inherited by child threads (via `InheritableThreadLocal`). In a virtual thread world with `CompletableFuture` or structured concurrency, you don't want tenant context to propagate to background tasks unless you explicitly intend it.

**Problem 4: Mutable state.** Any code can call `TenantContext.setTenantId()` at any time. There's no structural guarantee that the context was set by the filter and only the filter. A bug in a service method could accidentally overwrite the tenant context.

## The ScopedValue Replacement

`ScopedValue` (JEP 506, final in Java 25) is designed for exactly this use case: request-scoped context in concurrent applications.

Here's what my multi-tenant context looks like:

```java
public final class RequestScopes {

    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<UUID> MEMBER_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> ORG_ROLE = ScopedValue.newInstance();
    public static final ScopedValue<String> ORG_ID = ScopedValue.newInstance();
    public static final ScopedValue<Set<String>> CAPABILITIES = ScopedValue.newInstance();

    public static UUID requireMemberId() {
        if (!MEMBER_ID.isBound()) {
            throw new MemberContextNotBoundException();
        }
        return MEMBER_ID.get();
    }

    public static String requireTenantId() {
        if (!TENANT_ID.isBound()) {
            throw new IllegalStateException("Tenant context not available");
        }
        return TENANT_ID.get();
    }
}
```

And binding happens through lambdas, not set/clear:

```java
ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .where(RequestScopes.ORG_ID, orgId)
    .run(() -> filterChain.doFilter(request, response));
```

When the lambda exits — normally or via exception — the bindings are gone. No `finally` block. No `clear()`. No leak. The scope is structural, not procedural.

## The Key Differences

| Aspect | ThreadLocal | ScopedValue |
|--------|------------|-------------|
| **Lifetime** | Until `remove()` is called | Until the lambda exits |
| **Cleanup** | Manual (`finally` block) | Automatic (scope-based) |
| **Mutability** | Mutable (`set()` anytime) | Immutable once bound |
| **Inheritance** | Implicit (InheritableThreadLocal) | Explicit (must rebind) |
| **Cost** | O(n) per thread, where n = ThreadLocal count | O(1) per binding |
| **Virtual threads** | Works but risks leaking across mount/unmount | Designed for virtual threads |

The immutability point is critical for multi-tenancy. Once `TenantFilter` binds `TENANT_ID`, no code in the request pipeline can change it. Not a service method. Not a repository. Not a bug. The binding is structurally guaranteed by the language.

## The Servlet Filter Bridge

There's one wrinkle: `ScopedValue.Carrier.run()` takes a `Runnable`, which can't throw checked exceptions. Servlet filters throw `IOException` and `ServletException`. You need a bridge.

Here's mine:

```java
public final class ScopedFilterChain {

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

    private static final class WrappedIOException extends RuntimeException {
        final IOException wrapped;
        WrappedIOException(IOException e) { this.wrapped = e; }
    }

    private static final class WrappedServletException extends RuntimeException {
        final ServletException wrapped;
        WrappedServletException(ServletException e) { this.wrapped = e; }
    }
}
```

Not pretty. But it's written once and used everywhere. The alternative — using `ScopedValue.Carrier.call()` with a `Callable` — works for service methods but not for the filter chain because `FilterChain.doFilter()` has a void return.

## The Filter Chain in Practice

Here's how the actual `TenantFilter` binds context:

```java
@Override
protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth instanceof JwtAuthenticationToken jwtAuth) {
        String orgId = JwtUtils.extractOrgId(jwtAuth.getToken());

        if (orgId != null) {
            String schema = resolveTenant(orgId);
            if (schema != null) {
                // Bind tenant context for the rest of the request
                ScopedFilterChain.runScoped(
                    ScopedValue.where(RequestScopes.TENANT_ID, schema)
                               .where(RequestScopes.ORG_ID, orgId),
                    filterChain, request, response);
                return;
            }
        }
    }

    // No JWT or no org — continue without tenant context
    filterChain.doFilter(request, response);
}
```

And `MemberFilter` builds on top of it — it reads the already-bound `TENANT_ID` and adds member context:

```java
@Override
protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

    String tenantId = RequestScopes.getTenantIdOrNull();

    if (tenantId != null) {
        MemberInfo info = resolveMember(tenantId);
        if (info != null) {
            Set<String> capabilities = orgRoleService.resolveCapabilities(info.memberId());

            ScopedFilterChain.runScoped(
                ScopedValue.where(RequestScopes.MEMBER_ID, info.memberId())
                           .where(RequestScopes.ORG_ROLE, info.orgRole())
                           .where(RequestScopes.CAPABILITIES, capabilities),
                filterChain, request, response);
            return;
        }
    }

    filterChain.doFilter(request, response);
}
```

The filter chain nests naturally. `TenantFilter` binds `TENANT_ID`, calls next. `MemberFilter` reads `TENANT_ID`, binds `MEMBER_ID`, calls next. By the time a controller runs, all five scoped values are bound. When the request completes, they all unbind automatically as the lambdas exit.

## Reading Context in Services

Controllers and services read the context through the static `RequestScopes` class:

```java
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        // MEMBER_ID is guaranteed to be bound by MemberFilter
        // TENANT_ID is guaranteed to be bound by TenantFilter
        // No null checks needed — the filter chain ensures binding
        return projectService.findById(id);
    }
}
```

In services that need the current member (for audit trails, ownership checks):

```java
@Service
public class ProjectService {

    @Transactional
    public Project create(CreateProjectRequest request) {
        UUID memberId = RequestScopes.requireMemberId();

        var project = new Project(request.name(), request.description(), memberId);
        var saved = projectRepository.save(project);

        auditService.log(AuditEventBuilder.create("PROJECT_CREATED")
            .entity("Project", saved.getId())
            .detail("name", saved.getName())
            .build());

        return saved;
    }
}
```

`requireMemberId()` throws `MemberContextNotBoundException` if called from a code path where the filter hasn't bound the context (like an internal endpoint or a scheduled job). This is a compile-time-like safety check — it fails fast with a clear error instead of returning null and causing an NPE later.

## Testing with ScopedValue

Here's the test pattern. No `@BeforeEach` / `@AfterEach` cleanup needed:

```java
@Test
void shouldCreateProject() {
    ScopedValue.where(RequestScopes.TENANT_ID, testSchema)
        .where(RequestScopes.MEMBER_ID, testMemberId)
        .run(() -> {
            var request = new CreateProjectRequest("Test Project", "Description");
            var project = projectService.create(request);

            assertThat(project.getName()).isEqualTo("Test Project");
            assertThat(project.getCreatedBy()).isEqualTo(testMemberId);
        });
    // Bindings are automatically cleaned up here
    // No @AfterEach needed, no try-finally, no ThreadLocal.remove()
}
```

Compare this to the ThreadLocal version:

```java
@BeforeEach
void setUp() {
    TenantContext.setTenantId(testSchema);
    MemberContext.setMemberId(testMemberId);
}

@AfterEach
void tearDown() {
    TenantContext.clear();
    MemberContext.clear();
}

@Test
void shouldCreateProject() {
    var request = new CreateProjectRequest("Test Project", "Description");
    var project = projectService.create(request);
    assertThat(project.getName()).isEqualTo("Test Project");
}
```

The ThreadLocal version is fewer lines. But if a test throws an unexpected exception and `@AfterEach` doesn't run (possible in integration tests with container failures), the context leaks to the next test. With ScopedValue, this is structurally impossible.

## Internal Endpoints: The Edge Case

Not all endpoints have JWT context. Internal endpoints (called by the frontend's webhook handler, by scheduled jobs, or by the provisioning pipeline) need to operate in a specific tenant's schema without a JWT.

For these, I use `ScopedValue.where().call()` explicitly:

```java
@Service
public class MemberSyncService {

    public Member syncMember(String schema, String userId, String email, String name) {
        return ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .call(() -> {
                // This runs in the tenant's schema context
                return memberRepository.findByExternalUserId(userId)
                    .map(member -> updateExisting(member, email, name))
                    .orElseGet(() -> createNew(userId, email, name));
            });
    }
}
```

`call()` supports checked exceptions (via `CallableOp`), so service methods that throw domain exceptions work naturally.

## The Virtual Threads Payoff

All of this `ScopedValue` machinery isn't just about cleanliness — it's preparing for virtual threads.

With `spring.threads.virtual.enabled=true` (one line of config in Spring Boot 4), every request gets a virtual thread instead of a platform thread. Virtual threads are cheap — you can have millions of them. But they're scheduled across a small pool of carrier threads, and `ThreadLocal` state can behave unexpectedly across carrier-thread migrations.

`ScopedValue` is designed for this model. The bindings are scoped to the logical task (the lambda), not the physical thread. Whether the virtual thread migrates between carrier threads or not, the bindings stay correct.

I haven't flipped the switch yet (it's a one-line change, and I want to do it with load testing). But the architecture is ready. Every piece of request-scoped context uses `ScopedValue`. There are zero `ThreadLocal` instances in the multitenancy stack.

When I do enable virtual threads, nothing changes in the application code. That's the point.

---

*Next in this series: [Tenant Provisioning: From Webhook to Working Schema in Under 2 Seconds](03-tenant-provisioning.md)*

*Previous: [Schema-Per-Tenant in 2026](01-schema-per-tenant-vs-rls.md)*
