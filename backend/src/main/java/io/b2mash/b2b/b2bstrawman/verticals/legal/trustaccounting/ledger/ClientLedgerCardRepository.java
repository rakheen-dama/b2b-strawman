package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
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

  /** Sums the balance across all client ledger cards for a trust account. */
  @Query(
      "SELECT COALESCE(SUM(c.balance), 0) FROM ClientLedgerCard c WHERE c.trustAccountId = :trustAccountId")
  BigDecimal calculateTotalTrustBalance(@Param("trustAccountId") UUID trustAccountId);

  /** Sums the balance across all client ledger cards for a customer. Used by TrustBoundaryGuard. */
  @Query(
      "SELECT COALESCE(SUM(c.balance), 0) FROM ClientLedgerCard c WHERE c.customerId = :customerId")
  BigDecimal sumBalancesForCustomer(@Param("customerId") UUID customerId);

  /**
   * Batch variant of {@link #sumBalancesForCustomer}: one row per customer that has at least one
   * ledger card (absent customers have a zero balance). Used by the Matters list (Phase 87, Epic
   * 615A) and reusable by the Clients list.
   */
  @Query(
      """
      SELECT c.customerId AS customerId, COALESCE(SUM(c.balance), 0) AS balance
      FROM ClientLedgerCard c
      WHERE c.customerId IN :customerIds
      GROUP BY c.customerId
      """)
  List<ClientTrustBalanceProjection> sumBalancesByCustomerIdIn(
      @Param("customerIds") Collection<UUID> customerIds);
}
