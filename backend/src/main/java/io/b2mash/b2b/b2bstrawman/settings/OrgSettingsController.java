package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    return ResponseEntity.ok(
        orgSettingsService.updateSettingsWithBranding(
            request.defaultCurrency(),
            request.brandColor(),
            request.documentFooterText(),
            request.accountingEnabled(),
            request.aiEnabled(),
            request.documentSigningEnabled(),
            request.projectNamingPattern(),
            actor));
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

    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    return ResponseEntity.ok(orgSettingsService.uploadLogo(file, actor));
  }

  @DeleteMapping("/logo")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> deleteLogo() {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    return ResponseEntity.ok(orgSettingsService.deleteLogo(actor));
  }

  @PatchMapping("/compliance")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateComplianceSettings(
      @Valid @RequestBody UpdateComplianceSettingsRequest request) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    return ResponseEntity.ok(
        orgSettingsService.updateComplianceSettings(
            request.dormancyThresholdDays(), request.dataRequestDeadlineDays(), actor));
  }

  @PatchMapping("/tax")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateTaxSettings(
      @Valid @RequestBody UpdateTaxSettingsRequest request) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();
    return ResponseEntity.ok(
        orgSettingsService.updateTaxSettings(
            request.taxRegistrationNumber(),
            request.taxRegistrationLabel(),
            request.taxLabel(),
            request.taxInclusive(),
            actor));
  }

  @PatchMapping("/acceptance")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateAcceptanceSettings(
      @Valid @RequestBody UpdateAcceptanceSettingsRequest request) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();
    return ResponseEntity.ok(
        orgSettingsService.updateAcceptanceSettings(request.acceptanceExpiryDays(), actor));
  }

  @PatchMapping("/time-reminders")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateTimeReminderSettings(
      @Valid @RequestBody UpdateTimeReminderSettingsRequest request) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();
    return ResponseEntity.ok(
        orgSettingsService.updateTimeReminderSettings(
            request.timeReminderEnabled(),
            request.timeReminderDays(),
            request.timeReminderTime(),
            request.timeReminderMinMinutes(),
            actor));
  }

  @PatchMapping("/capacity")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateCapacitySettings(
      @Valid @RequestBody UpdateCapacitySettingsRequest request) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();
    return ResponseEntity.ok(
        orgSettingsService.updateDefaultWeeklyCapacityHours(
            request.defaultWeeklyCapacityHours(), actor));
  }

  @PatchMapping("/batch-billing")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<SettingsResponse> updateBatchBillingSettings(
      @Valid @RequestBody UpdateBatchBillingSettingsRequest request) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();
    return ResponseEntity.ok(
        orgSettingsService.updateBatchBillingSettings(
            request.billingBatchAsyncThreshold(),
            request.billingEmailRateLimit(),
            request.defaultBillingRunCurrency(),
            actor));
  }

  // --- DTOs ---

  public record SettingsResponse(
      String defaultCurrency,
      String logoUrl,
      String brandColor,
      String documentFooterText,
      Integer dormancyThresholdDays,
      Integer dataRequestDeadlineDays,
      List<Map<String, Object>> compliancePackStatus,
      boolean accountingEnabled,
      boolean aiEnabled,
      boolean documentSigningEnabled,
      String taxRegistrationNumber,
      String taxRegistrationLabel,
      String taxLabel,
      boolean taxInclusive,
      Integer acceptanceExpiryDays,
      Integer defaultRequestReminderDays,
      boolean timeReminderEnabled,
      String timeReminderDays,
      String timeReminderTime,
      Double timeReminderMinHours,
      BigDecimal defaultExpenseMarkupPercent,
      BigDecimal defaultWeeklyCapacityHours,
      Integer billingBatchAsyncThreshold,
      Integer billingEmailRateLimit,
      String defaultBillingRunCurrency,
      String projectNamingPattern) {}

  public record UpdateSettingsRequest(
      @NotBlank(message = "defaultCurrency is required")
          @Size(min = 3, max = 3, message = "defaultCurrency must be exactly 3 characters")
          String defaultCurrency,
      @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "brandColor must be a valid hex color")
          String brandColor,
      String documentFooterText,
      Boolean accountingEnabled,
      Boolean aiEnabled,
      Boolean documentSigningEnabled,
      @Size(max = 500, message = "projectNamingPattern must be at most 500 characters")
          String projectNamingPattern) {}

  public record UpdateComplianceSettingsRequest(
      @Positive(message = "dormancyThresholdDays must be positive") Integer dormancyThresholdDays,
      @Positive(message = "dataRequestDeadlineDays must be positive")
          Integer dataRequestDeadlineDays) {}

  public record UpdateAcceptanceSettingsRequest(
      @Min(value = 1, message = "acceptanceExpiryDays must be at least 1")
          @Max(value = 365, message = "acceptanceExpiryDays must be at most 365")
          Integer acceptanceExpiryDays) {}

  public record UpdateTaxSettingsRequest(
      @Size(max = 50, message = "taxRegistrationNumber must be at most 50 characters")
          String taxRegistrationNumber,
      @Size(max = 30, message = "taxRegistrationLabel must be at most 30 characters")
          String taxRegistrationLabel,
      @Size(max = 20, message = "taxLabel must be at most 20 characters") String taxLabel,
      boolean taxInclusive) {}

  public record UpdateTimeReminderSettingsRequest(
      Boolean timeReminderEnabled,
      @Size(max = 50, message = "timeReminderDays must be at most 50 characters")
          @Pattern(
              regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
              message =
                  "timeReminderDays must be a comma-separated list of valid day abbreviations")
          String timeReminderDays,
      @Pattern(
              regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
              message = "timeReminderTime must be in HH:mm format")
          String timeReminderTime,
      @Min(value = 0, message = "timeReminderMinMinutes must be non-negative")
          Integer timeReminderMinMinutes) {}

  public record UpdateCapacitySettingsRequest(
      @NotNull(message = "defaultWeeklyCapacityHours is required")
          @Positive(message = "defaultWeeklyCapacityHours must be positive")
          BigDecimal defaultWeeklyCapacityHours) {}

  public record UpdateBatchBillingSettingsRequest(
      @Min(value = 1, message = "billingBatchAsyncThreshold must be at least 1")
          @Max(value = 1000, message = "billingBatchAsyncThreshold must be at most 1000")
          Integer billingBatchAsyncThreshold,
      @Min(value = 1, message = "billingEmailRateLimit must be at least 1")
          @Max(value = 100, message = "billingEmailRateLimit must be at most 100")
          Integer billingEmailRateLimit,
      @Size(min = 3, max = 3, message = "defaultBillingRunCurrency must be exactly 3 characters")
          String defaultBillingRunCurrency) {}
}
