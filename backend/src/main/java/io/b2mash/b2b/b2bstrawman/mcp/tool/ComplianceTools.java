package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolErrors;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpComplianceGapDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only compliance MCP tool (Epic 564B): {@code list_compliance_gaps}. Combines a client's FICA
 * status with the flattened checklist items across their checklist instances. Requires the {@code
 * CUSTOMER_MANAGEMENT} capability, checked inline and returned as {@link McpError#forbidden()}
 * (never thrown). Items are capped and a {@code truncated} flag set when clipped.
 */
@Component
public class ComplianceTools {

  private static final String CAP_CUSTOMER_MANAGEMENT = "CUSTOMER_MANAGEMENT";

  private final InformationRequestService informationRequestService;
  private final ChecklistInstanceService checklistInstanceService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public ComplianceTools(
      InformationRequestService informationRequestService,
      ChecklistInstanceService checklistInstanceService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.informationRequestService = informationRequestService;
    this.checklistInstanceService = checklistInstanceService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @McpTool(
      name = "list_compliance_gaps",
      description =
          "Summarise a client's compliance posture: FICA verification status plus their open"
              + " checklist items (name, status, required). The item list is capped at 50; the"
              + " 'truncated' flag is set when clipped. Returns a non-leaking not-found error if the"
              + " client does not exist. Requires the CUSTOMER_MANAGEMENT capability.")
  public Object listComplianceGaps(
      @McpToolParam(description = "Client (customer) id.") UUID customerId) {
    if (!RequestScopes.hasCapability(CAP_CUSTOMER_MANAGEMENT)) {
      McpToolAudit.emitDenied("list_compliance_gaps", auditService);
      return McpToolErrors.asResult(McpError.forbidden(), objectMapper);
    }
    try {
      var fica = informationRequestService.getFicaStatus(customerId);
      var instances = checklistInstanceService.getInstancesWithItemsForCustomer(customerId);
      var dto =
          McpComplianceGapDto.from(
              customerId, fica.status(), instances, McpPagination.DEFAULT_MAX_SIZE);
      McpToolAudit.emitInvoked("list_compliance_gaps", auditService);
      return dto;
    } catch (ResourceNotFoundException e) {
      return McpToolErrors.asResult(McpError.notFound("client"), objectMapper);
    }
  }
}
