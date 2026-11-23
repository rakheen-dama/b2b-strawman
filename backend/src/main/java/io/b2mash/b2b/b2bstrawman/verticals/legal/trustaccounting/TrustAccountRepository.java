package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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

  /**
   * The posting lock (650A, F-1): {@code SELECT … FOR SHARE} on the account row, taken by {@code
   * TrustPostingService} immediately before it stamps a row's {@code posted_at}. Postings share it
   * with each other; it conflicts with the {@link #findByIdForUpdate} lock the Rule 54.15 list
   * takes before it reads the books, so a posting either commits before the list reads (and is in
   * it) or stamps {@code posted_at} after the list's {@code locked_at} (and is shown against it).
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("SELECT t FROM TrustAccount t WHERE t.id = :id")
  Optional<TrustAccount> findByIdForShare(@Param("id") UUID id);

  /**
   * Phase 93 (657A): the account's take-on cutover date; empty when it was never taken on. A JPQL
   * scalar — always read from the database, never from a managed (possibly stale) {@code
   * TrustAccount} — so {@code TrustPostingService} reads it after taking the posting lock.
   */
  @Query("SELECT t.takeOnCutoverDate FROM TrustAccount t WHERE t.id = :id")
  Optional<LocalDate> findTakeOnCutoverDate(@Param("id") UUID id);
}
