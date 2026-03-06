# ADR-145: Rule Engine vs Visual Workflow Builder

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 37 (Workflow Automations v1)

## Context

Phase 37 introduces configurable automation to DocTeams. Firms need to express repeatable patterns — "when a task is completed, create the next task," "when an invoice is overdue, email the client" — without code changes. The platform already has the event infrastructure (domain events, notifications, audit) and the action services (task creation, email delivery, status updates). The question is what abstraction to present to firm admins for wiring these together.

The target users are firm admins at professional services firms with 2-50 members. These users are not developers but are comfortable with structured forms (they already configure checklists, custom fields, rate cards, and document templates). The automation system must be accessible to this audience while covering the most common workflow patterns.

## Options Considered

1. **Visual workflow builder (canvas-based)** — Drag-and-drop canvas where admins place trigger nodes, condition nodes, action nodes, and delay nodes. Connections define flow. Supports branching (if-else), parallel paths, and loops.
   - Pros:
     - Maximum expressiveness — can model complex workflows with branching and parallel execution
     - Visual representation makes workflows self-documenting
     - Industry standard for enterprise automation (Zapier, n8n, Power Automate)
     - Future-proof — no ceiling on what can be expressed
   - Cons:
     - Substantial frontend complexity — canvas renderer, node editor, connection management, zoom/pan, undo/redo
     - Backend complexity — workflow execution engine with state machine, branch evaluation, join logic, error recovery per branch
     - Over-engineers v1 — 80%+ of professional services automation is linear (trigger → action)
     - 3-5x implementation effort compared to structured rules
     - Higher cognitive load for admins who just want "when X happens, do Y"
     - Testing surface is much larger (combinatorial branch paths)

2. **Structured rules engine (trigger → conditions → actions)** — Form-based configuration: select a trigger event, add optional conditions, define an ordered list of actions. Linear execution only.
   - Pros:
     - Covers 80% of professional services automation use cases (status reactions, follow-up reminders, task chaining, stakeholder notifications)
     - Forms-based UX is familiar — consistent with existing settings pages (checklists, custom fields, templates)
     - Small implementation surface — one event listener, one condition evaluator, six action executors
     - Easy to reason about — "this trigger fires this rule, which does these actions in order"
     - Template gallery provides starting points — admins customize rather than build from scratch
     - Straightforward testing — linear execution path, no branch combinatorics
   - Cons:
     - Cannot express branching logic ("if amount > 10000 do X, else do Y") within a single rule
     - No parallel execution paths
     - Admins must create multiple rules for OR conditions
     - May feel limiting for power users accustomed to visual builders
     - Future migration to visual builder may require data model changes

3. **Code-based rules (DSL or scripting)** — Admins write rules in a domain-specific language or embedded scripting engine (e.g., MVEL, SpEL, JavaScript).
   - Pros:
     - Maximum flexibility for technically inclined admins
     - Small frontend footprint (code editor)
     - Can express any logic without UI constraints
   - Cons:
     - Excludes non-technical admins entirely — violates the accessibility requirement
     - Security risk — arbitrary code execution in a multi-tenant environment requires sandboxing
     - No visual feedback on what the rule does — difficult to debug
     - Script debugging and error messages are poor for non-developers
     - Maintenance burden — scripts accumulate without visibility into what they do

## Decision

Option 2 — Structured rules engine (trigger → conditions → actions).

## Rationale

The structured rules engine is the right trade-off for v1. Professional services automation is overwhelmingly linear: event happens, check some conditions, do some things. The common patterns — task chaining, overdue reminders, status cascades, stakeholder notifications — are all linear trigger-action sequences. Branching is rare: "if invoice > 10000, escalate to partner" is better modeled as two rules (one for high-value invoices, one for standard) than a branching workflow.

The forms-based UX is consistent with the existing settings experience. Firm admins already configure checklists (ordered items), custom fields (type + validation), rate cards (hierarchical rules), and document templates (variable insertion). The automation rule form follows the same pattern: select a type, configure fields, add items to a list. The template gallery lowers the barrier further — admins start from a working template and customize rather than building from scratch.

The implementation surface is deliberately small. A single `AutomationEventListener` bridges to the existing event system. Six action executors delegate to existing services. The condition evaluator is a straightforward field-comparison engine. This keeps the Phase 37 scope achievable (8 slices) and the code maintainable.

The visual workflow builder is not abandoned — it is deferred to v2. The structured rules data model (`AutomationRule` + `AutomationAction`) is compatible with a future visual builder: rules become the simplest workflow type (single trigger, linear action chain), and the visual builder adds branching, parallel paths, and loops as additional workflow types. The migration path is additive, not destructive.

## Consequences

- Rules are linear: trigger → conditions → actions. No branching within a single rule.
- OR conditions require multiple rules (one per OR branch). This is documented in the UI.
- The frontend is forms-based, not canvas-based. Lower implementation effort, consistent with existing UX.
- A visual workflow builder can be added in a future phase as an additive feature alongside structured rules.
- Power users who need branching must work around it with multiple rules until v2.
