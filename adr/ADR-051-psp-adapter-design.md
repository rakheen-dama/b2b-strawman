# ADR-051: PSP Adapter Design

**Status**: Accepted

**Context**: Phase 10 introduces a "Record Payment" action on invoices. In v1, no external payment service provider (PSP) is integrated -- payment recording is a manual action by staff ("the client has paid; mark this invoice as paid"). However, the architecture must establish a clean seam for future PSP integration (e.g., Stripe, Paystack) so that adding real payment processing does not require changes to the invoice domain model, frontend, or controller layer.

Key constraints:
- No PSP SDKs or external service dependencies in this phase.
- The mock implementation must validate that the interface is usable: it should be called during the payment flow and produce a reference that's stored on the invoice.
- The system does not handle customer-facing payment portals or payment links in this phase.
- The interface must be simple enough to implement in a single phase but extensible enough to support async webhook-based flows (e.g., Stripe PaymentIntent) in the future.
- Provider selection must be configuration-driven (environment variable or property), not code-change-driven.

**Options Considered**:

1. **Synchronous `PaymentProvider` interface with `@ConditionalOnProperty` selection** -- A simple Java interface with a single `recordPayment(PaymentRequest): PaymentResult` method. Implementations are Spring beans selected by a configuration property.
   - Pros:
     - Minimal abstraction overhead: one interface, one method, two simple records (request/result).
     - `@ConditionalOnProperty` is a standard Spring Boot pattern for feature toggling -- no custom factory or registry needed.
     - Mock implementation is trivial: always return success with a generated reference.
     - The interface validates the seam: calling code (InvoiceService) depends on the interface, not the implementation. Swapping mock for Stripe requires only a new class + config change.
     - Synchronous model matches the v1 UX perfectly: user clicks "Record Payment", backend calls provider, immediate response.
   - Cons:
     - Synchronous model doesn't fit async payment flows (Stripe PaymentIntents need webhook confirmation). The interface would need to evolve.
     - No support for idempotency keys, webhook signature verification, or partial payment states -- these would be added with the real integration.

2. **Event-driven payment abstraction** -- Publish a `PaymentRequested` domain event; a listener handles it asynchronously. The invoice transitions to an intermediate `PAYMENT_PENDING` state and completes when a `PaymentConfirmed` event arrives.
   - Pros:
     - Natively supports async flows (Stripe webhooks map to `PaymentConfirmed` events).
     - Decouples the invoice service from payment timing.
   - Cons:
     - Massive over-engineering for v1: introduces a new intermediate state, event handlers, and async completion tracking -- all for a mock that always succeeds immediately.
     - The `PAYMENT_PENDING` state adds complexity to the state machine (what if confirmation never arrives? timeouts? retries?).
     - Requires a webhook endpoint even for the mock (or a self-invoking event loop).
     - Violates YAGNI: building for async before any real PSP integration exists means the abstraction may not match the actual PSP's model.

3. **No abstraction -- inline mock logic in InvoiceService** -- Hard-code the mock payment logic directly in the service. Refactor when a real PSP is added.
   - Pros:
     - Simplest possible implementation: no interface, no separate class.
     - Zero abstraction overhead.
   - Cons:
     - No seam: adding a real PSP means modifying `InvoiceService` directly, which violates the Open-Closed Principle.
     - Cannot test the payment flow in isolation.
     - The "mock" behavior is invisible -- there's no clear documentation of what needs to change for real integration.
     - Risk of the mock logic getting intertwined with business logic over time.

**Decision**: Synchronous `PaymentProvider` interface with `@ConditionalOnProperty` selection (Option 1).

**Rationale**: The synchronous interface strikes the right balance between proving the seam and avoiding premature complexity. The v1 payment flow is inherently synchronous (user action → immediate result), and the mock validates that `InvoiceService` delegates payment processing through a well-defined interface rather than embedding it.

The interface is intentionally simple:

```java
public interface PaymentProvider {
    PaymentResult recordPayment(PaymentRequest request);
}
```

Where `PaymentRequest` contains `(invoiceId, amount, currency, description)` and `PaymentResult` contains `(success, paymentReference, errorMessage)`. This covers the v1 use case (manual payment recording) and provides enough surface area for a real PSP implementation.

When a real PSP (e.g., Stripe) is integrated, the evolution path is clear:
1. Add `StripePaymentProvider implements PaymentProvider`.
2. Change `payment.provider` property from `mock` to `stripe`.
3. Add webhook endpoint (`/webhooks/stripe`) that listens for payment confirmation events.
4. Optionally, evolve the interface to support async results: add a `PaymentStatus getPaymentStatus(String paymentReference)` method for polling, or introduce an event-based completion callback.

The key insight is that the *interface itself* doesn't need to be async for async PSPs to work. The Stripe implementation can create a PaymentIntent synchronously (returning `success=true, paymentReference=pi_xxx`) and then update the invoice status via webhook when payment is confirmed. The interface evolution is additive, not breaking.

**Consequences**:
- New `PaymentProvider` interface in `invoice/` (or a `payment/` sub-package).
- `PaymentRequest` and `PaymentResult` records alongside the interface.
- `MockPaymentProvider` implementation: always returns `success=true` with reference `"MOCK-PAY-{UUID-short}"`. Logs the payment for debugging.
- Configuration: `payment.provider=mock` in `application.yml` (default). `@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)` on `MockPaymentProvider`.
- `InvoiceService` injects `PaymentProvider` and calls `recordPayment()` during the SENT → PAID transition.
- Future Stripe integration requires: one new class, one config change, one webhook endpoint. No changes to `InvoiceService`, `InvoiceController`, or the frontend.
- The mock provider is the default (`matchIfMissing = true`), so existing deployments and tests work without explicit configuration.
