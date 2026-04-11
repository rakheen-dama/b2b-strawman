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
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
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

  private static final String MODULE_ID = "bulk_billing";

  private final BillingRunLifecycleService lifecycleService;
  private final BillingRunSelectionService selectionService;
  private final BillingRunGenerationService generationService;
  private final RetainerBillingService retainerBillingService;
  private final VerticalModuleGuard moduleGuard;

  public BillingRunService(
      BillingRunLifecycleService lifecycleService,
      BillingRunSelectionService selectionService,
      BillingRunGenerationService generationService,
      RetainerBillingService retainerBillingService,
      VerticalModuleGuard moduleGuard) {
    this.lifecycleService = lifecycleService;
    this.selectionService = selectionService;
    this.generationService = generationService;
    this.retainerBillingService = retainerBillingService;
    this.moduleGuard = moduleGuard;
  }

  // --- Lifecycle ---

  @Transactional
  public BillingRunResponse createRun(CreateBillingRunRequest request, UUID actorMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    return lifecycleService.createRun(request, actorMemberId);
  }

  @Transactional
  public void cancelRun(UUID billingRunId, UUID actorMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    lifecycleService.cancelRun(billingRunId, actorMemberId);
  }

  @Transactional(readOnly = true)
  public BillingRunResponse getRun(UUID billingRunId) {
    moduleGuard.requireModule(MODULE_ID);

    return lifecycleService.getRun(billingRunId);
  }

  @Transactional(readOnly = true)
  public Page<BillingRunResponse> listRuns(Pageable pageable, List<BillingRunStatus> statuses) {
    moduleGuard.requireModule(MODULE_ID);

    return lifecycleService.listRuns(pageable, statuses);
  }

  // --- Selection & Preview ---

  @Transactional
  public BillingRunPreviewResponse loadPreview(UUID billingRunId, LoadPreviewRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.loadPreview(billingRunId, request);
  }

  @Transactional(readOnly = true)
  public List<BillingRunItemResponse> getItems(UUID billingRunId) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.getItems(billingRunId);
  }

  @Transactional(readOnly = true)
  public BillingRunItemResponse getItem(UUID billingRunId, UUID itemId) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.getItem(billingRunId, itemId);
  }

  @Transactional(readOnly = true)
  public List<TimeEntryResponse> getUnbilledTimeEntries(UUID billingRunId, UUID billingRunItemId) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.getUnbilledTimeEntries(billingRunId, billingRunItemId);
  }

  @Transactional(readOnly = true)
  public List<ExpenseResponse> getUnbilledExpenses(UUID billingRunId, UUID billingRunItemId) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.getUnbilledExpenses(billingRunId, billingRunItemId);
  }

  @Transactional
  public BillingRunItemResponse updateEntrySelection(
      UUID billingRunId, UUID billingRunItemId, UpdateEntrySelectionsRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.updateEntrySelection(billingRunId, billingRunItemId, request);
  }

  @Transactional
  public BillingRunItemResponse excludeCustomer(UUID billingRunId, UUID billingRunItemId) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.excludeCustomer(billingRunId, billingRunItemId);
  }

  @Transactional
  public BillingRunItemResponse includeCustomer(UUID billingRunId, UUID billingRunItemId) {
    moduleGuard.requireModule(MODULE_ID);

    return selectionService.includeCustomer(billingRunId, billingRunItemId);
  }

  // --- Generation ---

  public BillingRunResponse generate(UUID billingRunId, UUID actorMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    return generationService.generate(billingRunId, actorMemberId);
  }

  public BatchOperationResult batchApprove(UUID billingRunId, UUID actorMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    return generationService.batchApprove(billingRunId, actorMemberId);
  }

  public BatchOperationResult batchSend(
      UUID billingRunId, BatchSendRequest request, UUID actorMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    return generationService.batchSend(billingRunId, request, actorMemberId);
  }

  // --- Retainers ---

  @Transactional(readOnly = true)
  public List<RetainerPeriodPreview> loadRetainerPreview(UUID billingRunId) {
    moduleGuard.requireModule(MODULE_ID);

    return retainerBillingService.loadRetainerPreview(billingRunId);
  }

  public List<BillingRunItemResponse> generateRetainerInvoices(
      UUID billingRunId, RetainerGenerateRequest request, UUID actorMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    return retainerBillingService.generateRetainerInvoices(billingRunId, request, actorMemberId);
  }
}
