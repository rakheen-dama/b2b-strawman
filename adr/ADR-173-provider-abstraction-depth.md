# ADR-173: Provider Abstraction Depth

**Status**: Accepted
**Date**: 2026-03-12
**Phase**: Phase 45 — In-App AI Assistant (BYOAK)

## Context

Phase 45 introduces an in-app AI assistant backed by LLM providers. The first supported provider is Anthropic (Claude), but the architecture must accommodate future providers (OpenAI, Google) without rearchitecting the assistant service or tool layer. The question is how deep the provider abstraction should go: should the interface cover only the capabilities the assistant uses today (chat with tools, streaming, key validation), or should it pre-build abstractions for capabilities like embeddings, vision, function calling variations, and fine-tuning that might be useful in future phases?

## Options Considered

### 1. **Thin interface (chosen)** — only chat, validate, and models

The `LlmProvider` interface exposes three methods: `chat(ChatRequest, Consumer<StreamEvent>)`, `validateKey(String, String)`, and `availableModels()`. This covers exactly what the assistant needs: streaming chat with tool use, key validation for the settings page, and model listing for the dropdown.

- Pros:
  - Minimal implementation surface per provider — adding OpenAI means implementing 3 methods
  - No speculative abstractions that may not match future provider APIs
  - Easy to understand and test
  - Follows YAGNI — only build what is needed now
- Cons:
  - Adding embeddings or vision later requires extending the interface (or adding a new one)
  - Providers with unique capabilities (e.g., Anthropic's prompt caching) are not surfaced

### 2. **Full abstraction** — embeddings, vision, function calling variations, fine-tuning

A comprehensive interface with methods for every known LLM capability: `embed()`, `chatWithVision()`, `listFineTunes()`, capability flags, etc.

- Pros:
  - Future phases can use embeddings (document search) or vision (image analysis) without interface changes
  - Provider capabilities are discoverable via the interface
- Cons:
  - Most methods would return `UnsupportedOperationException` for most providers
  - Speculative design — embeddings and vision are explicitly out of scope
  - API shapes for embeddings and vision differ significantly across providers, making a good abstraction hard to design without real use cases
  - Increases implementation burden for every new provider

### 3. **Middleware pattern** — pluggable capability modules

A base provider interface with optional capability interfaces (`EmbeddingCapable`, `VisionCapable`) that providers can implement selectively. The service checks `instanceof` before calling capability-specific methods.

- Pros:
  - Providers only implement what they support
  - Capabilities are discoverable without dummy methods
  - Clean separation of concerns
- Cons:
  - Over-engineered for v1 where only chat is needed
  - `instanceof` checks spread through the codebase
  - Still requires designing capability interfaces speculatively
  - Adds cognitive overhead for a single-provider phase

## Decision

Use the thin interface. The `LlmProvider` interface exposes only `chat`, `validateKey`, and `availableModels` — the three capabilities the assistant needs today.

## Rationale

The assistant uses exactly one LLM capability: streaming chat with tool definitions. Key validation and model listing are settings-page utilities. Embeddings, vision, and fine-tuning are explicitly out of scope for Phase 45 and have no concrete design requirements. Building abstractions for capabilities without real use cases produces poor abstractions — the interface would need to be rewritten when those capabilities are actually needed anyway.

Extending the interface later is straightforward: add a method with a default implementation (Java 25 supports default methods on interfaces), or introduce a new capability-specific interface alongside `LlmProvider`. The cost of extension is low; the cost of premature abstraction (wrong abstractions, implementation burden, testing surface) is high.

## Consequences

- **Positive**: Minimal implementation surface per provider; adding OpenAI requires implementing 3 methods. Clear, testable interface. No speculative code.
- **Negative**: Adding embeddings or vision in a future phase requires extending the interface or introducing a new one.
- **Neutral**: The interface design is revisited if/when document intelligence (Layer 2) requires embeddings.
