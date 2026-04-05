package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService.InterestRunDetailResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService.InterestRunResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Controller for interest run lifecycle endpoints. */
@RestController
public class InterestController {

  private final InterestService interestService;

  public InterestController(InterestService interestService) {
    this.interestService = interestService;
  }

  @PostMapping("/api/trust-accounts/{accountId}/interest-runs")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<InterestRunResponse> createInterestRun(
      @PathVariable UUID accountId, @Valid @RequestBody CreateInterestRunRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            interestService.createInterestRun(
                accountId, request.periodStart(), request.periodEnd()));
  }

  @GetMapping("/api/trust-accounts/{accountId}/interest-runs")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<InterestRunResponse>> listInterestRuns(@PathVariable UUID accountId) {
    return ResponseEntity.ok(interestService.listInterestRuns(accountId));
  }

  @GetMapping("/api/interest-runs/{runId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<InterestRunDetailResponse> getInterestRunDetail(@PathVariable UUID runId) {
    return ResponseEntity.ok(interestService.getInterestRunDetail(runId));
  }

  @PostMapping("/api/interest-runs/{runId}/calculate")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<InterestRunResponse> calculateInterest(@PathVariable UUID runId) {
    return ResponseEntity.ok(interestService.calculateInterest(runId));
  }

  @PostMapping("/api/interest-runs/{runId}/approve")
  @RequiresCapability("APPROVE_TRUST_PAYMENT")
  public ResponseEntity<InterestRunResponse> approveInterestRun(@PathVariable UUID runId) {
    return ResponseEntity.ok(interestService.approveInterestRun(runId));
  }

  @PostMapping("/api/interest-runs/{runId}/post")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<InterestRunResponse> postInterestRun(@PathVariable UUID runId) {
    return ResponseEntity.ok(interestService.postInterestRun(runId));
  }

  // --- Request Records ---

  record CreateInterestRunRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}
}
