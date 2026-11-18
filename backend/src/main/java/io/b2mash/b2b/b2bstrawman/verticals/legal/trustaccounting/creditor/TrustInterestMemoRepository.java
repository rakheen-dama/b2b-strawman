package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** At-source memo records (Phase 92 650B, ADR-384): one per trust account and month. */
public interface TrustInterestMemoRepository extends JpaRepository<TrustInterestMemo, UUID> {

  /**
   * The account's memo for the month ({@code periodMonth} = its first day), row-locked for an
   * upsert ({@code SELECT … FOR UPDATE} on the memo row only — never the trust account row: a memo
   * is not a posting and takes no part in the posting lock order).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT m FROM TrustInterestMemo m"
          + " WHERE m.trustAccountId = :trustAccountId AND m.periodMonth = :periodMonth")
  Optional<TrustInterestMemo> findForUpdate(
      @Param("trustAccountId") UUID trustAccountId, @Param("periodMonth") LocalDate periodMonth);

  /** The account's memos from {@code from} to {@code to} (first days of months), oldest first. */
  List<TrustInterestMemo> findByTrustAccountIdAndPeriodMonthBetweenOrderByPeriodMonthAsc(
      UUID trustAccountId, LocalDate from, LocalDate to);
}
