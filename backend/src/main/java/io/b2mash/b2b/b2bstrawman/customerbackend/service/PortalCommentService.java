package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for creating portal comments. Encapsulates save, read-model sync, audit logging, and
 * event publishing in a single transaction.
 */
@Service
public class PortalCommentService {

  private static final Logger log = LoggerFactory.getLogger(PortalCommentService.class);

  private final CommentRepository commentRepository;
  private final PortalReadModelRepository readModelRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public PortalCommentService(
      CommentRepository commentRepository,
      PortalReadModelRepository readModelRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.commentRepository = commentRepository;
    this.readModelRepository = readModelRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Creates a portal comment, syncs to read-model, logs audit event, and publishes domain event.
   * Must be called within a tenant-scoped context (TENANT_ID bound by CustomerAuthFilter).
   */
  @Transactional
  public Comment createPortalComment(
      UUID projectId, UUID authorId, String authorName, String content, String orgId) {
    // Create and save comment
    var comment = new Comment("PROJECT", projectId, projectId, authorId, content, "SHARED");
    comment = commentRepository.save(comment);
    log.info("Created portal comment {} on project {}", comment.getId(), projectId);

    // Sync to portal read-model
    readModelRepository.upsertPortalComment(
        comment.getId(), orgId, projectId, authorName, content, comment.getCreatedAt());

    // Audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("comment.created")
            .entityType("comment")
            .entityId(comment.getId())
            .actorId(authorId)
            .actorType("PORTAL_USER")
            .source("PORTAL")
            .details(
                Map.of(
                    "body",
                    content,
                    "project_id",
                    projectId.toString(),
                    "entity_type",
                    "PROJECT",
                    "entity_id",
                    projectId.toString(),
                    "visibility",
                    "SHARED",
                    "source",
                    "PORTAL",
                    "actor_name",
                    authorName))
            .build());

    // Publish domain event for notifications
    String tenantId = RequestScopes.getTenantIdOrNull();
    eventPublisher.publishEvent(
        new CommentCreatedEvent(
            "comment.created",
            "comment",
            comment.getId(),
            projectId,
            authorId,
            authorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("body", content, "source", "PORTAL"),
            "PROJECT",
            projectId,
            "SHARED"));

    return comment;
  }
}
