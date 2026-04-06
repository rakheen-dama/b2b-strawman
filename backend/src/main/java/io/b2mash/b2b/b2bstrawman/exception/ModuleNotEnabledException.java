package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ModuleNotEnabledException extends ErrorResponseException {

  public ModuleNotEnabledException(String moduleId) {
    super(HttpStatus.FORBIDDEN, createProblem(moduleId), null);
  }

  private static org.springframework.http.ProblemDetail createProblem(String moduleId) {
    String humanName;
    if (moduleId == null || moduleId.isEmpty()) {
      humanName = "Unknown";
    } else {
      humanName = moduleId.replace("_", " ");
      humanName = Character.toUpperCase(humanName.charAt(0)) + humanName.substring(1);
    }
    return ProblemDetailFactory.create(
        HttpStatus.FORBIDDEN,
        "Module not enabled",
        "This feature requires the "
            + humanName
            + " module. "
            + "Contact your administrator to enable it.");
  }
}
