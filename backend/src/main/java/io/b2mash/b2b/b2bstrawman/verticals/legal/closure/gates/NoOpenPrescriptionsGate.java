package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 6 — no prescription trackers on the matter may remain in RUNNING or WARNED status (Phase 67
 * §67.3.4 gate 6).
 */
@Component
public class NoOpenPrescriptionsGate implements ClosureGate {

  static final String CODE = "NO_OPEN_PRESCRIPTIONS";
  private static final List<String> OPEN_STATUSES = List.of("RUNNING", "WARNED");

  private final PrescriptionTrackerRepository prescriptionTrackerRepository;

  public NoOpenPrescriptionsGate(PrescriptionTrackerRepository prescriptionTrackerRepository) {
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 6;
  }

  @Override
  public GateResult evaluate(Project project) {
    long count =
        prescriptionTrackerRepository.countByProjectIdAndStatusIn(project.getId(), OPEN_STATUSES);
    if (count == 0) {
      return new GateResult(true, CODE, "No prescription timers still running.", Map.of());
    }
    return new GateResult(
        false,
        CODE,
        "%d prescription timers still running.".formatted(count),
        Map.of("count", count));
  }
}
