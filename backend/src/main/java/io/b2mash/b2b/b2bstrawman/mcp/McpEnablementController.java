package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the Kazi MCP connector's per-tenant enablement (Epic 565C). Thin delegate over
 * {@link McpEnablementService}: enable records POPIA consent first then enables the integration,
 * revoke disables it, status reports the current effective state for the settings UI (Epic 566).
 * All three endpoints are gated by the {@code TEAM_OVERSIGHT} capability.
 */
@RestController
@RequestMapping("/api/integrations/mcp")
public class McpEnablementController {

  private final McpEnablementService service;

  public McpEnablementController(McpEnablementService service) {
    this.service = service;
  }

  @PostMapping("/enable")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Void> enable(@Valid @RequestBody EnableRequest request) {
    service.enable(request.consentVersion());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/revoke")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Void> revoke() {
    service.revoke();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/status")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<McpEnablementService.McpEnablementStatus> status() {
    return ResponseEntity.ok(service.status());
  }

  public record EnableRequest(@NotBlank String consentVersion) {}
}
