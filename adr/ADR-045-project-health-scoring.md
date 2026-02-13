# ADR-045: Project Health Scoring — Discrete Rule-Based Algorithm

**Status**: Accepted

**Context**: Phase 9 introduces project health scoring — a computed indicator that tells users at a glance whether a project is on track, at risk, or in trouble. This health status appears in the company dashboard's project list, the project overview tab's header badge, and eventually in notification triggers (e.g., "Project X moved to CRITICAL").

The scoring algorithm must be deterministic (same inputs always produce the same output), explainable (users understand *why* a project has a given status), and computable from existing data (no new data collection requirements). The inputs available are: task counts (total, done, overdue), budget consumption percentage (nullable — not all projects have budgets), and activity recency (days since last audit event for the project).

The health status is **never persisted** — it is computed from current data every time it is requested, cached for 1-3 minutes in Caffeine. This avoids stale scores and eliminates an entire class of bugs related to score invalidation on data changes.

**Options Considered**:

1. **Weighted numeric score (0-100)** -- Compute a single number from weighted factors: e.g., `score = 0.3 * taskCompletionScore + 0.3 * budgetScore + 0.2 * activityScore + 0.2 * overdueScore`. Map ranges to labels: 80-100 = Healthy, 50-79 = At Risk, 0-49 = Critical.
   - Pros:
     - Single numeric value is easy to sort and compare across projects
     - Granular — can distinguish between a "barely healthy" 81 and a "very healthy" 98
     - Familiar pattern (credit scores, health check scores)
     - Can be extended with new factors by adding weighted terms
   - Cons:
     - **Not explainable**: A score of 62 does not tell the user *why* the project is at risk. Is it budget? Overdue tasks? Inactivity? The user must dig deeper to find the root cause.
     - **Weights are arbitrary**: What makes task completion 30% of health and budget 30%? These weights are opinionated guesses that may not match user mental models. Different organizations value different factors — a consulting firm cares about budget; a product team cares about velocity.
     - **False precision**: The difference between a score of 72 and 74 is meaningless, but the numeric format implies it is meaningful. Users will ask "why did my score drop from 85 to 83?" when the answer is noise.
     - **Harder to test**: Testing that a specific input combination produces exactly 73.5 is brittle. Changing any weight changes every test.
     - Budget is optional (null if no budget configured). The weighting scheme must handle absent factors by redistributing weight, adding complexity.

2. **Discrete rule-based algorithm with explanatory reasons** -- Evaluate a series of rules against project metrics. Each rule can trigger AT_RISK or CRITICAL status with a human-readable reason. The worst status wins. The reasons array is returned alongside the status.
   - Pros:
     - **Explainable**: Users see "Budget 85% consumed but only 50% of tasks complete" — not just a yellow dot. The reasons array is the primary value proposition.
     - **Debuggable**: When a project is AT_RISK, the reasons tell you exactly which rules fired. No need to reverse-engineer a numeric score.
     - **Testable**: Each rule is tested independently. Adding a new rule does not affect existing tests.
     - **Graceful with missing data**: If budget is null, budget rules are simply skipped. No weight redistribution needed.
     - **Actionable**: Each reason suggests a corrective action. "5 overdue tasks" implies "go triage your overdue tasks." A score of 62 implies nothing.
     - **Extensible**: New rules can be added without changing existing rules or their behavior. Future: "No time logged in 7 days", "Milestone deadline approaching", etc.
   - Cons:
     - Less granular — cannot distinguish between "slightly at risk" and "very at risk." A project with 1 AT_RISK reason and a project with 3 AT_RISK reasons both show as AT_RISK.
     - Sorting within a severity tier requires a secondary sort key (e.g., completion percentage), not the health status itself.
     - Rule thresholds (30% overdue = critical, 10% = at-risk, 14-day inactivity) are still somewhat arbitrary — but they are individually tunable and their meaning is transparent.

3. **ML-based predictive health scoring** -- Train a model on historical project data to predict the likelihood of a project missing its deadline or exceeding its budget. Use the model's prediction as the health score.
   - Pros:
     - Potentially more accurate than hand-coded rules — can capture non-obvious patterns
     - "Predictive" rather than "reactive" — can flag projects before they become critical
     - Differentiating feature for the platform
   - Cons:
     - **No training data**: The platform is new. There is no historical dataset of projects with known outcomes to train on.
     - **Black box**: Users cannot understand why the model flagged their project. "The AI says your project is at risk" is not actionable.
     - **Infrastructure**: Requires model training pipeline, model serving (either embedded or as a service), model retraining schedule, and monitoring for data drift.
     - **Massive over-engineering** for the current stage. Rule-based scoring can be upgraded to ML scoring in a future phase if training data accumulates.
     - Cannot degrade gracefully for projects with sparse data (few tasks, no time entries).

**Decision**: Discrete rule-based algorithm with explanatory reasons (Option 2).

**Rationale**:

1. **Explainability is the primary UX differentiator**: The value of project health scoring is not the colored dot — it is the reasons underneath. Users do not just want to know a project is "at risk"; they want to know *why* and *what to do about it*. The reasons array maps directly to corrective actions: "Budget 85% consumed but only 50% of tasks complete" tells the project lead to either request more budget or accelerate task completion. A numeric score of 62 tells them nothing.

2. **Missing data is a first-class concern**: Not all projects have budgets (Phase 8 budget setup is optional). Not all projects use due dates on tasks. A scoring system must handle absent data gracefully, not produce misleading results. The rule-based approach handles this naturally: if budget is null, budget rules are skipped; if no tasks have due dates, overdue rules produce zero. The weighted numeric score approach would need complex weight redistribution logic for each combination of absent factors.

3. **Transparency builds trust**: When users see "CRITICAL: 12 of 30 tasks overdue", they can verify the claim by looking at the task list. When they see "Score: 38", they have no way to verify or dispute the number. Trust in the health indicator depends on users being able to validate it against their own understanding of the project.

4. **Implementation simplicity**: A pure function with 6 rules is trivial to implement, test, and maintain. The `ProjectHealthCalculator` class has no dependencies, no state, and no side effects. It can be fully unit-tested with ~15 test cases covering every rule individually and in combination. Adding a new rule is a 5-line change with a 2-line test.

5. **Future extensibility**: The rule-based system can evolve in several directions without architectural changes:
   - **Configurable thresholds**: The 30%/10% overdue ratios and 14-day inactivity window can be moved to `OrgSettings`, allowing each organization to tune sensitivity.
   - **Additional rules**: "No time logged in 7 days", "Milestone deadline within 3 days", "Budget increase requested" — each is an independent rule that fires or not.
   - **ML augmentation**: A future phase could add an ML-based rule that runs alongside hand-coded rules, contributing its own reason ("Predicted risk based on historical patterns"). The rule-based framework accommodates this without restructuring.

**Consequences**:
- Health status is always one of four values: HEALTHY, AT_RISK, CRITICAL, UNKNOWN. No numeric scores, no percentages, no ordinal rankings beyond severity ordering.
- Every health response includes a `healthReasons` array (possibly empty for HEALTHY). The frontend renders these as pill badges or tooltip text.
- Health is computed on every request (cached for 1 minute at the project level). It is never stored in the database, avoiding stale-score bugs.
- The health algorithm is a pure function (`ProjectHealthCalculator.calculate()`) with no Spring dependencies. It is fully unit-testable without Spring context.
- Rule thresholds are compile-time constants initially. A future enhancement can read them from `OrgSettings` for per-org configurability.
- The four-tier severity model (HEALTHY > AT_RISK > CRITICAL > UNKNOWN) provides a natural sort order for the project health list: CRITICAL projects surface to the top of the company dashboard.
- Health scoring does not account for financial metrics beyond budget consumption (e.g., margin is not a health input). Budget consumption is a capacity concern; margin is a financial concern. They serve different audiences and are surfaced in different widgets.
