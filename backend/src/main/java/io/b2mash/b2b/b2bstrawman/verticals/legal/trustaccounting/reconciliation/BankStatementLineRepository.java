package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {

  List<BankStatementLine> findByBankStatementIdOrderByLineNumber(UUID bankStatementId);

  List<BankStatementLine> findByMatchStatus(String matchStatus);

  List<BankStatementLine> findByBankStatementIdAndMatchStatusOrderByTransactionDateAscIdAsc(
      UUID bankStatementId, String matchStatus);

  /** The date of the statement's last line; empty for a statement with no lines (646C). */
  @Query(
      "SELECT MAX(l.transactionDate) FROM BankStatementLine l WHERE l.bankStatementId ="
          + " :statementId")
  Optional<LocalDate> findLastLineDate(@Param("statementId") UUID statementId);

  /**
   * File names of the account's other statements with a line dated in {@code (after, upTo]}, oldest
   * import first — §16.3.4's "another imported statement covers the gap" rule (646C).
   */
  @Query(
      """
      SELECT s.fileName FROM BankStatement s
      WHERE s.trustAccountId = :trustAccountId AND s.id <> :statementId
        AND EXISTS (SELECT l.id FROM BankStatementLine l WHERE l.bankStatementId = s.id
                      AND l.transactionDate > :after AND l.transactionDate <= :upTo)
      ORDER BY s.createdAt, s.id
      """)
  List<String> findOtherStatementsWithLinesBetween(
      @Param("trustAccountId") UUID trustAccountId,
      @Param("statementId") UUID statementId,
      @Param("after") LocalDate after,
      @Param("upTo") LocalDate upTo);

  long countByBankStatementIdAndMatchStatus(UUID bankStatementId, String matchStatus);

  /** The signed total of the statement's excluded lines (an explanatory figure; 646C). */
  @Query(
      """
      SELECT COALESCE(SUM(l.amount), 0) FROM BankStatementLine l
      WHERE l.bankStatementId = :statementId AND l.matchStatus = 'EXCLUDED'
      """)
  BigDecimal sumExcludedAmount(@Param("statementId") UUID statementId);
}
