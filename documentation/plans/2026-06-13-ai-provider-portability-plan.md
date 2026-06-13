# AI Provider Portability — Audit + Migration Plan

## Context

Kazi does all LLM work against Anthropic. Prospective customers want OpenAI/Azure,
Google Gemini/Vertex, and AWS Bedrock. This plan audits the current state (verified
against the codebase) and lays out a phased migration to multi-provider portability
by adopting **Spring AI 2.0** as the wire-protocol engine *beneath* the two AI ports
that already exist.

**Audit conclusion (verified): the abstraction is already in place.** Anthropic is
hardcoded *below* two clean ports, not at the domain level. The work is swapping
hand-rolled HTTP clients for a portable engine and adding provider adapters — not
re-architecting. Every claim below was confirmed by reading the cited files.

---

## Current State (verified)

### Two ports already abstract AI (both kept)

```
                         per-tenant dispatch
  Services (skills,      ┌───────────────────────────────┐
  AiSkillExecutionService│ IntegrationRegistry.resolve(   │   AiProvider (one-shot)
  IntegrationService) ──▶│   AI, AiProvider.class)        │──▶ AnthropicAiProvider
                         │  reads OrgIntegration.providerSlug   (slug=anthropic) / NoOp
                         └───────────────────────────────┘        │
                                                                  ▼ resolves key itself
                                                          SecretStore  ai:{slug}:api_key
                                                                  │
                                                                  ▼
                                                          AnthropicApiClient (RestClient)

  AssistantService /     ┌───────────────────────────────┐
  NonInteractiveSpecial- │ LlmChatProviderRegistry.get(   │   LlmChatProvider (streaming)
  istRunner ────────────▶│   providerSlug)                │──▶ AnthropicLlmProvider
   resolves key UPSTREAM │  by providerId()               │     (raw java.net.http SSE)
   ChatRequest.apiKey()  └───────────────────────────────┘
```

- **`AiProvider`** (`integration/ai/AiProvider.java`) — one-shot text/structured/vision:
  `generateText`, `summarize`, `suggestCategories`, `testConnection`,
  `complete(AiCompletionRequest)`, `completeWithVision(AiVisionRequest)`.
  Dispatched per tenant by `IntegrationRegistry.resolve(AI, AiProvider.class)` keyed on
  `OrgIntegration.providerSlug`. Adapters self-register via
  `@IntegrationAdapter(domain = AI, slug = …)`. **The adapter resolves its own API key**
  from `SecretStore` (e.g. `AnthropicAiProvider.resolveApiKey()`).
- **`LlmChatProvider`** (`assistant/provider/LlmChatProvider.java`) — streaming multi-turn
  chat + tool use: `chat(ChatRequest, Consumer<StreamEvent>)`, `validateKey`,
  `availableModels`. Dispatched by `LlmChatProviderRegistry.get(slug)` (auto-discovered by
  `providerId()`). **Here the API key is resolved upstream** by `AssistantService`
  (`IntegrationKeys.aiApiKey(slug)` → `SecretStore`) and passed in via `ChatRequest.apiKey()`.
  (ADR-200 deliberately splits the two ports; ADR-202 mandates no WebFlux.)

> **Key asymmetry to design around in Phase 0:** one-shot adapters fetch their own key from
> `SecretStore`; streaming providers receive the key in the request. A per-tenant `ChatModel`
> factory must therefore accept an **explicit `apiKey` argument** so it serves both paths.

### What is Anthropic-locked (all *below* the ports)
- `integration/ai/anthropic/AnthropicApiClient.java` — `RestClient` → `/v1/messages`,
  `x-api-key`/`anthropic-version` headers, `cache_control: ephemeral`,
  `AnthropicMessagesRequest/Response` DTOs. **`testConnection()` hardcodes
  `claude-haiku-4-5`** (line 94 — it's here, not in `AnthropicAiProvider`).
- `assistant/provider/anthropic/AnthropicLlmProvider.java` — raw `java.net.http.HttpClient`,
  hand-parsed Anthropic SSE event names; `availableModels()` returns the hardcoded Claude list.
- **Model-ID literals defaulting to a Claude model even for non-Anthropic tenants** — there are
  **four** `parseModel`/default sites (draft listed two; two more found):
  | Site | Literal |
  |------|---------|
  | `assistant/AssistantService.java:55` `DEFAULT_MODEL` + `parseModel:466` | `claude-sonnet-4-6` |
  | `assistant/specialist/NonInteractiveSpecialistRunner.java:43` + `parseModel:286` | `claude-sonnet-4-6` |
  | `integration/IntegrationService.java:32` `DEFAULT_MODEL` + `parseModel:297` | `claude-sonnet-4-6` |
  | `integration/ai/anthropic/AnthropicAiProvider.java:29` `DEFAULT_MODEL` | `claude-sonnet-4-20250514` (dated ID — drifts from the `-4-6` used elsewhere) |
  | `integration/ai/profile/AiFirmProfile.java:49` `preferredModel` default | `claude-sonnet-4-6` |
  | `integration/ai/cost/AiCostService.java:56` pricing fallback | `claude-sonnet-4-6` |

  The three identical `parseModel` copies are duplication worth consolidating into one shared
  helper while we're here (Phase 3).
- **Frontend** (all AI proxies through backend; zero client LLM SDKs):
  - `lib/schemas/ai-profile.ts:5` `preferredModelEnum = z.enum(["claude-sonnet-4-6","claude-opus-4-6"])`
    + matching radios in `components/ai/ai-profile-form.tsx`.
  - 8 "Anthropic API key" copy strings: `components/documents/documents-panel.tsx:360`,
    `components/ai/{compliance-audit-tab,fica-verification-panel,matter-intake-panel}.tsx`,
    `components/ai/contract-review-button.tsx:47`, `components/marketing/ai-section.tsx:45`,
    `components/marketing/integrations-section.tsx:36`, `components/assistant/empty-state.tsx:35`.
  - **Already backend-driven (good):** `components/integrations/IntegrationCard.tsx` provider+model
    dropdowns consume `availableProviders` / the `GET /api/settings/integrations/ai/models` endpoint.

### Cross-cutting machinery to preserve (Spring AI does NOT own these)
- **BYOAK** — per-tenant key in `SecretStore`/`org_secrets` (AES-256-GCM), convention
  `ai:{slug}:api_key` via `IntegrationKeys.aiApiKey()` (ADR-201).
- **Cost metering** — `AiCostService` + `AiPricingProperties` (`@ConfigurationProperties("kazi.ai")`,
  per-model pricing under `kazi.ai.pricing.*` in `application.yml`, ZAR conversion), budget gate.
- **Execution gates** — `AiExecutionGate` attorney-approval queue (ADR-281).
- **Prompts as code** — classpath `resources/ai/skills/*/system.txt` + `output-schema.json` (ADR-283).
- **Models endpoint** — `GET /api/settings/integrations/ai/models` (`AiSettingsController`) →
  `IntegrationService.getAiModels()` → `LlmChatProviderRegistry.get(slug).availableModels()`. So a
  provider-scoped model list comes **free** once new `LlmChatProvider`s register; no controller change.

### External landscape (research as of June 2026 — re-verify in Phase 0)
- **Spring AI 2.0** targets Spring Boot 4 (we're on **4.0.2**, confirmed in `backend/pom.xml`). 1.x is
  Boot 3.5 only. 2.0 is milestone-grade (M-series, GA ~mid-2026); Anthropic module moved to the
  official Anthropic Java SDK. Unified `ChatModel`/`ChatClient` over 15+ providers incl. all three
  targets; portable streaming, tool calling, structured output, Micrometer.
- **Koog 1.0** (JetBrains) — JVM agent framework, Kotlin-first. Value is *agent orchestration*
  (GOAP/strategies/MCP) we've already hand-built. Not adopted now.

---

## Recommendation: Spring AI 2.0 behind the existing ports

Adopt **Spring AI 2.0** as the engine *inside* the `AiProvider` and `LlmChatProvider`
implementations. **Do not** expose Spring AI's `ChatClient` to services — the two ports remain the
anti-corruption boundary.

**Why Spring AI, not Koog:** the ports already exist (Spring AI `ChatModel`/`ChatClient` slot in as
adapter internals); the three target providers are first-class Spring AI starters; it's
Java-idiomatic and Spring-native (Boot 4 autoconfig, Micrometer, `BeanOutputConverter`). Koog is
Kotlin-first orchestration we already own — two AI frameworks + Kotlin violates simplicity (and
the backend YAGNI rule). Koog stays a documented *future* option for the agentic layer only.

**Why keep the ports (not adopt `ChatClient` directly):** preserves per-tenant
`IntegrationRegistry`/`LlmChatProviderRegistry` dispatch, BYOAK `SecretStore` keys, cost metering,
and execution gates — none of which Spring AI models; confines Spring-AI-2.0-milestone API churn to
the adapter classes; enables provider-by-provider rollout without touching any skill/specialist.

> **Honesty note:** the load-bearing external assumptions (Spring AI 2.0 milestone status, the
> per-tenant-key builder/`mutate()` API, streaming on `reactor-core` only) are post-knowledge-cutoff
> and unverified in-repo. **Phase 0 exists precisely to prove them before any commitment.** Do not
> skip it.

---

## Migration Plan (phased; each phase ≈ one epic via `/breakdown`)

### Phase 0 — De-risk spike (HARD GATE — do not start Phase 1 until answered) ⚠️
Prove two unknowns with a throwaway OpenAI adapter:
1. **Per-tenant key at request time.** Spring AI `ChatModel` beans normally bind one static key from
   properties. Validate a **per-tenant `ChatModel` factory** that takes an explicit `apiKey` argument
   (caching by `providerSlug+model+keyHash`) built via the 2.0 builder/`mutate()` API. Must serve
   **both** the one-shot path (key from `SecretStore`) and streaming path (key from `ChatRequest`).
2. **Streaming without WebFlux (ADR-202).** Confirm `ChatModel.stream()` (Reactor `Flux`) can be
   block-subscribed and bridged to `Consumer<StreamEvent>` using only `reactor-core` (transitively on
   Spring AI core), **not** `spring-webflux`. Verify no `spring-boot-starter-webflux` enters the tree.

Add the Spring AI BOM to `backend/pom.xml` `<dependencyManagement>`; add the OpenAI starter only.
**Output: a written thumbs-up/down on the architecture, plus the confirmed dependency tree.**

### Phase 1 — One-shot `AiProvider` on Spring AI
- New `integration/ai/spring/SpringAiCompletionEngine` wrapping the Phase-0 `ChatModel` factory; maps
  `AiCompletionRequest`/`AiVisionRequest` → Spring AI `Prompt`+`Media`, and `ChatResponse` →
  `AiCompletionResponse` (use `ChatResponse.getMetadata().getUsage()` for tokens — uniform across
  providers, replaces Anthropic-specific extraction in `AnthropicApiClient.mapResponse`).
- Skill structured output: prefer `BeanOutputConverter`/JSON-schema options over hand-rolled parsing
  where it maps cleanly (keep `resources/ai/skills/*/output-schema.json` as the schema source).
- Add `@IntegrationAdapter(domain = AI, slug = …)` adapters: `OpenAiAiProvider`,
  `VertexAiGeminiAiProvider`, (`BedrockAiProvider` — see wrinkle), each keyed `ai:{slug}:api_key`.
  **Recommended order: OpenAI → Gemini → Bedrock** (Bedrock has the credential-shape wrinkle below).
  Decide in Phase 0 whether to re-implement `AnthropicAiProvider` on the same engine or leave its
  custom client untouched and only add the new adapters (lower-risk default).
- Prompt caching (`cache_control: ephemeral`) becomes provider-conditional (Anthropic-only; no-op
  elsewhere).
- Per-provider `testConnection()` (drop the hardcoded `claude-haiku-4-5` in `AnthropicApiClient`).

### Phase 2 — Streaming `LlmChatProvider` on Spring AI
- New engine bridging `ChatModel.stream()` + Spring AI `ToolCallback` → the existing
  `StreamEvent`/`ChatRequest` contract and tool-confirmation flow (preserve the `StreamEvent.TextDelta
  / ToolUse / Usage / Done / Error` taxonomy that `AnthropicLlmProvider.parseSseStream` emits).
- Adapters: OpenAI / Gemini / Bedrock chat providers registering via `providerId()` in
  `LlmChatProviderRegistry`. Each provides its own `availableModels()` (this auto-feeds the models
  endpoint and the frontend dropdown).

### Phase 3 — Generalize cost metering & model registry
- Extend `kazi.ai.pricing.*` in `application.yml` with new models (e.g. `gpt-4o`, `gemini-1.5-pro`,
  Bedrock model IDs) — same `ModelPricing` record (input/output/cacheRead/cacheCreation per-M-token).
- Replace the **six** hardcoded `claude-sonnet-4-6` fallbacks (`AiCostService:56`,
  `AssistantService:55`, `NonInteractiveSpecialistRunner:43`, `IntegrationService:32`,
  `AnthropicAiProvider:29`, `AiFirmProfile:49`) with **fail-loud or provider-aware** defaults.
  Consolidate the three identical `parseModel` copies into one shared helper.
- No controller change needed for the model list — `getAiModels()` already delegates to the
  provider's `availableModels()`.

### Phase 4 — Frontend de-Anthropic-ification
- Replace `lib/schemas/ai-profile.ts` `preferredModelEnum` and the `ai-profile-form.tsx` radios with
  the dynamic, provider-scoped model list (the `/models` endpoint the `IntegrationCard` already uses).
- Genericize the 8 "Anthropic API key" copy strings → provider-aware / "AI provider" wording (the
  `components/ai/*`, `components/documents/documents-panel.tsx`, `assistant/empty-state.tsx`,
  `marketing/{ai-section,integrations-section}.tsx` sites), plus the backend `NoOpAiProvider` message.

### Phase 5 — ADRs & cleanup
- New ADR: "Spring AI 2.0 as the multi-provider engine beneath `AiProvider`/`LlmChatProvider`."
  Annotate ADR-283 (caching now provider-conditional). Optionally run `/architecture` ADR-first
  before Phase 1 if you prefer.

---

## Known wrinkles to flag
- **Bedrock credentials don't fit BYOAK.** Bedrock authenticates via AWS IAM (access key/secret or
  assumed role), not a single API key — the `ai:{slug}:api_key` `SecretStore` convention breaks. Phase 1
  needs a small credential-shape extension (store an AWS key pair / role ARN) **or** defer Bedrock
  behind OpenAI + Gemini. Recommend deferring Bedrock to last.
- **Spring AI 2.0 is milestone-grade.** The anti-corruption boundary keeps that risk inside the adapter
  classes; pin the milestone version and budget for one API bump before GA.
- **Prompt caching is Anthropic-only** — non-Anthropic tenants lose that cost optimization (acceptable;
  metered honestly by Phase 3).

---

## Verification (per phase, end-to-end — observed, not inferred; per CLAUDE.md quality gates)
1. **Backend full suite:** `cd backend && ./mvnw verify` clean (full, not `-Dtest=…`).
2. **BYOAK round-trip per provider:** Settings → Integrations, set each provider's key, "Test connection"
   green.
3. **Skill E2E:** run FICA verification via the UI on the E2E stack (`bash compose/scripts/e2e-up.sh`,
   port 3001) against each configured provider; confirm a result renders **and** a row lands in
   `ai_executions` with non-zero tokens + cost.
4. **Streaming E2E:** open the assistant panel, run a multi-turn chat per provider; confirm SSE deltas
   stream and a tool-use gate is created.
5. **Frontend:** `cd frontend && pnpm lint && pnpm build && pnpm test` green (same for `portal/` if touched).
6. **Review pass per PR** (CodeRabbit / review subagent); one fix-cluster per PR (scope discipline).

## Scope note
This is the recommendation + roadmap. Each phase is sized to become one epic via `/breakdown`; formal
ADRs come from `/architecture` (Phase 5, or up front). **Phase 0 is a hard gate** — the per-tenant-key
and no-WebFlux questions must be answered before Phase 1.
