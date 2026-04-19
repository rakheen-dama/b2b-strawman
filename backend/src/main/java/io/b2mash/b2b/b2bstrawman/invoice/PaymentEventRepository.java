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
}
