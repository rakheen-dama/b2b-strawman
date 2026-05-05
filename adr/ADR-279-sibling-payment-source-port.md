# ADR-279: Sibling `AccountingPaymentSource` Port Instead of Overloading `AccountingProvider`

**Status**: Accepted

**Date**: 2026-05-03

**Context**: The Phase 21 `AccountingProvider` port has three methods: `syncInvoice`, `syncCustomer`, `testConnection`. Phase 71 needs a fourth concept — pulling payment events from the provider's side (Xero invoices that have moved to PAID since `lastPollAt`). The natural shape is `getPaymentsModifiedSince(Instant since): List<ExternalPaymentEvent>`. Where should this method live?

Three architectural options: extend `AccountingProvider` with the new method (everyone implements), introduce a sibling port (only payment-aware providers implement), or invent a generic pull-source abstraction.

The `NoOpAccountingProvider` is the canonical accounting fallback when no real provider is configured. Future providers (Sage Pastel, QuickBooks) may or may not support payment pull — Sage's older surface in particular doesn't have a clean equivalent. Forcing every accounting provider to implement payment-pull leads to throwaway no-op implementations on the providers that don't.

**Options Considered**:

1. **Sibling `AccountingPaymentSource` interface; `XeroAccountingProvider` implements both `AccountingProvider` and `AccountingPaymentSource`.** The two ports are independent; the payment-poll worker resolves `AccountingPaymentSource` separately via `IntegrationRegistry`.
   - Pros: Interface segregation — providers only implement what they support.
   - Pros: `NoOpAccountingProvider` doesn't need a no-op `getPaymentsModifiedSince`.
   - Pros: Future Sage adapter that lacks payment pull simply doesn't implement the sibling port; the registry returns absent and the poll worker skips that connection.
   - Pros: Existing `AccountingProvider` shape unchanged — Phase 21 contract is preserved.
   - Cons: Two registry lookups for Xero (push uses `AccountingProvider`; pull uses `AccountingPaymentSource`). One adapter class implementing two interfaces — minor tax.

2. **Extend `AccountingProvider` with `getPaymentsModifiedSince`.** Every accounting provider implements it; `NoOpAccountingProvider` returns empty list.
   - Pros: Single port to resolve.
   - Cons: Couples invoice/customer push contract to payment-pull contract — a future Sage adapter that supports push but not pull is forced to ship a meaningless no-op.
   - Cons: Violates interface segregation — payment-aware providers and non-payment-aware providers share a contract that lies for the latter.
   - Cons: Phase 21's existing port shape changes, which ripples to anything that already mocks or stubs it.

3. **Generic `IntegrationPullSource<T>` shape.** A reusable abstraction over any "pull modified-since" source, parameterised by event type.
   - Pros: One abstraction for accounting-payment-pull, KYC-status-pull, signing-document-pull, etc.
   - Cons: Premature generalisation. We have one pull source in v1.
   - Cons: Generics-over-tagged-events pattern is more complex than needed; debugging "why did this generic pull return nothing" is harder than debugging a typed `AccountingPaymentSource`.
   - Cons: Different pull sources have different cursor semantics (modified-since instant vs continuation token vs page cursor); a single shape would be a leaky abstraction.

**Decision**: Option 1 — sibling `AccountingPaymentSource` interface. `XeroAccountingProvider implements AccountingProvider, AccountingPaymentSource`. `NoOpAccountingProvider` implements only `AccountingProvider`.

**Rationale**: Interface segregation is the right principle here. Not every accounting provider supports payment pull; making the unsupported case a no-op lie pollutes the no-op surface and complicates future adapter implementations. The Xero adapter implements both interfaces on the same class — Java handles this cleanly. The payment-poll worker resolves the primary `AccountingProvider` via `IntegrationRegistry.resolve(ACCOUNTING, AccountingProvider.class)` and then narrows with `instanceof AccountingPaymentSource`, skipping connections whose adapter doesn't implement the sibling port. The existing registry contract resolves by `(domain, portInterface)` and casts to `T` rather than returning an `Optional`, so an `instanceof` runtime check on the resolved adapter is the safe pattern; adding a `resolveIfSupports(domain, Class<T>)` helper to `IntegrationRegistry` is an acceptable but non-required future cleanup.

The `ExternalPaymentEvent` record is colocated with `AccountingPaymentSource` and carries the minimal fields needed for `PaymentEvent` reconstruction: `externalInvoiceReference` (matches Kazi `external_reference`), `externalPaymentId` (Xero payment ID for dedup), `amount`, `currency`, `paidAt`, `status`. Provider-specific payload is intentionally not on this record — vendor JSON lands in `PaymentEvent.provider_payload` JSONB at the call site, not on the port.

The two-port shape also makes future Phase-72+ webhook receivers natural: a hypothetical `XeroWebhookReceiver` writes the same `PaymentEvent` shape with the same dedup key, and the port boundary stays clean.

**Consequences**:

- Positive: `AccountingProvider` Phase 21 contract unchanged — no rippling to existing tests / mocks.
- Positive: `NoOpAccountingProvider` doesn't need a meaningless no-op pull method.
- Positive: Future Sage adapter can ship push-only if Sage's payment-pull surface is unsuitable.
- Positive: Single Xero class implements both interfaces — no duplicated bean.
- Negative: Two registry lookups in the codebase (one for push, one for pull). Negligible cost; both go through `IntegrationRegistry.resolve`.
- Negative: poll worker carries an `instanceof AccountingPaymentSource` runtime check (vs a single typed `resolve` call). Documented; the alternative — adding `resolveIfSupports` to `IntegrationRegistry` — is a future cleanup, not required for Phase 71.
- Neutral: Phase 71 ships only one provider implementing both, so the segregation benefit is theoretical until Phase 72+ Sage. The cost of the segregation now is minimal; the cost of un-segregating later would be much higher.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (one provider in v1), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (pull direction), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (poll worker shape), [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) (poll mechanism).
