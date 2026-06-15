# ADR-301: MCP Server Deployment Topology

**Status**: Accepted

**Context**:

The MCP server needs a home. Kazi today is a Spring Boot 4 `backend` app sitting behind a gateway, with the Next.js `frontend`/`portal` in front. The MCP server must be reachable by external Claude clients over HTTPS, must validate Keycloak-issued OAuth tokens, and must execute every tool inside the existing per-tenant `ScopedValue` request context bound by the `TenantFilter`/`MemberFilter` servlet chain.

The decision: does the MCP server live **in-process** inside the existing `backend` app behind a dedicated path, or as a **separate deployable module/service**, and is it exposed **through the gateway** or **directly**? This shapes the security perimeter, the auth integration, the deploy pipeline, and the blast radius.

**Options Considered**:

1. **In-process in `backend`, behind `/mcp`, reachable via the existing edge (CHOSEN)** — The MCP servlet is registered inside the running `backend` application context, mounted at `/mcp`, and goes through the same Spring Security filter chain as every other authenticated API path. It reuses the existing domain service beans directly (in-process method calls, no network hop).
   - Pros: Zero new deployable, zero new infra, zero new service-to-service auth. The MCP tools call `ProjectService`, `CustomerService`, etc. as in-process beans, inheriting the exact `ScopedValue` request context and transaction semantics. The `/mcp` path is NOT in `MemberFilter.shouldNotFilter`'s exclusion set (`/internal/*`, `/actuator/*`, `/portal/*`), so member/capability context binds automatically. Fastest path to v1.
   - Cons: MCP read traffic shares the backend's JVM, connection pool, and thread budget with the web API — a noisy MCP client could contend for resources. Couples MCP release cadence to the backend's. Larger single-process blast radius.

2. **Separate deployable module** — A standalone Spring Boot service that re-validates tokens and reaches Kazi data via internal APIs or a shared data layer.
   - Pros: Independent scaling and release; isolates MCP load from the web API; smaller, auditable surface.
   - Cons: Must either re-implement the whole `TenantFilter`/`MemberFilter`/`ScopedValue` + domain-service stack (massive duplication) or call backend over the network (then it needs service-to-service auth AND a way to act as the end user — reintroducing the impersonation shortcut the requirements explicitly forbid). Forks the auth model. Large infra and ops cost for a v1 read surface.

3. **In-process but isolated behind its own connection pool / thread pool** — Same as Option 1 but with dedicated resources partitioned inside the JVM.
   - Pros: Keeps in-process simplicity while limiting MCP's ability to starve the web API.
   - Cons: Premature optimisation for a read-only v1 with modest expected traffic; adds tuning complexity before any load data exists. The isolation it buys can be retrofitted if metrics show contention.

**Decision**: Option 1 — run the MCP server in-process inside `backend` at `/mcp`, exposed over HTTPS via the existing edge, going through the standard authenticated filter chain.

**Rationale**:

1. **The "reuse auth, don't fork it" mandate forces in-process.** The requirements forbid a parallel auth model and any service-account-impersonates-user shortcut. Only an in-process server on the JWT-authenticated path inherits `TenantFilter` + `MemberFilter` + `RequestScopes.CAPABILITIES` for free; any out-of-process option must either duplicate or impersonate.
2. **The `/mcp` path is already correctly positioned.** It is not excluded by `MemberFilter.shouldNotFilter`, so capabilities and member identity bind without special-casing — unlike `/internal/*` (API-key) or `/portal/*` (customer context).
3. **In-process tool calls inherit the exact request context.** `ActorContext.fromRequestScopes()`, `@RequiresCapability`, `ProjectAccessService`, and audit attribution all behave identically to a web request — the central correctness property of the whole phase.
4. **It is reversible.** A separate module is captured as a future option; nothing in v1 forecloses extracting MCP later once load and isolation requirements are understood.

**Consequences**:
- Positive: No new deployable, infra, or service-to-service auth; full auth and request-context reuse; fastest correct path to v1.
- Negative: MCP shares JVM/pool/threads with the web API — must be watched via per-tenant MCP call-count and latency metrics ([Section 11.10](../architecture/phase78-mcp-server.md)); contention is a known accepted risk for v1.
- Negative: MCP and backend release together.
- Neutral: TLS termination and public exposure of `/mcp` follow the existing edge configuration; confirm the edge forwards the `Authorization` bearer header and the `/.well-known/oauth-protected-resource` discovery path unaltered.
- Related: [ADR-300](ADR-300-mcp-transport-and-sdk.md) (the WebMVC starter that makes in-process viable), [ADR-303](ADR-303-mcp-authentication.md) (the filter-chain auth this topology enables).
