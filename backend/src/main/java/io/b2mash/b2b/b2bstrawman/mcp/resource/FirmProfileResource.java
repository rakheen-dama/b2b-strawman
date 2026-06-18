package io.b2mash.b2b.b2bstrawman.mcp.resource;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.mcp.McpAuditMetadata;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.McpMetrics;
import io.b2mash.b2b.b2bstrawman.mcp.McpToolAudit;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpFirmProfileDto;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Duration;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Fixed-URI MCP resource (Epic 564B): {@code kazi://firm-profile}. Exposes the firm's AI grounding
 * profile — practice areas, jurisdiction, risk calibration, and house-style / fee-estimation notes
 * — projecting ONLY those five client-safe fields ([ADR-304]). Because the URI is fixed (no {@code
 * {id}} template), it appears in {@code resources/list} rather than {@code
 * resources/templates/list}.
 *
 * <p>The {@code AI_MANAGE} capability gate is RETAINED for this resource ([D1]): a member with
 * {@code MCP_ACCESS} but without {@code AI_MANAGE} receives a {@link McpError#forbidden()} JSON
 * payload. The capability is checked inline and the error serialised — a resource returns a JSON
 * String and must never throw (a thrown {@code AccessDeniedException} would leak as "Error invoking
 * method").
 */
@Component
public class FirmProfileResource {

  private static final String CAP_AI_MANAGE = "AI_MANAGE";

  private final AiFirmProfileService aiFirmProfileService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final McpEnablementService enablement;
  private final McpMetrics metrics;

  public FirmProfileResource(
      AiFirmProfileService aiFirmProfileService,
      AuditService auditService,
      ObjectMapper objectMapper,
      McpEnablementService enablement,
      McpMetrics metrics) {
    this.aiFirmProfileService = aiFirmProfileService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.enablement = enablement;
    this.metrics = metrics;
  }

  @McpResource(
      uri = "kazi://firm-profile",
      name = "firm-profile",
      description =
          "The firm's AI grounding profile: practice areas, jurisdiction, risk calibration, and"
              + " house-style / fee-estimation notes. Requires the AI_MANAGE capability.",
      mimeType = "application/json")
  public String firmProfile() {
    if (!enablement.effectiveState()) {
      return objectMapper.writeValueAsString(McpError.notEnabled());
    }
    long startNanos = System.nanoTime();
    if (!RequestScopes.hasCapability(CAP_AI_MANAGE)) {
      McpToolAudit.emitDenied(
          "kazi://firm-profile", CAP_AI_MANAGE, auditService, metrics, elapsed(startNanos));
      return objectMapper.writeValueAsString(McpError.forbidden());
    }
    var profile = aiFirmProfileService.getOrCreateProfile();
    var meta = McpAuditMetadata.builder().rowCount(1).build();
    McpToolAudit.emitInvoked(
        "kazi://firm-profile", meta, auditService, metrics, elapsed(startNanos));
    return objectMapper.writeValueAsString(McpFirmProfileDto.from(profile));
  }

  private static Duration elapsed(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }
}
