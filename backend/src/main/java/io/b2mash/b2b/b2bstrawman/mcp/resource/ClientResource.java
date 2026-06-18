package io.b2mash.b2b.b2bstrawman.mcp.resource;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpClientDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.time.Duration;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP resource {@code kazi://client/{id}} — the same client detail as the {@code get_client} tool,
 * addressable as a resource (Epic 563B).
 *
 * <p>Access model: <b>org-wide</b>. {@code linkedMatters} is resolved via a separate {@link
 * CustomerProjectService#listProjectsForCustomer(UUID, ActorContext)} call (mirrors {@code
 * get_client}). An unknown id yields a non-leaking {@link McpError}.
 *
 * <p>As with {@link MatterResource}, the URI variable is bound as {@code String} and the method
 * returns a JSON {@code String} ({@code mimeType=application/json}), because the {@code 2.0.0-M6}
 * resource converter does not serialise arbitrary DTO return types.
 */
@Component
public class ClientResource {

  private final CustomerService customerService;
  private final CustomerProjectService customerProjectService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final AuditService auditService;
  private final McpMetrics metrics;

  public ClientResource(
      CustomerService customerService,
      CustomerProjectService customerProjectService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      AuditService auditService,
      McpMetrics metrics) {
    this.customerService = customerService;
    this.customerProjectService = customerProjectService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.auditService = auditService;
    this.metrics = metrics;
  }

  @McpResource(
      uri = "kazi://client/{id}",
      name = "client",
      description =
          "A single client (customer) by id, with contacts and linked matters. Org-wide read.",
      mimeType = "application/json")
  public String client(String id) {
    if (!enablement.effectiveState()) {
      return objectMapper.writeValueAsString(McpError.notEnabled());
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    UUID clientId;
    try {
      // Parse the id in its own scope so a malformed UUID returns the same non-leaking not-found
      // response — without masking an unrelated IllegalArgumentException from the service.
      clientId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return objectMapper.writeValueAsString(McpError.notFound("client"));
    }
    try {
      var customer = customerService.getCustomer(clientId);
      var linkedProjects = customerProjectService.listProjectsForCustomer(clientId, actor);
      var meta = McpAuditMetadata.builder().rowCount(1).entityRef(clientId).build();
      McpToolAudit.emitInvoked(
          "kazi://client",
          meta,
          auditService,
          metrics,
          Duration.ofNanos(System.nanoTime() - startNanos));
      return objectMapper.writeValueAsString(McpClientDto.from(customer, linkedProjects));
    } catch (ResourceNotFoundException | InvalidStateException e) {
      return objectMapper.writeValueAsString(McpError.notFound("client"));
    }
  }
}
