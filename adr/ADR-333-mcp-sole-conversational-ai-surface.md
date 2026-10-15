# ADR-333: MCP as the Sole Conversational AI Surface — Chat Assistant and Specialists Deleted, `assistant/provider` Re-homed Verbatim

**Status**: Accepted

**Context**:

Kazi has three AI surfaces. (1) The in-app chat assistant (Phase 45/52; ADR-173..176, ADR-200..204): `assistant/AssistantController` SSE chat, `ChatContext`, a tool registry of 17 read + 12 write tools under `assistant/tool`, confirmation flow, and the `assistant/provider` package (`LlmChatProvider`, `LlmChatProviderRegistry`, `ModelInfo`, `AnthropicLlmProvider`, …). (2) Specialist assistants (Phase 70; ADR-265..270): `assistant/specialist` + `assistant/invocation` (`AiSpecialistInvocation`, `AiLlmCall`, V120), `InvokeAiSpecialistActionExecutor` in the automation engine, the Settings → Automations → AI Review Queue page, and inline launchers on customer/matter/invoice/request pages. (3) The AI foundation and skills (Phase 72/74; ADR-280..285): `integration/ai` with `AiProvider`, five skills (contract review, drafting, compliance audit, FICA gap, intake), execution gates (ADR-281), cost metering (ADR-282), BYOAK key settings, and the Phase 78/81/82 MCP server (`mcp/`, ADR-300..306, ADR-321/322/324) consumed by the `kazi-legal-za` plugin in `../claude-for-legal-sa`.

Scouting (`architecture/phase86-subtraction-legal-only.md` §11.3.6) established the dependency facts: `integration/ai` imports only `assistant.provider.*`; `mcp/` imports nothing from `assistant` or `automation`; the only out-of-package consumers of `assistant/provider` are `IntegrationService.getAiModels()` and `AiSettingsController` `GET …/models` (the Settings → AI model list). The founder decision (`requirements/claude-code-prompt-phase86.md`): **MCP is the only conversational AI surface** — delete the chat assistant and specialists; keep the foundation, skills, gates, cost metering, BYOAK settings and MCP; `assistant/provider` "is foundation and moves into `integration/ai`." Nothing in this phase touches `mcp/` or the plugin contract.

**Options Considered**:

### A. Which conversational surface survives

1. **Delete the in-app chat and specialists; MCP is the only conversational surface; skills/reviews/gates stay in-app (CHOSEN)** — `assistant/**` minus `provider` (74 files, 22 tests), `InvokeAiSpecialistActionExecutor` + config, `ai-specialist-*.json` automation templates, `assistant/specialists/*.md`, `AI_ASSISTANT_USE`, seven `ai.specialist.*` audit types (catalogue 35 → 28), the AI Review Queue page, shell mounts and nine launcher sites all go.
   - Pros: one tool registry, one contract, one place where "what can AI read in Kazi" is defined (`McpReadOnlyRegistryTest` allowlists, ADR-306) — the parallel `assistant/tool` registry duplicated that with its own scope rules; the kazi-legal-za skill pack is the product's conversational AI and already runs against the MCP contract; `integration/ai` and `mcp/` have no compile-time dependency on anything deleted, so the removal is a package deletion plus seams, not a redesign; ~7k backend lines and 25 tests leave the full verify.
   - Cons: firms without their own Claude subscription have **no conversational AI in the product** — only skills, reviews and gates; the specialists' OCR/intake flows (ADR-268) and billing/inbox specialists are gone with no in-app replacement; `LlmChatProvider`'s streaming/tool-use members become dead after the move.
2. **Keep the in-app chat but back it with the MCP tools** (one registry, two clients).
   - Pros: one tool contract; firms get chat without a Claude subscription.
   - Cons: an in-process MCP client, an in-app chat UI, consent/egress semantics (ADR-305) re-derived for a first-party caller, and a BYOAK billing model for chat — a new feature in a subtraction phase, and precisely the second surface the founder rejected; keeps the SSE controller, `ChatContext`, confirmation flow and shell mounts alive.
3. **Keep chat, delete only specialists.**
   - Pros: smallest change to the staff app shell.
   - Cons: keeps the parallel 29-tool registry whose scope rules diverge from MCP's; the chat's write tools (ADR-174/175) would be the only un-gated AI writes left after Phase 81 put every MCP write behind a gate (ADR-322) — a safety regression by omission.
4. **Delete MCP, keep in-app chat** (the reverse).
   - Pros: no external contract to protect.
   - Cons: breaks the `kazi-legal-za` plugin — the one AI deliverable with a GTM story — and discards Phases 78/81/82; contradicts the founder decision outright.

### B. What happens to `assistant/provider`

1. **`git mv` to `integration/ai/provider`, package rename only, no API change (CHOSEN)** — `IntegrationService` and `AiSettingsController` imports rewritten; the three provider tests move with it; `LlmChatProviderRegistryTest` `hasSize(2)` recounted (expected unchanged).
   - Pros: the model-list endpoint and BYOAK settings keep working without touching their behaviour; the move is mechanical and reviewable; ADR-200's decision to keep `LlmChatProvider` separate from `AiProvider` is not reopened inside a deletion PR.
   - Cons: `integration/ai` now hosts two provider abstractions (`AiProvider` and `LlmChatProvider`) and the streaming/tool-use members of the latter have no caller — reported in the closure, not pruned.
2. **Collapse `LlmChatProviderRegistry` into `AiProvider` now** (reverse ADR-200).
   - Pros: one provider abstraction; no dead members.
   - Cons: widens slice 7 from "delete + move" into a refactor of the Phase 72 port that every skill depends on; the requirements forbid "while I'm here" refactors in surviving packages; Phase 87+ work.
3. **Delete `provider` too and re-implement model listing inside `AnthropicAiProvider`.**
   - Pros: nothing from `assistant` survives at all.
   - Cons: re-implements working code to satisfy a package name; `ModelInfo`, key validation and the registry are exactly the foundation pieces the founder said to keep.

**Decision**: Delete the in-app chat assistant, its tool registry, the specialist assistants, the AI Review Queue and the `INVOKE_AI_SPECIALIST` automation action (A1). Move `assistant/provider` verbatim to `integration/ai/provider` before the deletion (B1). AI skills, reviews, execution gates, cost metering, BYOAK settings, Settings → AI (key, model list, consent), `settings/ai/history`, `ai/reviews`, `settings/integrations/mcp` and the entire MCP server remain; MCP tool names, schemas and `McpReadOnlyRegistryTest` allowlists are untouched by every slice.

**Rationale**:

1. **The plugin depends on the MCP contract, nothing depends on the chat.** `kazi-legal-za` (seven SA-legal skills) is built on `get_matter`, `list_correspondence`, `propose_task` and friends. The in-app chat has no external consumer and zero production users. Keeping the contract-bearing surface and deleting the other is the only ordering consistent with the GTM motion.
2. **Two tool registries is one too many.** `assistant/tool` and `mcp/` each defined what AI may read and write, with separate scope, consent and gate rules. After Phase 81 every MCP write goes through a gate; the chat's write tools predate that model. One registry removes a safety-divergence risk as well as code.
3. **Solo-operator continuity.** Prompts, specialist markdown, a review queue and a chat UI are maintenance surfaces. The skills + gates model (ADR-281) keeps the attorney-liability posture with far less surface.
4. **Verify cost and test churn.** `assistant/**` carries 25 test classes including a `V120MigrationTest`, `SpecialistRegistryIntegrationTest` and `AiSpecialistAutomationPackValidityTest` with pinned counts; removing them and the seven audit types shrinks a count-pinned registry that every slice otherwise has to touch.
5. **Move before delete, don't refactor.** The provider package is working foundation code with two consumers; the cheapest safe change is a rename. ADR-200's separation stays in force until Phase 87+ chooses to collapse it.

**Consequences**:

- Positive: one conversational surface with one contract; `AiFoundationWithoutAssistantTest` proves models list, skill execution, gate approval, cost reporting and `kazi_ping` all work with `assistant/**` absent from the classpath; audit catalogue 28 entries; `Capability` loses `AI_ASSISTANT_USE` (`AI_MANAGE/AI_EXECUTE/AI_REVIEW/MCP_ACCESS` stay).
- Positive: `automation/` loses `INVOKE_AI_SPECIALIST` from `ActionType`, `ActionConfig.permits` and the executor registry; `AutomationSchedulerScheduledTriggerIntegrationTest` is re-fixtured on `SEND_NOTIFICATION` and keeps its scheduled-trigger assertions.
- Positive: `billing/SubscriptionStatusCache` and `project/ProjectUpcomingDeadlinesService` each lose an `assistant` consumer but stay (`demo` and `ProjectController` still use them); `architecture/TenantScopeBindingTest` drops the `AssistantController` exemption; the staff shell (`org/[slug]/layout.tsx`) no longer mounts `AssistantProvider`/`AssistantPanel`/`AssistantTrigger`.
- Negative: **no in-app chat for firms without their own Claude.** A pilot firm that does not install the plugin gets skills and reviews only; this is a product decision, not a gap to be re-filled in Phase 86.
- Negative: specialist flows (intake triage from the create-customer dialog, billing specialist on invoices, inbox summary) have no in-app equivalent; the plugin's `intake-triage` and `correspondence-digest` skills are the replacement, outside the product.
- Negative: `LlmChatProvider` keeps streaming and tool-use members with no caller; `integration/ai` hosts two provider abstractions until a Phase 87+ consolidation.
- Negative: the Settings → AI model list is now served from `integration.ai.provider.LlmChatProviderRegistry` — a chat-shaped registry whose only remaining job is listing models and validating keys; anyone extending Settings → AI must know that `AiProvider` (skills) and `LlmChatProvider` (models) are different ports until they are merged.
- Negative: `qa/testplan/demos/ai-core-verification-keycloak.md` is amended (authorised: deleted feature) and re-run in the capstone; `ai_specialist_invocations` and `ai_llm_calls` are dropped by the owning slice's throwaway migration and omitted from the squashed baseline (ADR-331) with no history of specialist runs retained.
- Related: [ADR-306](ADR-306-mcp-tool-contract-design.md) (the contract that must not move), [ADR-303](ADR-303-mcp-authentication.md) / [ADR-305](ADR-305-mcp-popia-consent-audit.md) (MCP auth and consent, unchanged), [ADR-321](ADR-321-mcp-write-tool-category.md) / [ADR-322](ADR-322-tiered-write-safety-and-gate-over-mcp.md) (gated writes — why the chat's write tools would be the odd ones out), [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) / [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) / [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md) (the foundation kept), [ADR-200](ADR-200-llm-chat-provider-interface.md) (separation preserved by the move), [ADR-173](ADR-173-provider-abstraction-depth.md) / [ADR-174](ADR-174-tool-execution-model.md) / [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) / [ADR-266](ADR-266-inline-launchers-primary-chat-panel-secondary.md) (superseded via [ADR-330](ADR-330-archive-tag-not-fork.md)), [ADR-334](ADR-334-automation-engine-without-builder.md) (the `ai-queue` page and specialist action leave with this ADR).
