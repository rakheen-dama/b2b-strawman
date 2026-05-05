# ADR-277: Polling Over Webhooks for Payment Reconciliation in v1

**Status**: Accepted

**Date**: 2026-05-03

**Context**: Phase 71 needs to update Kazi's invoice-payment status when the firm's bookkeeper marks an invoice paid in Xero (typically via Xero's bank-feed reconciliation). Two mechanisms are available: Xero outbound webhooks ("invoice updated" events POSTed to a Kazi-hosted URL) or scheduled polling of Xero's `Invoices?modifiedAfter=...` endpoint.

Webhook delivery from third-party SaaS to a multi-tenant backend has well-known operational baggage: a public HTTPS endpoint per tenant (or a single endpoint with tenant routing via signed payload), webhook-secret rotation, signature verification, replay defence, idempotency, retries on the *receiver* side because Xero won't always retry exactly, and the operational cost of a per-tenant webhook configuration step in the connect flow. Polling has its own costs: rate-limit consumption, latency floor, and "we don't see the update for up to N minutes."

For Phase 71 the SLO is "AR-aging is current within 15 minutes of payment in Xero." For a small accounting / law / consulting firm this is well within tolerance — the previous state (no integration) had AR drift of 24+ hours.

**Options Considered**:

1. **15-minute scheduled poll only (no webhooks).** A `@Scheduled(fixedDelay=900_000)` worker iterates `CONNECTED` Xero connections per tenant and calls `getInvoicesModifiedSince(lastPollAt - PT5M)`. Tenant-configurable down to 5 minutes via the integration settings UI.
   - Pros: No public-endpoint configuration burden — works for any deployment topology, including local-dev and air-gapped.
   - Pros: No webhook-secret rotation, no signature verification, no per-tenant webhook URL provisioning.
   - Pros: Deterministic poll cadence — backpressure and rate-limit budgeting are simple.
   - Pros: 15-min SLO meets the v1 product requirement.
   - Cons: Up-to-15-minute lag between Xero update and Kazi reflection.
   - Cons: Per-tenant rate-limit cost (one paginated call every 15 minutes) — Xero's 60/min, 5000/day limits are easily within reach.

2. **Xero outbound webhooks only.** Each tenant configures Xero to webhook to `https://kazi.tld/webhook/xero/{tenantSlug}` on invoice update.
   - Pros: Near-real-time updates (~seconds).
   - Cons: Requires public HTTPS endpoint — incompatible with on-prem / air-gapped deployments and inconvenient in local dev.
   - Cons: Webhook delivery is not perfectly reliable; receiver must implement idempotency and reconciliation anyway.
   - Cons: Per-tenant webhook configuration step in connect flow — significant additional UX surface.
   - Cons: Webhook signature verification, replay defence, secret rotation — all additional implementation surface.
   - Cons: Webhook-only is single-point-of-failure: if delivery is missed, Kazi never sees the update.

3. **Hybrid — webhooks with poll fallback.** Webhooks are the primary path; the 15-minute poll catches drops.
   - Pros: Real-time when webhooks work; eventually consistent when they don't.
   - Cons: All the cons of Option 2 for the public endpoint, plus all the cons of Option 1 for the poll, plus reconciliation logic between the two ("did this PaymentEvent come from a webhook or a poll?").
   - Cons: Doubles the surface area; doubles the test matrix.
   - Cons: For v1 the 15-minute lag is acceptable, so the "real-time" benefit is theoretical.

**Decision**: Option 1 — 15-minute scheduled poll only. Tenant-configurable down to 5 minutes. No webhook receiver in v1.

**Rationale**: For small SA professional-services firms, a 15-minute lag in AR-aging is invisible — it's a Tuesday afternoon problem at worst, and the previous baseline was 24+ hours of drift. The configuration burden of per-tenant webhook URLs is significant in onboarding (a step where bookkeepers already churn) and the marginal latency improvement does not justify it in v1.

The poll worker mirrors `AutomationScheduler`'s shape (`TenantScopedRunner.forEachTenant()` + `TransactionTemplate`) but is a separate `@Scheduled` method on `AccountingSyncService`, owned by this phase. The 5-minute tenant-configurable lower bound prevents an aggressive tenant from blowing through Xero's 60/min rate limit on a single operation.

A 5-minute backstop window is added (`since = lastPollAt - PT5M`) to absorb clock-skew between Kazi and Xero. Dedup on `(invoice_id, provider_slug='xero', external_payment_id)` (looked up via JSONB `provider_payload`) prevents the backstop from creating duplicate `PaymentEvent` rows.

Webhooks are explicitly deferred to Phase 72+ as an *optimisation* — the foundation laid here (poll-driven `PaymentEvent` writes via `AccountingPaymentSource`) does not change when webhooks are added; webhooks would just be another input feeding the same `PaymentEvent` writer with the same dedup key.

**Consequences**:

- Positive: No public-endpoint requirement. Works in any deployment topology.
- Positive: Single integration point (`AccountingPaymentSource`); same code path for any future provider.
- Positive: Deterministic rate-limit budget — one paginated call per connection per poll interval.
- Positive: Simple tenant configuration ("Polling: every 15 minutes") rather than "register this URL with Xero."
- Negative: Up-to-15-minute lag on payment visibility. Accepted for v1.
- Negative: Idle tenants still consume 96 calls/day per connection (4/hour × 24h). Within Xero's 5000/day per-connection budget — comfortably.
- Neutral: When Phase 72+ adds webhooks, the poll continues as a fallback; no architectural change required.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way pull direction), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (worker shape), [ADR-279](ADR-279-sibling-payment-source-port.md) (port interface).
