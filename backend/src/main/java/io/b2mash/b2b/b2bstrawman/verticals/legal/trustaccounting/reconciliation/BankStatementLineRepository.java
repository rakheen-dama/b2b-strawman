package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {

  List<BankStatementLine> findByBankStatementIdOrderByLineNumber(UUID bankStatementId);

  List<BankStatementLine> findByMatchStatus(String matchStatus);

  List<BankStatementLine> findByBankStatementIdAndMatchStatusOrderByTransactionDateAscIdAsc(
      UUID bankStatementId, String matchStatus);
}
