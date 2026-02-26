package io.b2mash.b2b.b2bstrawman.tax;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

  List<TaxRate> findByActiveOrderBySortOrder(boolean active);

  List<TaxRate> findAllByOrderBySortOrder();

  Optional<TaxRate> findByIsDefaultTrue();

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  /** Counts draft invoice lines that reference this tax rate (used for deactivation guard). */
  @Query(
      "SELECT COUNT(il) FROM InvoiceLine il WHERE il.taxRateId = :taxRateId"
          + " AND il.invoiceId IN (SELECT i.id FROM Invoice i WHERE i.status ="
          + " io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus.DRAFT)")
  long countDraftInvoiceLinesByTaxRateId(@Param("taxRateId") UUID taxRateId);
}
