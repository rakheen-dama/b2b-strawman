package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ModuleNotEnabledException extends ErrorResponseException {

  public ModuleNotEnabledException(String moduleId) {
    super(HttpStatus.FORBIDDEN, createProblem(moduleId), null);
  }

  private static ProblemDetail createProblem(String moduleId) {
    String humanName = moduleId.replace("_", " ");
    humanName = Character.toUpperCase(humanName.charAt(0)) + humanName.substring(1);
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle("Module not enabled");
    problem.setDetail(
        "This feature requires the "
            + humanName
            + " module. "
            + "Contact your administrator to enable it.");
    return problem;
  }
}
