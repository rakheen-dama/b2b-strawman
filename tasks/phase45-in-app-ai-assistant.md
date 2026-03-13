# Phase 45 — In-App AI Assistant (BYOAK)

Phase 45 adds an in-app AI assistant to DocTeams. The assistant follows a Bring Your Own API Key (BYOAK) model: each tenant configures their own Anthropic API key, and the backend handles all LLM communication. The assistant can answer "how do I..." questions with context-aware guidance, execute read queries against tenant data, and perform reversible write actions on the user's behalf (with confirmation). Chat sessions are ephemeral (frontend state only). Responses stream via SSE.

**Architecture doc**: `architecture/phase45-in-app-ai-assistant.md` (Section 11 of ARCHITECTURE.md)

**ADRs**: [ADR-173](../adr/ADR-173-provider-abstraction-depth.md) (provider abstraction depth), [ADR-174](../adr/ADR-174-tool-execution-model.md) (tool execution model), [ADR-175](../adr/ADR-175-confirmation-flow-architecture.md) (confirmation flow architecture), [ADR-176](../adr/ADR-176-system-guide-maintenance.md) (system guide maintenance), [ADR-177](../adr/ADR-177-api-key-encryption.md) (API key encryption)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 338 | AI Key Management & Settings API | Backend | -- | M | 338A, 338B | |
| 339 | LLM Provider Abstraction & Claude Adapter | Backend | -- | M | 339A, 339B | |
| 340 | Tool Framework & Read Tools | Backend | -- | L | 340A, 340B, 340C | |
| 341 | Assistant Service & Chat API | Backend | 338, 339, 340 | M | 341A, 341B | |
| 342 | Write Tools & Confirmation Flow | Backend | 341 | M | 342A, 342B | |
| 343 | Chat UI — Panel & Message Rendering | Frontend | 341 | M | 343A, 343B | |
| 344 | Chat UI — SSE Hook, Confirmation & Settings Page | Frontend | 342, 343 | M | 344A, 344B | |

## Dependency Graph

```
[E338 AI Key Management]  ──────────────────┐
       (Backend)                             │
                                             ├──► [E341 Assistant Service] ──► [E342 Write Tools]
[E339 Provider Abstraction] ────────────────┤                                       │
       (Backend)                             │                                       │
                                             │                                       ▼
[E340 Tool Framework & Read Tools] ─────────┘               ┌──► [E343 Chat UI Panel] ──► [E344 SSE + Settings]
                  (Backend)                                  │
                                          [E341] ────────────┘
```

**Parallel tracks**:
- Epics 338, 339, and 340 have NO cross-dependencies and can run fully in parallel (Stage 1).
- After all three complete: Epic 341 (assistant service) converges them.
- After Epic 341: Epic 342 (write tools) and Epic 343 (chat UI panel) can run in parallel.
- Epic 344 (SSE hook + confirmation + settings page) depends on both 342 and 343.

## Implementation Order

### Stage 1: Backend Foundation (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 338 | 338A | V68 migration, OrgSettings AI fields, AiKeyEncryptionService. Database foundation. |
| 1b | Epic 338 | 338B | AiSettingsController, settings API endpoints, integration tests. Depends on 338A. |
| 1c | Epic 339 | 339A | LlmProvider interface, StreamEvent sealed interface, ChatRequest, ModelInfo, LlmProviderRegistry. Framework only, no HTTP. |
| 1d | Epic 339 | 339B | AnthropicLlmProvider adapter, Anthropic API SSE parsing, WireMock integration tests. Depends on 339A. |
| 1e | Epic 340 | 340A | AssistantTool interface, TenantToolContext, ToolDefinition, AssistantToolRegistry, GetNavigationHelpTool + system guide resource. |
| 1f | Epic 340 | 340B | Read tools batch 1 (7 tools): ListProjects, GetProject, ListCustomers, GetCustomer, ListTasks, GetMyTasks, SearchEntities. |
| 1g | Epic 340 | 340C | Read tools batch 2 (7 tools): GetUnbilledTime, GetTimeSummary, GetProjectBudget, GetProfitability, ListInvoices, GetInvoice. Plus tool registry filtering tests. |

### Stage 2: Orchestration

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 341 | 341A | AssistantService core: system prompt assembly, LLM provider invocation loop, tool execution for read tools, SseEmitter event writing. |
| 2b | Epic 341 | 341B | AssistantController: SSE endpoint with ScopedValue re-binding in virtual thread, ChatRequestDto, security config, end-to-end integration tests. |

### Stage 3: Write Tools + Frontend Chat (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 342 | 342A | CompletableFuture confirmation map in AssistantService, confirm endpoint, 4 write tools (CreateProject, UpdateProject, CreateCustomer, UpdateCustomer). |
| 3b | Epic 342 | 342B | 4 write tools (CreateTask, UpdateTask, LogTimeEntry, CreateInvoiceDraft), confirmation timeout/cleanup, full confirmation flow integration tests. |
| 3c | Epic 343 | 343A | AssistantPanel (Sheet), AssistantTrigger (floating button), MessageList, UserMessage, AssistantMessage (react-markdown), layout integration. |
| 3d | Epic 343 | 343B | ToolUseCard, ConfirmationCard, ToolResultCard, ErrorCard, TokenUsageBadge, empty state (no key configured). |

### Stage 4: Frontend Convergence

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 344 | 344A | useAssistantChat hook (SSE connection, streaming state, confirmation dispatch), wire hook into panel. |
| 4b | Epic 344 | 344B | AI settings page (provider selector, key input, model selector, test connection, enable toggle), nav-items update, component tests. |

### Timeline

```
Stage 1:  [338A → 338B] // [339A → 339B] // [340A → 340B → 340C]    ← 3 parallel backend tracks
Stage 2:  [341A → 341B]                                               ← orchestration convergence
Stage 3:  [342A → 342B] // [343A → 343B]                             ← write tools + chat UI (parallel)
Stage 4:  [344A → 344B]                                               ← frontend SSE + settings
```

---

## Epic 338: AI Key Management & Settings API

**Goal**: Add AI provider configuration columns to OrgSettings (V68 migration), implement AES-256-GCM encryption for API keys, and expose AI settings CRUD endpoints with owner-only access control. This epic delivers the BYOAK key management infrastructure.

**References**: Architecture doc Sections 11.1, 11.4.1, 11.6, 11.7. [ADR-177](../adr/ADR-177-api-key-encryption.md) (API key encryption).

**Dependencies**: None (builds on existing OrgSettings entity from Phase 8, `ai_enabled` boolean from V36)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **338A** | 338.1-338.5 | V68 migration, OrgSettings entity AI fields, AiKeyEncryptionService with AES-256-GCM, application config for encryption key | |
| **338B** | 338.6-338.10 | AiSettingsController with GET/PUT/test endpoints, OrgSettingsService AI methods, security config, audit events, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 338.1 | Create V68 tenant migration adding AI provider columns to org_settings | 338A | | `db/migration/tenant/V68__add_ai_provider_config.sql`. Add `ai_provider VARCHAR(32)`, `ai_api_key_enc TEXT`, `ai_model VARCHAR(64)` to `org_settings`. All nullable (NULL = not configured). `ai_enabled` already exists from V36. Add `COMMENT ON COLUMN` for each. No indexes needed. Pattern: follow `V67__create_org_role_tables.sql` for ALTER TABLE style. |
| 338.2 | Add AI fields to OrgSettings entity | 338A | | Modify `settings/OrgSettings.java` — add `aiProvider` (String, VARCHAR(32)), `aiApiKeyEnc` (String, TEXT), `aiModel` (String, VARCHAR(64)) fields with `@Column` annotations. Add getters/setters. Add `updateAiConfig(String provider, String encryptedKey, String model)` method that also sets `updatedAt`. Note: `aiEnabled` boolean already exists on the entity. |
| 338.3 | Create AiKeyEncryptionService | 338A | | `assistant/AiKeyEncryptionService.java` — `@Service` bean. Uses `javax.crypto.Cipher` with `AES/GCM/NoPadding`. Reads encryption key from `@Value("${app.ai.encryption-key}")`. Methods: `String encrypt(String plaintext)` (generates 12-byte random IV, prepends to ciphertext, Base64-encodes result), `String decrypt(String ciphertext)` (extracts IV from first 12 bytes, decrypts). ~50 lines. Note: the existing `SecretStore`/`OrgSecret` infrastructure in `integration/secret/` handles per-integration secrets; this service is purpose-built for the AI key per ADR-177. |
| 338.4 | Add application config for AI encryption key | 338A | | Add `app.ai.encryption-key` to `application.yml` (placeholder), `application-local.yml` (dev key), `application-test.yml` (test key). The value must be a 32-byte (256-bit) key, Base64-encoded. Pattern: follow existing `app.internal-api-key` config pattern. |
| 338.5 | Add AiKeyEncryptionService unit tests | 338A | | `assistant/AiKeyEncryptionServiceTest.java` (~6 tests): encrypt/decrypt round-trip produces original plaintext, different plaintexts produce different ciphertexts, decrypt with wrong key fails, encrypt produces Base64 string, null/empty input handling, IV uniqueness (encrypt same plaintext twice, ciphertexts differ). Standard JUnit 5, no Spring context. |
| 338.6 | Add AI settings methods to OrgSettingsService | 338B | | Modify `settings/OrgSettingsService.java` — add `getAiSettings()` returning a record `AiSettingsDto(String provider, String model, boolean enabled, boolean keyConfigured)` (never returns key material). Add `updateAiSettings(String provider, String apiKey, String model, boolean enabled)` — encrypts key via `AiKeyEncryptionService`, calls `OrgSettings.updateAiConfig()`, publishes audit event `ai_settings.updated` (log provider + model only, never key). When `apiKey` is null/blank, retain existing encrypted key. Inject `AiKeyEncryptionService`. |
| 338.7 | Create AiSettingsController | 338B | | `assistant/AiSettingsController.java` — `@RestController`, `@RequestMapping("/api/settings/ai")`. Inner DTOs: `AiSettingsResponse(String provider, String model, boolean enabled, boolean keyConfigured)`, `UpdateAiSettingsRequest(String provider, String apiKey, @Size(max=64) String model, Boolean enabled)`. Endpoints: `GET /` returns current settings (200), `PUT /` updates settings (owner only, `@PreAuthorize("hasRole('ORG_OWNER')")`), `POST /test` validates key by calling `LlmProviderRegistry.get(provider).validateKey()`. Controller delegates to `OrgSettingsService` — pure delegation, no logic. Pattern: follow `settings/OrgSettingsController.java` structure but as a separate controller. |
| 338.8 | Update SecurityConfig for AI settings endpoints | 338B | | Modify `security/SecurityConfig.java` — verify `/api/settings/ai/**` is covered by authenticated endpoint patterns (likely already covered by `/api/**`). Ensure `/api/assistant/**` pattern is added for future slices. |
| 338.9 | Add AiSettingsController integration tests | 338B | | `assistant/AiSettingsControllerTest.java` (~8 MockMvc tests): GET returns defaults (provider null, enabled false, keyConfigured false), PUT with owner JWT sets provider+model+enabled (201), GET after PUT shows keyConfigured true, PUT without apiKey retains existing key, PUT with non-owner JWT returns 403, PUT with admin JWT returns 403, POST /test without configured key returns 422, invalid provider rejected (400). Use `ownerJwt()` and `adminJwt()` mock helpers per `backend/CLAUDE.md` JWT mocking pattern. |
| 338.10 | Add audit event verification for AI settings changes | 338B | | Within `AiSettingsControllerTest.java` (~2 additional tests): verify PUT creates audit event with action `ai_settings.updated` and details containing provider + model (NOT key), verify audit event detail does not contain `apiKey` or `encryptedKey` fields. Pattern: follow `audit/AuditEventControllerTest.java` for audit verification. |

### Key Files

**Slice 338A — Create:**
- `backend/src/main/resources/db/migration/tenant/V68__add_ai_provider_config.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AiKeyEncryptionService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/AiKeyEncryptionServiceTest.java`

**Slice 338A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — Add 3 AI fields
- `backend/src/main/resources/application.yml` — Add `app.ai.encryption-key`
- `backend/src/main/resources/application-local.yml` — Dev encryption key
- `backend/src/test/resources/application-test.yml` — Test encryption key

**Slice 338A — Read for context:**
- `backend/src/main/resources/db/migration/tenant/V36__create_integration_tables.sql` — Where `ai_enabled` was added
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/SecretStore.java` — Existing secrets pattern (not reused, but for awareness)

**Slice 338B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AiSettingsController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/AiSettingsControllerTest.java`

**Slice 338B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` — Add AI settings methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` — Verify endpoint patterns

**Slice 338B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` — Existing settings controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — Audit integration
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerTest.java` — MockMvc + JWT mock pattern

### Architecture Decisions

- **Separate `AiKeyEncryptionService`**: The existing `SecretStore`/`OrgSecret` infrastructure (from Phase 21) stores integration secrets in the `org_secrets` table. The architecture doc specifies a separate encryption service with `app.ai.encryption-key` from app config (ADR-177). This is intentional: the AI key encryption is self-contained and does not depend on the `org_secrets` table. The encrypted key is stored directly on `org_settings.ai_api_key_enc`.
- **`assistant/` package**: New top-level feature package for all assistant-related code. The `AiKeyEncryptionService` and `AiSettingsController` live here (not in `settings/`), because they are assistant-specific concerns.
- **Owner-only settings**: Only the org owner can configure AI settings. Admins can view (GET) but not modify. This follows the existing pattern where sensitive configuration is owner-only.
- **`ai_enabled` reuse**: The `ai_enabled` boolean already exists on `OrgSettings` from V36. It serves as the org-level kill switch. The new `ai_provider`, `ai_api_key_enc`, and `ai_model` columns complement it.

---

## Epic 339: LLM Provider Abstraction & Claude Adapter

**Goal**: Define the framework-agnostic LLM provider interface with streaming support, implement the Anthropic Claude adapter that parses SSE from the Anthropic Messages API, and create the provider registry for lookup by provider ID.

**References**: Architecture doc Sections 11.2, 11.8 (code patterns). [ADR-173](../adr/ADR-173-provider-abstraction-depth.md) (thin interface).

**Dependencies**: None (provider receives pre-decrypted API key in `ChatRequest`, no dependency on encryption service)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **339A** | 339.1-339.5 | LlmProvider interface, StreamEvent sealed interface, ChatRequest/ModelInfo records, LlmProviderRegistry, unit tests | |
| **339B** | 339.6-339.10 | AnthropicLlmProvider adapter, Anthropic Messages API SSE parsing, RestClient configuration, WireMock integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 339.1 | Create LlmProvider interface | 339A | | `assistant/provider/LlmProvider.java` — 4 methods: `void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer)` (blocks until complete, pushes events to consumer), `boolean validateKey(String apiKey, String model)`, `List<ModelInfo> availableModels()`, `String providerId()`. Note: `chat()` uses `Consumer<StreamEvent>` not `Flux` — framework-agnostic, consumer writes to `SseEmitter`. |
| 339.2 | Create StreamEvent sealed interface | 339A | | `assistant/provider/StreamEvent.java` — sealed interface with 6 records: `TextDelta(String text)`, `ToolUse(String toolCallId, String toolName, Map<String, Object> input)`, `ToolResult(String toolCallId, Object result)`, `Usage(int inputTokens, int outputTokens)`, `Done()`, `Error(String message)`. All implement `StreamEvent`. |
| 339.3 | Create ChatRequest, ModelInfo, and ToolDefinition records | 339A | | `assistant/provider/ChatRequest.java` — record: `(String apiKey, String model, String systemPrompt, List<ChatMessage> messages, List<ToolDefinition> tools)`. `assistant/provider/ModelInfo.java` — record: `(String id, String displayName, boolean recommended)`. `assistant/provider/ChatMessage.java` — record: `(String role, String content)`. `assistant/tool/ToolDefinition.java` — record: `(String name, String description, Map<String, Object> inputSchema)` — the JSON Schema representation sent to the LLM. |
| 339.4 | Create LlmProviderRegistry | 339A | | `assistant/provider/LlmProviderRegistry.java` — `@Component`. Constructor-injected `List<LlmProvider>` (Spring auto-discovers all `LlmProvider` beans). `get(String providerId)` returns provider or throws `InvalidStateException("Unknown provider: " + providerId)`. `getAll()` returns all providers. Stores in `Map<String, LlmProvider>` keyed by `providerId()`. |
| 339.5 | Add provider framework unit tests | 339A | | `assistant/provider/LlmProviderRegistryTest.java` (~5 tests): registry with one provider returns it by ID, registry with unknown ID throws, registry with multiple providers returns correct one, `availableModels()` delegates correctly, `StreamEvent` sealed interface exhaustive switch works. Standard JUnit 5. Create a `TestLlmProvider` stub for testing. |
| 339.6 | Add Anthropic SDK or RestClient dependency | 339B | | Add `anthropic-java` SDK to `pom.xml` if official SDK exists and supports streaming. If no official SDK or it lacks SSE streaming support, use Spring's `RestClient` with manual SSE parsing. Check Maven Central for `com.anthropic:anthropic-java`. Add `app.ai.anthropic.base-url` to `application.yml` (default: `https://api.anthropic.com`). |
| 339.7 | Create AnthropicLlmProvider — chat method | 339B | | `assistant/provider/anthropic/AnthropicLlmProvider.java` — `@Component` implementing `LlmProvider`. `providerId()` returns `"anthropic"`. `chat()` method: (1) Build Anthropic Messages API request body (convert `ChatRequest.messages` to Anthropic format, convert `ToolDefinition` list to Anthropic tool format, set `stream: true`). (2) POST to `{baseUrl}/v1/messages` with `x-api-key` header and `anthropic-version: 2023-06-01`. (3) Parse SSE stream: `content_block_delta` (type=text_delta) emits `TextDelta`, `content_block_start` (type=tool_use) emits `ToolUse`, `message_delta` emits `Usage`, `message_stop` emits `Done`. (4) Handle `tool_use` content blocks by accumulating JSON input across deltas. Use `RestClient` with `exchange()` for streaming. |
| 339.8 | Create AnthropicLlmProvider — validateKey and availableModels | 339B | | In `AnthropicLlmProvider.java` — `validateKey()`: POST a minimal 1-token completion (`max_tokens: 1, messages: [{role: "user", content: "hi"}]`) and check for 200 response. Return false on 401/403. `availableModels()`: return static list of Claude models with display names: `claude-sonnet-4-6` (recommended), `claude-opus-4-6`, `claude-haiku-35-20241022`. Anthropic has no list-models API — hardcode known models. |
| 339.9 | Add AnthropicLlmProvider WireMock integration tests | 339B | | `assistant/provider/anthropic/AnthropicLlmProviderTest.java` (~10 tests): successful streaming chat (verify TextDelta events), tool_use content block parsed correctly, Usage extracted from message_delta, Done emitted on message_stop, 401 response emits Error event, 429 rate limit emits Error event, network timeout emits Error event, validateKey returns true on 200, validateKey returns false on 401, availableModels returns expected list. Use `WireMockTest` with `@RegisterExtension` for mocking Anthropic API. Configure `app.ai.anthropic.base-url` to point to WireMock. Pattern: if WireMock is not already in test dependencies, add `wiremock-spring-boot` or `com.github.tomakehurst:wiremock-jre8-standalone`. |
| 339.10 | Add Anthropic SSE response fixtures | 339B | | Create test resource files in `src/test/resources/fixtures/anthropic/` — `streaming-text-response.txt` (SSE formatted text-only response), `streaming-tool-use-response.txt` (SSE formatted response with tool_use block), `error-response.json` (401 error body). These fixtures are used by WireMock stubs in 339.9. |

### Key Files

**Slice 339A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/StreamEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/ChatRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/ChatMessage.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/ModelInfo.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmProviderRegistry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/ToolDefinition.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmProviderRegistryTest.java`

**Slice 339B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/anthropic/AnthropicLlmProvider.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/provider/anthropic/AnthropicLlmProviderTest.java`
- `backend/src/test/resources/fixtures/anthropic/streaming-text-response.txt`
- `backend/src/test/resources/fixtures/anthropic/streaming-tool-use-response.txt`
- `backend/src/test/resources/fixtures/anthropic/error-response.json`

**Slice 339B — Modify:**
- `backend/pom.xml` — Add Anthropic SDK or WireMock test dependency
- `backend/src/main/resources/application.yml` — Add `app.ai.anthropic.base-url`

**Slice 339B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/AiProvider.java` — Existing AI provider port (different scope: text generation, not chat)

### Architecture Decisions

- **`Consumer<StreamEvent>` not `Flux`**: The `chat()` method blocks and pushes events to a consumer callback. This keeps the interface framework-agnostic (no WebFlux dependency) and matches Spring MVC's `SseEmitter` pattern. The consumer writes events to the emitter.
- **Static model list**: Anthropic has no list-models API. The `availableModels()` method returns a hardcoded list of known models. New models are added by updating the list — acceptable for a single-provider v1.
- **Separate from existing `AiProvider`**: The existing `integration/ai/AiProvider.java` interface handles text generation, summarization, and categorization. The new `LlmProvider` interface handles conversational chat with tool use and streaming. They serve different purposes and coexist.
- **WireMock for Anthropic tests**: Integration tests mock the Anthropic API via WireMock to test SSE parsing without real API calls. This avoids API key requirements in CI and provides deterministic test data.

---

## Epic 340: Tool Framework & Read Tools

**Goal**: Define the tool interface and registry, implement the 14 read-only tools that query tenant data, and create the static system guide resource. Tools are Spring `@Component` beans that delegate to existing domain services via constructor injection.

**References**: Architecture doc Sections 11.3, 11.8 (tool interface, example tool). [ADR-174](../adr/ADR-174-tool-execution-model.md) (internal service calls).

**Dependencies**: None (tools use `ToolDefinition` from Epic 339 package, but can be built in parallel since `ToolDefinition` is a simple record)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **340A** | 340.1-340.6 | AssistantTool interface, TenantToolContext, AssistantToolRegistry, GetNavigationHelpTool, system guide resource, unit tests | |
| **340B** | 340.7-340.11 | Read tools batch 1 (7 tools): ListProjects, GetProject, ListCustomers, GetCustomer, ListTasks, GetMyTasks, SearchEntities + integration tests | |
| **340C** | 340.12-340.16 | Read tools batch 2 (7 tools): GetUnbilledTime, GetTimeSummary, GetProjectBudget, GetProfitability, ListInvoices, GetInvoice + tool registry filtering tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 340.1 | Create AssistantTool interface | 340A | | `assistant/tool/AssistantTool.java` — interface with 6 methods: `String name()`, `String description()`, `Map<String, Object> inputSchema()` (JSON Schema), `boolean requiresConfirmation()`, `Set<String> requiredCapabilities()`, `Object execute(Map<String, Object> input, TenantToolContext context)`. Pattern: follow architecture doc Section 11.8 code pattern. |
| 340.2 | Create TenantToolContext record | 340A | | `assistant/tool/TenantToolContext.java` — record: `(String tenantId, UUID memberId, String orgRole)`. Built from `RequestScopes` values in `AssistantService` before tool execution. Passed to every tool's `execute()` method. |
| 340.3 | Create AssistantToolRegistry | 340A | | `assistant/tool/AssistantToolRegistry.java` — `@Component`. Constructor-injected `List<AssistantTool>` (auto-discovers all tool beans). `getToolsForUser(String orgRole)` filters tools by `requiredCapabilities()` against the user's role. `getTool(String name)` returns tool by name or throws. Stores tools in `Map<String, AssistantTool>`. `getToolDefinitions(String orgRole)` returns `List<ToolDefinition>` for the LLM (filtered). Capability mapping: `"admin_or_owner"` matches `owner`/`admin`, `"any"` matches all roles. |
| 340.4 | Create system guide resource | 340A | | `backend/src/main/resources/assistant/system-guide.md` — manually written markdown (~3-5K tokens). Sections: Navigation Structure (6 zones with pages), Pages & Features (1-2 sentences per page), Common Workflows (5-8 workflows: new client engagement, generating an invoice, setting up rate cards, logging time, creating a project from template, running profitability reports, managing team members, configuring document templates), Terminology (domain terms: matter, engagement, WIP, billable/non-billable, rate card, etc.). Follow ADR-176 format with section markers (`<!-- AUTO:NAV -->`, `<!-- MANUAL:WORKFLOWS -->`). |
| 340.5 | Create GetNavigationHelpTool | 340A | | `assistant/tool/read/GetNavigationHelpTool.java` — `@Component`. Loads system guide from `classpath:assistant/system-guide.md` at construction (`@PostConstruct` or constructor with `ResourceLoader`). Parses into a `Map<String, String>` keyed by feature name (e.g., "invoices" -> "Navigate to Finance > Invoices..."). `inputSchema()`: `{ type: "object", properties: { feature: { type: "string", description: "Feature or page name to get navigation help for" } }, required: ["feature"] }`. `execute()`: looks up feature in map, returns description or "I don't have specific navigation help for that feature." `requiredCapabilities()`: empty set (all roles). |
| 340.6 | Add tool framework unit tests | 340A | | `assistant/tool/AssistantToolRegistryTest.java` (~6 tests): registry discovers all tools, `getToolsForUser("member")` excludes admin-only tools, `getToolsForUser("owner")` includes all tools, `getTool()` returns correct tool by name, `getTool()` throws for unknown name, `getToolDefinitions()` returns ToolDefinition list with correct schemas. Create 2-3 stub tool implementations for testing. `assistant/tool/read/GetNavigationHelpToolTest.java` (~3 tests): known feature returns description, unknown feature returns fallback message, system guide loaded correctly. |
| 340.7 | Create ListProjectsTool and GetProjectTool | 340B | | `assistant/tool/read/ListProjectsTool.java` — inject `ProjectService`. Input: optional `status` filter. Execute: call `projectService.findAll()` or filtered variant, return list of project summaries (id, name, status, customerName). `assistant/tool/read/GetProjectTool.java` — inject `ProjectService`. Input: `projectId` (UUID) or `projectName` (String). Execute: find by ID or search by name, return project details. Both: `requiresConfirmation()` false, `requiredCapabilities()` empty. Pattern: follow architecture doc Section 11.8 ListProjectsTool example. |
| 340.8 | Create ListCustomersTool and GetCustomerTool | 340B | | `assistant/tool/read/ListCustomersTool.java` — inject `CustomerService`. Input: optional `status` filter. Execute: return list of customer summaries (id, name, status, email). `assistant/tool/read/GetCustomerTool.java` — inject `CustomerService`. Input: `customerId` or `customerName`. Execute: find by ID or search by name. Both: no capabilities required. |
| 340.9 | Create ListTasksTool and GetMyTasksTool | 340B | | `assistant/tool/read/ListTasksTool.java` — inject `TaskService`. Input: `projectId` (required), optional `status`, optional `assigneeId`. Execute: return tasks for project. `assistant/tool/read/GetMyTasksTool.java` — inject `MyWorkService`. No input required (uses `TenantToolContext.memberId()`). Execute: return current user's tasks across all projects. Both: no capabilities required. |
| 340.10 | Create SearchEntitiesTool | 340B | | `assistant/tool/read/SearchEntitiesTool.java` — inject `ProjectService`, `CustomerService`, `TaskService`. Input: `query` (String, required). Execute: fan-out search across all three services (use their search/filter methods with the query string), merge results into a unified list with `type` field ("project"/"customer"/"task"), sort by relevance or name. No dedicated `SearchService` — the tool handles fan-out internally. `requiredCapabilities()`: empty. |
| 340.11 | Add read tools batch 1 integration tests | 340B | | `assistant/tool/read/ReadToolsBatch1Test.java` (~12 tests): ListProjectsTool returns projects, ListProjectsTool with status filter, GetProjectTool by ID, GetProjectTool by name, ListCustomersTool returns customers, GetCustomerTool by ID, ListTasksTool returns tasks for project, ListTasksTool with status filter, GetMyTasksTool returns user's tasks, SearchEntitiesTool finds project by name, SearchEntitiesTool finds customer, SearchEntitiesTool empty query returns empty. Test setup: provision tenant, create projects/customers/tasks. Pattern: follow `timeentry/TimeEntryIntegrationTest.java` for tenant-scoped test setup. |
| 340.12 | Create GetUnbilledTimeTool and GetTimeSummaryTool | 340C | | `assistant/tool/read/GetUnbilledTimeTool.java` — inject `TimeEntryService`. Input: optional `customerId`, optional `projectId`. Execute: find unbilled time entries, return total hours + total amount + entry count. `assistant/tool/read/GetTimeSummaryTool.java` — inject `TimeEntryService`. Input: optional `projectId`, optional `startDate`, optional `endDate`. Execute: return time summary (total hours, billable hours, non-billable hours). Both: no capabilities required. |
| 340.13 | Create GetProjectBudgetTool and GetProfitabilityTool | 340C | | `assistant/tool/read/GetProjectBudgetTool.java` — inject `BudgetService`. Input: `projectId` (required). Execute: return budget status (budget amount, spent, remaining, percentage). `requiredCapabilities()`: `Set.of("admin_or_owner")`. `assistant/tool/read/GetProfitabilityTool.java` — inject `ReportService`. Input: optional `projectId`, optional `customerId`. Execute: return profitability summary (revenue, cost, margin, margin %). `requiredCapabilities()`: `Set.of("admin_or_owner")`. |
| 340.14 | Create ListInvoicesTool and GetInvoiceTool | 340C | | `assistant/tool/read/ListInvoicesTool.java` — inject `InvoiceService`. Input: optional `status` filter, optional `customerId`. Execute: return list of invoice summaries (id, number, status, customer, amount, date). `assistant/tool/read/GetInvoiceTool.java` — inject `InvoiceService`. Input: `invoiceId` or `invoiceNumber`. Execute: return full invoice details. Both: `requiredCapabilities()`: `Set.of("admin_or_owner")`. |
| 340.15 | Add read tools batch 2 integration tests | 340C | | `assistant/tool/read/ReadToolsBatch2Test.java` (~10 tests): GetUnbilledTimeTool returns unbilled entries, GetUnbilledTimeTool filters by customer, GetTimeSummaryTool returns summary, GetTimeSummaryTool with date range, GetProjectBudgetTool returns status, GetProfitabilityTool by project, GetProfitabilityTool by customer, ListInvoicesTool returns invoices, ListInvoicesTool with status filter, GetInvoiceTool by ID. Test setup: provision tenant, create project + time entries + budget + invoices. |
| 340.16 | Add tool registry capability filtering integration tests | 340C | | `assistant/tool/AssistantToolRegistryIntegrationTest.java` (~5 tests): member role gets 10 tools (excludes budget, profitability, invoices, create/update project/customer, invoice draft), admin role gets all 22 tools, owner role gets all 22 tools, `getToolDefinitions("member")` returns correct ToolDefinition shapes, tool definitions have valid JSON Schema structure. Uses full Spring context with all tool beans discovered. |

### Key Files

**Slice 340A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/TenantToolContext.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantToolRegistry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetNavigationHelpTool.java`
- `backend/src/main/resources/assistant/system-guide.md`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantToolRegistryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetNavigationHelpToolTest.java`

**Slice 340B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ListProjectsTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetProjectTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ListCustomersTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetCustomerTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ListTasksTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetMyTasksTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/SearchEntitiesTool.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ReadToolsBatch1Test.java`

**Slice 340B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — Service API for ListProjectsTool
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — Service API for ListCustomersTool
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Service API for ListTasksTool
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mywork/MyWorkService.java` — Service API for GetMyTasksTool

**Slice 340C — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetUnbilledTimeTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetTimeSummaryTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetProjectBudgetTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetProfitabilityTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ListInvoicesTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/GetInvoiceTool.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/read/ReadToolsBatch2Test.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantToolRegistryIntegrationTest.java`

**Slice 340C — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Service API for time tools
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetService.java` — Service API for budget tool
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` — Service API for profitability tool
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — Service API for invoice tools

### Architecture Decisions

- **Three-slice split**: 340A is the framework (interface + registry + system guide), 340B is the first batch of 7 simpler read tools (projects, customers, tasks — well-understood service APIs), 340C is the second batch of 6 financial read tools (time, budget, profitability, invoices — more complex service APIs) plus the registry integration tests that need all tools present.
- **Tool capability strings**: Simple string-based capability matching (`"admin_or_owner"`, `"any"`) rather than a formal capability enum. This keeps tool definitions self-contained and avoids coupling to the security model.
- **SearchEntitiesTool fan-out**: No dedicated `SearchService` exists in the codebase. The tool does its own fan-out across `ProjectService`, `CustomerService`, and `TaskService`. This is acceptable because (a) the tool is the only consumer of cross-entity search, and (b) creating a service for a single tool violates YAGNI.
- **System guide as classpath resource**: Loaded from `classpath:assistant/system-guide.md` at startup. Updated by developers as part of each phase. No runtime generation.

---

## Epic 341: Assistant Service & Chat API

**Goal**: Implement the core orchestration service that assembles system prompts, invokes the LLM provider, handles read-tool execution within the streaming loop, and create the SSE controller endpoint with virtual thread `ScopedValue` re-binding.

**References**: Architecture doc Sections 11.3, 11.5 (sequence diagrams), 11.8 (SseEmitter controller pattern). [ADR-175](../adr/ADR-175-confirmation-flow-architecture.md) (SSE pause-and-resume).

**Dependencies**: Epic 338 (AiKeyEncryptionService for key decryption), Epic 339 (LlmProvider for chat), Epic 340 (tools for execution)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **341A** | 341.1-341.5 | AssistantService: system prompt assembly, LLM invocation loop, read-tool execution, token usage accumulation, ChatRequestDto | |
| **341B** | 341.6-341.10 | AssistantController with SseEmitter and virtual thread, security config, end-to-end integration tests with mock provider | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 341.1 | Create AssistantService core orchestration | 341A | | `assistant/AssistantService.java` — `@Service`. Inject: `OrgSettingsService`, `AiKeyEncryptionService`, `LlmProviderRegistry`, `AssistantToolRegistry`. Key method: `void chat(ChatRequestDto request, SseEmitter emitter)`. Steps: (1) Load OrgSettings, check `aiEnabled` + `aiProvider` not null. (2) Decrypt API key via `AiKeyEncryptionService`. (3) Assemble system prompt (system guide + tenant context + behavioral instructions). (4) Get tool definitions filtered by user role. (5) Build `ChatRequest`. (6) Call `provider.chat(request, eventConsumer)`. (7) In consumer: write each `StreamEvent` to `SseEmitter` as typed SSE events. |
| 341.2 | Implement tool execution in streaming loop | 341A | | In `AssistantService.chat()` — when the event consumer receives a `ToolUse` event: (1) Look up tool by name from `AssistantToolRegistry`. (2) If `tool.requiresConfirmation()` is false (read tool): execute immediately via `tool.execute(input, context)`. (3) Emit `tool_use` SSE event (with `requiresConfirmation: false`). (4) Emit `tool_result` SSE event with the result. (5) Feed tool result back to the LLM by re-invoking `provider.chat()` with the tool result appended to messages. (6) Continue processing events from the new invocation. Handle multi-turn: the LLM may request multiple tool calls in sequence. |
| 341.3 | Implement system prompt assembly | 341A | | In `AssistantService` — private method `assembleSystemPrompt(OrgSettings settings, ChatRequestDto request, String orgRole)`. Concatenates: (1) System guide loaded from classpath (`assistant/system-guide.md`). (2) Tenant context block: `Organization: {orgName}, User: {userName} (role: {role}), Current page: {currentPage}, Plan: {planTier}`. (3) Behavioral instructions (hardcoded): "You are the DocTeams assistant..." (5-6 instruction lines per architecture doc Section 11.3). Cache the system guide content in a field (loaded once at construction). |
| 341.4 | Implement token usage accumulation | 341A | | In `AssistantService` — accumulate `inputTokens` and `outputTokens` across multi-turn LLM invocations (tool result re-invocations). Emit a single aggregated `Usage` SSE event after the final LLM response completes (before `Done` event). |
| 341.5 | Create ChatRequestDto and response records | 341A | | `assistant/ChatRequestDto.java` — record: `(List<ChatMessageDto> messages, ChatContextDto context)`. `assistant/ChatMessageDto.java` — record: `(String role, String content)`. `assistant/ChatContextDto.java` — record: `(String currentPage)`. Used as `@RequestBody` in the controller. |
| 341.6 | Create AssistantController with SseEmitter | 341B | | `assistant/AssistantController.java` — `@RestController`, `@RequestMapping("/api/assistant")`. `@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)` returns `SseEmitter` (300s timeout). Captures `RequestScopes.TENANT_ID`, `MEMBER_ID`, `ORG_ROLE` from request thread. Submits chat to virtual thread executor (`Executors.newVirtualThreadPerTaskExecutor()`). Re-binds `ScopedValue` in virtual thread via `ScopedValue.where(...).run(...)`. Calls `assistantService.chat(request, emitter)`, then `emitter.complete()`. On exception: `emitter.completeWithError(e)`. Pattern: follow architecture doc Section 11.8 SseEmitter controller code exactly. |
| 341.7 | Add error handling for assistant endpoints | 341B | | In `AssistantService` — error cases: (1) AI not enabled or no provider configured: emit `Error` SSE event with message "AI assistant is not configured. Go to Settings > AI Assistant." (2) API key decryption fails: emit `Error` event. (3) Provider error (401, 429, timeout): emit `Error` event from provider's Error StreamEvent. (4) Tool execution exception: catch, emit `Error` event with message, continue (don't crash the stream). All errors emit the event and then complete the emitter — they do not throw. |
| 341.8 | Update SecurityConfig for assistant endpoints | 341B | | Modify `security/SecurityConfig.java` — add `/api/assistant/**` to authenticated endpoint patterns (all roles: owner, admin, member). The AI-enabled check is done at the service level, not the security config level. |
| 341.9 | Add AssistantController SSE integration tests | 341B | | `assistant/AssistantControllerTest.java` (~10 tests): POST /chat returns `text/event-stream` content type, SSE events contain expected types (text_delta, usage, done), text_delta events contain text, usage event contains token counts, error event when AI not enabled, error event when no API key configured, request without auth returns 401, read tool execution produces tool_use + tool_result events in stream, multi-turn tool execution works, system prompt contains org name and user role. Use a `TestLlmProvider` mock bean (or `@MockBean LlmProvider`) that emits predetermined events. |
| 341.10 | Add AssistantService unit tests | 341B | | `assistant/AssistantServiceTest.java` (~8 tests): system prompt includes system guide content, system prompt includes tenant context, system prompt includes behavioral instructions, token usage accumulated across multi-turn, read tool executed immediately (no confirmation), unknown tool name emits error, tool execution exception emits error (stream continues), chat with no tools definition works (text-only response). Mock `LlmProvider`, `AssistantToolRegistry`, `OrgSettingsService`, `AiKeyEncryptionService`. |

### Key Files

**Slice 341A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/ChatRequestDto.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/ChatMessageDto.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/ChatContextDto.java`

**Slice 341A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` — AI settings retrieval
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AiKeyEncryptionService.java` — Key decryption (from 338A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/provider/LlmProvider.java` — Provider interface (from 339A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/AssistantToolRegistry.java` — Tool registry (from 340A)

**Slice 341B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantControllerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantServiceTest.java`

**Slice 341B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` — Add `/api/assistant/**` pattern

**Slice 341B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — ScopedValue bindings to capture/re-bind

### Architecture Decisions

- **Virtual thread executor for SSE**: The controller returns `SseEmitter` immediately and runs the chat loop on a virtual thread. This is necessary because `ScopedValue` bindings from the request thread do not propagate to child threads — they must be explicitly captured and re-bound.
- **Read tools execute inline**: Read tools are executed immediately within the streaming loop without confirmation. The tool result is fed back to the LLM for the next response turn.
- **Error events, not exceptions**: All error conditions emit SSE `error` events and complete the emitter gracefully. The controller never throws exceptions during streaming — the client receives a structured error event.
- **System guide cached at construction**: The system guide file is read once from classpath and cached as a String field. It does not change at runtime.

---

## Epic 342: Write Tools & Confirmation Flow

**Goal**: Implement the 8 write tools that create/update entities, the `CompletableFuture`-based confirmation flow that pauses the SSE stream until the user confirms or rejects, and the confirm/reject endpoint.

**References**: Architecture doc Sections 11.3, 11.5 (Diagram 2: Write Action with Confirmation). [ADR-175](../adr/ADR-175-confirmation-flow-architecture.md) (SSE pause-and-resume with CompletableFuture).

**Dependencies**: Epic 341 (AssistantService orchestration, AssistantController)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **342A** | 342.1-342.5 | CompletableFuture confirmation map, confirm endpoint, 4 write tools (CreateProject, UpdateProject, CreateCustomer, UpdateCustomer), integration tests | |
| **342B** | 342.6-342.10 | 4 write tools (CreateTask, UpdateTask, LogTimeEntry, CreateInvoiceDraft), confirmation timeout/cleanup, full confirmation flow end-to-end tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 342.1 | Add confirmation infrastructure to AssistantService | 342A | | Modify `assistant/AssistantService.java` — add `ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations`. In the streaming loop, when a `ToolUse` event has `tool.requiresConfirmation() == true`: (1) Create `CompletableFuture<Boolean>`, store in map keyed by `toolCallId`. (2) Emit `tool_use` SSE event with `requiresConfirmation: true`. (3) Block virtual thread: `boolean approved = future.get(120, TimeUnit.SECONDS)`. (4) If approved: execute tool, emit `tool_result`, feed result to LLM. (5) If rejected: send "User cancelled this action" as tool result to LLM. (6) On timeout: emit error event, complete. (7) Always remove from map in `finally`. Add `confirm(String toolCallId, boolean approved)` method: completes the future. |
| 342.2 | Add confirm endpoint to AssistantController | 342A | | Modify `assistant/AssistantController.java` — add `@PostMapping("/chat/confirm")`. Body: `ConfirmRequestDto(String toolCallId, boolean approved)`. Delegates to `assistantService.confirm(toolCallId, approved)`. Returns `ResponseEntity.ok(Map.of("acknowledged", true))`. Returns 404 if no pending confirmation found. Create `assistant/ConfirmRequestDto.java` — record: `(String toolCallId, boolean approved)`. |
| 342.3 | Create CreateProjectTool and UpdateProjectTool | 342A | | `assistant/tool/write/CreateProjectTool.java` — inject `ProjectService`. Input: `name` (required), `customerId` (optional), `templateId` (optional). Execute: call `projectService.create(...)`. Return created project summary. `requiresConfirmation()`: true. `requiredCapabilities()`: `Set.of("admin_or_owner")`. `assistant/tool/write/UpdateProjectTool.java` — inject `ProjectService`. Input: `projectId` (required), `name` (optional), `status` (optional), `customerId` (optional). Execute: call `projectService.update(...)`. Return updated project summary. Same capabilities. |
| 342.4 | Create CreateCustomerTool and UpdateCustomerTool | 342A | | `assistant/tool/write/CreateCustomerTool.java` — inject `CustomerService`. Input: `name` (required), `email` (optional), `phone` (optional). Execute: call `customerService.create(...)`. Return created customer summary. `requiresConfirmation()`: true. `requiredCapabilities()`: `Set.of("admin_or_owner")`. `assistant/tool/write/UpdateCustomerTool.java` — inject `CustomerService`. Input: `customerId` (required), `name` (optional), `email` (optional), `phone` (optional), `status` (optional). Execute: call `customerService.update(...)`. Same capabilities. |
| 342.5 | Add write tools batch 1 + confirmation integration tests | 342A | | `assistant/tool/write/WriteToolsBatch1Test.java` (~10 tests): CreateProjectTool creates project, UpdateProjectTool updates project status, CreateCustomerTool creates customer, UpdateCustomerTool updates customer name, CreateProjectTool with invalid data throws, confirmation flow: approved creates entity, confirmation flow: rejected does not create entity, confirm endpoint with unknown toolCallId returns 404, confirm endpoint completes future, tool requires confirmation flag is true. Test setup: provision tenant, create test data. |
| 342.6 | Create CreateTaskTool and UpdateTaskTool | 342B | | `assistant/tool/write/CreateTaskTool.java` — inject `TaskService`. Input: `projectId` (required), `title` (required), `description` (optional), `assigneeId` (optional). Execute: call `taskService.create(...)`. `requiresConfirmation()`: true. `requiredCapabilities()`: empty (members can create tasks). `assistant/tool/write/UpdateTaskTool.java` — inject `TaskService`. Input: `taskId` (required), `title` (optional), `status` (optional), `assigneeId` (optional). Execute: call `taskService.update(...)`. Same capabilities. |
| 342.7 | Create LogTimeEntryTool | 342B | | `assistant/tool/write/LogTimeEntryTool.java` — inject `TimeEntryService`. Input: `taskId` (required), `hours` (required, BigDecimal), `date` (required, LocalDate), `description` (optional), `billable` (optional, boolean). Execute: call `timeEntryService.create(...)`. `requiresConfirmation()`: true. `requiredCapabilities()`: empty (members can log time). |
| 342.8 | Create CreateInvoiceDraftTool | 342B | | `assistant/tool/write/CreateInvoiceDraftTool.java` — inject `InvoiceService`. Input: `customerId` (required), `includeUnbilledTime` (optional boolean, default true). Execute: call `invoiceService.createDraft(...)`. If `includeUnbilledTime`, pull in unbilled time entries. `requiresConfirmation()`: true. `requiredCapabilities()`: `Set.of("admin_or_owner")`. |
| 342.9 | Add confirmation timeout and cleanup | 342B | | In `AssistantService` — add `@Scheduled` cleanup (or cleanup within the `finally` block of the confirmation wait). On `TimeoutException` from `future.get(120, SECONDS)`: emit error event "Confirmation timed out. Please try again." and complete the emitter. Remove expired futures from the map. Consider a `@PreDestroy` method to complete all pending futures on shutdown. |
| 342.10 | Add full confirmation flow end-to-end tests | 342B | | `assistant/ConfirmationFlowTest.java` (~8 tests): write tool emits tool_use with requiresConfirmation true, confirm approved executes tool and emits tool_result, confirm rejected emits cancellation tool_result, timeout emits error event, confirm after timeout returns 404, multiple pending confirmations handled independently, CreateTaskTool creates task after confirmation, LogTimeEntryTool logs time after confirmation. Use mock LLM provider that returns tool_use events. These tests verify the full SSE stream + confirm endpoint coordination. |

### Key Files

**Slice 342A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/ConfirmRequestDto.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/CreateProjectTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/UpdateProjectTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/CreateCustomerTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/UpdateCustomerTool.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/WriteToolsBatch1Test.java`

**Slice 342A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantService.java` — Add confirmation map + logic
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantController.java` — Add confirm endpoint

**Slice 342A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` — Create/update API
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — Create/update API

**Slice 342B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/CreateTaskTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/UpdateTaskTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/LogTimeEntryTool.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/tool/write/CreateInvoiceDraftTool.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/assistant/ConfirmationFlowTest.java`

**Slice 342B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Create/update API
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Create API
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — CreateDraft API

### Architecture Decisions

- **CompletableFuture in ConcurrentHashMap**: In-memory state is acceptable because chat sessions are ephemeral. If the server restarts, the confirmation is lost — the user simply re-asks. No persistence needed.
- **Virtual thread blocking**: The `future.get(120, SECONDS)` call blocks the virtual thread, which is cheap with Java 25 virtual threads. The carrier thread is released while waiting. No thread pool exhaustion risk.
- **Two-slice split**: 342A introduces the confirmation infrastructure + 4 project/customer write tools. 342B adds 4 more write tools (task/time/invoice) + timeout handling + end-to-end tests. This split keeps each slice under 10 files.
- **120-second timeout**: Generous timeout for user decision. On timeout, the stream emits an error event and completes. The future is removed from the map.

---

## Epic 343: Chat UI — Panel & Message Rendering

**Goal**: Build the frontend chat panel (slide-out drawer), message rendering components for all message types, floating trigger button, and integrate into the app layout.

**References**: Architecture doc Section 11.4 (Chat UI), 11.8 (frontend patterns).

**Dependencies**: Epic 341 (chat API must be available, but panel can be built with mock data initially)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **343A** | 343.1-343.6 | AssistantPanel (Sheet), AssistantTrigger, MessageList, UserMessage, AssistantMessage (react-markdown), layout integration, component tests | |
| **343B** | 343.7-343.12 | ToolUseCard, ConfirmationCard, ToolResultCard, ErrorCard, TokenUsageBadge, empty state, component tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 343.1 | Create AssistantPanel component | 343A | | `components/assistant/assistant-panel.tsx` — `"use client"`. Shadcn Sheet component anchored to right side. 420px width on desktop (`md:w-[420px]`), full-width on mobile. Header: "DocTeams Assistant" title, close button, TokenUsageBadge slot. Body: MessageList. Footer: text input with send button, Enter to send, Shift+Enter for newline, disabled while streaming. Props: `isOpen`, `onClose`, `messages`, `isStreaming`, `tokenUsage`, `onSendMessage`, `onConfirm`. Pattern: follow existing Shadcn Sheet usage in `components/mobile-sidebar.tsx`. |
| 343.2 | Create AssistantTrigger component | 343A | | `components/assistant/assistant-trigger.tsx` — `"use client"`. Floating button in bottom-right corner (`fixed bottom-6 right-6 z-50`). Sparkle/wand icon from lucide-react. Subtle design (slate-800 bg, rounded-full, shadow-lg). Only visible when `aiEnabled` is true. onClick toggles the panel open. Hide when panel is open. Props: `aiEnabled`, `onClick`. |
| 343.3 | Create MessageList component | 343A | | `components/assistant/message-list.tsx` — `"use client"`. Scrollable container with auto-scroll to bottom on new messages. Uses `useRef` + `useEffect` for scroll management. Maps over `messages` array, renders appropriate component based on message type (`user`, `assistant`, `tool_use`, `tool_result`, `confirmation`, `error`). Props: `messages: ChatMessage[]`, `isStreaming`, `onConfirm`. Define `ChatMessage` union type in `components/assistant/types.ts`. |
| 343.4 | Create UserMessage component | 343A | | `components/assistant/user-message.tsx` — right-aligned message bubble. Slate-800 bg, white text, rounded-2xl, max-width 85%. Props: `content: string`. Pattern: follow Signal Deck design — clean, sharp, slate palette. |
| 343.5 | Create AssistantMessage component | 343A | | `components/assistant/assistant-message.tsx` — `"use client"`. Left-aligned, no bubble background. Uses `react-markdown` (add dependency) to render markdown content (code blocks, lists, bold, links). Internal app links rendered as Next.js `Link` components. Streaming cursor: blinking `|` appended when `isStreaming` is true. Props: `content: string`, `isStreaming: boolean`. Add `react-markdown` to `package.json`. |
| 343.6 | Integrate trigger and panel into app layout | 343A | | Modify `app/(app)/org/[slug]/layout.tsx` — add `AssistantTrigger` and `AssistantPanel` components. The trigger and panel need to know if AI is enabled for the org — fetch `GET /api/settings/ai` in the layout and pass `aiEnabled` as prop. Create `components/assistant/assistant-provider.tsx` — `"use client"` context provider that wraps the trigger + panel state (open/closed). This avoids prop drilling. Add ~6 component tests in `components/__tests__/assistant-panel.test.tsx`: panel opens on trigger click, panel closes on close button, message input sends on Enter, input disabled while streaming, panel renders with correct width, trigger hidden when AI disabled. |

### Key Files

**Slice 343A — Create:**
- `frontend/components/assistant/assistant-panel.tsx`
- `frontend/components/assistant/assistant-trigger.tsx`
- `frontend/components/assistant/message-list.tsx`
- `frontend/components/assistant/user-message.tsx`
- `frontend/components/assistant/assistant-message.tsx`
- `frontend/components/assistant/assistant-provider.tsx`
- `frontend/components/assistant/types.ts`
- `frontend/components/__tests__/assistant-panel.test.tsx`

**Slice 343A — Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` — Add AssistantProvider + trigger
- `frontend/package.json` — Add `react-markdown` dependency

**Slice 343A — Read for context:**
- `frontend/components/mobile-sidebar.tsx` — Sheet component pattern
- `frontend/components/desktop-sidebar.tsx` — Layout integration pattern
- `frontend/app/(app)/org/[slug]/layout.tsx` — Current layout structure
- `frontend/lib/api.ts` — API client for settings fetch

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 343.7 | Create ToolUseCard component | 343B | | `components/assistant/tool-use-card.tsx` — `"use client"`. Compact card showing "Looked up {toolName}" with expand/collapse for raw data. Collapsed by default. Uses Shadcn Collapsible. Slate-100 bg, rounded-lg, subtle border. Props: `toolName: string`, `input: Record<string, unknown>`, `result?: unknown`. Shows result data when expanded. |
| 343.8 | Create ConfirmationCard component | 343B | | `components/assistant/confirmation-card.tsx` — `"use client"`. Prominent card with: tool description ("Create project"), preview of proposed data (rendered as key-value pairs), "Confirm" button (teal-600 bg) and "Cancel" button (ghost variant). Both disabled while waiting for response after click. Props: `toolCallId: string`, `toolName: string`, `input: Record<string, unknown>`, `onConfirm: (toolCallId: string, approved: boolean) => void`, `isPending: boolean`. Pattern: follow Signal Deck design — teal accent for confirm, slate ghost for cancel. |
| 343.9 | Create ToolResultCard component | 343B | | `components/assistant/tool-result-card.tsx` — card showing completed write action. Confirmed: green-tinted success card with "Created project 'Johnson Conveyancing'" and a "View" link (internal router link to the entity page). Cancelled: muted card with "Cancelled" text. Props: `toolName: string`, `result: unknown`, `approved: boolean`, `entityUrl?: string`. |
| 343.10 | Create ErrorCard component | 343B | | `components/assistant/error-card.tsx` — red-tinted card with error icon and message. Rounded-lg, `bg-red-50 border-red-200 text-red-800`. Props: `message: string`. |
| 343.11 | Create TokenUsageBadge component | 343B | | `components/assistant/token-usage-badge.tsx` — `"use client"`. Small badge in assistant panel header. Shows cumulative tokens formatted: "~1.2K tokens". Shadcn Tooltip on hover showing input vs output breakdown and estimated cost. Cost calculation: `(inputTokens * pricing.input + outputTokens * pricing.output) / 1_000_000`. Model pricing config: `const MODEL_PRICING` map (architecture doc Section 7). Props: `inputTokens: number`, `outputTokens: number`, `model: string`. |
| 343.12 | Create empty state and add component tests | 343B | | `components/assistant/empty-state.tsx` — shown when AI is not configured. Two variants: (1) Admin/owner: "AI Assistant needs an API key" + "Go to Settings > AI Assistant" link. (2) Member: "Ask your administrator to configure the AI Assistant". Props: `isAdmin: boolean`, `orgSlug: string`. Add ~8 component tests in `components/__tests__/assistant-cards.test.tsx`: ToolUseCard expands on click, ConfirmationCard calls onConfirm with approved=true, ConfirmationCard calls onConfirm with approved=false, ConfirmationCard disables buttons when pending, ToolResultCard shows success for approved, ToolResultCard shows cancelled for rejected, ErrorCard renders message, TokenUsageBadge formats tokens correctly, empty state shows settings link for admin, empty state shows ask-admin message for member. |

### Key Files

**Slice 343B — Create:**
- `frontend/components/assistant/tool-use-card.tsx`
- `frontend/components/assistant/confirmation-card.tsx`
- `frontend/components/assistant/tool-result-card.tsx`
- `frontend/components/assistant/error-card.tsx`
- `frontend/components/assistant/token-usage-badge.tsx`
- `frontend/components/assistant/empty-state.tsx`
- `frontend/components/__tests__/assistant-cards.test.tsx`

**Slice 343B — Read for context:**
- `frontend/components/ui/card.tsx` — Shadcn Card pattern
- `frontend/components/ui/badge.tsx` — Badge variants
- `frontend/components/ui/button.tsx` — Button variants (accent, ghost)
- `frontend/components/ui/tooltip.tsx` — Tooltip pattern
- `frontend/components/ui/collapsible.tsx` — Collapsible pattern (if exists, else add via Shadcn)

### Architecture Decisions

- **Two-slice split**: 343A covers the structural shell (panel, trigger, message list, basic message types, layout integration). 343B covers the specialized card components (tool cards, confirmation, error, token badge, empty state). This keeps each slice focused and testable independently.
- **AssistantProvider context**: A React context wraps the open/closed state and AI-enabled flag, avoiding prop drilling through the layout. The actual chat state (messages, streaming) lives in the `useAssistantChat` hook (Epic 344).
- **react-markdown dependency**: Required for rendering markdown in assistant responses. Lightweight, widely used. Added in 343A.
- **Types file**: `components/assistant/types.ts` defines the `ChatMessage` discriminated union type used across all assistant components. Centralized to avoid circular imports.

---

## Epic 344: Chat UI — SSE Hook, Confirmation & Settings Page

**Goal**: Implement the `useAssistantChat` hook that manages the SSE connection and streaming state, wire the confirmation flow from the frontend to the backend, and build the AI settings page for key configuration.

**References**: Architecture doc Sections 11.4, 11.8 (frontend SSE pattern).

**Dependencies**: Epic 342 (confirmation API), Epic 343 (panel and card components to wire into)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **344A** | 344.1-344.5 | useAssistantChat hook (SSE fetch, streaming state, message accumulation, confirmation dispatch), wire into AssistantPanel, component tests | |
| **344B** | 344.6-344.10 | AI settings page (provider selector, key input, model selector, test connection, enable toggle), nav-items update, server action, component tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 344.1 | Create useAssistantChat hook | 344A | | `components/assistant/use-assistant-chat.ts` — `"use client"` custom hook. State: `messages: ChatMessage[]`, `isStreaming: boolean`, `tokenUsage: { input: number, output: number }`, `pendingConfirmations: Set<string>`. Methods: `sendMessage(content: string, currentPage: string)`, `confirmToolCall(toolCallId: string, approved: boolean)`, `stopStreaming()`. `sendMessage`: appends user message to state, POSTs to `/api/assistant/chat` via `fetch()` with `ReadableStream` reader, parses SSE events from chunks using `parseSseEvents()` utility. On `text_delta`: accumulate text in current assistant message. On `tool_use`: add tool use message, add to `pendingConfirmations` if `requiresConfirmation`. On `tool_result`: update tool use message with result. On `usage`: accumulate tokens. On `done`: set `isStreaming` false. On `error`: add error message. Pattern: follow architecture doc Section 11.8 frontend SSE pattern. |
| 344.2 | Create SSE parser utility | 344A | | `components/assistant/sse-parser.ts` — `parseSseEvents(chunk: string): SseEvent[]`. Parses Server-Sent Events format: splits by double newline, extracts `event:` and `data:` fields, JSON-parses data. Handles partial chunks across multiple reads (maintains buffer). Returns typed events matching SSE event types from architecture doc Section 11.4. |
| 344.3 | Wire useAssistantChat into AssistantPanel | 344A | | Modify `components/assistant/assistant-provider.tsx` — instantiate `useAssistantChat()` hook in the provider, pass state and methods down through context. Modify `components/assistant/assistant-panel.tsx` — consume hook values from context: `messages`, `isStreaming`, `tokenUsage`, `sendMessage`, `confirmToolCall`. Wire `onConfirm` prop on `ConfirmationCard` to `confirmToolCall`. Wire input form to `sendMessage` with `currentPage` from `usePathname()`. Add "Stop" button visible during streaming that calls `stopStreaming()`. |
| 344.4 | Implement stop/abort streaming | 344A | | In `useAssistantChat` — `stopStreaming()` calls `abortController.abort()` to cancel the fetch. Sets `isStreaming` to false. The current partial assistant message is kept in state (user sees what was streamed so far). |
| 344.5 | Add useAssistantChat hook tests | 344A | | `components/__tests__/use-assistant-chat.test.tsx` (~8 tests): sendMessage adds user message to state, sendMessage sets isStreaming true, text_delta events accumulate text, tool_use event adds tool message, tool_result event updates tool message, usage event accumulates tokens, done event sets isStreaming false, confirmToolCall calls confirm endpoint, stopStreaming aborts fetch. Mock `fetch` with `ReadableStream` that emits SSE-formatted events. Pattern: use `renderHook` from `@testing-library/react`. |

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 344.6 | Create AI settings page | 344B | | `app/(app)/org/[slug]/settings/ai/page.tsx` — server component that fetches current AI settings via `GET /api/settings/ai`, renders `AiSettingsForm` client component. `components/settings/ai-settings-form.tsx` — `"use client"`. Provider selector (dropdown: "Anthropic (Claude)" enabled, "OpenAI" and "Google" disabled with "Coming soon" badge). API key input (password type, shows "Configured" with "Change" button if key exists). Model selector (dropdown populated from provider's available models — hardcode Claude models for now). Enable/disable toggle (Shadcn Switch). "Test Connection" button. "Save" button. Uses server action for save. |
| 344.7 | Create AI settings server action | 344B | | `app/(app)/org/[slug]/settings/ai/actions.ts` — `updateAiSettings(formData)` calls `PUT /api/settings/ai` via `lib/api.ts`. `testAiConnection()` calls `POST /api/settings/ai/test`. Returns success/error state. Pattern: follow existing settings actions (e.g., `app/(app)/org/[slug]/settings/rates/actions.ts` if it exists). |
| 344.8 | Add AI Assistant to settings navigation | 344B | | Modify `lib/nav-items.ts` — add "AI Assistant" item to the Settings sidebar navigation under the "Integrations" or "General" group. Route: `/settings/ai`. Icon: `Sparkles` from lucide-react. |
| 344.9 | Create settings layout integration | 344B | | Verify `app/(app)/org/[slug]/settings/ai/` directory is picked up by the settings layout. Check if there is a settings layout that needs updating for the new route. The existing `settings/layout.tsx` likely uses `nav-items.ts` to render the settings sidebar — adding the nav item in 344.8 should be sufficient. |
| 344.10 | Add AI settings page component tests | 344B | | `components/__tests__/ai-settings.test.tsx` (~8 tests): renders provider selector with Anthropic selected, renders "Coming soon" for disabled providers, shows "Configured" when key is set, shows empty input when no key, toggle reflects enabled state, save button calls updateAiSettings action, test connection button calls testAiConnection, shows success/error toast after test, model selector shows Claude models. Pattern: follow existing settings page test patterns. |

### Key Files

**Slice 344A — Create:**
- `frontend/components/assistant/use-assistant-chat.ts`
- `frontend/components/assistant/sse-parser.ts`
- `frontend/components/__tests__/use-assistant-chat.test.tsx`

**Slice 344A — Modify:**
- `frontend/components/assistant/assistant-provider.tsx` — Wire hook into context
- `frontend/components/assistant/assistant-panel.tsx` — Consume hook from context, add stop button

**Slice 344A — Read for context:**
- `frontend/lib/api.ts` — API client pattern for fetch calls
- `frontend/components/assistant/types.ts` — ChatMessage types (from 343A)
- `frontend/components/assistant/confirmation-card.tsx` — onConfirm prop interface (from 343B)

**Slice 344B — Create:**
- `frontend/app/(app)/org/[slug]/settings/ai/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/ai/actions.ts`
- `frontend/components/settings/ai-settings-form.tsx`
- `frontend/components/__tests__/ai-settings.test.tsx`

**Slice 344B — Modify:**
- `frontend/lib/nav-items.ts` — Add AI Assistant settings nav item

**Slice 344B — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` — Existing settings page pattern (if exists)
- `frontend/app/(app)/org/[slug]/settings/layout.tsx` — Settings layout structure
- `frontend/components/ui/switch.tsx` — Toggle component
- `frontend/components/ui/select.tsx` — Select/dropdown component

### Architecture Decisions

- **fetch + ReadableStream, not EventSource**: `EventSource` does not support POST requests. The chat endpoint is POST (sends message history in body). We use `fetch()` with `response.body.getReader()` to read the SSE stream. A custom `parseSseEvents()` utility handles SSE format parsing.
- **SSE parser as separate utility**: Extracted into `sse-parser.ts` for testability. Handles chunk buffering for partial SSE events split across reads.
- **Settings page as separate slice**: The settings page is independent of the chat UI — it only needs the settings API from Epic 338. Split from 344A for file count and scope management.
- **Hardcoded model list**: Claude model options are hardcoded in the frontend form for v1. When more providers are added, the model list can be fetched from the backend (`availableModels()` endpoint).
- **Context provider for chat state**: The `useAssistantChat` hook lives in `AssistantProvider`, which wraps the layout. Components consume state via context. This avoids prop drilling and keeps the panel/trigger components pure.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` - Entity to extend with AI provider/key/model fields
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` - Service to extend with AI settings methods
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` - Add assistant endpoint patterns
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/layout.tsx` - Layout integration for assistant trigger and panel
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/nav-items.ts` - Settings navigation for AI Assistant entry