package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Thrown when portal authentication fails (invalid/expired token, bad credentials). */
public class PortalAuthException extends ErrorResponseException {

  public PortalAuthException(String detail) {
    super(
        HttpStatus.UNAUTHORIZED,
        ProblemDetailFactory.create(
            HttpStatus.UNAUTHORIZED, "Portal authentication failed", detail),
        null);
  }
}
