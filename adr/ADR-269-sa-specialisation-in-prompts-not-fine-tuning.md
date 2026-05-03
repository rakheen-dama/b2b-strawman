# ADR-269: SA Specialisation in System Prompts, Not via Fine-Tuning

**Status**: Accepted

**Context**:

Kazi's vertical differentiation against Clio / LEAP / MyCase / generic accounting tools is South African specialisation: ZAR currency, SA English register, RSA ID number formats, CIPC registration formats, SA VAT number formats (10 digits starting with 4), POPIA awareness for special-personal-information categories, LSSA tariff vocabulary for legal-za, Section 86 trust-accounting concepts, "married in/out of community of property" matrimonial regimes. Phase 70's three specialists must carry that differentiation in their LLM behaviour — a Billing specialist that polishes time entries into US-English idioms ("phone call with client") undoes the brand promise; an Intake specialist that doesn't validate RSA ID checksums or recognise CIPC registration formats provides no advantage over a generic intake tool.

Two technical paths exist for getting SA context into the LLM's behaviour. Either bake the context into the model itself via fine-tuning (continued pre-training on SA-specific corpora; RLHF on SA-formatted outputs; vendor-specific custom-model creation), or carry the context in the system prompt at call time. The decision affects cost, reversibility, vendor-lock, and the speed at which prompts can be iterated.

**Options Considered**:

1. **Fine-tuned model (Anthropic custom model or third-party SA-specialised model).** Train Claude (via Anthropic's fine-tuning API where available) or a smaller open model (Llama variant, etc.) on a corpus of SA legal and accounting text; use the fine-tuned model in place of vanilla Claude for specialist calls.
   - Pros:
     - Per-call token cost is lower because SA context isn't repeated in every prompt.
     - Behaviour is "baked in" — less risk of a prompt edit accidentally removing SA register awareness.
     - At very high call volumes (millions per month), the amortised fine-tuning cost beats per-call prompt overhead.
   - Cons:
     - Fine-tuning cost is substantial (data preparation, training compute, ongoing model maintenance per Claude version refresh). At Phase 70's firm-pilot scale (low thousands of calls per month per tenant), the amortisation case is years out.
     - Vendor lock-in: a fine-tuned Anthropic custom model cannot be migrated to OpenAI / a local model without re-training. Locks Kazi to a specific provider in a way that ADR-200's provider-port abstraction was specifically designed to avoid.
     - Iteration speed collapses. A prompt-line edit ("flag POPIA §26 special-information explicitly") is a 5-minute PR with prompt-mode; with fine-tuning it is a re-train. SA legal concepts evolve (POPIA amendments, LSSA tariff updates) more often than re-trains can keep up.
     - Reviewability collapses. A fine-tuned model's "knowledge" is millions of weights, not a markdown file. A reviewer cannot diff "what changed in the SA register" between training runs.
     - Quality risk: small fine-tuned models lose general capability. A model trained heavily on SA legal text may forget how to hold a normal conversation, summarise generally, or follow tool-use instructions.
     - BYOAK is broken — tenants paste their own Anthropic key; a Kazi-trained custom model lives under Kazi's account, breaking the cost model and forcing platform-pays-for-AI.
     - Data-leakage risk on training corpus: SA legal text from Kazi tenants would need scrubbing before training, with material privilege concerns. Public SA legal text is available but smaller.

2. **System prompt SA-specialisation, vanilla Claude (CHOSEN).** Each specialist's behaviour is carried in a versioned markdown system prompt under `backend/src/main/resources/assistant/specialists/`. The model is unchanged vanilla Claude (whatever the tenant's BYOAK call resolves to). SA context is injected at call time as a bounded prompt prefix. Anthropic prompt-caching (when available) amortises the prefix cost across multi-turn sessions.
   - Pros:
     - Iteration speed = git speed. A POPIA amendment or LSSA tariff update is a markdown edit, a PR, a diff, a deploy. Hours, not weeks.
     - Reviewability = code reviewability. Every prompt change is a diff; reviewers can see "we added 'flag POPIA §26 explicitly'" and approve / reject like any code change.
     - No vendor lock-in beyond the Phase 52 lock. ADR-200's `LlmChatProvider` port stays intact; if a future phase adds an OpenAI adapter, the prompts work as-is (modulo provider-specific tool-use formatting).
     - BYOAK invariant preserved — same tenant key, same vanilla model, no Kazi-trained model.
     - Prompt-linter (a backend integration test) asserts each specialist prompt contains required SA tokens ("ZAR", "SA English", "RSA ID", etc.) — protects against accidental deletion during prompt maintenance.
     - Anthropic prompt-caching reduces the per-call cost overhead of repeated SA prefix to a marginal cache-read cost (substantially cheaper than full input tokens) for multi-turn sessions; for single-shot specialist calls (the common Billing / Intake case), the prefix cost is paid once per call but is bounded (~3-5K tokens against ~200K context windows).
     - Specialisation surface is bounded. Three prompts in v1, two more in Phase 71. Manageable as classpath markdown.
   - Cons:
     - Per-call token cost includes the SA prefix every time (mitigated by prompt-caching for multi-turn flows). At very high volumes this would matter; at Phase 70 scale it does not.
     - SA context can be "edited away" by a careless prompt change. Mitigated by the prompt-linter test that fails CI on missing required tokens.
     - SA context is not "deeply learned" — the model is following instructions in context, not exhibiting trained-in behaviour. Subtle SA register cues (e.g. natural choice of "perusal" over "review") are weaker than fine-tuning would produce. Acceptable: explicit prompt instructions ("prefer 'perusal' for reading correspondence") cover the high-value cases.

3. **Retrieval-augmented generation (RAG) for SA context** — the prompt is short, but on each call a vector-store lookup pulls SA-specific snippets (POPIA texts, LSSA tariff entries, CIPC formats) and injects them.
   - Pros:
     - Scales to a large corpus of SA reference material without bloating every prompt.
     - Updates to SA reference material flow through a vector-store update, not a prompt edit.
   - Cons:
     - New infrastructure: vector store, embedding model, ingestion pipeline, per-tenant or shared corpus decisions, BYOAK questions for embedding cost.
     - For Phase 70's three specialists, the SA context is already small enough to fit in a 3-5K-token prompt. RAG is over-engineered for the size of the corpus.
     - Adds latency: every call now does a vector lookup before the LLM call.
     - The relevant SA reference material (RSA ID checksum algorithm, CIPC format, SA VAT format) is not a corpus that grows weekly — it's a stable, small body of facts. RAG's value proposition is corpus growth; that's not the situation here.

**Decision**: Option 2 — SA specialisation lives in versioned system prompts (`billing-za.md`, `intake-za.md`, `inbox-za.md`), the model is vanilla Claude over BYOAK, no fine-tuning, no RAG.

**Rationale**:

Kazi's SA differentiation is a small, stable, well-understood body of facts and conventions. RSA ID format (13 digits with checksum), CIPC format (`YYYY/sequence/entityType`), VAT format (10 digits starting with 4), POPIA §26 categories (health, race, biometric), Section 86 trust accounting, LSSA tariff register — these don't grow weekly. They fit comfortably in a 3-5K-token prompt and they don't change often enough to need RAG-style retrieval. Fine-tuning's complexity is justified at corpus size + change rate that Kazi's domain doesn't have.

The iteration speed argument is decisive at Phase 70's stage. The product is in pilot; SA context will be tweaked weekly as feedback comes in from law firms, accounting firms, and consulting firms. Fine-tuning's months-long cycle would freeze the differentiation in place at exactly the moment it most needs to move. Prompt-mode lets us adjust register on Tuesday and ship the change on Wednesday.

Cost is acceptable at scale. Anthropic prompt-caching (when used in multi-turn flows like the Billing chat session) reduces the per-call overhead of the SA prefix to a marginal cache-read; for single-shot scheduled flows (the Inbox weekly summary), the prefix cost is paid once per matter and is bounded. At firm-pilot volumes the absolute cost is within the BYOAK envelope.

The vendor-lock argument matters for future-phase optionality. Phase 52's `LlmChatProvider` port was designed to allow an OpenAI adapter (or local-model adapter) to be added later. A fine-tuned Anthropic custom model would silently nullify that port — the prompts wouldn't work on any other provider. Prompts as markdown stay portable.

The prompt-linter test ([Phase 70 Section 7](../architecture/phase70-specialist-ai-assistants.md)) protects the SA invariants the same way unit tests protect any other behaviour: a CI gate that fails when the markdown stops mentioning required SA tokens. Prompt edits are reviewable; gaps are caught.

**Consequences**:

- Positive:
  - Three prompt files version-controlled in `backend/src/main/resources/assistant/specialists/`. Each carries YAML front-matter (`version`, `createdAt`, `specialist`) for traceability and a body that includes SA register, currency, date format, RSA ID / CIPC / VAT formats, POPIA awareness, and specialist-specific scope rules.
  - Prompt-linter integration test asserts `billing-za.md` contains `ZAR`, `SA English`, professional-register cues; `intake-za.md` contains `RSA ID`, `CIPC`, POPIA references; `inbox-za.md` contains terminology-aware tokens. Fails CI on accidental deletion.
  - Dev/local profile gets a `POST /internal/assistant/specialists/reload` endpoint to reload prompts from classpath without restart, speeding inner-loop iteration.
  - SA context updates flow through normal git PR workflow with reviewer approval, the same as any code change.

- Negative:
  - Per-call token cost includes the ~3-5K SA prefix. At Phase 70 firm-pilot volumes this is invisible; Phase 71+ may revisit if scale increases substantially.
  - SA register is "instruction-followed" rather than "learned." Edge cases (subtle idiom choice, register switching mid-document) may need explicit prompt-rule additions over time.

- Neutral:
  - No fine-tuning data corpus to maintain. No vendor-specific model artefact to track. No platform-side AI training infrastructure.
  - **Prompt caching.** System prompts attach `cache_control: {type: 'ephemeral'}` on the last system block; tool definitions render before the system block so a single cache breakpoint covers tools + system. The runner records `usage.cache_read_input_tokens` for telemetry. Trade-off acknowledged: low-traffic tenants may write the cache without ever reading it.
  - Prompts go through the Phase 43 message-catalogue translation layer for display strings (specialist display names, taglines, launcher labels). The system-prompt body itself is SA-English-only by design — translating it to isiXhosa / Afrikaans is explicitly out of scope per the Phase 70 requirements.
  - Vertical profiles (`legal-za`, `accounting-za`, `consulting-za`) tune behaviour at session-build time via OrgSettings lookups; the prompt may receive a small profile-specific suffix (e.g. tariff-vocabulary cues for `legal-za` only). The profile-suffix logic lives in `SystemPromptBuilder` alongside the static prompt loader.

- Related: [ADR-200](ADR-200-llm-chat-provider-interface.md) (provider port preserved by this decision), [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (specialist registry — `systemPromptResource` field points at the markdown file), [ADR-268](ADR-268-ocr-via-claude-vision-byoak-no-separate-vendor.md) (BYOAK invariant, same model, also preserved), [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md) (BYOAK key storage — same key for vanilla model use).
