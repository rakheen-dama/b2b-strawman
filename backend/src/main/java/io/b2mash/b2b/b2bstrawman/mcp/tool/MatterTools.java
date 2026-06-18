package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpMatterDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpMatterListItem;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only MCP tools over the firm's matters (projects): {@code list_matters} and {@code
 * get_matter} (Epic 563A, §11.4).
 *
 * <p>Access model: <b>project-access</b>. {@code list_matters} is access-scoped inside the service
 * (a member sees only matters they are assigned to; owner/admin see all). {@code get_matter} routes
 * through {@code requireViewAccess}, which throws {@link ResourceNotFoundException} (404) for a
 * caller who cannot view the matter — caught here and surfaced as a non-leaking {@link McpError}
 * with the SAME message whether the matter is absent or merely inaccessible
 * (security-by-obscurity).
 *
 * <p>The MCP_ACCESS front-door capability gate is intentionally <b>not</b> applied here — it lands
 * in 565B. Tools rely on the authenticated {@code /mcp} chain plus their own per-domain gates.
 */
@Component
public class MatterTools {

  private static final String GATE_PROJECT_ACCESS = "project-access";

  private final ProjectService projectService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public MatterTools(
      ProjectService projectService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.projectService = projectService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "list_matters",
      description =
          "List the firm's matters (projects) visible to you. Members see only assigned matters;"
              + " owners/admins see all. Optionally filter by status (e.g. ACTIVE) or customerId."
              + " Paginated — page size is capped at 50.")
  public Object listMatters(
      @McpToolParam(required = false, description = "Zero-based page index (default 0).") int page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          int size,
      @McpToolParam(required = false, description = "Filter by short status enum, e.g. ACTIVE.")
          String status,
      @McpToolParam(required = false, description = "Filter to matters linked to this client id.")
          UUID customerId) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    // ALWAYS use the 6-arg filtered overload (the (ActorContext) overload returns a different
    // type). No saved-view (view=null) and no custom-field/tag filtering exposed via MCP
    // (Map.of()).
    var matters =
        projectService.listProjects(null, status, null, customerId, Map.of(), actor).stream()
            .map(
                p ->
                    new McpMatterListItem(
                        p.id(), p.name(), p.status(), p.customerId(), p.dueDate(), p.createdAt()))
            .toList();
    // Guard against an unbounded materialised result set: if the full list exceeds the per-call
    // ceiling, fail with a structured error telling the LLM to narrow its query rather than ever
    // emitting a truncated page.
    if (McpPagination.exceedsResponseCeiling(matters.size())) {
      return McpToolErrors.asResult(McpError.responseTooLarge(), objectMapper);
    }
    var pageResult = McpPagination.paginate(matters, page, size, McpPagination.DEFAULT_MAX_SIZE);
    // entityRefs are the ids on THIS page; params summary carries only the id/enum filters.
    var meta =
        McpAuditMetadata.builder()
            .rowCount(pageResult.items().size())
            .entityRefs(pageResult.items().stream().map(McpMatterListItem::id).toList())
            .param("status", status)
            .param("customerId", customerId)
            .build();
    emitInvoked("list_matters", meta, startNanos);
    return pageResult;
  }

  @McpTool(
      name = "get_matter",
      description =
          "Fetch one matter (project) by id, including your role on it. Returns a non-leaking"
              + " not-found error if the matter does not exist OR you cannot access it.")
  public Object getMatter(@McpToolParam(description = "Matter (project) id.") UUID id) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    try {
      var withRole = projectService.getProject(id, actor);
      var dto = McpMatterDto.from(withRole.project(), withRole.projectRole());
      var meta = McpAuditMetadata.builder().rowCount(1).entityRef(id).build();
      emitInvoked("get_matter", meta, startNanos);
      return dto;
    } catch (ResourceNotFoundException e) {
      // project-access denial: a non-member is turned away with a non-leaking not_found, but this
      // IS a refusal — emit mcp.access.denied (gate=project-access) so the denial is audited and
      // metered as "denied" (567A: refusal-type coverage includes project-access).
      McpToolAudit.emitDenied(
          "get_matter",
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
