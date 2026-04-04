package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustAccountRepository extends JpaRepository<TrustAccount, UUID> {

  List<TrustAccount> findByStatus(TrustAccountStatus status);

  Optional<TrustAccount> findByAccountTypeAndPrimaryTrue(TrustAccountType accountType);
}
