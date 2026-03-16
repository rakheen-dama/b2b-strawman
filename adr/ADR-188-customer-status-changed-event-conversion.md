# ADR-188: CustomerStatusChangedEvent Conversion

**Status**: Accepted
**Date**: 2026-03-16
**Phase**: 48 (QA Gap Closure)

## Context

`CustomerStatusChangedEvent` is currently a Spring `ApplicationEvent` subclass in the `compliance/` package. It was created in Phase 15 (Customer Compliance & Lifecycle) before the automation engine existed (Phase 37). It carries `customerId`, `oldStatus`, and `newStatus` and is published when a customer's lifecycle status transitions (e.g., PROSPECT -> ONBOARDING, ONBOARDING -> ACTIVE).

The automation engine (Phase 37) operates exclusively on `DomainEvent` -- a sealed interface in the `event/` package. `AutomationEventListener.onDomainEvent(DomainEvent event)` is annotated with `@EventListener` and Spring's event dispatching routes events by type. Because `CustomerStatusChangedEvent extends ApplicationEvent` and does NOT implement `DomainEvent`, the `AutomationEventListener` never receives it.

This means:
- The `CUSTOMER_STATUS_CHANGED` value exists in `TriggerType` enum (added in Phase 37, anticipating this wiring).
- The `AutomationContext.buildCustomerStatusChanged()` method exists as a stub with the comment "No event is currently mapped."
- The `accounting-za.json` automation pack includes a `fica-reminder` rule targeting `CUSTOMER_STATUS_CHANGED`, but it never fires.

The QA cycle (GAP-003) confirmed this: transitioning a customer from PROSPECT through ONBOARDING to ACTIVE never triggered the FICA reminder automation.

`DomainEvent` is a sealed interface requiring explicit `permits` listing. It currently has 35 permitted types, all of which are records in the `event/` package. The interface defines methods: `eventType()`, `entityType()`, `entityId()`, `projectId()`, `actorMemberId()`, `actorName()`, `tenantId()`, `orgId()`, `occurredAt()`, `details()`, and `automationExecutionId()` (with a default implementation).

## Options Considered

### Option 1: Convert existing `ApplicationEvent` to `DomainEvent` record

Replace `CustomerStatusChangedEvent extends ApplicationEvent` with a `DomainEvent` record. Move the class from `compliance/` to `event/` (where all other `DomainEvent` types live). Add it to the `permits` clause. Update all publishers to use the new record constructor.

- **Pros:**
  - Single event type for customer status changes -- no ambiguity about which event to listen for
  - Follows the established pattern: all 35 existing `DomainEvent` types are records in the `event/` package
  - The automation engine, notification handlers, and audit consumers all receive the same event
  - The `details()` map carries `old_status`, `new_status`, `customer_name` -- richer than the current class's three fields
  - `automationExecutionId()` default implementation provides cycle detection for free
  - No new event infrastructure -- uses the same `ApplicationEventPublisher.publishEvent()` call

- **Cons:**
  - Breaking change: all existing `@EventListener` methods that accept `CustomerStatusChangedEvent` must be updated to handle the record type
  - The record constructor has 10 parameters (all `DomainEvent` fields) vs. the current class's 4 parameters (`source`, `customerId`, `oldStatus`, `newStatus`) -- publisher code becomes more verbose
  - Existing tests that construct `CustomerStatusChangedEvent` with the 4-arg constructor must be rewritten
  - The `source` field from `ApplicationEvent` is lost (though it was only used for Spring's internal event tracking, not by any consumer in our codebase)

### Option 2: Create parallel `DomainEvent` + keep `ApplicationEvent`

Keep `CustomerStatusChangedEvent extends ApplicationEvent` unchanged. Create a new `CustomerStatusChangedDomainEvent` record implementing `DomainEvent`. Publish both events from the same location (or publish the new one and have a bridge listener that converts it to the old one).

- **Pros:**
  - Zero breaking changes: existing listeners continue to work without modification
  - New `DomainEvent` follows the record pattern exactly
  - Incremental migration: old listeners can be updated to use the new event type over time

- **Cons:**
  - Two event types for the same concept -- confusing for developers ("which one do I listen to?")
  - Double publishing: either the publisher publishes both events (duplication) or a bridge listener converts one to the other (indirection)
  - Bridge listener introduces ordering concerns: does the automation engine see the event before or after the notification handler?
  - Every future consumer must decide which event type to listen for, or both
  - Violates the principle established in Phase 37 that all domain events implement `DomainEvent` -- this creates a precedent for maintaining parallel event hierarchies
  - Technical debt: the old `ApplicationEvent` version would eventually need to be removed, but inertia means it persists indefinitely

### Option 3: Add `ApplicationEvent` listener to `AutomationEventListener`

Keep `CustomerStatusChangedEvent extends ApplicationEvent` unchanged. Add a second `@EventListener` method to `AutomationEventListener` that accepts `CustomerStatusChangedEvent` (the `ApplicationEvent`), wraps it in a `DomainEvent`-compatible adapter, and delegates to the standard automation processing.

- **Pros:**
  - No changes to the event class or its publishers
  - No changes to existing listeners
  - Localized change: only `AutomationEventListener` is modified

- **Cons:**
  - The adapter must implement `DomainEvent` but is NOT a permitted type in the sealed interface -- requires either unsealing the interface or creating a special case
  - If unsealed, `DomainEvent` loses its compile-time exhaustiveness guarantees (`switch` statements no longer verify coverage)
  - If not unsealed, the adapter cannot implement `DomainEvent`, making it incompatible with `TriggerTypeMapping`, `AutomationContext`, and `ConditionEvaluator` (all of which type-check against `DomainEvent`)
  - Creates a special-case code path in the automation engine for one event type -- every future event would need to justify why it is or is not a `DomainEvent`
  - The adapter wrapping is fragile: if `DomainEvent` gains new methods, the adapter must be updated

## Decision

**Option 1 -- Convert the existing `ApplicationEvent` to a `DomainEvent` record.**

## Rationale

The `DomainEvent` sealed interface is the established event contract in DocTeams. All 35 domain event types are records in the `event/` package, all implement the same interface, and all carry the same base fields. `CustomerStatusChangedEvent` predates this contract (Phase 15 vs. Phase 37) and is the only `ApplicationEvent` subclass that should participate in automation. Converting it aligns with the existing pattern rather than creating exceptions.

The breaking change cost is small and well-contained. A codebase search shows three consumers of `CustomerStatusChangedEvent`:

1. `CustomerLifecycleEventHandler.onStatusChanged()` -- handles post-transition logic (e.g., notifications). Needs to be updated to accept the record type and read from `details()` map instead of getter methods.
2. `AuditEventListener.onCustomerStatusChanged()` -- logs audit events. Reads `customerId`, `oldStatus`, `newStatus` -- all available in the record's `details()` map.
3. `AutomationEventListener.onDomainEvent()` -- the new path being wired. Already expects `DomainEvent`.

Updating 2 existing listeners and 1-2 publishers is a bounded, low-risk change. The record constructor is more verbose (10 fields vs. 4), but this follows the same pattern as every other `DomainEvent` -- the consistency benefit outweighs the verbosity cost.

Creating a parallel event type (Option 2) introduces long-term confusion that costs more than the one-time migration. Two event types for the same concept means every developer who encounters customer status changes must understand the distinction, and every new consumer must choose between them. This is exactly the kind of technical debt that Phase 48 should eliminate, not create.

Adapting the `AutomationEventListener` (Option 3) would require unsealing `DomainEvent` or creating a special-case code path. The sealed interface is a deliberate design choice (Phase 6.5): it ensures compile-time exhaustiveness in `switch` expressions and makes the full set of domain events visible in one location. Unsealing it for one event would undermine the contract for all events.

Spring's `ApplicationEventPublisher.publishEvent()` accepts any `Object`, not only `ApplicationEvent` subclasses. The record-based `CustomerStatusChangedEvent` will be dispatched to all matching `@EventListener` methods exactly as the class-based version was. No Spring infrastructure changes are needed.

## Consequences

- `CustomerStatusChangedEvent` moves from `compliance/` to `event/` package, from a class to a record, from `ApplicationEvent` to `DomainEvent`.
- The `DomainEvent` permits clause grows from 35 to 36 types (37 with `FieldDateApproachingEvent` in the same phase).
- Existing listeners (`CustomerLifecycleEventHandler`, `AuditEventListener`) are updated to accept the record type. They read `oldStatus` and `newStatus` from `event.details().get("old_status")` and `event.details().get("new_status")` respectively, consistent with how other domain events carry status data.
- Publishers (`ChecklistInstanceService` and any other code that publishes this event) are updated to construct the record with all required `DomainEvent` fields. A factory method or builder can reduce constructor verbosity.
- The `CUSTOMER_STATUS_CHANGED` trigger type, which has existed since Phase 37 without a mapped event, now works end-to-end.
- The `fica-reminder` automation template in `accounting-za.json` activates and fires on customer status transitions.
- The `AutomationContext.buildCustomerStatusChanged()` stub is filled in with real field extraction from the `details()` map.
- The `source` field from `ApplicationEvent` is lost. No consumer in the codebase reads this field. If needed in the future, the event publisher's class name can be added to `details()`.
