# ADR-319: Inbound-Correspondence Domain — Entity, Linkage Rule, Attachments-as-Documents & Idempotency

**Status**: Accepted

**Context**:

Phase 81 lets a firm's own Claude file an inbound email into the right Kazi matter. Kazi today has **no inbound-correspondence concept** — only outbound notification email (`integration/email/`, unrelated). A filed email needs a home: a record carrying the headers (from/to/cc/subject), the body, timestamps, a thread key, and a direction, attached to a matter and/or a client, with its attachments persisted as files. Three sub-decisions follow: (1) how attachments are stored, (2) whether one entity or a thread/message split, (3) how re-filing the same email is deduplicated. The phase mandate is **reuse over rebuild**, schema-per-tenant isolation, and a small surface (BYOC — extraction happens in Claude, not Kazi). `Document`/`DocumentService` already give a presigned-upload storage path; `Document` links to `aiExecutionId` but has no generic source-reference field.

**Options Considered**:

1. **One `Correspondence` entity, ≥1-non-null linkage, attachments as `Document` rows via a new `documents.correspondence_id` FK, dedupe on a client-supplied `messageId` (CHOSEN)** — a flat record per filed email with optional `customerId`/`projectId` (at least one set), recipients as JSONB, and a unique `message_id`. Attachments are normal `Document`s carrying a nullable `correspondence_id` (mirrors `ai_execution_id`).
   - Pros: maximal reuse (no new storage path, attachments appear in the existing documents list and obey existing retention/access); one new column mirrors a pattern reviewers already know; flat entity matches the only v1 use case (file *this* email here); `messageId` dedupe is exactly what an idempotent MCP write needs.
   - Cons: a `documents` table ALTER touches a shared entity; JSONB recipients aren't individually queryable (acceptable — never queried that way in v1); the ≥1-non-null rule needs both a `CHECK` and a service guard.
2. **Separate correspondence storage (bytes/attachments on the correspondence aggregate, not `Document`s)** — store attachment bytes against `Correspondence`, a parallel file path.
   - Pros: correspondence is fully self-contained; no `documents` change.
   - Cons: a second storage path to secure, presign, retain, and back up; attachments would NOT appear in the matter's documents list; duplicates `DocumentService` wholesale — directly violates the reuse mandate.
3. **Thread+message split (a `Thread` parent owning `Message` children)** — normalise email threads up front.
   - Pros: ready for full threaded-conversation UI.
   - Cons: premature normalisation — thread reconstruction is explicitly v2; adds a join and a second migration for a parent the v1 use case never reads; `threadKey` on a flat row already carries the forward-compat hook.

For dedupe specifically: (a) **by `messageId`** (CHOSEN) — exact, cheap, and the natural idempotency key for a retryable MCP call; (b) **by content hash** — robust to missing `Message-ID` but expensive, and re-files of edited drafts hash differently (false negatives); (c) **no dedupe** — every retry creates a duplicate, unacceptable for an idempotent write contract.

**Decision**: One flat `Correspondence` entity in a new `correspondence/` bounded context, with both `customerId` and `projectId` nullable under a `CHECK (customer_id IS NOT NULL OR project_id IS NOT NULL)` rule; recipients stored as JSONB; attachments persisted as `Document`s via the existing `DocumentService`, linked by a new nullable `documents.correspondence_id` column and an `EMAIL_INGEST` source value; and idempotency enforced by a unique `message_id` with a find-then-insert upsert whose collision behaviour is a no-op returning the existing id.

**Rationale**:

1. **Reuse the document path, don't fork it.** Attachments-as-`Document`s means one storage path, one presigned-upload mechanism, one retention/access model, and attachments surfacing in the matter's existing documents list — satisfying the phase's reuse mandate. A single nullable `correspondence_id` column mirrors `ai_execution_id`, which reviewers already understand, over a polymorphic `source_reference_type` (YAGNI for one link).
2. **Both-nullable + CHECK fits real cases.** An email can arrive before a matter is opened (client-only), or be matter-only with ambiguous client linkage. Forcing either field alone would reject a real filing; the ≥1-non-null `CHECK` (plus a friendly service-layer guard) is correct and defends in depth.
3. **Flat entity, `threadKey` hook.** The v1 use case ("file this email into this matter") never reads a thread parent. A flat row with `threadKey` is forward-compatible to v2 thread grouping without premature normalisation.
4. **`messageId` is the right idempotency key for a BYOC retryable write.** Claude may retry; the lawyer may re-run. A unique `message_id` (RFC-822 `Message-ID` or Claude-supplied `externalId`) makes re-filing a no-op returning the existing id, with the unique constraint as the race backstop. Schema-per-tenant means `message_id` is already tenant-scoped (the schema *is* the boundary).
5. **Schema-per-tenant, no `tenant_id`.** The table follows every existing tenant table (`tasks`, `documents`, `ai_execution_gates`): no `tenant_id`, isolation via Hibernate `search_path`.

**Consequences**:
- Positive: maximal reuse; attachments are first-class documents; idempotent ingestion; a forward-compatible thread hook at zero v1 cost; one small, recognisable `documents` change.
- Positive: a re-file with the same `messageId` but a different `matterId` is a safe no-op (returns the existing record; a human moves it in Kazi if needed) — no silent re-linking.
- Negative: JSONB recipients aren't individually queryable without a later GIN index; the ≥1-non-null rule is enforced in two places (service + `CHECK`) and both must stay in sync; the `documents` ALTER touches a shared table (low risk — additive nullable column).
- Negative: no content-hash dedupe means an email with no `Message-ID` and no supplied `externalId` cannot be deduped — Claude must always supply a stable key (documented in the tool contract).
- Related: [ADR-321](ADR-321-mcp-write-tool-category.md) (the write tools that create these records), [ADR-322](ADR-322-tiered-write-safety-and-gate-over-mcp.md) (`propose_task` references `correspondenceId`), [ADR-323](ADR-323-email-matter-linking.md) (how the matter/customer is chosen before filing), [ADR-320](ADR-320-byoc-ingestion-boundary.md) (Kazi receives structured input, does not extract).
