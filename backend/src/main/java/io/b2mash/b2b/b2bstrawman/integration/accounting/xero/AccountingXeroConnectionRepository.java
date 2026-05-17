package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountingXeroConnectionRepository
    extends JpaRepository<AccountingXeroConnection, UUID> {

  @Query("SELECT c FROM AccountingXeroConnection c WHERE c.id = :id")
  Optional<AccountingXeroConnection> findOneById(@Param("id") UUID id);

  Optional<AccountingXeroConnection> findByOrgIntegrationId(UUID orgIntegrationId);

  @Query("SELECT c FROM AccountingXeroConnection c WHERE c.status = :status")
  List<AccountingXeroConnection> findByStatus(@Param("status") XeroConnectionStatus status);
}
