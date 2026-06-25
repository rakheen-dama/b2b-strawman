# Phase 81 — Inbound Correspondence & the First Gated MCP Write-Back

Phase 81 opens the **gated write-back chapter** on Kazi's read-only Phase 78 MCP server, using email-filing as the lighthouse use case. It adds a net-new `correspondence/` bounded context (a filed-email record against a matter/customer, attachments persisted as first-class `Document`s) and the **first four write-aware MCP tools** layered onto the existing auth/enablement/consent/audit pipeline (not forked): `resolve_matter_by_email` (READ, lives with the existing read tools), `file_correspondence` + `attach_document` (Tier-1 direct writes), and `propose_task` (Tier-2 gated write). The Tier-2 tool is the first time `AiExecutionGate` *creation* is exposed over MCP — backed by a synthetic zero-cost `AiExecution` so the `execution_id NOT NULL` invariant holds, approved in-product unchanged via `AiExecutionGateController`.

The governing constraint is **BYOC ingestion**: NO Gmail/IMAP/webhook/poll and NO LLM/extraction call in the Kazi backend. The firm's own Claude reads the mailbox, extracts, and calls the Kazi write tools with already-structured input; Kazi *receives and validates* (tenant, capability, linkage, idempotency) but never reads the mailbox and never reasons.

This phase ships as **6 epics (581–586)**, expanded to **11 numbered slices** to honour the 8-12 file / ~800 LOC slice-sizing budget. The architecture's 6 capability slices (§N.10) form the epic spine; the foundation slice and the write-capability slice are split where the migration/entity work and the capability/audit-plumbing work are distinct concerns. **No slice mixes backend + frontend scope.**

**Migration high-water at phase start**: tenant **V129** (`V129__create_mcp_egress_consents.sql`). Phase 81 ships **one** tenant migration: **V130** (`correspondence` table + `documents.correspondence_id` ALTER).

---

## Open Questions

- **V130 numbering collision risk.** The architecture doc (§N.7) confirms **V130** as the next-free tenant migration as of 2026-06-21, but the in-flight Phase 80 (CRM) task file also reserves V130/V131. **Resolution**: re-verify the actual next-free `V` at implementation time of Slice 581A by listing `db/migration/tenant/`. If Phase 80 has merged first, this phase's single migration shifts to the next free number (V131/V132); the file *contents* (table + ALTER) are unchanged. Do **not** hardcode V130 without confirming.
- **`AiExecution.status` shape (closed enum vs free String).** §N.2.3/§N.8 require confirming, before coding Slice 585A, whether `AiExecution.status` is a closed enum (→ add a new `EXTERNALLY_EXECUTED` value) or a free `String` (→ use the constant). **Resolution**: inspect `integration/ai/execution/AiExecution.java` first in 585A; the slice carries either change.
- **`AiGatePendingEvent` existence.** §N.8 notes the existing service emits `AiGateApprovedEvent`/`AiGateRejectedEvent`/`AiGateExpiredEvent` but may not have a created/pending event. **Resolution**: in 585A, if no listener needs it in v1, omit the event emission; the `ai.gate.created` audit row is mandatory regardless.
- **`TaskService.createTask` signature.** The `executeCreateTask` helper (§N.8) assumes a specific arg order (`projectId, title, description, priority, …, dueDate, actor, customFields, …, assigneeId, …`). **Resolution**: confirm the live `TaskService.createTask` signature when implementing 585A; map params to the actual signature, passing the correspondence back-link via `customFields = Map.of("correspondenceId", …)`.
- **`source` as string, not enum.** `Correspondence.source` and `Document.source` (`EMAIL_INGEST`) are free-form String constants (matching the existing `Document.source` convention), not JPA enums. No source enum is introduced.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 581 | Correspondence Entity + Migration + Document Link | Backend | — | M | 581A | **Done** (PR #1504) |
| 582 | MCP Write Capability + Audit Family + `file_correspondence` | Backend | 581A | L | 582A, 582B | |
| 583 | `attach_document` (presigned reuse + correspondence stamp) | Backend | 581A, 582A, 582B | M | 583A | |
| 584 | `resolve_matter_by_email` (read tool) | Backend | 582A | S | 584A | |
| 585 | Gate-over-MCP — `propose_task` + synthetic execution + executor arm | Backend | 581A, 582B | L | 585A, 585B | |
| 586 | Frontend — Correspondence Tab + Gate-Origin Display + QA Capstone | Frontend / Both | 581A, 582B, 583A, 585B | L | 586A, 586B, 586C | |

**Slice count: 11** (6 architecture capability slices expanded to 11 numbered slices for the 8-12 file / ~800 LOC budget). Backend/frontend split preserved per slice — no slice mixes both scopes. The correspondence-list REST endpoints (a thin backend read surface the frontend tab depends on) land in **586A** as a backend slice; the React UI in **586B**; the QA capstone in **586C**.

---

## Dependency Graph

```
PHASES already complete (reused, not rebuilt):
  Phase 78 (Kazi MCP server — mcp/, ~12 read tools, McpEnablementService.effectiveState(),
            McpCapabilityGuard, McpToolAudit, McpToolErrors, McpAuditMetadata, McpPagination,
            ClientTools/MatterTools/DocumentTools, OAuth ADR-303, V129 egress consent)
  Phase 8/41 (Documents — DocumentService.initiateCustomerUpload/initiateUpload/confirmUpload,
              Document entity, source/visibility, S3 key structure)
  AI gate    (integration/ai/gate/ — AiExecutionGate PENDING→APPROVED|REJECTED|EXPIRED,
              AiExecutionGateService.approve/reject, AiExecutionGateController /api/ai/gates,
              GateAction sealed iface (6 records), GateActionExecutor, AI_REVIEW, expiry scheduler)
  AI exec    (integration/ai/execution/ — AiExecution, AiExecutionService, AiExecutionRepository)
  Phase 4    (Customer — CustomerRepository.findByEmail; CustomerProjectService matter listing)
  Tasks      (task/ — TaskService.createTask)
  Phase 6/69 (Audit — AuditEventTypeRegistry, McpToolAudit, mcp.tool.invoked family)
  Phase 6.5  (Activity — ActivityMessageFormatter)
  orgrole    (Capability enum, @RequiresCapability, OrgRoleService default grants)
                                 │
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 1 — Database + Domain Foundation (sequential)       │
        │   [581A  V130 migration (correspondence table +           │
        │          documents.correspondence_id ALTER); Correspondence│
        │          entity + Direction enum; CorrespondenceRepository;│
        │          CorrespondenceService (fileInbound + idempotency  │
        │          + linkage validation + list methods); dto/*;      │
        │          Document.correspondenceId setter + EMAIL_INGEST]  │
        └──────────────────────────────────────────────────────────┘
                                 │
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 2 — MCP Write Capability + first write tool         │
        │   [582A  MCP_WRITE Capability enum value + OrgRoleService  │
        │          owner/admin auto-grant; mcp.write.* audit-family  │
        │          registration + ActivityMessageFormatter arms]     │
        │                       │                                    │
        │                       ▼                                    │
        │   [582B  CorrespondenceWriteTools @Component +             │
        │          file_correspondence (inline MCP_WRITE guard       │
        │          preamble) + mcp/dto write DTOs + read-only-user-  │
        │          rejected + tenant-isolation tests]               │
        └──────────────────────────────────────────────────────────┘
                  │                    │                    │
        ┌─────────┘          ┌─────────┘          ┌─────────┘
        ▼                    ▼                    ▼
  ┌──────────────┐   ┌──────────────┐   ┌────────────────────────────┐
  │ Stage 3a     │   │ Stage 3b     │   │ Stage 3c — Gate-over-MCP    │
  │ [583A        │   │ [584A        │   │ [585A recordSyntheticMcp-   │
  │  attach_     │   │  resolve_    │   │  Execution + createGate +   │
  │  document    │   │  matter_by_  │   │  GateAction record + parse/ │
  │  presigned + │   │  email read  │   │  execute arm + TaskService  │
  │  confirm     │   │  tool in     │   │  injection]                 │
  │  stamp]      │   │  ClientTools]│   │       │                     │
  └──────────────┘   └──────────────┘   │       ▼                     │
        │                    │           │ [585B propose_task tool +   │
        │                    │           │  open-gate dedupe guard +   │
        │                    │           │  end-to-end gate test]      │
        │                    │           └────────────────────────────┘
        └────────────────────┴────────────────────┬─────────────────┘
                                                   ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 4 — Frontend + QA (after all backend)               │
        │   [586A  correspondence list REST endpoints (backend) +   │
        │          CorrespondenceController + CorrespondenceList-    │
        │          Response + attachment-count]                     │
        │                       │                                   │
        │                       ▼                                   │
        │   [586B  matter-detail Correspondence tab + gate-origin   │
        │          display on the gate review screen + lib/api]      │
        │                       │                                   │
        │                       ▼                                   │
        │   [586C  QA capstone — observed end-to-end run + full     │
        │          mvnw verify + frontend lint/build/test +         │
        │          tenant-isolation regression]                    │
        └──────────────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- After **582B** lands (the `MCP_WRITE` capability + audit family + the first write-tool component exist), **583A** (attach_document), **584A** (resolve_matter_by_email read tool), and **585A** (gate plumbing) can all proceed in parallel — they share no files (583A in `CorrespondenceWriteTools`, 584A in `ClientTools`, 585A in `integration/ai/gate` + `integration/ai/execution`).
- **585B** (`propose_task` tool) runs after **585A** (it consumes `createGate` + `recordSyntheticMcpExecution`) and **582B** (the write-tool component + guard pattern).
- Backend slices **581A → 582A → 582B** are sequential. Frontend/QA **586A → 586B → 586C** are sequential.

---

## Implementation Order

### Stage 1 — Database + Domain Foundation (sequential)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **581A** | **Done** (PR #1504) — V130 migration (`correspondence` table with `chk_correspondence_linkage` CHECK + `ux_correspondence_message_id` UNIQUE + project/customer/thread indexes; `documents.correspondence_id` ALTER + index); `Correspondence` entity (mirror `AiExecutionGate`: JSONB collections, `@Version`, `@PrePersist`/`@PreUpdate`); `Direction` enum; `CorrespondenceRepository` (`findByMessageId`, paginated project/customer lists); `CorrespondenceService` (`fileInbound` idempotent upsert, `validateLinkage`, list + attachment-count); `dto/*`; `Document.correspondenceId` setter + `EMAIL_INGEST` source constant; entity/idempotency/linkage tests. |

### Stage 2 — MCP Write Capability + first write tool (sequential after 581A)

| Order | Slice | Summary |
|-------|-------|---------|
| 2a | **582A** | `MCP_WRITE` `Capability` enum value; `OrgRoleService` owner/admin auto-grant; `AuditEventTypeRegistry` `mcp.write.*` family (`correspondence_filed`, `correspondence_refiled`, `document_attached`, `task_proposed`); `ActivityMessageFormatter` arms + `correspondence` entity-name resolver. |
| 2b | **582B** | `CorrespondenceWriteTools` `@Component` shell + `file_correspondence` tool (inline guard preamble: `effectiveState()` → `McpCapabilityGuard.gatedTool("MCP_WRITE", …)` → `validateLinkage` → `fileInbound` → `mcp.write.*` audit); `mcp/dto/*` write DTOs; read-only-user-rejected + tenant-isolation + file-via-MCP tests. |

### Stage 3 — Direct write + read tool + gate plumbing (parallel after 582B)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 3a | **583A** | `attach_document` tool (one tool, `phase` enum INITIATE\|CONFIRM) reusing `DocumentService` presigned-upload + confirm; stamp `correspondence_id` + `source=EMAIL_INGEST` on confirm; `mcp.write.document_attached` audit; INITIATE/CONFIRM + attachment-in-documents-list tests. | 584A, 585A |
| 3b | **584A** | `resolve_matter_by_email` **read** tool in `ClientTools` (reuse `CustomerRepository.findByEmail` + `CustomerProjectService.listProjectsForCustomer`); `MCP_ACCESS` (not `MCP_WRITE`), `mcp.tool.invoked` audit; zero/many behaviour; read MCP DTOs; match/no-match/multi-matter tests. | 583A, 585A |
| 3c | **585A** | `recordSyntheticMcpExecution(memberId)` (provider=MCP, cost=0, status `EXTERNALLY_EXECUTED`); `AiExecutionGateService.createGate(...)` + `findPendingGateForCorrespondence(...)`; `GateAction.CreateTaskFromCorrespondenceAction` record + sealed-permits; `GateActionExecutor` parse/execute arm + `executeCreateTask` (inject `TaskService`); synthetic-execution + executor-arm + approval-creates-task tests. | 583A, 584A |
| 3d | **585B** | `propose_task` tool in `CorrespondenceWriteTools` (inline `MCP_WRITE` guard; v1 open-gate dedupe via `findPendingGateForCorrespondence`; create synthetic execution → `createGate`; `mcp.write.task_proposed` audit); end-to-end "propose creates PENDING gate, no Task; approve → task" test. (After 585A.) | — |

### Stage 4 — Frontend + QA (sequential, after all backend)

| Order | Slice | Summary |
|-------|-------|---------|
| 4a | **586A** | (Backend) correspondence list REST: `CorrespondenceController` `GET /api/projects/{projectId}/correspondence` + `GET /api/customers/{customerId}/correspondence` (paginated, `isAuthenticated()` + existing project/customer view-access check, **no MCP capability**); `CorrespondenceListResponse` (`{id, subject, fromAddress, receivedAt, attachmentCount, direction}`); list + access-control + tenant-isolation tests. |
| 4b | **586B** | (Frontend) read-only "Correspondence" tab on matter detail (subject, from, date, attachment count, link to documents); gate review screen shows originating correspondence (subject + link) for `CREATE_TASK_FROM_CORRESPONDENCE` gates; `lib/api` correspondence client; component tests. |
| 4c | **586C** | (QA capstone) **observed** end-to-end run (MCP tool call → backend log → DB row / S3 object / gate record → UI); full `./mvnw verify` clean; frontend `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; tenant-isolation regression; boundary asserts (no Gmail/Google/IMAP dependency, no inbound webhook/poll, no LLM call added). |

### Timeline

```
Stage 1: [581A]                                    <- entity + migration + document link
Stage 2: [582A] -> [582B]                          <- MCP_WRITE capability/audit, then file_correspondence
Stage 3: [583A] // [584A] // [585A] -> [585B]      <- 3-way parallel; 585B after 585A
Stage 4: [586A] -> [586B] -> [586C]                <- backend REST -> frontend -> QA capstone
```

---

## Epic 581: Correspondence Entity + Migration + Document Link

**Goal**: Lay the persistence + domain foundation for the entire phase. V130 creates the new tenant-scoped `correspondence` table (with the ≥1-non-null linkage CHECK, the `message_id` UNIQUE idempotency index, and the project/customer/thread indexes) and adds the nullable `documents.correspondence_id` link column. The `Correspondence` rich-domain entity (mirroring `AiExecutionGate`'s JSONB + `@Version` pattern), its `Direction` enum, repository, and `CorrespondenceService` (idempotent `fileInbound`, linkage validation, paginated list + attachment-count) provide the domain layer the MCP write tools build on. `Document` gains the `correspondenceId` setter and the `EMAIL_INGEST` source constant.

**References**: Architecture §N.2 (Domain Model), §N.2.1 (`Correspondence` field table), §N.2.2 (idempotency), §N.2.4 (`Document.correspondence_id`), §N.3.a (`fileInbound`), §N.7 (V130 migration SQL verbatim), §N.8 (annotated entity, repository JPQL), §N.10 Slice 1; [ADR-319](../adr/ADR-319-inbound-correspondence-domain.md).

**Dependencies**: Phase 4 (`Customer`/`Project`); Phase 8/41 (`Document`, `documents` table); existing `AiExecutionGate` (entity pattern). None within this phase (foundation).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **581A** ✅ Done (PR #1504) | 581A.1–581A.8 | ~10 backend files (1 migration + 1 entity + 1 enum + 1 repo + 1 service + ~3 DTOs + 1 `Document` mod + 2 test files) | V130 migration; `Correspondence` entity + `Direction` enum; `CorrespondenceRepository`; `CorrespondenceService` (`fileInbound` + idempotency + linkage validation + list + attachment-count); `dto/*`; `Document.correspondenceId` setter + `EMAIL_INGEST`; entity/idempotency/linkage tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 581A.1 | Create V130 migration | `backend/src/main/resources/db/migration/tenant/V130__create_correspondence_tables.sql` | verified by 581A.7/.8 (runs clean) | `db/migration/tenant/V129__create_mcp_egress_consents.sql` for format | SQL verbatim from §N.7: `CREATE TABLE correspondence` (`id` PK; `customer_id`/`project_id` nullable UUID, raw FK; `direction` VARCHAR(10) NOT NULL DEFAULT 'INBOUND'; `subject` VARCHAR(500); `body_text`/`body_html` TEXT; `from_address` VARCHAR(320) NOT NULL; `to_addresses`/`cc_addresses` JSONB; `sent_at`/`received_at` TIMESTAMPTZ; `thread_key` VARCHAR(255); `message_id` VARCHAR(512) NOT NULL; `source` VARCHAR(30) NOT NULL DEFAULT 'MCP'; `filed_by_member_id` UUID NOT NULL; `filed_at`/`created_at`/`updated_at` TIMESTAMPTZ NOT NULL; `version` INTEGER NOT NULL DEFAULT 0; `CONSTRAINT chk_correspondence_linkage CHECK (customer_id IS NOT NULL OR project_id IS NOT NULL)`). Indexes: `ux_correspondence_message_id` UNIQUE; `ix_correspondence_project (project_id, received_at DESC)`; `ix_correspondence_customer (customer_id, received_at DESC)`; `ix_correspondence_thread (thread_key)`. Then `ALTER TABLE documents ADD COLUMN correspondence_id UUID` (nullable) + `CREATE INDEX ix_documents_correspondence ON documents (correspondence_id)`. No `tenant_id` (schema-per-tenant). **Re-verify V130 is next-free before applying (Phase 80 may have taken it — see Open Questions).** |
| 581A.2 | Create `Direction` enum | `correspondence/Direction.java` | covered by 581A.7 | `proposal/ProposalStatus.java` enum style | `INBOUND, OUTBOUND`. Only `INBOUND` written in v1; `OUTBOUND` modelled to avoid a future enum migration (no send path). |
| 581A.3 | Create `Correspondence` entity | `correspondence/Correspondence.java` | 581A.7, 582B, 583A | §N.8 annotated entity (verbatim); `integration/ai/gate/AiExecutionGate.java` (JSONB + `@Version` template) | `@Table(name="correspondence")`. Raw-UUID refs (`customerId`/`projectId`/`filedByMemberId`, NOT `@ManyToOne`); `direction` `@Enumerated(EnumType.STRING)` len 10 default INBOUND; `subject` len 500; `bodyText`/`bodyHtml` `columnDefinition="TEXT"`; `fromAddress` not null len 320; `toAddresses`/`ccAddresses` `@JdbcTypeCode(SqlTypes.JSON) List<String>` jsonb; `messageId` not null len 512; `source` not null len 30 default "MCP"; `@Version int version`; `@PrePersist onCreate` (sets created/updated/filedAt), `@PreUpdate onUpdate`. No `tenant_id`, no `@Filter`. |
| 581A.4 | Create `CorrespondenceRepository` | `correspondence/CorrespondenceRepository.java` | 581A.7, 586A | §N.8 repository JPQL block; `integration/ai/gate/AiExecutionGateRepository.java` | `Optional<Correspondence> findByMessageId(String)` (idempotency lookup); `Page<Correspondence> findByProjectId(@Param UUID pid, Pageable)` ORDER BY receivedAt DESC; `Page<Correspondence> findByCustomerId(@Param UUID cid, Pageable)` ORDER BY receivedAt DESC. Inherited `findById` is tenant-safe via search_path. |
| 581A.5 | Create `CorrespondenceService` | `correspondence/CorrespondenceService.java` | 581A.7, 581A.8 | §N.3.a `fileInbound` code block; `timeentry/TimeEntryService.java` for `@Transactional` service shape | `@Transactional fileInbound(FileCorrespondenceCommand, ActorContext)`: `validateLinkage` then `findByMessageId` → if present return `FileCorrespondenceResult.idempotent(id)` (no second persist); else build `Correspondence` (direction INBOUND, fields from cmd, `filedByMemberId=actor.memberId()`, source from cmd) + save + `created(id)`. Catch unique-constraint violation on race → re-read `findByMessageId` → return winner id. `validateLinkage(customerId, projectId)`: ≥1 non-null else `InvalidStateException`. List passthroughs (`listByProject`/`listByCustomer`, used by 586A). `attachmentCount(correspondenceId)` helper (defer body or stub for 586A — count `documents` by `correspondence_id`). |
| 581A.6 | Create correspondence DTOs | `correspondence/dto/FileCorrespondenceCommand.java`, `correspondence/dto/FileCorrespondenceResult.java`, `correspondence/dto/CorrespondenceListResponse.java` | 581A.7, 582B, 586A | §N.4 JSON shapes; existing `*/dto/*` records | `FileCorrespondenceCommand` (record: `matterId`/`customerId`/`messageId`/`subject`/`bodyText`/`bodyHtml`/`fromAddress`/`toAddresses`/`ccAddresses`/`sentAt`/`receivedAt`/`threadKey`/`source`). `FileCorrespondenceResult` (record + static `created(id)`/`idempotent(id)` factories exposing `correspondenceId()` + `idempotent()`). `CorrespondenceListResponse` (record: `id`, `subject`, `fromAddress`, `receivedAt`, `attachmentCount`, `direction`) — used by 586A. |
| 581A.7 | Extend `Document` for correspondence link | `document/Document.java` (modify) | 581A.8, 583A | existing `Document.aiExecutionId` field + setter; existing `source` constants | Add nullable `@Column(name="correspondence_id") UUID correspondenceId` + getter/setter (mirror `aiExecutionId`). Add `EMAIL_INGEST` to the `source` String constants (alongside `MANUAL`/`AI_GENERATED`). No new storage path. |
| 581A.8 | Entity + idempotency + linkage tests | `backend/src/test/java/.../correspondence/CorrespondenceServiceTest.java`, `correspondence/CorrespondenceRepositoryTest.java` | ~7 tests: (1) V130 runs clean; (2) `fileInbound` persists a row INBOUND + correct linkage + `findById` round-trip; (3) idempotent re-file (same `messageId`) returns same id, persists nothing new; (4) re-file with same `messageId` but different `matterId` returns existing record (no re-link); (5) both-null linkage rejected (service `validateLinkage`); (6) both-null rejected at DB CHECK level; (7) JSONB `toAddresses`/`ccAddresses` round-trip | `tasks/phase80` repo-test setup (`@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")`, bind `RequestScopes.TENANT_ID`) | Provision/bind tenant; assert persist + idempotency + linkage invariant (both service and DB CHECK). |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/tenant/V130__create_correspondence_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/Correspondence.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/Direction.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/dto/FileCorrespondenceCommand.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/dto/FileCorrespondenceResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/dto/CorrespondenceListResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceRepositoryTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java` — add `correspondenceId` + setter; add `EMAIL_INGEST` source constant

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGate.java` — JSONB + `@Version` + `@PrePersist`/`@PreUpdate` template
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — leaner tenant-aware entity reference
- `backend/src/main/resources/db/migration/tenant/V129__create_mcp_egress_consents.sql` — migration format

### Architecture Decisions
- **Single flat `Correspondence`, ≥1-non-null linkage** ([ADR-319](../adr/ADR-319-inbound-correspondence-domain.md)) — one row per filed email with an optional `threadKey` (no thread/message split); `customer_id`/`project_id` both nullable with a table CHECK + service validation.
- **Idempotent on `message_id`** ([ADR-319](../adr/ADR-319-inbound-correspondence-domain.md)) — UNIQUE index is the race backstop; re-file returns the existing id, never re-links.
- **Attachments as `Document`s via `correspondence_id` FK** ([ADR-319](../adr/ADR-319-inbound-correspondence-domain.md)) — one nullable FK column mirroring `ai_execution_id`, no polymorphic source-reference, no new storage path.

### Non-scope
- No MCP tool (582B). No capability/audit (582A). No attach (583A). No resolve (584A). No gate (585). No REST/frontend (586).

---

## Epic 582: MCP Write Capability + Audit Family + `file_correspondence`

**Goal**: Open the write chapter on the read-only MCP server. Introduce the `MCP_WRITE` capability (auto-granted to owner/admin) and the `mcp.write.*` audit family + activity formatter arms (582A), then build the first write tool — `file_correspondence` — as a `@Component` carrying the inline guard preamble (`effectiveState()` → `MCP_WRITE` gate → linkage validation → `fileInbound` → write audit). This slice proves the read-only-user-rejection and tenant-isolation invariants for all subsequent write tools.

**References**: Architecture §N.3 (inline guard preamble), §N.3.a (`file_correspondence`), §N.4 (tool surface + JSON shapes + read-only-rejected error), §N.6 (POPIA/egress symmetry), §N.8 (write-tool annotated skeleton, backend change table), §N.9 (permission model), §N.10 Slices 1-2; [ADR-321](../adr/ADR-321-mcp-write-tool-category.md), [ADR-320](../adr/ADR-320-byoc-ingestion-boundary.md).

**Dependencies**: 581A (`CorrespondenceService`, `FileCorrespondenceCommand/Result`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **582A** | 582A.1–582A.4 | ~4 backend files (1 `Capability` mod + 1 `OrgRoleService` mod + 1 audit-registry mod + 1 formatter mod) + 1 test | `MCP_WRITE` capability + owner/admin auto-grant; `mcp.write.*` audit-family registration; `ActivityMessageFormatter` arms + `correspondence` entity-name resolver. |
| **582B** | 582B.1–582B.5 | ~7 backend files (1 tool component + ~2 mcp DTOs + 3 test files) | `CorrespondenceWriteTools` `@Component` + `file_correspondence` with inline write-guard preamble; `mcp/dto/*` write DTOs; file-via-MCP + read-only-rejected + tenant-isolation tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 582A.1 | Add `MCP_WRITE` capability | `orgrole/Capability.java` (modify) | 582B.4 | existing `MCP_ACCESS` capability value | Add `MCP_WRITE` enum value alongside `MCP_ACCESS`. Distinct from `MCP_ACCESS` (read) and `AI_REVIEW` (gate approval). |
| 582A.2 | Auto-grant `MCP_WRITE` to owner/admin | `orgrole/OrgRoleService.java` (modify) | 582B.4 | existing `MCP_ACCESS` grant in `OrgRoleService` default role sets | Grant `MCP_WRITE` to Owner/Admin by default (matches the `MCP_ACCESS` auto-grant pattern). Other roles need explicit grant (v2 row-grant migration, not required here). |
| 582A.3 | Register `mcp.write.*` audit family | `audit/AuditEventTypeRegistry.java` (modify) | 582B.2/.3 | existing `mcp.tool.invoked` + `mcp.access.denied` registrations | Register `mcp.write.correspondence_filed`, `mcp.write.correspondence_refiled`, `mcp.write.document_attached`, `mcp.write.task_proposed` (severity `NOTICE`, group `STANDARD` per §N.8). Distinct family from `mcp.tool.invoked` (reads). |
| 582A.4 | Add activity formatter arms | `activity/ActivityMessageFormatter.java` (modify) | covered by 582B.2 | existing `mcp.tool.invoked` formatter case + entity-name resolvers | Add formatter arms for `mcp.write.correspondence_filed` / `…_document_attached` / `…_task_proposed`; add a `correspondence` entity-name resolver (resolves `correspondenceId` → subject for the feed message). |
| 582B.1 | Create `CorrespondenceWriteTools` + `file_correspondence` | `mcp/tool/CorrespondenceWriteTools.java` | 582B.2/.3/.4 | §N.8 write-tool skeleton (verbatim); `mcp/tool/DocumentTools.java` (existing tool-component shape); `mcp/McpCapabilityGuard.java` `gatedTool` usage | `@Component` ctor: `CorrespondenceService`, `McpEnablementService`, `AuditService`, `McpMetrics`, `ObjectMapper` (NOT `CustomerRepository`/`CustomerProjectService` — those live with the read tool in 584A). `@McpTool(name="file_correspondence")` with params per §N.4 (`matterId?`/`customerId?` ≥1, `messageId` req, email fields). Inline preamble: (1) `!enablement.effectiveState()` → `McpToolErrors.asResult(McpError.notEnabled())`; (2) `McpCapabilityGuard.gatedTool("MCP_WRITE", "file_correspondence", auditService, metrics, objectMapper, …)` wrapping; (3) build `FileCorrespondenceCommand` + `fileInbound`; (4) audit `mcp.write.correspondence_filed` (created) / `…_refiled` (idempotent) via the guard success path with `McpAuditMetadata` (`entityRef=correspondenceId`, `param("idempotent", …)`). Return write DTO. |
| 582B.2 | Create MCP write DTOs | `mcp/dto/FileCorrespondenceToolResponse.java` (+ any shared write response shape) | 582B.3 | §N.4 response JSON; existing `mcp/dto/*` read DTOs | Response shape `{correspondenceId, idempotent}` (created vs re-file no-op). Keep tool DTOs in `mcp/dto/` separate from domain DTOs. |
| 582B.3 | `file_correspondence` happy-path + idempotency MCP test | `backend/src/test/java/.../mcp/CorrespondenceWriteToolsTest.java` | ~4 tests: file via MCP creates a `Correspondence` row + returns `{correspondenceId, idempotent:false}` + emits `mcp.write.correspondence_filed`; re-file same `messageId` returns `idempotent:true` + emits `mcp.write.correspondence_refiled`; both-null linkage → `McpError.invalidRequest`; not-enabled tenant → `McpError.notEnabled` | existing MCP tool tests (Phase 78) under `mcp/`; §N.8 testing strategy | Drive the tool method directly with bound `RequestScopes`; assert DB row + audit emission. |
| 582B.4 | Read-only-user-rejected (capability gate) test | `backend/src/test/java/.../mcp/McpWriteCapabilityGateTest.java` | ~3 tests: member with `MCP_ACCESS` but **no** `MCP_WRITE` → `FORBIDDEN` ("MCP access is read-only") on `file_correspondence` + `mcp.access.denied` audited; member with `MCP_WRITE` succeeds; `MCP_WRITE` not auto-granted to non-owner/admin roles | §N.4 read-only-rejected error JSON; §N.8 "MCP write-capability gate" test | The capability gate is the cross-cutting invariant reused by 583A/585B — establish it here. |
| 582B.5 | Tenant-isolation test (**mandatory**) | `backend/src/test/java/.../mcp/CorrespondenceWriteToolsTenantIsolationTest.java` | ~2 tests: a member in tenant A cannot file into tenant B; correspondence filed in tenant A is invisible/not-found under tenant B's schema | §N.3 tenant boundary; §N.8 mandatory tenant-isolation test | Mandatory per requirements. Bind `RequestScopes.TENANT_ID` per tenant; assert search_path isolation. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceWriteTools.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/FileCorrespondenceToolResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/CorrespondenceWriteToolsTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/McpWriteCapabilityGateTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/CorrespondenceWriteToolsTenantIsolationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` — add `MCP_WRITE`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRoleService.java` — auto-grant `MCP_WRITE` to owner/admin
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistry.java` — `mcp.write.*` family
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java` — `mcp.write.*` arms + `correspondence` resolver

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpCapabilityGuard.java` — `gatedTool` signature
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/DocumentTools.java` — existing tool-component shape
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/McpToolErrors.java`, `McpAuditMetadata.java`, `McpEnablementService.java`

### Architecture Decisions
- **First writes on the read-only server; `MCP_WRITE` distinct from read** ([ADR-321](../adr/ADR-321-mcp-write-tool-category.md)) — a read-only MCP user (`MCP_ACCESS`, no `MCP_WRITE`) cannot write; inline-per-tool enforcement (no central `tools/call` interceptor in Spring AI M6).
- **Writes are enablement-gated, not separately consent-gated; distinct `mcp.write.*` audit family** ([ADR-321](../adr/ADR-321-mcp-write-tool-category.md)) — ingress into Kazi creates no new POPIA egress, so no second consent toggle; the write audit family gives a defensible record of what AI wrote.
- **BYOC trust boundary** ([ADR-320](../adr/ADR-320-byoc-ingestion-boundary.md)) — Kazi validates tenant/capability/linkage/idempotency; it never reads the mailbox or reasons.

### Non-scope
- No `attach_document` (583A). No `resolve_matter_by_email` (584A). No gate (585). No REST/frontend (586).

---

## Epic 583: `attach_document` (presigned reuse + correspondence stamp)

**Goal**: Add the Tier-1 `attach_document` write tool — one MCP tool with a `phase` enum (INITIATE | CONFIRM) — that reuses `DocumentService`'s presigned-upload + confirm handshake (the same path the web UI uses), then stamps `correspondence_id` + `source=EMAIL_INGEST` on confirm so the attachment is a first-class `Document` that appears in the matter's existing documents list and additionally links to its correspondence. No inline base64, no new storage path.

**References**: Architecture §N.3.b (`attach_document` flow + presigned reuse), §N.4 (tool params + INITIATE/CONFIRM JSON shapes), §N.6 (S3 key reuse), §N.8 (backend change table), §N.10 Slice 3; [ADR-319](../adr/ADR-319-inbound-correspondence-domain.md).

**Dependencies**: 581A (`Document.correspondenceId` setter, `EMAIL_INGEST`); 582A (`MCP_WRITE` capability + `mcp.write.*` audit); 582B (`CorrespondenceWriteTools` component + guard pattern).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **583A** | 583A.1–583A.4 | ~5 backend files (1 tool method add + ~2 mcp DTOs + 2 test files) | `attach_document` tool (`phase` INITIATE\|CONFIRM) reusing `DocumentService` presigned + confirm; stamp `correspondence_id` + `EMAIL_INGEST` on confirm; INITIATE/CONFIRM + documents-list tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 583A.1 | Add `attach_document` tool | `mcp/tool/CorrespondenceWriteTools.java` (modify) | 583A.3/.4 | §N.3.b code block; existing `DocumentTools.java` + `DocumentService` presigned usage | One `@McpTool(name="attach_document")` with required `phase` enum param. INITIATE (`correspondenceId`, `fileName`, `contentType`, `size`): resolve scope (CUSTOMER via `documentService.initiateCustomerUpload`, or PROJECT via `initiateUpload` when a matter is known) → return `{documentId, presignedUrl, expiresInSeconds}`. CONFIRM (`documentId`): `documentService.confirmUpload(documentId, actor)` → `doc.setCorrespondenceId(correspondenceId)` + `doc.setSource("EMAIL_INGEST")` → return `{documentId, status:"UPLOADED", correspondenceId}`. Same inline `MCP_WRITE` guard preamble as `file_correspondence`. Add `DocumentService` (+ `CorrespondenceService` for correspondence-lookup/scope-resolution) to the ctor. |
| 583A.2 | Create attach MCP DTOs | `mcp/dto/AttachDocumentInitResponse.java`, `mcp/dto/AttachDocumentConfirmResponse.java` | 583A.3 | §N.4 INITIATE/CONFIRM response JSON; existing `mcp/dto/*` | INITIATE response `{documentId, presignedUrl, expiresInSeconds}`; CONFIRM response `{documentId, status, correspondenceId}`. |
| 583A.3 | INITIATE/CONFIRM happy-path test | `backend/src/test/java/.../mcp/AttachDocumentToolTest.java` | ~4 tests: INITIATE returns a presigned URL + `documentId`; CONFIRM stamps `correspondence_id` + `source=EMAIL_INGEST` on the `Document`; confirm is idempotent/safe to retry per `documentId`; emits `mcp.write.document_attached` with `{documentId, correspondenceId, fileName}` | §N.8 "attach_document" test; existing `DocumentService` test setup (mock S3 / presign) | Reuse the project's S3 mock convention (no testcontainers). Assert the `Document` row carries `correspondence_id`. |
| 583A.4 | Attachment-in-documents-list + capability test | `backend/src/test/java/.../mcp/AttachDocumentDocumentsListTest.java` | ~2 tests: the confirmed attachment appears in the matter's existing documents list (it *is* a `Document`); read-only user (no `MCP_WRITE`) rejected on `attach_document` | §N.3.b ("appears in the matter's documents list"); 582B.4 capability-gate pattern | Confirms reuse (no bespoke correspondence-document UI) + the capability gate applies to all three write tools. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/AttachDocumentInitResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/AttachDocumentConfirmResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/AttachDocumentToolTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/AttachDocumentDocumentsListTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceWriteTools.java` — add `attach_document` + `DocumentService`/`CorrespondenceService` ctor deps

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/DocumentService.java` — `initiateCustomerUpload`/`initiateUpload`/`confirmUpload`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/DocumentTools.java` — existing document tool + presigned usage

### Architecture Decisions
- **Presigned-upload + confirm, not inline base64** ([ADR-319](../adr/ADR-319-inbound-correspondence-domain.md)) — multi-MB attachments reuse the one storage path the codebase trusts; bytes never bloat the MCP JSON or the JVM heap.
- **One tool with a `phase` param, not two tools** — keeps the "exactly four MCP tools" invariant; the two-step handshake rides the `phase` enum.
- **Attachment is a normal `Document` + `correspondence_id`, reusing the existing S3 key structure** ([ADR-319](../adr/ADR-319-inbound-correspondence-domain.md)) — no new key prefix; it appears in the matter documents list and obeys the same retention/access rules.

### Non-scope
- No `resolve_matter_by_email` (584A). No gate (585). No REST/frontend (586). No outbound/send path.

---

## Epic 584: `resolve_matter_by_email` (read tool)

**Goal**: Add the read helper that lets Claude pick the right matter before writing. `resolve_matter_by_email` reuses `CustomerRepository.findByEmail` + the customer's matter listing, returns `{customer, matters[]}` (zero → empty, many → all), and keeps the Phase 78 **read** shape (`MCP_ACCESS`, `mcp.tool.invoked` audit) — it does **not** require `MCP_WRITE`. Kazi never auto-files on a guess; Claude disambiguates and passes an explicit `matterId`/`customerId` to the write tools.

**References**: Architecture §N.3.c (`resolve_matter_by_email` code + zero/many behaviour), §N.4 (read tool surface + JSON shapes), §N.8 (read tool lives with `ClientTools`, not `CorrespondenceWriteTools`), §N.9 (permission model), §N.10 Slice 4; [ADR-323](../adr/ADR-323-email-matter-linking.md).

**Dependencies**: 582A (the MCP write epic establishes the write/read split; this read tool ships after the capability split is clear). Can run in parallel with 583A/585A.

**Scope**: Backend only

**Estimated Effort**: S

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **584A** | 584A.1–584A.3 | ~4 backend files (1 `ClientTools` mod + ~2 mcp read DTOs + 1 test file) | `resolve_matter_by_email` read tool in `ClientTools` (reuse `findByEmail` + matter listing); `MCP_ACCESS`, read audit; zero/many; match/no-match/multi-matter tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 584A.1 | Add `resolve_matter_by_email` to `ClientTools` | `mcp/tool/ClientTools.java` (modify) | 584A.3 | §N.3.c code block; existing read tools in `ClientTools`/`MatterTools` (read-shape preamble) | `@McpTool(name="resolve_matter_by_email")` params `email` (req), `subjectHint`?, `reference`? (pass-through hints only — **no server-side fuzzy matching**). Read preamble: `effectiveState()` → `MCP_ACCESS` (read capability, NOT `MCP_WRITE`) → body → `mcp.tool.invoked` audit. Body: `customerRepository.findByEmail(email.trim().toLowerCase())`; if present, `customerProjectService.listProjectsForCustomer(customerId, actor)`; return `{customer, matters[]}`. Zero match → `{customer:null, matters:[]}`; multi-matter → all. `CustomerRepository`/`CustomerProjectService` deps live here (per §N.8), not in `CorrespondenceWriteTools`. |
| 584A.2 | Create resolve MCP DTOs | `mcp/dto/ResolveMatterResponse.java`, `mcp/dto/McpMatterDto.java` (+ reuse existing `McpClientDto` if present) | 584A.3 | §N.4 response JSON; existing `mcp/dto/*` read DTOs (`McpClientDto`) | `ResolveMatterResponse {customer (nullable McpClientDto), matters (List<McpMatterDto>)}`. `McpMatterDto {id, name}`. Reuse the existing read DTO for client if one exists. |
| 584A.3 | resolve match/no-match/multi-matter + capability test | `backend/src/test/java/.../mcp/ResolveMatterByEmailToolTest.java` | ~5 tests: match → `{customer, matters[≥1]}`; no match → `{customer:null, matters:[]}`; customer with multiple matters → all returned; emits `mcp.tool.invoked` (read family, **never** `mcp.write.*`); requires `MCP_ACCESS` not `MCP_WRITE` (a read-only MCP user can call it) | §N.8 "resolve_matter_by_email" test; existing read-tool tests | Drive the tool with bound `RequestScopes`; assert read-audit family + capability shape. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/ResolveMatterResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/McpMatterDto.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/ResolveMatterByEmailToolTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/ClientTools.java` — add `resolve_matter_by_email`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` — `findByEmail`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/MatterTools.java` — existing matter-listing read tool + `CustomerProjectService` usage

### Architecture Decisions
- **`resolve_matter_by_email` is a read tool, lives with the read-tool family** ([ADR-323](../adr/ADR-323-email-matter-linking.md), §N.8) — `MCP_ACCESS`, `mcp.tool.invoked` audit; the customer/matter-lookup deps stay out of `CorrespondenceWriteTools`.
- **Claude disambiguates with an explicit target; Kazi does no server-side auto-file/fuzzy-match** ([ADR-323](../adr/ADR-323-email-matter-linking.md)) — zero → empty, many → all; hints are pass-through only.

### Non-scope
- No write (582/583). No gate (585). No REST/frontend (586).

---

## Epic 585: Gate-over-MCP — `propose_task` + Synthetic Execution + Executor Arm

**Goal**: Expose `AiExecutionGate` *creation* over MCP for the first time, proven end-to-end by exactly one Tier-2 tool. 585A builds the plumbing: a synthetic zero-cost `AiExecution` (`recordSyntheticMcpExecution`, status `EXTERNALLY_EXECUTED`) that preserves the `execution_id NOT NULL` invariant; a new public `AiExecutionGateService.createGate(...)` + `findPendingGateForCorrespondence(...)`; a 7th `GateAction.CreateTaskFromCorrespondenceAction` record + parse/execute arm + `executeCreateTask` helper (injecting `TaskService` into `GateActionExecutor`). 585B builds the `propose_task` tool with the v1 open-gate dedupe guard. Approval stays in-product unchanged via the existing `AiExecutionGateController` (`AI_REVIEW`); on approval the executor calls `TaskService.createTask` with a correspondence back-link.

**References**: Architecture §N.2.3 (synthetic execution), §N.3.d (`propose_task` flow + open-gate guard + executor wiring), §N.4 (tool surface + JSON shapes), §N.5.2 (end-to-end sequence), §N.8 (`GateAction` record + executor arm + `createGate` method, `recordSyntheticMcpExecution`, `TaskService` injection), §N.10 Slice 5; [ADR-322](../adr/ADR-322-tiered-write-safety-and-gate-over-mcp.md), [ADR-281](../adr/ADR-281-execution-gate-pattern-attorney-liability.md).

**Dependencies**: 581A (`Correspondence`); 582B (`CorrespondenceWriteTools` component + `MCP_WRITE` guard). 585B after 585A.

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **585A** | 585A.1–585A.6 | ~7 backend files (1 exec-service mod + 1 gate-service mod + 1 gate-repo mod + 1 `GateAction` mod + 1 `GateActionExecutor` mod + 1 exec-entity mod if enum + 1 test) | `recordSyntheticMcpExecution`; `AiExecutionGateService.createGate` + `findPendingGateForCorrespondence`; `GateAction.CreateTaskFromCorrespondenceAction` + parse/execute arm + `executeCreateTask` (inject `TaskService`); plumbing tests. |
| **585B** | 585B.1–585B.3 | ~4 backend files (1 tool method add + 1 mcp DTO + 2 test files) | `propose_task` tool (inline `MCP_WRITE` guard + v1 open-gate dedupe + synthetic execution + `createGate`); `mcp.write.task_proposed` audit; end-to-end gate test. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 585A.1 | Add `recordSyntheticMcpExecution` | `integration/ai/execution/AiExecutionService.java` (modify); `integration/ai/execution/AiExecution.java` (modify **only if** status is a closed enum) | 585A.6 | §N.2.3 synthetic-execution field table; existing `AiExecutionService` record/create methods | `recordSyntheticMcpExecution(UUID memberId)` → persist `AiExecution` with actor=member, provider="MCP", source="MCP"/"BYOC", model null/"byoc", input/output tokens 0, cost 0, status `EXTERNALLY_EXECUTED`. **First confirm whether `AiExecution.status` is a closed enum (→ add `EXTERNALLY_EXECUTED` value) or a free String (→ use the constant)** by inspecting `AiExecution.java` (see Open Questions). |
| 585A.2 | Add `createGate` to `AiExecutionGateService` | `integration/ai/gate/AiExecutionGateService.java` (modify) | 585A.6, 585B.3 | §N.8 `createGate` block; existing `approve`/`reject` methods (persist + event emission shape) | `@Transactional createGate(AiExecution execution, String gateType, Map<String,Object> proposedAction, String aiReasoning, Instant expiresAt)` → `new AiExecutionGate(execution, gateType, proposedAction, aiReasoning, expiresAt)` (status defaults PENDING) → save → optionally publish `AiGatePendingEvent` (omit if no listener in v1) → emit `ai.gate.created` audit (mandatory). Mirrors `approve`/`reject` shape. |
| 585A.3 | Add `findPendingGateForCorrespondence` | `integration/ai/gate/AiExecutionGateRepository.java` (modify); `AiExecutionGateService.java` (passthrough) | 585A.6, 585B.3 | §N.3.d open-gate guard; existing gate-repo PENDING queries | Query for a PENDING gate where `gate_type=:gateType` and `proposedAction->>'correspondence_id' = :correspondenceId` (jsonb extraction). Service passthrough `findPendingGateForCorrespondence(UUID correspondenceId, String gateType)` → `Optional<UUID>`. Backs the v1 dedupe guard. |
| 585A.4 | Add `CreateTaskFromCorrespondenceAction` to `GateAction` | `integration/ai/gate/GateAction.java` (modify) | 585A.6 | §N.8 `GateAction` block (verbatim); existing 6 records + sealed `permits` | Add `CreateTaskFromCorrespondenceAction(UUID correspondenceId, UUID projectId, String title, String description, LocalDate dueDate, UUID assigneeId)` to the sealed `permits` clause + as a record (7th subtype). |
| 585A.5 | Add executor parse + execute arm + `executeCreateTask` | `integration/ai/gate/GateActionExecutor.java` (modify) | 585A.6 | §N.8 executor arms (verbatim); existing `parseAction`/`execute` switch arms | `parseAction`: `case "CREATE_TASK_FROM_CORRESPONDENCE" -> new CreateTaskFromCorrespondenceAction(...)` (UUID/LocalDate parsing per §N.8). `execute`: `case CreateTaskFromCorrespondenceAction a -> executeCreateTask(a, reviewerId)` (exhaustive switch — compiler-enforced). `executeCreateTask` calls `taskService.createTask(projectId, title, description, "MEDIUM", …, dueDate, ActorContext.forMember(reviewerId), Map.of("correspondenceId", …), …, assigneeId, …)` — **confirm the live `createTask` signature** (see Open Questions). **Inject `TaskService` as a new ctor dependency** (currently absent — §N.8). |
| 585A.6 | Gate plumbing tests | `backend/src/test/java/.../integration/ai/gate/GateOverMcpPlumbingTest.java` | ~5 tests: synthetic execution persists with provider=MCP, cost=0, status `EXTERNALLY_EXECUTED`; `createGate` produces a PENDING gate with non-null `execution_id`; `findPendingGateForCorrespondence` finds the open gate by jsonb correspondence_id; approving the gate (`AI_REVIEW`) → executor → `TaskService.createTask` creates a `Task` linked back via `customFields.correspondenceId`; exhaustive-switch compiles (no default arm) | §N.8 testing strategy; existing `GateActionExecutor` tests | "PASS means observed" — gate record → approval → task row + back-link. |
| 585B.1 | Add `propose_task` tool | `mcp/tool/CorrespondenceWriteTools.java` (modify) | 585B.2/.3 | §N.3.d code block; 582B `file_correspondence` guard pattern | `@McpTool(name="propose_task")` params `projectId` (req), `correspondenceId` (req), `title` (req), `description?`, `dueDate?`, `assigneeId?`. Inline `MCP_WRITE` guard preamble. Body: v1 open-gate guard `findPendingGateForCorrespondence(correspondenceId, "CREATE_TASK_FROM_CORRESPONDENCE")` → if present return `{gateId, status:PENDING, duplicate:true}` (no second execution/gate); else `recordSyntheticMcpExecution(member)` → build jsonb payload → `createGate(synthetic, "CREATE_TASK_FROM_CORRESPONDENCE", payload, reasoning, now.plus(72h))` → audit `mcp.write.task_proposed {gateId, correspondenceId, projectId}` → return `{gateId, status:PENDING, duplicate:false}`. **Never creates a `Task`.** Add `AiExecutionService`/`AiExecutionGateService` to the ctor. |
| 585B.2 | Create `propose_task` MCP DTO | `mcp/dto/ProposeTaskToolResponse.java` | 585B.3 | §N.4 propose_task response JSON | `{gateId, status, duplicate, message}` (new gate vs duplicate message per §N.4). |
| 585B.3 | `propose_task` end-to-end + dedupe test | `backend/src/test/java/.../mcp/ProposeTaskToolTest.java` | ~5 tests: propose creates a PENDING gate + synthetic execution (cost 0), **no `Task` exists before approval**; a second propose for the same correspondence returns the existing `gateId` with `duplicate:true` (no second gate); approval (`AI_REVIEW`) via `AiExecutionGateController` creates the task; read-only user (no `MCP_WRITE`) rejected; emits `mcp.write.task_proposed` | §N.5.2 sequence; §N.8 "propose_task creates gate only"/"approval executes" tests | Mandatory: assert **no Task** before approval; the open-gate guard prevents duplicate pending gates. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/dto/ProposeTaskToolResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateOverMcpPlumbingTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/ProposeTaskToolTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecutionService.java` — `recordSyntheticMcpExecution`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/execution/AiExecution.java` — `EXTERNALLY_EXECUTED` (only if status is a closed enum)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java` — `createGate` + `findPendingGateForCorrespondence`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateRepository.java` — open-gate jsonb query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateAction.java` — 7th record + sealed permits
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java` — parse/execute arm + `executeCreateTask` + `TaskService` injection
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceWriteTools.java` — add `propose_task` + ctor deps

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGate.java` — gate constructor/lifecycle
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateController.java` — existing approve/reject (unchanged, `AI_REVIEW`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — `createTask` signature

### Architecture Decisions
- **Gate CREATION over MCP, approval stays in-product** ([ADR-322](../adr/ADR-322-tiered-write-safety-and-gate-over-mcp.md), [ADR-281](../adr/ADR-281-execution-gate-pattern-attorney-liability.md)) — `propose_task` creates only a PENDING gate; no Tier-2 bypass path to `TaskService` exists; `AI_REVIEW` approves via the existing controller/UI/executor unchanged.
- **Synthetic zero-cost `AiExecution`, not a nullable FK** ([ADR-322](../adr/ADR-322-tiered-write-safety-and-gate-over-mcp.md)) — preserves `execution_id NOT NULL` and `gate.getExecution().getId()`; cost 0 is the BYOC cost-model signal.
- **v1 open-gate dedupe guard** ([ADR-322](../adr/ADR-322-tiered-write-safety-and-gate-over-mcp.md)) — one PENDING gate per `(correspondenceId, CREATE_TASK_FROM_CORRESPONDENCE)`; fuller idempotency-key dedupe is v2.
- **Exactly one Tier-2 proof tool** — bulk task/deadline extraction and contact creation are v2.

### Non-scope
- No bulk task/deadline extraction, no calendar entities, no contact creation (all v2). No second gate/executor/approval UI. No REST/frontend (586).

---

## Epic 586: Frontend — Correspondence Tab + Gate-Origin Display + QA Capstone

**Goal**: Surface the new domain leanly and prove the whole loop observed. 586A (backend) adds the read-only correspondence-list REST endpoints the tab consumes (authenticated member + tenant scope + existing project/customer view-access check; no MCP capability). 586B (frontend) adds a read-only "Correspondence" tab on matter detail (subject, from, date, attachment count, link to documents) and surfaces the originating correspondence on the gate review screen for `CREATE_TASK_FROM_CORRESPONDENCE` gates. 586C is the QA capstone: an observed end-to-end run plus full quality gates and the mandatory tenant-isolation + BYOC-boundary regressions.

**References**: Architecture §N.4 (thin REST surface + access control), §N.3.d (paging via `Pageable`), §N.8 (frontend changes table), §N.9 (correspondence-list REST permission row), §N.10 Slice 6; [ADR-319](../adr/ADR-319-inbound-correspondence-domain.md).

**Dependencies**: 581A (entity/repo/service); 582B + 583A + 585B (the write tools that produce the data the UI shows); a fully wired backend.

**Scope**: 586A backend; 586B frontend; 586C QA (both).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **586A** | 586A.1–586A.3 | ~5 backend files (1 controller + 1 response DTO + service-method confirm + 2 test files) | `CorrespondenceController` `GET …/correspondence` (paginated, view-access-gated, no MCP cap); `CorrespondenceListResponse` + attachment-count; list/access/isolation tests. |
| **586B** | 586B.1–586B.4 | ~7 frontend files (1 tab page/component + 1 list component + 1 gate-origin component edit + 1 api client + component tests) | Matter-detail Correspondence tab; gate-origin display on the gate review screen; `lib/api` correspondence client; component tests. |
| **586C** | 586C.1–586C.3 | ~2-3 test/QA artefacts (E2E spec + regression assertions) | Observed end-to-end run; full `./mvnw verify` + frontend `lint/build/test` + prettier; tenant-isolation + BYOC-boundary regression. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 586A.1 | Create `CorrespondenceController` | `correspondence/CorrespondenceController.java` | 586A.3 | `proposal/ProposalController.java` (thin controller); existing project/customer document-list endpoints (view-access check) | `GET /api/projects/{projectId}/correspondence?page=&size=` + `GET /api/customers/{customerId}/correspondence?page=&size=` → `Page<CorrespondenceListResponse>`. `@PreAuthorize("isAuthenticated()")` + the **existing project/customer view-access check** (same as listing a matter's documents). **No MCP capability** (MCP caps gate MCP tools, not REST). Default page size 50, capped (mirror `McpPagination` ceiling). |
| 586A.2 | Wire list + attachment-count in `CorrespondenceService` | `correspondence/CorrespondenceService.java` (modify); `correspondence/dto/CorrespondenceListResponse.java` (confirm from 581A.6) | 586A.3 | §N.3.d paging; `ix_documents_correspondence` index | `listByProject(projectId, pageable)` / `listByCustomer(customerId, pageable)` → map to `CorrespondenceListResponse` with `attachmentCount` (count `documents` by `correspondence_id`, served by `ix_documents_correspondence`). |
| 586A.3 | Correspondence-list REST tests | `backend/src/test/java/.../correspondence/CorrespondenceListRestTest.java`, `correspondence/CorrespondenceListAccessTest.java` | ~6 tests: list returns filed correspondence newest-first with attachment count + `direction`; pagination caps page size; member who can view the matter can read its correspondence **without** any MCP capability; member without project/customer view-access → 403; tenant B cannot read tenant A's correspondence list (**mandatory isolation**) | §N.9 REST permission row; phase80 MockMvc setup | Assert the access check matches document-list access, not MCP caps. |
| 586B.1 | Add correspondence api client | `frontend/lib/api/correspondence.ts` (or extend existing matter api module) | 586B.4 | existing `frontend/lib/api/*` clients; documents-list fetch | Typed client for `GET …/correspondence` (project + customer); `CorrespondenceListResponse` type. Next.js 16 conventions (params are Promises). |
| 586B.2 | Add Correspondence tab to matter detail | `frontend/app/(app)/org/[slug]/projects/[projectId]/…` (matter detail tab + a `CorrespondenceList` component) | 586B.4 | existing matter-detail tabs + documents/timeline components (reuse, do not rebuild) | Read-only "Correspondence" tab: subject, from, date, attachment count, link to the matter's documents. Render `bodyHtml` as text / sanitised (never raw HTML — stored-XSS guard, §N.2.1). No inbox, no threading, no compose. |
| 586B.3 | Surface originating correspondence on gate review | `frontend/app/(app)/org/[slug]/ai/…` (gate review queue/detail) | 586B.4 | existing gate review surface (unchanged approve/reject) | For a `CREATE_TASK_FROM_CORRESPONDENCE` gate, show the originating correspondence (subject + link) so approval is informed. No new approval screen. |
| 586B.4 | Frontend component tests | `frontend/.../__tests__/correspondence-*.test.tsx` (per project convention) | ~4 tests: tab renders filed correspondence + attachment count; empty state; gate review shows originating correspondence subject + link; `bodyHtml` not rendered as raw HTML | existing frontend component tests; `frontend/CLAUDE.md` test conventions | Per `frontend/CLAUDE.md` (Shadcn, Next.js 16). |
| 586C.1 | Observed end-to-end run | (QA artefact / E2E spec) `qa/…` or `frontend/e2e/correspondence.spec.ts` per convention | observed: MCP tool call → backend log → DB row / S3 object / gate record → UI | requirements §"PASS means observed"; `/acceptance-spec` if applicable | Exercise the four tools against a running MCP server (Claude or an MCP test client): resolve → file → attach → propose → approve in Kazi → task created; confirm the tab + gate-origin render. Reproduce-before-fix for any bug. |
| 586C.2 | Full quality gates | (no new files — CI/verify) | full `./mvnw verify` clean (not narrowed); frontend `pnpm lint && pnpm build && pnpm test` + prettier `format:check` | requirements §"Style & Boundaries" | Both suites green. |
| 586C.3 | Tenant-isolation + BYOC-boundary regression | `backend/src/test/java/.../mcp/Phase81BoundaryTest.java` (boundary asserts) | ~4 asserts: tenant A cannot read/file tenant B correspondence (regression); **no Gmail/Google/IMAP dependency added**; **no inbound HTTP webhook/poll added**; **no Anthropic/LLM call added to the backend** by this phase (grep + dependency check) | requirements §"Hard boundaries to verify at review"; §N.8 "Boundary asserts" | Mandatory boundary review gate; fail the build if any boundary is breached. |

### Key Files

**Create (backend, 586A):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceListRestTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceListAccessTest.java`

**Create (frontend, 586B):**
- `frontend/lib/api/correspondence.ts`
- `frontend/app/(app)/org/[slug]/projects/[projectId]/…` correspondence tab + `CorrespondenceList` component
- `frontend/.../__tests__/correspondence-*.test.tsx`

**Create (QA, 586C):**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/Phase81BoundaryTest.java`
- E2E spec per project convention (`frontend/e2e/…` or `qa/…`)

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/CorrespondenceService.java` — list + attachment-count (586A)
- `frontend/app/(app)/org/[slug]/ai/…` gate review surface — originating-correspondence display (586B)

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java` — thin controller + paging
- existing matter-detail tabs + documents/timeline components; existing gate review surface
- `frontend/CLAUDE.md` — Next.js 16 / Shadcn conventions

### Architecture Decisions
- **Correspondence-list REST is in-app, not MCP** (§N.4/§N.9) — authenticated member + tenant scope + existing project/customer view-access check; **no MCP capability** required; no write REST (writes are MCP-only by design).
- **Lean surfacing, no inbox** ([ADR-319](../adr/ADR-319-inbound-correspondence-domain.md)) — read-only tab + gate-origin link only; attachments appear in the existing documents list; `bodyHtml` rendered as text (stored-XSS guard); no portal exposure (firm-internal).
- **PASS means observed** (requirements) — the capstone exercises the tools against a running server and traces MCP → log → DB/S3/gate → UI.

### Non-scope
- No inbox/threading/reply/read-receipts UI. No compose/send. No portal exposure. No write REST. The consumer Claude skill (Gmail-read → reason → call Kazi tools) ships separately in `../claude-for-legal-sa`.

---

### Critical Files for Implementation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/correspondence/Correspondence.java` (new entity — the foundation that gates every other slice; mirror `integration/ai/gate/AiExecutionGate.java`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/mcp/tool/CorrespondenceWriteTools.java` (new — hosts all three write tools with inline `MCP_WRITE` guards; the central write surface)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/GateActionExecutor.java` (modify — the 7th action arm + `TaskService` injection; the gate-over-MCP execution seam)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/ai/gate/AiExecutionGateService.java` (modify — new public `createGate` + `findPendingGateForCorrespondence`; first MCP-exposed gate creation)
- `backend/src/main/resources/db/migration/tenant/V130__create_correspondence_tables.sql` (new — the `correspondence` table + `documents.correspondence_id` ALTER; re-verify the next-free V number before applying)