package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class OrgSettingsController {

  private final OrgSettingsService orgSettingsService;

  public OrgSettingsController(OrgSettingsService orgSettingsService) {
    this.orgSettingsService = orgSettingsService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> getSettings() {
    var settings = orgSettingsService.getSettings();
    return ResponseEntity.ok(new SettingsResponse(settings.defaultCurrency()));
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateSettings(
      @Valid @RequestBody UpdateSettingsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var settings = orgSettingsService.updateSettings(request.defaultCurrency(), memberId, orgRole);
    return ResponseEntity.ok(new SettingsResponse(settings.defaultCurrency()));
  }

  // --- DTOs ---

  public record SettingsResponse(String defaultCurrency) {}

  public record UpdateSettingsRequest(
      @NotBlank(message = "defaultCurrency is required")
          @Size(min = 3, max = 3, message = "defaultCurrency must be exactly 3 characters")
          String defaultCurrency) {}
}
