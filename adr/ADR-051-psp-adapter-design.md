# ADR-051: PSP Adapter — Synchronous Strategy Interface

**Status**: Accepted

**Context**: Phase 10 introduces payment recording for invoices. In this phase, payment is a manual action — an admin clicks "Record Payment" and the system records the payment reference. However, the design must accommodate future integration with a real payment service provider (PSP) like Stripe, where payment confirmation may be asynchronous (webhook-driven).

The question is how to structure the payment integration seam: what interface does the invoice service call, and how is the implementation selected?

Key constraints:
- No external PSP SDKs or APIs in this phase — the implementation is entirely mocked.
- The seam must be clean enough that switching to a real PSP requires only a new implementation class and a configuration change.
- The invoice domain model and frontend should not change when the PSP implementation changes.
- The mock must be realistic enough to validate the integration points.

**Options Considered**:

1. **Synchronous strategy interface with `@ConditionalOnProperty`** — A `PaymentProvider` interface with a single `recordPayment(PaymentRequest): PaymentResult` method. Implementations are Spring beans selected via `@ConditionalOnProperty(name = "payment.provider")`. The mock returns success immediately. A future Stripe implementation would create a PaymentIntent and return the reference synchronously (blocking until Stripe confirms, or using Stripe's synchronous confirmation mode).
   - Pros:
     - **Simplest possible interface**: One method, one request, one result. No callbacks, no event handlers, no state machines.
     - **Clean substitution**: Switching providers is a config change (`payment.provider=stripe`) + adding a new `@Component` class.
     - **Testable**: The mock is a real Spring bean — integration tests use the mock naturally, no test-specific wiring needed.
     - **No infrastructure changes**: No message queues, no webhook endpoints, no async retry mechanisms.
     - **Validates the seam**: If the mock works through the full invoice lifecycle, a real implementation will too (the interface contract is proven).
   - Cons:
     - **Synchronous assumption**: Real PSPs often use asynchronous confirmation (webhooks). A synchronous wrapper around an async PSP would need to either block (bad for throughput) or use polling (complex).
     - **No webhook handling**: The interface does not model incoming payment notifications. A real Stripe integration would need a webhook endpoint to handle `payment_intent.succeeded` events.
     - **Limited abstraction**: The interface assumes payment is a single request-response. Real PSPs have multi-step flows (create intent → customer pays → webhook confirms → update invoice).

2. **Event-driven payment interface** — A `PaymentProvider` that publishes a `PaymentInitiated` event and listens for a `PaymentConfirmed` event. The mock immediately publishes `PaymentConfirmed`. A real implementation would publish `PaymentInitiated` when the Stripe PaymentIntent is created, and `PaymentConfirmed` when the webhook fires.
   - Pros:
     - Accurately models the async nature of real payment processing.
     - Decouples payment initiation from confirmation — the invoice service does not block.
     - Webhook-ready: the event-driven model naturally supports incoming PSP notifications.
   - Cons:
     - **Massive over-engineering for v1**: The mock immediately fires both events in the same thread. The entire event infrastructure (publisher, listener, event types, error handling, idempotency) is built for a mock that returns instantly.
     - **Complex invoice state model**: The invoice must have a `PENDING_PAYMENT` intermediate state between SENT and PAID. This adds another transition, another status badge, another set of edge cases.
     - **Testing complexity**: Event-driven flows are harder to test deterministically. Must handle event ordering, retry, and idempotency even for the mock.
     - **Infrastructure dependency**: Real event-driven payment processing needs a durable event store or message queue for reliability. This is far beyond Phase 10 scope.

3. **No abstraction — inline mock logic** — The `InvoiceLifecycleService.recordPayment()` method directly generates a mock reference. No interface, no separate class.
   - Pros:
     - Absolute minimum code. No extra classes, no Spring wiring.
     - YAGNI: if a real PSP is never integrated, no unused abstraction exists.
   - Cons:
     - **No seam for substitution**: Switching to a real PSP means modifying `InvoiceLifecycleService` directly, violating OCP (Open/Closed Principle).
     - **Testing**: Cannot inject a different payment behavior for different test scenarios (e.g., testing payment failure handling).
     - **Code smell**: Payment logic mixed into invoice lifecycle logic. Two concerns in one class.
     - **Future cost**: When a real PSP is needed, the refactoring to extract the interface is trivial but creates unnecessary churn in a battle-tested service.

**Decision**: Synchronous strategy interface with `@ConditionalOnProperty` (Option 1).

**Rationale**: The synchronous interface is the right level of abstraction for this phase. It proves the integration seam — the `InvoiceLifecycleService` calls `paymentProvider.recordPayment()` and handles the result, without knowing (or caring) whether the provider is mocked or real. This is the Strategy pattern applied correctly: the interface models the *capability* (record a payment), not the *mechanism* (sync vs. async).

When a real Stripe integration is needed (future phase), the migration path is:
1. Add `StripePaymentProvider implements PaymentProvider` with `@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")`.
2. For Stripe's synchronous confirmation mode (charge immediately), the existing interface works as-is.
3. For Stripe's async confirmation mode (PaymentIntents with webhooks), the interface evolves: `recordPayment()` returns a `PaymentResult` with `status = PENDING`, and a new webhook controller handles `payment_intent.succeeded` → updates the invoice. This evolution requires adding a webhook endpoint and a `PENDING_PAYMENT` status to `InvoiceStatus`, but does NOT require changing the core interface or the mock.

The key insight is that the interface does not need to model async flows today. It needs to model the seam — and the seam is proven by the mock.

**Consequences**:
- `PaymentProvider` interface with `recordPayment(PaymentRequest): PaymentResult` in package `io.b2mash.b2b.b2bstrawman.invoice.payment`.
- `MockPaymentProvider` annotated with `@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)`.
- `payment.provider: mock` in `application.yml` (default config).
- `InvoiceLifecycleService` injects `PaymentProvider` and calls it during the SENT → PAID transition.
- Integration tests use the mock naturally — no test-specific payment configuration needed.
- Future Stripe integration requires: a new `StripePaymentProvider` class, Stripe SDK dependency, `payment.provider: stripe` config, and potentially a webhook endpoint. No changes to `InvoiceLifecycleService`, `Invoice` entity, or frontend.
- The mock generates references in the format `MOCK-PAY-{uuid-short}` for easy identification in logs and UI.
