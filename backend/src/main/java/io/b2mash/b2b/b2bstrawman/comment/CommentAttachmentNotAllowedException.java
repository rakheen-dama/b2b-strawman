package io.b2mash.b2b.b2bstrawman.comment;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A comment attachment failed validation (Phase 88 U24, A13). Results in HTTP 422 titled
 * "Attachment not allowed", with a per-reason detail and a {@code documentId} property when a
 * single document is at fault.
 */
public class CommentAttachmentNotAllowedException extends ErrorResponseException {

  public CommentAttachmentNotAllowedException(String detail) {
    this(detail, null);
  }

  public CommentAttachmentNotAllowedException(String detail, UUID documentId) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, problem(detail, documentId), null);
  }

  private static ProblemDetail problem(String detail, UUID documentId) {
    var problem =
        ProblemDetailFactory.create(
            HttpStatus.UNPROCESSABLE_ENTITY, "Attachment not allowed", detail);
    if (documentId != null) {
      problem.setProperty("documentId", documentId);
    }
    return problem;
  }
}
