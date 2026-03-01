package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.MemberContextNotBoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final AuditService auditService;

  public GlobalExceptionHandler(AuditService auditService) {
    this.auditService = auditService;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn(
        "Access denied: path={}, method={}, reason=insufficient_role",
        request.getRequestURI(),
        request.getMethod());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("security.access_denied")
            .entityType("security")
            .entityId(UUID.randomUUID())
            .details(
                Map.of(
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "reason", "insufficient_role"))
            .build());

    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle("Access denied");
    problem.setDetail("Insufficient permissions for this operation");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {
    String reason = ex.getBody().getDetail();
    log.warn(
        "Forbidden: path={}, method={}, reason={}",
        request.getRequestURI(),
        request.getMethod(),
        reason);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("security.access_denied")
            .entityType("security")
            .entityId(UUID.randomUUID())
            .details(
                Map.of(
                    "path", request.getRequestURI(),
                    "method", request.getMethod(),
                    "reason", reason != null ? reason : "forbidden"))
            .build());

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getBody());
  }

  @ExceptionHandler(MemberContextNotBoundException.class)
  public ResponseEntity<ProblemDetail> handleMemberContextNotBound(
      MemberContextNotBoundException ex) {
    log.error("Member context invariant violation: {}", ex.getMessage());
    var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Member context not available");
    problem.setDetail("Unable to resolve member identity for request");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }

  @ExceptionHandler(PrerequisiteNotMetException.class)
  public ResponseEntity<Map<String, Object>> handlePrerequisiteNotMet(
      PrerequisiteNotMetException ex) {
    log.warn("Prerequisite check failed: {}", ex.getBody().getDetail());
    var check = ex.getPrerequisiteCheck();

    var violations =
        check.violations().stream()
            .map(
                v ->
                    Map.of(
                        "code",
                        (Object) v.code(),
                        "message",
                        v.message(),
                        "entityType",
                        v.entityType(),
                        "entityId",
                        v.entityId().toString(),
                        "fieldSlug",
                        v.fieldSlug() != null ? v.fieldSlug() : "",
                        "resolution",
                        v.resolution() != null ? v.resolution() : ""))
            .toList();

    var body = new LinkedHashMap<String, Object>();
    body.put("type", "about:blank");
    body.put("title", ex.getBody().getTitle());
    body.put("status", 422);
    body.put("detail", ex.getBody().getDetail());
    body.put("violations", violations);

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex) {
    log.warn("Optimistic locking failure: {}", ex.getMessage());
    var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    problem.setTitle("Concurrent modification");
    problem.setDetail("Resource was modified concurrently. Please retry.");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }
}
