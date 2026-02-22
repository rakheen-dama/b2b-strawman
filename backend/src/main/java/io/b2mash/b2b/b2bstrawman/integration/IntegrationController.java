package io.b2mash.b2b.b2bstrawman.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
public class IntegrationController {

  private final IntegrationService integrationService;

  public IntegrationController(IntegrationService integrationService) {
    this.integrationService = integrationService;
  }

  @GetMapping
  public ResponseEntity<List<OrgIntegrationDto>> listIntegrations() {
    return ResponseEntity.ok(integrationService.listAllIntegrations());
  }

  @GetMapping("/providers")
  public ResponseEntity<Map<String, List<String>>> listProviders() {
    return ResponseEntity.ok(integrationService.availableProviders());
  }

  @PutMapping("/{domain}")
  public ResponseEntity<OrgIntegrationDto> upsertIntegration(
      @PathVariable IntegrationDomain domain,
      @Valid @RequestBody UpsertIntegrationRequest request) {
    return ResponseEntity.ok(
        integrationService.upsertIntegration(domain, request.providerSlug(), request.configJson()));
  }

  @PostMapping("/{domain}/set-key")
  public ResponseEntity<Void> setApiKey(
      @PathVariable IntegrationDomain domain, @Valid @RequestBody SetApiKeyRequest request) {
    integrationService.setApiKey(domain, request.apiKey());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{domain}/test")
  public ResponseEntity<ConnectionTestResult> testConnection(
      @PathVariable IntegrationDomain domain) {
    return ResponseEntity.ok(integrationService.testConnection(domain));
  }

  @DeleteMapping("/{domain}/key")
  public ResponseEntity<Void> deleteApiKey(@PathVariable IntegrationDomain domain) {
    integrationService.deleteApiKey(domain);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{domain}/toggle")
  public ResponseEntity<OrgIntegrationDto> toggleIntegration(
      @PathVariable IntegrationDomain domain, @Valid @RequestBody ToggleRequest request) {
    return ResponseEntity.ok(integrationService.toggleIntegration(domain, request.enabled()));
  }

  // --- DTOs ---

  public record UpsertIntegrationRequest(String providerSlug, String configJson) {}

  public record SetApiKeyRequest(@NotBlank(message = "apiKey must not be blank") String apiKey) {}

  public record ToggleRequest(boolean enabled) {}
}
