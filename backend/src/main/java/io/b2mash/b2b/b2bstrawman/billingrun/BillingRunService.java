package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CreateBillingRunRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingRunService {

  private static final Logger log = LoggerFactory.getLogger(BillingRunService.class);

  private final BillingRunRepository billingRunRepository;
  private final BillingRunItemRepository billingRunItemRepository;
  private final BillingRunEntrySelectionRepository billingRunEntrySelectionRepository;
  private final AuditService auditService;

  public BillingRunService(
      BillingRunRepository billingRunRepository,
      BillingRunItemRepository billingRunItemRepository,
      BillingRunEntrySelectionRepository billingRunEntrySelectionRepository,
      AuditService auditService) {
    this.billingRunRepository = billingRunRepository;
    this.billingRunItemRepository = billingRunItemRepository;
    this.billingRunEntrySelectionRepository = billingRunEntrySelectionRepository;
    this.auditService = auditService;
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

    if (run.getStatus() != BillingRunStatus.PREVIEW) {
      throw new InvalidStateException(
          "Cannot cancel billing run",
          "Only billing runs in PREVIEW status can be cancelled. Current status: "
              + run.getStatus());
    }

    // Audit before delete so the event captures the run details
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("billing_run.cancelled")
            .entityType("billing_run")
            .entityId(billingRunId)
            .details(
                Map.of(
                    "name", run.getName() != null ? run.getName() : "",
                    "period_from", run.getPeriodFrom().toString(),
                    "period_to", run.getPeriodTo().toString(),
                    "status", run.getStatus().name()))
            .build());

    // Hard delete is appropriate here: PREVIEW-only billing runs have no generated invoices
    // or financial impact. Later slices (306B) will implement soft cancel for
    // IN_PROGRESS/COMPLETED runs that have associated invoices.
    billingRunEntrySelectionRepository.deleteByBillingRunId(billingRunId);
    billingRunItemRepository.deleteByBillingRunId(billingRunId);
    billingRunRepository.delete(run);

    log.info("Cancelled and deleted billing run {}", billingRunId);
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
