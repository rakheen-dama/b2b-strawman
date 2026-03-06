# ADR-146: Automation Cycle Detection Strategy

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 37 (Workflow Automations v1)

## Context

The automation engine subscribes to domain events and can execute actions that produce new domain events. This creates the potential for infinite loops: a rule triggers on task creation, creates a new task as an action, the new task fires a task creation event, the rule triggers again, and so on indefinitely. At target firm sizes (2-50 members), runaway loops would not cause infrastructure-level problems (no millions of events), but they would create confusing noise — hundreds of spurious tasks, notifications, or emails — and consume database resources unnecessarily.

The cycle detection mechanism must prevent infinite loops without being so restrictive that it blocks legitimate automation patterns. It must also have minimal performance overhead for the common case (most domain events are not triggered by automations).

## Options Considered

1. **Metadata flag on domain events** — Add a nullable `automationExecutionId` field to domain events. When an action executor creates an entity that fires a domain event, it passes the execution ID to the service, which includes it in the event. The `AutomationEventListener` checks for this field and skips processing if present.
   - Pros:
     - Zero overhead for non-automation events (field is null, check is a null comparison)
     - Simple implementation — one field addition, one check in the listener
     - Deterministic — no timing-dependent behavior
     - Clear audit trail — the execution ID links back to the originating rule
     - No additional database queries or state management
   - Cons:
     - Prevents ALL automation evaluation for automation-originated events (no cross-rule chaining)
     - Requires modifying domain event constructors and service method signatures to propagate the execution ID
     - If a service forgets to propagate the ID, cycles are possible

2. **Execution depth counter** — Track how many times an automation has recursively triggered. Stop at depth N (e.g., 3).
   - Pros:
     - Allows controlled chaining (rule A → action → event → rule B → action, up to depth N)
     - Flexible — depth limit is configurable
   - Cons:
     - Requires propagating a depth counter through service calls and domain events
     - Harder to reason about — "why did my rule only fire 3 times?" is confusing
     - The "right" depth limit is unclear — too low blocks legitimate patterns, too high allows long chains
     - More complex implementation than a simple flag

3. **Rule dependency graph analysis** — At rule creation/update time, analyze all rules to build a dependency graph. Detect cycles in the graph and prevent creation of rules that would cause loops.
   - Pros:
     - Prevents cycles at configuration time, not execution time
     - No runtime overhead for event processing
   - Cons:
     - Complex graph analysis — must map trigger types to action types and determine which actions produce which events
     - JSONB conditions make static analysis unreliable (a rule might only fire under specific conditions, so the graph edge is conditional)
     - Rules can be created in any order — must re-analyze on every rule change
     - False positives — may block legitimate rules that wouldn't actually cycle due to conditions
     - Significant implementation complexity for marginal benefit at v1 scale

4. **Rate limiting per rule** — Track how many times each rule fires within a time window. If a rule exceeds N executions in M minutes, auto-disable it.
   - Pros:
     - Catches runaway rules regardless of the cause (cycles, external triggers, etc.)
     - Simple per-rule counter
   - Cons:
     - Reactive, not preventive — the rule fires many times before being stopped
     - Legitimate high-frequency rules (e.g., rule triggers on every time entry) might be falsely rate-limited
     - Auto-disabling a rule is a disruptive side effect — admin must investigate and re-enable
     - Does not prevent the root cause (cycles), only mitigates the symptom

## Decision

Option 1 — Metadata flag (`automationExecutionId`) on domain events.

## Rationale

The metadata flag approach is the simplest mechanism that completely prevents the dangerous case (direct self-triggering loops) with zero overhead for the normal case. The vast majority of domain events in the system are not triggered by automations — they come from user actions via HTTP requests. For these events, the cycle check is a single null comparison and returns immediately.

The trade-off — blocking all automation evaluation for automation-originated events — is acceptable for v1. Cross-rule chaining (rule A's action triggers rule B) is explicitly out of scope. At target firm sizes, the need for multi-rule chains is rare, and the same effect can often be achieved with delayed actions within a single rule or by structuring triggers and conditions to avoid the chain entirely.

The propagation requirement (services must pass `automationExecutionId` to event constructors) is addressed by adding the field as a default method on the `DomainEvent` interface returning `null`. Only event classes that can be triggered by automation actions need to override it. The action executors are the single point where the ID is injected, keeping the change localized.

A future version can relax this to a depth counter (Option 2) if cross-rule chaining becomes a requirement. The metadata field naturally supports this — replace the boolean check (`!= null → skip`) with a depth counter (`depth >= N → skip, else increment`).

## Consequences

- `DomainEvent` gains a `default UUID automationExecutionId()` method returning `null`
- Event implementations that can be triggered by automations override the method
- Action executors pass the execution ID when invoking services
- Services include the execution ID in emitted events
- The `AutomationEventListener` skips all events where `automationExecutionId != null`
- Cross-rule chaining is not possible in v1 (rule A's action cannot trigger rule B)
- Can be relaxed to a depth counter in a future version without data model changes
