# ADR-280: Evolve AiProvider Port for Skills (Not a New Port)

**Status**: Accepted

**Context**:

Phase 72 introduces two embedded AI skills (FICA verification, matter intake) that need a structured completion API: send a system prompt + user prompt, receive a structured JSON response with token counts and cost data. The codebase already has two AI-related interfaces:

1. **`AiProvider`** (`integration/ai/AiProvider.java`, Phase 52) — tenant-scoped port for one-shot text operations: `generateText(AiTextRequest)`, `summarize(String, int)`, `suggestCategories(String, List<String>)`, `testConnection()`. Registered in `IntegrationRegistry` with `IntegrationDomain.AI`. Implementations: `NoOpAiProvider` (default fallback). The `AnthropicAiProvider` was planned but never built — only the no-op exists.

2. **`LlmChatProvider`** (`assistant/provider/LlmChatProvider.java`, Phase 52) — provider-agnostic interface for streaming multi-turn chat with tool use: `chat(ChatRequest, Consumer<StreamEvent>)`, `validateKey(String, String)`, `availableModels()`. Implementation: `AnthropicLlmChatProvider`. Used by the Phase 52 chat panel and Phase 70 specialists.

Phase 72 skills need `complete(AiCompletionRequest): AiCompletionResponse` and `completeWithVision(AiVisionRequest): AiCompletionResponse` — single-call, synchronous, no streaming, no tool use, with detailed token/cost metrics in the response. The question is where to add these methods.

**Options Considered**:

1. **Extend the existing `AiProvider` interface with `complete()` and `completeWithVision()` (CHOSEN)** — Add two new methods to the existing port. Build `AnthropicAiProvider` implementing the full interface. Extend `NoOpAiProvider` with default implementations for the new methods.
   - Pros:
     - Reuses the established `IntegrationRegistry` resolution path (`IntegrationDomain.AI` -> `AiProvider`). No new registry wiring needed.
     - The existing `NoOpAiProvider` already handles the "AI not configured" fallback for the same domain. Adding new methods keeps the fallback consistent.
     - `OrgIntegration` with `domain = AI` and the `SecretStore` API key management is already wired for `AiProvider`. The new `AnthropicAiProvider` plugs into the same slot.
     - `testConnection()` is shared — both the existing text operations and the new completion operations use the same API key. One connection test verifies both.
     - The interface remains cohesive: all methods are one-shot AI text operations (not streaming, not chat). The new methods are richer versions of `generateText()` with structured input/output.
   - Cons:
     - The interface grows from 5 to 7 methods. However, the existing `generateText`/`summarize`/`suggestCategories` methods can be implemented as thin wrappers around `complete()` in the Anthropic adapter, reducing internal complexity.
     - `NoOpAiProvider` must implement two additional methods. The implementations are trivial (return a fixed "not configured" response).
     - If a future provider supports `complete()` but not `generateText()` (unlikely given the methods are semantically similar), it would need to implement all methods.

2. **Create a new `AiSkillProvider` port interface** — Define a separate interface with `complete()` and `completeWithVision()` in a new package. Register it alongside `AiProvider` in the `IntegrationRegistry`.
   - Pros:
     - Interface segregation: the new interface is focused on structured completion for skills. The existing `AiProvider` is untouched.
     - A future provider that supports skills but not the Phase 52 text operations could implement only `AiSkillProvider`.
   - Cons:
     - Duplicates the `IntegrationDomain.AI` resolution path — the registry would need to resolve two interfaces for the same domain, using the same API key, the same connection test, the same tenant scoping. This is the same pattern as `AccountingProvider` + `AccountingPaymentSource` (ADR-279), but the justification is weaker here: accounting push and pull have genuinely different operational semantics (event-driven vs scheduled), while `generateText()` and `complete()` are both synchronous one-shot calls that differ only in request/response richness.
     - Two separate `NoOp` implementations (or one class implementing both interfaces) for the same "AI not configured" fallback. More wiring for no functional benefit.
     - `testConnection()` would need to exist on both interfaces or be shared via a common supertype, adding abstraction without value.
     - Developers must understand which interface to use for which AI operation. The distinction is subtle (structured vs simple text) and likely to cause confusion.

3. **Merge `AiProvider` and `LlmChatProvider` into a single `AiPort`** — Combine all AI operations (one-shot text, structured completion, streaming chat) into one interface.
   - Pros:
     - One interface for all AI operations. Maximum discoverability.
   - Cons:
     - **Violates ISP severely.** Streaming chat (`Consumer<StreamEvent>`) and one-shot completion are fundamentally different operational patterns. The chat interface requires tool use support, multi-turn state, streaming event handling. The completion interface returns a single synchronous result. Forcing both into one interface creates a god interface where every implementation must handle both patterns.
     - The `NoOpAiProvider` would need to implement streaming chat methods. The `AnthropicLlmChatProvider` would need to implement one-shot methods. Neither gains from the coupling.
     - ADR-200 (Phase 52) explicitly separated these interfaces because the operational semantics differ. Reversing that decision adds no value.
     - Testing becomes broader — a test for skill completion would need to mock streaming chat methods and vice versa.

**Decision**: Option 1 — Extend the existing `AiProvider` interface with `complete(AiCompletionRequest)` and `completeWithVision(AiVisionRequest)`.

**Rationale**:

The key question is whether the new methods represent a genuinely different operational concern (which would justify a separate interface per ADR-279's pattern) or a richer version of the same concern (which should stay on the same interface). The existing `AiProvider` methods — `generateText`, `summarize`, `suggestCategories` — are one-shot, synchronous, text-in/text-out. The new `complete()` and `completeWithVision()` methods are also one-shot, synchronous, text-in/text-out, with richer request/response shapes. They share the same API key, the same connection test, the same tenant scoping, and the same fallback (`NoOpAiProvider`). The operational semantics are identical — only the data shapes differ.

This is unlike the `AccountingProvider` / `AccountingPaymentSource` split (ADR-279), where push (event-driven, per-entity) and pull (schedule-driven, batch) have genuinely different operational patterns, different workers, different retry strategies, and different rate-limit implications. For `AiProvider`, all methods are called synchronously by the same type of caller (a service that needs an AI text response).

The `LlmChatProvider` separation remains correct — streaming multi-turn chat with tool use is a fundamentally different pattern from one-shot completion. Phase 72 does not touch `LlmChatProvider`.

**Consequences**:

- Positive:
  - `AiProvider` remains the single port for all one-shot AI text operations. Resolution via `IntegrationRegistry` is unchanged.
  - The new `AnthropicAiProvider` implements the full interface, including `generateText`/`summarize`/`suggestCategories` (which can delegate to `complete()` internally). This retroactively provides a real implementation for Phase 52 text operations that previously only had the no-op.
  - `NoOpAiProvider` grows by two methods — trivial implementations that return a "not configured" message.
  - No new registry wiring, no new domain, no new package structure for the port itself.

- Negative:
  - The interface has 7 methods. If future phases add more AI operation shapes (e.g. `embed()` for embeddings, `moderate()` for content moderation), the interface may need to be split. That split is deferred until there is a concrete second concern — YAGNI.
  - Every `AiProvider` implementation must implement all 7 methods. For v1 (only `NoOpAiProvider` and `AnthropicAiProvider`), this is fine.

- Neutral:
  - The new request/response records (`AiCompletionRequest`, `AiVisionRequest`, `AiCompletionResponse`, `AiImageInput`) live alongside `AiTextRequest` and `AiTextResult` in `integration/ai/`. The old records are not deprecated — existing callers continue to use them.
  - `AnthropicAiProvider` lives in `integration/ai/anthropic/` — a new subpackage alongside the existing `integration/ai/` package.

- Related: [ADR-200](ADR-200-llm-chat-provider-interface.md) (LlmChatProvider separation from AiProvider), [ADR-279](ADR-279-sibling-payment-source-port.md) (ISP-based interface split — justified for push/pull, not justified here), [ADR-088](ADR-088-integration-port-package-structure.md) (port package structure), [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (Phase 70 specialist architecture)
