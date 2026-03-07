# ADR-160: Email Rate Limiting Strategy

**Status**: Proposed
**Date**: 2026-03-07
**Phase**: 40 (Bulk Billing & Batch Operations)

## Context

The billing run's "Send" step dispatches invoice emails to all approved invoices in the batch. Each email is sent via the existing `InvoiceEmailService`, which delegates to `EmailNotificationChannel` (Phase 24) with SMTP and SendGrid adapters. When sending 50-200 invoices in rapid succession, email provider rate limits become a concern: SendGrid free tier allows 100 emails/day, paid tiers range from 100-1,000 emails/second depending on plan. SMTP servers (self-hosted or ISP-provided) typically reject connections above 2-5 per second.

Batch invoice sending is an infrequent operation — typically monthly or weekly. It is not a high-throughput messaging system. The total volume per run is bounded by the firm's client count, which for DocTeams' target market (professional services firms) ranges from 10 to 200. The challenge is not handling millions of messages but avoiding provider throttling for a burst of 50-200 emails that arrive in seconds.

The existing `EmailNotificationChannel` sends emails synchronously — one at a time, blocking until the provider confirms delivery. The billing run needs to add pacing between sends to stay within provider limits, while still completing the batch in a reasonable time (minutes, not hours).

## Options Considered

1. **No rate limiting** — Send all emails as fast as the provider accepts them, relying on the synchronous send loop's natural pacing.
   - Pros: Simplest implementation — just loop and send. No configuration needed
   - Cons: Risks provider throttling (HTTP 429 from SendGrid, connection refused from SMTP). Throttled emails may be silently dropped or deferred, leading to customers not receiving invoices. Account suspension risk on strict providers. No retry mechanism for throttled sends. The natural pacing of synchronous sends (~100-200ms per email) still produces 5-10 emails/second, which exceeds many SMTP server limits

2. **Full message queue** — Use Redis or SQS as a durable message queue. Enqueue all invoice emails, consume at a controlled rate with a dedicated worker.
   - Pros: Durable — messages survive server restarts. Built-in retry with backoff. Rate limiting via consumer concurrency control. Scales to thousands of emails. Dead-letter queue for permanent failures
   - Cons: Adds infrastructure dependency (Redis or SQS) that doesn't exist in the current stack for this purpose. Significant implementation complexity: queue producer, consumer, serialization, retry policy, dead-letter handling, monitoring. Over-engineered for batch sizes under 200. LocalStack already provides S3 for file storage, but adding SQS for email queuing introduces a new operational concern. Harder to reason about — "did all emails send?" requires checking queue depth rather than a simple loop result

3. **Token bucket / sleep-between-bursts (chosen)** — Process emails in bursts of N (from `OrgSettings.billingEmailRateLimit`, default 5), sleep 1 second between bursts. Simple in-process rate limiting with no external dependencies.
   - Pros: No infrastructure dependencies — pure Java implementation. Configurable per tenant via `OrgSettings`, matching the established pattern for tenant-specific configuration. Easy to reason about: "send 5, wait 1 second, send 5, wait 1 second." Completes a 50-email batch in 10 seconds, a 200-email batch in 40 seconds — well within acceptable UX. Simple to implement, test, and debug. Updates `BillingRun.totalSent` after each burst, enabling progress tracking
   - Cons: Not durable — if the server restarts mid-batch, unsent emails are lost (the run shows partial `totalSent`). No automatic retry for individual send failures (though failures are captured in `BatchOperationResult`). The fixed sleep interval doesn't adapt to provider feedback (e.g., SendGrid's `X-RateLimit-Remaining` header). Less sophisticated than a proper rate limiter library

4. **Provider-managed throttling** — Send at full speed, catch 429/throttle responses, and retry with exponential backoff based on provider headers (`Retry-After`, `X-RateLimit-Reset`).
   - Pros: Adapts to the actual provider limits rather than a configured estimate. No pre-configuration needed — works automatically with any provider
   - Cons: Reactive rather than proactive — triggers throttling before backing off, which some providers penalize. Provider response headers vary (SendGrid vs SMTP vs custom). SMTP doesn't have standard rate limit headers — rejection is the only signal. Retry loops add unpredictable completion time. Some providers count throttled attempts against the rate limit, making the problem worse

## Decision

Use **token bucket / sleep-between-bursts** (Option 3). Process invoice emails in configurable bursts with a 1-second pause between bursts, using `OrgSettings.billingEmailRateLimit` as the burst size.

## Rationale

The batch email sending problem in DocTeams is bounded and infrequent. A firm sends invoice emails at most weekly, with batch sizes capped by their client count (typically 10-200). This volume does not justify a message queue. Redis or SQS (Option 2) would be the right choice for a platform sending thousands of transactional emails per hour, but a professional services firm sending 50 invoices on the first of the month needs a simpler solution.

The sleep-between-bursts approach is deterministic and easy to configure. A firm using SendGrid's free tier (100 emails/day) sets `billingEmailRateLimit` to 2 and knows their 40-email batch will complete in 20 seconds without hitting limits. A firm on SendGrid Pro sets it to 10 and completes in 4 seconds. The `OrgSettings` pattern is already established for `billingBatchAsyncThreshold` ([ADR-159](ADR-159-sync-vs-async-batch-generation.md)) and other tenant-specific configuration — adding `billingEmailRateLimit` is a single column on an existing entity.

The durability concern (server restart mid-batch) is acceptable given the operation's frequency and the recovery path. If a batch send is interrupted, the `BillingRun.totalSent` counter and per-invoice status show exactly which emails were dispatched. The administrator can identify unsent invoices and either re-trigger the batch send (which skips already-sent invoices) or send them individually. For a monthly operation, this manual recovery is a reasonable tradeoff against the complexity of a durable queue.

## Consequences

- **Positive**: Zero infrastructure dependencies — no Redis, SQS, or external queue to deploy, monitor, or maintain
- **Positive**: Configurable per tenant via `OrgSettings`, allowing firms to tune rate limits to their email provider's capabilities
- **Positive**: Predictable completion time — a firm can calculate exactly how long their batch send will take (batch_size / rate_limit seconds)
- **Positive**: Progress tracking via `BillingRun.totalSent` updates after each burst, enabling the UI to show send progress
- **Negative**: Not durable — server restart mid-batch requires manual identification and re-sending of unsent invoices
- **Negative**: Fixed interval doesn't adapt to provider feedback — if the provider's actual limit is higher or lower than configured, the system doesn't self-adjust
- **Negative**: Individual send failures within a burst are captured but not automatically retried — the administrator must re-send failed invoices manually
- **Neutral**: The SMTP adapter may need a lower default rate limit (2/second vs 5/second for SendGrid). This is handled by documenting recommended settings per provider, not by auto-detection
