package io.b2mash.b2b.b2bstrawman.billingrun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingRunEntrySelectionRepository
    extends JpaRepository<BillingRunEntrySelection, UUID> {

  List<BillingRunEntrySelection> findByBillingRunItemId(UUID billingRunItemId);

  @Modifying
  @Query("DELETE FROM BillingRunEntrySelection s WHERE s.billingRunItemId = :billingRunItemId")
  void deleteByBillingRunItemId(@Param("billingRunItemId") UUID billingRunItemId);

  @Modifying
  @Query(
      value =
          "DELETE FROM billing_run_entry_selections WHERE billing_run_item_id IN "
              + "(SELECT id FROM billing_run_items WHERE billing_run_id = :billingRunId)",
      nativeQuery = true)
  void deleteByBillingRunId(@Param("billingRunId") UUID billingRunId);
}
