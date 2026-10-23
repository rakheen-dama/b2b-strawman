package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InformationRequestRepository extends JpaRepository<InformationRequest, UUID> {

  List<InformationRequest> findByCustomerId(UUID customerId);

  /**
   * FICA status batch (ADR-348): the given clients' requests on any template of pack {@code
   * packId}, excluding status {@code excluded} (callers pass {@code CANCELLED}). The pack lookup is
   * a subquery so the batch costs one statement here (Clients list cap, Epic 615B).
   */
  @Query(
      """
      SELECT r FROM InformationRequest r
      WHERE r.customerId IN :customerIds
        AND r.status <> :excluded
        AND r.requestTemplateId IN (SELECT t.id FROM RequestTemplate t WHERE t.packId = :packId)
      """)
  List<InformationRequest> findFicaRequestsForClients(
      @Param("customerIds") Collection<UUID> customerIds,
      @Param("packId") String packId,
      @Param("excluded") RequestStatus excluded);

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
  long countByProjectIdAndStatusIn(UUID projectId, Collection<RequestStatus> statuses);
}
