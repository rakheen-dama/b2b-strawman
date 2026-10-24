package io.b2mash.b2b.b2bstrawman.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

  /** {@code payment_events.provider_slug} for a trust fee transfer (sourced as TRUST_TRANSFER). */
  public static final String TRUST_PROVIDER = "trust_fee_transfer";

  /** {@code payment_events.provider_slug} for a payment pulled from Xero (sourced as XERO). */
  public static final String XERO_PROVIDER = "xero";

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

  /**
   * Trust fee-transfer approval: the transfer amount, method TRUST_TRANSFER, destination TRUST; the
   * trust reference is the key the reversal cascade matches on.
   */
  public static PaymentPosting trustTransfer(BigDecimal amount, String trustReference) {
    return new PaymentPosting(
        amount, PaymentMethod.TRUST_TRANSFER, trustReference, TRUST_PROVIDER, null, "TRUST");
  }

  /** Pay-now webhook: the checkout session's amount, method CARD, the PSP slug and session id. */
  public static PaymentPosting card(
      BigDecimal amount, String providerSlug, String sessionId, String reference) {
    if (MANUAL_PROVIDER.equals(providerSlug)) {
      throw new IllegalArgumentException("a gateway payment cannot use the manual provider slug");
    }
    return new PaymentPosting(amount, PaymentMethod.CARD, reference, providerSlug, sessionId, null);
  }

  /**
   * Xero pull: the Xero payment amount (rounded to scale 2 — Xero amounts arrive via {@code
   * double}), method XERO; the Xero PaymentID is the reference the next poll dedups on.
   */
  public static PaymentPosting xero(BigDecimal amount, String externalPaymentId) {
    return new PaymentPosting(
        amount.setScale(2, RoundingMode.HALF_UP),
        PaymentMethod.XERO,
        externalPaymentId,
        XERO_PROVIDER,
        null,
        null);
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
