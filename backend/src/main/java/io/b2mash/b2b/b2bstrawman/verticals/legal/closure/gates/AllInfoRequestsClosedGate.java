package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 8 — no information requests on the matter may remain in an active (non-terminal) status
 * (Phase 67 §67.3.4 gate 8).
 *
 * <p>Implementation note: the architecture enumerates the open state as "{@code PENDING}", but the
 * actual {@link RequestStatus} enum uses SENT / IN_PROGRESS for the "outstanding to the client"
 * path (DRAFT is pre-send; COMPLETED / CANCELLED are terminal). We count SENT + IN_PROGRESS as
 * outstanding.
 */
@Component
public class AllInfoRequestsClosedGate implements ClosureGate {

  static final String CODE = "ALL_INFO_REQUESTS_CLOSED";
  private static final List<RequestStatus> ACTIVE_STATUSES =
      List.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS);

  private final InformationRequestRepository informationRequestRepository;

  public AllInfoRequestsClosedGate(InformationRequestRepository informationRequestRepository) {
    this.informationRequestRepository = informationRequestRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 8;
  }

  @Override
  public GateResult evaluate(Project project) {
    long count =
        informationRequestRepository.countByProjectIdAndStatusIn(project.getId(), ACTIVE_STATUSES);
    if (count == 0) {
      return new GateResult(true, CODE, "All client information requests closed.", Map.of());
    }
    return new GateResult(
        false,
        CODE,
        "%d client information requests outstanding.".formatted(count),
        Map.of("count", count));
  }
}
