# Phase 52 — In-App AI Assistant (BYOAK)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 51 phases of functionality. The platform has a vertical architecture (Phase 49) with module guards, profile system, and tenant-gated modules. The platform serves professional services firms (accounting, legal, consulting) with time tracking, invoicing, project management, document generation, customer lifecycle, resource planning, workflow automation, and more.

**The existing infrastructure that this phase builds on**:
- **Integration ports** (Phase 21): `OrgIntegration` entity with `IntegrationDomain` enum (includes `AI`), `OrgIntegrationRepository`, `IntegrationGuardService.requireEnabled(AI)`. The `IntegrationDomain.AI` domain exists with a `"noop"` default slug. `OrgSettings.aiEnabled` boolean flag (from V36) controls the toggle.
- **Secret storage** (Phase 21): `SecretStore` interface with `EncryptedDatabaseSecretStore` implementation. `OrgSecret` entity stores AES-256-GCM encrypted secrets per tenant. Methods: `store(key, plaintext)`, `retrieve(key)`, `delete(key)`, `exists(key)`. Tenant-scoped via schema isolation.
- **Existing AI provider port** (Phase 21): `AiProvider` interface with `generateText()`, `summarize()`, `suggestCategories()`. This is for one-shot text generation, NOT conversational chat. `NoOpAiProvider` is the default. The new conversational AI assistant requires a separate interface for multi-turn streaming chat with tool use.
- **Capability-based RBAC** (Phase 46): `@RequiresCapability("...")` annotation with `CapabilityAuthorizationService`. Existing capabilities: `FINANCIAL_VISIBILITY`, `INVOICING`, `PROJECT_MANAGEMENT`, `TEAM_OVERSIGHT`, `CUSTOMER_MANAGEMENT`, `AUTOMATIONS`, `RESOURCE_PLANNING`, `MANAGE_COMPLIANCE`, `MANAGE_COMPLIANCE_DESTRUCTIVE`. Frontend: `useCapabilities()` hook, `RequiresCapability` component, `CapabilityProvider`.
- **Plan enforcement** (Phase 2, extended): `Tier` enum (STARTER, PRO). `PlanSyncService` tracks tier per org. Features can be gated by plan tier.
- **Integration settings UI** (Phase 21): `IntegrationCard` component on the Settings → Integrations page. Already has an "AI Assistant" card with provider selector, API key input, enable toggle, and test connection. Domain config for AI exists in the frontend.
- **ScopedValue multitenancy** (throughout): `RequestScopes.TENANT_ID`, `MEMBER_ID`, `ORG_ROLE`, `CAPABILITIES`. All bound by `MemberFilter` during request processing. Virtual threads require explicit re-binding of `ScopedValue`.
- **Navigation zones** (Phase 44): Zone-based sidebar (`Work`, `Delivery`, `Clients`, `Finance`, `Team & Resources`). `NavItem` with optional `requiredCapability` and `requiredModule`. Settings sidebar with 24+ items.
- **Settings layout** (Phase 44): `SettingsLayoutShell` with sidebar navigation. Settings items defined in `SETTINGS_ITEMS` array in `lib/nav-items.ts`.
- **Service layer**: All domain services (`ProjectService`, `CustomerService`, `TaskService`, `TimeEntryService`, `InvoiceService`, `BudgetService`, `ReportService`, `MyWorkService`, etc.) expose methods that tools can delegate to.

**The problem**: DocTeams has 51 phases of functionality — time tracking, invoicing, proposals, document templates, automations, rate cards, budgets, compliance checklists, and more. Non-technical users at professional services firms don't discover or use most of it. The command palette (Phase 44) helps power users navigate, but doesn't help someone who doesn't know what they're looking for. The product needs an accessibility layer that turns its depth into a differentiator rather than a barrier.

**The fix**: An in-app AI assistant that acts as a "system expert" — it knows what DocTeams can do, can query tenant data, and can perform actions on behalf of the user (with explicit confirmation). The assistant follows a Bring Your Own API Key (BYOAK) model where each tenant provides their own Anthropic API key. Premium-tier feature. Claude-first with provider abstraction for future expansion.

## Objective

1. **Conversational AI assistant** — A slide-out chat panel accessible from any page. The assistant can answer "how do I..." questions with context-aware navigation guidance, explain features, and walk users through workflows. It knows the user's role, current page, and org configuration.

2. **Data-aware read tools** — The assistant can query tenant data on behalf of the user: list projects, look up customer details, check unbilled time, view budget status, find invoices. All queries respect the user's role and capabilities. Results are returned inline in the conversation.

3. **Reversible write actions with confirmation** — The assistant can create and update entities (projects, customers, tasks, time entries, invoice drafts) but ONLY after showing the user exactly what will be created/changed and receiving explicit confirmation. Every write action is reversible (create → delete, update → undo). The assistant NEVER sends emails, generates documents for external delivery, or performs destructive actions (delete, archive, close).

4. **BYOAK key management** — Tenants configure their own Anthropic API key via the existing Integrations settings page. Keys are stored encrypted using the existing `SecretStore` infrastructure. The feature is gated behind the PRO tier.

5. **Streaming responses** — All assistant responses stream via Server-Sent Events (SSE). The user sees text appearing in real-time. Tool executions appear as compact cards within the conversation. Token usage is tracked and displayed.

## Constraints & Assumptions

- **BYOAK only for v1.** Each tenant provides their own Anthropic API key. No platform-managed key. No cost subsidization. Future: premium tier with platform-provided key and usage billing.
- **Claude-first, provider-agnostic interface.** The backend defines a `LlmChatProvider` interface for streaming chat with tool use. Only the Anthropic Claude adapter is implemented in v1. The interface is clean enough to add OpenAI or Google adapters later without changing the assistant service.
- **Ephemeral sessions.** Chat history lives in frontend state only. No server-side persistence of conversations. Refreshing the page clears the conversation. This is intentional — the assistant is a workflow accelerator, not a knowledge base.
- **Reversible actions only.** Write tools can create and update entities. They CANNOT: delete entities, send emails, send documents for acceptance, close/archive projects, change member roles, modify billing/payment settings, or perform any action visible to external parties (clients, portal contacts). The litmus test: "Can the user undo this with one click in the UI?"
- **Confirmation before every write.** Every write action pauses the SSE stream and shows a preview card with the exact data that will be created/changed. The user must explicitly confirm or reject. No implicit writes.
- **PRO tier only.** The AI assistant is a premium feature gated by plan tier. STARTER tenants see the toggle but get a "Upgrade to PRO" prompt. The existing `IntegrationGuardService` + `OrgSettings.aiEnabled` handle the feature toggle; plan enforcement is added on top.
- **Cross-vertical.** The assistant works identically across all vertical profiles (accounting, legal, consulting). It's NOT module-gated. The tool set is the same regardless of vertical — tools that query entities the tenant doesn't use simply return empty results.
- **Static system guide.** A manually-written markdown document (~3-5K tokens) describing DocTeams' features, navigation, and common workflows. Loaded from classpath at startup, cached in memory. Updated by developers as part of each phase. Not runtime-generated.
- **Core tool set to start.** The initial tool set covers the most-used entities: projects, customers, tasks, time entries, invoices, budgets, and profitability. Additional tools (expenses, retainers, proposals, schedules, automations, deadlines, resource allocations) can be added in a follow-up phase. The tool framework makes adding new tools trivial (one Spring `@Component` per tool).
- **No streaming dependency (WebFlux).** The backend uses Spring MVC's `SseEmitter` with virtual threads. No WebFlux/reactive dependency. The `LlmChatProvider.chat()` method uses `Consumer<StreamEvent>` callbacks, not `Flux`. This keeps the interface framework-agnostic.

---

## Section 1 — LLM Chat Provider Abstraction

### 1.1 LlmChatProvider Interface

A new interface separate from the existing `AiProvider` (which handles one-shot text generation). This interface handles multi-turn conversational chat with streaming and tool use.

```
LlmChatProvider
  + providerId(): String                    — "anthropic", "openai", etc.
  + chat(ChatRequest, Consumer<StreamEvent>): void   — blocks until complete, pushes events
  + validateKey(apiKey, model): boolean     — test API key validity
  + availableModels(): List<ModelInfo>      — supported models for this provider
```

**`ChatRequest`**: API key, model ID, system prompt, message history, tool definitions.

**`StreamEvent`** (sealed interface): `TextDelta(text)`, `ToolUse(toolCallId, toolName, input)`, `Usage(inputTokens, outputTokens)`, `Done()`, `Error(message)`.

The `chat()` method blocks the calling (virtual) thread and pushes `StreamEvent` instances to the consumer as they arrive from the LLM API. The consumer writes each event to an `SseEmitter`. This pattern avoids any reactive/WebFlux dependency.

### 1.2 Anthropic Claude Adapter

Implements `LlmChatProvider` for the Anthropic Messages API:
- POSTs to `/v1/messages` with `stream: true`
- Parses SSE stream: `content_block_delta` → `TextDelta`, `content_block_start` (type=tool_use) → `ToolUse`, `message_delta` → `Usage`, `message_stop` → `Done`
- Uses Spring's `RestClient` with manual SSE parsing (no SDK dependency needed, keeps it lightweight)
- `validateKey()`: sends a minimal 1-token completion, checks for 200 vs 401/403
- `availableModels()`: returns static list (Claude has no list-models API): `claude-sonnet-4-6` (recommended), `claude-opus-4-6`, `claude-haiku-4-5`

### 1.3 Provider Registry

`LlmChatProviderRegistry` — `@Component`, auto-discovers all `LlmChatProvider` beans. Lookup by `providerId()`. Only the Anthropic provider exists in v1.

---

## Section 2 — API Key Management & Feature Gating

### 2.1 Key Storage

Use the existing `SecretStore` infrastructure. The AI API key is stored as an `OrgSecret` with a well-known key (e.g., `ai.api-key`). This reuses the existing AES-256-GCM encryption, per-tenant isolation, and key rotation infrastructure.

**No new entity or migration for key storage.** The `org_secrets` table already exists (V36). The `OrgIntegration` record for the `AI` domain tracks the provider slug, enabled state, and key suffix (last 6 chars for display). This record already exists from Phase 21.

### 2.2 Settings Flow

The existing Integrations settings page already has an "AI Assistant" card (`IntegrationCard` component with `IntegrationDomain.AI`). The existing flow:
1. User selects provider (e.g., "Anthropic")
2. User enters API key → stored via `SecretStore`, key suffix saved on `OrgIntegration`
3. User toggles enabled → updates `OrgIntegration.enabled` + `OrgSettings.aiEnabled`
4. User clicks "Test Connection" → calls `LlmChatProviderRegistry.get(slug).validateKey()`

**What needs to be added/modified:**
- Wire the "Test Connection" button to the new `LlmChatProvider.validateKey()` (currently uses `AiProvider.testConnection()`)
- Add a model selector dropdown to the integration card (populated from `availableModels()`)
- Store selected model in `OrgIntegration.configJson` (e.g., `{"model": "claude-sonnet-4-6"}`)
- Add PRO tier check: if tenant is STARTER, show upgrade prompt instead of configuration

### 2.3 Plan Tier Gating

The AI assistant is a PRO-tier feature:
- **Backend**: Check plan tier in `AssistantService` before processing chat requests. Return a clear error if STARTER tier.
- **Frontend**: The `AssistantTrigger` (floating button) is hidden for STARTER tenants. The integration card on the settings page shows a "PRO" badge and upgrade prompt.
- The existing `OrgSettings.aiEnabled` boolean remains the per-org toggle. The plan tier is an additional prerequisite.

### 2.4 Audit Events

- `ai_settings.updated` — when provider/model/enabled changes (log provider + model, NEVER log key material)
- `ai_chat.session` — when a chat session is initiated (log user, model, total tokens used at session end)

---

## Section 3 — Tool Framework

### 3.1 AssistantTool Interface

```
AssistantTool
  + name(): String                           — tool name for LLM (e.g., "list_projects")
  + description(): String                    — tool description for LLM
  + inputSchema(): Map<String, Object>       — JSON Schema for input parameters
  + requiresConfirmation(): boolean          — true for write tools
  + requiredCapabilities(): Set<String>      — capabilities needed (empty = all roles)
  + execute(input, TenantToolContext): Object — execute and return result
```

**`TenantToolContext`**: `tenantId`, `memberId`, `orgRole`, `capabilities` (Set<String>). Built from `RequestScopes` values before tool execution.

### 3.2 Tool Registry

`AssistantToolRegistry` — auto-discovers all `AssistantTool` beans. Filters tools by user capabilities using `CapabilityAuthorizationService` logic (not ad-hoc string matching). Methods:
- `getToolsForUser(capabilities)` — returns tools the user can access
- `getToolDefinitions(capabilities)` — returns LLM-formatted tool definitions
- `getTool(name)` — lookup by name

### 3.3 Read Tools (Initial Set)

All read tools: `requiresConfirmation() = false`, execute inline during streaming.

| Tool | Service Dependency | Capability Required | Input | Output |
|------|--------------------|---------------------|-------|--------|
| `get_navigation_help` | System guide (classpath) | None | `feature` (String) | Navigation instructions |
| `list_projects` | `ProjectService` | None | Optional: `status` | Project summaries |
| `get_project` | `ProjectService` | None | `projectId` or `projectName` | Project details |
| `list_customers` | `CustomerService` | None | Optional: `status` | Customer summaries |
| `get_customer` | `CustomerService` | None | `customerId` or `customerName` | Customer details |
| `list_tasks` | `TaskService` | None | `projectId`, optional: `status`, `assigneeId` | Task list |
| `get_my_tasks` | `MyWorkService` | None | None (uses context `memberId`) | User's tasks across projects |
| `search_entities` | Multiple services | None | `query` (String) | Cross-entity search results |
| `get_unbilled_time` | `TimeEntryService` | `FINANCIAL_VISIBILITY` | Optional: `customerId`, `projectId` | Unbilled hours + amount |
| `get_time_summary` | `TimeEntryService` | None | Optional: `projectId`, `startDate`, `endDate` | Hours breakdown |
| `get_project_budget` | `BudgetService` | `FINANCIAL_VISIBILITY` | `projectId` | Budget status |
| `get_profitability` | `ReportService` | `FINANCIAL_VISIBILITY` | Optional: `projectId`, `customerId` | Profitability summary |
| `list_invoices` | `InvoiceService` | `INVOICING` | Optional: `status`, `customerId` | Invoice summaries |
| `get_invoice` | `InvoiceService` | `INVOICING` | `invoiceId` or `invoiceNumber` | Invoice details |

### 3.4 Write Tools (Initial Set)

All write tools: `requiresConfirmation() = true`, stream pauses until user confirms.

| Tool | Service Dependency | Capability Required | Input | What It Creates/Updates |
|------|--------------------|---------------------|-------|------------------------|
| `create_project` | `ProjectService` | `PROJECT_MANAGEMENT` | `name`, optional: `customerId`, `templateId` | New project |
| `update_project` | `ProjectService` | `PROJECT_MANAGEMENT` | `projectId`, optional: `name`, `status`, `customerId` | Updated project |
| `create_customer` | `CustomerService` | `CUSTOMER_MANAGEMENT` | `name`, optional: `email`, `phone` | New customer |
| `update_customer` | `CustomerService` | `CUSTOMER_MANAGEMENT` | `customerId`, optional: `name`, `email`, `phone`, `status` | Updated customer |
| `create_task` | `TaskService` | None | `projectId`, `title`, optional: `description`, `assigneeId` | New task |
| `update_task` | `TaskService` | None | `taskId`, optional: `title`, `status`, `assigneeId` | Updated task |
| `log_time_entry` | `TimeEntryService` | None | `taskId`, `hours`, `date`, optional: `description`, `billable` | New time entry |
| `create_invoice_draft` | `InvoiceService` | `INVOICING` | `customerId`, optional: `includeUnbilledTime` | Draft invoice |

### 3.5 System Guide

A static markdown file (`classpath:assistant/system-guide.md`, ~3-5K tokens) describing:
- **Navigation structure** — the 5 sidebar zones and their pages
- **Page descriptions** — 1-2 sentences per page describing what it does
- **Common workflows** — 5-8 step-by-step workflows (new client engagement, generating an invoice, logging time, setting up rate cards, running profitability reports, creating a project from template, managing team members)
- **Terminology** — domain terms mapped to UI concepts (matter = project, WIP = unbilled time, etc.)

Loaded once at startup, cached in memory. Updated by developers as part of each phase.

---

## Section 4 — Assistant Service & Chat API

### 4.1 Assistant Service (Orchestration)

`AssistantService` — the core orchestration layer:

1. **Pre-flight checks**: Verify PRO tier, AI enabled, provider configured, key exists.
2. **Key retrieval**: Load API key from `SecretStore` via `secretStore.retrieve("ai.api-key")`.
3. **System prompt assembly**: Concatenate system guide + tenant context (org name, user name, role, current page, plan tier, vertical profile) + behavioral instructions ("You are the DocTeams assistant...").
4. **Tool definition filtering**: Get tool definitions filtered by user's capabilities.
5. **LLM invocation**: Call `provider.chat(request, eventConsumer)`.
6. **Event routing**: The consumer receives `StreamEvent` instances and writes them to the `SseEmitter`:
   - `TextDelta` → emit as `text_delta` SSE event
   - `ToolUse` with `requiresConfirmation = false` → execute tool immediately, emit `tool_use` + `tool_result`, feed result back to LLM (multi-turn)
   - `ToolUse` with `requiresConfirmation = true` → emit `tool_use` with `requiresConfirmation: true`, pause stream (block virtual thread on `CompletableFuture`), wait for user confirmation
   - `Usage` → accumulate tokens
   - `Done` → emit aggregated usage, complete emitter
   - `Error` → emit error event, complete emitter

### 4.2 Confirmation Flow

When a write tool is requested:
1. Store `CompletableFuture<Boolean>` in `ConcurrentHashMap` keyed by `toolCallId`
2. Emit SSE event with tool details + `requiresConfirmation: true`
3. Block virtual thread: `future.get(120, TimeUnit.SECONDS)` — cheap with Java 25 virtual threads
4. If confirmed: execute tool, emit `tool_result`, feed result to LLM
5. If rejected: send "User cancelled this action" as tool result to LLM
6. On timeout: emit error event, complete emitter
7. Always remove from map in `finally`

**Confirm endpoint**: `POST /api/assistant/chat/confirm` with `{ toolCallId, approved }`. Completes the corresponding `CompletableFuture`.

### 4.3 Chat Controller

`AssistantController`:
- `POST /api/assistant/chat` → returns `SseEmitter` (300s timeout)
- Captures `RequestScopes` values from request thread
- Submits chat to virtual thread executor (`Executors.newVirtualThreadPerTaskExecutor()`)
- Re-binds `ScopedValue` in virtual thread via `ScopedValue.where(...).run(...)`
- `POST /api/assistant/chat/confirm` → confirmation endpoint

**Error handling**: All errors emit SSE events and complete the emitter gracefully. The controller never throws during streaming.

---

## Section 5 — Frontend Chat UI

### 5.1 Chat Panel (Sheet)

`AssistantPanel` — Shadcn Sheet anchored to right side, 420px on desktop, full-width on mobile:
- **Header**: "DocTeams Assistant" title, close button, token usage badge
- **Body**: Scrollable message list with auto-scroll to bottom
- **Footer**: Text input with send button, Enter to send, Shift+Enter for newline, disabled while streaming, "Stop" button during streaming

### 5.2 Floating Trigger

`AssistantTrigger` — Fixed button in bottom-right corner (`fixed bottom-6 right-6 z-50`). Sparkle icon. Only visible when AI is enabled AND tenant is PRO tier. Hidden when panel is open.

### 5.3 Message Components

| Component | Purpose |
|-----------|---------|
| `UserMessage` | Right-aligned bubble, user's input |
| `AssistantMessage` | Left-aligned, markdown-rendered via `react-markdown`. Streaming cursor while typing |
| `ToolUseCard` | Compact card: "Looked up {tool}" with expand/collapse for raw data |
| `ConfirmationCard` | Prominent card with data preview, "Confirm" (teal) and "Cancel" (ghost) buttons |
| `ToolResultCard` | Success (green) or cancelled (muted) result card with "View" link to entity |
| `ErrorCard` | Red-tinted error card with message |
| `TokenUsageBadge` | Small badge showing "~1.2K tokens" with cost tooltip |
| `EmptyState` | "AI not configured" with settings link (admin) or "ask your admin" (member) |

### 5.4 useAssistantChat Hook

Custom hook managing the SSE connection and conversation state:
- State: `messages`, `isStreaming`, `tokenUsage`, `pendingConfirmations`
- `sendMessage(content, currentPage)` — POSTs to `/api/assistant/chat`, reads SSE stream via `fetch()` + `ReadableStream`
- `confirmToolCall(toolCallId, approved)` — POSTs to `/api/assistant/chat/confirm`
- `stopStreaming()` — aborts fetch via `AbortController`

### 5.5 SSE Parser Utility

`parseSseEvents(chunk)` — parses SSE format (splits by double newline, extracts `event:` + `data:` fields, JSON-parses data). Handles partial chunks across reads with a buffer.

### 5.6 Layout Integration

- `AssistantProvider` context wraps the app layout — manages open/closed state + AI-enabled flag
- `AssistantTrigger` + `AssistantPanel` added to `app/(app)/org/[slug]/layout.tsx`
- AI-enabled check: fetch `GET /api/settings/ai` (or use existing org settings fetch) in layout

---

## Section 6 — Settings UI Modifications

### 6.1 Integration Card Enhancement

The existing `IntegrationCard` for the AI domain needs:
- **Model selector**: Dropdown populated from `LlmChatProvider.availableModels()`. Stored in `OrgIntegration.configJson.model`. Default: `claude-sonnet-4-6`.
- **PRO badge**: Show "PRO" badge on the card. If STARTER tier, show upgrade prompt instead of configuration controls.
- **Test connection wiring**: The "Test Connection" button should call the new `LlmChatProvider.validateKey()` endpoint instead of the old `AiProvider.testConnection()`.

### 6.2 AI Settings in Nav

Add "AI Assistant" to `SETTINGS_ITEMS` in `lib/nav-items.ts`. Links to the existing integrations page (no separate page needed — the AI card is already there). Alternatively, if the AI settings grow complex enough, create a dedicated settings page. For v1, the integration card is sufficient.

---

## Section 7 — API Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/assistant/chat` | Start chat session (returns SSE stream) |
| `POST` | `/api/assistant/chat/confirm` | Confirm or reject a write action |
| `GET` | `/api/settings/integrations/ai/models` | List available models for configured provider |
| `POST` | `/api/settings/integrations/ai/test` | Test API key validity |

Existing endpoints used (no modification needed):
- `GET/PUT /api/settings/integrations` — integration CRUD (already exists)
- All domain service endpoints — tools call services directly, not HTTP endpoints

---

## Section 8 — Testing

### 8.1 Backend Tests

| Test | What it verifies |
|------|-----------------|
| `LlmChatProviderRegistryTest` | Registry discovers providers, lookup by ID works, unknown ID throws |
| `AnthropicLlmProviderTest` | SSE parsing: TextDelta events from `content_block_delta` |
| `AnthropicLlmProviderTest` | SSE parsing: ToolUse from `content_block_start` (type=tool_use) |
| `AnthropicLlmProviderTest` | SSE parsing: Usage from `message_delta` |
| `AnthropicLlmProviderTest` | SSE parsing: Done from `message_stop` |
| `AnthropicLlmProviderTest` | validateKey returns true on 200, false on 401 |
| `AnthropicLlmProviderTest` | Rate limit (429) emits Error event |
| `AssistantToolRegistryTest` | Discovers tools, filters by capability, returns correct tool definitions |
| `ReadToolsBatchTest` | Each read tool delegates to correct service and returns formatted results |
| `ReadToolsBatchTest` | Financial tools require `FINANCIAL_VISIBILITY` capability |
| `ReadToolsBatchTest` | Invoice tools require `INVOICING` capability |
| `AssistantServiceTest` | System prompt includes guide + tenant context + behavioral instructions |
| `AssistantServiceTest` | Read tool executed inline (no confirmation) |
| `AssistantServiceTest` | Token usage accumulated across multi-turn |
| `AssistantServiceTest` | Error emitted when AI not enabled |
| `AssistantServiceTest` | Error emitted when STARTER tier |
| `AssistantControllerTest` | POST /chat returns text/event-stream |
| `AssistantControllerTest` | SSE events contain expected types |
| `AssistantControllerTest` | Request without auth returns 401 |
| `WriteToolsBatchTest` | Each write tool creates/updates entity after confirmation |
| `ConfirmationFlowTest` | Approved confirmation executes tool |
| `ConfirmationFlowTest` | Rejected confirmation sends cancellation to LLM |
| `ConfirmationFlowTest` | Timeout emits error event |
| `ConfirmationFlowTest` | Confirm after timeout returns 404 |

### 8.2 Frontend Tests

| Test | What it verifies |
|------|-----------------|
| Assistant panel | Opens on trigger click, closes on close button |
| Assistant panel | Message input sends on Enter, disabled while streaming |
| Assistant panel | Trigger hidden when AI disabled or STARTER tier |
| Message rendering | AssistantMessage renders markdown correctly |
| Message rendering | ToolUseCard expands/collapses |
| Confirmation flow | ConfirmationCard calls onConfirm with approved=true |
| Confirmation flow | ConfirmationCard calls onConfirm with approved=false |
| Confirmation flow | Buttons disabled while pending |
| Token badge | Formats tokens correctly, tooltip shows breakdown |
| Empty state | Shows settings link for admin, ask-admin for member |
| useAssistantChat hook | sendMessage adds user message, sets streaming |
| useAssistantChat hook | SSE events accumulate text, handle tool results |
| useAssistantChat hook | stopStreaming aborts fetch |
| SSE parser | Parses complete events, handles partial chunks |

---

## Out of Scope

- **Server-side chat history.** Sessions are ephemeral. No persistence. No conversation recall. This is a deliberate v1 simplification.
- **Multiple providers.** Only Anthropic Claude in v1. The interface supports future providers but no OpenAI/Google adapter is built.
- **Platform-managed API key.** No Anthropic key provisioned by the platform. No usage-based billing. All BYOAK.
- **Document drafting.** The assistant cannot compose documents in the Tiptap editor or modify document content. That's Layer 2 (future).
- **Delete/destructive actions.** No tools that delete entities, archive projects, close invoices, or perform irreversible operations.
- **External-facing actions.** No sending emails, no sending documents for acceptance, no portal notifications. The assistant operates within the firm's internal workspace only.
- **Advanced tool coverage.** Expenses, retainers, proposals, recurring schedules, automations, deadlines, resource allocations, billing runs, and custom fields are NOT in the initial tool set. Each is a straightforward addition (one `@Component` per tool) and can be added incrementally.
- **Conversation context injection.** The assistant doesn't automatically see the user's current page data (e.g., the project they're viewing). It receives the current page path as context but must use tools to look up data. Page-aware context injection is a v2 enhancement.
- **Rate limiting.** No per-user or per-tenant rate limiting on chat requests. The BYOAK model means the tenant's own API key handles rate limits. Platform-level rate limiting can be added if abuse becomes an issue.
- **Streaming audio/voice.** Text-only interface.

## ADR Topics

- **Separate interface from existing AiProvider** — why `LlmChatProvider` is a new interface rather than extending `AiProvider`. The existing `AiProvider` handles one-shot text operations (summarize, categorize). The new interface handles multi-turn streaming chat with tool use. They serve fundamentally different purposes and coexist.
- **SecretStore reuse vs. separate encryption** — why the AI API key uses the existing `SecretStore`/`OrgSecret` infrastructure rather than a dedicated `AiKeyEncryptionService`. Single encryption implementation to maintain, existing key rotation support, tenant-scoped by schema isolation.
- **Consumer callback vs. Reactive Streams** — why `Consumer<StreamEvent>` instead of `Flux<StreamEvent>`. Avoids WebFlux dependency, keeps the interface framework-agnostic, matches Spring MVC's `SseEmitter` pattern.
- **CompletableFuture confirmation** — why in-memory `CompletableFuture` is acceptable for the confirmation flow. Chat sessions are ephemeral. If the server restarts, the user simply re-asks. No persistence needed.
- **Virtual thread ScopedValue re-binding** — why the SSE controller must explicitly capture and re-bind `ScopedValue` in the virtual thread. `ScopedValue` bindings don't propagate to child threads.

## Style & Boundaries

- The assistant panel follows the existing Sheet pattern from `mobile-sidebar.tsx`. Dark slate palette matching the app shell.
- `react-markdown` for rendering assistant responses. No custom markdown parser.
- The `assistant/` package is a top-level feature package (like `deadline/`, `automation/`, `retainer/`). Sub-packages: `provider/`, `provider/anthropic/`, `tool/`, `tool/read/`, `tool/write/`.
- Tools are thin delegation layers — 20-40 lines each. They call existing service methods, format the result, and return. No business logic in tools.
- WireMock for Anthropic API integration tests. No real API calls in CI.
- The system guide is maintained as a single markdown file. It's small enough (~3-5K tokens) that splitting it adds complexity without benefit.
- The floating trigger button and chat panel are the only UI additions to the main layout. No sidebar nav item for the assistant — it's always accessible via the trigger.
