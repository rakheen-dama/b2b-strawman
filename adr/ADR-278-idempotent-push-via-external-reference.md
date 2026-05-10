# ADR-278: Idempotent Push via Kazi-Side External Reference + Xero Reference Field

**Status**: Accepted

**Context**:

Phase 71 pushes invoices and customers from Kazi to Xero via the `AccountingSyncService` ([ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)). The push is event-driven: when an invoice is approved, a sync entry is created and the worker calls the Xero API. The same invoice may be re-pushed due to retry after transient failure, re-enqueue on subsequent events (e.g., invoice sent after invoice approved), or manual retry from dead-letter.

Without an idempotency mechanism, each push could create a new invoice in Xero. A retry after a network timeout (where the original request may have succeeded on Xero's side but the response was lost) would create a duplicate. A re-push after a status change (approved -> sent) would create a second Xero invoice for the same Kazi invoice. Duplicate invoices in a client's general ledger are a serious data-integrity issue — the bookkeeper sees two invoices for the same work, receivables are doubled, and manual cleanup is required.

The question is how to make the push idempotent: ensuring that pushing the same Kazi invoice multiple times results in exactly one Xero invoice, updated in place.

**Options Considered**:

1. **Track Xero invoice ID in `accounting_sync_entry.external_id` and use PUT on re-push** — After the first successful push, store the Xero invoice ID in the sync entry. On re-push, detect that an `external_id` exists and issue a PUT (update) instead of a POST (create).
   - Pros:
     - Straightforward: first push = POST, subsequent pushes = PUT to the known Xero ID.
     - No dependency on Xero-side dedup mechanisms. The idempotency is controlled entirely by the Kazi-side state.
   - Cons:
     - **The "lost response" problem.** If the first POST succeeds on Xero's side but the response is lost (network timeout, process crash), the sync entry has no `external_id`. The next retry issues another POST, creating a duplicate. The mechanism only protects against re-push after a successful first push, not against retry after an ambiguous failure.
     - Requires the sync entry to be in COMPLETED state before re-push is safe. But retries happen precisely when the entry is NOT completed — when it is in FAILED_RETRYING state. The mechanism does not help in the failure scenario where duplicates are most likely.
     - If the Xero invoice is deleted Xero-side (rare but possible — user deletes it manually), the stored `external_id` points to a non-existent resource. The PUT fails with 404, and the system must fall back to a POST — but now it needs a way to detect that the 404 means "deleted" vs "never existed."

2. **Xero `Reference` field as a dedup key (CHOSEN)** — Every Kazi invoice pushed to Xero carries a `Reference` field set to a deterministic Kazi-side identifier: `KAZI-INV-{invoice_uuid}`. Before creating a new invoice in Xero, the adapter queries `GET /api.xro/2.0/Invoices?Reference=KAZI-INV-{uuid}`. If a match exists, the adapter issues a PUT to update it. If no match, the adapter issues a POST. The same pattern applies to customers: `KAZI-CUST-{customer_uuid}` is set on the Xero contact's `AccountNumber` field.
   - Pros:
     - **Solves the lost-response problem.** Even if the first POST succeeded but the response was lost, the next retry queries by reference, finds the existing invoice, and updates it. No duplicate is created regardless of when or why the retry happens.
     - **Deterministic idempotency key.** The reference is derived from the Kazi entity UUID, which is immutable. No state lookup needed to compute the key — it is always `KAZI-INV-{invoice.id}`. The sync entry's `external_reference` column stores this key for audit and debugging.
     - **Works across process restarts.** If the backend crashes after a successful push but before persisting the `external_id`, the reference-based lookup recovers the Xero invoice ID on the next attempt. No data loss.
     - **Xero's `Reference` field is indexed and queryable.** The `GET /Invoices?Reference=...` query is efficient on Xero's side. It is an exact-match lookup, not a full-table scan.
     - **Human-readable.** A bookkeeper looking at the Xero invoice sees `Reference: KAZI-INV-abc123-...` and immediately knows it came from Kazi. This aids manual debugging if sync issues arise.
     - **The pattern extends to customers.** Xero contacts have an `AccountNumber` field (intended for customer reference numbers). Setting it to `KAZI-CUST-{uuid}` provides the same dedup contract for customer push.
   - Cons:
     - **Requires a pre-push lookup call.** Every push makes one extra API call to check if the reference already exists in Xero. This doubles the API call count for pushes. Mitigated by: (a) the lookup is lightweight (single-field exact match), (b) it only runs when actually pushing (not on every event), and (c) rate-limit budgets (60 calls/minute) are sufficient for the push volumes of small firms (tens of invoices per day, not thousands).
     - **Relies on the `Reference` field not being overwritten Xero-side.** If a bookkeeper manually changes the `Reference` field on the Xero invoice, the dedup lookup fails (no match by reference) and the next push creates a duplicate. Mitigated by: (a) the reference field is typically not user-facing in Xero's default views — bookkeepers rarely edit it, (b) the adapter also stores `external_id` after a successful push, so re-pushes can fall back to PUT-by-ID if the reference lookup fails but `external_id` is known. This dual-key approach handles both scenarios.
     - **The `AccountNumber` field on Xero contacts has a 50-character limit.** The `KAZI-CUST-` prefix (10 chars) + UUID (36 chars) = 46 characters — within limits, but tight. If the prefix changes or the UUID format changes, this could overflow. Acceptable for the current design.

3. **Xero-side idempotency header** — Use a request-level idempotency mechanism (e.g., a custom `Idempotency-Key` header) so that Xero deduplicates requests server-side.
   - Pros:
     - No pre-push lookup required. The idempotency is handled entirely by Xero's API infrastructure.
     - Zero additional API calls per push — the idempotency key is just a header on the same POST request.
     - Industry-standard pattern (Stripe, PayPal, and other payment APIs use `Idempotency-Key` headers).
   - Cons:
     - **Xero does not support idempotency headers.** The Xero API documentation does not specify an `Idempotency-Key` or equivalent header. There is no server-side dedup mechanism available. This option is not implementable with the current Xero API.
     - Even if Xero added idempotency headers in the future, the key would need to be stable across retries (same key for the same invoice push). This is equivalent to the `external_reference` approach (Option 2) but relying on Xero's infrastructure rather than the adapter's own lookup.
     - Idempotency-key approaches typically have a TTL (e.g., Stripe's keys expire after 24 hours). A retry after the TTL would not be deduped. For accounting sync with multi-day retry back-off (up to 6 hours between attempts), the key could expire before all retries are exhausted.

**Decision**: Option 2 — Idempotent push via Kazi-side `external_reference` (stored in sync entry) mapped to Xero's `Reference` field (for invoices) and `AccountNumber` field (for contacts). Pre-push lookup by reference; update if exists, create if not.

**Rationale**:

The lost-response problem is the critical scenario. In any distributed system where a client calls a remote API, the response can be lost without the client knowing whether the request succeeded. For accounting data, this ambiguity is unacceptable — a duplicate invoice in the general ledger is a real-world problem that requires manual bookkeeper intervention to clean up. The idempotency mechanism must handle this scenario, not just the "known successful re-push" scenario.

Option 2 (reference-based lookup) is the only option that solves the lost-response problem without Xero-side infrastructure support. The reference is deterministic (derived from the Kazi entity UUID), stable (UUIDs are immutable), and queryable (Xero indexes the Reference field). The pre-push lookup adds one API call per push but provides a guarantee: no matter how many times a push is attempted, and regardless of which previous attempts succeeded or failed, the result is exactly one Xero invoice with the correct data.

The dual-key approach (reference for dedup + external_id for direct update) provides defense in depth:
1. First push: lookup by reference → no match → POST → store `external_id` and `external_reference`.
2. Retry after successful push: lookup by reference → match found → PUT to the matched ID.
3. Retry after lost response: lookup by reference → match found (first attempt succeeded) → PUT to update.
4. Re-push after Xero-side reference edit: lookup by reference → no match → fall back to PUT by `external_id` (stored from a previous successful interaction).
5. Re-push after Xero-side deletion: lookup by reference → no match → PUT by `external_id` → 404 → POST new invoice.

This cascade covers all failure modes. No scenario results in a duplicate invoice.

The one-API-call cost per push is acceptable. A small SA professional-services firm approves ~5–20 invoices per day. At two API calls per push (one lookup + one create/update), that is 10–40 calls per day for invoice sync. Adding customer pushes (~2–5 per day) brings the total to ~15–50 daily API calls for push operations. This is well within Xero's 5000/day limit and leaves ample budget for payment polling ([ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md)).

**Consequences**:

- Positive:
  - Duplicate invoices in Xero are structurally prevented regardless of retry patterns, lost responses, or re-push triggers.
  - The `external_reference` column on `accounting_sync_entry` provides a traceable link between the Kazi entity and its Xero counterpart. Debugging "which Xero invoice is this?" is a direct lookup.
  - The Xero `Reference` field (`KAZI-INV-{uuid}`) is visible to bookkeepers, providing a clear provenance indicator ("this invoice came from Kazi").
  - The pattern is portable to future providers. Any accounting system with a queryable reference/identifier field can use the same dedup approach. The `external_reference` is stored on the sync entry regardless of provider.
  - Works correctly across backend restarts, deployments, and crash recovery. The reference is deterministic and does not depend on in-memory state.

- Negative:
  - One extra API call per push (the reference lookup). Doubles the push API call count. For the expected volumes (tens of pushes per day), this is negligible, but it is non-zero.
  - If a bookkeeper edits the `Reference` field in Xero (unlikely but possible), the dedup lookup misses and falls back to the `external_id` path. If `external_id` is also lost (extremely unlikely — would require both a lost response and a Xero-side edit), a duplicate is created. This edge case is documented in the operational runbook.
  - The `AccountNumber` field on Xero contacts is being repurposed for dedup. If a firm was already using `AccountNumber` for their own customer reference numbers, the Kazi sync would overwrite them. Mitigated by: the initial customer import preserves existing `AccountNumber` values in the Kazi `Customer.externalReference` field; the push only sets `AccountNumber` for customers that originated in Kazi or were imported and subsequently pushed.

- Neutral:
  - The `external_reference` format (`KAZI-INV-{uuid}` for invoices, `KAZI-CUST-{uuid}` for customers) is a stable contract. Changing the format would break dedup for existing sync entries. Any format change must migrate existing entries or maintain backward-compatible lookup.
  - The pre-push lookup and the push itself are not atomic (two separate API calls). Between the lookup (no match) and the POST (create), another system could theoretically create an invoice with the same reference. This is not a practical concern — only the Kazi sync service writes invoices with `KAZI-INV-*` references to Xero.
  - Per [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md), the `accounting_sync_entry` table (which stores `external_reference` and `external_id`) is tenant-scoped. References from one tenant cannot collide with another tenant's references because they are in separate schemas.

- Related: [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync service — owns the push path and sync entries), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way sync — push is the only outbound path), [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero adapter — implements the reference-based dedup), [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) (payment polling — uses `external_reference` to match Xero payments to Kazi invoices), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant isolation of sync entries).
