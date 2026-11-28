# ADR-402: AI egress ledger

**Status**: Accepted

*Amended 2026-11-28 (architecture review): events gain `category_counts` (counts per category, no values) and `document_content_sent` (an MCP presigned document link was issued, FD-20); the route A SENT event is written with the cost row before rehydration, so a later failure is FAILED-with-cost with the event kept; `kazi_ping` writes no event; the MCP p95 baseline is captured in 94D.*

**Context**:

POPIA requires the firm to tell a data subject the identity or categories of third parties who have had access to their information (s23(1)(b)), to give notice of recipients and of transfers to a third country (s18(1)(g)/(h)), and to describe planned transborder flows in its PAIA manual (PAIA s51). Today Kazi keeps no record of what left for an AI provider. Route A stores the execution (`AiExecution`, with `inputSummary` but not the prompt) and route B emits `mcp.tool.invoked` with entity refs and a row count (`mcp/McpToolAudit.java`), but neither says which data subject's which categories were sent, masked, tokenised or withheld, to whom, or on what basis.

Constraints at HEAD: the audit log (`audit/AuditEvent`, `audit_events`) is a general-purpose, PI-free stream whose details go through `SafeAuditDetails` (SAFE_TOKEN only) and whose rows are retained under the `AUDIT_EVENT` retention policy (`retention/RetentionService.java:77,298`, default 2520 days); the DSAR export streams the subject's audit trail (`DataExportService.buildAuditTrail`, `:540`; [ADR-262](ADR-262-dsar-audit-trail-unsanitised.md)) and assembles its sections in `collectData` (`:330`), with no recipients section; anonymisation (`DataAnonymizationService.anonymizeCustomer`, `:258`) overwrites the customer's PI but keeps its id ([ADR-193](ADR-193-anonymization-vs-deletion.md)). Route A deliberately splits the HTTP call from the DB transactions (`AiExecutionPersistenceService`: `startExecution` `:72`, `completeExecution` `:124`, `failExecution` `:170`). Route B tool handlers return their response after the service reads; there is no surrounding transaction to join.

**Options Considered**:

1. **Reuse `audit_events`: one `egress.*` audit row per egress, subjects and categories in details.**
   - Pros: No new tables; existing retention, audit UI and DSAR trail pick it up.
   - Cons: Per-subject lookup ("every event about client X") becomes a jsonb scan over the whole audit table; one MCP list call can touch 50 subjects, which does not fit a PI-free details map; the audit stream would carry a per-subject disclosure register it was not designed for.
2. **A JSON blob of subjects and dispositions on each `AiExecution` (and a sibling row per MCP call).**
   - Pros: Route A data sits next to its execution.
   - Cons: Two shapes for one ledger; no index on subject; MCP has no execution row; DSAR and the client "AI disclosures" panel would have to parse blobs.
3. **Store the actual payload sent (or a hash of it) for proof.**
   - Pros: The strongest evidence of what left.
   - Cons: Re-creates the PI at rest that the guard exists to minimise; a hash proves nothing a person can read. Rejected outright.
4. **Two dedicated tables: `ai_egress_events` (one per skill execution or MCP call) and `ai_egress_subjects` (one per data subject per event), never holding raw values (chosen).**
   - Pros: Indexed per-subject reads for DSAR and the client panel; one shape for both routes; easy to prove PI-free.
   - Cons: Two more tenant tables and their retention wiring.

**Decision**: Every egress decision on either route writes one `ai_egress_events` row and one `ai_egress_subjects` row per data subject with a per-category disposition, never a raw value, in the same transaction as the route A execution persist and in a `REQUIRES_NEW` transaction before the route B response returns.

**Rationale**:

*Model (R6).* `ai_egress_events`: id, route SKILL | MCP, skill_id or tool_name, ai_execution_id (nullable FK), member_id, project_id (nullable), provider_legal_entity, provider_country, purpose, disposition SENT | BLOCKED, block_reasons jsonb, policy_version, basis_version ([ADR-401](ADR-401-ai-transfer-basis-record.md)), category_counts jsonb (`{"NATIONAL_ID":2,"PERSON_NAME":3}` — occurrences replaced/masked/sent, counts only; it carries unattributed detector hits and feeds the skill-run chips), free_text_sent, image_sent, document_content_sent (true only for an MCP SENT event that issued a presigned document link — the whole document, unmasked), occurred_at. `ai_egress_subjects`: id, event_id, subject_type CLIENT | ADVERSE_PARTY | CONTACT, subject_id, categories jsonb `{category: SENT | MASKED | TOKENISED | WITHHELD}`, occurred_at denormalised, index `(subject_type, subject_id, occurred_at)`. Provider and country are copied from the basis at write time, so a later provider change never rewrites history.

*No raw values (invariant).* Columns are ids, enums, counts and booleans; `purpose` is a fixed per-skill / "MCP read" code. A test inserts a fixture egress with known identifier and name values and asserts none of them appears in any ledger column.

*Transactions.* Route A: `EgressLedgerService.recordSkill` runs inside `completeExecution` (SENT) or the new BLOCKED persist, so the ledger and the execution commit or fail together and the HTTP call stays outside any transaction. In `completeExecution` the SENT event is written right after the cost row and **before** rehydration and `createGates`, which stay inside the existing never-roll-back-a-billed-call `try`: a rehydration or gate failure becomes FAILED-with-cost with the SENT event kept ("sent is sent"). The event is validated in Java against its CHECK rules before the insert, so a ledger constraint cannot abort the transaction and lose the cost row. A BLOCKED execution has no provider call and no cost row. A call that fails after sending (`failExecution`) still records SENT, because the bytes left. Route B: `McpEgressWriter` calls `EgressLedgerService.recordMcp` with `REQUIRES_NEW` before returning; if the response then fails to reach the client, the ledger over-reports. Over-reporting is preferred to under-reporting (FD-7). `kazi_ping` carries no client data and writes no event.

*Retention and anonymisation.* The ledger follows the `AUDIT_EVENT` retention period: `RetentionService`'s sweep is extended to purge ledger rows past the same cutoff (no separate record type to configure). DSAR anonymisation keeps the rows; `subject_id` still points at the anonymised customer, which holds no PI, so the disclosure record survives (A4, FD-9).

*DSAR.* `collectData` gains an "AI providers and disclosures" section: each provider and country, then the events about the subject (date, route, purpose, categories with disposition, event disposition). This answers s23(1)(b). The same query backs the client "AI disclosures" panel (`CUSTOMER_MANAGEMENT`) and the firm-wide egress log with CSV export (`AI_MANAGE`).

**Consequences**:

- Positive:
  - "Show me everything that went to Anthropic about this client" is one indexed query, usable by the DSAR export, the client panel and the egress log.
  - Each event names the policy and basis version in force, so the answer is reproducible later.
  - The ledger itself is PI-free and can be shown to a design partner as evidence.
- Negative:
  - On route B every successful call costs an extra short write transaction; the p95 baseline is captured in 94D before the writer merges and compared after it.
  - MCP over-reporting (FD-7) means the ledger can list a disclosure that never reached the client; the DSAR copy says "made available to", not "read by".
  - Firm staff appear as `member_id` only and are not ledger subjects (A5); names not in the dictionary are not subjects either, so the ledger is complete only for known subjects (RG-4).
  - Rows retained after anonymisation are still personal-information-adjacent metadata about a former client; FD-9 accepts this as the record of disclosure.
  - `DataProtectionWireContractTest` ([ADR-365](ADR-365-wire-shape-contract-pins-compliance.md)) and the DSAR export tests re-pin.
- Related: [ADR-397](ADR-397-one-egress-guard-two-route-adapters.md), [ADR-398](ADR-398-reversible-ephemeral-tokenisation-route-a.md), [ADR-399](ADR-399-mcp-dto-egress-classification-single-writer.md), [ADR-400](ADR-400-special-pi-children-biometric-blocking.md), [ADR-403](ADR-403-popia-document-integration-ai-egress.md), [ADR-196](ADR-196-pre-anonymization-export-storage.md), [ADR-261](ADR-261-audit-severity-derived-read-time.md), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md), [ADR-305](ADR-305-mcp-popia-consent-audit.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slices 94A (entities + service), 94C/94D (writes), 94E (log, panel, DSAR).
