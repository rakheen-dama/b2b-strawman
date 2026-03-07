# ADR-159: Sync vs Async Batch Generation

**Status**: Proposed
**Date**: 2026-03-07
**Phase**: 40 (Bulk Billing & Batch Operations)

## Context

The billing run's "Generate" step creates draft invoices for all selected customers in the batch. Each customer's invoice generation involves: resolving selected entries from `BillingRunEntrySelection` records ([ADR-158](ADR-158-explicit-entry-selection-vs-snapshot.md)), calling `InvoiceService.createDraft()` (which runs prerequisite checks, creates invoice lines, applies tax rates, and assigns an invoice number), and updating the `BillingRunItem` status. For a firm with 10 customers, this completes in seconds. For a firm with 200 customers, it could take minutes.

The system must handle both cases — small boutique firms and larger practices — without forcing unnecessary complexity on the common case. DocTeams targets professional services firms (accounting, consulting, legal), where client counts typically range from 5 to 200 active customers. The median firm has 20-40 clients doing monthly billing.

The schema-per-tenant architecture means each billing run operates within a single tenant schema. There are no cross-tenant concerns during generation. The `BillingRun` entity ([ADR-157](ADR-157-billing-run-entity-vs-tag.md)) already tracks per-customer status via `BillingRunItem`, which provides the progress tracking foundation regardless of sync/async execution.

## Options Considered

1. **Always synchronous** — Process all customers in the HTTP request thread. Return the complete result when all items are processed.
   - Pros: Simplest implementation — a single transactional loop. No polling, no background workers, no status endpoint. Easy to debug (stack traces are linear). Immediate feedback — the response contains all results. Error handling is straightforward (catch per-item, aggregate results)
   - Cons: HTTP request timeout risk for large batches — generating 200 invoices could take 2-5 minutes, exceeding typical proxy/load balancer timeouts (60-120 seconds). Browser may show "connection lost" for long-running requests. Blocks the request thread (though virtual threads mitigate this). Poor UX for large batches — no progress indication until completion

2. **Always asynchronous** — Queue the generation job, return a `202 Accepted` immediately, provide a polling endpoint for progress.
   - Pros: Handles any batch size. Immediate response to the client. Progress polling enables real-time UI updates. Survives server restarts (if backed by a persistent job queue)
   - Cons: Over-engineered for the common case — most firms have fewer than 50 customers. Adds complexity: background executor, polling endpoint, progress tracking, failure recovery for interrupted jobs. Testing is harder (async assertions, timing issues). The `BillingRun` entity already has status tracking, but async adds a second layer of "is the background job still running?" state. Without a persistent job queue (Redis, database-backed), an interrupted async job leaves the run in a zombie IN_PROGRESS state

3. **Threshold-based (chosen)** — Synchronous for batches with N or fewer customers (configurable, default 50), asynchronous with progress polling for batches exceeding the threshold. The threshold is stored in `OrgSettings.billingBatchAsyncThreshold`.
   - Pros: Sync simplicity for the common case (most firms under 50 clients). Async capability for large firms that need it. Configurable threshold via `OrgSettings` — firms can tune based on their experience. The `BillingRunItem` status updates serve double duty: sync returns them in the response, async exposes them via the polling endpoint. No wasted complexity for small firms
   - Cons: Two code paths to maintain and test (sync loop + async executor). The threshold is a heuristic — 50 may be too low or too high depending on invoice complexity (many line items = slower per-customer generation). Slightly more complex API contract (response is either the full result or a "generation started" acknowledgment depending on batch size)

## Decision

Use **threshold-based generation** (Option 3) with a configurable threshold defaulting to 50 customers. Batches at or below the threshold execute synchronously; batches above execute asynchronously with progress available via the existing `GET /api/billing-runs/{id}/items` endpoint.

## Rationale

The threshold approach optimizes for the common case without sacrificing scalability. Professional services firms on DocTeams typically have 10-50 active clients. For these firms, synchronous generation completes in under 30 seconds, provides immediate feedback, and requires no polling infrastructure. The sync path is a straightforward loop: iterate `BillingRunItem` records, call `InvoiceService.createDraft()` for each, update statuses, compute summary stats, return the result.

For firms exceeding the threshold, the async path uses a `@Async`-annotated method (Spring's task executor with virtual threads enabled). The `BillingRunItem` status updates (`PENDING -> GENERATING -> GENERATED/FAILED`) are written within the async execution, and the existing items endpoint serves as the progress polling mechanism — no separate progress tracking infrastructure is needed. If the async job is interrupted (server restart), the run remains in `IN_PROGRESS` with some items still `PENDING`. The administrator can detect this (items stuck in PENDING) and re-trigger generation, which skips already-GENERATED items.

Making the threshold configurable via `OrgSettings` follows the established pattern — `OrgSettings` already holds `billingEmailRateLimit` and `defaultBillingRunCurrency`. A firm that finds sync generation too slow at 30 customers can lower the threshold. A firm comfortable waiting can raise it. The default of 50 is conservative — at roughly 500ms per customer (prerequisite checks, entry resolution, invoice creation, tax application), 50 customers takes approximately 25 seconds, well within HTTP timeout limits.

## Consequences

- **Positive**: Most firms (under 50 clients) get the simplest possible experience — synchronous request with immediate results, no polling
- **Positive**: Large firms get async generation with progress tracking, avoiding HTTP timeout failures
- **Positive**: Configurable threshold via `OrgSettings` allows per-tenant tuning without code changes
- **Positive**: Both paths reuse the same `BillingRunItem` status tracking — no separate progress infrastructure
- **Negative**: Two code paths (sync loop and async executor) must be maintained and tested independently
- **Negative**: The threshold is a heuristic — invoice generation time varies with line item count, tax rules, and prerequisite complexity. A firm with 30 customers but 500 entries each may still hit timeout issues at the default threshold
- **Neutral**: The async path does not use a persistent job queue (Redis/SQS). If the server restarts mid-generation, the run requires manual re-triggering. This is acceptable for an infrequent operation (monthly billing) and avoids adding infrastructure dependencies
