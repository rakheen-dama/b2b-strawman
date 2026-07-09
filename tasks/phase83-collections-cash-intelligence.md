# Phase 83 — Collections & Cash Intelligence

Phase 83 closes the revenue loop that today ends at `SENT`: nothing in the product chases cash. It adds a net-new fork-neutral `collections/` bounded context in three layers that reuse almost everything already built. (1) A **deterministic dunning engine** — a `CollectionsSettings` embeddable on `OrgSettings` (enable + 4 stage thresholds), a per-customer `collectionsExempt` flag, and a `CollectionActivity` ledger (one row per `(invoice, stage)`, UNIQUE-indexed, transitions in place) driven by a daily `collections_scan` `JobHandler` that derives overdue at query time (`status = SENT AND due_date < today` — **no `OVERDUE` status**). (2) An **AI layer** — a `collection-reminder` `AiSkill` drafts each due reminder in the firm's house style; every draft lands as a PENDING `AiExecutionGate` (new sealed `SEND_COLLECTION_REMINDER` variant); a human approves singly or via a new generic `batch-approve` endpoint; only `GateActionExecutor` can send, through the existing invoice-email pipeline. **No auto-send path and no direct send endpoint exist, by construction.** Payment on any of the three routes (PSP webhook, Xero pull, manual) cancels pending gates via one AFTER_COMMIT listener. (3) A **weekly cash digest** — a `cash_digest` job assembles numbers deterministically (aging buckets, billed vs collected, stale WIP, reminder activity, triage top-risks), the `cash-digest` skill narrates them, and delivery is in-app + email to owners/admins with a numbers-only fallback when AI is disabled. The only vertical-aware behaviour is the `CollectionsAdvisor` seam (no-op default; legal-za `TrustAwareCollectionsAdvisor` over `ClientLedgerCardRepository`).

This phase ships as **7 epics (588–594)**, expanded to **15 numbered slices** to honour the 8–12 file / ~800 LOC slice-sizing budget. The architecture's 7 capability slices (§10) form the epic spine; the Foundation slice splits into schema/registrations vs APIs vs UI, and the Batch-Approval+Frontend slice splits backend vs two frontend surfaces. **No slice mixes backend + frontend scope** (594A is E2E/Process — it drives both stacks but writes no product code).

**Migration high-water at phase start**: tenant **V132** (`V132__create_correspondence_tables.sql`). Phase 83 ships **one** tenant migration: **V133** (`org_settings` +5 columns, `customers.collections_exempt`, `collection_activities` table + indexes). No global migration.

---

## Open Questions

- **V133 numbering.** Confirmed next-free as of 2026-07-09 (`architecture/phase83` §7), but **re-verify the actual next-free `V` when implementing 588A** by listing `backend/src/main/resources/db/migration/tenant/` — another in-flight phase may land first. File contents are unchanged if the number shifts.
- **`ai_executions.invoked_by` nullability.** Phase 83 invokes skills from job context with no user principal (`invokedBy = null`). If the column carries NOT NULL, V133 must include the conditional `ALTER TABLE ai_executions ALTER COLUMN invoked_by DROP NOT NULL` (commented in the architecture §7 SQL). **Resolution**: inspect the column in 588A; carry or drop the ALTER accordingly. Also confirm `AiSkillExecutionService` tolerates a null member (system invocation, §6.4) — if it requires a principal today, 590A adds the system-invocation path.
- **Recurring per-tenant enqueue wiring point.** `collections_scan` (daily) and `cash_digest` (weekly) register with the same fanout mechanism that drives `portal_digest` / `accounting_payment_poll`. The exact registration site (scheduler config vs enqueue registry) is resolved at build in 589A (scan) and 593A (digest) by reading how `portal_digest` is enqueued.
- **"Invoice-area view" permission exact shape.** Debtors/activities reads mirror the existing invoice-list permission (§9 of the architecture). **Resolution**: in 591A, copy the exact guard used by the invoice list controller (capability annotation or `isAuthenticated()` + service-side check) rather than inventing a new capability.
- **`NoOpReminderComposer` default-bean mechanism.** 589A ships the no-op as the default; 590A ships `AiReminderComposer` which must win. Use `@ConditionalOnMissingBean`-style config or `@Primary` on the AI composer — decide in 589A and document in the composer Javadoc so 590A follows it.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 588 | Collections Foundation — Migration, Policy Embeddable, Ledger Entity, Registrations, Settings UI | Backend + Frontend (split slices) | — | L | 588A, 588B, 588C | 588A **Done** (PR #1534), 588B **Done** (PR #1535) |
| 589 | Scan Engine + Escalation + Payment Cancellation | Backend | 588A, 588B | L | 589A, 589B | |
| 590 | Drafting Skill + Gated Send Executor | Backend | 589 | L | 590A, 590B | |
| 591 | Batch Approval + Collections Read APIs + Collections Frontend | Backend + Frontend (split slices) | 590 | L | 591A, 591B, 591C | |
| 592 | Debtor Triage + Trust-Aware Advisor Seam | Backend + Frontend (split slices) | 589 (592B also 591B) | M | 592A, 592B | |
| 593 | Weekly Cash Digest | Backend | 589A, 592A (types from 588A) | L | 593A, 593B | |
| 594 | QA Capstone — Observed End-to-End Lifecycle | E2E / Process | 588–593 | M | 594A | |

**Slice count: 15** (7 architecture capability slices expanded to 15 for the sizing budget). Backend/frontend split preserved per slice.

---

## Dependency Graph

```
REUSED (not rebuilt):
  Invoicing      (invoice/ — Invoice SENT/PAID/VOID + dueDate + customerEmail + paymentUrl,
                  InvoiceTransitionService → event/InvoicePaidEvent|InvoiceVoidedEvent,
                  PaymentReconciliationService, AccountingPaymentPollWorker, recordPayment)
  Email pipeline (integration/email/ — IntegrationRegistry, EmailContextBuilder,
                  EmailTemplateRenderer, EmailRateLimiter, EmailMessage.withTracking,
                  EmailDeliveryLogService; invoice/InvoiceEmailService as the mirror)
  AI foundation  (integration/ai/ — AiSkill + AiSkillExecutionService + AiExecution metering,
                  AiExecutionGate PENDING→APPROVED|REJECTED|EXPIRED + AiGate*Events +
                  AiGateExpiryHandler, sealed GateAction + GateActionExecutor, AI_REVIEW,
                  AiFirmProfileService, LlmJsonParser, StubAiProvider)
  Job queue      (infrastructure/jobqueue/ — JobHandler + JobHandlerRegistry + JobWorker
                  ScopedValue binding; PortalDigestHandler + AccountingPaymentPollHandler shapes)
  Settings       (settings/OrgSettings 10-embeddable spine + OrgSettingsService/Controller +
                  OrgSettingsSchemaSnapshotTest pin)
  Reporting      (reporting/InvoiceAgingReportQuery — aging-bucket SQL to extract, not duplicate)
  Audit/Activity (AuditEventTypeRegistry count-pinned at 36; ActivityMessageFormatter)
  Notifications  (NotificationService NOTIFICATION_TYPES + createIfEnabled + notifyAdminsAndOwners)
  Trust (legal)  (verticals/legal/trustaccounting/ledger/ClientLedgerCardRepository;
                  TrustBoundaryGuard DataAccessException-tolerance pattern)
                                 │
                                 ▼
    ┌────────────────────────────────────────────────────────────────┐
    │ Stage 1 — Foundation (sequential)                               │
    │  [588A  V133 migration; CollectionsSettings embeddable +        │
    │         OrgSettings wiring + snapshot-pin update;               │
    │         CollectionActivity + CollectionStage/Status enums +     │
    │         repository; Customer.collectionsExempt field]           │
    │                       │                                         │
    │                       ▼                                         │
    │  [588B  audit registry +5 (36→41) + formatter arms +            │
    │         NOTIFICATION_TYPES +2; GET/PUT /api/settings/collections│
    │         + strictly-increasing validation + policy audit;        │
    │         PUT /api/customers/{id}/collections-exemption]          │
    └────────────────────────────────────────────────────────────────┘
              │                                    │
              ▼                                    ▼
    ┌───────────────────────────┐   ┌────────────────────────────────┐
    │ Stage 2a — Frontend policy │   │ Stage 2b — Scan engine          │
    │  [588C settings/collections│   │  [589A ReminderComposer seam +  │
    │   page + customer exemption│   │   NoOpReminderComposer; scan    │
    │   toggle + lib/api client] │   │   service (stage select/        │
    │  (parallel with 589)       │   │   supersede/skip/escalate) +    │
    └───────────────────────────┘   │   collections_scan handler +    │
                                    │   recurring enqueue]            │
                                    │  [589B CollectionsPaymentListener│
                                    │   paid/void/reject/expiry →     │
                                    │   activity transitions]         │
                                    │  (589B parallel with 589A)      │
                                    └────────────────────────────────┘
                                           │                │
                          ┌────────────────┘                └───────────────┐
                          ▼                                                 ▼
    ┌────────────────────────────────────────┐   ┌─────────────────────────────────┐
    │ Stage 3 — AI send path (sequential)     │   │ Stage 3-parallel — Triage seam   │
    │  [590A CollectionReminderSkill + prompts│   │  [592A CollectionsTriageService + │
    │   + AiReminderComposer + stub outputs + │   │   CollectionsAdvisor SPI + no-op  │
    │   system invocation]                    │   │   + TrustAwareCollectionsAdvisor  │
    │                  │                      │   │   (legal-za) + boundary test]     │
    │                  ▼                      │   │  (needs only 589; runs parallel   │
    │  [590B SendCollectionReminderAction +   │   │   with 590/591A)                  │
    │   executor branch + send service +      │   └─────────────────────────────────┘
    │   collection-reminder template +        │                    │
    │   tenant-isolation test]                │                    │
    └────────────────────────────────────────┘                    │
                          │                                        │
                          ▼                                        ▼
    ┌────────────────────────────────────────┐   ┌─────────────────────────────────┐
    │ Stage 4 — Approval surface + frontend   │   │ Stage 4-parallel — Cash digest   │
    │  [591A batch-approve endpoint +         │   │  [593A aging-bucket helper       │
    │   collections read APIs (debtors/       │   │   extraction + CashDigestData    │
    │   activities)]                          │   │   assembly + cash_digest handler │
    │        │                                │   │   + weekly enqueue]              │
    │        ├──────────────┐                 │   │        │                         │
    │        ▼              ▼                 │   │        ▼                         │
    │  [591B debtors page  [591C invoice      │   │  [593B CashDigestSkill + prompts │
    │   + reminder queue    activity tab +    │   │   + stub + cash-digest template  │
    │   + batch-approve UI] customer history] │   │   + delivery + AI-off fallback]  │
    │        │                                │   │  (needs 589A + 592A; parallel    │
    │        ▼                                │   │   with 591 frontend)             │
    │  [592B triage badges on debtors page]   │   └─────────────────────────────────┘
    └────────────────────────────────────────┘                    │
                          └────────────────┬───────────────────────┘
                                           ▼
    ┌────────────────────────────────────────────────────────────────┐
    │ Stage 5 — QA Capstone                                           │
    │  [594A browser-driven lifecycle: policy on → seed overdue →     │
    │   scan → queue → batch approve → Mailpit → webhook pay →        │
    │   cancellation verified; escalation + digest observed;          │
    │   full mvnw verify + frontend gates; gap report]                │
    └────────────────────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- **588C** (settings/exemption UI) runs parallel with **589** — it needs only the 588B APIs.
- **589A** and **589B** run parallel after 588B — they share no files (scan service/handler vs event listener); both only read `CollectionActivity` from 588A.
- **592A** (triage + trust seam) needs only 589 — runs parallel with the whole 590 → 591A chain.
- **593A → 593B** (digest) needs 589A + 592A + the `CASH_DIGEST` type from 588B — runs parallel with 591B/591C frontend work.
- **591B** and **591C** run parallel after 591A (different pages; both extend `lib/api/collections.ts` — 591B creates the collections sections, 591C appends; sequence them if merge conflicts are a concern).
- **592B** needs 591B (the debtors page exists) + 592A (signals in the API).

---

## Implementation Order

### Stage 1 — Foundation (sequential)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **588A** | V133 migration (org_settings +5, customers.collections_exempt, collection_activities + UNIQUE/status/customer indexes, conditional invoked_by ALTER); `CollectionsSettings` embeddable + `OrgSettings` wiring + `OrgSettingsSchemaSnapshotTest` deliberate pin update; `CollectionActivity` entity + `CollectionStage`/`CollectionActivityStatus` enums + repository; `Customer.collectionsExempt` field. **Done** (PR #1534) |
| 1b | **588B** | `AuditEventTypeRegistry` +5 `collections.*` types (count 36→41) + `ActivityMessageFormatter` arms + `collection_activity` entity resolver; `NOTIFICATION_TYPES` + `COLLECTION_ESCALATED`/`CASH_DIGEST`; `GET/PUT /api/settings/collections` (strictly-increasing validation, `collections.policy.updated` audit); `PUT /api/customers/{id}/collections-exemption` (admin/owner). **Done** (PR #1535) |

### Stage 2 — Policy UI ∥ Scan engine (parallel after 588B)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 2a | **588C** | (Frontend) `settings/collections` policy card (enable + 4 thresholds, increasing-order client validation); customer-detail exemption toggle (admin/owner); `lib/api/collections.ts` settings/exemption clients. | 589A, 589B |
| 2b | **589A** | `ReminderComposer` seam + `NoOpReminderComposer`; `CollectionsScanService` (candidate query, highest-stage selection, supersede/skip semantics, escalation flag + `COLLECTION_ESCALATED` notification + audit); `CollectionsScanHandler` (`collections_scan`) + daily recurring enqueue registration. | 588C, 589B |
| 2c | **589B** | `CollectionsPaymentListener` — AFTER_COMMIT on `InvoicePaidEvent`/`InvoiceVoidedEvent` → expire pending gates + `CANCELLED_PAYMENT`; listeners for `AiGateRejectedEvent` → `REJECTED`, `AiGateExpiredEvent` → `SKIPPED(gate_expired)`. All-three-routes cancellation tests. | 588C, 589A |

### Stage 3 — AI send path (sequential) ∥ Triage seam

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 3a | **590A** | `CollectionReminderSkill` + `system.txt`/`output-schema.json`; `AiReminderComposer` (system-invoked via `AiSkillExecutionService`, replaces no-op); `StubAiProvider` canned reminder output; `draft_failed`/`ai_unavailable` skip semantics. | 592A |
| 3b | **590B** | `SendCollectionReminderAction` + `GateAction` permits + `parseAction`/`execute` arms; `CollectionReminderSendService` (mirror `InvoiceEmailService`) + `collection-reminder.html` template; `CollectionsTenantIsolationTest` (mandatory). | 592A |
| 3c | **592A** | `CollectionsTriageService` (deterministic signals); `CollectionsAdvisor` SPI + no-op default; `verticals/legal/collections/TrustAwareCollectionsAdvisor` (fail-open on `DataAccessException`); core-has-no-legal-imports boundary test. | 590A, 590B, 591A |

### Stage 4 — Approval surface + frontend ∥ Digest

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 4a | **591A** | `POST /api/ai/gates/batch-approve` (per-gate tx, per-gate dispositions, 200-always); `CollectionsController` read APIs (`/api/collections/debtors`, `/debtors/{customerId}`, `/activities?invoiceId=`) + paged native debtor-book query (signals wired from 592A when present, else empty). | 592A, 593A |
| 4b | **593A** | Extract shared aging-bucket helper from `InvoiceAgingReportQuery`; `CashDigestData` record + deterministic assembly (aging, billed vs collected, stale WIP, reminder activity, triage signals); `CashDigestHandler` (`cash_digest`) + weekly enqueue. | 591A, 591B |
| 4c | **591B** | (Frontend) `invoices/collections` debtors page + pending-reminder queue (multi-select, `ExecutionGateCard` preview, batch approve); `lib/api/ai.ts` `batchApproveAiGates`; `lib/api/collections.ts` debtors/activities clients; nav item. | 593A, 593B, 591C |
| 4d | **591C** | (Frontend) invoice-detail activity-ledger section + customer-detail chase-history section. | 591B, 593B |
| 4e | **593B** | `CashDigestSkill` + prompts + stub output; `cash-digest.html` (conditional narrative block); `CASH_DIGEST` notification + email delivery to owners/admins; AI-disabled numbers-only fallback; `collections.digest.sent` audit. | 591B, 591C |
| 4f | **592B** | (Frontend) triage-signal badges on the debtors page (after 591B + 592A). | 593B |

### Stage 5 — QA Capstone

| Order | Slice | Summary |
|-------|-------|---------|
| 5a | **594A** | Browser-driven observed lifecycle on the E2E stack (policy on → seed overdue via product APIs/UI → scan → queue → batch approve → Mailpit email with payment CTA → webhook-sim payment → cancellation in UI; escalation notification; digest email + bell; exempt customer produces nothing); screenshots + evidence trail; full `./mvnw verify` + `pnpm lint && pnpm build && pnpm test` + `format:check`; gap report. |

### Timeline

```
Stage 1: [588A] -> [588B]                                <- migration + embeddable + ledger, then registrations + APIs
Stage 2: [588C] // ([589A] // [589B])                    <- policy UI parallel with scan engine + cancellation
Stage 3: [590A] -> [590B]   // [592A]                    <- skill then executor; triage seam in parallel
Stage 4: [591A] -> ([591B] // [591C]) // ([593A] -> [593B]) ; [592B] after 591B+592A
Stage 5: [594A]                                          <- QA capstone
```

---
## Epic 588: Collections Foundation — Migration, Policy Embeddable, Ledger Entity, Registrations, Settings UI

**Goal**: Lay the entire persistence + registration foundation. V133 adds the five `CollectionsSettings` columns to `org_settings`, the `customers.collections_exempt` flag, and the `collection_activities` chase ledger with its `(invoice_id, stage)` UNIQUE index (the idempotency backbone). The `CollectionsSettings` embeddable follows `TimeReminderSettings` exactly (NOT NULL primitive boolean, lazy-init getter, deliberate snapshot-pin update). `CollectionActivity` follows `Correspondence` conventions verbatim. 588B registers the 5 `collections.*` audit types (catalogue count 36→41), formatter arms, the 2 notification types, and ships the policy + exemption APIs. 588C ships the settings UI and the customer exemption toggle.

**References**: Architecture §2 (Domain Model), §2.1 (`CollectionsSettings` field table), §2.2 (`CollectionActivity` + state machine), §2.3 (exemption), §4.2 (policy/exclusion APIs), §7 (V133 SQL verbatim), §8.1/§8.3 (backend change table + entity/repo patterns), §10 Slice 83-1; [ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md).

**Dependencies**: None within this phase (foundation). Reuses `settings/OrgSettings` embeddable spine, `customer/Customer`, audit/activity/notification registries.

**Scope**: Backend (588A, 588B) + Frontend (588C) — no slice mixes both.

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **588A** | 588A.1–588A.6 | ~9 backend files (1 migration + 1 embeddable + 1 OrgSettings mod + 1 entity + 2 enums + 1 repo + 1 Customer mod + 1–2 test mods/files) | V133; `CollectionsSettings` + wiring + snapshot-pin update; `CollectionActivity` + enums + repository; `Customer.collectionsExempt` field mapping. **Done** (PR #1534) |
| **588B** | 588B.1–588B.6 | ~9 backend files (registry mod + formatter mod + NotificationService mod + settings service/controller mods + customer service/controller mods + 2 test files) | Audit +5 (36→41) + formatter arms; `NOTIFICATION_TYPES` +2; settings GET/PUT with validation + policy audit; exemption endpoint. **Done** (PR #1535) |
| **588C** | 588C.1–588C.4 | ~7 frontend files (settings page + actions + 1 client component + customer-detail mod + lib/api client + 1–2 tests) | `settings/collections` policy card; customer exemption toggle; `lib/api/collections.ts` settings/exemption clients. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 588A.1 | Create V133 migration | `backend/src/main/resources/db/migration/tenant/V133__create_collections_tables.sql` | verified by 588A.5/.6 (runs clean) | `V132__create_correspondence_tables.sql` format | SQL verbatim from architecture §7: `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS collections_enabled boolean NOT NULL DEFAULT false` + 4 nullable integer columns (`collections_stage1_days`/`stage2`/`stage3`/`escalate`); `ALTER TABLE customers ADD COLUMN IF NOT EXISTS collections_exempt boolean NOT NULL DEFAULT false`; `CREATE TABLE IF NOT EXISTS collection_activities` (id PK uuid, invoice_id/customer_id NOT NULL raw-UUID FKs, stage varchar(20) + CHECK STAGE_1/2/3/ESCALATION, status varchar(20) + CHECK 7 values, gate_id/email_delivery_log_id nullable uuid, days_overdue_at_action int NOT NULL, reason varchar(255), created_at/updated_at timestamptz NOT NULL, version int NOT NULL DEFAULT 0); indexes `ux_collection_activity_invoice_stage` UNIQUE `(invoice_id, stage)`, `ix_collection_activity_invoice_status (invoice_id, status)`, `ix_collection_activity_customer_created (customer_id, created_at DESC)`. **Re-verify V133 is next-free by listing the dir first.** Check `ai_executions.invoked_by` nullability — include `ALTER ... DROP NOT NULL` only if currently NOT NULL (Open Questions). Idempotent (`IF NOT EXISTS`); no `tenant_id`, no RLS. Do NOT add speculative indexes on `invoices` (§7 rationale). |
| 588A.2 | Create `CollectionsSettings` embeddable + wire into `OrgSettings` | `settings/CollectionsSettings.java` (new), `settings/OrgSettings.java` (modify) | 588A.5 | follow `settings/TimeReminderSettings.java` verbatim (`@Embeddable`, NOT NULL primitive boolean, atomic domain mutator); `OrgSettings` `@Embedded` + `@AttributeOverride` block + lazy-init getter for `timeReminder` | Fields per §2.1: `boolean collectionsEnabled` (`collections_enabled`, NOT NULL) + `Integer stage1DaysOverdue`/`stage2DaysOverdue`/`stage3DaysOverdue`/`escalateDaysOverdue` (nullable; ctor defaults 7/21/45/60). Add `updateCollectionsSettings(boolean, Integer, Integer, Integer, Integer)` atomic mutator. In `OrgSettings`: `@Embedded` + 5 `@AttributeOverride`s + `getCollections()` lazy-init that never returns null. NOT NULL boolean ⇒ group never reloads as NULL ⇒ **no new `OrgSettingsEmbeddableNullReloadTest` case needed** (state this in the embeddable Javadoc). |
| 588A.3 | Create `CollectionStage` + `CollectionActivityStatus` enums | `collections/CollectionStage.java`, `collections/CollectionActivityStatus.java` | 588A.6 | plain enums like `invoice/InvoiceStatus.java` | `CollectionStage`: `STAGE_1, STAGE_2, STAGE_3, ESCALATION`. `CollectionActivityStatus`: `PROPOSED, SENT, SEND_FAILED, REJECTED, CANCELLED_PAYMENT, SKIPPED, FLAGGED`. Javadoc the state machine + retryable-vs-terminal semantics from §2.2 (SKIPPED/SEND_FAILED retryable; REJECTED/SENT/CANCELLED_PAYMENT/FLAGGED terminal). |
| 588A.4 | Create `CollectionActivity` entity + repository | `collections/CollectionActivity.java`, `collections/CollectionActivityRepository.java` | 588A.6 | follow `correspondence/Correspondence.java` verbatim in style (UUID pk `GenerationType.UUID`, raw-UUID FKs, explicit snake_case columns, `@PrePersist`/`@PreUpdate`, `@Version int`, no Lombok, protected no-arg + full-arg ctor); repo JPQL per §8.3 (`Repository` iface, `findOneById` JPQL not `findById`) | Fields per §2.2: `invoiceId`/`customerId` NOT NULL, `stage`/`status` `@Enumerated(STRING)` len 20, `gateId`/`emailDeliveryLogId` nullable, `int daysOverdueAtAction` NOT NULL, `reason` len 255, timestamps, version. Domain mutators for the transitions (e.g. `markProposed(gateId, daysOverdue)`, `markSent(deliveryLogId)`, `markSkipped(reason)`, `markCancelled(reason)`, `markRejected()`) — transitions in place, one row per (invoice, stage) forever. Repository: `findOneById`, `findByInvoiceIdAndStage(UUID, CollectionStage)`, `findByInvoiceIdAndStatus(UUID, CollectionActivityStatus)`, `findByInvoiceId(UUID)`, paged `findByCustomerId(UUID, Pageable)` ORDER BY createdAt DESC. |
| 588A.5 | Update `OrgSettingsSchemaSnapshotTest` pin | `backend/src/test/java/.../settings/OrgSettingsSchemaSnapshotTest.java` (modify) | itself | the Phase 81/Wave-3.5 deliberate pin-update precedent | **Deliberate** pin update for exactly the 5 new `collections_*` columns (names, types, nullability). This is the intended-change ritual, not a papering-over — note V133 in the pin comment. |
| 588A.6 | Add `Customer.collectionsExempt` + entity/repo round-trip tests | `customer/Customer.java` (modify), `backend/src/test/java/.../collections/CollectionActivityRepositoryTest.java` (new) | ~6 tests: V133 runs clean; `CollectionActivity` persist + `findOneById` round-trip (all fields incl. enums); UNIQUE `(invoice_id, stage)` violation on duplicate insert; `findByInvoiceIdAndStatus` + paged `findByCustomerId`; `CollectionsSettings` defaults + `getCollections()` never-null on fresh row; `collectionsExempt` persists + defaults false | `Customer` boolean field pattern (existing NOT NULL boolean columns); repo-test setup per phase-80/81 convention (`@ActiveProfiles("test")`, embedded Postgres, bind `RequestScopes.TENANT_ID`, **no testcontainers**) | `@Column(name = "collections_exempt", nullable = false)` primitive `boolean` + getter/setter on `Customer`. Keep entity-layer only in this slice — the exemption endpoint is 588B.6. |
| 588B.1 | Register 5 `collections.*` audit types + count bump | `audit/AuditEventTypeRegistry.java` (modify) | 588B.5; existing catalogue-count assertion updated 36→41 | Phase 81 `mcp.write.*` registration block (§9j of `.arch-context.md`) | Add `collections.reminder.sent`, `collections.reminder.cancelled`, `collections.escalation.flagged`, `collections.digest.sent`, `collections.policy.updated` (`AuditSeverity.NOTICE`, `AuditEventGroup.STANDARD`; consider COMPLIANCE for `escalation.flagged` only if a sibling precedent supports it — default STANDARD). Gate propose/approve/reject stay covered by existing `ai.gate.*` — do NOT re-register. **Update the catalogue-count assertion 36→41 deliberately.** |
| 588B.2 | Add `ActivityMessageFormatter` arms + entity resolver | `activity/ActivityMessageFormatter.java` (modify) | covered by 588B.5, 589A, 590B, 593B tests asserting audit rows format | existing `mcp.write.*` formatter arms + entity-name resolver switch | Human message per the 5 types (e.g. `"%s approved a collection reminder"` shape); entity display case `"collection_activity"` — **lowercase snake_case free string on the audit plane** (casing rule, PR #1503; never the UPPERCASE EntityTag plane, never an `EntityType` enum value). Details carry ids/numbers only — no client PII (POPIA). |
| 588B.3 | Register notification types | `notification/NotificationService.java` (modify) | 589A escalation test, 593B digest test | existing `NOTIFICATION_TYPES` static list (§9k of `.arch-context.md`) | Append `COLLECTION_ESCALATED` and `CASH_DIGEST` to `NOTIFICATION_TYPES` (drives the preferences UI so members can mute). Emission call sites land in 589A (escalation, `notifyAdminsAndOwners`) and 593B (digest, `createIfEnabled` per owner/admin) — registration only here. |
| 588B.4 | Settings API — GET/PUT `/api/settings/collections` | `settings/OrgSettingsService.java` (modify), `settings/OrgSettingsController.java` (modify) | 588B.5 | existing time-reminder settings GET/PUT surface on the same controller/service | GET returns the group (member-readable); PUT admin/owner-only (`@RequiresCapability` / role check mirroring the sibling settings PUT). Request per §4.2: `{collectionsEnabled, stage1DaysOverdue, stage2DaysOverdue, stage3DaysOverdue, escalateDaysOverdue}`. **Service-side validation**: each threshold ≥ 1, strictly increasing (stage1 < stage2 < stage3 < escalate) — semantic exception, not DB constraint. Audit `collections.policy.updated` with old/new numbers in details (numbers only, no PII). Controller stays a pure one-line delegate (backend/CLAUDE.md controller discipline). DTOs as nested records in the controller. |
| 588B.5 | Settings API tests | `backend/src/test/java/.../settings/CollectionsSettingsApiTest.java` (new) | ~6 tests: GET defaults (disabled, 7/21/45/60); PUT round-trip persists all 5; non-increasing thresholds rejected 400; threshold < 1 rejected; member (non-admin) PUT forbidden; `collections.policy.updated` audit row written with old/new values | sibling settings API tests; `TestJwtFactory.ownerJwt/memberJwt`, `TestMemberHelper` (never private jwt/sync helpers — backend/CLAUDE.md) | MockMvc-driven; assert audit via the audit repository/service, not log scraping. |
| 588B.6 | Exemption endpoint — PUT `/api/customers/{id}/collections-exemption` | `customer/CustomerService.java` (modify), `customer/CustomerController.java` (modify), `backend/src/test/java/.../customer/CustomerCollectionsExemptionTest.java` (new) | ~4 tests: owner/admin sets + clears flag (persisted); member forbidden; unknown customer → 404 (`ResourceNotFoundException`) | existing customer PUT sub-resource endpoints on `CustomerController` | Body `{"collectionsExempt": true|false}`. Admin/owner only per §9. One service method (`setCollectionsExemption(customerId, boolean, actor)`); controller one-liner. No audit type is specified for exemption in the architecture — do not invent one (the flag change is visible on the customer row; if review demands audit, raise it, don't self-add a 6th type and break the 41 pin). |
| 588C.1 | Settings page — collections policy card | `frontend/app/(app)/org/[slug]/settings/collections/page.tsx` (new), `.../settings/collections/actions.ts` (new), `frontend/components/settings/collections-settings-form.tsx` (new, or colocated client component) | 588C.4 | mirror an existing settings page pair, e.g. `settings/time-tracking/` (`page.tsx` RSC fetch + `actions.ts` server action + Shadcn form card) | Enable switch + 4 numeric threshold inputs; client-side increasing-order validation mirroring the server rule; admin/owner-only edit (mirror how sibling settings pages gate the form). Next.js 16: `params` are Promises — `await params`. Add the settings-hub entry if the hub enumerates pages statically. |
| 588C.2 | Customer exemption toggle | `frontend/app/(app)/org/[slug]/customers/[id]/` (modify page or its client component) + customer actions file (modify) | 588C.4 | existing admin/owner-gated toggles on the customer detail page | "Exclude from collections" switch wired to `PUT /api/customers/{id}/collections-exemption`; visible read-only state for members. Keep the chase-history section OUT — that is 591C. |
| 588C.3 | API client | `frontend/lib/api/collections.ts` (new) | 588C.4 | `frontend/lib/api/ai.ts` client shape (typed functions over `lib/api.ts` fetcher) | `getCollectionsSettings()`, `updateCollectionsSettings(dto)`, `setCollectionsExemption(customerId, exempt)`. 591B extends this same file with debtors/activities clients — keep section comments so the extension is additive. |
| 588C.4 | Frontend tests + gates | `frontend/__tests__/` (1–2 new test files) | form renders defaults; increasing-order validation blocks submit; exemption toggle calls the action | existing settings-form vitest patterns | `pnpm lint && pnpm build && pnpm test` + **`format:check`** (prettier is a separate CI step — frontend verify must include it). |

### Key Files

**Create (backend):** `db/migration/tenant/V133__create_collections_tables.sql`; `settings/CollectionsSettings.java`; `collections/CollectionActivity.java`, `CollectionStage.java`, `CollectionActivityStatus.java`, `CollectionActivityRepository.java`; tests `collections/CollectionActivityRepositoryTest.java`, `settings/CollectionsSettingsApiTest.java`, `customer/CustomerCollectionsExemptionTest.java`.
**Modify (backend):** `settings/OrgSettings.java`, `OrgSettingsService.java`, `OrgSettingsController.java`; `customer/Customer.java`, `CustomerService.java`, `CustomerController.java`; `audit/AuditEventTypeRegistry.java` (count 36→41); `activity/ActivityMessageFormatter.java`; `notification/NotificationService.java`; `settings/OrgSettingsSchemaSnapshotTest.java` (deliberate pin).
**Create (frontend):** `app/(app)/org/[slug]/settings/collections/page.tsx` + `actions.ts`; `lib/api/collections.ts`; tests.
**Modify (frontend):** `app/(app)/org/[slug]/customers/[id]/` detail (exemption toggle).
**Read for context:** `settings/TimeReminderSettings.java` (embeddable pattern); `correspondence/Correspondence.java` (entity pattern); `settings/OrgSettingsSchemaSnapshotTest.java`.

### Architecture Decisions
- **Policy as embeddable, not entity; exemption as a `Customer` column, not a settings-side list** ([ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md)) — single per-tenant value object; the exemption flag is one predicate in the scan's join.
- **Stateful `(invoice, stage)` ledger with a UNIQUE index as the idempotency backbone** ([ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md)) — transitions in place; the append-only trail lives in the audit plane.
- **No `OVERDUE` status, no `Invoice` column changes, no new `DomainEvent` records** ([ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md)).

### Non-scope
- No scan/handler (589A). No listener (589B). No AI/gate/send (590). No debtors read APIs or queue UI (591). No triage (592). No digest (593).

---

## Epic 589: Scan Engine + Escalation + Payment Cancellation

**Goal**: Ship the deterministic dunning engine, fully integration-testable without any AI machinery. `CollectionsScanService` derives overdue candidates at query time, selects **the highest eligible stage per invoice per scan** (recording lower un-actioned stages as `SKIPPED(superseded_by_higher_stage)` so the ledger stays complete), flags `ESCALATION` deterministically (row + `COLLECTION_ESCALATED` notification + audit — **no gate, no AI, no email**), and delegates drafting to the `ReminderComposer` seam whose 589 default (`NoOpReminderComposer`) records `SKIPPED(draft_unavailable)` — retryable, so every activity re-proposes automatically once 590A's real composer deploys. `CollectionsPaymentListener` closes the safety loop: payment/void on any route expires pending reminder gates and cancels activities; gate reject/expiry events transition activities to their terminal/retryable states.

**References**: Architecture §3.1 (scan steps + candidate SQL + `ReminderComposer` seam), §3.3 (payment cancellation, all three routes), §5.2 (race sequence), §8.1, §8.4 (test table rows 1–3, 6–7), §10 Slice 83-2; [ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md), [ADR-326](../adr/ADR-326-gated-send-safety-model.md).

**Dependencies**: 588A (entity/repo/settings), 588B (audit types, `COLLECTION_ESCALATED` registration).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **589A** | 589A.1–589A.5 | ~9 backend files (seam iface + no-op + scan service + handler + enqueue-registration mod + 3–4 test files) | `ReminderComposer` + `NoOpReminderComposer`; `CollectionsScanService` (candidate query, stage selection, supersede/skip, escalation); `CollectionsScanHandler` + daily recurring enqueue. |
| **589B** | 589B.1–589B.3 | ~5 backend files (1 listener + 2–3 test files) | `CollectionsPaymentListener` (paid/void → cancel; reject → REJECTED; expiry → SKIPPED(gate_expired)); all-three-routes cancellation tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 589A.1 | Create `ReminderComposer` seam + `NoOpReminderComposer` | `collections/ReminderComposer.java`, `collections/NoOpReminderComposer.java` | 589A.4 | §3.1 interface verbatim: `Optional<AiExecutionGate> compose(CollectionActivity, Invoice, Customer)` | No-op returns `Optional.empty()` → scan records `SKIPPED(reason=draft_unavailable)` (retryable). Register the no-op so 590A's `AiReminderComposer` cleanly supersedes it (`@ConditionalOnMissingBean`-style config or `@Primary` on the AI bean — decide here, document in Javadoc; see Open Questions). |
| 589A.2 | Create `CollectionsScanService` | `collections/CollectionsScanService.java` | 589A.4 | native candidate query per §3.1 SQL (EntityManager native query like `reporting/InvoiceAgingReportQuery`); `@Transactional` service shape | Steps 1–6 of §3.1: (1) `orgSettingsService.get().getCollections()` — no-op if `!collectionsEnabled`; (2) candidate query `status='SENT' AND due_date < CURRENT_DATE AND c.collections_exempt=false` (join `customers`; **exempt customers produce NO rows at all**, not SKIPPED); (3) highest eligible stage with no non-retryable activity; lower un-actioned stages → `SKIPPED(superseded_by_higher_stage)`; (4) escalation: `days_overdue >= escalateDaysOverdue` and no ESCALATION row → `FLAGGED` row + `NotificationService.notifyAdminsAndOwners(COLLECTION_ESCALATED, …)` + `collections.escalation.flagged` audit — no gate/AI/email; (5) blank `customer_email` → `SKIPPED(no_recipient)` retryable; (6) delegate to `ReminderComposer` — present gate → activity `PROPOSED` + gateId; empty → `SKIPPED(draft_unavailable)`; composer exception → `SKIPPED(draft_failed)`, batch continues. Returns `ScanResult(proposed, skipped, escalated, superseded)` record for the job log. Re-proposal path: retryable `SKIPPED`/`SEND_FAILED` rows transition back to `PROPOSED` in place (same row — UNIQUE index untouched). `daysOverdueAtAction` snapshot on every (re-)proposal/flag. |
| 589A.3 | Create `CollectionsScanHandler` + recurring enqueue | `collections/CollectionsScanHandler.java` (new) + the recurring-enqueue registration site (modify — resolve at build, see Open Questions) | 589A.4 drives the service directly; one handler test asserts jobType + delegation | `PortalDigestHandler`/`AccountingPaymentPollHandler` thin-handler shape (jobType snake_case; tenant scope pre-bound by `JobWorker`; payload `com.fasterxml.jackson.databind.JsonNode` — Jackson 2 in the job subsystem) | `jobType() = "collections_scan"`, daily cadence via the same per-tenant fanout that enqueues `portal_digest`/`accounting_payment_poll`. Handler is a 5-line delegate to `scanForTenant()`. Do NOT use raw `@Scheduled`. |
| 589A.4 | Scan correctness + idempotency + escalation tests | `backend/src/test/java/.../collections/CollectionsScanServiceTest.java`, `CollectionsScanIdempotencyTest.java`, `CollectionsEscalationTest.java` | §8.4 rows 1–3: seeded overdue invoices → correct stage per threshold; invoice first seen at 50d → ONE stage-3 activity + stage-1/2 SKIPPED(superseded); exemption → zero rows; blank email → SKIPPED(no_recipient), retried next scan after email added; no-op composer → SKIPPED(draft_unavailable); disabled policy → scan no-ops; **re-run same day → zero new activities/gates** (UNIQUE index + status filter); escalation row created once (idempotent re-scan), `COLLECTION_ESCALATED` to admins/owners only, `collections.escalation.flagged` audit present | phase-80/81 integration-test setup; `TestEntityHelper`/`TestCustomerFactory` (ACTIVE customers need explicit transition); seed invoices via the invoice service/API path, not raw SQL | Freeze "today" via seeded due dates relative to `LocalDate.now()`. Assert notification recipients exclude plain members. |
| 589A.5 | Handler registration test | folded into 589A.4 files | `JobHandlerRegistry` resolves `collections_scan`; duplicate-jobType fail-fast untouched | existing handler-registry tests | Keep tiny; the heavy behaviour tests drive the service directly. |
| 589B.1 | Create `CollectionsPaymentListener` | `collections/CollectionsPaymentListener.java` | 589B.2/.3 | `invoice/InvoiceEmailEventListener` (`@TransactionalEventListener(phase = AFTER_COMMIT)` on DomainEvents); `AiGateRejectedEvent`/`AiGateExpiredEvent` listener shape in `integration/ai/gate/` | On `InvoicePaidEvent`/`InvoiceVoidedEvent`: `findByInvoiceIdAndStatus(invoiceId, PROPOSED)` → for each, expire the PENDING gate via `AiExecutionGateService` (review note `invoice_paid`) → activity `CANCELLED_PAYMENT(reason=invoice_paid|invoice_voided)` → `collections.reminder.cancelled` audit. On `AiGateRejectedEvent` (gateType `SEND_COLLECTION_REMINDER` only): activity → `REJECTED` (terminal for the stage). On `AiGateExpiredEvent`: activity → `SKIPPED(gate_expired)` (retryable; **gateId retained** per §2.2 so the last draft stays traceable). Race safety: only expire PENDING gates; activity `@Version` + terminal-state no-ops make either commit order deterministic (§5.2). `InvoicePaymentReversedEvent` deliberately unhandled (§3.3 — next scan resumes at the next un-actioned stage). |
| 589B.2 | All-three-routes cancellation test | `backend/src/test/java/.../collections/CollectionsPaymentCancellationTest.java` | §8.4 row 6: (a) PSP webhook via `PaymentReconciliationService.processWebhookResult(COMPLETED)`, (b) Xero pull via `AccountingPaymentPollWorker.pollForTenant()` (or its recordPayment call path), (c) manual `InvoiceService.recordPayment` — each: pending gate → EXPIRED, activity → CANCELLED_PAYMENT, audit row; plus void → CANCELLED_PAYMENT(invoice_voided); approve-after-pay refused (gate not PENDING) | existing reconciliation/poll-worker test setups | Seed activities `PROPOSED` with manually-created PENDING `SEND_COLLECTION_REMINDER` gates (gate machinery exists; no skill needed — construct `AiExecutionGate` directly with a synthetic execution as in Phase 81 tests). AFTER_COMMIT ⇒ use real transactions, not `@Transactional` test rollback, for the listener legs. |
| 589B.3 | Gate reject/expiry transition test | `backend/src/test/java/.../collections/GateLifecycleTransitionsTest.java` | §8.4 row 7: reject → activity REJECTED; next scan does NOT re-propose that stage (invoice progresses to next threshold when passed); expiry → SKIPPED(gate_expired) then re-proposed by the next scan run (same row, fresh gate) | `AiGateExpiryHandler` existing expiry flow | Drives scan + listener together — the first place the retryable loop is proven end-to-end. |

### Key Files

**Create (backend):** `collections/ReminderComposer.java`, `NoOpReminderComposer.java`, `CollectionsScanService.java`, `CollectionsScanHandler.java`, `CollectionsPaymentListener.java`; tests `CollectionsScanServiceTest.java`, `CollectionsScanIdempotencyTest.java`, `CollectionsEscalationTest.java`, `CollectionsPaymentCancellationTest.java`, `GateLifecycleTransitionsTest.java`.
**Modify (backend):** recurring-enqueue registration site (resolve at build alongside `portal_digest`).
**Read for context:** `integration/accounting/sync/AccountingPaymentPollHandler.java` (handler shape); `invoice/InvoiceEmailEventListener.java` (AFTER_COMMIT listener); `invoice/PaymentReconciliationService.java` + `InvoiceService.recordPayment` (the three routes); `reporting/InvoiceAgingReportQuery.java` (native-query shape); `integration/ai/gate/AiExecutionGateService.java` (expiry API).

### Architecture Decisions
- **Scan-on-job-queue with derived overdue; at most one reminder per invoice per scan; complete ledger via supersede rows** ([ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md)).
- **One AFTER_COMMIT listener covers all payment routes; zero new domain events** ([ADR-325](../adr/ADR-325-collections-domain-dunning-engine.md)) — any route that marks paid already publishes `InvoicePaidEvent`.
- **Deterministic ungated escalation** ([ADR-326](../adr/ADR-326-gated-send-safety-model.md)) — gates protect client-facing AI output; flagging a partner faces no client.
- **Terminal per-stage rejection; retryable SKIPPED/SEND_FAILED** ([ADR-326](../adr/ADR-326-gated-send-safety-model.md)) — a human "no" is respected per stage; "no email ever left" is safe to re-propose.
- **`ReminderComposer` seam** ([ADR-327](../adr/ADR-327-ai-reminder-drafting-debtor-triage.md)) — scan arithmetic fully testable before AI lands; `SKIPPED(draft_unavailable)` self-heals on composer deploy.

### Non-scope
- No AI skill/composer (590A). No `GateAction`/executor/send/template (590B). No batch approve or read APIs (591). No triage (592). No digest (593).

---

## Epic 590: Drafting Skill + Gated Send Executor

**Goal**: Put the AI layer on the engine and open the **only** send path. 590A ships the `collection-reminder` `AiSkill` (firm-profile system prompt + stage-tone blocks; deterministic user prompt from invoice facts, relationship context, chase history; schema-validated `{subject, body_html, body_text, reasoning}` output; `createGates` → one PENDING `SEND_COLLECTION_REMINDER` gate, 72 h) and the `AiReminderComposer` that invokes it **system-invoked from job context** (`invokedBy` null), plus `StubAiProvider` parity so the full loop runs in `./mvnw verify` with zero live tokens. 590B adds the sealed `SendCollectionReminderAction`, the executor branch, and `CollectionReminderSendService` mirroring `InvoiceEmailService` step for step over the new `collection-reminder` Thymeleaf template — **frame owns facts** (invoice table + payment CTA template-rendered; AI owns only subject + letter paragraphs). Includes the mandatory tenant-isolation test.

**References**: Architecture §3.2 (send-on-approve, template/draft split, rejection/expiry), §5.1 (sequence), §6.1 (skill), §6.3–§6.4 (metering, system invocation), §6.6 (stub parity), §8.4 (test rows 4, 7, 11), §10 Slice 83-3; [ADR-326](../adr/ADR-326-gated-send-safety-model.md), [ADR-327](../adr/ADR-327-ai-reminder-drafting-debtor-triage.md).

**Dependencies**: 589A (composer seam + scan), 589B (reject/expiry transitions already listening).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **590A** | 590A.1–590A.4 | ~8 backend files (1 skill + 2 prompt assets + 1 composer + 1 output record + StubAiProvider mod + possible AiSkillExecutionService mod + 1–2 test files) | `CollectionReminderSkill` + prompts/schema; `AiReminderComposer` (system-invoked); stub outputs; draft-failure skip semantics. |
| **590B** | 590B.1–590B.5 | ~8 backend files (GateAction mod + executor mod + 1 send service + 1 template + 3 test files) | `SendCollectionReminderAction` + parse/execute arms; `CollectionReminderSendService` + `collection-reminder.html`; executor + tenant-isolation tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 590A.1 | Create `CollectionReminderSkill` + prompt assets | `integration/ai/skill/collections/CollectionReminderSkill.java`, `backend/src/main/resources/ai/skills/collection-reminder/system.txt`, `.../output-schema.json`, `integration/ai/skill/collections/CollectionReminderOutput.java` (record) | 590A.4 | follow `integration/ai/skill/fica/FicaVerificationSkill.java` verbatim (classpath-resource loading, `LlmJsonParser.parse`, `tools.jackson.databind.ObjectMapper` — Jackson 3 in the skill subsystem, NOT `com.fasterxml`) | `skillId() = "collection-reminder"`; registered by existence (`@Component` — `AiSkillExecutionService` collects all `AiSkill` beans). System prompt: `{firm_profile_block}` (via `AiFirmProfileService.assembleProfileBlock()`) + stage-tone block selected by `{stage}` (STAGE_1 friendly / STAGE_2 firm / STAGE_3 final-notice, explicitly short of a formal letter of demand) + `{output_schema}`. User prompt assembled deterministically from `SkillContext` (entityType `collection_activity`, entityId = activityId): invoice facts, relationship context (customer since, lifetime billed, median days-to-pay), chase history from `CollectionActivity`, advisor annotations as acknowledgeable context only (592A wires them; tolerate absence). Output `{subject, body_html, body_text, reasoning}`; `createGates` → ONE `new AiExecutionGate(execution, "SEND_COLLECTION_REMINDER", proposedAction, reasoning, now+72h)` with **snake_case JSONB keys** (`collection_activity_id`, `invoice_id`, `customer_id`, `stage`, `subject`, `body_html`, `body_text`) matching `parseAction` convention. `requiresVision() = false`. |
| 590A.2 | Create `AiReminderComposer` + system invocation | `collections/AiReminderComposer.java` (new; supersedes `NoOpReminderComposer` per the 589A.1 mechanism), possible `integration/ai/skill/AiSkillExecutionService.java` (modify, only if system invocation needs support) | 590A.4 | `AiSkillExecutionService` invocation path (§6.4); job-context ScopedValue binding already done by `JobWorker` | Pre-flight AI-enablement check (provider configured + firm profile present) — on failure return `Optional.empty()` with a distinct signal so the scan records `SKIPPED(ai_unavailable)` (extend the scan's reason mapping if 589A only knew `draft_unavailable`). Invoke the skill with `invokedBy = null` (system) — **verify `ai_executions.invoked_by` nullability outcome from 588A.1** and that the execution service tolerates a null member; if it doesn't, add the minimal system-invocation overload here (flag in PR if the change exceeds a small overload — scope discipline). Provider failure → propagate as compose failure → scan `SKIPPED(draft_failed)`; one bad draft must not sink the tenant's scan. Cost metering rides `AiExecution` per reminder unchanged. |
| 590A.3 | StubAiProvider canned output | `integration/ai/StubAiProvider` (modify — e2e/test stub, exact path per existing stub location) | 590A.4, 590B.4, 594A | Phase 74 stub-parity precedent (canned per-skillId outputs) | Deterministic schema-valid JSON for `collection-reminder`: subject/body incorporating the input invoice number, so tests can assert the draft references the right invoice. (Add the `cash-digest` canned output in 593B, not here.) |
| 590A.4 | Skill + composer tests | `backend/src/test/java/.../collections/AiReminderComposerTest.java` (+ optional `CollectionReminderSkillTest.java` for prompt assembly) | ~6 tests: scan with AI composer + stub → activity PROPOSED with PENDING `SEND_COLLECTION_REMINDER` gate, 72 h expiry, snake_case proposed_action carrying activity/invoice ids + drafted subject/body; stage-tone block selected per stage; AI unavailable → `SKIPPED(ai_unavailable)` retryable; provider exception → `SKIPPED(draft_failed)`, siblings in batch still proposed; execution row metered with null invokedBy | 589A test harness + stub provider profile | This is where `NoOpReminderComposer` stops being the resolved bean — assert the AI composer wins the injection. |
| 590B.1 | Add `SendCollectionReminderAction` + parse/execute arms | `integration/ai/gate/GateAction.java` (modify), `integration/ai/gate/GateActionExecutor.java` (modify) | 590B.4 | the standard "adding a gate type" checklist (`.arch-context.md` §9c): permits-list + record; `parseAction` case `"SEND_COLLECTION_REMINDER"`; exhaustive-switch case + `executeSendCollectionReminder` | Record per §3.2: `(UUID collectionActivityId, UUID invoiceId, UUID customerId, String stage, String subject, String bodyHtml, String bodyText)`. `parseAction` reads snake_case JSONB keys; malformed data normalizes to `IllegalStateException` like siblings. Executor method delegates to `CollectionReminderSendService` (inject it) — keep the executor arm thin. |
| 590B.2 | Create `CollectionReminderSendService` | `collections/CollectionReminderSendService.java` | 590B.4 | **mirror `invoice/InvoiceEmailService.java` step for step** (provider resolve → `EmailContextBuilder.buildBaseContext` → render → `EmailRateLimiter.tryAcquire` → `EmailMessage.withTracking` → send → `EmailDeliveryLogService.record`) | Template `collection-reminder`, `referenceType = "COLLECTION_REMINDER"`, `referenceId = activityId`. Context: customerName, invoiceNumber, amount, currency, dueDate, daysOverdue, AI subject/body, `paymentUrl` + always-populated `portalUrl` fallback (GAP-L-64 precedent), vertical-aware `invoiceTerm`. On rate limit: `recordRateLimited` + activity `SKIPPED(rate_limited)` (retryable, **gateId retained**). On provider success: activity `SENT` + `emailDeliveryLogId` + `collections.reminder.sent` audit; on failure: `SEND_FAILED` (scan re-proposes with a fresh draft — email never left). No attachment in v1 (no statement generation — out of scope). |
| 590B.3 | Create `collection-reminder.html` template | `backend/src/main/resources/templates/email/collection-reminder.html` (+ plain-text variant if the renderer convention requires one) | 590B.4 renders it | `templates/email/invoice-delivery.html` (branding frame, facts table, payment CTA with portalUrl fallback, footer) | **Frame owns facts** (§3.2 / ADR-327): invoice number/amount/due-date table and the payment CTA are template-rendered; the AI contributes only the letter paragraphs + subject. The approver reviews exactly the human-language part; amounts/links cannot be hallucinated. |
| 590B.4 | Executor + send-path tests | `backend/src/test/java/.../collections/SendCollectionReminderExecutorTest.java`, `BatchlessApproveLoopTest` content folded in | §8.4 row 4: approve gate → GreenMail-observed email (shared port **13025** — never a new port) containing payment CTA + drafted subject; `EmailDeliveryLog` row (`COLLECTION_REMINDER`/activityId); activity SENT + `collections.reminder.sent` audit; provider failure → SEND_FAILED, next scan re-proposes; rate-limited → SKIPPED(rate_limited) + `recordRateLimited`; reject/expiry ↔ activity transitions re-asserted end-to-end with real drafted gates | GreenMail singleton convention (backend test-speed invariants); existing gate-approval test flow through `AiExecutionGateService.approve` | Full loop: scan(stub) → gate → approve via service → email observed. This is the phase's core safety proof: no send API exists other than this executor path. |
| 590B.5 | Tenant-isolation test (**mandatory**) | `backend/src/test/java/.../collections/CollectionsTenantIsolationTest.java` | §8.4 row 11: activities, gates, and collections settings written under tenant A are invisible under tenant B's schema (repo lookups + gate queries return empty; settings independent) | Phase 81 `CorrespondenceWriteToolsTenantIsolationTest` shape (bind `RequestScopes.TENANT_ID` per tenant) | Mandatory per requirements. Covers the whole domain built so far; 591A's controllers add nothing schema-level, so this lands here where all row types exist. |

### Key Files

**Create (backend):** `integration/ai/skill/collections/CollectionReminderSkill.java` + `CollectionReminderOutput.java`; `ai/skills/collection-reminder/system.txt` + `output-schema.json`; `collections/AiReminderComposer.java`, `CollectionReminderSendService.java`; `templates/email/collection-reminder.html`; tests `AiReminderComposerTest.java`, `SendCollectionReminderExecutorTest.java`, `CollectionsTenantIsolationTest.java`.
**Modify (backend):** `integration/ai/gate/GateAction.java`, `GateActionExecutor.java`; `integration/ai/StubAiProvider`; possibly `integration/ai/skill/AiSkillExecutionService.java` (system invocation only if needed).
**Read for context:** `integration/ai/skill/fica/FicaVerificationSkill.java` (skill shape); `invoice/InvoiceEmailService.java` (send mirror); `templates/email/invoice-delivery.html` (template frame); `integration/ai/gate/AiExecutionGateService.java` (approve path).

### Architecture Decisions
- **Executor-only sending; no direct send endpoint by construction** ([ADR-326](../adr/ADR-326-gated-send-safety-model.md)) — the only code path that emails a client is `GateActionExecutor.executeSendCollectionReminder`.
- **Frame-owns-facts drafting** ([ADR-327](../adr/ADR-327-ai-reminder-drafting-debtor-triage.md)) — a wrong amount or broken payment link is worse than a clumsy sentence; facts stay template-rendered.
- **Scan-time drafting, system-invoked, metered per reminder** ([ADR-327](../adr/ADR-327-ai-reminder-drafting-debtor-triage.md)) — `invokedBy` null; volume bounded by (invoice, stage) idempotency.
- **72 h expiry with retryable re-proposal** ([ADR-326](../adr/ADR-326-gated-send-safety-model.md)) — a stale draft regenerates with current days-overdue context.

### Non-scope
- No batch-approve endpoint or read APIs (591A). No frontend (591B/C). No triage/advisor wiring beyond tolerating absent advice (592A). No digest (593).

---
## Epic 591: Batch Approval + Collections Read APIs + Collections Frontend

**Goal**: Make the engine operable. 591A extends the **existing** gate surface with `POST /api/ai/gates/batch-approve` (per-gate transactions, per-gate dispositions, 200-always — one paid-in-the-meantime invoice must not block nine valid sends) and ships the `CollectionsController` read APIs: the paged debtor book (per-customer outstanding, oldest days overdue, aging split, signals, last activity), per-customer drill-in, and the per-invoice activity ledger. 591B ships the debtors page with the pending-reminder queue (multi-select, per-item `ExecutionGateCard` preview, batch approve). 591C surfaces chase history on the invoice and customer detail pages.

**References**: Architecture §4.1 (read APIs + JSON shape), §4.3 (batch endpoint contract), §5.1 (sequence), §8.1/§8.2 (change tables), §9 (permissions), §10 Slice 83-4; [ADR-326](../adr/ADR-326-gated-send-safety-model.md).

**Dependencies**: 590 (gates of type `SEND_COLLECTION_REMINDER` exist and send on approve). 592A optional at build time — 591A wires signals when present, else returns empty arrays.

**Scope**: Backend (591A) + Frontend (591B, 591C) — no slice mixes both.

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **591A** | 591A.1–591A.4 | ~8 backend files (gate controller/service mods + 1 collections controller + 1 read service/query + 2–3 test files) | Batch-approve endpoint; debtors/drill-in/activities read APIs + paged native debtor-book query. |
| **591B** | 591B.1–591B.4 | ~9 frontend files (page + 2–3 client components + actions + 2 lib/api mods + nav mod + 1–2 tests) | Debtors page + pending-reminder queue with multi-select preview + batch approve; nav entry. |
| **591C** | 591C.1–591C.2 | ~5 frontend files (invoice-detail mod + customer-detail mod + 1 shared history component + 1 test) | Invoice activity-ledger section; customer chase-history section. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 591A.1 | Batch-approve endpoint | `integration/ai/gate/AiExecutionGateController.java` (modify), `AiExecutionGateService.java` (modify) | 591A.3 | existing single `approve(id, notes)` path; §4.3 contract verbatim | `POST /api/ai/gates/batch-approve` body `{gateIds: UUID[], notes?}`; `@RequiresCapability(AI_REVIEW)` same as single approve. Service iterates the **existing** single-gate approve path, each gate in its own transaction (`REQUIRES_NEW` or self-injection — follow an existing per-item-tx precedent), collecting `{gateId, outcome: APPROVED_EXECUTED|FAILED, error?}`. 200 always; dispositions carry per-gate failure. Generic over gate types — no collections coupling in the gate package. Cap batch size (e.g. 100) with a 400 above it. |
| 591A.2 | Collections read APIs + debtor-book query | `collections/CollectionsController.java` (new), `collections/CollectionsReadService.java` (new; holds the native queries) | 591A.3 | `reporting/InvoiceAgingReportQuery` native-query + paging shape; §4.1 response JSON verbatim | `GET /api/collections/debtors` (paged): per-customer aggregation over outstanding `SENT` invoices — outstandingTotal, currency, invoiceCount, oldestDaysOverdue, bucket split (reuse the §7-noted bucket boundaries; full helper extraction happens in 593A — keep the SQL here self-contained but structured for extraction), `collectionsExempt`, lastActivity from the ledger, `signals` from `CollectionsTriageService` **if the bean exists** (constructor `ObjectProvider`/optional injection so 591A builds before 592A) else `[]`. `GET /api/collections/debtors/{customerId}`: outstanding invoices + paged activities. `GET /api/collections/activities?invoiceId=`: the per-invoice ledger. Auth: mirror the invoice-list guard exactly (Open Questions — copy, don't invent). Controllers pure delegation. |
| 591A.3 | Read API + batch tests | `backend/src/test/java/.../collections/CollectionsReadApiTest.java`, `.../integration/ai/gate/BatchApproveTest.java` | §8.4 row 5 + read coverage: mixed batch (2 PENDING + 1 EXPIRED) → 2 sent (GreenMail-observed), dispositions correct, siblings unaffected; non-`AI_REVIEW` member → 403; debtors aggregation correct against seeded book (totals, oldest days, buckets, exempt flag surfaces); drill-in + activities paging; member-without-invoice-access → 403/404 per the mirrored guard | 590B.4 harness (stub + GreenMail); MockMvc | Batch test seeds gates via the scan+stub path from 590, not hand-rolled JSON. |
| 591A.4 | Wire `SKIPPED(rate_limited)`/`SEND_FAILED` visibility | covered in 591A.2 DTOs | 591A.3 asserts they surface in activity DTOs | §2.2 status table | Debtor/activity DTOs expose status + reason verbatim so the UI can show "send failed / rate limited" rows needing attention. No new endpoint. |
| 591B.1 | Debtors page + queue shell | `frontend/app/(app)/org/[slug]/invoices/collections/page.tsx` (new), `.../collections/collections-client.tsx` (new), `.../collections/actions.ts` (new) | 591B.4 | `ai/reviews/page.tsx` (RSC capability guard + gate fetch), Shadcn table patterns from the invoices list | RSC: guard mirrors `ai/reviews` (`fetchMyCapabilities()`); fetch debtors page 1 + pending gates (`getAiGates({gateType: 'SEND_COLLECTION_REMINDER', status: 'PENDING'})`). Two sections: debtor book table (outstanding, oldest overdue, buckets, badges placeholder, last activity) and pending-reminder queue. Next.js 16: `await params`. |
| 591B.2 | Queue multi-select + batch approve + preview | `.../collections/reminder-queue.tsx` (new client component) | 591B.4 | `components/ai/execution-gate-card.tsx` (per-item preview reuse) | Checkbox multi-select; per-item expand renders `ExecutionGateCard` (drafted subject/body visible pre-approval — the review must review the real thing); "Approve selected (N)" → server action → `batchApproveAiGates`; render per-gate dispositions (approved ✓ / failed with reason) without dropping the rest of the queue; single reject stays per-card via existing action. |
| 591B.3 | API clients + nav | `frontend/lib/api/ai.ts` (modify), `frontend/lib/api/collections.ts` (modify — extend 588C.3 file), nav config (modify — wherever `invoices/` siblings register) | 591B.4 | `lib/api/ai.ts` existing gate functions | `batchApproveAiGates(gateIds, notes?)`; `getDebtors(params)`, `getDebtorDetail(customerId)`, `getInvoiceActivities(invoiceId)`. Nav: "Collections" under the invoices/financial group, capability-gated like siblings. |
| 591B.4 | Frontend tests + gates | `frontend/__tests__/` (1–2 files) | queue multi-select state; batch action called with selected ids; dispositions rendered; debtor table renders API shape | existing vitest + testing-library patterns | `pnpm lint && pnpm build && pnpm test` + `format:check`. |
| 591C.1 | Invoice-detail activity section | invoice detail page/components (modify) | 591C.2 | existing invoice-detail tab/section composition | Read-only ledger for the invoice: stage, status (+reason), days-overdue-at-action, timestamps, link to the gate where PROPOSED. Uses `getInvoiceActivities`. |
| 591C.2 | Customer chase-history section + tests | customer detail (modify; beside 588C.2's toggle), shared `collections-history-table.tsx` (new), 1 test file | history table renders statuses/reasons; pagination wired | 588C.2 placement; shared-component conventions | Same table component serves both surfaces — build once here, import in 591C.1. |

### Key Files

**Create (backend):** `collections/CollectionsController.java`, `CollectionsReadService.java`; tests `CollectionsReadApiTest.java`, `BatchApproveTest.java`.
**Modify (backend):** `integration/ai/gate/AiExecutionGateController.java`, `AiExecutionGateService.java`.
**Create (frontend):** `app/(app)/org/[slug]/invoices/collections/` (page, client components, actions), `components/.../collections-history-table.tsx`.
**Modify (frontend):** `lib/api/ai.ts`, `lib/api/collections.ts`, nav config, invoice + customer detail pages.
**Read for context:** `ai/reviews/` page trio (guard + gate list + actions); `components/ai/execution-gate-card.tsx`; invoices list page (table + guard to mirror).

### Architecture Decisions
- **Batch approval extends the gate controller, not a collections endpoint** ([ADR-326](../adr/ADR-326-gated-send-safety-model.md)) — one approval surface; capability check and audit stay in the gate machinery.
- **200-with-dispositions** ([ADR-326](../adr/ADR-326-gated-send-safety-model.md)) — N independent client emails; partial failure is the normal case, not an error.
- **Signals optionally injected** — 591A builds and ships before 592A; empty arrays are the documented pre-592 behaviour.

### Non-scope
- No triage computation (592A) or badges (592B). No digest (593). No new approval capability — `AI_REVIEW` only.

---

## Epic 592: Debtor Triage + Trust-Aware Advisor Seam

**Goal**: Deterministic intelligence, fork-neutral core. 592A ships `CollectionsTriageService` (signals `DRIFTING`, `SERIAL_LATE`, `GONE_QUIET`, `ESCALATED`, plus advisor-contributed values — pure queries, no AI, no persistence), the `CollectionsAdvisor` SPI with a no-op default, and legal-za's `TrustAwareCollectionsAdvisor` over `ClientLedgerCardRepository.sumBalancesForCustomer` — fail-open to empty advice on `DataAccessException` (absent trust tables = non-legal tenant), with a **boundary test proving core `collections/` has no `verticals/legal` imports**. 592B renders the signal badges on the debtors page.

**References**: Architecture §3.4 (signal derivations table), §6.5 (SPI + advisor), §8.4 (test rows 8–9), §10 Slice 83-5; [ADR-327](../adr/ADR-327-ai-reminder-drafting-debtor-triage.md), [ADR-329](../adr/ADR-329-trust-aware-collections-extension-seam.md).

**Dependencies**: 589 (ledger data for `GONE_QUIET`/`ESCALATED`). 592B additionally needs 591B (the page exists).

**Scope**: Backend (592A) + Frontend (592B)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **592A** | 592A.1–592A.4 | ~8 backend files (1 triage service + 1 SPI + 1 no-op + 1 advice record + 1 legal advisor + 3 test files) | Triage signals; advisor SPI + no-op; `TrustAwareCollectionsAdvisor`; boundary test; wire signals into 591A's read service + 590A's skill context. |
| **592B** | 592B.1 | ~3 frontend files (badge component + debtors table mod + 1 test) | Signal badges with `TRUST_FUNDS_AVAILABLE` detail tooltip. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 592A.1 | `CollectionsTriageService` | `collections/CollectionsTriageService.java` (new) | 592A.3 | §3.4 derivation table verbatim; native queries like `CollectionsReadService` | Signals per customer: `DRIFTING` (oldest current days_overdue > historical median days-to-pay (`paid_at - due_date` over PAID invoices) + 14); `SERIAL_LATE` (median > 30 but reliably pays — suppress urgency); `GONE_QUIET` (≥2 SENT activities, no payment since); `ESCALATED` (FLAGGED row exists); merge advisor `CollectionsAdvice` signals. Pure computation — no rows written, no AI. |
| 592A.2 | `CollectionsAdvisor` SPI + no-op + legal advisor | `collections/CollectionsAdvisor.java` (new, with nested `CollectionsAdvice(String signal, String detail)` record), `collections/NoOpCollectionsAdvisor.java` (new), `verticals/legal/collections/TrustAwareCollectionsAdvisor.java` (new) | 592A.3 | §6.5 interface verbatim; `TrustBoundaryGuard` `DataAccessException`-tolerance (`.arch-context.md` §9i) | Core collects `List<CollectionsAdvisor>` (Spring list injection — beans register by existence, like `AiSkill`/`JobHandler`). Legal advisor: `sumBalancesForCustomer(customerId)` > 0 → `("TRUST_FUNDS_AVAILABLE", "R … held in trust")` — detail says **"held in trust"**, deliberately not "available to transfer" (aggregate balance ≠ matter-earmarked availability). `DataAccessException` → `List.of()` + debug log — fail-OPEN (advice is informational; the sync guard fails closed because it protects a boundary). Advisor never suppresses a reminder, never proposes a trust transaction. |
| 592A.3 | Triage + advisor tests | `backend/src/test/java/.../collections/CollectionsTriageServiceTest.java`, `.../verticals/legal/collections/TrustAwareCollectionsAdvisorTest.java` | §8.4 rows 8–9: each signal derivation incl. `SERIAL_LATE` suppression; positive trust balance → advice; zero balance → none; absent trust tables (non-legal tenant profile) → empty, no error, scan/read unaffected | seeded PAID/SENT invoice histories via product APIs | Median computation edge cases: no history → no `DRIFTING`/`SERIAL_LATE`. |
| 592A.4 | Boundary test + wiring | `backend/src/test/java/.../collections/CollectionsCoreBoundaryTest.java` (new); `collections/CollectionsReadService.java` (modify — real signals replace empty), `integration/ai/skill/collections/CollectionReminderSkill.java` (modify — advisor annotations into user-prompt context) | boundary: no `io.b2mash...verticals.legal` import in any `collections/` source (scan the package the Phase81BoundaryTest way — scoped to `collections/` sources, not whole-backend); wiring: debtors API now returns computed signals; skill prompt includes advice when present | `Phase81BoundaryTest` source-scan precedent | Advice enters the drafting prompt as acknowledgeable context only — never as an instruction to promise transfers ([ADR-329](../adr/ADR-329-trust-aware-collections-extension-seam.md)). |
| 592B.1 | Signal badges | `frontend/components/.../triage-badges.tsx` (new), debtors table component (modify), 1 test | badges render per signal; `TRUST_FUNDS_AVAILABLE` shows the detail string in a tooltip | Shadcn `Badge` + `Tooltip` conventions | Badge prominence is the ADR-329 mitigation for "reminder despite trust funds" — make `TRUST_FUNDS_AVAILABLE` visually distinct (not destructive-styled; informational). |

### Key Files

**Create (backend):** `collections/CollectionsTriageService.java`, `CollectionsAdvisor.java`, `NoOpCollectionsAdvisor.java`; `verticals/legal/collections/TrustAwareCollectionsAdvisor.java`; tests incl. `CollectionsCoreBoundaryTest.java`.
**Modify (backend):** `collections/CollectionsReadService.java`, `integration/ai/skill/collections/CollectionReminderSkill.java`.
**Create (frontend):** `triage-badges.tsx`; **modify** debtors table.
**Read for context:** `integration/accounting/sync/TrustBoundaryGuard.java` (tolerance pattern); `verticals/legal/trustaccounting/ledger/ClientLedgerCardRepository` (`sumBalancesForCustomer`).

### Architecture Decisions
- **Deterministic triage; AI narrates, never invents** ([ADR-327](../adr/ADR-327-ai-reminder-drafting-debtor-triage.md)) — signals are arithmetic, unit-testable, free, and available with AI disabled.
- **SPI with no-op default; inform-only; fail-open** ([ADR-329](../adr/ADR-329-trust-aware-collections-extension-seam.md)) — core never imports legal; advice decorates a human-reviewed flow, so absence degrades gracefully.

### Non-scope
- No digest consumption (593B reads the same signals). No advice-driven suppression or trust transactions — ever, per ADR-329.

---

## Epic 593: Weekly Cash Digest

**Goal**: The owner-facing surface. 593A extracts the aging-bucket logic from `InvoiceAgingReportQuery` into a shared helper (extract, don't duplicate — 591A's inline SQL converges on it here), assembles `CashDigestData` deterministically (outstanding + buckets, billed vs collected for the trailing period, stale unbilled WIP > 30 days, reminder-activity counts by status, triage signals), and ships the weekly `cash_digest` handler. 593B adds the `cash-digest` skill (narrative + top-3 risks, schema-validated, figures forbidden from invention — the template prints authoritative numbers regardless), the `cash-digest.html` template with a **conditional narrative block** (AI-disabled tenants get numbers-only), in-app `CASH_DIGEST` notifications via `createIfEnabled` + email per owner/admin, and the `collections.digest.sent` audit.

**References**: Architecture §3.5, §6.2 (skill), §6.4 (system invocation), §8.4 (test row "CashDigestServiceTest"), §10 Slice 83-6; [ADR-328](../adr/ADR-328-weekly-cash-digest.md).

**Dependencies**: 589A (activity data + job wiring precedent), 592A (signals). Types registered in 588B. Runs parallel with 591B/C frontend.

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **593A** | 593A.1–593A.3 | ~7 backend files (bucket helper + InvoiceAgingReportQuery mod + CollectionsReadService mod + CashDigestData + assembly service + handler + 1 test file) | Bucket-helper extraction; `CashDigestData` deterministic assembly; `cash_digest` handler + weekly enqueue. |
| **593B** | 593B.1–593B.3 | ~8 backend files (skill + 2 prompt assets + output record + template + StubAiProvider mod + delivery wiring in the digest service + 1–2 test files) | `CashDigestSkill`; template with conditional narrative; notification + email delivery; AI-off fallback; audit. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 593A.1 | Extract shared aging-bucket helper | `reporting/AgingBuckets.java` (new — or package-visible helper in `reporting/`), `reporting/InvoiceAgingReportQuery.java` (modify to delegate), `collections/CollectionsReadService.java` (modify to delegate) | existing `invoice-aging` report tests stay green (regression guard); 593A.3 | §3.5 step 1 ("extract … rather than duplicating") | Pure refactor + reuse: bucket boundaries (current/30/60/90+) defined once. Keep the report's public behaviour identical — its tests are the proof. |
| 593A.2 | `CashDigestData` + assembly + handler | `collections/CashDigestData.java` (record), `collections/CashDigestService.java` (new), `collections/CashDigestHandler.java` (new), recurring-enqueue registration (modify — same site as 589A.3) | 593A.3 | `portal/notification/PortalDigestHandler` (thin handler → `processTenant()`); §3.5 field list | Data: outstandingTotal + buckets; billedVsCollected (trailing 7 days — invoices SENT vs payments recorded); stale WIP (unbilled `TimeEntry` > 30 days via the existing unbilled-time query surface); activity counts by status; triage signals. `jobType() = "cash_digest"`, weekly cadence. Assembly is pure queries — testable without AI. |
| 593A.3 | Assembly tests | `backend/src/test/java/.../collections/CashDigestDataTest.java` | numbers correct against a seeded book (buckets, billed-vs-collected window edges, stale-WIP threshold, activity counts); handler jobType registered | 589A.4 harness | No delivery assertions here — that's 593B. |
| 593B.1 | `CashDigestSkill` + prompts + stub | `integration/ai/skill/collections/CashDigestSkill.java` (new), `ai/skills/cash-digest/system.txt` + `output-schema.json` (new), `CashDigestOutput.java` (record), `integration/ai/StubAiProvider` (modify) | 593B.3 | `CollectionReminderSkill` (590A.1) shape; §6.2 | Input: serialized `CashDigestData` + signals. Output `{narrative, topRisks[{customerName, why, suggestedAction}]}` max 3. Prompt forbids figures not present in the input (the template's authoritative tables bound the damage regardless — ADR-328). System-invoked (`invokedBy` null) like 590A.2. Stub: fixed narrative + risks. |
| 593B.2 | Template + delivery + fallback | `templates/email/cash-digest.html` (new), `collections/CashDigestService.java` (modify — narrate-then-deliver) | 593B.3 | `invoice-delivery.html` frame; `NotificationService.createIfEnabled` + owner/admin fan-out (`.arch-context.md` §9k); email per owner/admin via the standard pipeline (`referenceType = "CASH_DIGEST"`) | Template prints `CashDigestData` tables always; narrative + risks block wrapped in ONE conditional (absent when AI disabled/unavailable — the §3.5 "cheapest correct" fallback). In-app `CASH_DIGEST` via `createIfEnabled` (member-mutable in preferences); emails to owner/admin member addresses; delivery-logged; `collections.digest.sent` audit once per run. AI failure at narrate time degrades to fallback — the job never crashes on AI unavailability (§6.4). |
| 593B.3 | Digest end-to-end tests | `backend/src/test/java/.../collections/CashDigestServiceTest.java` | §8.4 digest row: AI-enabled (stub) → narrative + risks present in GreenMail-observed email + bell notification for owners/admins ONLY; AI-disabled → numbers-only email (no narrative block), still delivered; notification respects preference mute; audit row present; delivery log rows per recipient | 590B.4 GreenMail conventions (port 13025 singleton) | Assert a plain member receives neither email nor bell. |

### Key Files

**Create (backend):** `reporting/AgingBuckets.java`; `collections/CashDigestData.java`, `CashDigestService.java`, `CashDigestHandler.java`; `integration/ai/skill/collections/CashDigestSkill.java` + `CashDigestOutput.java`; `ai/skills/cash-digest/system.txt` + `output-schema.json`; `templates/email/cash-digest.html`; tests.
**Modify (backend):** `reporting/InvoiceAgingReportQuery.java`, `collections/CollectionsReadService.java`, `integration/ai/StubAiProvider`, recurring-enqueue site.
**Read for context:** `portal/notification/PortalDigestHandler.java` + `PortalDigestScheduler` (cadence/fanout); `reporting/InvoiceAgingReportQuery.java` (what to extract).

### Architecture Decisions
- **Deterministic numbers, AI narrates; table always wins** ([ADR-328](../adr/ADR-328-weekly-cash-digest.md)) — a hallucinated figure in prose cannot change what the table shows.
- **Numbers-only fallback over skipping** ([ADR-328](../adr/ADR-328-weekly-cash-digest.md)) — one Thymeleaf conditional; AI-disabled firms keep their weekly lockup read.
- **Weekly, owner/admin, no persistence** ([ADR-328](../adr/ADR-328-weekly-cash-digest.md)) — the email is the artifact; the delivery log is the evidence.

### Non-scope
- No digest archive page/entity. No configurable cadence or recipients. No frontend changes.

---

## Epic 594: QA Capstone — Observed End-to-End Lifecycle

**Goal**: Prove the whole loop with observed evidence, per the project's PASS-means-observed bar. Browser-driven on the E2E mock-auth stack (port 3001; stub AI provider): enable the policy → seed overdue invoices **via product APIs/UI, never raw SQL** → run the scan → see the queue → batch approve → observe the email in Mailpit (payment CTA present) → simulate a webhook payment → observe cancellation in the UI and ledger → observe escalation notification and the digest (bell + Mailpit, then AI-off fallback) → verify the exempt customer produced nothing. Full regression gates. Gap report for anything found.

**References**: Architecture §8.4 (merge bar), §10 Slice 83-7; all five ADRs; CLAUDE.md quality gates (§3 PASS means observed, §4 reproduce-before-fix).

**Dependencies**: 588–593 all merged.

**Scope**: E2E / Process (drives both stacks; writes no product code — fixes found here are re-specced, one fix per PR, per quality gate §7)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **594A** | 594A.1–594A.3 | test-plan doc + Playwright artefacts + screenshots dir (no product code) | Scripted observed lifecycle + regression gates + gap report. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 594A.1 | Author the lifecycle script | `qa/testplan/phase83-collections-lifecycle.md` (new) | — | Phase 81 QA-capstone script format (586C) | Step-by-step executable script (test-plan format convention — execution script, not gap analysis): policy on via settings UI → 3 customers (one exempt, one with trust balance if legal profile) → invoices SENT with staggered due dates via product flows → trigger `collections_scan` (job-queue admin/API path — document the trigger used) → queue shows drafts w/ correct stages → reject one (stays rejected) → batch approve rest → Mailpit assertions (subject, CTA link present) → webhook-sim payment on one → UI shows CANCELLED_PAYMENT + gate refused if re-approved → escalation seeded past 60d → bell + audit → `cash_digest` trigger → digest email + bell → disable AI → numbers-only digest. |
| 594A.2 | Execute + evidence | screenshots under the QA baselines dir; evidence log in the script doc | the script IS the test | Playwright MCP conventions (QA drives the browser, not REST — Mailpit API is the only sanctioned REST) | Every checkpoint gets an artefact: screenshot, Mailpit message id, or ledger/DB read via product API. DEFERRED (not PASS-with-note) for anything not completed. |
| 594A.3 | Regression gates + gap report | gap report appended to the script doc | full `./mvnw verify` clean; `pnpm lint && pnpm build && pnpm test` + `format:check` (frontend + portal if touched) | CLAUDE.md §1 build bar | Bugs found: reproduce-before-fix, one fix per PR, re-run the affected checkpoint after merge. No scenario amendments without authorization (quality gate §6). |

### Key Files

**Create:** `qa/testplan/phase83-collections-lifecycle.md`, screenshots/evidence artefacts.
**Read for context:** Phase 81 586C capstone script; `compose/scripts/e2e-up.sh` stack docs; Mailpit API usage precedent.

### Architecture Decisions
- **PASS means observed** — browser → backend log → Mailpit/DB artefact for every claim; MERGED-AWAITING-VERIFY until then.
- **Seeding via product APIs only** — no SQL shortcuts in QA (standing feedback rule).

### Non-scope
- No product code. No scenario rewrites to "match the product" without explicit authorization.
