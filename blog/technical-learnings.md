# We Lost 6 Hours to a 0-Line Bug: Lessons from Building a Multi-Tenant SaaS Platform

We build DocTeams — a multi-tenant B2B SaaS platform — with Spring Boot 4, Java 25, Next.js 16, and PostgreSQL with schema-per-tenant isolation. Over 200 pull requests, 800+ integration tests, and 13 development phases, we've accumulated a collection of lessons that textbooks don't teach.

The most expensive lesson? A YAML file with 32 lines of JWT and S3 configuration — but zero datasource settings — cost us 6 hours of automated build time, 5 zombie JVMs, and a cascade failure that made every single test fail with an identical error.

Configuration properties are not boilerplate. They are architecture.

## The Architecture in 60 Seconds

DocTeams uses schema-per-tenant isolation: each customer organization gets its own PostgreSQL schema (`tenant_a1b2c3d4e5f6`), with a `public` schema for global tables and a `portal` schema for the customer-facing read model. This design requires three separate HikariCP connection pools:

```
App Pool (HikariPool-1)       — Hibernate/JPA, dynamic search_path per tenant
Migration Pool (HikariPool-2) — Flyway DDL, direct PostgreSQL access
Portal Pool (HikariPool-3)    — Customer portal, locked to portal schema
```

Tenant context flows through Java 25's `ScopedValue` (not ThreadLocal), bound by servlet filters and read by Hibernate's `CurrentTenantIdentifierResolver`. OSIV is disabled globally. Virtual threads are enabled. Every service method that touches the database runs inside a `@Transactional` boundary — there is no other option.

Understanding this setup is essential because every lesson below is a consequence of these architectural choices interacting with each other.

## Properties Are Architecture

Here is `application-test.yml` as it existed for 7 months and 150+ pull requests:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://test-issuer.example.com

  jpa:
    open-in-view: false

  flyway:
    enabled: false

internal:
  api:
    key: test-api-key

portal:
  jwt:
    secret: test-portal-secret-must-be-at-least-32-bytes-long
  magic-link:
    secret: test-magic-link-secret-must-be-at-least-32-bytes-long

aws:
  s3:
    endpoint: http://localhost:4566
    region: us-east-1
    bucket-name: test-bucket
  credentials:
    access-key-id: test
    secret-access-key: test
```

Thirty-two lines. JWT issuer, portal secrets, S3 config. Everything the application needed to start up and authenticate requests in tests.

Zero lines about connection pools.

This meant every test run used HikariCP's defaults: no leak detection, no explicit pool sizes, no connection timeouts tuned for Testcontainers. Meanwhile, the local development profile (`application-local.yml`) had leak detection at 30 seconds, explicit pool sizes, and validation timeouts. Production had its own carefully tuned settings.

The test profile was running a fundamentally different application than local dev or production. And we didn't notice — because HikariCP defaults are designed to be reasonable for most applications. They just aren't reasonable for ours.

### The Cascade

The failure started with a missing Maven Surefire setting. Without `forkedProcessExitTimeoutInSeconds`, Surefire waits indefinitely for a forked test JVM to exit. If a test hangs (deadlocked pool, Spring context that won't shut down), the JVM becomes a zombie — holding onto its Testcontainers PostgreSQL container and all three HikariCP pools.

An AI agent was building a feature. It ran Maven, saw a test failure, and retried. The previous JVM didn't exit. The agent retried again. And again. Over 6 hours, five zombie JVMs accumulated:

```
Build 1 (03:44) — Surefire JVM alive, 12 connections, 1 PostgreSQL container (~100MB)
Build 2 (05:36) — Surefire JVM alive, 12 connections, 1 PostgreSQL container
Build 3 (06:52) — Surefire JVM alive, 12 connections, 1 PostgreSQL container
Build 4 (07:54) — Surefire JVM alive, 12 connections, 1 PostgreSQL container
Build 5 (09:07) — New build starts. Docker under memory pressure.
```

Each JVM held 3 HikariCP pools with up to 12 connections total. Each held a Testcontainers PostgreSQL container consuming roughly 100MB of RAM. Testcontainers' Ryuk cleanup container couldn't reap them because the JVMs were technically still alive.

By build 5, Docker was under enough memory pressure that the new PostgreSQL container started slowly. HikariCP's default connection timeout (30 seconds for the migration pool) expired before PostgreSQL was ready to accept connections. The result:

```
HikariPool-2 — Connection is not available, request timed out after 30000ms.
total=0, active=0, idle=0, waiting=0
```

`total=0`. Not `total=10, active=10` (which would indicate a leak). Zero. The pool never initialized at all. Since `HikariPool-2` is the migration pool, `TenantMigrationRunner` couldn't bootstrap the schemas at startup, the Spring context was corrupted, and every test class failed with the identical error — regardless of whether it had anything to do with the changed code.

### The Fix: 16 Lines of YAML

```yaml
spring:
  datasource:
    app:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout: 10000
      leak-detection-threshold: 10000
    migration:
      maximum-pool-size: 2
      connection-timeout: 30000
      leak-detection-threshold: 15000
    portal:
      maximum-pool-size: 3
      minimum-idle: 1
      connection-timeout: 10000
      leak-detection-threshold: 10000
```

And one XML element in `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <forkedProcessExitTimeoutInSeconds>60</forkedProcessExitTimeoutInSeconds>
    </configuration>
</plugin>
```

The leak detection thresholds are more aggressive in tests (10-15 seconds) than in local dev (30 seconds). If a connection is held longer than the threshold, HikariCP logs a warning with the full stack trace of where the connection was acquired. In a CI environment, you want to catch leaks fast.

The `forkedProcessExitTimeoutInSeconds` ensures Surefire force-kills a forked JVM after 60 seconds of inactivity. This triggers JVM shutdown hooks, lets HikariCP close its pools, and lets Ryuk reap the PostgreSQL container. No more zombie accumulation.

**The meta-lesson**: your test profile should be a superset of your production profile's safety nets, not a subset. If production has leak detection, tests need it more.

## ScopedValue Over ThreadLocal

Java 25 finalized `ScopedValue` (JEP 506), and we migrated to it from ThreadLocal for tenant context. The entire migration was a single PR.

Before:

```java
// ThreadLocal — manual lifecycle management
public class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String tenantId) { CURRENT.set(tenantId); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}

// In TenantFilter — try-finally required
try {
    TenantContext.set(schema);
    filterChain.doFilter(request, response);
} finally {
    TenantContext.clear();  // miss this and you leak tenant context
}
```

After:

```java
public final class RequestScopes {
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<UUID>   MEMBER_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> ORG_ROLE  = ScopedValue.newInstance();

    private RequestScopes() {}
}

// In TenantFilter — cleanup is structural, not manual
ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .run(() -> filterChain.doFilter(request, response));
// Value automatically unbound when the lambda exits — no try-finally
```

Three things make this worthwhile beyond aesthetics:

**Immutability within scope.** A `ScopedValue` binding cannot be mutated once set. With ThreadLocal, any code in the call chain could call `TenantContext.set("wrong_tenant")` and silently redirect all subsequent queries to the wrong schema. With ScopedValue, the binding is fixed for the duration of the lambda. You can rebind via a nested `ScopedValue.where().run()`, but that creates a new scope — the outer scope's value is unchanged.

**Virtual thread safety.** With `spring.threads.virtual.enabled: true`, Tomcat spawns a virtual thread per request. ThreadLocal has O(n) memory overhead per virtual thread because each thread copies the parent's `ThreadLocalMap`. ScopedValue bindings are shared (read-only), so the overhead is O(1). For a server handling thousands of concurrent requests, this matters.

**Test simplicity.** Tests no longer need `@AfterEach` cleanup:

```java
// Before — forget the cleanup and the next test inherits a stale tenant
@AfterEach void cleanup() { TenantContext.clear(); }

// After — scope is structural
ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test123").run(() -> {
    // test code — auto-cleans up when lambda exits
});
```

The one complication: servlet filters throw checked exceptions (`IOException`, `ServletException`), but `ScopedValue.Carrier.run()` takes a `Runnable`. We wrote a small `ScopedFilterChain` helper that wraps checked exceptions in private `RuntimeException` subclasses and unwraps them on the other side. It's 30 lines of code that we've never had to touch since.

## OSIV: The Silent Tenant Leak

Disabling Open Session in View (`spring.jpa.open-in-view: false`) is often presented as a performance optimization. In a schema-per-tenant application, it's a security requirement.

Here's why. With OSIV enabled, Spring creates an `EntityManager` at the start of every HTTP request — before the controller, before the service, before your tenant filter has even run. This EntityManager is bound to whatever `search_path` is active at that moment. If the tenant filter hasn't executed yet, the EntityManager pins to `public`.

Every query for that request — even queries inside `@Transactional` methods that run after the tenant filter — uses that initial EntityManager. They all hit `public` instead of the tenant's schema.

The failure mode is subtle. Your tenant filter correctly sets `search_path` to `tenant_abc123`. Your `SchemaMultiTenantConnectionProvider` dutifully switches schemas on connection checkout. But the EntityManager was created before any of that happened, and it holds its own JDBC connection — pinned to `public` from the start.

For `/internal/**` endpoints (which skip the tenant filter entirely and set tenant context programmatically), OSIV is even worse: the EntityManager pins to `public` and stays there because there's no filter chain to rebind it.

The fix is two words: `open-in-view: false`. With OSIV disabled, the EntityManager is only created inside `@Transactional` boundaries, after the filter chain has bound the tenant context. The connection is held for milliseconds instead of the entire request lifecycle. Pool utilization improves because 5 connections can serve dozens of concurrent requests when transactions are fast.

The tradeoff: lazy loading outside a transaction throws `LazyInitializationException`. We handle this by not using JPA relationships at all — which brings us to the next section.

## The N+1 That Wasn't

Every entity in DocTeams uses UUID foreign key columns instead of JPA relationships:

```java
// What we do
@Column(name = "project_id", nullable = false)
private UUID projectId;

// What we never do
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id")
private Project project;
```

This is deliberate, and it solves three problems at once.

**No hidden N+1 queries.** With `@ManyToOne(fetch = LAZY)`, accessing `timeEntry.getProject().getName()` inside a loop triggers a query per iteration — and it's invisible in the service code. With a UUID field, there's no object graph to traverse. If you need the project name, you explicitly load it:

```java
var projectIds = entries.stream().map(TimeEntry::getProjectId).distinct().toList();
var projectMap = projectRepository.findAllById(projectIds).stream()
    .collect(Collectors.toMap(Project::getId, p -> p));

entries.forEach(e -> {
    var project = projectMap.get(e.getProjectId());
    // ...
});
```

It's more code. But the data access pattern is visible. Every query is in the service method, not hiding behind a proxy object. A code review can see exactly how many queries a method executes.

**No `LazyInitializationException`.** Since OSIV is disabled, any lazy-loaded relationship accessed outside a transaction throws. With UUID fields, there's nothing to lazy-load. DTOs and projections carry the data out of the transaction boundary.

**Simpler multitenancy.** JPA relationships introduce cascading operations and implicit fetches that interact with schema switching in unpredictable ways. A `@ManyToOne` fetch might trigger a query at an unexpected point in the transaction lifecycle. UUID fields keep database access explicit and transactional.

The batch-loading pattern (load all related entities upfront, build a `Map`, iterate) is the standard alternative to N+1 with JPA relationships. It requires 2-3 extra lines per service method. We have reference implementations across the codebase — `MyWorkService`, `ActivityService`, `DashboardService` — that all follow the same pattern.

Hibernate's `default_batch_fetch_size: 16` ensures that `findAllById()` with large lists gets batched into reasonable SQL `IN` clauses rather than one query per ID.

## What AI Agents Taught Us About Build Infrastructure

DocTeams is built primarily by AI agents (Claude Code) operating in a scout-builder pipeline. A scout agent reads documentation and codebases, writes a brief, and hands it off to a builder agent that implements the feature. The scout's context is discarded after the brief is written — keeping context windows clean but creating blind spots.

This is relevant because agents interact with build systems fundamentally differently than humans.

**Agents retry mechanically.** A human developer who sees a test failure, retries, sees the same failure, and then notices their laptop fan screaming will think "something is wrong with my environment." An agent reads the log output. It sees "38 tests failed." It doesn't see memory pressure, doesn't hear the fan, doesn't notice that `docker stats` shows 5 PostgreSQL containers consuming 500MB. It retries.

**Zombie process accumulation is invisible to log-based reasoning.** The agent reads Maven's output. Maven's output says "tests failed." It doesn't say "by the way, the JVM from your last build is still alive and holding 12 database connections." The agent's entire world model is the text in its context window. Background processes exist outside that model.

**The 3-retry lesson.** We now cap Maven build retries at 3 per feature slice. If three builds fail with the same infrastructure error (not a code error), the agent stops and reports rather than making the problem worse. Each retry without cleanup was additive — 12 more connections, 100MB more memory, one more container that Ryuk couldn't reap.

**The `forkedProcessExitTimeoutInSeconds` addition was a direct consequence of this failure mode.** Humans rarely accumulate zombie Surefire processes because they notice slowness, check process lists, or restart their machines. Agents don't do any of that. The infrastructure must protect itself.

We also added a zombie kill guard to the build scripts:

```bash
# Before every Maven build
pkill -f surefire 2>/dev/null || true
docker container prune -f
sleep 2
```

This is crude, but it prevents accumulation. An agent running its fourth build starts with a clean slate instead of layering onto the debris of three previous failures.

## The Checklist

If you run Spring Boot with Testcontainers and HikariCP, audit your `application-test.yml` against this list:

- [ ] **Pool sizes explicitly set** for every datasource — don't inherit HikariCP defaults (max 10) when your production config uses 5
- [ ] **`leak-detection-threshold`** enabled for all pools — 10-15 seconds in tests (more aggressive than production)
- [ ] **`connection-timeout`** set per pool — 10 seconds for app queries, 30 seconds for migration (DDL is slower)
- [ ] **`minimum-idle`** set to 1 for test pools — no reason to eagerly fill pools in tests
- [ ] **`forkedProcessExitTimeoutInSeconds`** in Surefire/Failsafe config — 60 seconds is a reasonable default
- [ ] **`spring.jpa.open-in-view: false`** in the test profile — not just production
- [ ] **Virtual threads** considered: if `spring.threads.virtual.enabled: true`, your pool sizes become the concurrency bottleneck, not thread count
- [ ] **Zombie process cleanup** in CI scripts — `pkill -f surefire` before each build

## Closing

The 6-hour incident was caused by the absence of 16 lines of YAML and 1 XML element. No code was wrong. No logic was broken. The application's behavior in tests simply diverged from its behavior in production because the test profile didn't specify the same guardrails.

Properties files are not boilerplate to copy from a Stack Overflow answer and forget. They are the silent contract between your code and your infrastructure. When your application creates three connection pools, enables virtual threads, disables OSIV, and runs schema migrations at startup — the defaults are not going to be right. You have to be explicit.

Every property you leave unset is a decision. Make sure it's an intentional one.
