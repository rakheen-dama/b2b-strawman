package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trust_reconciliations")
public class TrustReconciliation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "bank_statement_id")
  private UUID bankStatementId;

  @Column(name = "bank_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal bankBalance;

  @Column(name = "cashbook_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal cashbookBalance;

  @Column(name = "client_ledger_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal clientLedgerTotal;

  @Column(name = "outstanding_deposits", nullable = false, precision = 15, scale = 2)
  private BigDecimal outstandingDeposits;

  @Column(name = "outstanding_payments", nullable = false, precision = 15, scale = 2)
  private BigDecimal outstandingPayments;

  @Column(name = "adjusted_bank_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal adjustedBankBalance;

  // The creditor side as calculated as at period_end (646C, V19, §16.2.11).
  @Column(name = "investment_creditor_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal investmentCreditorTotal;

  @Column(name = "fund_ledger_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal fundLedgerBalance;

  @Column(name = "unidentified_deposit_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal unidentifiedDepositTotal;

  @Column(name = "inter_account_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal interAccountBalance;

  /** The six-term creditor side (§16.1.2 {@code creditors(A,D)}). */
  @Column(name = "creditor_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal creditorTotal;

  /** {@code adjusted_bank_balance − creditor_total} (§16.3.4 step 3). */
  @Column(name = "difference", nullable = false, precision = 15, scale = 2)
  private BigDecimal difference;

  @Column(name = "is_balanced", nullable = false)
  private boolean isBalanced;

  /**
   * The integrity flags at the last calculation, blocking and warning (V19 column, written since
   * 649C, §16.3.4 step 5): what the books showed when the reconciliation was last calculated or
   * completed.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "integrity_flags", nullable = false, columnDefinition = "jsonb")
  private List<IntegrityFlag> integrityFlags = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ReconciliationStatus status;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TrustReconciliation() {}

  public TrustReconciliation(UUID trustAccountId, LocalDate periodEnd, UUID bankStatementId) {
    this.trustAccountId = trustAccountId;
    this.periodEnd = periodEnd;
    this.bankStatementId = bankStatementId;
    this.bankBalance = BigDecimal.ZERO;
    this.cashbookBalance = BigDecimal.ZERO;
    this.clientLedgerTotal = BigDecimal.ZERO;
    this.outstandingDeposits = BigDecimal.ZERO;
    this.outstandingPayments = BigDecimal.ZERO;
    this.adjustedBankBalance = BigDecimal.ZERO;
    this.investmentCreditorTotal = BigDecimal.ZERO;
    this.fundLedgerBalance = BigDecimal.ZERO;
    this.unidentifiedDepositTotal = BigDecimal.ZERO;
    this.interAccountBalance = BigDecimal.ZERO;
    this.creditorTotal = BigDecimal.ZERO;
    this.difference = BigDecimal.ZERO;
    this.isBalanced = false;
    this.status = ReconciliationStatus.DRAFT;
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

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public UUID getBankStatementId() {
    return bankStatementId;
  }

  /** Re-points a DRAFT to the statement the wizard now reconciles against (652C, §16.3.12). */
  public void setBankStatementId(UUID bankStatementId) {
    this.bankStatementId = bankStatementId;
  }

  public BigDecimal getBankBalance() {
    return bankBalance;
  }

  public void setBankBalance(BigDecimal bankBalance) {
    this.bankBalance = bankBalance;
  }

  public BigDecimal getCashbookBalance() {
    return cashbookBalance;
  }

  public void setCashbookBalance(BigDecimal cashbookBalance) {
    this.cashbookBalance = cashbookBalance;
  }

  public BigDecimal getClientLedgerTotal() {
    return clientLedgerTotal;
  }

  public void setClientLedgerTotal(BigDecimal clientLedgerTotal) {
    this.clientLedgerTotal = clientLedgerTotal;
  }

  public BigDecimal getOutstandingDeposits() {
    return outstandingDeposits;
  }

  public void setOutstandingDeposits(BigDecimal outstandingDeposits) {
    this.outstandingDeposits = outstandingDeposits;
  }

  public BigDecimal getOutstandingPayments() {
    return outstandingPayments;
  }

  public void setOutstandingPayments(BigDecimal outstandingPayments) {
    this.outstandingPayments = outstandingPayments;
  }

  public BigDecimal getAdjustedBankBalance() {
    return adjustedBankBalance;
  }

  public void setAdjustedBankBalance(BigDecimal adjustedBankBalance) {
    this.adjustedBankBalance = adjustedBankBalance;
  }

  public boolean isBalanced() {
    return isBalanced;
  }

  public void setBalanced(boolean balanced) {
    this.isBalanced = balanced;
  }

  public List<IntegrityFlag> getIntegrityFlags() {
    return integrityFlags;
  }

  public void setIntegrityFlags(List<IntegrityFlag> integrityFlags) {
    this.integrityFlags = new ArrayList<>(integrityFlags);
  }

  public ReconciliationStatus getStatus() {
    return status;
  }

  public void setStatus(ReconciliationStatus status) {
    this.status = status;
  }

  public UUID getCompletedBy() {
    return completedBy;
  }

  public void setCompletedBy(UUID completedBy) {
    this.completedBy = completedBy;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public BigDecimal getInvestmentCreditorTotal() {
    return investmentCreditorTotal;
  }

  public void setInvestmentCreditorTotal(BigDecimal investmentCreditorTotal) {
    this.investmentCreditorTotal = investmentCreditorTotal;
  }

  public BigDecimal getFundLedgerBalance() {
    return fundLedgerBalance;
  }

  public void setFundLedgerBalance(BigDecimal fundLedgerBalance) {
    this.fundLedgerBalance = fundLedgerBalance;
  }

  public BigDecimal getUnidentifiedDepositTotal() {
    return unidentifiedDepositTotal;
  }

  public void setUnidentifiedDepositTotal(BigDecimal unidentifiedDepositTotal) {
    this.unidentifiedDepositTotal = unidentifiedDepositTotal;
  }

  public BigDecimal getInterAccountBalance() {
    return interAccountBalance;
  }

  public void setInterAccountBalance(BigDecimal interAccountBalance) {
    this.interAccountBalance = interAccountBalance;
  }

  public BigDecimal getCreditorTotal() {
    return creditorTotal;
  }

  public void setCreditorTotal(BigDecimal creditorTotal) {
    this.creditorTotal = creditorTotal;
  }

  public BigDecimal getDifference() {
    return difference;
  }

  public void setDifference(BigDecimal difference) {
    this.difference = difference;
  }
}
