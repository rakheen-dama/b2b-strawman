package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The Rule 54.15 list of one trust account at one month end, frozen when that month's
 * reconciliation completed (Phase 92 650A, architecture §16.2.10, ADR-382): the money side as
 * reconciled, the cashbook, each creditor-side total, every creditor by name ({@link #getLines()}),
 * the integrity flags raised at completion, who locked it and when. Rows are written once and never
 * updated or deleted (A5) — a posting made later and dated on or before {@code periodEnd} is shown
 * against the list, never folded into it.
 */
@Entity
@Immutable
@Table(name = "trust_monthly_lists")
public class TrustMonthlyList {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false, updatable = false)
  private UUID trustAccountId;

  /** The reconciliation's period end — the last day of a calendar month. */
  @Column(name = "period_end", nullable = false, updatable = false)
  private LocalDate periodEnd;

  @Column(name = "reconciliation_id", nullable = false, updatable = false)
  private UUID reconciliationId;

  @Column(name = "bank_balance", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal bankBalance;

  @Column(
      name = "outstanding_deposits",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal outstandingDeposits;

  @Column(
      name = "outstanding_payments",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal outstandingPayments;

  @Column(
      name = "adjusted_bank_balance",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal adjustedBankBalance;

  @Column(name = "cashbook_balance", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal cashbookBalance;

  @Column(
      name = "client_ledger_total",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal clientLedgerTotal;

  @Column(
      name = "investment_creditor_total",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal investmentCreditorTotal;

  @Column(
      name = "fund_ledger_balance",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal fundLedgerBalance;

  @Column(
      name = "unidentified_deposit_total",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal unidentifiedDepositTotal;

  @Column(
      name = "inter_account_balance",
      nullable = false,
      precision = 15,
      scale = 2,
      updatable = false)
  private BigDecimal interAccountBalance;

  @Column(name = "creditor_total", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal creditorTotal;

  /** Always zero: only a balanced reconciliation locks a list (CHECK in V25). */
  @Column(name = "difference", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal difference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "lines", nullable = false, columnDefinition = "jsonb", updatable = false)
  private List<CreditorLine> lines = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "integrity_flags", nullable = false, columnDefinition = "jsonb", updatable = false)
  private List<IntegrityFlag> integrityFlags = new ArrayList<>();

  @Column(name = "locked_by", nullable = false, updatable = false)
  private UUID lockedBy;

  /** After this instant a posting dated on or before {@code periodEnd} counts as back-dated. */
  @Column(name = "locked_at", nullable = false, updatable = false)
  private Instant lockedAt;

  protected TrustMonthlyList() {}

  /**
   * Freezes a completed reconciliation's figures (all as at its period end) with the creditor lines
   * and the flags stored on the reconciliation at completion.
   */
  public TrustMonthlyList(
      TrustReconciliation completed, List<CreditorLine> lines, UUID lockedBy, Instant lockedAt) {
    this.trustAccountId = completed.getTrustAccountId();
    this.periodEnd = completed.getPeriodEnd();
    this.reconciliationId = completed.getId();
    this.bankBalance = completed.getBankBalance();
    this.outstandingDeposits = completed.getOutstandingDeposits();
    this.outstandingPayments = completed.getOutstandingPayments();
    this.adjustedBankBalance = completed.getAdjustedBankBalance();
    this.cashbookBalance = completed.getCashbookBalance();
    this.clientLedgerTotal = completed.getClientLedgerTotal();
    this.investmentCreditorTotal = completed.getInvestmentCreditorTotal();
    this.fundLedgerBalance = completed.getFundLedgerBalance();
    this.unidentifiedDepositTotal = completed.getUnidentifiedDepositTotal();
    this.interAccountBalance = completed.getInterAccountBalance();
    this.creditorTotal = completed.getCreditorTotal();
    this.difference = completed.getDifference();
    this.lines = new ArrayList<>(lines);
    this.integrityFlags = new ArrayList<>(completed.getIntegrityFlags());
    this.lockedBy = lockedBy;
    this.lockedAt = lockedAt;
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

  public UUID getReconciliationId() {
    return reconciliationId;
  }

  public BigDecimal getBankBalance() {
    return bankBalance;
  }

  public BigDecimal getOutstandingDeposits() {
    return outstandingDeposits;
  }

  public BigDecimal getOutstandingPayments() {
    return outstandingPayments;
  }

  public BigDecimal getAdjustedBankBalance() {
    return adjustedBankBalance;
  }

  public BigDecimal getCashbookBalance() {
    return cashbookBalance;
  }

  public BigDecimal getClientLedgerTotal() {
    return clientLedgerTotal;
  }

  public BigDecimal getInvestmentCreditorTotal() {
    return investmentCreditorTotal;
  }

  public BigDecimal getFundLedgerBalance() {
    return fundLedgerBalance;
  }

  public BigDecimal getUnidentifiedDepositTotal() {
    return unidentifiedDepositTotal;
  }

  public BigDecimal getInterAccountBalance() {
    return interAccountBalance;
  }

  public BigDecimal getCreditorTotal() {
    return creditorTotal;
  }

  public BigDecimal getDifference() {
    return difference;
  }

  public List<CreditorLine> getLines() {
    return List.copyOf(lines);
  }

  public List<IntegrityFlag> getIntegrityFlags() {
    return List.copyOf(integrityFlags);
  }

  public UUID getLockedBy() {
    return lockedBy;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }
}
