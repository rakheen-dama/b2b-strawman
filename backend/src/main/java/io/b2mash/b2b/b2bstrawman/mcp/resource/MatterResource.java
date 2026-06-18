package io.b2mash.b2b.b2bstrawman.mcp.resource;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpMatterDto;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP resource {@code kazi://matter/{id}} — the same matter detail as the {@code get_matter} tool,
 * addressable as a resource (Epic 563B).
 *
 * <p>Access model: <b>project-access</b>. {@code getProject} routes through {@code
 * requireViewAccess} → {@link ResourceNotFoundException} for a non-member, returned here as a
 * non-leaking {@link McpError} (same message for absent vs inaccessible).
 *
 * <p>The Spring AI {@code 2.0.0-M6} resource result converter only serialises {@code
 * ReadResourceResult}/{@code ResourceContents}/{@code String} return values (not arbitrary DTOs),
 * and URI-template variables must be bound as {@code String}. So this method takes the {@code id}
 * as a {@code String}, parses it, and returns a JSON {@code String} with {@code
 * mimeType=application/json}.
 */
@Component
public class MatterResource {

  private static final String GATE_PROJECT_ACCESS = "project-access";

  private final ProjectService projectService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final AuditService auditService;
  private final McpMetrics metrics;

  public MatterResource(
      ProjectService projectService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      AuditService auditService,
      McpMetrics metrics) {
    this.projectService = projectService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.auditService = auditService;
    this.metrics = metrics;
  }

  @McpResource(
      uri = "kazi://matter/{id}",
      name = "matter",
      description = "A single matter (project) by id, with your role on it. Project-access gated.",
      mimeType = "application/json")
  public String matter(String id) {
    if (!enablement.effectiveState()) {
      return objectMapper.writeValueAsString(McpError.notEnabled());
    }
    long startNanos = System.nanoTime();
    var actor = ActorContext.fromRequestScopes();
    UUID matterId;
    try {
      // Parse the id in its own scope so a malformed UUID returns the same non-leaking not-found
      // response — without masking an unrelated IllegalArgumentException from the service.
      matterId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return objectMapper.writeValueAsString(McpError.notFound("matter"));
    }
    try {
      var withRole = projectService.getProject(matterId, actor);
      var meta = McpAuditMetadata.builder().rowCount(1).entityRef(matterId).build();
      McpToolAudit.emitInvoked(
          "kazi://matter", meta, auditService, metrics, McpToolAudit.elapsed(startNanos));
      return objectMapper.writeValueAsString(
          McpMatterDto.from(withRole.project(), withRole.projectRole()));
    } catch (ResourceNotFoundException e) {
      McpToolAudit.emitDenied(
          "kazi://matter",
          GATE_PROJECT_ACCESS,
          auditService,
          metrics,
          McpToolAudit.elapsed(startNanos));
      return objectMapper.writeValueAsString(McpError.notFound("matter"));
    }
  }
}
