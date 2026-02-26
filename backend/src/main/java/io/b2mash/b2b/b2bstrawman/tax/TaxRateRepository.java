package io.b2mash.b2b.b2bstrawman.tax;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

  List<TaxRate> findByActiveOrderBySortOrder(boolean active);

  List<TaxRate> findAllByOrderBySortOrder();

  Optional<TaxRate> findByIsDefaultTrue();

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  // TODO(epic-182A): Replace stub with real JPQL join query when InvoiceLine.taxRateId field is
  // added
  // Stub guard: returns 0 until InvoiceLine.tax_rate_id column is added in a later epic
  default long countDraftInvoiceLinesByTaxRateId(UUID taxRateId) {
    return 0L;
  }
}
