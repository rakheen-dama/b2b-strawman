package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BatchOperationResult;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BatchSendRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunItemResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunPreviewResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CreateBillingRunRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.ExpenseResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.LoadPreviewRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.RetainerGenerateRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.RetainerPeriodPreview;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.TimeEntryResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.UpdateEntrySelectionsRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade that preserves the original BillingRunService public API while delegating to focused
 * collaborator services: BillingRunLifecycleService, BillingRunSelectionService,
 * BillingRunGenerationService, and RetainerBillingService.
 */
@Service
public class BillingRunService {

  private final BillingRunLifecycleService lifecycleService;
  private final BillingRunSelectionService selectionService;
  private final BillingRunGenerationService generationService;
  private final RetainerBillingService retainerBillingService;

  public BillingRunService(
      BillingRunLifecycleService lifecycleService,
      BillingRunSelectionService selectionService,
      BillingRunGenerationService generationService,
      RetainerBillingService retainerBillingService) {
    this.lifecycleService = lifecycleService;
    this.selectionService = selectionService;
    this.generationService = generationService;
    this.retainerBillingService = retainerBillingService;
  }

  // --- Lifecycle ---

  @Transactional
  public BillingRunResponse createRun(CreateBillingRunRequest request, UUID actorMemberId) {
    return lifecycleService.createRun(request, actorMemberId);
  }

  @Transactional
  public void cancelRun(UUID billingRunId, UUID actorMemberId) {
    lifecycleService.cancelRun(billingRunId, actorMemberId);
  }

  @Transactional(readOnly = true)
  public BillingRunResponse getRun(UUID billingRunId) {
    return lifecycleService.getRun(billingRunId);
  }

  @Transactional(readOnly = true)
  public Page<BillingRunResponse> listRuns(Pageable pageable, List<BillingRunStatus> statuses) {
    return lifecycleService.listRuns(pageable, statuses);
  }

  // --- Selection & Preview ---

  @Transactional
  public BillingRunPreviewResponse loadPreview(UUID billingRunId, LoadPreviewRequest request) {
    return selectionService.loadPreview(billingRunId, request);
  }

  @Transactional(readOnly = true)
  public List<BillingRunItemResponse> getItems(UUID billingRunId) {
    return selectionService.getItems(billingRunId);
  }

  @Transactional(readOnly = true)
  public BillingRunItemResponse getItem(UUID billingRunId, UUID itemId) {
    return selectionService.getItem(billingRunId, itemId);
  }

  @Transactional(readOnly = true)
  public List<TimeEntryResponse> getUnbilledTimeEntries(UUID billingRunId, UUID billingRunItemId) {
    return selectionService.getUnbilledTimeEntries(billingRunId, billingRunItemId);
  }

  @Transactional(readOnly = true)
  public List<ExpenseResponse> getUnbilledExpenses(UUID billingRunId, UUID billingRunItemId) {
    return selectionService.getUnbilledExpenses(billingRunId, billingRunItemId);
  }

  @Transactional
  public BillingRunItemResponse updateEntrySelection(
      UUID billingRunId, UUID billingRunItemId, UpdateEntrySelectionsRequest request) {
    return selectionService.updateEntrySelection(billingRunId, billingRunItemId, request);
  }

  @Transactional
  public BillingRunItemResponse excludeCustomer(UUID billingRunId, UUID billingRunItemId) {
    return selectionService.excludeCustomer(billingRunId, billingRunItemId);
  }

  @Transactional
  public BillingRunItemResponse includeCustomer(UUID billingRunId, UUID billingRunItemId) {
    return selectionService.includeCustomer(billingRunId, billingRunItemId);
  }

  // --- Generation ---

  public BillingRunResponse generate(UUID billingRunId, UUID actorMemberId) {
    return generationService.generate(billingRunId, actorMemberId);
  }

  public BatchOperationResult batchApprove(UUID billingRunId, UUID actorMemberId) {
    return generationService.batchApprove(billingRunId, actorMemberId);
  }

  public BatchOperationResult batchSend(
      UUID billingRunId, BatchSendRequest request, UUID actorMemberId) {
    return generationService.batchSend(billingRunId, request, actorMemberId);
  }

  // --- Retainers ---

  @Transactional(readOnly = true)
  public List<RetainerPeriodPreview> loadRetainerPreview(UUID billingRunId) {
    return retainerBillingService.loadRetainerPreview(billingRunId);
  }

  public List<BillingRunItemResponse> generateRetainerInvoices(
      UUID billingRunId, RetainerGenerateRequest request, UUID actorMemberId) {
    return retainerBillingService.generateRetainerInvoices(billingRunId, request, actorMemberId);
  }
}
