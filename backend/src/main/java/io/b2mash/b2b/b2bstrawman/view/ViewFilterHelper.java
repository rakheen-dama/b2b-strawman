package io.b2mash.b2b.b2bstrawman.view;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class ViewFilterHelper {

  private final SavedViewRepository savedViewRepository;
  private final ViewFilterService viewFilterService;

  public ViewFilterHelper(
      SavedViewRepository savedViewRepository, ViewFilterService viewFilterService) {
    this.savedViewRepository = savedViewRepository;
    this.viewFilterService = viewFilterService;
  }

  /**
   * Resolve a SavedView, validate its entity type, execute the filter query, and optionally apply
   * access control for non-admin users.
   *
   * @param accessibleIds if non-null, only entities whose ID is in this set are returned (used for
   *     member-level access control on projects). Pass null to skip access filtering.
   * @param idExtractor extracts the entity ID for access control filtering. Ignored if
   *     accessibleIds is null.
   * @return the filtered entity list, or null if the view produced no WHERE clause (caller should
   *     fall back to default listing logic)
   */
  public <T> List<T> applyViewFilter(
      UUID viewId,
      String entityType,
      String tableName,
      Class<T> entityClass,
      Set<UUID> accessibleIds,
      Function<T, UUID> idExtractor) {

    var savedView = resolveAndValidate(viewId, entityType);

    List<T> filtered =
        viewFilterService.executeFilterQuery(
            tableName, entityClass, savedView.getFilters(), entityType);

    if (filtered == null) {
      return null;
    }

    if (accessibleIds != null) {
      filtered =
          filtered.stream().filter(e -> accessibleIds.contains(idExtractor.apply(e))).toList();
    }

    return filtered;
  }

  /**
   * Resolve a SavedView, validate its entity type, and execute the filter query scoped to a
   * specific project.
   *
   * @return the filtered entity list, or null if the view produced no WHERE clause
   */
  public <T> List<T> applyViewFilterForProject(
      UUID viewId, String entityType, String tableName, Class<T> entityClass, UUID projectId) {

    var savedView = resolveAndValidate(viewId, entityType);

    return viewFilterService.executeFilterQueryForProject(
        tableName, entityClass, savedView.getFilters(), entityType, projectId);
  }

  private SavedView resolveAndValidate(UUID viewId, String entityType) {
    var savedView =
        savedViewRepository
            .findById(viewId)
            .orElseThrow(() -> new ResourceNotFoundException("SavedView", viewId));

    if (!entityType.equals(savedView.getEntityType())) {
      throw new InvalidStateException(
          "View type mismatch",
          "Expected " + entityType + " view but got " + savedView.getEntityType());
    }

    return savedView;
  }
}
