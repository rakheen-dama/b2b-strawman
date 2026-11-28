# ADR-399: MCP DTO egress classification and a single writer

**Status**: Accepted

*Amended 2026-11-28 (architecture review): 24 handlers (21 tools + 3 resources); SQL-paged tools push the exclusion into the service query; document tools resolve document → matter → client before issuing a link (FD-20); unclassified paths fail closed; the coverage test uses per-tool fixtures and exempts `kazi_ping`; the p95 baseline is taken in 94D before the writer merges.*

**Context**:

Route B returns tenant data to the firm's own Claude over MCP ([ADR-300](ADR-300-mcp-transport-and-sdk.md), [ADR-306](ADR-306-mcp-tool-contract-design.md)). The payloads are 23 records in `mcp/dto` — `McpClientDto` (name, contacts with name/email/phone, linked matters), `McpCorrespondenceDto` (subject, `bodyText`, `bodyHtml`, from/to/cc), `McpMatterDto`, `McpInvoiceDto`, `McpActivityItem`, `McpAuditEventItem`, `McpFirmProfileDto` and others — several with nested records (`McpClientDto.Contact`, `.LinkedMatter`) and a generic envelope `McpPage<T>(items, page, size, total, truncated)`. Correspondence bodies go out verbatim.

There is no serialisation choke point. Errors go through `McpToolErrors.asResult(McpError, ObjectMapper)` (`mcp/McpToolErrors.java:24`), which returns a pre-built `CallToolResult` that Spring AI's converter passes through verbatim; successes are `return dto` / `return pageResult` from the tool method (`mcp/tool/ClientTools.java:132,153`) and are serialised by the framework, while resources serialise themselves (`mcp/resource/ClientResource.java:83`, `MatterResource.java:80`, `FirmProfileResource.java:75`). Spring AI 2.0.0-M6 has no tool-call interceptor. Enablement is `McpEnablementService.effectiveState()` = MCP integration enabled ∧ latest acknowledgement GRANTED (`mcp/McpEnablementService.java:52-59`), with copy "Acknowledge POPIA responsibility", version `popia-egress-v1` ([ADR-305](ADR-305-mcp-popia-consent-audit.md)). Names must pass through on this route (founder decision 3): the lawyer's Claude must show them.

**Options Considered**:

1. **Masking code inside each tool and resource.**
   - Pros: No reflection; each handler controls its output.
   - Cons: 24 handlers to get right and keep right; correspondence bodies are read in three tools; nothing fails the build when a new field or tool forgets; the ledger depends on each author.
2. **A central registry in `egress/` mapping DTO class → field → category.**
   - Pros: One file to review; no annotation processing.
   - Cons: Lives away from the record, so a renamed or added component drifts silently unless a test cross-checks; reviewers of a DTO change do not see its classification.
3. **A Jackson module (serializer modifier) on the MCP `ObjectMapper` that masks per field.**
   - Pros: Automatic for anything serialised by that mapper.
   - Cons: A field serializer has no per-call view of the subject or matter (NO_AI, sensitive flags) without ambient state; list omission and the non-leaking 404 are not serializer concerns; it cannot aggregate one ledger event per call; it would also run on `McpError` and any other use of the mapper; whether Spring AI's converter uses the same mapper is a framework detail that can change.
4. **`@EgressField` / `@EgressSubject` annotations on every DTO record component, and one `McpEgressWriter` through which every success response is written (chosen).**
   - Pros: Classification sits on the field and is checked by a scanner; one place applies policy and writes the ledger; nested records and `McpPage<T>` are handled once.
   - Cons: The writer is a convention enforced by tests, not by the framework; reflection metadata must be cached.

**Decision**: Every MCP DTO string component carries `@EgressField(EgressCategory.X)` (or `NONE`) and its subject id `@EgressSubject(...)`, and every tool and resource success path returns `McpEgressWriter.write(dto, McpEgressScope)`, which applies NO_AI omission, sensitive-matter withholding, identifier masking and free-text masking over a `JsonNode`, records the ledger, and is the only way PI leaves MCP.

**Rationale**:

*Writer.* It serialises the DTO to a `JsonNode` and walks it with a per-class classification map built reflectively once and cached (records recurse into nested records, collections and `McpPage.items`). In order: (1) a NO_AI client or matter is omitted from lists, and a direct get returns `McpToolErrors.asResult(McpError.notFound(noun))` — byte-identical to "no such id / no access" (`McpError.java:27-29`); (2) on a SPECIAL or CHILD matter id, reference, status and dates stay, and name, description, FREE_TEXT, correspondence subject/body and document names become `{"withheld": "<SENSITIVE_MATTER|CHILD|NO_AI>", "category": "<CATEGORY>"}` (FD-6: the name is withheld too); (3) identifier fields are irreversibly masked — last four visible for ID, bank and tax numbers, `j***@domain` for email, `*** *** 1234` for phone; (4) FREE_TEXT gets detector-based identifier masking inside the text; (5) names pass through and are recorded. Then the ledger event is written ([ADR-402](ADR-402-ai-egress-ledger.md)). For tools it returns a `CallToolResult` (passed verbatim, as errors already are); for resources the JSON string. Write tools (`file_correspondence`, `attach_document`, `propose_task`) route only their response payloads through it. **Unclassified fails closed:** a class or JSON path the classification map does not cover is replaced by `{"withheld":"UNCLASSIFIED","category":"NONE"}` and logged at ERROR (class and path, no value) — never passed through.

*Document tools (FD-20).* `McpDownloadUrl(url, expiresInSeconds)` has no subject id, and a presigned link hands out the whole document unmasked, so the writer cannot fix it after the fact. `get_document_url` resolves document → matter → client through the scope **before** calling `getPresignedDownloadUrl`: NO_AI → non-leaking `notFound("document")` + BLOCKED event; SPECIAL/CHILD matter not unblocked → withheld marker, no link; otherwise the link is issued and the event is SENT with `document_content_sent = true` (whole-document disclosure, a stated residual risk under RG-5). `attach_document` resolves correspondence → matter → client the same way (NO_AI → 404); `search_documents` withholds document names on sensitive matters (FD-6).

*List omission happens before pagination.* `McpPage.total` and page sizes would otherwise reveal hidden NO_AI rows. Tools that materialise then paginate in memory (`list_clients`, `list_matters`, `list_invoices`, `get_unbilled_time`, `search_documents`) filter the excluded subject ids before `McpPagination.paginate`. Tools paged in SQL, whose `total` comes from `Page.getTotalElements()` (`list_correspondence`, `get_matter_activity`, `get_audit_events`, `list_trust_transactions`), push the exclusion into the service query (`NOT IN :excludedProjectIds` / excluded subject ids) through overloads that the REST callers do not use; filtering after an SQL page is forbidden. The writer's omission is the backstop and logs a warning if it ever removes a row.

*Enablement.* `effectiveState()` = integration enabled ∧ latest acknowledgement GRANTED ∧ a current `AiTransferBasis` with `mcpCommercialPlanAttested = true` ([ADR-401](ADR-401-ai-transfer-basis-record.md)). The acknowledgement is renamed "Firm acknowledgement: AI data egress" and new grants carry `popia-egress-v2`. FD-1 (ruled 2026-11-28): there are no live tenants, so no grace period, switch-off or re-acknowledgement logic is built.

**Consequences**:

- Positive:
  - Correspondence identifiers are masked and sensitive or NO_AI content withheld in one place for all 24 handlers; a new tool is safe by construction once its DTO is classified.
  - The marker tells the lawyer's Claude something exists without showing it, instead of a silent gap.
- Enforcement (Tier-2 tests):
  - `McpDtoEgressClassificationTest` fails on any record component of type String / String collection / String array in `mcp/dto` (nested records included) without `@EgressField`, and on a DTO with PI fields but no `@EgressSubject`.
  - A source scan forbids `ObjectMapper.writeValue*` / `valueToTree` in `mcp/tool` and `mcp/resource` except to serialise an `McpError`.
  - Because `return dto` bypasses any `ObjectMapper` ban, an integration test iterates every tool and resource in `McpToolRegistry` and calls each with a **hand-maintained per-tool fixture** (`tool-contract.json` pins names and schemas, not call arguments); it fails for any registered tool or resource without a fixture, and asserts each success wrote exactly one ledger event — `kazi_ping` explicitly exempt (no client data) — and that `total` excludes NO_AI rows per list tool.
  - Annotations do not change component names or types, so the [ADR-343](ADR-343-mcp-contract-pinning-test.md) pin is unaffected; the deliberately-unclassified-field failure is demonstrated in E8.
- Negative:
  - Wire shape changes: a string field can become a marker object. The `kazi-legal-za` skill pack needs a doc note so skills do not read a withheld block as "no data" (follow-up PR in `../claude-for-legal-sa`).
  - Free-text masking is pattern-only; an identifier split across HTML tags in `bodyHtml`, or written in words, passes (RG-5).
  - Existing MCP users lose access at deploy until the owner acts (FD-1); copy for the attestation and acknowledgement is RG-3 / RG-6.
  - Per-response JSON walk adds latency; the p95 baseline on `list_clients` / `list_correspondence` is captured in 94D **before** the writer merges, measured after in 94D and re-observed in E8.
- Related: [ADR-397](ADR-397-one-egress-guard-two-route-adapters.md), [ADR-400](ADR-400-special-pi-children-biometric-blocking.md), [ADR-302](ADR-302-mcp-read-scope-model.md), [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md), [ADR-321](ADR-321-mcp-write-tool-category.md), [ADR-324](ADR-324-correspondence-readback-over-mcp.md), [ADR-333](ADR-333-mcp-sole-conversational-ai-surface.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slice 94D.
