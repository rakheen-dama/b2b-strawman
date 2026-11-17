package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnidentifiedDepositRepository extends JpaRepository<UnidentifiedDeposit, UUID> {

  /** Lock order for every item operation: the item row first (§16.3.6). Used by 647D. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT d FROM UnidentifiedDeposit d WHERE d.id = :id")
  Optional<UnidentifiedDeposit> findByIdForUpdate(@Param("id") UUID id);

  /**
   * The item a recorded line opened (by its opening row — a line unmatched and re-recorded opens a
   * second item on the same line), locked: the unmatch hook (647B). Taken after the line lock and
   * before any transaction lock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT d FROM UnidentifiedDeposit d WHERE d.depositTransactionId = :transactionId")
  Optional<UnidentifiedDeposit> findByDepositTransactionIdForUpdate(
      @Param("transactionId") UUID transactionId);

  /** Oldest first (idx_unidentified_deposits_account_status). */
  Page<UnidentifiedDeposit> findByTrustAccountIdOrderByReceivedDateAscCreatedAtAscIdAsc(
      UUID trustAccountId, Pageable pageable);

  /** One status, oldest first (idx_unidentified_deposits_account_status). */
  Page<UnidentifiedDeposit> findByTrustAccountIdAndStatusOrderByReceivedDateAscCreatedAtAscIdAsc(
      UUID trustAccountId, UnidentifiedDepositStatus status, Pageable pageable);
}
