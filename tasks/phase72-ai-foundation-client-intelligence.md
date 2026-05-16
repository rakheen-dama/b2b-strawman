# Phase 72 — AI Foundation + Client Intelligence (FICA & Matter Intake)

> **Architecture**: [`architecture/phase72-ai-foundation-client-intelligence.md`](../architecture/phase72-ai-foundation-client-intelligence.md)
> **Requirements**: [`requirements/claude-code-prompt-phase72.md`](../requirements/claude-code-prompt-phase72.md)
> **ADRs**: [ADR-280](../adr/ADR-280-evolve-ai-provider-port-for-skills.md), [ADR-281](../adr/ADR-281-execution-gate-pattern-attorney-liability.md), [ADR-282](../adr/ADR-282-per-invocation-cost-metering-byoak.md), [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md), [ADR-284](../adr/ADR-284-document-reading-s3-vision-no-vector-store.md), [ADR-285](../adr/ADR-285-stub-ai-provider-for-ci-testing.md)
> **Predecessors**: Phase 21 (Integration Ports + BYOAK -- `IntegrationRegistry`, `OrgIntegration`, `SecretStore`, `IntegrationGuardService`), Phase 14 (Customer Compliance -- `ComplianceChecklist`, `ComplianceChecklistItem`), Phase 52 (In-App AI Assistant -- `AiProvider` port, `NoOpAiProvider`, `LlmChatProvider`), Phase 55 (Legal Foundations -- conflict check, LSSA tariff), Phase 64 (Legal Vertical QA -- matter templates, terminology pack), Phase 6/6.5 (Audit + Notifications)
> **Starting epic**: 526 . Last completed: 525 (Phase 71)
> **Migration high-water at phase start**: tenant **V121**. Phase 72 ships **one** tenant migration (V122).

Phase 72 builds the AI infrastructure layer that all future AI skills depend on. It evolves the existing `AiProvider` port with `complete()` and `completeWithVision()` methods, ships the first real `AnthropicAiProvider` implementation, adds a firm AI profile entity that drives all downstream skill prompts, introduces execution gates that enforce attorney approval before any AI-suggested action takes effect, delivers per-invocation cost metering with tenant budget enforcement, and ships two embedded skills -- FICA/KYC verification and matter intake intelligence -- targeting the legal-za vertical.

Three strategic constraints: (1) **Anthropic only for v1** -- no OpenAI/Google adapters. (2) **Mandatory execution gates** -- the Attorneys Act makes the attorney personally liable; AI suggests, human decides. (3) **BYOAK** -- each tenant provides their own Anthropic API key; no platform-subsidised tokens.

---

## Open Questions

- **V121 migration landing.** Phase 71 ships V121. V122 must not be created until V121 has merged. If Phase 71 and Phase 72 development overlap, the V122 migration file must be held in a feature branch until V121 is on `main`.
- **PDFBox dependency.** The architecture notes PDFBox is a transitive dependency via Phase 42. Verify at implementation time that `org.apache.pdfbox:pdfbox` is already in the dependency tree. If not, add it to `pom.xml` in slice 530A.
- **`application.yml` pricing configuration.** The AI pricing configuration (`kazi.ai.pricing.*`, `kazi.ai.exchange-rate.*`, `kazi.ai.timeout-seconds`, etc.) lands in slice 528B alongside `AiCostService`. Corresponding `application-test.yml` entries must be added in the same slice.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 526 | AiProvider Evolution + AnthropicAiProvider | Backend | -- | L | 526A, 526B | **Done** — 526A (PR #1313), 526B (PR #1314) |
| 527 | AiFirmProfile Entity + API + Frontend | Both | 526A | L | 527A, 527B | 527A **Done** (PR #1315) |
| 528 | AiExecution + AiExecutionGate + Cost Metering | Backend | 527A | L | 528A, 528B | 528A **Done** (PR #1316) |
| 529 | Skill Execution Infrastructure + StubAiProvider | Backend | 526A, 528A | M | 529A, 529B | |
| 530 | FICA Verification Skill (Backend + Frontend) | Both | 529A | L | 530A, 530B | |
| 531 | Matter Intake Skill (Backend + Frontend) | Both | 529A | L | 531A, 531B | |

**Slice count: 12** (6 architecture slices expanded to 12 numbered slices to enforce the backend-frontend separation rule and honour the 6-10 files / ~800 LOC slice-sizing budget).

---

## Dependency Graph

```
PHASES already complete:
  Phase 21 (Integration Ports — OrgIntegration, SecretStore, IntegrationRegistry, IntegrationGuardService)
  Phase 14 (Customer Compliance — ComplianceChecklist, ComplianceChecklistItem)
  Phase 52 (AiProvider port, NoOpAiProvider, LlmChatProvider, AnthropicLlmChatProvider)
  Phase 55 (Legal Foundations — ConflictCheck, TariffItem, LSSA tariff)
  Phase 64 (Legal Vertical QA — matter templates, terminology pack)
  Phase 6/6.5 (AuditEvent, Notification, email delivery)
  Phase 70 (AiSpecialistInvocation, AiLlmCall, specialist approval queue)
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 1 — Provider Foundation (sequential)               │
        │                                                          │
        │   [526A  AiProvider interface evolution — add complete()  │
        │          + completeWithVision() methods, new request/     │
        │          response records, NoOpAiProvider extension]      │
        │                       │                                  │
        │                       ▼                                  │
        │   [526B  AnthropicAiProvider + AnthropicApiClient —      │
        │          real Anthropic implementation, HTTP client,      │
        │          prompt caching, rate limit handling]             │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┘
                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 2 — Profile + Execution Entities (parallel)        │
        │                                                          │
        │   [527A  V122 migration (ALL 3 tables + capability       │
        │          seeds); AiFirmProfile entity + repo + service   │
        │          + controller; Capability enum additions]        │
        │                       │                                  │
        │   [528A  AiExecution + AiExecutionGate entities +        │
        │          repos; GateAction sealed interface +             │
        │          GateActionExecutor]                             │
        │                       │                                  │
        │               ┌───────┘                                  │
        │               ▼                                          │
        │   [528B  AiExecutionGateService (approve/reject/expire)  │
        │          + AiExecutionGateController + AiCostService     │
        │          + pricing config in application.yml]            │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 3 — Skill Infra + Profile Frontend (parallel)      │
        │                                                          │
        │   [529A  AiSkill interface + AiSkillExecutionService +   │
        │          AiSkillController + StubAiProvider for tests]   │
        │                                                          │
        │   [527B  Frontend — settings/ai page + profile form +   │
        │          cost summary + nav items + Zod schema]          │
        │                                                          │
        │   [529B  Frontend — AI reviews page (gate list) +        │
        │          execution history page + gate review UI +       │
        │          API client functions]                           │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 4 — Skills (parallel after 529A)                   │
        │                                                          │
        │   [530A  FicaVerificationSkill backend — skill class,    │
        │          FicaDocumentReader, system prompt, output        │
        │          schema, integration test]                       │
        │                                                          │
        │   [531A  MatterIntakeSkill backend — skill class,        │
        │          system prompt, output schema, integration test] │
        └─────────────────────────────────────────────────────────┘
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 5 — Skill Frontends (parallel after Stage 4)       │
        │                                                          │
        │   [530B  Frontend — FICA verification panel on customer  │
        │          detail page + execution gate card component]    │
        │                                                          │
        │   [531B  Frontend — matter intake panel on project       │
        │          creation page + cost summary component]         │
        └─────────────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- After **526A** lands, **526B** and **527A** can begin (526B depends on 526A for the interface; 527A depends on 526A for Capability additions but mainly creates entities).
- After **527A** lands, **528A** can begin (needs V122 migration to be in place).
- **529A**, **527B**, and **529B** parallelise in Stage 3 (529A needs 528A; 527B needs 527A backend; 529B needs 528B backend).
- **530A** and **531A** parallelise in Stage 4 -- the two skills are independent.
- **530B** and **531B** parallelise in Stage 5 -- each skill's frontend is independent.

---

## Implementation Order

### Stage 1 -- Provider Foundation (sequential)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **526A** | Evolve `AiProvider` interface -- add `complete(AiCompletionRequest)` and `completeWithVision(AiVisionRequest)` methods; create `AiCompletionRequest`, `AiVisionRequest`, `AiImageInput`, `AiCompletionResponse` records; extend `NoOpAiProvider` with default implementations of new methods. **Done** (PR #1313) |
| 1b | **526B** | `AnthropicAiProvider` implementing full `AiProvider` interface; `AnthropicApiClient` using `RestClient` for Anthropic Messages API; `AnthropicProperties` configuration; prompt caching with `cache_control`; rate limit handling; retry-on-429; integration tests with mocked HTTP. **Done** (PR #1314) |

### Stage 2 -- Profile + Execution Entities (sequential within, parallel across)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 2a | **527A** | V122 migration (ALL 3 tables + indexes + capability seeds); `AiFirmProfile` entity + repo + service + controller; `Capability` enum additions (`AI_MANAGE`, `AI_EXECUTE`, `AI_REVIEW`); profile assembly for system prompts; integration tests. **Done** (PR #1315) | -- |
| 2b | **528A** | `AiExecution` entity + repo (with cost aggregation query); `AiExecutionGate` entity + repo (with drain query for expiry); `GateAction` sealed interface (`MarkKycCompleteAction`, `SelectMatterTemplateAction`, `ClearConflictAction`); `GateActionExecutor`; integration tests. **Done** (PR #1316) | After 527A |
| 2c | **528B** | `AiExecutionGateService` (approve/reject/expire with `@Scheduled` expiry worker); `AiExecutionGateController` (AI_REVIEW gated); `AiCostService` (cost calculation, budget check, cost summary); AI pricing + exchange rate config in `application.yml` + `application-test.yml`; integration tests. | After 528A |

### Stage 3 -- Skill Infra + Frontend (parallel after Stage 2)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 3a | **529A** | `AiSkill` interface; `AiSkillExecutionService` (shared orchestration -- pre-flight, invoke, record, create gates, notify, audit); `AiSkillController` (AI_EXECUTE gated); `SkillExecutionRequest` + `SkillContext` records; `StubAiProvider` (`@TestConfiguration @Primary`); canned response test resources; integration tests. | 527B, 529B |
| 3b | **527B** | Frontend -- `settings/ai/page.tsx`; `components/ai/ai-profile-form.tsx` (cold-start wizard + edit); `components/ai/ai-cost-summary.tsx`; `lib/schemas/ai-profile.ts` (Zod); `lib/api/ai.ts` (API client functions for profile + cost summary); `lib/nav-items.ts` modification (add AI settings + AI reviews nav items). | 529A, 529B |
| 3c | **529B** | Frontend -- `ai/reviews/page.tsx` (pending gates list + history tab); `settings/ai/history/page.tsx` (execution history table); `components/ai/execution-gate-card.tsx` (gate review card with approve/reject); API client functions for gates + executions in `lib/api/ai.ts` (extend). | 529A, 527B |

### Stage 4 -- Skills Backend (parallel after 529A)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 4a | **530A** | `FicaVerificationSkill` implementing `AiSkill`; `FicaVerificationOutput` record; `FicaDocumentReader` (S3 fetch + PDFBox text extraction + vision fallback); `resources/ai/skills/fica-verification/system.txt`; `resources/ai/skills/fica-verification/output-schema.json`; `test/resources/ai/stubs/fica-verification/response.json`; integration test. | 531A |
| 4b | **531A** | `MatterIntakeSkill` implementing `AiSkill`; `MatterIntakeOutput` record; `resources/ai/skills/matter-intake/system.txt`; `resources/ai/skills/matter-intake/output-schema.json`; `test/resources/ai/stubs/matter-intake/response.json`; integration test. | 530A |

### Stage 5 -- Skill Frontends (parallel after Stage 4)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 5a | **530B** | Frontend -- `components/ai/fica-verification-panel.tsx` (results + risk flags + per-item review); `customers/[id]/page.tsx` modification (add "Verify with AI" button to compliance panel); `components/ai/fica-result-display.tsx` (structured output rendering); API client functions for FICA skill in `lib/api/ai.ts` (extend). | 531B |
| 5b | **531B** | Frontend -- `components/ai/matter-intake-panel.tsx` (recommendations alongside creation form); `projects/new/page.tsx` modification (add "Get AI Recommendations" button); `components/ai/intake-result-display.tsx` (template suggestion + fee estimate + conflict screen); API client functions for intake skill in `lib/api/ai.ts` (extend). | 530B |

### Timeline

```
Stage 1: [526A] -> [526B]                                               <- sequential
Stage 2: [527A] -> [528A] -> [528B]                                     <- sequential
Stage 3: [529A] // [527B] // [529B]                                     <- 3-way parallel
Stage 4: [530A] // [531A]                                               <- 2-way parallel
Stage 5: [530B] // [531B]                                               <- 2-way parallel
```

A realistic day-by-day cadence: 526A days 1-3; 526B days 3-6; 527A days 4-7 (can start after 526A); 528A days 7-10; 528B days 10-13; 529A + 527B + 529B days 13-17 (3-way parallel); 530A + 531A days 17-21 (2-way parallel); 530B + 531B days 21-25 (2-way parallel).

---

## Epic 526: AiProvider Evolution + AnthropicAiProvider

**Goal**: Evolve the existing `AiProvider` port interface with `complete()` and `completeWithVision()` methods for structured AI completions, build the corresponding request/response records, extend `NoOpAiProvider`, and ship the first real `AnthropicAiProvider` implementation backed by an `AnthropicApiClient` that talks to the Anthropic Messages API via `RestClient`.

**References**: Architecture Section 12.3.1 (Evolved AiProvider Interface), Section 12.3.2 (AnthropicAiProvider), Section 12.3.3 (NoOpAiProvider Extension), Section 12.10 Slice 72A; [ADR-280](../adr/ADR-280-evolve-ai-provider-port-for-skills.md), [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md).

**Dependencies**: Phase 52 (`AiProvider` port, `NoOpAiProvider`, `IntegrationDomain.AI`); Phase 21 (`IntegrationRegistry`, `IntegrationGuardService`, `OrgIntegration`, `SecretStore`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **526A** | 526A.1-526A.6 | ~6 backend files (1 interface modification + 4 new records + 1 provider modification) | Evolve `AiProvider` interface with 2 new methods; create `AiCompletionRequest`, `AiVisionRequest`, `AiImageInput`, `AiCompletionResponse` records; extend `NoOpAiProvider` with default implementations. **Done** (PR #1313) |
| **526B** | 526B.1-526B.5 | ~6 backend files (1 provider + 1 API client + 1 config properties + 3 test files) | `AnthropicAiProvider` implementing full `AiProvider`; `AnthropicApiClient` using `RestClient`; `AnthropicProperties` config; integration tests with mocked HTTP. **Done** (PR #1314) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 526A.1 | Create `AiCompletionRequest` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiCompletionRequest.java` | covered by 526B.5 | existing `AiTextRequest.java` record in same package | `public record AiCompletionRequest(String systemPrompt, String userPrompt, String model, int maxTokens, double temperature, Map<String, String> metadata) {}`. Per architecture Section 12.3.1. |
| 526A.2 | Create `AiVisionRequest` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiVisionRequest.java` | covered by 526B.5 | existing `AiTextRequest.java` | `public record AiVisionRequest(String systemPrompt, String userPrompt, String model, int maxTokens, double temperature, Map<String, String> metadata, List<AiImageInput> images) {}`. Composition over inheritance -- not extending AiCompletionRequest. |
| 526A.3 | Create `AiImageInput` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiImageInput.java` | covered by 526B.5 | -- | `public record AiImageInput(String mediaType, String base64Data) {}`. |
| 526A.4 | Create `AiCompletionResponse` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiCompletionResponse.java` | covered by 526B.5 | existing `AiTextResult.java` | `public record AiCompletionResponse(String content, String model, int inputTokens, int outputTokens, int cacheReadInputTokens, int cacheCreationInputTokens, String stopReason, long durationMs) {}`. Per architecture Section 12.3.1. |
| 526A.5 | Evolve `AiProvider` interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiProvider.java` (modify) | covered by 526B.5 | existing interface shape -- additive change | Add two new methods: `AiCompletionResponse complete(AiCompletionRequest request)` and `AiCompletionResponse completeWithVision(AiVisionRequest request)`. Existing methods unchanged. Per [ADR-280](../adr/ADR-280-evolve-ai-provider-port-for-skills.md). |
| 526A.6 | Extend `NoOpAiProvider` with new methods | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/NoOpAiProvider.java` (modify) | covered by 526B.5 | architecture Section 12.3.3 provides implementation verbatim | Add `complete()` and `completeWithVision()` methods returning `AiCompletionResponse` with `"{\"error\": \"AI not configured. Connect an Anthropic API key in Settings > AI.\"}"` content, `"noop"` model, zero tokens, `"end_turn"` stop reason, 0 duration. Log input length/image count. |
| 526B.1 | Create `AnthropicProperties` configuration | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicProperties.java` | covered by 526B.5 | existing `@ConfigurationProperties` pattern in `XeroProperties.java` (Phase 71) or `S3Properties.java` | `@ConfigurationProperties(prefix = "kazi.ai.anthropic")`. Properties: `apiBaseUrl` (default `https://api.anthropic.com`), `apiVersion` (default `2023-06-01`), `timeoutSeconds` (default 120), `maxRetries` (default 3). Add corresponding entries to `application.yml` and `application-test.yml`. |
| 526B.2 | Create `AnthropicApiClient` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicApiClient.java` | 526B.5 | architecture Section 12.3.2 (API client spec) | `@Service`. Uses Spring `RestClient`. Methods: `sendCompletion(String apiKey, AiCompletionRequest request)` -- POST to `/v1/messages`, attaches `x-api-key` header + `anthropic-version` header + `cache_control: {"type": "ephemeral"}` on system prompt content block, parses response to `AiCompletionResponse`; `sendVisionCompletion(String apiKey, AiVisionRequest request)` -- same but with image content blocks (type `image`, source type `base64`); `testConnection(String apiKey)` -- lightweight call to verify key validity. Handles 429 with exponential backoff (max 3 retries), 120s timeout. Response parsing extracts `content[0].text`, `usage.input_tokens`, `usage.output_tokens`, `usage.cache_read_input_tokens`, `usage.cache_creation_input_tokens`, `stop_reason`, `model`. Jackson serialization. |
| 526B.3 | Create `AnthropicAiProvider` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicAiProvider.java` | 526B.5 | existing `NoOpAiProvider` for interface contract; architecture Section 12.3.2 | `@Component @IntegrationAdapter(domain = IntegrationDomain.AI, slug = "anthropic")`. Implements full `AiProvider` interface. Constructor injection of `AnthropicApiClient` + `IntegrationGuardService`. `complete()` and `completeWithVision()` resolve API key via `guardService.resolveApiKey(IntegrationDomain.AI)` then delegate to `apiClient`. Existing methods (`generateText`, `summarize`, `suggestCategories`) delegate to `complete()` with simplified prompt wrapping. `testConnection()` uses `apiClient.testConnection()` with resolved key. |
| 526B.4 | Create Anthropic JSON request/response DTOs | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicMessagesRequest.java`, `AnthropicMessagesResponse.java` | covered by 526B.5 | Jackson record serialization pattern | Internal records for Anthropic Messages API wire format. `AnthropicMessagesRequest`: `model`, `max_tokens`, `system` (list of content blocks with `cache_control`), `messages` (list with role + content). `AnthropicMessagesResponse`: `id`, `model`, `content` (list), `stop_reason`, `usage` (nested record with token counts). These are internal to the `anthropic/` package -- not exposed outside. |
| 526B.5 | Integration tests for AnthropicAiProvider + ApiClient | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicAiProviderTest.java`, `AnthropicApiClientTest.java` | ~7 tests: (1) `complete()` builds correct Messages API request body; (2) `completeWithVision()` includes image content blocks; (3) response parsing extracts all token count fields; (4) `cache_control` directive present on system prompt block; (5) 429 response triggers retry (up to 3); (6) `testConnection()` returns success on valid key; (7) `NoOpAiProvider` new methods return expected fallback response | existing test pattern with `@MockitoBean` on `RestClient.Builder` or `MockRestServiceServer` | Mock the Anthropic HTTP endpoint. No real API calls. Verify request body construction (especially `cache_control` on system prompt, image content blocks, token count parsing). Test `NoOpAiProvider` as a unit test alongside. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiCompletionRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiVisionRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiImageInput.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiCompletionResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicAiProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicApiClient.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicProperties.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicMessagesRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicMessagesResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicAiProviderTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/anthropic/AnthropicApiClientTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiProvider.java` -- add 2 new methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/NoOpAiProvider.java` -- implement 2 new methods
- `backend/src/main/resources/application.yml` -- add `kazi.ai.anthropic.*` config section
- `backend/src/main/resources/application-test.yml` -- add test config values

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiTextRequest.java` -- existing record shape
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiTextResult.java` -- existing response shape
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java` -- API key resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java` -- provider resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ConnectionTestResult.java` -- existing result shape

### Architecture Decisions

- **Evolve existing `AiProvider` rather than new port** ([ADR-280](../adr/ADR-280-evolve-ai-provider-port-for-skills.md)) -- the new methods share the same API key, connection test, tenant scoping, and fallback as existing methods. Interface segregation does not apply because all methods are one-shot synchronous text operations.
- **Prompt caching via `cache_control`** ([ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md)) -- `AnthropicApiClient` attaches `{"type": "ephemeral"}` to system prompt content blocks. This is a wire-format concern owned by the API client, not the skill.
- **Jackson for JSON, not Anthropic Java SDK** -- the architecture explicitly notes the Anthropic Java SDK is too immature. Raw `RestClient` + Jackson is the implementation choice.

### Non-scope

- No firm AI profile (lands in 527A).
- No execution/gate entities (lands in 528A).
- No skill infrastructure (lands in 529A).
- No frontend (lands in 527B).
- No V122 migration (lands in 527A).

---

## Epic 527: AiFirmProfile Entity + API + Frontend

**Goal**: Create the `AiFirmProfile` tenant-scoped entity, its CRUD API, and the V122 migration that creates all three Phase 72 tables (`ai_firm_profiles`, `ai_executions`, `ai_execution_gates`) plus capability seed data. On the frontend, build the AI configuration settings page with the profile form (doubling as the cold-start wizard), cost summary display, and navigation items.

**References**: Architecture Section 12.2.1 (AiFirmProfile entity), Section 12.3.4 (Firm AI Profile Management), Section 12.4.1 (AI Configuration Endpoints), Section 12.7 (V122 Migration), Section 12.9 (Permission Model), Section 12.10 Slice 72B; [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md).

**Dependencies**: Epic 526A (AiProvider interface evolution -- Capability enum context, but not code dependency). Phase 41/46 (Capabilities + `@RequiresCapability`).

**Scope**: Both (backend slice 527A, frontend slice 527B)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **527A** | 527A.1-527A.7 | ~10 backend files (1 migration + 1 entity + 1 repo + 1 service + 1 controller + 1 enum modification + 2 DTO records + 2 test files) | V122 migration (all 3 tables + indexes + capability seeds); `AiFirmProfile` entity + repo; `AiFirmProfileService` (CRUD, profile assembly for system prompts); `AiFirmProfileController` (`AI_MANAGE` gated); `Capability` enum additions; integration tests. **Done** (PR #1315) |
| **527B** | 527B.1-527B.6 | ~7 frontend files (2 pages + 2 components + 1 schema + 1 API client + 1 nav modification) | `settings/ai/page.tsx`; `components/ai/ai-profile-form.tsx`; `components/ai/ai-cost-summary.tsx`; `lib/schemas/ai-profile.ts`; `lib/api/ai.ts`; `lib/nav-items.ts` modification. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 527A.1 | Create V122 tenant migration | `backend/src/main/resources/db/migration/tenant/V122__ai_foundation.sql` | verified by 527A.7 (migration runs clean) | existing `V120__add_ai_specialist_invocations_and_llm_calls.sql` for format | SQL verbatim from architecture Section 12.7: three tables (`ai_firm_profiles`, `ai_executions`, `ai_execution_gates`), four execution indexes (`idx_ai_executions_skill_status`, `idx_ai_executions_entity`, `idx_ai_executions_invoked_by`, `idx_ai_executions_created_at`), three gate indexes (`idx_ai_execution_gates_execution`, `idx_ai_execution_gates_status_expires` partial, `idx_ai_execution_gates_reviewed_by` partial), FK from `ai_execution_gates.execution_id` to `ai_executions.id` with `ON DELETE CASCADE`, capability seed UPDATEs (AI_MANAGE to OWNER/ADMIN, AI_EXECUTE to OWNER/ADMIN/MEMBER, AI_REVIEW to OWNER/ADMIN). All `CREATE TABLE IF NOT EXISTS` for idempotency. |
| 527A.2 | Add `Capability` enum values | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` (modify) | covered by 527A.7 | existing enum values; Phase 71 added `INTEGRATION_MANAGE` etc. | Add three values: `AI_MANAGE`, `AI_EXECUTE`, `AI_REVIEW`. Follow existing pattern. |
| 527A.3 | Create `AiFirmProfile` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfile.java` | 527A.7 | architecture Section 12.8.3 entity code pattern; existing entity in `integration/OrgIntegration.java` | `@Entity @Table(name = "ai_firm_profiles")`. All fields from architecture Section 12.2.1. `@Column` annotations, no Lombok, protected no-arg constructor, public constructor for creation. Domain methods: `updateProfile(...)` for field updates + `profileVersion` increment + `updatedBy` set; `markColdStartCompleted()`. `@Convert` for JSONB fields using existing `JpaJsonbConverter` pattern. `@PrePersist`/`@PreUpdate` for timestamps. |
| 527A.4 | Create `AiFirmProfileRepository` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileRepository.java` | 527A.7 | existing repo pattern | `public interface AiFirmProfileRepository extends JpaRepository<AiFirmProfile, UUID> {}`. Single row per tenant -- `findAll()` limited to one result by service logic. No custom queries needed for v1. |
| 527A.5 | Create `AiFirmProfileService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileService.java` | 527A.7 | architecture Section 12.3.4 | `@Service`. Methods: `getOrCreateProfile()` -- returns existing profile or creates default with `coldStartCompleted = false`; `updateProfile(UpdateAiFirmProfileRequest request)` -- validates input, updates fields, increments `profileVersion`, emits `AiFirmProfileUpdatedEvent`; `assembleProfileBlock()` -- builds XML-tagged `<firm-profile version="N">` string from current profile for system prompt inclusion per [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md). Constructor injection of `AiFirmProfileRepository`, `ApplicationEventPublisher`, `RequestScopes`. |
| 527A.6 | Create `AiFirmProfileController` + DTO records | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileController.java` | 527A.7 | existing controller pattern with `@RequiresCapability` | `@RestController @RequestMapping("/api/ai")`. Endpoints: `GET /api/ai/profile` (`AI_MANAGE`) -- returns profile DTO; `PUT /api/ai/profile` (`AI_MANAGE`) -- creates/updates profile. DTO records: `AiFirmProfileResponse` and `UpdateAiFirmProfileRequest` as nested records. Uses `RequestScopes.requireMemberId()` for `createdBy`/`updatedBy`. Per architecture Section 12.4.1, 12.4.4. |
| 527A.7 | Integration tests for firm profile | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileIntegrationTest.java` | ~6 tests: (1) V122 migration runs clean; (2) GET profile creates default when none exists; (3) PUT profile creates new profile with all fields; (4) PUT profile updates existing and increments profileVersion; (5) profile assembly produces correct XML-tagged block; (6) AI_MANAGE capability required (403 without) | existing `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` pattern; use `TestMemberHelper`, `TestJwtFactory` | Standard integration test. Verify RBAC gating with `AI_MANAGE`. Verify profile version increment. Verify `assembleProfileBlock()` output format. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 527B.1 | Create AI API client functions | `frontend/lib/api/ai.ts` | -- | existing `frontend/lib/api/integrations.ts` for API client pattern | Functions: `getAiProfile()`, `updateAiProfile(data)`, `getAiCostSummary()`. Each calls `fetchApi()` from `lib/api/client.ts` with appropriate path and method. Types for `AiProfileResponse`, `UpdateAiProfileRequest`, `AiCostSummaryResponse`. |
| 527B.2 | Create Zod schema for AI profile form | `frontend/lib/schemas/ai-profile.ts` | -- | existing `frontend/lib/schemas/customer.ts` for Zod schema pattern | Schema with fields: `practiceAreas` (array of strings), `jurisdiction` (string with ZA province pattern), `riskCalibration` (enum: CONSERVATIVE/MODERATE/AGGRESSIVE), `houseStyleNotes` (optional string), `ficaRequirements` (optional object), `feeEstimationNotes` (optional string), `preferredModel` (enum: claude-sonnet-4-6/claude-opus-4-6), `monthlyBudgetCents` (optional positive integer), `coldStartCompleted` (boolean). |
| 527B.3 | Create AI profile form component | `frontend/components/ai/ai-profile-form.tsx` | -- | existing form patterns in settings (e.g., `frontend/app/(app)/org/[slug]/settings/general/page.tsx`) | Form with sections matching the cold-start wizard: practice areas (multi-select), jurisdiction (select with ZA provinces), risk calibration (radio group with descriptions), house style (textarea), FICA requirements (checkboxes for enhanced due diligence options), fee estimation (textarea), model preference (radio: Sonnet/Opus with cost comparison), monthly budget (optional currency input). Uses `react-hook-form` + Zod resolver + Shadcn form components. Submits via `updateAiProfile()` server action. Shows cold-start wizard header when `coldStartCompleted = false`. |
| 527B.4 | Create cost summary component | `frontend/components/ai/ai-cost-summary.tsx` | -- | existing budget/spend display patterns in billing pages | Card displaying: current month spend (formatted ZAR), monthly budget (if set), remaining budget, progress bar (spend/budget percentage with 80% and 100% threshold colors), invocation count, period (month name + year). Uses `getAiCostSummary()` from API client. Empty state when no invocations yet. |
| 527B.5 | Create AI settings page | `frontend/app/(app)/org/[slug]/settings/ai/page.tsx` | -- | existing settings page patterns (e.g., `settings/integrations/page.tsx`) | Page layout: heading "AI Configuration", two-column grid on desktop. Left column: AI profile form component. Right column: cost summary card, model info card (current model, estimated cost per invocation), API key status (linked to existing integration settings). Wraps content in capability check for `AI_MANAGE`. Server component that fetches profile and cost summary. |
| 527B.6 | Add AI nav items | `frontend/lib/nav-items.ts` (modify) | -- | existing nav items in file | Add two entries: (1) "AI Configuration" under Settings section linking to `/settings/ai` with a Brain or Sparkles icon, gated by `AI_MANAGE` capability; (2) "AI Reviews" as a top-level nav item linking to `/ai/reviews` with a ShieldCheck icon, gated by `AI_REVIEW` capability. Follow existing nav item structure and capability gating pattern. |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/tenant/V122__ai_foundation.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfile.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileIntegrationTest.java`

**Create (frontend):**
- `frontend/lib/api/ai.ts`
- `frontend/lib/schemas/ai-profile.ts`
- `frontend/components/ai/ai-profile-form.tsx`
- `frontend/components/ai/ai-cost-summary.tsx`
- `frontend/app/(app)/org/[slug]/settings/ai/page.tsx`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` -- add `AI_MANAGE`, `AI_EXECUTE`, `AI_REVIEW`

**Modify (frontend):**
- `frontend/lib/nav-items.ts` -- add AI settings + AI reviews entries

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java` -- entity pattern
- `backend/src/main/resources/db/migration/tenant/V120__add_ai_specialist_invocations_and_llm_calls.sql` -- migration format
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` -- settings page pattern
- `frontend/lib/schemas/customer.ts` -- Zod schema pattern

### Architecture Decisions

- **V122 creates ALL 3 tables** -- the migration is atomic. Even though `AiExecution` and `AiExecutionGate` entities are not coded until Epic 528, the tables exist in the schema from Epic 527 forward. This avoids a second migration.
- **Capability seed via JSONB append** -- follows the Phase 41/46 pattern (`UPDATE org_roles SET capabilities = capabilities || '["AI_MANAGE"]'::jsonb WHERE ... AND NOT capabilities @> '["AI_MANAGE"]'::jsonb`).
- **Profile assembly as XML** ([ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md)) -- `assembleProfileBlock()` produces an XML-tagged string for prompt cache coherence.
- **No `PlanTier`** -- capabilities are the sole authorization mechanism.

### Non-scope

- No `AiExecution` or `AiExecutionGate` entity code (lands in 528A -- tables created here, entity classes there).
- No skill execution logic (lands in 529A).
- No execution history page (lands in 529B).
- No AI reviews page (lands in 529B).

---

## Epic 528: AiExecution + AiExecutionGate + Cost Metering

**Goal**: Build the execution tracking and attorney approval infrastructure: `AiExecution` entity for skill invocation audit trail, `AiExecutionGate` entity for attorney review gates, `AiExecutionGateService` for approve/reject/expire workflows, `GateAction` sealed interface for typed gate actions, `GateActionExecutor` for dispatching approved actions, `AiCostService` for per-invocation cost calculation and budget enforcement, and the gate controller for the approval API.

**References**: Architecture Section 12.2.2 (AiExecution), Section 12.2.3 (AiExecutionGate), Section 12.3.5 (Skill Execution Flow), Section 12.3.6 (Gate Workflow), Section 12.3.7 (Cost Metering), Section 12.4.2 (Gate Endpoints), Section 12.10 Slice 72C; [ADR-281](../adr/ADR-281-execution-gate-pattern-attorney-liability.md), [ADR-282](../adr/ADR-282-per-invocation-cost-metering-byoak.md).

**Dependencies**: Epic 527A (V122 migration creates tables; Capability enum values added).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **528A** | 528A.1-528A.6 | ~8 backend files (2 entities + 2 repos + 1 sealed interface + 1 executor + 2 test files) | `AiExecution` entity + repo; `AiExecutionGate` entity + repo; `GateAction` sealed interface with 3 permits; `GateActionExecutor`. **Done** (PR #1316) |
| **528B** | 528B.1-528B.6 | ~8 backend files (1 service + 1 controller + 1 cost service + 1 config + 2 domain events + 2 test files) | `AiExecutionGateService` (approve/reject/expire); `AiExecutionGateController` (AI_REVIEW gated); `AiCostService` (cost calc + budget check + summary); AI pricing config; cost summary endpoint addition to profile controller; domain events; integration tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 528A.1 | Create `AiExecution` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecution.java` | 528A.6 | architecture Section 12.8.3 entity code pattern | `@Entity @Table(name = "ai_executions")`. All fields from architecture Section 12.2.2. Domain methods: `markCompleted(AiCompletionResponse response, long costCents)` sets status/tokens/cost/duration; `markFailed(String errorMessage, long durationMs)` sets status/error/duration. Write-once semantics -- no `@Version`. Protected no-arg + public constructor per convention. |
| 528A.2 | Create `AiExecutionRepository` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecutionRepository.java` | 528A.6 | architecture Section 12.8.4 repo code | `JpaRepository<AiExecution, UUID>`. Custom queries: `sumCostCentsForCurrentMonth(@Param("monthStart") Instant)` returning `long`; `countForCurrentMonth(@Param("monthStart") Instant)` returning `int`; `findBySkillIdAndStatusOrderByCreatedAtDesc(String, String, Pageable)` returning `Page<AiExecution>`; `findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String, UUID)` returning `List<AiExecution>`. |
| 528A.3 | Create `AiExecutionGate` entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGate.java` | 528A.6 | architecture Section 12.2.3 | `@Entity @Table(name = "ai_execution_gates")`. All fields per architecture. `@ManyToOne(fetch = LAZY) @JoinColumn(name = "execution_id")` to `AiExecution`. Domain methods: `requirePendingStatus()` throws `InvalidStateException` if not PENDING; `approve(UUID reviewerId, String notes)` sets status/reviewed fields; `reject(UUID reviewerId, String notes)` same; `expire()` sets status to EXPIRED. `proposed_action` stored as `@Column(columnDefinition = "jsonb")` using `JpaJsonbConverter`. |
| 528A.4 | Create `AiExecutionGateRepository` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateRepository.java` | 528A.6 | existing repo pattern | `JpaRepository<AiExecutionGate, UUID>`. Custom queries: `findByExecutionId(UUID)` returning `List<AiExecutionGate>`; `findByStatusOrderByCreatedAtDesc(String, Pageable)` returning `Page<AiExecutionGate>`; `findPendingExpiredBefore(Instant now)` using `@Query("SELECT g FROM AiExecutionGate g WHERE g.status = 'PENDING' AND g.expiresAt < :now")`; `findByStatusAndGateTypeOrderByCreatedAtDesc(String, String, Pageable)` returning `Page<AiExecutionGate>`. |
| 528A.5 | Create `GateAction` sealed interface + `GateActionExecutor` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateAction.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java` | 528A.6 | architecture Section 12.3.6 | `GateAction` sealed interface permits `MarkKycCompleteAction(List<UUID> checklistItemIds, String completionNotes)`, `SelectMatterTemplateAction(UUID templateId, String customisationNotes)`, `ClearConflictAction(UUID conflictCheckId, String clearanceNotes)`. `GateActionExecutor` `@Service`: `execute(AiExecutionGate gate)` -- parses `proposed_action` JSONB into the appropriate `GateAction` record via `gate_type` discriminator, then dispatches: `MarkKycCompleteAction` -> `checklistService.completeItems(...)`; `SelectMatterTemplateAction` -> no-op (frontend pre-fill, no entity mutation); `ClearConflictAction` -> `conflictCheckService.recordClearance(...)`. |
| 528A.6 | Integration tests for execution + gate entities | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecutionRepositoryTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateTest.java` | ~6 tests: (1) AiExecution persist + round-trip; (2) sumCostCentsForCurrentMonth aggregation; (3) AiExecutionGate persist with JSONB proposed_action; (4) gate approve() state transition; (5) gate reject() state transition; (6) requirePendingStatus() throws on non-PENDING gate | standard integration test pattern | Verify entity persistence, JSONB round-trip, state transition methods, and repository query correctness. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 528B.1 | Create `AiCostService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiCostService.java` | 528B.6 | architecture Section 12.3.7 | `@Service`. Methods: `checkBudget(AiFirmProfile profile)` -- aggregates current month spend via `executionRepository.sumCostCentsForCurrentMonth()`, throws `InvalidStateException` if budget exceeded; `calculateCostCents(AiCompletionResponse response)` -- uses model pricing config + exchange rate to compute ZAR cents; `getCostSummary()` -- returns `AiCostSummary` record with spent/budget/remaining/count/period. Uses `ModelPricingConfig` and `ExchangeRateConfig` injected from application properties. |
| 528B.2 | Create AI pricing + exchange rate configuration | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiPricingProperties.java` | covered by 528B.6 | architecture Section 12.3.7 application.yml config | `@ConfigurationProperties(prefix = "kazi.ai")`. Nested `pricing` map: model -> `ModelPricing(inputPerMToken, outputPerMToken, cacheReadPerMToken, cacheCreationPerMToken)`. `exchangeRate.usdToZar`. `timeoutSeconds`. `maxDocumentSizeBytes`. `maxTotalDocumentSizeBytes`. Add all values to `application.yml` and `application-test.yml` per architecture Section 12.3.7. |
| 528B.3 | Create `AiExecutionGateService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java` | 528B.6 | architecture Section 12.3.6 | `@Service`. Methods: `approve(UUID gateId, UUID reviewerId, String notes)` -- loads gate, calls `requirePendingStatus()`, calls `approve()`, saves, delegates to `GateActionExecutor.execute(gate)`, emits `AiGateApprovedEvent`, emits audit event; `reject(UUID gateId, UUID reviewerId, String notes)` -- same pattern without action execution, emits `AiGateRejectedEvent`; `expireStaleGates()` -- `@Scheduled(fixedRate = 3600000)` hourly job using `TenantScopedRunner` to iterate all tenants, finds PENDING gates past `expires_at`, marks EXPIRED, emits audit events. Uses `UPDATE WHERE status = 'PENDING'` row-count check to prevent double-approval races. |
| 528B.4 | Create `AiExecutionGateController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateController.java` | 528B.6 | existing controller pattern with `@RequiresCapability` | `@RestController @RequestMapping("/api/ai/gates")`. Endpoints per architecture Section 12.4.2: `GET /api/ai/gates` (`AI_REVIEW`) -- paginated list filterable by gate_type, status; `GET /api/ai/gates/{id}` (`AI_REVIEW`) -- gate detail with execution context; `POST /api/ai/gates/{id}/approve` (`AI_REVIEW`) -- approve with optional notes; `POST /api/ai/gates/{id}/reject` (`AI_REVIEW`) -- reject with optional notes. Request/response DTOs as nested records. |
| 528B.5 | Create domain events + add cost summary endpoint | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateApprovedEvent.java`, `AiGateRejectedEvent.java`, `AiGateExpiredEvent.java` | covered by 528B.6 | existing event pattern in `event/InvoiceApprovedEvent.java` | Three domain event records published by `AiExecutionGateService`. Also: add `GET /api/ai/cost-summary` endpoint to `AiFirmProfileController` (from 527A) -- `@RequiresCapability("AI_MANAGE")`, delegates to `AiCostService.getCostSummary()`. This is a minor modification to 527A's controller. |
| 528B.6 | Integration tests for gate service + cost service | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateServiceTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiCostServiceTest.java` | ~8 tests: (1) approve() transitions gate to APPROVED and executes action; (2) approve() on non-PENDING gate throws; (3) reject() transitions to REJECTED with notes; (4) expireStaleGates() expires past-due gates; (5) unexpired gates are not affected by expiry sweep; (6) calculateCostCents() computes correct ZAR from USD pricing; (7) checkBudget() passes when under budget; (8) checkBudget() throws when budget exhausted | standard integration test pattern | Use `@MockitoBean` on `ChecklistService` and `ConflictCheckService` for gate action executor tests. Verify cost calculation with known token counts and pricing config. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecution.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecutionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateAction.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiCostService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiPricingProperties.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateApprovedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateRejectedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiGateExpiredEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecutionRepositoryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiCostServiceTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/profile/AiFirmProfileController.java` -- add `GET /api/ai/cost-summary` endpoint
- `backend/src/main/resources/application.yml` -- add `kazi.ai.pricing.*` and `kazi.ai.exchange-rate.*` config sections
- `backend/src/main/resources/application-test.yml` -- add test config values

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistService.java` -- for gate action execution (complete items)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` -- for expiry sweep (verify class name)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoiceApprovedEvent.java` -- event record pattern

### Architecture Decisions

- **Mandatory execution gates** ([ADR-281](../adr/ADR-281-execution-gate-pattern-attorney-liability.md)) -- every AI action that modifies an entity requires attorney approval. No auto-execute. 72h expiry on unreviewed gates.
- **Per-invocation cost metering** ([ADR-282](../adr/ADR-282-per-invocation-cost-metering-byoak.md)) -- `cost_cents` pre-calculated at execution time in ZAR. Exchange rate is a static config value. Budget enforcement is pre-flight.
- **Sealed interface for gate actions** -- `GateAction` uses Java sealed interface with pattern matching. Each gate type has a typed action record. The executor dispatches via pattern matching switch.
- **`@Scheduled` expiry** -- hourly sweep via `TenantScopedRunner.forEachTenant()` per ADR-T008.

### Non-scope

- No skill execution orchestration (lands in 529A).
- No specific skills (lands in 530A/531A).
- No frontend (lands in 527B/529B).
- No notification delivery (lands in 529A alongside skill execution).

---

## Epic 529: Skill Execution Infrastructure + StubAiProvider

**Goal**: Build the shared skill execution infrastructure that all AI skills plug into, the skill controller that exposes skill endpoints, the `StubAiProvider` test double for CI, and the frontend pages for AI reviews (gate list) and execution history.

**References**: Architecture Section 12.3.5 (Skill Execution Flow), Section 12.4.3 (Skill Endpoints), Section 12.8.5 (Testing Strategy), Section 12.10 Slice 72D, Section 12.12 (Notification + Audit); [ADR-285](../adr/ADR-285-stub-ai-provider-for-ci-testing.md).

**Dependencies**: Epic 526A (`AiProvider` interface), Epic 528A (execution + gate entities and repos), Epic 528B (gate service, cost service).

**Scope**: Both (backend slice 529A, frontend slice 529B)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **529A** | 529A.1-529A.7 | ~9 backend files (1 interface + 1 service + 1 controller + 2 records + 1 stub provider + 1 test config + 2 test files) | `AiSkill` interface; `AiSkillExecutionService`; `AiSkillController`; `SkillExecutionRequest` + `SkillContext` records; `StubAiProvider` (`@TestConfiguration @Primary`); integration tests. |
| **529B** | 529B.1-529B.5 | ~7 frontend files (2 pages + 2 components + 1 API client extension + 1 actions file + 1 layout) | AI reviews page; execution history page; execution gate card component; execution detail components; API client extension; server actions. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 529A.1 | Create `AiSkill` interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkill.java` | covered by 529A.7 | architecture Section 12.3.5 | `public interface AiSkill { String skillId(); String assembleSystemPrompt(AiFirmProfile profile); String assembleUserPrompt(SkillContext context); List<AiExecutionGate> createGates(AiExecution execution, String outputContent); boolean requiresVision(); }`. Each skill implements this interface. The `AiSkillExecutionService` calls these methods in sequence. |
| 529A.2 | Create `SkillExecutionRequest` + `SkillContext` records | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/SkillExecutionRequest.java`, `SkillContext.java` | covered by 529A.7 | existing record style | `SkillExecutionRequest(AiSkill skill, SkillContext context, UUID invokedBy, List<AiImageInput> images)`. `SkillContext(UUID entityId, String entityType, String description, Map<String, Object> additionalContext)`. |
| 529A.3 | Create `AiSkillExecutionService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillExecutionService.java` | 529A.7 | architecture Section 12.3.5 provides full pseudocode | `@Service`. Core method: `executeSkill(SkillExecutionRequest request)` -- (1) pre-flight: load firm profile, check budget, resolve AiProvider; (2) assemble prompts via skill interface; (3) invoke AI via provider (complete or completeWithVision based on `requiresVision()`); (4) on exception: record failed execution; (5) on success: calculate cost, record execution; (6) parse output and create gates via skill interface; (7) save gates; (8) send notifications for pending gates (reuse Phase 6.5 notification infrastructure); (9) check budget alerts (80%/100%); (10) emit `AiSkillInvokedEvent` audit event. Constructor injection of all dependencies. |
| 529A.4 | Create `AiSkillController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillController.java` | 529A.7 | existing controller pattern | `@RestController @RequestMapping("/api/ai/skills")`. Skeleton endpoints: `POST /api/ai/skills/fica-verification` (`AI_EXECUTE`) and `POST /api/ai/skills/matter-intake` (`AI_EXECUTE`). Each endpoint: validates request body, resolves the skill bean by ID from injected map of `AiSkill` implementations, builds `SkillExecutionRequest` + `SkillContext`, calls `executionService.executeSkill()`, returns execution result DTO. DTO includes: `executionId`, `status`, `output` (parsed JSON), `gates` (list of gate DTOs), `costCents`, `model`, `durationMs`. Request body DTOs: `FicaVerificationRequest(UUID customerId)` and `MatterIntakeRequest(UUID customerId, String description)`. |
| 529A.5 | Create `AiSkillInvokedEvent` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillInvokedEvent.java` | covered by 529A.7 | existing event pattern | `public record AiSkillInvokedEvent(UUID executionId, String skillId, String entityType, UUID entityId, String model, int inputTokens, int outputTokens, long costCents, String status) {}`. Published by `AiSkillExecutionService` after execution completes. Consumed by audit service. |
| 529A.6 | Create `StubAiProvider` + test configuration | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/StubAiProvider.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/TestAiConfiguration.java` | used by all subsequent AI tests | [ADR-285](../adr/ADR-285-stub-ai-provider-for-ci-testing.md) | `StubAiProvider implements AiProvider`: returns canned JSON from test resources. `complete()` reads `test/resources/ai/stubs/{skill-id}/response.json` based on `metadata.get("skill-id")` and returns it wrapped in `AiCompletionResponse` with realistic token counts (2000 input, 800 output, 1500 cache read, 0 cache creation). `@TestConfiguration` class `TestAiConfiguration` declares `StubAiProvider` as `@Bean @Primary`. Import from `TestcontainersConfiguration` so all integration tests get it automatically. Create stub response directories: `test/resources/ai/stubs/` (empty for now -- skill slices add response.json files). |
| 529A.7 | Integration tests for skill execution service + controller | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillExecutionServiceTest.java`, `AiSkillControllerTest.java` | ~7 tests: (1) executeSkill creates AiExecution with COMPLETED status; (2) executeSkill creates gates from skill output; (3) executeSkill records correct cost; (4) budget check prevents execution when exhausted; (5) failed API call records FAILED execution with error; (6) controller returns 403 without AI_EXECUTE; (7) controller returns valid execution response DTO | standard `@SpringBootTest` pattern; needs a test `AiSkill` implementation (create inline or as test fixture) | Create a minimal `TestSkill implements AiSkill` in the test class that returns a simple JSON output and creates one gate. This tests the infrastructure without depending on real skills (which land in 530A/531A). |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 529B.1 | Extend AI API client for gates + executions | `frontend/lib/api/ai.ts` (extend) | -- | existing API client pattern in `lib/api/ai.ts` (created in 527B.1) | Add functions: `getAiGates(params)` (paginated, filterable by status/type), `getAiGate(id)`, `approveAiGate(id, notes?)`, `rejectAiGate(id, notes?)`, `getAiExecutions(params)` (paginated, filterable by skill/status/date range), `getAiExecution(id)`. Add corresponding TypeScript types for `AiGateResponse`, `AiExecutionResponse`, `AiExecutionListResponse`. |
| 529B.2 | Create execution gate card component | `frontend/components/ai/execution-gate-card.tsx` | -- | existing card patterns in the codebase | Card component displaying: gate type badge, AI reasoning text, proposed action summary (rendered per gate type), status badge, approve/reject buttons (when PENDING), review notes (when reviewed), expiry countdown (when PENDING). Approve/reject buttons trigger server actions that call the gate API. Uses Shadcn Card, Badge, Button, Textarea (for notes). Alert dialog for approve/reject confirmation. |
| 529B.3 | Create AI reviews page | `frontend/app/(app)/org/[slug]/ai/reviews/page.tsx`, `frontend/app/(app)/org/[slug]/ai/reviews/actions.ts`, `frontend/app/(app)/org/[slug]/ai/layout.tsx` | -- | existing list page patterns (e.g., notifications page) | Page with two tabs: "Pending Review" (gates with status PENDING) and "History" (APPROVED/REJECTED/EXPIRED). Each tab renders a list of `execution-gate-card` components. Server component fetches gates. Wraps in `AI_REVIEW` capability check. Empty state with explanation. Layout adds breadcrumbs. |
| 529B.4 | Create execution history page | `frontend/app/(app)/org/[slug]/settings/ai/history/page.tsx` | -- | existing table pages (e.g., audit log page) | Table with columns: Date, Skill, Target Entity (linked), Status badge, Cost (formatted ZAR), Tokens (input + output), Duration. Filterable by skill, date range, status. Click row to expand and show: input summary, output content (formatted JSON), list of gates with status. Server component fetches executions. Wraps in `AI_MANAGE` capability check. |
| 529B.5 | Create server actions for gate operations | `frontend/app/(app)/org/[slug]/ai/reviews/actions.ts` | -- | existing action patterns | `approveGateAction(gateId, notes?)` and `rejectGateAction(gateId, notes?)` server actions that call `approveAiGate()` / `rejectAiGate()` from API client and revalidate the page. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkill.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillExecutionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/SkillExecutionRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/SkillContext.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillInvokedEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/StubAiProvider.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/TestAiConfiguration.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillExecutionServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/AiSkillControllerTest.java`

**Create (frontend):**
- `frontend/app/(app)/org/[slug]/ai/reviews/page.tsx`
- `frontend/app/(app)/org/[slug]/ai/reviews/actions.ts`
- `frontend/app/(app)/org/[slug]/ai/layout.tsx`
- `frontend/app/(app)/org/[slug]/settings/ai/history/page.tsx`
- `frontend/components/ai/execution-gate-card.tsx`

**Modify (frontend):**
- `frontend/lib/api/ai.ts` -- add gate + execution API functions

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java` -- provider resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` -- notification creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- audit event emission
- `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` -- table page pattern

### Architecture Decisions

- **StubAiProvider as @Primary @TestConfiguration** ([ADR-285](../adr/ADR-285-stub-ai-provider-for-ci-testing.md)) -- deterministic canned responses. No API cost in CI. All downstream logic (gates, cost, audit) fully tested.
- **Skill registration via Spring DI** -- skills are `@Component` beans implementing `AiSkill`. The controller resolves them from an injected `Map<String, AiSkill>` (Spring auto-wires by bean name) or a `List<AiSkill>` iterated by `skillId()`.
- **Notification for pending gates** -- reuses Phase 6.5 `Notification` entity. Gates surface in the notification bell and on the dedicated reviews page.

### Non-scope

- No FICA skill implementation (lands in 530A).
- No matter intake skill implementation (lands in 531A).
- No customer detail page modification (lands in 530B).
- No project creation page modification (lands in 531B).

---

## Epic 530: FICA Verification Skill (Backend + Frontend)

**Goal**: Implement the FICA/KYC verification skill that reads uploaded documents from S3, extracts text (PDFBox for digital PDFs, Claude vision for scanned documents and images), reviews documents against the customer's compliance checklist, and produces structured recommendations with execution gates for checklist item completion. On the frontend, add the "Verify with AI" button and FICA results panel to the customer detail page.

**References**: Architecture Section 12.3.8 (FICA Verification Skill), Section 12.5.1 (FICA Sequence Diagram), Section 12.10 Slice 72E; [ADR-284](../adr/ADR-284-document-reading-s3-vision-no-vector-store.md), [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md).

**Dependencies**: Epic 529A (AiSkill interface, AiSkillExecutionService, AiSkillController, StubAiProvider).

**Scope**: Both (backend slice 530A, frontend slice 530B)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **530A** | 530A.1-530A.7 | ~9 backend files (1 skill class + 1 output record + 1 document reader + 2 resource files + 1 test resource + 2 test files + 1 application config addition) | `FicaVerificationSkill` implementing `AiSkill`; `FicaVerificationOutput` record; `FicaDocumentReader`; system prompt and output schema resources; canned test response; integration test. |
| **530B** | 530B.1-530B.5 | ~6 frontend files (2 components + 1 page modification + 1 API client extension + 1 actions file + 1 type file) | FICA verification panel; FICA result display; customer detail page modification; skill invocation server action; API client extension. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 530A.1 | Create `FicaVerificationOutput` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationOutput.java` | covered by 530A.7 | architecture Section 12.3.8 output format | `public record FicaVerificationOutput(String overallAssessment, String riskLevel, List<ChecklistReview> checklistReview, List<String> missingDocuments, List<String> riskFlags, List<RecommendedAction> recommendedActions) {}`. Nested records: `ChecklistReview(UUID checklistItemId, String itemName, String status, String evidenceDocument, String reasoning, List<String> flags)`, `RecommendedAction(String action, List<UUID> items, String reasoning, String documentType)`. Parsed from AI JSON output via Jackson `ObjectMapper`. |
| 530A.2 | Create `FicaDocumentReader` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaDocumentReader.java` | 530A.7 | [ADR-284](../adr/ADR-284-document-reading-s3-vision-no-vector-store.md) | `@Service`. Methods: `readDocuments(List<Document> documents)` -- returns `DocumentReadResult(String textContent, List<AiImageInput> images)`. For each document: (1) fetch from S3 via `StorageAdapter.download()`; (2) if PDF: extract text via `PDFBox` (`PDDocument.load()` -> `PDFTextStripper`), if text < 100 chars fall back to vision (render pages as images); (3) if image (JPG/PNG): encode as base64, add to images list; (4) skip documents > 10 MB with note; (5) stop if total payload > 50 MB with note. Size limits from `AiPricingProperties`. |
| 530A.3 | Create FICA system prompt resource | `backend/src/main/resources/ai/skills/fica-verification/system.txt` | covered by 530A.7 | architecture Section 12.3.8 provides full prompt template | System prompt text per architecture: role assignment, legal context (FICA Act 38 of 2001), `{firm_profile_block}` placeholder for interpolation, output format specification with `{output_schema}` placeholder, risk calibration instructions. Prompts are code -- stored as classpath resources per [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md). |
| 530A.4 | Create FICA output schema resource | `backend/src/main/resources/ai/skills/fica-verification/output-schema.json` | covered by 530A.7 | -- | JSON Schema defining the expected output structure. Used in the system prompt as `{output_schema}` to instruct the AI on response format. Matches `FicaVerificationOutput` record structure. |
| 530A.5 | Create `FicaVerificationSkill` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkill.java` | 530A.7 | architecture Section 12.3.8 provides implementation spec | `@Component`. Implements `AiSkill`. `skillId() = "fica-verification"`. `requiresVision()` returns true when any images exist. `assembleSystemPrompt(profile)` -- reads `system.txt` from classpath, interpolates `{firm_profile_block}` from `profileService.assembleProfileBlock()`, interpolates `{output_schema}` from `output-schema.json`, interpolates `{risk_calibration}` from profile. `assembleUserPrompt(context)` -- loads customer, checklist, items, documents; builds XML-tagged prompt per architecture Section 12.3.8. Pre-flight checks: customer exists, has documents, has active checklist with PENDING items. `createGates(execution, outputContent)` -- parses JSON to `FicaVerificationOutput`, creates `AiExecutionGate` for each `MARK_ITEMS_COMPLETE` action with `gate_type = "MARK_KYC_COMPLETE"`, `proposed_action = {"checklist_item_ids": [...], "completion_notes": "..."}`. REQUEST_ADDITIONAL_DOCUMENT actions are informational only (no gate). |
| 530A.6 | Create canned test response | `backend/src/test/resources/ai/stubs/fica-verification/response.json` | used by 530A.7 via StubAiProvider | [ADR-285](../adr/ADR-285-stub-ai-provider-for-ci-testing.md) | Valid JSON matching `FicaVerificationOutput` schema: `overallAssessment = "INCOMPLETE"`, `riskLevel = "MEDIUM"`, 3 checklist review items (1 SATISFIED, 1 NOT_SATISFIED, 1 NEEDS_REVIEW), 1 missing document, 1 risk flag, 2 recommended actions (1 MARK_ITEMS_COMPLETE, 1 REQUEST_ADDITIONAL_DOCUMENT). Realistic for a 3-document FICA submission. |
| 530A.7 | Integration test for FICA verification skill | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkillTest.java` | ~6 tests: (1) skill produces COMPLETED execution with correct skill_id; (2) output is parsed as valid FicaVerificationOutput; (3) MARK_ITEMS_COMPLETE action creates execution gate with MARK_KYC_COMPLETE type; (4) REQUEST_ADDITIONAL_DOCUMENT does not create a gate; (5) skill fails gracefully when customer has no documents; (6) pre-flight check rejects customer without active checklist | standard integration test; StubAiProvider returns canned response; use `TestEntityHelper.createCustomer()`, `TestChecklistHelper` | Provision customer, checklist, items, and documents in test setup. `InMemoryStorageService` for S3 (no real S3). Verify full flow: controller call -> execution created -> gates created -> output parseable. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 530B.1 | Extend AI API client for FICA skill | `frontend/lib/api/ai.ts` (extend) | -- | existing API client functions in same file | Add function: `invokeFicaVerification(customerId)`. Return type: `FicaVerificationResponse` with `executionId`, `status`, `output` (typed), `gates`, `costCents`, `model`, `durationMs`. Add TypeScript types: `FicaVerificationOutput`, `ChecklistReviewItem`, `RecommendedAction`. |
| 530B.2 | Create FICA result display component | `frontend/components/ai/fica-result-display.tsx` | -- | existing structured display patterns | Component rendering `FicaVerificationOutput`: overall assessment badge (color-coded: COMPLETE=green, INCOMPLETE=amber, NEEDS_REVIEW=red), risk level badge, checklist review table (item name, status, evidence document, reasoning, flags), missing documents list, risk flags as warning cards, recommended actions list. Handles empty/null states gracefully. |
| 530B.3 | Create FICA verification panel component | `frontend/components/ai/fica-verification-panel.tsx` | -- | existing panel patterns on customer detail page (e.g., compliance panel) | Panel with: "Verify with AI" button (disabled with tooltip when prerequisites not met: no AI configured, no documents, no pending checklist items), loading spinner during invocation, FICA result display on success, error message on failure, link to execution detail, gate cards for pending gates. State machine: IDLE -> LOADING -> SUCCESS/ERROR. Uses `invokeFicaVerification()` action. |
| 530B.4 | Modify customer detail page | `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (modify) | -- | existing compliance section on the page | Add `FicaVerificationPanel` component to the compliance/checklist section of the customer detail page. Render below the existing compliance checklist panel. Pass customer ID and prerequisite state (hasDocuments, hasPendingChecklistItems, isAiConfigured) as props. Gate by `AI_EXECUTE` capability -- only show panel to users with the capability. |
| 530B.5 | Create FICA invocation server action | `frontend/app/(app)/org/[slug]/customers/[id]/fica-actions.ts` | -- | existing action patterns in `kyc-actions.ts` | `invokeFicaVerificationAction(customerId)` server action that calls `invokeFicaVerification()` from API client and returns the result. Handles errors and returns user-friendly error messages. Revalidates the customer page after successful invocation. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkill.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationOutput.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaDocumentReader.java`
- `backend/src/main/resources/ai/skills/fica-verification/system.txt`
- `backend/src/main/resources/ai/skills/fica-verification/output-schema.json`
- `backend/src/test/resources/ai/stubs/fica-verification/response.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/fica/FicaVerificationSkillTest.java`

**Create (frontend):**
- `frontend/components/ai/fica-verification-panel.tsx`
- `frontend/components/ai/fica-result-display.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/fica-actions.ts`

**Modify (frontend):**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- add FICA verification panel
- `frontend/lib/api/ai.ts` -- add FICA skill API function + types

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistService.java` -- checklist item loading
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` -- document loading
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/S3PresignedUrlService.java` -- document download (or StorageAdapter equivalent)
- `frontend/app/(app)/org/[slug]/customers/[id]/kyc-actions.ts` -- existing KYC action pattern

### Architecture Decisions

- **S3 + PDFBox + Vision** ([ADR-284](../adr/ADR-284-document-reading-s3-vision-no-vector-store.md)) -- no vector store, no external OCR. PDF text extraction via PDFBox with fallback to Claude vision for scanned documents. BYOAK: vision calls use the tenant's Anthropic key.
- **All documents included** -- the skill reads ALL uploaded documents for the customer, not a semantic subset. FICA verification requires reviewing every document against the checklist.
- **Prompts as code** ([ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md)) -- `system.txt` is a classpath resource. Prompt engineering iteration happens via code changes, not runtime configuration.
- **Informational actions vs gated actions** -- `MARK_ITEMS_COMPLETE` creates a gate; `REQUEST_ADDITIONAL_DOCUMENT` does not. Only entity-modifying actions require attorney approval.

### Non-scope

- No bulk verification ("verify all pending KYC") -- single customer at a time.
- No automatic re-verification schedules.
- No document upload from within the skill (uses existing uploads).

---

## Epic 531: Matter Intake Skill (Backend + Frontend)

**Goal**: Implement the matter intake intelligence skill that classifies new matter descriptions, recommends project templates, screens for conflicts, estimates fees based on LSSA tariff, and produces structured recommendations with execution gates for template selection and conflict clearance. On the frontend, add the "Get AI Recommendations" button and intake results panel to the project creation page.

**References**: Architecture Section 12.3.9 (Matter Intake Skill), Section 12.5.2 (Intake Sequence Diagram), Section 12.10 Slice 72F; [ADR-283](../adr/ADR-283-prompt-architecture-firm-profile-cache.md).

**Dependencies**: Epic 529A (AiSkill interface, AiSkillExecutionService, AiSkillController, StubAiProvider).

**Scope**: Both (backend slice 531A, frontend slice 531B)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **531A** | 531A.1-531A.6 | ~8 backend files (1 skill class + 1 output record + 2 resource files + 1 test resource + 2 test files + 1 application config validation) | `MatterIntakeSkill` implementing `AiSkill`; `MatterIntakeOutput` record; system prompt and output schema resources; canned test response; integration test. |
| **531B** | 531B.1-531B.5 | ~6 frontend files (2 components + 1 page modification + 1 API client extension + 1 actions file + 1 type file) | Matter intake panel; intake result display; project creation page modification; skill invocation server action; API client extension. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 531A.1 | Create `MatterIntakeOutput` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/intake/MatterIntakeOutput.java` | covered by 531A.6 | architecture Section 12.3.9 output format | `public record MatterIntakeOutput(MatterClassification matterClassification, TemplateRecommendation templateRecommendation, List<RequiredDocument> requiredDocuments, FeeEstimate feeEstimate, ConflictScreening conflictScreening, List<String> riskFlags) {}`. Nested records: `MatterClassification(String recommendedType, double confidence, String reasoning)`, `TemplateRecommendation(UUID templateId, String templateName, String reasoning, String customisationNotes)`, `RequiredDocument(String documentType, String reasoning, String priority)`, `FeeEstimate(String tariffBasis, long estimatedRangeMinCents, long estimatedRangeMaxCents, String reasoning, List<String> assumptions)`, `ConflictScreening(String status, List<ConflictMatch> matches)`, `ConflictMatch(String existingMatterName, String customerName, String matchType, String reasoning)`. |
| 531A.2 | Create matter intake system prompt resource | `backend/src/main/resources/ai/skills/matter-intake/system.txt` | covered by 531A.6 | architecture Section 12.3.9 | System prompt: role assignment ("matter intake assistant for a South African law firm"), SA legal practice context (matter types, LSSA tariff structure, conflict of interest rules), `{firm_profile_block}` placeholder, available templates placeholder, output format with `{output_schema}`, risk calibration instructions. |
| 531A.3 | Create matter intake output schema resource | `backend/src/main/resources/ai/skills/matter-intake/output-schema.json` | covered by 531A.6 | -- | JSON Schema defining expected output structure, matching `MatterIntakeOutput` record. |
| 531A.4 | Create `MatterIntakeSkill` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/intake/MatterIntakeSkill.java` | 531A.6 | architecture Section 12.3.9 | `@Component`. Implements `AiSkill`. `skillId() = "matter-intake"`. `requiresVision()` returns false (text-only skill). `assembleSystemPrompt(profile)` -- reads `system.txt`, interpolates firm profile block and output schema. `assembleUserPrompt(context)` -- loads customer, templates, active matters (limited to 500 most recent, name + customer name only), tariff schedule for firm's jurisdiction; builds XML-tagged prompt with `<matter-description>`, `<customer>`, `<available-templates>`, `<active-matters>`, `<tariff-schedule>` sections per architecture. Pre-flight checks: customer selected, description >= 20 characters. `createGates(execution, outputContent)` -- parses JSON to `MatterIntakeOutput`, creates: (1) `SELECT_MATTER_TEMPLATE` gate if template recommended; (2) `CONFIRM_CONFLICT_SCREEN` gate if conflict status is CLEAR or POTENTIAL_CONFLICT (not if CONFLICT_DETECTED). Fee estimate and documents are informational only. |
| 531A.5 | Create canned test response | `backend/src/test/resources/ai/stubs/matter-intake/response.json` | used by 531A.6 via StubAiProvider | [ADR-285](../adr/ADR-285-stub-ai-provider-for-ci-testing.md) | Valid JSON matching `MatterIntakeOutput` schema: matterClassification with LITIGATION type + 0.92 confidence, template recommendation for a Litigation template, 3 required documents, fee estimate with range, conflict screening with one POTENTIAL_CONFLICT match, 1 risk flag. Realistic for a RAF litigation matter. |
| 531A.6 | Integration test for matter intake skill | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/intake/MatterIntakeSkillTest.java` | ~6 tests: (1) skill produces COMPLETED execution with correct skill_id; (2) output is parsed as valid MatterIntakeOutput; (3) template recommendation creates SELECT_MATTER_TEMPLATE gate; (4) POTENTIAL_CONFLICT creates CONFIRM_CONFLICT_SCREEN gate; (5) CONFLICT_DETECTED does NOT create a gate; (6) pre-flight rejects description under 20 characters | standard integration test; StubAiProvider returns canned response | Provision customer, project templates, active matters in test setup. Verify full flow: controller call -> execution created -> correct gates created -> output parseable. |

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 531B.1 | Extend AI API client for intake skill | `frontend/lib/api/ai.ts` (extend) | -- | existing API client functions | Add function: `invokeMatterIntake(customerId, description)`. Return type: `MatterIntakeResponse` with `executionId`, `status`, `output` (typed), `gates`, `costCents`, `model`, `durationMs`. Add TypeScript types: `MatterIntakeOutput`, `MatterClassification`, `TemplateRecommendation`, `RequiredDocument`, `FeeEstimate`, `ConflictScreening`, `ConflictMatch`. |
| 531B.2 | Create intake result display component | `frontend/components/ai/intake-result-display.tsx` | -- | existing structured display patterns | Component rendering `MatterIntakeOutput`: matter classification card (type badge, confidence percentage, reasoning), template recommendation card (template name, reasoning, customisation notes, "Apply Template" button that approves the gate), required documents list (priority-coded), fee estimate card (range formatted as ZAR, tariff basis, reasoning, assumptions), conflict screening card (status badge, match details), risk flags as warning cards. |
| 531B.3 | Create matter intake panel component | `frontend/components/ai/matter-intake-panel.tsx` | -- | existing panel patterns | Panel alongside the project creation form. "Get AI Recommendations" button (disabled when: no customer selected, description < 20 chars, AI not configured). Loading state during invocation. Intake result display on success. Error message on failure. Cost display (formatted ZAR). Duration display. Panel does NOT replace the creation form -- it enhances it with advisory information. State machine: IDLE -> LOADING -> SUCCESS/ERROR. |
| 531B.4 | Modify project creation page | `frontend/app/(app)/org/[slug]/projects/new/page.tsx` (modify) | -- | existing page layout | Add `MatterIntakePanel` component to the project creation form. Render in a side panel (responsive: below on mobile, beside on desktop) next to the existing creation form. Pass customer ID (from form state), description (from form state), and prerequisite state as props. Gate by `AI_EXECUTE` capability. When the SELECT_MATTER_TEMPLATE gate is approved (or "Apply Template" is clicked), pre-populate the form with the recommended template's tasks. |
| 531B.5 | Create intake invocation server action | `frontend/app/(app)/org/[slug]/projects/new/intake-actions.ts` | -- | existing action patterns in projects | `invokeMatterIntakeAction(customerId, description)` server action that calls `invokeMatterIntake()` from API client and returns the result. Error handling with user-friendly messages. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/intake/MatterIntakeSkill.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/intake/MatterIntakeOutput.java`
- `backend/src/main/resources/ai/skills/matter-intake/system.txt`
- `backend/src/main/resources/ai/skills/matter-intake/output-schema.json`
- `backend/src/test/resources/ai/stubs/matter-intake/response.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/skill/intake/MatterIntakeSkillTest.java`

**Create (frontend):**
- `frontend/components/ai/matter-intake-panel.tsx`
- `frontend/components/ai/intake-result-display.tsx`
- `frontend/app/(app)/org/[slug]/projects/new/intake-actions.ts`

**Modify (frontend):**
- `frontend/app/(app)/org/[slug]/projects/new/page.tsx` -- add matter intake panel
- `frontend/lib/api/ai.ts` -- add intake skill API function + types

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectTemplateService.java` -- template loading
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- active matters query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/legal/tariff/TariffService.java` -- tariff schedule loading (verify package name)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/legal/conflict/ConflictCheckService.java` -- conflict check integration
- `frontend/app/(app)/org/[slug]/projects/new/page.tsx` -- existing creation form structure

### Architecture Decisions

- **Text-only skill** -- matter intake does not require document reading or vision. All input is structured entity data and free-text description.
- **SELECT_MATTER_TEMPLATE gate = frontend pre-fill** -- approving this gate does not mutate an entity server-side. The frontend uses the gate's `proposed_action.template_id` to pre-populate the creation form. The gate approval is still recorded for audit trail.
- **CONFIRM_CONFLICT_SCREEN gate** -- only created for CLEAR and POTENTIAL_CONFLICT. CONFLICT_DETECTED requires manual attorney investigation -- no gate offered.
- **Fee estimate is informational** -- displayed but no gate. The attorney uses it to inform the matter budget, which is set through the normal budget form.
- **Active matters limited to 500** -- prevents excessive token usage in the prompt. Sufficient for conflict screening in a small-to-medium firm.

### Non-scope

- No automatic template application (requires attorney approval via gate).
- No bulk intake analysis.
- No integration with external conflict databases.
- No automatic matter creation (the form remains the entry point).

---

## Summary: Files Created and Modified

### New Backend Files (by package)

**`integration/ai/`** (4 records):
- `AiCompletionRequest.java`, `AiVisionRequest.java`, `AiImageInput.java`, `AiCompletionResponse.java`

**`integration/ai/anthropic/`** (5 files):
- `AnthropicAiProvider.java`, `AnthropicApiClient.java`, `AnthropicProperties.java`, `AnthropicMessagesRequest.java`, `AnthropicMessagesResponse.java`

**`integration/ai/profile/`** (4 files):
- `AiFirmProfile.java`, `AiFirmProfileRepository.java`, `AiFirmProfileService.java`, `AiFirmProfileController.java`

**`integration/ai/execution/`** (2 files):
- `AiExecution.java`, `AiExecutionRepository.java`

**`integration/ai/gate/`** (8 files):
- `AiExecutionGate.java`, `AiExecutionGateRepository.java`, `AiExecutionGateService.java`, `AiExecutionGateController.java`, `GateAction.java`, `GateActionExecutor.java`, `AiGateApprovedEvent.java`, `AiGateRejectedEvent.java`, `AiGateExpiredEvent.java`

**`integration/ai/skill/`** (7 files):
- `AiSkill.java`, `AiSkillExecutionService.java`, `AiSkillController.java`, `AiCostService.java`, `AiPricingProperties.java`, `SkillExecutionRequest.java`, `SkillContext.java`, `AiSkillInvokedEvent.java`

**`integration/ai/skill/fica/`** (3 files):
- `FicaVerificationSkill.java`, `FicaVerificationOutput.java`, `FicaDocumentReader.java`

**`integration/ai/skill/intake/`** (2 files):
- `MatterIntakeSkill.java`, `MatterIntakeOutput.java`

**Resources** (5 files):
- `db/migration/tenant/V122__ai_foundation.sql`
- `ai/skills/fica-verification/system.txt`, `ai/skills/fica-verification/output-schema.json`
- `ai/skills/matter-intake/system.txt`, `ai/skills/matter-intake/output-schema.json`

**Test files** (12 files):
- `testutil/StubAiProvider.java`, `testutil/TestAiConfiguration.java`
- `ai/anthropic/AnthropicAiProviderTest.java`, `ai/anthropic/AnthropicApiClientTest.java`
- `ai/profile/AiFirmProfileIntegrationTest.java`
- `ai/execution/AiExecutionRepositoryTest.java`, `ai/gate/AiExecutionGateTest.java`, `ai/gate/AiExecutionGateServiceTest.java`, `ai/skill/AiCostServiceTest.java`
- `ai/skill/AiSkillExecutionServiceTest.java`, `ai/skill/AiSkillControllerTest.java`
- `ai/skill/fica/FicaVerificationSkillTest.java`, `ai/skill/intake/MatterIntakeSkillTest.java`
- `test/resources/ai/stubs/fica-verification/response.json`, `test/resources/ai/stubs/matter-intake/response.json`

### Modified Backend Files (4)

- `integration/ai/AiProvider.java` -- add 2 new methods
- `integration/ai/NoOpAiProvider.java` -- implement 2 new methods
- `orgrole/Capability.java` -- add AI_MANAGE, AI_EXECUTE, AI_REVIEW
- `application.yml` + `application-test.yml` -- add AI config sections

### New Frontend Files (12)

**Pages** (4):
- `app/(app)/org/[slug]/settings/ai/page.tsx`
- `app/(app)/org/[slug]/settings/ai/history/page.tsx`
- `app/(app)/org/[slug]/ai/reviews/page.tsx`
- `app/(app)/org/[slug]/ai/layout.tsx`

**Components** (6):
- `components/ai/ai-profile-form.tsx`
- `components/ai/ai-cost-summary.tsx`
- `components/ai/execution-gate-card.tsx`
- `components/ai/fica-verification-panel.tsx`
- `components/ai/fica-result-display.tsx`
- `components/ai/matter-intake-panel.tsx`
- `components/ai/intake-result-display.tsx`

**Libraries** (2):
- `lib/api/ai.ts`
- `lib/schemas/ai-profile.ts`

**Actions** (3):
- `app/(app)/org/[slug]/ai/reviews/actions.ts`
- `app/(app)/org/[slug]/customers/[id]/fica-actions.ts`
- `app/(app)/org/[slug]/projects/new/intake-actions.ts`

### Modified Frontend Files (3)

- `lib/nav-items.ts` -- add AI settings + AI reviews nav items
- `app/(app)/org/[slug]/customers/[id]/page.tsx` -- add FICA verification panel
- `app/(app)/org/[slug]/projects/new/page.tsx` -- add matter intake panel

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase72-ai-foundation-client-intelligence.md`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiProvider.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/tasks/phase71-xero-accounting-integration.md`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/nav-items.ts`