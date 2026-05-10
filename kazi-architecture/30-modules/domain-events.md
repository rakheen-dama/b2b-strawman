# Domain Events

**Status:** filled (Phase C).
**Bounded context:** see [`10-bounded-contexts.md` § domain-events](../10-bounded-contexts.md).

## Purpose

The sealed `DomainEvent` interface and its record permits are Kazi's cross-module communication backbone. Publishers call Spring's `ApplicationEventPublisher.publishEvent(DomainEvent)`; consumers register `@EventListener` or `@TransactionalEventListener` methods. There is no Kafka, no in-memory queue, no custom bus — the whole mechanism is the Spring container plus a sealed-interface convention.

The bus exists to **decouple producers from consumers**. The `ProjectService` knows it just completed a project; it does not know that an automation rule, a notification fan-out, and a portal read-model sync all want to react. It publishes one event; the listeners declare interest.

The `AFTER_COMMIT` transactional phase makes the bus **safe for irreversible side effects**: emails sent, integration pushes, read-model writes that must reflect committed state. Audit is the deliberate exception (see §6).

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java:17` — sealed interface declaration.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java:9-15` — Javadoc: events must be records of primitive/UUID fields, no JPA references, no lazy proxies.

## Entities owned

_None._ `DomainEvent` is a Java sealed interface; every permit is a Java `record`. There are no JPA entities, no tables, no migrations associated with this module. This is pure infrastructure / convention.

## REST surface

_None._ Events are a backend-internal mechanism; they are not exposed via HTTP, SSE, or any public endpoint.

## Frontend pages / components

_None._ The frontend never directly observes `DomainEvent` instances. Consumers that *result* from events (notifications, portal read-model rows) are surfaced via their own controllers (`/api/notifications`, `/portal/*`).

## Domain events — the catalogue

There are **41 permits** on the sealed interface (A1 said "~35 records"; the actual count at HEAD is 41, listed here verbatim from `event/DomainEvent.java:18-59`). All event records implement the same shape — `eventType, entityType, entityId, projectId, actorMemberId, actorName, tenantId, orgId, occurredAt, details, automationExecutionId` — see the interface methods at `DomainEvent.java:61-98`.

| Event | Publisher | Trigger | Primary consumers |
|---|---|---|---|
| `BudgetThresholdEvent` | `InvoiceService` | Invoice approval crosses a budget threshold | `NotificationService`, `AutomationEventListener` |
| `CommentCreatedEvent` | `CommentService` | New comment created on project/task | `NotificationService` (mention/thread fan-out), `AutomationEventListener` |
| `CommentUpdatedEvent` | `CommentService` | Comment edited | `AutomationEventListener` |
| `CommentDeletedEvent` | `CommentService` | Comment soft-deleted | `AutomationEventListener` |
| `CommentVisibilityChangedEvent` | `CommentService` | Comment visibility toggled (internal ↔ portal-visible) | `AutomationEventListener`, portal read-model |
| `TaskAssignedEvent` | `TaskService` | Task assignee set or changed | `NotificationService`, `AutomationEventListener` |
| `TaskClaimedEvent` | `TaskService` | Self-assignment via claim | `NotificationService`, `AutomationEventListener` |
| `TaskStatusChangedEvent` | `TaskService` | Status enum transition | `AutomationEventListener` |
| `TaskCompletedEvent` | `TaskService` | Status → COMPLETED | `AutomationEventListener` (heavy trigger surface) |
| `TaskCancelledEvent` | `TaskService` | Status → CANCELLED | `AutomationEventListener` |
| `TaskReopenedEvent` | `TaskService` | Status → reopened from terminal state | `AutomationEventListener` |
| `TaskRecurrenceCreatedEvent` | `TaskService` (recurrence engine) | Recurrence rule materialises a new task instance | `NotificationService`, `AutomationEventListener` |
| `DocumentUploadedEvent` | `DocumentService` | New document confirmed after S3 presign | `NotificationService`, portal read-model, `AutomationEventListener` |
| `DocumentGeneratedEvent` | `DocumentGenerationService` | Template render produces a `GeneratedDocument` | `NotificationService`, portal read-model (`PortalDocumentNotificationHandler`) |
| `MemberAddedToProjectEvent` | `ProjectMemberService` | `ProjectMember` row created | `NotificationService`, `AutomationEventListener` |
| `InvoiceApprovedEvent` | `InvoiceService` | DRAFT → APPROVED | `NotificationService`, `AutomationEventListener` |
| `InvoiceSentEvent` | `InvoiceService` | APPROVED → SENT | `NotificationService`, portal read-model (`PortalEmailNotificationChannel`) |
| `InvoicePaidEvent` | `InvoiceService` | Payment recorded → PAID | `NotificationService`, portal read-model, `AutomationEventListener` (InvoicePaid trigger) |
| `InvoicePaymentReversedEvent` | `InvoiceService` | Full payment reversal | `NotificationService`, `AutomationEventListener` |
| `InvoicePaymentPartiallyReversedEvent` | `InvoiceService` | Partial payment reversal | `NotificationService`, `AutomationEventListener` |
| `InvoiceVoidedEvent` | `InvoiceService` | Invoice voided | `NotificationService`, `AutomationEventListener` |
| `TimeEntryChangedEvent` | `TimeEntryService` | Create / update / delete of a time entry | `AutomationEventListener` |
| `ExpenseCreatedEvent` | `ExpenseService` | Expense recorded | `AutomationEventListener` |
| `ExpenseDeletedEvent` | `ExpenseService` | Expense removed | `AutomationEventListener` |
| `AcceptanceRequestSentEvent` | `AcceptanceService` | Acceptance request emailed to portal contact | portal read-model, `AutomationEventListener` |
| `AcceptanceRequestViewedEvent` | `AcceptanceService` | Recipient opens acceptance link | `AutomationEventListener` |
| `AcceptanceRequestAcceptedEvent` | `AcceptanceService` | Recipient accepts | `NotificationService`, portal read-model, `AutomationEventListener` |
| `AcceptanceRequestRevokedEvent` | `AcceptanceService` | Sender revokes | `AutomationEventListener` |
| `AcceptanceRequestExpiredEvent` | `AcceptanceExpiryProcessor` | Hourly job expires past `expiresAt` | `AutomationEventListener` |
| `ProjectCompletedEvent` | `ProjectService` | `project.complete()` (`Project.java:259`) | `NotificationService`, `AutomationEventListener`, portal read-model |
| `ProjectArchivedEvent` | `ProjectService` | `project.archive()` | `NotificationService`, `AutomationEventListener` |
| `ProjectReopenedEvent` | `ProjectService` | `project.reopen()` | `AutomationEventListener` |
| `ProposalSentEvent` | `ProposalService` | Proposal emailed | portal read-model, `AutomationEventListener` |
| `InformationRequestSentEvent` | `InformationRequestService` | Request issued to portal contact | portal read-model, `AutomationEventListener` |
| `InformationRequestCompletedEvent` | `InformationRequestService` | All required items submitted+accepted | `NotificationService`, `AutomationEventListener` |
| `InformationRequestCancelledEvent` | `InformationRequestService` | Sender cancels in-flight request | `AutomationEventListener` |
| `InformationRequestDraftCreatedEvent` | `InformationRequestService` | Draft persisted (pre-send) | `AutomationEventListener` |
| `RequestItemSubmittedEvent` | `InformationRequestService` | Portal contact submits a single item | portal read-model, `AutomationEventListener` |
| `RequestItemAcceptedEvent` | `InformationRequestService` | Staff accepts submitted item | `AutomationEventListener` |
| `RequestItemRejectedEvent` | `InformationRequestService` | Staff rejects submitted item | `AutomationEventListener` |
| `CustomerStatusChangedEvent` | `CustomerLifecycleService` | `LifecycleStatus` transition (PROSPECT→ONBOARDING→ACTIVE→DORMANT→OFFBOARDING→OFFBOARDED→ANONYMIZED) | `AutomationEventListener` (lifecycle triggers) |
| `FieldDateApproachingEvent` | `FieldDateScannerJob` (daily) | DATE-typed custom field approaching deadline | `AutomationEventListener` (scheduled-trigger entry) |

**Conspicuous absences (events that are *not* on the bus):**

- `RecurringProjectCreatedEvent`, `ScheduleCompletedEvent`, `ScheduleSkippedEvent` — referenced in `10-bounded-contexts.md` and A1 §4 as scheduler events; they are emitted by `ProjectScheduleService` but not currently in the sealed permit list at `event/DomainEvent.java:18-59`. Treat as either (a) not-yet-migrated to the sealed interface, or (b) emitted via a different mechanism. Cross-link gap to track in `90-adr-index.md`.
- `ProposalExpiredEvent` — A1 lists this; not a permit at HEAD. Same gap as above.
- Trust-accounting events — extend the pattern via a separate sealed `TrustDomainEvent` interface (see §7).

**Per-event source files:** every permit is a single-record file in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/<Name>.java`. Listed permits map 1:1 (e.g. `event/ProjectCompletedEvent.java`, `event/InvoicePaidEvent.java`).

## Cross-cutting touchpoints

### AFTER_COMMIT vs in-transaction

Spring offers two listener annotations; Kazi uses both deliberately:

- **`@EventListener`** (no transaction phase) — fires synchronously inside the publishing transaction. The handler sees in-flight state. Used by **AutomationEventListener** because the automation engine schedules delayed actions, binds `RequestScopes.AUTOMATION_EXECUTION_ID` on the same call stack, and needs the parent transaction to be live for cycle-detection bookkeeping. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListener.java:25` (the `@Component`); `:56` (`@EventListener public void onDomainEvent(DomainEvent event)` — single universal method).
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`** — fires after the publishing transaction commits successfully; if the transaction rolls back, the listener never runs. Used by **NotificationService** and the **portal read-model handlers** for two reasons: (1) email is irreversible, (2) read-model writes must reflect committed state. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java:50` (16 listener methods, all AFTER_COMMIT); `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalDocumentNotificationHandler.java:114`; `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java:115, :152, :182, :209` (four handlers: acceptance, proposal, document, invoice).

### The three primary consumer patterns

1. **`AutomationEventListener` (universal subscriber, in-flight)** — single `@EventListener` method takes any `DomainEvent`, routes to the trigger-matching engine. **Not** `@TransactionalEventListener` because automation needs in-flight execution context (delayed-action scheduling, `AUTOMATION_EXECUTION_ID` binding for cycle detection — see `DomainEvent.automationExecutionId()` default impl at `DomainEvent.java:94`). Every event hits this listener; there is no opt-out (see Open Questions).
2. **`NotificationService` (after-commit, fan-out)** — declares ~16 specific `@TransactionalEventListener(AFTER_COMMIT)` methods, one per event type it cares about. Drives in-app + email notifications via the EMAIL integration port. Fail-safe: if the transaction rolls back, no notification fires. `→ A1 §4 consumers table`.
3. **Portal read-model + portal-email listeners (after-commit, projection)** — sync committed state into portal read-model rows in the portal projection. Multiple handlers in `portal/readmodel/` and `portal/notification/`. All use AFTER_COMMIT. The portal app reads only from the read-model, never directly from domain tables.

### Audit is **not** a bus consumer

Audit is the deliberate exception. Services call `AuditService.log(...)` **directly, in the same transaction** as the change being audited (`audit/DatabaseAuditService.java:88`). This is the load-bearing property: an audit row cannot exist for a rolled-back operation, and an unrolled-back operation cannot exist without its audit row. If audit were `AFTER_COMMIT`, a successful commit could still fail to write an audit row (e.g. JVM crash between commit and listener fire). Cross-link → [`audit.md`](audit.md) §3.

### The base contract every event must satisfy

`DomainEvent.java:61-80` declares 10 required methods + 1 default. Notable invariants:

- `tenantId()` and `orgId()` — every event carries its tenant + Clerk org ID so that listeners running on async threads (or fired from `TenantScopedRunner` jobs) can re-bind `RequestScopes`.
- `automationExecutionId()` (default impl) — reads from `RequestScopes.AUTOMATION_EXECUTION_ID`, allowing the automation engine to suppress events that *originated from* an automation action (cycle detection). Some events (e.g. `TaskStatusChangedEvent`, `ProjectCompletedEvent`) override this to carry the ID as an explicit field, decoupling the event from its publishing scope.
- Records only, primitives + UUIDs only — no JPA references, no lazy proxies. The event must remain valid after the publishing transaction commits and the persistence context closes (Javadoc, `DomainEvent.java:9-15`).

## Vertical specifics

None for the core sealed interface — events are profile-agnostic infrastructure.

The legal vertical extends the *pattern* with its own sealed `TrustDomainEvent` hierarchy (separate interface, separate permit list, separate bus call). That hierarchy is documented in [`trust-accounting.md`](trust-accounting.md). Trust events do not flow through the same `DomainEvent` bus — they are deliberately segregated so a Kazi-wide listener (e.g. portal read-model) cannot accidentally pick up trust-account state.

## Active ADRs

There is **no formal ADR** for the event-bus pattern itself. The conventions are documented in:

- `glossary.md:114` — Domain Event glossary entry referencing "ADR-029" (which does not appear to exist as a numbered file at HEAD; provisional reference).
- `20-cross-cutting/event-bus.md` — narrative of the convention (after-commit semantics, sealed-interface contract).
- `_discovery/A6-cross-cutting.md` §6 — design rationale for AFTER_COMMIT and the audit exception.

ADRs that touch the bus indirectly:

- ADR-271 (scheduled-trigger reaper pattern) — `FieldDateApproachingEvent` is the canonical scheduled-trigger event entry.
- ADR-274 (dedicated accounting sync service, not the rule engine) — explicitly opts the Xero adapter *out* of the universal-subscription pattern, since fan-out per event would not give the sync service what it needs (queue + retry + dead-letter).

See `90-adr-index.md` for the events cluster.

## Key flows

Every flow page in `50-flows/` references this module:

- `50-flows/project-completion.md` — `ProjectCompletedEvent` fan-out to automation, notification, portal.
- `50-flows/invoice-paid.md` — `InvoicePaidEvent` triggers AutomationEventListener (InvoicePaid trigger), NotificationService (email customer), portal read-model.
- `50-flows/portal-acceptance.md` — `AcceptanceRequestAcceptedEvent` lifecycle.
- `50-flows/automation-trigger-firing.md` — Universal `AutomationEventListener.onDomainEvent` dispatch.

This module page is the **convention reference** that the flow pages link back to.

## Open questions / known fragility

1. **Sealed-interface permit cardinality.** Adding a new event currently requires editing the sealed declaration at `DomainEvent.java:18-59` *and* creating a new record. Forgetting the permit declaration produces a compile error (good), but the friction is non-trivial when many events are added in a single phase. Pattern works at 41; would it work at 100? Unclear. Splitting into per-domain sub-sealed-interfaces (mirroring the `TrustDomainEvent` precedent in §7) is one option.

2. **Universal automation subscription is non-opt-out.** `AutomationEventListener.onDomainEvent` runs the trigger-matching engine for *every* `DomainEvent`, regardless of whether any rule cares about that event type. With ~41 event types and (say) 50 active rules per tenant, every published event walks the rule list. There is no per-tenant or per-event-type short-circuit at the listener level (the engine itself filters on trigger type, but it still gets called). Potentially expensive at scale; not currently a bottleneck.

3. **Cross-module event coupling.** When a downstream module (e.g. `portal/readmodel`) relies on an upstream event (e.g. `InvoiceSentEvent`), the upstream owner cannot easily refactor the event shape without breaking the downstream listener. Java's compile-time pattern-match on sealed interfaces *does* alert listeners that a new permit exists, but renaming a field in an existing event record is a silent break for any listener that reads that field. There is no event-versioning mechanism (no `version()` method, no schema registry).

4. **Schema migration for event payloads is risky.** Because there is no event versioning and no on-disk persistence (events are in-memory, fired-and-forgotten via Spring's listener container), a payload-shape change is "safe" in the sense that no historic events ever replay. But cross-listener divergence during a rolling deploy *is* possible — half the listeners on v1, half on v2, both running against shared queues of in-flight events. Not an issue today (deploys are atomic), but a constraint to keep in mind.

5. **A1 lists 3 events that are not in the current permit set.** `RecurringProjectCreatedEvent`, `ScheduleCompletedEvent`, `ScheduleSkippedEvent`, and `ProposalExpiredEvent` appear in A1 §4 and `10-bounded-contexts.md`, but are not permits on the sealed interface at HEAD. Either A1 was written against a different commit, the events were renamed, or they are emitted via a different mechanism (e.g. direct service-to-service call, or a non-sealed event). Worth a one-pass reconciliation.

6. **No dead-letter / retry for listeners.** If `NotificationService` throws inside an AFTER_COMMIT listener, Spring rethrows but the source transaction has already committed. The mutation happened; the side effect didn't. There is no DLQ, no retry, no reaper. For email this is mitigated because the EMAIL adapter has its own retry (`Resilience4j`), but for portal read-model writes a thrown exception is silently swallowed. Cross-link [`notifications.md`](notifications.md) for the email-side discussion.
