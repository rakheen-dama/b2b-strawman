package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Native projection for {@link TrustTransactionRepository#findClientHistoryWithRunningBalance} —
 * one trust transaction plus its server-computed running balance (ADR-361, arch §14.3.3). {@link
 * #getRunningBalance()} is null for non-posted (AWAITING_APPROVAL / REJECTED / REVERSED) and
 * REVERSAL rows.
 *
 * <p>Deliberately an interface, not a record: {@code WireVocabularyContractTest} scans records
 * whose name ends in {@code Row}, and the stored column names ({@code customerId}, {@code
 * projectId}) are mapped to the wire vocabulary by {@code TrustTransactionResponse}, not here.
 */
public interface ClientHistoryRow {
  UUID getId();

  UUID getTrustAccountId();

  String getTransactionType();

  BigDecimal getAmount();

  UUID getCustomerId();

  UUID getProjectId();

  UUID getCounterpartyCustomerId();

  UUID getInvoiceId();

  String getReference();

  String getDescription();

  LocalDate getTransactionDate();

  String getStatus();

  UUID getApprovedBy();

  Instant getApprovedAt();

  UUID getSecondApprovedBy();

  Instant getSecondApprovedAt();

  UUID getRejectedBy();

  Instant getRejectedAt();

  String getRejectionReason();

  UUID getReversalOf();

  UUID getReversedById();

  UUID getBankStatementLineId();

  UUID getRecordedBy();

  Instant getCreatedAt();

  BigDecimal getRunningBalance();
}
