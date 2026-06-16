package io.b2mash.b2b.b2bstrawman.mcp.resource;

import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpFirmProfileDto;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
  private final ObjectMapper objectMapper;

  public FirmProfileResource(AiFirmProfileService aiFirmProfileService, ObjectMapper objectMapper) {
    this.aiFirmProfileService = aiFirmProfileService;
    this.objectMapper = objectMapper;
  }

  @McpResource(
      uri = "kazi://firm-profile",
      name = "firm-profile",
      description =
          "The firm's AI grounding profile: practice areas, jurisdiction, risk calibration, and"
              + " house-style / fee-estimation notes. Requires the AI_MANAGE capability.",
      mimeType = "application/json")
  public String firmProfile() {
    if (!RequestScopes.hasCapability(CAP_AI_MANAGE)) {
      return objectMapper.writeValueAsString(McpError.forbidden());
    }
    var profile = aiFirmProfileService.getOrCreateProfile();
    return objectMapper.writeValueAsString(McpFirmProfileDto.from(profile));
  }
}
