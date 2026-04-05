package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustReconciliationRepository extends JpaRepository<TrustReconciliation, UUID> {

  List<TrustReconciliation> findByTrustAccountIdOrderByPeriodEndDesc(UUID trustAccountId);
}
