package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequestRepository;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 9 — no document acceptance requests may remain in an active (non-terminal) status (Phase 67
 * §67.3.4 gate 9).
 *
 * <p>Implementation note: acceptance requests are scoped to a customer (not to a project). The gate
 * therefore queries by the matter's customerId — on the legal-vertical assumption that one matter
 * per customer is the common case. A future phase may extend {@code AcceptanceRequest} with a
 * direct {@code projectId} for tighter scoping. Active statuses are PENDING, SENT, VIEWED (the
 * non-terminal set defined on {@link AcceptanceStatus}).
 */
@Component
public class AllAcceptanceRequestsFinalGate implements ClosureGate {

  static final String CODE = "ALL_ACCEPTANCE_REQUESTS_FINAL";
  private static final List<AcceptanceStatus> ACTIVE_STATUSES =
      List.of(AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED);

  private final AcceptanceRequestRepository acceptanceRequestRepository;

  public AllAcceptanceRequestsFinalGate(AcceptanceRequestRepository acceptanceRequestRepository) {
    this.acceptanceRequestRepository = acceptanceRequestRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 9;
  }

  @Override
  public GateResult evaluate(Project project) {
    // Acceptance requests are customer-scoped, not project-scoped. If a project has no linked
    // customer (should be rare in legal-vertical flows), treat the gate as passing — we have
    // nothing to count.
    var customerId = project.getCustomerId();
    if (customerId == null) {
      return new GateResult(true, CODE, "No document acceptances pending.", Map.of());
    }
    long count =
        acceptanceRequestRepository.countByCustomerIdAndStatusIn(customerId, ACTIVE_STATUSES);
    if (count == 0) {
      return new GateResult(true, CODE, "No document acceptances pending.", Map.of());
    }
    return new GateResult(
        false, CODE, "%d document acceptances pending.".formatted(count), Map.of("count", count));
  }
}
