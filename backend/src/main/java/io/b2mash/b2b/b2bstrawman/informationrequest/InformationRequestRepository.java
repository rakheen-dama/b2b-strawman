package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformationRequestRepository extends JpaRepository<InformationRequest, UUID> {

  List<InformationRequest> findByCustomerId(UUID customerId);

  List<InformationRequest> findByProjectId(UUID projectId);

  List<InformationRequest> findByStatusIn(List<RequestStatus> statuses);

  List<InformationRequest> findByStatus(RequestStatus status);

  List<InformationRequest> findByCustomerIdAndStatus(UUID customerId, RequestStatus status);

  List<InformationRequest> findByProjectIdAndStatus(UUID projectId, RequestStatus status);

  List<InformationRequest> findByCustomerIdAndProjectId(UUID customerId, UUID projectId);

  List<InformationRequest> findByCustomerIdAndProjectIdAndStatus(
      UUID customerId, UUID projectId, RequestStatus status);

  long countByStatus(RequestStatus status);

  long countByStatusAndCompletedAtAfter(RequestStatus status, Instant since);

  long countBySentAtAfter(Instant since);

  /**
   * Counts information requests on a project whose status is in the given set. Used by matter
   * closure gate {@code ALL_INFO_REQUESTS_CLOSED} (active statuses: SENT, IN_PROGRESS — DRAFT is
   * not yet "sent to client" and COMPLETED/CANCELLED are terminal).
   */
  long countByProjectIdAndStatusIn(UUID projectId, java.util.Collection<RequestStatus> statuses);
}
