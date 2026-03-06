# ADR-141: Gateway Servlet vs Reactive Stack

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Spring Cloud Gateway offers two server flavors:
- **Server WebFlux** (`spring-cloud-starter-gateway-server-webflux`) — Reactive stack (Netty), uses `WebSession`
- **Server WebMVC** (`spring-cloud-starter-gateway-server-webmvc`) — Servlet stack (Tomcat/Jetty), uses `HttpSession`

Both support `TokenRelay` and `SaveSession` filters.

## Decision

Use **`spring-cloud-starter-gateway-server-webmvc`** (servlet stack).

### Rationale

1. **Consistency**: The existing Spring Boot backend runs on the servlet stack. Using the same stack for the gateway avoids introducing reactive programming patterns into the codebase.
2. **Spring Session JDBC**: Standard `HttpSession` integrates natively with `spring-session-jdbc`. The reactive `WebSession` requires a reactive session repository adapter — an unnecessary abstraction layer.
3. **Debugging**: Servlet stack uses traditional thread-per-request model, making debugging and stack traces straightforward.
4. **Performance**: For a BFF gateway that proxies to a single backend, the reactive stack's non-blocking advantage is negligible. The bottleneck is the backend, not the gateway.

## Consequences

- **Positive**: Familiar programming model for the team
- **Positive**: Standard `HttpSession` + Spring Session JDBC — well-documented, widely used
- **Positive**: Compatible with Spring Boot 4.0.2 via Spring Cloud 2025.1.x
- **Negative**: Lower theoretical throughput than reactive stack (irrelevant for B2B workloads)
