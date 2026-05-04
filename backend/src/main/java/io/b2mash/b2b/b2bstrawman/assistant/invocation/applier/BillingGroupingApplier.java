package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a {@link BillingGroupingPayload} on approval — replaces the invoice's existing line items
 * with the proposed grouping. Destructive (delete-then-add) within a single transaction.
 *
 * <p>Capability gate: re-checks {@code INVOICE_EDIT} as a belt-and-braces guard on top of the
 * upstream {@code AI_ASSISTANT_USE} check applied in {@code AiSpecialistInvocationService.approve}.
 */
@Component
public class BillingGroupingApplier implements OutputApplier<BillingGroupingPayload> {

  private final InvoiceService invoiceService;
  private final InvoiceLineRepository invoiceLineRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final TaskRepository taskRepository;
  private final CapabilityAuthorizationService capabilityAuthorizationService;

  public BillingGroupingApplier(
      InvoiceService invoiceService,
      InvoiceLineRepository invoiceLineRepository,
      TimeEntryRepository timeEntryRepository,
      TaskRepository taskRepository,
      CapabilityAuthorizationService capabilityAuthorizationService) {
    this.invoiceService = invoiceService;
    this.invoiceLineRepository = invoiceLineRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.taskRepository = taskRepository;
    this.capabilityAuthorizationService = capabilityAuthorizationService;
  }

  @Override
  public Class<BillingGroupingPayload> payloadType() {
    return BillingGroupingPayload.class;
  }

  @Override
  @Transactional
  public void apply(BillingGroupingPayload payload, UUID actorId) {
    capabilityAuthorizationService.requireCapability("INVOICE_EDIT");
    UUID invoiceId = payload.invoiceId();

    // Snapshot existing lines and delete them via the service so invoice totals are recomputed.
    var existing = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    for (var line : existing) {
      invoiceService.deleteLineItem(invoiceId, line.getId());
    }

    int sortOrder = 0;
    for (var group : payload.groups()) {
      UUID projectId = resolveProjectId(group.sourceTimeEntryIds());
      BigDecimal unitPrice = resolveUnitPrice(group.sourceTimeEntryIds(), group.hours());
      BigDecimal quantity = group.hours();
      if (quantity == null || quantity.signum() <= 0) {
        // Skip empty groups rather than fail — Phase 52 validation requires Positive quantity.
        sortOrder++;
        continue;
      }
      var request =
          new AddLineItemRequest(
              projectId, group.description(), quantity, unitPrice, sortOrder, null, null);
      invoiceService.addLineItem(invoiceId, request);
      sortOrder++;
    }
  }

  private UUID resolveProjectId(java.util.List<UUID> sourceTimeEntryIds) {
    if (sourceTimeEntryIds == null || sourceTimeEntryIds.isEmpty()) {
      return null;
    }
    UUID firstId = sourceTimeEntryIds.get(0);
    var entry = timeEntryRepository.findById(firstId).orElse(null);
    if (entry == null) {
      return null;
    }
    return taskRepository
        .findById(entry.getTaskId())
        .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()))
        .getProjectId();
  }

  /**
   * Compute a weighted unit price (per-hour) for the group: total billable amount across source
   * time entries divided by the group's quoted hours. Falls back to BigDecimal.ZERO when no
   * billing-rate snapshots exist on the source entries.
   */
  private BigDecimal resolveUnitPrice(java.util.List<UUID> sourceTimeEntryIds, BigDecimal hours) {
    if (sourceTimeEntryIds == null
        || sourceTimeEntryIds.isEmpty()
        || hours == null
        || hours.signum() == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (UUID id : sourceTimeEntryIds) {
      var entry = timeEntryRepository.findById(id).orElse(null);
      if (entry == null) continue;
      BigDecimal rate = entry.getBillingRateSnapshot();
      if (rate == null) continue;
      BigDecimal entryHours =
          BigDecimal.valueOf(entry.getDurationMinutes())
              .divide(BigDecimal.valueOf(60L), 4, RoundingMode.HALF_UP);
      totalAmount = totalAmount.add(rate.multiply(entryHours));
    }
    if (totalAmount.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return totalAmount.divide(hours, 2, RoundingMode.HALF_UP);
  }
}
