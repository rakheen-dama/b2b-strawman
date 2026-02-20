package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.retainer.dto.RetainerSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SecurityConfig covers /api/** with .authenticated() — @PreAuthorize handles fine-grained auth.
@RestController
@RequestMapping("/api/customers")
public class RetainerSummaryController {

  private final RetainerAgreementRepository retainerAgreementRepository;
  private final RetainerPeriodRepository retainerPeriodRepository;

  public RetainerSummaryController(
      RetainerAgreementRepository retainerAgreementRepository,
      RetainerPeriodRepository retainerPeriodRepository) {
    this.retainerAgreementRepository = retainerAgreementRepository;
    this.retainerPeriodRepository = retainerPeriodRepository;
  }

  @GetMapping("/{customerId}/retainer-summary")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RetainerSummaryResponse> getRetainerSummary(@PathVariable UUID customerId) {

    var agreementOpt = retainerAgreementRepository.findActiveOrPausedByCustomerId(customerId);
    if (agreementOpt.isEmpty()) {
      return ResponseEntity.ok(RetainerSummaryResponse.noRetainer());
    }
    var agreement = agreementOpt.get();

    // Find open period — there may be none if the retainer was just created or is paused
    var periodOpt =
        retainerPeriodRepository.findByAgreementIdAndStatus(agreement.getId(), PeriodStatus.OPEN);

    BigDecimal consumedHours =
        periodOpt.map(RetainerPeriod::getConsumedHours).orElse(BigDecimal.ZERO);

    if (agreement.getType() == RetainerType.FIXED_FEE) {
      // FIXED_FEE: no allocation/remaining/percent fields
      return ResponseEntity.ok(
          new RetainerSummaryResponse(
              true,
              agreement.getId(),
              agreement.getName(),
              agreement.getType(),
              null, // allocatedHours — not applicable for FIXED_FEE
              consumedHours,
              null, // remainingHours — not applicable
              null, // percentConsumed — not applicable
              false)); // isOverage — not applicable
    }

    // HOUR_BANK: full summary
    BigDecimal allocatedHours = agreement.getAllocatedHours();
    BigDecimal remainingHours =
        periodOpt.map(RetainerPeriod::getRemainingHours).orElse(allocatedHours);

    BigDecimal percentConsumed = null;
    boolean isOverage = false;
    if (allocatedHours != null && allocatedHours.signum() > 0) {
      percentConsumed =
          consumedHours
              .multiply(BigDecimal.valueOf(100))
              .divide(allocatedHours, 1, RoundingMode.HALF_UP);
      isOverage = consumedHours.compareTo(allocatedHours) > 0;
    }

    return ResponseEntity.ok(
        new RetainerSummaryResponse(
            true,
            agreement.getId(),
            agreement.getName(),
            agreement.getType(),
            allocatedHours,
            consumedHours,
            remainingHours,
            percentConsumed,
            isOverage));
  }
}
