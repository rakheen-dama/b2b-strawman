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
@RequestMapping("/api/projects/{projectId}/comments")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @PostMapping
  public ResponseEntity<CommentResponse> createComment(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateCommentRequest request,
      ActorContext actor) {

    var comment =
        commentService.createComment(
            projectId,
            request.entityType(),
            request.entityId(),
            request.body(),
            request.visibility(),
            actor);

    var authors = commentService.resolveAuthors(List.of(comment));
    return ResponseEntity.created(
            URI.create("/api/projects/" + projectId + "/comments/" + comment.getId()))
        .body(CommentResponse.from(comment, authors));
  }

  @GetMapping
  public ResponseEntity<List<CommentResponse>> listComments(
      @PathVariable UUID projectId,
      @RequestParam String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      ActorContext actor) {

    var commentPage =
        commentService.listComments(
            projectId, entityType, entityId, PageRequest.of(page, size), actor);

    var comments = commentPage.getContent();
    var authors = commentService.resolveAuthors(comments);
    var responses = comments.stream().map(c -> CommentResponse.from(c, authors)).toList();

    return ResponseEntity.ok(responses);
  }

  @PutMapping("/{commentId}")
  public ResponseEntity<CommentResponse> updateComment(
      @PathVariable UUID projectId,
      @PathVariable UUID commentId,
      @Valid @RequestBody UpdateCommentRequest request,
      ActorContext actor) {

    var comment =
        commentService.updateComment(
            projectId, commentId, request.body(), request.visibility(), actor);

    var authors = commentService.resolveAuthors(List.of(comment));
    return ResponseEntity.ok(CommentResponse.from(comment, authors));
  }

  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable UUID projectId, @PathVariable UUID commentId, ActorContext actor) {

    commentService.deleteComment(projectId, commentId, actor);
    return ResponseEntity.noContent().build();
  }

  // --- DTOs ---

  public record CreateCommentRequest(
      @NotBlank(message = "entityType is required") String entityType,
      @NotNull(message = "entityId is required") UUID entityId,
      @NotBlank(message = "body is required")
          @Size(max = 10000, message = "body must be at most 10000 characters")
          String body,
      String visibility) {}

  public record UpdateCommentRequest(
      @NotBlank(message = "body is required")
          @Size(max = 10000, message = "body must be at most 10000 characters")
          String body,
      String visibility) {}

  public record CommentResponse(
      UUID id,
      String entityType,
      UUID entityId,
      UUID projectId,
      UUID authorMemberId,
      String authorName,
      String authorAvatarUrl,
      String body,
      String visibility,
      String source,
      UUID parentId,
      Instant createdAt,
      Instant updatedAt) {

    public static CommentResponse from(
        Comment comment, Map<UUID, CommentService.AuthorInfo> authors) {
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
          comment.getUpdatedAt());
    }
  }
}
