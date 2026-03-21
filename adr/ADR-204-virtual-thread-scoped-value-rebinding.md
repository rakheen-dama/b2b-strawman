# ADR-204: Virtual Thread ScopedValue Re-binding for SSE Chat Streams

**Status**: Accepted
**Date**: 2026-03-21
**Phase**: 52 (In-App AI Assistant — BYOAK)

## Context

The AI assistant's chat endpoint (`POST /api/assistant/chat`) returns an `SseEmitter` for streaming responses. The controller creates the emitter, captures the current request's `ScopedValue` bindings from `RequestScopes`, and submits the actual chat processing to a virtual thread via `Executors.newVirtualThreadPerTaskExecutor()`. The controller returns the `SseEmitter` immediately (non-blocking from the servlet container's perspective), while the virtual thread drives the streaming by calling `LlmChatProvider.chat()` with a consumer that writes to the emitter ([ADR-202](ADR-202-consumer-callback-streaming.md)).

The problem: `ScopedValue` bindings (JEP 506, final in Java 25) are scoped to the `ScopedValue.where(...).run(...)` or `.call(...)` call frame and are not visible to tasks submitted to separate executors. When `AssistantController` submits a `Runnable` to the virtual thread executor, the new virtual thread has no `ScopedValue` bindings. But the code running on that virtual thread needs access to `RequestScopes.TENANT_ID` (for Hibernate's `TenantIdentifierResolver`), `RequestScopes.MEMBER_ID` (for tool context), `RequestScopes.ORG_ROLE` (for capability checks), and `RequestScopes.CAPABILITIES` (for tool filtering via `AssistantToolRegistry`).

The codebase already has a pattern for this: `MemberSyncService` uses `ScopedValue.where(RequestScopes.TENANT_ID, schema).call(() -> { ... })` for internal operations that run outside a normal HTTP request context. The question is whether the assistant's virtual thread should follow this same pattern or use an alternative approach.

## Options Considered

### Option 1: Capture and Re-bind ScopedValue in Virtual Thread (Selected)

In the controller, capture the current `ScopedValue` bindings into local variables before submitting the virtual thread:

```java
String tenantId = RequestScopes.requireTenantId();
UUID memberId = RequestScopes.requireMemberId();
String orgRole = RequestScopes.getOrgRole();
Set<String> capabilities = RequestScopes.getCapabilities();

executor.submit(() -> {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, orgRole)
        .where(RequestScopes.CAPABILITIES, capabilities)
        .run(() -> assistantService.processChat(request, emitter));
});
```

The virtual thread runs with the same `ScopedValue` bindings as the original request thread. All downstream code (`TenantIdentifierResolver`, `CapabilityAuthorizationService`, tool execution via domain services) reads from `RequestScopes` as usual.

- **Pros:**
  - **Consistent with existing patterns:** `MemberSyncService` already captures and re-binds `ScopedValue` for non-request contexts. The assistant controller follows the same pattern. Developers recognize the idiom
  - **Service layer is unmodified:** `AssistantService`, `AssistantToolRegistry`, all domain services (`ProjectService`, `CustomerService`, etc.), and `TenantIdentifierResolver` continue reading from `RequestScopes` without any changes. They don't know they're running on a virtual thread — the `ScopedValue` bindings are identical to a normal request
  - **Compile-time safety:** `ScopedValue.where()` requires all four values to be provided. Forgetting one is a runtime `IllegalStateException` on first access — fail-fast, not silent corruption
  - **Explicit over implicit:** The re-binding code is visible in the controller. A developer reading the code can see exactly which context values are captured and re-bound. No magic, no framework annotation
  - **Works with `StructuredTaskScope` in the future:** If the assistant needs parallel tool execution (e.g., executing two read tools concurrently), `StructuredTaskScope` (JEP 505) inherits `ScopedValue` bindings from the parent thread when used with `ScopedValue.where().run()`. The capture-and-rebind pattern is forward-compatible

- **Cons:**
  - **Manual capture is error-prone:** If a new `ScopedValue` is added to `RequestScopes` (e.g., `GROUPS` was added recently), the controller must be updated to capture and re-bind it. Forgetting the new value causes a `NoSuchElementException` when code tries to read it. Mitigated by the fact that new `ScopedValue` additions are rare and should be caught by integration tests
  - **Boilerplate:** The capture + re-bind code is ~8 lines per controller method. If multiple controllers need this pattern (currently only `AssistantController`), the boilerplate grows. Could be extracted into a utility method
  - **Four separate captures:** Each `ScopedValue` is captured individually. If `RequestScopes` had a `snapshot()` method returning all bindings, the capture would be a single call. This doesn't exist today but could be added

### Option 2: Pass Context as Method Parameters (Context Object)

Create a `ChatContext` record containing `tenantId`, `memberId`, `orgRole`, and `capabilities`. Pass it as a parameter through `AssistantService.processChat()`, tool execution, and any code that needs request context. Don't use `ScopedValue` on the virtual thread at all.

- **Pros:**
  - No `ScopedValue` re-binding — the context is an explicit parameter, visible at every call site
  - Type-safe — the compiler ensures `ChatContext` is passed through the call chain
  - Easy to test — construct a `ChatContext` directly in tests, no `ScopedValue` setup needed

- **Cons:**
  - **Breaks existing service contracts:** All domain services (`ProjectService`, `CustomerService`, `TaskService`, etc.) read `TENANT_ID` from `RequestScopes` for Hibernate's `TenantIdentifierResolver`. Passing context via parameters does not bind `ScopedValue`, so `TenantIdentifierResolver.resolveCurrentTenantIdentifier()` returns `"public"` (the fallback). Every database query hits the wrong schema
  - **Pervasive signature changes:** `AssistantService.processChat()`, every `AssistantTool.execute()`, and every domain service method called by a tool would need a `ChatContext` parameter. This is 20+ method signatures across 14+ tools
  - **Dual invocation path:** Domain services are called from both HTTP request handlers (with `ScopedValue` bound by `MemberFilter`) and from the assistant's virtual thread (with `ChatContext` parameter). The service layer would need to handle both patterns — read from `ScopedValue` OR from parameter. This is a maintenance burden and a source of bugs
  - **`TenantIdentifierResolver` still needs `ScopedValue`:** Hibernate's `CurrentTenantIdentifierResolver` is called by the framework during entity loading, not by application code. There is no way to pass a `ChatContext` to Hibernate's internal resolver. `ScopedValue` binding is required for tenant schema resolution regardless of what the application layer does

### Option 3: Use ThreadLocal Instead of ScopedValue for SSE Threads

Set `ThreadLocal` values on the virtual thread instead of `ScopedValue`. Create a `ThreadLocalRequestContext` that mirrors `RequestScopes` but uses `ThreadLocal` storage.

- **Pros:**
  - `ThreadLocal` propagates naturally within a thread — no re-binding needed after initial set
  - Familiar pattern — `ThreadLocal` has been the standard context propagation mechanism in Java for 20+ years
  - No `ScopedValue.where().run()` nesting — set once at the start of the virtual thread, clear in `finally`

- **Cons:**
  - **Contradicts the codebase's direction:** The entire codebase migrated from `ThreadLocal` (`TenantContext`, `MemberContext`) to `ScopedValue` (`RequestScopes`) in PR #40 (ADR-009). Re-introducing `ThreadLocal` for the assistant is a step backward
  - **ThreadLocal and virtual threads don't mix well:** `ThreadLocal` values are per-carrier-thread, not per-virtual-thread, in some edge cases with pinned virtual threads (synchronized blocks). While Java 25 has improved this, `ScopedValue` was designed specifically for virtual threads
  - **Dual context mechanism:** The service layer reads from `RequestScopes` (`ScopedValue`). If the assistant sets `ThreadLocal` values, `RequestScopes` checks would fail (`ScopedValue.isBound()` returns `false`). The service layer would need to check both `ScopedValue` and `ThreadLocal` — a maintenance nightmare
  - **Manual cleanup required:** `ThreadLocal.remove()` must be called in a `finally` block. `ScopedValue` bindings are automatically scoped — when the `run()` block exits, the binding is gone. `ThreadLocal` has caused memory leaks in production systems when cleanup is forgotten
  - **`TenantIdentifierResolver` reads `RequestScopes.TENANT_ID` (`ScopedValue`):** It does not read from `ThreadLocal`. Either the resolver must be changed to check both (fragile), or the `ThreadLocal` approach must also set the `ScopedValue` (which requires `ScopedValue.where().run()` anyway — negating the purpose of using `ThreadLocal`)

### Option 4: Use StructuredTaskScope with ScopedValue Inheritance

Use `StructuredTaskScope` (JEP 505) instead of a raw virtual thread executor. `StructuredTaskScope` can propagate `ScopedValue` bindings to child threads when used within a `ScopedValue.where().run()` block.

- **Pros:**
  - Automatic `ScopedValue` propagation — no manual capture-and-rebind
  - Structured concurrency provides lifecycle management — child tasks are bounded by the scope's lifetime
  - Cancellation propagation — if the parent scope shuts down, child tasks are interrupted

- **Cons:**
  - **`StructuredTaskScope` is designed for fork-join, not fire-and-forget:** The controller needs to return the `SseEmitter` immediately and let the chat processing run independently. `StructuredTaskScope` requires the parent to `join()` all child tasks before the scope exits. Using it for the SSE pattern requires keeping the scope open for the duration of the chat session — which defeats the purpose of returning the `SseEmitter` early
  - **Still requires initial re-binding:** `StructuredTaskScope` propagates `ScopedValue` from the thread that creates the scope. If the scope is created in the controller method (which runs on the request thread with `ScopedValue` bound), propagation works. But the controller must return the `SseEmitter` synchronously — it cannot block inside a `StructuredTaskScope`. The scope must be created inside the virtual thread, which doesn't have the bindings. Chicken-and-egg problem
  - **`StructuredTaskScope` adds ceremony for no benefit here:** While both `ScopedValue` (JEP 506) and `StructuredTaskScope` (JEP 505) are final in Java 25, using `StructuredTaskScope` for a single sequential task adds API overhead (scope creation, `join()`, `close()`) without enabling any parallelism
  - **Adds complexity for a single-threaded use case:** The chat processing is sequential — read from LLM, execute tool, read more from LLM. There is no parallel subtask that benefits from `StructuredTaskScope`. The scope would manage exactly one child task — equivalent to a simple virtual thread with manual re-binding but with more ceremony

## Decision

**Option 1 — Capture and re-bind ScopedValue in virtual thread.**

## Rationale

The capture-and-rebind pattern is the established idiom in the codebase for running code with `ScopedValue` bindings outside the original request thread. `MemberSyncService` uses it for internal operations, and the assistant controller follows the same pattern. The alternatives either break existing service contracts (Option 2), re-introduce deprecated patterns (Option 3), or add complexity without benefit for this use case (Option 4).

1. **`TenantIdentifierResolver` is the hard constraint.** Hibernate's `CurrentTenantIdentifierResolver` reads `RequestScopes.TENANT_ID` to determine which schema to use for database operations. This is called by the framework during entity loading — not by application code. There is no way to pass tenant context to Hibernate except through `ScopedValue`. Any solution that doesn't bind `RequestScopes.TENANT_ID` on the virtual thread causes all database operations to hit the `public` schema — a catastrophic tenant isolation failure. Options 2 and 3 both require `ScopedValue` re-binding anyway for this reason, making their alternative context mechanisms redundant.

2. **The service layer must not change.** The assistant's tools delegate to existing domain services: `ProjectService.createProject()`, `CustomerService.findById()`, `TimeEntryService.findUnbilledByProject()`, etc. These services read `RequestScopes.MEMBER_ID` for audit context, `RequestScopes.CAPABILITIES` for authorization, and `RequestScopes.TENANT_ID` for schema resolution. Changing these services to accept a `ChatContext` parameter would create a second invocation path for every service — doubling the API surface and the test matrix. Re-binding `ScopedValue` on the virtual thread means the services run identically to when they're called from a normal HTTP request.

3. **The boilerplate is acceptable and isolated.** The capture-and-rebind code is ~8 lines in one controller method (`AssistantController.chat()`). It is not scattered across the codebase. If the pattern is needed in more places, it can be extracted into a utility:

   ```java
   public static Runnable withCurrentScopes(Runnable task) {
       String tenantId = RequestScopes.requireTenantId();
       UUID memberId = RequestScopes.requireMemberId();
       String orgRole = RequestScopes.getOrgRole();
       Set<String> capabilities = RequestScopes.getCapabilities();
       return () -> ScopedValue.where(TENANT_ID, tenantId)
           .where(MEMBER_ID, memberId)
           .where(ORG_ROLE, orgRole)
           .where(CAPABILITIES, capabilities)
           .run(task);
   }
   ```

   This utility can live in `RequestScopes` itself, making re-binding a one-liner.

4. **Forward-compatible with `StructuredTaskScope`.** If the assistant later needs parallel tool execution (e.g., executing `list_projects` and `get_unbilled_time` concurrently), a `StructuredTaskScope` created inside the virtual thread (which already has `ScopedValue` bindings from the re-bind) will propagate those bindings to its child tasks. The capture-and-rebind pattern is the foundation that enables future `StructuredTaskScope` use.

## Consequences

- **Positive:**
  - All domain services, `TenantIdentifierResolver`, `CapabilityAuthorizationService`, and `RequestScopes` helper methods work without modification on the virtual thread
  - Tenant isolation is maintained — `TenantIdentifierResolver` resolves the correct schema for all database operations
  - Capability-based tool filtering works — `AssistantToolRegistry.getToolsForUser()` reads `RequestScopes.CAPABILITIES` as expected
  - The pattern is proven — `MemberSyncService` has used the same capture-and-rebind pattern since the `ScopedValue` migration (PR #40)
  - Fail-fast behavior — if a required `ScopedValue` is not bound (e.g., `requireMemberId()` when `MEMBER_ID` is not bound), the exception is thrown immediately on the virtual thread, not silently returning null

- **Negative:**
  - Manual maintenance — if a new `ScopedValue` is added to `RequestScopes`, the controller's capture-and-rebind code must be updated. The current fields are: `TENANT_ID`, `MEMBER_ID`, `ORG_ROLE`, `CAPABILITIES`. Other fields (`ORG_ID`, `CUSTOMER_ID`, `PORTAL_CONTACT_ID`, `AUTOMATION_EXECUTION_ID`, `GROUPS`) are not needed by the assistant and are not captured. If a new field is added that tools need, the controller must be updated — a potential source of bugs caught only by integration tests
  - The `ScopedValue.where().where().where().where().run()` chain is verbose. Readability is acceptable for one call site but would become noisy if duplicated. Extracting to a `RequestScopes.withCurrentScopes(Runnable)` utility is recommended but not strictly required for v1

- **Neutral:**
  - The virtual thread executor (`Executors.newVirtualThreadPerTaskExecutor()`) is created per controller (or shared as a bean). Each submitted task gets its own virtual thread with its own `ScopedValue` bindings. There is no shared mutable state between chat sessions — each session's virtual thread has its own independent binding scope
  - The capture happens on the request thread (inside the controller method, before the executor submission). The re-bind happens on the virtual thread (inside the submitted `Runnable`). The handoff is via Java closures capturing final local variables — safe and efficient
