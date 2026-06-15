# ADR-300: MCP Transport & SDK Choice

**Status**: Accepted

**Context**:

Phase 78 ships a read-only Model Context Protocol (MCP) server that lets a firm connect its own Claude client (Desktop / Code / claude.ai) to its live Kazi data. The MCP protocol is JSON-RPC 2.0 framed over a transport; the protocol defines a lifecycle (`initialize` handshake with capability negotiation, `resources/list`, `resources/read`, `tools/list`, `tools/call`) that a client and server must implement faithfully or clients silently fail to connect.

Two questions must be settled before any code: (1) which **transport** Kazi exposes, and (2) whether to use an **SDK** or hand-roll the protocol. The repo has **no** existing MCP or Spring AI dependency (`backend/pom.xml` grep for `mcp`/`spring-ai`/`modelcontextprotocol` returns nothing), and the backend runs **Spring Boot 4.0.2 / Java 25**. This matters because Spring AI's GA 1.0.x line targets Spring Boot 3.x, while the Boot-4-aligned MCP server support lives in the Spring AI 2.0 line, which is currently at milestone maturity (e.g. 2.0.0-M6).

**Options Considered**:

1. **Spring AI 2.0 milestone MCP server starter + Streamable HTTP (CHOSEN)** — Add `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (Spring AI 2.0.x), configure `spring.ai.mcp.server.protocol=STREAMABLE`, expose at `/mcp`. The starter handles JSON-RPC framing, lifecycle, capability negotiation, and tool/resource registration; Kazi supplies typed tool/resource specs.
   - Pros: Protocol correctness is the SDK's responsibility, not ours; honours the requirement to "not hand-roll the protocol." Streamable HTTP is the current remote-MCP transport. WebMVC variant matches the existing servlet (blocking) stack, so it slots into the existing Spring Security filter chain (critical for auth reuse — see [ADR-303](ADR-303-mcp-authentication.md)). Boot-4-aligned via the 2.0 line. Tool/resource registration is declarative.
   - Cons: Spring AI 2.0 is a **milestone** (not GA) — API churn and milestone-repo dependency risk. Adds a sizeable dependency tree. Couples Kazi to Spring AI's autoconfigure conventions.

2. **Hand-rolled JSON-RPC over Streamable HTTP** — Implement the MCP lifecycle directly in a Spring MVC controller, no SDK.
   - Pros: Zero new dependency; no milestone-maturity risk; full control over framing and error shapes; nothing to upgrade when Spring AI 2.0 churns.
   - Cons: Directly contradicts the requirement to not hand-roll the protocol. MCP's lifecycle, capability negotiation, content typing, and streaming semantics are fiddly and evolve with the spec; we would own protocol-conformance bugs and every spec revision. Far more code to write, test, and maintain. High risk of subtle client-incompatibility that only surfaces against a real Claude client.

3. **stdio-transport MCP server** — Ship a local process speaking MCP over stdio, as most reference MCP servers do.
   - Pros: Simplest transport; the most common MCP server shape; no HTTP/OAuth surface.
   - Cons: stdio is for **locally-run** servers co-located with the client. Kazi is a multi-tenant hosted SaaS; firm data lives server-side behind per-user RBAC. A stdio server cannot be the Kazi-hosted remote server the strategy requires, and gives no central audit/POPIA vantage point. Out of scope per the requirements (local/on-prem is a later premium story).

**Decision**: Option 1 — adopt the Spring AI 2.0 milestone `spring-ai-starter-mcp-server-webmvc` starter with the `STREAMABLE` protocol, exposed over HTTPS at `/mcp`; keep Option 2 (hand-rolled) documented as the contingency if the milestone proves not Boot-4-ready.

**Rationale**:

1. **Protocol correctness is non-negotiable and not our differentiator.** A Claude client either speaks MCP correctly or it does not connect. Owning JSON-RPC framing and lifecycle conformance is pure liability with no product upside; the SDK exists precisely to absorb it.
2. **The WebMVC (servlet) starter is the only variant that composes with the existing auth stack.** Kazi's `TenantFilter`/`MemberFilter` are servlet filters binding `ScopedValue` request context. A blocking WebMVC MCP servlet lands inbound bearer tokens in that exact chain (see [ADR-303](ADR-303-mcp-authentication.md)); a WebFlux/reactive variant would fork the request-context model.
3. **Streamable HTTP is the current remote transport** and the right fit for a hosted server with an OAuth front door.
4. **The milestone risk is bounded and reversible.** It is isolated to the transport adapter; the tool/resource catalogue, DTOs, gating, and audit are pure Kazi code that survive an SDK swap. If 2.0 proves unworkable on Boot 4, Option 2 is the fallback with no change to the rest of the design.

**Consequences**:
- Positive: Minimal protocol code; declarative tool/resource registration; conformance maintained by the SDK; transport choice matches the hosted-remote strategy.
- Positive: The MCP servlet runs inside the existing Spring Security filter chain, enabling full auth reuse.
- Negative: Depends on a non-GA Spring AI 2.0 milestone — pin the exact version, watch for API churn, and plan a GA-upgrade pass. The Spring milestone repository must be added to the build.
- Negative: Adds a non-trivial dependency tree to the backend.
- Neutral: A first capability slice (S1) should prove the `initialize` handshake end-to-end on Boot 4.0.2 *before* the catalogue is built, so the milestone risk is retired early.
- Related: [ADR-301](ADR-301-mcp-deployment-topology.md) (where the `/mcp` servlet runs), [ADR-303](ADR-303-mcp-authentication.md) (how the filter chain authenticates it), [ADR-306](ADR-306-mcp-tool-contract-design.md) (the contract the SDK exposes).
