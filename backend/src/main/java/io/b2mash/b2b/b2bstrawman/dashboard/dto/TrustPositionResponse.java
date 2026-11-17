package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.CreditorBreakdown;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Trust position for one trust account — {@code GET /api/dashboard/trust-position} (Phase 87, Epic
 * 614A, arch §12.2.6). Composes the cashbook balance, the trust creditors, latest reconciliation
 * and pending-approval count already exposed by the trust-accounting reads, as at today. {@code
 * clientLedgerTotal} is the client ledgers (general); {@code creditorBreakdown} is every trust
 * creditor term and their {@code total}; {@code difference = cashbookBalance −
 * creditorBreakdown.total} (EQ-2, §16.3.2, 646D). Reconciliation fields are null when the account
 * has never been reconciled.
 */
public record TrustPositionResponse(
    UUID trustAccountId,
    String accountName,
    String bankName,
    BigDecimal cashbookBalance,
    BigDecimal clientLedgerTotal,
    CreditorBreakdown creditorBreakdown,
    BigDecimal difference,
    LocalDate lastReconciliationPeriodEnd,
    String lastReconciliationStatus,
    Boolean lastReconciliationBalanced,
    Integer daysSinceReconciliation,
    int pendingApprovalCount,
    List<TrustAccountRef> accounts) {

  /** An active trust account the widget can switch to. */
  public record TrustAccountRef(UUID id, String accountName, boolean isPrimary) {}
}
