# ADR-098: PaymentGateway Interface Design

**Status**: Accepted

**Context**:

Phase 10 introduced a `PaymentProvider` interface in the `invoice/` package with a single synchronous method (`recordPayment(PaymentRequest): PaymentResult`) and a `MockPaymentProvider` implementation for manual payment recording. ADR-051 documented this as an intentional seam for future PSP integration. Phase 21 then introduced the integration port framework (`IntegrationDomain`, `IntegrationRegistry`, `@IntegrationAdapter`, `SecretStore`) with EMAIL, ACCOUNTING, AI, DOCUMENT_SIGNING, and PAYMENT domains. The EMAIL domain is the reference implementation: `EmailProvider` interface in `integration/email/` with `providerId()`, `sendEmail()`, `testConnection()`, and adapter registration via `@IntegrationAdapter`.

The `PaymentProvider` interface was not migrated to the integration port pattern during Phase 21. It remains in the `invoice/` package, lacks `providerId()` and `testConnection()`, and `InvoiceService` injects it directly via constructor injection rather than resolving through `IntegrationRegistry.resolve()`. The `MockPaymentProvider` registers with `@IntegrationAdapter(domain = PAYMENT, slug = "mock")`, but `IntegrationDomain.PAYMENT` has `defaultSlug = "noop"` -- no `"noop"` adapter exists. The registry works today only because `InvoiceService` bypasses it entirely.

Phase 25 adds online payment collection (Stripe Checkout, PayFast) which requires async flows that do not exist in the current interface: create a checkout session, redirect the client to a hosted payment page, receive a webhook callback, and reconcile payment status. The question is how to reconcile the legacy synchronous interface with the new async flows while aligning with the integration port pattern.

**Options Considered**:

1. **Two separate interfaces -- keep `PaymentProvider` (sync) and add `PaymentGateway` (async)** -- Keep `PaymentProvider` for manual recording, add a new `PaymentGateway` port interface in `integration/payment/` for online payment. `InvoiceService` uses both: `PaymentProvider` for manual recording, `PaymentGateway` (via registry) for checkout session creation.
   - Pros:
     - Zero breaking changes to existing code. `MockPaymentProvider` and `InvoiceService.recordPayment()` are untouched.
     - Separation of concerns: manual recording is conceptually different from online payment collection.
     - Each interface is focused: `PaymentProvider` has one method, `PaymentGateway` has three.
   - Cons:
     - Two interfaces for the same domain (`PAYMENT`) is confusing. `IntegrationRegistry.resolve()` returns a single interface per domain.
     - `InvoiceService` has two injection paths: one direct (`PaymentProvider`), one via registry (`PaymentGateway`). Inconsistent with how EMAIL works.
     - The `MockPaymentProvider` still bypasses the registry, so the "noop" default slug mismatch is not fixed.
     - Future PSP adapters (e.g., Peach Payments) must implement both interfaces or decide which one to support.

2. **Merge into one interface with both method sets** -- Create a `PaymentGateway` interface with all four methods: `recordManualPayment()`, `createCheckoutSession()`, `handleWebhook()`, `queryPaymentStatus()`. All adapters (mock, noop, stripe, payfast) implement it.
   - Pros:
     - Single interface per domain, consistent with how other domains work.
     - `IntegrationRegistry.resolve(PAYMENT, PaymentGateway.class)` works cleanly.
     - One adapter per PSP. No ambiguity about which interface to implement.
   - Cons:
     - Online-only adapters (Stripe, PayFast) must implement `recordManualPayment()` with a no-op or throw.
     - The NoOp/mock adapter must implement `createCheckoutSession()` with a no-op or sensible default.
     - Interface is wider than any single adapter needs (ISP violation), though mild.

3. **Deprecate `PaymentProvider`, consolidate into `PaymentGateway` (chosen)** -- Create `PaymentGateway` in `integration/payment/` as the single port interface for the PAYMENT domain. It includes `createCheckoutSession()`, `handleWebhook()`, `queryPaymentStatus()`, and `recordManualPayment()`. Migrate `InvoiceService` to resolve via `IntegrationRegistry`. Delete `PaymentProvider`. Create `NoOpPaymentGateway` (slug `"noop"`) that handles manual recording (always succeeds) and returns `NOT_SUPPORTED` for online methods. Stripe and PayFast adapters implement all four methods (with `recordManualPayment()` delegating to the same mark-as-paid flow).
   - Pros:
     - Fully aligned with the integration port pattern (EMAIL, ACCOUNTING, etc.). Single interface, single resolution path, `@IntegrationAdapter` registration.
     - Fixes the `defaultSlug = "noop"` mismatch -- a real `"noop"` adapter exists.
     - `InvoiceService` uses `IntegrationRegistry.resolve()` like every other service. No more direct injection bypass.
     - Clean upgrade path: future PSP adapters implement `PaymentGateway` and register with `@IntegrationAdapter`.
     - Manual recording and online payment are unified: `InvoiceService` always resolves one adapter. The adapter decides how to handle each operation based on its capabilities.
   - Cons:
     - Breaking change: `PaymentProvider` interface is deleted. Any code referencing it must be updated. In practice, only `InvoiceService` and `MockPaymentProvider` reference it.
     - Online adapters must implement `recordManualPayment()`. For Stripe/PayFast, this is trivial: return success (the invoice status transition is handled by `InvoiceService`, the adapter just confirms "yes, recording a manual payment is fine").
     - Requires updating existing tests that inject `MockPaymentProvider` directly.

**Decision**: Option 3 -- Deprecate `PaymentProvider`, consolidate into `PaymentGateway` in `integration/payment/`.

**Rationale**:

The core argument is consistency. Every other integration domain (EMAIL, ACCOUNTING, AI, DOCUMENT_SIGNING) follows the port pattern: interface in `integration/{domain}/`, resolution via `IntegrationRegistry.resolve()`, NoOp default adapter matching `defaultSlug`. The PAYMENT domain is the sole exception because it predates Phase 21. Keeping a legacy interface alongside the new one (Option 1) perpetuates this inconsistency and forces `InvoiceService` to juggle two resolution paths. Merging without cleanup (Option 2) has the same shape as Option 3 but doesn't fix the direct injection bypass.

Option 3 fixes all three defects in one move: (a) the interface moves to `integration/payment/`, (b) `InvoiceService` resolves via registry, (c) a `NoOpPaymentGateway` with slug `"noop"` eliminates the default slug mismatch. The breaking change is contained -- only `InvoiceService`, `MockPaymentProvider`, and their tests reference `PaymentProvider`.

The `NoOpPaymentGateway` serves two roles: it is the default for orgs without a configured PSP (manual recording always succeeds), and it returns a clear "online payment not configured" signal for `createCheckoutSession()`. `InvoiceService` checks the result and skips payment link generation when the adapter does not support online payments.

**Implementation sketch**:

```java
// integration/payment/PaymentGateway.java
public interface PaymentGateway {
    String providerId();
    CreateSessionResult createCheckoutSession(CheckoutRequest request);
    WebhookResult handleWebhook(String payload, Map<String, String> headers);
    PaymentStatus queryPaymentStatus(String sessionId);
    PaymentResult recordManualPayment(PaymentRequest request);
    ConnectionTestResult testConnection();
}

// integration/payment/NoOpPaymentGateway.java
@Component
@IntegrationAdapter(domain = IntegrationDomain.PAYMENT, slug = "noop")
public class NoOpPaymentGateway implements PaymentGateway {
    @Override
    public String providerId() { return "noop"; }

    @Override
    public CreateSessionResult createCheckoutSession(CheckoutRequest request) {
        return CreateSessionResult.notSupported("No payment provider configured");
    }

    @Override
    public PaymentResult recordManualPayment(PaymentRequest request) {
        String reference = "MANUAL-" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentResult(true, reference, null);
    }

    @Override
    public ConnectionTestResult testConnection() {
        return new ConnectionTestResult(true, "noop", null);
    }
    // ... handleWebhook and queryPaymentStatus return safe defaults
}
```

```java
// InvoiceService -- migrated to registry resolution
private PaymentGateway resolvePaymentGateway() {
    return integrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class);
}
```

**Consequences**:

- Positive:
  - PAYMENT domain is fully aligned with the integration port pattern. All five domains now follow the same resolution, caching, and adapter registration conventions.
  - The `defaultSlug = "noop"` mismatch is resolved. `IntegrationRegistry.resolve()` works correctly for PAYMENT.
  - `InvoiceService` no longer has a special injection path. It resolves `PaymentGateway` through the registry like `EmailDeliveryService` resolves `EmailProvider`.
  - Future PSP adapters (Peach Payments, Ozow, etc.) implement `PaymentGateway` and register with `@IntegrationAdapter`. No ambiguity about which interface to use.

- Negative:
  - `PaymentProvider`, `MockPaymentProvider`, `PaymentRequest`, and `PaymentResult` are deleted or relocated. Existing tests must be updated. Migration cost is low (contained to `invoice/` package).
  - `NoOpPaymentGateway.recordManualPayment()` generates a synthetic reference (`MANUAL-{uuid}`). In the old flow, `MockPaymentProvider` generated `MOCK-PAY-{uuid}`. If any tests assert on the prefix, they need updating.

- Neutral:
  - `PaymentRequest` and `PaymentResult` records move from `invoice/` to `integration/payment/`. Imports change but the shapes are identical.
  - `IntegrationService.testConnection()` can now handle `PAYMENT` domain instead of throwing. The switch case changes from `throw` to `resolve + testConnection()`.

- Related: [ADR-051](ADR-051-psp-adapter-design.md) (original `PaymentProvider` design, superseded by this ADR), [ADR-095](ADR-095-two-tier-email-resolution.md) (EMAIL port pattern used as reference).
