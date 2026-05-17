package io.b2mash.b2b.b2bstrawman.integration.accounting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingTaxCodeMappingRepository
    extends JpaRepository<AccountingTaxCodeMapping, UUID> {

  List<AccountingTaxCodeMapping> findByProviderId(String providerId);

  Optional<AccountingTaxCodeMapping> findByProviderIdAndKaziTaxMode(
      String providerId, String kaziTaxMode);
}
