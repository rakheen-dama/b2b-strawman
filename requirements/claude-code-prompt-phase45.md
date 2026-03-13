# Phase 45 — In-App AI Assistant (BYOAK)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After 44 phases, the platform has extensive functionality: projects, tasks, time tracking, customers, invoicing, rate cards, budgets, profitability dashboards, document templates, comments, notifications, audit trails, customer portal, tags, custom fields, proposals, workflow automations, resource planning, compliance, recurring work, and more — spread across 77+ pages with 6 sidebar navigation zones and 23 settings.

**The problem**: The platform is feature-rich but the surface area creates a power gap. Non-technical users — bookkeepers, legal secretaries, junior associates — won't discover half the functionality through navigation alone. Setting up rate cards, configuring templates, understanding profitability reports, or knowing the right workflow sequence requires expertise that new users don't have. The product needs an accessibility layer that meets users where they are: natural language.

**The fix**: An in-app AI assistant that knows the system deeply. Users bring their own API key (BYOAK). The assistant can answer "how do I..." questions with context-aware guidance, execute reversible actions on the user's behalf (create projects, log time, update customers), and query tenant data to surface answers ("what's my unbilled time for Acme?"). The assistant inherits the user's permissions and operates within the existing tenant security boundary.

## Objective

1. **BYOAK key management** — tenants store an encrypted LLM API key in OrgSettings. Claude (Anthropic) is the first supported provider. The backend interface is provider-agnostic so OpenAI/Gemini adapters can be added without rearchitecting.
2. **Conversational assistant UI** — a slide-out drawer panel on the right side of the app. Session-persistent (no server-side chat history). Messages stream via SSE. Tool executions render as confirmation cards before executing and result cards after.
3. **System knowledge** — the assistant has a static system guide document describing all pages, features, navigation paths, and common workflows. This guide is a build-time artifact, refreshable via a dev skill.
4. **Tool execution** — ~20 tools mapping to existing backend API endpoints. Read queries (list, search, details) and reversible write actions (create, update). Write actions require user confirmation before execution. No destructive or irreversible actions (no delete, no send, no email).
5. **Token usage visibility** — each conversation displays cumulative token usage so tenants can manage their own API costs.

## Constraints & Assumptions

- **BYOAK only.** No platform-managed API key. If a tenant hasn't configured a key, the assistant panel shows a setup prompt linking to settings. No free tier, no trial tokens.
- **Claude first, abstraction always.** The backend defines a provider-agnostic `LlmProvider` interface. The Claude adapter is the only implementation in this phase. The interface must support: chat with tools, streaming responses, and token counting. Adding a new provider means implementing the interface — no changes to the assistant service or tool layer.
- **Server-side LLM calls.** The frontend never sees the API key. All LLM communication happens on the Spring Boot backend. The backend is both the LLM client and the tool executor — tools call internal services directly (not HTTP endpoints), inheriting the request's tenant scope.
- **User permissions inherited.** The assistant operates with the same permissions as the authenticated user. If a member can't create invoices, neither can the assistant acting on their behalf. Tool definitions are filtered by the user's capabilities at conversation start.
- **Session-scoped only.** No server-side chat history entity for v1. Messages live in frontend state, cleared on page refresh or logout. This avoids a new database entity and keeps the scope tight. Conversation context is maintained via the LLM's context window within a session.
- **Reversible actions only.** The assistant can create and update entities but cannot delete, send, email, or perform any irreversible action. The line is: "can the user undo this with one click in the UI?" If yes, the assistant can do it.
- **Confirmation before write.** Every write action shows a preview card with the proposed changes. The user clicks "Confirm" or "Cancel." The assistant does not execute write actions without explicit approval.
- **Static system guide.** The assistant's knowledge of the app comes from a markdown document (`assistant/system-guide.md` or similar) that describes pages, features, navigation, and workflows. This is NOT generated at runtime. It is refreshed by a dev skill (`/refresh-ai-guide`) that crawls route files, nav config, and component structure.
- **No document intelligence.** Uploading documents, extracting data from PDFs, or composing templates via AI is out of scope (Layer 2, future phase).
- **No time narrative polish or smart grouping.** The drudgery-removal features (Layer 3) are a follow-up phase. They may become assistant-invocable commands later.
- **SSE streaming.** Responses stream token-by-token via Server-Sent Events. The frontend renders incrementally. Tool calls appear as structured events in the stream.
- **Anthropic Java SDK.** Use the official Anthropic SDK for Java for the Claude adapter. If no official SDK exists at the time of implementation, use direct HTTP with the Messages API.

---

## Section 1 — BYOAK Key Management

### Data Model

Extend `OrgSettings` (existing entity) with:

```
ai_provider        VARCHAR(32)    -- 'anthropic', 'openai', etc. (nullable, null = not configured)
ai_api_key_enc     TEXT           -- encrypted API key (AES-256-GCM, key from app config)
ai_model           VARCHAR(64)    -- e.g. 'claude-sonnet-4-6' (default per provider)
ai_enabled         BOOLEAN        -- org-level kill switch (default false)
```

### Encryption

- API keys are encrypted at rest using AES-256-GCM with a platform-managed encryption key (`app.ai.encryption-key` in application config).
- The key is decrypted only when making LLM calls — never returned to the frontend, never logged, never included in audit events.
- The settings API returns `ai_api_key_configured: true/false` (boolean) — never the key itself.
- A "Test Connection" endpoint validates the key by making a minimal API call (e.g., list models or a 1-token completion).

### Settings UI

Add an "AI Assistant" section to the Settings sidebar (under the "General" group):

- **Provider selector**: Dropdown with "Anthropic (Claude)" as the only option for now. Disabled options for "OpenAI" and "Google" with "Coming soon" badges.
- **API Key input**: Password-masked input field. Shows "Configured" with a "Change" button if a key is already set. "Test Connection" button that validates the key.
- **Model selector**: Dropdown of available models for the selected provider. Default to the recommended model (e.g., `claude-sonnet-4-6` for Anthropic — fast + capable for tool use).
- **Enable/Disable toggle**: Org-level toggle to enable the assistant. When disabled, the assistant panel trigger is hidden for all users.

### API Endpoints

```
PUT  /api/settings/ai          — update provider, encrypted key, model, enabled flag
GET  /api/settings/ai          — returns provider, model, enabled, key_configured (boolean)
POST /api/settings/ai/test     — validates the configured key, returns success/error
```

---

## Section 2 — Provider Abstraction Layer

### Interface Design

```java
public interface LlmProvider {
    /** Stream a chat completion with tool definitions. */
    Flux<StreamEvent> chat(ChatRequest request);

    /** Validate an API key without a full conversation. */
    boolean validateKey(String apiKey, String model);

    /** Return supported models for this provider. */
    List<ModelInfo> availableModels();

    /** Provider identifier (e.g., "anthropic", "openai"). */
    String providerId();
}
```

### Stream Events

The `StreamEvent` sealed interface covers all possible events in a response stream:

```java
sealed interface StreamEvent {
    record TextDelta(String text) implements StreamEvent {}
    record ToolUse(String toolId, String toolName, Map<String, Object> input) implements StreamEvent {}
    record ToolResult(String toolId, Object result) implements StreamEvent {}
    record Usage(int inputTokens, int outputTokens) implements StreamEvent {}
    record Done() implements StreamEvent {}
    record Error(String message) implements StreamEvent {}
}
```

### Chat Request

```java
record ChatRequest(
    String apiKey,           // decrypted, per-request
    String model,
    String systemPrompt,     // system guide + tenant context
    List<Message> messages,  // conversation history
    List<ToolDefinition> tools  // available tools for this user
) {}
```

### Claude Adapter

The `AnthropicLlmProvider` implements `LlmProvider` using the Anthropic Messages API:
- Converts `ToolDefinition` to Anthropic's tool format
- Streams via SSE from the Anthropic API, converting to `StreamEvent`s
- Handles tool_use content blocks by emitting `ToolUse` events
- Token usage extracted from the `message_delta` event

### Provider Registry

A `LlmProviderRegistry` bean holds all registered `LlmProvider` implementations. The `AssistantService` looks up the provider by the org's `ai_provider` setting. New providers are added by implementing `LlmProvider` and registering as a Spring bean.

---

## Section 3 — Assistant Service (Backend Orchestration)

### Conversation Flow

```
1. User sends message → POST /api/assistant/chat (SSE endpoint)
2. Backend loads org's AI config (provider, key, model)
3. Backend decrypts API key
4. Backend assembles system prompt:
   - Static system guide (from classpath resource)
   - Tenant context: org name, user's name and role, current page
   - Available tool definitions (filtered by user's capabilities)
5. Backend calls LlmProvider.chat() with full message history + tools
6. LLM responds with text and/or tool_use blocks
7. For tool_use: backend executes the tool against internal services
   - Write tools: emit a ToolUse event and WAIT for user confirmation
   - Read tools: execute immediately, return result
8. Tool results fed back to LLM for next response
9. Stream continues until LLM produces a final text response
10. Usage event emitted at end with token counts
```

### Write Action Confirmation Flow

This is the critical UX pattern. When the LLM wants to execute a write action:

1. Backend emits a `ToolUse` event with the tool name and proposed input (e.g., `createProject { name: "Johnson Conveyancing", customerId: 42, templateId: 7 }`)
2. Frontend renders a confirmation card showing what will happen
3. User clicks "Confirm" or "Cancel"
4. Frontend sends the decision back: `POST /api/assistant/chat/confirm` with `{ toolCallId, approved: true/false }`
5. If approved: backend executes the tool, returns result to LLM, stream continues
6. If rejected: backend sends a tool result of "User cancelled this action" to LLM, stream continues

This means the SSE stream has a "pause" state for write confirmations. The frontend manages this — the stream stays open but the backend waits for the confirmation before proceeding.

### API Endpoints

```
POST /api/assistant/chat              — SSE stream. Body: { messages: [...], context: { currentPage } }
POST /api/assistant/chat/confirm      — Confirm/reject a pending tool call. Body: { toolCallId, approved }
```

### System Prompt Assembly

The system prompt is assembled per-request from:

1. **System guide** — loaded from `classpath:assistant/system-guide.md`. Describes all pages, features, navigation, and common workflows. ~3-5K tokens.
2. **Tenant context block** — dynamically assembled:
   ```
   Organization: {orgName}
   User: {userName} (role: {orgRole})
   Current page: {currentPage}
   Plan: {planTier}
   ```
3. **Behavioral instructions** — hardcoded in the assistant service:
   - "You are the DocTeams assistant. Help users navigate and use the platform."
   - "For write actions, always use the provided tools. Never instruct users to navigate and do it themselves if a tool can do it."
   - "When a user asks 'how do I...', provide step-by-step guidance with links to the relevant pages."
   - "Be concise. Professional services users value their time."
   - "If you don't know something or can't do something, say so. Don't make things up."

### Error Handling

- **No API key configured**: Return 422 with a message directing to settings
- **Invalid API key**: Return 401 with "API key rejected by provider"
- **Rate limited by provider**: Stream an error event with retry guidance
- **Tool execution failure**: Return the error to the LLM as a tool result so it can inform the user gracefully
- **Provider timeout**: 30-second timeout on LLM calls, stream an error event

---

## Section 4 — Tool Definitions

### Tool Registry

Tools are defined as Spring beans implementing a `AssistantTool` interface:

```java
public interface AssistantTool {
    String name();
    String description();           // shown to LLM
    JsonSchema inputSchema();       // JSON Schema for tool parameters
    boolean requiresConfirmation(); // true for write tools
    Set<String> requiredCapabilities(); // capability gating
    Object execute(Map<String, Object> input, TenantToolContext context);
}
```

`TenantToolContext` provides the tenant schema, member ID, and org role — so tools call internal services with full tenant scoping.

### Tool Inventory (v1)

**Read Tools** (execute immediately, no confirmation):

| Tool | Description | Maps to |
|------|-------------|---------|
| `list_projects` | List projects with optional status filter | ProjectService.findAll() |
| `get_project` | Get project details by ID or name | ProjectService.findById() |
| `list_customers` | List customers with optional status filter | CustomerService.findAll() |
| `get_customer` | Get customer details by ID or name | CustomerService.findById() |
| `list_tasks` | List tasks for a project, optionally filtered by status/assignee | TaskService.findByProject() |
| `get_my_tasks` | Get current user's assigned tasks across all projects | MyWorkService.getMyTasks() |
| `get_unbilled_time` | Get unbilled time entries, optionally filtered by customer/project | TimeEntryService.findUnbilled() |
| `get_time_summary` | Get time summary for a project or date range | TimeEntryService.getSummary() |
| `get_project_budget` | Get budget status for a project | BudgetService.getStatus() |
| `get_profitability` | Get profitability summary for a project or customer | ReportService.getProfitability() |
| `list_invoices` | List invoices with optional status/customer filter | InvoiceService.findAll() |
| `get_invoice` | Get invoice details by ID or number | InvoiceService.findById() |
| `search_entities` | Full-text search across projects, customers, tasks | Search across services |
| `get_navigation_help` | Describe how to reach a page or feature | Static lookup from system guide |

**Write Tools** (require user confirmation):

| Tool | Description | Maps to |
|------|-------------|---------|
| `create_project` | Create a new project | ProjectService.create() |
| `update_project` | Update project name, status, or customer link | ProjectService.update() |
| `create_customer` | Create a new customer | CustomerService.create() |
| `update_customer` | Update customer details | CustomerService.update() |
| `create_task` | Create a task within a project | TaskService.create() |
| `update_task` | Update task status, assignee, or details | TaskService.update() |
| `log_time_entry` | Log a time entry for a task | TimeEntryService.create() |
| `create_invoice_draft` | Create a draft invoice for a customer | InvoiceService.createDraft() |

### Capability Gating

Each tool declares required capabilities. At conversation start, the backend filters the tool list based on the user's org role and plan tier. A `member` role user won't see `create_invoice_draft` if invoicing requires `admin` or `owner`. This uses the existing capability system — no new authorization logic.

---

## Section 5 — Chat UI (Frontend)

### Panel Layout

A slide-out drawer anchored to the right edge of the viewport:

- **Width**: 420px on desktop, full-width on mobile
- **Trigger**: A floating button in the bottom-right corner of the app (only visible when AI is enabled for the org). Icon: sparkle/wand or similar. Subtle — not a giant chat bubble.
- **Header**: "DocTeams Assistant" with a close button and a token usage badge (e.g., "~1.2K tokens")
- **Message area**: Scrollable, auto-scrolls to bottom on new messages
- **Input area**: Text input with send button. Enter to send, Shift+Enter for newline. Disabled while waiting for response.

### Message Types

Render different message types distinctly:

1. **User message**: Right-aligned bubble, slate background
2. **Assistant text**: Left-aligned, no bubble, markdown-rendered (code blocks, lists, bold, links). Links to app pages are internal router links.
3. **Tool execution (read)**: Inline card showing "Looked up [entity]" with a subtle expand to see raw data. Collapsed by default.
4. **Tool confirmation (write)**: Prominent card with:
   - Tool description ("Create project")
   - Preview of the data (name, customer, template)
   - "Confirm" (teal) and "Cancel" (ghost) buttons
   - Disabled state while waiting for user decision
5. **Tool result (write, confirmed)**: Success card with "Created project 'Johnson Conveyancing'" and a "View" link to the entity
6. **Tool result (write, cancelled)**: Muted card with "Cancelled"
7. **Error**: Red-tinted card with error message

### Streaming UX

- Text streams token-by-token with a blinking cursor indicator
- Tool use events interrupt the text stream and render as cards
- The input is disabled while the assistant is responding
- A "Stop" button appears during streaming to cancel the response

### State Management

- Messages stored in React state (useState or zustand — architect decides)
- No persistence — cleared on unmount/refresh
- Pending confirmation state tracked per tool call ID
- Token usage accumulated from `Usage` stream events

### SSE Connection

- Frontend opens an EventSource or fetch-with-ReadableStream to the SSE endpoint
- Events are typed: `text_delta`, `tool_use`, `tool_result`, `usage`, `done`, `error`
- On `tool_use` with `requiresConfirmation: true`, the frontend renders the confirmation card and waits for user input before sending the confirm/reject call
- Connection closes on `done` or `error`

### Empty State (No Key Configured)

If the org has no AI key configured, the panel shows:
- "AI Assistant needs an API key to work"
- Brief explanation of BYOAK
- "Go to Settings → AI Assistant" link (admin only)
- For non-admin users: "Ask your administrator to configure the AI Assistant"

### Mobile

On mobile (`< md` breakpoint):
- The panel becomes a full-screen sheet (Shadcn Sheet)
- Trigger button in the mobile header bar or bottom nav
- Same message rendering, adapted for narrow width

---

## Section 6 — System Guide Document

### Purpose

A static markdown file that gives the assistant deep knowledge of the application's pages, features, and workflows. This is the assistant's "training material" — without it, the LLM only knows what its tools expose.

### Content Structure

```markdown
# DocTeams System Guide

## Navigation Structure
- Work zone: Dashboard, My Work, Calendar
- Delivery zone: Projects, Documents, Recurring Schedules
- Clients zone: Customers, Retainers, Compliance
- Finance zone: Invoices, Profitability, Reports
- Team zone: Team, Resources
- Settings: General, Work, Documents, Finance, Clients, Access & Integrations

## Pages & Features

### Dashboard
[What it shows, what actions are available, when users should go here]

### Projects
[CRUD operations, project detail tabs, statuses, templates, team assignment]

### Time Tracking
[How to log time, where time appears, billable vs non-billable, rate snapshots]

... (one section per major page/feature)

## Common Workflows

### Setting up a new client engagement
1. Create customer (Customers page)
2. Set up rate card (Settings → Rates, or per-customer rates)
3. Create project from template (Projects → New)
4. Assign team members
5. Team logs time against tasks
6. Generate invoice from unbilled time

### Generating an invoice
1. Navigate to Invoices → New Draft
2. Select customer
3. Pull in unbilled time entries
4. Review line items
5. Approve and send

... (5-10 common workflows)

## Terminology
[Domain terms: matter, engagement, retainer, billable, WIP, etc.]
```

### Location

`backend/src/main/resources/assistant/system-guide.md` — loaded as a classpath resource by the assistant service.

### Refresh Mechanism

A dev-time skill (`/refresh-ai-guide`) that:
1. Reads `frontend/src/lib/nav-items.ts` for the full navigation structure
2. Reads route files (`frontend/src/app/org/[slug]/...`) for page inventory
3. Reads component files for feature descriptions (dialog titles, form fields)
4. Reads existing system guide for workflow sections (preserves manually-written content)
5. Regenerates the navigation and pages sections, preserving workflow and terminology sections

This skill is NOT part of this phase's implementation. It's a separate dev tooling concern. For v1, the system guide is manually written based on the current app state.

### Token Budget

The system guide should be **3,000-5,000 tokens** — enough to be comprehensive but not so large that it dominates the context window. The assistant service can optionally include only relevant sections based on the user's current page (optimization for later).

---

## Section 7 — Token Usage & Cost Visibility

### Tracking

The backend emits a `Usage` stream event at the end of each LLM call with `inputTokens` and `outputTokens`. Multi-turn conversations (tool calls → re-invocations) accumulate across the full interaction.

### Display

- **Per-conversation**: A subtle badge in the assistant panel header showing cumulative tokens (e.g., "~2.4K tokens")
- **Tooltip on hover**: Breakdown of input vs. output tokens and estimated cost based on the model's published pricing (hardcoded per model in frontend config)
- **No server-side aggregation for v1**: No usage history, no monthly totals, no per-user breakdown. That's a follow-up if tenants want it.

### Cost Estimation

Frontend maintains a simple pricing lookup:

```typescript
const MODEL_PRICING: Record<string, { input: number; output: number }> = {
  'claude-sonnet-4-6': { input: 3.0, output: 15.0 },  // per 1M tokens
  // ... add models as supported
};
```

Estimated cost = `(inputTokens * pricing.input + outputTokens * pricing.output) / 1_000_000`. Displayed as "$0.003" or similar. This is an estimate — actual billing is between the tenant and the provider.

---

## Out of Scope

- **Document intelligence** — uploading documents, extracting data from PDFs, template composition via AI. This is Layer 2, a future phase.
- **Time narrative polish / smart line item grouping** — drudgery-removal features are Layer 3. They may become assistant commands later.
- **Server-side chat history** — no ChatMessage entity, no conversation persistence across sessions. Follow-up if needed.
- **Multi-modal input** — no image uploads, no voice input. Text only for v1.
- **Platform-managed API key** — no free tier, no trial, no platform billing for AI usage.
- **Agent-style automation** — no autonomous background tasks, no event-triggered AI actions. The assistant only acts in response to user messages.
- **Custom tool definitions** — tenants cannot define their own tools. The tool set is platform-defined.
- **Usage quotas or rate limiting** — the tenant's API key has its own provider-side limits. No platform-side rate limiting for v1.
- **The `/refresh-ai-guide` dev skill** — the system guide is manually written for this phase. The dev skill for automated refresh is a separate concern.
- **Destructive actions** — no delete, archive, send, email, or any irreversible operation via the assistant.

## ADR Topics

1. **Provider abstraction depth** — thin interface (chat + validate + models) vs. full abstraction (embeddings, vision, function calling variations). Recommend thin interface covering only what the assistant needs. Don't pre-build for capabilities we won't use.
2. **Tool execution model** — internal service calls vs. HTTP self-calls. Recommend internal service calls (direct method invocation within the same JVM) for performance and simplicity. The assistant service has access to all domain services via Spring DI.
3. **Confirmation flow architecture** — SSE pause-and-resume vs. separate request/response for confirmations. Recommend SSE pause: the stream stays open, backend waits on a CompletableFuture that resolves when the confirm endpoint is called. Simpler than managing separate conversation state.
4. **System guide maintenance strategy** — fully manual vs. semi-automated generation. Recommend manual for v1 with a documented structure. The dev skill for automated refresh is a follow-up — don't block the phase on tooling.
5. **API key encryption approach** — application-level encryption (AES-GCM with config key) vs. database-level encryption vs. external secrets manager. Recommend application-level AES-GCM for simplicity and portability. The encryption key lives in app config (env var in production).

## Style & Boundaries

- Follow Signal Deck design system for the chat panel (slate palette, Sora headings, teal accents for confirm buttons)
- Backend follows existing Spring Boot patterns: service layer, controller layer, clear package boundaries
- New packages: `assistant/` (service, controller, tools, provider), with sub-packages for tool definitions and provider adapters
- Tool definitions are individual beans — easy to add, remove, or modify independently
- Frontend: new `components/assistant/` directory for chat panel, message types, confirmation cards
- SSE streaming uses Spring WebFlux's `Flux<ServerSentEvent>` (already available in Spring Boot 4)
- All assistant API endpoints are tenant-scoped (go through existing tenant filter)
- Test coverage: provider abstraction unit tests, tool execution integration tests, SSE streaming tests, frontend component tests for message rendering and confirmation flow
