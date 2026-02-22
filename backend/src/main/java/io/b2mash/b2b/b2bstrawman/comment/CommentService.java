package io.b2mash.b2b.b2bstrawman.comment;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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

  private final CommentRepository commentRepository;
  private final ProjectAccessService projectAccessService;
  private final TaskRepository taskRepository;
  private final DocumentRepository documentRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;

  public CommentService(
      CommentRepository commentRepository,
      ProjectAccessService projectAccessService,
      TaskRepository taskRepository,
      DocumentRepository documentRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver) {
    this.commentRepository = commentRepository;
    this.projectAccessService = projectAccessService;
    this.taskRepository = taskRepository;
    this.documentRepository = documentRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
  }

  @Transactional
  public Comment createComment(
      UUID projectId,
      String entityType,
      UUID entityId,
      String body,
      String visibility,
      UUID memberId,
      String orgRole) {
    var access = projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    // Validate entity type
    if (!"TASK".equals(entityType) && !"DOCUMENT".equals(entityType)) {
      throw new InvalidStateException("Invalid entity type", "entityType must be TASK or DOCUMENT");
    }

    // Verify entity exists and belongs to project
    validateEntityBelongsToProject(entityType, entityId, projectId);

    // SHARED visibility requires canEdit (lead/admin/owner)
    String resolvedVisibility = visibility != null ? visibility : "INTERNAL";
    if ("SHARED".equals(resolvedVisibility) && !access.canEdit()) {
      throw new ForbiddenException(
          "Cannot create shared comment",
          "Only leads, admins, and owners can create SHARED comments");
    }

    var comment = new Comment(entityType, entityId, projectId, memberId, body, resolvedVisibility);
    comment = commentRepository.save(comment);
    log.info(
        "Created comment {} on {} {} in project {}",
        comment.getId(),
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
                    "visibility", resolvedVisibility))
            .build());

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CommentCreatedEvent(
            "comment.created",
            "comment",
            comment.getId(),
            projectId,
            memberId,
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
      UUID projectId,
      UUID commentId,
      String body,
      String visibility,
      UUID memberId,
      String orgRole) {
    var comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

    if (!comment.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Comment", commentId);
    }

    var access = projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    boolean isAuthor = comment.getAuthorMemberId().equals(memberId);
    boolean isOrgAdminOrOwner = Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole);

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

    // Update fields
    String oldBody = comment.getBody();
    if (body != null) {
      comment.updateBody(body);
    }
    if (visibilityChanging) {
      comment.updateVisibility(visibility);
    }
    comment = commentRepository.save(comment);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("comment.updated")
            .entityType("comment")
            .entityId(comment.getId())
            .details(
                Map.of(
                    "old_body",
                    oldBody,
                    "new_body",
                    body != null ? body : oldBody,
                    "project_id",
                    projectId.toString()))
            .build());

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CommentUpdatedEvent(
            "comment.updated",
            "comment",
            comment.getId(),
            projectId,
            memberId,
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
              memberId,
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
  public void deleteComment(UUID projectId, UUID commentId, UUID memberId, String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

    if (!comment.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Comment", commentId);
    }

    boolean isAuthor = comment.getAuthorMemberId().equals(memberId);
    boolean isOrgAdminOrOwner = Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole);

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

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CommentDeletedEvent(
            "comment.deleted",
            "comment",
            commentId,
            projectId,
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("body", deletedBody),
            entityType,
            entityId));
  }

  @Transactional(readOnly = true)
  public Page<Comment> listComments(
      UUID projectId,
      String entityType,
      UUID entityId,
      Pageable pageable,
      UUID memberId,
      String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    return commentRepository.findByTargetAndProject(entityType, entityId, projectId, pageable);
  }

  private void validateEntityBelongsToProject(String entityType, UUID entityId, UUID projectId) {
    if ("TASK".equals(entityType)) {
      var task =
          taskRepository
              .findById(entityId)
              .orElseThrow(() -> new ResourceNotFoundException("Task", entityId));
      if (!task.getProjectId().equals(projectId)) {
        throw new ResourceNotFoundException("Task", entityId);
      }
    } else if ("DOCUMENT".equals(entityType)) {
      var document =
          documentRepository
              .findById(entityId)
              .orElseThrow(() -> new ResourceNotFoundException("Document", entityId));
      if (!document.getProjectId().equals(projectId)) {
        throw new ResourceNotFoundException("Document", entityId);
      }
    }
  }

  private String resolveActorName(UUID memberId) {
    return memberNameResolver.resolveName(memberId);
  }
}
