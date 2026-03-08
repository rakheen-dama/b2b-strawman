package io.b2mash.b2b.b2bstrawman.billingrun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRunRepository extends JpaRepository<BillingRun, UUID> {

  Page<BillingRun> findByStatusIn(List<BillingRunStatus> statuses, Pageable pageable);

  boolean existsByStatusIn(List<BillingRunStatus> statuses);

  Page<BillingRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
