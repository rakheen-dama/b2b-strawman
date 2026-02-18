package io.b2mash.b2b.b2bstrawman.view;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedViewService {

  private static final Logger log = LoggerFactory.getLogger(SavedViewService.class);

  private final SavedViewRepository savedViewRepository;
  private final AuditService auditService;

  public SavedViewService(SavedViewRepository savedViewRepository, AuditService auditService) {
    this.savedViewRepository = savedViewRepository;
    this.auditService = auditService;
  }

  /**
   * Lists all views visible to the given member: shared views + member's personal views. Uses a
   * single query with OR condition — no deduplication needed.
   */
  @Transactional(readOnly = true)
  public List<SavedViewResponse> listViews(String entityType, UUID memberId) {
    return savedViewRepository.findVisibleViews(entityType, memberId).stream()
        .map(SavedViewResponse::from)
        .toList();
  }

  @Transactional
  public SavedViewResponse create(CreateSavedViewRequest req) {
    UUID memberId = RequestScopes.requireMemberId();

    if (req.shared() && !isAdminOrOwner()) {
      throw new ForbiddenException(
          "Shared view creation", "Only admin/owner can create shared views");
    }

    // Check for duplicate names
    if (req.shared()) {
      savedViewRepository
          .findSharedByEntityTypeAndName(req.entityType(), req.name())
          .ifPresent(
              existing -> {
                throw new ResourceConflictException(
                    "Duplicate name",
                    "A shared view named '"
                        + req.name()
                        + "' already exists for "
                        + req.entityType());
              });
    } else {
      var personal =
          savedViewRepository.findByEntityTypeAndCreatedByOrderBySortOrder(
              req.entityType(), memberId);
      boolean duplicate = personal.stream().anyMatch(v -> v.getName().equals(req.name()));
      if (duplicate) {
        throw new ResourceConflictException(
            "Duplicate name",
            "You already have a personal view named '" + req.name() + "' for " + req.entityType());
      }
    }

    var view =
        new SavedView(
            req.entityType(),
            req.name(),
            req.filters(),
            req.columns(),
            req.shared(),
            memberId,
            req.sortOrder());

    view = savedViewRepository.save(view);

    log.info(
        "Created saved view: id={}, name={}, entityType={}, shared={}",
        view.getId(),
        view.getName(),
        view.getEntityType(),
        view.isShared());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("view.created")
            .entityType("saved_view")
            .entityId(view.getId())
            .details(
                Map.of(
                    "name", view.getName(),
                    "entityType", view.getEntityType(),
                    "shared", view.isShared()))
            .build());

    return SavedViewResponse.from(view);
  }

  @Transactional
  public SavedViewResponse update(UUID id, UpdateSavedViewRequest req) {
    var view =
        savedViewRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SavedView", id));

    UUID memberId = RequestScopes.requireMemberId();

    if (!view.getCreatedBy().equals(memberId) && !isAdminOrOwner()) {
      throw new ForbiddenException(
          "View update denied", "Only the creator or admin/owner can update this view");
    }

    view.updateFilters(req.name(), req.filters(), req.columns(), req.sortOrder());
    view = savedViewRepository.save(view);

    log.info("Updated saved view: id={}, name={}", view.getId(), view.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("view.updated")
            .entityType("saved_view")
            .entityId(view.getId())
            .details(
                Map.of(
                    "name", view.getName(),
                    "entityType", view.getEntityType(),
                    "shared", view.isShared()))
            .build());

    return SavedViewResponse.from(view);
  }

  @Transactional
  public void delete(UUID id) {
    var view =
        savedViewRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SavedView", id));

    UUID memberId = RequestScopes.requireMemberId();

    if (!view.getCreatedBy().equals(memberId) && !isAdminOrOwner()) {
      throw new ForbiddenException(
          "View deletion denied", "Only the creator or admin/owner can delete this view");
    }

    savedViewRepository.delete(view);

    log.info("Deleted saved view: id={}, name={}", view.getId(), view.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("view.deleted")
            .entityType("saved_view")
            .entityId(view.getId())
            .details(
                Map.of(
                    "name", view.getName(),
                    "entityType", view.getEntityType(),
                    "shared", view.isShared()))
            .build());
  }

  private boolean isAdminOrOwner() {
    String role = RequestScopes.getOrgRole();
    return "admin".equals(role) || "owner".equals(role);
  }
}
