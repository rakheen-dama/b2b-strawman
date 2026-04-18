package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 3 — every APPROVED disbursement must be out of UNBILLED status (billed or written off).
 * Prevents closing a matter that still owes the firm recovery on an approved disbursement (Phase 67
 * §67.3.4 gate 3).
 */
@Component
public class AllDisbursementsSettledGate implements ClosureGate {

  static final String CODE = "ALL_DISBURSEMENTS_SETTLED";
  private static final String APPROVED = "APPROVED";
  private static final String UNBILLED = "UNBILLED";

  private final DisbursementRepository disbursementRepository;

  public AllDisbursementsSettledGate(DisbursementRepository disbursementRepository) {
    this.disbursementRepository = disbursementRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 3;
  }

  @Override
  public GateResult evaluate(Project project) {
    long count =
        disbursementRepository.countByProjectIdAndApprovalStatusAndBillingStatus(
            project.getId(), APPROVED, UNBILLED);
    if (count == 0) {
      return new GateResult(true, CODE, "All approved disbursements are settled.", Map.of());
    }
    return new GateResult(
        false,
        CODE,
        "%d approved disbursements are unbilled.".formatted(count),
        Map.of("count", count));
  }
}
