# ADR-198: Post-Create Action Execution Model

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 51 (Accounting Practice Management Essentials)

## Context

When `RecurringScheduleExecutor` creates a project via `executeSingleSchedule()`, Phase 51 adds the ability to automatically trigger follow-up actions: generating an engagement letter document and/or sending a client information request. These actions are configured per-schedule via the new `post_create_actions` JSONB column on `RecurringSchedule`.

The question is how these post-create actions should be executed relative to the project creation: synchronously within the same transaction, asynchronously via a domain event and the automation engine, or asynchronously via a message queue.

The existing `executeSingleSchedule()` method runs in a `@Transactional(propagation = Propagation.REQUIRES_NEW)` transaction. It creates the project, records the execution, advances the schedule, and publishes a `RecurringProjectCreatedEvent` via Spring's `ApplicationEventPublisher`. The executor processes schedules sequentially within each tenant.

## Options Considered

### Option 1: Synchronous Within Schedule Transaction (Selected)

Execute post-create actions as additional service calls within the same `REQUIRES_NEW` transaction, after project creation (step 7) but before the audit log (step 9). Each action is wrapped in its own try-catch — a failure in one action does not prevent the other from executing, and neither failure rolls back the project creation.

- **Pros:**
  - **Simplest implementation:** Direct service calls to `GeneratedDocumentService` and `InformationRequestService`. No new infrastructure, no event listeners, no message consumers
  - **Deterministic:** The actions always execute in the same order, with the same inputs. No race conditions, no ordering guarantees needed
  - **Transactional visibility:** The newly created project is visible to the document generation and info request services because they run in the same transaction
  - **Error handling is local:** Each action's try-catch logs the error, sends a notification, and continues. The failure path is simple and testable
  - **No retry complexity:** If an action fails, it fails once. The notification tells the staff to do it manually. No retry loops, no dead letter queues, no idempotency tokens

- **Cons:**
  - **Increases transaction duration:** Document generation (Tiptap/Word rendering + S3 upload) could add 1-3 seconds per schedule execution. For 50 schedules running at 02:00 UTC, total execution time increases from ~30s to ~2-3 minutes
  - **Couples schedule execution to document/request services:** If `GeneratedDocumentService` has a bug that throws uncaught exceptions, it could affect schedule processing (mitigated by the try-catch wrapping)
  - **Blocking:** While post-create actions execute, the next schedule in the queue waits. No parallelism within a tenant's schedule batch

### Option 2: Async via Domain Event + Automation Engine

Publish a `PostCreateActionsRequested` domain event after project creation. A dedicated `PostCreateActionListener` (or the existing `AutomationEventListener`) picks up the event and executes the actions asynchronously.

- **Pros:**
  - Schedule execution is fast — publish the event and move on to the next schedule
  - Decoupled: the schedule executor doesn't need to know about document generation or info request services
  - Could leverage the existing automation engine's retry and error handling infrastructure

- **Cons:**
  - **New infrastructure:** Requires a new event type, a new listener, and potentially new automation rules. The automation engine (Phase 37/48) is rules-based and event-driven — post-create actions are not rules (they are schedule-specific configuration), so they don't fit the automation model cleanly
  - **Transactional boundary:** The event listener runs in a separate transaction. If the listener executes before the project creation transaction commits, the project won't be visible. Must use `@TransactionalEventListener(phase = AFTER_COMMIT)` — which means the event is lost if the application crashes between commit and event delivery
  - **Error handling is distributed:** The failure notification must be sent from the event listener, not the schedule executor. The listener needs access to the schedule's `createdBy` member ID for notification targeting — which means the event must carry this context
  - **Testing complexity:** Integration tests must account for async event processing. Test timing becomes non-deterministic unless a synchronous event bus is used in tests

### Option 3: Async via Message Queue

Publish a message to an external queue (SQS, Redis) after project creation. A consumer processes the post-create actions asynchronously with retry support.

- **Pros:**
  - Maximum decoupling — schedule execution and action execution are completely independent
  - Built-in retry with backoff (SQS) or dead letter queue
  - Scales independently — action processing can be parallelized across consumers

- **Cons:**
  - **Significant infrastructure overhead:** Requires a message queue (SQS or Redis), consumer configuration, message serialization, dead letter queue handling. The platform currently has no message queue infrastructure — this would be the first
  - **Operational complexity:** Monitoring queue depth, consumer health, message age. New failure mode: messages stuck in queue
  - **Overkill for the use case:** Post-create actions are executed at most once per schedule execution (typically daily or monthly). The volume is tiny — perhaps 10-50 actions per day for a large firm. Queue infrastructure is designed for thousands of messages per second
  - **Eventual consistency:** Staff might see the project but not the generated document for several seconds/minutes. This creates confusion: "The engagement was created but where's the letter?"

## Decision

**Option 1 — Synchronous within the schedule transaction.**

## Rationale

Post-create actions are fundamentally different from automation rules. Automation rules are reactive (triggered by events), configurable (per-tenant, per-entity), and potentially complex (conditions, delays, chains). Post-create actions are deterministic (same schedule always produces the same actions), simple (two possible actions: generate document, send info request), and schedule-specific (not tenant-wide rules).

1. **The performance impact is acceptable.** The executor runs at 02:00 UTC. Even with 50 schedules each taking 3 seconds (worst case with document generation + S3 upload), total execution is ~2.5 minutes. No user is waiting. The cron job has a 24-hour cycle — there is no urgency.

2. **The existing services are ready.** `GeneratedDocumentService` and `InformationRequestService` are already `@Service` beans with `@Transactional` methods. Calling them from `RecurringScheduleService` requires only injecting the dependencies. No new classes, no new event types, no new infrastructure.

3. **Error handling is straightforward.** Each action in its own try-catch means:
   - Document generation fails? Log it, notify the creator, continue to info request.
   - Info request fails? Log it, notify the creator, continue to audit/event publishing.
   - Both fail? The project is still created, two notifications are sent, the schedule advances normally.

   This is exactly what staff expect: "The system tried to automate this, it didn't work, here's what you need to do manually."

4. **The migration path is clear.** If the synchronous approach becomes a bottleneck (e.g., a firm with 500 monthly schedules all with document generation), the post-create actions can be extracted into an `@Async` method or a Spring `@TransactionalEventListener`. The service method signatures don't change — only the execution context moves from synchronous to async. The controller and frontend are unaffected.

## Consequences

- **Positive:**
  - Zero new infrastructure — uses existing service beans and transaction management
  - Simple error model — try-catch per action with notification on failure
  - Deterministic execution order — document generation always runs before info request
  - Testable with standard integration tests — no async timing issues
  - Staff see the generated document and info request immediately after the project appears (no eventual consistency delay)

- **Negative:**
  - Schedule execution takes longer when post-create actions are configured (1-3 seconds per action)
  - A bug in `GeneratedDocumentService` that causes an infinite loop or very slow operation would block schedule processing for the current tenant (mitigated by the executor's per-schedule error isolation — the executor catches exceptions per schedule and moves to the next)
  - Both actions share the same `REQUIRES_NEW` transaction as project creation — a database-level failure in action execution could theoretically affect the transaction (mitigated by the try-catch wrapping which prevents exception propagation)

- **Neutral:**
  - The `RecurringScheduleService` gains two new dependencies (`GeneratedDocumentService`, `InformationRequestService`). This increases its constructor parameter count but is acceptable given the direct relationship between schedule execution and these services.
  - The `post_create_actions` JSONB structure is parsed at execution time. Invalid JSON or unknown action types are handled gracefully (logged and skipped).
