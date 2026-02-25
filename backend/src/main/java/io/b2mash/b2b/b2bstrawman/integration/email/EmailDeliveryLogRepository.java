package io.b2mash.b2b.b2bstrawman.integration.email;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, UUID> {

  Optional<EmailDeliveryLog> findByProviderMessageId(String providerMessageId);

  Page<EmailDeliveryLog> findByStatusAndCreatedAtBetween(
      String status, Instant from, Instant to, Pageable pageable);

  Page<EmailDeliveryLog> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

  long countByStatusAndCreatedAtAfter(String status, Instant after);

  long countByCreatedAtAfter(Instant after);
}
