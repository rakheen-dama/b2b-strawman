package io.b2mash.b2b.b2bstrawman.integration.accounting;

import java.time.Instant;
import java.util.List;

/**
 * Port for pulling payment data from an external accounting system. Sibling to {@link
 * AccountingProvider} (push direction) — Interface Segregation Principle per ADR-279.
 */
public interface AccountingPaymentSource {

  /** Provider identifier (e.g., "xero", "quickbooks", "noop"). */
  String providerId();

  /** Retrieve payments modified since the given timestamp from the external system. */
  List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since);
}
