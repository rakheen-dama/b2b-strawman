# ADR-174: Tool Execution Model

**Status**: Accepted
**Date**: 2026-03-12
**Phase**: Phase 45 — In-App AI Assistant (BYOAK)

## Context

The AI assistant executes ~22 tools (14 read, 8 write) that map to existing backend functionality: listing projects, creating customers, logging time entries, etc. These operations are already implemented in domain services (`ProjectService`, `CustomerService`, `TimeEntryService`, etc.). The question is how tools should invoke these services: directly within the same JVM via Spring dependency injection, via HTTP self-calls to the existing REST API, or via an internal RPC mechanism.

## Options Considered

### 1. **Internal service calls (chosen)** — direct dependency injection

Each tool is a Spring `@Component` that receives domain services via constructor injection. Tool execution is a direct method call: `projectService.create(dto)`. The tool runs in the same JVM, same transaction context, and same `ScopedValue` tenant scope as the assistant request.

- Pros:
  - Zero network overhead — direct method call
  - Inherits `ScopedValue` tenant scope naturally (same thread/virtual thread with re-bound scopes)
  - Participates in the same transaction context if needed
  - No authentication bypass needed — the request is already authenticated
  - Simple to implement, test, and debug
  - Type-safe: tools work with Java objects, not HTTP request/response serialization
- Cons:
  - Tools are coupled to service layer APIs — if a service method signature changes, the tool must update
  - Cannot be deployed as a separate service (not a concern for this monolith)

### 2. **HTTP self-calls** — localhost loopback to REST endpoints

Tools make HTTP requests to the existing REST API endpoints (e.g., `POST http://localhost:8080/api/projects`). Each tool uses `RestClient` to call the same server.

- Pros:
  - Tools use the same API surface as the frontend — no separate code path
  - Could theoretically support remote tool execution
  - API contract is already defined and tested
- Cons:
  - Network overhead for every tool call (even on localhost, serialization + deserialization + HTTP framing)
  - Requires authentication bypass or token forwarding for internal calls
  - `ScopedValue` bindings are lost — the loopback request goes through the full filter chain as a new request
  - Error handling is more complex (HTTP status codes vs. exceptions)
  - Increased latency in multi-tool conversations (each tool adds ~5-20ms of HTTP overhead)
  - Harder to test — requires a running server

### 3. **gRPC internal calls** — inter-process communication

Define gRPC service definitions for tool operations and call them internally. Would require adding gRPC infrastructure to the Spring Boot application.

- Pros:
  - Efficient binary serialization
  - Strongly typed contracts via protobuf
  - Could support remote execution in a microservices architecture
- Cons:
  - Massive infrastructure overhead for an internal call within the same JVM
  - No existing gRPC infrastructure in the project
  - Requires protobuf definitions duplicating existing service contracts
  - Same authentication and tenant scope issues as HTTP self-calls
  - Completely unjustified complexity for a monolithic application

## Decision

Use internal service calls via Spring dependency injection. Each tool receives its domain service(s) via constructor injection and invokes methods directly.

## Rationale

DocTeams is a monolithic Spring Boot application. All domain services are in the same JVM, already managed by the Spring container. Internal service calls are the natural execution model: zero overhead, inherited tenant scope, type safety, and straightforward testing.

The `ScopedValue`-based tenant context (`RequestScopes.TENANT_ID`, `RequestScopes.MEMBER_ID`, `RequestScopes.ORG_ROLE`) is bound in the virtual thread that runs the assistant chat flow. Since tools execute within that same virtual thread (or within the same `ScopedValue.where(...).run(...)` block), they inherit the tenant scope without any additional plumbing. HTTP self-calls would require re-authenticating and re-establishing tenant context for every tool invocation.

The coupling concern (tools depending on service APIs) is acceptable because both are in the same codebase and deploy together. If a service method changes, the compiler catches the tool breakage immediately.

## Consequences

- **Positive**: Simplest implementation, best performance, natural tenant scope inheritance, type-safe, easy to test with standard Spring test utilities.
- **Negative**: Tools cannot be extracted to a separate service without refactoring to HTTP/gRPC calls. This is not a concern given the monolithic architecture.
- **Neutral**: Tool implementations follow the same patterns as controllers — they are thin adapters that delegate to domain services.
