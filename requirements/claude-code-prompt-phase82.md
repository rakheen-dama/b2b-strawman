# Phase 82 — Correspondence Read-Back over MCP (close the BYOC filing loop)

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-06-27). This is a **small slice**: it adds the **read** half that Phase 81 left open. Phase 81 made the Kazi MCP server able to *write* correspondence (`file_correspondence`, `attach_document`, `propose_task`) but a firm's Claude **cannot read filed correspondence back over MCP** — the only correspondence read surface is the in-app REST tab (586A), which is not an MCP tool. This phase adds two MCP **read** tools so the consumer skill pack in `../claude-for-legal-sa` can build a correspondence-digest skill. There is **no new domain, no new write path, no migration** — it is a thin exposure layer over the existing `correspondence/` bounded context.
>
> **Scouting note (verified against current code, 2026-06-27 — do NOT re-derive):**
> - The **`correspondence/` bounded context is built** (Phase 81). `Correspondence` (entity) already persists everything the read tools need: `id`, `customerId`, `projectId`, `direction`, `subject`, **`bodyText` AND `bodyHtml`**, `fromAddress`, `toAddresses`, `ccAddresses` (jsonb), `sentAt`, `receivedAt`, `threadKey`, `messageId`, `source`, `filedByMemberId`, `filedAt`, audit columns.
> - **`CorrespondenceService` already lists correspondence:** `listByProject(projectId, pageable)` and `listByCustomer(customerId, pageable)` return `Page<CorrespondenceListResponse>` (the projection `id`, `subject`, `fromAddress`, `receivedAt`, `attachmentCount`, `direction`). The REST-facing `listForProject`/`listForCustomer` wrap them with a **view-access check + page-size clamp**. There is **no service method that returns a single correspondence WITH its body** — that is the one net-new service method this phase adds.
> - **`CorrespondenceRepository` has `findByProjectId`/`findByCustomerId`/`findById`/`findByMessageId`/`countAttachments`.** `requireScopeById(id)` exists (returns a `CorrespondenceScope` record, body-less) — reuse the *pattern*, but the new detail read needs body fields, so it needs its own DTO + method.
> - **The Phase 78 MCP read pipeline is the template.** `resolve_matter_by_email` (Phase 81, in `ClientTools`) is the closest precedent for a new read tool: `McpCapabilityGuard.gatedTool("MCP_ACCESS", …)` + `McpEnablementService.effectiveState()` first-statement guard + `mcp.tool.invoked` read audit + non-leaking 404 via `McpToolErrors`. `McpPagination.clampSize` + the materialize-then-paginate ceiling guard are the established list pattern.
> - **`McpReadOnlyRegistryTest` will fail on any new `@McpTool` whose name is not a `list|get|search` verb** (it broke on `resolve_matter_by_email`, fixed via `READ_NAME_EXEMPTIONS`). `list_correspondence` and `get_correspondence` **match the read-verb pattern**, so no exemption edit is needed — but a new read tool still must not land in `WRITE_TOOL_NAMES`/`ALLOWED_WRITE_TOOL_BEANS`. Expect the **catalogue-count assertions** (`AuditEventTypeRegistryTest`, any tool-count test) to need a bump; only the full `./mvnw verify` catches these (per CLAUDE.md §5).

## System Context

Kazi is a mature multi-tenant B2B practice-management platform (Next.js 16 frontend + portal, Spring Boot 4 / Java 25 backend, Keycloak OIDC, schema-per-tenant via Hibernate + Flyway). 81 phases have shipped. Phase 78 shipped a **read-only Kazi MCP server**; Phase 81 added the first **write** tools using email-filing as the lighthouse — a firm's own Claude (Gmail/Outlook connector or a pasted email) files an email into the right matter (`file_correspondence`), attaches its documents (`attach_document`), and proposes follow-up tasks for in-Kazi approval (`propose_task`). The consumer skill that drives this lives in `../claude-for-legal-sa` (`kazi-legal-za` plugin).

The asymmetry this phase fixes: **Claude can write correspondence into Kazi but cannot read it back out over MCP.** A "what's happening across my matters' inboxes — what needs a reply, what implies a deadline" digest skill is therefore impossible to ground today. Reading the body is what makes such a digest useful (deadlines and obligations live in the body, not the subject line).

- **MCP server** (`backend/.../mcp/`) — per-user OAuth, per-tenant, enablement- + POPIA-egress-consent-gated (`McpEnablementService.effectiveState()`), every read audited (`mcp.tool.invoked`). Read tools resolve `MCP_ACCESS`.
- **`correspondence/`** — Phase 81's bounded context. `CorrespondenceService.listByProject/listByCustomer` already exist; the single-with-body read does not.
- **Read-egress posture** — every existing read tool (`get_matter`, `get_client`, `list_trust_transactions`, …) already egresses client PII to the firm's Claude under the **existing** egress-consent gate. Reading a correspondence body rides that **same** posture — it is not a new POPIA category.

### Founder decisions that constrain this phase (2026-06-27 ideation)

- **Two read tools only; metadata list + full-body detail.** `list_correspondence` returns the existing metadata projection; `get_correspondence` returns a single record **with body**. Nothing else.
- **Body read-back is in scope and rides the existing read-egress consent** — NOT a new consent flag, NOT a new gate. It is the same egress posture as `get_matter`/`get_client`. (The alternative — re-hydrating the body from the firm's Gmail by `messageId` — was **rejected** because it would make the digest Gmail-only; reading from Kazi is provider-neutral and works for archived mail.)
- **Reuse the Phase 81 service + projection.** `list_correspondence` wraps `listByProject`/`listByCustomer` verbatim. Do **not** add a second list path. `get_correspondence` adds exactly one service method + one detail DTO.
- **No write, no new domain, no migration.** If you find yourself adding a table or a write tool, you have left scope.

## Objective

Let a firm's own Claude read filed correspondence back over MCP so the `correspondence-digest` consumer skill can reason over it. Specifically: (1) `list_correspondence(matterId | customerId, page)` — paginated metadata rows, reusing `CorrespondenceService.listByProject/listByCustomer`; (2) `get_correspondence(id)` — a single correspondence **with body** + headers, via one new `CorrespondenceService` method + a detail DTO. Both gated, audited, and tenant-isolated exactly like every other read tool. Zero new tables.

## Constraints & Assumptions

- **Reuse, do not duplicate.** `list_correspondence` calls the **existing** `listByProject`/`listByCustomer` (+ `McpPagination.clampSize`). `get_correspondence` is the only net-new service method (`requireDetailById` or similar) — it must throw `ResourceNotFoundException` for an unknown/wrong-tenant id (search_path isolation makes a cross-tenant id invisible), reusing the `requireScopeById` shape but returning a **body-bearing detail record**, never the JPA entity across the MCP boundary.
- **Gating = read pipeline.** Both tools: `McpEnablementService.effectiveState()` guard as the first statement, `McpCapabilityGuard.gatedTool("MCP_ACCESS", …)`, `mcp.tool.invoked` audit (never `mcp.write.*`), non-leaking 404 via `McpToolErrors` (identical message for not-exist vs no-access). Decide at `/architecture` whether `get_correspondence` also needs a **per-matter view-access** check (the REST `listForProject` enforces `projectAccessService.requireViewAccess`; an MCP read resolved to `MCP_ACCESS` is org-wide — match whichever the existing matter/document read tools use, and state the choice).
- **POPIA / audit.** The body egresses under the existing read-egress consent — no new flag. **No PII in audit params** (the standing `McpAuditMetadata` `SAFE_TOKEN` rule): audit `correspondenceId`/`projectId` entity refs only — never the subject, from-address, or body. Confirm `get_correspondence`'s audit carries only safe entity refs.
- **Registry tests.** `list_correspondence` / `get_correspondence` match the `list|get|search` read-verb pattern, so `McpReadOnlyRegistryTest` needs **no** `READ_NAME_EXEMPTIONS` edit — but verify they are NOT added to any write set. Bump any hardcoded catalogue/tool-count assertions; only the full `./mvnw verify` surfaces cross-package count tests (CLAUDE.md §5).
- **Tenant isolation.** Mandatory test: tenant A's `get_correspondence(B's id)` → 404, never the body. `list_correspondence` for a matter in tenant A returns nothing for tenant B.
- **No migration, no new entity, no write tool, no LLM, no Gmail.** Verify at review.
- **Test strategy.** Full `./mvnw verify` clean. Tests: `list_correspondence` pagination + clamp + newest-first; `get_correspondence` returns body for an in-tenant id and 404 for a fabricated/cross-tenant id; both reject when MCP disabled / consent revoked; audit emits `mcp.tool.invoked` with safe refs only; tenant-isolation. **"PASS means observed"** — call the tools against a running MCP server (Claude or an MCP test client) → backend log → returned payload; reproduce-before-fix.

---

## Section 1 — `list_correspondence` (read)

- **Input:** exactly one of `matterId` (projectId) or `customerId`, plus optional `page`/`size`. Reject "neither" and "both" with a clear `McpError` (mirror `resolve_matter_by_email`'s argument discipline).
- **Behaviour:** delegate to `CorrespondenceService.listByProject` / `listByCustomer` (newest-first is JPQL-hardcoded; `clampSize` caps page size). Return the **metadata projection** (`id`, `subject`, `fromAddress`, `receivedAt`, `attachmentCount`, `direction`) as an `McpPage`. **No body** in the list — the body comes from `get_correspondence`. Consider adding `messageId` to the row so the digest can cross-check against what `file_correspondence` filed (decide at `/architecture`; the current `CorrespondenceListResponse` omits it).
- **Audit:** `mcp.tool.invoked`, entity refs only.

## Section 2 — `get_correspondence` (read, with body)

- **Input:** `correspondenceId`.
- **Behaviour:** one new `@Transactional(readOnly = true)` service method returning a **detail DTO** — `id`, linkage (`customerId`/`projectId`), `direction`, `subject`, **`bodyText`** (and `bodyHtml` if useful), `fromAddress`, `toAddresses`, `ccAddresses`, `sentAt`, `receivedAt`, `threadKey`, `messageId`, `attachmentCount`, `filedAt`. Throw `ResourceNotFoundException` (→ non-leaking 404) for an unknown/cross-tenant id. The JPA entity never crosses the MCP boundary — map to the record in the service.
- **Body egress** rides the existing read-egress consent; no new consent flag.
- **Audit:** `mcp.tool.invoked`, `correspondenceId` ref only — **no subject/from/body in audit**.

## Section 3 — Grounding the consumer skill (informational — not built here)

- The `../claude-for-legal-sa` `correspondence-digest` skill consumes these two tools. To stay groundable, the tools must appear in the plugin's MCP catalogue (`kazi-legal-za/data/mcp-catalogue.json`) — note that the **consumer-repo PRD** (`project/prd-correspondence-skills.md`) is the place that wiring is specified; this phase just ships the tools the catalogue will reference.

---

## Out of Scope

- **Any write tool, new entity, or migration.** This is read-only exposure of an existing domain.
- **Re-hydrating bodies from Gmail/Outlook** — rejected (would make the digest provider-locked).
- **A correspondence MCP resource** (`kazi://correspondence/{id}`) — `get_correspondence` covers the need; add a resource only if `/architecture` shows a concrete consumer for it.
- **Outbound/sent correspondence, threading reconstruction, attachments-as-bytes in the payload** — attachments are already `Document`s reachable via `search_documents`/`get_document_url`; `list_correspondence` exposes `attachmentCount` only.
- **The two consumer skills themselves** (`file-email`, `correspondence-digest`) — they ship in `../claude-for-legal-sa` per `project/prd-correspondence-skills.md`, not this repo.
- **Portal exposure** — correspondence is firm-internal.

---

## ADR Topics to Address

- **ADR-324**: Correspondence read-back over MCP — exposing the existing `correspondence/` domain through `list_correspondence` (metadata, reusing `listByProject/listByCustomer`) + `get_correspondence` (body-bearing detail DTO); why no new domain/table; the body-via-Kazi vs body-via-Gmail decision (provider-neutrality) and why body read-back inherits the existing read-egress consent rather than introducing a new flag.
- **(If the access-model question is non-trivial)** a short note within ADR-324 on whether `get_correspondence` enforces per-matter view-access or org-wide `MCP_ACCESS`, matching the existing matter/document read tools.

---

## Style & Boundaries

- Follow `backend/CLAUDE.md` (Spring Boot 4, Hibernate 7, multitenancy, `@RequiresCapability`, audit patterns, controller/service discipline). New code is two `@McpTool` read methods (alongside the existing read tools — `MatterTools`/`ClientTools`/`CorrespondenceWriteTools` are the neighbours; decide whether the reads live on a new `CorrespondenceReadTools` bean or an existing read bean) + one `CorrespondenceService` detail method + one detail DTO.
- **Reuse over rebuild:** `CorrespondenceService.listByProject/listByCustomer`, `McpPagination`, `McpCapabilityGuard`, `McpToolErrors`, `McpToolAudit`, the `resolve_matter_by_email` precedent.
- **Hard boundaries to verify at review:** no write tool, no new table/migration, no LLM, no Gmail/IMAP; PII never in audit params; tenant-isolation test present; full `./mvnw verify` clean.
- **"PASS means observed"** — MCP tool call → backend log → returned body; reproduce-before-fix for any bug.

---

## Next step

`/architecture requirements/claude-code-prompt-phase82.md` — generates the architecture section + ADR-324. Then `/breakdown 82` (this is small — likely **1 epic / 2 slices**: `list_correspondence`, then `get_correspondence`). The two **consumer skills** that use these tools ship in `../claude-for-legal-sa` per `project/prd-correspondence-skills.md` (its plugin/marketplace flow, not `/architecture`).
