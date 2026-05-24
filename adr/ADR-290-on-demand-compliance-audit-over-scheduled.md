# ADR-290: On-Demand Compliance Audit Over Scheduled Sweeps

**Status**: Accepted

**Context**:

Phase 74 introduces a compliance audit AI skill that sweeps the firm's entire client book -- FICA checklists, POPIA processing activities, trust accounting integrity, prescription tracking, record retention -- and produces a graded report with severity-ranked findings and remediation recommendations. The audit aggregates data from 6+ services (CustomerService, ComplianceChecklistService, DataProtectionService, TrustAccountService, PrescriptionTrackerService, RetentionService) into a comprehensive compliance snapshot.

The question is when and how the audit runs. Three trigger models are possible: on-demand (attorney clicks a button), scheduled (automated weekly/monthly sweeps), or continuous (event-driven, reacting to compliance-relevant changes in real time).

The BYOAK (bring-your-own-API-key) model from Phase 72 means every AI invocation costs the tenant real money -- ~$0.01-0.05 per audit depending on data volume and model. This cost consideration, combined with the execution gate requirement (ADR-281: every AI-proposed action requires attorney approval), shapes the trigger model decision.

**Options Considered**:

1. **On-demand only -- "Run audit" button on the compliance dashboard (CHOSEN)** -- The attorney clicks "Run AI Audit" on the compliance dashboard. The skill runs once, produces a report, and creates an execution gate for publication. No automatic scheduling, no background sweeps.
   - Pros:
     - **Zero infrastructure.** No scheduler service, no cron configuration, no background job management, no retry logic for failed scheduled runs. The audit is a regular skill invocation triggered by a user action.
     - **Cost control.** The tenant decides when to spend API tokens on an audit. No surprise charges from a weekly sweep they forgot was running. The firm's monthly AI budget is consumed deliberately, not automatically.
     - **Fits the execution gate model.** Every audit produces a gate that requires attorney review. A scheduled audit that fires at 2 AM creates a gate that no one reviews for hours -- defeating the purpose of mandatory review. On-demand audits are triggered when the attorney is actively engaged.
     - **No stale report risk.** A scheduled weekly audit produces a report on Monday that may be stale by Friday. On-demand audits reflect the current state of the data when the attorney wants to see it.
     - **Concurrent audit prevention is simple.** A boolean check ("is there an IN_PROGRESS audit for this tenant?") prevents double-invocation. Scheduled audits would need more complex deduplication logic.
   - Cons:
     - **Requires manual discipline.** If the firm forgets to run audits, compliance gaps accumulate silently. There is no automatic safety net.
     - **No proactive compliance monitoring.** The system does not alert the firm about emerging compliance issues between manual audits. A prescription deadline approaching next week is only discovered when someone runs an audit.
     - **No trend data.** Without regular audits, the firm cannot track compliance improvement over time. Trend charts require a history of audits at regular intervals.

2. **Scheduled weekly/monthly sweeps** -- A background scheduler runs the compliance audit at a configured interval (weekly or monthly). Results are persisted automatically; critical findings trigger notifications.
   - Pros:
     - **Proactive monitoring.** Compliance gaps are detected automatically, even if the attorney forgets to run a manual audit. Critical findings trigger notifications.
     - **Regular trend data.** Weekly audits produce a time series of compliance grades, enabling trend charts and progress tracking.
     - **Set and forget.** Once configured, the audit runs without human intervention.
   - Cons:
     - **Infrastructure complexity.** Requires a scheduler service (Spring `@Scheduled` + `TenantScopedRunner` iteration across all tenants), configurable cron expressions per tenant, retry logic for failed runs, and a way to pause/resume the schedule. The Phase 72 expiry worker uses `@Scheduled` for a lightweight hourly scan -- a full compliance audit per tenant is heavier (6+ service queries, API call, report persistence).
     - **Unwanted API cost.** A scheduled weekly audit costs the tenant ~$0.04-0.20/month in API tokens (4 audits x $0.01-0.05 each). Small amount, but it accumulates across tenants and adds to the monthly budget without explicit consent. Some tenants may not want automatic spending.
     - **Gate backlog.** Scheduled audits produce execution gates at unpredictable times. If the firm doesn't review gates promptly, they expire after 72 hours (ADR-281), and the audit is wasted. A weekly audit that consistently produces expired gates is spending tokens for no value.
     - **Stale data window.** A weekly audit reflects the state at the time of the scan. Data changes between scans are invisible until the next run. On-demand audits are fresher.
     - **Configuration UX.** The settings page needs scheduler configuration: frequency (weekly/monthly), day of week, time of day, enabled/disabled toggle. This is a non-trivial settings panel for a feature that may not be used.

3. **Continuous monitoring (event-driven)** -- Listen for compliance-relevant events (checklist item change, document upload, prescription date update) and re-evaluate compliance in real time. Alert on threshold crossings.
   - Pros:
     - **Real-time compliance posture.** The firm's compliance status is always current. An overdue checklist item triggers an immediate assessment.
     - **No manual trigger needed.** Compliance monitoring is automatic and continuous.
   - Cons:
     - **Massive over-engineering.** Real-time monitoring requires event listeners on 6+ services, a compliance score aggregator that runs on every relevant event, debouncing logic (don't trigger on every keystroke), and a notification pipeline for threshold crossings. This is a monitoring platform, not a skill.
     - **Prohibitive API cost.** Every compliance-relevant event would trigger an AI call. A busy firm could generate hundreds of compliance events per day. At $0.01-0.05 per call, continuous monitoring could cost $5-50/day. Even with debouncing, the cost model is unsustainable under BYOAK.
     - **Alert fatigue.** Continuous monitoring produces continuous alerts. An attorney receiving 10 compliance notifications per day stops reading them within a week.
     - **Not needed at v1 scale.** Kazi's target firms have 2-10 attorneys and 50-500 clients. The compliance posture of a firm this size does not change materially hour-to-hour. A weekly or monthly snapshot is sufficient.

**Decision**: Option 1 -- On-demand compliance audit triggered by a "Run AI Audit" button on the compliance dashboard. No scheduled sweeps, no continuous monitoring.

**Rationale**:

On-demand auditing is the right fit for Phase 74 because it aligns with three foundational constraints: BYOAK cost control (the firm decides when to spend tokens), execution gates (the attorney is present to review the gate immediately after invocation), and infrastructure simplicity (no scheduler, no background jobs, no retry logic).

The main risk of on-demand -- that firms forget to audit -- is acceptable at this stage because Kazi's target firms are small enough that the compliance officer (or managing partner) has direct visibility into the firm's compliance state through existing Phase 14 dashboards. The AI audit is an acceleration tool, not a replacement for manual oversight. If the firm wants proactive monitoring, the natural evolution is a scheduled option (opt-in, with explicit cost consent) in a future phase -- not a mandatory background process.

Continuous monitoring (Option 3) is the theoretically optimal approach but is architecturally premature. The compliance data model would need a stable event taxonomy, the cost model would need a fundamentally different structure (subscription pricing, not BYOAK), and the notification system would need anti-fatigue features (digest mode, severity thresholds, snooze). These are Phase 80+ concerns.

**Consequences**:

- Positive:
  - Zero scheduler infrastructure. The audit is a regular skill invocation -- no new background services, no cron, no retry logic.
  - The firm controls audit timing and cost. No surprise charges from automated sweeps.
  - The attorney is present when the audit runs, ensuring the execution gate is reviewed promptly.
  - Simple concurrent audit prevention: a single boolean check per tenant.

- Negative:
  - No proactive compliance monitoring. Emerging gaps are invisible between manual audits. Mitigated: prescription tracking (Phase 55) already has its own alerting for approaching deadlines. FICA checklist overdue items are visible in the existing Phase 14 dashboard.
  - No automatic trend data. Firms that want trend charts must remember to run audits at regular intervals. Mitigated: the audit history page shows past audits with dates and grades, providing a manual trend view.

- Neutral:
  - The "Run AI Audit" button is disabled while an audit is in progress (prevents concurrent audits). The in-progress check queries `AiExecution` for a `compliance-audit` skill with status `IN_PROGRESS`.
  - A future "scheduled audit" feature can be added by wrapping the on-demand invocation in a `@Scheduled` + `TenantScopedRunner` loop with an opt-in flag on `AiFirmProfile`. The skill itself does not change -- only the trigger mechanism.
  - The compliance dashboard "AI Audit" tab shows a prompt to run an audit if no audits exist yet, reducing the forgetting-to-audit risk for new setups.

- Related: [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) (execution gates -- audit publication requires attorney approval), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md) (BYOAK cost model -- on-demand gives cost control), [ADR-291](ADR-291-compliance-findings-persistent-lifecycle.md) (finding persistence -- findings created on gate approval)
