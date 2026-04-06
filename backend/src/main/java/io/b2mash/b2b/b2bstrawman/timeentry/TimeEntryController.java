package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeEntryController {

  private final TimeEntryService timeEntryService;
  private final TimeEntryBatchService timeEntryBatchService;

  public TimeEntryController(
      TimeEntryService timeEntryService, TimeEntryBatchService timeEntryBatchService) {
    this.timeEntryService = timeEntryService;
    this.timeEntryBatchService = timeEntryBatchService;
  }

  @PostMapping("/api/tasks/{taskId}/time-entries")
  public ResponseEntity<TimeEntryResponse> createTimeEntry(
      @PathVariable UUID taskId,
      @Valid @RequestBody CreateTimeEntryRequest request,
      ActorContext actor) {

    var result =
        timeEntryService.createTimeEntry(
            taskId,
            request.date(),
            request.durationMinutes(),
            request.billable() != null ? request.billable() : true,
            request.rateCents(),
            request.description(),
            actor);

    var names = timeEntryService.resolveNames(List.of(result.entry()));
    var invoiceNumbers = timeEntryService.resolveInvoiceNumbers(List.of(result.entry()));
    return ResponseEntity.created(URI.create("/api/time-entries/" + result.entry().getId()))
        .body(TimeEntryResponse.from(result.entry(), names, invoiceNumbers, result.rateWarning()));
  }

  @GetMapping("/api/tasks/{taskId}/time-entries")
  public ResponseEntity<List<TimeEntryResponse>> listTimeEntries(
      @PathVariable UUID taskId,
      @RequestParam(required = false) Boolean billable,
      @RequestParam(required = false) BillingStatus billingStatus,
      ActorContext actor) {

    var entries = timeEntryService.listTimeEntriesByTask(taskId, actor, billable, billingStatus);
    var names = timeEntryService.resolveNames(entries);
    var invoiceNumbers = timeEntryService.resolveInvoiceNumbers(entries);
    var response =
        entries.stream().map(e -> TimeEntryResponse.from(e, names, invoiceNumbers)).toList();
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/api/projects/{projectId}/time-entries/{id}/billable")
  public ResponseEntity<TimeEntryResponse> toggleBillable(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @Valid @RequestBody ToggleBillableRequest request,
      ActorContext actor) {

    var entry = timeEntryService.toggleBillable(projectId, id, request.billable(), actor);
    var names = timeEntryService.resolveNames(List.of(entry));
    var invoiceNumbers = timeEntryService.resolveInvoiceNumbers(List.of(entry));
    return ResponseEntity.ok(TimeEntryResponse.from(entry, names, invoiceNumbers));
  }

  @PutMapping("/api/time-entries/{id}")
  public ResponseEntity<TimeEntryResponse> updateTimeEntry(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTimeEntryRequest request,
      ActorContext actor) {

    var entry =
        timeEntryService.updateTimeEntry(
            id,
            request.date(),
            request.durationMinutes(),
            request.billable(),
            request.rateCents(),
            request.description(),
            actor);

    var names = timeEntryService.resolveNames(List.of(entry));
    var invoiceNumbers = timeEntryService.resolveInvoiceNumbers(List.of(entry));
    return ResponseEntity.ok(TimeEntryResponse.from(entry, names, invoiceNumbers));
  }

  @PostMapping("/api/time-entries/batch")
  public ResponseEntity<BatchTimeEntryResult> createBatch(
      @Valid @RequestBody BatchTimeEntryRequest request, ActorContext actor) {
    var result = timeEntryBatchService.createBatch(request, actor);
    return ResponseEntity.ok(result);
  }

  @DeleteMapping("/api/time-entries/{id}")
  public ResponseEntity<Void> deleteTimeEntry(@PathVariable UUID id, ActorContext actor) {

    timeEntryService.deleteTimeEntry(id, actor);
    return ResponseEntity.noContent().build();
  }

  // --- DTOs ---

  public record ToggleBillableRequest(
      @NotNull(message = "billable is required") Boolean billable) {}

  public record CreateTimeEntryRequest(
      @NotNull(message = "date is required") LocalDate date,
      @NotNull(message = "durationMinutes is required")
          @Positive(message = "durationMinutes must be positive")
          Integer durationMinutes,
      Boolean billable,
      Integer rateCents,
      String description) {}

  public record UpdateTimeEntryRequest(
      LocalDate date,
      @Positive(message = "durationMinutes must be positive") Integer durationMinutes,
      Boolean billable,
      Integer rateCents,
      String description) {}

  public record BatchTimeEntryItem(
      @NotNull(message = "taskId is required") UUID taskId,
      @NotNull(message = "date is required") LocalDate date,
      @Positive(message = "durationMinutes must be positive") int durationMinutes,
      String description,
      boolean billable) {}

  public record BatchTimeEntryRequest(
      @NotNull(message = "entries is required")
          @Size(min = 1, max = 50, message = "entries must have between 1 and 50 items")
          List<BatchTimeEntryItem> entries) {}

  public record CreatedEntry(UUID id, UUID taskId, LocalDate date) {}

  public record EntryError(int index, UUID taskId, String message) {}

  public record BatchTimeEntryResult(
      List<CreatedEntry> created, List<EntryError> errors, int totalCreated, int totalErrors) {}

  public record TimeEntryResponse(
      UUID id,
      UUID taskId,
      UUID memberId,
      String memberName,
      LocalDate date,
      int durationMinutes,
      boolean billable,
      Integer rateCents,
      BigDecimal billingRateSnapshot,
      String billingRateCurrency,
      BigDecimal costRateSnapshot,
      String costRateCurrency,
      BigDecimal billableValue,
      BigDecimal costValue,
      String description,
      UUID invoiceId,
      String invoiceNumber,
      Instant createdAt,
      Instant updatedAt,
      String rateWarning) {

    public static TimeEntryResponse from(
        TimeEntry entry, Map<UUID, String> memberNames, Map<UUID, String> invoiceNumbers) {
      return from(entry, memberNames, invoiceNumbers, null);
    }

    public static TimeEntryResponse from(
        TimeEntry entry,
        Map<UUID, String> memberNames,
        Map<UUID, String> invoiceNumbers,
        String rateWarning) {
      return new TimeEntryResponse(
          entry.getId(),
          entry.getTaskId(),
          entry.getMemberId(),
          memberNames.get(entry.getMemberId()),
          entry.getDate(),
          entry.getDurationMinutes(),
          entry.isBillable(),
          entry.getRateCents(),
          entry.getBillingRateSnapshot(),
          entry.getBillingRateCurrency(),
          entry.getCostRateSnapshot(),
          entry.getCostRateCurrency(),
          entry.getBillableValue(),
          entry.getCostValue(),
          entry.getDescription(),
          entry.getInvoiceId(),
          entry.getInvoiceId() != null ? invoiceNumbers.get(entry.getInvoiceId()) : null,
          entry.getCreatedAt(),
          entry.getUpdatedAt(),
          rateWarning);
    }
  }
}
