package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The columns of one trust transaction that the portal trust projection reads ({@code
 * TrustLedgerPortalSyncService}). {@link TrustTransactionRepository#findHistoryRowsByProjectId}
 * builds it from column values in a JPQL constructor expression, so it never passes through the
 * persistence context: a replay reads every row as committed, even a row the replaying thread's
 * transaction already holds as a managed (and since changed) entity (Phase 92 648D F-5).
 */
public record TrustTransactionHistoryRow(
    UUID id,
    String transactionType,
    BigDecimal amount,
    UUID clientId,
    UUID matterId,
    String reference,
    String description,
    LocalDate transactionDate,
    String status,
    Instant approvedAt,
    Instant createdAt,
    TrustLedger ledger,
    BigDecimal ledgerEffect) {

  /** The same columns from an already-loaded entity (the backfill's tenant-wide read). */
  public static TrustTransactionHistoryRow of(TrustTransaction txn) {
    return new TrustTransactionHistoryRow(
        txn.getId(),
        txn.getTransactionType(),
        txn.getAmount(),
        txn.getCustomerId(),
        txn.getProjectId(),
        txn.getReference(),
        txn.getDescription(),
        txn.getTransactionDate(),
        txn.getStatus(),
        txn.getApprovedAt(),
        txn.getCreatedAt(),
        txn.getLedger(),
        txn.getLedgerEffect());
  }
}
