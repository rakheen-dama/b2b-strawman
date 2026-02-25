# ADR-097: Rate Limiting Implementation

**Status**: Accepted

**Context**:

DocTeams needs per-tenant email rate limiting to prevent abuse of the platform SMTP quota (which the platform pays for) and to protect against misconfigured automations or malicious tenants. Rate limits are tier-aware: 50 emails/hour for platform SMTP (Tier 1), 200 emails/hour for BYOAK SendGrid (Tier 2), and a platform aggregate limit of 2,000 emails/hour across all tenants using platform SMTP.

The decision is: how should rate limit counters be implemented? The system is currently single-instance (one backend JVM). Future multi-instance deployment is planned but not imminent. The rate limiter must be fast (called on every email send), reasonably accurate (off by a few percent is fine — this is abuse prevention, not billing), and simple to implement.

**Options Considered**:

1. **Caffeine cache with sliding-window counters (chosen)** — Use Caffeine (already a project dependency via `IntegrationRegistry`) to maintain per-tenant counters. Each counter tracks the number of emails sent in the current sliding window (1 hour). A `ConcurrentHashMap` inside a Caffeine-cached entry, or Caffeine's built-in expiry, resets counters after the window.
   - Pros:
     - Fast: O(1) check per email send, no I/O.
     - Simple: ~50 lines of code. No new dependencies.
     - Caffeine is already in the dependency tree (used by `IntegrationRegistry`).
     - Graceful under load: no database contention.
     - Platform aggregate counter is trivially added (one additional cache entry).
   - Cons:
     - In-memory only: counters reset on JVM restart, briefly granting extra quota.
     - Not shared across instances: in a multi-instance deployment, each instance tracks independently, effectively multiplying the limit by N instances.
     - Sliding window approximation: using Caffeine's `expireAfterWrite` with fixed-window semantics (not true sliding window). A burst at the window boundary could temporarily allow 2x the limit.

2. **Database counter with periodic reset** — Store a `(tenant_schema, window_start, count)` row in a table. Increment atomically on each send. A scheduled job resets counters every hour.
   - Pros:
     - Persistent: survives JVM restarts.
     - Shared across instances: all JVMs read/write the same counters.
     - Exact count: no approximation.
   - Cons:
     - Database I/O on every email send — adds latency to the email path.
     - Contention under high concurrency (row-level lock on the counter row).
     - Requires a scheduled job for cleanup (or `ON CONFLICT DO UPDATE` with window rotation).
     - Over-engineered for single-instance: adds complexity for a problem that doesn't exist yet.
     - The counter table would need to be in a shared schema or global schema (breaking tenant isolation) or replicated per-tenant (overhead).

3. **Token bucket algorithm (in-memory)** — Implement a token bucket per tenant using `java.util.concurrent` primitives or a library like Bucket4j.
   - Pros:
     - True rate limiting (smooth distribution, no window boundary spikes).
     - Well-understood algorithm with proven implementations.
     - In-memory: fast, no I/O.
   - Cons:
     - New dependency (Bucket4j) or ~100 lines of custom code.
     - Same in-memory limitations as Option 1 (restart resets, not shared across instances).
     - Token bucket is more complex than needed: smooth distribution doesn't matter for email (we care about aggregate count per hour, not burst smoothing).
     - Caffeine already provides expiry-based windowing — adding another in-memory mechanism is redundant.

**Decision**: Option 1 — Caffeine cache with sliding-window counters.

**Rationale**:

For a single-instance deployment with abuse-prevention (not billing) accuracy requirements, Caffeine-based counters are the simplest solution that works. The implementation is approximately 50 lines of code with no new dependencies. Caffeine is already proven in the codebase (`IntegrationRegistry` uses it for config caching).

The fixed-window approximation (using `expireAfterWrite(1, HOURS)`) means a burst at the window boundary could temporarily allow up to 2x the per-tenant limit. For abuse prevention, this is acceptable — the limit is a safety net, not a billing meter. If tighter accuracy is needed in the future, the window can be subdivided (e.g., 6 x 10-minute windows summed).

Option 2 (database counters) was rejected because it adds database I/O to every email send — unnecessary overhead for a single-instance system. The persistence benefit (surviving restarts) is marginal: a restart mid-hour is rare, and the brief extra quota is harmless. When multi-instance deployment arrives, the rate limiter can be upgraded to Redis (or a database counter) without changing the `EmailRateLimiter` interface.

Option 3 (token bucket) was rejected because smooth burst distribution is not a requirement for email rate limiting. We care about "no more than 50 emails per hour," not "no more than 1 email per 72 seconds." Token bucket adds complexity without benefit.

**Implementation sketch**:

```java
@Service
public class EmailRateLimiter {

    // Key: "tenant:{schema}:{provider}" → AtomicInteger count
    private final Cache<String, AtomicInteger> tenantCounters;
    // Key: "platform-aggregate" → AtomicInteger count
    private final Cache<String, AtomicInteger> aggregateCounter;

    private final int smtpLimit;
    private final int byoakLimit;
    private final int platformAggregateLimit;

    public boolean tryAcquire(String tenantSchema, String providerSlug) {
        int limit = "smtp".equals(providerSlug) ? smtpLimit : byoakLimit;
        var counter = tenantCounters.get(
            tenantSchema + ":" + providerSlug,
            k -> new AtomicInteger(0));
        if (counter.incrementAndGet() > limit) {
            counter.decrementAndGet();
            return false;
        }
        // Platform aggregate check (SMTP only)
        if ("smtp".equals(providerSlug)) {
            var agg = aggregateCounter.get(
                "platform-aggregate",
                k -> new AtomicInteger(0));
            if (agg.incrementAndGet() > platformAggregateLimit) {
                agg.decrementAndGet();
                counter.decrementAndGet();
                return false;
            }
        }
        return true;
    }
}
```

**Upgrade path to multi-instance**:

When the system scales to multiple instances:
1. Replace `Caffeine` cache with Redis (`INCR` + `EXPIRE`).
2. The `EmailRateLimiter` interface (`tryAcquire`, `getStatus`) remains unchanged.
3. Redis-based implementation: `INCR tenant:{schema}:{provider}:{windowKey}` with `EXPIRE 3600`.
4. Platform aggregate: `INCR platform-aggregate:{windowKey}` with `EXPIRE 3600`.

**Consequences**:

- Positive:
  - Zero new dependencies — Caffeine is already available.
  - Fast: no I/O on the email send path.
  - Simple: ~50 lines of production code, easy to test.
  - Clear upgrade path: swap Caffeine for Redis when multi-instance is needed.

- Negative:
  - Counters reset on JVM restart. A restart mid-hour briefly allows extra emails. Acceptable for abuse prevention.
  - Not shared across instances. In multi-instance deployment, effective limit is N * configured limit. Must upgrade to Redis before scaling to multiple instances.
  - Fixed-window approximation allows brief 2x bursts at window boundaries. Acceptable.

- Neutral:
  - The `EmailRateLimiter` interface is provider-agnostic and instance-agnostic — switching from Caffeine to Redis changes only the implementation, not callers.
  - Rate limit status is exposed via `GET /api/email/stats` so admins can see current usage vs. limit.

- Related: [ADR-095](ADR-095-two-tier-email-resolution.md) (tier-aware limits depend on which provider is resolved), [ADR-096](ADR-096-webhook-tenant-identification.md) (delivery tracking for rate-limited emails).
