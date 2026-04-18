package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown by {@code MatterClosureService.reopen} when the retention window for the matter has
 * elapsed. The retention anchor is {@code project.retentionClockStartedAt} when present, with
 * {@code project.closedAt} as a fallback for legacy rows. Eligibility condition:
 *
 * <pre>
 *   retentionAnchor + orgSettings.legalMatterRetentionYears &lt; now
 * </pre>
 *
 * <p>Once retention has elapsed a matter cannot be reopened — Phase 67 §67.3.6, ADR-249. Note: the
 * minimal 489B slice does not yet persist a retention_policies row / soft-cancel on reopen — see
 * TODO(489C) in {@code MatterClosureService}.
 */
public class RetentionElapsedException extends ErrorResponseException {

  private static final URI TYPE_URI = URI.create("https://kazi.app/problems/retention-elapsed");

  public RetentionElapsedException(UUID projectId, LocalDate retentionEndedOn) {
    super(HttpStatus.CONFLICT, createProblem(projectId, retentionEndedOn), null);
  }

  private static ProblemDetail createProblem(UUID projectId, LocalDate retentionEndedOn) {
    ProblemDetail problem =
        ProblemDetailFactory.create(
            HttpStatus.CONFLICT,
            "Retention elapsed",
            "Matter retention window expired on "
                + retentionEndedOn
                + ". This matter can no longer be reopened.");
    problem.setType(TYPE_URI);
    problem.setProperty("projectId", projectId);
    problem.setProperty("retentionEndedOn", retentionEndedOn.toString());
    return problem;
  }
}
