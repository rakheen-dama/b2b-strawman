package io.b2mash.b2b.b2bstrawman.acceptance;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link AcceptanceRequest} entities. */
public interface AcceptanceRequestRepository extends JpaRepository<AcceptanceRequest, UUID> {

  Optional<AcceptanceRequest> findByRequestToken(String token);

  /**
   * Loads the request by token under a row lock ({@code SELECT … FOR UPDATE}). Every portal path
   * that mutates a request's terminal outcome (accept, decline) locks first, then checks state, so
   * a concurrent accept and decline serialise instead of overwriting each other (Phase 88 U23).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM AcceptanceRequest r WHERE r.requestToken = :token")
  Optional<AcceptanceRequest> findByRequestTokenForUpdate(@Param("token") String token);

  List<AcceptanceRequest> findByGeneratedDocumentIdOrderByCreatedAtDesc(UUID documentId);

  List<AcceptanceRequest> findByCustomerIdAndStatusInOrderByCreatedAtDesc(
      UUID customerId, List<AcceptanceStatus> statuses);

  List<AcceptanceRequest> findByStatusInAndExpiresAtBefore(
      List<AcceptanceStatus> statuses, Instant cutoff);

  Optional<AcceptanceRequest> findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(
      UUID documentId, UUID contactId, List<AcceptanceStatus> activeStatuses);

  /**
   * Counts acceptance requests for a customer whose status is in the given set. Used by matter
   * closure gate {@code ALL_ACCEPTANCE_REQUESTS_FINAL} — acceptance requests are scoped to a
   * customer (not a project), so the gate counts open acceptances across the matter's customer.
   */
  long countByCustomerIdAndStatusIn(
      UUID customerId, java.util.Collection<AcceptanceStatus> statuses);

  /**
   * Returns every acceptance request for the customer regardless of status. Used by DSAR audit
   * trail collection (Epic 505A) to resolve child-entity IDs for the customer-scoped audit-event
   * query.
   */
  List<AcceptanceRequest> findByCustomerId(UUID customerId);
}
