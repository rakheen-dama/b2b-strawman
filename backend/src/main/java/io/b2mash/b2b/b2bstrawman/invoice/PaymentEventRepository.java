package io.b2mash.b2b.b2bstrawman.invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

  List<PaymentEvent> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

  Optional<PaymentEvent> findBySessionIdAndStatus(String sessionId, PaymentEventStatus status);

  boolean existsBySessionIdAndStatus(String sessionId, PaymentEventStatus status);
}
