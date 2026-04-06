package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.template.TemplateValidationService.TemplateValidationResult;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a template generate request has missing required context fields and the caller has
 * not acknowledged the warnings. Results in HTTP 422 Unprocessable Entity.
 */
public class ValidationWarningException extends ErrorResponseException {

  public ValidationWarningException(TemplateValidationResult validationResult) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ProblemDetailFactory.create(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Required context fields missing",
            "Template has required context fields that are not populated. "
                + "Set acknowledgeWarnings=true to generate anyway.",
            Map.of("validationResult", validationResult)),
        null);
  }
}
