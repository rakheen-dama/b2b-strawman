# Phase 28 — Document Acceptance (Lightweight E-Signing)

Phase 28 adds a **document acceptance workflow** to the DocTeams platform. Firms can send any generated document (engagement letters, proposals, service agreements) to portal contacts for formal acknowledgment via an email link. The client views the PDF in-browser, types their full name, and clicks "I Accept." The system records acceptance metadata (name, timestamp, IP, user agent), auto-generates a Certificate of Acceptance PDF with SHA-256 hash integrity, and provides real-time status tracking with full audit trail.

**Architecture doc**: `architecture/phase28-document-acceptance.md`

**ADRs**:
- [ADR-107](../adr/ADR-107-acceptance-token-strategy.md) -- Acceptance Token Strategy (separate acceptance-specific token, not magic-link reuse)
- [ADR-108](../adr/ADR-108-certificate-storage-and-integrity.md) -- Certificate Storage and Integrity (separate S3 object on AcceptanceRequest)
- [ADR-109](../adr/ADR-109-portal-read-model-sync-granularity.md) -- Portal Read-Model Sync Granularity (hybrid: read-model for lists, live query for acceptance page)

**Migrations**: V45 tenant (`acceptance_requests` table + `org_settings` extension), V11 global (`portal_acceptance_requests` read-model table).

**Dependencies on prior phases**: Phase 12 (Document Templates/PDF), Phase 7 (Customer Portal Backend), Phase 6 (Audit), Phase 6.5 (Notifications), Phase 8 (OrgSettings), Phase 24 (Email Delivery), Phase 22 (Portal Frontend).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 193 | AcceptanceRequest Entity Foundation + Migration | Backend | -- | M | 193A, 193B | **Done** (PRs #397, #398) |
| 194 | AcceptanceService Core Workflow + Email | Backend | 193 | L | 194A, 194B | **Done** (PRs #399, #400) |
| 195 | Certificate Generation + Portal Read-Model Sync | Backend | 194 | M | 195A, 195B | **Done** (PRs #401, #402) |
| 196 | Firm-Facing REST API + Audit + Notifications | Backend | 194, 195 | M | 196A, 196B | |
| 197 | Portal Acceptance Controller + Expiry Processor | Backend | 194, 195 | M | 197A | |
| 198 | Frontend -- Send for Acceptance + Status Tracking | Frontend | 196 | M | 198A, 198B | |
| 199 | Portal -- Acceptance Page + Pending List | Portal | 197 | M | 199A, 199B | |
| 200 | Frontend -- OrgSettings Acceptance Config | Frontend | 196 | S | 200A | |

---

## Dependency Graph

```
[E193A AcceptanceRequest Entity + V45 Migration + Enum]
        |
[E193B AcceptanceRequestRepository + Entity Tests]
        |
        +-----------------------------------+
        |                                   |
[E194A AcceptanceService Core               |
 (createAndSend, markViewed, accept,        |
  revoke, remind) + Email Templates]        |
        |                                   |
[E194B Domain Events + Acceptance           |
 Submission DTO + Service Tests]            |
        |                                   |
        +-------------------+---------------+
        |                   |
[E195A Certificate          [E195B Portal Read-Model
 Generation Service          Entity + V11 Migration
 + Certificate Template      + PortalEventHandler
 + S3 Upload]                Extension]
        |                   |
        +-------------------+
        |                   |
[E196A AcceptanceController [E197A PortalAcceptanceController
 + Response DTOs +           + Token Auth + Expiry
 Certificate Download]       Processor (@Scheduled)]
        |                   |
[E196B Audit Events +       |
 Notification Integration   |
 + Controller Tests]        |
        |                   |
        +-------------------+-------------------+
        |                   |                   |
[E198A Send for Acceptance  [E199A Portal       [E200A OrgSettings
 Dialog + API Client +      Acceptance Page      Acceptance Expiry
 Status Badge]              (PDF viewer + form)] Config Section]
        |                   |
[E198B Acceptance Detail    [E199B Portal Pending
 Panel + Generated Docs     Acceptances List]
 List Integration]
```

**Parallel opportunities**:
- Epics 195A and 195B are fully independent after 194B -- can start in parallel.
- Epics 196 and 197 are fully independent after 195A/195B -- can start in parallel.
- After 196B completes: Epics 198 and 200 can start in parallel.
- After 197A completes: Epic 199 can start.
- 198A and 199A are fully independent (main frontend vs. portal frontend).
- 198B depends on 198A. 199B depends on 199A.

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 193 | 193A | V45 tenant migration (`acceptance_requests` table, `org_settings.acceptance_expiry_days` column, all indexes + partial unique constraint) + V11 global migration (`portal_acceptance_requests` table + indexes) + `AcceptanceStatus` enum + `AcceptanceRequest` entity. ~6 new files. Backend only. | **Done** (PR #397) |
| 0b | 193 | 193B | `AcceptanceRequestRepository` with custom finders + entity unit tests + repository integration tests. ~3 new files, ~0-1 modified. Backend only. | **Done** (PR #398) |

### Stage 1: Service Core + Email Templates

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 194 | 194A | `AcceptanceService` core methods (createAndSend, markViewed, accept, revoke, remind) + `AcceptanceSubmission` record + token generation utility + email template rendering calls + 3 email templates (acceptance-request.html, acceptance-reminder.html, acceptance-confirmation.html). ~5 new files, ~3 template files. Backend only. | **Done** (PR #399) |
| 1b | 194 | 194B | 5 domain event records (Sent, Viewed, Accepted, Revoked, Expired) in `acceptance/event/` + event publishing in AcceptanceService + `OrgSettings` extension (acceptanceExpiryDays field + getter/setter) + service integration tests. ~7 new files, ~2 modified files. Backend only. | **Done** (PR #400) |

### Stage 2: Certificate + Portal Read-Model (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 195 | 195A | `AcceptanceCertificateService` (SHA-256 hash computation, Thymeleaf certificate rendering, S3 upload) + certificate template (`certificates/certificate-of-acceptance.html`) + integration with AcceptanceService.accept() + certificate generation tests. ~2 new files, ~1 template file, ~1 modified file. Backend only. | **Done** (PR #401) |
| 2b (parallel) | 195 | 195B | `PortalAcceptanceView` read-model entity + `PortalReadModelRepository` extension (acceptance CRUD methods) + `PortalEventHandler` extension (handlers for acceptance domain events) + portal read-model sync integration tests. ~1 new file, ~2 modified files, ~1 test file. Backend only. | **Done** (PR #402) |

### Stage 3: Controllers (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 196 | 196A | `AcceptanceController` at `/api/acceptance-requests` (POST create, GET list by document/customer, GET detail, POST remind, POST revoke, GET certificate download) + response DTOs (`AcceptanceRequestResponse`, `CreateAcceptanceRequest`) + RBAC annotations + controller integration tests. ~4 new files. Backend only. | |
| 3b (parallel) | 197 | 197A | `PortalAcceptanceController` at `/api/portal/acceptance/{token}` (GET page data, GET pdf stream, POST accept) + IP/UA extraction + token-based auth filter exemption + `@Scheduled` expiry processor in `AcceptanceService.processExpired()` + controller integration tests. ~2 new files, ~2 modified files. Backend only. | |

### Stage 4: Audit + Notifications

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 196 | 196B | Audit event recording for all state transitions (acceptance.created, .sent, .viewed, .accepted, .reminded, .revoked, .expired, .certificate_generated) wired into AcceptanceService + `NotificationEventHandler` extension for `AcceptanceRequestAcceptedEvent` (in-app notification to sender) + OrgSettings controller extension (acceptance config endpoint) + integration tests. ~0-1 new files, ~4 modified files. Backend only. | |

### Stage 5: Frontend (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 198 | 198A | `acceptance-actions.ts` server actions + `SendForAcceptanceDialog.tsx` (recipient picker, expiry override, send) + `AcceptanceStatusBadge.tsx` + integration into `GeneratedDocumentsList.tsx` (action menu item + status badge) + frontend tests. ~4 new files, ~1 modified file. Frontend only. | |
| 5b (parallel) | 199 | 199A | Portal acceptance page at `portal/app/(public)/accept/[token]/page.tsx` (PDF viewer via iframe, acceptance form with typed name, post-acceptance confirmation, expired/revoked states) + portal acceptance API calls + portal frontend tests. ~3 new files. Portal only. | |
| 5c (parallel) | 200 | 200A | Add "Document Acceptance" section to OrgSettings page (acceptance expiry days input: number field, min 1, max 365, default 30) + settings action update + frontend test. ~0-1 new files, ~2 modified files. Frontend only. | |

### Stage 6: Detail Panel + Portal List

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 198 | 198B | `AcceptanceDetailPanel.tsx` (expandable panel: recipient info, status timeline, remind/revoke actions, certificate download) + integration into generated document detail views + `GenerateDocumentDialog.tsx` post-generation "Send for Acceptance" button + frontend tests. ~2 new files, ~2 modified files. Frontend only. | |
| 6b (parallel) | 199 | 199B | "Pending Acceptances" section on portal dashboard or documents page (list of documents awaiting acceptance with direct links to acceptance page) + portal API call to read-model endpoint + portal frontend tests. ~1 new file, ~1 modified file. Portal only. | |

### Timeline

```
Stage 0: [193A] --> [193B]                                          (sequential)
Stage 1: [194A] --> [194B]                                          (sequential, after 193B)
Stage 2: [195A] // [195B]                                           (parallel, after 194B)
Stage 3: [196A] // [197A]                                           (parallel, after 195A+195B)
Stage 4: [196B]                                                     (after 196A)
Stage 5: [198A] // [199A] // [200A]                                 (parallel, after 196B / 197A respectively)
Stage 6: [198B] // [199B]                                           (parallel, after 198A / 199A respectively)
```

**Critical path**: 193A -> 193B -> 194A -> 194B -> 195A -> 196A -> 196B -> 198A -> 198B

---

## Epic 193: AcceptanceRequest Entity Foundation + Migration

**Goal**: Create the `AcceptanceRequest` entity, `AcceptanceStatus` enum, repository, V45 tenant migration, V11 global migration (portal read-model table), and tests. This is the foundation for all subsequent acceptance work.

**References**: Architecture doc Sections 28.2 (domain model), 28.8 (migration SQL), 28.9.3 (entity code pattern). ADR-107, ADR-108.

**Dependencies**: None -- this is the foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **193A** | 193.1--193.5 | V45 tenant migration (`acceptance_requests` table with all columns, indexes, partial unique constraint + `org_settings.acceptance_expiry_days` column) + V11 global migration (`portal_acceptance_requests` table + indexes) + `AcceptanceStatus` enum + `AcceptanceRequest` entity with lifecycle methods (markSent, markViewed, markAccepted, markRevoked, markExpired). ~6 new files. Backend only. | **Done** (PR #397) |
| **193B** | 193.6--193.10 | `AcceptanceRequestRepository` with custom finders (findByRequestToken, findByGeneratedDocumentId, findByCustomerIdAndStatusIn, findByStatusAndExpiresAtBefore, findByGeneratedDocumentIdAndPortalContactIdAndStatusIn) + entity unit tests + repository integration tests. ~3 new files. Backend only. | **Done** (PR #398) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 193.1 | Create V45 tenant migration | 193A | | New file: `db/migration/tenant/V45__create_acceptance_requests.sql`. (1) CREATE `acceptance_requests` table per architecture doc Section 28.8.1 -- all 20 columns, UUID PK with `gen_random_uuid()`, `status` VARCHAR(20) DEFAULT 'PENDING', `reminder_count` INTEGER DEFAULT 0, `created_at`/`updated_at` with DEFAULT now(). (2) CREATE UNIQUE INDEX `idx_acceptance_requests_token` on `(request_token)`. (3) CREATE UNIQUE INDEX `idx_acceptance_requests_active_unique` on `(generated_document_id, portal_contact_id) WHERE status IN ('PENDING', 'SENT', 'VIEWED')`. (4) CREATE INDEX `idx_acceptance_requests_customer_status` on `(customer_id, status)`. (5) CREATE INDEX `idx_acceptance_requests_document` on `(generated_document_id)`. (6) CREATE INDEX `idx_acceptance_requests_expiry` on `(status, expires_at) WHERE status IN ('PENDING', 'SENT', 'VIEWED')`. (7) ALTER `org_settings` ADD COLUMN `acceptance_expiry_days INTEGER`. |
| 193.2 | Create V11 global migration | 193A | | New file: `db/migration/global/V11__portal_acceptance_requests.sql`. CREATE `portal_acceptance_requests` table per architecture doc Section 28.8.2 -- 11 columns, UUID PK (no gen_random_uuid -- ID copied from main entity). CREATE INDEX `idx_portal_acceptance_contact_status` on `(portal_contact_id, status)`. CREATE INDEX `idx_portal_acceptance_token` on `(request_token)`. |
| 193.3 | Create `AcceptanceStatus` enum | 193A | | New file: `acceptance/AcceptanceStatus.java`. Enum: `PENDING`, `SENT`, `VIEWED`, `ACCEPTED`, `EXPIRED`, `REVOKED`. No additional methods needed -- state transition logic lives in the entity. Pattern: `clause/ClauseSource.java` enum style. |
| 193.4 | Create `AcceptanceRequest` entity | 193A | 193.1, 193.3 | New file: `acceptance/AcceptanceRequest.java`. `@Entity`, `@Table(name = "acceptance_requests")`. All 20 fields per architecture doc Section 28.2.1. `@Enumerated(EnumType.STRING)` for status with `length = 20`. Protected no-arg constructor. Public constructor `(UUID generatedDocumentId, UUID portalContactId, UUID customerId, String requestToken, Instant expiresAt, UUID sentByMemberId)` -- sets status=PENDING, reminderCount=0, timestamps. Lifecycle methods: `markSent()`, `markViewed(Instant viewedAt)`, `markAccepted(String name, String ip, String ua)`, `markRevoked(UUID revokedBy)`, `markExpired()`, `recordReminder()`. Each method validates status preconditions and updates `updatedAt`. `isActive()` returns true if status in PENDING/SENT/VIEWED. `isExpired()` returns `Instant.now().isAfter(expiresAt)`. Pattern: `template/GeneratedDocument.java` entity style (no Lombok). |
| 193.5 | Create `OrgSettings` extension | 193A | 193.1 | Modify `settings/OrgSettings.java`: add `@Column(name = "acceptance_expiry_days") private Integer acceptanceExpiryDays;` with getter and setter. Add `getEffectiveAcceptanceExpiryDays()` returning `acceptanceExpiryDays != null ? acceptanceExpiryDays : 30`. Pattern: existing OrgSettings fields (logoS3Key, brandColor). |
| 193.6 | Create `AcceptanceRequestRepository` | 193B | 193.4 | New file: `acceptance/AcceptanceRequestRepository.java`. `JpaRepository<AcceptanceRequest, UUID>`. Custom queries: `findByRequestToken(String token)` -> `Optional<AcceptanceRequest>`, `findByGeneratedDocumentIdOrderByCreatedAtDesc(UUID documentId)` -> `List<AcceptanceRequest>`, `findByCustomerIdAndStatusInOrderByCreatedAtDesc(UUID customerId, List<AcceptanceStatus> statuses)` -> `List<AcceptanceRequest>`, `findByStatusInAndExpiresAtBefore(List<AcceptanceStatus> statuses, Instant cutoff)` -> `List<AcceptanceRequest>`, `findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(UUID documentId, UUID contactId, List<AcceptanceStatus> activeStatuses)` -> `Optional<AcceptanceRequest>`. Pattern: `clause/ClauseRepository.java`. |
| 193.7 | Write entity unit tests | 193B | 193.4 | New file: `acceptance/AcceptanceRequestTest.java`. Tests: (1) constructor_sets_required_fields_and_defaults, (2) markSent_sets_sentAt_and_status, (3) markViewed_sets_viewedAt_and_status, (4) markAccepted_records_metadata_and_status, (5) markRevoked_sets_revokedAt_and_revokedBy, (6) markExpired_sets_status, (7) recordReminder_increments_count_and_sets_lastRemindedAt, (8) isActive_returns_true_for_active_statuses, (9) isExpired_returns_true_when_past_expiresAt, (10) markViewed_throws_for_terminal_status. ~10 tests. |
| 193.8 | Write repository integration tests | 193B | 193.6 | New file: `acceptance/AcceptanceRequestRepositoryIntegrationTest.java`. Tests: (1) save_and_findById, (2) findByRequestToken_returns_request, (3) findByGeneratedDocumentIdOrderByCreatedAtDesc_returns_list, (4) findByCustomerIdAndStatusIn_filters_correctly, (5) findByStatusInAndExpiresAtBefore_returns_expired, (6) findByGeneratedDocumentIdAndPortalContactIdAndStatusIn_finds_active, (7) unique_token_constraint_enforced, (8) active_unique_constraint_allows_terminal_duplicates. ~8 tests. Pattern: `clause/ClauseRepositoryIntegrationTest.java`. |
| 193.9 | Verify V45 migration runs on Testcontainers | 193B | 193.1 | Covered by repository integration tests: Flyway runs V45 automatically. Verify: `acceptance_requests` table created, `org_settings.acceptance_expiry_days` column added, all indexes exist. |
| 193.10 | Verify V11 global migration runs | 193B | 193.2 | Covered by integration test bootstrap: Flyway runs V11 for the global/portal schema. Verify: `portal_acceptance_requests` table created with indexes. |

### Key Files

**Slice 193A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V45__create_acceptance_requests.sql`
- `backend/src/main/resources/db/migration/global/V11__portal_acceptance_requests.sql`
- `backend/src/main/java/.../acceptance/AcceptanceStatus.java`
- `backend/src/main/java/.../acceptance/AcceptanceRequest.java`

**Slice 193A -- Modify:**
- `backend/src/main/java/.../settings/OrgSettings.java`

**Slice 193B -- Create:**
- `backend/src/main/java/.../acceptance/AcceptanceRequestRepository.java`
- `backend/src/test/java/.../acceptance/AcceptanceRequestTest.java`
- `backend/src/test/java/.../acceptance/AcceptanceRequestRepositoryIntegrationTest.java`

**Slice 193B -- Read for context:**
- `backend/src/main/java/.../clause/ClauseRepository.java` -- repository pattern
- `backend/src/main/java/.../template/GeneratedDocument.java` -- entity pattern
- `backend/src/main/java/.../settings/OrgSettings.java` -- field extension pattern

### Architecture Decisions

- **V45 tenant + V11 global (not V45 for both)**: Tenant migrations run per-schema (each tenant gets the `acceptance_requests` table). Portal read-model table is in the global schema, following the existing portal migration pattern (V8-V10 in `db/migration/global/`).
- **Loose FK references (UUID columns, no DB constraints)**: Follows established pattern for cross-entity references. Service-layer validation catches invalid references. Avoids cascade complexity.
- **Partial unique index for active-request-per-pair**: Uses `WHERE status IN ('PENDING', 'SENT', 'VIEWED')` -- terminal states (ACCEPTED, EXPIRED, REVOKED) do not block new requests.
- **Entity lifecycle methods**: State transitions are encapsulated in `markSent()`, `markViewed()`, etc. rather than exposing raw setters. Each method validates preconditions (e.g., `markViewed` rejects terminal statuses).

---

## Epic 194: AcceptanceService Core Workflow + Email

**Goal**: Implement the core `AcceptanceService` with all lifecycle methods (createAndSend, markViewed, accept, revoke, remind), domain events, email templates, and the `OrgSettings` extension for expiry configuration.

**References**: Architecture doc Sections 28.3 (core flows), 28.6 (email templates), 28.2.4 (OrgSettings extension).

**Dependencies**: Epic 193 (entity + repository).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **194A** | 194.1--194.7 | `AcceptanceService` with core methods (createAndSend, markViewed, accept, revoke, remind, getByToken, getByDocument, getByCustomer) + `AcceptanceSubmission` record + token generation (SecureRandom + URL-safe Base64) + customer resolution logic + 3 Thymeleaf email templates (acceptance-request.html, acceptance-reminder.html, acceptance-confirmation.html) + email sending integration. ~5 new files, ~3 template files. Backend only. | **Done** (PR #399) |
| **194B** | 194.8--194.14 | 5 domain event records in `acceptance/event/` (AcceptanceRequestSentEvent, ViewedEvent, AcceptedEvent, RevokedEvent, ExpiredEvent) implementing `DomainEvent` + event publishing in AcceptanceService via `ApplicationEventPublisher` + service integration tests. ~7 new files, ~1 modified file. Backend only. | **Done** (PR #400) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 194.1 | Create `AcceptanceSubmission` record | 194A | | New file: `acceptance/AcceptanceSubmission.java`. Simple record: `public record AcceptanceSubmission(@NotBlank @Size(min = 2, max = 255) String name) {}`. Pattern: simple DTO records in `clause/dto/`. |
| 194.2 | Create `AcceptanceService` | 194A | 193.6 | New file: `acceptance/AcceptanceService.java`. `@Service`. Constructor injection: `AcceptanceRequestRepository`, `GeneratedDocumentRepository`, `PortalContactRepository`, `OrgSettingsRepository`, `CustomerRepository`, `ProjectRepository`, `InvoiceRepository` (for customer resolution), `StorageService` (needed later for cert gen -- inject now). Methods per architecture doc Section 28.3: `createAndSend()` -- validates document exists, resolves customerId (dispatches on primaryEntityType: CUSTOMER/PROJECT/INVOICE), validates portal contact belongs to customer, auto-revokes active request for same doc-contact pair, generates token (32 bytes SecureRandom, URL-safe Base64), calculates expiresAt from org settings or default 30 days, saves entity, sends email, transitions to SENT. `markViewed()`, `accept()`, `revoke()`, `remind()`, `getByToken()`, `getByDocument()`, `getByCustomer()`. Each method follows the pseudocode in architecture doc Sections 28.3.1--28.3.6. Pattern: `clause/ClauseService.java` for service structure, `portal/MagicLinkService.java` for token generation. |
| 194.3 | Implement token generation | 194A | 194.2 | In `AcceptanceService`: private method `generateToken()` using `SecureRandom` + `Base64.getUrlEncoder().withoutPadding().encodeToString()`. 32 bytes = 43 characters URL-safe Base64. Pattern: `portal/MagicLinkService.java` token generation (but without hashing -- acceptance tokens stored in plaintext per ADR-107). |
| 194.4 | Implement customer resolution | 194A | 194.2 | In `AcceptanceService`: private method `resolveCustomerId(GeneratedDocument doc)`. Switch on `doc.getPrimaryEntityType()`: CUSTOMER -> `doc.getPrimaryEntityId()`. PROJECT -> lookup `CustomerProject` or `Project.customerId`. INVOICE -> lookup `Invoice.customerId`. Pattern: `template/TemplateContextHelper.java` entity dispatch. |
| 194.5 | Create acceptance-request email template | 194A | | New file: `backend/src/main/resources/templates/email/acceptance-request.html`. Thymeleaf template extending `base.html`. Variables: orgName, orgLogo, brandColor, contactName, documentFileName, acceptanceUrl, expiresAt. Content per architecture doc Section 28.6.1: org branding header, greeting, body, document callout, CTA button "Review & Accept", expiry note, footer. Pattern: `templates/email/portal-magic-link.html` for layout and styling. |
| 194.6 | Create acceptance-reminder email template | 194A | | New file: `backend/src/main/resources/templates/email/acceptance-reminder.html`. Variables per Section 28.6.2: adds sentAt to show original send date. "This is a reminder" framing. Pattern: same as acceptance-request.html with reminder copy. |
| 194.7 | Create acceptance-confirmation email template | 194A | | New file: `backend/src/main/resources/templates/email/acceptance-confirmation.html`. Sent to portal contact after acceptance. Variables per Section 28.6.3: orgName, orgLogo, brandColor, contactName, documentFileName, acceptedAt. Confirmation message with acceptance timestamp. Pattern: same base layout. |
| 194.8 | Create `AcceptanceRequestSentEvent` | 194B | | New file: `acceptance/event/AcceptanceRequestSentEvent.java`. Record implementing `DomainEvent`: `UUID requestId, UUID generatedDocumentId, UUID portalContactId, UUID customerId, String documentTitle, String documentFileName, String requestToken, Instant expiresAt, String orgName, String orgLogo`. Pattern: `event/DocumentGeneratedEvent.java`. |
| 194.9 | Create `AcceptanceRequestViewedEvent` | 194B | | New file: `acceptance/event/AcceptanceRequestViewedEvent.java`. Record: `UUID requestId, String ipAddress`. |
| 194.10 | Create `AcceptanceRequestAcceptedEvent` | 194B | | New file: `acceptance/event/AcceptanceRequestAcceptedEvent.java`. Record: `UUID requestId, UUID sentByMemberId, String documentFileName, String contactName`. |
| 194.11 | Create `AcceptanceRequestRevokedEvent` | 194B | | New file: `acceptance/event/AcceptanceRequestRevokedEvent.java`. Record: `UUID requestId`. |
| 194.12 | Create `AcceptanceRequestExpiredEvent` | 194B | | New file: `acceptance/event/AcceptanceRequestExpiredEvent.java`. Record: `UUID requestId`. |
| 194.13 | Wire event publishing in AcceptanceService | 194B | 194.2, 194.8--194.12 | Modify `acceptance/AcceptanceService.java`: inject `ApplicationEventPublisher`. Publish events at each state transition: `AcceptanceRequestSentEvent` after SENT, `AcceptanceRequestViewedEvent` after VIEWED, `AcceptanceRequestAcceptedEvent` after ACCEPTED, `AcceptanceRequestRevokedEvent` after REVOKED, `AcceptanceRequestExpiredEvent` after EXPIRED. Pattern: existing event publishing in `template/GeneratedDocumentService.java`. |
| 194.14 | Write service integration tests | 194B | 194.2 | New file: `acceptance/AcceptanceServiceIntegrationTest.java`. MockMvc/service-level tests: (1) createAndSend_creates_and_transitions_to_sent, (2) createAndSend_auto_revokes_existing_active_request, (3) createAndSend_validates_document_exists, (4) createAndSend_validates_contact_belongs_to_customer, (5) createAndSend_uses_org_expiry_default, (6) createAndSend_uses_per_request_expiry_override, (7) markViewed_transitions_sent_to_viewed, (8) markViewed_idempotent_for_viewed, (9) markViewed_rejects_expired, (10) accept_records_metadata_and_transitions, (11) accept_rejects_expired, (12) accept_rejects_revoked, (13) revoke_transitions_active_request, (14) revoke_rejects_terminal_status, (15) remind_resends_email_and_increments_count, (16) remind_rejects_expired. ~16 tests. Pattern: `clause/ClauseServiceTest.java`. |

### Key Files

**Slice 194A -- Create:**
- `backend/src/main/java/.../acceptance/AcceptanceSubmission.java`
- `backend/src/main/java/.../acceptance/AcceptanceService.java`
- `backend/src/main/resources/templates/email/acceptance-request.html`
- `backend/src/main/resources/templates/email/acceptance-reminder.html`
- `backend/src/main/resources/templates/email/acceptance-confirmation.html`

**Slice 194A -- Read for context:**
- `backend/src/main/java/.../portal/MagicLinkService.java` -- token generation pattern
- `backend/src/main/java/.../notification/channel/EmailNotificationChannel.java` -- email sending pattern
- `backend/src/main/resources/templates/email/portal-magic-link.html` -- email template pattern
- `backend/src/main/resources/templates/email/base.html` -- email base layout
- `backend/src/main/java/.../template/GeneratedDocument.java` -- entity lookup pattern
- `backend/src/main/java/.../portal/PortalContact.java` -- contact validation
- `backend/src/main/java/.../template/TemplateContextHelper.java` -- entity type dispatch

**Slice 194B -- Create:**
- `backend/src/main/java/.../acceptance/event/AcceptanceRequestSentEvent.java`
- `backend/src/main/java/.../acceptance/event/AcceptanceRequestViewedEvent.java`
- `backend/src/main/java/.../acceptance/event/AcceptanceRequestAcceptedEvent.java`
- `backend/src/main/java/.../acceptance/event/AcceptanceRequestRevokedEvent.java`
- `backend/src/main/java/.../acceptance/event/AcceptanceRequestExpiredEvent.java`
- `backend/src/test/java/.../acceptance/AcceptanceServiceIntegrationTest.java`

**Slice 194B -- Modify:**
- `backend/src/main/java/.../acceptance/AcceptanceService.java` -- add event publishing

**Slice 194B -- Read for context:**
- `backend/src/main/java/.../event/DomainEvent.java` -- event interface
- `backend/src/main/java/.../event/DocumentGeneratedEvent.java` -- event record pattern

### Architecture Decisions

- **Email templates in 194A, events in 194B**: Splits the service build across two slices. 194A focuses on the core workflow logic and email templates. 194B adds the event infrastructure (which is needed for portal read-model sync in 195B) and tests.
- **Token generation is inline, not a separate utility class**: The token generation logic is 3 lines of code (SecureRandom + Base64). A separate utility class adds complexity without benefit. If other features need similar tokens, extraction can happen later.
- **Customer resolution in service, not entity**: Resolving customerId from GeneratedDocument requires repository lookups (Project, Invoice). This is service-layer logic, not entity logic.

---

## Epic 195: Certificate Generation + Portal Read-Model Sync

**Goal**: Implement certificate generation (SHA-256 hash, Thymeleaf rendering, S3 upload) and portal read-model sync for acceptance events.

**References**: Architecture doc Sections 28.3.4 (certificate generation), 28.7 (certificate template), 28.3.8 (portal read-model sync). ADR-108, ADR-109.

**Dependencies**: Epic 194 (AcceptanceService + domain events).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **195A** | 195.1--195.5 | `AcceptanceCertificateService` (SHA-256 computation, Thymeleaf context assembly, PDF rendering via PdfRenderingService, S3 upload) + certificate template (`certificates/certificate-of-acceptance.html`) + integration with `AcceptanceService.accept()` + certificate generation tests. ~2 new files, ~1 template, ~1 modified, ~1 test. Backend only. | **Done** (PR #401) |
| **195B** | 195.6--195.10 | `PortalAcceptanceView` read-model entity + `PortalReadModelRepository` extension (save/update acceptance) + `PortalEventHandler` extension (handlers for Sent, Accepted, Revoked, Expired events) + portal read-model sync integration tests. ~1 new file, ~2 modified, ~1 test. Backend only. | **Done** (PR #402) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 195.1 | Create certificate Thymeleaf template | 195A | | New file: `backend/src/main/resources/templates/certificates/certificate-of-acceptance.html`. System-managed template (not user-editable). Variables per architecture doc Section 28.7.2: orgName, orgLogo, brandColor, documentTitle, recipientName, recipientEmail, acceptorName, acceptedAt, ipAddress, userAgent, documentHash, requestId, generatedAt. Layout per Section 28.7.1. Styled with inline CSS for PDF rendering (OpenHTMLToPDF). Pattern: existing document templates in `templates/` for PDF-compatible HTML. |
| 195.2 | Create `AcceptanceCertificateService` | 195A | 195.1 | New file: `acceptance/AcceptanceCertificateService.java`. `@Service`. Constructor injection: `GeneratedDocumentRepository`, `PortalContactRepository`, `OrgSettingsRepository`, `StorageService`, `PdfRenderingService`, `TemplateEngine` (Spring standard -- classpath resolver). Method `generateCertificate(AcceptanceRequest request, String tenantSchema)`: (1) fetch original PDF bytes from S3 via StorageService, (2) compute SHA-256 hex via `MessageDigest`, (3) build Thymeleaf context with all certificate variables, (4) render template `certificates/certificate-of-acceptance`, (5) convert HTML to PDF via `PdfRenderingService.htmlToPdf()`, (6) upload to S3 with key `{tenantSchema}/certificates/{requestId}/certificate.pdf`, (7) set `certificateS3Key` and `certificateFileName` on the request. Pattern: `template/PdfRenderingService.java` for PDF generation, `s3/StorageService.java` for S3 operations. |
| 195.3 | Implement SHA-256 computation | 195A | 195.2 | In `AcceptanceCertificateService`: private method `sha256Hex(byte[] data)` using `MessageDigest.getInstance("SHA-256")` + hex encoding via `HexFormat.of().formatHex()` (Java 17+). Pattern: standard Java crypto. |
| 195.4 | Integrate certificate generation with AcceptanceService | 195A | 195.2 | Modify `acceptance/AcceptanceService.java`: inject `AcceptanceCertificateService`. In `accept()` method, after marking as ACCEPTED, call `certificateService.generateCertificate(request, tenantSchema)`. Tenant schema obtained from `RequestScopes.TENANT_ID`. |
| 195.5 | Write certificate generation tests | 195A | 195.2 | New file: `acceptance/AcceptanceCertificateServiceTest.java`. Tests: (1) generateCertificate_computes_hash_and_renders_pdf, (2) generateCertificate_uploads_to_correct_s3_key, (3) generateCertificate_sets_certificate_fields_on_request, (4) sha256Hex_produces_correct_hash. ~4 tests. Mock StorageService, PdfRenderingService, TemplateEngine. |
| 195.6 | Create `PortalAcceptanceView` | 195B | | New file: `customerbackend/model/PortalAcceptanceView.java`. `@Entity`, `@Table(name = "portal_acceptance_requests")`. Fields per architecture doc Section 28.2.3: id (UUID PK, not auto-generated), portalContactId, generatedDocumentId, documentTitle, documentFileName, status (String), requestToken, sentAt, expiresAt, orgName, orgLogo, createdAt. No-arg protected constructor + public constructor. Pattern: `customerbackend/model/PortalDocumentView.java`. |
| 195.7 | Extend `PortalReadModelRepository` | 195B | 195.6 | Modify `customerbackend/repository/PortalReadModelRepository.java`: add methods `saveAcceptanceRequest(PortalAcceptanceView view)`, `updateAcceptanceRequestStatus(UUID id, String status)`, `findAcceptanceRequestsByContactId(UUID contactId)`, `findPendingAcceptancesByContactId(UUID contactId)` (status in SENT, VIEWED). Pattern: existing save/update methods in PortalReadModelRepository. |
| 195.8 | Extend `PortalEventHandler` for acceptance events | 195B | 195.6, 195.7 | Modify `customerbackend/handler/PortalEventHandler.java`: add `@TransactionalEventListener(phase = AFTER_COMMIT)` handlers for: `AcceptanceRequestSentEvent` -> create PortalAcceptanceView, `AcceptanceRequestAcceptedEvent` -> update status to ACCEPTED, `AcceptanceRequestRevokedEvent` -> update status to REVOKED, `AcceptanceRequestExpiredEvent` -> update status to EXPIRED. Pattern: existing handlers in PortalEventHandler for InvoiceSentEvent, etc. |
| 195.9 | Write portal read-model sync tests | 195B | 195.8 | New file: `customerbackend/AcceptanceReadModelSyncIntegrationTest.java`. Tests: (1) sentEvent_creates_portalAcceptanceView, (2) acceptedEvent_updates_status, (3) revokedEvent_updates_status, (4) expiredEvent_updates_status, (5) findPendingByContactId_returns_active_only. ~5 tests. Pattern: existing portal read-model tests. |
| 195.10 | Verify V11 migration + entity mapping | 195B | 195.6 | Covered by integration tests: verify PortalAcceptanceView maps correctly to `portal_acceptance_requests` table created by V11 global migration. |

### Key Files

**Slice 195A -- Create:**
- `backend/src/main/resources/templates/certificates/certificate-of-acceptance.html`
- `backend/src/main/java/.../acceptance/AcceptanceCertificateService.java`
- `backend/src/test/java/.../acceptance/AcceptanceCertificateServiceTest.java`

**Slice 195A -- Modify:**
- `backend/src/main/java/.../acceptance/AcceptanceService.java` -- inject certificate service, call in accept()

**Slice 195A -- Read for context:**
- `backend/src/main/java/.../template/PdfRenderingService.java` -- htmlToPdf() method
- `backend/src/main/java/.../s3/StorageService.java` -- upload/download pattern

**Slice 195B -- Create:**
- `backend/src/main/java/.../customerbackend/model/PortalAcceptanceView.java`
- `backend/src/test/java/.../customerbackend/AcceptanceReadModelSyncIntegrationTest.java`

**Slice 195B -- Modify:**
- `backend/src/main/java/.../customerbackend/repository/PortalReadModelRepository.java`
- `backend/src/main/java/.../customerbackend/handler/PortalEventHandler.java`

**Slice 195B -- Read for context:**
- `backend/src/main/java/.../customerbackend/model/PortalDocumentView.java` -- read-model entity pattern
- `backend/src/main/java/.../customerbackend/handler/PortalEventHandler.java` -- event handler pattern
- `backend/src/main/java/.../event/DomainEvent.java` -- event interface

### Architecture Decisions

- **Certificate rendering uses Spring TemplateEngine (classpath), not StringTemplateResolver**: Certificate templates are system-managed files on the classpath, not user-editable DB templates. Using the classpath resolver avoids the SSTI concerns of StringTemplateResolver. Per architecture doc Section 28.7.4.
- **Hybrid portal read-model sync (ADR-109)**: Read-model stores enough for list views (title, status, token, expiry). The acceptance page reads live from the main schema via token lookup. Only status transitions are synced -- acceptance metadata (name, IP, UA) is NOT synced to the read-model.
- **Certificate generation is synchronous**: One-page PDF renders in <100ms. Async would add transaction boundary complexity for minimal latency benefit. Per architecture doc Section 28.3.4.

---

## Epic 196: Firm-Facing REST API + Audit + Notifications

**Goal**: Create the firm-facing `AcceptanceController` with full CRUD and lifecycle endpoints, wire audit events for all state transitions, and integrate acceptance notifications.

**References**: Architecture doc Sections 28.4.1 (API surface), 28.6.4 (firm notification), 28.9.1 (backend changes).

**Dependencies**: Epics 194 (service), 195 (certificate for download endpoint).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **196A** | 196.1--196.6 | `AcceptanceController` at `/api/acceptance-requests` (POST create, GET list by document/customer, GET detail, POST remind, POST revoke, GET certificate download) + response DTOs (`AcceptanceRequestResponse`, `CreateAcceptanceRequest`) + `@PreAuthorize` RBAC annotations + controller integration tests. ~4 new files, ~1 test file. Backend only. | |
| **196B** | 196.7--196.12 | Audit event recording wired into AcceptanceService for all 8 event types (acceptance.created, .sent, .viewed, .accepted, .reminded, .revoked, .expired, .certificate_generated) + `NotificationEventHandler` extension for in-app acceptance notification + OrgSettings controller/service extension (acceptance config endpoint) + audit + notification integration tests. ~0-1 new files, ~5 modified files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 196.1 | Create `CreateAcceptanceRequest` DTO | 196A | | New file: `acceptance/dto/CreateAcceptanceRequest.java`. Record: `@NotNull UUID generatedDocumentId`, `@NotNull UUID portalContactId`, `@Min(1) @Max(365) Integer expiryDays` (nullable -- uses org default if null). Pattern: `clause/dto/CreateClauseRequest.java`. |
| 196.2 | Create `AcceptanceRequestResponse` DTO | 196A | | New file: `acceptance/dto/AcceptanceRequestResponse.java`. Record: all fields from AcceptanceRequest entity + nested `PortalContactSummary` (id, displayName, email) + nested `GeneratedDocumentSummary` (id, fileName). Static factory `from(AcceptanceRequest, PortalContact, GeneratedDocument)`. Pattern: `clause/dto/ClauseResponse.java`. |
| 196.3 | Create `AcceptanceController` | 196A | 196.1, 196.2 | New file: `acceptance/AcceptanceController.java`. `@RestController`, `@RequestMapping("/api/acceptance-requests")`. Endpoints per architecture doc Section 28.4.1: `POST /` -- `@PreAuthorize("hasAnyRole('ORG_ADMIN','ORG_OWNER')")`, returns 201. `GET /?documentId=` -- MEMBER+, returns list. `GET /?customerId=` -- MEMBER+, returns list. `GET /{id}` -- MEMBER+, returns detail with nested contact + document. `POST /{id}/remind` -- ADMIN/OWNER, returns 200. `POST /{id}/revoke` -- ADMIN/OWNER, returns 200. `GET /{id}/certificate` -- MEMBER+, streams PDF from S3 via StorageService, sets Content-Type and Content-Disposition. 404 if no certificate yet. Pattern: `clause/ClauseController.java` for thin controller pattern, `template/GeneratedDocumentController.java` for PDF streaming. |
| 196.4 | Implement certificate download | 196A | 196.3 | In `AcceptanceController.GET /{id}/certificate`: fetch AcceptanceRequest, check certificateS3Key is non-null (404 otherwise), download bytes from S3 via StorageService, return ResponseEntity with `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="{certificateFileName}"`. Pattern: `template/GeneratedDocumentController.java` download endpoint. |
| 196.5 | Write controller integration tests | 196A | 196.3 | New file: `acceptance/AcceptanceControllerIntegrationTest.java`. MockMvc tests: (1) POST_creates_acceptance_request_as_admin, (2) POST_403_for_member, (3) GET_list_by_documentId, (4) GET_list_by_customerId, (5) GET_detail_returns_full_response, (6) POST_remind_sends_email, (7) POST_remind_403_for_member, (8) POST_revoke_transitions_to_revoked, (9) GET_certificate_streams_pdf, (10) GET_certificate_404_when_not_accepted. ~10 tests. Pattern: `clause/ClauseControllerIntegrationTest.java`. |
| 196.6 | Verify RBAC enforcement | 196A | 196.5 | Covered by controller tests: write operations (POST create, POST remind, POST revoke) restricted to ADMIN/OWNER (403 for MEMBER). Read operations (GET list, GET detail, GET certificate) available to MEMBER+. |
| 196.7 | Wire audit events in AcceptanceService | 196B | | Modify `acceptance/AcceptanceService.java`: inject `AuditService`. Add audit recording at each state transition: `acceptance.created` (details: document title, recipient name/email, expiry date), `acceptance.sent` (details: document title, recipient email), `acceptance.viewed` (details: IP address, timestamp), `acceptance.accepted` (details: acceptor name, IP, user agent, timestamp), `acceptance.reminded` (details: reminder count, recipient email), `acceptance.revoked` (details: revoked by member, reason), `acceptance.expired` (details: original expiry date, document title), `acceptance.certificate_generated` (details: S3 key, document hash). Pattern: `clause/ClauseService.java` audit calls using `AuditService.record()`. |
| 196.8 | Wire firm notification for acceptance | 196B | | Modify `notification/NotificationEventHandler.java`: add `@EventListener` for `AcceptanceRequestAcceptedEvent`. Create in-app notification for `sentByMemberId`: "{contactName} has accepted {documentFileName}". Use `NotificationService.createNotification()` with type `ACCEPTANCE_COMPLETED`, referenceType `ACCEPTANCE_REQUEST`, referenceId = requestId. Pattern: existing notification handlers for InvoicePaidEvent, etc. |
| 196.9 | Register ACCEPTANCE_COMPLETED notification type | 196B | 196.8 | Modify `notification/NotificationService.java` or notification type registry: add `ACCEPTANCE_COMPLETED` to the registered notification types. This may just be adding a string constant or enum value -- follow existing pattern. |
| 196.10 | Extend OrgSettings controller for acceptance config | 196B | | Modify `settings/OrgSettingsController.java`: add `PUT /api/org-settings/acceptance` or extend existing update endpoint to accept `acceptanceExpiryDays`. Modify `settings/OrgSettingsService.java`: add update method. Pattern: existing OrgSettings update endpoints for branding fields. |
| 196.11 | Write audit + notification integration tests | 196B | 196.7, 196.8 | New file: `acceptance/AcceptanceAuditAndNotificationIntegrationTest.java`. Tests: (1) createAndSend_records_created_and_sent_audit_events, (2) accept_records_accepted_audit_event, (3) revoke_records_revoked_audit_event, (4) remind_records_reminded_audit_event, (5) accept_creates_notification_for_sender. ~5 tests. |
| 196.12 | Write OrgSettings acceptance config test | 196B | 196.10 | In existing or new test file: verify PUT acceptance expiry days updates OrgSettings, verify min/max validation (1-365). ~2 tests. |

### Key Files

**Slice 196A -- Create:**
- `backend/src/main/java/.../acceptance/dto/CreateAcceptanceRequest.java`
- `backend/src/main/java/.../acceptance/dto/AcceptanceRequestResponse.java`
- `backend/src/main/java/.../acceptance/AcceptanceController.java`
- `backend/src/test/java/.../acceptance/AcceptanceControllerIntegrationTest.java`

**Slice 196A -- Read for context:**
- `backend/src/main/java/.../clause/ClauseController.java` -- thin controller pattern
- `backend/src/main/java/.../template/GeneratedDocumentController.java` -- PDF streaming pattern
- `backend/src/main/java/.../s3/StorageService.java` -- download for certificate streaming

**Slice 196B -- Modify:**
- `backend/src/main/java/.../acceptance/AcceptanceService.java` -- add AuditService injection + audit calls
- `backend/src/main/java/.../notification/NotificationEventHandler.java` -- add acceptance handler
- `backend/src/main/java/.../notification/NotificationService.java` -- register notification type
- `backend/src/main/java/.../settings/OrgSettingsController.java` -- acceptance config endpoint
- `backend/src/main/java/.../settings/OrgSettingsService.java` -- acceptance config update

**Slice 196B -- Create:**
- `backend/src/test/java/.../acceptance/AcceptanceAuditAndNotificationIntegrationTest.java`

### Architecture Decisions

- **Audit wired in 196B, not 194A**: Keeps the core service slice (194A) focused on business logic. Audit is a cross-cutting concern that modifies the service but does not change its interface.
- **Certificate download streams from S3**: Follows the existing `GeneratedDocumentController` pattern (stream bytes, set Content-Disposition). Presigned URL redirect is an alternative but inconsistent with established patterns.
- **OrgSettings extension in 196B**: Small change (one field, one endpoint). Grouped with audit/notification to avoid a standalone 1-task slice.

---

## Epic 197: Portal Acceptance Controller + Expiry Processor

**Goal**: Create the portal-facing `PortalAcceptanceController` for token-authenticated acceptance flow and the `@Scheduled` expiry processor.

**References**: Architecture doc Sections 28.4.2 (portal API), 28.3.7 (expiry processing).

**Dependencies**: Epics 194 (service), 195 (certificate, portal read-model).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **197A** | 197.1--197.8 | `PortalAcceptanceController` at `/api/portal/acceptance/{token}` (GET page data + markViewed, GET pdf stream, POST accept with IP/UA extraction) + security config exemption for token-based auth + `@Scheduled` expiry processor in AcceptanceService + portal list endpoint for pending acceptances + controller + expiry integration tests. ~2 new files, ~2 modified, ~1 test. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 197.1 | Create `PortalAcceptanceController` | 197A | | New file: `acceptance/PortalAcceptanceController.java`. `@RestController`, `@RequestMapping("/api/portal/acceptance")`. Endpoints per architecture doc Section 28.4.2: `GET /{token}` -- calls `acceptanceService.markViewed()`, returns page data (document title, file name, status, expiry, org branding). `GET /{token}/pdf` -- looks up request by token, streams original PDF from S3 via StorageService. `POST /{token}/accept` -- extracts IP from `HttpServletRequest.getRemoteAddr()` (with X-Forwarded-For check), user agent from `User-Agent` header, calls `acceptanceService.accept()`. All endpoints are token-authenticated (no portal session required). Pattern: `portal/PortalDocumentController.java` for portal controller pattern. |
| 197.2 | Implement IP address extraction | 197A | 197.1 | In `PortalAcceptanceController`: private method `extractClientIp(HttpServletRequest request)`. Check `X-Forwarded-For` header first (take first IP), fall back to `request.getRemoteAddr()`. Also check `X-Real-IP` header. Pattern: standard reverse proxy IP resolution. |
| 197.3 | Configure security exemption for portal acceptance | 197A | 197.1 | Modify `config/SecurityConfig.java` or equivalent: add `/api/portal/acceptance/**` to the list of paths that bypass JWT authentication. These endpoints authenticate via the request token, not via JWT or portal session. Pattern: existing portal endpoint security exemptions (e.g., `/api/portal/auth/**`). |
| 197.4 | Add portal list endpoint | 197A | 195.7 | In `PortalAcceptanceController` or a separate method: `GET /api/portal/acceptance/pending?contactId={id}` -- returns pending acceptance requests from the portal read-model for a specific contact. Uses `PortalReadModelRepository.findPendingAcceptancesByContactId()`. This endpoint requires portal session auth (JWT from magic link). Pattern: existing portal list endpoints. |
| 197.5 | Implement `processExpired()` scheduled task | 197A | | Modify `acceptance/AcceptanceService.java`: add `@Scheduled(fixedDelay = 3600000)` on `processExpired()` method. Finds all requests where status IN (PENDING, SENT, VIEWED) AND expiresAt < now(). Transitions each to EXPIRED. Publishes `AcceptanceRequestExpiredEvent` for each. Records audit events. Logs count of expired requests. Pattern: `schedule/ScheduleExecutionService.java` for @Scheduled pattern. Note: `@EnableScheduling` must be present on a config class (check if already enabled). |
| 197.6 | Write portal controller integration tests | 197A | 197.1 | New file: `acceptance/PortalAcceptanceControllerIntegrationTest.java`. Tests: (1) GET_by_token_returns_page_data, (2) GET_by_token_marks_viewed, (3) GET_by_token_returns_expired_status_for_expired_request, (4) GET_by_token_404_for_invalid_token, (5) GET_pdf_streams_document, (6) POST_accept_records_acceptance_with_ip_ua, (7) POST_accept_rejects_expired, (8) POST_accept_rejects_revoked, (9) POST_accept_idempotent_for_already_accepted. ~9 tests. |
| 197.7 | Write expiry processor tests | 197A | 197.5 | In portal controller test file or separate: (1) processExpired_transitions_expired_requests, (2) processExpired_ignores_terminal_statuses, (3) processExpired_publishes_events. ~3 tests. |
| 197.8 | Verify token-based auth bypasses JWT filter | 197A | 197.3 | Covered by integration tests: verify GET/POST portal acceptance endpoints work without JWT token in Authorization header. The request token in the URL path is the sole authentication credential. |

### Key Files

**Slice 197A -- Create:**
- `backend/src/main/java/.../acceptance/PortalAcceptanceController.java`
- `backend/src/test/java/.../acceptance/PortalAcceptanceControllerIntegrationTest.java`

**Slice 197A -- Modify:**
- `backend/src/main/java/.../config/SecurityConfig.java` -- add portal acceptance path exemption
- `backend/src/main/java/.../acceptance/AcceptanceService.java` -- add @Scheduled processExpired()

**Slice 197A -- Read for context:**
- `backend/src/main/java/.../portal/PortalDocumentController.java` -- portal controller pattern
- `backend/src/main/java/.../portal/CustomerAuthFilter.java` -- portal auth pattern
- `backend/src/main/java/.../config/SecurityConfig.java` -- security exemption pattern
- `backend/src/main/java/.../s3/StorageService.java` -- PDF streaming

### Architecture Decisions

- **Token-based auth is separate from portal session**: Per ADR-107, acceptance tokens authenticate access to a specific acceptance page. No portal session is created. The security config must exempt these endpoints from JWT validation.
- **Expiry processor runs hourly**: `@Scheduled(fixedDelay = 3600000)` is generous -- expiry is soft. The inline expiry check in `markViewed()` and `accept()` catches expired requests immediately on access. The batch processor is a cleanup backstop.
- **IP extraction with proxy awareness**: X-Forwarded-For is checked before RemoteAddr to get the real client IP behind load balancers/reverse proxies.

---

## Epic 198: Frontend -- Send for Acceptance + Status Tracking

**Goal**: Add the "Send for Acceptance" action to generated documents, acceptance status badges, and the acceptance detail panel to the main firm-facing frontend.

**References**: Architecture doc Section 28.9.2 (frontend changes), requirements Sections 10-11.

**Dependencies**: Epic 196 (firm-facing API).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **198A** | 198.1--198.6 | `acceptance-actions.ts` server actions + `SendForAcceptanceDialog.tsx` (recipient picker from portal contacts, expiry override, send action) + `AcceptanceStatusBadge.tsx` (colored badge per status) + integration into `GeneratedDocumentsList.tsx` (action menu item + status badge per row) + frontend tests. ~4 new files, ~1 modified. Frontend only. | |
| **198B** | 198.7--198.11 | `AcceptanceDetailPanel.tsx` (expandable panel: recipient info, status timeline with timestamps, remind/revoke action buttons, certificate download link) + integration into generated document detail views + `GenerateDocumentDialog.tsx` post-generation "Send for Acceptance" button + frontend tests. ~2 new files, ~2 modified. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 198.1 | Create `acceptance-actions.ts` | 198A | | New file: `frontend/app/(app)/org/[slug]/acceptance-actions.ts` (or `frontend/lib/api/acceptance.ts`). Server actions: `sendForAcceptance(generatedDocumentId, portalContactId, expiryDays?)` -> POST `/api/acceptance-requests`. `getAcceptanceRequests(documentId)` -> GET `/api/acceptance-requests?documentId=`. `remindAcceptance(requestId)` -> POST `/api/acceptance-requests/{id}/remind`. `revokeAcceptance(requestId)` -> POST `/api/acceptance-requests/{id}/revoke`. `getAcceptanceDetail(requestId)` -> GET `/api/acceptance-requests/{id}`. `fetchPortalContacts(customerId)` -> GET `/api/portal-contacts?customerId=` (existing endpoint). Pattern: `frontend/app/(app)/org/[slug]/settings/clauses/clause-actions.ts`. |
| 198.2 | Create `AcceptanceStatusBadge.tsx` | 198A | | New file: `frontend/components/acceptance/AcceptanceStatusBadge.tsx`. Renders colored badge per status: no request = no badge, SENT = "Awaiting Acceptance" (amber), VIEWED = "Viewed" (blue), ACCEPTED = "Accepted" (green + check icon), EXPIRED = "Expired" (gray), REVOKED = "Revoked" (gray). Uses Shadcn Badge component. Pattern: invoice status badges or task status badges. |
| 198.3 | Create `SendForAcceptanceDialog.tsx` | 198A | 198.1 | New file: `frontend/components/acceptance/SendForAcceptanceDialog.tsx`. Shadcn Dialog. Props: `generatedDocumentId`, `customerId`, `documentTitle`, `onSuccess`. Content: (1) document title display, (2) recipient picker -- ComboBox/Select loading portal contacts for the customer via `fetchPortalContacts()`. Shows contact name + email. If no contacts exist, show message "No portal contacts found" with link to customer contacts tab. (3) Expiry override -- optional NumberInput "Expires in N days" (placeholder shows org default). (4) "Send" button -- calls `sendForAcceptance()`, shows success toast, closes dialog. (5) Loading + error states. Pattern: `frontend/components/templates/GenerateDocumentDialog.tsx` dialog pattern. |
| 198.4 | Integrate into `GeneratedDocumentsList.tsx` | 198A | 198.2, 198.3 | Modify `frontend/components/templates/GeneratedDocumentsList.tsx`: (1) Add "Send for Acceptance" item to the action dropdown menu per document row. Opens `SendForAcceptanceDialog` with the document's ID and customer ID. (2) Add `AcceptanceStatusBadge` next to each document row, showing the latest acceptance status (fetch via `getAcceptanceRequests` or embed in the generated document list response). (3) Only show "Send for Acceptance" action for ADMIN/OWNER roles. Pattern: existing action menu items in document rows. |
| 198.5 | Write frontend tests for acceptance actions | 198A | 198.1, 198.2, 198.3 | New file: `frontend/__tests__/acceptance/SendForAcceptanceDialog.test.tsx`. Tests: (1) renders_dialog_with_recipient_picker, (2) shows_no_contacts_message, (3) sends_acceptance_request_on_submit, (4) shows_success_toast, (5) validates_expiry_range. ~5 tests. New file: `frontend/__tests__/acceptance/AcceptanceStatusBadge.test.tsx`. Tests: (1) renders_correct_badge_for_each_status, (2) renders_nothing_when_no_status. ~2 tests. Pattern: `frontend/__tests__/templates/GenerateDocumentDialog.test.tsx`. |
| 198.6 | Write GeneratedDocumentsList integration test | 198A | 198.4 | In existing `frontend/__tests__/templates/GeneratedDocumentsList.test.tsx` or new file: (1) shows_send_for_acceptance_action, (2) shows_acceptance_status_badge. ~2 tests. |
| 198.7 | Create `AcceptanceDetailPanel.tsx` | 198B | 198.1 | New file: `frontend/components/acceptance/AcceptanceDetailPanel.tsx`. Expandable panel (Collapsible or Sheet). Content: (1) Recipient info -- name, email. (2) Status timeline -- sent at, viewed at, accepted at with timestamps. (3) If accepted: acceptor name typed, acceptance date, "Download Certificate" button (calls GET `/api/acceptance-requests/{id}/certificate`). (4) Actions: "Remind" button (if SENT/VIEWED, ADMIN/OWNER only), "Revoke" button (if SENT/VIEWED, ADMIN/OWNER only). (5) Reminder history: "Reminded N times, last on {date}". (6) Loading states. Pattern: task detail panels or existing expandable detail components. |
| 198.8 | Integrate AcceptanceDetailPanel into document views | 198B | 198.7 | Modify `frontend/components/templates/GeneratedDocumentsList.tsx` or create a detail view: when a generated document with an acceptance request is expanded/clicked, show `AcceptanceDetailPanel` with the acceptance request details. Alternatively, add as an inline expandable row below the document row. |
| 198.9 | Add "Send for Acceptance" to GenerateDocumentDialog success state | 198B | 198.3 | Modify `frontend/components/templates/GenerateDocumentDialog.tsx`: in the post-generation success state (after PDF is generated), add a "Send for Acceptance" button alongside existing "Download" and "View" actions. Clicking opens `SendForAcceptanceDialog` with the newly generated document ID. Pattern: existing post-generation action buttons. |
| 198.10 | Write AcceptanceDetailPanel tests | 198B | 198.7 | New file: `frontend/__tests__/acceptance/AcceptanceDetailPanel.test.tsx`. Tests: (1) renders_recipient_info, (2) renders_status_timeline, (3) shows_certificate_download_when_accepted, (4) shows_remind_button_for_sent_status, (5) shows_revoke_button_for_active_status, (6) hides_actions_for_terminal_status. ~6 tests. |
| 198.11 | Write GenerateDocumentDialog acceptance integration test | 198B | 198.9 | In existing `frontend/__tests__/templates/GenerateDocumentDialog.test.tsx`: add test for "Send for Acceptance" button appearing in success state. ~1 test. |

### Key Files

**Slice 198A -- Create:**
- `frontend/app/(app)/org/[slug]/acceptance-actions.ts` (or `frontend/lib/api/acceptance.ts`)
- `frontend/components/acceptance/AcceptanceStatusBadge.tsx`
- `frontend/components/acceptance/SendForAcceptanceDialog.tsx`
- `frontend/__tests__/acceptance/SendForAcceptanceDialog.test.tsx`
- `frontend/__tests__/acceptance/AcceptanceStatusBadge.test.tsx`

**Slice 198A -- Modify:**
- `frontend/components/templates/GeneratedDocumentsList.tsx`

**Slice 198A -- Read for context:**
- `frontend/components/templates/GenerateDocumentDialog.tsx` -- dialog pattern
- `frontend/app/(app)/org/[slug]/settings/clauses/clause-actions.ts` -- server actions pattern
- `frontend/__tests__/templates/GeneratedDocumentsList.test.tsx` -- test pattern

**Slice 198B -- Create:**
- `frontend/components/acceptance/AcceptanceDetailPanel.tsx`
- `frontend/__tests__/acceptance/AcceptanceDetailPanel.test.tsx`

**Slice 198B -- Modify:**
- `frontend/components/templates/GeneratedDocumentsList.tsx` -- detail panel integration
- `frontend/components/templates/GenerateDocumentDialog.tsx` -- post-generation acceptance button

### Architecture Decisions

- **Server actions pattern (not API client module)**: Following the established frontend pattern where each domain has a `*-actions.ts` file with server actions. The actions handle API calls, error handling, and revalidation.
- **Status badge follows invoice badge pattern**: Same color scheme and Shadcn Badge component used for invoice status badges. Consistent visual language.
- **Detail panel is expandable, not a separate page**: Acceptance detail is shown inline or in a side panel within the generated documents context. No separate acceptance management page -- acceptances are always viewed in the context of the document they belong to.

---

## Epic 199: Portal -- Acceptance Page + Pending List

**Goal**: Create the portal acceptance page (PDF viewer + acceptance form) and the pending acceptances list in the portal dashboard.

**References**: Architecture doc Section 28.9.2 (frontend changes), requirements Section 12.

**Dependencies**: Epic 197 (portal controller).

**Scope**: Portal (Next.js)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **199A** | 199.1--199.6 | Portal acceptance page at `/accept/[token]` (PDF viewer via iframe, acceptance form with typed name, post-acceptance confirmation state, expired/revoked error states, org branding) + portal acceptance API calls + portal frontend tests. ~3 new files. Portal only. | |
| **199B** | 199.7--199.10 | "Pending Acceptances" section on portal dashboard or projects page (list of documents awaiting acceptance, status badges, direct links to acceptance page) + portal read-model API call + portal frontend tests. ~1 new file, ~1 modified. Portal only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 199.1 | Create portal acceptance page route | 199A | | New file: `portal/app/accept/[token]/page.tsx` (public route -- not under `(authenticated)` since token-based auth). Server component that fetches acceptance data via `GET /api/portal/acceptance/{token}`. Passes data to client component for interactive form. If request is expired/revoked, show error state. Pattern: `portal/app/(authenticated)/projects/page.tsx` for portal page structure. Note: this route is OUTSIDE the `(authenticated)` layout since it uses token auth, not portal session auth. |
| 199.2 | Create acceptance page client component | 199A | 199.1 | New file: `portal/app/accept/[token]/acceptance-page.tsx` (or `components/acceptance/AcceptancePage.tsx`). Client component. Layout: (1) Org branding header (logo, name, brand color). (2) Document title + file name. (3) PDF viewer -- `<iframe src="/api/portal/acceptance/{token}/pdf" />` with responsive sizing. (4) Acceptance form (below PDF): text "By typing your name below...", Input field "Your full name" (required, min 2 chars), "I Accept" button (primary, disabled until name entered). (5) Legal fine print about IP/browser recording. (6) Loading state while PDF loads. Pattern: portal page components. |
| 199.3 | Implement post-acceptance state | 199A | 199.2 | In acceptance page client component: after successful POST accept, transition to confirmation state: green checkmark, "You have accepted this document on {date}", PDF remains viewable, "Download PDF" button, acceptance form replaced with confirmation. Use React state to toggle between form and confirmation. |
| 199.4 | Implement expired/revoked states | 199A | 199.2 | In acceptance page: if API returns status EXPIRED, show "This acceptance request has expired. Please contact {orgName} for a new link." If REVOKED, show "This acceptance request has been revoked by {orgName}." PDF should still be viewable per requirements. Style: muted error message, no acceptance form. |
| 199.5 | Create portal acceptance API utilities | 199A | | New file: `portal/lib/api/acceptance.ts`. Functions: `getAcceptancePageData(token)` -> GET `/api/portal/acceptance/{token}`. `submitAcceptance(token, name)` -> POST `/api/portal/acceptance/{token}/accept`. `getAcceptancePdfUrl(token)` -> constructs URL for PDF iframe. Error handling for expired/revoked/not-found responses. Pattern: existing portal API utilities. |
| 199.6 | Write portal acceptance page tests | 199A | 199.2 | New file: `portal/__tests__/accept/AcceptancePage.test.tsx` (or in portal test directory). Tests: (1) renders_pdf_viewer_and_form, (2) disables_accept_button_without_name, (3) enables_accept_button_with_valid_name, (4) submits_acceptance_and_shows_confirmation, (5) shows_expired_message, (6) shows_revoked_message, (7) renders_org_branding. ~7 tests. |
| 199.7 | Create pending acceptances list component | 199B | | New file: `portal/components/PendingAcceptancesList.tsx`. Client component. Fetches pending acceptances for the current portal contact via the portal read-model API (`GET /api/portal/acceptance/pending?contactId={id}`). Renders: document title, sent date, expiry date, "Review & Accept" link (to `/accept/{requestToken}`). If no pending acceptances, show "No pending acceptances." Pattern: existing portal list components (invoice list, project list). |
| 199.8 | Integrate pending acceptances into portal dashboard | 199B | 199.7 | Modify `portal/app/(authenticated)/layout.tsx` or dashboard page: add "Pending Acceptances" section using `PendingAcceptancesList`. Position prominently (above or alongside project list) since pending acceptances require action. Alternatively, add as a tab/section in the documents page if one exists. |
| 199.9 | Write pending acceptances tests | 199B | 199.7 | In portal test directory: (1) renders_pending_acceptances_list, (2) shows_empty_state, (3) links_to_acceptance_page. ~3 tests. |
| 199.10 | Portal acceptance page responsive styling | 199B | 199.2 | Verify acceptance page works on mobile viewports (min 320px width). PDF iframe should be scrollable. Acceptance form should stack vertically. "I Accept" button should be full-width on mobile. Pattern: existing portal responsive patterns. |

### Key Files

**Slice 199A -- Create:**
- `portal/app/accept/[token]/page.tsx`
- `portal/app/accept/[token]/acceptance-page.tsx` (or component file)
- `portal/lib/api/acceptance.ts`
- `portal/__tests__/accept/AcceptancePage.test.tsx`

**Slice 199A -- Read for context:**
- `portal/app/(authenticated)/projects/page.tsx` -- portal page pattern
- `portal/app/(authenticated)/layout.tsx` -- portal layout/branding
- `portal/app/login/page.tsx` -- public route pattern (outside authenticated layout)

**Slice 199B -- Create:**
- `portal/components/PendingAcceptancesList.tsx`
- `portal/__tests__/PendingAcceptancesList.test.tsx` (or similar)

**Slice 199B -- Modify:**
- `portal/app/(authenticated)/layout.tsx` or dashboard page -- add pending acceptances section

### Architecture Decisions

- **Acceptance page is a public route (not under `(authenticated)`)**: The acceptance page uses token-based auth (ADR-107). It does not require a portal session. The route is at `/accept/[token]`, outside the `(authenticated)` layout group.
- **PDF viewer via iframe**: Simple, browser-native PDF rendering. PDF.js is an alternative for richer UX but adds dependency complexity. `<iframe>` with the PDF stream URL is the pragmatic choice.
- **Pending acceptances read from portal read-model**: Per ADR-109 hybrid approach. List views use the read-model. The acceptance page reads live from the main schema.

---

## Epic 200: Frontend -- OrgSettings Acceptance Config

**Goal**: Add acceptance expiry configuration to the existing OrgSettings page.

**References**: Architecture doc Section 28.2.4 (OrgSettings extension), requirements Section 7.

**Dependencies**: Epic 196 (OrgSettings API endpoint in 196B).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **200A** | 200.1--200.3 | Add "Document Acceptance" section to existing org settings page with acceptance expiry days input (number field, min 1, max 365, default 30) + server action update + frontend test. ~0-1 new files, ~2 modified. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 200.1 | Add acceptance settings section to settings page | 200A | | Modify `frontend/app/(app)/org/[slug]/settings/page.tsx` (or the relevant settings sub-page): add a "Document Acceptance" section (Card with heading). Content: (1) "Default Expiry Period" label. (2) Number input field with placeholder "30", min=1, max=365. (3) Help text: "Number of days before acceptance requests expire. Default: 30 days." (4) Save button. Pattern: existing settings sections (branding, email, etc.). |
| 200.2 | Create or extend settings action for acceptance config | 200A | | Modify settings actions file (e.g., `frontend/app/(app)/org/[slug]/settings/actions.ts` or create `acceptance-settings-actions.ts`): add `updateAcceptanceSettings(acceptanceExpiryDays: number)` -> PUT `/api/org-settings/acceptance`. Pattern: existing settings actions. |
| 200.3 | Write acceptance settings test | 200A | | In settings test file or new file: (1) renders_acceptance_expiry_input_with_current_value, (2) saves_updated_expiry_days, (3) validates_min_max_range. ~3 tests. |

### Key Files

**Slice 200A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` (or relevant settings sub-page)
- `frontend/app/(app)/org/[slug]/settings/actions.ts` (or new acceptance-settings-actions.ts)

**Slice 200A -- Read for context:**
- Existing settings sections for pattern (branding, email toggle, etc.)

### Architecture Decisions

- **Section on existing page, not a new settings page**: The acceptance expiry config is one field. Creating a new settings page would be over-engineering. A small card/section on the existing settings page is proportional.
- **Grouped with settings, not with acceptance**: The expiry config is an org-level default, not a per-request setting. It belongs in the settings UI, not in the acceptance workflow UI.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java` - Core entity that AcceptanceRequest references; pattern for entity design
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java` - Token generation pattern to follow for acceptance tokens
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` - Portal read-model sync pattern to extend
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` - Certificate PDF rendering via htmlToPdf()
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/templates/GeneratedDocumentsList.tsx` - Primary frontend file to extend with acceptance actions and status badges