# ADR-202: Consumer Callback vs Reactive Streams for LLM Streaming

**Status**: Accepted
**Date**: 2026-03-21
**Phase**: 52 (In-App AI Assistant — BYOAK)

## Context

The `LlmChatProvider` interface ([ADR-200](ADR-200-llm-chat-provider-interface.md)) needs a mechanism to deliver streaming tokens from the LLM API to the SSE controller. The Anthropic Messages API returns a Server-Sent Events stream where each event contains a token delta, tool use request, usage statistics, or completion signal. These events must flow from the HTTP client (reading the Anthropic SSE stream) through the provider, through `AssistantService`, and into the `SseEmitter` that delivers them to the user's browser.

The backend uses Spring Boot 4 with Spring MVC (not WebFlux). Virtual threads are enabled (`spring.threads.virtual.enabled=true`), making blocking operations cheap. The `SseEmitter` class in Spring MVC provides `send()` for pushing events and `complete()` for closing the stream. The `AssistantController` creates an `SseEmitter`, submits the chat processing to a virtual thread (with `ScopedValue` re-binding per [ADR-204](ADR-204-virtual-thread-scoped-value-rebinding.md)), and returns the emitter immediately.

The design question is the method signature for `LlmChatProvider.chat()`: should it push events via a callback consumer, return a reactive `Flux`, use a pull-based iterator, or return a future with chunked response?

## Options Considered

### Option 1: Consumer<StreamEvent> Callback with SseEmitter (Selected)

`void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer)` — the method blocks the calling thread until the LLM response is complete. As each SSE event arrives from the Anthropic API, the provider parses it into a `StreamEvent` and calls `eventConsumer.accept(event)`. The consumer (provided by `AssistantService`) writes each event to the `SseEmitter`.

- **Pros:**
  - **No reactive dependency:** The interface uses `java.util.function.Consumer` — a standard JDK type. No Spring WebFlux, no Project Reactor, no `Flux`, no `Mono`. The provider implementation and the service layer have zero reactive imports
  - **Framework-agnostic interface:** `LlmChatProvider` can be used in any Java application — Spring MVC, Quarkus, plain Java. The consumer pattern doesn't assume any specific HTTP framework
  - **Natural fit for virtual threads:** The `chat()` method blocks the calling virtual thread while reading the Anthropic SSE stream. With Java 25 virtual threads, blocking is cheap — the platform thread is released while the virtual thread waits on I/O. No thread pool sizing, no backpressure tuning
  - **Simple error propagation:** If the Anthropic API returns an error, the provider calls `eventConsumer.accept(new StreamEvent.Error(message))` and then returns. The caller handles the error in the same code path as normal events. No `onError` callbacks, no error channels
  - **Matches SseEmitter pattern:** `SseEmitter.send()` is a blocking call that writes to the servlet output stream. `Consumer<StreamEvent>` maps directly: `event -> emitter.send(...)`. No adapter, no bridge, no scheduler
  - **Tool execution fits naturally:** When a `ToolUse` event arrives, `AssistantService` can execute the tool synchronously (for read tools) or block on a `CompletableFuture` (for write tools requiring confirmation, per [ADR-203](ADR-203-completable-future-confirmation.md)) before pushing the result back to the LLM — all within the same virtual thread, in sequential code

- **Cons:**
  - **No backpressure:** If the consumer is slow (e.g., network congestion to the browser), events queue up in the virtual thread's call stack. In practice, `SseEmitter.send()` blocks until the write completes, so the Anthropic stream read is naturally throttled by the browser's receive rate — but this is implicit, not explicit backpressure
  - **Single consumer:** Only one consumer per `chat()` call. If multiple consumers need the same stream (e.g., logging + SSE delivery), the consumer must fan out internally. Not a current requirement
  - **Blocking call is unfamiliar to reactive developers:** Developers experienced with WebFlux may expect a `Flux` return type. The blocking pattern requires understanding that virtual threads make blocking acceptable

### Option 2: Flux<StreamEvent> with WebFlux

`Flux<StreamEvent> chat(ChatRequest request)` — the method returns a reactive stream of events. The controller subscribes to the `Flux` and maps events to SSE responses via `ServerSentEvent`.

- **Pros:**
  - Built-in backpressure via Reactor's `request(n)` protocol
  - Composable — `Flux` operators (`map`, `filter`, `onErrorResume`, `timeout`) provide a rich transformation API
  - Familiar to developers with reactive experience
  - Can use `WebClient` (WebFlux's HTTP client) to consume the Anthropic SSE stream natively as a `Flux<ServerSentEvent>`

- **Cons:**
  - **Requires WebFlux dependency:** Adding `spring-boot-starter-webflux` alongside `spring-boot-starter-web` changes the application context. Spring Boot 4 auto-configures either a servlet or reactive container — having both requires explicit configuration to avoid conflicts. The platform currently uses Spring MVC exclusively
  - **Reactive learning curve:** The entire codebase uses imperative/blocking patterns with virtual threads. Introducing Reactor (`Flux`, `Mono`, `Schedulers`, `publishOn`, `subscribeOn`) for a single feature creates a paradigm island. Every developer who touches the assistant code must understand reactive operators
  - **`ScopedValue` incompatibility:** Reactor's scheduler threads are platform threads from a shared pool, not the request's virtual thread. `ScopedValue` bindings don't propagate to Reactor schedulers. Every operator in the chain that needs `RequestScopes.TENANT_ID` must receive it explicitly — defeating the purpose of `ScopedValue`. This is the same problem described in [ADR-204](ADR-204-virtual-thread-scoped-value-rebinding.md) but amplified across every Reactor operator
  - **Tool execution breaks the stream:** When a `ToolUse` event requires execution (read tool) or confirmation (write tool), the reactive chain must pause, execute the tool, and resume with the result fed back to the LLM. This requires `Flux.concatMap()` with nested `Mono` calls — significantly more complex than sequential code in a virtual thread
  - **Interface is framework-specific:** `Flux<StreamEvent>` ties the `LlmChatProvider` interface to Project Reactor. Future adapters (OpenAI, Google) must depend on Reactor even if they don't use Spring

### Option 3: Iterator/Pull-Based Streaming

`Iterator<StreamEvent> chat(ChatRequest request)` or `Stream<StreamEvent> chat(ChatRequest request)` — the method returns a lazy iterator. The caller pulls events one at a time.

- **Pros:**
  - Standard JDK types — no external dependencies
  - Caller controls the consumption rate (natural backpressure)
  - Easy to compose with `Stream` operations (`map`, `filter`, `takeWhile`)

- **Cons:**
  - **Requires buffering in the provider:** The provider must read from the Anthropic SSE stream in a separate thread and buffer events for the iterator to consume. This introduces a producer-consumer pattern with a `BlockingQueue`, which is more complex than a simple callback
  - **Resource management is the caller's responsibility:** The caller must consume the iterator to completion or explicitly close it. If the caller abandons the iterator (e.g., user disconnects), the Anthropic HTTP connection leaks unless a `close()` method is called. `AutoCloseable` helps but requires try-with-resources discipline
  - **Awkward for multi-turn:** After a tool execution, the result must be fed back to the LLM for a continuation response. With an iterator, this means calling `chat()` again with the tool result appended to the message history — creating a loop of `chat()` calls. With a callback, this loop happens inside the provider naturally
  - **Thread management leaks:** The provider must manage the thread that reads from the Anthropic API. With a callback, the calling thread does the reading. With an iterator, a background thread reads and buffers while the caller pulls. This adds concurrency complexity without benefit

### Option 4: CompletableFuture with Chunked Response

`CompletableFuture<ChatResponse> chat(ChatRequest request)` — the method returns a future that completes when the entire LLM response is ready. The response includes the full text, tool calls, and usage statistics. No streaming.

- **Pros:**
  - Simplest interface — request in, response out
  - No streaming complexity — the controller waits for the complete response and sends it as a single JSON response
  - Easy to test — assert on the `ChatResponse` object

- **Cons:**
  - **No streaming UX:** The user sees nothing until the entire response is generated. For a 500-token response at ~50 tokens/second, that is a 10-second wait with no feedback. This fundamentally undermines the assistant UX — the requirement specifies real-time token streaming
  - **Higher Time to First Token (TTFT):** The user perceives latency as the time until the first visible response. With streaming, TTFT is ~200ms. Without streaming, TTFT equals the full generation time (5-15 seconds)
  - **Cannot pause for confirmation:** Write tools require the user to confirm before execution ([ADR-203](ADR-203-completable-future-confirmation.md)). Without streaming, the entire LLM response (including tool calls) arrives at once — there is no opportunity to pause mid-response and wait for user input before executing a tool
  - **Wasteful for aborted requests:** If the user clicks "Stop" after 2 seconds, the server has already generated the full response (using API tokens and incurring costs). With streaming, the `AbortController` on the frontend triggers connection close, which the provider can detect and stop reading

## Decision

**Option 1 — Consumer<StreamEvent> callback with SseEmitter.**

## Rationale

The consumer callback pattern is the natural choice for a Spring MVC application running on virtual threads. It avoids the reactive paradigm entirely, keeping the codebase consistent with its imperative style, while delivering real-time streaming to the user.

1. **Virtual threads make blocking the right paradigm.** The `chat()` method blocks the calling virtual thread while reading the Anthropic SSE stream. This is not a thread pool scalability issue — virtual threads are cheap (a few KB of stack per thread). The platform already uses virtual threads for all request processing via `spring.threads.virtual.enabled=true`. Writing blocking code that reads from a network stream and pushes events to a consumer is the simplest possible implementation.

2. **The consumer-to-SseEmitter mapping is trivial.** `AssistantService` provides a consumer that calls `emitter.send(SseEmitter.event().name(eventType).data(eventData))`. There is no adapter, no bridge, no scheduler. The event flows from the Anthropic HTTP response → provider parsing → `Consumer.accept()` → `SseEmitter.send()` → servlet output stream → browser `ReadableStream`. Each step is a direct method call on the same virtual thread.

3. **Tool execution is sequential code.** When the consumer receives a `StreamEvent.ToolUse` event, `AssistantService` can execute the tool immediately (read tools) or block on a `CompletableFuture` (write tools, [ADR-203](ADR-203-completable-future-confirmation.md)). The result is appended to the message history, and `provider.chat()` is called again for the continuation. This multi-turn loop is a simple `while` loop — no reactive composition, no `concatMap`, no scheduler switching.

4. **`ScopedValue` works naturally.** The virtual thread has `ScopedValue` bindings for `TENANT_ID`, `MEMBER_ID`, `ORG_ROLE`, and `CAPABILITIES` (re-bound per [ADR-204](ADR-204-virtual-thread-scoped-value-rebinding.md)). Since the consumer callback runs on the same virtual thread, all `RequestScopes` reads in tool execution resolve correctly. With a `Flux` (Option 2), every Reactor operator would need explicit context propagation.

## Consequences

- **Positive:**
  - No WebFlux or Project Reactor dependency — the application remains a pure Spring MVC application
  - `LlmChatProvider` interface uses only JDK types (`Consumer`, `String`, `List`, `boolean`) — maximally portable
  - Streaming works immediately with `SseEmitter` — no custom SSE framework needed
  - Multi-turn tool execution is sequential code, easy to read, debug, and test
  - `ScopedValue` bindings propagate naturally through the consumer callback on the same virtual thread

- **Negative:**
  - No explicit backpressure mechanism — if the browser connection is slow, the virtual thread blocks on `SseEmitter.send()`, which blocks the Anthropic stream read. This is acceptable implicit backpressure but not a formal contract
  - The `void` return type means errors must be communicated via the consumer (`StreamEvent.Error`) rather than via exceptions. The provider must catch all exceptions and convert them to error events. An uncaught exception in the provider would kill the virtual thread without notifying the browser — the provider implementation must be defensive

- **Neutral:**
  - The `StreamEvent` sealed interface (`TextDelta`, `ToolUse`, `Usage`, `Done`, `Error`) defines the event vocabulary. Adding new event types (e.g., `Thinking`, `ImageGeneration`) requires extending the sealed interface — a compile-time-safe change
  - The `Consumer<StreamEvent>` pattern is used by the Anthropic adapter internally to parse the Anthropic SSE format and emit platform `StreamEvent` instances. The adapter is the only class that knows about Anthropic-specific event types (`content_block_delta`, `message_stop`, etc.)
