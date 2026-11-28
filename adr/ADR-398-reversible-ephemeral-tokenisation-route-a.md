# ADR-398: Reversible ephemeral tokenisation for in-product skills

**Status**: Accepted

*Amended 2026-11-28 (architecture review): dictionary invalidation is a fingerprint query + 10-minute TTL (only `CustomerStatusChangedEvent` exists, so there are no domain events to listen to); model output is plain JSON strings, not Tiptap, so rehydration walks the parsed strings before `TiptapNodeBuilder` runs and the "split across marks" case does not exist; policy `ALLOW` skips replacement only, never matching; rehydration runs inside the existing never-roll-back-a-billed-call `try`.*

**Context**:

On route A Kazi owns the whole round trip: it builds the prompt, calls Anthropic with the firm's own key (BYOAK, [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md)), parses the answer and shows it to a human through an `AiExecutionGate` ([ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md)). Today drafting sends the client's ID, registration and tax numbers and full address; intake sends up to 500 recent matter names with their clients; FICA sends the extracted text of ID documents (`integration/ai/skill/fica/FicaDocumentReader.java:165` adds an "[Image document: …]" placeholder for images). The drafted document must still contain the real values once approved, so masking that cannot be reversed would break drafting.

Kazi holds every client, contact, portal-contact and adverse-party name in the tenant schema, so it can recognise those names deterministically and swap them back. Constraints at HEAD: `AiExecution.outputContent` stores the raw model output (`integration/ai/execution/AiExecution.java:37,116`); `completeExecution` passes `response.content()` to `createGates` (`AiExecutionPersistenceService.java:140`); the skill-run REST response re-parses `execution.getOutputContent()` (`AiSkillController.java:135`); `CashDigestService` parses `result.execution().getOutputContent()` (`:319-320`) and then drops risks whose customer is not in the debtor book; the system prompt is sent as one block with `cache_control: ephemeral` (`integration/ai/anthropic/AnthropicApiClient.java:124-128`); the HTTP call runs outside any transaction (Phase 72 split).

**Options Considered**:

1. **Irreversible masking on route A too (`****4821`, "a client").**
   - Pros: Nothing to rehydrate; one masking code path for both routes; no map to protect.
   - Cons: Drafting produces unusable drafts (no ID number in an affidavit, no client name in an engagement letter); FICA could no longer compare document and record. It fails the founder's "Claude can still write them into a draft" requirement.
2. **Reversible tokenisation with the token map persisted, encrypted, with the execution.**
   - Pros: AI history and retries can rehydrate later; an auditor could re-derive what each placeholder meant.
   - Cons: A table of real-value ↔ placeholder pairs at rest is new sensitive data: it needs `EncryptedDatabaseSecretStore`-grade AES-GCM ([ADR-090](ADR-090-secret-storage-strategy.md), [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md)), retention tied to executions, purging on DSAR anonymisation, and it widens RG-7 (operator terms, breach notification).
3. **Natural-language surrogates ("Client-A", "Party-B") substituted in place.**
   - Pros: Reads naturally to Claude.
   - Cons: Collide with real text; Claude inflects or paraphrases them ("Client A's", "the A client") so rehydration becomes guesswork; no unambiguous regex.
4. **Reversible tokenisation with a fixed bracket grammar and an ephemeral, in-memory token map for one execution (chosen).**
   - Pros: Drafts and gates carry real values; no map at rest; a strict grammar makes rehydration exact and lets unknown placeholders be detected rather than guessed.
   - Cons: History shows placeholders; a retry re-runs (and re-bills) the whole skill.

**Decision**: Route A replaces dictionary names and detected identifiers with placeholders `[[K:<TYPE>:<n>]]` held in a per-execution in-memory `TokenMap`, rehydrates every textual node of the parsed output before `createGates` and any other parse, and never persists the map, so `AiExecution.outputContent` stores the tokenised output (FD-2).

**Rationale**:

*Grammar.* Regex `\[\[K:[A-Z_]+:\d+\]\]`; types `CLIENT` (Customer names), `PARTY` (adverse/opposing parties), `CONTACT` (client contacts, portal contacts), `IDNUM`, `PASSPORT`, `COMPANYREG`, `TAXNUM`, `BANKACC`, `EMAIL`, `PHONE`, `ADDRESS`. Numbers are assigned in first-occurrence order, scanning the system prompt before the user prompt; the same subject or value always gets the same placeholder within one call, and an alias maps to its party's placeholder. Double brackets and the `K:` prefix do not occur in legal prose, JSON or matter references. Policy `ALLOW` for a category skips **replacement only**: matching still runs in record-only mode, so subjects are still attributed in the ledger and the non-configurable `NO_AI_SUBJECT_MENTIONED` check still fires when names are `ALLOW`.

*Dictionary* (per tenant, R2.7): Customer.name, Customer contact name, PortalContact.displayName, AdverseParty.name + aliases, matter custom fields `opposing_party` / `opposing_attorney`. Cached per tenant and rebuilt when a per-tenant **fingerprint** (one query: `count(*)` and `max(updated_at)` over `customers`, `portal_contacts`, `adverse_parties`, `projects`) changes or the entry is older than 10 minutes — there are no customer, adverse-party or portal-contact update events to listen to (`event/` holds only `CustomerStatusChangedEvent`); never shared across tenants (tenant-isolation test). Matching is longest-first, case-insensitive, word-boundary, minimum 3 characters after trimming, with a stop-list that skips names made only of generic words ("Trust", "Holdings"). Identifiers: known structured fields are classified by field; free text by the deterministic detectors (SA ID Luhn + YYMMDD, passport format, CIPC `\d{4}/\d{6}/\d{2}`, SARS 10-digit leading-digit rule, bank account only beside an account label or in a known field, email, ZA phone); ADDRESS only from known fields. FICA gets a locally computed facts block from `SaIdNumber` (`idFoundOnDocument`, `luhnValid`, `matchesClientRecord`, `dobConsistent`) so Claude never needs the raw number. Intake keeps its 500-matter list, tokenised (FD-3).

*Rehydration.* `LlmJsonParser.parse(mapper, raw, JsonNode.class)` → `Rehydrator` walks every textual value node of the parsed JSON (keys are never touched). Model output is plain JSON strings (`DraftingOutput` is records of strings); Tiptap is built server-side from those strings afterwards by `AiDraftDocumentGenerator` / `AiReviewReportGenerator` via `TiptapNodeBuilder`, so real values reach the document without any Tiptap-aware rehydration. The re-serialised JSON becomes the "raw output" handed to `createGates`; the re-parse is idempotent and `AiSkill` does not change. Well-formed placeholders not in the map, and malformed `[[K:` fragments, are left verbatim and counted in `AiExecution.egressWarnings`; nothing is guessed. Rehydration runs inside `completeExecution`'s existing `try` after the cost row and the SENT ledger event are written, so a rehydration failure becomes FAILED-with-cost and never rolls back a billed call ([ADR-402](ADR-402-ai-egress-ledger.md)). Because the skill-run response (`AiSkillController.java:135`) and `CashDigestService` (`:320`) read `getOutputContent()`, `SkillExecutionResult` carries the rehydrated output as a transient field and both read that instead; otherwise the cash digest's grounding check would drop every tokenised debtor.

*Prompt caching.* A fixed egress-notice preamble ("reproduce `[[K:…]]` placeholders exactly; never invent new ones") is static text prepended to the system prompt, so the cached prefix stays byte-stable. The system prompt is firm-profile text ([ADR-283](ADR-283-prompt-architecture-firm-profile-cache.md)) and is tokenised first, so its numbering does not depend on the user prompt.

**Consequences**:

- Positive:
  - The outbound body carries no raw identifier and no dictionary name (the exit-bar capture checks this); approved drafts and gate payloads carry real values (Kazi-internal data, as today).
  - No token map at rest: RG-7's token-map-security clause shrinks to "held in memory for one call".
  - Unknown placeholders are visible (`egressWarnings`) rather than silently wrong.
- Negative:
  - The AI history page and `GET` of an execution show placeholders, with a "names and identifiers were tokenised" label; a report re-opened from history (e.g. contract review) is no longer readable in full there. Gates remain the system of record for human-visible content.
  - Retry re-runs the whole skill at full cost.
  - Names not in the dictionary (witnesses, people named only in documents) pass through; matter facts can still identify a client. This is pseudonymised PI, not de-identified (Q6): copy must never say "anonymised" ([ADR-403](ADR-403-popia-document-integration-ai-egress.md)); residual risk is RG-4/RG-5.
  - A placeholder the model mangles (altered, split or invented) cannot be rehydrated and is counted as a warning; the preamble asks Claude to reproduce placeholders exactly.
  - Dictionary build and matching add per-call latency; bounded by the tenant cache.
- FD-2 (ephemeral map, recommended) and FD-3 (500 tokenised) are founder decisions; RG-4, RG-5, RG-7 are release gates.
- Related: [ADR-397](ADR-397-one-egress-guard-two-route-adapters.md), [ADR-400](ADR-400-special-pi-children-biometric-blocking.md) (blocks evaluated before tokenising), [ADR-402](ADR-402-ai-egress-ledger.md) (TOKENISED disposition), [ADR-284](ADR-284-document-reading-s3-vision-no-vector-store.md), [ADR-288](ADR-288-contract-review-document-as-report.md), [ADR-289](ADR-289-template-guided-drafting-over-freeform.md), [ADR-292](ADR-292-ai-generated-document-provenance.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slice 94C.
