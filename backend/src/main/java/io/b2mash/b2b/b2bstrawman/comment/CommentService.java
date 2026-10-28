package io.b2mash.b2b.b2bstrawman.comment;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentController.CommentAttachmentResponse;
import io.b2mash.b2b.b2bstrawman.comment.CommentController.CommentResponse;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccess;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

  private static final Logger log = LoggerFactory.getLogger(CommentService.class);

  /** At most this many documents may be attached to one comment (Phase 88 U24, A13). */
  static final int MAX_ATTACHMENTS = 5;

  private final CommentRepository commentRepository;
  private final ProjectAccessService projectAccessService;
  private final TaskRepository taskRepository;
  private final DocumentRepository documentRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;
  private final io.b2mash.b2b.b2bstrawman.member.MemberRepository memberRepository;
  private final ProjectLifecycleGuard projectLifecycleGuard;
  private final CommentAttachmentRepository commentAttachmentRepository;
  private final DocumentService documentService;

  public CommentService(
      CommentRepository commentRepository,
      ProjectAccessService projectAccessService,
      TaskRepository taskRepository,
      DocumentRepository documentRepository,
      ProjectRepository projectRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      io.b2mash.b2b.b2bstrawman.member.MemberRepository memberRepository,
      ProjectLifecycleGuard projectLifecycleGuard,
      CommentAttachmentRepository commentAttachmentRepository,
      DocumentService documentService) {
    this.commentRepository = commentRepository;
    this.projectAccessService = projectAccessService;
    this.taskRepository = taskRepository;
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.memberRepository = memberRepository;
    this.projectLifecycleGuard = projectLifecycleGuard;
    this.commentAttachmentRepository = commentAttachmentRepository;
    this.documentService = documentService;
  }

  /**
   * Creates a comment with its attachments and returns the response DTO. Thin-controller delegation
   * wrapper.
   */
  @Transactional
  public CommentResponse createCommentResponse(
      UUID projectId,
      String entityType,
      UUID entityId,
      String body,
      String visibility,
      List<UUID> attachmentDocumentIds,
      ActorContext actor) {
    var comment =
        createComment(
            projectId,
            entityType,
            entityId,
            body,
            visibility,
            "INTERNAL",
            attachmentDocumentIds,
            actor);
    return toResponses(List.of(comment)).getFirst();
  }

  /**
   * Updates a comment (and, when {@code attachmentDocumentIds} is non-null, replaces its
   * attachments) and returns the response DTO. Thin-controller delegation wrapper.
   */
  @Transactional
  public CommentResponse updateCommentResponse(
      UUID projectId,
      UUID commentId,
      String body,
      String visibility,
      List<UUID> attachmentDocumentIds,
      ActorContext actor) {
    var comment =
        updateComment(projectId, commentId, body, visibility, attachmentDocumentIds, actor);
    return toResponses(List.of(comment)).getFirst();
  }

  /** Lists comments with authors and attachments resolved. Thin-controller delegation wrapper. */
  @Transactional(readOnly = true)
  public List<CommentResponse> listCommentResponses(
      UUID projectId, String entityType, UUID entityId, Pageable pageable, ActorContext actor) {
    return toResponses(listComments(projectId, entityType, entityId, pageable, actor).getContent());
  }

  /** Creates a comment with the default source ("INTERNAL"). Delegates to the 7-arg overload. */
  @Transactional
  public Comment createComment(
      UUID projectId,
      String entityType,
      UUID entityId,
      String body,
      String visibility,
      ActorContext actor) {
    return createComment(projectId, entityType, entityId, body, visibility, "INTERNAL", actor);
  }

  /**
   * Creates a comment with an explicit (non-default) source. Delegates to the same validation and
   * audit logic as the standard {@link #createComment} but uses the 7-arg Comment constructor to
   * set the source field.
   */
  @Transactional
  public Comment createComment(
      UUID projectId,
      String entityType,
      UUID entityId,
      String body,
      String visibility,
      String source,
      ActorContext actor) {
    return createComment(projectId, entityType, entityId, body, visibility, source, null, actor);
  }

  private Comment createComment(
      UUID projectId,
      String entityType,
      UUID entityId,
      String body,
      String visibility,
      String source,
      List<UUID> attachmentDocumentIds,
      ActorContext actor) {
    var access = projectAccessService.requireViewAccess(projectId, actor);

    // Check project is not archived
    projectLifecycleGuard.requireNotReadOnly(projectId);

    // Validate entity type
    if ("PROJECT".equals(entityType)) {
      String resolvedVis = visibility != null ? visibility : "INTERNAL";
      if (!"SHARED".equals(resolvedVis)) {
        throw new InvalidStateException(
            "Invalid visibility for project comment",
            "PROJECT-level comments must have SHARED visibility");
      }
      if (!entityId.equals(projectId)) {
        throw new InvalidStateException(
            "Invalid entity for project comment",
            "entityId must match projectId for PROJECT-level comments");
      }
    } else if (!"TASK".equals(entityType) && !"DOCUMENT".equals(entityType)) {
      throw new InvalidStateException(
          "Invalid entity type", "entityType must be TASK, DOCUMENT, or PROJECT");
    }

    String entityName = validateEntityBelongsToProject(entityType, entityId, projectId);

    String resolvedVisibility = visibility != null ? visibility : "INTERNAL";
    if ("SHARED".equals(resolvedVisibility) && !access.canEdit() && !"PROJECT".equals(entityType)) {
      throw new ForbiddenException(
          "Cannot create shared comment",
          "Only leads, admins, and owners can create SHARED comments");
    }

    var attachments =
        validateAttachments(
            attachmentDocumentIds, projectId, Set.of(), resolvedVisibility, access, actor);

    var comment =
        new Comment(
            entityType, entityId, projectId, actor.memberId(), body, resolvedVisibility, source);
    comment = commentRepository.save(comment);
    for (int i = 0; i < attachments.size(); i++) {
      commentAttachmentRepository.save(
          new CommentAttachment(comment.getId(), attachments.get(i).getId(), i));
    }
    applyAttachmentRule(attachments, resolvedVisibility);
    log.info(
        "Created comment {} (source={}) on {} {} in project {}",
        comment.getId(),
        source,
        entityType,
        entityId,
        projectId);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("comment.created")
            .entityType("comment")
            .entityId(comment.getId())
            .details(
                Map.of(
                    "body", body,
                    "project_id", projectId.toString(),
                    "entity_type", entityType,
                    "entity_id", entityId.toString(),
                    "entity_name", entityName,
                    "visibility", resolvedVisibility,
                    "source", source,
                    "attachment_count", attachments.size()))
            .build());

    String actorName = resolveActorName(actor.memberId());
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CommentCreatedEvent(
            "comment.created",
            "comment",
            comment.getId(),
            projectId,
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("body", body),
            entityType,
            entityId,
            resolvedVisibility));

    return comment;
  }

  @Transactional
  public Comment updateComment(
      UUID projectId, UUID commentId, String body, String visibility, ActorContext actor) {
    return updateComment(projectId, commentId, body, visibility, null, actor);
  }

  /**
   * Updates a comment. {@code attachmentDocumentIds}: {@code null} leaves the attachments as they
   * are; a list replaces them in that order ({@code []} clears). Removed documents are left as they
   * were. When the attachments or the comment's visibility change, the visibility rule is
   * re-applied to every attached document.
   */
  private Comment updateComment(
      UUID projectId,
      UUID commentId,
      String body,
      String visibility,
      List<UUID> attachmentDocumentIds,
      ActorContext actor) {
    var comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

    if (!comment.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Comment", commentId);
    }

    var access = projectAccessService.requireViewAccess(projectId, actor);

    // Check project is not archived
    projectLifecycleGuard.requireNotReadOnly(projectId);

    boolean isAuthor = comment.getAuthorMemberId().equals(actor.memberId());
    boolean isOrgAdminOrOwner =
        Roles.ORG_ADMIN.equals(actor.orgRole()) || Roles.ORG_OWNER.equals(actor.orgRole());

    // Authorization: editing others' comments requires admin/owner
    if (!isAuthor && !isOrgAdminOrOwner) {
      throw new ForbiddenException(
          "Cannot update comment", "You do not have permission to update comment " + commentId);
    }

    // Validate visibility value if provided
    if (visibility != null && !"INTERNAL".equals(visibility) && !"SHARED".equals(visibility)) {
      throw new InvalidStateException(
          "Invalid visibility", "visibility must be INTERNAL or SHARED");
    }

    // Visibility changes require canEdit (lead/admin/owner) for own comments,
    // or admin/owner for others' comments (already checked above)
    String oldVisibility = comment.getVisibility();
    boolean visibilityChanging = visibility != null && !visibility.equals(oldVisibility);
    if (visibilityChanging && isAuthor && !access.canEdit()) {
      throw new ForbiddenException(
          "Cannot change comment visibility",
          "Only leads, admins, and owners can change comment visibility");
    }

    String finalVisibility = visibilityChanging ? visibility : oldVisibility;
    var existingLinks = commentAttachmentRepository.findByCommentIdOrderBySortOrderAsc(commentId);
    var existingIds = existingLinks.stream().map(CommentAttachment::getDocumentId).toList();
    List<Document> requested =
        attachmentDocumentIds == null
            ? null
            : validateAttachments(
                attachmentDocumentIds,
                projectId,
                Set.copyOf(existingIds),
                finalVisibility,
                access,
                actor);

    // Update fields
    String oldBody = comment.getBody();
    if (body != null) {
      comment.updateBody(body);
    }
    if (visibilityChanging) {
      comment.updateVisibility(visibility);
    }
    comment = commentRepository.save(comment);

    boolean attachmentsChanged =
        requested != null && replaceAttachments(commentId, existingLinks, requested);
    if (attachmentsChanged) {
      applyAttachmentRule(requested, finalVisibility);
    } else if (visibilityChanging && !existingIds.isEmpty()) {
      applyAttachmentRule(documentRepository.findAllById(existingIds), finalVisibility);
    }

    var updateDetails = new HashMap<String, Object>();
    updateDetails.put("old_body", oldBody);
    updateDetails.put("new_body", body != null ? body : oldBody);
    updateDetails.put("project_id", projectId.toString());
    if (attachmentsChanged) {
      updateDetails.put(
          "attachment_document_ids", requested.stream().map(d -> d.getId().toString()).toList());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("comment.updated")
            .entityType("comment")
            .entityId(comment.getId())
            .details(updateDetails)
            .build());

    String actorName = resolveActorName(actor.memberId());
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CommentUpdatedEvent(
            "comment.updated",
            "comment",
            comment.getId(),
            projectId,
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("old_body", oldBody, "new_body", body != null ? body : oldBody)));

    if (visibilityChanging) {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("comment.visibility_changed")
              .entityType("comment")
              .entityId(comment.getId())
              .details(
                  Map.of(
                      "old_visibility", oldVisibility,
                      "new_visibility", visibility,
                      "project_id", projectId.toString()))
              .build());

      eventPublisher.publishEvent(
          new CommentVisibilityChangedEvent(
              "comment.visibility_changed",
              "comment",
              comment.getId(),
              projectId,
              actor.memberId(),
              actorName,
              tenantId,
              orgId,
              Instant.now(),
              Map.of("old_visibility", oldVisibility, "new_visibility", visibility),
              oldVisibility,
              visibility));
    }

    return comment;
  }

  @Transactional
  public void deleteComment(UUID projectId, UUID commentId, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);

    var comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

    if (!comment.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Comment", commentId);
    }

    boolean isAuthor = comment.getAuthorMemberId().equals(actor.memberId());
    boolean isOrgAdminOrOwner =
        Roles.ORG_ADMIN.equals(actor.orgRole()) || Roles.ORG_OWNER.equals(actor.orgRole());

    if (!isAuthor && !isOrgAdminOrOwner) {
      throw new ForbiddenException(
          "Cannot delete comment", "You do not have permission to delete comment " + commentId);
    }

    String deletedBody = comment.getBody();
    String entityType = comment.getEntityType();
    UUID entityId = comment.getEntityId();

    commentRepository.delete(comment);
    log.info("Deleted comment {} from project {}", commentId, projectId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("comment.deleted")
            .entityType("comment")
            .entityId(commentId)
            .details(
                Map.of(
                    "body",
                    deletedBody,
                    "project_id",
                    projectId.toString(),
                    "entity_type",
                    entityType,
                    "entity_id",
                    entityId.toString()))
            .build());

    String actorName = resolveActorName(actor.memberId());
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CommentDeletedEvent(
            "comment.deleted",
            "comment",
            commentId,
            projectId,
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("body", deletedBody),
            entityType,
            entityId));
  }

  /**
   * Lists project-level comments created at or after {@code from}, bounded by pageable size.
   * Enforces project view access.
   */
  @Transactional(readOnly = true)
  public java.util.List<Comment> listByProjectAndWindow(
      UUID projectId, java.time.Instant from, Pageable pageable, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);
    return commentRepository.findByTargetAndProjectAndCreatedAtAfter(
        "PROJECT", projectId, projectId, from, pageable);
  }

  @Transactional(readOnly = true)
  public Page<Comment> listComments(
      UUID projectId, String entityType, UUID entityId, Pageable pageable, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);

    if ("PROJECT".equals(entityType)) {
      return commentRepository.findProjectLevelComments(projectId, pageable);
    }

    if (entityId == null) {
      throw new InvalidStateException(
          "entityId is required", "entityId is required for " + entityType + " comments");
    }
    return commentRepository.findByTargetAndProject(entityType, entityId, projectId, pageable);
  }

  // --- Attachments (Phase 88 U24, A13) ---

  /**
   * Validates the requested attachment ids and returns their documents in request order. Duplicate
   * ids are dropped silently (first position wins). Documents already attached to this comment
   * ({@code alreadyAttached}) skip the attach-time checks so an edit can resend the full list.
   */
  private List<Document> validateAttachments(
      List<UUID> requestedIds,
      UUID projectId,
      Set<UUID> alreadyAttached,
      String commentVisibility,
      ProjectAccess access,
      ActorContext actor) {
    if (requestedIds == null || requestedIds.isEmpty()) {
      return List.of();
    }
    if (requestedIds.contains(null)) {
      throw new CommentAttachmentNotAllowedException("attachmentDocumentIds must not contain null");
    }
    var ids = List.copyOf(new LinkedHashSet<>(requestedIds));
    if (ids.size() > MAX_ATTACHMENTS) {
      throw new CommentAttachmentNotAllowedException(
          "A comment can have at most %d attachments (got %d)"
              .formatted(MAX_ATTACHMENTS, ids.size()));
    }
    Map<UUID, Document> found =
        documentRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Document::getId, Function.identity()));
    var documents = new ArrayList<Document>(ids.size());
    for (UUID id : ids) {
      var document = found.get(id);
      // Missing, other-matter and non-matter documents share one detail: no existence oracle.
      if (document == null
          || !document.isProjectScoped()
          || !projectId.equals(document.getProjectId())) {
        throw new CommentAttachmentNotAllowedException(
            "Document %s is not a document on this matter".formatted(id), id);
      }
      if (document.getStatus() != Document.Status.UPLOADED) {
        throw new CommentAttachmentNotAllowedException(
            "Document %s has not finished uploading".formatted(id), id);
      }
      if (!alreadyAttached.contains(id)) {
        requireAttachable(document, commentVisibility, access, actor);
      }
      documents.add(document);
    }
    return documents;
  }

  /** Attach-time checks for a document not yet on this comment. */
  private void requireAttachable(
      Document document, String commentVisibility, ProjectAccess access, ActorContext actor) {
    UUID id = document.getId();
    if (!actor.memberId().equals(document.getUploadedBy()) && !access.canEdit()) {
      throw new CommentAttachmentNotAllowedException(
          "Only the uploader or a matter lead can attach document %s".formatted(id), id);
    }
    // A document belongs to one comment, so a later comment visibility change is unambiguous.
    if (commentAttachmentRepository.existsByDocumentId(id)) {
      throw new CommentAttachmentNotAllowedException(
          "Document %s is already attached to another comment".formatted(id), id);
    }
    // Attaching never silently unshares something the client can already see.
    if (!"SHARED".equals(commentVisibility)
        && Document.Visibility.isPortalVisible(document.getVisibility())) {
      throw new CommentAttachmentNotAllowedException(
          "Document %s is shared with the client; attach it to a shared comment or unshare it first"
              .formatted(id),
          id);
    }
  }

  /**
   * Replaces the comment's links with {@code documents} in that order: removes dropped links,
   * renumbers kept ones and inserts new ones. Returns whether the ordered set changed.
   */
  private boolean replaceAttachments(
      UUID commentId, List<CommentAttachment> existingLinks, List<Document> documents) {
    var newIds = documents.stream().map(Document::getId).toList();
    var existingIds = existingLinks.stream().map(CommentAttachment::getDocumentId).toList();
    if (newIds.equals(existingIds)) {
      return false;
    }
    var existingById =
        existingLinks.stream()
            .collect(Collectors.toMap(CommentAttachment::getDocumentId, Function.identity()));
    commentAttachmentRepository.deleteAll(
        existingLinks.stream().filter(l -> !newIds.contains(l.getDocumentId())).toList());
    for (int i = 0; i < newIds.size(); i++) {
      var kept = existingById.get(newIds.get(i));
      if (kept != null) {
        kept.setSortOrder(i);
      } else {
        commentAttachmentRepository.save(new CommentAttachment(commentId, newIds.get(i), i));
      }
    }
    return true;
  }

  /**
   * The comment-attachment visibility rule, in one place. A PORTAL document (a generated artefact
   * shared through its own flow) is never rewritten; otherwise the document follows the comment:
   * SHARED comment → SHARED, INTERNAL comment → INTERNAL. Every document is also stamped source
   * COMMENT when it is a plain MANUAL upload.
   */
  private void applyAttachmentRule(List<Document> documents, String commentVisibility) {
    for (var document : documents) {
      String target;
      if (Document.Visibility.PORTAL.equals(document.getVisibility())) {
        target = Document.Visibility.PORTAL;
      } else if ("SHARED".equals(commentVisibility)) {
        target = Document.Visibility.SHARED;
      } else {
        target = Document.Visibility.INTERNAL;
      }
      documentService.applyCommentAttachment(document.getId(), target);
    }
  }

  /** Maps comments to responses with authors and attachment chips resolved in batch. */
  private List<CommentResponse> toResponses(List<Comment> comments) {
    if (comments.isEmpty()) {
      return List.of();
    }
    var authors = resolveAuthors(comments);
    var chips = new LinkedHashMap<UUID, List<CommentAttachmentResponse>>();
    for (var chip :
        commentAttachmentRepository.findChipsByCommentIds(
            comments.stream().map(Comment::getId).toList())) {
      chips
          .computeIfAbsent(chip.getCommentId(), k -> new ArrayList<>())
          .add(
              new CommentAttachmentResponse(
                  chip.getDocumentId(),
                  chip.getFileName(),
                  chip.getContentType(),
                  chip.getFileSize()));
    }
    return comments.stream()
        .map(c -> CommentResponse.from(c, authors, chips.getOrDefault(c.getId(), List.of())))
        .toList();
  }

  /**
   * Validates that the entity belongs to the given project and returns the entity name. This
   * consolidates validation and name resolution into a single DB read per entity.
   */
  private String validateEntityBelongsToProject(String entityType, UUID entityId, UUID projectId) {
    if ("PROJECT".equals(entityType)) {
      return projectRepository.findById(projectId).map(p -> p.getName()).orElse("project");
    }
    if ("TASK".equals(entityType)) {
      var task =
          taskRepository
              .findById(entityId)
              .orElseThrow(() -> new ResourceNotFoundException("Task", entityId));
      if (!task.getProjectId().equals(projectId)) {
        throw new ResourceNotFoundException("Task", entityId);
      }
      return task.getTitle();
    } else if ("DOCUMENT".equals(entityType)) {
      var document =
          documentRepository
              .findById(entityId)
              .orElseThrow(() -> new ResourceNotFoundException("Document", entityId));
      if (!document.getProjectId().equals(projectId)) {
        throw new ResourceNotFoundException("Document", entityId);
      }
      return document.getFileName();
    }
    return entityType.toLowerCase();
  }

  private String resolveActorName(UUID memberId) {
    return memberNameResolver.resolveName(memberId);
  }

  // --- Author Resolution (moved from controller for BE-007) ---

  /** Info about a comment author for response enrichment. */
  public record AuthorInfo(String name, String avatarUrl) {}

  /** Batch-resolves author information for the given comments. */
  public Map<UUID, AuthorInfo> resolveAuthors(java.util.List<Comment> comments) {
    var ids =
        comments.stream()
            .map(Comment::getAuthorMemberId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberRepository.findAllById(ids).stream()
        .collect(
            java.util.stream.Collectors.toMap(
                io.b2mash.b2b.b2bstrawman.member.Member::getId,
                m -> new AuthorInfo(m.getName(), m.getAvatarUrl()),
                (a, b) -> a));
  }
}
