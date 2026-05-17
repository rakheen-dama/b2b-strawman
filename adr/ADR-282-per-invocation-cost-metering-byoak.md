# ADR-282: Per-Invocation Cost Metering with BYOAK

**Status**: Accepted

**Context**:

Phase 72 AI skills make Anthropic API calls using the tenant's own API key (BYOAK — Bring Your Own API Key, established in Phase 21/52). Each API call has a cost determined by the model, input tokens, output tokens, and prompt cache usage. Tenants need visibility into their AI spend and the ability to set monthly budgets. The question is how granular cost tracking should be and whether the platform should subsidise or mediate API spend.

Current state: Phase 70's `AiLlmCall` entity tracks per-API-call token counts (input, output, cache read, cache creation) for streaming specialist interactions. However, it does not calculate cost in currency, does not aggregate per-tenant spend, and does not enforce budgets. The token counts are telemetry for debugging, not cost management.

**Options Considered**:

1. **Per-invocation cost tracking with tenant budget enforcement (CHOSEN)** — Each `AiExecution` records the exact token counts and pre-calculated cost in ZAR cents. `AiCostService` aggregates monthly spend per tenant and enforces optional monthly budgets. Budget alerts at 80% and 100% thresholds.
   - Pros:
     - **Full cost transparency.** Each skill invocation shows its cost. The tenant knows exactly how much each FICA verification or matter intake analysis costs.
     - **Budget control.** Firms can set monthly caps to prevent runaway spend. Budget enforcement is pre-flight (checked before the API call, not after).
     - **Aggregation is cheap.** `SUM(cost_cents) WHERE created_at >= month_start` on the `ai_executions` table is a simple indexed query. No separate aggregation table needed for v1 volumes (~50-200 invocations/month per firm).
     - **ZAR-denominated.** Tenants think in ZAR, not USD. Converting at execution time (using a configurable exchange rate) avoids exposing foreign currency complexity to the UI.
     - **Budget alerts reuse existing infrastructure.** Phase 8's budget alert pattern (notification at threshold crossings) applies directly.
   - Cons:
     - Exchange rate drift. The configurable USD-to-ZAR rate is a point-in-time approximation. If the rate changes significantly mid-month, the aggregated ZAR cost will be slightly inaccurate relative to actual Anthropic billing. Acceptable for v1 — Anthropic bills in USD, so the tenant's actual spend is visible on their Anthropic dashboard. Kazi's metering is an estimate, not a billing system.
     - Per-invocation tracking adds a write per skill call. At v1 volumes (~50-200/month per firm), this is negligible. At future scale (if bulk invocation is added in Phase 73+), the write volume increases but remains within Postgres's capacity for a tenant schema.

2. **Batch estimation (no per-invocation tracking)** — Estimate monthly cost based on average token usage per skill type. Display estimated spend on the settings page without tracking individual invocations.
   - Pros:
     - No per-invocation write overhead. Simpler implementation.
     - No exchange rate management — display token counts only, let the tenant map to cost on Anthropic's dashboard.
   - Cons:
     - **No cost transparency.** The tenant cannot see how much a specific FICA verification cost. They have no basis for evaluating whether AI is worth the spend.
     - **No budget enforcement.** Without per-invocation tracking, the system cannot check whether the budget is exhausted before making an API call.
     - **Estimation accuracy degrades.** Different invocations have wildly different token counts depending on document size, checklist length, and matter complexity. An average is misleading.
     - **Undermines BYOAK value proposition.** BYOAK means the tenant controls their spend. Without granular metering, they have less control than they would with a platform-mediated key.

3. **No cost metering** — Tenants use their Anthropic dashboard to track API spend. Kazi provides no cost visibility or budget management.
   - Pros:
     - Zero implementation cost. No new entities, no aggregation, no budget logic.
     - Anthropic's dashboard provides accurate, real-time cost data.
   - Cons:
     - **Terrible user experience.** The firm's managing partner or bookkeeper must log into Anthropic separately to see spend. There is no connection between Kazi's AI features and Anthropic's billing dashboard — no way to see "this FICA verification cost R42."
     - **No budget enforcement.** A junior attorney could invoke 100 FICA verifications without realising the cost implications. The managing partner sees the bill at month end.
     - **Devalues the product.** A practice management tool that includes AI skills but outsources cost management to the AI vendor feels incomplete. Cost visibility is table stakes.
     - **Blocks future features.** AI cost allocation to projects/clients (Phase 73+ candidate) requires per-invocation cost data. If we don't track it now, we cannot retroactively add it.

**Decision**: Option 1 — Per-invocation cost tracking in ZAR cents with tenant budget enforcement.

**Rationale**:

BYOAK is a core product decision: each tenant provides their own Anthropic API key, there is no platform-subsidised token spend, and per-tenant cost visibility is mandatory. This decision was made during the Phase 72 ideation session (2026-05-15) and reflects the "no PlanTier" strategy — AI features are capability-gated, not tier-gated, and the tenant bears the direct API cost.

Per-invocation tracking is the natural corollary. If the tenant bears the cost, the tenant must see the cost. And if they can see the cost, they should be able to set limits. The implementation cost is low: one `BIGINT cost_cents` column on `AiExecution`, one `SUM()` query, one pre-flight budget check, and two notification thresholds. The exchange rate imprecision is acceptable because Kazi's metering is operational (budget enforcement, cost visibility), not financial (invoicing, reconciliation). The tenant's actual bill comes from Anthropic.

The alternative (batch estimation or no metering) fails the "would a staff engineer approve this?" test. A product that charges for AI usage but cannot tell the user what they spent is incomplete.

**Consequences**:

- Positive:
  - Full cost transparency: each execution shows model, tokens, cost in ZAR.
  - Budget enforcement: optional monthly cap checked pre-flight, prevents runaway spend.
  - Budget alerts at 80% and 100% notify `AI_MANAGE` members.
  - Foundation for future cost allocation (per-project, per-client AI spend in Phase 73+).

- Negative:
  - Exchange rate is a static configuration value, not a live feed. Drift between the configured rate and the actual rate means ZAR costs are estimates. Acceptable for v1; a live rate feed could be added later.
  - Per-invocation writes add marginal load. At v1 volumes (tens per day per tenant), this is negligible.

- Neutral:
  - Pricing configuration lives in `application.yml` — model-specific rates (input/output per million tokens, cache read/creation per million tokens) and USD-to-ZAR exchange rate. Updated via deployment when Anthropic changes pricing.
  - `AiExecution.cost_cents` is pre-calculated at write time. There is no recalculation path — if the exchange rate changes, historical costs remain at the original rate. This is correct: the cost was X at the time of the invocation.
  - Phase 70's `AiLlmCall` continues to track per-API-call telemetry for streaming specialists. Phase 72's `AiExecution` tracks per-skill-invocation cost for one-shot skills. The two entities serve different purposes and do not overlap.

- Related: [ADR-267](ADR-267-human-approval-default-direct-mode-exception.md) (BYOAK key flow), [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (AiProvider port — the interface that returns token counts), [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (AiSpecialistInvocation — separate entity for specialist telemetry), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant — cost data is tenant-scoped)
