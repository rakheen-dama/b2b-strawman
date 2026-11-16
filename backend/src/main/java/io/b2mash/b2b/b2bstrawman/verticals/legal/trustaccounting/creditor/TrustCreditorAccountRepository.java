package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustCreditorAccountRepository extends JpaRepository<TrustCreditorAccount, UUID> {

  List<TrustCreditorAccount> findByTrustAccountId(UUID trustAccountId);

  boolean existsByTrustAccountIdAndAccountType(
      UUID trustAccountId, TrustCreditorAccountType accountType);
}
