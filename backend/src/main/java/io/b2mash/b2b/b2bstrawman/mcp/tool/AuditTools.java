package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpAuditEventItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Duration;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only audit MCP tool (Epic 564B): {@code get_audit_events}. Returns enriched audit events
 * newest-first ({@code occurredAt DESC} — the ordering is fixed by the audit query, the caller's
 * Sort is discarded). Requires the {@code TEAM_OVERSIGHT} capability, checked inline and returned
 * as {@link McpError#forbidden()} (never thrown). Page size capped at 200.
 *
 * <p>Customer scope: {@code AuditEventFilter} has no first-class customer dimension, so a supplied
 * {@code customerId} matches events whose entity IS the client record itself ({@code
 * entityType="customer"}); it does not union project/invoice/document events for that client.
 */
@Component
public class AuditTools {

  private static final String CAP_TEAM_OVERSIGHT = "TEAM_OVERSIGHT";

  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public AuditTools(
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpTool(
      name = "get_audit_events",
      description =
          "List the firm's audit events, newest first. Optionally filter by eventType (prefix"
              + " match, e.g. 'invoice.') and/or clientId (matches events on the client record"
              + " itself). Paginated — page size capped at 200. Requires the TEAM_OVERSIGHT"
              + " capability.")
  public Object getAuditEvents(
      @McpToolParam(
              required = false,
              description = "Filter to events on this client (customer) record.")
          UUID customerId,
      @McpToolParam(
              required = false,
              description = "Filter by event type, prefix match (e.g. 'invoice.').")
          String eventType,
      @McpToolParam(required = false, description = "Zero-based page index (default 0).") int page,
      @McpToolParam(required = false, description = "Page size, capped at 200 (default 50).")
          int size) {
    if (!enablement.effectiveState()) {
      return McpToolErrors.asResult(McpError.notEnabled(), objectMapper);
    }
    long startNanos = System.nanoTime();
    if (!RequestScopes.hasCapability(CAP_TEAM_OVERSIGHT)) {
      McpToolAudit.emitDenied(
          "get_audit_events", CAP_TEAM_OVERSIGHT, auditService, metrics, elapsed(startNanos));
      return McpToolErrors.asResult(McpError.forbidden(), objectMapper);
    }
    int clampedSize = McpPagination.clampSize(size, McpPagination.AUDIT_MAX_SIZE);
    int clampedPage = Math.max(page, 0);

    AuditEventFilter filter =
        customerId != null
            ? new AuditEventFilter("customer", customerId, null, eventType, null, null, null)
            : new AuditEventFilter(null, null, null, eventType, null, null, null);

    // Ordering is fixed occurredAt DESC by the audit query — do not set a Sort.
    var eventsPage =
        auditService.findEventsEnriched(filter, PageRequest.of(clampedPage, clampedSize));
    var items = eventsPage.getContent().stream().map(McpAuditEventItem::from).toList();
    McpPage<McpAuditEventItem> result =
        McpPage.of(
            items,
            eventsPage.getNumber(),
            eventsPage.getSize(),
            eventsPage.getTotalElements(),
            eventsPage.hasNext());
    // eventType is a prefix string filter (not free-text PII); customerId is an id. Both are safe.
    var meta =
        McpAuditMetadata.builder()
            .rowCount(items.size())
            .entityRef(customerId)
            .param("eventType", eventType)
            .build();
    McpToolAudit.emitInvoked("get_audit_events", meta, auditService, metrics, elapsed(startNanos));
    return result;
  }

  private static Duration elapsed(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }
}
