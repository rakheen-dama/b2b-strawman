package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.settings.dto.SettingsResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  private final OrgSettingsService orgSettingsService;

  public OrgSettingsController(OrgSettingsService orgSettingsService) {
    this.orgSettingsService = orgSettingsService;
  }

  @GetMapping
  public ResponseEntity<SettingsResponse> getSettings() {
    return ResponseEntity.ok(orgSettingsService.getSettingsWithBranding());
  }

  @PutMapping
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateSettings(
      @Valid @RequestBody UpdateSettingsRequest request, ActorContext actor) {
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
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> uploadLogo(
      @RequestParam("file") MultipartFile file, ActorContext actor) {
    return ResponseEntity.ok(orgSettingsService.uploadLogo(file, actor));
  }

  @DeleteMapping("/logo")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> deleteLogo(ActorContext actor) {
    return ResponseEntity.ok(orgSettingsService.deleteLogo(actor));
  }

  @PatchMapping("/compliance")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateComplianceSettings(
      @Valid @RequestBody UpdateComplianceSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateComplianceSettings(
            request.dormancyThresholdDays(), request.dataRequestDeadlineDays(), actor));
  }

  @PatchMapping("/tax")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateTaxSettings(
      @Valid @RequestBody UpdateTaxSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateTaxSettings(
            request.taxRegistrationNumber(),
            request.taxRegistrationLabel(),
            request.taxLabel(),
            request.taxInclusive(),
            actor));
  }

  @PatchMapping("/acceptance")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateAcceptanceSettings(
      @Valid @RequestBody UpdateAcceptanceSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateAcceptanceSettings(request.acceptanceExpiryDays(), actor));
  }

  @PatchMapping("/time-reminders")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateTimeReminderSettings(
      @Valid @RequestBody UpdateTimeReminderSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateTimeReminderSettings(
            request.timeReminderEnabled(),
            request.timeReminderDays(),
            request.timeReminderTime(),
            request.timeReminderMinMinutes(),
            actor));
  }

  @PatchMapping("/capacity")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateCapacitySettings(
      @Valid @RequestBody UpdateCapacitySettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateDefaultWeeklyCapacityHours(
            request.defaultWeeklyCapacityHours(), actor));
  }

  @PatchMapping("/batch-billing")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateBatchBillingSettings(
      @Valid @RequestBody UpdateBatchBillingSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateBatchBillingSettings(
            request.billingBatchAsyncThreshold(),
            request.billingEmailRateLimit(),
            request.defaultBillingRunCurrency(),
            actor));
  }

  // Service enforces owner-only; TEAM_OVERSIGHT is the nearest capability
  @PatchMapping("/data-protection")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateDataProtectionSettings(
      @Valid @RequestBody DataProtectionSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(orgSettingsService.updateDataProtectionSettings(request, actor));
  }

  // Service enforces owner-only; TEAM_OVERSIGHT is the nearest capability
  @PatchMapping("/vertical-profile")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateVerticalProfile(
      @Valid @RequestBody UpdateVerticalProfileRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateVerticalProfile(request.verticalProfile(), actor));
  }

  @PatchMapping("/portal-digest-cadence")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updatePortalDigestCadence(
      @Valid @RequestBody UpdatePortalDigestCadenceRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updatePortalDigestCadence(request.portalDigestCadence(), actor));
  }

  @PatchMapping("/portal-retainer-member-display")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updatePortalRetainerMemberDisplay(
      @Valid @RequestBody UpdatePortalRetainerMemberDisplayRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updatePortalRetainerMemberDisplay(
            request.portalRetainerMemberDisplay(), actor));
  }

  // --- DTOs ---

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

  public record UpdateVerticalProfileRequest(
      @Size(max = 50, message = "verticalProfile must be at most 50 characters")
          String verticalProfile) {}

  public record UpdatePortalDigestCadenceRequest(
      @NotNull(message = "portalDigestCadence is required")
          PortalDigestCadence portalDigestCadence) {}

  public record UpdatePortalRetainerMemberDisplayRequest(
      @NotNull(message = "portalRetainerMemberDisplay is required")
          PortalRetainerMemberDisplay portalRetainerMemberDisplay) {}

  public record DataProtectionSettingsRequest(
      @Size(max = 10, message = "dataProtectionJurisdiction must be at most 10 characters")
          String dataProtectionJurisdiction,
      Boolean retentionPolicyEnabled,
      @Min(value = 1, message = "defaultRetentionMonths must be positive")
          Integer defaultRetentionMonths,
      @Min(value = 12, message = "financialRetentionMonths must be at least 12")
          Integer financialRetentionMonths,
      @Size(max = 255, message = "informationOfficerName must be at most 255 characters")
          String informationOfficerName,
      @jakarta.validation.constraints.Email(
              message = "informationOfficerEmail must be a valid email")
          @Size(max = 255, message = "informationOfficerEmail must be at most 255 characters")
          String informationOfficerEmail) {}
}
