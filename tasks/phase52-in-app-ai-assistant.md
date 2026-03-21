# Phase 52 — In-App AI Assistant (BYOAK)

Phase 52 adds a conversational AI assistant that acts as a system expert for DocTeams. It knows what DocTeams can do, can query tenant data via 14 read tools, and can perform reversible actions via 8 write tools with explicit user confirmation. The assistant follows a Bring Your Own API Key (BYOAK) model where each tenant provides their own Anthropic API key, gated behind the PRO tier. Sessions are ephemeral: frontend state only, no server-side persistence. The architecture introduces no new database entities and no new migrations -- it builds entirely on the integration infrastructure from Phase 21 (`SecretStore`, `OrgIntegration`, `IntegrationRegistry`) and the capability-based RBAC from Phase 46.

The next epic number starts at **387**. ADRs 200--204 are already accepted.

**Architecture doc**: `architecture/phase52-in-app-ai-assistant.md`

**ADRs**:
- [ADR-200](adr/ADR-200-llm-chat-provider-interface.md) -- Separate LlmChatProvider interface from AiProvider (streaming chat vs. one-shot text)
- [ADR-201](adr/ADR-201-secret-store-reuse-for-ai-keys.md) -- SecretStore reuse for AI API keys (no new migration)
- [ADR-202](adr/ADR-202-consumer-callback-streaming.md) -- Consumer callback vs reactive streams for LLM streaming
- [ADR-203](adr/ADR-203-completable-future-confirmation.md) -- CompletableFuture confirmation flow for write tools
- [ADR-204](adr/ADR-204-virtual-thread-scoped-value-rebinding.md) -- Virtual thread ScopedValue re-binding for SSE chat streams

**Dependencies on prior phases**:
- Phase 21: `SecretStore`, `OrgSecret`, `EncryptedDatabaseSecretStore` -- API key storage and retrieval
- Phase 21: `IntegrationService`, `IntegrationGuardService`, `IntegrationRegistry`, `OrgIntegration` -- integration configuration, test connection, key set/delete
- Phase 21: `IntegrationDomain.AI`, `OrgSettings.aiEnabled` -- existing AI integration slot
- Phase 21: `AiProvider`, `NoOpAiProvider` -- existing one-shot AI interface (unchanged, coexists)
- Phase 4: `ProjectService`, `CustomerService`, `TaskService` -- read/write tool delegation targets
- Phase 5: `TimeEntryService`, `MyWorkService` -- time tracking tool delegation
- Phase 8: `ProjectBudgetService`, `ReportService`, `BillingRateService` -- financial read tools
- Phase 10: `InvoiceService` -- invoice read/write tool delegation
- Phase 46: `CapabilityAuthorizationService`, `RequestScopes.CAPABILITIES` -- tool-level capability filtering
- Phase 2: `PlanSyncService`, `Organization.tier` -- PRO tier gating

**Migration**: None. No new database tables or columns. Chat sessions are ephemeral (frontend state only). API key storage uses existing `org_secrets` table (V36). Provider config uses existing `org_integrations` table (V36). The `OrgSettings.aiEnabled` flag already exists (V36).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 387 | LLM Provider Abstraction: Interface, Records, Registry, Anthropic Adapter | Backend | -- | M | 387A, 387B | **Done** (PRs #802, #803) |
| 388 | Tool Framework + Read Tools (Batch 1: Core Entities) | Backend | 387A | M | 388A, 388B | **Done** (PRs #804, #805) |
| 389 | Read Tools (Batch 2: Financial + Search) + System Guide | Backend | 387A | M | 389A | **Done** (PR #806) |
| 390 | Assistant Service + Chat API + Confirmation Flow | Backend | 387, 388, 389 | L | 390A, 390B | **Done** (PRs #807, #808) |
| 391 | Frontend Chat UI: Provider, Panel, Trigger, SSE Hook | Frontend | 390 | L | 391A, 391B | **Done** (PRs #809, #810) |
| 392 | Write Tools + Settings Enhancement | Both | 390, 391 | M | 392A, 392B | **Done** (PRs #811, #812) |

---

## Dependency Graph

```
BACKEND INFRASTRUCTURE (sequential within epic)
──────────────────────────────────────────────────────────────────

[E387A LlmChatProvider interface,
 ChatRequest, StreamEvent (sealed), ChatMessage,
 ToolDefinition, ModelInfo records,
 LlmChatProviderRegistry,
 + unit tests]
        |
[E387B AnthropicLlmProvider
 with RestClient SSE parsing,
 validateKey(), availableModels(),
 WireMock test suite]
        |
        +──────────────────────────────────+
        |                                  |
TOOL FRAMEWORK + CORE                FINANCIAL + SEARCH
READ TOOLS (sequential)              READ TOOLS (sequential)
────────────────────                  ─────────────────────
        |                                  |
[E388A AssistantTool interface,       [E389A GetUnbilledTimeTool,
 AssistantToolRegistry,                GetTimeSummaryTool,
 TenantToolContext record,             GetProjectBudgetTool,
 + unit tests]                         GetProfitabilityTool,
        |                              ListInvoicesTool,
[E388B ListProjectsTool,               GetInvoiceTool,
 GetProjectTool,                       SearchEntitiesTool,
 ListCustomersTool,                    GetNavigationHelpTool,
 GetCustomerTool,                      system-guide.md,
 ListTasksTool,                        + integration tests]
 GetMyTasksTool,
 GetTimeSummaryTool,
 + integration tests]
        |                                  |
        +──────────────────────────────────+
                         |
ASSISTANT SERVICE + CHAT API (sequential)
──────────────────────────────────────────────────────────────────
                         |
[E390A AssistantService orchestration,
 ChatContext record, system prompt assembly,
 event routing, multi-turn tool loop,
 confirmation flow (CompletableFuture),
 IntegrationService.testConnection update,
 + integration tests]
        |
[E390B AssistantController
 (POST /chat + SseEmitter + ScopedValue rebind,
  POST /chat/confirm,
  GET /ai/models endpoint),
 + integration tests]
        |
FRONTEND CHAT UI (sequential)
──────────────────────────────────────────────────────────────────
        |
[E391A AssistantProvider context,
 AssistantPanel (Sheet),
 AssistantTrigger (floating button),
 parseSseEvents utility,
 useAssistantChat hook,
 layout.tsx integration,
 + frontend tests]
        |
[E391B UserMessage, AssistantMessage,
 ToolUseCard, ConfirmationCard,
 ToolResultCard, ErrorCard,
 TokenUsageBadge, EmptyState,
 + frontend tests]
        |
WRITE TOOLS + SETTINGS (parallel)
──────────────────────────────────────────────────────────────────
        |
        +─────────────────────────────+
        |                             |
[E392A 8 write tools               [E392B AI IntegrationCard
 (create/update project,            model selector dropdown,
  customer, task;                   PRO badge, upgrade prompt,
  log time; create draft invoice),  fetchAiModels action,
 + integration tests]               ModelInfo type,
                                    + frontend tests]
```

**Parallel opportunities**:
- After E387B: E388 (core read tools) and E389 (financial read tools) can run in parallel. They share only the `AssistantTool` interface and `ToolDefinition` record from E388A.
- E388A must complete before E388B (tool implementations depend on interface).
- E390A depends on all of E387, E388, and E389 (service uses provider registry + tool registry).
- E390A and E390B are sequential (controller depends on service).
- E391A and E391B are sequential (message components depend on panel and hook).
- E392A and E392B can run in parallel (write tools are backend, settings enhancement is frontend).

---

## Implementation Order

### Stage 0: Backend Infrastructure

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 387 | 387A | `LlmChatProvider` interface, `ChatRequest`/`StreamEvent`/`ChatMessage`/`ToolDefinition`/`ModelInfo`/`ToolResult` records, `LlmChatProviderRegistry` auto-discovery registry. Unit tests (~4). Backend only. | **Done** (PR #802) |
| 0b | 387 | 387B | `AnthropicLlmProvider` -- RestClient-based Anthropic Messages API adapter with SSE parsing, `validateKey()`, `availableModels()`, error mapping (401/403/429/5xx). WireMock test suite (~6). Backend only. | **Done** (PR #803) |

### Stage 1: Backend Tools (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a-i (parallel) | 388 | 388A | `AssistantTool` interface, `AssistantToolRegistry` auto-discovery + capability filtering, `TenantToolContext` record. Unit tests (~4). Backend only. | **Done** (PR #804) |
| 1a-ii (after 388A) | 388 | 388B | Core entity read tools: `ListProjectsTool`, `GetProjectTool`, `ListCustomersTool`, `GetCustomerTool`, `ListTasksTool`, `GetMyTasksTool`, `GetTimeSummaryTool`. Integration tests (~7). Backend only. | **Done** (PR #805) |
| 1b (parallel with 388) | 389 | 389A | Financial/search read tools: `GetUnbilledTimeTool`, `GetProjectBudgetTool`, `GetProfitabilityTool`, `ListInvoicesTool`, `GetInvoiceTool`, `SearchEntitiesTool`, `GetNavigationHelpTool`. `system-guide.md` resource file. Integration tests (~8). Backend only. | **Done** (PR #806) |

### Stage 2: Backend Orchestration (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 390 | 390A | `AssistantService` orchestration: pre-flight checks (tier, aiEnabled, provider, key), system prompt assembly (guide + tenant context + behavioral instructions), LLM invocation with `Consumer<StreamEvent>`, event routing loop, multi-turn tool execution, confirmation flow (`CompletableFuture` + `ConcurrentHashMap`). Update `IntegrationService.testConnection()` for AI domain. `ChatContext` record. Integration tests (~8). Backend only. | **Done** (PR #807) |
| 2b | 390 | 390B | `AssistantController`: `POST /api/assistant/chat` (returns `SseEmitter`, virtual thread executor, `ScopedValue` re-binding), `POST /api/assistant/chat/confirm`, `GET /api/settings/integrations/ai/models`. Integration tests (~6). Backend only. | **Done** (PR #808) |

### Stage 3: Frontend Chat UI (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 391 | 391A | `AssistantProvider` context, `AssistantPanel` (Sheet), `AssistantTrigger` (floating button), `parseSseEvents` utility, `useAssistantChat` hook (SSE connection + state + abort), layout.tsx integration. Frontend tests (~7). Frontend only. | **Done** (PR #809) |
| 3b | 391 | 391B | Message components: `UserMessage`, `AssistantMessage` (react-markdown), `ToolUseCard`, `ConfirmationCard`, `ToolResultCard`, `ErrorCard`, `TokenUsageBadge`, `EmptyState`. Frontend tests (~8). Frontend only. | **Done** (PR #810) |

### Stage 4: Write Tools + Settings (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 392 | 392A | 8 write tools: `CreateProjectTool`, `UpdateProjectTool`, `CreateCustomerTool`, `UpdateCustomerTool`, `CreateTaskTool`, `UpdateTaskTool`, `LogTimeEntryTool`, `CreateInvoiceDraftTool`. Integration tests (~8). Backend only. | **Done** (PR #811) |
| 4b (parallel) | 392 | 392B | AI `IntegrationCard` enhancement: model selector dropdown (from `GET /ai/models`), PRO badge, STARTER upgrade prompt, `fetchAiModels` server action, `ModelInfo` type. Frontend tests (~4). Frontend only. | **Done** (PR #812) |

---

## Epic 387: LLM Provider Abstraction — Interface, Records, Registry, Anthropic Adapter

**Goal**: Establish the provider-agnostic LLM chat interface, all shared data records, the provider registry, and the Anthropic Messages API adapter with WireMock-based tests for SSE parsing and error handling.

**References**: Architecture doc Sections 11.6.1--11.6.5. ADR-200 (separate interface), ADR-202 (consumer callback).

**Dependencies**: None (standalone infrastructure).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **387A** | 387.1--387.5 | `LlmChatProvider` interface with `chat()`, `validateKey()`, `availableModels()`, `providerId()`. Records: `ChatRequest`, `StreamEvent` (sealed interface with 5 inner records: `TextDelta`, `ToolUse`, `Usage`, `Done`, `Error`), `ChatMessage`, `ToolDefinition`, `ToolResult`, `ModelInfo`. `LlmChatProviderRegistry` auto-discovery + lookup. Unit tests (~4). Backend only. | **Done** (PR #802) |
| **387B** | 387.6--387.12 | `AnthropicLlmProvider` implementing `LlmChatProvider` via Spring `RestClient` with manual SSE parsing. Maps Anthropic SSE events (`content_block_delta`, `content_block_start`, `message_delta`, `message_stop`) to `StreamEvent` records. `validateKey()` sends minimal 1-token request. `availableModels()` returns static list. `@IntegrationAdapter(domain = AI, slug = "anthropic")`. WireMock test suite (~6). Backend only. | **Done** (PR #803) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 387.1 | Create `LlmChatProvider` interface | 387A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmChatProvider.java`. Methods: `String providerId()`, `void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer)`, `boolean validateKey(String apiKey, String model)`, `List<ModelInfo> availableModels()`. No Spring annotations on the interface itself. Pattern: `integration/ai/AiProvider.java` (interface structure). |
| 387.2 | Create shared records | 387A | -- | New files in `assistant/provider/` package: (1) `ChatRequest.java` -- record with `apiKey`, `model`, `systemPrompt`, `messages` (List of ChatMessage), `tools` (List of ToolDefinition). (2) `StreamEvent.java` -- sealed interface with inner records `TextDelta(String text)`, `ToolUse(String toolCallId, String toolName, Map<String,Object> input)`, `Usage(int inputTokens, int outputTokens)`, `Done()`, `Error(String message)`. (3) `ChatMessage.java` -- record with `role`, `content`, `toolResults` (List of ToolResult). (4) `ToolDefinition.java` -- record with `name`, `description`, `inputSchema` (Map). (5) `ToolResult.java` -- record with `toolCallId`, `content`. (6) `ModelInfo.java` -- record with `id`, `name`, `recommended`. All plain Java records, no annotations. |
| 387.3 | Create `LlmChatProviderRegistry` | 387A | 387.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmChatProviderRegistry.java`. `@Component` that injects `List<LlmChatProvider>` via constructor, builds `Map<String, LlmChatProvider>` keyed by `providerId()`. Fail fast on duplicate provider IDs. Methods: `LlmChatProvider get(String providerId)` (throws `IllegalArgumentException` if not found), `Collection<LlmChatProvider> getAll()`. Pattern: `integration/IntegrationRegistry.java` (auto-discovery from bean list). |
| 387.4 | Write unit tests for `LlmChatProviderRegistry` | 387A | 387.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmChatProviderRegistryTest.java`. 4 tests: (1) Registry discovers provider by `providerId()`, (2) `get("anthropic")` returns correct provider, (3) `get("unknown")` throws `IllegalArgumentException`, (4) `getAll()` returns all registered providers. Pure unit tests -- create mock `LlmChatProvider` implementations, no Spring context. |
| 387.5 | Write unit test for `StreamEvent` sealed interface | 387A | 387.2 | Extend `LlmChatProviderRegistryTest.java` or create new file. 1 test: verify all 5 `StreamEvent` variants can be pattern-matched via `switch` expression (compile-time exhaustiveness). Verify `TextDelta`, `ToolUse`, `Usage`, `Done`, `Error` are all permitted subtypes. |
| 387.6 | Create `AnthropicLlmProvider` class skeleton | 387B | 387.1, 387.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/anthropic/AnthropicLlmProvider.java`. `@Component` + `@IntegrationAdapter(domain = IntegrationDomain.AI, slug = "anthropic")`. Constructor injects `RestClient.Builder`. Fields: `RestClient restClient` (built from builder with base URL `https://api.anthropic.com`), `static final String API_VERSION = "2023-06-01"`. Implement `providerId()` returning `"anthropic"`. Implement `availableModels()` returning static list: `claude-sonnet-4-6` (recommended), `claude-opus-4-6`, `claude-haiku-4-5`. |
| 387.7 | Implement `chat()` method with SSE parsing | 387B | 387.6 | Within `AnthropicLlmProvider`. Build request body: `model`, `max_tokens` (4096), `stream: true`, `system` (from request.systemPrompt), `messages` (mapped from ChatMessage list with `tool_use_id` translation), `tools` (mapped from ToolDefinition list). POST to `/v1/messages` with headers `x-api-key`, `anthropic-version`, `content-type: application/json`, `accept: text/event-stream`. Read response body as `InputStream`, parse SSE lines (`event:` + `data:` separated by `\n\n`). Map events: `content_block_delta` (type `text_delta`) -> `StreamEvent.TextDelta`, `content_block_start` (type `tool_use`) -> `StreamEvent.ToolUse`, `message_delta` (has `usage`) -> `StreamEvent.Usage`, `message_stop` -> `StreamEvent.Done`. Call `eventConsumer.accept()` for each parsed event. |
| 387.8 | Implement SSE line parser helper | 387B | 387.7 | Within `AnthropicLlmProvider` or as a private inner class / static utility. Method: `parseSseChunk(BufferedReader reader)` reads lines until blank line, extracts `event` type and `data` JSON. Returns parsed event or null on EOF. Handle: multi-line data fields (concatenate), `event:` prefix, `data:` prefix, empty lines as event separators. Pattern: manual SSE parsing (no external SSE client library). |
| 387.9 | Implement error mapping in `chat()` | 387B | 387.7 | Within `AnthropicLlmProvider`. Catch HTTP status codes before SSE parsing: 401/403 -> `eventConsumer.accept(new StreamEvent.Error("Invalid API key..."))`, 429 -> `StreamEvent.Error("Rate limit exceeded...")`, 5xx -> `StreamEvent.Error("Provider unavailable...")`, connection timeout -> `StreamEvent.Error("Unable to reach provider...")`. Also handle `error` SSE event type from Anthropic (type `error` with `error.message` in data). |
| 387.10 | Implement `validateKey()` method | 387B | 387.6 | Within `AnthropicLlmProvider`. Send minimal request: `model` from parameter, `max_tokens: 1`, `messages: [{"role": "user", "content": "Hi"}]`, `stream: false`. POST to `/v1/messages` with the provided `apiKey`. Return `true` if HTTP 200, `false` if 401/403. Catch other errors and return `false`. Does not consume meaningful tokens. |
| 387.11 | Write WireMock tests for SSE parsing | 387B | 387.7, 387.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/provider/anthropic/AnthropicLlmProviderTest.java`. Use `@WireMockTest`. 4 tests: (1) `content_block_delta` SSE events parsed into `TextDelta` stream events, (2) `content_block_start` with `type: tool_use` parsed into `ToolUse` event with correct `toolCallId`, `toolName`, and `input`, (3) `message_delta` with usage parsed into `Usage` event + `message_stop` parsed into `Done` event, (4) Full multi-turn response (text + tool_use + text) emits events in correct order. Stub `POST /v1/messages` with pre-recorded SSE response bodies. |
| 387.12 | Write WireMock tests for error handling and validateKey | 387B | 387.9, 387.10 | Extend `AnthropicLlmProviderTest.java`. 3 tests: (5) HTTP 401 response emits `StreamEvent.Error` with "Invalid API key" message, (6) HTTP 429 response emits `StreamEvent.Error` with "Rate limit exceeded" message, (7) `validateKey()` returns true on 200 and false on 401. Stub responses with appropriate HTTP status codes. |

### Key Files

**Create:** `LlmChatProvider.java`, `ChatRequest.java`, `StreamEvent.java`, `ChatMessage.java`, `ToolDefinition.java`, `ToolResult.java`, `ModelInfo.java`, `LlmChatProviderRegistry.java`, `AnthropicLlmProvider.java`, `LlmChatProviderRegistryTest.java`, `AnthropicLlmProviderTest.java`

**Modify:** None (all new files in the `assistant/provider/` package).

### Architecture Decisions

- **Separate `LlmChatProvider` from `AiProvider` (ADR-200)**: One-shot text generation and multi-turn streaming chat are fundamentally different concerns. `AiProvider` is unchanged.
- **`Consumer<StreamEvent>` callback, not `Flux` (ADR-202)**: Avoids WebFlux dependency, maps directly to `SseEmitter.send()`, works naturally with virtual thread blocking.
- **Manual SSE parsing, no Anthropic SDK**: The SSE format is simple enough that a purpose-built parser is lighter and more maintainable than a third-party SDK. Avoids transitive dependency conflicts.
- **`@IntegrationAdapter` annotation on provider**: Present for consistency with integration pattern and for `IntegrationService.testConnection()` delegation, but chat path resolves via `LlmChatProviderRegistry`, not `IntegrationRegistry`.

---

## Epic 388: Tool Framework + Read Tools (Batch 1: Core Entities)

**Goal**: Establish the `AssistantTool` interface, auto-discovery registry with capability filtering, and implement the first batch of 7 read tools covering core entities (projects, customers, tasks, time). These tools delegate to existing domain services with zero business logic.

**References**: Architecture doc Sections 11.7.1--11.7.3, 11.7.5, 11.7.6. ADR-200 (ToolDefinition record).

**Dependencies**: Epic 387A (uses `ToolDefinition` record for registry output format).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **388A** | 388.1--388.5 | `AssistantTool` interface with `name()`, `description()`, `inputSchema()`, `requiresConfirmation()`, `requiredCapabilities()`, `execute()`. `AssistantToolRegistry` auto-discovery + capability-filtered `getToolsForUser()`, `getToolDefinitions()`, `getTool()`. `TenantToolContext` record with `fromRequestScopes()` factory method. Unit tests (~4). Backend only. | **Done** (PR #804) |
| **388B** | 388.6--388.14 | 7 core read tool `@Component` implementations: `ListProjectsTool`, `GetProjectTool`, `ListCustomersTool`, `GetCustomerTool`, `ListTasksTool`, `GetMyTasksTool`, `GetTimeSummaryTool`. Integration tests (~7). Backend only. | **Done** (PR #805) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 388.1 | Create `AssistantTool` interface | 388A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantTool.java`. Methods: `String name()`, `String description()`, `Map<String,Object> inputSchema()`, `boolean requiresConfirmation()`, `Set<String> requiredCapabilities()`, `Object execute(Map<String,Object> input, TenantToolContext context)`. No Spring annotations on the interface. Pattern: architecture doc Section 11.7.1. |
| 388.2 | Create `TenantToolContext` record | 388A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/TenantToolContext.java`. Record: `tenantId` (String), `memberId` (UUID), `orgRole` (String), `capabilities` (Set of String). Static factory: `fromRequestScopes()` reads from `RequestScopes`. Pattern: architecture doc Section 11.7.5. |
| 388.3 | Create `AssistantToolRegistry` | 388A | 388.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantToolRegistry.java`. `@Component` that injects `List<AssistantTool>` via constructor, builds `Map<String, AssistantTool>` keyed by `name()`. Methods: `List<AssistantTool> getToolsForUser(Set<String> capabilities)` -- returns tools where `requiredCapabilities()` is empty OR is a subset of user capabilities; `List<ToolDefinition> getToolDefinitions(Set<String> capabilities)` -- maps filtered tools to `ToolDefinition` records; `AssistantTool getTool(String name)` -- lookup, throws `IllegalArgumentException` if not found. |
| 388.4 | Write unit tests for `AssistantToolRegistry` | 388A | 388.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantToolRegistryTest.java`. 4 tests: (1) Registry discovers all tools from bean list, (2) `getToolsForUser` with full capabilities returns all tools, (3) `getToolsForUser` without `FINANCIAL_VISIBILITY` excludes financial tools, (4) `getTool("unknown_tool")` throws `IllegalArgumentException`. Create mock `AssistantTool` implementations for testing. Pure unit tests, no Spring context. |
| 388.5 | Write unit test for `TenantToolContext.fromRequestScopes()` | 388A | 388.2 | Extend `AssistantToolRegistryTest.java`. 1 test: within `ScopedValue.where(TENANT_ID, ...).where(MEMBER_ID, ...).run()`, call `TenantToolContext.fromRequestScopes()` and verify all fields populated correctly. |
| 388.6 | Implement `ListProjectsTool` | 388B | 388.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ListProjectsTool.java`. `@Component`. Injects `ProjectService`. `name()` = `"list_projects"`. `requiresConfirmation()` = false. `requiredCapabilities()` = empty. `execute()`: reads optional `status` from input, calls `projectService.findAll()` or `findByStatus()`, returns list of maps with `id`, `name`, `status`, `customerName`. ~30 lines. Pattern: architecture doc Section 11.7.6 example. |
| 388.7 | Implement `GetProjectTool` | 388B | 388.1 | New file: `assistant/tool/read/GetProjectTool.java`. `@Component`. Injects `ProjectService`. Accepts `projectId` (UUID) or `projectName` (String). Delegates to `findById()` or search by name. Returns map with full project details (id, name, status, description, customer, budget status, member count). `requiredCapabilities()` = empty. |
| 388.8 | Implement `ListCustomersTool` | 388B | 388.1 | New file: `assistant/tool/read/ListCustomersTool.java`. `@Component`. Injects `CustomerService`. Optional `status` filter. Returns list of maps with `id`, `name`, `status`, `email`, `phone`. `requiredCapabilities()` = empty. |
| 388.9 | Implement `GetCustomerTool` | 388B | 388.1 | New file: `assistant/tool/read/GetCustomerTool.java`. `@Component`. Injects `CustomerService`. Accepts `customerId` (UUID) or `customerName` (String). Returns map with full customer details. `requiredCapabilities()` = empty. |
| 388.10 | Implement `ListTasksTool` | 388B | 388.1 | New file: `assistant/tool/read/ListTasksTool.java`. `@Component`. Injects `TaskService`. Required: `projectId`. Optional: `status`, `assigneeId`. Returns list of maps with `id`, `title`, `status`, `assignee`, `priority`. `requiredCapabilities()` = empty. |
| 388.11 | Implement `GetMyTasksTool` | 388B | 388.1 | New file: `assistant/tool/read/GetMyTasksTool.java`. `@Component`. Injects `MyWorkService`. No input params -- uses `context.memberId()`. Returns user's tasks across all projects. `requiredCapabilities()` = empty. |
| 388.12 | Implement `GetTimeSummaryTool` | 388B | 388.1 | New file: `assistant/tool/read/GetTimeSummaryTool.java`. `@Component`. Injects `TimeEntryService`. Optional: `projectId`, `startDate`, `endDate`. Returns hours breakdown (billable, non-billable, by member). `requiredCapabilities()` = empty. |
| 388.13 | Write integration tests for core read tools | 388B | 388.6--388.12 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/CoreReadToolsTest.java`. `@SpringBootTest` + `TestcontainersConfiguration`. 5 tests: (1) `ListProjectsTool` returns projects from tenant schema, (2) `GetProjectTool` returns full project details by ID, (3) `ListCustomersTool` filters by status correctly, (4) `GetMyTasksTool` returns tasks only for the context member, (5) `ListTasksTool` filters by project and status. Seed test data via service calls within `ScopedValue.where()`. |
| 388.14 | Write integration test for tool registry end-to-end | 388B | 388.3, 388.6--388.12 | Extend `CoreReadToolsTest.java`. 2 tests: (6) `AssistantToolRegistry` discovers all registered tool beans (count >= 7), (7) `getToolDefinitions()` returns `ToolDefinition` records with correct `name` and `inputSchema` fields for each tool. |

### Key Files

**Create:** `AssistantTool.java`, `TenantToolContext.java`, `AssistantToolRegistry.java`, `AssistantToolRegistryTest.java`, `ListProjectsTool.java`, `GetProjectTool.java`, `ListCustomersTool.java`, `GetCustomerTool.java`, `ListTasksTool.java`, `GetMyTasksTool.java`, `GetTimeSummaryTool.java`, `CoreReadToolsTest.java`

**Modify:** None (all new files in the `assistant/tool/` package).

### Architecture Decisions

- **"Thin tool" pattern**: Every tool is 20-40 lines delegating to an existing `@Service`. No business logic, no validation, no authorization beyond capability filtering.
- **Capability filtering in registry, not tools**: `AssistantToolRegistry.getToolsForUser()` filters out tools the user cannot access. The LLM never sees tools it cannot use. This is cleaner than checking capabilities inside each tool.
- **`TenantToolContext` as immutable snapshot**: Created once from `RequestScopes` before tool execution. Tools receive it as a parameter but most do not use it directly -- services read from `RequestScopes` themselves.

---

## Epic 389: Read Tools (Batch 2: Financial + Search) + System Guide

**Goal**: Implement the remaining 7 read tools covering financial data (unbilled time, budgets, profitability, invoices), cross-entity search, and navigation help. Create the system guide markdown resource.

**References**: Architecture doc Sections 11.7.3, 11.7.7.

**Dependencies**: Epic 388A (uses `AssistantTool` interface and `TenantToolContext`).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **389A** | 389.1--389.10 | 7 read tool `@Component` implementations: `GetUnbilledTimeTool` (FINANCIAL_VISIBILITY), `GetProjectBudgetTool` (FINANCIAL_VISIBILITY), `GetProfitabilityTool` (FINANCIAL_VISIBILITY), `ListInvoicesTool` (INVOICING), `GetInvoiceTool` (INVOICING), `SearchEntitiesTool`, `GetNavigationHelpTool`. `system-guide.md` classpath resource (~3-5K tokens). Integration tests (~8). Backend only. | **Done** (PR #806) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 389.1 | Implement `GetUnbilledTimeTool` | 389A | -- | New file: `assistant/tool/read/GetUnbilledTimeTool.java`. `@Component`. Injects `TimeEntryService`. Optional: `customerId`, `projectId`. Returns unbilled hours and amount. `requiredCapabilities()` = `Set.of("FINANCIAL_VISIBILITY")`. Pattern: `ListProjectsTool` structure. |
| 389.2 | Implement `GetProjectBudgetTool` | 389A | -- | New file: `assistant/tool/read/GetProjectBudgetTool.java`. `@Component`. Injects `ProjectBudgetService`. Required: `projectId`. Returns budget status (hours/monetary used vs. cap, threshold alerts). `requiredCapabilities()` = `Set.of("FINANCIAL_VISIBILITY")`. |
| 389.3 | Implement `GetProfitabilityTool` | 389A | -- | New file: `assistant/tool/read/GetProfitabilityTool.java`. `@Component`. Injects `ReportService`. Optional: `projectId`, `customerId`. Returns profitability summary (revenue, cost, margin). `requiredCapabilities()` = `Set.of("FINANCIAL_VISIBILITY")`. |
| 389.4 | Implement `ListInvoicesTool` | 389A | -- | New file: `assistant/tool/read/ListInvoicesTool.java`. `@Component`. Injects `InvoiceService`. Optional: `status`, `customerId`. Returns invoice summaries (id, number, status, amount, customer). `requiredCapabilities()` = `Set.of("INVOICING")`. |
| 389.5 | Implement `GetInvoiceTool` | 389A | -- | New file: `assistant/tool/read/GetInvoiceTool.java`. `@Component`. Injects `InvoiceService`. Accepts `invoiceId` (UUID) or `invoiceNumber` (String). Returns full invoice details with line items. `requiredCapabilities()` = `Set.of("INVOICING")`. |
| 389.6 | Implement `SearchEntitiesTool` | 389A | -- | New file: `assistant/tool/read/SearchEntitiesTool.java`. `@Component`. Injects `ProjectService`, `CustomerService`, `TaskService`. Required: `query` (String). Searches across projects (by name), customers (by name/email), tasks (by title). Returns categorized results with entity type, id, name, and context. `requiredCapabilities()` = empty. |
| 389.7 | Implement `GetNavigationHelpTool` | 389A | -- | New file: `assistant/tool/read/GetNavigationHelpTool.java`. `@Component`. No service injection -- reads from the system guide string (injected via `@Value("classpath:assistant/system-guide.md")`). Required: `feature` (String). Searches the guide text for relevant sections and returns navigation instructions. `requiredCapabilities()` = empty. |
| 389.8 | Create `system-guide.md` resource file | 389A | -- | New file: `backend/src/main/resources/assistant/system-guide.md`. ~3-5K tokens (~1500-2500 words). Sections: (1) Navigation -- 6 zones with page names and paths (Work, Delivery, Clients, Finance, Team, Settings). (2) Common Workflows -- new client engagement, creating a project, logging time, generating an invoice, running a billing run, checking profitability. (3) Terminology -- "Matter"=Project, "WIP"=unbilled time, "Engagement Letter"=document template, etc. (4) Quick Reference -- entity relationships (Customer has Projects, Project has Tasks, Task has TimeEntries, etc.). Pattern: architecture doc Section 11.7.7 structure. |
| 389.9 | Write integration tests for financial read tools | 389A | 389.1--389.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/FinancialReadToolsTest.java`. `@SpringBootTest` + `TestcontainersConfiguration`. 5 tests: (1) `GetUnbilledTimeTool` returns unbilled hours for a project with time entries, (2) `GetProjectBudgetTool` returns budget status for a project with configured budget, (3) `ListInvoicesTool` filters by status, (4) `GetInvoiceTool` returns line items, (5) Financial tools excluded from `getToolsForUser()` when capabilities lack `FINANCIAL_VISIBILITY`. |
| 389.10 | Write integration tests for search and navigation tools | 389A | 389.6, 389.7 | Extend `FinancialReadToolsTest.java` or new file. 3 tests: (6) `SearchEntitiesTool` returns results across projects/customers/tasks matching query, (7) `SearchEntitiesTool` returns empty results for non-matching query, (8) `GetNavigationHelpTool` returns content from system guide for known feature keyword. |

### Key Files

**Create:** `GetUnbilledTimeTool.java`, `GetProjectBudgetTool.java`, `GetProfitabilityTool.java`, `ListInvoicesTool.java`, `GetInvoiceTool.java`, `SearchEntitiesTool.java`, `GetNavigationHelpTool.java`, `system-guide.md`, `FinancialReadToolsTest.java`

**Modify:** None (all new files).

### Architecture Decisions

- **Financial tools gated by `FINANCIAL_VISIBILITY`**: Users without this capability never see these tools. The LLM cannot even attempt to call them.
- **Invoice tools gated by `INVOICING`**: Same pattern. The capability filtering happens in the registry, not in the tool.
- **System guide is static, loaded once**: No runtime generation. Updated by developers per phase. `GetNavigationHelpTool` does simple text search/section extraction from the cached guide string.
- **`SearchEntitiesTool` is a thin aggregator**: Calls three existing services and merges results. No new search infrastructure. Limited to name/title matching.

---

## Epic 390: Assistant Service + Chat API + Confirmation Flow

**Goal**: Build the core orchestration service (`AssistantService`) that assembles system prompts, routes LLM events, executes tools inline, and manages the write confirmation flow via `CompletableFuture`. Build the `AssistantController` with `SseEmitter` streaming, virtual thread executor with `ScopedValue` re-binding, the confirm endpoint, and the AI models endpoint. Update `IntegrationService.testConnection()` for AI domain routing.

**References**: Architecture doc Sections 11.3.1--11.3.7, 11.4.1--11.4.5, 11.6.5, 11.9.2. ADR-203 (CompletableFuture confirmation), ADR-204 (ScopedValue re-binding).

**Dependencies**: Epic 387 (provider abstraction + registry), Epic 388 (tool framework + core tools), Epic 389 (financial tools + system guide).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **390A** | 390.1--390.8 | `AssistantService` orchestration: `chat()` method with pre-flight checks (PRO tier, aiEnabled, provider configured, key exists), system prompt assembly (guide + tenant context + behavioral instructions), `Consumer<StreamEvent>` event routing loop with multi-turn tool execution, confirmation flow (`CompletableFuture` + `ConcurrentHashMap` + 120s timeout), `confirm()` method. `ChatContext` record. Update `IntegrationService.testConnection()` for AI domain. Integration tests (~8). Backend only. | **Done** (PR #807) |
| **390B** | 390.2, 390.9--390.15 | `AssistantController`: `POST /api/assistant/chat` with `SseEmitter` (300s timeout), virtual thread executor, `ScopedValue` capture and re-bind (ADR-204). `POST /api/assistant/chat/confirm`. `GET /api/settings/integrations/ai/models`. Integration tests (~6). Backend only. | **Done** (PR #808) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 390.1 | Create `ChatContext` record | 390A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/ChatContext.java`. Record: `message` (String), `history` (List of ChatMessage), `currentPage` (String). Used as the request DTO for the chat endpoint. |
| 390.2 | Create `AssistantService` with pre-flight checks | 390A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantService.java`. `@Service`. Constructor injects: `LlmChatProviderRegistry`, `AssistantToolRegistry`, `SecretStore`, `IntegrationService`, `IntegrationGuardService`, `PlanSyncService`, `OrgSettingsService`. Field: `ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations`. Field: `String systemGuide` loaded via `@Value("classpath:assistant/system-guide.md")`. Method `chat(ChatContext context, SseEmitter emitter)`: (1) PRO tier check via `PlanSyncService`, (2) `IntegrationGuardService.requireEnabled(AI)`, (3) Get provider slug from `IntegrationService`, verify not `"noop"`, (4) `SecretStore.exists("ai:anthropic:api_key")` check. On any failure, emit `StreamEvent.Error` via emitter and complete. |
| 390.3 | Implement system prompt assembly | 390A | 390.2 | Within `AssistantService`. Private method `assembleSystemPrompt(String currentPage)`: concatenates 3 sections: (1) Static system guide from `this.systemGuide`. (2) Tenant context: org name (from `OrgSettingsService`), user role (from `RequestScopes.getOrgRole()`), current page path. (3) Behavioral instructions (hardcoded static string): "You are the DocTeams assistant...", "Always use tools to look up data...", "For write actions, describe what will be created/changed...", "Never claim to have performed an action unless confirmed...". Returns combined system prompt string. |
| 390.4 | Implement event routing loop with multi-turn tool execution | 390A | 390.2, 390.3 | Within `AssistantService.chat()`. After pre-flight and prompt assembly: (1) Retrieve API key from `SecretStore.retrieve("ai:anthropic:api_key")`. (2) Get model from `IntegrationService` (configJson `model` field). (3) Build initial `ChatRequest`. (4) Call `provider.chat(request, eventConsumer)` where `eventConsumer` routes events. (5) For `TextDelta` -> `emitter.send(event("text_delta", data))`. (6) For `ToolUse` with `requiresConfirmation()` = false -> execute tool inline, emit `tool_use` + `tool_result` events, append tool result to message history, call `provider.chat()` again for continuation (loop). (7) For `ToolUse` with `requiresConfirmation()` = true -> enter confirmation flow (task 390.5). (8) For `Usage` -> accumulate token counts. (9) For `Done` -> emit aggregated usage + done event, complete emitter. (10) For `Error` -> emit error event, complete emitter. |
| 390.5 | Implement confirmation flow | 390A | 390.4 | Within `AssistantService`. When a write `ToolUse` event is received: (1) Create `CompletableFuture<Boolean>`. (2) `pendingConfirmations.put(toolCallId, future)`. (3) Emit `tool_use` SSE event with `requiresConfirmation: true` and data preview. (4) `boolean approved = future.get(120, TimeUnit.SECONDS)`. (5) If approved: execute tool, emit `tool_result` with success, feed result to LLM for continuation. (6) If rejected: emit `tool_result` with "User cancelled", feed to LLM. (7) On `TimeoutException`: emit error event, complete emitter. (8) `finally`: `pendingConfirmations.remove(toolCallId)`. |
| 390.6 | Implement `confirm()` method | 390A | 390.5 | Within `AssistantService`. Method `confirm(String toolCallId, boolean approved)`: lookup `pendingConfirmations.get(toolCallId)`. If not found, throw `ResourceNotFoundException` ("Confirmation expired or already processed"). If found, call `future.complete(approved)`. |
| 390.7 | Update `IntegrationService.testConnection()` for AI domain | 390A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationService.java`. In the `testConnection()` method's `AI` case: when the configured slug is not `"noop"`, resolve provider via `LlmChatProviderRegistry.get(slug)`, retrieve API key from `SecretStore`, call `provider.validateKey(apiKey, model)`, return `ConnectionTestResult`. Inject `LlmChatProviderRegistry` into `IntegrationService` constructor. Pattern: existing switch cases for other domains. |
| 390.8 | Write integration tests for `AssistantService` | 390A | 390.2--390.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantServiceTest.java`. `@SpringBootTest` + `TestcontainersConfiguration`. Use a mock `LlmChatProvider` (test double that emits predefined `StreamEvent` sequences). 8 tests: (1) Chat with text-only response emits `text_delta` and `done` events, (2) Chat with read tool invocation executes tool and feeds result to LLM for continuation, (3) System prompt includes system guide content, (4) System prompt includes tenant context (org name, user role), (5) Error emitted when AI not enabled (`OrgSettings.aiEnabled = false`), (6) Error emitted when STARTER tier, (7) Error emitted when no API key configured, (8) Token usage accumulated across multi-turn conversation. Register mock provider in test config with `@TestConfiguration`. |
| 390.9 | Create `AssistantController` with chat endpoint | 390B | 390.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantController.java`. `@RestController` + `@RequestMapping("/api/assistant")`. `POST /chat`: (1) Accept `@RequestBody ChatContext`. (2) Capture `RequestScopes` values into local variables: `tenantId`, `memberId`, `orgRole`, `capabilities`. (3) Create `SseEmitter(300_000L)` (5-minute timeout). (4) Submit to `Executors.newVirtualThreadPerTaskExecutor()`: re-bind `ScopedValue.where(TENANT_ID, tenantId).where(MEMBER_ID, memberId).where(ORG_ROLE, orgRole).where(CAPABILITIES, capabilities).run(() -> assistantService.chat(context, emitter))`. (5) Set `emitter.onTimeout()` and `emitter.onError()` handlers. (6) Return emitter with `Content-Type: text/event-stream`. Pattern: ADR-204 code example. |
| 390.10 | Add confirm endpoint to `AssistantController` | 390B | 390.9 | Within `AssistantController`. `POST /chat/confirm`: Accept `@RequestBody` record `ConfirmRequest(String toolCallId, boolean approved)`. Delegate to `assistantService.confirm(toolCallId, approved)`. Return `Map.of("acknowledged", true)`. Catches `ResourceNotFoundException` (returns 404). |
| 390.11 | Add AI models endpoint | 390B | -- | Within `AssistantController` or in `IntegrationController` (evaluate which is more appropriate -- architecture doc says `IntegrationController`). `GET /api/settings/integrations/ai/models`: resolve configured provider slug from `IntegrationService`, look up provider via `LlmChatProviderRegistry`, call `provider.availableModels()`, return `Map.of("models", models)`. Requires Admin/Owner role (`@RequiresCapability`). |
| 390.12 | Write integration tests for chat endpoint | 390B | 390.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantControllerTest.java`. `@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration`. Use mock `LlmChatProvider` from test config. 3 tests: (1) `POST /api/assistant/chat` returns `text/event-stream` content type, (2) SSE response contains `text_delta` events, (3) Unauthenticated request returns 401. Use JWT mocks from `memberJwt()` pattern. |
| 390.13 | Write integration tests for confirm endpoint | 390B | 390.10 | Extend `AssistantControllerTest.java`. 2 tests: (4) `POST /api/assistant/chat/confirm` with unknown `toolCallId` returns 404, (5) Confirm endpoint returns `{"acknowledged": true}` for valid request (requires a pre-populated `CompletableFuture` in the service -- may need to trigger a chat first or directly manipulate the service for test setup). |
| 390.14 | Write integration test for models endpoint | 390B | 390.11 | Extend `AssistantControllerTest.java`. 1 test: (6) `GET /api/settings/integrations/ai/models` returns list of model objects with `id`, `name`, `recommended` fields. |
| 390.15 | Write integration test for `IntegrationService.testConnection()` AI domain | 390B | 390.7 | New test or extend existing `IntegrationServiceTest.java`. 1 test: `testConnection(AI)` when slug is `"anthropic"` calls `LlmChatProvider.validateKey()` and returns `ConnectionTestResult(success=true)`. Use WireMock or mock provider. |

### Key Files

**Create:** `ChatContext.java`, `AssistantService.java`, `AssistantController.java`, `AssistantServiceTest.java`, `AssistantControllerTest.java`

**Modify:** `IntegrationService.java` (+1 case in `testConnection()` switch, +1 constructor parameter)

### Architecture Decisions

- **CompletableFuture for confirmation (ADR-203)**: In-memory, no persistence. Chat sessions are ephemeral. Virtual thread blocking is cheap. 120s timeout auto-cleans.
- **ScopedValue re-binding in virtual thread (ADR-204)**: Controller captures all RequestScopes values and re-binds in the submitted virtual thread. Essential for Hibernate schema routing and capability checks.
- **Multi-turn tool loop**: After executing a read tool and feeding the result back to the LLM, the service calls `provider.chat()` again with the updated message history. This loop continues until the LLM produces a final text response.
- **All errors as SSE events**: The controller never throws during streaming. All error conditions are surfaced as `error` SSE events followed by stream completion.

---

## Epic 391: Frontend Chat UI — Provider, Panel, Trigger, SSE Hook

**Goal**: Build the complete frontend chat UI: the context provider for open/close state, the slide-out panel with streaming text, the floating trigger button, the SSE parser utility, the `useAssistantChat` hook managing conversation state and SSE connections, and all message rendering components.

**References**: Architecture doc Sections 11.8.1--11.8.7, 11.9.3--11.9.4.

**Dependencies**: Epic 390 (backend chat API).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **391A** | 391.1--391.9 | `AssistantProvider` context (open/close, aiEnabled flag), `AssistantPanel` (Sheet-based, 420px, message list, textarea input, send/stop controls), `AssistantTrigger` (fixed bottom-right, teal button, visibility logic), `parseSseEvents` utility, `useAssistantChat` hook (SSE via fetch + ReadableStream, message state, abort control, token tracking), layout.tsx integration. Frontend tests (~7). Frontend only. | **Done** (PR #809) |
| **391B** | 391.10--391.19 | Message components: `UserMessage` (right-aligned bubble), `AssistantMessage` (left-aligned, react-markdown, streaming cursor), `ToolUseCard` (loading spinner, expand/collapse), `ConfirmationCard` (data preview, Confirm/Cancel buttons), `ToolResultCard` (success/cancelled states), `ErrorCard`, `TokenUsageBadge` (header badge), `EmptyState` (admin vs. member messaging). Frontend tests (~8). Frontend only. | **Done** (PR #810) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 391.1 | Create `AssistantProvider` context | 391A | -- | New file: `frontend/components/assistant/assistant-provider.tsx`. `"use client"`. React context with: `isOpen` (boolean), `isAiEnabled` (boolean), `toggle()` (open/close). Props: `aiEnabled` (boolean), `children`. Export `useAssistant()` hook. Pattern: existing context providers (e.g., `OrgProfileProvider`). |
| 391.2 | Create `parseSseEvents` utility | 391A | -- | New file: `frontend/lib/sse-parser.ts`. Function `parseSseEvents(buffer: string): { parsed: SseEvent[], remainder: string }`. Splits by `\n\n`, extracts `event:` and `data:` fields, JSON-parses data. Interface `SseEvent { type: string; data: unknown }`. Handles: incomplete events at buffer end (carry over), multi-line data fields, empty lines. Pattern: architecture doc Section 11.8.6. |
| 391.3 | Create `useAssistantChat` hook | 391A | 391.2 | New file: `frontend/hooks/use-assistant-chat.ts`. `"use client"`. State: `messages` (ChatMessage[]), `isStreaming` (boolean), `tokenUsage` ({input, output}), `pendingConfirmations` (Map). Methods: `sendMessage(content, currentPage)` -- adds user message, POSTs to `/api/assistant/chat` with fetch + AbortController, reads SSE via `response.body.getReader()` + `TextDecoder` + `parseSseEvents()`, dispatches events to state. `confirmToolCall(toolCallId, approved)` -- POSTs to `/api/assistant/chat/confirm`. `stopStreaming()` -- aborts fetch, sets isStreaming false. `clearChat()` -- resets all state. Uses `useReducer` for state management. Pattern: architecture doc Section 11.8.5. |
| 391.4 | Create `AssistantPanel` component | 391A | 391.1, 391.3 | New file: `frontend/components/assistant/assistant-panel.tsx`. `"use client"`. Uses Shadcn `Sheet` (side="right"). Width: `sm:max-w-[420px]`. Header: "DocTeams Assistant" + Sparkles icon + close button. Body: scrollable div with `messages.map()` rendering (placeholder: just text for now, message components in 391B). Footer: `Textarea` (auto-resize), Send button (`bg-teal-600`), Stop button (shown during streaming). Enter=send, Shift+Enter=newline. Uses `useAssistantChat()` hook. Auto-scroll to bottom on new messages via `useEffect` + `scrollIntoView`. `z-50`. |
| 391.5 | Create `AssistantTrigger` component | 391A | 391.1 | New file: `frontend/components/assistant/assistant-trigger.tsx`. `"use client"`. Fixed button: `fixed bottom-6 right-6 z-50`. Icon: `Sparkles`. Styles: `bg-teal-600 hover:bg-teal-700 text-white rounded-full shadow-lg`. Size: 48x48px. Hidden when `isAiEnabled = false` or panel is open. Calls `toggle()` on click. |
| 391.6 | Integrate into org layout | 391A | 391.1, 391.4, 391.5 | Modify: `frontend/app/(app)/org/[slug]/layout.tsx`. Import `AssistantProvider`, `AssistantPanel`, `AssistantTrigger`. Derive `aiEnabled` from existing `settingsResult` data (`orgSettings.aiEnabled && org.tier === "PRO"`). Wrap content inside `AssistantProvider`. Add `<AssistantTrigger />` and `<AssistantPanel slug={slug} />` as siblings inside the provider. Place inside existing `CommandPaletteProvider`. Pattern: architecture doc Section 11.8.7. |
| 391.7 | Write frontend tests for AssistantProvider and trigger | 391A | 391.1, 391.5 | New file: `frontend/__tests__/assistant/assistant-trigger.test.tsx`. 3 tests: (1) `AssistantTrigger` visible when `aiEnabled = true`, (2) `AssistantTrigger` hidden when `aiEnabled = false`, (3) Click trigger toggles panel open. Use Vitest + Testing Library. Wrap in `AssistantProvider`. Add `afterEach(() => cleanup())`. |
| 391.8 | Write frontend tests for SSE parser | 391A | 391.2 | New file: `frontend/__tests__/assistant/sse-parser.test.ts`. 2 tests: (1) `parseSseEvents` correctly parses complete SSE events with `event:` and `data:` lines, (2) Partial chunks carry over to next call via `remainder`. Pure unit tests, no DOM. |
| 391.9 | Write frontend tests for useAssistantChat hook | 391A | 391.3 | New file: `frontend/__tests__/assistant/use-assistant-chat.test.ts`. 2 tests: (1) `sendMessage` adds user message to state and sets `isStreaming = true`, (2) `stopStreaming` aborts fetch and sets `isStreaming = false`. Mock `fetch` globally. Use `renderHook` from Testing Library. |
| 391.10 | Create `UserMessage` component | 391B | -- | New file: `frontend/components/assistant/user-message.tsx`. Right-aligned bubble with `bg-slate-100 rounded-lg p-3 max-w-[85%] ml-auto`. Displays user text. No `"use client"` needed (no hooks/handlers). |
| 391.11 | Create `AssistantMessage` component | 391B | -- | New file: `frontend/components/assistant/assistant-message.tsx`. `"use client"`. Left-aligned. Renders content via `react-markdown` with Tailwind typography classes (`prose prose-sm prose-slate`). Animated cursor: blinking `|` appended when `isStreaming = true`. Install `react-markdown` if not already a dependency. Max width 85%. |
| 391.12 | Create `ToolUseCard` component | 391B | -- | New file: `frontend/components/assistant/tool-use-card.tsx`. `"use client"`. Compact card: `border rounded-lg p-2`. Shows `"Looking up {toolName}..."` with `Loader2` spinner during loading, then `"Looked up {toolName}"` with expand/collapse chevron for raw result data (rendered as `<pre>` with JSON.stringify). Uses Shadcn `Collapsible`. |
| 391.13 | Create `ConfirmationCard` component | 391B | -- | New file: `frontend/components/assistant/confirmation-card.tsx`. `"use client"`. Prominent card with `border-l-4 border-teal-600 p-4 rounded-lg`. Shows action title ("Create Project"), data preview as key-value pairs (from tool input). Two buttons: "Confirm" (`bg-teal-600`) and "Cancel" (`variant="ghost"`). Buttons disabled while pending (after click). Calls `confirmToolCall(toolCallId, approved)` from `useAssistantChat`. |
| 391.14 | Create `ToolResultCard` component | 391B | -- | New file: `frontend/components/assistant/tool-result-card.tsx`. Success state: green-tinted card (`bg-emerald-50 border-emerald-200`) with checkmark icon and entity link ("View project" -> navigates to entity page). Cancelled state: muted card (`bg-slate-50`) with "Cancelled" label. |
| 391.15 | Create `ErrorCard` component | 391B | -- | New file: `frontend/components/assistant/error-card.tsx`. Red/destructive-tinted card (`bg-red-50 border-red-200`). Displays error message text. Pattern: existing error display patterns. |
| 391.16 | Create `TokenUsageBadge` component | 391B | -- | New file: `frontend/components/assistant/token-usage-badge.tsx`. Small badge in panel header. Displays `"~1.2K tokens"` (formats large numbers with K suffix). Tooltip (Shadcn `Tooltip`) shows breakdown: "Input: 834 / Output: 412". Props: `inputTokens`, `outputTokens`. |
| 391.17 | Create `EmptyState` component | 391B | -- | New file: `frontend/components/assistant/empty-state.tsx`. Shown when no messages. Two variants based on user role: (1) Admin/Owner: "AI not configured" with `Sparkles` icon and link to Settings -> Integrations page. (2) Member: "AI assistant is not available. Ask your admin to enable it." Props: `orgRole`, `slug` (for settings link). |
| 391.18 | Integrate message components into `AssistantPanel` | 391B | 391.10--391.17 | Modify: `frontend/components/assistant/assistant-panel.tsx`. Replace placeholder text rendering with proper component dispatch: render `UserMessage` for user role, `AssistantMessage` for assistant text, `ToolUseCard` for tool_use events, `ConfirmationCard` for pending confirmations, `ToolResultCard` for tool results, `ErrorCard` for errors. Add `TokenUsageBadge` in panel header. Show `EmptyState` when `messages.length === 0`. |
| 391.19 | Write frontend tests for message components | 391B | 391.10--391.17 | New file: `frontend/__tests__/assistant/message-components.test.tsx`. 8 tests: (1) `UserMessage` renders text right-aligned, (2) `AssistantMessage` renders markdown (heading, list), (3) `ToolUseCard` shows loading state then expand/collapse, (4) `ConfirmationCard` renders data preview and buttons, (5) `ConfirmationCard` Confirm button calls confirmToolCall with approved=true, (6) `ConfirmationCard` Cancel button calls confirmToolCall with approved=false, (7) `TokenUsageBadge` formats "~1.2K tokens" for 1234 input+output, (8) `EmptyState` admin variant shows settings link. Add `afterEach(() => cleanup())`. |

### Key Files

**Create:** `assistant-provider.tsx`, `assistant-panel.tsx`, `assistant-trigger.tsx`, `sse-parser.ts`, `use-assistant-chat.ts`, `user-message.tsx`, `assistant-message.tsx`, `tool-use-card.tsx`, `confirmation-card.tsx`, `tool-result-card.tsx`, `error-card.tsx`, `token-usage-badge.tsx`, `empty-state.tsx`

**Modify:** `app/(app)/org/[slug]/layout.tsx` (+AssistantProvider, +AssistantTrigger, +AssistantPanel)

### Architecture Decisions

- **Sheet (not Dialog) for panel**: The panel is an overlay that does not block interaction with the main page. Users can switch pages while the panel is open (though chat history is preserved in React state).
- **fetch + ReadableStream for SSE, not EventSource**: `EventSource` only supports GET requests. The chat endpoint is POST (with body). Using `fetch()` with `response.body.getReader()` provides SSE consumption via POST.
- **`useReducer` for chat state**: The chat state has complex transitions (add message, update streaming text, add tool card, resolve confirmation). A reducer with discriminated action types is cleaner than multiple `useState` calls.
- **react-markdown for assistant responses**: LLM responses include markdown formatting (headings, lists, code blocks, bold/italic). `react-markdown` renders this safely without `dangerouslySetInnerHTML`.

---

## Epic 392: Write Tools + Settings Enhancement

**Goal**: Implement all 8 write tools that require user confirmation before execution, and enhance the AI integration card on the Settings page with a model selector dropdown, PRO badge, and STARTER upgrade prompt.

**References**: Architecture doc Sections 11.7.4, 11.9.2, 11.9.3, 11.9.4, 11.10.2, 11.10.3.

**Dependencies**: Epic 390 (confirmation flow infrastructure in `AssistantService`), Epic 391 (`ConfirmationCard` component).

**Scope**: Backend + Frontend (split into separate slices)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **392A** | 392.1--392.10 | 8 write tool `@Component` implementations: `CreateProjectTool`, `UpdateProjectTool`, `CreateCustomerTool`, `UpdateCustomerTool`, `CreateTaskTool`, `UpdateTaskTool`, `LogTimeEntryTool`, `CreateInvoiceDraftTool`. All return `requiresConfirmation() = true`. Integration tests (~8). Backend only. | **Done** (PR #811) |
| **392B** | 392.11--392.16 | AI `IntegrationCard` enhancement: model selector dropdown populated from `GET /api/settings/integrations/ai/models`, PRO badge on AI card, STARTER tier upgrade prompt, `fetchAiModels` server action, `getAiModels()` API client, `ModelInfo` TypeScript type. Frontend tests (~4). Frontend only. | **Done** (PR #812) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 392.1 | Implement `CreateProjectTool` | 392A | -- | New file: `assistant/tool/write/CreateProjectTool.java`. `@Component`. Injects `ProjectService`. `name()` = `"create_project"`. `requiresConfirmation()` = true. `requiredCapabilities()` = `Set.of("PROJECT_MANAGEMENT")`. `execute()`: extracts `name`, optional `customerId`, `templateId` from input. Calls `projectService.createProject(...)`. Returns map with `id`, `name`, `status`. ~35 lines. |
| 392.2 | Implement `UpdateProjectTool` | 392A | -- | New file: `assistant/tool/write/UpdateProjectTool.java`. `@Component`. Injects `ProjectService`. Required: `projectId`. Optional: `name`, `status`, `customerId`. `requiredCapabilities()` = `Set.of("PROJECT_MANAGEMENT")`. Calls `projectService.updateProject(...)`. Returns updated project map. |
| 392.3 | Implement `CreateCustomerTool` | 392A | -- | New file: `assistant/tool/write/CreateCustomerTool.java`. `@Component`. Injects `CustomerService`. Required: `name`. Optional: `email`, `phone`. `requiredCapabilities()` = `Set.of("CUSTOMER_MANAGEMENT")`. Calls `customerService.createCustomer(...)`. Returns customer map with PROSPECT status. |
| 392.4 | Implement `UpdateCustomerTool` | 392A | -- | New file: `assistant/tool/write/UpdateCustomerTool.java`. `@Component`. Injects `CustomerService`. Required: `customerId`. Optional: `name`, `email`, `phone`, `status`. `requiredCapabilities()` = `Set.of("CUSTOMER_MANAGEMENT")`. Calls `customerService.updateCustomer(...)`. Returns updated map. |
| 392.5 | Implement `CreateTaskTool` | 392A | -- | New file: `assistant/tool/write/CreateTaskTool.java`. `@Component`. Injects `TaskService`. Required: `projectId`, `title`. Optional: `description`, `assigneeId`. `requiredCapabilities()` = empty (all roles). Creates task with OPEN status. Returns map with `id`, `title`, `status`. |
| 392.6 | Implement `UpdateTaskTool` | 392A | -- | New file: `assistant/tool/write/UpdateTaskTool.java`. `@Component`. Injects `TaskService`. Required: `taskId`. Optional: `title`, `status`, `assigneeId`. `requiredCapabilities()` = empty. Returns updated map. |
| 392.7 | Implement `LogTimeEntryTool` | 392A | -- | New file: `assistant/tool/write/LogTimeEntryTool.java`. `@Component`. Injects `TimeEntryService`. Required: `taskId`, `hours`, `date`. Optional: `description`, `billable`. `requiredCapabilities()` = empty. Uses `context.memberId()` as the time entry member. Returns map with `id`, `hours`, `date`, `billable`. |
| 392.8 | Implement `CreateInvoiceDraftTool` | 392A | -- | New file: `assistant/tool/write/CreateInvoiceDraftTool.java`. `@Component`. Injects `InvoiceService`. Required: `customerId`. Optional: `includeUnbilledTime` (boolean). `requiredCapabilities()` = `Set.of("INVOICING")`. Creates DRAFT invoice, optionally with unbilled time entries as line items. Returns map with `id`, `invoiceNumber`, `status`, `totalAmount`. |
| 392.9 | Write integration tests for write tools | 392A | 392.1--392.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/WriteToolsTest.java`. `@SpringBootTest` + `TestcontainersConfiguration`. 6 tests: (1) `CreateProjectTool` creates a project visible in `ProjectRepository`, (2) `CreateCustomerTool` creates a PROSPECT customer, (3) `CreateTaskTool` creates a task linked to a project, (4) `LogTimeEntryTool` creates a time entry for the context member, (5) `UpdateProjectTool` updates project name, (6) All write tools return `requiresConfirmation() = true`. Seed test data (project, customer, task) via service calls within `ScopedValue.where()`. |
| 392.10 | Write integration tests for capability enforcement on write tools | 392A | 392.1--392.8 | Extend `WriteToolsTest.java`. 2 tests: (7) `CreateProjectTool` requires `PROJECT_MANAGEMENT` -- tool excluded from `getToolsForUser()` without this capability, (8) `CreateInvoiceDraftTool` requires `INVOICING` -- tool excluded without this capability. |
| 392.11 | Add `ModelInfo` TypeScript type | 392B | -- | Modify: `frontend/lib/types/settings.ts` (or appropriate types file). Add: `export interface ModelInfo { id: string; name: string; recommended: boolean; }`. |
| 392.12 | Create `fetchAiModels` server action | 392B | 392.11 | Modify: `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts`. Add: `export async function fetchAiModels(): Promise<{ models: ModelInfo[] }>` -- calls `GET /api/settings/integrations/ai/models` via `fetchWithAuth()`. Pattern: existing server actions in same file. |
| 392.13 | Enhance AI `IntegrationCard` with model selector | 392B | 392.11, 392.12 | Modify: `frontend/components/integrations/IntegrationCard.tsx` (or create a new `AiIntegrationCard.tsx` if cleaner). Add: model selector `Select` dropdown populated from `fetchAiModels()` (called via SWR when card is expanded and domain is AI). Show recommended badge next to recommended model. On model change, update `configJson.model` via existing integration upsert action. Conditional rendering: only show model selector when domain is `AI` and key is configured. |
| 392.14 | Add PRO badge and upgrade prompt | 392B | -- | Modify: `IntegrationCard.tsx` (for AI domain). When domain is `AI`: (1) Show `PRO` badge (Shadcn `Badge` with `variant="pro"`) next to the card title. (2) If tenant tier is `STARTER`, show upgrade prompt: "AI Assistant requires the PRO plan" with link to billing page. Disable the key input and toggle when STARTER. Pattern: existing PRO badge usage in billing components. |
| 392.15 | Write frontend tests for AI integration card | 392B | 392.13, 392.14 | New file: `frontend/__tests__/integrations/ai-integration-card.test.tsx`. 4 tests: (1) Model selector dropdown renders with models from API, (2) PRO badge visible on AI integration card, (3) STARTER tier shows upgrade prompt and disables inputs, (4) Model change calls upsert action with updated `configJson`. Use Vitest + Testing Library. Mock `fetchAiModels` and integration actions. Add `afterEach(() => cleanup())`. |
| 392.16 | Add `getAiModels()` to API client | 392B | 392.11 | Modify: `frontend/lib/api/` (appropriate file, possibly `integrations.ts` or create `ai.ts`). Add `getAiModels()` function that calls the API endpoint. Used by the server action in 392.12. Pattern: existing API client functions in same directory. |

### Key Files

**Create:** `CreateProjectTool.java`, `UpdateProjectTool.java`, `CreateCustomerTool.java`, `UpdateCustomerTool.java`, `CreateTaskTool.java`, `UpdateTaskTool.java`, `LogTimeEntryTool.java`, `CreateInvoiceDraftTool.java`, `WriteToolsTest.java`

**Modify:** `IntegrationCard.tsx` (+model selector, +PRO badge, +upgrade prompt), `settings/integrations/actions.ts` (+fetchAiModels), `lib/types/settings.ts` (+ModelInfo), `lib/api/` (+getAiModels)

### Architecture Decisions

- **Write tools follow identical pattern to read tools**: Same `AssistantTool` interface, same ~30 lines each. The only difference is `requiresConfirmation() = true`. The confirmation flow is handled by `AssistantService`, not the tool.
- **Confirmation card content from tool input, not entity lookup**: For `create_*` tools, the card shows all field values from the tool input. No entity lookup needed. For `update_*` tools, the card shows changed fields -- `AssistantService` may fetch current values for before/after display.
- **Model selector uses SWR**: Fetches model list once when the AI card expands. The list is static (returns same values every time) but SWR handles loading/error states cleanly.
- **PRO gating at two levels**: Backend: `AssistantService` checks tier before processing. Frontend: card shows upgrade prompt for STARTER, trigger button hidden.

---
