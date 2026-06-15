# Phase 78 — Kazi MCP Server (Read-Only Grounded Context for the Firm's Own Claude)

> **Status: READY for `/architecture`.** The AI-core live-verification blocker is **CLEARED** (2026-06-15): the `/qa-cycle-kc ai-core-verification` cycle confirmed V0–V12 against a live Anthropic key (V11.1 deferred), fixing a never-run-live cascade (#1445) + follow-on PRs (#1447–#1452). The capability and audit substrates this server reuses are now proven. The tool catalogue (Section 3) has been mapped to the real firm-side service signatures. Strategy source: `.claude/ideas/mcp-plugin-strategy-2026-06-14.md`.

## System Context

Kazi is a multi-tenant B2B practice-management platform with three live verticals (legal-za, accounting-za, consulting-za) and schema-per-tenant isolation. As of Phase 72/74 it is **AI-native in-product**: the firm's own Anthropic key (BYOAK) powers embedded skills (FICA verification, matter intake, contract review, drafting, compliance audit), each gated behind attorney approval. That path is **Kazi calls Claude**.

Phase 78 opens the **second head on the same body: Claude calls Kazi.** A law firm already runs Kazi as its system of record *and* its people increasingly work inside Claude (Claude Desktop / Claude Code / claude.ai). Today those are severed — a lawyer asking Claude to summarise a matter, draft a fee-note narrative, or sanity-check FICA must paste context by hand, which is slow, lossy, and a POPIA hazard. Phase 78 ships a **Model Context Protocol (MCP) server** that exposes the firm's own Kazi data to the firm's own Claude, **read-only**, authenticated per-user and scoped by the member's existing RBAC.

**Positioning**: *"Bring your own Claude — Kazi provides the grounded context."* Kazi becomes the data layer underneath whatever AI the firm adopts (including Anthropic's open-source [Claude for Legal](https://github.com/anthropics/claude-for-legal) skill suite). The firm's own Claude subscription pays the token bill; Kazi exposes data and bears no token cost for this phase.

**This is additive, not a pivot.** A firm without the MCP connector loses nothing. A firm with it gets a fuller, AI-augmented experience grounded in live matter, time, compliance, and trust data that no external tool can reach.

### Predecessor systems that make Phase 78 cheap

- **Phase 21 — Integration Ports + BYOAK** (PRs #302–#314) — `OrgIntegration`, `IntegrationRegistry`, `IntegrationGuardService`, encrypted `SecretStore`, integrations settings UI. Phase 78 adds an `MCP` integration domain (per-tenant enablement + POPIA consent) on this exact pattern.
- **Phase 72/74 — AI core (live-verified)** — `AiFirmProfile`/`AiFirmProfileService` (house style, jurisdiction, risk calibration), `AiExecutionGate` (the reserved v2 write-back gate), `AiCostService`, the `ai.*` audit event family. The MCP server exposes the firm profile as a read resource and (in a later phase) reuses `AiExecutionGate` for gated writes.
- **Phase 7 / Phase 22 — Portal read-model** — `PortalReadModelService`, `PortalReadModelRepository`, 15+ denormalized `Portal*View` projections (project, invoice, task, retainer, trust balance/transaction, deadline, document). These are a strong **shape reference** for token-efficient read views. ⚠️ See ADR-302: they are *customer-scoped* (for the external client portal) and must NOT be reused verbatim for firm-staff reads, which are firm-wide and scoped by member RBAC.
- **Phase 18/19/20 — Project access control + capabilities** — `@RequiresCapability`, project-member access control, `RequestScopes.CAPABILITIES`. The MCP server runs every tool inside the same request scope so a member sees exactly what they'd see in the web app — no more, no less.
- **Phase 6 — Audit foundations** — `AuditEvent`, append-only. Every MCP session and tool call is audited; no new audit infrastructure needed.
- **Auth stack** — Keycloak OIDC (production), schema-per-tenant resolution via `TenantFilter` (org → schema mapping) and `MemberFilter` (JWT subject → member → capabilities), bound to `RequestScopes` via Java 25 `ScopedValue`. The MCP server is an OAuth resource server that lands inbound tokens in this same pipeline.

### What is missing today

- **No MCP server anywhere in the repo.** No protocol endpoint, no resource/tool registry, no MCP-specific auth flow.
- **No external read surface for firm staff's own tools.** The firm web app reads via internal controllers behind the gateway/session; there is no per-user, OAuth-scoped, machine-consumable read API a Claude client can connect to.
- **No per-tenant AI-egress consent.** POPIA requires an explicit, recorded firm decision before client PII flows into the firm's external AI context.

### Founder decisions that constrain this phase (2026-06-14 ideation)

- **Read-only v1.** Tools expose reads only. Claude drafts; a human reviews and commits results back into Kazi by hand. This deliberately sidesteps the §86 trust / Attorneys Act write-liability surface and ships fast. The write path is *reserved* (Section 7) but **out of scope to build**.
- **Reuse auth — do not fork it.** MCP authentication must resolve through the existing Keycloak OIDC + `TenantFilter` + `MemberFilter` + capability pipeline. No parallel auth model, no service-account-impersonates-user shortcut. A user sees via MCP exactly what their Kazi role + project access permit.
- **Kazi-hosted remote MCP server.** Easiest adoption for SA firms; Kazi bears the infra and sees the read traffic (good for the audit/POPIA story). A locally-run / on-prem MCP option is a later premium story, out of scope here.
- **Per-tenant opt-in + POPIA consent.** MCP is disabled by default. An owner/admin must enable it and acknowledge the data-egress consent before any tool returns data.
- **Token-efficient, stable contracts.** Tools return clean, LLM-shaped structured data with stable schemas, hard pagination, and size caps — never raw entity graphs that blow up Claude's context.
- **No skill pack in this phase.** The curated Kazi skill pack (published Claude Code plugin) and the Claude-for-Legal bridge are **Phase 79 (Phase C)**. Phase 78 ships the server + a documented connection method only.
- **Legal-za first.** The server is vertical-agnostic; the data it exposes is whatever the tenant has. Skill curation (Phase 79) is where legal-za tuning happens.

## Objective

Ship a **Kazi-hosted, read-only MCP server** that lets a firm connect its own Claude client (Desktop / Code / claude.ai) and query its live Kazi data — matters, clients, time, compliance/FICA state, trust balances, documents, invoices, and the firm AI profile — authenticated per-user via Keycloak OAuth and scoped by the member's existing RBAC + project access. Every session and tool call is audited. MCP is per-tenant opt-in behind a recorded POPIA consent. Kazi makes **zero** outbound LLM calls in this phase.

## Constraints & Assumptions

- **Schema-per-tenant only.** Any new tables live under `tenant/`. Aim for **minimal-to-zero new tables** — prefer extending `OrgIntegration` config and reusing `AuditEvent`. One Flyway migration only if a table is genuinely required (e.g. consent record); justify it at architecture time. (Next migration number: check the latest `V###` at build time.)
- **MCP transport: Streamable HTTP** (the current remote-MCP transport), exposed over HTTPS. No stdio transport (that's for local servers; out of scope). Confirm the exact transport + SDK at architecture time via the MCP spec and the Java/Spring MCP server SDK — do not hand-roll the protocol.
- **Implementation language: Java / Spring Boot 4**, in-process within the existing `backend` app behind a dedicated path (e.g. `/mcp/**`), reusing the existing servlet filters and service layer. A separate deployable module is a future option (ADR-301), not v1.
- **OAuth 2.1 resource-server model.** The MCP server validates Keycloak-issued access tokens exactly as the existing API does. The Claude client performs the OAuth authorization-code/device flow against Keycloak to obtain a token bound to the firm (org) + user. Reuse `ClerkJwtAuthenticationConverter`/Keycloak JWT validation, `TenantFilter`, `MemberFilter`.
- **No outbound LLM calls.** Kazi is a pure data provider here. No `AiProvider` invocation, no token cost, no `AiExecution` records created by MCP reads.
- **Read-only enforcement is structural, not conventional.** No tool may mutate domain state. Writes are physically absent from the v1 tool registry (not merely "discouraged").
- **Tenant isolation is non-negotiable.** Every tool call is bound to exactly one tenant schema resolved from the token; cross-tenant reads are impossible by construction. This must be covered by explicit isolation tests.
- **Project-level access control applies.** A member who cannot see a matter in the web app must not see it via MCP. Tools defer to the same project-access and per-domain capability checks the web controllers use.
- **Token-budget discipline.** Every list tool is paginated with a hard server-side max page size; every detail tool caps nested collections; document tools return metadata + a presigned URL, never inlined file bytes.
- **Test strategy: protocol + auth + isolation, no live Claude in CI.** Integration tests drive the MCP endpoint directly (initialize handshake, list resources/tools, call tools) asserting auth enforcement, capability gating, tenant isolation, pagination caps, and audit emission. No real Claude client needed in CI; a manual QA step connects a real Claude Desktop against a dev tenant.

---

## Section 1 — MCP Server Runtime & Transport

### 1.1 Endpoint & protocol
- Expose an MCP server over **Streamable HTTP** at `/mcp` within the backend app (final routing — gateway vs direct — per ADR-301).
- Implement the MCP lifecycle: `initialize` handshake (capabilities negotiation), `resources/list`, `resources/read`, `tools/list`, `tools/call`. Advertise `resources` and `tools` capabilities only (no `prompts`, no `sampling` in v1).
- Use the official Java/Spring MCP server SDK; do not implement the JSON-RPC framing by hand.

### 1.2 Server identity & versioning
- Server advertises name `kazi`, a semantic version, and a short instructions string telling the client what Kazi is and that it is read-only.
- Tool/resource schemas are versioned and stable; breaking changes require a new tool name, not a silent schema change.

---

## Section 2 — Authentication & Authorization

### 2.1 OAuth resource server
- The MCP server is an OAuth 2.1 protected resource. It validates Keycloak access tokens and publishes the protected-resource metadata the MCP client needs to discover the Keycloak authorization server.
- Inbound token → existing `TenantFilter` (org claim → tenant schema) → `MemberFilter` (subject → `Member` → `OrgRole` → `RequestScopes.CAPABILITIES`). The MCP request executes inside the same `ScopedValue` context as a normal API request.

### 2.2 Capability model
- New capability **`MCP_ACCESS`** (added to the existing `io.b2mash.b2b.b2bstrawman.orgrole.Capability` enum, seeded like other capabilities) — gates the ability to use the MCP server at all. Default roles: OWNER, ADMIN (members may be granted it via custom roles). Without it, `initialize` succeeds but every tool returns an authorization error.
- **Per-tool deference to each domain's existing gate** (verified strings, not assumed): `INVOICING` for invoices + firm-wide unbilled, `VIEW_TRUST` for trust, `TEAM_OVERSIGHT` for the firm audit log, `CUSTOMER_MANAGEMENT` for compliance, `AI_MANAGE` for the firm profile; matters/project-documents/project-activity are **project-access-gated** (`ProjectAccessService`, no capability); customers + org/customer documents are **org-wide** (any authenticated member). `MCP_ACCESS` is purely the front door — it never widens any of these. A user with `MCP_ACCESS` but no `VIEW_TRUST` gets matters but not trust data; a member sees only matters they're assigned to.
- **Project access control** (Phase 19/20, `io.b2mash.b2b.b2bstrawman.member.ProjectAccessService`) applies to every matter-scoped tool: `requireViewAccess` returns 404 (security-by-obscurity) for non-members; owner/admin see all.

### 2.3 Read scope correction (critical)
- Firm-staff MCP reads are **firm-wide, RBAC-scoped** — NOT customer-scoped. The MCP server MUST resolve data through the firm-side service layer (`ProjectService`, `CustomerService`, `InvoiceService`, `ClientLedgerService`, `DocumentService`, `ActivityService`, `AuditService`, `AiFirmProfileService` — see Section 3), honoring member capabilities + project access, and MUST NOT route through `customerbackend.PortalReadModelService`'s customer-id filtering, which would incorrectly restrict a firm member to a single client. The portal `Portal*View` projections may be reused only as a *shape* reference. (See ADR-302.)

### 2.4 ActorContext propagation
- Several firm-side reads take an `ActorContext actor` parameter (`ProjectService`, `DocumentService`, `ActivityService`). The MCP server MUST build an `ActorContext` from the resolved member (same data `MemberFilter` binds to `RequestScopes`) and pass it through, so `ProjectAccessService` and audit attribution behave identically to a web request. No anonymous/system actor.

---

## Section 3 — Resources & Tools Catalogue (read-only)

Resources are stable, browsable entities addressed by URI (e.g. `kazi://matter/{id}`); tools are parameterized queries/searches. All return token-efficient JSON. **Every tool delegates to the existing firm-side service** named below — the MCP server adds no new query logic. Several firm-side reads take an `ActorContext`; the MCP server constructs it from the resolved member (Section 2.4) and passes it through so `ProjectAccessService` and capability checks fire exactly as in the web app.

> **Access-gate reality (verified against source).** Firm-side reads fall into three gating regimes, NOT a uniform "view" capability:
> - **Project-access-gated** (no `@RequiresCapability`; `ProjectAccessService.requireViewAccess` → 404 if the member isn't on the project, owner/admin see all): matters, project documents, project activity.
> - **Capability-gated** (org-wide): invoices + firm-wide unbilled → `INVOICING`; trust → `VIEW_TRUST`; firm audit log → `TEAM_OVERSIGHT`; compliance packs → `CUSTOMER_MANAGEMENT`; firm AI profile → `AI_MANAGE`.
> - **Org-wide, no capability** (any authenticated member): customers, org/customer-scoped documents.
> `MCP_ACCESS` (Section 9) is the front-door gate; each tool then inherits its domain's existing regime above — MCP must not widen access.

### 3.1 Resources
- `kazi://firm-profile` — `AiFirmProfileService.getOrCreateProfile()` → `AiFirmProfile` (practice areas, jurisdiction, risk calibration, house-style notes, fee-estimation notes). The grounding context Claude should read first. **Note:** the existing read is guarded by `AI_MANAGE` (owner/admin). Since this is house-style grounding rather than sensitive client data, ADR-304 should decide whether to expose it under `MCP_ACCESS` alone or keep the `AI_MANAGE` gate — do not silently relax it.
- `kazi://matter/{id}` — `ProjectService.getProject(id, actor)` → `ProjectWithRole` (status, customer, dates, role; `ProjectResponse` field set). Project-access-gated.
- `kazi://client/{id}` — `CustomerService.getCustomer(id)` → `Customer` / `CustomerResponse` (type, lifecycle status, contacts) + linked matters (`LinkedProjectResponse`). Org-wide.

### 3.2 Tools — mapped to real service methods
| Tool | Backing service method (FQN) | Returns | Access gate |
|---|---|---|---|
| `list_matters` | `ProjectService.listProjects(view, status, dueBefore, customerId, params, actor)` | `List<ProjectResponse>` | project-access (member → assigned only; owner/admin → all) |
| `get_matter` | `ProjectService.getProject(id, actor)` | `ProjectWithRole` | project-access (`requireViewAccess` → 404) |
| `list_clients` | `CustomerService.listCustomers()` / `listCustomersByLifecycleStatus(status)` | `List<CustomerResponse>` | org-wide (authenticated) |
| `get_client` | `CustomerService.getCustomer(id)` (+ linked projects) | `CustomerResponse` + `LinkedProjectResponse[]` | org-wide |
| `get_unbilled_time` | `UnbilledTimeService.getUnbilledSummary(from, to, currency)`; per-matter via `UnbilledTimeSummaryService.getProjectUnbilledSummary(projectId)` | `List<CustomerUnbilledSummary>` / `UnbilledTimeSummary` | `INVOICING` (firm-wide); per-matter also project-access |
| `list_compliance_gaps` | `InformationRequestService.getFicaStatus(customerId)` + `ChecklistInstanceService` item reads (NOT `CompliancePackService`, which is static definitions) | `FicaStatusResponse` (status + per-item breakdown) | `CUSTOMER_MANAGEMENT` |
| `get_trust_balance` | `ClientLedgerService.getClientLedger(customerId, trustAccountId)`; total via `getTotalBalance(trustAccountId)` | `ClientLedgerCardResponse` / `TotalBalanceResponse` | `VIEW_TRUST` |
| `list_trust_transactions` | `ClientLedgerService.getClientTransactionHistory(trustAccountId, customerId, Pageable)` | `Page<TrustTransactionResponse>` | `VIEW_TRUST` |
| `list_invoices` | `InvoiceService.findAll(customerId, status, projectId)` | `List<InvoiceResponse>` | `INVOICING` |
| `get_invoice` | `InvoiceService.findById(invoiceId)` (+ `getPaymentEvents`) | `InvoiceResponse` (incl. `lines`) | `INVOICING` |
| `search_documents` | `DocumentService.listDocumentResponses(projectId, actor)` / `listDocumentsByScope(scope, customerId)` | `List<DocumentResponse>` (metadata only) | project-access (project scope) / org-wide (ORG/CUSTOMER scope) |
| `get_document_url` | `DocumentService.getPresignedDownloadUrl(documentId, actor)` → `StorageService.presignDownloadUrl` | `PresignedUrlResult(url, expiresInSeconds=3600)` | project-access (in-service) |
| `get_matter_activity` | `ActivityService.getProjectActivity(projectId, entityType, since, Pageable, actor)` | `Page<ActivityItem>` | project-access |
| `get_audit_events` | `AuditService.findEventsEnriched(filter, Pageable)`; per-customer via `findEventsForCustomer(customerId)` | `Page<EnrichedAuditEvent>` | `TEAM_OVERSIGHT` |

- The exact tool set/naming is finalized at architecture time; this is the verified v1 target. Each tool's input schema is explicit (typed params, required vs optional). `get_document_url` returns a 1-hour presigned URL — never inlined bytes.

### 3.3 Output shaping
- Tools return flat, named fields and short enums — not ORM entity graphs (do not serialise `Project`/`Customer`/`Invoice` entities directly; project to a trimmed MCP DTO). Money is returned with explicit currency + minor units (the firm-side DTOs already use cents/`BigDecimal` + currency). Dates are ISO-8601. Nested collections are capped and signal truncation explicitly (`"truncated": true, "total": N`) rather than silently dropping rows.

---

## Section 4 — Response Shaping, Pagination & Token Budgeting

- **Pagination is uneven in the firm-side layer and the MCP server must normalise it.** Only some reads are `Pageable` today (`ClientLedgerService` transactions, `ActivityService`, `AuditService`); others return **unbounded** `List<...>` (`ProjectService.listProjects`, `CustomerService.listCustomers`, `InvoiceService.findAll`, `DocumentService` lists). Every MCP list tool MUST expose `page`/`size` with a hard server max (e.g. 50) and apply it at the MCP layer — slicing the unbounded firm-side results — so an LLM never receives an unbounded blob. Default sizes should mirror the existing controller caps where present (activity max 50, audit max 200).
- Every detail tool caps nested arrays (e.g. ≤ N transactions, ≤ N lines) and exposes a follow-up tool/param to page deeper.
- Document tools never inline file content; `get_document_url` returns metadata + a 1-hour presigned S3 URL (`StorageService.presignDownloadUrl`).
- A global per-call response-size ceiling guards against context blowups; exceeding it returns a structured "narrow your query" error, not a truncated blob.

---

## Section 5 — Per-Tenant Enablement & POPIA Consent

- New `OrgIntegration` domain **`MCP`** (enabled/disabled, config JSON). Disabled by default. Managed from the existing Integrations settings UI (`/org/[slug]/settings/integrations`) with a new "Claude / MCP Connector" card.
- Enabling requires an owner/admin to acknowledge a **POPIA data-egress consent** (client PII will flow into the firm's external AI context; the firm is the responsible party). Record who consented and when (minimal consent record — justify table vs `OrgIntegration` config at architecture time).
- When the `MCP` integration is disabled, the MCP server refuses tool calls for that tenant with a clear, non-leaking error.
- The settings card surfaces the connection details (server URL, how to add it to Claude Desktop/Code) and a "revoke / disable" control.

---

## Section 6 — Audit & Observability of MCP Reads

- New audit event types (reuse Phase 6 `AuditEvent`): `mcp.session.opened`, `mcp.tool.invoked` (with tool name, params summary, result row count, actor, entity refs), `mcp.access.denied`.
- The audit trail gives the firm a POPIA-defensible record of which user pulled which client data into AI, and when — the property the whole strategy depends on.
- Basic metrics: per-tenant MCP call counts and latency (reuse existing observability hooks). No PII in metrics labels.

---

## Section 7 — Reserved (NOT built): v2 Gated Write-Back Contract

To prevent reinventing safety machinery later, **specify but do not implement**:
- A future "propose" tool (e.g. `propose_kyc_complete`, `propose_fee_note`) does NOT mutate state — it creates an `AiExecutionGate` (PENDING) exactly as the in-product skills do (Phase 72).
- Approval happens **inside Kazi** (existing `AiExecutionGateController` + Reviews UI), never inside the Claude client.
- Audit records "AI-suggested (via MCP) → attorney-approved" with actor + timestamp.
- Phase 78 ships none of this; the architecture must simply not foreclose it (tool registry is extensible; gate creation is already a solved server-side capability).

---

## Section 8 — Connection & Distribution (server-side only)

- Provide a documented connection method: the MCP server URL, the OAuth flow a firm uses to authorize Claude Desktop/Code, and a minimal config snippet. This lives in product docs + the settings card.
- The **curated skill pack** (published Claude Code plugin with legal-za skills) and the **Claude-for-Legal bridge** are **Phase 79**, explicitly out of scope here. Phase 78 proves a firm can connect a vanilla Claude client and successfully read their data.

---

## Section 9 — Capabilities & Permissions

| Capability | Description | Default roles |
|---|---|---|
| `MCP_ACCESS` | Use the MCP server (front-door gate) | OWNER, ADMIN |
| *(existing per-domain view capabilities)* | Enforced per tool — trust, invoice, compliance, time, document, profitability views | as already seeded |

Enforcement follows the existing `@RequiresCapability` pattern; MCP tools additionally check `MCP_ACCESS` and project access.

---

## Out of Scope

- **Any write/mutation tool** (reserved for a later phase; Section 7).
- **Outbound LLM calls from Kazi** (this is a data provider, not an AI caller).
- **The curated skill pack + Claude-for-Legal bridge** (Phase 79).
- **Local / on-prem (stdio) MCP server** — Kazi-hosted remote only in v1.
- **Client-portal MCP** — firm-staff only; the external client portal is unchanged.
- **MCP `prompts` and `sampling` capabilities** — resources + tools only.
- **Vector search / RAG / embeddings** — tools query structured data, same as Phase 72.
- **New analytics or reports** — MCP exposes existing reads; it does not invent new aggregations.
- **Non-Keycloak auth, API keys, or service accounts** for MCP — OAuth-per-user only.
- **Multi-language tool output** — English only.

---

## ADR Topics to Address

- **ADR-300**: MCP transport & SDK choice — Streamable HTTP + the Java/Spring MCP server SDK; why not stdio, why not hand-rolled JSON-RPC.
- **ADR-301**: Deployment topology — MCP in-process in `backend` behind `/mcp` vs a separate module; routing through the gateway vs direct exposure; TLS and public-endpoint considerations.
- **ADR-302**: Read-scope model — firm-side RBAC-scoped reads vs the customer-scoped portal read-model; why MCP must NOT reuse `PortalReadModelService` filtering, and how project access control is enforced per tool.
- **ADR-303**: MCP authentication — OAuth 2.1 resource-server reusing Keycloak + `TenantFilter`/`MemberFilter`; protected-resource metadata; how the Claude client authorizes; token scoping to org + user.
- **ADR-304**: Tenant isolation & capability gating for machine-consumable reads — structural guarantees, `MCP_ACCESS` front door + per-tool domain capabilities, and the isolation test strategy.
- **ADR-305**: POPIA data-egress consent & audit — per-tenant opt-in, consent recording, `mcp.*` audit events, and the responsible-party position.
- **ADR-306**: Tool/resource contract design — resources vs tools, output shaping, pagination/size caps, schema versioning, and the reserved (non-built) write-back extension point.

---

## Style & Boundaries

- Follow all conventions in `backend/CLAUDE.md` and `frontend/CLAUDE.md`.
- Package structure: `backend/.../mcp/` for the server + lifecycle, `backend/.../mcp/tool/` and `backend/.../mcp/resource/` for the read catalogue, reusing existing domain services for data access (no new query logic that duplicates existing read paths).
- Reuse existing patterns: `@RequiresCapability`, `AuditEvent` emission, `OrgIntegration` + Integrations settings UI, Keycloak JWT validation, `TenantFilter`/`MemberFilter`, `ScopedValue` request scopes, S3 presigning.
- No new shared-schema tables. Everything tenant-scoped; prefer zero new tables (extend `OrgIntegration` config, reuse `AuditEvent`).
- Read-only is a hard invariant: the v1 tool registry contains no mutating operation, and a test asserts this.
- Tool/resource schemas live in code (typed), version-controlled — not runtime-configurable.
- The Section 3 catalogue is mapped to real firm-side service signatures (verified 2026-06-15). Confirm exact method overloads and DTO field sets against source at `/architecture` time; treat the gating regimes (project-access vs capability vs org-wide) as authoritative.

---

## Next step

`/architecture requirements/claude-code-prompt-phase78.md` — the AI-core blocker is cleared; this is ready to architect.
