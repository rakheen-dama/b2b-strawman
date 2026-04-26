package io.b2mash.b2b.b2bstrawman.invoice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

  List<PaymentEvent> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

  /**
   * Batched lookup used to avoid N+1 when aggregating payments across many invoices (e.g. Statement
   * of Account previous-balance + period-payments computations).
   */
  List<PaymentEvent> findByInvoiceIdInOrderByCreatedAtDesc(Collection<UUID> invoiceIds);

  Optional<PaymentEvent> findBySessionIdAndStatus(String sessionId, PaymentEventStatus status);

  boolean existsBySessionIdAndStatus(String sessionId, PaymentEventStatus status);

  /**
   * Lookup all payment events for an invoice in a given status. Used by the trust-side reversal
   * cascade ({@code InvoiceTransitionService.reversePayment}) to:
   *
   * <ul>
   *   <li>Locate the single COMPLETED event matching a payment reference for deletion.
   *   <li>Decide whether the invoice should flip back to SENT (no other COMPLETED events remain) or
   *       stay PAID (multi-payment partial reversal case).
   * </ul>
   */
  List<PaymentEvent> findByInvoiceIdAndStatus(UUID invoiceId, PaymentEventStatus status);
}
