# ADR-283: Prompt Architecture with Firm Profile Cache

**Status**: Accepted

**Context**:

Phase 72 AI skills need to include firm-specific context (practice areas, jurisdiction, risk calibration, house style, FICA requirements, fee estimation notes) in every prompt. This context drives the AI's behaviour — a CONSERVATIVE risk calibration produces more flags than AGGRESSIVE, a `ZA-GP` jurisdiction references Gauteng-specific court rules, and firm-specific FICA requirements add enhanced due diligence triggers.

The firm profile changes infrequently (updated when the firm's configuration changes, typically once at cold-start and occasionally thereafter). But every skill invocation reads it. At ~50-200 invocations/month, the profile is read far more often than written. The Anthropic Messages API supports `cache_control` directives on message content blocks, allowing the API to cache and reuse prompt content across requests, reducing input token cost by 90% for cached blocks.

The question is how to deliver firm-specific context to the AI: via the system prompt (prompt engineering), via RAG retrieval, or via fine-tuning.

**Options Considered**:

1. **Firm profile in system prompt with prompt caching (CHOSEN)** — Assemble the firm profile as an XML-tagged block inside the system prompt. Apply Anthropic's `cache_control: {"type": "ephemeral"}` directive to the system prompt content block. Track `profile_version` on the firm profile entity to detect when the cached prompt is stale.
   - Pros:
     - **Simple and deterministic.** The firm profile is a small structured block (~200-500 tokens). Including it in the system prompt means every skill invocation gets the exact same firm context. No retrieval latency, no embedding mismatch, no stale index.
     - **Prompt caching dramatically reduces cost.** When the profile hasn't changed between invocations, the system prompt (which includes the profile, legal context, and output format specification — typically 2,000-4,000 tokens) hits the Anthropic prompt cache. Cached input tokens cost 10% of regular input tokens. For a firm doing 100 invocations/month with a stable profile, this saves ~90% on system prompt tokens.
     - **Profile versioning enables cache control.** The `profile_version` field (incremented on each profile update) serves as a cache key. The system prompt is rebuilt only when the profile version changes. Within a profile version, all invocations use the same system prompt text, maximizing cache hit rate.
     - **No infrastructure dependency.** No vector store, no embedding pipeline, no retrieval service. The profile is a database read and a string interpolation.
     - **Prompts are code.** Skill system prompts are stored as classpath resources (`resources/ai/skills/{skill-id}/system.txt`). The firm profile block is interpolated at runtime. Prompt engineering iteration happens via code changes (pull requests, code review, version control), not runtime configuration. This is intentional — prompts are as important as code and should be treated with the same rigour.
   - Cons:
     - **Token limit risk at scale.** If the firm profile grows very large (unlikely — it's configuration data, not content), it could consume a significant portion of the context window. Mitigated: the profile is bounded by design (~500 tokens max). The real token consumers are document content (FICA skill) and active matter lists (intake skill), which are in the user prompt, not the system prompt.
     - **No semantic retrieval.** The profile is included in full — the AI cannot "focus" on the relevant parts. For a 500-token profile, this is fine. If profiles grew to thousands of tokens (unlikely), selective retrieval would be needed.
     - **Static within a session.** If the profile is updated mid-invocation (race condition), the invocation uses the old version. Acceptable — the profile version is recorded on the execution for auditability, and the next invocation picks up the new version.

2. **RAG retrieval from a firm knowledge base** — Store the firm profile (and potentially other firm documents) in a vector store. Embed the profile and retrieve relevant sections per skill invocation based on semantic similarity to the skill's topic.
   - Pros:
     - Scalable to large knowledge bases. If firms accumulate extensive AI context (precedent notes, template guidelines, client-specific instructions), RAG can retrieve the most relevant subset.
     - Selective retrieval reduces token usage — only the relevant profile sections are included in the prompt.
   - Cons:
     - **Massive over-engineering for v1.** The firm profile is ~500 tokens. RAG infrastructure (vector store, embedding pipeline, retrieval service) adds weeks of implementation for a problem that does not exist. YAGNI.
     - **New infrastructure dependency.** A vector store (pgvector, Pinecone, etc.) is a new operational component with its own backup, monitoring, and failure modes. Phase 72 explicitly excludes vector databases.
     - **Retrieval quality risk.** Semantic similarity between "FICA requirements" and the FICA skill's topic is high, but between "fee estimation notes" and FICA is low — yet both are relevant (fee estimation notes affect the intake skill's fee estimate, not FICA). Relevance is structural, not semantic. The profile is small enough to include in full.
     - **Breaks prompt caching.** RAG retrieval produces different context per invocation (depending on what's retrieved), defeating Anthropic's prompt cache. Every invocation would pay full input token cost.

3. **Fine-tuning with firm data** — Fine-tune a custom model with the firm's historical data (completed checklists, past matter classifications, approved invoices).
   - Pros:
     - The model "knows" the firm's patterns without explicit prompting. No profile block needed.
     - Potentially higher accuracy for firms with extensive historical data.
   - Cons:
     - **Not available for Claude models.** Anthropic does not offer fine-tuning for Claude as of May 2026. This option is technically impossible.
     - **Even if available, wildly impractical.** Fine-tuning requires curated training data, per-tenant model instances, ongoing retraining as data accumulates, and model versioning. The operational complexity dwarfs the value for small law firms.
     - **Undermines the BYOAK model.** Fine-tuned models would be platform-hosted, not tenant-keyed. The tenant loses direct cost control.
     - **SA-specific legal knowledge changes.** FICA amendments, LSSA tariff updates, and court rule changes require prompt updates, not model retraining. Prompts-as-code iterate faster than fine-tuning cycles.

**Decision**: Option 1 — Firm profile as an XML-tagged block in the system prompt, with Anthropic prompt caching via `cache_control`.

**Rationale**:

The firm profile is small, structured, and read-heavy. Including it in the system prompt is the simplest approach that provides full firm context to every skill. Prompt caching makes this approach cost-efficient — the system prompt (profile + legal context + output format) is cached across invocations with the same profile version, reducing input token cost by 90%.

RAG adds complexity without value for a 500-token profile. Fine-tuning is technically impossible and operationally impractical. The correct time to introduce RAG is when firm-specific knowledge exceeds what fits in a system prompt (~50,000 tokens with Claude's context window) — that is not Phase 72.

Prompts living as classpath resources means prompt engineering follows the same workflow as code: branches, pull requests, code review, version control. This is a deliberate choice. Prompts that live in a database are invisible to code review, hard to diff, and tempting to modify in production without testing. Prompts are code.

**Consequences**:

- Positive:
  - Simple, deterministic firm context in every prompt. No retrieval, no embedding, no vector store.
  - Prompt caching reduces system prompt cost by ~90% for stable profiles.
  - `profile_version` on `AiFirmProfile` and `firm_profile_version` on `AiExecution` enable cache invalidation and auditability.
  - Prompts as classpath resources enable version-controlled prompt engineering.

- Negative:
  - Full profile included every time (no selective retrieval). Acceptable at ~500 tokens.
  - Exchange rate for cache cost calculation is a static config value. Acceptable for v1.
  - If a firm never completes the cold-start wizard, skills run with a generic profile and a warning. The output is less firm-specific but still useful.

- Neutral:
  - System prompt structure: `[legal context block] + [firm profile block] + [output format block]`. The first and third blocks are static per skill (classpath resources). The second block is dynamic (interpolated from entity). All three blocks are wrapped in a single `cache_control` directive.
  - The `cache_control: {"type": "ephemeral"}` directive tells Anthropic to cache the block for the duration of the session (5 minutes). For a firm doing multiple skill invocations in quick succession (e.g., verifying three clients' FICA), the system prompt is cached across all three calls.
  - Graceful degradation: if the profile is missing, the skill assembles a minimal profile with defaults (`jurisdiction: "ZA"`, `risk_calibration: "CONSERVATIVE"`) and adds a warning to the output: "Firm AI profile not configured — results may be generic. Configure your profile in Settings > AI."

- Related: [ADR-269](ADR-269-sa-specialisation-in-prompts-not-fine-tuning.md) (SA specialisation via prompts — same principle applied to Phase 70 specialists), [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (AiProvider — the interface that accepts system prompts), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md) (cost metering — prompt caching affects cost calculation), [ADR-284](ADR-284-document-reading-s3-vision-no-vector-store.md) (document reading — user prompt content, not system prompt)
