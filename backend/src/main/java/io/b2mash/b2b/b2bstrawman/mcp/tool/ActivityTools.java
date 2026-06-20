package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.activity.ActivityService;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpActivityItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
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

  private static final String GATE_PROJECT_ACCESS = "project-access";

  private final ActivityService activityService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public ActivityTools(
      ActivityService activityService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.activityService = activityService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "get_matter_activity",
      description =
          "List the activity timeline for one matter (project), newest first. Project-access is"
              + " enforced — a non-member receives a non-leaking not-found error. Paginated — page"
              + " size capped at 50.")
  public Object getMatterActivity(
      @McpToolParam(description = "Matter (project) id.") UUID projectId,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).")
          Integer page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          Integer size) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
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
      var meta = McpAuditMetadata.builder().rowCount(items.size()).entityRef(projectId).build();
      emitInvoked("get_matter_activity", meta, startNanos);
      return result;
    } catch (ResourceNotFoundException e) {
      // project-access denial: non-member turned away with non-leaking not_found, but audit it.
      McpToolAudit.emitDenied(
          "get_matter_activity",
          GATE_PROJECT_ACCESS,
          auditService,
          metrics,
          McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("matter"), objectMapper);
    }
  }

  private void emitInvoked(String tool, McpAuditMetadata meta, long startNanos) {
    McpToolAudit.emitInvoked(tool, meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
  }
}
