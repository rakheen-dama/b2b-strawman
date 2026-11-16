package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The per-client lock anchors (ADR-378). Balances are not stored here — see {@code
 * TrustLedgerQueryRepository}.
 */
public interface ClientLedgerCardRepository extends JpaRepository<ClientLedgerCard, UUID> {

  Optional<ClientLedgerCard> findByTrustAccountIdAndCustomerId(
      UUID trustAccountId, UUID customerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT c FROM ClientLedgerCard c WHERE c.trustAccountId = :accountId AND c.customerId = :customerId")
  Optional<ClientLedgerCard> findByAccountAndCustomerForUpdate(
      @Param("accountId") UUID accountId, @Param("customerId") UUID customerId);

  Page<ClientLedgerCard> findByTrustAccountId(UUID trustAccountId, Pageable pageable);

  /**
   * The lock anchors of a page of clients (the client-ledgers list: id / createdAt / updatedAt).
   */
  List<ClientLedgerCard> findByTrustAccountIdAndCustomerIdIn(
      UUID trustAccountId, Collection<UUID> customerIds);

  /**
   * Ensures the client's lock anchor exists (ADR-378, ADR-231): {@code INSERT … ON CONFLICT DO
   * NOTHING}, safe when two first postings for the client race. Callers then lock it with {@link
   * #findByAccountAndCustomerForUpdate}. Returns the number of rows inserted (0 or 1).
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO client_ledger_cards (id, trust_account_id, customer_id, created_at, updated_at)
          VALUES (gen_random_uuid(), :accountId, :customerId, now(), now())
          ON CONFLICT (trust_account_id, customer_id) DO NOTHING
          """)
  int insertLockAnchorIfAbsent(
      @Param("accountId") UUID accountId, @Param("customerId") UUID customerId);
}
