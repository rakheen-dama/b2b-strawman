package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 2 — all disbursements on the matter must be past DRAFT / PENDING_APPROVAL status (Phase 67
 * §67.3.4 gate 2).
 */
@Component
public class AllDisbursementsApprovedGate implements ClosureGate {

  static final String CODE = "ALL_DISBURSEMENTS_APPROVED";
  private static final List<String> UNAPPROVED_STATUSES = List.of("DRAFT", "PENDING_APPROVAL");

  private final DisbursementRepository disbursementRepository;

  public AllDisbursementsApprovedGate(DisbursementRepository disbursementRepository) {
    this.disbursementRepository = disbursementRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 2;
  }

  @Override
  public GateResult evaluate(Project project) {
    long count =
        disbursementRepository.countByProjectIdAndApprovalStatusIn(
            project.getId(), UNAPPROVED_STATUSES);
    if (count == 0) {
      return new GateResult(true, CODE, "All disbursements approved.", Map.of());
    }
    return new GateResult(
        false, CODE, "%d disbursements are unapproved.".formatted(count), Map.of("count", count));
  }
}
