package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteCheck;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class PrerequisiteNotMetException extends ErrorResponseException {

  private final PrerequisiteCheck prerequisiteCheck;

  public PrerequisiteNotMetException(PrerequisiteCheck check) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, createProblem(check), null);
    this.prerequisiteCheck = check;
  }

  public PrerequisiteCheck getPrerequisiteCheck() {
    return prerequisiteCheck;
  }

  private static ProblemDetail createProblem(PrerequisiteCheck check) {
    var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    problem.setTitle("Prerequisites not met");
    problem.setDetail(
        check.violations().size()
            + " required field(s) missing for "
            + check.context().getDisplayLabel());
    return problem;
  }
}
