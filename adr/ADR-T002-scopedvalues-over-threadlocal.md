# ADR-T002: ScopedValues Over ThreadLocal

> Template ADR for `java-keycloak-multitenant-saas` starter template

**Status:** Accepted

**Context:** Request-scoped context (tenant ID, member ID, org role) must be propagated from servlet filters through services to Hibernate's tenant resolver. The traditional approach is `ThreadLocal`; Java 25 introduces `ScopedValue` (JEP 506, final) as a modern alternative. This template targets Java 25 with virtual threads enabled.

**Options Considered:**

1. **ThreadLocal** — Standard `ThreadLocal<T>` variables in a static context class
   - Pros: Universally understood, no Java version requirement, extensive library support
   - Cons: Requires explicit `remove()` calls to avoid memory leaks, mutable (any code can overwrite), per-thread storage cost problematic with virtual threads (millions of threads possible)

2. **ScopedValue (Java 25)** — `ScopedValue<T>` with `ScopedValue.where().run()` binding
   - Pros: Immutable within scope, automatic cleanup when lambda exits, zero per-thread overhead, explicit scope boundaries in code
   - Cons: Requires Java 25+, binding API requires lambda/Runnable (different programming model), limited library familiarity

3. **Spring RequestAttributes / RequestContextHolder** — Spring's built-in request-scoped context
   - Pros: Spring-native, integrates with Spring Security context
   - Cons: Tied to Spring's lifecycle, not usable outside of web request threads, still ThreadLocal-based internally, same virtual thread concerns

**Decision:** Use ScopedValues exclusively. No ThreadLocal usage anywhere in the codebase.

**Rationale:**
1. **Immutability:** ScopedValues cannot be overwritten within their scope. ThreadLocal can be modified by any code, leading to subtle bugs where downstream code accidentally corrupts upstream context.
2. **Automatic cleanup:** Values are unbound when the binding lambda exits. ThreadLocal requires explicit `remove()` calls, and forgotten cleanup causes memory leaks and stale data — a common source of production bugs.
3. **Virtual thread safety:** ThreadLocal has per-thread storage cost. With virtual threads (millions possible), this becomes a real memory concern. ScopedValues have zero per-thread overhead.
4. **Code clarity:** The `ScopedValue.where(key, val).run(() -> ...)` pattern makes the scope boundary explicit and visible in code. There's no question about "who set this" or "when does this get cleaned up."

**Consequences:**
- Positive: No memory leaks from forgotten ThreadLocal cleanup
- Positive: Safe for virtual threads from day one (`spring.threads.virtual.enabled=true`)
- Positive: Compile-time guarantee that context cannot be mutated mid-request
- Negative: Requires Java 25 (latest LTS — acceptable for a modern template)
- Negative: Cannot mutate context mid-request (this is intentional — context should be immutable)
- Negative: `ScopedFilterChain` bridge class needed to adapt servlet filter chain's checked exceptions to ScopedValue's `Runnable` binding
