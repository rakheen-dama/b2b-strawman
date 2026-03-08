package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunItemResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunPreviewResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CreateBillingRunRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.ExpenseResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.LoadPreviewRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.TimeEntryResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.UpdateEntrySelectionsRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing-runs")
public class BillingRunController {

  private final BillingRunService billingRunService;

  public BillingRunController(BillingRunService billingRunService) {
    this.billingRunService = billingRunService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunResponse> createRun(
      @Valid @RequestBody CreateBillingRunRequest request) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    var response = billingRunService.createRun(request, actorMemberId);
    return ResponseEntity.created(URI.create("/api/billing-runs/" + response.id())).body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<BillingRunResponse>> listRuns(
      @RequestParam(required = false) List<BillingRunStatus> status, Pageable pageable) {
    return ResponseEntity.ok(billingRunService.listRuns(pageable, status));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunResponse> getRun(@PathVariable UUID id) {
    return ResponseEntity.ok(billingRunService.getRun(id));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> cancelRun(@PathVariable UUID id) {
    UUID actorMemberId = RequestScopes.requireMemberId();
    billingRunService.cancelRun(id, actorMemberId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunPreviewResponse> loadPreview(
      @PathVariable UUID id, @RequestBody(required = false) LoadPreviewRequest request) {
    return ResponseEntity.ok(billingRunService.loadPreview(id, request));
  }

  @GetMapping("/{id}/items")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<BillingRunItemResponse>> getItems(@PathVariable UUID id) {
    return ResponseEntity.ok(billingRunService.getItems(id));
  }

  @GetMapping("/{id}/items/{itemId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunItemResponse> getItem(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return ResponseEntity.ok(billingRunService.getItem(id, itemId));
  }

  @GetMapping("/{id}/items/{itemId}/unbilled-time")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TimeEntryResponse>> getUnbilledTime(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return ResponseEntity.ok(billingRunService.getUnbilledTimeEntries(id, itemId));
  }

  @GetMapping("/{id}/items/{itemId}/unbilled-expenses")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ExpenseResponse>> getUnbilledExpenses(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return ResponseEntity.ok(billingRunService.getUnbilledExpenses(id, itemId));
  }

  @PutMapping("/{id}/items/{itemId}/selections")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunItemResponse> updateSelections(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @Valid @RequestBody UpdateEntrySelectionsRequest request) {
    return ResponseEntity.ok(billingRunService.updateEntrySelection(id, itemId, request));
  }

  @PutMapping("/{id}/items/{itemId}/exclude")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunItemResponse> excludeCustomer(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return ResponseEntity.ok(billingRunService.excludeCustomer(id, itemId));
  }

  @PutMapping("/{id}/items/{itemId}/include")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BillingRunItemResponse> includeCustomer(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return ResponseEntity.ok(billingRunService.includeCustomer(id, itemId));
  }
}
