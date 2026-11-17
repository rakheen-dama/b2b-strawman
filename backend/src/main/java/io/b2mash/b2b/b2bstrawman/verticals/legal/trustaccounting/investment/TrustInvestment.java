package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "trust_investments")
public class TrustInvestment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** The funding trust banking account the money came from and returns to (Rule 54.14.7.3). */
  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  /**
   * The INVESTMENT trust account holding the money (648A, ADR-379); its basis equals the
   * investment's (service check).
   */
  @Column(name = "investment_account_id", nullable = false)
  private UUID investmentAccountId;

  /** The client — section 86(4) only; null for a section 86(3) investment (V23 CHECK). */
  @Column(name = "customer_id")
  private UUID customerId;

  /** The matter whose money is invested — section 86(4) only (V23 CHECK). */
  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "institution", nullable = false, length = 200)
  private String institution;

  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @Column(name = "principal", nullable = false, precision = 15, scale = 2)
  private BigDecimal principal;

  @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal interestRate;

  @Column(name = "deposit_date", nullable = false)
  private LocalDate depositDate;

  @Column(name = "maturity_date")
  private LocalDate maturityDate;

  @Column(name = "interest_earned", nullable = false, precision = 15, scale = 2)
  private BigDecimal interestEarned;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "withdrawal_date")
  private LocalDate withdrawalDate;

  @Column(name = "withdrawal_amount", precision = 15, scale = 2)
  private BigDecimal withdrawalAmount;

  /**
   * The placement row ({@code INV_PLACE} / {@code FIRM_INV_PLACE}). Null only inside the placing
   * transaction: the investment is saved first so the placement row can carry its id (§16.3.7).
   */
  @Column(name = "deposit_transaction_id")
  private UUID depositTransactionId;

  @Column(name = "withdrawal_transaction_id")
  private UUID withdrawalTransactionId;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(name = "investment_basis", nullable = false)
  private InvestmentBasis investmentBasis;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TrustInvestment() {}

  public TrustInvestment(
      UUID trustAccountId,
      UUID investmentAccountId,
      UUID customerId,
      UUID projectId,
      String institution,
      String accountNumber,
      BigDecimal principal,
      BigDecimal interestRate,
      LocalDate depositDate,
      LocalDate maturityDate,
      String notes,
      InvestmentBasis investmentBasis) {
    this.trustAccountId = trustAccountId;
    this.investmentAccountId = investmentAccountId;
    this.customerId = customerId;
    this.projectId = projectId;
    this.institution = institution;
    this.accountNumber = accountNumber;
    this.principal = principal;
    this.interestRate = interestRate;
    this.depositDate = depositDate;
    this.maturityDate = maturityDate;
    this.interestEarned = BigDecimal.ZERO;
    this.status = "ACTIVE";
    this.notes = notes;
    this.investmentBasis = investmentBasis;
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

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public UUID getInvestmentAccountId() {
    return investmentAccountId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getInstitution() {
    return institution;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public BigDecimal getPrincipal() {
    return principal;
  }

  public BigDecimal getInterestRate() {
    return interestRate;
  }

  public LocalDate getDepositDate() {
    return depositDate;
  }

  public LocalDate getMaturityDate() {
    return maturityDate;
  }

  public BigDecimal getInterestEarned() {
    return interestEarned;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getWithdrawalDate() {
    return withdrawalDate;
  }

  public BigDecimal getWithdrawalAmount() {
    return withdrawalAmount;
  }

  public UUID getDepositTransactionId() {
    return depositTransactionId;
  }

  public UUID getWithdrawalTransactionId() {
    return withdrawalTransactionId;
  }

  public String getNotes() {
    return notes;
  }

  public InvestmentBasis getInvestmentBasis() {
    return investmentBasis;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Setters (mutable fields only) ---

  /** Set once, inside the placing transaction, after the placement row is saved (§16.3.7). */
  public void setDepositTransactionId(UUID depositTransactionId) {
    this.depositTransactionId = depositTransactionId;
  }

  public void setInterestEarned(BigDecimal interestEarned) {
    this.interestEarned = interestEarned;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setWithdrawalDate(LocalDate withdrawalDate) {
    this.withdrawalDate = withdrawalDate;
  }

  public void setWithdrawalAmount(BigDecimal withdrawalAmount) {
    this.withdrawalAmount = withdrawalAmount;
  }

  public void setWithdrawalTransactionId(UUID withdrawalTransactionId) {
    this.withdrawalTransactionId = withdrawalTransactionId;
  }
}
