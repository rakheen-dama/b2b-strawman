package io.b2mash.b2b.b2bstrawman.invoice;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One payment posted to a fee note through {@link InvoiceTransitionService#postPayment} — the
 * single internal entry point every payment source uses (Phase 88 U15, ADR-347). The amount is the
 * money that actually moved; {@code destination} null means "the invoice's payment destination".
 */
public record PaymentPosting(
    BigDecimal amount,
    PaymentMethod method,
    String reference,
    String providerSlug,
    String sessionId,
    String destination) {

  /** {@code payment_events.provider_slug} for the firm app's Record Payment form. */
  public static final String MANUAL_PROVIDER = "manual";

  public PaymentPosting {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(providerSlug, "providerSlug must not be null");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
  }

  /** The Record Payment form (and the automation "mark PAID" action). */
  public static PaymentPosting manual(BigDecimal amount, PaymentMethod method, String reference) {
    return new PaymentPosting(amount, method, reference, MANUAL_PROVIDER, null, null);
  }

  PaymentPosting withReference(String newReference) {
    return new PaymentPosting(amount, method, newReference, providerSlug, sessionId, destination);
  }

  boolean isManualEntry() {
    return MANUAL_PROVIDER.equals(providerSlug);
  }

  /**
   * ADR-347 A4: the manual form refuses an excess (409) whatever method was picked; CARD (webhook),
   * XERO and TRUST_TRANSFER sources are capped at PAID and audited as {@code
   * invoice.payment_excess}.
   */
  boolean refusesExcess() {
    return isManualEntry() || method.isManual();
  }
}
