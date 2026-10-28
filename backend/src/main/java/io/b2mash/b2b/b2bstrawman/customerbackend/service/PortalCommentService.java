package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentAttachmentRepository;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PortalCommentController.PortalCommentAttachmentResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PortalCommentController.PortalCommentResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalCommentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for portal comments. Creating encapsulates save, read-model sync, audit logging, and
 * event publishing in a single transaction; listing enriches the read model with attachment chips.
 */
@Service
public class PortalCommentService {

  private static final Logger log = LoggerFactory.getLogger(PortalCommentService.class);

  private final CommentRepository commentRepository;
  private final PortalReadModelRepository readModelRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final PortalContactRepository portalContactRepository;
  private final ProjectRepository projectRepository;
  private final PortalReadModelService portalReadModelService;
  private final CommentAttachmentRepository commentAttachmentRepository;

  public PortalCommentService(
      CommentRepository commentRepository,
      PortalReadModelRepository readModelRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      PortalContactRepository portalContactRepository,
      ProjectRepository projectRepository,
      PortalReadModelService portalReadModelService,
      CommentAttachmentRepository commentAttachmentRepository) {
    this.commentRepository = commentRepository;
    this.readModelRepository = readModelRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.portalContactRepository = portalContactRepository;
    this.projectRepository = projectRepository;
    this.portalReadModelService = portalReadModelService;
    this.commentAttachmentRepository = commentAttachmentRepository;
  }

  /**
   * Lists the matter's portal comments (the read model holds SHARED comments only) with attachment
   * chips for the documents the client may see by the existing portal rule (as in {@code
   * PortalQueryService}): portal-visible (SHARED or PORTAL), UPLOADED, and PROJECT-scoped on this
   * matter. Chips for a whole page come from one query. TENANT_ID is bound by CustomerAuthFilter,
   * so the tenant tables are readable here. Throws 404 if the matter is not linked to the customer.
   */
  @Transactional(readOnly = true)
  public List<PortalCommentResponse> listProjectComments(
      UUID projectId, UUID customerId, String orgId) {
    var comments = portalReadModelService.listProjectComments(projectId, customerId, orgId);
    if (comments.isEmpty()) {
      return List.of();
    }
    var chips = new HashMap<UUID, List<PortalCommentAttachmentResponse>>();
    for (var chip :
        commentAttachmentRepository.findChipsByCommentIds(
            comments.stream().map(PortalCommentView::id).toList())) {
      boolean clientVisible =
          Document.Visibility.isPortalVisible(chip.getVisibility())
              && chip.getStatus() == Document.Status.UPLOADED
              && Document.Scope.PROJECT.equals(chip.getScope())
              && projectId.equals(chip.getProjectId());
      if (clientVisible) {
        chips
            .computeIfAbsent(chip.getCommentId(), k -> new ArrayList<>())
            .add(
                new PortalCommentAttachmentResponse(
                    chip.getDocumentId(),
                    chip.getFileName(),
                    chip.getContentType(),
                    chip.getFileSize()));
      }
    }
    return comments.stream()
        .map(
            c ->
                new PortalCommentResponse(
                    c.id(),
                    c.authorName(),
                    c.content(),
                    c.createdAt(),
                    chips.getOrDefault(c.id(), List.of())))
        .toList();
  }

  /**
   * Creates a portal comment, syncs to read-model, logs audit event, and publishes domain event.
   * Must be called within a tenant-scoped context (TENANT_ID bound by CustomerAuthFilter).
   */
  @Transactional
  public Comment createPortalComment(
      UUID projectId, UUID authorId, String authorName, String content, String orgId) {
    // Create and save comment
    var comment =
        new Comment("PROJECT", projectId, projectId, authorId, content, "SHARED", "PORTAL");
    comment = commentRepository.save(comment);
    log.info("Created portal comment {} on project {}", comment.getId(), projectId);

    // Sync to portal read-model
    readModelRepository.upsertPortalComment(
        comment.getId(), orgId, projectId, authorName, content, comment.getCreatedAt());

    // Resolve project name for activity feed
    String projectName =
        projectRepository.findById(projectId).map(p -> p.getName()).orElse("project");

    // Audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("comment.created")
            .entityType("comment")
            .entityId(comment.getId())
            .actorId(authorId)
            .actorType("PORTAL_CONTACT")
            .source("PORTAL")
            .details(
                Map.ofEntries(
                    Map.entry("body", content),
                    Map.entry("project_id", projectId.toString()),
                    Map.entry("entity_type", "PROJECT"),
                    Map.entry("entity_id", projectId.toString()),
                    Map.entry("entity_name", projectName),
                    Map.entry("visibility", "SHARED"),
                    Map.entry("source", "PORTAL"),
                    Map.entry("actor_name", authorName)))
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

  /** Resolves the display name for a portal contact. Returns "Portal User" if not found. */
  public String resolveContactDisplayName(UUID contactId) {
    var contact = portalContactRepository.findById(contactId).orElse(null);
    if (contact != null && contact.getDisplayName() != null) {
      return contact.getDisplayName();
    }
    return "Portal User";
  }
}
