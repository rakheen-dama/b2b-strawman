package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.activity.ActivityService;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpActivityItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only MCP tool over a matter's activity timeline: {@code get_matter_activity} (Epic 563B,
 * §11.4).
 *
 * <p>Access model: <b>project-access</b>. The service calls {@code requireViewAccess(projectId,
 * actor)} first → {@link ResourceNotFoundException} (404) for a non-member, surfaced here as a
 * non-leaking {@link McpError}.
 *
 * <p>Pagination: the service paginates at the DB level and returns a {@code Page<ActivityItem>}, so
 * we clamp the requested size (cap 50) into the {@link PageRequest} and build the {@link McpPage}
 * directly from the returned page — we do NOT re-slice an unbounded list.
 */
@Component
public class ActivityTools {

  private final ActivityService activityService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public ActivityTools(
      ActivityService activityService, AuditService auditService, ObjectMapper objectMapper) {
    this.activityService = activityService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @McpTool(
      name = "get_matter_activity",
      description =
          "List the activity timeline for one matter (project), newest first. Project-access is"
              + " enforced — a non-member receives a non-leaking not-found error. Paginated — page"
              + " size capped at 50.")
  public Object getMatterActivity(
      @McpToolParam(description = "Matter (project) id.") UUID projectId,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).") int page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          int size) {
    var actor = ActorContext.fromRequestScopes();
    int clampedSize = McpPagination.clampSize(size, McpPagination.DEFAULT_MAX_SIZE);
    int clampedPage = Math.max(page, 0);
    try {
      var activityPage =
          activityService.getProjectActivity(
              projectId, null, null, PageRequest.of(clampedPage, clampedSize), actor);
      var items = activityPage.getContent().stream().map(McpActivityItem::from).toList();
      var result =
          McpPage.of(
              items,
              activityPage.getNumber(),
              activityPage.getSize(),
              activityPage.getTotalElements(),
              activityPage.hasNext());
      emitInvoked("get_matter_activity");
      return result;
    } catch (ResourceNotFoundException e) {
      return McpToolErrors.asResult(McpError.notFound("matter"), objectMapper);
    }
  }

  private void emitInvoked(String tool) {
    try {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("mcp.tool.invoked")
              .entityType("mcp_tool")
              .entityId(RequestScopes.requireMemberId())
              .details(Map.of("tool", tool))
              .build());
    } catch (RuntimeException e) {
      // Audit emission must never break a successful tool call.
    }
  }
}
