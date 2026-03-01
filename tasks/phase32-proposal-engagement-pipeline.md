# Phase 32 — Proposal → Engagement Pipeline

Phase 32 introduces the **Proposal to Engagement Pipeline** -- the connective tissue that wires DocTeams' existing capabilities (Tiptap documents, project templates, rate cards, retainer agreements, invoicing, customer lifecycle, portal) into a single client-facing flow. A firm member composes a formal proposal using the Tiptap rich editor with structured fee configuration (fixed, hourly, or retainer), sends it to a client contact via the portal, and on acceptance the system automatically orchestrates the full engagement setup: project creation, billing setup, team assignment, and notifications.

**Architecture doc**: `architecture/phase32-proposal-engagement-pipeline.md`

**ADRs**:
- [ADR-124](../adr/ADR-124-proposal-storage-model.md) -- Proposal Storage Model (standalone entity with embedded Tiptap content)
- [ADR-125](../adr/ADR-125-acceptance-orchestration-transaction-boundary.md) -- Acceptance Orchestration Transaction Boundary (single transaction + async side effects)
- [ADR-126](../adr/ADR-126-milestone-invoice-creation-strategy.md) -- Milestone Invoice Creation Strategy (create all invoices on acceptance)
- [ADR-127](../adr/ADR-127-portal-proposal-rendering.md) -- Portal Proposal Rendering (server-side HTML in read-model)
- [ADR-128](../adr/ADR-128-proposal-numbering-strategy.md) -- Proposal Numbering Strategy (sequential counter with fixed `PROP-NNNN` prefix)
- [ADR-129](../adr/ADR-129-fee-model-architecture.md) -- Fee Model Architecture (single fee model per proposal)

**Migrations**: V51 (tenant schema), V12 (global/portal schema)

**Dependencies on prior phases**: Phase 31 (Tiptap JSON/renderer), Phase 28 (AcceptanceRequest pattern, portal read-model sync), Phase 24 (EmailNotificationChannel), Phase 17 (RetainerAgreementService), Phase 16 (ProjectTemplateService), Phase 10 (InvoiceService, InvoiceCounter pattern, InvoiceLineType), Phase 8 (OrgSettings branding, rate cards), Phase 7 (PortalContact, portal auth, read-model), Phase 6.5 (NotificationService), Phase 6 (AuditService), Phase 14 (Customer lifecycle transitions).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 230 | Proposal Entity Foundation & Migration | Backend | -- | M | 230A, 230B | |
| 231 | Proposal CRUD & Lifecycle Backend | Backend | 230 | L | 231A, 231B | |
| 232 | Send Flow & Portal Read-Model Sync | Backend | 231 | M | 232A, 232B | |
| 233 | Acceptance Orchestration | Backend | 231 | L | 233A, 233B | |
| 234 | Portal Proposal Backend & Expiry Processor | Backend | 232, 233 | M | 234A, 234B | |
| 235 | Audit, Notifications & Activity Integration | Backend | 231 | S | 235A | |
| 236 | Proposals Frontend -- List & Pipeline Stats | Frontend | 231 | M | 236A, 236B | |
| 237 | Proposals Frontend -- Create/Edit & Detail Pages | Frontend | 232, 236 | L | 237A, 237B | |
| 238 | Proposals Frontend -- Customer Tab & Project Link | Frontend | 236 | S | 238A | |
| 239 | Portal Frontend -- Proposal Pages | Portal | 234 | M | 239A, 239B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────
[E230A V51 migration: proposals,
 milestones, team, counter tables]
        |
[E230B Proposal entity + enums +
 repos + ProposalNumberService
 + entity unit tests]
        |
[E231A ProposalService: CRUD,
 validation, milestone/team
 replacement, filter query]
        |
[E231B ProposalController: CRUD
 endpoints + customer-scoped
 + stats endpoint + DTOs
 + integration tests]
        |
        +──────────────────────────+──────────────────────+
        |                          |                      |
[E232A V12 global migration:  [E233A Orchestration:   [E235A Audit events,
 portal_proposals table +      FIXED fee (milestones   notifications,
 ProposalPortalSyncService +   + single invoice),       activity feed
 ProposalVariableResolver +    project creation         integration for
 TiptapRenderer integration]   (template + bare),       all proposal
        |                      team assignment,          lifecycle events]
[E232B ProposalService.send()  HOURLY (no-op),
 + send validation + email     customer lifecycle
 + in-app notification         + tests]
 + portal sync + tests]               |
        |                      [E233B Orchestration:
        |                       RETAINER fee →
        |                       RetainerAgreement,
        |                       InvoiceLineType.FIXED_FEE,
        |                       OrchestrationResult,
        |                       error handling/rollback
        |                       + tests]
        |                              |
        +──────────────+───────────────+
                       |
               [E234A PortalProposalController:
                list, detail, accept, decline
                endpoints + portal auth scoping
                + decline/withdraw flow + tests]
                       |
               [E234B ProposalExpiryProcessor:
                @Scheduled job, tenant iteration,
                expiry notifications + email
                + tests]

FRONTEND TRACK (after E231B)
─────────────────────────────
[E236A proposals/page.tsx:
 proposal-actions.ts,
 ProposalListTable,
 ProposalStatusBadge,
 sidebar nav addition]
        |
[E236B ProposalPipelineStats
 cards, filters, sort
 + frontend tests]
        |                              +───────────────────+
        +──────────────────────+       |                   |
        |                      |  [E238A Customer detail
[E237A proposals/new:     [E237B proposals/[id]:       Proposals tab +
 ProposalForm,             detail page,                project "Created
 FeeConfigSection,         actions (Send,              from Proposal"
 MilestoneEditor,          Edit, Delete,               link + tests]
 TeamMemberPicker,         Withdraw, Copy,
 template picker,          View Project),
 proposal-actions.ts       SendProposalDialog,
 + edit page + tests]      ProposalPreview
                           + tests]

PORTAL TRACK (after E234A)
───────────────────────────
[E239A Portal proposals
 list page + layout nav
 + proposal-actions.ts
 + portal ProposalStatusBadge]
        |
[E239B Portal proposal
 detail page: rendered
 HTML, fee summary,
 milestones, accept/
 decline buttons,
 expired banner + tests]
```

**Parallel opportunities**:
- After E231B: E232A, E233A, E235A, and E236A can all start in parallel (4 independent tracks).
- After E232B and E233B: E234A starts.
- After E234B: E239A starts (portal frontend).
- Frontend track (E236-E238) is fully independent of backend tracks E232-E235 except for API existence (E231B is sufficient).
- E237A and E238A can run in parallel after E236B.

---

## Implementation Order

### Stage 0: Database Migration & Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 230 | 230A | V51 tenant migration: CREATE TABLE proposals, proposal_milestones, proposal_team_members, proposal_counters with all columns, constraints, indexes, and counter seed row. ~1 new migration file. Backend only. | **Done** (PR #467) |
| 0b | 230 | 230B | Proposal, ProposalMilestone, ProposalTeamMember, ProposalCounter entities + ProposalStatus/FeeModel enums + 4 repositories + ProposalNumberService + entity unit tests (~15 tests). ~12 new files. Backend only. | |

### Stage 1: Service & Controller

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 231 | 231A | ProposalService: full CRUD, validation, milestone/team replacement, filtered listing, customer-scoped query. ~2 new files. Backend only. | |
| 1b | 231 | 231B | ProposalController: 9 endpoints + DTOs + pipeline stats query + integration tests (~20 tests). ~2 new files, ~1 new test file. Backend only. | |

### Stage 2: Send Flow, Orchestration & Cross-Cutting (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 232 | 232A | V12 global migration (portal_proposals table) + ProposalPortalSyncService + ProposalVariableResolver + TiptapRenderer integration. ~3 new files, ~1 migration. Backend only. | |
| 2b (parallel) | 233 | 233A | ProposalOrchestrationService: FIXED fee (milestones + single), project creation (template + bare), team assignment, HOURLY no-op, customer PROSPECT transition + tests (~8 tests). ~2 new files, ~1 test file. Backend only. | |
| 2c (parallel) | 235 | 235A | Audit events for all proposal lifecycle transitions + notification templates (accepted/declined/expired/sent) + activity feed integration + tests (~5 tests). ~2 new/modified files. Backend only. | |
| 2d (parallel) | 236 | 236A | Proposals list page + proposal-actions.ts server actions + ProposalListTable + ProposalStatusBadge + sidebar nav update. ~5 new/modified files. Frontend only. | |

### Stage 3: Send Integration, Orchestration Completion & Frontend Stats

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 232 | 232B | ProposalService.sendProposal() + validation + portal sync + email to portal contact + in-app notification + audit + tests (~10 tests). ~2 modified files, ~1 test file. Backend only. | |
| 3b (parallel) | 233 | 233B | Orchestration: RETAINER fee path (RetainerAgreement creation), InvoiceLineType.FIXED_FEE extension, OrchestrationResult VO, error handling, transaction rollback tests + tests (~8 tests). ~3 modified files. Backend only. | |
| 3c (parallel) | 236 | 236B | ProposalPipelineStats component + filters + sort controls + frontend tests (~5 tests). ~3 new/modified files. Frontend only. | |

### Stage 4: Portal Backend, Frontend Create/Edit & Customer Integration

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 234 | 234A | PortalProposalController (4 endpoints) + portal auth scoping + accept triggers orchestration + decline/withdraw flow + tests (~10 tests). ~1 new controller, ~1 test file. Backend only. | |
| 4b (parallel) | 237 | 237A | Create/edit proposal pages: ProposalForm, FeeConfigSection, MilestoneEditor, TeamMemberPicker, template picker + edit route + tests (~7 tests). ~7 new files. Frontend only. | |
| 4c (parallel) | 238 | 238A | Customer detail Proposals tab + Project detail "Created from Proposal" link + tests (~3 tests). ~3 modified files. Frontend only. | |

### Stage 5: Expiry Processor, Detail Page & Portal Frontend

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 234 | 234B | ProposalExpiryProcessor: @Scheduled job, tenant iteration, notification + email on expiry + portal sync + tests (~5 tests). ~1 new file, ~1 test file. Backend only. | |
| 5b (parallel) | 237 | 237B | Proposal detail page + SendProposalDialog + ProposalPreview + context-dependent actions (Send/Edit/Delete/Withdraw/Copy/View Project) + tests (~5 tests). ~5 new files. Frontend only. | |
| 5c (parallel) | 239 | 239A | Portal proposals list page + nav link + portal proposal-actions.ts + portal ProposalStatusBadge. ~4 new files. Portal only. | |

### Stage 6: Portal Detail Page

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 239 | 239B | Portal proposal detail: rendered HTML body, fee summary card, milestones table, accept/decline buttons, expired banner, confirmation dialogs + tests (~5 tests). ~3 new files. Portal only. | |

### Timeline

```
Stage 0: [230A] → [230B]                                            (sequential)
Stage 1: [231A] → [231B]                                            (sequential)
Stage 2: [232A] // [233A] // [235A] // [236A]                      (parallel)
Stage 3: [232B] // [233B] // [236B]                                 (parallel)
Stage 4: [234A] // [237A] // [238A]                                 (parallel)
Stage 5: [234B] // [237B] // [239A]                                 (parallel)
Stage 6: [239B]                                                      (final)
```

**Critical path**: 230A → 230B → 231A → 231B → 233A → 233B → 234A → 239A → 239B (9 slices sequential at most).

**Fastest path with parallelism**: 230A → 230B → 231A → 231B → then 4 parallel tracks → converge at 234A → portal frontend. Estimated: 17 slices total, 9 slices on critical path.

---

## Epic 230: Proposal Entity Foundation & Migration

**Goal**: Create the V51 tenant schema migration with all proposal tables, then build the four new JPA entities (`Proposal`, `ProposalMilestone`, `ProposalTeamMember`, `ProposalCounter`), enums (`ProposalStatus`, `FeeModel`), repositories, and `ProposalNumberService` as the foundation for the proposal domain.

**References**: Architecture doc Sections 32.2, 32.7, 32.8.1, 32.8.3. ADR-124, ADR-128, ADR-129.

**Dependencies**: None -- this is the greenfield foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **230A** | 230.1--230.4 | V51 tenant migration: CREATE TABLE proposals (23 columns, 4 constraints, 4 indexes), proposal_milestones (8 columns, 1 index), proposal_team_members (5 columns, 1 index), proposal_counters (2 columns + seed INSERT). ~1 new migration file. Backend only. | **Done** (PR #467) |
| **230B** | 230.5--230.16 | `Proposal` entity (23 fields, lifecycle methods, status guards) + `ProposalMilestone` entity + `ProposalTeamMember` entity + `ProposalCounter` entity + `ProposalStatus` enum + `FeeModel` enum + 4 repositories + `ProposalNumberService` (atomic increment) + entity unit tests (~15 tests). ~12 new files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 230.1 | Create V51 tenant migration -- proposals table | 230A | | New file: `backend/src/main/resources/db/migration/tenant/V51__create_proposal_tables.sql`. CREATE TABLE proposals with all 23 columns: id (UUID PK), proposal_number (VARCHAR(20) NOT NULL UNIQUE), title (VARCHAR(200) NOT NULL), customer_id (UUID NOT NULL FK customers), portal_contact_id (UUID nullable), status (VARCHAR(20) NOT NULL DEFAULT 'DRAFT'), fee_model (VARCHAR(20) NOT NULL), fixed_fee_amount (NUMERIC(12,2)), fixed_fee_currency (VARCHAR(3)), hourly_rate_note (VARCHAR(500)), retainer_amount (NUMERIC(12,2)), retainer_currency (VARCHAR(3)), retainer_hours_included (NUMERIC(6,1)), content_json (JSONB NOT NULL DEFAULT '{}'), project_template_id (UUID), sent_at (TIMESTAMPTZ), expires_at (TIMESTAMPTZ), accepted_at (TIMESTAMPTZ), declined_at (TIMESTAMPTZ), decline_reason (VARCHAR(500)), created_project_id (UUID), created_retainer_id (UUID), created_by_id (UUID NOT NULL), created_at (TIMESTAMPTZ NOT NULL DEFAULT now()), updated_at (TIMESTAMPTZ NOT NULL DEFAULT now()). CHECK constraints: proposals_status_check, proposals_fee_model_check. |
| 230.2 | V51 migration -- proposals indexes | 230A | 230.1 | Same file. 4 indexes: `idx_proposals_customer_id ON proposals(customer_id)`, `idx_proposals_status ON proposals(status)`, `idx_proposals_created_by ON proposals(created_by_id)`, `idx_proposals_expires_at ON proposals(expires_at) WHERE status = 'SENT' AND expires_at IS NOT NULL` (partial index for expiry processor). |
| 230.3 | V51 migration -- milestone and team member tables | 230A | 230.1 | Same file. CREATE TABLE proposal_milestones (id UUID PK, proposal_id UUID NOT NULL FK proposals ON DELETE CASCADE, description VARCHAR(200) NOT NULL, percentage NUMERIC(5,2) NOT NULL, relative_due_days INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0, invoice_id UUID, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ) + index. CREATE TABLE proposal_team_members (id UUID PK, proposal_id UUID NOT NULL FK proposals ON DELETE CASCADE, member_id UUID NOT NULL, role VARCHAR(100), sort_order INTEGER NOT NULL DEFAULT 0) + index. |
| 230.4 | V51 migration -- proposal counter table with seed | 230A | | Same file. CREATE TABLE proposal_counters (id UUID PK DEFAULT gen_random_uuid(), next_number INTEGER NOT NULL DEFAULT 1). INSERT INTO proposal_counters (id, next_number) VALUES (gen_random_uuid(), 1). Pattern: see `V23__invoicing.sql` InvoiceCounter seed row. |
| 230.5 | Create `ProposalStatus` enum | 230B | | New file: `proposal/ProposalStatus.java`. Values: DRAFT, SENT, ACCEPTED, DECLINED, EXPIRED. No additional methods needed -- lifecycle is managed by entity. Pattern: `acceptance/AcceptanceRequestStatus.java`. |
| 230.6 | Create `FeeModel` enum | 230B | | New file: `proposal/FeeModel.java`. Values: FIXED, HOURLY, RETAINER. Include display label method: `getDisplayLabel()` returning "Fixed Fee", "Hourly", "Retainer". Pattern: `invoice/InvoiceLineType.java`. |
| 230.7 | Create `Proposal` entity | 230B | 230.5, 230.6 | New file: `proposal/Proposal.java`. 23 fields with `@Column` annotations. `contentJson` as `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)`. Constructor: `Proposal(String proposalNumber, String title, UUID customerId, FeeModel feeModel, UUID createdById)`. Lifecycle methods: `markSent(UUID portalContactId)`, `markAccepted()`, `markDeclined(String reason)`, `markExpired()`. Guards: `isEditable()`, `isTerminal()`, `requireEditable()`. Setters for mutable fields with `requireEditable()` guard. `@PrePersist`/`@PreUpdate` for timestamps. Pattern: `acceptance/AcceptanceRequest.java` for entity structure and lifecycle pattern. |
| 230.8 | Create `ProposalMilestone` entity | 230B | | New file: `proposal/ProposalMilestone.java`. Fields: id (UUID), proposalId (UUID), description (String), percentage (BigDecimal), relativeDueDays (int), sortOrder (int), invoiceId (UUID nullable), createdAt (Instant), updatedAt (Instant). Constructor: `ProposalMilestone(UUID proposalId, String description, BigDecimal percentage, int relativeDueDays, int sortOrder)`. `@PrePersist`/`@PreUpdate` callbacks. Pattern: same simple entity pattern as `acceptance/AcceptanceRequest.java`. |
| 230.9 | Create `ProposalTeamMember` entity | 230B | | New file: `proposal/ProposalTeamMember.java`. Fields: id (UUID), proposalId (UUID), memberId (UUID), role (String nullable), sortOrder (int). No timestamps (simple association). Constructor: `ProposalTeamMember(UUID proposalId, UUID memberId, String role, int sortOrder)`. |
| 230.10 | Create `ProposalCounter` entity | 230B | | New file: `proposal/ProposalCounter.java`. Fields: id (UUID), nextNumber (int). Pattern: `invoice/InvoiceCounter.java`. |
| 230.11 | Create `ProposalRepository` | 230B | 230.7 | New file: `proposal/ProposalRepository.java`. Extends `JpaRepository<Proposal, UUID>`. Methods: `Page<Proposal> findByCustomerId(UUID customerId, Pageable pageable)`, `List<Proposal> findByStatusAndExpiresAtBefore(ProposalStatus status, Instant now)` (for expiry processor), `@Query` `findFiltered(UUID customerId, String status, String feeModel, UUID createdById, Pageable pageable)` with optional null checks. `long countByStatus(ProposalStatus status)`. Pattern: `expense/ExpenseRepository.java`. |
| 230.12 | Create `ProposalMilestoneRepository` | 230B | 230.8 | New file: `proposal/ProposalMilestoneRepository.java`. Extends `JpaRepository<ProposalMilestone, UUID>`. Methods: `List<ProposalMilestone> findByProposalIdOrderBySortOrder(UUID proposalId)`, `void deleteByProposalId(UUID proposalId)`. |
| 230.13 | Create `ProposalTeamMemberRepository` | 230B | 230.9 | New file: `proposal/ProposalTeamMemberRepository.java`. Extends `JpaRepository<ProposalTeamMember, UUID>`. Methods: `List<ProposalTeamMember> findByProposalIdOrderBySortOrder(UUID proposalId)`, `void deleteByProposalId(UUID proposalId)`. |
| 230.14 | Create `ProposalCounterRepository` | 230B | 230.10 | New file: `proposal/ProposalCounterRepository.java`. Extends `JpaRepository<ProposalCounter, UUID>`. Method: `@Query("SELECT c FROM ProposalCounter c") Optional<ProposalCounter> findCounter()`. Pattern: `invoice/InvoiceCounterRepository.java`. |
| 230.15 | Create `ProposalNumberService` | 230B | 230.14 | New file: `proposal/ProposalNumberService.java`. Method: `String allocateNumber()`. Loads counter, reads `nextNumber`, formats as `String.format("PROP-%04d", nextNumber)`, increments counter, saves. Uses pessimistic lock or atomic update: `@Modifying @Query("UPDATE ProposalCounter c SET c.nextNumber = c.nextNumber + 1")` then read. Pattern: `invoice/InvoiceNumberService.java`. |
| 230.16 | Write Proposal entity unit tests | 230B | 230.7 | New file: `proposal/ProposalTest.java`. Tests (~15): (1) `constructor_setsDraftStatus`; (2) `markSent_fromDraft_succeeds`; (3) `markSent_fromSent_throws`; (4) `markSent_requiresPortalContactId`; (5) `markAccepted_fromSent_succeeds`; (6) `markAccepted_fromDraft_throws`; (7) `markDeclined_fromSent_succeeds`; (8) `markDeclined_setsReasonAndTimestamp`; (9) `markExpired_fromSent_succeeds`; (10) `markExpired_fromAccepted_throws`; (11) `isEditable_trueForDraft_falseForOthers`; (12) `isTerminal_trueForAcceptedDeclinedExpired`; (13) `requireEditable_throwsForNonDraft`; (14) `setTitle_onDraft_succeeds`; (15) `setTitle_onSent_throws`. Pattern: `acceptance/AcceptanceRequestTest.java`. |

### Key Files

**Slice 230A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V51__create_proposal_tables.sql`

**Slice 230B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/FeeModel.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalMilestone.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalTeamMember.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalCounter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalMilestoneRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalTeamMemberRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalCounterRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalNumberService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalTest.java`

**Slice 230B -- Read for context:**
- `backend/src/main/java/.../acceptance/AcceptanceRequest.java` -- entity lifecycle pattern (status guards, `@PrePersist`/`@PreUpdate`, `requireStatus()`)
- `backend/src/main/java/.../invoice/InvoiceCounter.java` -- counter entity pattern
- `backend/src/main/java/.../invoice/InvoiceCounterRepository.java` -- counter repository pattern
- `backend/src/main/java/.../invoice/InvoiceNumberService.java` -- number allocation pattern

### Architecture Decisions

- **Standalone entity with embedded Tiptap content**: Proposals own their `contentJson` JSONB column directly. No coupling to `DocumentTemplate` or `GeneratedDocument`. See ADR-124.
- **Single fee model per proposal**: `FeeModel` is a single enum on the entity, not a collection of fee items. See ADR-129.
- **Sequential numbering with PROP-NNNN prefix**: `ProposalCounter` follows `InvoiceCounter` pattern exactly. Counter incremented at creation time (not send time). See ADR-128.
- **No multitenancy boilerplate**: Per ADR-064, schema boundary handles isolation. Entities are plain `@Entity` + `JpaRepository`.

---

## Epic 231: Proposal CRUD & Lifecycle Backend

**Goal**: Implement `ProposalService` with full CRUD, validation, milestone/team bulk replacement, filtered listing, pipeline stats, and customer-scoped queries. Add `ProposalController` with all firm-facing REST endpoints.

**References**: Architecture doc Sections 32.3.1, 32.4.1, 32.8.1.

**Dependencies**: Epic 230 (entity foundation).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **231A** | 231.1--231.8 | `ProposalService`: createProposal (number allocation, customer validation, fee validation), getProposal, updateProposal (DRAFT guard), deleteProposal (DRAFT guard), listProposals (filtered), replaceMilestones (percentage validation), replaceTeamMembers, getStats (aggregate query), customer-scoped listing. ~1 new service file. Backend only. | |
| **231B** | 231.9--231.16 | `ProposalController`: 9 endpoints (CRUD + milestones + team + stats + customer-scoped) + `ProposalResponse`/`CreateProposalRequest`/`UpdateProposalRequest` DTOs + integration tests (~20 tests). ~2 new files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 231.1 | Implement `ProposalService.createProposal()` | 231A | 230B | New file: `proposal/ProposalService.java`. Validates: customer exists via `CustomerRepository.findById()` (404 if not). Allocates number via `ProposalNumberService.allocateNumber()`. Creates `Proposal` entity with DRAFT status. Validates fee fields per model: FIXED requires `fixedFeeAmount > 0`, RETAINER requires `retainerAmount > 0`, HOURLY has no amount requirement. Saves proposal. Returns saved entity. Pattern: `expense/ExpenseService.java` for validation pattern. |
| 231.2 | Implement `ProposalService.getProposal()` and `listProposals()` | 231A | 230B | In `ProposalService.java`. `getProposal(UUID proposalId)`: load proposal (throw 404 if not found), eagerly load milestones and team members via separate repository calls. `listProposals(ProposalFilterCriteria criteria, Pageable pageable)`: delegate to `ProposalRepository.findFiltered()`. `listByCustomer(UUID customerId, Pageable pageable)`: delegate to `ProposalRepository.findByCustomerId()`. |
| 231.3 | Implement `ProposalService.updateProposal()` | 231A | 230B | In `ProposalService.java`. Validates: proposal exists (404), proposal is DRAFT (`requireEditable()` -- 409 if not). Updates mutable fields: title, customerId, portalContactId, feeModel, fee configuration fields, contentJson, projectTemplateId, expiresAt. Saves. Returns updated entity. |
| 231.4 | Implement `ProposalService.deleteProposal()` | 231A | 230B | In `ProposalService.java`. Validates: proposal exists (404), proposal is DRAFT (409 if not). Hard delete via `proposalRepository.deleteById()`. Cascade deletes milestones and team members (ON DELETE CASCADE in DB). |
| 231.5 | Implement `ProposalService.replaceMilestones()` | 231A | 230B | In `ProposalService.java`. `replaceMilestones(UUID proposalId, List<MilestoneRequest> milestones)`. Validates: proposal exists, is DRAFT, feeModel is FIXED. If milestones provided: validate all percentages sum to exactly 100.00 (`BigDecimal` comparison). Delete existing milestones via `ProposalMilestoneRepository.deleteByProposalId()`. Create new `ProposalMilestone` entities with sequential sortOrder. Save all. |
| 231.6 | Implement `ProposalService.replaceTeamMembers()` | 231A | 230B | In `ProposalService.java`. `replaceTeamMembers(UUID proposalId, List<TeamMemberRequest> members)`. Validates: proposal exists, is DRAFT. For each member: validate member exists via `MemberRepository.findById()` (404 if not). Delete existing team members via `ProposalTeamMemberRepository.deleteByProposalId()`. Create new `ProposalTeamMember` entities. Save all. |
| 231.7 | Implement `ProposalService.getStats()` | 231A | 230B | In `ProposalService.java`. Returns `ProposalStats` record: `totalDraft`, `totalSent`, `totalAccepted`, `totalDeclined`, `totalExpired` (from `countByStatus()`), `conversionRate` (accepted / (accepted + declined) * 100, or 0 if none), `averageDaysToAccept` (JPQL avg of `EXTRACT(EPOCH FROM (accepted_at - sent_at)) / 86400` for ACCEPTED proposals). Add custom `@Query` to `ProposalRepository` for average calculation. |
| 231.8 | Create `ProposalFilterCriteria` and `MilestoneRequest`/`TeamMemberRequest` records | 231A | | In `proposal/dto/` sub-package (or nested in service). `ProposalFilterCriteria(UUID customerId, ProposalStatus status, FeeModel feeModel, UUID createdById)`. `MilestoneRequest(String description, BigDecimal percentage, int relativeDueDays)`. `TeamMemberRequest(UUID memberId, String role)`. |
| 231.9 | Create `ProposalController` with CRUD endpoints | 231B | 231A | New file: `proposal/ProposalController.java`. Endpoints: (1) `POST /api/proposals` (ADMIN+) → 201 Created; (2) `GET /api/proposals` (MEMBER+) → paged list with `?customerId&status&feeModel&createdById&page&size&sort`; (3) `GET /api/proposals/{id}` (MEMBER+) → detail with milestones and team; (4) `PUT /api/proposals/{id}` (ADMIN+) → update; (5) `DELETE /api/proposals/{id}` (ADMIN+) → 204 No Content. Read `RequestScopes.MEMBER_ID` for `createdById` on create. Pattern: `expense/ExpenseController.java`. |
| 231.10 | Add milestone and team endpoints to `ProposalController` | 231B | 231.9 | In `ProposalController.java`: (6) `PUT /api/proposals/{id}/milestones` (ADMIN+) → bulk replace milestones; (7) `PUT /api/proposals/{id}/team` (ADMIN+) → bulk replace team members. Both return 200 with updated proposal detail. |
| 231.11 | Add stats and customer-scoped endpoints | 231B | 231.9 | In `ProposalController.java`: (8) `GET /api/proposals/stats` (MEMBER+) → pipeline stats; (9) `GET /api/customers/{customerId}/proposals` (MEMBER+) → customer-scoped paged list. Stats returns `ProposalStatsResponse`. |
| 231.12 | Create `ProposalResponse` and request DTOs | 231B | | In `ProposalController.java` as nested records (or `proposal/dto/`). `ProposalResponse`: all entity fields + computed `customerName` (String), `portalContactName` (String), `createdByName` (String), `projectTemplateName` (String), nested `milestones` list, nested `teamMembers` list. `CreateProposalRequest`: title, customerId, portalContactId (nullable), feeModel, fee fields, contentJson, projectTemplateId, expiresAt. `UpdateProposalRequest`: same nullable fields. `ProposalStatsResponse`: totals, conversionRate, averageDaysToAccept. |
| 231.13 | Write integration tests -- CRUD happy paths | 231B | 231.9 | New file: `proposal/ProposalControllerTest.java`. Tests (1-8): (1) `createProposal_fixedFee_returnsCreated`; (2) `createProposal_hourly_returnsCreated`; (3) `createProposal_retainer_returnsCreated`; (4) `getProposal_returnsDetailWithMilestonesAndTeam`; (5) `listProposals_filterByStatus`; (6) `listProposals_filterByCustomer`; (7) `updateProposal_draftOnly_succeeds`; (8) `deleteProposal_draftOnly_succeeds`. Pattern: `expense/ExpenseControllerTest.java`. |
| 231.14 | Write integration tests -- validation and guards | 231B | 231.13 | Continuing `ProposalControllerTest.java`. Tests (9-14): (9) `createProposal_nonExistentCustomer_returns404`; (10) `createProposal_fixedFee_missingAmount_returns400`; (11) `updateProposal_sentStatus_returns409`; (12) `deleteProposal_sentStatus_returns409`; (13) `createProposal_asMember_returns403`; (14) `getProposal_asMember_succeeds`. |
| 231.15 | Write integration tests -- milestones and team | 231B | 231.13 | Continuing `ProposalControllerTest.java`. Tests (15-20): (15) `replaceMilestones_sumTo100_succeeds`; (16) `replaceMilestones_sumNot100_returns400`; (17) `replaceMilestones_nonFixedFeeModel_returns400`; (18) `replaceTeamMembers_succeeds`; (19) `replaceTeamMembers_nonExistentMember_returns404`; (20) `getStats_returnsAggregates`. |

### Key Files

**Slice 231A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/dto/ProposalFilterCriteria.java` (or nested)

**Slice 231A -- Read for context:**
- `backend/src/main/java/.../expense/ExpenseService.java` -- service CRUD pattern with validation
- `backend/src/main/java/.../customer/CustomerRepository.java` -- customer lookup for validation
- `backend/src/main/java/.../member/MemberRepository.java` -- member lookup for team validation

**Slice 231B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalControllerTest.java`

**Slice 231B -- Read for context:**
- `backend/src/main/java/.../expense/ExpenseController.java` -- controller pattern with RequestScopes
- `backend/src/test/java/.../expense/ExpenseControllerTest.java` -- test scaffolding with MockMvc + JWT

### Architecture Decisions

- **ProposalService owns CRUD and simple lifecycle**: CRUD, validation, milestone/team management. Complex orchestration (acceptance) is in `ProposalOrchestrationService` (Epic 233).
- **Bulk replacement for milestones and team**: DELETE all + INSERT all pattern. Simpler than individual CRUD for child entities that are always managed as a set.
- **Stats use aggregate queries**: `countByStatus` for totals, custom JPQL for average days. No materialized view -- query volume is low.
- **ADMIN+ for write operations**: Proposals represent financial commitments. Same trust level as invoice management.

---

## Epic 232: Send Flow & Portal Read-Model Sync

**Goal**: Implement the proposal send flow (DRAFT to SENT transition with validation, portal read-model sync, email notification, and Tiptap HTML rendering) and the global migration for the `portal_proposals` table.

**References**: Architecture doc Sections 32.3.2, 32.6, 32.7 (V12 global migration). ADR-127.

**Dependencies**: Epic 231 (ProposalService CRUD).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **232A** | 232.1--232.5 | V12 global migration for `portal_proposals` table + `ProposalPortalSyncService` (insert on send, status updates) + `ProposalVariableResolver` (build Tiptap variable context from customer/org data). ~3 new files, ~1 migration. Backend only. | |
| **232B** | 232.6--232.12 | `ProposalService.sendProposal()`: send validation, DRAFT to SENT transition, TiptapRenderer integration for HTML, portal sync, email to portal contact via EmailNotificationChannel, in-app notification to creator, audit event + integration tests (~10 tests). ~2 modified files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 232.1 | Create V12 global migration for portal_proposals | 232A | | New file: `backend/src/main/resources/db/migration/global/V12__portal_proposals.sql`. CREATE TABLE portal.portal_proposals with all columns per architecture doc Section 32.7. Indexes: `idx_portal_proposals_contact ON portal.portal_proposals(portal_contact_id, status)`, `idx_portal_proposals_customer ON portal.portal_proposals(org_id, customer_id)`. Pattern: existing global migrations in `db/migration/global/`. |
| 232.2 | Create `ProposalVariableResolver` | 232A | | New file: `proposal/ProposalVariableResolver.java`. Method: `Map<String, String> buildContext(Proposal proposal, Customer customer, PortalContact contact, OrgSettings orgSettings)`. Returns map: `client_name` = customer.name, `client_contact_name` = contact.name, `proposal_number` = proposal.proposalNumber, `proposal_date` = formatted proposal.createdAt, `fee_total` = formatted fee amount (FIXED: fixedFeeAmount, RETAINER: retainerAmount, HOURLY: ""), `fee_model` = feeModel.getDisplayLabel(), `org_name` = orgSettings.orgName, `expiry_date` = formatted expiresAt or "". Pattern: variable resolution in `template/ContextBuilders.java`. |
| 232.3 | Create `ProposalPortalSyncService` -- insert on send | 232A | 232.1 | New file: `proposal/ProposalPortalSyncService.java`. Method: `void syncProposalToPortal(Proposal proposal, String contentHtml, OrgSettings orgSettings)`. Uses `@Qualifier("portalDataSource")` JDBC template. Inserts row into `portal.portal_proposals` with all denormalized fields: id, org_id, customer_id, portal_contact_id, proposal_number, title, status, fee_model, fee_amount (computed: FIXED=fixedFeeAmount, RETAINER=retainerAmount, HOURLY=null), fee_currency, content_html, milestones_json (serialize milestones to JSON array), sent_at, expires_at, org_name, org_logo_url, org_brand_color, synced_at. Pattern: `portal/PortalReadModelSyncService.java` (Phase 7). |
| 232.4 | Create `ProposalPortalSyncService` -- status update | 232A | 232.3 | In `ProposalPortalSyncService.java`. Method: `void updatePortalProposalStatus(UUID proposalId, String newStatus)`. UPDATE portal.portal_proposals SET status = ?, synced_at = now() WHERE id = ?. Used by accept, decline, and expiry flows. |
| 232.5 | Write unit tests for ProposalVariableResolver | 232A | 232.2 | New file: `proposal/ProposalVariableResolverTest.java`. Tests (~3): (1) `buildContext_fixedFee_includesAmount`; (2) `buildContext_hourly_emptyFeeTotal`; (3) `buildContext_missingExpiry_emptyString`. |
| 232.6 | Implement `ProposalService.sendProposal()` -- validation | 232B | 231A, 232A | In `ProposalService.java`. Method: `Proposal sendProposal(UUID proposalId, UUID portalContactId)`. Validates: (1) proposal exists and is DRAFT; (2) portalContactId is provided and references existing PortalContact via `PortalContactRepository`; (3) portalContact belongs to the proposal's customer; (4) contentJson is not empty (not default `{}`); (5) fee configuration valid per model; (6) if FIXED with milestones: percentages sum to 100. Throw `InvalidStateException` or `ValidationException` with descriptive messages for each failure. |
| 232.7 | Implement `ProposalService.sendProposal()` -- transition and rendering | 232B | 232.6 | Continuing in `ProposalService.java`. After validation: call `proposal.markSent(portalContactId)`. Load Customer, PortalContact, OrgSettings. Build variable context via `ProposalVariableResolver.buildContext()`. Call `TiptapRenderer.renderToHtml(proposal.getContentJson(), variableContext)` to produce HTML. Call `ProposalPortalSyncService.syncProposalToPortal(proposal, html, orgSettings)`. Save proposal. |
| 232.8 | Implement send notifications | 232B | 232.7 | In `ProposalService.sendProposal()`: publish `ProposalSentEvent` as Spring `ApplicationEvent`. Create `@TransactionalEventListener(phase = AFTER_COMMIT)` handler that: (1) sends email to portal contact via `EmailNotificationChannel` with template "new-proposal"; (2) creates in-app notification to proposal creator via `NotificationService`. Pattern: `acceptance/AcceptanceService.java` notification pattern. |
| 232.9 | Create `ProposalSentEvent` domain event | 232B | | New file: `proposal/ProposalSentEvent.java`. Record with fields: `UUID proposalId, UUID portalContactId, String proposalNumber, String customerName, String orgName, String portalContactEmail, String tenantId`. Pattern: `event/TaskCompletedEvent.java`. |
| 232.10 | Write integration tests -- send happy path | 232B | 232.9 | New file (or extend `ProposalControllerTest.java`): `proposal/ProposalSendTest.java`. Tests (1-5): (1) `sendProposal_fromDraft_transitionsToSent`; (2) `sendProposal_setsTimestampAndPortalContact`; (3) `sendProposal_createsPortalReadModelRow`; (4) `sendProposal_rendersHtmlWithVariables`; (5) `sendProposal_portalRowContainsOrgBranding`. |
| 232.11 | Write integration tests -- send validation failures | 232B | 232.10 | Continuing send test file. Tests (6-10): (6) `sendProposal_fromSent_returns409`; (7) `sendProposal_noPortalContact_returns400`; (8) `sendProposal_emptyContent_returns400`; (9) `sendProposal_milestonesSumNot100_returns400`; (10) `sendProposal_contactNotOnCustomer_returns400`. |
| 232.12 | Add send endpoint to ProposalController | 232B | 232.7 | In `ProposalController.java`: `POST /api/proposals/{id}/send` (ADMIN+). Request body: `{ "portalContactId": "UUID" }` (or query param). Returns 200 with updated proposal. Add `POST /api/proposals/{id}/withdraw` (ADMIN+): calls `proposalService.declineProposal(proposalId, "Withdrawn by firm")`. |

### Key Files

**Slice 232A -- Create:**
- `backend/src/main/resources/db/migration/global/V12__portal_proposals.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalPortalSyncService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalVariableResolver.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalVariableResolverTest.java`

**Slice 232A -- Read for context:**
- `backend/src/main/java/.../portal/PortalReadModelSyncService.java` -- portal sync pattern (DataSource, JDBC)
- `backend/src/main/java/.../template/ContextBuilders.java` -- variable resolution pattern

**Slice 232B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalSentEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalSendTest.java`

**Slice 232B -- Modify:**
- `backend/src/main/java/.../proposal/ProposalService.java` -- add sendProposal()
- `backend/src/main/java/.../proposal/ProposalController.java` -- add send + withdraw endpoints

**Slice 232B -- Read for context:**
- `backend/src/main/java/.../acceptance/AcceptanceService.java` -- send flow with notification pattern
- `backend/src/main/java/.../template/TiptapRenderer.java` -- renderToHtml() API
- `backend/src/main/java/.../notification/NotificationService.java` -- in-app notification pattern

### Architecture Decisions

- **Server-side HTML rendering at send time**: Tiptap JSON rendered to HTML once with variables resolved. Stored in `portal_proposals.content_html`. No Tiptap dependency in portal. See ADR-127.
- **Portal sync via direct JDBC**: Uses `portalDataSource` qualifier, not JPA. Portal schema is a separate data source. Consistent with Phase 7 pattern.
- **Branding snapshot at send time**: `org_name`, `org_logo_url`, `org_brand_color` captured from `OrgSettings` at send. Consistent even if firm changes branding later.

---

## Epic 233: Acceptance Orchestration

**Goal**: Implement `ProposalOrchestrationService` -- the core value of Phase 32. When a portal contact accepts a proposal, orchestrate project creation, team assignment, billing setup (FIXED/HOURLY/RETAINER), and customer lifecycle transition in a single database transaction.

**References**: Architecture doc Sections 32.3.3, 32.8.1. ADR-125, ADR-126, ADR-129.

**Dependencies**: Epic 231 (ProposalService CRUD), existing ProjectTemplateService, InvoiceService, RetainerAgreementService, ProjectMemberService, CustomerService.

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **233A** | 233.1--233.7 | `ProposalOrchestrationService` core: project creation (template + bare), team member assignment, FIXED fee path (milestones + single invoice), HOURLY no-op, customer PROSPECT to ONBOARDING transition + integration tests (~8 tests). ~2 new files, ~1 test file. Backend only. | |
| **233B** | 233.8--233.14 | RETAINER fee path (RetainerAgreement creation), `InvoiceLineType.FIXED_FEE` extension, `OrchestrationResult` value object, post-commit event publication (`ProposalAcceptedEvent`), error handling (transaction rollback, failure notification), portal status sync on accept + tests (~8 tests). ~3 modified/new files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 233.1 | Create `ProposalOrchestrationService` skeleton | 233A | 231A | New file: `proposal/ProposalOrchestrationService.java`. `@Service` `@Transactional`. Constructor-injected dependencies: `ProposalRepository`, `ProposalMilestoneRepository`, `ProposalService`, `ProjectService` (or `ProjectRepository`), `ProjectTemplateService`, `ProjectMemberService`, `InvoiceService`, `CustomerService`, `AuditService`, `ApplicationEventPublisher`. Method signature: `OrchestrationResult acceptProposal(UUID proposalId, UUID portalContactId)`. |
| 233.2 | Implement Step 1 -- Update proposal status | 233A | 233.1 | In `ProposalOrchestrationService`. Load proposal (verify status = SENT, verify portalContactId matches). Call `proposal.markAccepted()`. Save. Audit event: `proposal.accepted`. |
| 233.3 | Implement Step 2 -- Create project | 233A | 233.2 | In `ProposalOrchestrationService`. If `proposal.getProjectTemplateId()` is set: call `ProjectTemplateService.instantiate(projectTemplateId, customerId, proposal.getTitle())`. Else: create bare project via `ProjectService.createProject()` with proposal title as name, linked to customer. Set `proposal.setCreatedProjectId(project.getId())`. Save proposal. |
| 233.4 | Implement Step 3 -- Assign team | 233A | 233.3 | In `ProposalOrchestrationService`. Load team members via `ProposalTeamMemberRepository.findByProposalIdOrderBySortOrder()`. For each: call `ProjectMemberService.addMember(project.getId(), teamMember.getMemberId())`. Collect all member IDs for `OrchestrationResult`. |
| 233.5 | Implement Step 4a -- FIXED fee billing setup | 233A | 233.3 | In `ProposalOrchestrationService`. If `feeModel == FIXED`: load milestones. If milestones exist: for each milestone, create DRAFT invoice via `InvoiceService.createDraftInvoice()` with amount = `fixedFeeAmount * percentage / 100`, dueDate = `acceptedAt + relativeDueDays`, description = milestone.description, lineType = FIXED_FEE. Set `milestone.setInvoiceId(invoice.getId())`. Save milestone. If no milestones: create single DRAFT invoice for full `fixedFeeAmount`. |
| 233.6 | Implement Step 5 -- Customer lifecycle transition | 233A | 233.3 | In `ProposalOrchestrationService`. If `customer.getLifecycleStatus() == PROSPECT`: call `CustomerService.transitionToOnboarding(customerId)`. If already ACTIVE or ONBOARDING: no-op. |
| 233.7 | Write orchestration integration tests -- FIXED and HOURLY | 233A | 233.5 | New file: `proposal/ProposalOrchestrationServiceTest.java`. Tests (~8): (1) `accept_fixedWithMilestones_createsNDraftInvoices`; (2) `accept_fixedWithMilestones_invoiceAmountsCorrect`; (3) `accept_fixedWithMilestones_dueDatesCorrect`; (4) `accept_fixedNoMilestones_createsSingleInvoice`; (5) `accept_hourly_noInvoicesCreated`; (6) `accept_withTemplate_createsProjectFromTemplate`; (7) `accept_withoutTemplate_createsBareProject`; (8) `accept_prospectCustomer_transitionsToOnboarding`. Pattern: use Testcontainers, set up tenant schema, create prerequisite entities. |
| 233.8 | Implement Step 4b -- RETAINER fee billing setup | 233B | 233A | In `ProposalOrchestrationService`. If `feeModel == RETAINER`: create `RetainerAgreement` via `RetainerAgreementService.createAgreement()` with amount = `retainerAmount`, currency = `retainerCurrency`, includedHours = `retainerHoursIncluded`, projectId = created project ID, customerId, status = ACTIVE, startDate = today. Set `proposal.setCreatedRetainerId(retainer.getId())`. Save proposal. |
| 233.9 | Add `InvoiceLineType.FIXED_FEE` | 233B | | Modify `invoice/InvoiceLineType.java`: add `FIXED_FEE` enum value. The DB column is `VARCHAR(20)` -- no DDL change needed. |
| 233.10 | Create `OrchestrationResult` value object | 233B | | New file: `proposal/OrchestrationResult.java`. Record: `OrchestrationResult(UUID proposalId, UUID projectId, List<UUID> invoiceIds, UUID retainerAgreementId, List<UUID> teamMemberIds)`. |
| 233.11 | Implement post-commit event publication | 233B | 233A | In `ProposalOrchestrationService`. At end of `acceptProposal()`: publish `ProposalAcceptedEvent` via `applicationEventPublisher.publishEvent()`. Create `@TransactionalEventListener(phase = AFTER_COMMIT)` handler that: notifies creator ("Proposal accepted, project created"), notifies all team members ("You've been assigned to project X"), updates portal status via `ProposalPortalSyncService.updatePortalProposalStatus()`. |
| 233.12 | Create `ProposalAcceptedEvent` domain event | 233B | | New file: `proposal/ProposalAcceptedEvent.java`. Record: `UUID proposalId, UUID createdProjectId, String proposalNumber, String customerName, String projectName, List<UUID> teamMemberIds, String tenantId, String orgId`. |
| 233.13 | Implement error handling and rollback | 233B | 233A | In `ProposalOrchestrationService`. Wrap orchestration in try-catch. On any exception: the `@Transactional` annotation handles rollback automatically. Proposal stays SENT. Log error with all available context. Publish `ProposalOrchestrationFailedEvent` (for failure notification to creator). |
| 233.14 | Write orchestration integration tests -- RETAINER, error, and events | 233B | 233.8 | Continuing `ProposalOrchestrationServiceTest.java`. Tests (~8): (9) `accept_retainer_createsRetainerAgreement`; (10) `accept_retainer_setsCreatedRetainerId`; (11) `accept_teamMembers_allAddedToProject`; (12) `accept_activeCustomer_noLifecycleChange`; (13) `accept_failure_rollsBackAllChanges`; (14) `accept_proposalStaysSent_onFailure`; (15) `accept_fixedFee_invoiceLineTypeIsFixedFee`; (16) `accept_orchestrationResult_containsAllIds`. |

### Key Files

**Slice 233A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalOrchestrationService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalOrchestrationServiceTest.java`

**Slice 233A -- Read for context:**
- `backend/src/main/java/.../projecttemplate/ProjectTemplateService.java` -- `instantiate()` method signature
- `backend/src/main/java/.../project/ProjectService.java` -- `createProject()` pattern
- `backend/src/main/java/.../member/ProjectMemberService.java` -- `addMember()` pattern
- `backend/src/main/java/.../invoice/InvoiceService.java` -- `createDraftInvoice()` or equivalent method
- `backend/src/main/java/.../customer/CustomerService.java` -- lifecycle transition methods

**Slice 233B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/OrchestrationResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalAcceptedEvent.java`

**Slice 233B -- Modify:**
- `backend/src/main/java/.../invoice/InvoiceLineType.java` -- add FIXED_FEE
- `backend/src/main/java/.../proposal/ProposalOrchestrationService.java` -- add RETAINER path, events, error handling

**Slice 233B -- Read for context:**
- `backend/src/main/java/.../retainer/RetainerAgreementService.java` -- `createAgreement()` method
- `backend/src/main/java/.../acceptance/AcceptanceService.java` -- post-commit event listener pattern

### Architecture Decisions

- **Single transaction for all data operations**: All entity modifications in one `@Transactional` method. Notifications are async after commit. See ADR-125.
- **All milestone invoices created on acceptance**: DRAFT invoices with future due dates. See ADR-126.
- **Dedicated orchestration service**: `ProposalOrchestrationService` is separate from `ProposalService` to keep CRUD and orchestration responsibilities isolated.
- **HOURLY is a no-op**: No billing entities created. Billing happens through existing time tracking flow.

---

## Epic 234: Portal Proposal Backend & Expiry Processor

**Goal**: Implement the portal-facing REST API for proposal viewing, acceptance, and decline, plus the scheduled expiry processor for overdue proposals.

**References**: Architecture doc Sections 32.4.2, 32.3.4, 32.3.5, 32.9.2.

**Dependencies**: Epic 232 (portal read-model), Epic 233 (acceptance orchestration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **234A** | 234.1--234.7 | `PortalProposalController`: list proposals for portal contact, get detail (rendered HTML + fee summary + milestones), accept (triggers orchestration), decline (with optional reason). Portal auth scoping. Decline/withdraw flow. ~1 new controller, ~1 test file. Backend only. | |
| **234B** | 234.8--234.12 | `ProposalExpiryProcessor`: @Scheduled job iterating tenant schemas, find SENT proposals past expiry, transition to EXPIRED, notification to creator, email to portal contact, portal sync + tests (~5 tests). ~1 new file, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 234.1 | Create `PortalProposalController` | 234A | 232A, 233A | New file: `proposal/PortalProposalController.java`. Uses portal JWT auth context (`PortalAuthContext` or equivalent from Phase 7). All endpoints scoped to the authenticated portal contact's customer. Pattern: `portal/PortalInvoiceController.java` or `portal/PortalProjectController.java`. |
| 234.2 | Implement `GET /portal/api/proposals` | 234A | 234.1 | In `PortalProposalController`. Query `portal.portal_proposals` WHERE `portal_contact_id = ?` (or `customer_id = ?` depending on scoping decision). Order by `sent_at DESC`. Return list of `PortalProposalSummary` (id, number, title, status, feeModel, feeAmount, feeCurrency, sentAt). Only SENT/ACCEPTED/DECLINED/EXPIRED visible (DRAFT never synced). |
| 234.3 | Implement `GET /portal/api/proposals/{id}` | 234A | 234.1 | In `PortalProposalController`. Load from `portal.portal_proposals` WHERE `id = ?` AND customer scoping check. Return `PortalProposalDetail`: all summary fields + `contentHtml`, `milestonesJson` (parsed), `expiresAt`, `orgName`, `orgLogoUrl`, `orgBrandColor`. |
| 234.4 | Implement `POST /portal/api/proposals/{id}/accept` | 234A | 233A | In `PortalProposalController`. Validate: proposal exists in portal schema, status = SENT, portalContactId matches authenticated contact. Resolve tenant schema from org_id. Execute within tenant context: call `ProposalOrchestrationService.acceptProposal(proposalId, portalContactId)`. Return `PortalAcceptResponse`: proposalId, status=ACCEPTED, acceptedAt, projectName, message. |
| 234.5 | Implement `POST /portal/api/proposals/{id}/decline` | 234A | 234.1 | In `PortalProposalController`. Request body: `{ "reason": "optional text" }`. Validate: proposal exists, status = SENT. Resolve tenant context. Call `ProposalService.declineProposal(proposalId, reason)`. Update portal status via `ProposalPortalSyncService.updatePortalProposalStatus(proposalId, "DECLINED")`. Return updated status. |
| 234.6 | Implement firm-side decline/withdraw in ProposalService | 234A | 231A | In `ProposalService.java`. Method: `declineProposal(UUID proposalId, String reason)`. Load proposal (must be SENT). Call `proposal.markDeclined(reason)`. Save. Audit event: `proposal.declined`. Notify creator if client-initiated (reason != "Withdrawn by firm"). Portal sync: update status. |
| 234.7 | Write portal controller integration tests | 234A | 234.4 | New file: `proposal/PortalProposalControllerTest.java`. Tests (~10): (1) `listProposals_returnsOnlySentAndTerminal`; (2) `listProposals_scopedToPortalContact`; (3) `getProposalDetail_returnsRenderedHtml`; (4) `getProposalDetail_includesMilestones`; (5) `acceptProposal_triggersOrchestration`; (6) `acceptProposal_alreadyAccepted_returns200Idempotent`; (7) `acceptProposal_expired_returns409`; (8) `declineProposal_setsReasonAndStatus`; (9) `declineProposal_alreadyDeclined_returns409`; (10) `getProposal_wrongContact_returns404`. Pattern: portal controller tests from Phase 22/28. |
| 234.8 | Create `ProposalExpiryProcessor` | 234B | 231A, 232A | New file: `proposal/ProposalExpiryProcessor.java`. `@Component`. Method: `@Scheduled(fixedRateString = "${proposal.expiry.interval:3600000}") void processExpiredProposals()`. Iterates all tenant schemas (same pattern as `RecurringScheduleProcessor` or `AcceptanceExpiryProcessor`). For each schema: query `ProposalRepository.findByStatusAndExpiresAtBefore(SENT, Instant.now())`. |
| 234.9 | Implement expiry processing logic | 234B | 234.8 | In `ProposalExpiryProcessor`. For each expired proposal: call `proposal.markExpired()`. Save. Audit event: `proposal.expired`. Publish `ProposalExpiredEvent` for post-commit processing. |
| 234.10 | Implement expiry notifications | 234B | 234.9 | `@TransactionalEventListener(phase = AFTER_COMMIT)` handler for `ProposalExpiredEvent`. (1) In-app notification to creator: "Your proposal [number] for [client] has expired." (2) Email to portal contact: "The proposal from [org] has expired. Contact them if you'd like to discuss further." (3) Portal sync: `updatePortalProposalStatus(proposalId, "EXPIRED")`. |
| 234.11 | Create `ProposalExpiredEvent` | 234B | | New file: `proposal/ProposalExpiredEvent.java`. Record: `UUID proposalId, String proposalNumber, String customerName, String createdByMemberId, String portalContactEmail, String orgName, String tenantId`. |
| 234.12 | Write expiry processor integration tests | 234B | 234.9 | New file: `proposal/ProposalExpiryProcessorTest.java`. Tests (~5): (1) `processExpired_sentPastExpiry_transitionsToExpired`; (2) `processExpired_sentNotYetExpired_noChange`; (3) `processExpired_draftWithExpiry_ignored`; (4) `processExpired_sentNoExpiry_ignored`; (5) `processExpired_updatesPortalStatus`. |

### Key Files

**Slice 234A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalControllerTest.java`

**Slice 234A -- Read for context:**
- `backend/src/main/java/.../portal/PortalProjectController.java` -- portal controller pattern (auth, scoping)
- `backend/src/main/java/.../acceptance/PortalAcceptanceController.java` -- portal acceptance pattern

**Slice 234B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessor.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiredEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessorTest.java`

**Slice 234B -- Read for context:**
- `backend/src/main/java/.../schedule/RecurringScheduleProcessor.java` -- tenant iteration pattern for @Scheduled
- `backend/src/main/java/.../acceptance/AcceptanceExpiryProcessor.java` -- expiry processor pattern

### Architecture Decisions

- **Portal accept is idempotent**: Re-accepting an ACCEPTED proposal returns success (not error). Prevents double-click issues.
- **Tenant context resolution from portal**: Portal controller resolves tenant from org_id on the portal_proposals row, then executes orchestration in tenant context. Same pattern as Phase 28 portal acceptance.
- **Expiry processor uses existing tenant iteration**: Iterates all schemas via the same mechanism as `RecurringScheduleProcessor`. Runs hourly by default (configurable).

---

## Epic 235: Audit, Notifications & Activity Integration

**Goal**: Wire all proposal lifecycle events into the audit trail, notification system, and activity feed using existing infrastructure.

**References**: Architecture doc Sections 32.3 (lifecycle events), 32.8 (audit events list). Phase 6, Phase 6.5 patterns.

**Dependencies**: Epic 231 (ProposalService with lifecycle transitions).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **235A** | 235.1--235.6 | Audit events for all 7 proposal lifecycle transitions + notification templates (proposal.sent/accepted/declined/expired) + activity feed entries for customer and project + tests (~5 tests). ~3 modified files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 235.1 | Add audit events to ProposalService lifecycle methods | 235A | 231A | Modify `ProposalService.java`. Inject `AuditService`. Add `AuditEventBuilder` calls for: `proposal.created` (on create), `proposal.updated` (on update), `proposal.deleted` (on delete). Details map includes relevant entity IDs and changed fields. Pattern: `expense/ExpenseService.java` audit calls. |
| 235.2 | Add audit events to send, accept, decline, expire flows | 235A | 232B, 233A, 234A | Verify that send flow (232B), orchestration (233A), portal decline (234A), and expiry (234B) all emit audit events. This task is verification + gap-filling. Events: `proposal.sent`, `proposal.accepted`, `proposal.declined`, `proposal.expired`, `proposal.orchestration_completed`. If any are missing from earlier slices, add them here. |
| 235.3 | Create notification templates for proposal events | 235A | | Modify notification template configuration (or create new templates). Templates: (1) "proposal_sent" -- "Proposal {number} sent to {customerName}"; (2) "proposal_accepted" -- "Proposal {number} accepted! Project {projectName} created."; (3) "proposal_declined" -- "Proposal {number} declined by {customerName}. Reason: {reason}"; (4) "proposal_expired" -- "Proposal {number} for {customerName} has expired." Pattern: existing notification templates from Phase 6.5. |
| 235.4 | Wire activity feed for proposal events | 235A | | Modify `activity/ActivityService.java` (or create proposal-specific activity formatter). Map proposal events to activity entries: proposal events appear in customer activity feed, acceptance event also appears in the created project's activity feed. Pattern: `activity/ActivityFormatter.java` entity type handling. |
| 235.5 | Create email templates for portal-facing notifications | 235A | | Two email templates: (1) "new-proposal" -- sent to portal contact on proposal send; (2) "proposal-expired" -- sent to portal contact when proposal expires. HTML templates in `resources/templates/email/` (or wherever Phase 24 email templates live). Pattern: existing email templates from Phase 24. |
| 235.6 | Write audit and notification integration tests | 235A | 235.1 | New file: `proposal/ProposalAuditTest.java`. Tests (~5): (1) `createProposal_emitsAuditEvent`; (2) `sendProposal_emitsAuditEvent`; (3) `acceptProposal_emitsAuditAndOrchestrationEvents`; (4) `declineProposal_emitsAuditEvent`; (5) `proposalEvents_appearInCustomerActivity`. |

### Key Files

**Slice 235A -- Modify:**
- `backend/src/main/java/.../proposal/ProposalService.java` -- audit calls on CRUD
- `backend/src/main/java/.../activity/ActivityService.java` (or equivalent) -- proposal activity entries
- `backend/src/main/resources/templates/email/` -- new email templates

**Slice 235A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalAuditTest.java`

**Slice 235A -- Read for context:**
- `backend/src/main/java/.../audit/AuditService.java` -- audit event builder pattern
- `backend/src/main/java/.../activity/ActivityService.java` -- activity feed integration
- `backend/src/main/java/.../notification/template/` -- notification template patterns

### Architecture Decisions

- **Audit events are transactional**: Persisted within the same transaction as the entity state change (for compliance integrity). Notifications are async after commit.
- **Activity feed dual-scope**: Proposal events appear on both customer and project (if created) activity feeds.

---

## Epic 236: Proposals Frontend -- List & Pipeline Stats

**Goal**: Create the proposals list page with pipeline statistics header, filterable table, and sidebar navigation integration.

**References**: Architecture doc Sections 32.5.1 (proposals page), 32.8.2.

**Dependencies**: Epic 231 (CRUD API must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **236A** | 236.1--236.6 | Proposals page shell: `proposal-actions.ts` server actions, `ProposalListTable` component, `ProposalStatusBadge` component, proposals route `page.tsx`, sidebar navigation "Proposals" link. ~5 new/modified files. Frontend only. | |
| **236B** | 236.7--236.11 | `ProposalPipelineStats` component (stat cards), table filters (status/customer/feeModel/dateRange), sort controls, empty state, frontend tests (~5 tests). ~3 new/modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 236.1 | Create `proposal-actions.ts` server actions | 236A | | New file: `frontend-v2/src/app/(app)/org/[slug]/proposals/proposal-actions.ts`. Server actions: `listProposals(filters)`, `getProposal(id)`, `createProposal(formData)`, `updateProposal(id, formData)`, `deleteProposal(id)`, `sendProposal(id, portalContactId)`, `withdrawProposal(id)`, `getProposalStats()`, `listCustomerProposals(customerId)`. Each calls backend API with org auth headers. Pattern: `frontend-v2/src/app/(app)/org/[slug]/invoices/invoice-actions.ts`. |
| 236.2 | Create `ProposalStatusBadge` component | 236A | | New file: `frontend-v2/src/components/proposals/proposal-status-badge.tsx`. Props: `status: string`. Color mapping: DRAFT=gray, SENT=blue, ACCEPTED=green, DECLINED=red, EXPIRED=amber. Use Shadcn `Badge` with `cn()`. Pattern: `frontend-v2/src/components/invoices/invoice-status-badge.tsx`. |
| 236.3 | Create `ProposalListTable` component | 236A | 236.2 | New file: `frontend-v2/src/components/proposals/proposal-list-table.tsx`. Client component. Shadcn `Table` with columns: Number, Title, Customer, Fee Model, Amount, Status (badge), Sent Date, Actions (View/Edit/Delete dropdown). Pagination. Click row navigates to detail. Pattern: `frontend-v2/src/components/invoices/invoice-list-table.tsx`. |
| 236.4 | Create proposals route `page.tsx` | 236A | 236.1, 236.3 | New file: `frontend-v2/src/app/(app)/org/[slug]/proposals/page.tsx`. Server component. Fetches initial proposal list and stats via server actions. Renders `ProposalPipelineStats` (placeholder in 236A, implemented in 236B) + `ProposalListTable`. Empty state: icon + "Create your first proposal" CTA. |
| 236.5 | Add "Proposals" to sidebar navigation | 236A | | Modify `frontend-v2/src/components/shell/` sidebar component. Add "Proposals" nav item with FileText icon (or Send icon). Position between "Invoices" and "Reports". Pattern: existing nav items. |
| 236.6 | Create proposals layout | 236A | | New file: `frontend-v2/src/app/(app)/org/[slug]/proposals/layout.tsx`. Standard layout wrapper for the proposals section. Pattern: `frontend-v2/src/app/(app)/org/[slug]/invoices/layout.tsx` if it exists. |
| 236.7 | Create `ProposalPipelineStats` component | 236B | 236.1 | New file: `frontend-v2/src/components/proposals/proposal-pipeline-stats.tsx`. Props: `stats: ProposalStatsResponse`. Renders 5 stat cards in a horizontal row: Total Open (SENT count), Accepted (this month), Declined (this month), Conversion Rate (percentage), Avg Days to Accept. Use Shadcn `Card` components. Pattern: `frontend-v2/src/components/dashboard/` stat card patterns. |
| 236.8 | Add filters to ProposalListTable | 236B | 236.3 | Modify `proposal-list-table.tsx`. Add filter bar above table: status dropdown (All/DRAFT/SENT/ACCEPTED/DECLINED/EXPIRED), customer search, fee model dropdown, date range picker. Filters update URL search params and refetch. Pattern: existing table filter patterns. |
| 236.9 | Add sort controls and empty state | 236B | 236.3 | Modify `proposal-list-table.tsx`. Sortable column headers: Sent Date (default desc), Amount, Status. Empty state for filtered results: "No proposals match your filters." |
| 236.10 | Write frontend tests -- list table | 236B | 236.3 | New file: `frontend-v2/src/components/proposals/__tests__/proposal-list-table.test.tsx`. Tests (~3): (1) `renders_proposal_rows_with_correct_columns`; (2) `shows_empty_state_when_no_proposals`; (3) `status_badge_renders_correct_color`. |
| 236.11 | Write frontend tests -- pipeline stats | 236B | 236.7 | New file: `frontend-v2/src/components/proposals/__tests__/proposal-pipeline-stats.test.tsx`. Tests (~2): (4) `renders_all_stat_cards`; (5) `shows_zero_conversion_rate_when_no_data`. |

### Key Files

**Slice 236A -- Create:**
- `frontend-v2/src/app/(app)/org/[slug]/proposals/proposal-actions.ts`
- `frontend-v2/src/app/(app)/org/[slug]/proposals/page.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/proposals/layout.tsx`
- `frontend-v2/src/components/proposals/proposal-status-badge.tsx`
- `frontend-v2/src/components/proposals/proposal-list-table.tsx`

**Slice 236A -- Modify:**
- `frontend-v2/src/components/shell/` -- sidebar nav (add Proposals link)

**Slice 236B -- Create:**
- `frontend-v2/src/components/proposals/proposal-pipeline-stats.tsx`
- `frontend-v2/src/components/proposals/__tests__/proposal-list-table.test.tsx`
- `frontend-v2/src/components/proposals/__tests__/proposal-pipeline-stats.test.tsx`

**Slice 236A/B -- Read for context:**
- `frontend-v2/src/components/invoices/` -- list table, status badge patterns
- `frontend-v2/src/app/(app)/org/[slug]/invoices/` -- page structure, actions pattern

### Architecture Decisions

- **Server actions for API calls**: Same pattern as invoices and expenses. Server actions handle auth headers and backend URL resolution.
- **Filter via URL search params**: Shareable URLs for filtered views. Consistent with existing table patterns.

---

## Epic 237: Proposals Frontend -- Create/Edit & Detail Pages

**Goal**: Build the create/edit proposal form (multi-section with Tiptap editor, fee configuration, milestone editor, team picker) and the proposal detail page with context-dependent actions.

**References**: Architecture doc Sections 32.5.2-32.5.4, 32.8.2.

**Dependencies**: Epic 232 (send flow API), Epic 236 (proposals page shell).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **237A** | 237.1--237.8 | Create/edit proposal pages: `ProposalForm` (multi-section), `FeeConfigSection` (dynamic per model), `MilestoneEditor` (percentage validation), `TeamMemberPicker` (multi-select with roles), template picker, proposals/new route, proposals/[id]/edit route + tests (~7 tests). ~7 new files. Frontend only. | |
| **237B** | 237.9--237.15 | Proposal detail page: read-only view, fee summary card, rendered body (Tiptap viewer), team list, `SendProposalDialog` (confirmation with portal contact selection), `ProposalPreview` component, context-dependent actions (DRAFT: Edit/Send/Delete; SENT: Withdraw/Copy; ACCEPTED: View Project/View Invoice; DECLINED/EXPIRED: Copy) + tests (~5 tests). ~5 new files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 237.1 | Create `FeeConfigSection` component | 237A | | New file: `frontend-v2/src/components/proposals/fee-config-section.tsx`. Client component. Props: `feeModel: string, onChange: (feeData) => void`. Dynamic rendering: FIXED shows amount + currency inputs + "Add milestones" toggle. HOURLY shows rate note textarea + info text. RETAINER shows monthly amount + currency + included hours. Fee model selector as card group (3 cards: Fixed Fee / Hourly / Retainer). Pattern: Shadcn `RadioGroup` styled as cards. |
| 237.2 | Create `MilestoneEditor` component | 237A | | New file: `frontend-v2/src/components/proposals/milestone-editor.tsx`. Client component. Props: `milestones: MilestoneData[], onChange`. Each row: description input, percentage input (number), due days input (number). "Add milestone" button. Delete button per row. Live validation: show running total of percentages with green/red indicator (green at exactly 100, red otherwise). Pattern: dynamic form row pattern from invoice line items editor. |
| 237.3 | Create `TeamMemberPicker` component | 237A | | New file: `frontend-v2/src/components/proposals/team-member-picker.tsx`. Client component. Props: `members: TeamMemberData[], onChange, orgMembers: Member[]`. Multi-select from org members (Shadcn `Combobox` or searchable dropdown). Each selected member gets a role text input. "Add member" button. Remove button per member. Pattern: project member picker from Phase 4. |
| 237.4 | Create `ProposalForm` component | 237A | 237.1, 237.2, 237.3 | New file: `frontend-v2/src/components/proposals/proposal-form.tsx`. Client component. Four sections: (1) Basics: title, customer dropdown, portal contact dropdown (scoped to customer), expiry date picker. (2) Fee Configuration: `FeeConfigSection`. (3) Proposal Body: Tiptap editor (reuse from Phase 31) with "Start from template" button + variable insertion menu. (4) Team & Project: project template dropdown, `TeamMemberPicker`. "Save Draft" and "Preview" buttons. Pattern: multi-section form similar to invoice creation. |
| 237.5 | Create proposals/new route | 237A | 237.4 | New file: `frontend-v2/src/app/(app)/org/[slug]/proposals/new/page.tsx`. Server component. Fetches customer list, org members, project templates, document templates. Renders `ProposalForm` with empty initial data. On submit: calls `createProposal()` server action, redirects to detail page. |
| 237.6 | Create proposals/[id]/edit route | 237A | 237.4 | New file: `frontend-v2/src/app/(app)/org/[slug]/proposals/[id]/edit/page.tsx`. Server component. Fetches existing proposal (redirect to detail if not DRAFT). Renders `ProposalForm` pre-populated with existing data. On submit: calls `updateProposal()` + optionally `replaceMilestones()` + `replaceTeamMembers()`. |
| 237.7 | Write frontend tests -- ProposalForm | 237A | 237.4 | New file: `frontend-v2/src/components/proposals/__tests__/proposal-form.test.tsx`. Tests (~4): (1) `renders_all_form_sections`; (2) `fee_model_switch_shows_correct_fields`; (3) `milestone_percentage_validation_shows_error`; (4) `submits_with_valid_data`. |
| 237.8 | Write frontend tests -- MilestoneEditor | 237A | 237.2 | New file: `frontend-v2/src/components/proposals/__tests__/milestone-editor.test.tsx`. Tests (~3): (5) `add_milestone_increments_rows`; (6) `percentage_total_indicator_turns_green_at_100`; (7) `remove_milestone_updates_total`. |
| 237.9 | Create proposals/[id] detail page | 237B | 236A | New file: `frontend-v2/src/app/(app)/org/[slug]/proposals/[id]/page.tsx`. Server component. Fetches proposal detail. Renders: header (number, title, status badge, customer link, dates), fee summary card, rendered proposal body (Tiptap viewer in read-only mode), team members list, project template reference. Context-dependent action buttons. |
| 237.10 | Create `SendProposalDialog` component | 237B | | New file: `frontend-v2/src/components/proposals/send-proposal-dialog.tsx`. Client component. Triggered by "Send" button on DRAFT proposal detail. Shows confirmation: "Send proposal to [contact name] at [email]?" If customer has multiple portal contacts: show contact selector dropdown. Confirm button calls `sendProposal()` server action. Success: toast + refresh page (now shows SENT status). Pattern: `components/invoices/send-invoice-dialog.tsx` if exists, or `AlertDialog` pattern. |
| 237.11 | Create `ProposalPreview` component | 237B | | New file: `frontend-v2/src/components/proposals/proposal-preview.tsx`. Client component. Props: `contentJson, variableContext`. Renders Tiptap JSON in read-only mode with variables resolved (client-side preview). Reuses the Tiptap viewer from Phase 31 document preview. Show in a modal or side panel. |
| 237.12 | Implement context-dependent actions on detail page | 237B | 237.9 | In proposals/[id]/page.tsx. Action bar logic: DRAFT = "Edit" (link to edit page), "Send" (opens SendProposalDialog), "Delete" (confirmation alert dialog). SENT = "Withdraw" (confirmation + calls withdrawProposal), "Copy" (calls createProposal with copied data). ACCEPTED = "View Project" (link to created project), "View Invoice(s)" (if FIXED, link to invoices), "View Retainer" (if RETAINER, link to retainer). DECLINED/EXPIRED = "Copy" (create new from this), show decline reason if present. |
| 237.13 | Add "Copy" proposal action | 237B | 237.12 | In detail page. "Copy" creates a new DRAFT proposal pre-populated with the original's data (title prefixed with "Copy of", same customer, fees, content, team). Calls `createProposal()` with copied fields, then navigates to the new proposal's edit page. |
| 237.14 | Write frontend tests -- detail page | 237B | 237.9 | New file: `frontend-v2/src/components/proposals/__tests__/proposal-detail.test.tsx`. Tests (~3): (8) `draft_shows_edit_send_delete_actions`; (9) `sent_shows_withdraw_copy_actions`; (10) `accepted_shows_view_project_link`. |
| 237.15 | Write frontend tests -- SendProposalDialog | 237B | 237.10 | File: `frontend-v2/src/components/proposals/__tests__/send-proposal-dialog.test.tsx`. Tests (~2): (11) `shows_contact_name_in_confirmation`; (12) `calls_sendProposal_on_confirm`. |

### Key Files

**Slice 237A -- Create:**
- `frontend-v2/src/components/proposals/fee-config-section.tsx`
- `frontend-v2/src/components/proposals/milestone-editor.tsx`
- `frontend-v2/src/components/proposals/team-member-picker.tsx`
- `frontend-v2/src/components/proposals/proposal-form.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/proposals/new/page.tsx`
- `frontend-v2/src/app/(app)/org/[slug]/proposals/[id]/edit/page.tsx`
- `frontend-v2/src/components/proposals/__tests__/proposal-form.test.tsx`
- `frontend-v2/src/components/proposals/__tests__/milestone-editor.test.tsx`

**Slice 237B -- Create:**
- `frontend-v2/src/app/(app)/org/[slug]/proposals/[id]/page.tsx`
- `frontend-v2/src/components/proposals/send-proposal-dialog.tsx`
- `frontend-v2/src/components/proposals/proposal-preview.tsx`
- `frontend-v2/src/components/proposals/__tests__/proposal-detail.test.tsx`
- `frontend-v2/src/components/proposals/__tests__/send-proposal-dialog.test.tsx`

**Slice 237A/B -- Read for context:**
- `frontend-v2/src/components/templates/` -- Tiptap editor integration from Phase 31
- `frontend-v2/src/components/invoices/` -- form and detail page patterns
- `frontend-v2/src/components/retainers/` -- create/detail page patterns

### Architecture Decisions

- **Tiptap editor reused from Phase 31**: Same component, same variable insertion mechanism. No new editor infrastructure needed.
- **Fee model as card selector**: Visual card selection (not dropdown) for the three fee models. Makes the choice prominent and clear.
- **Client-side preview with variable resolution**: ProposalPreview resolves variables in-browser using current form data. Same approach as template preview.

---

## Epic 238: Proposals Frontend -- Customer Tab & Project Link

**Goal**: Add a Proposals tab to the customer detail page and a "Created from Proposal" reference link to the project detail page.

**References**: Architecture doc Section 32.8.2 (modified pages).

**Dependencies**: Epic 236 (ProposalListTable component, proposal-actions.ts).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **238A** | 238.1--238.4 | Customer detail "Proposals" tab (pre-filtered list), project detail "Created from Proposal PROP-XXXX" link + frontend tests (~3 tests). ~3 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 238.1 | Add "Proposals" tab to customer detail page | 238A | 236A | Modify `frontend-v2/src/app/(app)/org/[slug]/customers/[id]/` page or layout. Add "Proposals" tab link in the customer detail tabs navigation. New tab content: fetches proposals via `listCustomerProposals(customerId)` server action. Renders `ProposalListTable` pre-filtered to customer. "New Proposal" button pre-selects customer. Pattern: existing customer detail tabs (Projects, Retainers, etc). |
| 238.2 | Add "Created from Proposal" link to project detail | 238A | | Modify `frontend-v2/src/app/(app)/org/[slug]/projects/[id]/` page or header component. If the project was created from a proposal (check via project metadata or separate API call to find proposal by `createdProjectId`), show a small text link in the project header: "Created from Proposal PROP-XXXX" linking to the proposal detail page. If no proposal origin: show nothing. |
| 238.3 | Write frontend test -- customer proposals tab | 238A | 238.1 | New file: `frontend-v2/src/components/proposals/__tests__/customer-proposals-tab.test.tsx`. Tests (~2): (1) `renders_proposals_for_customer`; (2) `new_proposal_button_links_to_create_with_customer`. |
| 238.4 | Write frontend test -- project proposal link | 238A | 238.2 | Tests (~1): (3) `shows_created_from_proposal_link_when_applicable`. Can be in same test file or a project-specific test. |

### Key Files

**Slice 238A -- Modify:**
- `frontend-v2/src/app/(app)/org/[slug]/customers/[id]/` -- add Proposals tab
- `frontend-v2/src/app/(app)/org/[slug]/projects/[id]/` -- add proposal origin link

**Slice 238A -- Create:**
- `frontend-v2/src/components/proposals/__tests__/customer-proposals-tab.test.tsx`

**Slice 238A -- Read for context:**
- `frontend-v2/src/app/(app)/org/[slug]/customers/[id]/` -- existing tab structure
- `frontend-v2/src/app/(app)/org/[slug]/projects/[id]/` -- project detail header

### Architecture Decisions

- **Proposal-to-project reference is informational**: The link is a convenience for auditing/navigation. No runtime dependency between project and proposal after acceptance.
- **Customer tab reuses ProposalListTable**: Same component as the main proposals page, just pre-filtered. No duplication.

---

## Epic 239: Portal Frontend -- Proposal Pages

**Goal**: Build the portal-facing proposal list and detail pages where clients view proposals, review fee structures, and accept or decline.

**References**: Architecture doc Sections 32.6.3, 32.6.4 (portal page structure).

**Dependencies**: Epic 234 (portal backend API).

**Scope**: Portal (frontend-v2 portal routes)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **239A** | 239.1--239.4 | Portal proposals list page + nav link + portal `proposal-actions.ts` + portal `ProposalStatusBadge`. ~4 new files. Portal only. | |
| **239B** | 239.5--239.11 | Portal proposal detail page: rendered HTML body in branded wrapper, fee summary card, milestones table, accept/decline buttons with confirmation dialogs, expired banner, already-actioned banner + tests (~5 tests). ~3 new files. Portal only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 239.1 | Create portal `proposal-actions.ts` | 239A | | New file: `frontend-v2/src/app/portal/proposals/proposal-actions.ts`. Server actions: `listPortalProposals()`, `getPortalProposal(id)`, `acceptProposal(id)`, `declineProposal(id, reason)`. Calls portal API endpoints (`/portal/api/proposals/...`). Pattern: `frontend-v2/src/app/portal/projects/` actions pattern. |
| 239.2 | Create portal ProposalStatusBadge | 239A | | New file: `frontend-v2/src/components/portal/proposal-status-badge.tsx`. Similar to firm-side badge but with portal-friendly labels: SENT = "Pending", ACCEPTED = "Accepted", DECLINED = "Declined", EXPIRED = "Expired". Pattern: `frontend-v2/src/components/portal/` existing components. |
| 239.3 | Create portal proposals list page | 239A | 239.1, 239.2 | New file: `frontend-v2/src/app/portal/proposals/page.tsx`. Server component. Fetches proposal list. Renders table: Title, Number, Status (badge), Fee, Sent Date. SENT rows show "View & Respond" CTA. Other statuses show "View" link. Empty state: "No proposals yet." |
| 239.4 | Add "Proposals" to portal layout navigation | 239A | | Modify `frontend-v2/src/app/portal/layout.tsx`. Add "Proposals" nav item. Pattern: existing portal nav items (Projects, Documents). |
| 239.5 | Create portal proposal detail page -- header and body | 239B | 239.1 | New file: `frontend-v2/src/app/portal/proposals/[id]/page.tsx`. Server component. Fetches proposal detail. Renders: header with org branding (logo, brand color), proposal title, number, sent date. Body section: render `contentHtml` as sanitized HTML using `dangerouslySetInnerHTML` (content is pre-sanitized server-side) or a safe HTML renderer. Wrapped in a styled container with org brand color accents. |
| 239.6 | Add fee summary card to portal detail | 239B | 239.5 | In portal detail page. Fee summary card showing: fee model label, total amount (formatted with currency), milestones table (if FIXED with milestones: description, percentage, calculated amount, due date relative to send date). For RETAINER: monthly amount + included hours. For HOURLY: "Billing based on time tracked at standard rates." |
| 239.7 | Implement accept button and confirmation | 239B | 239.5 | In portal detail page. For SENT proposals: "Accept Proposal" button (prominent primary CTA with org brand color). On click: confirmation dialog: "By accepting this proposal, you agree to the scope of work and fee structure described above. A project will be set up for you automatically." Confirm calls `acceptProposal()`. On success: refresh page showing ACCEPTED status + success message. |
| 239.8 | Implement decline button and reason input | 239B | 239.5 | In portal detail page. For SENT proposals: "Decline" button (secondary/outline). On click: dialog with optional textarea for reason. Confirm calls `declineProposal(id, reason)`. On success: refresh page showing DECLINED status. |
| 239.9 | Implement expired and already-actioned banners | 239B | 239.5 | In portal detail page. If EXPIRED: show amber banner "This proposal has expired. Contact [orgName] if you'd like to discuss further." No action buttons. If ACCEPTED: show green banner "You accepted this proposal on [date]." No action buttons. If DECLINED: show gray banner "You declined this proposal on [date]." No action buttons. |
| 239.10 | Write portal frontend tests -- list | 239B | 239.3 | New file: `frontend-v2/src/components/portal/__tests__/portal-proposals.test.tsx`. Tests (~2): (1) `renders_proposal_list_with_status_badges`; (2) `sent_proposals_show_respond_cta`. |
| 239.11 | Write portal frontend tests -- detail | 239B | 239.5 | Continuing test file. Tests (~3): (3) `renders_fee_summary_card`; (4) `shows_accept_decline_for_sent_only`; (5) `expired_shows_banner_no_buttons`. |

### Key Files

**Slice 239A -- Create:**
- `frontend-v2/src/app/portal/proposals/proposal-actions.ts`
- `frontend-v2/src/app/portal/proposals/page.tsx`
- `frontend-v2/src/components/portal/proposal-status-badge.tsx`

**Slice 239A -- Modify:**
- `frontend-v2/src/app/portal/layout.tsx` -- add Proposals nav link

**Slice 239B -- Create:**
- `frontend-v2/src/app/portal/proposals/[id]/page.tsx`
- `frontend-v2/src/components/portal/__tests__/portal-proposals.test.tsx`

**Slice 239A/B -- Read for context:**
- `frontend-v2/src/app/portal/projects/page.tsx` -- portal page pattern
- `frontend-v2/src/app/portal/layout.tsx` -- portal navigation structure
- `frontend-v2/src/components/portal/` -- portal component patterns

### Architecture Decisions

- **Pre-rendered HTML from portal read-model**: No Tiptap dependency in the portal. `contentHtml` is pre-rendered server-side at send time. Displayed as sanitized HTML.
- **Branding from portal row**: `orgName`, `orgLogoUrl`, `orgBrandColor` captured at send time. Portal does not call back to the firm-side API for branding.
- **Accept is idempotent**: If the user double-clicks or refreshes after accepting, the portal shows the ACCEPTED state without error.

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceRequest.java` -- Core entity lifecycle pattern to follow for the Proposal entity (status guards, lifecycle methods, `@PrePersist`/`@PreUpdate`)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberService.java` -- Counter-based number allocation pattern to replicate for ProposalNumberService
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalReadModelSyncService.java` -- Portal schema JDBC sync pattern for ProposalPortalSyncService
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase32-proposal-engagement-pipeline.md` -- Full architecture doc with entity definitions, sequence diagrams, migration DDL, and implementation guidance
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend-v2/src/app/portal/layout.tsx` -- Portal navigation structure and layout for portal proposal pages
