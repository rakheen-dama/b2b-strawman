package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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

  Page<TrustInvestment> findByTrustAccountIdOrderByDepositDateDesc(
      UUID trustAccountId, Pageable pageable);

  Page<TrustInvestment> findByTrustAccountIdAndInvestmentBasisOrderByDepositDateDesc(
      UUID trustAccountId, InvestmentBasis investmentBasis, Pageable pageable);

  List<TrustInvestment> findByCustomerId(UUID customerId);

  /**
   * The placement PAYMENT of an investment, read without loading the entity — so the withdrawal can
   * lock the placement before it locks (and first loads) the investment (PR #1757 security review).
   */
  @Query("SELECT i.depositTransactionId FROM TrustInvestment i WHERE i.id = :id")
  Optional<UUID> findPlacementTransactionIdById(@Param("id") UUID id);

  /** Locks the investment row (lock order: placement PAYMENT row first, then this). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM TrustInvestment i WHERE i.id = :id")
  Optional<TrustInvestment> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Locks the investment placed by this PAYMENT (lock order: placement PAYMENT row first, then
   * this).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM TrustInvestment i WHERE i.depositTransactionId = :placementId")
  Optional<TrustInvestment> findByPlacementTransactionIdForUpdate(
      @Param("placementId") UUID placementId);

  List<TrustInvestment> findByTrustAccountIdAndCustomerId(UUID trustAccountId, UUID customerId);

  List<TrustInvestment> findByStatusAndMaturityDateBetween(
      String status, LocalDate maturityStart, LocalDate maturityEnd);

  List<TrustInvestment> findByTrustAccountIdAndStatusAndMaturityDateBetween(
      UUID trustAccountId, String status, LocalDate maturityStart, LocalDate maturityEnd);
}
