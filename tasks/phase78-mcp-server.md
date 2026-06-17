# Phase 78 — Kazi MCP Server (Read-Only Grounded Context for the Firm's Own Claude)

> **Architecture**: [`architecture/phase78-mcp-server.md`](../architecture/phase78-mcp-server.md) (Section 11)
> **Requirements**: [`requirements/claude-code-prompt-phase78.md`](../requirements/claude-code-prompt-phase78.md)
> **ADRs**: [ADR-300](../adr/ADR-300-mcp-transport-and-sdk.md) (transport & SDK — Spring AI 2.0 `spring-ai-starter-mcp-server-webmvc`, STREAMABLE, hand-rolled fallback), [ADR-301](../adr/ADR-301-mcp-deployment-topology.md) (in-process at `/mcp`), [ADR-302](../adr/ADR-302-mcp-read-scope-model.md) (firm-side RBAC reads, NOT portal read-model), [ADR-303](../adr/ADR-303-mcp-authentication.md) (OAuth 2.1 resource server reusing Keycloak), [ADR-304](../adr/ADR-304-mcp-tenant-isolation-capability-gating.md) (`MCP_ACCESS` front door + per-domain gates), [ADR-305](../adr/ADR-305-mcp-popia-consent-audit.md) (`IntegrationDomain.MCP` + consent table + `mcp.*` audit), [ADR-306](../adr/ADR-306-mcp-tool-contract-design.md) (thin MCP DTOs, pagination caps, name-versioned schemas)
> **Predecessors**: Phase 21 (`OrgIntegration`, `IntegrationDomain`, `OrgIntegrationRepository`, Integrations settings UI), Phase 72/74 (`AiFirmProfile`/`AiFirmProfileService`, `AiExecutionGate`, `ai.*` audit family — reused as the firm-profile resource + reserved write-back substrate), Phase 6 (`AuditEvent` append-only), Phase 18/19/20 (`ProjectAccessService`, `@RequiresCapability`, `RequestScopes.CAPABILITIES`, `ActorContext`), Phase 1/13 (`TenantFilter`/`MemberFilter`, schema-per-tenant `search_path`)
> **Starting epic**: 562. Last completed: 561 (Phase 77).
> **Migration high-water at phase start**: tenant **V128** (`V128__compliance_audit_single_published_index.sql`). Phase 78 ships **one** tenant migration. ⚠️ **The architecture doc (§11.7) names this `V100`, but the live `db/migration/tenant/` high-water is V128. Use the next free number `V129`.** No global migrations. The `IntegrationDomain.MCP` enum constant and `MCP_ACCESS` capability are **code changes, not migrations**.

Phase 78 opens the second head on the same body: **Claude calls Kazi.** It ships a Kazi-hosted, read-only **MCP server** in-process in the `backend` app at `/mcp`, exposing the firm's own Kazi data (matters, clients, time, trust, compliance, documents, invoices, firm profile) to the firm's own Claude client, authenticated per-user via the existing Keycloak OAuth + `TenantFilter`/`MemberFilter` pipeline and scoped by the member's existing RBAC + project access. Every session and tool call is audited. MCP is per-tenant opt-in behind a recorded POPIA data-egress consent. Kazi makes **zero** outbound LLM calls in this phase. The catalogue is 3 resources + 13 tools; all are read-only by construction. This is additive — a firm without the connector loses nothing.

---

## Open Questions (resolve at implementation time)

- **Migration number.** Architecture §11.7 says `V100`; live tenant high-water is **V128**. This breakdown uses **V129**. The Builder must confirm the next free tenant number at branch time and use it (filename + Flyway version must match).
- **Role-capability seeding mechanism for `MCP_ACCESS`** ([ADR-304], §11.2). Confirm whether OWNER/ADMIN capability sets are `ALL`-expanded by `OrgRoleService` (then the enum addition flows automatically — code-only, no seeder change) or row-materialised per-tenant (then a **seeder backfill**, not a migration, is needed). Inspect `orgrole/OrgRoleService.java` + `seeder/`. **No global migration either way.**
- **Spring AI 2.0 milestone Boot-4 readiness** ([ADR-300]). S1 must prove the `initialize` handshake on Boot 4.0.2 before any catalogue work. If `spring-ai-starter-mcp-server-webmvc` (2.0.x milestone) is not Boot-4-ready, fall back to a **hand-rolled JSON-RPC-over-Streamable-HTTP** endpoint reusing the same filter chain + tool registry (fallback only — verify the milestone repo must be added to `pom.xml`).
- **Keycloak OAuth client + protected-resource-metadata** ([ADR-303]). The exact authorization-code-with-PKCE / dynamic-client-registration handshake the Claude client expects, plus token audience, must be validated against the MCP spec + Keycloak in S1. Confirm the edge forwards the `Authorization` bearer header and the `/.well-known/oauth-protected-resource` path unaltered ([ADR-301]).
- **Controller-private DTOs / entity returns** (§11.3 reconciliation callout). The requirements file's §3.2 has 7 wrong service signatures; use the **verified** FQNs in architecture §11.4. `ProjectResponse` is controller-private (`project/ProjectController.java`); `CustomerService.listCustomers()` returns raw `Customer` entities; `ClientLedgerService.getClientTransactionHistory` arg order is `(customerId, trustAccountId, Pageable)`; etc. MCP defines its own thin DTOs in `mcp/dto/`.
- **`ActorContext.fromRequestScopes()` exact API.** Architecture §11.3 shows `ActorContext.fromRequestScopes()`. Confirm the precise factory name against `multitenancy/ActorContext.java` (it may be `ActorContext.fromRequestScopes()` or a `RequestScopes`-backed constructor).
- **`mcp/` package placement.** New top-level package `io.b2mash.b2b.b2bstrawman.mcp` with sub-packages `mcp/tool/`, `mcp/resource/`, `mcp/dto/`, `mcp/consent/`. Register in any ArchUnit package-dependency rules if present.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 562 | MCP Runtime + Transport + Auth Skeleton | Backend | -- | L | 562A, 562B, 562C | **Done** — 562A (PR #1454), 562B (PR #1455), 562C (PR #1456) |
| 563 | Read Catalogue Batch 1 — Project-Access + Org-Wide Tools | Backend | 562 | L | 563A, 563B | **Done** — 563A, 563B (PR #1457) |
| 564 | Read Catalogue Batch 2 — Capability-Gated Tools + Firm-Profile Resource | Backend | 562 (563 recommended) | L | 564A, 564B | **Done** — 564A, 564B (PR #1458) |
| 565 | Enablement + POPIA Consent (Backend) | Backend | 562 | M | 565A, 565B | **Done** — 565A, 565B (PR #1459) |
| 566 | MCP Connector Settings Card (Frontend) | Frontend | 565 | M | 566A | |
| 567 | Audit / Observability + Isolation / Read-Only Hardening | Backend | 562, 563, 564, 565 | M | 567A, 567B | |

**Slice count: 12** (5 architecture capability slices S1–S5 expanded to 6 epics / 12 numbered slices to enforce the 6–12 files / ~800 LOC slice budget; S1→Epic 562 split into 3 slices; S2→Epic 563; S3→Epic 564; S4 split into backend Epic 565 + frontend Epic 566; S5→Epic 567).

**Maps to architecture capability slices**: S1 → 562 · S2 → 563 · S3 → 564 · S4 → 565 (backend) + 566 (frontend) · S5 → 567.

---

## Dependency Graph

```
Predecessors already complete:
  Phase 21 (OrgIntegration, IntegrationDomain, OrgIntegrationRepository, Integrations settings UI)
  Phase 72/74 (AiFirmProfile/AiFirmProfileService, AiExecutionGate, ai.* audit)
  Phase 6 (AuditEvent), Phase 18/19/20 (ProjectAccessService, @RequiresCapability, ActorContext, RequestScopes)
  Phase 1/13 (TenantFilter, MemberFilter, schema-per-tenant search_path)
                                 │
                                 ▼
        ┌────────────────────────────────────────────────────┐
        │ EPIC 562 — MCP RUNTIME + TRANSPORT + AUTH SKELETON   │   ← risk-bearing, lands FIRST
        │  [562A  pom.xml deps + Spring AI starter + STREAMABLE │      (proves initialize handshake +
        │         + McpServerConfig (identity/caps) + filter-   │       token→RequestScopes binding
        │         chain wiring so /mcp binds RequestScopes]     │       BEFORE any tools exist)
        │              │                                        │
        │              ▼                                        │
        │  [562B  MCP_ACCESS capability + seeding +             │
        │         OAuth protected-resource-metadata endpoint +  │
        │         initialize/tools-list/resources-list handshake│
        │         (empty registry) + mcp.session.opened audit]  │
        │              │                                        │
        │              ▼                                        │
        │  [562C  MCP DTO base + pagination-normalisation helper│
        │         + global response-size ceiling + read-only    │
        │         registry scaffold + auth-enforcement tests]   │
        └───────┬───────────────────┬───────────────────┬──────┘
                │                   │                   │
        ┌───────▼──────┐   ┌────────▼────────┐   ┌──────▼───────────────┐
        │ EPIC 563     │   │ EPIC 564        │   │ EPIC 565 (backend)   │
        │ Catalogue    │   │ Catalogue       │   │ Enablement + POPIA   │
        │ batch 1      │   │ batch 2         │   │ consent              │
        │ (project +   │   │ (capability-    │   │  [565A V129 migration│
        │  org-wide)   │   │  gated + profile│   │   + McpEgressConsent  │
        │  [563A list/ │   │  resource)      │   │   entity + repo]      │
        │   get matter,│   │  [564A trust +  │   │       │               │
        │   clients,   │   │   invoices +    │   │       ▼               │
        │   docs] →    │   │   unbilled] →   │   │  [565B IntegrationDom.│
        │  [563B activ-│   │  [564B compli-  │   │   MCP + McpConsent-    │
        │   ity + matter│   │   ance + audit  │   │   Service + McpEnable-│
        │   /client res]│   │   + firm-profile│   │   mentService + effec-│
        │              │   │   resource]     │   │   tive-state gate]    │
        └──────┬───────┘   └────────┬────────┘   └──────────┬───────────┘
               │                    │                       │
               │                    │            ┌──────────▼───────────┐
               │                    │            │ EPIC 566 (frontend)  │
               │                    │            │ MCP settings card    │
               │                    │            │  [566A page.tsx +    │
               │                    │            │   actions.ts +       │
               │                    │            │   integrations hub   │
               │                    │            │   link + conn docs]  │
               │                    │            └──────────┬───────────┘
               └────────────────────┴───────────┬──────────┘
                                                 ▼
                          ┌─────────────────────────────────────────┐
                          │ EPIC 567 — AUDIT/OBS + HARDENING          │
                          │  [567A mcp.tool.invoked / mcp.access.     │
                          │   denied metadata finalise (params        │
                          │   summary sanitisation, row counts,       │
                          │   entity refs) + per-tenant metrics +     │
                          │   audit-emission tests]                   │
                          │              │                            │
                          │              ▼                            │
                          │  [567B cross-tenant isolation test +      │
                          │   capability-gating test + read-only-     │
                          │   registry assertion + pagination-cap     │
                          │   test + module-gating tolerance test +   │
                          │   manual-QA evidence (no live Claude/CI)] │
                          └─────────────────────────────────────────┘
```

**Parallel opportunities:**
- **Epic 562 lands first and alone** — it retires the Spring AI 2.0 milestone + filter-chain auth risk before anything else is built. All other epics depend on it.
- Once 562 merges, **Epics 563, 564, and 565 can run fully in parallel** (separate tool packages / separate consent + enablement package; only shared dependency is the 562C DTO/pagination helper). 564 *recommends* 563 first only to reuse the shared DTO/pagination utilities, but does not hard-block on it.
- **Epic 566 (frontend)** depends only on 565 (backend server actions + effective-state). It can run in parallel with 563/564.
- **Epic 567 (hardening)** is the convergence point — it depends on 562–565 (it asserts isolation/capability/read-only/audit across the full catalogue + enablement gate). It does NOT depend on 566 (frontend).

---

## Implementation Order

### Stage 1 — Runtime + Auth Skeleton (sequential; the risk-retirement gate)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **562A** | ✅ **Done** (PR #1454). `backend/pom.xml`: Spring AI 2.0 milestone BOM + repo + `spring-ai-starter-mcp-server-webmvc`; `spring.ai.mcp.server.protocol=STREAMABLE`. `mcp/McpServerConfig` (server identity `kazi`, version, read-only instructions, advertise `resources`+`tools` only). Spring Security wiring so `/mcp` runs the **full** filter chain (NOT `/internal/*`). |
| 1b | **562B** | ✅ **Done** (PR #1455). `MCP_ACCESS` added to `orgrole/Capability.java` + seeding (confirm `ALL`-expand vs seeder backfill); OAuth `/.well-known/oauth-protected-resource` metadata endpoint; `initialize` / `tools/list` / `resources/list` handshake with an **empty** registry; `mcp.session.opened` audit emission. |
| 1c | **562C** | ✅ **Done** (PR #1456). `mcp/dto/` DTO base + `McpPage` envelope (`items, page, size, total, truncated`); pagination-normalisation helper (hard max 50 / audit 200, slices unbounded `List`); global per-call response-size ceiling ("narrow your query" error); read-only registry scaffold (registration point); auth-enforcement + handshake integration tests. |

### Stage 2 — Catalogue + Enablement (parallel after 562)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 2a | **563A** | ✅ **Done** (PR #1457). Tools `list_matters`, `get_matter`, `list_clients`, `get_client`, `search_documents`, `get_document_url` + their MCP DTOs; project-access + org-wide gating; `ActorContext.fromRequestScopes()` propagation; `mcp.tool.invoked` audit; integration tests. | 564A, 565A |
| 2b | **563B** | ✅ **Done** (PR #1457). Tool `get_matter_activity` + resources `kazi://matter/{id}`, `kazi://client/{id}`; activity DTO; project-access gating; integration tests. | 564B, 565B |
| 2c | **564A** | ✅ **Done** (PR #1458). Tools `get_trust_balance`, `list_trust_transactions`, `list_invoices`, `get_invoice`, `get_unbilled_time` + DTOs; `VIEW_TRUST` (+ trust module-gate tolerance) + `INVOICING` gating; integration tests. | 563A, 565A |
| 2d | **564B** | ✅ **Done** (PR #1458). Tools `list_compliance_gaps`, `get_audit_events` + resource `kazi://firm-profile` (`AI_MANAGE` gate) + DTOs; `CUSTOMER_MANAGEMENT` + `TEAM_OVERSIGHT` gating; integration tests. | 563B, 565B |
| 2e | **565A** | ✅ **Done** (PR #1459). `V129` consent migration + `mcp/consent/McpEgressConsent` entity (AiFirmProfile pattern) + `McpEgressConsentRepository`; migration + entity round-trip tests. | 563A, 564A |
| 2f | **565B** | ✅ **Done** (PR #1459). `IntegrationDomain.MCP("kazi")` constant; `McpConsentService` (append GRANTED/REVOKED, latest-decision lookup); `McpEnablementService` (effective state = `OrgIntegration.enabled` AND latest consent GRANTED); per-tool/per-resource effective-state gate (inline guard — orchestrator-approved; Spring AI 2.0.0-M6 has no tool-call interceptor); tests. | 563B, 564B |

### Stage 3 — Frontend Settings Card (after 565)

| Order | Slice | Summary |
|-------|-------|---------|
| 3a | **566A** | `integrations/mcp/page.tsx` "Claude / MCP Connector" card (status, server URL, connection instructions for Claude Desktop/Code, consent metadata, revoke); `integrations/mcp/actions.ts` (`enableMcpAction`, `revokeMcpAction`, `getMcpStatusAction`); link the card on the integrations hub `page.tsx`; unit tests. |

### Stage 4 — Convergence: Audit/Observability + Hardening (after 562–565)

| Order | Slice | Summary |
|-------|-------|---------|
| 4a | **567A** | Finalise `mcp.tool.invoked` (params summary sanitisation — ids/enums only, never free-text PII; result row count; entity refs) + `mcp.access.denied` (tool, denied gate, actor) metadata; per-tenant MCP call-count + latency metrics (no PII labels); audit-emission integration tests. |
| 4b | **567B** | Hardening test suite: cross-tenant isolation test; capability-gating test per regime; read-only registry-assertion test; pagination-cap + response-ceiling test; trust module-gating tolerance test; enablement/revoke refusal test. Manual-QA step (real Claude Desktop against a dev tenant) documented. No live Claude in CI. |

### Timeline

```
Stage 1:  [562A → 562B → 562C]                                         ← risk-retirement gate (sequential)
Stage 2:  [563A → 563B]  //  [564A → 564B]  //  [565A → 565B]           ← catalogue + enablement (parallel after 562)
Stage 3:  [566A]                                                        ← frontend card (after 565)
Stage 4:  [567A → 567B]                                                 ← audit/obs + hardening (after 562–565)
```

A realistic cadence: 562A days 1–3 (milestone-risk spike), 562B days 3–5, 562C days 5–7; then 563/564/565 tracks days 7–14 in parallel; 566 days 14–16 (after 565); 567A days 14–16, 567B days 16–19.

---

## Epic 562: MCP Runtime + Transport + Auth Skeleton

**Goal**: Stand up the Spring AI MCP server in-process at `/mcp`, prove the `initialize` handshake on Boot 4.0.2, and prove that an inbound Keycloak bearer token binds `RequestScopes` (TENANT_ID / MEMBER_ID / ORG_ROLE / CAPABILITIES) through the full filter chain **before** any tool executes — the single biggest integration risk, retired in isolation with an empty tool registry. Add the `MCP_ACCESS` front-door capability, the OAuth protected-resource-metadata endpoint, the `mcp.session.opened` audit event, and the foundational MCP DTO base + pagination-normalisation helper + read-only registry scaffold that every later tool epic depends on.

**References**: Architecture §11.3 (MCP lifecycle, ActorContext propagation), §11.6 (API surface, `/mcp` on JWT path NOT `/internal/*`, protected-resource metadata, `initialize` request/response), §11.8a/§11.8b (sequence diagrams), §11.12 (backend changes: `pom.xml`, `McpServerConfig`, capability), §11.13 S1; [ADR-300], [ADR-301], [ADR-303], [ADR-304], [ADR-306].

**Dependencies**: None (first epic; depends only on existing Phase 1/13 filter chain + Phase 6 audit).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **562A** | 562A.1–562A.3 | ~4 backend files (1 `pom.xml` modify + 1 config + 1 security config modify + 2 yml modify) | Spring AI 2.0 milestone starter + STREAMABLE protocol; `McpServerConfig` (identity, version, read-only instructions, advertise `resources`+`tools`); Spring Security wiring so `/mcp` runs the full filter chain. | **Done** (PR #1454) |
| **562B** | 562B.1–562B.4 | ~6 backend files (1 enum modify + 1 seeder modify/verify + 1 metadata endpoint + 1 audit emitter + 1 handshake config + 1 test) | `MCP_ACCESS` capability + seeding; OAuth protected-resource-metadata endpoint; `initialize`/`tools/list`/`resources/list` handshake with empty registry; `mcp.session.opened` audit. | **Done** (PR #1455) |
| **562C** | 562C.1–562C.4 | ~7 backend files (1 DTO base + 1 page envelope + 1 pagination helper + 1 registry scaffold + 1 size-ceiling + 2 tests) | MCP DTO base; `McpPage` envelope; pagination-normalisation helper + global response-size ceiling; read-only registry scaffold; auth-enforcement + handshake integration tests. | **Done** (PR #1456) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 562A.1 | Add Spring AI 2.0 milestone MCP server starter | `backend/pom.xml` (modify) | verified by 562C.4 (handshake test) | existing `pom.xml` dependency-management + the Phase 72 `526B` Anthropic SDK addition as a "new milestone dependency" precedent | Add Spring AI 2.0 milestone BOM + Spring milestone repository; add `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`. **Pin the exact milestone version** (e.g. 2.0.0-M6); document GA-upgrade pass. ⚠️ Milestone-maturity risk ([ADR-300]): if not Boot-4.0.2-ready, fall back to a hand-rolled JSON-RPC-over-Streamable-HTTP endpoint reusing the same filter chain + tool registry (fallback only). WebMVC (servlet) variant is mandatory — WebFlux forks the request-context model. |
| 562A.2 | Add MCP config to `application.yml` + `application-test.yml` | `backend/src/main/resources/application.yml` (modify), `backend/src/test/resources/application-test.yml` (modify) | covered by 562C.4 | existing `kazi:` / `spring:` config blocks; Phase 75 `547B.4` config-addition pattern | Set `spring.ai.mcp.server.protocol=STREAMABLE`; server name `kazi`, semantic version, read-only instructions string. Test profile: ensure the MCP server is enabled for integration tests against MockMvc/WebTestClient. |
| 562A.3 | Create `McpServerConfig` + Spring Security wiring | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpServerConfig.java` (new), `backend/.../security/...SecurityConfig.java` (modify) | covered by 562C.4 | existing `security/` filter-chain config; [ADR-301]/[ADR-303] | `@Configuration` advertising `resources` + `tools` capabilities only (NO `prompts`/`sampling`). Wire `/mcp` into the **authenticated** Spring Security chain so `BearerTokenAuthFilter` → `ClerkJwtAuthenticationConverter` → `TenantFilter` → `MemberFilter` bind `RequestScopes` before any tool runs. **Critical**: `/mcp` must NOT be added to `MemberFilter.shouldNotFilter`'s exclusion set (`/internal/*`, `/actuator/*`, `/portal/*`) — that would leave `MEMBER_ID`/`ORG_ROLE`/`CAPABILITIES` unbound. Verify the edge forwards `Authorization` + `/.well-known/oauth-protected-resource` unaltered. |
| 562B.1 | Add `MCP_ACCESS` capability + seeding | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` (modify), `backend/.../seeder/...` (modify/verify) | 562C.4 | Phase 72 `527A` added `AI_MANAGE`/`AI_EXECUTE`/`AI_REVIEW` to this exact enum + seeded | Add `MCP_ACCESS` enum constant (front-door gate; default roles OWNER, ADMIN). **Confirm the seeding mechanism** ([ADR-304], §11.2): if OWNER/ADMIN resolve `ALL` pseudo-capability (then no seeder change — enum addition flows automatically), or if role-capability rows are materialised per-tenant (then a seeder backfill into OWNER/ADMIN role definitions). **No global migration.** |
| 562B.2 | Create OAuth protected-resource-metadata endpoint | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpProtectedResourceMetadataController.java` (new) | 562C.4 | [ADR-303]; existing Keycloak issuer config | Publish `/.well-known/oauth-protected-resource` pointing the Claude client at the Keycloak authorization server (`{ authorization_servers: [Keycloak issuer] }`). Reuse existing Keycloak JWT validation (`ClerkJwtAuthenticationConverter`). Verify PKCE/dynamic-client-registration/token-audience handshake against the MCP spec + Keycloak (S1 spike). |
| 562B.3 | Wire `initialize` handshake + advertise capabilities + `mcp.session.opened` audit | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpSessionAuditListener.java` (new), `backend/.../mcp/McpServerConfig.java` (modify) | 562C.4 | §11.6 `initialize` request/response (do NOT hard-code `protocolVersion` — let the SDK supply it); `audit/AuditEventBuilder.java` for emission | `initialize` advertises `serverInfo{ name:"kazi", version }`, `capabilities{ resources:{}, tools:{} }`, and the read-only `instructions` string. Emit `mcp.session.opened` (actor member id, client name/version) via the Phase 6 `AuditEvent`. `initialize` **succeeds even without `MCP_ACCESS`** (so the client can connect); every later tool returns an authorization error without it. Empty (or one trivial) registry at this stage. |
| 562B.4 | (folded into 562C.4) | — | — | — | Handshake/auth tests live with 562C scaffold. |
| 562C.1 | Create MCP DTO base + `McpPage` envelope | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpPage.java` (new), `backend/.../mcp/dto/McpError.java` (new) | 562C.4 | [ADR-306]; portal `Portal*View` projections as **shape reference only** (NOT a dependency, per [ADR-302]) | `McpPage<T>(List<T> items, int page, int size, long total, boolean truncated)` envelope for all list tools. `McpError(String error, String message)` for non-leaking tool-level errors (`isError:true`). Flat named fields, short enums, money as **minor units + currency**, ISO-8601 dates. No ORM entity serialisation. |
| 562C.2 | Create pagination-normalisation helper + global response-size ceiling | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpPagination.java` (new) | 562C.4 | §11.4 / §11.13 (hard max 50, audit 200; mirror controller caps — activity 50, audit 200) | Helper that takes `page`/`size` params, clamps `size` to the hard server max (50; 200 for audit), and **slices unbounded firm-side `List` results** at the MCP boundary so an LLM never receives an unbounded blob. Sets `truncated`/`total`. A global per-call response-size ceiling returns a structured `McpError("response_too_large","narrow your query")` rather than a truncated blob. |
| 562C.3 | Create read-only registry scaffold | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/McpToolRegistry.java` (new) | 562C.4 + 567B.3 | §11.3 read-only-by-construction; Spring AI `@Tool` auto-registration | Registration point where `@Tool`-annotated `@Component` methods are discovered. No mutating tool may register. Expose a way to iterate registered tools for the read-only assertion test (lands fully in 567B). At this stage the registry is empty (or one trivial read tool to prove `tools/list`). |
| 562C.4 | Integration tests — handshake + auth enforcement + session audit | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpHandshakeIntegrationTest.java` (new), `backend/.../mcp/McpAuthEnforcementTest.java` (new) | ~6 tests: (1) `initialize` negotiates `resources`+`tools` only, advertises `kazi` + read-only instructions; (2) `tools/list`/`resources/list` return (empty/trivial) registry; (3) no/invalid token → 401 + `WWW-Authenticate` resource-metadata hint; (4) valid token binds `RequestScopes` (assert via a probe); (5) `MCP_ACCESS`-less member → tool returns authorization error (front door); (6) `mcp.session.opened` emitted on initialize | Phase 72 `527A.7` `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` + `TestMemberHelper`/`TestJwtFactory`; drive `/mcp` via MockMvc/WebTestClient (NO live Claude) | This slice **proves the auth linchpin in isolation** before any tools exist (the biggest risk, derisked first). |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpServerConfig.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpProtectedResourceMetadataController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpSessionAuditListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpPage.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpError.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpPagination.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/McpToolRegistry.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpHandshakeIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpAuthEnforcementTest.java`

**Modify (backend):**
- `backend/pom.xml` — Spring AI 2.0 milestone BOM + repo + `spring-ai-starter-mcp-server-webmvc`
- `backend/src/main/resources/application.yml` + `backend/src/test/resources/application-test.yml` — MCP server config
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` — add `MCP_ACCESS`
- `backend/.../security/...SecurityConfig.java` — wire `/mcp` into the authenticated chain
- `backend/.../seeder/...` — `MCP_ACCESS` backfill if role-capability rows are materialised

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — scope binding (`runForTenantWithMember`, [ADR-T008])
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ActorContext.java` — `fromRequestScopes()` factory
- `backend/.../orgrole/Capability.java` + `RequiresCapability.java` + `CapabilityAuthorizationService.java` — capability model
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — audit emission
- `backend/.../integration/ai/profile/AiFirmProfileController.java` — `527A` controller + capability pattern precedent

### Architecture Decisions

- **Spring AI 2.0 milestone starter + STREAMABLE, hand-rolled fallback** ([ADR-300]) — protocol correctness is the SDK's job, not ours. Milestone risk is bounded to the transport adapter; the catalogue/DTOs/gating/audit survive an SDK swap.
- **In-process at `/mcp` on the authenticated path** ([ADR-301], [ADR-303]) — the only topology that inherits `TenantFilter`/`MemberFilter`/`RequestScopes.CAPABILITIES` for free; `/mcp` deliberately NOT in `MemberFilter.shouldNotFilter`.
- **`MCP_ACCESS` is a pure front door** ([ADR-304]) — `initialize` succeeds without it; tools deny without it; it never widens any per-domain gate.
- **MCP owns its DTO + pagination layer** ([ADR-302], [ADR-306]) — firm-side returns are uneven (entities, controller-private records, unbounded lists), so normalisation happens at the MCP boundary, not by reusing portal projections.

### Non-scope

- No tools/resources beyond a trivial probe (land in 563/564).
- No enablement/consent gate (lands in 565).
- No `mcp.tool.invoked`/`mcp.access.denied` metadata finalisation (lands in 567).

---

## Epic 563: Read Catalogue Batch 1 — Project-Access + Org-Wide Tools

**Goal**: Ship the project-access-gated and org-wide read tools and the two browsable entity resources, each delegating to the verified firm-side service with an `ActorContext.fromRequestScopes()`-propagated member identity, projecting through thin MCP DTOs, normalising pagination, and emitting `mcp.tool.invoked`.

**References**: Architecture §11.4 (Resources + Tools, verified FQNs), §11.3 (reconciliation callout — use verified signatures, NOT requirements §3.2), §11.9 (permission model), §11.12 (tool/DTO patterns), §11.13 S2; [ADR-302], [ADR-306].

**Dependencies**: Epic 562 (runtime, DTO base, pagination helper, registry, `ActorContext` propagation).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **563A** | 563A.1–563A.4 | ~9 backend files (2 tool classes + 4 DTOs + 1 test + reuse helper) | `list_matters`, `get_matter` (MatterTools); `list_clients`, `get_client` (ClientTools); `search_documents`, `get_document_url` (DocumentTools); MCP DTOs; project-access + org-wide gating; `mcp.tool.invoked`. | **Done** (PR #1457) |
| **563B** | 563B.1–563B.3 | ~7 backend files (1 tool class + 2 resource handlers + 2 DTOs + 1 test) | `get_matter_activity` (ActivityTools); resources `kazi://matter/{id}`, `kazi://client/{id}`; activity DTO; project-access gating. | **Done** (PR #1457) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 563A.1 | Create `MatterTools` (`list_matters`, `get_matter`) + DTOs | `backend/.../mcp/tool/MatterTools.java` (new), `backend/.../mcp/dto/McpMatterDto.java` (new), `backend/.../mcp/dto/McpMatterListItem.java` (new) | 563A.4 | §11.12 conceptual `MatterTools`/`McpMatterDto`; verified `ProjectService` FQNs | `list_matters` → `ProjectService.listProjects(UUID view, String status, LocalDate dueBefore, UUID customerId, Map params, ActorContext actor)` → `List<ProjectResponse>` (controller-private — project to `McpMatterListItem`). **Always use the 6-arg filtered overload; pass null for unused filters. Do NOT fall back to `listProjects(ActorContext)` (returns `List<ProjectWithRole>`).** Slice via `McpPagination` (max 50). `get_matter` → `ProjectService.getProject(UUID id, ActorContext)` → `ProjectWithRole` → `McpMatterDto`. Project-access: `requireViewAccess` → 404 surfaced as `isError:true` non-leaking error. |
| 563A.2 | Create `ClientTools` (`list_clients`, `get_client`) + DTOs | `backend/.../mcp/tool/ClientTools.java` (new), `backend/.../mcp/dto/McpClientDto.java` (new), `backend/.../mcp/dto/McpClientListItem.java` (new) | 563A.4 | verified `CustomerService` FQNs (reconciliation callout) | `list_clients` → `CustomerService.listCustomers()` / `listCustomersByLifecycleStatus(LifecycleStatus)` → `List<Customer>` **entities** (project to `McpClientListItem`; replicate or **deliberately omit** the controller's tag/member-name enrichment — document which). `get_client` → `CustomerService.getCustomer(UUID id)` → `Customer` **+** `listProjectsForCustomer(...)` → `LinkedProjectResponse[]` (linked matters come from the **separate** call, NOT `getCustomer`). Org-wide gate (authenticated + `MCP_ACCESS`). |
| 563A.3 | Create `DocumentTools` (`search_documents`, `get_document_url`) + DTO | `backend/.../mcp/tool/DocumentTools.java` (new), `backend/.../mcp/dto/McpDocumentDto.java` (new) | 563A.4 | verified `DocumentService` FQNs; `PresignDownloadResult(String url, long expiresInSeconds)` | `search_documents` → project scope `DocumentService.listDocumentResponses(UUID projectId, ActorContext)` (project-access) / org/customer scope `listDocumentsByScope(String scope, UUID customerId)` (org-wide) → `List<DocumentResponse>` (**metadata only, no bytes**). `get_document_url` → `getPresignedDownloadUrl(UUID documentId, ActorContext)` → `PresignDownloadResult` (1-hour presigned S3 URL, **never inlined bytes**; access enforced in-service). |
| 563A.4 | Integration tests — batch 1 tools | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CatalogueBatch1ToolsTest.java` (new) | ~7 tests: (1) `list_matters` member sees only assigned, owner/admin sees all; (2) `get_matter` 404 for non-member (non-leaking); (3) `list_clients` org-wide; (4) `get_client` returns linked matters from separate call; (5) `search_documents` project-access vs org/customer scope; (6) `get_document_url` returns presigned URL, no bytes; (7) pagination cap (size clamped to 50, `truncated`/`total`) | 562C.4 test pattern; `TestMemberHelper`/`TestJwtFactory`; MockMvc/WebTestClient | Drive `/mcp` `tools/call` directly. Assert `mcp.tool.invoked` emitted. |
| 563B.1 | Create `ActivityTools` (`get_matter_activity`) + DTO | `backend/.../mcp/tool/ActivityTools.java` (new), `backend/.../mcp/dto/McpActivityItem.java` (new) | 563B.3 | verified `ActivityService.getProjectActivity(UUID projectId, String entityType, Instant since, Pageable, ActorContext)` → `Page<ActivityItem>` (calls `requireViewAccess` internally) | Project-access-gated. Map `Page<ActivityItem>` → `McpPage<McpActivityItem>` (default/max size 50). |
| 563B.2 | Create resource handlers `kazi://matter/{id}`, `kazi://client/{id}` | `backend/.../mcp/resource/MatterResource.java` (new), `backend/.../mcp/resource/ClientResource.java` (new) | 563B.3 | §11.4 resources; reuse `McpMatterDto`/`McpClientDto` from 563A | `resources/read` resolves `kazi://matter/{id}` → `ProjectService.getProject` → `McpMatterDto`; `kazi://client/{id}` → `CustomerService.getCustomer` + `listProjectsForCustomer` → `McpClientDto`. Same gates as the corresponding tools (project-access / org-wide). |
| 563B.3 | Integration tests — activity tool + resources | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/MatterClientResourceTest.java` (new) | ~5 tests: (1) `get_matter_activity` project-access + pagination; (2) `resources/list` returns the 2 resources (firm-profile lands in 564B); (3) `kazi://matter/{id}` read for member; (4) `kazi://matter/{id}` 404 for non-member; (5) `kazi://client/{id}` org-wide read | 562C.4 test pattern | |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/MatterTools.java`, `ClientTools.java`, `DocumentTools.java`, `ActivityTools.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/resource/MatterResource.java`, `ClientResource.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpMatterDto.java`, `McpMatterListItem.java`, `McpClientDto.java`, `McpClientListItem.java`, `McpDocumentDto.java`, `McpActivityItem.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CatalogueBatch1ToolsTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/MatterClientResourceTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` + `ProjectController.java` (controller-private `ProjectResponse`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` + `CustomerController.java` (`CustomerResponse.from` enrichment)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` (`PresignDownloadResult`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectAccessService.java` (`requireViewAccess`)

### Architecture Decisions

- **Firm-side delegation only** ([ADR-302]) — every tool calls the named firm-side service with an `ActorContext`; no new query logic; portal projections are shape reference only.
- **Thin DTOs, never entities** ([ADR-306]) — `ProjectWithRole`/`Customer` entities projected to flat MCP DTOs; controller-private `ProjectResponse` is NOT reused.
- **404 security-by-obscurity** — `requireViewAccess` 404 surfaced as a non-leaking tool-level `isError:true`, never disclosing whether a matter exists.

### Non-scope

- Capability-gated tools + firm-profile resource (Epic 564).
- Enablement/consent gate (Epic 565).
- `mcp.tool.invoked` metadata finalisation (Epic 567).

---

## Epic 564: Read Catalogue Batch 2 — Capability-Gated Tools + Firm-Profile Resource

**Goal**: Ship the capability-gated read tools (trust, invoices, unbilled, compliance, audit) and the `kazi://firm-profile` resource, each deferring to its domain's existing capability gate, with the trust tools tolerating the module-disabled case gracefully for non-legal tenants.

**References**: Architecture §11.4 (verified FQNs), §11.3 (reconciliation callout, module-gating caveat), §11.9 (permission model — `INVOICING`/`VIEW_TRUST`/`CUSTOMER_MANAGEMENT`/`TEAM_OVERSIGHT`/`AI_MANAGE`), §11.13 S3; [ADR-304] [D1] (firm-profile retains `AI_MANAGE`), [ADR-306].

**Dependencies**: Epic 562 (runtime + DTO/pagination helper). Epic 563 recommended (shared DTO/pagination utilities) but not a hard block.

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **564A** | 564A.1–564A.4 | ~9 backend files (2 tool classes + 4 DTOs + 1 test) | `get_trust_balance`, `list_trust_transactions` (TrustTools, `VIEW_TRUST` + module-gate tolerance); `list_invoices`, `get_invoice`, `get_unbilled_time` (BillingTools, `INVOICING`); DTOs. | **Done** (PR #1458) |
| **564B** | 564B.1–564B.4 | ~8 backend files (2 tool classes + 1 resource + 3 DTOs + 1 test) | `list_compliance_gaps` (`CUSTOMER_MANAGEMENT`), `get_audit_events` (`TEAM_OVERSIGHT`); resource `kazi://firm-profile` (`AI_MANAGE`); DTOs. | **Done** (PR #1458) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 564A.1 | Create `TrustTools` (`get_trust_balance`, `list_trust_transactions`) + DTOs | `backend/.../mcp/tool/TrustTools.java` (new), `backend/.../mcp/dto/McpTrustBalanceDto.java` (new), `backend/.../mcp/dto/McpTrustTransactionItem.java` (new) | 564A.4 | verified `ClientLedgerService` FQNs (reconciliation callout) | `get_trust_balance` → `ClientLedgerService.getClientLedger(UUID customerId, UUID trustAccountId)` → `ClientLedgerCardResponse`; total → `getTotalTrustBalance(UUID trustAccountId)` (NOT `getTotalBalance`). `list_trust_transactions` → `getClientTransactionHistory(UUID customerId, UUID trustAccountId, Pageable)` — **customerId FIRST** → `Page<TrustTransactionResponse>`. `VIEW_TRUST` gate. **Module-gating tolerance**: every method calls `moduleGuard.requireModule` first; for non-legal tenants return a structured `McpError("module_disabled","trust accounting is not enabled for this firm")`, not a stack trace. Money as minor units + currency. |
| 564A.2 | Create `BillingTools` (`list_invoices`, `get_invoice`, `get_unbilled_time`) + DTOs | `backend/.../mcp/tool/BillingTools.java` (new), `backend/.../mcp/dto/McpInvoiceDto.java` (new), `backend/.../mcp/dto/McpUnbilledSummaryItem.java` (new) | 564A.4 | verified `InvoiceService` + `UnbilledTimeService` + `setupstatus.UnbilledTimeSummaryService` FQNs | `list_invoices` → `InvoiceService.findAll(UUID customerId, InvoiceStatus status, UUID projectId)` → `List<InvoiceResponse>` (slice, max 50). `get_invoice` → `findById(UUID)` + `getPaymentEvents(UUID)` (cap lines/payments, signal truncation). `get_unbilled_time` → firm-wide `UnbilledTimeService.getUnbilledSummary(LocalDate periodFrom, LocalDate periodTo, String currency)` → `List<CustomerUnbilledSummary>`; per-matter (when `projectId` set) → `UnbilledTimeSummaryService.getProjectUnbilledSummary(UUID projectId)` (also project-access). `INVOICING` gate. |
| 564A.3 | (folded into 564A.1/.2) | — | — | — | DTOs created alongside their tools. |
| 564A.4 | Integration tests — trust + billing tools | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CatalogueBatch2TrustBillingTest.java` (new) | ~7 tests: (1) `get_trust_balance` with `VIEW_TRUST`; (2) `MCP_ACCESS` member without `VIEW_TRUST` → denied + `mcp.access.denied`; (3) trust tool on non-legal tenant → clean module-disabled response; (4) `list_trust_transactions` pagination (customerId-first arg order); (5) `list_invoices` `INVOICING` gate + pagination; (6) `get_invoice` caps lines/payments; (7) `get_unbilled_time` firm-wide vs per-matter path | 562C.4 pattern; provision a non-legal tenant for the module-gating case | |
| 564B.1 | Create `ComplianceTools` (`list_compliance_gaps`) + DTO | `backend/.../mcp/tool/ComplianceTools.java` (new), `backend/.../mcp/dto/McpComplianceGapDto.java` (new) | 564B.4 | verified `informationrequest.InformationRequestService.getFicaStatus(UUID customerId)` → `FicaStatusResponse` + `checklist.ChecklistInstanceService.getInstancesWithItemsForCustomer(UUID customerId)` (NOT `CompliancePackService`) | `CUSTOMER_MANAGEMENT` gate. Project FICA status + per-item breakdown to a capped, truncation-signalling DTO. |
| 564B.2 | Create `AuditTools` (`get_audit_events`) + DTO | `backend/.../mcp/tool/AuditTools.java` (new), `backend/.../mcp/dto/McpAuditEventItem.java` (new) | 564B.4 | verified `audit.AuditService.findEventsEnriched(AuditEventFilter, Pageable)` → `Page<EnrichedAuditEvent>` (NOT `findEventsForCustomer`, which is a raw `Stream<AuditEvent>`) | `TEAM_OVERSIGHT` gate. Per-customer via a customer-scoped `AuditEventFilter`. **Ordering is fixed `occurredAt DESC`** (Pageable `Sort` discarded by `DatabaseAuditService`). Max size 200. |
| 564B.3 | Create `kazi://firm-profile` resource + DTO | `backend/.../mcp/resource/FirmProfileResource.java` (new), `backend/.../mcp/dto/McpFirmProfileDto.java` (new) | 564B.4 | verified `integration.ai.profile.AiFirmProfileService.getOrCreateProfile()` → `AiFirmProfile` entity | Project to `{ practiceAreas[], jurisdiction, riskCalibration, houseStyleNotes, feeEstimationNotes }` — **NOT** budget/key/model fields. **`AI_MANAGE` gate RETAINED** ([D1], not relaxed): a member with `MCP_ACCESS` but no `AI_MANAGE` gets a clean auth error **on this resource only**. |
| 564B.4 | Integration tests — compliance + audit + firm-profile | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/CatalogueBatch2ComplianceAuditTest.java` (new) | ~6 tests: (1) `list_compliance_gaps` `CUSTOMER_MANAGEMENT` gate; (2) `get_audit_events` `TEAM_OVERSIGHT` gate + fixed `occurredAt DESC` ordering + max 200; (3) `kazi://firm-profile` read with `AI_MANAGE`; (4) firm-profile denied for `MCP_ACCESS`-only member ([D1]); (5) firm-profile DTO excludes budget/key/model fields; (6) `resources/list` now returns all 3 resources | 562C.4 pattern | |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/TrustTools.java`, `BillingTools.java`, `ComplianceTools.java`, `AuditTools.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/resource/FirmProfileResource.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpTrustBalanceDto.java`, `McpTrustTransactionItem.java`, `McpInvoiceDto.java`, `McpUnbilledSummaryItem.java`, `McpComplianceGapDto.java`, `McpAuditEventItem.java`, `McpFirmProfileDto.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CatalogueBatch2TrustBillingTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/CatalogueBatch2ComplianceAuditTest.java`

**Read for context:**
- `backend/.../verticals/legal/trustaccounting/ledger/ClientLedgerService.java` (module-gated; arg orders)
- `backend/.../invoice/InvoiceService.java`, `backend/.../invoice/UnbilledTimeService.java`, `backend/.../setupstatus/UnbilledTimeSummaryService.java`
- `backend/.../informationrequest/InformationRequestService.java`, `backend/.../checklist/ChecklistInstanceService.java`
- `backend/.../audit/AuditService.java` + `DatabaseAuditService` + `AuditEventFilter.java` + `AuditEventMetadataResolver.java`
- `backend/.../integration/ai/profile/AiFirmProfileService.java`

### Architecture Decisions

- **Firm-profile retains `AI_MANAGE`** ([ADR-304] [D1]) — house-style grounding is not silently relaxed; clean auth error for `MCP_ACCESS`-only members on this resource only.
- **Trust module-gating tolerance** (§11.3 caveat) — trust tools appear in `tools/list` (registry is vertical-agnostic) but refuse gracefully for non-legal tenants.
- **Audit ordering is fixed `occurredAt DESC`** — the Pageable `Sort` is discarded by `DatabaseAuditService`; the DTO/test must reflect this.

### Non-scope

- Enablement/consent gate (Epic 565); metadata finalisation + hardening (Epic 567).

---

## Epic 565: Enablement + POPIA Consent (Backend)

**Goal**: Make MCP per-tenant opt-in behind a recorded POPIA data-egress consent. Add the `IntegrationDomain.MCP` enum + `OrgIntegration` row enablement flag (no adapter bean), the append-only `mcp_egress_consents` tenant table + entity/repo, a consent service, an effective-state service, and wire the effective-state check into every `tools/call` with a non-leaking refusal when disabled or consent absent/revoked.

**References**: Architecture §11.2 (domain model, consent table), §11.5 (enablement + POPIA flow, effective state), §11.7 (V129 migration — ⚠️ doc says V100), §11.8c (sequence), §11.12 (backend changes), §11.13 S4 (backend portion); [ADR-305] [D2][D4].

**Dependencies**: Epic 562 (gate hooks into the `tools/call` path; can land after 563/564 are mergeable).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **565A** | 565A.1–565A.3 | ~4 backend files (1 migration + 1 entity + 1 repo + 1 test) | `V129` consent migration (append-only `mcp_egress_consents` + 2 indexes); `McpEgressConsent` entity (AiFirmProfile pattern); `McpEgressConsentRepository`; migration + round-trip tests. | **Done** (PR #1459) |
| **565B** | 565B.1–565B.4 | ~6 backend files (1 enum modify + 1 consent service + 1 enablement service + 1 gate wiring + 1 test) | `IntegrationDomain.MCP("kazi")`; `McpConsentService` (append GRANTED/REVOKED, latest-decision); `McpEnablementService` (effective state); effective-state gate on every `tools/call` (non-leaking refusal). | **Done** (PR #1459) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 565A.1 | Create `V129` consent migration | `backend/src/main/resources/db/migration/tenant/V129__create_mcp_egress_consents.sql` (new) | 565A.3 | §11.7 SQL verbatim; existing tenant migrations (`V127__compliance_audit_tables.sql`) for format | ⚠️ **Confirm next free tenant number at branch time** (doc says V100; live high-water is V128 → use **V129**). `CREATE TABLE IF NOT EXISTS mcp_egress_consents` (id uuid PK, consented_by uuid NOT NULL, consented_at timestamptz NOT NULL, consent_version text NOT NULL, action text NOT NULL CHECK (action IN ('GRANTED','REVOKED')), created_at timestamptz NOT NULL DEFAULT now()). Indexes: `idx_mcp_egress_consents_consented_at (consented_at DESC)`, `idx_mcp_egress_consents_member (consented_by, consented_at DESC)`. Inline `CHECK` (idempotent), `CREATE INDEX IF NOT EXISTS`. No cross-module FK (house style). Tenant-scoped, runs per-schema. |
| 565A.2 | Create `McpEgressConsent` entity + repository | `backend/.../mcp/consent/McpEgressConsent.java` (new), `backend/.../mcp/consent/McpEgressConsentRepository.java` (new) | 565A.3 | `AiFirmProfile` entity convention exactly (§11.2): no tenant annotation, `@GeneratedValue(strategy = GenerationType.UUID)`, hand-rolled `created_at` via `@PrePersist`, protected no-arg constructor, domain-method construction, no Lombok, no setters | Append-only: a revoke inserts a new `REVOKED` row. Domain factory `grant(memberId, version)` / `revoke(memberId, version)`. Repository: `JpaRepository<McpEgressConsent, UUID>` + `findTopByOrderByConsentedAtDesc()` for latest-decision lookup. |
| 565A.3 | Integration tests — migration + consent entity | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/consent/McpEgressConsentTest.java` (new) | ~4 tests: (1) V129 migration runs clean per-schema; (2) entity round-trip; (3) append-only history (grant → revoke → re-grant = 3 rows); (4) latest-decision lookup returns newest by `consented_at` | Phase 72 `527A.7` migration-test pattern; tenant provisioning | |
| 565B.1 | Add `IntegrationDomain.MCP` constant | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java` (modify) | 565B.4 | existing enum constants; [ADR-305] [D4] | Add `MCP("kazi")`. **No `IntegrationAdapter` bean** (inbound read-exposure, nothing to resolve outbound). `org_integrations.domain` is already `varchar` storing the enum name — **no migration**. The `OrgIntegration(domain, providerSlug)` constructor sets `enabled=false` (disabled by default). |
| 565B.2 | Create `McpConsentService` | `backend/.../mcp/consent/McpConsentService.java` (new) | 565B.4 | Phase 72 `AiFirmProfileService` service pattern; `RequestScopes.requireMemberId()` | `@Service`. `grant(consentVersion)` appends a `GRANTED` row (consenting member from `RequestScopes`); `revoke()` appends a `REVOKED` row; `currentState()` returns latest-decision state. Used by the frontend server actions (Epic 566) and the enablement service. |
| 565B.3 | Create `McpEnablementService` + wire effective-state gate | `backend/.../mcp/McpEnablementService.java` (new), `backend/.../mcp/tool/McpToolRegistry.java` (modify, from 562C) | 565B.4 | §11.5 effective state; `OrgIntegrationRepository.findByDomain(IntegrationDomain.MCP)` | `effectiveState()` = `OrgIntegration(domain=MCP).enabled == true` **AND** latest `mcp_egress_consents` row is `GRANTED`. Wire into every `tools/call`: if false, refuse with a **non-leaking** `McpError("not_enabled","the MCP connector is not enabled for this firm")` (no data, no member-existence, no matter-existence disclosure). `initialize` may still succeed. `enable(consentVersion)` (consent first, then `OrgIntegration.enable()` + `config_json`) and `revoke()` (`enabled=false` + append REVOKED) — re-checked per request so revoke takes effect on next call. |
| 565B.4 | Integration tests — enablement + consent gate | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpEnablementServiceTest.java` (new) | ~6 tests: (1) disabled tenant → non-leaking refusal at `tools/call`; (2) enabled + GRANTED → tool returns data; (3) enabled but no consent → refusal; (4) revoke → next `tools/call` refuses (no token-expiry wait); (5) consent append-only history preserved through enable/revoke/re-enable; (6) `initialize` succeeds even when disabled | 562C.4 pattern | |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/tenant/V129__create_mcp_egress_consents.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/consent/McpEgressConsent.java`, `McpEgressConsentRepository.java`, `McpConsentService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpEnablementService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/consent/McpEgressConsentTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpEnablementServiceTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java` — add `MCP("kazi")`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/McpToolRegistry.java` — effective-state gate hook

**Read for context:**
- `backend/.../integration/OrgIntegration.java` (constructor, `enable()`, `config_json`), `OrgIntegrationRepository.java` (`findByDomain`)
- `backend/.../integration/ai/profile/AiFirmProfile.java` (entity convention to mirror exactly)
- `backend/src/main/resources/db/migration/tenant/V127__compliance_audit_tables.sql` (tenant migration format)
- `backend/.../multitenancy/RequestScopes.java` (`requireMemberId()`)

### Architecture Decisions

- **`OrgIntegration` row, no adapter bean** ([ADR-305] [D4]) — MCP is inbound read-exposure; the row is a flag + config holder, queried via `findByDomain(MCP)`. `IntegrationAdapter`/`IntegrationRegistry` are for outbound ports.
- **Dedicated append-only consent table** ([ADR-305] [D2]) — POPIA evidence needs history (who consented to which version, when, revoke), which a `config_json` field cannot represent. One tenant table (V129) is the justified cost.
- **Effective state re-checked per request** — revoke takes effect on the next `tools/call` without waiting for token expiry; non-leaking refusal posture.

### Non-scope

- Frontend settings card (Epic 566). Audit metadata finalisation + hardening (Epic 567).

---

## Epic 566: MCP Connector Settings Card (Frontend)

**Goal**: Add the "Claude / MCP Connector" card to the Integrations settings hub, with enable (POPIA consent acknowledgement modal), revoke, status display, the MCP server URL, copy-paste connection instructions for Claude Desktop/Code, and the current consent metadata. Server actions call the Epic 565 backend.

**References**: Architecture §11.5 (settings card content), §11.6 (settings server actions: `enableMcpAction`, `revokeMcpAction`, `getMcpStatusAction`), §11.8c (sequence), §11.12 (frontend changes), §11.13 S4 (frontend portion) + §8 (connection docs fold in here); [ADR-305].

**Dependencies**: Epic 565 (backend enablement + consent + effective-state + server-action contract).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **566A** | 566.1–566.6 | `integrations/mcp/page.tsx` connector card (status, server URL, connection instructions, consent metadata, revoke); `integrations/mcp/actions.ts` (enable/revoke/status server actions); consent acknowledgement modal; integrations hub link; unit tests (~6 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 566.1 | Create MCP server actions | 566A | | Create `frontend/app/(app)/org/[slug]/settings/integrations/mcp/actions.ts`. `enableMcpAction(consentVersion)` (appends GRANTED consent then enables `OrgIntegration` — consent first, atomic), `revokeMcpAction()` (appends REVOKED + disables), `getMcpStatusAction()` (returns effective state + current consent metadata for the card). Call the Epic 565 backend endpoints. Pattern: follow `frontend/app/(app)/org/[slug]/settings/integrations/xero/actions.ts` and the integrations hub `actions.ts`. RSC server-action conventions per `frontend/CLAUDE.md`. |
| 566.2 | Create `integrations/mcp/page.tsx` connector card | 566A | | Create `frontend/app/(app)/org/[slug]/settings/integrations/mcp/page.tsx`. Server component fetching status via `getMcpStatusAction()`. Card shows: enablement toggle, status badge (Enabled/Disabled), the MCP **server URL**, current consent version + who consented + when, and the revoke control. OWNER/ADMIN only. Pattern: follow `integrations/xero/page.tsx` card structure + the integrations hub `page.tsx`. |
| 566.3 | Add POPIA consent acknowledgement modal | 566A | | Within the MCP card (client component). Enabling opens a consent modal explaining client PII flows into the firm's external AI context (firm is responsible party); on acknowledge, calls `enableMcpAction(consentVersion)` with the current `consentVersion` (e.g. `popia-egress-v1`). Pattern: follow existing Shadcn `Dialog` + controlled-state pattern (e.g. `components/customers/anonymize-customer-dialog.tsx` for a consequential-action modal). Respect OBS-2103 dialog rendering. |
| 566.4 | Add connection instructions content | 566A | | Within the MCP card. Copy-paste instructions for adding the connector to Claude Desktop/Code: the server URL, the OAuth flow note, and a minimal config snippet (§8 — server-side docs only; the curated skill pack is Phase 79, out of scope). Surface a "copy" affordance for the server URL/snippet. |
| 566.5 | Link the MCP card on the integrations hub | 566A | | Modify `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx`. Add a "Claude / MCP Connector" entry/link to the integrations hub alongside the existing Xero card. Pattern: follow how the Xero card is surfaced on the hub. |
| 566.6 | Add unit tests | 566A | | Create `frontend/__tests__/app/settings/integrations/mcp-card.test.tsx` (~6 tests): (1) renders Disabled state with enable control; (2) enable opens consent modal; (3) acknowledging consent calls `enableMcpAction`; (4) renders server URL + connection instructions when enabled; (5) renders consent metadata (who/when/version); (6) revoke control calls `revokeMcpAction`. Mock server actions. Always `afterEach(() => cleanup())`. Pattern: follow Phase 77 `557.8` overflow-menu test structure + existing integrations test if present. |

### Key Files

**Create (frontend):**
- `frontend/app/(app)/org/[slug]/settings/integrations/mcp/actions.ts`
- `frontend/app/(app)/org/[slug]/settings/integrations/mcp/page.tsx`
- `frontend/__tests__/app/settings/integrations/mcp-card.test.tsx`

**Modify (frontend):**
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` — surface the MCP card on the hub

**Read for context:**
- `frontend/app/(app)/org/[slug]/settings/integrations/xero/page.tsx` + `xero/actions.ts` — card + server-action pattern
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` + `actions.ts` — hub layout + status fetch
- `frontend/components/customers/anonymize-customer-dialog.tsx` — consequential-action modal pattern (OBS-2103-safe)
- `frontend/CLAUDE.md` — RSC serialization boundary, server-action conventions, dialog anti-patterns

### Architecture Decisions

- **Consent-first, atomic enable** ([ADR-305]) — `enableMcpAction` appends the GRANTED consent row before flipping `OrgIntegration.enabled`.
- **Reuse the Integrations settings hub** — the MCP card lives alongside Xero; no new settings area, matching the established per-tenant integration-config UX.
- **Connection docs fold into the card** (§8) — server URL + Claude Desktop/Code snippet on the card; the curated skill pack is Phase 79.

### Non-scope

- No backend changes (all in Epic 565). No skill pack / Claude-for-Legal bridge (Phase 79).

---

## Epic 567: Audit / Observability + Isolation / Read-Only Hardening

**Goal**: Finalise the `mcp.tool.invoked` and `mcp.access.denied` audit metadata (sanitised params summary, result row count, entity refs, denied gate), add per-tenant MCP metrics with no PII labels, and add the structural hardening test suite (cross-tenant isolation, capability gating per regime, read-only registry assertion, pagination cap + response ceiling, trust module-gating tolerance, enablement/revoke refusal). Document the manual-QA step (real Claude Desktop against a dev tenant). No live Claude in CI.

**References**: Architecture §11.10 (audit + observability, metric labels), §11.3 (read-only by construction), §11.9 (gating to assert), §11.12 (testing strategy table), §11.13 S5; [ADR-304] (isolation/read-only test demands), [ADR-305] (audit), [ADR-306] (read-only registry).

**Dependencies**: Epics 562, 563, 564, 565 (asserts across the full catalogue + enablement gate). Does NOT depend on 566.

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **567A** | 567A.1–567A.3 | ~5 backend files (1 audit metadata builder + 1 metrics + 1 wiring modify + 1 test) | Finalise `mcp.tool.invoked` (sanitised params summary, row count, entity refs) + `mcp.access.denied` (tool, denied gate); per-tenant call-count + latency metrics (no PII labels); audit-emission tests. | |
| **567B** | 567B.1–567B.5 | ~5 backend test files | Cross-tenant isolation test; capability-gating test per regime; read-only registry-assertion test; pagination-cap + response-ceiling test; module-gating tolerance + enablement/revoke refusal test; manual-QA evidence doc. | |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 567A.1 | Finalise MCP audit metadata emission | `backend/.../mcp/McpAuditMetadata.java` (new), `backend/.../mcp/tool/McpToolRegistry.java` (modify) | 567A.3 | §11.10 metadata table; `audit/AuditEventBuilder.java` + `AuditEventMetadataResolver.java` | `mcp.tool.invoked`: tool name, **params summary** (sanitised — ids/enums only, NEVER free-text PII), **result row count**, **entity refs** (matter/customer/invoice ids touched), actor. `mcp.access.denied`: tool name, denied gate, actor. Emitted from the common tool-invocation path (so all 13 tools + 3 resources inherit it). |
| 567A.2 | Add per-tenant MCP metrics | `backend/.../mcp/McpMetrics.java` (new) | 567A.3 | Phase 75 `555A` `JobQueueMetrics`/`ShardMetrics` Micrometer pattern | Per-tenant call counts + latency. **No PII in labels** — labels carry tenant schema, tool name, outcome (`ok`/`denied`/`error`) only; never member/client names or entity ids. |
| 567A.3 | Integration tests — audit emission + metric labels | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpAuditEmissionTest.java` (new) | ~5 tests: (1) `mcp.session.opened` on initialize; (2) `mcp.tool.invoked` with row count + entity refs on call; (3) params summary contains no free-text PII (ids/enums only); (4) `mcp.access.denied` on each refusal type (front door, capability, project-access); (5) metric labels carry no PII | 562C.4 pattern | |
| 567B.1 | Cross-tenant isolation test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpTenantIsolationTest.java` (new) | ~3 tests: token bound to tenant A cannot read tenant B data via any tool; no tenant-id parameter exists to override; schema resolved from token `o.id` only | [ADR-304] testing demands; provision 2 tenants | Cross-tenant reads impossible by construction (schema-per-tenant `search_path` from token). |
| 567B.2 | Capability-gating test per regime | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpCapabilityGatingTest.java` (new) | ~6 tests: `MCP_ACCESS` without `VIEW_TRUST` → trust denied but matters allowed; without `AI_MANAGE` → firm-profile denied ([D1]); `INVOICING`/`CUSTOMER_MANAGEMENT`/`TEAM_OVERSIGHT` enforced per their tools; project-access non-member → 404 | §11.9 permission matrix | Asserts MCP scope = web scope (never widens). |
| 567B.3 | Read-only registry-assertion test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpReadOnlyRegistryTest.java` (new) | ~2 tests: iterate all registered tools, fail if any delegates to a non-read service method; assert `prompts`/`sampling` not advertised | §11.3 read-only by construction; [ADR-306] | The structural read-only guarantee. |
| 567B.4 | Pagination-cap + response-ceiling test | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpPaginationCapTest.java` (new) | ~4 tests: list tools clamp size to 50 (audit 200); unbounded service results sliced; `truncated`/`total` surfaced; oversized call → "narrow your query" error | §11.4 / 562C.2 | |
| 567B.5 | Module-gating tolerance + enablement/revoke refusal test + manual-QA evidence | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpModuleAndEnablementHardeningTest.java` (new); manual-QA note in the QA testplan | ~4 tests: non-legal tenant calling `get_trust_balance` → clean module-disabled response (no stack trace); disabled tenant → non-leaking refusal; revoked consent → refusal on next call; enabled+consented → data | §11.3 module caveat, §11.5 enablement; QA testplan precedent in Phase 75/77 | Document the manual-QA step: connect a real Claude Desktop against a dev tenant before release (NO live Claude in CI). |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpAuditMetadata.java`, `McpMetrics.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpAuditEmissionTest.java`, `McpTenantIsolationTest.java`, `McpCapabilityGatingTest.java`, `McpReadOnlyRegistryTest.java`, `McpPaginationCapTest.java`, `McpModuleAndEnablementHardeningTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/McpToolRegistry.java` — common audit-emission path

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java`, `AuditEventMetadataResolver.java`
- `backend/.../infrastructure/jobqueue/...Metrics` (Phase 75 `555A` Micrometer pattern)
- `qa/testplan/` — manual-QA step format precedent (Phase 75/77)

### Architecture Decisions

- **Audit reuses Phase 6 entirely** ([ADR-305]) — three new event-type strings on the append-only `AuditEvent`; no new audit infrastructure. The actor is the real `MemberFilter`-resolved member, giving the POPIA-defensible record.
- **No PII in metric labels** (§11.10) — labels carry tenant schema, tool name, outcome only.
- **Structural guarantees proved by tests** ([ADR-304]) — isolation, capability gating, read-only, pagination caps are asserted, not assumed; no live Claude in CI, manual QA before release.

### Non-scope

- No new tools/resources or DTOs (catalogue is complete after 563/564).
- No reserved write-back (`propose_*`) — Phase 79+; the registry is left extensible (§11.11).

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/pom.xml` (Spring AI 2.0 milestone starter + STREAMABLE — the 562A risk gate)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` (add `MCP_ACCESS`)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java` (add `MCP("kazi")`) and `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegrationRepository.java` (`findByDomain`)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/ActorContext.java` and `RequestScopes.java` (`fromRequestScopes()` propagation — the auth linchpin)
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/settings/integrations/xero/actions.ts` (server-action + card pattern for the MCP connector card)

Note on the migration discrepancy: the architecture doc (§11.7) and ADR-305 both name the consent migration `V100`, but the live `db/migration/tenant/` high-water mark is `V128` — the next free number is `V129`. The breakdown uses V129 and flags it as an Open Question for the Builder to confirm at branch time.