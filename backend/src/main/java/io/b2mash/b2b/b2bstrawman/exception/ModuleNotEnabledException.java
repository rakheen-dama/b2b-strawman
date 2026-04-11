package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ModuleNotEnabledException extends ErrorResponseException {

  public ModuleNotEnabledException(String moduleId) {
    super(HttpStatus.FORBIDDEN, createProblem(moduleId), null);
  }

  private static org.springframework.http.ProblemDetail createProblem(String moduleId) {
    var problem =
        ProblemDetailFactory.create(
            HttpStatus.FORBIDDEN,
            "Module not enabled",
            "This feature is not enabled for your organization. "
                + "An admin can enable it in Settings → Features.");
    if (moduleId != null && !moduleId.isEmpty()) {
      problem.setProperty("moduleId", moduleId);
    }
    return problem;
  }
}
