package io.b2mash.b2b.b2bstrawman.comment;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matters/{matterId}/comments")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @PostMapping
  public ResponseEntity<CommentResponse> createComment(
      @PathVariable UUID matterId,
      @Valid @RequestBody CreateCommentRequest request,
      ActorContext actor) {
    var response =
        commentService.createCommentResponse(
            matterId,
            request.entityType(),
            request.entityId(),
            request.body(),
            request.visibility(),
            request.attachmentDocumentIds(),
            actor);
    return ResponseEntity.created(
            URI.create("/api/matters/" + matterId + "/comments/" + response.id()))
        .body(response);
  }

  @GetMapping
  public ResponseEntity<List<CommentResponse>> listComments(
      @PathVariable UUID matterId,
      @RequestParam String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      ActorContext actor) {
    return ResponseEntity.ok(
        commentService.listCommentResponses(
            matterId, entityType, entityId, PageRequest.of(page, size), actor));
  }

  @PutMapping("/{commentId}")
  public ResponseEntity<CommentResponse> updateComment(
      @PathVariable UUID matterId,
      @PathVariable UUID commentId,
      @Valid @RequestBody UpdateCommentRequest request,
      ActorContext actor) {
    return ResponseEntity.ok(
        commentService.updateCommentResponse(
            matterId,
            commentId,
            request.body(),
            request.visibility(),
            request.attachmentDocumentIds(),
            actor));
  }

  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable UUID matterId, @PathVariable UUID commentId, ActorContext actor) {

    commentService.deleteComment(matterId, commentId, actor);
    return ResponseEntity.noContent().build();
  }

  // --- DTOs ---

  public record CreateCommentRequest(
      @NotBlank(message = "entityType is required") String entityType,
      @NotNull(message = "entityId is required") UUID entityId,
      @NotBlank(message = "body is required")
          @Size(max = 10000, message = "body must be at most 10000 characters")
          String body,
      String visibility,
      // At most 5 — enforced in the service as a 422, so no @Size here (that would be a 400).
      List<UUID> attachmentDocumentIds) {}

  public record UpdateCommentRequest(
      @NotBlank(message = "body is required")
          @Size(max = 10000, message = "body must be at most 10000 characters")
          String body,
      String visibility,
      // null = leave the attachments unchanged; a list replaces them in order; [] clears.
      List<UUID> attachmentDocumentIds) {}

  /** One attachment chip on a comment; the document is fetched via its own download route. */
  public record CommentAttachmentResponse(
      UUID documentId, String fileName, String contentType, long size) {}

  public record CommentResponse(
      UUID id,
      String entityType,
      UUID entityId,
      UUID matterId,
      UUID authorMemberId,
      String authorName,
      String authorAvatarUrl,
      String body,
      String visibility,
      String source,
      UUID parentId,
      Instant createdAt,
      Instant updatedAt,
      List<CommentAttachmentResponse> attachments) {

    public static CommentResponse from(
        Comment comment,
        Map<UUID, CommentService.AuthorInfo> authors,
        List<CommentAttachmentResponse> attachments) {
      var author = authors.get(comment.getAuthorMemberId());
      return new CommentResponse(
          comment.getId(),
          comment.getEntityType(),
          comment.getEntityId(),
          comment.getProjectId(),
          comment.getAuthorMemberId(),
          author != null ? author.name() : null,
          author != null ? author.avatarUrl() : null,
          comment.getBody(),
          comment.getVisibility(),
          comment.getSource(),
          comment.getParentId(),
          comment.getCreatedAt(),
          comment.getUpdatedAt(),
          attachments);
    }
  }
}
