# ADR-397: One egress guard, two route adapters

**Status**: Accepted

*Amended 2026-11-28 (architecture review): handler counts corrected to 21 tools + 3 resources = 24; bypass test also scans for the Anthropic base URL and new HTTP clients, with `AnthropicLlmProvider` the known exempt caller and test classes excluded.*

**Context**:

Kazi sends client personal information (PI) to Anthropic by two routes, and neither minimises or records anything today:

- **Route A — in-product skills (BYOAK).** Seven `AiSkill`s run through one method, `integration/ai/skill/AiSkillExecutionService.executeSkill` (`:83`). Prompts are assembled in `AiExecutionPersistenceService.assemblePrompts` (`:108-112`); the only provider calls are `provider.completeWithVision(...)` (`:122`) and `provider.complete(...)` (`:133`); after the call `AiExecutionPersistenceService.completeExecution` (`:124`) meters cost and calls `skill.createGates(execution, response.content(), …)` (`:140`). `CashDigestService` parses the output itself (`:319-320`). `AnthropicApiClient` is referenced only from `integration/ai/anthropic`. `IntegrationService` calls `provider.validateKey` (`:296`) with no client data.
- **Route B — the MCP server.** 21 `@McpTool`s and 3 `@McpResource`s (24 handlers) each guard themselves inline (`enablement.effectiveState()`, `McpCapabilityGuard.gatedTool/gatedResource`, `McpToolAudit`). Tools return the DTO object and Spring AI's converter serialises it (`mcp/tool/ClientTools.java:132,153`); resources call `objectMapper.writeValueAsString(dto)` themselves (`mcp/resource/ClientResource.java:83`, `MatterResource.java:80`, `FirmProfileResource.java:75`). Spring AI 2.0.0-M6 (`backend/pom.xml:63`) has **no tool-call interceptor** (Phase 78, Epic 565), so route B has no choke point.

Phase 94 needs one firm policy, one set of categories and one ledger across both routes (founder decision 1), with no skill or tool able to bypass them. The routes differ in what they can do with the data: route A owns the whole round trip and can swap real values back into the parsed answer; route B hands data to the firm's own Claude and never sees it again. The tenant model is schema-per-tenant, so policy, basis, dictionary and ledger are tenant tables like every other.

**Options Considered**:

1. **Hand masking in each skill and each tool.**
   - Pros: No new abstraction; each author knows their payload best; no reflection.
   - Cons: Seven skills and 24 MCP handlers would each re-implement detection, policy lookup and ledger writes; the next skill or tool added is unprotected by default; nothing makes a bypass fail the build; the ledger would be only as complete as the least careful author.
2. **An HTTP-level outbound filter on `AnthropicApiClient` (or a recording proxy) alone.**
   - Pros: One place on route A; catches every byte sent to `/v1/messages`.
   - Cons: It cannot see route B at all (MCP traffic goes from Kazi to the firm's Claude client, not through `AnthropicApiClient`). At that layer the payload is a JSON string with no knowledge of which subject, matter or field a value came from, so it cannot block on NO_AI/CHILD/sensitive matters or write per-subject ledger rows. It cannot rehydrate: the response is still a raw string at that point, and rehydration has to happen on the parsed structure, before `createGates`, inside the Phase 72 transaction split.
3. **Wait for a Spring AI tool-call interceptor and put route B behind it.**
   - Pros: Would give route B a framework-level choke point.
   - Cons: None exists in 2.0.0-M6 and no date is committed; it would only cover route B; and an interceptor still sees an untyped result unless the DTOs are classified anyway. The phase must land before more skills or tools are added.
4. **A horizontal `egress/` package owning categories, detectors, policy, transfer basis, tokeniser and ledger behind one `EgressGuard` façade, with one thin adapter per route beside its route, and architecture tests that make a bypass fail the build (chosen).**
   - Pros: One policy engine; each route plugs in where it has typed context (assembled prompts + `SkillContext` subjects on route A, DTO classes on route B); bypass is a test failure, not a review hope.
   - Cons: Two adapters to keep in step; route B's single writer has to be enforced by scanner tests because the framework offers no hook.

**Decision**: A new top-level `egress/` package (`classify/`, `policy/`, `token/`, `ledger/`, `EgressGuard`) is the only policy engine, route A plugs in through `integration/ai/skill/SkillEgressAdapter` called from `AiSkillExecutionService.executeSkill`, route B through `mcp/egress/McpEgressWriter`, and architecture tests forbid any path to Anthropic or out of MCP that does not go through them.

**Rationale**:

Route A already has a single method that every skill (including the collections reminder and the cash digest) passes through, so the hook costs two calls: `EgressGuard.prepareSkill(EgressRequest)` after `assemblePrompts` and before the provider call, returning `PreparedEgress` = `Blocked(reasons)` | `Allowed(systemPrompt', userPrompt', TokenMap, EgressDisposition)`; and the rehydrator after the call, before `createGates` and before anything parses the output ([ADR-398](ADR-398-reversible-ephemeral-tokenisation-route-a.md)). The `AiSkill` interface does not change. Route B gets its choke point at serialisation instead of at invocation: every tool and resource hands its DTO to `McpEgressWriter.write(dto, scope)`, which classifies by annotation, masks, withholds and records ([ADR-399](ADR-399-mcp-dto-egress-classification-single-writer.md)). This mirrors how Phase 78 solved the missing interceptor for capability gating — a per-handler call plus a test that every handler makes it ([ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md)).

The package is horizontal, not `integration/ai/egress/`, because route B lives in `mcp/` and the POPIA surfaces in `datarequest/` read the ledger; putting the engine under `integration/ai` would make `mcp` and `datarequest` depend on the AI provider package. Name matching uses a small in-house trie/Aho-Corasick matcher; no third-party dependency is added (R1).

**Consequences**:

- Positive:
  - One policy, one category set, one ledger for both routes; a design partner's "what left, about whom" question has one answer.
  - New skills inherit the guard for free; new MCP DTOs fail the build until classified.
  - Enforcement is mechanical. Architecture tests (sibling of `takeon/TakeOnEntryPointsArchTest`, under `src/test/.../architecture/`): only `integration/ai/anthropic` (and `egress`, if ever needed) may reference `AnthropicApiClient`; only `AiSkillExecutionService` may call `AiProvider.complete` / `completeWithVision`; the same rule covers the currently uncalled `AiProvider.generateText/summarize/suggestCategories` and `LlmChatProvider.chat`, so reviving them goes through the guard. `IntegrationService.validateKey` is exempt (no client data). A source scan also fails on the Anthropic base URL (`api.anthropic.com`, `/v1/messages`) or a new HTTP client (`RestClient`, `WebClient`, `java.net.http.HttpClient`, `RestTemplate`) aimed at Anthropic outside `integration/ai/anthropic`; the known exempt caller is `integration/ai/provider/anthropic/AnthropicLlmProvider` (own `DEFAULT_BASE_URL`; `validateKey` and the uncalled `chat`), allow-listed by name, with `AnthropicProperties` / `application.yml` allow-listed as configuration. The ArchUnit import excludes test classes (`ImportOption.DoNotIncludeTests`). Route B scanners are specified in ADR-399.
- Negative:
  - Route B's choke point is a convention enforced by tests, not by the framework; if Spring AI later ships an interceptor, the writer may move behind it, but the DTO classification stays.
  - The `egress` package becomes a dependency of `integration/ai/skill`, `mcp` and `datarequest`; it must not depend back on any of them (subject resolution takes ids and loaded values, not services from those packages).
  - Scanner tests over `mcp/**` and `architecture/**` are Tier-2: changes touching them need the full `./mvnw verify`, not package-scoped runs.
  - An HTTP-level capture of the outbound body is still needed, but only as **evidence** for the exit bar (test-profile request logging, never in prod), not as the guard.
- Related: [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (provider port), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md) (BYOAK metering), [ADR-283](ADR-283-prompt-architecture-firm-profile-cache.md), [ADR-300](ADR-300-mcp-transport-and-sdk.md), [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md), [ADR-343](ADR-343-mcp-contract-pinning-test.md); Phase 94 ADRs [398](ADR-398-reversible-ephemeral-tokenisation-route-a.md), [399](ADR-399-mcp-dto-egress-classification-single-writer.md), [400](ADR-400-special-pi-children-biometric-blocking.md), [401](ADR-401-ai-transfer-basis-record.md), [402](ADR-402-ai-egress-ledger.md), [403](ADR-403-popia-document-integration-ai-egress.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slices 94A (package), 94C (route A), 94D (route B).
