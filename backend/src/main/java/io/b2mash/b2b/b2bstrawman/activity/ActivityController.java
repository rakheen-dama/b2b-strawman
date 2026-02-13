package io.b2mash.b2b.b2bstrawman.activity;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for the project activity feed. */
@RestController
@RequestMapping("/api/projects/{projectId}/activity")
public class ActivityController {

  private final ActivityService activityService;

  public ActivityController(ActivityService activityService) {
    this.activityService = activityService;
  }

  /**
   * Returns a paginated activity feed for the given project.
   *
   * @param projectId the project ID
   * @param page zero-based page number (default 0)
   * @param size page size (default 20, max 50)
   * @param entityType optional filter by entity type (TASK, DOCUMENT, COMMENT, PROJECT_MEMBER,
   *     TIME_ENTRY)
   * @param since optional filter -- only events after this ISO 8601 timestamp
   * @return a page of activity items ordered by occurredAt DESC
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<ActivityItem>> getProjectActivity(
      @PathVariable UUID projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) Instant since) {

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var pageable = PageRequest.of(page, Math.min(size, 50));
    var activity =
        activityService.getProjectActivity(
            projectId, entityType, since, pageable, memberId, orgRole);

    return ResponseEntity.ok(activity);
  }
}
