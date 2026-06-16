package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpClientDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpClientListItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only MCP tools over the firm's clients (customers): {@code list_clients} and {@code
 * get_client} (Epic 563A, §11.4).
 *
 * <p>Access model: <b>org-wide</b> (the whole authenticated tenant sees all clients). The live
 * MCP_ACCESS front-door gate is deferred to 565B, so no capability check is applied here.
 *
 * <p>{@code list_clients} deliberately OMITs the controller's tag/member-name enrichment ({@code
 * CustomerResponse.from}) — the list row is {@code {id, name, type, lifecycleStatus}} only, keeping
 * it token-efficient and avoiding the extra batch queries. Full detail (contacts + linked matters)
 * comes from {@code get_client}, where {@code linkedMatters} is resolved via a SEPARATE {@link
 * CustomerProjectService#listProjectsForCustomer(UUID, ActorContext)} call (not from {@code
 * getCustomer}).
 */
@Component
public class ClientTools {

  private final CustomerService customerService;
  private final CustomerProjectService customerProjectService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public ClientTools(
      CustomerService customerService,
      CustomerProjectService customerProjectService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.customerService = customerService;
    this.customerProjectService = customerProjectService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @McpTool(
      name = "list_clients",
      description =
          "List the firm's clients (customers), org-wide. Optionally filter by lifecycleStatus"
              + " (PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED). Paginated —"
              + " page size is capped at 50.")
  public Object listClients(
      @McpToolParam(required = false, description = "Zero-based page index (default 0).") int page,
      @McpToolParam(required = false, description = "Page size, capped at 50 (default 50).")
          int size,
      @McpToolParam(
              required = false,
              description = "Filter by lifecycle status short enum name, e.g. ACTIVE.")
          String lifecycleStatus) {
    LifecycleStatus parsed;
    try {
      parsed = lifecycleStatus == null ? null : LifecycleStatus.valueOf(lifecycleStatus);
    } catch (IllegalArgumentException e) {
      return McpToolErrors.asResult(
          McpError.invalidRequest("Unknown lifecycleStatus. See the tool description for values."),
          objectMapper);
    }

    var customers =
        (parsed == null
                ? customerService.listCustomers()
                : customerService.listCustomersByLifecycleStatus(parsed))
            .stream().map(McpClientListItem::from).toList();
    McpPage<McpClientListItem> pageResult =
        McpPagination.paginate(customers, page, size, McpPagination.DEFAULT_MAX_SIZE);
    emitInvoked("list_clients");
    return pageResult;
  }

  @McpTool(
      name = "get_client",
      description =
          "Fetch one client (customer) by id, including its contact details and the matters linked"
              + " to it. Returns a non-leaking not-found error if the client does not exist.")
  public Object getClient(@McpToolParam(description = "Client (customer) id.") UUID id) {
    var actor = ActorContext.fromRequestScopes();
    try {
      var customer = customerService.getCustomer(id);
      // linkedMatters comes from a SEPARATE service call (drift: spec said CustomerService).
      var linkedProjects = customerProjectService.listProjectsForCustomer(id, actor);
      var dto = McpClientDto.from(customer, linkedProjects);
      emitInvoked("get_client");
      return dto;
    } catch (ResourceNotFoundException e) {
      return McpToolErrors.asResult(McpError.notFound("client"), objectMapper);
    } catch (InvalidStateException e) {
      return McpToolErrors.asResult(McpError.notFound("client"), objectMapper);
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
