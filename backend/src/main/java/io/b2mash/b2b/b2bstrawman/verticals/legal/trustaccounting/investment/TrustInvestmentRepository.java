package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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

public interface TrustInvestmentRepository extends JpaRepository<TrustInvestment, UUID> {

  /**
   * The investments funded from, or held in, the account (648A: the register accepts either the
   * funding trust banking account or the investment account), optionally of one basis, newest
   * deposit first. The id is the final key so the order is total: two placements sharing a deposit
   * date and creation instant cannot repeat on, or drop out of, consecutive pages (648B).
   */
  @Query(
      """
      SELECT i FROM TrustInvestment i
       WHERE (i.trustAccountId = :accountId OR i.investmentAccountId = :accountId)
         AND (:basis IS NULL OR i.investmentBasis = :basis)
       ORDER BY i.depositDate DESC, i.createdAt DESC, i.id
      """)
  Page<TrustInvestment> findByFundingOrInvestmentAccount(
      @Param("accountId") UUID accountId, @Param("basis") InvestmentBasis basis, Pageable pageable);

  List<TrustInvestment> findByCustomerId(UUID customerId);

  /**
   * The placement row of an investment, read without loading the entity — so the withdrawal can
   * lock the placement before it locks (and first loads) the investment (PR #1757 security review).
   */
  @Query("SELECT i.depositTransactionId FROM TrustInvestment i WHERE i.id = :id")
  Optional<UUID> findPlacementTransactionIdById(@Param("id") UUID id);

  /** Locks the investment row (lock order: placement row first, then this). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM TrustInvestment i WHERE i.id = :id")
  Optional<TrustInvestment> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Whether the investment account holds an investment in one of {@code statuses} (648C found.md
   * F-2: its basis cannot change while it does).
   */
  boolean existsByInvestmentAccountIdAndStatusIn(
      UUID investmentAccountId, Collection<String> statuses);

  /**
   * Whether an investment in one of {@code statuses} was placed from, or is held in, the account
   * (648A review F-4: the account cannot be closed while one is). An investment whose placement
   * still awaits approval is {@code ACTIVE}, so it counts.
   */
  @Query(
      """
      SELECT COUNT(i) > 0 FROM TrustInvestment i
       WHERE (i.trustAccountId = :accountId OR i.investmentAccountId = :accountId)
         AND i.status IN :statuses
      """)
  boolean existsByFundingOrInvestmentAccountAndStatusIn(
      @Param("accountId") UUID accountId, @Param("statuses") Collection<String> statuses);

  List<TrustInvestment> findByTrustAccountIdAndCustomerId(UUID trustAccountId, UUID customerId);

  List<TrustInvestment> findByStatusAndMaturityDateBetween(
      String status, LocalDate maturityStart, LocalDate maturityEnd);

  /** Active investments funded from, or held in, the account, maturing within the window. */
  @Query(
      """
      SELECT i FROM TrustInvestment i
       WHERE (i.trustAccountId = :accountId OR i.investmentAccountId = :accountId)
         AND i.status = :status AND i.maturityDate BETWEEN :start AND :end
       ORDER BY i.maturityDate ASC
      """)
  List<TrustInvestment> findMaturingByFundingOrInvestmentAccount(
      @Param("accountId") UUID accountId,
      @Param("status") String status,
      @Param("start") LocalDate maturityStart,
      @Param("end") LocalDate maturityEnd);
}
