package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown by {@code MatterClosureService.close} when one or more closure gates fail and the caller
 * did not supply {@code override=true}. Surfaces as a 409 ProblemDetail with the full {@link
 * MatterClosureReport} attached as the {@code report} extension property.
 */
public class ClosureGateFailedException extends ErrorResponseException {

  private static final URI TYPE_URI = URI.create("https://kazi.app/problems/closure-gates-failed");

  private final transient MatterClosureReport report;

  public ClosureGateFailedException(MatterClosureReport report) {
    super(HttpStatus.CONFLICT, createProblem(report), null);
    this.report = report;
  }

  public MatterClosureReport getReport() {
    return report;
  }

  private static ProblemDetail createProblem(MatterClosureReport report) {
    long failed = report.gates().stream().filter(g -> !g.passed()).count();
    ProblemDetail problem =
        ProblemDetailFactory.create(
            HttpStatus.CONFLICT,
            "Matter closure gates failed",
            "%d of %d closure gates failed.".formatted(failed, report.gates().size()));
    problem.setType(TYPE_URI);
    problem.setProperty("report", report);
    return problem;
  }
}
