package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunItemResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.RetainerGenerateRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.RetainerPeriodPreview;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.retainer.PeriodStatus;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriodRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriodService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles retainer billing preview and invoice generation within billing runs. Extracted from
 * BillingRunService as a focused collaborator.
 */
@Service
public class RetainerBillingService {

  private static final Logger log = LoggerFactory.getLogger(RetainerBillingService.class);

  private final BillingRunRepository billingRunRepository;
  private final BillingRunItemRepository billingRunItemRepository;
  private final CustomerRepository customerRepository;
  private final InvoiceRepository invoiceRepository;
  private final RetainerAgreementRepository retainerAgreementRepository;
  private final RetainerPeriodRepository retainerPeriodRepository;
  private final RetainerPeriodService retainerPeriodService;
  private final TransactionTemplate transactionTemplate;

  public RetainerBillingService(
      BillingRunRepository billingRunRepository,
      BillingRunItemRepository billingRunItemRepository,
      CustomerRepository customerRepository,
      InvoiceRepository invoiceRepository,
      RetainerAgreementRepository retainerAgreementRepository,
      RetainerPeriodRepository retainerPeriodRepository,
      RetainerPeriodService retainerPeriodService,
      TransactionTemplate transactionTemplate) {
    this.billingRunRepository = billingRunRepository;
    this.billingRunItemRepository = billingRunItemRepository;
    this.customerRepository = customerRepository;
    this.invoiceRepository = invoiceRepository;
    this.retainerAgreementRepository = retainerAgreementRepository;
    this.retainerPeriodRepository = retainerPeriodRepository;
    this.retainerPeriodService = retainerPeriodService;
    this.transactionTemplate = transactionTemplate;
  }

  @Transactional(readOnly = true)
  public List<RetainerPeriodPreview> loadRetainerPreview(UUID billingRunId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (!run.isIncludeRetainers()) {
      return List.of();
    }

    var agreements =
        retainerAgreementRepository.findActiveWithDuePeriodsInRange(
            run.getPeriodFrom(), run.getPeriodTo());

    return agreements.stream()
        .map(
            agreement -> {
              var openPeriod =
                  retainerPeriodRepository
                      .findByAgreementIdAndStatus(agreement.getId(), PeriodStatus.OPEN)
                      .orElse(null);
              if (openPeriod == null) {
                return null;
              }
              String customerName =
                  customerRepository
                      .findById(agreement.getCustomerId())
                      .map(Customer::getName)
                      .orElse("Unknown");
              return new RetainerPeriodPreview(
                  agreement.getId(),
                  agreement.getCustomerId(),
                  customerName,
                  openPeriod.getPeriodStart(),
                  openPeriod.getPeriodEnd(),
                  openPeriod.getConsumedHours(),
                  agreement.getPeriodFee());
            })
        .filter(p -> p != null)
        .toList();
  }

  /**
   * Generates retainer invoices by delegating to RetainerPeriodService.closePeriod(). Each
   * agreement is processed in its own transaction via transactionTemplate for failure isolation.
   */
  public List<BillingRunItemResponse> generateRetainerInvoices(
      UUID billingRunId, RetainerGenerateRequest request, UUID actorMemberId) {
    transactionTemplate.execute(
        status -> {
          var run =
              billingRunRepository
                  .findById(billingRunId)
                  .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

          if (run.getStatus() != BillingRunStatus.PREVIEW
              && run.getStatus() != BillingRunStatus.COMPLETED) {
            throw new InvalidStateException(
                "Cannot generate retainer invoices",
                "Only billing runs in PREVIEW or COMPLETED status can generate retainer invoices. Current status: "
                    + run.getStatus());
          }
          return null;
        });

    List<BillingRunItemResponse> results = new ArrayList<>();

    for (UUID agreementId : request.retainerAgreementIds()) {
      try {
        var result =
            transactionTemplate.execute(
                status -> {
                  var closeResult = retainerPeriodService.closePeriod(agreementId, actorMemberId);

                  var invoice = closeResult.generatedInvoice();
                  invoice.setBillingRunId(billingRunId);
                  invoiceRepository.save(invoice);

                  UUID customerId = invoice.getCustomerId();
                  var item = new BillingRunItem(billingRunId, customerId);
                  item.markGenerating();
                  item.markGenerated(invoice.getId());
                  item = billingRunItemRepository.save(item);

                  String customerName =
                      customerRepository
                          .findById(customerId)
                          .map(Customer::getName)
                          .orElse("Unknown");

                  return new BillingRunItemResponse(
                      item.getId(),
                      customerId,
                      customerName,
                      item.getStatus(),
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      0,
                      0,
                      invoice.getTotal() != null ? invoice.getTotal() : BigDecimal.ZERO,
                      false,
                      null,
                      invoice.getId(),
                      null);
                });
        if (result != null) {
          results.add(result);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to generate retainer invoice for agreement {}: {}",
            agreementId,
            e.getMessage());

        try {
          String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
          final String errorMessage = truncate(reason, 1000);

          var agreementOpt = retainerAgreementRepository.findById(agreementId);
          if (agreementOpt.isPresent()) {
            var failedResult =
                transactionTemplate.execute(
                    status -> {
                      var agreement = agreementOpt.get();
                      UUID customerId = agreement.getCustomerId();
                      String customerName =
                          customerRepository
                              .findById(customerId)
                              .map(Customer::getName)
                              .orElse("Unknown");
                      var item = new BillingRunItem(billingRunId, customerId);
                      item.markGenerating();
                      item.markFailed(errorMessage);
                      item = billingRunItemRepository.save(item);
                      return new BillingRunItemResponse(
                          item.getId(),
                          customerId,
                          customerName,
                          item.getStatus(),
                          BigDecimal.ZERO,
                          BigDecimal.ZERO,
                          0,
                          0,
                          BigDecimal.ZERO,
                          false,
                          null,
                          null,
                          errorMessage);
                    });
            if (failedResult != null) {
              results.add(failedResult);
            }
          } else {
            results.add(
                new BillingRunItemResponse(
                    null,
                    null,
                    "Unknown",
                    BillingRunItemStatus.FAILED,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    0,
                    BigDecimal.ZERO,
                    false,
                    null,
                    null,
                    errorMessage));
          }
        } catch (Exception innerEx) {
          log.error(
              "Failed to record failure for agreement {}: {}", agreementId, innerEx.getMessage());
        }
      }
    }

    return results;
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return null;
    return text.length() <= maxLength ? text : text.substring(0, maxLength);
  }
}
