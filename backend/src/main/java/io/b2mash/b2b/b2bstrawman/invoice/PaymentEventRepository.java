package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Row lock on one payment event of one fee note ({@code SELECT … FOR UPDATE}, Phase 89 632B). The
   * receipt paths take the invoice lock first ({@code InvoiceRepository.findByIdForUpdate}), then
   * this one — the same order as {@code postPayment} / {@code reversePayment}. Scoped by invoice so
   * a payment event of another fee note never resolves (portal isolation, §14.6.2).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT pe FROM PaymentEvent pe WHERE pe.id = :id AND pe.invoiceId = :invoiceId")
  Optional<PaymentEvent> findByIdAndInvoiceIdForUpdate(
      @Param("id") UUID id, @Param("invoiceId") UUID invoiceId);

  /** Dedup check for payment pull reconciliation — prevents duplicate PaymentEvent on re-poll. */
  boolean existsByInvoiceIdAndPaymentReference(UUID invoiceId, String paymentReference);
}
