package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.IdempotentTrustWrite;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.InvestmentInterestResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.PlaceInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.ReverseInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.TrustInvestmentResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.WithdrawInvestmentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
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
      @PathVariable UUID accountId,
      @RequestParam(required = false) InvestmentBasis investmentBasis,
      Pageable pageable) {
    return ResponseEntity.ok(
        investmentService.listInvestments(accountId, investmentBasis, pageable));
  }

  @GetMapping("/api/trust-investments/{investmentId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustInvestmentResponse> getInvestment(@PathVariable UUID investmentId) {
    return ResponseEntity.ok(investmentService.getInvestment(investmentId));
  }

  /** {@code accountId} is the funding trust banking account (648A). */
  @PostMapping("/api/trust-accounts/{accountId}/investments")
  @IdempotentTrustWrite
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustInvestmentResponse> placeInvestment(
      @PathVariable UUID accountId, @Valid @RequestBody PlaceInvestmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(investmentService.placeInvestment(accountId, request));
  }

  /** 648B (§16.4.5): a section 86(4) interest entry — replaces the typed {@code PUT}. */
  @PostMapping("/api/trust-investments/{investmentId}/interest")
  @IdempotentTrustWrite
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<InvestmentInterestResponse> recordInterest(
      @PathVariable UUID investmentId,
      @Valid @RequestBody RecordInvestmentInterestRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(investmentService.recordInterest(investmentId, request));
  }

  @GetMapping("/api/trust-investments/{investmentId}/interest")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<InvestmentInterestResponse>> listInterest(
      @PathVariable UUID investmentId) {
    return ResponseEntity.ok(investmentService.listInterest(investmentId));
  }

  @PostMapping("/api/trust-investment-interest/{interestId}/reverse")
  @IdempotentTrustWrite
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<InvestmentInterestResponse> reverseInterest(
      @PathVariable UUID interestId, @Valid @RequestBody ReverseInterestRequest request) {
    return ResponseEntity.ok(investmentService.reverseInterest(interestId, request));
  }

  @PostMapping("/api/trust-investments/{investmentId}/withdraw")
  @IdempotentTrustWrite
  @RequiresCapability("MANAGE_TRUST")
  public ResponseEntity<TrustInvestmentResponse> withdrawInvestment(
      @PathVariable UUID investmentId, @Valid @RequestBody WithdrawInvestmentRequest request) {
    return ResponseEntity.ok(investmentService.withdrawInvestment(investmentId, request));
  }

  @GetMapping("/api/trust-accounts/{accountId}/investments/maturing")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TrustInvestmentResponse>> getMaturingInvestments(
      @PathVariable UUID accountId,
      @RequestParam(defaultValue = "30") @Positive @Max(365) int daysAhead) {
    return ResponseEntity.ok(investmentService.getMaturing(accountId, daysAhead));
  }
}
