# ADR-203: CompletableFuture Confirmation Flow for Write Tools

**Status**: Accepted
**Date**: 2026-03-21
**Phase**: 52 (In-App AI Assistant — BYOAK)

## Context

Phase 52's AI assistant can perform write actions (create projects, update customers, log time entries) but every write must be explicitly confirmed by the user before execution. The flow is:

1. The LLM decides to call a write tool (e.g., `create_project`) and emits a `ToolUse` event.
2. `AssistantService` detects `requiresConfirmation() = true` on the tool (via `AssistantToolRegistry`).
3. The SSE stream sends a confirmation card to the browser showing exactly what will be created/changed.
4. The stream must **pause** — no more tokens are generated until the user responds.
5. The user clicks "Confirm" or "Cancel" on the confirmation card.
6. The browser POSTs to `/api/assistant/chat/confirm` with `{ toolCallId, approved }`.
7. The stream resumes — the tool is executed (if confirmed) or a cancellation message is sent to the LLM (if rejected).
8. The LLM generates a follow-up response based on the tool result.

The challenge is step 4: the SSE stream is driven by a virtual thread ([ADR-202](ADR-202-consumer-callback-streaming.md)) that is blocking while reading from the Anthropic API. When a write tool is encountered, this virtual thread must pause and wait for user input from a separate HTTP request (the confirm endpoint). The mechanism for this pause-and-resume is the subject of this ADR.

The chat sessions are ephemeral — conversation history lives in frontend state only. There is no server-side persistence of chat sessions. If the server restarts, the user simply re-asks their question. This constraint significantly simplifies the design space.

## Options Considered

### Option 1: In-Memory CompletableFuture with ConcurrentHashMap (Selected)

When a write tool is encountered, `AssistantService` creates a `CompletableFuture<Boolean>` and stores it in a `ConcurrentHashMap<String, CompletableFuture<Boolean>>` keyed by `toolCallId`. The virtual thread blocks on `future.get(120, TimeUnit.SECONDS)`. The confirm endpoint looks up the future by `toolCallId` and completes it with `true` (confirmed) or `false` (rejected). The entry is removed from the map in a `finally` block.

- **Pros:**
  - **Minimal implementation:** Three JDK classes (`CompletableFuture`, `ConcurrentHashMap`, `TimeUnit`) — no external dependencies, no new infrastructure
  - **Virtual thread blocking is cheap:** `CompletableFuture.get()` on a virtual thread unmounts the virtual thread from the platform thread. The platform thread is free to run other virtual threads. When the future completes, the virtual thread re-mounts and continues. This is the design intent of Java 25 virtual threads (JEP 506)
  - **Thread-safe by construction:** `ConcurrentHashMap.put()` and `ConcurrentHashMap.remove()` are atomic. `CompletableFuture.complete()` is idempotent — calling it twice is safe (second call returns false)
  - **Timeout prevents leaks:** `future.get(120, TimeUnit.SECONDS)` ensures the virtual thread does not block indefinitely if the user abandons the browser tab. After 120 seconds, a `TimeoutException` is caught, an error event is emitted via the `SseEmitter`, and the entry is cleaned up
  - **No persistence needed:** Chat sessions are ephemeral. If the server restarts between "show confirmation" and "user confirms," the SSE connection is lost, the browser detects the disconnect, and the user re-sends the message. There is no orphaned state to recover
  - **Idempotent confirm endpoint:** If the user double-clicks "Confirm," the second `complete()` call returns `false` (already completed). The endpoint returns 200 regardless — no race condition, no error

- **Cons:**
  - **In-memory only:** If the application runs behind a load balancer with multiple instances, the confirm request must hit the same instance that holds the `CompletableFuture`. Requires sticky sessions (which the platform already uses for SSE connections) or a shared store
  - **No audit of confirmation decisions:** The confirmation accept/reject is an in-memory event. If auditing of "user confirmed tool call X" is required, it must be explicitly logged after the future completes — it does not happen automatically
  - **120-second timeout is arbitrary:** Too short, and users who step away lose their session. Too long, and abandoned sessions hold virtual threads. 120 seconds is a reasonable default but may need tuning
  - **Map growth under abuse:** A malicious or buggy client could trigger many tool calls without confirming, growing the map. Mitigated by the 120s timeout (entries self-clean) and the fact that each SSE session can have at most one pending confirmation (the stream is paused)

### Option 2: Database-Persisted Confirmation with Polling

Store confirmation requests in a database table (`assistant_confirmations`). The virtual thread polls the table every 500ms checking for a response. The confirm endpoint writes the decision to the table.

- **Pros:**
  - Survives server restarts — the confirmation state is durable
  - Works across load-balanced instances — any instance can read the confirmation decision
  - Provides an audit trail of all confirmation requests and responses

- **Cons:**
  - **Polling is wasteful:** A 500ms polling interval means 240 database queries over a 120-second timeout window. For 10 concurrent chat sessions with pending confirmations, that is 2,400 queries per timeout window — pure overhead
  - **New migration:** Requires a tenant migration for the `assistant_confirmations` table. For a feature where persistence provides no value (sessions are ephemeral), this is unnecessary schema growth
  - **Latency:** 500ms polling interval means the user waits up to 500ms after clicking "Confirm" before the stream resumes. With an in-memory `CompletableFuture`, the resume is instant (microseconds)
  - **Cleanup complexity:** Expired confirmation records must be periodically cleaned up. A `@Scheduled` job or TTL-based cleanup adds operational overhead
  - **Over-engineered for ephemeral sessions:** The entire point of ephemeral sessions is to avoid server-side state management. Persisting confirmation decisions contradicts this design choice

### Option 3: WebSocket Bidirectional Channel

Replace or supplement the SSE connection with a WebSocket for the assistant chat. The server sends events to the client and receives confirmation responses on the same connection. No separate HTTP endpoint needed for confirmations.

- **Pros:**
  - True bidirectional communication — confirmations flow on the same channel as the chat stream
  - No separate confirm endpoint — the protocol handles both directions
  - Lower latency for confirmations — no HTTP request overhead

- **Cons:**
  - **Requires WebSocket infrastructure:** The platform uses Spring MVC with `SseEmitter` for streaming. Adding WebSocket support requires `spring-boot-starter-websocket`, `WebSocketConfigurer`, message handler registration, and STOMP or raw WebSocket protocol decisions. This is significant new infrastructure for a single feature
  - **Breaks the unidirectional SSE model:** The frontend `useAssistantChat` hook uses `fetch()` + `ReadableStream` to consume SSE events. Switching to WebSocket changes the entire client-side connection management — reconnection logic, heartbeat handling, binary frame parsing
  - **`ScopedValue` binding is harder:** SSE runs on a virtual thread with `ScopedValue` bindings ([ADR-204](ADR-204-virtual-thread-scoped-value-rebinding.md)). WebSocket message handlers run on WebSocket threads, which are a different thread pool. Each message handler invocation requires `ScopedValue` re-binding — more complex than the SSE model where bindings are set once per stream
  - **Proxy/CDN compatibility:** WebSockets require upgrade handshake support from all intermediaries (load balancer, CDN, reverse proxy). SSE uses standard HTTP — no special configuration needed
  - **Complexity for one interaction:** The only client-to-server message during a chat stream is the confirmation response. WebSocket's bidirectional capability is overkill when 99% of the traffic is server-to-client

### Option 4: Separate Confirmation Request with Session Resumption Token

When a write tool is encountered, the SSE stream completes with a `confirmation_required` event containing a `sessionToken`. The user confirms via a POST with the token. A new SSE stream is started with the token, which resumes the conversation from the point of the tool call.

- **Pros:**
  - No long-lived server state — each SSE stream is short-lived
  - Works across load-balanced instances — the session token contains the conversation state (or references it in a cache)
  - Clean HTTP semantics — each SSE stream is a complete request/response cycle

- **Cons:**
  - **Complex state serialization:** The session token must encode the full conversation history (message list, accumulated tool results, pending tool call details). This is potentially large (several KB for multi-turn conversations with tool results). JWTs or encrypted tokens have size limits
  - **Resumption is a new SSE stream:** The frontend must handle SSE stream completion, wait for user input, start a new SSE stream with the token, and merge the new stream's events with the existing conversation UI. This is significantly more complex than pausing and resuming a single stream
  - **Multiple confirmations require multiple stream restarts:** If the LLM calls two write tools in sequence, each requires a stream stop, confirmation POST, and stream restart. The UX is janky — the streaming cursor disappears and reappears for each confirmation
  - **Token management:** Tokens must expire, be single-use, and be validated for the correct user/tenant. This is a mini-auth system within the assistant feature

## Decision

**Option 1 — In-memory CompletableFuture with ConcurrentHashMap.**

## Rationale

The confirmation flow is a synchronization point between two HTTP requests: the SSE stream (which must pause) and the confirm endpoint (which must signal resume). `CompletableFuture` is the JDK's standard tool for exactly this pattern — one thread blocks on `get()`, another thread calls `complete()`.

1. **Ephemeral sessions make persistence unnecessary.** The requirements explicitly state that chat history lives in frontend state only. If the server restarts, all SSE connections drop, the browser detects the disconnect, and the user re-sends their message. There is no orphaned confirmation to recover, no partial state to reconcile. Persisting confirmations (Option 2) adds infrastructure for a scenario that, by design, resolves itself.

2. **Virtual threads make blocking free.** Before virtual threads, blocking a thread on `CompletableFuture.get()` for up to 120 seconds was expensive — it consumed a platform thread from a limited pool. With Java 25 virtual threads, the blocked virtual thread is unmounted, consuming only ~2 KB of heap. The platform thread runs other virtual threads. Ten concurrent pending confirmations cost ~20 KB of heap and zero platform threads. This changes the calculus entirely — blocking is no longer a design concern.

3. **The `ConcurrentHashMap` is bounded.** Each SSE session can have at most one pending confirmation at a time (the stream is paused while waiting). With the 120-second timeout, entries self-clean. Even under extreme load (100 concurrent chat sessions, each with a pending confirmation), the map holds 100 entries. Memory usage is negligible.

4. **Sticky sessions are already required for SSE.** The SSE connection between the browser and the server must be maintained for the duration of the chat. If a load balancer routes requests round-robin, the SSE stream would break on the second request. The platform already uses sticky sessions (session affinity) for long-lived connections. The confirm endpoint naturally hits the same instance that holds the `CompletableFuture` because the session cookie routes it there.

5. **The timeout is the cleanup mechanism.** The 120-second timeout on `future.get()` serves two purposes: (a) it prevents resource leaks from abandoned sessions, and (b) it provides a clear UX boundary — if the user hasn't confirmed in 2 minutes, the assistant responds with "The confirmation timed out. Would you like me to try again?" The user is not left in a broken state.

## Consequences

- **Positive:**
  - Zero new infrastructure — `CompletableFuture` and `ConcurrentHashMap` are JDK classes
  - Zero new migrations — no server-side persistence of confirmation state
  - Instant resume — `CompletableFuture.complete()` is O(1), the virtual thread resumes in microseconds
  - Self-cleaning — 120s timeout ensures no orphaned entries in the map
  - Idempotent — double-clicking "Confirm" is harmless (`complete()` returns false on second call)
  - Simple testing — create a `CompletableFuture`, call `complete()` from a separate thread, assert the result

- **Negative:**
  - Server restart during a pending confirmation loses the confirmation. The SSE connection also drops, so the browser detects the failure. The user re-sends the message. Acceptable for v1 but worth documenting in the UX
  - In a multi-instance deployment, the confirm request must reach the same instance as the SSE stream. Requires sticky sessions or session affinity at the load balancer. The platform already assumes this for SSE, but it is an operational constraint to be aware of
  - No durable audit of confirmation decisions. The tool execution itself is audited (via domain service audit events), but the "user confirmed at timestamp T" metadata is not persisted. If confirmation auditing is needed, it can be added as an audit event emission after `future.get()` returns

- **Neutral:**
  - The `ConcurrentHashMap<String, CompletableFuture<Boolean>>` is an instance field on the `AssistantService` singleton bean (Spring `@Service` default scope is singleton, making the field effectively application-scoped and accessible from both the SSE virtual thread and the confirm endpoint request thread). The key is `toolCallId` (a UUID string generated by the LLM). The value is the future. The lifecycle is: `put()` on tool use detection, `get()` on confirm endpoint, `remove()` in finally block after timeout or completion
  - The confirm endpoint (`POST /api/assistant/chat/confirm`) validates that the `toolCallId` exists in the map. If not found (already timed out or already completed), it returns 404 with a clear message. The frontend handles this by showing "This confirmation has expired"
