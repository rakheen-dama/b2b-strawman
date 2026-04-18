package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 5 — no court dates on or after today may remain in SCHEDULED or POSTPONED status (Phase 67
 * §67.3.4 gate 5).
 */
@Component
public class NoOpenCourtDatesGate implements ClosureGate {

  static final String CODE = "NO_OPEN_COURT_DATES";
  private static final List<String> OPEN_STATUSES = List.of("SCHEDULED", "POSTPONED");

  private final CourtDateRepository courtDateRepository;
  private final Clock clock;

  public NoOpenCourtDatesGate(CourtDateRepository courtDateRepository, Clock clock) {
    this.courtDateRepository = courtDateRepository;
    this.clock = clock;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 5;
  }

  @Override
  public GateResult evaluate(Project project) {
    long count =
        courtDateRepository.countByProjectIdAndStatusInAndScheduledDateGreaterThanEqual(
            project.getId(), OPEN_STATUSES, LocalDate.now(clock));
    if (count == 0) {
      return new GateResult(true, CODE, "No court dates scheduled for today or later.", Map.of());
    }
    return new GateResult(
        false,
        CODE,
        "%d court dates scheduled for today or later.".formatted(count),
        Map.of("count", count));
  }
}
