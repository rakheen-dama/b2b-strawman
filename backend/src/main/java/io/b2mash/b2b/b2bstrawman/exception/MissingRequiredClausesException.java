package io.b2mash.b2b.b2bstrawman.exception;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a document generation request is missing required clauses. Results in HTTP 422
 * Unprocessable Entity.
 */
public class MissingRequiredClausesException extends ErrorResponseException {

  public MissingRequiredClausesException(List<UUID> missingClauseIds) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, createProblem(missingClauseIds), null);
  }

  private static ProblemDetail createProblem(List<UUID> missingClauseIds) {
    var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    problem.setTitle("Missing required clauses");
    problem.setDetail("Required clause(s) missing: " + missingClauseIds);
    problem.setProperty("missingClauseIds", missingClauseIds);
    return problem;
  }
}
