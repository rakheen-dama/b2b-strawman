package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunEvents.BillingRunCompletedEvent;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunEvents.BillingRunFailuresEvent;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunEvents.BillingRunSentEvent;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BatchFailure;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BatchOperationResult;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BatchSendRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.SendInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles billing run invoice generation, batch approve, and batch send operations. Extracted from
 * BillingRunService as a focused collaborator.
 */
@Service
public class BillingRunGenerationService {

  private static final Logger log = LoggerFactory.getLogger(BillingRunGenerationService.class);

  private final BillingRunRepository billingRunRepository;
  private final BillingRunItemRepository billingRunItemRepository;
  private final AuditService auditService;
  private final InvoiceService invoiceService;
  private final InvoiceRepository invoiceRepository;
  private final TransactionTemplate transactionTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final OrgSettingsRepository orgSettingsRepository;
  private final BillingRunSelectionService selectionService;

  public BillingRunGenerationService(
      BillingRunRepository billingRunRepository,
      BillingRunItemRepository billingRunItemRepository,
      AuditService auditService,
      InvoiceService invoiceService,
      InvoiceRepository invoiceRepository,
      TransactionTemplate transactionTemplate,
      ApplicationEventPublisher eventPublisher,
      OrgSettingsRepository orgSettingsRepository,
      BillingRunSelectionService selectionService) {
    this.billingRunRepository = billingRunRepository;
    this.billingRunItemRepository = billingRunItemRepository;
    this.auditService = auditService;
    this.invoiceService = invoiceService;
    this.invoiceRepository = invoiceRepository;
    this.transactionTemplate = transactionTemplate;
    this.eventPublisher = eventPublisher;
    this.orgSettingsRepository = orgSettingsRepository;
    this.selectionService = selectionService;
  }

  /**
   * Generates invoices for all PENDING items in the billing run. NOT @Transactional — each
   * customer's invoice creation runs in its own transaction via TransactionTemplate for failure
   * isolation.
   */
  public BillingRunResponse generate(UUID billingRunId, UUID actorMemberId) {
    var run =
        transactionTemplate.execute(
            status -> {
              var r =
                  billingRunRepository
                      .findByIdForUpdate(billingRunId)
                      .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

              if (r.getStatus() != BillingRunStatus.PREVIEW) {
                throw new InvalidStateException(
                    "Cannot generate billing run",
                    "Only billing runs in PREVIEW status can be generated. Current status: "
                        + r.getStatus());
              }

              if (billingRunRepository.existsByStatusIn(List.of(BillingRunStatus.IN_PROGRESS))) {
                throw new ResourceConflictException(
                    "Billing run conflict", "Another billing run is already in progress");
              }

              r.startGeneration();
              return billingRunRepository.save(r);
            });

    var pendingItems =
        billingRunItemRepository.findByBillingRunIdAndStatus(
            billingRunId, BillingRunItemStatus.PENDING);

    int successCount = 0;
    int failureCount = 0;
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (var item : pendingItems) {
      try {
        var invoiceTotal =
            transactionTemplate.execute(
                status -> {
                  var freshItem = billingRunItemRepository.findById(item.getId()).orElseThrow();
                  freshItem.markGenerating();
                  billingRunItemRepository.save(freshItem);

                  List<UUID> timeEntryIds = selectionService.resolveSelectedTimeEntryIds(freshItem);
                  List<UUID> expenseIds = selectionService.resolveSelectedExpenseIds(freshItem);

                  var invoiceRequest =
                      new CreateInvoiceRequest(
                          freshItem.getCustomerId(),
                          run.getCurrency(),
                          timeEntryIds.isEmpty() ? null : timeEntryIds,
                          expenseIds.isEmpty() ? null : expenseIds,
                          null,
                          null,
                          null);

                  var invoiceResponse = invoiceService.createDraft(invoiceRequest, actorMemberId);

                  var invoice = invoiceRepository.findById(invoiceResponse.id()).orElseThrow();
                  invoice.setBillingRunId(billingRunId);
                  invoiceRepository.save(invoice);

                  freshItem.markGenerated(invoiceResponse.id());
                  billingRunItemRepository.save(freshItem);

                  return invoiceResponse.total();
                });

        successCount++;
        if (invoiceTotal != null) {
          totalAmount = totalAmount.add(invoiceTotal);
        }
      } catch (Exception e) {
        failureCount++;
        log.warn(
            "Failed to generate invoice for item {} (customer {}): {}",
            item.getId(),
            item.getCustomerId(),
            e.getMessage());

        try {
          String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
          final String errorMessage = truncate(reason, 1000);
          transactionTemplate.executeWithoutResult(
              status -> {
                var failedItem = billingRunItemRepository.findById(item.getId()).orElseThrow();
                if (failedItem.getStatus() == BillingRunItemStatus.PENDING) {
                  failedItem.markGenerating();
                }
                failedItem.markFailed(errorMessage);
                billingRunItemRepository.save(failedItem);
              });
        } catch (Exception innerEx) {
          log.error("Failed to mark item {} as FAILED: {}", item.getId(), innerEx.getMessage());
        }
      }
    }

    final int finalSuccessCount = successCount;
    final int finalFailureCount = failureCount;
    final BigDecimal finalTotalAmount = totalAmount;

    var completedRun =
        transactionTemplate.execute(
            status -> {
              var r = billingRunRepository.findById(billingRunId).orElseThrow();
              r.complete(finalSuccessCount, finalFailureCount, finalTotalAmount);
              r = billingRunRepository.save(r);

              auditService.log(
                  AuditEventBuilder.billingRunGenerated(r, finalSuccessCount, finalFailureCount));

              return r;
            });

    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(
        new BillingRunCompletedEvent(
            completedRun.getId(), completedRun.getName(), finalSuccessCount, tenantId, orgId));
    if (finalFailureCount > 0) {
      eventPublisher.publishEvent(
          new BillingRunFailuresEvent(
              completedRun.getId(), completedRun.getName(), finalFailureCount, tenantId, orgId));
    }

    log.info(
        "Completed billing run {} — {} invoices generated, {} failed, total: {}",
        billingRunId,
        finalSuccessCount,
        finalFailureCount,
        finalTotalAmount);

    return BillingRunResponse.from(completedRun);
  }

  /**
   * Approves all DRAFT invoices linked to GENERATED billing run items. Each invoice is approved
   * individually; failures are captured without aborting the batch.
   */
  public BatchOperationResult batchApprove(UUID billingRunId, UUID actorMemberId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.COMPLETED) {
      throw new InvalidStateException(
          "Cannot approve billing run",
          "Only COMPLETED billing runs can be approved. Current status: " + run.getStatus());
    }

    var generatedItems =
        billingRunItemRepository.findByBillingRunIdAndStatus(
            billingRunId, BillingRunItemStatus.GENERATED);

    int successCount = 0;
    List<BatchFailure> failures = new ArrayList<>();

    for (var item : generatedItems) {
      if (item.getInvoiceId() == null) {
        continue;
      }
      try {
        transactionTemplate.executeWithoutResult(
            status -> invoiceService.approve(item.getInvoiceId(), actorMemberId));
        successCount++;
      } catch (Exception e) {
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        failures.add(new BatchFailure(item.getInvoiceId(), reason));
        log.warn(
            "Failed to approve invoice {} for billing run {}: {}",
            item.getInvoiceId(),
            billingRunId,
            reason);
      }
    }

    log.info(
        "Batch approve for billing run {} — {} succeeded, {} failed",
        billingRunId,
        successCount,
        failures.size());

    final int finalSuccessCount = successCount;
    auditService.log(AuditEventBuilder.billingRunApproved(run, finalSuccessCount));

    return new BatchOperationResult(successCount, failures.size(), failures);
  }

  /**
   * Sends all APPROVED invoices linked to a COMPLETED billing run in rate-limited bursts. Applies
   * default due date and payment terms to invoices missing them before sending.
   */
  public BatchOperationResult batchSend(
      UUID billingRunId, BatchSendRequest request, UUID actorMemberId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.COMPLETED) {
      throw new InvalidStateException(
          "Cannot send billing run",
          "Only COMPLETED billing runs can be sent. Current status: " + run.getStatus());
    }

    int rateLimit =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(s -> s.getBillingEmailRateLimit())
            .orElse(5);

    var approvedInvoiceIds =
        invoiceRepository.findByBillingRunIdAndStatus(billingRunId, InvoiceStatus.APPROVED).stream()
            .map(Invoice::getId)
            .toList();

    if (approvedInvoiceIds.isEmpty()) {
      return new BatchOperationResult(0, 0, List.of());
    }

    if (request != null) {
      transactionTemplate.executeWithoutResult(
          status -> {
            var invoices = invoiceRepository.findAllById(approvedInvoiceIds);
            for (Invoice invoice : invoices) {
              invoice.applyDefaults(request.defaultDueDate(), request.defaultPaymentTerms());
              invoiceRepository.save(invoice);
            }
          });
    }

    List<List<UUID>> bursts = partition(approvedInvoiceIds, rateLimit);

    int successCount = 0;
    List<BatchFailure> failures = new ArrayList<>();

    for (int i = 0; i < bursts.size(); i++) {
      if (i > 0) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

      for (UUID invoiceId : bursts.get(i)) {
        try {
          invoiceService.send(invoiceId, new SendInvoiceRequest(true));
          successCount++;
        } catch (Exception e) {
          String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
          failures.add(new BatchFailure(invoiceId, reason));
          log.warn(
              "Failed to send invoice {} for billing run {}: {}", invoiceId, billingRunId, reason);
        }
      }

      final int currentSuccessCount = successCount;
      transactionTemplate.executeWithoutResult(
          status -> {
            var freshRun = billingRunRepository.findById(billingRunId).orElseThrow();
            freshRun.setTotalSent(currentSuccessCount);
            billingRunRepository.save(freshRun);
          });
    }

    final int finalSuccessCount = successCount;
    final String sendTenantId =
        RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    final String sendOrgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    transactionTemplate.executeWithoutResult(
        status -> {
          var freshRun = billingRunRepository.findById(billingRunId).orElseThrow();
          auditService.log(AuditEventBuilder.billingRunSent(freshRun, finalSuccessCount));
          eventPublisher.publishEvent(
              new BillingRunSentEvent(
                  billingRunId, freshRun.getName(), finalSuccessCount, sendTenantId, sendOrgId));
        });

    log.info(
        "Batch send for billing run {} — {} sent, {} failed",
        billingRunId,
        successCount,
        failures.size());

    return new BatchOperationResult(successCount, failures.size(), failures);
  }

  // --- Private helpers ---

  private String truncate(String text, int maxLength) {
    if (text == null) return null;
    return text.length() <= maxLength ? text : text.substring(0, maxLength);
  }

  static <T> List<List<T>> partition(List<T> list, int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("Partition size must be positive");
    }
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }
}
