# Automation

**Status:** Phase C — filled.
**Bounded context:** see [`10-bounded-contexts.md` § automation](../10-bounded-contexts.md#automation).

## 1. Purpose

Tenant-defined rules that fire over **domain events**, evaluate **conditions**, and chain **ordered actions**. Triggers are event-based (any class in the sealed `DomainEvent` hierarchy), **scheduled** (cron), or **field-date-based** (custom DATE fields nearing a deadline). Actions are executed inline, **delayed** (poll-based catch-up), or **scheduled** (cron-fired). `INVOKE_AI_SPECIALIST` is a first-class action type — its output lands in a human-approval queue by default, with a direct-mode exception (ADR-267).

There is no "Workflow" entity; the canonical noun is **Automation Rule** (`glossary.md:287, 322`). The shape is a rule engine, not a visual workflow (canonical: ADR-145; carve-out for accounting sync: ADR-274).

## 2. Entities owned

| Entity | Anchor | Notable fields |
|---|---|---|
| `AutomationRule` | `→ backend/.../automation/AutomationRule.java:20` | `name`, `enabled`, `triggerType`, `triggerConfig (jsonb)`, `conditions (jsonb)`, `source` (`RuleSource` enum), `templateSlug` |
| `AutomationAction` | `→ backend/.../automation/AutomationAction.java:19` | `ruleId`, `sortOrder`, `actionType` (`ActionType` enum), `actionConfig (jsonb)`, `delayDuration`, `delayUnit` (`DelayUnit`) |
| `AutomationExecution` | `→ backend/.../automation/AutomationExecution.java:19` | `ruleId`, `triggerEventType`, `conditionsMet`, `status` (`ExecutionStatus`), `startedAt`, `errorMessage` |
| `ActionExecution` | `→ backend/.../automation/ActionExecution.java` (sibling, surfaced by repo) | Per-action execution row; `ActionExecutionStatus` includes `SCHEDULED` for delayed actions |
| `FieldDateNotificationLog` | `→ backend/.../automation/FieldDateNotificationLog.java` | Dedupe log keyed by entity + field + threshold for the field-date scanner |

Supporting enums (`glossary.md:34, 84, 103, 241, 272`): `ActionType` (`CREATE_TASK, SEND_NOTIFICATION, SEND_EMAIL, UPDATE_STATUS, CREATE_PROJECT, ASSIGN_MEMBER, INVOKE_AI_SPECIALIST`), `ConditionOperator`, `DelayUnit` (`MINUTES, HOURS, DAYS`), `RuleSource` (`MANUAL, PACK, SYSTEM`), `TriggerType` (event types + `FIELD_DATE_APPROACHING`, `SCHEDULED`).

JSONB-on-config (rather than normalised condition/action tables) is canonical — ADR-148.

## 3. REST surface

`AutomationRuleController` — `/api/automation-rules` — ~11 endpoints (`_discovery/A1-backend-map.md:399`):

- `GET /api/automation-rules` — list
- `POST /api/automation-rules` — create
- `GET /api/automation-rules/{id}` — read
- `PUT /api/automation-rules/{id}` — update
- `DELETE /api/automation-rules/{id}` — delete
- `POST /api/automation-rules/{id}/toggle` — enable/disable
- `POST /api/automation-rules/{id}/duplicate` — clone
- `GET /api/automation-rules/templates` — list pack-shipped templates
- `POST /api/automation-rules/{id}/test` — dry-run a rule (see Open Questions §10)
- `GET /api/automation-rules/executions` — execution history
- `GET /api/automation-rules/executions/{id}` — execution detail

Sibling controllers in the package: `AutomationActionController`, `AutomationExecutionController`. AI invocation queue endpoints (Phase 515 series, surfaced by frontend `lib/api/ai-invocations.ts`) live under the AI Queue route group — they are produced by the `INVOKE_AI_SPECIALIST` action and consumed by the AI Queue page (§4).

Capability gate: `AUTOMATIONS` (`glossary.md:67`). Module gate: `automation_builder` (`_discovery/A2-frontend-map.md:486`).

## 4. Frontend pages / components

| Page / module | Anchor | Notes |
|---|---|---|
| Rules list + editor | `frontend/.../settings/automations/page.tsx` (`A2-frontend-map.md:240-241`) | Module-gated `automation_builder`, admin-only. Rich rule-builder UI (trigger picker, condition rows, ordered action list with per-action delay config). |
| AI Queue review | `frontend/.../settings/automations/ai-queue/page.tsx` (`A2-frontend-map.md:409`, `glossary.md:40`) | Surfaces AI specialist invocations queued for human approval (ADR-267). |
| API client | `frontend/lib/api/automations.ts`, `lib/api/ai-invocations.ts` | |
| Components | `components/automations/` — `RuleList`, automation rule editor (`A2-frontend-map.md:438`) | |

Admin gating + `automation_builder` module gating are enforced server-side by `@RequiresCapability(AUTOMATIONS)` and `verticalModuleGuard.requireModule("automation_builder")`; the frontend mirrors both.

## 5. Domain events

**Subscribed to:** `DomainEvent` (sealed root) — `AutomationEventListener` is a **universal subscriber**. It uses `@EventListener` (no `@TransactionalEventListener` phase) because automation needs in-flight access to the source transaction's state; secondary consumers (notifications, portal-read-model, audit) are deliberately `AFTER_COMMIT` (`10-bounded-contexts.md:232`).

> `AutomationEventListener.onDomainEvent(DomainEvent)` — `→ automation/AutomationEventListener.java:25,54`
>
> Routes every `DomainEvent` permit through `TriggerTypeMapping` → matched rules → `ConditionEvaluator` → `AutomationActionExecutor` (`A1-backend-map.md:474`).

This means **every event the platform emits is observed by automation**. Trigger types currently mapped (`glossary.md:272`): `TASK_STATUS_CHANGED, PROJECT_STATUS_CHANGED, CUSTOMER_STATUS_CHANGED, INVOICE_STATUS_CHANGED, TIME_ENTRY_CREATED, BUDGET_THRESHOLD_REACHED, DOCUMENT_ACCEPTED, INFORMATION_REQUEST_COMPLETED, PROPOSAL_SENT, FIELD_DATE_APPROACHING, SCHEDULED`. Events without a `TriggerType` mapping are dropped silently at step 1 of `processEvent` (anchor: `AutomationEventListener.java:70-74`).

**Published:** `FieldDateApproachingEvent` from `FieldDateScannerJob` (`→ automation/fielddate/FieldDateApproachingEvent.java`, `glossary.md:132`). This is the only event the automation module emits — it is a self-loop into its own listener via the trigger type `FIELD_DATE_APPROACHING`.

## 6. Cross-cutting touchpoints

### 6.1 Scheduler

`AutomationScheduler` (`→ automation/AutomationScheduler.java:24`) runs two pollers:

| Method | Cadence | Purpose |
|---|---|---|
| `pollDelayedActions` | `@Scheduled(fixedDelay = 15 min)` (`AutomationScheduler.java:61`) | Picks up `ActionExecution` rows in `SCHEDULED` status whose `scheduledFor` time has passed and executes them. Implements ADR-147 (delayed-action-scheduling). |
| `pollScheduledTriggers` | `@Scheduled(fixedDelay = 60 s)` (`AutomationScheduler.java:78`) | Evaluates rules with `triggerType=SCHEDULED` against their `cron` expression (parsed via Spring `CronExpression`, **UTC-only** — `AutomationScheduler.java:155-157`) and fires due rules. ADR-271. |

### 6.2 Field-date scanner

`FieldDateScannerJob` (`→ automation/FieldDateScannerJob.java:30,60`) runs daily at **06:00 UTC** (`@Scheduled(cron = "${app.automation.field-date-scan-cron:0 0 6 * * *}")`) — the default the discovery doc flagged as "TBD" (`A1-backend-map.md:491`) is now confirmed. Scans tenant custom fields of DATE type, dedupes via `FieldDateNotificationLog`, and publishes `FieldDateApproachingEvent`.

### 6.3 Universal subscription cost

Automation observes **every** domain event. With `N` rules per tenant, the listener executes `O(N)` `TriggerConfigMatcher` evaluations per event before any condition or action runs. This is the dominant tax of the rule-engine shape over a visual workflow (ADR-145 trade-off accepted). See Open Questions §10 for the threshold.

### 6.4 AI specialist as action

`ActionType.INVOKE_AI_SPECIALIST` (`glossary.md:34`) is treated as a first-class action. Per ADR-267, the **default execution mode is human-approval**: the action enqueues an AI Invocation row for review on the AI Queue page rather than persisting the AI's output directly. **Direct mode** is the documented exception. ADR-265 specifies specialists as prompt-tools-launcher metadata; ADR-266 places launchers inline (primary) with the chat panel secondary; ADR-270 fixes the invocation output as JSONB.

### 6.5 JSONB config

Both `triggerConfig`/`conditions` on the rule and `actionConfig` on the action are JSONB columns (ADR-148). Variable interpolation is handled by `VariableResolver` (`automation/VariableResolver.java`).

### 6.6 Source / template provenance

`AutomationRule.source` is `MANUAL | PACK | SYSTEM`. `templateSlug` ties pack-shipped rules back to their `AUTOMATION_TEMPLATE` pack (`glossary.md:53, 193`) — this is how vertical-specific automation seeds re-key after a pack upgrade.

### 6.7 ScopedValue

`RequestScopes.AUTOMATION_EXECUTION_ID` (`glossary.md:244`) is bound while an action executes, used for audit attribution and as the cycle-detection seed (`AutomationEventListener.java:77-79`, ADR-146).

### 6.8 Cycle detection

ADR-146 — events emitted by an action carry the originating `AUTOMATION_EXECUTION_ID`; the listener short-circuits when it sees its own. The current implementation is a **placeholder** in `processEvent` (anchor: `AutomationEventListener.java:77`); promotion to a real check is open.

## 7. Vertical specifics

Automation is a **terminology-overlaid + module-gated** context (`10-bounded-contexts.md:463`). The engine is universal; what varies is:

- **Module gate.** `automation_builder` is in the gated-module set (`glossary.md:174`). The accounting-ZA and consulting profiles enable it; legal-ZA enables it conditionally per pack.
- **Pack-seeded rule sets.** Vertical-specific default rules ship as `AUTOMATION_TEMPLATE` packs (`PackType` enum, `glossary.md:193`; `10-bounded-contexts.md:244`). On provisioning, `TenantProvisioningService` installs profile-specific automation packs (`10-bounded-contexts.md:461`). E.g., legal-za seeds court-deadline reminders and conflict-check follow-ups; accounting-za seeds invoice-overdue and bank-reconciliation prompts.
- **Carve-out: accounting sync is not a rule.** ADR-274 — accounting-system synchronisation is a dedicated sync service, **not** an automation rule, even though it is event-driven. Don't try to model Xero push as an automation action.

## 8. Active ADRs

Canonical: **ADR-145** (rule-engine-vs-visual-workflow). Cluster from `90-adr-index.md:295-306`:

| ADR | Title |
|---|---|
| ADR-070 | pre-calculated-next-execution-date |
| ADR-071 | daily-batch-scheduler |
| ADR-092 | auto-apply-strategy |
| ADR-145 | rule-engine-vs-visual-workflow (canonical shape) |
| ADR-146 | automation-cycle-detection |
| ADR-147 | delayed-action-scheduling |
| ADR-148 | jsonb-config-vs-normalized-tables |
| ADR-149 | execution-logging-granularity |
| ADR-198 | post-create-action-execution |
| ADR-271 | scheduled-trigger-extension |
| ADR-274 | dedicated-accounting-sync-service-not-rule-engine (carve-out) |

AI-specialist-as-action cluster (`90-adr-index.md:319-329`):

| ADR | Title |
|---|---|
| ADR-265 | specialist-as-prompt-tools-launcher-metadata |
| ADR-266 | inline-launchers-primary-chat-panel-secondary |
| ADR-267 | human-approval-default-direct-mode-exception |
| ADR-270 | ai-specialist-invocation-jsonb-output |

## 9. Key flows

- `50-flows/automation-trigger-to-action.md` — DomainEvent → `AutomationEventListener` → trigger-type map → rule fan-out → condition eval → ordered action execution (inline + scheduled). Anchored to `AutomationEventListener.java:25,54,68` and `AutomationActionExecutor`.
- `50-flows/ai-specialist-invocation.md` — `INVOKE_AI_SPECIALIST` action → AI Invocation queue row → AI Queue page review (ADR-267) → approve/reject → output persisted as JSONB (ADR-270).
- `50-flows/scheduled-trigger.md` — `pollScheduledTriggers` (60 s) → `CronExpression` (UTC) → matched rules → `AutomationActionExecutor`. ADR-271.
- `50-flows/field-date-trigger.md` — `FieldDateScannerJob` (06:00 UTC) → custom-field scan + dedupe via `FieldDateNotificationLog` → `FieldDateApproachingEvent` → back through the universal listener.

(Pages to be authored when `50-flows/` is populated. Anchors above are the source of truth.)

## 10. Open questions / known fragility

- **Universal-subscription perf threshold.** `AutomationEventListener` matches every rule on every event. At what per-tenant rule count does trigger matching become a hot path? `TriggerConfigMatcher` should be measured; today there is no rule-count cap or short-circuit by trigger type at the SQL layer.
- **Action ordering when multiple rules match the same event.** `AutomationAction.sortOrder` orders actions **within** a rule. Cross-rule ordering when N rules fire on one event is not specified — current behaviour is rule-iteration order from the repository (effectively undefined). ADR worth raising.
- **AI specialist queue backlog handling.** The AI Queue is human-paced; there is no documented TTL, max-queue-depth, or backpressure into the firing rule. A burst of `INVOKE_AI_SPECIALIST` actions can pile up indefinitely.
- **Test mode (`POST /api/automation-rules/{id}/test`) semantics.** Does the dry-run bypass the universal subscriber (synthetic event + isolated execution row), or replay through it? The controller endpoint exists (`A1-backend-map.md:399`) but the flow is not anchored — needs verification before relying on it for diagnostics.
- **Cycle detection is a placeholder.** `isCycleDetected(event)` at `AutomationEventListener.java:77` is currently a stub. ADR-146 specifies the contract; promotion is outstanding.
- **UTC-only cron.** `pollScheduledTriggers` parses cron expressions in UTC by deliberate decision (`AutomationScheduler.java:155-157`). Tenants in non-UTC zones expressing "every weekday 09:00 local" must precompute the offset, and DST will drift their schedule by an hour twice a year.
- **Field-date scanner cadence.** Daily at 06:00 UTC is fine for "due in 7 days" reminders; for "due in 4 hours" use cases the cadence is too coarse and a different mechanism is needed (likely a timer-per-due-date job, not a daily scan).
