# ADR-271: `SCHEDULED` Trigger Extension to Phase 37 Trigger Registry

**Status**: Accepted

**Context**:

Phase 37 ships eight `TriggerType` values, all event-driven: `TASK_STATUS_CHANGED`, `PROJECT_STATUS_CHANGED`, `CUSTOMER_STATUS_CHANGED`, `INVOICE_STATUS_CHANGED`, `TIME_ENTRY_CREATED`, `BUDGET_THRESHOLD_REACHED`, `DOCUMENT_ACCEPTED`, `INFORMATION_REQUEST_COMPLETED`. Phase 37 has a delayed-action mechanism (`AutomationScheduler` polls every 15 minutes for `ActionExecution.scheduledFor <= now()`) but that mechanism is for follow-ups *after* an event-driven rule fires — it cannot fire a rule purely on a clock, with no event to trigger from.

Phase 70 needs purely-clock-driven rules. The pre-seeded "Weekly matter activity summary" template (Phase 70 Section 5.6) must fire every Monday 07:00 on every active matter, with no preceding event. Without a clock-driven trigger, that template cannot exist; the partner who wants automated Monday-morning catch-up summaries has no path. The "Catch-up summary on matter reactivation" template *can* live on the existing `PROJECT_STATUS_CHANGED` trigger, but the weekly template cannot.

The decision is whether to add a `SCHEDULED` trigger type to Phase 37's existing `TriggerType` enum (extending the engine slightly) or build a parallel scheduled-rule mechanism specifically for Phase 70.

**Options Considered**:

1. **Parallel mechanism: a Phase 70-specific `ScheduledAiInvocation` table with its own scheduler.** Add a new table tracking "every Monday 07:00, invoke specialist X with config Y on every matter matching condition Z"; a new Phase-70-owned `ScheduledAiInvocationScheduler` polls and dispatches.
   - Pros:
     - Phase 37 stays untouched. No risk of regression.
     - Phase 70's scheduling logic is co-located with its other code.
   - Cons:
     - Duplicates concepts: rule + condition + action already exist in Phase 37 as `AutomationRule` + `conditions` + `AutomationAction`. A parallel table re-implements those concepts in a Phase-70-specific shape.
     - The pre-seeded "Weekly matter activity summary" template now has two homes — half is an `AutomationRule` (the template seeder, the action executor), half is a `ScheduledAiInvocation` row (the trigger schedule). Two-database-row-per-template is confusing for support staff and admins debugging "why did my rule fire (or not)?"
     - Phase 71+ wants other scheduled rules (e.g. "every quarter, fire a compliance review on ACTIVE customers") that are *not* AI-specialist-invoking — they're plain `SEND_EMAIL` or `CREATE_TASK` actions on a clock. With a Phase-70-specific scheduler, those rules either need yet another mechanism or the Phase-70 mechanism needs to generalise (and at that point it's just the Phase 37 engine with extra steps).
     - Scheduling-related code (cron parsing, last-run tracking, missed-run handling) is non-trivial; building two of them is twice the complexity for the same correctness.

2. **Extend Phase 37's `TriggerType` with `SCHEDULED` (CHOSEN).** Add `SCHEDULED` to the enum; extend `AutomationRule.triggerConfig` JSONB to optionally carry `cronExpression` and `lastRunAt`; add a new poller (or extend the existing `AutomationScheduler`) that finds rules with `triggerType=SCHEDULED` whose cron expression is due to fire and dispatches them through the same `ActionExecutor` path as event-triggered rules. Phase 70's `INVOKE_AI_SPECIALIST` action plugs in as a normal Phase 37 action executor.
   - Pros:
     - One automation engine, one mental model, one set of audit events. Admins building rules see "Trigger: scheduled (every Monday 07:00) → Condition: project.status=ACTIVE → Action: invoke INBOX specialist". One configuration surface, one place to debug.
     - Phase 70's pre-seeded "Weekly matter activity summary" template is just a normal `AutomationRule` row with `triggerType=SCHEDULED` and an `INVOKE_AI_SPECIALIST` action. Same template seeder, same execution log, same UI. No special case.
     - Phase 71+ scheduled rules of any action type get the same primitive for free. "Every quarter, send-email to all ACTIVE customers" is a `SCHEDULED` trigger + `SEND_EMAIL` action — no new infrastructure.
     - The execution-log surface (`AutomationExecution` + `ActionExecution`) already handles "did the rule fire? did conditions pass? did each action succeed?" The scheduled-trigger path inherits that. Admins see scheduled fires alongside event fires in one UI.
     - Implementation cost is bounded: one enum value, one nullable column on the trigger config (`cronExpression`), one nullable column for `lastRunAt`, one cron-evaluation poller (or extension to the existing 15-minute poller), one validation pass on `cronExpression` at rule save.
   - Cons:
     - Touches Phase 37 — strictly speaking, a cross-phase change. Risk of regressing existing event-driven rules. Mitigated by: existing rules have `triggerType` in their existing 8 values, ignore the new poller path, and pass through unchanged; the new path is additive.
     - Cron expressions are user-facing config; UI must support entering / editing them (or a higher-level "every Monday at 07:00" wizard mapping to cron). Phase 70 ships pre-seeded templates only; raw cron entry can be deferred to a UI follow-up.

3. **Use the existing `AutomationScheduler` (which polls for delayed `ActionExecution`s) by recording phantom delayed actions on a clock.** For "every Monday 07:00", on each Monday firing, immediately schedule a follow-up phantom action for next Monday.
   - Pros:
     - Reuses existing scheduler code with zero changes to Phase 37 enums.
   - Cons:
     - Phantom self-rescheduling actions are a confusing model — there is no real triggering event, but the data implies one. Admins debugging "why did this phantom fire?" find nothing in the trigger event log.
     - First-time rule activation has nothing to schedule from — the rule has never fired, so there's no `ActionExecution` to extend. Bootstrapping requires a special-case "kick off the first scheduled run when the rule is enabled" path; at that point the cron-poller solution is simpler and more honest.
     - Drift: if a phantom fires late, its self-rescheduled successor inherits the drift. A 60s-late Monday firing accumulates over the year. Cron-style next-run-from-scheduled-time avoids this.
     - Reuses existing infrastructure in a way that the existing infrastructure was not designed for. Rejecting [ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md)-style "add the right primitive" in favour of "abuse the existing primitive" trades a small ADR for technical debt.

**Decision**: Option 2 — extend Phase 37's `TriggerType` enum with `SCHEDULED`. Add `cronExpression` and `lastRunAt` to the trigger config / `AutomationRule` row (the latter as a nullable column on the entity, the former in the existing `triggerConfig` JSONB). Add a cron-evaluation pass to the existing `AutomationScheduler` (or a sibling scheduler co-located in `automation/`) that fires due rules through the standard `ActionExecutor` dispatch.

**Rationale**:

The Phase 37 engine was designed to be the single automation primitive across the codebase — "configurable rules that react to triggers and perform actions, audited end-to-end." Building a parallel mechanism (Option 1) for AI-only scheduling forks that primitive into two engines, doubling the maintenance, halving the consistency, and creating an awkward "is this rule in the automation page or somewhere else?" question for admins. The cleanest extension is the smallest one: add the missing trigger type to the existing engine.

The risk to Phase 37's stability is bounded. Existing rules use one of the eight existing trigger types; the new poller path filters on `triggerType=SCHEDULED` and never touches them. The `AutomationActionExecutor` dispatch is unchanged — a scheduled rule's actions execute through the same path as an event-triggered rule's actions. Phase 70's `InvokeAiSpecialistActionExecutor` is registered as a normal action executor and works for both invocation paths transparently.

The cross-phase reusability matters. Phase 71's compliance specialist (deferred per Phase 70's scope) will plausibly want quarterly scheduled rules. Phase 73+ retention or audit purges may want daily scheduled rules. Each of those benefits from `SCHEDULED` as a primitive in the shared engine, not as a Phase-70-specific mechanism that everyone else has to extend or re-implement.

The cron-vs-phantom comparison (Option 2 vs Option 3) is decisive on drift: cron evaluates "when is the next Monday at 07:00?" from the wall clock, not from the previous firing. A late firing does not bias the next firing. Phantom self-rescheduling accumulates drift; over a year of weekly fires, that drift becomes visible.

**Consequences**:

- Positive:
  - One trigger registry: existing eight event types + `SCHEDULED`. One automation page, one execution log, one set of audit events. Admins, support staff, and the system have one mental model.
  - Pre-seeded templates from Phase 70 (Section 5.6) — "Weekly matter activity summary", "Catch-up summary on matter reactivation" — both live as normal `AutomationRule` rows. The first uses `SCHEDULED`, the second uses `PROJECT_STATUS_CHANGED`; both flow through the same dispatch.
  - Phase 71+ inherits scheduled triggers for any action type. Compliance scheduled reviews, retention cleanups, monthly digests — all expressible without further engine extension.
  - Cron expression validation at rule save (using a vetted Java cron library — Spring's `CronExpression` or `quartz`'s parser) catches mistyped expressions before they reach the poller.
  - `lastRunAt` is updated atomically with the firing (within the same transaction) so a poller restart mid-firing does not double-fire.

- Negative:
  - Phase 37 schema gets one new column on `automation_rules` (`last_run_at`, nullable `TIMESTAMPTZ`) and one new value on the `TriggerType` enum. The migration is a tenant migration that ALTERs the existing table; minor coordination cost but mechanically simple.
  - Cron-expression UX in the rule wizard is a follow-up. Phase 70 ships only pre-seeded templates with cron expressions hard-coded in the seeder; raw cron entry by admins is deferred. Acceptable: the demo path uses pre-seeded templates only.
  - The `AutomationScheduler` poll loop now does cron evaluation in addition to delayed-action evaluation. Slightly more work per poll cycle; bounded by the small expected count of `SCHEDULED` rules per tenant (low single digits).

- Neutral:
  - The Phase 37 trigger config JSONB schema gets a new variant for `SCHEDULED`: `{"cronExpression": "0 7 * * 1", "timezone": "Africa/Johannesburg"}`. Sealed-class `TriggerConfig` adds a `ScheduledTriggerConfig` record. Same pattern as existing trigger configs.
  - Time-zone handling defaults to `Africa/Johannesburg` (SA pilot scope) but is configurable per-rule in the JSONB. Phase 73+ multi-region tenants can extend.
  - Missed-run handling (poller down for an hour, weekly Monday 07:00 firing missed): on resume, the poller fires the next scheduled run from `lastRunAt + cron-interval`. If the missed run is older than the next scheduled run, the missed run is skipped (no backfill flood). This is a deliberate choice; alternative behaviour would be a config flag in a future phase.

- Related: [ADR-145](ADR-145-rule-engine-vs-visual-workflow.md) (Phase 37's structured rules engine — the foundation this ADR extends), [ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md) (JSONB trigger config — `cronExpression` lives in the JSONB), [ADR-265](ADR-265-specialist-as-prompt-tools-launcher-metadata.md) (specialist `automationCapable` flag — only `automationCapable=true` specialists can be the target of a `SCHEDULED` rule's `INVOKE_AI_SPECIALIST` action), [ADR-267](ADR-267-human-approval-default-direct-mode-exception.md) (the DIRECT mode that scheduled Inbox rules use), [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (the invocation table that records `invoked_by=SCHEDULED`).
