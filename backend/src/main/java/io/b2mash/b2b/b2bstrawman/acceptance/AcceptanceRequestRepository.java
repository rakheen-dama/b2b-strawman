package io.b2mash.b2b.b2bstrawman.acceptance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link AcceptanceRequest} entities. */
public interface AcceptanceRequestRepository extends JpaRepository<AcceptanceRequest, UUID> {

  Optional<AcceptanceRequest> findByRequestToken(String token);

  List<AcceptanceRequest> findByGeneratedDocumentIdOrderByCreatedAtDesc(UUID documentId);

  List<AcceptanceRequest> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
      UUID customerId, List<AcceptanceStatus> statuses);

  List<AcceptanceRequest> findByStatusInAndExpiresAtBefore(
      List<AcceptanceStatus> statuses, Instant cutoff);

  Optional<AcceptanceRequest> findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(
      UUID documentId, UUID contactId, List<AcceptanceStatus> activeStatuses);
}
