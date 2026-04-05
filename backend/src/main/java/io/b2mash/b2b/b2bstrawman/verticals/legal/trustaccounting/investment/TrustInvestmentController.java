package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.PlaceInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.TrustInvestmentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class TrustInvestmentController {

  private final TrustInvestmentService investmentService;

  public TrustInvestmentController(TrustInvestmentService investmentService) {
    this.investmentService = investmentService;
  }

  @GetMapping("/api/trust-accounts/{accountId}/investments")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<Page<TrustInvestmentResponse>> listInvestments(
      @PathVariable UUID accountId, Pageable pageable) {
    return ResponseEntity.ok(investmentService.listInvestments(accountId, pageable));
  }

  @GetMapping("/api/trust-investments/{investmentId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustInvestmentResponse> getInvestment(@PathVariable UUID investmentId) {
    return ResponseEntity.ok(investmentService.getInvestment(investmentId));
  }

  @PostMapping("/api/trust-accounts/{accountId}/investments")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustInvestmentResponse> placeInvestment(
      @PathVariable UUID accountId, @Valid @RequestBody PlaceInvestmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(investmentService.placeInvestment(accountId, request));
  }

  @PutMapping("/api/trust-investments/{investmentId}/interest")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustInvestmentResponse> recordInterest(
      @PathVariable UUID investmentId, @Valid @RequestBody RecordInterestRequest request) {
    return ResponseEntity.ok(
        investmentService.recordInterestEarned(investmentId, request.amount()));
  }

  @PostMapping("/api/trust-investments/{investmentId}/withdraw")
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustInvestmentResponse> withdrawInvestment(
      @PathVariable UUID investmentId) {
    return ResponseEntity.ok(investmentService.withdrawInvestment(investmentId));
  }

  @GetMapping("/api/trust-accounts/{accountId}/investments/maturing")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustInvestmentResponse>> getMaturingInvestments(
      @PathVariable UUID accountId,
      @RequestParam(defaultValue = "30") @Positive @Max(365) int daysAhead) {
    return ResponseEntity.ok(investmentService.getMaturing(accountId, daysAhead));
  }

  // --- Request Records ---

  record RecordInterestRequest(@NotNull @Positive BigDecimal amount) {}
}
