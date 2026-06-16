package io.b2mash.b2b.b2bstrawman.mcp.resource;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpClientDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
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

  public ClientResource(
      CustomerService customerService,
      CustomerProjectService customerProjectService,
      ObjectMapper objectMapper) {
    this.customerService = customerService;
    this.customerProjectService = customerProjectService;
    this.objectMapper = objectMapper;
  }

  @McpResource(
      uri = "kazi://client/{id}",
      name = "client",
      description =
          "A single client (customer) by id, with contacts and linked matters. Org-wide read.",
      mimeType = "application/json")
  public String client(String id) {
    var actor = ActorContext.fromRequestScopes();
    try {
      UUID clientId = UUID.fromString(id);
      var customer = customerService.getCustomer(clientId);
      var linkedProjects = customerProjectService.listProjectsForCustomer(clientId, actor);
      return objectMapper.writeValueAsString(McpClientDto.from(customer, linkedProjects));
    } catch (ResourceNotFoundException | InvalidStateException | IllegalArgumentException e) {
      return objectMapper.writeValueAsString(McpError.notFound("client"));
    }
  }
}
