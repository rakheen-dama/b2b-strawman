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
    this.balance = this.balance.add(amount);
    this.totalDeposits = this.totalDeposits.add(amount);
    this.lastTransactionDate = transactionDate;
  }

  public void debitBalance(BigDecimal amount, LocalDate transactionDate) {
    this.balance = this.balance.subtract(amount);
    this.lastTransactionDate = transactionDate;
  }

  public void creditBalance(BigDecimal amount, LocalDate transactionDate) {
    this.balance = this.balance.add(amount);
    this.lastTransactionDate = transactionDate;
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
