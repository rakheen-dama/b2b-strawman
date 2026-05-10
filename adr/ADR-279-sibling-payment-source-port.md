# ADR-279: Sibling AccountingPaymentSource Port Instead of Overloading AccountingProvider

**Status**: Accepted

**Context**:

Phase 71 introduces a payment-pull path: the system polls Xero for invoices that moved to PAID status and writes `PaymentEvent` records to transition Kazi invoices accordingly ([ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md)). The existing `AccountingProvider` port from Phase 21 defines three methods: `syncInvoice(InvoiceSyncRequest)`, `syncCustomer(CustomerSyncRequest)`, and `testConnection()`. It is a push-only interface — every method sends data to the external system.

The payment-pull operation has a fundamentally different shape: it reads data from the external system based on a time window and returns a list of payment events. The question is whether to add a `getPaymentsModifiedSince(Instant)` method to the existing `AccountingProvider` interface or to define a separate sibling interface (`AccountingPaymentSource`) for payment pull.

The `AccountingProvider` interface is implemented by `NoOpAccountingProvider` (returns success with a fake ID) and will be implemented by `XeroAccountingProvider` in Phase 71. Future adapters (Sage Pastel in Phase 72+) would also implement it. Any change to the interface affects all implementations.

**Options Considered**:

1. **Add `getPaymentsModifiedSince` to `AccountingProvider`** — Extend the existing interface with a fourth method: `List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since)`. All implementations must provide this method.
   - Pros:
     - One interface for all accounting adapter operations. A developer looking at the `AccountingProvider` interface sees the complete surface of what an accounting adapter does — push invoices, push customers, test connection, and pull payments.
     - Simpler dependency injection. The `AccountingSyncService` injects one adapter (`AccountingProvider`) and calls push methods for outbound and pull methods for inbound. No need to look up a second interface.
     - The `IntegrationRegistry` resolves one adapter per domain. With a single interface, `registry.resolve(ACCOUNTING, AccountingProvider.class)` returns an adapter that can do everything. No secondary lookup needed.
   - Cons:
     - **Violates the Interface Segregation Principle (ISP).** Push (outbound) and pull (inbound) are orthogonal capabilities. A provider might support push but not pull (e.g., a hypothetical write-only adapter for a ledger that does not expose payment data via API). With a combined interface, such an adapter must implement `getPaymentsModifiedSince` as a stub that returns an empty list — the method exists but does nothing, misleading the reader about the adapter's capabilities.
     - **`NoOpAccountingProvider` must implement the method.** The no-op adapter has no external system to poll. It must return an empty list, which is semantically correct but adds a method to an implementation that has no real logic for it. This is a minor concern but signals that the interface is too broad.
     - **Different lifecycle concerns.** Push methods are called per-event (one invoice → one push call). The pull method is called per-schedule (every 15 minutes, regardless of events). Mixing per-event and per-schedule operations on the same interface conflates two operational patterns.
     - **Rate-limit accounting becomes complex.** The push worker and the pull worker both call methods on the same adapter, both consuming from the same Xero rate-limit budget. If they are on the same interface, rate-limit state (remaining calls, retry-after) must be shared across methods. With separate interfaces, each can independently track its rate-limit consumption.
     - **Testing becomes broader.** A test that verifies push behaviour must mock the full `AccountingProvider` interface (including the pull method). A test that verifies pull behaviour must mock the push methods. The test surface is unnecessarily broad when the concerns are independent.

2. **Separate `AccountingPaymentSource` interface (CHOSEN)** — Define a new sibling interface with a single method: `List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since)`. `XeroAccountingProvider` implements both `AccountingProvider` and `AccountingPaymentSource`. `NoOpAccountingProvider` implements `AccountingPaymentSource` with an empty-list return (or does not implement it at all, with the polling worker skipping providers that do not implement the interface).
   - Pros:
     - **Clean interface segregation.** Push and pull are separate interfaces with separate contracts. A developer reading `AccountingProvider` sees three push/test methods. A developer reading `AccountingPaymentSource` sees one pull method. Each interface is cohesive — all methods serve the same operational pattern.
     - **Implementations can opt in independently.** A future adapter that only supports push (no payment read API) implements `AccountingProvider` only. The polling worker checks `if (adapter instanceof AccountingPaymentSource)` and skips providers that do not support pull. No stub methods, no misleading empty implementations.
     - **Independent lifecycle.** The push worker calls `AccountingProvider.syncInvoice` / `syncCustomer`. The pull worker calls `AccountingPaymentSource.getPaymentsModifiedSince`. They are different workers with different schedules, and they interact with different interfaces. Clean separation of concerns at the calling layer as well as the implementation layer.
     - **Narrow test surface.** A test for the push path mocks `AccountingProvider`. A test for the pull path mocks `AccountingPaymentSource`. Each test only stubs the methods relevant to the scenario under test.
     - **Rate-limit tracking can be interface-scoped.** The `XeroApiClient` tracks rate limits globally (one budget per connection), but the calling workers can independently reason about their API call patterns without crossing concerns. The push worker knows it makes 2 calls per invoice; the pull worker knows it makes 1 call per poll cycle.
     - **The `IntegrationRegistry` already supports multi-interface resolution.** The registry resolves by `(domain, interface)`. `registry.resolve(ACCOUNTING, AccountingProvider.class)` returns the push adapter; `registry.resolve(ACCOUNTING, AccountingPaymentSource.class)` returns the pull adapter. For Xero, both resolve to the same bean (which implements both interfaces). For no-op, only the push resolution returns a useful adapter.
   - Cons:
     - **Two interfaces for one provider.** A developer must know that Xero implements both `AccountingProvider` and `AccountingPaymentSource`. The relationship is not obvious from either interface alone. Mitigated by: the implementation class `XeroAccountingProvider implements AccountingProvider, AccountingPaymentSource` makes the relationship explicit in one line.
     - **Slightly more complex wiring.** The pull worker must resolve the `AccountingPaymentSource` adapter separately from the push worker's `AccountingProvider` adapter. This is one additional registry call — trivial code but one more moving part.
     - **If all future providers support both push and pull, the separation is unnecessary overhead.** In practice, it is likely that most accounting providers (Xero, Sage Pastel, QuickBooks) will support both. The separation provides optionality that may never be exercised. However, the cost of the separation is near-zero (one more interface file), and the ISP benefit is real even if all implementations happen to implement both interfaces.

3. **Default method on `AccountingProvider`** — Add `getPaymentsModifiedSince` to `AccountingProvider` with a default implementation that returns an empty list. Adapters that support pull override the default. Adapters that do not support pull inherit the empty-list default.
   - Pros:
     - One interface, no breaking change. Existing implementations (`NoOpAccountingProvider`) continue to work without modification — they inherit the default.
     - The pull method is co-located with the push methods on the same interface. Discoverability is good — a developer looking at the interface sees all capabilities.
     - The default method signals "this is optional" — adapters that cannot pull simply do not override.
   - Cons:
     - **Default methods on interfaces conflate "optional" with "unimplemented."** A no-op return (empty list) looks the same as a broken implementation (adapter forgot to override). The pull worker cannot distinguish "this provider does not support pull" from "this provider's pull is broken and returning empty." With a separate interface, the distinction is structural: the adapter either implements `AccountingPaymentSource` (it supports pull) or it does not (it does not support pull).
     - **Interface growth over time.** If future phases add more operations (e.g., `getCreditNotesModifiedSince`, `getInvoicePdfUrl`, `revokeInvoice`), the interface accumulates default methods until it becomes a god interface where most methods are empty defaults. The separate-interface pattern scales better — each new operation is a new small interface.
     - **The `@IntegrationAdapter` annotation and `IntegrationRegistry` resolve adapters by interface type.** A default method does not change the type relationship — the no-op adapter is still an `AccountingProvider`, so the registry cannot distinguish "supports pull" from "does not support pull" at the type level. The pull worker would still need a runtime check (`if result is empty, is it because there are no payments or because the adapter is no-op?`).
     - **Violates the principle of explicit capability.** An adapter's capabilities should be evident from its type signature (`implements AccountingPaymentSource`), not inferred from whether it overrode a default method. Type-level capability declaration is checkable at compile time; default-method override is only verifiable at runtime.

**Decision**: Option 2 — Separate `AccountingPaymentSource` interface as a sibling port to `AccountingProvider`. `XeroAccountingProvider` implements both. The polling worker resolves `AccountingPaymentSource` from the registry; the push worker resolves `AccountingProvider`.

**Rationale**:

The Interface Segregation Principle states that clients should not be forced to depend on methods they do not use. The push worker calls `syncInvoice` and `syncCustomer` — it never calls `getPaymentsModifiedSince`. The pull worker calls `getPaymentsModifiedSince` — it never calls `syncInvoice` or `syncCustomer`. Putting all methods on one interface forces each worker to depend on the full surface, making the dependency graph broader than necessary and the test mocks larger than necessary.

The operational semantics are genuinely different. Push methods are event-driven (called when a domain event fires), synchronous per-entity (one invoice, one call), and write to the external system. The pull method is schedule-driven (called every 15 minutes), batch-oriented (returns a list of all payments since last poll), and reads from the external system. These are different operations with different error modes, different retry strategies, and different rate-limit implications. Grouping them on one interface obscures these differences.

The `AccountingProvider` port from Phase 21 was designed for push. Its methods (`syncInvoice`, `syncCustomer`, `testConnection`) all send data outward or verify the connection. Adding a read method changes the port's semantic — it is no longer a "push-to-accounting" port but a "do-everything-with-accounting" port. The sibling-interface approach preserves the original port's cohesion while adding the new capability as a separate, focused interface.

The `IntegrationRegistry` supports this cleanly. Phase 21's registry resolves adapters by `(domain, interfaceType)`. The same `@IntegrationAdapter(domain = ACCOUNTING, slug = "xero")` bean can implement multiple interfaces, and the registry resolves it regardless of which interface the caller requests. This is the same pattern used in Phase 25's `PaymentGateway` — the registry does not assume one interface per domain.

For `NoOpAccountingProvider`, the cleanest approach is to have it implement `AccountingPaymentSource` with a trivial `return List.of()`. This allows the polling worker to resolve the adapter without a null check, but the empty result signals "no payments" (which is correct — a no-op provider has no external system to poll). The polling worker's `lastPollAt` timestamp never advances for a no-op connection (because there is no connection), so in practice the worker never runs for no-op tenants. The implementation is defensive, not operational.

**Consequences**:

- Positive:
  - `AccountingProvider` remains a cohesive push-only interface. Its semantic is unchanged from Phase 21.
  - `AccountingPaymentSource` is a single-method interface: `List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since)`. It is the simplest possible contract for payment pull.
  - The push worker and pull worker have narrow, independent dependencies. Testing either in isolation requires mocking only the relevant interface.
  - Future providers can implement one or both interfaces, declaring their capabilities at the type level.
  - The pattern is consistent with `PaymentGateway` (Phase 25) — each integration domain has focused port interfaces rather than god interfaces.

- Negative:
  - Two interfaces where one might suffice (if all providers always support both push and pull). The additional interface is ~15 lines of code — trivial maintenance cost.
  - A developer encountering the codebase for the first time must understand that `AccountingProvider` and `AccountingPaymentSource` are siblings, and that `XeroAccountingProvider` implements both. This relationship is documented in the class declaration and in this ADR.
  - The `IntegrationRegistry` makes two resolution calls (one per worker) where a combined interface would require one. The registry uses a Caffeine cache with 60-second TTL — both calls hit the cache after the first resolution. Performance impact is negligible.

- Neutral:
  - The `ExternalPaymentEvent` record (returned by `getPaymentsModifiedSince`) is defined alongside the `AccountingPaymentSource` interface in the same package (`integration/accounting/`). It carries: `externalInvoiceReference` (matches Kazi-side `external_reference`), `externalPaymentId` (Xero payment ID for dedup), `amount`, `currency`, `paidAt`, and `status` (PAID, PARTIALLY_PAID, VOIDED).
  - `NoOpAccountingProvider implements AccountingProvider, AccountingPaymentSource` — it implements both interfaces for completeness, with trivial implementations (success result for push, empty list for pull).
  - Per [ADR-088](ADR-088-integration-port-package-structure.md), both interfaces live under `integration/accounting/`. The Xero implementation lives under `integration/accounting/xero/`. Package structure is unchanged — the new interface is a new file in an existing package.

- Related: [ADR-088](ADR-088-integration-port-package-structure.md) (integration port package structure), [ADR-098](ADR-098-payment-gateway-interface-design.md) (PaymentGateway — same single-purpose port pattern), [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero adapter — implements both interfaces), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync service — the consumer of both ports), [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) (polling — the pull worker that calls AccountingPaymentSource).
