package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceService;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.CorrespondenceListResponse;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpCorrespondenceDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpCorrespondenceListItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-back over the existing {@code correspondence/} bounded context (Phase 82, Epic 587). Two MCP
 * READ tools that close the Phase 81 read/write asymmetry:
 *
 * <ul>
 *   <li>{@code list_correspondence(matterId | customerId, page, size)} — the metadata projection as
 *       an {@link McpPage} (587A).
 *   <li>{@code get_correspondence(id)} — one record with body + headers (587B).
 * </ul>
 *
 * <p><b>Gating:</b> project view-access (mirroring {@code get_matter}), NOT the org-wide {@code
 * MCP_ACCESS} capability — these tools never call {@code McpCapabilityGuard.gatedTool}. The matter
 * path goes through {@code requireViewAccess}; the customer-only path resolves on
 * existence-in-tenant (search_path isolation makes a cross-tenant id invisible). Both ride the
 * existing read-egress consent via {@link McpEnablementService#effectiveState()}.
 *
 * <p><b>Denial audit asymmetry:</b> {@code mcp.access.denied} ({@code deniedGate=project-access})
 * fires ONLY on found-but-refused (a matter-scoped record the caller cannot view). An absent /
 * cross-tenant id or an unknown {@code customerId} is a non-leaking {@code not_found} with NO
 * denial audit (a lookup miss is not a policy denial; security-by-obscurity).
 *
 * <p><b>POPIA:</b> {@code mcp.tool.invoked} audit details carry only safe refs (correspondence ids
 * + {@code matterId}/{@code customerId} param) — NEVER subject/from/to/cc/body.
 */
@Component
public class CorrespondenceReadTools {

  private static final String GATE_PROJECT_ACCESS = "project-access";

  private final CorrespondenceService correspondenceService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public CorrespondenceReadTools(
      CorrespondenceService correspondenceService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.correspondenceService = correspondenceService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "list_correspondence",
      description =
          "List filed correspondence (emails) for ONE matter (matterId) OR ONE client (customerId)"
              + " — provide exactly one. Returns metadata rows (subject, from, receivedAt,"
              + " attachmentCount, direction) newest-first; use get_correspondence(id) for the body."
              + " Paginated — page size is capped at 50. Returns a non-leaking not-found if the"
              + " matter/client does not exist OR you cannot access it.")
  public Object listCorrespondence(
      @McpToolParam(
              required = false,
              description = "List correspondence for this matter (project) id.")
          UUID matterId,
      @McpToolParam(
              required = false,
              description = "List correspondence for this client (customer) id.")
          UUID customerId,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).")
          Integer page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          Integer size) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    // exactly-one-of: reject neither AND both (mirrors resolve_matter_by_email's arg discipline).
    boolean hasMatter = matterId != null;
    boolean hasCustomer = customerId != null;
    if (hasMatter == hasCustomer) {
      return McpToolErrors.asResult(
          McpError.invalidRequest("provide exactly one of matterId or customerId"), objectMapper);
    }

    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    // Unsorted Pageable — newest-first is JPQL-hardcoded and the service clamp() rejects any
    // client-supplied sort. The service also clamps the size to 50.
    Pageable pageable =
        PageRequest.of(
            page == null ? 0 : Math.max(page, 0),
            size == null || size <= 0 ? McpPagination.DEFAULT_MAX_SIZE : size);

    if (hasMatter) {
      try {
        var pageResult = correspondenceService.listForProject(matterId, actor, pageable);
        return success(pageResult, matterId, null, startNanos);
      } catch (ResourceNotFoundException e) {
        // Matter path: the only ResourceNotFoundException is requireViewAccess's obscurity-404 →
        // found-but-refused → emit denial before the non-leaking not_found.
        McpToolAudit.emitDenied(
            "list_correspondence",
            GATE_PROJECT_ACCESS,
            auditService,
            metrics,
            McpToolAudit.elapsed(startNanos));
        return McpToolErrors.asResult(McpError.notFound("correspondence"), objectMapper);
      }
    }
    // Customer path: an unknown/cross-tenant customerId is a lookup miss, NOT a policy denial → no
    // mcp.access.denied.
    try {
      var pageResult = correspondenceService.listForCustomer(customerId, pageable);
      return success(pageResult, null, customerId, startNanos);
    } catch (ResourceNotFoundException e) {
      return McpToolErrors.asResult(McpError.notFound("correspondence"), objectMapper);
    }
  }

  @McpTool(
      name = "get_correspondence",
      description =
          "Fetch one filed correspondence (email) by id, INCLUDING its body (bodyText/bodyHtml) and"
              + " headers (to/cc/from, subject, messageId, threadKey). Returns a non-leaking"
              + " not-found error if it does not exist OR you cannot access it.")
  public Object getCorrespondence(@McpToolParam(description = "Correspondence id.") UUID id) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    try {
      var detail = correspondenceService.requireDetailById(id, actor);
      var dto = McpCorrespondenceDto.from(detail);
      var meta = McpAuditMetadata.builder().rowCount(1).entityRef(id).build();
      emitInvoked("get_correspondence", meta, startNanos);
      return dto;
    } catch (ForbiddenException e) {
      // (b) found-with-projectId but view-access refused → the ONLY denial path.
      McpToolAudit.emitDenied(
          "get_correspondence",
          GATE_PROJECT_ACCESS,
          auditService,
          metrics,
          McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.notFound("correspondence"), objectMapper);
    } catch (ResourceNotFoundException e) {
      // (a) absent / cross-tenant id → lookup miss, NO denial audit.
      return McpToolErrors.asResult(McpError.notFound("correspondence"), objectMapper);
    }
  }

  /**
   * DB-paginated success: adapt the Spring {@link Page} to an {@link McpPage} + emit safe-ref
   * audit.
   */
  private Object success(
      Page<CorrespondenceListResponse> page, UUID matterId, UUID customerId, long startNanos) {
    var items = page.getContent().stream().map(McpCorrespondenceListItem::from).toList();
    var mcpPage =
        McpPage.of(
            items, page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    var meta =
        McpAuditMetadata.builder()
            .rowCount(items.size())
            .entityRefs(items.stream().map(McpCorrespondenceListItem::id).toList())
            .param("matterId", matterId)
            .param("customerId", customerId)
            .build();
    emitInvoked("list_correspondence", meta, startNanos);
    return mcpPage;
  }

  private void emitInvoked(String tool, McpAuditMetadata meta, long startNanos) {
    McpToolAudit.emitInvoked(tool, meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
  }
}
