package io.b2mash.b2b.b2bstrawman.comment;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final MemberRepository memberRepository;

  public CommentController(CommentService commentService, MemberRepository memberRepository) {
    this.commentService = commentService;
    this.memberRepository = memberRepository;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CommentResponse> createComment(
      @PathVariable UUID projectId, @Valid @RequestBody CreateCommentRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var comment =
        commentService.createComment(
            projectId,
            request.entityType(),
            request.entityId(),
            request.body(),
            request.visibility(),
            memberId,
            orgRole);

    var authors = resolveAuthors(List.of(comment));
    return ResponseEntity.created(
            URI.create("/api/projects/" + projectId + "/comments/" + comment.getId()))
        .body(CommentResponse.from(comment, authors));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<CommentResponse>> listComments(
      @PathVariable UUID projectId,
      @RequestParam String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var commentPage =
        commentService.listComments(
            projectId, entityType, entityId, PageRequest.of(page, size), memberId, orgRole);

    var comments = commentPage.getContent();
    var authors = resolveAuthors(comments);
    var responses = comments.stream().map(c -> CommentResponse.from(c, authors)).toList();

    return ResponseEntity.ok(responses);
  }

  @PutMapping("/{commentId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CommentResponse> updateComment(
      @PathVariable UUID projectId,
      @PathVariable UUID commentId,
      @Valid @RequestBody UpdateCommentRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var comment =
        commentService.updateComment(
            projectId, commentId, request.body(), request.visibility(), memberId, orgRole);

    var authors = resolveAuthors(List.of(comment));
    return ResponseEntity.ok(CommentResponse.from(comment, authors));
  }

  @DeleteMapping("/{commentId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteComment(
      @PathVariable UUID projectId, @PathVariable UUID commentId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    commentService.deleteComment(projectId, commentId, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  private Map<UUID, AuthorInfo> resolveAuthors(List<Comment> comments) {
    var ids =
        comments.stream()
            .map(Comment::getAuthorMemberId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> new AuthorInfo(m.getName(), m.getAvatarUrl()), (a, b) -> a));
  }

  // --- DTOs ---

  record AuthorInfo(String name, String avatarUrl) {}

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

    public static CommentResponse from(Comment comment, Map<UUID, AuthorInfo> authors) {
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
