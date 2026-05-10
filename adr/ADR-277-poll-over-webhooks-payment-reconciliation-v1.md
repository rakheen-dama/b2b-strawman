# ADR-277: Polling Over Webhooks for Inbound Payment Reconciliation (V1)

**Status**: Accepted

**Context**:

Phase 71's payment reconciliation path pulls payment status from Xero into Kazi: when an invoice is marked as paid in Xero (through bank reconciliation, manual allocation, or batch payment), Kazi should learn about it and transition its local invoice to PAID, writing a `PaymentEvent` with `source=XERO_RECONCILE`. This keeps Kazi's AR aging report current.

Xero supports two mechanisms for external systems to learn about invoice payment changes:

1. **Outbound webhooks**: Xero can POST a notification to a configured HTTPS endpoint when an invoice is updated. The webhook payload contains the Xero resource ID and event type; the receiver must then call back to Xero's API to get the full payload.
2. **Polling via modified-since**: The `GET /api.xro/2.0/Invoices?ModifiedAfter={timestamp}` endpoint returns all invoices modified since a given time. The caller can filter for invoices that moved to PAID status.

The question is which mechanism to use for v1 payment reconciliation, given the constraints of the Kazi platform (multi-tenant, schema-per-tenant, no fixed public URL per tenant) and Xero's webhook reliability characteristics.

**Options Considered**:

1. **Xero outbound webhooks with a shared receiver endpoint** — Register a single webhook endpoint (e.g., `https://api.kazi.co.za/webhooks/xero`) that receives notifications for all tenant connections. The endpoint demultiplexes by Xero tenant ID, resolves the Kazi tenant, and processes the payment event.
   - Pros:
     - Near-real-time notification. When a payment is recorded in Xero, the webhook fires within seconds. Kazi's AR aging is updated almost immediately.
     - No polling infrastructure needed. The system is event-driven — no scheduled workers, no "last polled at" state to maintain.
     - Lower API call volume. Webhooks deliver only changed resources; polling requests all changes since the last poll (including non-payment changes that must be filtered out).
   - Cons:
     - **Xero webhook delivery is unreliable.** Xero's documentation acknowledges that webhooks are best-effort. Webhook deliveries can be delayed (up to hours during Xero outages), can be delivered out of order, can be duplicated, and can be silently dropped. A system that depends solely on webhooks for payment reconciliation will miss payments during Xero delivery failures.
     - **Public HTTPS endpoint requirement.** The webhook receiver must be publicly accessible with a valid TLS certificate. In the Kazi deployment model (single backend instance or small cluster), this is achievable but adds operational configuration: DNS entry, certificate provisioning, firewall rules, and health monitoring for the webhook endpoint.
     - **Webhook signature validation adds complexity.** Xero webhooks are signed with a per-connection webhook key. The receiver must validate the HMAC-SHA256 signature on every request and return 401 for invalid signatures. The signing key must be stored per tenant connection (in `SecretStore`), and key rotation must be handled.
     - **Per-tenant webhook registration.** Each Xero connection requires a separate webhook subscription (Xero webhooks are scoped to the connected Xero tenant). The registration must happen during the OAuth2 connection flow, and the subscription must be cleaned up on disconnect. Failed registrations (Xero-side errors, network issues) must be retried.
     - **Webhook endpoint must respond within 5 seconds.** Xero expects a 2xx response within 5 seconds or it considers the delivery failed and retries. The receiver cannot perform slow operations (database lookups across tenant schemas, Xero API callbacks) synchronously in the webhook handler — it must acknowledge immediately and process asynchronously. This adds queuing infrastructure that partially negates the "no polling" benefit.
     - **Still need polling as a fallback.** Because webhook delivery is unreliable, a polling safety net is needed anyway to catch missed webhooks. This means building both mechanisms — webhooks for speed, polling for completeness. The complexity is additive, not alternative.

2. **Polling via modified-since on a 15-minute schedule (CHOSEN)** — A Spring `@Scheduled` worker runs every 15 minutes (configurable down to 5 minutes per tenant). For each active Xero connection, it calls `GET /api.xro/2.0/Invoices?ModifiedAfter={lastPollAt}&Statuses=PAID,VOIDED`. For each returned invoice that matches a Kazi invoice (via `external_reference`), it writes a `PaymentEvent` and transitions the invoice. The `lastPollAt` timestamp is persisted on the connection entity.
   - Pros:
     - **Simple and reliable.** The polling worker is a straightforward `@Scheduled` method that iterates connections and calls the Xero API. No webhook infrastructure, no public endpoint, no signature validation, no per-tenant subscription management.
     - **Guaranteed completeness.** Every poll retrieves all changes since the last successful poll. There is no delivery reliability concern — if the poll succeeds, all changes are captured. If the poll fails (Xero down, rate limited), it retries on the next cycle with the same `lastPollAt`, ensuring no gap.
     - **No public endpoint required.** The polling worker makes outbound HTTP calls from the Kazi backend. No firewall rules, no DNS configuration, no TLS certificate management for an inbound endpoint. This is particularly valuable for development, staging, and on-premise deployments where public HTTPS endpoints are not available.
     - **Rate-limit friendly.** Each poll is one API call per tenant connection (the modified-since endpoint returns paginated results). At 15-minute intervals, a tenant with one Xero connection makes 96 API calls per day for payment polling — well within Xero's 5000/day limit. The remaining budget is available for invoice and customer push operations.
     - **Tenant-configurable interval.** Firms that need faster reconciliation can reduce the interval to 5 minutes (288 calls/day for polling alone — still within limits). Firms that do not need fast reconciliation can increase to 30 or 60 minutes. The setting lives on the tenant's integration configuration.
     - **No additional infrastructure.** The `@Scheduled` worker runs within the existing Spring Boot backend. No separate microservice, no message queue, no webhook endpoint server. Operational simplicity for a small team.
   - Cons:
     - **Up to 15-minute latency (default).** A payment recorded in Xero at 10:01 may not appear in Kazi until 10:16. For AR aging reports (which are typically viewed daily or weekly), this latency is invisible. For real-time dashboards, it is noticeable but acceptable.
     - **Wasted API calls when nothing changed.** If no invoices were paid between polls, the API call returns an empty result set. For a firm that receives 2–3 payments per day, most of the 96 daily poll calls return nothing. The cost is negligible (one lightweight API call per 15 minutes) but is not zero.
     - **Slightly higher API usage than webhooks.** Webhooks deliver only changed resources; polling retrieves all changes (including non-payment modifications that must be filtered). In practice, the modified-since endpoint with `Statuses=PAID,VOIDED` filter is efficient — Xero filters server-side — but the call is made regardless of whether there are results.
     - **Clock skew risk.** The `lastPollAt` timestamp is persisted on the Kazi side. If the Kazi server clock and Xero's clock diverge, changes could be missed (if Kazi's clock is ahead) or duplicated (if behind). Mitigated by using a 30-second overlap window on each poll (`ModifiedAfter = lastPollAt - 30s`) and deduplicating by `(invoice_id, xero_payment_id)` on the Kazi side.

3. **Webhooks primary + polling fallback (hybrid)** — Register webhooks for near-real-time delivery. Run a less-frequent polling safety net (every 60 minutes) to catch any missed webhook deliveries. The webhook handler processes payment events immediately; the polling worker catches stragglers.
   - Pros:
     - Best of both: near-real-time delivery (seconds) via webhooks, guaranteed completeness via polling.
     - The polling interval can be longer (60 minutes instead of 15) because webhooks handle the common case. Lower API call volume than polling-only.
     - Industry-standard pattern for reliable event delivery — webhooks for speed, polling for completeness.
   - Cons:
     - **Double the implementation complexity.** Both a webhook receiver (with public endpoint, signature validation, per-tenant subscription, async processing) and a polling worker must be built, tested, and maintained. Two code paths for the same outcome.
     - **Operational burden of the webhook endpoint.** The public HTTPS endpoint requires DNS, TLS, firewall, and monitoring. For a small team deploying a single backend instance, this is non-trivial operational overhead for a v1 feature.
     - **Webhook debugging is harder than polling debugging.** When a payment is not reconciled, the developer must check: Did Xero send the webhook? Did the endpoint receive it? Did signature validation pass? Did the async processor handle it? Or will the polling fallback catch it on the next cycle? The debugging surface is larger.
     - **The latency improvement is marginal for the use case.** Payment reconciliation updates AR aging. AR aging is typically consumed daily (morning report, end-of-day review). The difference between "payment appears in 5 seconds" (webhook) and "payment appears in 15 minutes" (polling) does not change the user's workflow for this use case.
     - **Over-engineering for v1.** Phase 71 is the first accounting integration. The priority is correctness, reliability, and simplicity — not millisecond-level latency. Webhooks can be added in Phase 72+ once the polling foundation is proven and the latency becomes a real user complaint (rather than a theoretical concern).

**Decision**: Option 2 — Polling via modified-since on a 15-minute schedule. Webhooks are deferred to Phase 72+ as a latency optimisation.

**Rationale**:

The 15-minute polling SLO is acceptable for the payment reconciliation use case. Payment reconciliation serves one primary purpose in Kazi: keeping the AR aging report current. AR aging is consumed as a periodic report (daily, weekly) — not as a real-time dashboard. The difference between a payment appearing in Kazi after 5 seconds (webhook) vs 15 minutes (polling) is invisible to the report consumer. No small SA professional-services firm makes operational decisions based on second-level payment latency from their accounting system.

The reliability argument is decisive. Xero's webhook delivery is best-effort — their documentation explicitly states that delivery can be delayed, deduplicated, or dropped. A payment reconciliation system that depends on webhooks must still have a polling fallback to ensure completeness. This means building both mechanisms (Option 3) even if the primary path is webhooks. Since polling alone provides guaranteed completeness with acceptable latency, webhooks add complexity without adding business value in v1.

The operational simplicity argument reinforces the decision. Polling requires no public endpoint, no DNS configuration, no TLS certificate management, no webhook signature validation, no per-tenant subscription lifecycle. The worker is a `@Scheduled` method in the existing Spring Boot backend — it works in development, staging, CI, and production without additional infrastructure. For a small team shipping a first accounting integration, operational simplicity reduces the risk of deployment failures and reduces the debugging surface when things go wrong.

The path to webhooks in Phase 72+ is clear and additive. If polling latency becomes a real user complaint (not a theoretical concern), webhooks can be added alongside polling: the webhook handler processes events immediately, and the polling worker acts as a safety net (with a longer interval, e.g., 60 minutes). The polling infrastructure built in Phase 71 becomes the fallback, not wasted work.

**Consequences**:

- Positive:
  - Simple, reliable payment reconciliation with guaranteed completeness. No webhook delivery failures to debug, no missed events to investigate.
  - No public endpoint required. Works in development (localhost), CI (ephemeral containers), staging, and production without infrastructure changes.
  - Rate-limit friendly. One API call per 15 minutes per tenant connection. Well within Xero's daily limit.
  - Tenant-configurable interval (5/15/30/60 minutes) via the integration settings UI. Firms that need faster reconciliation can reduce the interval at the cost of higher API call volume.
  - The `lastPollAt` timestamp on `AccountingXeroConnection` provides a clear "last known good" watermark. Debugging "when did we last check for payments?" is a single column lookup.

- Negative:
  - Up to 15-minute latency (default) between a payment being recorded in Xero and appearing in Kazi. Configurable down to 5 minutes, but never real-time without webhooks.
  - Wasted API calls when no payments occurred since the last poll. For the typical small firm (2–5 payments per day), ~90% of poll calls return empty results. The cost is negligible but non-zero.
  - If a firm has many Xero connections (unlikely for small firms, but possible for multi-entity structures), the polling worker makes one call per connection per cycle. At 10 connections and 15-minute intervals, that is 960 calls/day for polling alone. Still within limits but worth monitoring.

- Neutral:
  - The polling worker runs per-tenant, setting `RequestScopes.TENANT_ID` for each active connection. It respects schema-per-tenant isolation ([ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md)) and never operates without a tenant context.
  - The 30-second overlap window (`ModifiedAfter = lastPollAt - 30s`) handles clock skew and ensures no payments are missed at the boundary. Deduplication by `(invoice_id, xero_payment_id)` prevents duplicate `PaymentEvent` records.
  - The `@Scheduled` worker is separate from the sync-drain worker (which runs every 30 seconds for outbound push). Two workers, two intervals, two concerns. They share the `AccountingSyncService` class but not the scheduling lifecycle.

- Related: [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way sync — payment pull direction), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (dedicated sync service — houses the polling worker), [ADR-279](ADR-279-sibling-payment-source-port.md) (AccountingPaymentSource port — the interface the worker calls), [ADR-275](ADR-275-oauth2-augmentation-org-integration.md) (connection entity — stores `lastPollAt`), [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero adapter implements the payment source).
