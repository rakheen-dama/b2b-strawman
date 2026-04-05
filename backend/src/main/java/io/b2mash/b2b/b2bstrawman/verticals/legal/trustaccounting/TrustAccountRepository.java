package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustAccountRepository extends JpaRepository<TrustAccount, UUID> {

  List<TrustAccount> findByStatus(TrustAccountStatus status);

  Optional<TrustAccount> findByAccountTypeAndPrimaryTrue(TrustAccountType accountType);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM TrustAccount t WHERE t.id = :id")
  Optional<TrustAccount> findByIdForUpdate(@Param("id") UUID id);
}
