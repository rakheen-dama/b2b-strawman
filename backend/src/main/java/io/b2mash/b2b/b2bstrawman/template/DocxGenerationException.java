package io.b2mash.b2b.b2bstrawman.template;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class DocxGenerationException extends ErrorResponseException {

  public DocxGenerationException(String detail, Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, createProblem(detail), cause);
  }

  private static ProblemDetail createProblem(String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("DOCX Generation Failed");
    problem.setDetail(detail);
    return problem;
  }
}
