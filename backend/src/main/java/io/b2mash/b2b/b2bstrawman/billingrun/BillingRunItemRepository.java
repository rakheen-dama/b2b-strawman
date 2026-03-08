package io.b2mash.b2b.b2bstrawman.billingrun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingRunItemRepository extends JpaRepository<BillingRunItem, UUID> {

  List<BillingRunItem> findByBillingRunId(UUID billingRunId);

  List<BillingRunItem> findByBillingRunIdAndStatus(UUID billingRunId, BillingRunItemStatus status);

  @Modifying
  @Query("DELETE FROM BillingRunItem i WHERE i.billingRunId = :billingRunId")
  void deleteByBillingRunId(@Param("billingRunId") UUID billingRunId);
}
