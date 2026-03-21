# ADR-200: Separate LlmChatProvider Interface from AiProvider

**Status**: Accepted
**Date**: 2026-03-21
**Phase**: 52 (In-App AI Assistant — BYOAK)

## Context

Phase 52 introduces a conversational AI assistant that streams multi-turn chat responses with tool use. The platform already has an `AiProvider` interface (Phase 21) in `integration/ai/` that handles one-shot text generation: `generateText()`, `summarize()`, and `suggestCategories()`. The existing `NoOpAiProvider` is registered via `@IntegrationAdapter(domain = AI, slug = "noop")` and resolved through `IntegrationRegistry`.

The new assistant requires fundamentally different capabilities: multi-turn message history, streaming token delivery via `Consumer<StreamEvent>` callbacks, tool definitions passed alongside the request, and a blocking call pattern designed for virtual threads. The question is whether these conversational capabilities should be added to the existing `AiProvider` interface or introduced as a separate abstraction.

The distinction matters architecturally because `AiProvider` and the new chat interface serve different consumers with different lifecycles. `AiProvider` is consumed by background services (document summarization, category suggestion) that expect a synchronous return value. The chat provider is consumed by `AssistantService` via SSE streaming, with sessions that span multiple LLM round-trips (read tool execution, confirmation flows). Merging them forces every adapter to implement both concerns, and forces the `IntegrationRegistry` to resolve a single bean that handles both stateless text generation and stateful conversational streaming.

## Options Considered

### Option 1: Extend AiProvider with Chat Methods

Add `chat(ChatRequest, Consumer<StreamEvent>)`, `validateKey(String, String)`, and `availableModels()` to the existing `AiProvider` interface. All providers implement both one-shot and chat methods.

- **Pros:**
  - Single interface — no new abstraction to learn
  - Single adapter registration per provider via `@IntegrationAdapter`
  - `IntegrationRegistry` resolves one bean per tenant for all AI operations

- **Cons:**
  - Violates Interface Segregation Principle — `NoOpAiProvider` and any future one-shot-only provider must implement chat methods with no-op stubs
  - Different streaming models in one interface: `AiTextResult generateText()` returns a value; `void chat(ChatRequest, Consumer<StreamEvent>)` pushes events. These are fundamentally different invocation patterns
  - The `chat()` method's `Consumer<StreamEvent>` callback introduces a streaming concern into an interface designed for request/response. Future maintainers must understand both paradigms to work on either
  - Adapter complexity increases — an Anthropic adapter must handle both the Completions API (one-shot) and the Messages API with streaming (chat). These are different HTTP endpoints with different payload structures
  - Testing becomes harder — mocking `AiProvider` in tests that only care about `summarize()` now requires stubbing chat methods too

### Option 2: Create Separate LlmChatProvider Interface (Selected)

Define a new `LlmChatProvider` interface in `integration/ai/chat/` with `chat()`, `validateKey()`, `availableModels()`, and `providerId()`. Register chat providers via a new `LlmChatProviderRegistry` that auto-discovers `LlmChatProvider` beans. The existing `AiProvider` interface and `IntegrationRegistry` resolution remain unchanged.

- **Pros:**
  - Clean separation of concerns — one-shot text generation (`AiProvider`) and conversational streaming (`LlmChatProvider`) are independent abstractions
  - Each interface has a single invocation pattern: return value for `AiProvider`, callback consumer for `LlmChatProvider`
  - `NoOpAiProvider` is unaffected — no new methods to implement
  - Chat providers can be tested independently without mocking text generation methods
  - Future providers that support only chat (or only text generation) implement only the relevant interface
  - The `LlmChatProviderRegistry` can apply chat-specific logic (model validation, key format checking) without polluting `IntegrationRegistry`

- **Cons:**
  - Two interfaces for AI operations — developers must know which to use (mitigated by clear naming: "text generation" vs. "chat")
  - An Anthropic adapter that supports both text generation and chat needs two `@Component` classes (or one class implementing both interfaces)
  - `IntegrationService.testConnection()` switch statement for the `AI` domain must decide which interface to use — currently resolves `AiProvider`, needs to also consider `LlmChatProvider` for key validation

### Option 3: Merge into Single Unified AI Interface

Create a new `AiService` interface that replaces `AiProvider` and includes both text generation and chat methods. Deprecate `AiProvider`.

- **Pros:**
  - Single interface going forward — no ambiguity
  - Can redesign the text generation methods to use modern patterns (streaming, structured output)
  - Clean break from the existing `AiProvider` API

- **Cons:**
  - Breaking change to existing consumers of `AiProvider` — `DocumentTemplateService`, `TagSuggestionService`, and any other service using `generateText()` or `summarize()` must migrate
  - Forces chat capabilities onto providers that only support text generation
  - The unified interface becomes a large surface area: 7+ methods spanning two fundamentally different interaction models
  - Migration cost for existing tests (unit and integration tests mocking `AiProvider`)

### Option 4: Use AI SDK / External Library Directly

Skip the interface abstraction and use the Anthropic Java SDK (or a multi-provider SDK like LangChain4j) directly in `AssistantService`.

- **Pros:**
  - No custom abstraction layer — less code to maintain
  - SDK handles HTTP, SSE parsing, retries, and model-specific quirks
  - Community-maintained, updated when provider APIs change

- **Cons:**
  - Direct SDK dependency couples `AssistantService` to a specific provider — switching from Anthropic to OpenAI requires rewriting the service, not just adding an adapter
  - The Anthropic Java SDK brings transitive dependencies (OkHttp, Kotlin stdlib, Jackson modules) that may conflict with the Spring Boot 4 dependency tree
  - SDK abstractions may not align with the platform's `ScopedValue`-based multitenancy model — API keys are per-tenant, not application-wide
  - Cannot share the provider abstraction with the existing `AiProvider` test connection flow
  - Testing requires mocking SDK internals rather than a clean interface boundary

## Decision

**Option 2 — Create separate LlmChatProvider interface.**

## Rationale

The fundamental insight is that `AiProvider` and `LlmChatProvider` serve different architectural roles despite both interacting with LLM APIs. `AiProvider` is a background utility — services call `summarize()` or `suggestCategories()` and get a result. The caller controls the flow. `LlmChatProvider` is a session-oriented streaming interface — `AssistantService` calls `chat()` and the provider pushes events back via `Consumer<StreamEvent>`. The provider controls the flow within the callback.

1. **Different invocation patterns demand different interfaces.** `AiTextResult generateText(AiTextRequest)` is synchronous request/response. `void chat(ChatRequest, Consumer<StreamEvent>)` is asynchronous push. Putting both in one interface creates a "fat interface" where every consumer must understand both paradigms. The `Consumer<StreamEvent>` pattern was chosen specifically to avoid WebFlux dependencies (see [ADR-202](ADR-202-consumer-callback-streaming.md)) — embedding it in `AiProvider` would force that streaming concern onto all AI provider implementations.

2. **They coexist, not replace.** Background AI tasks (document summarization, tag suggestion) will continue using `AiProvider.generateText()` and `AiProvider.summarize()`. The conversational assistant uses `LlmChatProvider.chat()`. Both may even use the same underlying Anthropic API key (stored via `SecretStore` with key `"ai:anthropic:api_key"`), but the invocation patterns are distinct. An Anthropic adapter can implement both interfaces in a single class if desired, while keeping the interfaces separate.

3. **Independent evolution.** The chat interface will evolve rapidly — vision support, function calling enhancements, context window management, conversation summarization. The text generation interface is stable. Coupling their evolution forces `AiProvider` consumers to absorb changes driven by chat requirements.

4. **The registry pattern is proven.** `IntegrationRegistry` handles `AiProvider` resolution. `LlmChatProviderRegistry` follows the same auto-discovery pattern (`@Component` beans implementing `LlmChatProvider`) with a simpler lookup model (by `providerId()` string rather than by tenant configuration + domain). The registry is a small, focused class.

## Consequences

- **Positive:**
  - `AiProvider` and its existing consumers (`NoOpAiProvider`, `IntegrationRegistry`, `IntegrationService.testConnection()`) are completely unaffected
  - Chat provider implementations are focused — only streaming chat methods, no text generation stubs
  - Testing is clean — mock `LlmChatProvider` for assistant tests, mock `AiProvider` for text generation tests, never both
  - Future providers (OpenAI, Google) implement only the interfaces they support

- **Negative:**
  - Two AI-related interfaces in the codebase — `AiProvider` (one-shot) and `LlmChatProvider` (chat). Developers must understand the distinction. Mitigated by naming: the word "Chat" in `LlmChatProvider` signals its purpose
  - `IntegrationService.testConnection()` for the AI domain currently resolves `AiProvider`. The chat provider's `validateKey()` is called separately from `AssistantService`, not through `IntegrationService`. This creates two paths for "test AI connection" — one for the generic integration flow and one for the chat-specific key validation. May need unification in a future phase

- **Neutral:**
  - The `LlmChatProvider` interface lives in `integration/ai/chat/` as a sub-package of the existing `integration/ai/` package. The Anthropic adapter lives in `integration/ai/chat/anthropic/`. This mirrors the existing pattern where `EmailProvider` has sub-packages for `smtp/` and `sendgrid/`
  - The new interface does not affect the `IntegrationDomain.AI` enum or the `OrgIntegration` entity — the AI domain configuration (provider slug, enabled state, key suffix) is shared between both interfaces
