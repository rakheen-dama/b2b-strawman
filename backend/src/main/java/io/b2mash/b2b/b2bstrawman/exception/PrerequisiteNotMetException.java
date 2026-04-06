package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteCheck;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PrerequisiteNotMetException extends ErrorResponseException {

  private final PrerequisiteCheck prerequisiteCheck;

  public PrerequisiteNotMetException(PrerequisiteCheck check) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ProblemDetailFactory.create(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Prerequisites not met",
            check.violations().size()
                + " required field(s) missing for "
                + check.context().getDisplayLabel()),
        null);
    this.prerequisiteCheck = check;
  }

  public PrerequisiteCheck getPrerequisiteCheck() {
    return prerequisiteCheck;
  }
}
