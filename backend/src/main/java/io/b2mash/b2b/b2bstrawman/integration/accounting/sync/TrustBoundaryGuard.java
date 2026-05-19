package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Trust boundary guard that prevents trust-related invoices from being synced to external
 * accounting systems. Evaluates three conditions in order:
 *
 * <ol>
 *   <li>Invoice-level trust flag ({@code is_trust_invoice} custom field)
 *   <li>Line items linked to trust-account disbursements
 *   <li>Customer has active trust balances (via client ledger cards)
 * </ol>
 *
 * <p>Fail-closed: any unexpected exception during evaluation returns refused. Non-legal tenants
 * (where trust tables do not exist) return permit, since trust accounting is not relevant.
 */
@Service
public class TrustBoundaryGuard {

  private static final Logger log = LoggerFactory.getLogger(TrustBoundaryGuard.class);

  private final DisbursementRepository disbursementRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;

  public TrustBoundaryGuard(
      DisbursementRepository disbursementRepository,
      ClientLedgerCardRepository clientLedgerCardRepository) {
    this.disbursementRepository = disbursementRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
  }

  /**
   * Evaluates whether an invoice may be synced to an external accounting provider or must be
   * blocked due to trust accounting concerns.
   *
   * @param invoice the invoice to evaluate
   * @param lines the invoice's line items
   * @param customer the customer associated with the invoice
   * @return a decision: {@link TrustBoundaryDecision#permit()} if sync is allowed, or {@link
   *     TrustBoundaryDecision#refused(String)} with a reason if blocked
   */
  public TrustBoundaryDecision evaluate(
      Invoice invoice, List<InvoiceLine> lines, Customer customer) {
    try {
      // Condition 1: Invoice-level trust flag
      Object trustFlag = invoice.getCustomFields().get("is_trust_invoice");
      if (Boolean.TRUE.equals(trustFlag)) {
        return TrustBoundaryDecision.refused(
            "Invoice is flagged as trust-related (is_trust_invoice=true)");
      }

      // Condition 2: Line items linked to trust-account disbursements
      TrustBoundaryDecision lineDecision = checkDisbursementLines(lines);
      if (!lineDecision.allowed()) {
        return lineDecision;
      }

      // Condition 3: Customer has active trust balances
      TrustBoundaryDecision balanceDecision = checkCustomerTrustBalance(customer);
      if (!balanceDecision.allowed()) {
        return balanceDecision;
      }

      return TrustBoundaryDecision.permit();
    } catch (Exception e) {
      log.error(
          "Trust boundary guard evaluation failed for invoice {}: {}",
          invoice.getId(),
          e.getMessage(),
          e);
      return TrustBoundaryDecision.refused("Guard evaluation failed: " + e.getMessage());
    }
  }

  /**
   * Checks whether any invoice line is linked to a disbursement paid from a trust account. If the
   * trust tables do not exist (non-legal tenant), the query will throw a {@link
   * DataAccessException} which is caught and treated as "no trust data = not a trust concern".
   */
  private TrustBoundaryDecision checkDisbursementLines(List<InvoiceLine> lines) {
    for (InvoiceLine line : lines) {
      if (line.getDisbursementId() != null) {
        try {
          var disbursement = disbursementRepository.findById(line.getDisbursementId());
          if (disbursement.isPresent()
              && DisbursementPaymentSource.TRUST_ACCOUNT
                  .name()
                  .equals(disbursement.get().getPaymentSource())) {
            return TrustBoundaryDecision.refused(
                "Line item '%s' is sourced from trust account (trustTransactionId=%s)"
                    .formatted(line.getDescription(), disbursement.get().getTrustTransactionId()));
          }
        } catch (DataAccessException e) {
          // Trust tables do not exist for this tenant — skip this condition
          log.debug("Disbursement lookup failed (non-legal tenant?): {}", e.getMessage());
          return TrustBoundaryDecision.permit();
        }
      }
    }
    return TrustBoundaryDecision.permit();
  }

  /**
   * Checks whether the customer has any non-zero trust balances via client ledger cards. If the
   * trust tables do not exist (non-legal tenant), the query will throw a {@link
   * DataAccessException} which is caught and treated as "no trust data = not a trust concern".
   */
  private TrustBoundaryDecision checkCustomerTrustBalance(Customer customer) {
    try {
      BigDecimal trustBalance = clientLedgerCardRepository.sumBalancesForCustomer(customer.getId());
      if (trustBalance != null && trustBalance.compareTo(BigDecimal.ZERO) != 0) {
        return TrustBoundaryDecision.refused(
            "Customer '%s' has active trust balance of %s"
                .formatted(customer.getName(), trustBalance));
      }
    } catch (DataAccessException e) {
      // Trust tables do not exist for this tenant — skip this condition
      log.debug("Client ledger card lookup failed (non-legal tenant?): {}", e.getMessage());
    }
    return TrustBoundaryDecision.permit();
  }
}
