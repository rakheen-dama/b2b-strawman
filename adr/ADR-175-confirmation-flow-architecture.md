# ADR-175: Confirmation Flow Architecture

**Status**: Accepted
**Date**: 2026-03-12
**Phase**: Phase 45 — In-App AI Assistant (BYOAK)

## Context

The AI assistant can execute write actions (create project, log time entry, create invoice draft, etc.) on behalf of the user. Every write action must be confirmed by the user before execution — the user sees a preview card and clicks "Confirm" or "Cancel." The architectural question is how to coordinate between the streaming response (SSE from backend to frontend) and the confirmation signal (frontend to backend) without losing the conversation context or stream connection.

The backend is Spring MVC (no WebFlux). SSE streaming uses `SseEmitter`, not `Flux<ServerSentEvent>`. The chat orchestration runs on a virtual thread that writes events to the emitter.

## Options Considered

### 1. **SSE pause-and-resume with CompletableFuture (chosen)** — blocking virtual thread until confirmation

When the LLM requests a write tool, the assistant service:
1. Stores a `CompletableFuture<Boolean>` in a `ConcurrentHashMap` keyed by the tool call ID
2. Emits a `ToolUse` SSE event to the frontend (with `requiresConfirmation: true`)
3. Calls `future.get(120, TimeUnit.SECONDS)` — blocking the virtual thread

The frontend renders a confirmation card. When the user clicks Confirm/Cancel, the frontend sends `POST /api/assistant/chat/confirm`. The confirm endpoint completes the `CompletableFuture`, unblocking the assistant thread.

With virtual threads (Java 25, `spring.threads.virtual.enabled=true`), the blocked thread is cheap — it does not consume a platform thread. The SSE connection stays open throughout because the `SseEmitter` was already returned to the client before the virtual thread started.

- Pros:
  - Single SSE connection per conversation turn — no reconnection overhead
  - The conversation context (LLM state, accumulated messages, pending tool call) stays in memory on the virtual thread
  - Simple coordination via `CompletableFuture` — a standard Java concurrency primitive
  - Virtual threads make blocking cheap — no thread pool exhaustion risk
  - The frontend only needs to parse SSE events and make one POST call for confirmation
- Cons:
  - The virtual thread is blocked for up to 120 seconds waiting for user input
  - If the server restarts during a pending confirmation, the conversation is lost (acceptable for session-scoped chats)
  - The `ConcurrentHashMap` of pending futures is in-memory state — not replicated across instances (acceptable for v1, single-instance deployment)

### 2. **Separate request/response per turn** — no persistent SSE connection

Each assistant response is a complete HTTP request/response cycle. The chat endpoint returns a full response (not streamed). If the response includes a write tool, the frontend sends a confirm/reject followed by another chat request with the confirmation result appended to the message history.

- Pros:
  - Stateless backend — no in-memory pending futures
  - Works naturally across server restarts
  - Simpler backend implementation (no SSE, no virtual thread coordination)
- Cons:
  - Loses streaming — responses arrive all at once, poor UX for long responses
  - Each confirmation round-trip requires resending the full conversation history (cost: repeated input tokens)
  - Higher latency — each turn is a full LLM call with the entire context
  - The LLM must re-process all prior messages for each turn, increasing cost

### 3. **WebSocket bidirectional** — full duplex communication

Use a WebSocket connection for the entire chat session. Messages flow in both directions: assistant responses stream from server to client, and confirmation signals flow from client to server on the same connection.

- Pros:
  - True bidirectional communication — no separate confirmation endpoint needed
  - Lower overhead than SSE + HTTP POST
  - Natural fit for interactive chat
- Cons:
  - WebSocket infrastructure is not present in the codebase
  - More complex connection management (reconnection, state recovery, heartbeats)
  - Harder to test than SSE + REST
  - Spring MVC WebSocket support requires additional configuration
  - Overkill for unidirectional streaming with occasional (1-2 per conversation) confirmations

## Decision

Use SSE pause-and-resume with `CompletableFuture`. The SSE stream stays open, the virtual thread blocks on a `CompletableFuture` until the confirm endpoint resolves it.

## Rationale

The confirmation flow is architecturally a "pause" in a mostly unidirectional stream. The assistant streams text and tool results to the frontend (server → client). Occasionally — only for write tools — the stream pauses while the user makes a binary decision. This is not a bidirectional conversation; it is a unidirectional stream with rare synchronization points.

`CompletableFuture` is the standard Java primitive for "wait for a result that will be produced by another thread." Virtual threads (Java 25, JEP 506) make the blocking call (`future.get()`) cheap — the virtual thread unmounts from its carrier thread while waiting, freeing it for other work. There is no thread pool exhaustion risk even with many concurrent conversations.

The alternative of separate request/response cycles sacrifices streaming (the core UX requirement) and increases LLM costs by resending the full conversation on each turn. WebSocket is a heavier solution for a problem that occurs 1-2 times per conversation.

The in-memory `ConcurrentHashMap` of pending futures is acceptable because chat sessions are ephemeral (no server-side persistence) and the deployment is single-instance. If horizontal scaling is needed later, the pending futures can be moved to Redis with minimal changes to the `AssistantService`.

## Consequences

- **Positive**: Single SSE connection per turn, minimal infrastructure, cheap blocking via virtual threads, standard Java concurrency primitives, simple frontend integration.
- **Negative**: In-memory state (pending futures) is lost on server restart — acceptable for session-scoped chats. Single-instance limitation for the pending futures map.
- **Neutral**: The confirmation timeout (120 seconds) bounds the maximum block time. Expired futures are cleaned up automatically.
