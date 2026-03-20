package io.b2mash.b2b.b2bstrawman.deadline;

import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.CalculatedDeadline;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.DeadlineFilters;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.DeadlineSummary;
import io.b2mash.b2b.b2bstrawman.deadline.FilingStatusService.BatchUpdateRequest;
import io.b2mash.b2b.b2bstrawman.deadline.FilingStatusService.FilingStatusResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for regulatory deadline calculation and filing status management. */
@RestController
public class DeadlineController {

  private final DeadlineCalculationService deadlineCalculationService;
  private final FilingStatusService filingStatusService;
  private final VerticalModuleGuard moduleGuard;

  public DeadlineController(
      DeadlineCalculationService deadlineCalculationService,
      FilingStatusService filingStatusService,
      VerticalModuleGuard moduleGuard) {
    this.deadlineCalculationService = deadlineCalculationService;
    this.filingStatusService = filingStatusService;
    this.moduleGuard = moduleGuard;
  }

  @GetMapping("/api/deadlines")
  public ResponseEntity<List<CalculatedDeadline>> getDeadlines(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId) {
    moduleGuard.requireModule("regulatory_deadlines");
    return ResponseEntity.ok(
        deadlineCalculationService.calculateDeadlines(
            from, to, new DeadlineFilters(category, status, customerId)));
  }

  @GetMapping("/api/deadlines/summary")
  public ResponseEntity<List<DeadlineSummary>> getDeadlineSummary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId) {
    moduleGuard.requireModule("regulatory_deadlines");
    return ResponseEntity.ok(
        deadlineCalculationService.calculateSummary(
            from, to, new DeadlineFilters(category, status, customerId)));
  }

  @GetMapping("/api/customers/{id}/deadlines")
  public ResponseEntity<List<CalculatedDeadline>> getCustomerDeadlines(
      @PathVariable UUID id,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    moduleGuard.requireModule("regulatory_deadlines");
    return ResponseEntity.ok(
        deadlineCalculationService.calculateDeadlinesForCustomer(id, from, to));
  }

  @PutMapping("/api/deadlines/filing-status")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<List<FilingStatusResponse>> updateFilingStatus(
      @Valid @RequestBody BatchUpdateRequest request) {
    moduleGuard.requireModule("regulatory_deadlines");
    return ResponseEntity.ok(
        filingStatusService.batchUpsert(request, RequestScopes.requireMemberId()));
  }

  @GetMapping("/api/filing-statuses")
  public ResponseEntity<List<FilingStatusResponse>> listFilingStatuses(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) String deadlineTypeSlug,
      @RequestParam(required = false) String status) {
    moduleGuard.requireModule("regulatory_deadlines");
    return ResponseEntity.ok(filingStatusService.list(customerId, deadlineTypeSlug, status));
  }
}
