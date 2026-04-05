package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {

  List<BankStatement> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);
}
