package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "client_ledger_cards")
public class ClientLedgerCard {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal balance = BigDecimal.ZERO;

  @Column(name = "total_deposits", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalDeposits = BigDecimal.ZERO;

  @Column(name = "total_payments", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalPayments = BigDecimal.ZERO;

  @Column(name = "total_fee_transfers", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalFeeTransfers = BigDecimal.ZERO;

  @Column(name = "total_interest_credited", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalInterestCredited = BigDecimal.ZERO;

  @Column(name = "last_transaction_date")
  private LocalDate lastTransactionDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ClientLedgerCard() {}

  public ClientLedgerCard(UUID trustAccountId, UUID customerId) {
    this.trustAccountId = trustAccountId;
    this.customerId = customerId;
  }

  @PrePersist
  protected void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public void addDeposit(BigDecimal amount, LocalDate transactionDate) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    this.balance = this.balance.add(amount);
    this.totalDeposits = this.totalDeposits.add(amount);
    updateLastTransactionDate(transactionDate);
  }

  /**
   * Subtracts the given amount from the balance. Used for inter-client transfers. Running totals
   * (totalPayments, totalFeeTransfers) are NOT updated here — those are updated by type-specific
   * methods added in future slices (PAYMENT, FEE_TRANSFER transaction types).
   */
  public void debitBalance(BigDecimal amount, LocalDate transactionDate) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    this.balance = this.balance.subtract(amount);
    updateLastTransactionDate(transactionDate);
  }

  public void creditBalance(BigDecimal amount, LocalDate transactionDate) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    this.balance = this.balance.add(amount);
    updateLastTransactionDate(transactionDate);
  }

  /** Debits balance for a PAYMENT transaction. Updates totalPayments running total. */
  public void recordPayment(BigDecimal amount, LocalDate transactionDate) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    this.balance = this.balance.subtract(amount);
    this.totalPayments = this.totalPayments.add(amount);
    updateLastTransactionDate(transactionDate);
  }

  /** Debits balance for a FEE_TRANSFER transaction. Updates totalFeeTransfers running total. */
  public void recordFeeTransfer(BigDecimal amount, LocalDate transactionDate) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    this.balance = this.balance.subtract(amount);
    this.totalFeeTransfers = this.totalFeeTransfers.add(amount);
    updateLastTransactionDate(transactionDate);
  }

  /** Debits balance for a REFUND transaction. Updates balance only (no separate refund total). */
  public void recordRefund(BigDecimal amount, LocalDate transactionDate) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    this.balance = this.balance.subtract(amount);
    updateLastTransactionDate(transactionDate);
  }

  /** Only advances lastTransactionDate — never moves it backwards for backdated transactions. */
  private void updateLastTransactionDate(LocalDate transactionDate) {
    if (this.lastTransactionDate == null
        || (transactionDate != null && transactionDate.isAfter(this.lastTransactionDate))) {
      this.lastTransactionDate = transactionDate;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public BigDecimal getTotalDeposits() {
    return totalDeposits;
  }

  public BigDecimal getTotalPayments() {
    return totalPayments;
  }

  public BigDecimal getTotalFeeTransfers() {
    return totalFeeTransfers;
  }

  public BigDecimal getTotalInterestCredited() {
    return totalInterestCredited;
  }

  public LocalDate getLastTransactionDate() {
    return lastTransactionDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
