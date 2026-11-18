package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Section 86(4) interest entries (Phase 92 648B, ADR-379). */
public interface TrustInvestmentInterestRepository
    extends JpaRepository<TrustInvestmentInterest, UUID> {

  /** The investment's entries, oldest first. */
  List<TrustInvestmentInterest> findByInvestmentIdOrderByEntryDateAscCreatedAtAsc(
      UUID investmentId);

  /**
   * The investment's entries, POSTED and REVERSED — a bank advice's reference ordinal (read under
   * the investment's lock).
   */
  long countByInvestmentId(UUID investmentId);

  /** The entry's investment, read without loading the entry (lock order: investment first). */
  @Query("SELECT e.investmentId FROM TrustInvestmentInterest e WHERE e.id = :id")
  Optional<UUID> findInvestmentIdById(@Param("id") UUID id);

  /** Locks the entry (lock order: the investment row first, then this). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM TrustInvestmentInterest e WHERE e.id = :id")
  Optional<TrustInvestmentInterest> findByIdForUpdate(@Param("id") UUID id);

  /**
   * The POSTED entry recorded from a statement line (the unmatch hook), read without loading it so
   * its investment can be locked first.
   */
  @Query(
      """
      SELECT e.id FROM TrustInvestmentInterest e
       WHERE e.bankStatementLineId = :lineId AND e.status = 'POSTED'
      """)
  Optional<UUID> findPostedIdByBankStatementLineId(@Param("lineId") UUID lineId);

  /** The date of the investment's latest POSTED entry (648B OQ-1: no withdrawal before it). */
  @Query(
      """
      SELECT MAX(e.entryDate) FROM TrustInvestmentInterest e
       WHERE e.investmentId = :investmentId AND e.status = 'POSTED'
      """)
  Optional<LocalDate> findLatestPostedEntryDate(@Param("investmentId") UUID investmentId);

  /**
   * Σ gross, Σ Fund share and Σ net of the POSTED entries of each investment in {@code
   * investmentIds} — one query for a page (callers short-circuit an empty list). An investment with
   * no posted entry has no row.
   */
  @Query(
      """
      SELECT e.investmentId AS investmentId, SUM(e.grossAmount) AS gross,
             SUM(e.fundShareAmount) AS fundShare, SUM(e.netAmount) AS net
        FROM TrustInvestmentInterest e
       WHERE e.investmentId IN :investmentIds AND e.status = 'POSTED'
       GROUP BY e.investmentId
      """)
  List<InterestTotals> sumPostedByInvestmentIds(
      @Param("investmentIds") Collection<UUID> investmentIds);

  /** The POSTED totals of one investment (projection). */
  interface InterestTotals {
    UUID getInvestmentId();

    BigDecimal getGross();

    BigDecimal getFundShare();

    BigDecimal getNet();
  }
}
