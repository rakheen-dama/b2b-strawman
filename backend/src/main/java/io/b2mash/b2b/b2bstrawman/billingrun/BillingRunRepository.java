package io.b2mash.b2b.b2bstrawman.billingrun;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingRunRepository extends JpaRepository<BillingRun, UUID> {

  Page<BillingRun> findByStatusIn(List<BillingRunStatus> statuses, Pageable pageable);

  boolean existsByStatusIn(List<BillingRunStatus> statuses);

  Page<BillingRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT br FROM BillingRun br WHERE br.id = :id")
  Optional<BillingRun> findByIdForUpdate(@Param("id") UUID id);
}
