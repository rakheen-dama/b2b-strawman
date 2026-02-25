# ADR-100: Payment Link Lifecycle

**Status**: Accepted

**Context**:

Phase 25 generates payment links (Stripe Checkout Session URLs, PayFast redirect URLs) for invoices when a PSP is configured. The payment link is stored on the invoice (`paymentUrl`), included in the invoice email, and displayed as a "Pay Now" button on the portal invoice detail page. The lifecycle question is: when should payment links be generated, what happens when they expire, and how are expired links handled?

Key constraints and observations:
- Stripe Checkout Sessions expire after 24 hours by default (configurable up to 24 hours, not longer). After expiry, the URL returns a "session expired" page to the client.
- PayFast redirect URLs do not expire -- they are signed form POST URLs that PayFast processes on receipt.
- Invoice emails are sent once (on the APPROVED -> SENT transition). The email contains the payment URL. If the Stripe session expires, the email link is dead.
- Invoice amounts are immutable after the SENT transition. If an amount needs to change, the invoice is voided and a new one is created. This means the payment link amount never becomes stale.
- The portal invoice detail page is dynamic -- it can check the current link validity and offer a refresh option.

**Options Considered**:

1. **Generate on SENT, manual refresh (chosen)** -- Generate the payment link when the invoice transitions to SENT (alongside the email send). If the link expires (Stripe 24h), the portal page detects the expiry and shows a "Link expired -- refresh" button. The backend provides a `POST /api/invoices/{id}/refresh-payment-link` endpoint. The invoice email link becomes dead after Stripe expiry, but the portal page always has a working link.
   - Pros:
     - Simple and predictable: one generation point (SENT transition), one refresh mechanism.
     - The email link works for 24 hours (Stripe) or indefinitely (PayFast) -- most invoices are paid within this window.
     - Manual refresh gives the tenant control: they can decide whether to regenerate the link or switch to manual payment.
     - Portal page always shows the current state: working link, expired link with refresh option, or paid badge.
     - No background jobs or scheduled tasks. Refresh is user-initiated.
   - Cons:
     - Email link dies after 24h for Stripe. Clients who open the email after 24h see an expired session page. They must visit the portal for a fresh link.
     - Refresh requires a user action (tenant clicks "Regenerate" or client visits portal where auto-detection triggers refresh). No fully automatic recovery.

2. **Generate on SENT, auto-refresh on portal access** -- Same as Option 1, but when a portal contact loads the invoice detail page and the session is expired, the backend automatically generates a new session and returns the fresh URL. No manual intervention needed.
   - Pros:
     - Portal always has a working link -- zero friction for clients who access via the portal.
     - No manual "Regenerate" step for tenants.
     - Degrades gracefully: email link may expire, but portal self-heals.
   - Cons:
     - Auto-refresh on a read operation (GET invoice detail) has a side effect: it creates a new PSP session and updates the database. This violates the principle that GET requests should be idempotent/side-effect-free.
     - If the client's browser retries the request (network flap), multiple sessions may be created.
     - Cost: each Stripe session creation is an API call. Frequent portal page loads could create many sessions (Stripe doesn't charge for session creation, but it clutters the dashboard).
     - The portal read-model endpoint is in the `portal_read` schema -- it cannot directly call `PaymentLinkService` (which operates in the tenant schema). Would require a secondary call to the main backend.
     - Complexity: the portal page must detect "link expired" and either show a loading state while refreshing or pre-check before rendering.

3. **On-demand generation (just-in-time)** -- Don't generate the payment link on SENT. Instead, when the client clicks "Pay Now" on the portal, the portal makes a backend call that creates a fresh checkout session on the spot and redirects immediately.
   - Pros:
     - Links are always fresh -- no expiry problem. Created at the moment of use.
     - No wasted sessions for invoices that are paid manually or never paid online.
     - Simplest lifecycle: create, use, done.
   - Cons:
     - No payment link in the invoice email. The email can only say "Pay online via your portal" with a link to the portal page, not a direct payment link. This is a significantly worse UX than a "Pay Now" button in the email.
     - Adds latency to the payment flow: client clicks "Pay Now" -> backend creates session -> redirect. With pre-generation, the redirect is immediate.
     - The portal needs a write endpoint for session creation, complicating the read-only portal architecture (currently all portal invoice endpoints are read-only).
     - PayFast URLs don't expire, so just-in-time generation provides no benefit for PayFast tenants.

**Decision**: Option 1 -- Generate on SENT, manual refresh.

**Rationale**:

The primary UX goal is a "Pay Now" button in the invoice email. Option 3 eliminates this entirely, which is a dealbreaker -- email is the primary channel through which clients learn about invoices, and a direct payment link in the email dramatically increases payment conversion rates.

Between Options 1 and 2, the difference is manual vs. automatic refresh on portal access. Option 2 introduces a side effect on a GET request, violating REST conventions and adding complexity to the portal read-model architecture. The portal backend currently serves read-only projections from the `portal_read` schema -- adding session creation logic would require cross-schema calls or a secondary API request to the main backend.

Option 1 keeps the architecture clean: generation happens on SENT (a write operation), refresh is an explicit POST (a write operation), and all portal reads remain side-effect-free. The tradeoff is that Stripe email links expire after 24h. In practice, this is acceptable:

1. Most invoices are paid within 24h of receipt (industry data for email-linked payments).
2. For late payers, the portal page detects the expired session and offers a refresh. The client visits the portal (which the email also links to) and gets a fresh link.
3. PayFast URLs don't expire, so PayFast tenants are unaffected.
4. The tenant dashboard shows which invoices have expired payment links, enabling proactive follow-up.

The refresh endpoint (`POST /api/invoices/{id}/refresh-payment-link`) can be called by tenants from the invoice detail page, or potentially automated in a future phase (e.g., a daily job that regenerates expired Stripe sessions for unpaid invoices).

**Implementation sketch**:

```java
// PaymentLinkService.java
public void generatePaymentLink(Invoice invoice) {
    var gateway = integrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class);
    var result = gateway.createCheckoutSession(buildCheckoutRequest(invoice));
    if (result.success()) {
        invoice.setPaymentSessionId(result.sessionId());
        invoice.setPaymentUrl(result.redirectUrl());
        invoiceRepository.save(invoice);
        // Write CREATED payment event
    }
    // If not supported (NoOp), fields remain null -- no payment link
}

public void refreshPaymentLink(Invoice invoice) {
    // Cancel old session if provider supports it
    // Generate new session
    generatePaymentLink(invoice);
    // Sync to portal read-model
}
```

```java
// InvoiceService.send() -- trigger payment link generation
public InvoiceResponse send(UUID invoiceId) {
    // ... existing APPROVED -> SENT transition
    invoice.markSent();
    invoice = invoiceRepository.save(invoice);

    // Generate payment link if PSP is configured
    paymentLinkService.generatePaymentLink(invoice);

    // Send email (Phase 24) -- includes paymentUrl if non-null
    emailDeliveryService.sendInvoiceEmail(invoice);

    return InvoiceResponse.from(invoice);
}
```

Portal refresh flow:
```
Client visits portal invoice page
  -> GET /api/portal/invoices/{id}
  -> Response includes paymentUrl + paymentSessionId
  -> Frontend checks: if status == SENT && paymentUrl != null
     -> Display "Pay Now" button
     -> If Stripe session is expired (detected via client-side redirect or status endpoint):
        -> Show "Payment link expired. Contact {org} or check back shortly."
        -> Tenant can regenerate from their dashboard
```

**Consequences**:

- Positive:
  - Invoice email includes a direct "Pay Now" link. Best possible UX for the primary payment channel.
  - Clear separation of read (portal GET) and write (generate/refresh POST) operations.
  - Simple lifecycle: generate on SENT, refresh on demand. No background jobs.
  - PayFast tenants get permanent links. Stripe tenants get 24h links with manual refresh.

- Negative:
  - Stripe email links expire after 24h. Clients who open the email after 24h see an expired session page. Mitigation: the email also includes a portal link where the client can see the current invoice status.
  - Tenants must manually regenerate expired Stripe links (or a future automated job handles it). Mitigation: the invoice detail page surfaces expired link status prominently.

- Neutral:
  - Payment link generation adds one PSP API call to the SENT transition. For Stripe, this is a `Session.create()` call (~200ms). For PayFast, it's a local URL construction (no API call).
  - The `paymentUrl` is synced to the portal read-model via `InvoiceSyncEvent`. The portal page displays the URL as-is.
  - A future enhancement could add a scheduled job that regenerates expired Stripe sessions for unpaid invoices daily. This would require no architectural changes -- just a `@Scheduled` method calling `PaymentLinkService.refreshPaymentLink()`.

- Related: [ADR-098](ADR-098-payment-gateway-interface-design.md) (PaymentGateway interface used by PaymentLinkService), [ADR-099](ADR-099-webhook-tenant-identification-payments.md) (tenant identification in webhook callbacks after payment).
