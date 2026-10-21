package io.b2mash.b2b.b2bstrawman.exception;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class InvalidStateException extends ErrorResponseException {

  public InvalidStateException(String title, String detail) {
    super(
        HttpStatus.BAD_REQUEST,
        ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, title, detail),
        null);
  }

  private InvalidStateException(ProblemDetail problem) {
    super(HttpStatus.BAD_REQUEST, problem, null);
  }

  /**
   * 400 carrying a machine-readable RFC 9457 {@code type} (e.g. {@code "diary.window-too-large"})
   * the frontend can branch on via {@code detail.type} (same convention as {@code
   * SubscriptionGuardFilter}'s {@code "subscription_required"}).
   */
  public static InvalidStateException withType(String type, String title, String detail) {
    var problem = ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, title, detail);
    problem.setType(URI.create(type));
    return new InvalidStateException(problem);
  }
}
