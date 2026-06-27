# Phase 82 — Correspondence Read-Back over MCP

Phase 81 made the Kazi MCP server able to **write** correspondence (`file_correspondence`, `attach_document`, `propose_task`) but left a read/write asymmetry: a firm's own Claude (BYOC) can file an email into a matter but **cannot read filed correspondence back over MCP** — the only read surface is the in-app REST Correspondence tab (586A), which is not an MCP tool. A "what's happening across my matters' inboxes — what needs a reply, what implies a deadline" digest is therefore impossible to ground, because the deadlines and obligations live in the **body**, which no MCP tool exposes.

Phase 82 closes that asymmetry with exactly **two MCP read tools** over the existing `correspondence/` bounded context: `list_correspondence(matterId | customerId, page)` returns the existing metadata projection as an `McpPage`; `get_correspondence(id)` returns a single record **with body** + headers via one net-new service method and one new detail DTO. Both are gated by **project view-access** (not the org-wide `MCP_ACCESS` capability — ADR-324), audited (`mcp.tool.invoked`, safe refs only), and tenant-isolated exactly like `get_matter` / `search_documents`. This is a **thin exposure layer**: **no new entity, table, migration, write tool, audit family, LLM, Gmail integration, or frontend.** The consumer `correspondence-digest` skill ships in `../claude-for-legal-sa`, not this repo.

**Migration high-water at phase start**: tenant **V132** (`V132__create_correspondence_tables.sql`). Phase 82 ships **NO migration** — it reads the existing `correspondence` table only. If a `V133` (or any migration) appears in the diff, scope has been left.

---

## Open Questions

- **`messageId` on the MCP list row (recommended, not required).** Architecture §11.2.1 recommends a dedicated `McpCorrespondenceListItem` record (the `CorrespondenceListResponse` fields **+ `messageId`**) so the digest can cross-check a listed row against the idempotency key `file_correspondence` filed, **without** touching the shared REST `CorrespondenceListResponse`. **Resolution**: 587A adds `McpCorrespondenceListItem` with `messageId`; if the breakdown chooses minimal, reusing `CorrespondenceListResponse` verbatim (no `messageId`) is acceptable and the digest cross-checks by id. The recommended path is taken below. It exposes no new PII (`messageId` is an opaque RFC-5322 header, already returned in full by `get_correspondence`).
- **`CorrespondenceDetail` vs `McpCorrespondenceDto` indirection.** §11.3 says the service returns a body-bearing `CorrespondenceDetail` boundary record (entity never crosses the MCP boundary) and the tool maps it to `McpCorrespondenceDto`. The two **may be merged** if the indirection is found gratuitous — but the JPA entity must stay off the MCP boundary either way (mirrors the existing `CorrespondenceScope` / `requireScopeById` discipline). Kept separate below for symmetry with `CorrespondenceScope`.
- **No new repository method.** §11.3 confirms `requireDetailById` uses the inherited `CorrespondenceRepository.findById` + in-service mapping (a JPQL detail projection saves nothing for a single-row, join-free read). **Resolution**: no repo change; `attachmentCount` filled from the existing `CorrespondenceRepository.countAttachments(id)`.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 587 | Correspondence Read-Back over MCP — `list_correspondence` + `get_correspondence` | Backend | — (reuses Phase 81 `correspondence/` + Phase 78 MCP read pipeline) | M | 587A, 587B | **Done** (PR #1510) |

**Slice count: 2.** This is a SMALL phase — **1 epic, 2 slices**, matching architecture §11.8. Both slices are **Backend only**; no slice mixes backend + frontend. There is **no migration, no entity, no frontend, no write tool**. 587B has a **soft dependency** on the `CorrespondenceReadTools` bean created in 587A — **do 587A first**.

---

## Dependency Graph

```
PHASES already complete (reused, NOT rebuilt):
  Phase 78 (Kazi MCP server — mcp/, ~12 read tools; McpEnablementService.effectiveState();
            McpPagination (McpPage envelope, clampSize); McpToolAudit / McpAuditMetadata
            (mcp.tool.invoked, SAFE_TOKEN, emitDenied → mcp.access.denied);
            McpToolErrors (non-leaking not_found); McpError; ObjectMapper; McpMetrics;
            MatterTools.get_matter / ClientTools.resolve_matter_by_email read precedents;
            McpReadOnlyRegistryTest ALLOWED_TOOL_BEANS; ADR-304/306)
  Phase 81 (correspondence/ — Correspondence entity (subject, bodyText, bodyHtml, fromAddress,
            toAddresses, ccAddresses, sentAt, receivedAt, threadKey, messageId, direction,
            customerId/projectId, filedAt); CorrespondenceRepository.findById / countAttachments;
            CorrespondenceService.listForProject / listForCustomer (access-gated + 50-clamp) and
            requireScopeById → CorrespondenceScope (body-less boundary record — the PATTERN to mirror);
            CorrespondenceListResponse projection; V132 correspondence table)
  ProjectAccessService.requireViewAccess (the project view-access gate get_matter uses)
                                 │
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ 587A — list_correspondence (Backend)                      │
        │   NEW CorrespondenceReadTools @Component bean + list_      │
        │   correspondence @McpTool; reuse listForProject/listFor-   │
        │   Customer (NOT package-private listBy*); exactly-one-of    │
        │   arg validation; Page<CorrespondenceListResponse> →       │
        │   McpPage; McpCorrespondenceListItem (+ messageId);        │
        │   mcp.tool.invoked (safe refs) + emitDenied on matter-path │
        │   view-access refusal; +1 line ALLOWED_TOOL_BEANS          │
        └──────────────────────────────────────────────────────────┘
                                 │  (soft dep: bean must exist)
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ 587B — get_correspondence (Backend)                       │
        │   add get_correspondence @McpTool to CorrespondenceRead-  │
        │   Tools; +1 service method requireDetailById(UUID,actor)  │
        │   → new body-bearing CorrespondenceDetail record (entity   │
        │   never crosses MCP boundary) + new McpCorrespondenceDto;  │
        │   project-access gating mirrors get_matter (emitDenied     │
        │   ONLY on found-but-refused, NOT absent/cross-tenant);     │
        │   body egress under EXISTING read-egress consent;          │
        │   mcp.tool.invoked entityRef=id only                       │
        └──────────────────────────────────────────────────────────┘
```

**Parallel opportunities:** none — 587B soft-depends on the `CorrespondenceReadTools` bean established in 587A. Strictly sequential: **587A → 587B**. Both must pass a clean full `./mvnw verify`.

---

## Implementation Order

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 1 | **587A** ✅ Done (PR #1510) | `CorrespondenceReadTools` bean + `list_correspondence`; reuse access-gated `listForProject`/`listForCustomer`; exactly-one-of arg validation; `Page → McpPage` adapter; optional `McpCorrespondenceListItem` (+ `messageId`); `ALLOWED_TOOL_BEANS` one-liner; pagination/clamp/isolation/audit tests. | — |
| 2 | **587B** ✅ Done (PR #1510) | `get_correspondence` on the bean from 587A; `CorrespondenceService.requireDetailById` + `CorrespondenceDetail` record; `McpCorrespondenceDto`; project-access gating mirroring `get_matter`; body egress under existing consent; body / not-found / isolation / audit-safe-refs tests. | — |

### Timeline

```
587A  ───►  587B            <- read bean + list tool, then body-bearing detail tool
(no frontend, no migration, no entity, no write tool in either slice)
```

---

## Epic 587: Correspondence Read-Back over MCP — `list_correspondence` + `get_correspondence`

**Goal**: Close the Phase 81 read/write asymmetry by exposing the existing `correspondence/` bounded context over two MCP **read** tools. `list_correspondence` (587A) returns the existing metadata projection as an `McpPage`, reusing the access-gated `CorrespondenceService.listForProject`/`listForCustomer` **verbatim** (no new list path). `get_correspondence` (587B) returns a single record **with body** + headers via one net-new `CorrespondenceService.requireDetailById` method, a new body-bearing `CorrespondenceDetail` boundary record, and a new `McpCorrespondenceDto`. Both tools live on a new `CorrespondenceReadTools` bean, are gated by **project view-access** (mirroring `get_matter`, **not** the org-wide `MCP_ACCESS` capability — ADR-324), ride the **existing** read-egress consent for body egress, and emit `mcp.tool.invoked` with **safe entity refs only** — never subject, from, to/cc, or body.

**References**: Architecture §11.2 (tool contracts), §11.2.1 (`list_correspondence` + arg discipline + `not_found` cases + `messageId` row), §11.2.2 (`get_correspondence` + `McpCorrespondenceDto` field table + 3 `not_found` sub-cases), §11.3 (reuse `listForProject`/`listForCustomer` NOT `listBy*`; `requireDetailById` + `CorrespondenceDetail`; no new repo method), §11.4 (sequence diagrams), §11.5 (security/POPIA/audit — project-access gate, safe refs, body under existing consent), §11.6 (test impact — `ALLOWED_TOOL_BEANS` one-liner, `AuditEventTypeRegistryTest` stays 36), §11.7 (implementation guidance / file table), §11.8 Slices 82A/82B; [ADR-324](../adr/ADR-324-correspondence-readback-over-mcp.md), [ADR-323](../adr/ADR-323-email-matter-linking.md), ADR-304, ADR-306.

**Dependencies**: Phase 81 (`Correspondence` entity, `CorrespondenceRepository.findById`/`countAttachments`, `CorrespondenceService.listForProject`/`listForCustomer`/`requireScopeById`, `CorrespondenceListResponse`); Phase 78 MCP read pipeline (`McpEnablementService`, `McpPagination`/`McpPage`, `McpToolAudit`/`McpAuditMetadata`, `McpToolErrors`, `McpError`); `ProjectAccessService.requireViewAccess`. None within this phase beyond 587A → 587B.

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **587A** ✅ Done (PR #1510) | 587A.1–587A.4 | ~7 backend files (1 new tool bean + 1 new MCP list-item DTO + 1-line registry-test edit + 3 test files) | NEW `CorrespondenceReadTools` `@Component` + `list_correspondence` reusing access-gated `listForProject`/`listForCustomer`; `McpCorrespondenceListItem` (+ `messageId`); `Page → McpPage` adapter; exactly-one-of arg validation; `ALLOWED_TOOL_BEANS` one-liner; pagination/clamp/newest-first/denial/isolation/audit tests. |
| **587B** ✅ Done (PR #1510) | 587B.1–587B.4 | ~8 backend files (1 service +1 method + 1 new `CorrespondenceDetail` record + 1 new `McpCorrespondenceDto` + 1 tool-method add + 3 test files) | `get_correspondence` on the 587A bean; `CorrespondenceService.requireDetailById` + `CorrespondenceDetail`; `McpCorrespondenceDto`; project-access gating mirroring `get_matter`; body egress under existing consent; body / not-found / isolation / audit-safe-refs tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 587A.1 | Create `CorrespondenceReadTools` bean + `list_correspondence` | `mcp/tool/CorrespondenceReadTools.java` (**NEW**) | 587A.3, 587A.4 | `mcp/tool/MatterTools.java` (`list_matters`/`get_matter` read-shape preamble + project-access); `mcp/tool/ClientTools.java` (`resolve_matter_by_email` exactly-one-arg discipline + safe-ref audit); `mcp/McpPagination.java`; `mcp/McpToolAudit.java`/`McpAuditMetadata.java`; `mcp/McpToolErrors.java`/`McpError.java`; `mcp/McpEnablementService.java` | `@Component` ctor: `CorrespondenceService`, `AuditService`, `ObjectMapper`, `McpEnablementService`, `McpMetrics` (do **NOT** inject `CustomerRepository`/`DocumentService`). `@McpTool(name="list_correspondence")` params `matterId?`, `customerId?`, `page?`, `size?`. **First statement**: `if (!enablement.effectiveState()) return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);`. Then arg validation: reject neither **and** both with `McpError.invalidRequest("provide exactly one of matterId or customerId")` — mirror `resolve_matter_by_email`. Then: `matterId` → `correspondenceService.listForProject(matterId, actor, pageable)`; `customerId` → `correspondenceService.listForCustomer(customerId, pageable)`. **Call the access-gated `listForProject`/`listForCustomer` — NOT the package-private `listByProject`/`listByCustomer`, which bypass the view-access check AND the 50-row clamp.** Build an **unsorted** `Pageable` from `page`/`size` (newest-first is JPQL-hardcoded; the clamp rejects client sorts). Adapt: `McpPage.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext())`. Catch `ResourceNotFoundException` → non-leaking `not_found` via `McpToolErrors`. On the **matter path** view-access refusal emit `emitDenied("list_correspondence","project-access",…)` → `mcp.access.denied`; on the **customer path** unknown-id (a lookup miss) emit **NO** `mcp.access.denied`. Success → `mcp.tool.invoked` with `McpAuditMetadata` carrying `rowCount` + page correspondence ids as `entityRefs` + a `matterId`/`customerId` param ref. **Never** subject/from/body. **Deliberate deviation from materialise-then-paginate**: reuse the DB-paginated `Page`-returning service methods (already page-bounded; no response-ceiling check) — see §11.3. |
| 587A.2 | Create `McpCorrespondenceListItem` MCP list-row DTO | `mcp/dto/McpCorrespondenceListItem.java` (**NEW**, recommended) | 587A.3 | §11.2.1 row shape; existing `mcp/dto/McpMatterListItem.java`, `mcp/dto/McpClientListItem.java` | Record: `id`, `subject`, `fromAddress`, `receivedAt`, `attachmentCount`, `direction`, **`messageId`** (the digest cross-check key the REST `CorrespondenceListResponse` omits). Keeps the shared REST DTO untouched. **If the slice chooses minimal**, skip this DTO and have `list_correspondence` emit `CorrespondenceListResponse` verbatim (no `messageId`); then `McpPage<CorrespondenceListResponse>` and the digest cross-checks by id. Map from `CorrespondenceListResponse` in the tool (+ pull `messageId` from the projection — extend the projection/JPQL only if `messageId` is not already selected; prefer mapping without touching the REST DTO). |
| 587A.3 | `list_correspondence` behaviour tests | `backend/src/test/java/.../mcp/ListCorrespondenceToolTest.java` (**NEW**) | ~8 tests: (1) matter path returns rows newest-first as `McpPage`; (2) `clampSize` — request size > 50 clamped to 50; (3) pagination — page index honoured, `truncated`/`hasNext` correct; (4) **reject neither id** → `invalid_request`; (5) **reject both ids** → `invalid_request`; (6) matter-path view-access denial → `not_found` **+ `mcp.access.denied` (gate=project-access)**; (7) customer path: known customer lists; **unknown/cross-tenant customerId → `not_found`, NO `mcp.access.denied`**; (8) MCP disabled / consent revoked → `not_enabled` | existing `mcp/` tool tests (Phase 78); `ResolveMatterByEmailToolTest.java` (arg-discipline + read-audit assertions); §11.6 test rows | Drive the tool method directly with bound `RequestScopes.TENANT_ID`. Assert `McpPage` shape, the two distinct `not_found` paths, and the denial-emission asymmetry (denied on matter-refusal, NOT on customer-miss). |
| 587A.4 | Tenant-isolation + audit-safe-refs tests + `ALLOWED_TOOL_BEANS` one-liner | `backend/src/test/java/.../mcp/ListCorrespondenceTenantIsolationTest.java` (**NEW**); `backend/src/test/java/.../mcp/McpReadOnlyRegistryTest.java` (**+1 line**) | ~3 tests: tenant A's `list_correspondence` for A's matter returns **empty** for tenant B (search_path isolation); `mcp.tool.invoked` details carry **only** `entityRef`(s) + `matterId`/`customerId` param — **no subject/from/body** (POPIA `SAFE_TOKEN`); registry test green | §11.6; existing `mcp/CorrespondenceWriteToolsTenantIsolationTest.java`; `McpReadOnlyRegistryTest.ALLOWED_TOOL_BEANS` | **Add `"CorrespondenceReadTools"` to `ALLOWED_TOOL_BEANS`** (one line, alongside `MatterTools`/`ClientTools`). **No** regex edit, **no** `READ_NAME_EXEMPTIONS` entry (`list_correspondence` matches `^(list\|get\|search)_`). **Do NOT** add it to `ALLOWED_WRITE_TOOL_BEANS` (that is `CorrespondenceWriteTools`). **`AuditEventTypeRegistryTest` — NO change, stays `hasSize(36)`** (tools emit the pre-existing `mcp.tool.invoked`/`mcp.access.denied`; no new audit type — do NOT bump). **CLAUDE.md §5**: both registry/count tests live outside the `mcp.tool`/`correspondence` packages — only a full `./mvnw verify` catches them; a targeted `-Dtest=` run will not. |
| 587B.1 | Add `requireDetailById` + `CorrespondenceDetail` to the service | `correspondence/CorrespondenceService.java` (**+1 method**); `correspondence/CorrespondenceDetail.java` (**NEW**) | 587B.3, 587B.4 | existing `CorrespondenceService.requireScopeById` + `correspondence/CorrespondenceScope.java` (body-less boundary-record pattern to mirror, now body-bearing); `correspondence/CorrespondenceRepository.java` (`findById`, `countAttachments`) | `@Transactional(readOnly = true) CorrespondenceDetail requireDetailById(UUID id, ActorContext actor)`: `correspondenceRepository.findById(id)` (tenant-safe via search_path) `.orElseThrow(ResourceNotFoundException)`; if `projectId != null` → `projectAccessService.requireViewAccess(projectId, actor)` (mirrors `listForProject`); else (customer-only) existence-in-tenant only. Map the entity → `CorrespondenceDetail` **inside the service** (entity never crosses the MCP boundary — mirror `CorrespondenceScope::of`); fill `attachmentCount` from `correspondenceRepository.countAttachments(id)`. **No new repository method** (§11.3). `CorrespondenceDetail` record fields per §11.2.2: `id`, `customerId`, `projectId`, `direction`, `subject`, `bodyText`, `bodyHtml`, `fromAddress`, `toAddresses`, `ccAddresses`, `sentAt`, `receivedAt`, `threadKey`, `messageId`, `attachmentCount`, `filedAt`. Inject `ProjectAccessService` into `CorrespondenceService` only if not already present (it backs `listForProject`'s `requireViewAccess` — likely already a dep). |
| 587B.2 | Create `McpCorrespondenceDto` detail DTO | `mcp/dto/McpCorrespondenceDto.java` (**NEW**) | 587B.3 | §11.2.2 field table + JSON; existing `mcp/dto/McpMatterDto.java`, `mcp/dto/McpDocumentDto.java` (flat MCP read DTO, ISO-8601 instants) | Flat MCP-facing record mapped from `CorrespondenceDetail` in the tool. Fields + types per the §11.2.2 table (`bodyText` is the read-back payload; `bodyHtml`/`sentAt`/`receivedAt`/`threadKey`/`customerId`/`projectId` nullable; `attachmentCount` `long`; `filedAt` non-null). ISO-8601 dates. **`CorrespondenceDetail` and `McpCorrespondenceDto` may be merged** if the indirection is gratuitous — but keep the JPA entity off the MCP boundary (§11.3, Open Questions). |
| 587B.3 | Add `get_correspondence` to `CorrespondenceReadTools` | `mcp/tool/CorrespondenceReadTools.java` (**modify**) | 587B.4 | `mcp/tool/MatterTools.java` `get_matter` (project-access gating + `emitDenied` ONLY on found-but-refused; non-leaking `not_found`); §11.2.2 / §11.4.2 sequence | `@McpTool(name="get_correspondence")` param `id` (req). **First statement** enablement guard (as in 587A.1). Body: `correspondenceService.requireDetailById(id, actor)` → map `CorrespondenceDetail` → `McpCorrespondenceDto` → return; emit `mcp.tool.invoked` with `rowCount=1` + `entityRef=id` **only** (never subject/from/body). Error handling — three `not_found` sub-cases with an **identical non-leaking message**: (a) unknown/cross-tenant id (`findById` empty → `ResourceNotFoundException`) → `not_found` with **NO** `mcp.access.denied`; (b) found-with-`projectId` but `requireViewAccess` refuses → `not_found` **+ `emitDenied("get_correspondence","project-access",…)`** (the **only** path that emits `mcp.access.denied`, mirroring `get_matter` exactly); (c) customer-only resolves on existence — no access.denied. Body egresses under the **existing** read-egress consent (the `effectiveState()` gate) — **no** new consent flag/gate/audit family (ADR-324, decision 2). No `McpCapabilityGuard` call (project-access, not `MCP_ACCESS`). |
| 587B.4 | `get_correspondence` body / not-found / isolation / audit tests | `backend/src/test/java/.../mcp/GetCorrespondenceToolTest.java` (**NEW**); `backend/src/test/java/.../mcp/GetCorrespondenceTenantIsolationTest.java` (**NEW**) | ~7 tests: (1) returns `bodyText`/headers for an in-tenant matter-scoped id the caller can view; (2) **fabricated id → `not_found`, no body, NO `mcp.access.denied`**; (3) **cross-tenant id (tenant A reads B's id) → `not_found`, never the body, NO `mcp.access.denied`** (mandatory isolation); (4) customer-only correspondence (`projectId==null`) resolves on existence-in-tenant; (5) matter-scoped correspondence + non-member caller → `not_found` **+ `mcp.access.denied` (gate=project-access)** (the found-but-refused path); (6) MCP disabled / consent revoked → `not_enabled`; (7) `mcp.tool.invoked` details carry **`entityRef=id` only** — **no subject/from/to/cc/body** (POPIA `SAFE_TOKEN`) | §11.6 test rows; §11.4.2 sequence; `MatterTools` `get_matter` tests; existing `mcp/` tool + isolation tests | "PASS means observed". Assert the denial-emission asymmetry: emitted ONLY on found-but-refused (case 5), NOT on absent/cross-tenant (cases 2/3). Drive with bound `RequestScopes.TENANT_ID` per tenant. **CLAUDE.md §5**: confirm the green bar on a **full `./mvnw verify`** (the registry/count tests are cross-package); `AuditEventTypeRegistryTest` stays `hasSize(36)`. |

### Key Files

**Create (backend, 587A):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceReadTools.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpCorrespondenceListItem.java` *(recommended; optional)*
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/ListCorrespondenceToolTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/ListCorrespondenceTenantIsolationTest.java`

**Create (backend, 587B):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceDetail.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpCorrespondenceDto.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/GetCorrespondenceToolTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/GetCorrespondenceTenantIsolationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceReadTools.java` — add `get_correspondence` (587B.3)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceService.java` — add `requireDetailById` (587B.1)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpReadOnlyRegistryTest.java` — **+1 line** `"CorrespondenceReadTools"` in `ALLOWED_TOOL_BEANS` (587A.4)

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/MatterTools.java` — `get_matter`/`list_matters` project-access + `emitDenied`-only-on-refused pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/ClientTools.java` — `resolve_matter_by_email` exactly-one-arg discipline + safe-ref read audit
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceScope.java` + `CorrespondenceService.requireScopeById` — the body-less boundary-record pattern `CorrespondenceDetail`/`requireDetailById` mirror
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpPagination.java`, `McpToolAudit.java`, `McpAuditMetadata.java`, `McpToolErrors.java`, `McpError.java`, `McpEnablementService.java`

### Architecture Decisions
- **Two MCP read tools over the existing domain — no new domain/table/migration** ([ADR-324](../adr/ADR-324-correspondence-readback-over-mcp.md)) — `list_correspondence` reuses the metadata projection; `get_correspondence` adds exactly one service method + one detail record + one MCP DTO. Body comes from Kazi (provider-neutral, works for archived mail), not re-hydrated from Gmail.
- **Project view-access gating, NOT the org-wide `MCP_ACCESS` capability** ([ADR-324](../adr/ADR-324-correspondence-readback-over-mcp.md), §11.5) — mirrors `get_matter`/`search_documents`/`get_document_url` and the in-app REST tab; `requireViewAccess(projectId)` on matter scope, existence-in-tenant on customer-only. The tools do **not** call `McpCapabilityGuard` (supersedes the requirements doc's `gatedTool("MCP_ACCESS", …)` suggestion). `emitDenied` fires **only** on found-but-refused; an absent/cross-tenant id is a non-leaking `not_found` with **no** `mcp.access.denied` (security-by-obscurity).
- **Body egress under the existing read-egress consent** ([ADR-324](../adr/ADR-324-correspondence-readback-over-mcp.md), decision C1) — same POPIA category and consent posture as `get_matter`/`get_client`; **no** new consent flag, gate, or audit family. Revoking consent / disabling the connector turns both tools off uniformly.
- **Reuse the access-gated `listForProject`/`listForCustomer`, NOT the package-private `listBy*`** ([ADR-324](../adr/ADR-324-correspondence-readback-over-mcp.md), §11.3) — the package-private variants bypass both the view-access check and the 50-row `McpPagination` clamp; the requirements doc loosely names `listBy*`, which is the wrong method.
- **Safe-refs-only audit; no new audit type** ([ADR-324](../adr/ADR-324-correspondence-readback-over-mcp.md), §11.6) — both tools emit the pre-existing `mcp.tool.invoked`/`mcp.access.denied` with `entityRef`(s) only (correspondence id, matter/customer ids) — never subject/from/to/cc/body (POPIA `SAFE_TOKEN`, mirroring `resolve_matter_by_email`). `AuditEventTypeRegistryTest` stays `hasSize(36)`.

### Non-scope
- **No migration** — reads the existing V132 `correspondence` table; if a `V133`/any migration appears in the diff, scope has been left. **No new entity/table** (`CorrespondenceDetail` is an in-JVM boundary record, not persisted). **No write tool** (those are `CorrespondenceWriteTools`, 582/583/585). **No frontend** (the in-app Correspondence tab shipped in 586B; this phase adds no UI). **No new audit event type / `AuditEventTypeRegistry` entry / regex or `READ_NAME_EXEMPTIONS` edit.** **No LLM, no Gmail/IMAP, no new repository method, no MCP resource** (`get_correspondence` covers the need). The consumer `correspondence-digest` skill ships in `../claude-for-legal-sa`, not here.
