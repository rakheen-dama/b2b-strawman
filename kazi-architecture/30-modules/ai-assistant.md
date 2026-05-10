# AI Assistant

**Bounded context:** see [`10-bounded-contexts.md` § ai-assistant](../10-bounded-contexts.md).
**Source root:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/` (`provider/`, `specialist/`, `tool/`, `invocation/` sub-packages — `_discovery/A1-backend-map.md:71-74`).
**Frontend root:** `frontend/components/assistant/`, `frontend/hooks/use-assistant-chat.ts`, `frontend/app/(app)/org/[slug]/settings/automations/ai-queue/`.

## Purpose

In-app AI assistant with **bring-your-own-API-key (BYOAK)** model — every tenant pastes its own Anthropic key (and in future others) into `OrgIntegration`, and Kazi routes chat through it. The module provides a provider-agnostic streaming chat interface, a registry of capability-gated tools (read + write), and a registry of named **specialists** (focused personas) launchable inline from entity pages or from a generalist chat panel. SSE streaming is used for the chat surface; specialist write actions invoked from automation rules are queued for human review (ADR-267).

The product axis differentiating Kazi here is **South African specialisation in prompts, not in models** (ADR-269 — vanilla Claude + versioned markdown system prompts under `backend/src/main/resources/assistant/specialists/`, no fine-tuning, no vendor lock).

## Entities owned

Tenant-schema entities live under `assistant/invocation/`:

- **`AiSpecialistInvocation`** `→ assistant/invocation/AiSpecialistInvocation.java:29` — single-table model per **ADR-270** with sealed `OutputPayload` JSONB (proposed + applied output payloads). Fields: `specialistId`, `invokedBy` (`InvocationSource`), `actorId`, `automationActionExecutionId` (nullable — present when automation invoked), `contextEntityType`+`contextEntityId`, `status` (`InvocationStatus`), `proposedOutput`, `appliedOutput`, `reviewedAt`, `reviewedById`, `rejectReason`, `errorMessage`, `promptVersion`, `@Version` for optimistic-locking the approve/reject/retry races. Tenant migration **V120** introduces the table (per ADR-270).
- **`InvocationStatus`** `→ assistant/invocation/InvocationStatus.java:4` enum: `RUNNING, PENDING_APPROVAL, APPROVED, REJECTED, AUTO_APPLIED, FAILED, EXPIRED`.
- **`InvocationSource`** `→ assistant/invocation/InvocationSource.java:4` enum: `MEMBER, AUTOMATION, SCHEDULED`.
- **`AiLlmCall`** `→ assistant/invocation/AiLlmCall.java` — per-call ledger row (one invocation may make multiple LLM round-trips; this table records each round-trip — token counts and provider response metadata).
- **`OutputPayload`** sealed interface `→ assistant/invocation/payload/OutputPayload.java` permits `BillingPolishPayload`, `BillingGroupingPayload`, `IntakeExtractionPayload`, `InboxSummaryPayload` (per-specialist proposed-output shapes).

In-memory registries (no row-per-entry):

- **`Specialist`** record `→ assistant/specialist/Specialist.java:23` — id, displayName, tagline, `systemPromptResource` (classpath markdown), `toolIds` whitelist, `launchers`, `automationCapable`, `maxToolIterations`, `directModeAllowedTools`. Loaded by `SpecialistRegistry` `→ assistant/specialist/SpecialistRegistry.java:21` from `*SpecialistConfig` beans. **No `Specialist` table** (ADR-265 — code-only registry; prompts version with git, not Flyway).
- **`AssistantTool`** SPI `→ assistant/tool/AssistantTool.java`; auto-discovered by **`AssistantToolRegistry`** `→ assistant/tool/AssistantToolRegistry.java:20`. ~26 tools today: 16 read tools under `tool/read/`, 10 write tools under `tool/write/` (`CreateInvoiceDraftTool`, `CreateProjectTool`, `CreateTaskTool`, `LogTimeEntryTool`, `UpdateProjectTool`, `UpdateTaskTool`, `UpdateCustomerTool`, `CreateCustomerTool`, `PostInboxSummaryTool`, plus three `Propose*` tools that emit `OutputPayload`s for queue review).
- **`LlmChatProvider`** SPI `→ assistant/provider/LlmChatProvider.java:12` (per ADR-200 — provider-agnostic streaming chat with tool use, deliberately separate from the integration-domain `AiProvider` which handles one-shot text). `AnthropicLlmProvider` `→ assistant/provider/anthropic/AnthropicLlmProvider.java:35` is the only implementation today; `LlmChatProviderRegistry` resolves the provider from `OrgIntegration` config (`_discovery/A1-backend-map.md:557`).

Note that `OrgIntegration` itself (with the encrypted BYOAK key) is owned by `integration-ports`, not this module — see [`integration-ports.md`](integration-ports.md) and [`20-cross-cutting/integration-ports.md`](../20-cross-cutting/integration-ports.md).

## REST surface

| Path | Verb | Capability | Notes |
|------|------|-----------|-------|
| `POST /api/assistant/chat` | SSE stream | `isAuthenticated()` | Streaming chat. `→ assistant/AssistantController.java:33`. Captures ScopedValues on the request thread, re-binds them onto a virtual thread that runs the LLM tool loop (see Cross-cutting §SSE re-bind). |
| `POST /api/assistant/chat/confirm` | POST | `isAuthenticated()` | Member-driven confirmation of an in-flight tool call. `→ AssistantController.java:64`. |
| `GET /api/assistant/invocations` | GET | `AI_ASSISTANT_USE` | Filtered + paginated review-queue list. `→ assistant/invocation/AiSpecialistInvocationController.java:39`. |
| `GET /api/assistant/invocations/{id}` | GET | `AI_ASSISTANT_USE` | Detail view. `→ AiSpecialistInvocationController.java:58`. |
| `POST /api/assistant/invocations/{id}/approve` | POST | `AI_ASSISTANT_USE` | Approve (with optional edited payload). |
| `POST /api/assistant/invocations/{id}/reject` | POST | `AI_ASSISTANT_USE` | Reject with reason. |
| `POST /api/assistant/invocations/{id}/retry` | POST | `AI_ASSISTANT_USE` | FAILED → RUNNING reset (clears stale outputs — `AiSpecialistInvocation.resetToRunning()` :170). |
| `POST /api/assistant/invocations/bulk-approve` | POST | `AI_ASSISTANT_USE` | Multi-id approve. |
| `GET/POST /api/assistant/specialists/...` | various | — | Specialist catalog + dev controller `→ assistant/specialist/SpecialistController.java`, `SpecialistDevController.java` (the latter is dev-profile-only). |
| `GET/PUT /api/integrations/ai/...` | — | — | BYOAK key validation + model selection (`integration/AiSettingsController.java`). |

Capability-gating: every tool declares `requiredCapabilities()`; the registry filters tools per acting member at session start (`AssistantToolRegistry.getToolsForUser` :49). Tool execution re-checks capabilities and raises `InsufficientToolCapabilityException` (`AssistantToolRegistry.getTool` :79). Specialists are visible iff the caller has `AI_ASSISTANT_USE` AND the current route matches at least one launcher route (`SpecialistRegistry.visibleTo` :77).

## Frontend pages / components

Anchored to `_discovery/A2-frontend-map.md`:

- **`AssistantPanel`** `→ frontend/components/assistant/assistant-panel.tsx` (mounted in the org layout, A2:94). Sheet panel, opened by **`AssistantTrigger`** (top bar, `⌘K`). Renders `UserMessage`, `AssistantMessage`, `ToolUseCard`, `ConfirmationCard`, `ToolResultCard`, `ErrorCard`, `EmptyState`, `TokenUsageBadge` (A2:402, 442).
- **`AssistantProvider`** `→ frontend/components/assistant/assistant-provider.tsx` — context with `isOpen`, `isAiEnabled`, `toggle()`. The `aiEnabled` flag is checked server-side in the org layout from `OrgSettings.aiEnabled` (A2:406).
- **`useAssistantChat` hook** `→ frontend/hooks/use-assistant-chat.ts:128` — the **only** browser-direct backend call in the entire frontend (A2:271, 317). All other API calls go through `lib/api/client.ts` server-side, but SSE streaming needs client-side `fetch` for abort control. Supports specialist mode via `specialistId` option (A2:407).
- **`SpecialistPanel`** + **`SpecialistLauncherButton`** `→ frontend/components/assistant/{specialist-panel,specialist-launcher-button}.tsx` — inline launchers per **ADR-266** (specialists primary surface; chat panel secondary generalist fallback).
- **AI Queue review page** `→ frontend/app/(app)/org/[slug]/settings/automations/ai-queue/page.tsx` (A2:242, 409). Client at `ai-queue-client.tsx`; data layer at `frontend/lib/api/ai-invocations.ts`. Module-gated `automation_builder` (A2:486).

Entry from the nav: `/settings/automations` plus `/settings/automations/ai-queue` for the review queue. The chat panel is global (mounted in org layout).

## Domain events

Published by the invocation lifecycle (`assistant/invocation/`):

- **`AiInvocationApprovedEvent`** `→ AiInvocationApprovedEvent.java:14` — invocation approved + applier ran successfully. Carries scalar snapshot, not the JPA entity. Phase 515A declared and publishes it; subscribers belong to later slices (per the file header).
- **`AiInvocationRejectedEvent`** `→ AiInvocationRejectedEvent.java` — invocation rejected with reason.

These events are **not** in the sealed `DomainEvent` interface (`event/DomainEvent.java` per `10-bounded-contexts.md` § domain-events) — they live on the assistant-invocation namespace and use Spring's `ApplicationEventPublisher` directly. Audit events are emitted alongside (per ADR-270): `ai.specialist.invoked`, `ai.specialist.approved`, `ai.specialist.rejected`, `ai.specialist.failed`, `ai.specialist.auto_applied` — uniform per-outcome surface across all specialists.

## Cross-cutting touchpoints

- **Integration-ports binding.** AI is one of six `IntegrationDomain` values (`ACCOUNTING, AI, DOCUMENT_SIGNING, EMAIL, KYC_VERIFICATION, PAYMENT` — `integration/IntegrationDomain.java:4`). The BYOAK API key is encrypted at rest by `EncryptedDatabaseSecretStore` (AES-GCM) on `OrgIntegration`, with the LLM-chat-provider resolved per-tenant by `LlmChatProviderRegistry` from the integration's `providerSlug`. `IntegrationGuardService.requireEnabled(AI)` is the gate before any chat call. AI / ACCOUNTING / DOCUMENT_SIGNING are feature-flagged; PAYMENT/EMAIL/KYC always pass (foundational). See [`integration-ports.md`](integration-ports.md).
- **SSE re-bind on virtual thread.** The chat endpoint captures `RequestScopes` (TENANT_ID, MEMBER_ID, ORG_ID, ORG_ROLE, CAPABILITIES) on the request thread, then submits the streaming work to a `Executors.newVirtualThreadPerTaskExecutor()` and re-binds the captured ScopedValues onto the virtual thread before the LLM loop runs (`AssistantController.java:36-59`, justified per ADR-204). Tools called from inside the loop use `TenantToolContext.fromRequestScopes()` (`tool/TenantToolContext.java:32`) which requires the bind to be present. This is **the** load-bearing cross-cutting pattern in the module — without it, a tool call from a virtual thread sees an unbound `TENANT_ID` and fails.
- **Human-approval queue.** `INVOKE_AI_SPECIALIST` automation actions land in the queue per **ADR-267** — every Billing and Intake output, every member-driven Inbox invocation, and every REVIEW-mode automation queues for human approval (status `PENDING_APPROVAL`); only the Inbox specialist's `PostInboxSummary` tool, in scheduled DIRECT mode, may auto-apply (status `AUTO_APPLIED`). Direct-mode validation is enforced at rule-save time and at execution time by `InvokeAiSpecialistActionExecutor` — non-Inbox specialists or non-comment-posting tools with `mode=DIRECT` are rejected.
- **Reaper / sweeper jobs.** `AiInvocationExpirySweeper` `→ assistant/invocation/AiInvocationExpirySweeper.java` daily sweeps `PENDING_APPROVAL` rows past the TTL → `EXPIRED`. `AiInvocationReaper` `→ assistant/invocation/AiInvocationReaper.java` retention-ages old rows (POPIA §14 alignment via `nullOutputsForRetention()` :164 — keeps status as audit shadow but nulls the JSONB payloads). Both follow the **ADR-271** scheduled-trigger reaper pattern.
- **OCR via Claude Vision.** `ExtractTextFromDocumentTool` (`tool/read/ExtractTextFromDocumentTool.java`) is the Intake specialist's vision path. Per **ADR-268**, image-only PDFs/scans are sent as a vision content block on the same BYOAK chat call (no separate OCR vendor, no separate key, no platform cost-centre). `VisionContentBlock` (`provider/VisionContentBlock.java`) is the provider-agnostic carrier.
- **Specialist personas = SA system prompts.** Per **ADR-269** specialist behaviour rides the system prompt (versioned markdown under classpath `assistant/specialists/*.md`) — vanilla Claude, not fine-tuned. `SystemPromptBuilder` (`specialist/SystemPromptBuilder.java`) builds the per-session prompt; `promptVersion` is recorded on every `AiSpecialistInvocation` row for forensics.
- **Inline launchers, generalist fallback.** Per **ADR-266**, specialists appear as inline launcher buttons on entity pages (route-matched via `LauncherContext`). The generalist `AssistantPanel` remains for unstructured questions and as the SSE chat surface for member-invoked specialist sessions.
- **Pack content.** Specialists today are code-defined (ADR-265). Vertical-specific behaviour enters via (a) the system-prompt markdown shipped with the JAR and (b) the underlying tools, which are themselves verticality-aware via `OrgSettings` lookups.

## Vertical specifics

- **Specialists are vertical-aware via prompt content, not registry partitioning.** All tenants get the same registry; SA register is in the prompt; legal-vs-accounting specialisation is in the prompt; the runtime is one binary, one set of beans. ADR-269 is the load-bearing reason.
- **Tool subset filters by capability not vertical.** Trust-aware tools (e.g. anything that touches `trust-accounting` entities) are gated by capabilities (`MANAGE_TRUST`, `VIEW_TRUST`, `APPROVE_TRUST_PAYMENT`) which are themselves only granted on legal-vertical roles — verticality reaches the assistant indirectly, via `getToolsForUser(capabilities)` (`AssistantToolRegistry.java:49`).
- **OCR (Claude Vision) is unconditional** — same path on any vertical (intake docs come in scans regardless of vertical).
- **Phase-71 expansion**: Drafting + Compliance specialists are deferred to Phase 71+; the registry is sized to absorb them without migration (ADR-265 rationale).

## Active ADRs

The full 200s cluster owned by this module:

- **ADR-200** — `LlmChatProvider` interface (separate from one-shot `AiProvider`).
- **ADR-202** — `Consumer<StreamEvent>` over `Flux` (no WebFlux dependency).
- **ADR-203** — Completable-future confirmation contract for AI write actions (Phase 52).
- **ADR-204** — ScopedValue re-bind across the request-thread → virtual-thread boundary for SSE chat (anchored at `AssistantController.java:31`).
- **ADR-265** — Specialist = system prompt + tool subset + launcher metadata, **not** a tenant entity (code-only registry).
- **ADR-266** — Inline launchers primary, chat panel secondary generalist fallback.
- **ADR-267** — Human approval is the default; the single DIRECT-mode carve-out is Inbox specialist comment-posting in scheduled mode.
- **ADR-268** — OCR via Claude Vision over BYOAK; no separate OCR vendor.
- **ADR-269** — SA specialisation in system prompts, not via fine-tuning.
- **ADR-270** — Single `AiSpecialistInvocation` table with JSONB output (vs per-specialist tables).
- **ADR-271** — `SCHEDULED` trigger extension to Phase 37 (the reaper-pattern source for `AiInvocationExpirySweeper` + `AiInvocationReaper`).

## Key flows

- **Member chat with tool use** → `50-flows/ai-specialist-invocation.md` (chat-mode entry; SSE stream; in-flight confirmation).
- **Automation-invoked specialist (REVIEW mode)** → same flow doc — automation publishes; specialist executor records `InvocationSource=AUTOMATION` + `status=PENDING_APPROVAL`; queue page surfaces; admin approves → applier runs → `AiInvocationApprovedEvent`.
- **Scheduled DIRECT-mode Inbox post** → same flow doc — `SCHEDULED` trigger fires (ADR-271); Inbox specialist runs; output applied immediately with `status=AUTO_APPLIED`; comment posts with "Posted by Inbox Assistant" attribution (ADR-267 carve-out).

(Pointers will be filled when `50-flows/ai-specialist-invocation.md` is written.)

## Open questions / known fragility

- **BYOAK secret strength.** `EncryptedDatabaseSecretStore` is AES-GCM; KEK rotation cadence and storage location for the master key are owned by `integration-ports` and not yet codified in an ADR — recorded against that module, not this one.
- **Tool-level capability authorisation.** `AssistantToolRegistry` filters at session start AND on each tool call; individual tool implementations also re-validate via the underlying service's `@RequiresCapability`. This is belt-and-braces but is currently invariant-by-convention — there is no ArchUnit rule that says "every write tool must invoke a `@RequiresCapability`-annotated service method." A tool that calls a repository directly would silently bypass capability checks. Tracked.
- **Per-tenant LLM cost / token tracking.** `AiLlmCall` records token counts per round-trip; there is no tenant-level monthly aggregate, no rate-limit, no cost-cap. Acceptable at firm-pilot scale (BYOAK means tenant pays directly); will surface as a need at multi-tenant scale.
- **Specialist registry growth.** Today 3-5 specialists in code; ADR-265 keeps them in code on purpose. The boundary at which an admin-curated catalog becomes worth its complexity is undocumented — when a tenant first asks for a custom specialist, this ADR amends.
- **Phase 45 → Phase 52 supersession.** The original `phase45-in-app-ai-assistant.md` was superseded by `phase52-in-app-ai-assistant.md`; what changed (provider abstraction + tool framework + SSE + confirmation contract) is captured across ADR-200/202/203/204 but the supersession story itself is in the phase docs, not summarised here. Phase 70 (`phase70-specialist-ai-assistants.md`) layers specialists + the queue on top.
- **Specialist hand-off.** ADR-266 mentions a v1+ "specialist suggests handing over to the generalist (and vice-versa)" UX. Not implemented; tracked in the ADR's consequences.
- **Profile change does not retire specialist content.** Switching a tenant from `legal-za` to `consulting-generic` does not gate the legal-flavoured prompts off — the prompts live in code, not in tenant rows; visibility instead falls to capabilities + module gates. Same one-way-safe-not-reversible-clean property as `vertical-profiles` (`10-bounded-contexts.md` §4 known fragility).
