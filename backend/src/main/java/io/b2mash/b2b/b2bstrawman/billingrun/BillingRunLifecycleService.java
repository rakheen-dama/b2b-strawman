package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CreateBillingRunRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles billing run creation, cancellation, and status management. Extracted from
 * BillingRunService as a focused collaborator.
 */
@Service
public class BillingRunLifecycleService {

  private static final Logger log = LoggerFactory.getLogger(BillingRunLifecycleService.class);

  private final BillingRunRepository billingRunRepository;
  private final BillingRunItemRepository billingRunItemRepository;
  private final BillingRunEntrySelectionRepository billingRunEntrySelectionRepository;
  private final AuditService auditService;
  private final TimeEntryRepository timeEntryRepository;
  private final ExpenseRepository expenseRepository;
  private final EntityManager entityManager;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;

  public BillingRunLifecycleService(
      BillingRunRepository billingRunRepository,
      BillingRunItemRepository billingRunItemRepository,
      BillingRunEntrySelectionRepository billingRunEntrySelectionRepository,
      AuditService auditService,
      TimeEntryRepository timeEntryRepository,
      ExpenseRepository expenseRepository,
      EntityManager entityManager,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository) {
    this.billingRunRepository = billingRunRepository;
    this.billingRunItemRepository = billingRunItemRepository;
    this.billingRunEntrySelectionRepository = billingRunEntrySelectionRepository;
    this.auditService = auditService;
    this.timeEntryRepository = timeEntryRepository;
    this.expenseRepository = expenseRepository;
    this.entityManager = entityManager;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
  }

  @Transactional
  public BillingRunResponse createRun(CreateBillingRunRequest request, UUID actorMemberId) {
    if (request.periodFrom().isAfter(request.periodTo())) {
      throw new InvalidStateException("Invalid period", "periodFrom must be on or before periodTo");
    }

    var run =
        new BillingRun(
            request.name(),
            request.periodFrom(),
            request.periodTo(),
            request.currency(),
            request.includeExpenses(),
            request.includeRetainers(),
            actorMemberId);

    run = billingRunRepository.save(run);

    log.info(
        "Created billing run {} for period {} to {}",
        run.getId(),
        run.getPeriodFrom(),
        run.getPeriodTo());

    auditService.log(AuditEventBuilder.billingRunCreated(run));

    return BillingRunResponse.from(run);
  }

  @Transactional
  public void cancelRun(UUID billingRunId, UUID actorMemberId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() == BillingRunStatus.CANCELLED) {
      throw new InvalidStateException(
          "Cannot cancel billing run", "Billing run is already cancelled");
    }

    if (run.getStatus() == BillingRunStatus.IN_PROGRESS) {
      throw new InvalidStateException(
          "Cannot cancel billing run",
          "Cannot cancel a billing run while generation is in progress");
    }

    if (run.getStatus() == BillingRunStatus.PREVIEW) {
      auditService.log(AuditEventBuilder.billingRunCancelled(run, 0));
      billingRunEntrySelectionRepository.deleteByBillingRunId(billingRunId);
      billingRunItemRepository.deleteByBillingRunId(billingRunId);
      billingRunRepository.delete(run);
      log.info("Cancelled and deleted billing run {} (PREVIEW)", billingRunId);
      return;
    }

    // COMPLETED: soft cancel
    var draftInvoices =
        invoiceRepository.findByBillingRunIdAndStatus(billingRunId, InvoiceStatus.DRAFT);
    List<UUID> draftInvoiceIds = draftInvoices.stream().map(Invoice::getId).toList();

    int voidedCount = 0;
    for (var invoiceId : draftInvoiceIds) {
      timeEntryRepository.unbillByInvoiceId(invoiceId);
      expenseRepository.unbillByInvoiceId(invoiceId);
      invoiceLineRepository.deleteByInvoiceId(invoiceId);

      var freshInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
      freshInvoice.voidDraft();
      invoiceRepository.save(freshInvoice);
      entityManager.flush();
      voidedCount++;
    }

    billingRunItemRepository.updateStatusByBillingRunIdAndStatus(
        billingRunId, BillingRunItemStatus.GENERATED, BillingRunItemStatus.CANCELLED);

    var freshRun = billingRunRepository.findById(billingRunId).orElseThrow();
    freshRun.cancel();
    billingRunRepository.save(freshRun);

    auditService.log(AuditEventBuilder.billingRunCancelled(freshRun, voidedCount));

    log.info("Cancelled billing run {} — {} invoices voided", billingRunId, voidedCount);
  }

  @Transactional(readOnly = true)
  public BillingRunResponse getRun(UUID billingRunId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));
    return BillingRunResponse.from(run);
  }

  @Transactional(readOnly = true)
  public Page<BillingRunResponse> listRuns(Pageable pageable, List<BillingRunStatus> statuses) {
    Pageable effectivePageable = applyDefaultSort(pageable);
    Page<BillingRun> page;
    if (statuses != null && !statuses.isEmpty()) {
      page = billingRunRepository.findByStatusIn(statuses, effectivePageable);
    } else {
      page = billingRunRepository.findAll(effectivePageable);
    }
    return page.map(BillingRunResponse::from);
  }

  private Pageable applyDefaultSort(Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      return pageable;
    }
    return PageRequest.of(
        pageable.getPageNumber(),
        pageable.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
