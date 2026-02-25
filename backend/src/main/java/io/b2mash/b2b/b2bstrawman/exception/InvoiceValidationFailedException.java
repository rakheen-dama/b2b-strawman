package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceValidationService.ValidationCheck;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when invoice send validation has critical failures that require admin/owner override.
 * Results in HTTP 422 Unprocessable Entity with canOverride flag and validation checks.
 */
public class InvoiceValidationFailedException extends ErrorResponseException {

  public InvoiceValidationFailedException(List<ValidationCheck> checks) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, createProblem(checks), null);
  }

  private static ProblemDetail createProblem(List<ValidationCheck> checks) {
    var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    problem.setTitle("Invoice validation failed");
    problem.setDetail(
        "Invoice has validation issues that must be resolved or overridden before sending.");
    problem.setProperty("canOverride", true);
    problem.setProperty("validationChecks", checks);
    return problem;
  }
}
