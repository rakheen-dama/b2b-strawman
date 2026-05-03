# ADR-270: Single `AiSpecialistInvocation` Table with JSONB Output (vs Per-Specialist Tables)

**Status**: Accepted

**Context**:

Phase 70 introduces a review queue for AI specialist outputs. Every specialist invocation — whether triggered by a member clicking an inline launcher, an automation rule firing in REVIEW mode, or a scheduled rule firing in DIRECT mode — must be recorded for audit, must support an approve/reject/edit lifecycle (REVIEW mode), and must be queryable by status, specialist, date range, and context entity. The output payloads vary substantially per specialist: the Billing polish output is a list of `(timeEntryId, beforeText, afterText)` tuples plus an optional grouping proposal; the Intake extraction output is a `Map<String, Any>` of proposed field values plus an `extractionPath` flag and a list of POPIA-flagged fields; the Inbox summary output is a markdown string plus a lookback window plus a list of source-entity references.

A schema decision must be made: one normalised table with a JSONB output column (one row shape, many output payload shapes), or three per-specialist tables (one per output payload shape, more rows shapes, less variant payload to encode). The decision affects review-queue query simplicity, audit-event uniformity, schema migration cadence as new specialists are added, and the type-safety of the output payload at the Java layer.

**Options Considered**:

1. **Per-specialist tables: `ai_billing_proposals`, `ai_intake_extractions`, `ai_inbox_summaries`.** Each output shape is normalised. Each specialist gets its own repository, its own controller route, its own review-queue endpoint slice.
   - Pros:
     - Strong type-safety at the database layer. Each column has a known SQL type; analytics SQL can join on `time_entry_id` or `customer_id` directly.
     - Each table is small, focused, indexed for its specific query shape.
     - Schema rejects ill-formed payloads at the database boundary, not at deserialisation.
   - Cons:
     - The review queue page wants to show *all* pending AI work in one list — across specialists, sortable by date, filterable by status. With three tables this requires a `UNION` query or three separate calls + client-side merge. Either approach becomes increasingly awkward as Phase 71 adds Drafting + Compliance specialists (5 tables, 5-way union).
     - Adding a specialist requires a new table, a new migration, a new repository, a new controller path for its review actions. Phase 70's three specialists alone would mean three migrations; Phase 71+ continues the cost.
     - Approve / reject / retry / edit endpoints multiply: `/api/assistant/billing-proposals/{id}/approve`, `/api/assistant/intake-extractions/{id}/approve`, etc. Frontend has three different fetch shapes for the same UX.
     - The audit-event surface fragments: `ai.billing_proposal.approved`, `ai.intake_extraction.approved`, `ai.inbox_summary.approved` — five+ event types per outcome instead of one. Phase 69 audit dashboard widget classification has to know about each.
     - The cross-specialist query "show me all pending AI work targeting customer X" is a UNION across all specialist tables. Awkward and brittle.

2. **Single `ai_specialist_invocations` table with JSONB `proposed_output` and `applied_output` (CHOSEN).** Every invocation is one row regardless of specialist; the output payload's specialist-specific shape lives in JSONB. The Java side uses sealed `OutputPayload` interface (with `BillingPolishPayload`, `IntakeExtractionPayload`, `InboxSummaryPayload` records) for type-safety at the application boundary, the same pattern Phase 37 uses for `AutomationAction.config`.
   - Pros:
     - The review queue is one table, one query — `SELECT * FROM ai_specialist_invocations WHERE status = 'PENDING_APPROVAL' ORDER BY created_at DESC LIMIT N`. Filtering by specialist / status / date range is a single index. Adding Phase 71's Drafting specialist requires zero migrations.
     - Approve / reject / retry endpoints are one path per action: `POST /api/assistant/invocations/{id}/approve`. Frontend has one fetch shape; the per-specialist diff-rendering is a client-side switch on `specialistId`.
     - One audit-event type per outcome: `ai.specialist.invoked`, `ai.specialist.approved`, `ai.specialist.rejected`, `ai.specialist.failed`, `ai.specialist.auto_applied`. Phase 69's dashboard classification is uniform.
     - Cross-specialist queries are trivial: `WHERE context_entity_type = 'customer' AND context_entity_id = ?` returns all pending AI work targeting that customer regardless of specialist.
     - JSONB at the database is matched by sealed-interface `OutputPayload` at the Java side — application code branches on payload type with exhaustive `switch`, the same idiom Phase 37 uses for action configs ([ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md) sets the precedent).
     - The variant payloads (Billing's diff list, Intake's field map, Inbox's summary markdown) are not joined-from in normal review-queue queries — they are *displayed* in the diff drawer. JSONB is the right shape for "opaque-to-SQL, structured-on-render" data.
   - Cons:
     - Less type-safety at the SQL layer. A malformed JSONB payload is detected at deserialisation, not at INSERT. Mitigated by application-layer validation in the executor and the sealed `OutputPayload` interface.
     - Analytics queries that want to extract specific fields ("which time entries did the Billing assistant polish most often?") need JSONB path expressions (`proposed_output->'edits'->0->>'timeEntryId'`). Acceptable: analytics on AI proposals is a v3+ concern, not a Phase 70 requirement.
     - Deserialiser must dispatch on `specialist_id` to pick the right `OutputPayload` subtype. Single-line Jackson polymorphic registration; not novel.

3. **Hybrid: one table for the queue header + one JSONB column, plus a per-specialist normalised denormalisation table** (e.g. `ai_billing_proposal_edits` rows for the polish edits) populated alongside. The queue lists from the header table; specialist-detail views read from the normalised side.
   - Pros:
     - Best of both — uniform queue, normalised analytics.
   - Cons:
     - Twice the write cost per invocation (header insert + normalised inserts). Twice the schema surface to maintain.
     - The normalised side is dead weight until analytics demand emerges. Phase 70 has no such demand. Speculative.
     - Adding a specialist is now *two* migrations: extend the header table's enum tolerances, plus create the new normalised side. Worst case of both prior options.

**Decision**: Option 2 — one `ai_specialist_invocations` table with JSONB `proposed_output` and `applied_output`. Java-side sealed `OutputPayload` interface for type-safety. Tenant migration **V120** introduces the table.

**Rationale**:

The review queue's primary user-facing query is "show me all pending AI work, sortable by date, filterable by specialist + status". That query shape demands one table; per-specialist tables (Option 1) require a UNION that scales linearly with the specialist count and that complicates indexes (you can't have a single `(status, created_at)` index across separate tables; each table needs its own and the planner does a sort-merge).

The JSONB-with-sealed-interface pattern is already proven in this codebase — Phase 37 stores `AutomationAction.config` as JSONB with sealed configs and the system has been stable across multiple action-type additions. Phase 70 follows the same established pattern; adopting it does not introduce new architectural risk. [ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md) sets the explicit rationale ("each trigger type has different config fields, and conditions are a variable-length array of heterogeneous predicates — JSONB keeps the entity model simple while sealed classes provide compile-time type safety").

The Phase 71+ extensibility argument is decisive. The whole point of the specialist framework is that adding a specialist is cheap (per [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md), it's one Java record + one markdown file). If adding a specialist *also* required a new table, a new migration, a new controller path, and a new audit-event type, the framework's elasticity would collapse. Option 2 keeps the framework cost flat — adding a specialist costs only what its tools and prompt cost.

The per-specialist analytics use case (Option 1's main argument) is real but is a Phase 73+ concern when the data volume justifies the analytical work. JSONB path queries are sufficient until then; if a future phase shows the queries are slow at scale, denormalised analytics tables can be added without changing the queue schema.

**Consequences**:

- Positive:
  - One migration (V120) creates the `ai_specialist_invocations` table with the columns specified in Phase 70 Section 8 (`id`, `specialist_id`, `invoked_by`, `actor_id`, `automation_action_execution_id`, `context_entity_type`, `context_entity_id`, `status`, `proposed_output` JSONB, `applied_output` JSONB, `created_at`, `reviewed_at`, `reviewed_by_id`, `reject_reason`, `error_message`, `prompt_version`, `version`). The same migration creates the sibling `ai_llm_calls` child table (one-to-many; FK `invocation_id` with `ON DELETE CASCADE`).
    - `error_message VARCHAR(2000)` — populated when `status=FAILED`; mirrored from `ActionExecution.errorMessage` for rule-invoked failures so the queue can show the failure cause without a JOIN to `action_executions`.
    - `prompt_version VARCHAR(40)` — denormalised from the specialist's prompt YAML front-matter `version` at runner start; allows queue filtering by prompt version for rollback / cohort analysis.
    - `version INTEGER NOT NULL DEFAULT 0` — Hibernate `@Version` optimistic-locking guard against double-approval (two reviewers racing on the same `PENDING_APPROVAL` row).
  - **Retention.** A daily sweeper nulls `proposed_output` and `applied_output` JSONB on terminal-state rows older than `OrgSettings.aiSettings.aiInvocationRetentionDays` (default 365); status, audit shadow, and the `AiLlmCall` rows are preserved. POPIA §14 alignment (Phase 50). See Phase 70 §3.9.
  - Indexes: `(status, created_at)` for review-queue listing; `(context_entity_type, context_entity_id)` for entity-level pending widgets; `(automation_action_execution_id)` for cross-reference to Phase 37's `ActionExecution` records.
  - One repository (`AiSpecialistInvocationRepository`), one service (`AiSpecialistInvocationService`), one controller (`AiSpecialistInvocationController`). Phase 71 adds specialists without touching any of them.
  - Audit-event surface: `ai.specialist.invoked`, `ai.specialist.approved`, `ai.specialist.rejected`, `ai.specialist.failed`, `ai.specialist.auto_applied`. Five types covering all specialists. Phase 69 dashboard classification is one entry per type.

- Negative:
  - Less SQL-level type safety on the output payload. A malformed JSONB payload is detected at Java deserialisation; the database accepts any JSON object. Mitigated by the sealed `OutputPayload` interface and validation in the executor.
  - Cross-payload analytics SQL uses JSONB path expressions. Slightly less ergonomic than normalised columns; acceptable for the current need.

- Neutral:
  - Per [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md), this is a tenant-scoped table living in each `tenant_<hash>` schema. Schema-per-tenant boundary handles isolation; no `tenant_id` column, no RLS policy needed.
  - The `automation_action_execution_id` FK is nullable (member-invoked rows have no automation linkage). When non-null it points at the Phase 37 `action_executions` table — providing the cross-reference for "which rule did this AI work come from?" debugging.
  - The `reject_reason` column captures the reviewer's text on rejection. Useful for prompt-iteration analytics in the gap report (Phase 70 Section 6) — "what kinds of outputs are rejected most?" feeds future prompt edits.

- Related: [ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md) (Phase 37's precedent for JSONB + sealed configs — the pattern this ADR copies), [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (specialist registry — the source of `specialist_id` enum values), [ADR-267](ADR-267-human-approval-default-direct-mode-exception.md) (the REVIEW vs DIRECT modes that drive `status` lifecycle), [ADR-271](ADR-271-scheduled-trigger-extension.md) (the scheduled-trigger source that creates `invoked_by=SCHEDULED` rows), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant boundary).
