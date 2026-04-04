package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientLedgerCardRepository extends JpaRepository<ClientLedgerCard, UUID> {

  Optional<ClientLedgerCard> findByTrustAccountIdAndCustomerId(
      UUID trustAccountId, UUID customerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT c FROM ClientLedgerCard c WHERE c.trustAccountId = :accountId AND c.customerId = :customerId")
  Optional<ClientLedgerCard> findByAccountAndCustomerForUpdate(
      @Param("accountId") UUID accountId, @Param("customerId") UUID customerId);

  Page<ClientLedgerCard> findByTrustAccountId(UUID trustAccountId, Pageable pageable);
}
