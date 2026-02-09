package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.multitenancy.MemberContextNotBoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MemberContextNotBoundException.class)
  public ResponseEntity<ProblemDetail> handleMemberContextNotBound(
      MemberContextNotBoundException ex) {
    log.error("Member context invariant violation: {}", ex.getMessage());
    var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Member context not available");
    problem.setDetail("Unable to resolve member identity for request");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
}
