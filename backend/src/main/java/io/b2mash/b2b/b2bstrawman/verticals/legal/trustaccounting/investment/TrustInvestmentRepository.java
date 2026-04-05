package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustInvestmentRepository extends JpaRepository<TrustInvestment, UUID> {

  Page<TrustInvestment> findByTrustAccountIdOrderByDepositDateDesc(
      UUID trustAccountId, Pageable pageable);

  List<TrustInvestment> findByCustomerId(UUID customerId);

  List<TrustInvestment> findByStatusAndMaturityDateBetween(
      String status, LocalDate maturityStart, LocalDate maturityEnd);
}
