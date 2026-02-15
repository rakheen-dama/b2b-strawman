package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings")
public class OrgSettingsController {

  private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2MB
  private static final java.util.Set<String> ALLOWED_CONTENT_TYPES =
      java.util.Set.of("image/png", "image/jpeg", "image/svg+xml");

  private final OrgSettingsService orgSettingsService;

  public OrgSettingsController(OrgSettingsService orgSettingsService) {
    this.orgSettingsService = orgSettingsService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> getSettings() {
    return ResponseEntity.ok(orgSettingsService.getSettingsWithBranding());
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateSettings(
      @Valid @RequestBody UpdateSettingsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    return ResponseEntity.ok(
        orgSettingsService.updateSettingsWithBranding(
            request.defaultCurrency(),
            request.brandColor(),
            request.documentFooterText(),
            memberId,
            orgRole));
  }

  @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      throw new InvalidStateException("Invalid file", "File is empty");
    }
    if (file.getSize() > MAX_LOGO_SIZE) {
      throw new InvalidStateException("File too large", "Logo file must be under 2MB");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new InvalidStateException("Invalid file type", "Logo must be PNG, JPG, or SVG");
    }

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    return ResponseEntity.ok(orgSettingsService.uploadLogo(file, memberId, orgRole));
  }

  @DeleteMapping("/logo")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> deleteLogo() {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    return ResponseEntity.ok(orgSettingsService.deleteLogo(memberId, orgRole));
  }

  // --- DTOs ---

  public record SettingsResponse(
      String defaultCurrency, String logoUrl, String brandColor, String documentFooterText) {}

  public record UpdateSettingsRequest(
      @NotBlank(message = "defaultCurrency is required")
          @Size(min = 3, max = 3, message = "defaultCurrency must be exactly 3 characters")
          String defaultCurrency,
      @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "brandColor must be a valid hex color")
          String brandColor,
      String documentFooterText) {}
}
