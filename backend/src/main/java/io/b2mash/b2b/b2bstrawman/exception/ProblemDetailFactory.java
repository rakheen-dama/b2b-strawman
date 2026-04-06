package io.b2mash.b2b.b2bstrawman.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailFactory {
  private ProblemDetailFactory() {}

  public static ProblemDetail create(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatus(status);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }

  public static ProblemDetail create(
      HttpStatus status, String title, String detail, Map<String, Object> properties) {
    var problem = create(status, title, detail);
    properties.forEach(problem::setProperty);
    return problem;
  }
}
