package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformationRequestRepository extends JpaRepository<InformationRequest, UUID> {

  List<InformationRequest> findByCustomerId(UUID customerId);

  List<InformationRequest> findByProjectId(UUID projectId);

  List<InformationRequest> findByStatusIn(List<RequestStatus> statuses);

  long countByStatus(RequestStatus status);
}
