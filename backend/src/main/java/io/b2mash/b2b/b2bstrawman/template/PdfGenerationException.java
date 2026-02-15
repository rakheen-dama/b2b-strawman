package io.b2mash.b2b.b2bstrawman.template;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Thrown when PDF generation fails due to rendering or I/O errors. */
public class PdfGenerationException extends ErrorResponseException {

  public PdfGenerationException(String detail, Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, createProblem(detail), cause);
  }

  private static ProblemDetail createProblem(String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("PDF Generation Failed");
    problem.setDetail(detail);
    return problem;
  }
}
