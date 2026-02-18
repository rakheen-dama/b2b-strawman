package io.b2mash.b2b.b2bstrawman.datarequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {

  List<DataSubjectRequest> findByStatus(String status);

  List<DataSubjectRequest> findByCustomerId(UUID customerId);

  List<DataSubjectRequest> findByStatusInAndDeadlineBefore(
      List<String> statuses, LocalDate deadline);

  long countByStatusIn(List<String> statuses);
}
