package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final MemberNameResolver memberNameResolver;
  private final InvoiceRepository invoiceRepository;

  public TimeEntryController(
      TimeEntryService timeEntryService,
      MemberNameResolver memberNameResolver,
      InvoiceRepository invoiceRepository) {
    this.timeEntryService = timeEntryService;
    this.memberNameResolver = memberNameResolver;
    this.invoiceRepository = invoiceRepository;
  }

  @PostMapping("/api/tasks/{taskId}/time-entries")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TimeEntryResponse> createTimeEntry(
      @PathVariable UUID taskId, @Valid @RequestBody CreateTimeEntryRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var result =
        timeEntryService.createTimeEntry(
            taskId,
            request.date(),
            request.durationMinutes(),
            request.billable() != null ? request.billable() : true,
            request.rateCents(),
            request.description(),
            memberId,
            orgRole);

    var names = resolveNames(List.of(result.entry()));
    var invoiceNumbers = resolveInvoiceNumbers(List.of(result.entry()));
    return ResponseEntity.created(URI.create("/api/time-entries/" + result.entry().getId()))
        .body(TimeEntryResponse.from(result.entry(), names, invoiceNumbers, result.rateWarning()));
  }

  @GetMapping("/api/tasks/{taskId}/time-entries")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TimeEntryResponse>> listTimeEntries(
      @PathVariable UUID taskId,
      @RequestParam(required = false) Boolean billable,
      @RequestParam(required = false) BillingStatus billingStatus) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var entries =
        timeEntryService.listTimeEntriesByTask(taskId, memberId, orgRole, billable, billingStatus);
    var names = resolveNames(entries);
    var invoiceNumbers = resolveInvoiceNumbers(entries);
    var response =
        entries.stream().map(e -> TimeEntryResponse.from(e, names, invoiceNumbers)).toList();
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/api/projects/{projectId}/time-entries/{id}/billable")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TimeEntryResponse> toggleBillable(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @Valid @RequestBody ToggleBillableRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var entry =
        timeEntryService.toggleBillable(projectId, id, request.billable(), memberId, orgRole);
    var names = resolveNames(List.of(entry));
    var invoiceNumbers = resolveInvoiceNumbers(List.of(entry));
    return ResponseEntity.ok(TimeEntryResponse.from(entry, names, invoiceNumbers));
  }

  @PutMapping("/api/time-entries/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TimeEntryResponse> updateTimeEntry(
      @PathVariable UUID id, @Valid @RequestBody UpdateTimeEntryRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var entry =
        timeEntryService.updateTimeEntry(
            id,
            request.date(),
            request.durationMinutes(),
            request.billable(),
            request.rateCents(),
            request.description(),
            memberId,
            orgRole);

    var names = resolveNames(List.of(entry));
    var invoiceNumbers = resolveInvoiceNumbers(List.of(entry));
    return ResponseEntity.ok(TimeEntryResponse.from(entry, names, invoiceNumbers));
  }

  @DeleteMapping("/api/time-entries/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteTimeEntry(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    timeEntryService.deleteTimeEntry(id, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  /**
   * Batch-loads member names for all member IDs referenced by the given time entries. Returns a map
   * of member UUID to display name.
   */
  private Map<UUID, String> resolveNames(List<TimeEntry> entries) {
    var ids =
        entries.stream().map(TimeEntry::getMemberId).filter(Objects::nonNull).distinct().toList();
    return memberNameResolver.resolveNames(ids);
  }

  /**
   * Batch-loads invoice numbers for all invoice IDs referenced by the given time entries. Returns a
   * map of invoice UUID to human-readable invoice number (e.g., "INV-0001"). Drafts without an
   * assigned number are represented as "Draft".
   */
  private Map<UUID, String> resolveInvoiceNumbers(List<TimeEntry> entries) {
    var invoiceIds =
        entries.stream().map(TimeEntry::getInvoiceId).filter(Objects::nonNull).distinct().toList();

    if (invoiceIds.isEmpty()) {
      return Map.of();
    }

    return invoiceRepository.findAllById(invoiceIds).stream()
        .collect(
            Collectors.toMap(
                Invoice::getId,
                inv -> inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "Draft",
                (a, b) -> a));
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
