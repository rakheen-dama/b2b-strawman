package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Locked Rule 54.15 lists (Phase 92 650A, ADR-382). Rows are only ever inserted. */
public interface TrustMonthlyListRepository extends JpaRepository<TrustMonthlyList, UUID> {

  Optional<TrustMonthlyList> findByTrustAccountIdAndPeriodEnd(
      UUID trustAccountId, LocalDate periodEnd);

  /** The account's locked lists, newest first. */
  List<TrustMonthlyList> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);

  /** The list a reconciliation locked, if any — a scalar read for the reconciliation response. */
  @Query("SELECT m.id FROM TrustMonthlyList m WHERE m.reconciliationId = :reconciliationId")
  Optional<UUID> findIdByReconciliationId(@Param("reconciliationId") UUID reconciliationId);

  /**
   * For each locked list of the account with {@code period_end} on or before {@code asAt}: the
   * posted rows dated on or before its period end and posted after it was locked (§16.3.10) — their
   * count and net cashbook effect. Lists with no such row are not returned. One statement for the
   * account (the integrity check's statement budget). Oldest list first.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT m.id AS monthlyListId, m.period_end AS periodEnd, count(t.id) AS entryCount,
                 COALESCE(SUM(t.cashbook_effect), 0) AS cashbookEffect
            FROM trust_monthly_lists m
            JOIN trust_transactions t
              ON t.trust_account_id = m.trust_account_id
             AND t.transaction_date <= m.period_end
             AND t.posted_at IS NOT NULL
             AND t.posted_at > m.locked_at
           WHERE m.trust_account_id = :trustAccountId
             AND m.period_end <= CAST(:asAt AS date)
           GROUP BY m.id, m.period_end
           ORDER BY m.period_end, m.id""")
  List<BackdatedListRow> backdatedAfterLock(
      @Param("trustAccountId") UUID trustAccountId, @Param("asAt") LocalDate asAt);

  /** One row of {@link #backdatedAfterLock}. */
  interface BackdatedListRow {

    UUID getMonthlyListId();

    LocalDate getPeriodEnd();

    long getEntryCount();

    BigDecimal getCashbookEffect();
  }
}
