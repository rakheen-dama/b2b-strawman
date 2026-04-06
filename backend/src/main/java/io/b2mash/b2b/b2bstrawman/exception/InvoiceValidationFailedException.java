package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceValidationService.ValidationCheck;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when invoice send validation has critical failures that require admin/owner override.
 * Results in HTTP 422 Unprocessable Entity with canOverride flag and validation checks.
 */
public class InvoiceValidationFailedException extends ErrorResponseException {

  public InvoiceValidationFailedException(List<ValidationCheck> checks) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ProblemDetailFactory.create(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Invoice validation failed",
            "Invoice has validation issues that must be resolved or overridden before sending.",
            Map.of("canOverride", true, "validationChecks", checks)),
        null);
  }
}
