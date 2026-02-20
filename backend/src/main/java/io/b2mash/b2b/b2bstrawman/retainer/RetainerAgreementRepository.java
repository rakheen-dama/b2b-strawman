package io.b2mash.b2b.b2bstrawman.retainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetainerAgreementRepository extends JpaRepository<RetainerAgreement, UUID> {

  List<RetainerAgreement> findByStatus(RetainerStatus status);

  List<RetainerAgreement> findByCustomerId(UUID customerId);

  @Query(
      "SELECT ra FROM RetainerAgreement ra WHERE ra.customerId = :customerId"
          + " AND ra.status IN ('ACTIVE', 'PAUSED')")
  Optional<RetainerAgreement> findActiveOrPausedByCustomerId(@Param("customerId") UUID customerId);

  List<RetainerAgreement> findByCustomerIdAndStatus(UUID customerId, RetainerStatus status);
}
