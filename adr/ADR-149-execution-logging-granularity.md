# ADR-149: Execution Logging Granularity

**Status**: Accepted
**Date**: 2026-03-06
**Phase**: 37 (Workflow Automations v1)

## Context

The automation engine needs an execution audit trail so admins can answer two questions: "Did my rule fire?" and "Why did this action fail?" The logging mechanism must capture enough detail for debugging without generating excessive data. A rule with 3 actions might succeed on 2 and fail on 1 — the logging must distinguish which action failed and why, while also showing the overall rule execution status.

Additionally, delayed actions (those with `delayDuration`) have their own lifecycle: they are scheduled at rule execution time but execute minutes, hours, or days later. The logging system must track the full lifecycle of delayed actions independently of the initial rule execution.

## Options Considered

1. **Rule-level only (single table)** — One `automation_executions` table with a row per rule evaluation. Action results are stored as a JSONB array within the execution record.
   - Pros:
     - Simple schema — one table
     - One row per rule firing, easy to count and query
     - All data in one place
   - Cons:
     - Cannot independently track delayed action status (JSONB array is hard to query/update)
     - JSONB array grows with action count, making updates awkward (must read-modify-write the whole array)
     - No FK from scheduled action records — delayed action scheduler cannot easily find and update specific action results
     - Querying "show me all failed actions" requires JSONB array scanning across all executions

2. **Action-level only (single table)** — One `action_executions` table with a row per action execution. No rule-level summary record.
   - Pros:
     - Fine-grained — every action has its own record
     - Delayed actions have independent lifecycle
     - Easy to query by action type, status, etc.
   - Cons:
     - No quick answer to "did the rule fire?" — must aggregate action records
     - Rule-level metadata (trigger event data, condition evaluation) has no natural home
     - Execution list view requires GROUP BY or subqueries to show per-rule summaries
     - Condition evaluation results (conditions not met → rule didn't fire) require a special "no-action" record

3. **Both rule-level + action-level (two tables)** — `automation_executions` for rule-level summary (one row per rule evaluation) and `action_executions` for per-action detail (one row per action within an execution). Parent-child relationship via FK.
   - Pros:
     - Rule-level table answers "did the rule fire?" with a single row lookup
     - Action-level table answers "which action failed?" with per-action records
     - Delayed actions have independent status tracking (`SCHEDULED → COMPLETED/FAILED/CANCELLED`)
     - Condition evaluation results stored at rule level (clean separation)
     - List views query `automation_executions`; detail views join to `action_executions`
     - Each table has clear, non-overlapping responsibilities
   - Cons:
     - Two tables instead of one — slightly more complex schema
     - Must keep rule-level status in sync with action-level outcomes (e.g., update to `ACTIONS_FAILED` when any action fails)
     - More rows overall (N executions x M actions)

4. **Single table with parent-child self-reference** — One `automation_executions` table with a nullable `parent_id` FK to itself. Rule-level records have `parent_id = null`; action-level records reference their parent.
   - Pros:
     - Single table (simpler migration)
     - Self-referential FK is a common pattern
   - Cons:
     - Overloaded table — rule-level and action-level records have different column semantics
     - Many columns are nullable depending on whether it's a rule or action record
     - Queries must filter by `parent_id IS NULL` (rules) or `parent_id IS NOT NULL` (actions)
     - Less clear than two purpose-built tables
     - Indexing is awkward — different queries need different indexes on the same table

## Decision

Option 3 — Both rule-level (`AutomationExecution`) and action-level (`ActionExecution`) tables.

## Rationale

The two-table approach maps cleanly to the two questions admins need answered. The execution list page shows rule-level data: which rule fired, when, was it successful. This is a simple query on `automation_executions` — no aggregation, no JSONB scanning. The execution detail view shows action-level data: which actions ran, which succeeded, which failed, what was the error. This is a join to `action_executions` — one query, clear results.

The strongest argument for two tables is delayed actions. A delayed action has its own lifecycle that extends beyond the initial rule execution. The rule execution might complete with status `ACTIONS_COMPLETED` (all immediate actions succeeded), but a scheduled action is still `PENDING`. Hours later, the scheduler executes the delayed action and updates its status to `COMPLETED` or `FAILED`. This lifecycle is natural in a separate `action_executions` table with its own `status`, `scheduled_for`, and `executed_at` columns. In a single-table or JSONB approach, tracking this lifecycle requires awkward partial updates.

The synchronization concern (keeping rule-level status consistent with action-level outcomes) is straightforward: the `AutomationActionExecutor` updates the parent `AutomationExecution.status` to `ACTIONS_FAILED` when any action fails, and to `ACTIONS_COMPLETED` when all immediate actions succeed. Delayed actions do not update the parent status — they have their own lifecycle.

The two-table approach adds one table compared to Options 1 and 2 but eliminates significant query complexity and JSONB manipulation. Given that execution logs are a primary debugging tool for admins, query simplicity justifies the schema cost.

## Consequences

- `automation_executions` stores one row per rule evaluation — includes trigger event data, conditions met, overall status
- `action_executions` stores one row per action within an execution — includes status, scheduled time, result data, error detail
- Execution list views query `automation_executions` only (fast, no joins)
- Execution detail views join to `action_executions` (one-to-many, indexed by `execution_id`)
- Delayed actions are tracked as `ActionExecution` records with `status = SCHEDULED` and `scheduled_for` timestamp
- The `AutomationScheduler` queries `action_executions WHERE status = 'SCHEDULED' AND scheduled_for <= now()`
- Rule-level status reflects immediate action outcomes only; delayed action outcomes are tracked independently
