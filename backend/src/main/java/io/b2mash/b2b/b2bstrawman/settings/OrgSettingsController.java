package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.settings.dto.CollectionsSettingsResponse;
import io.b2mash.b2b.b2bstrawman.settings.dto.DataProtectionSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.SettingsResponse;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateAcceptanceSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateBatchBillingSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateCapacitySettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateCollectionsSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateComplianceSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateExpenseSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdatePortalDigestCadenceRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdatePortalRetainerMemberDisplayRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateTaxSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateTimeReminderSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateVerticalProfileRequest;
import jakarta.validation.Valid;
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

  // Collections / dunning policy (Phase 83, §4.2). GET is member-readable (no capability); PUT is
  // admin/owner via TEAM_OVERSIGHT + a service-side role check. PUT (full-replace), not PATCH, per
  // §4.2 — all five fields are meaningful on every call.
  @GetMapping("/collections")
  public ResponseEntity<CollectionsSettingsResponse> getCollectionsSettings() {
    return ResponseEntity.ok(orgSettingsService.getCollectionsSettings());
  }

  @PutMapping("/collections")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<CollectionsSettingsResponse> updateCollectionsSettings(
      @Valid @RequestBody UpdateCollectionsSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateCollectionsSettings(
            request.collectionsEnabled(),
            request.stage1DaysOverdue(),
            request.stage2DaysOverdue(),
            request.stage3DaysOverdue(),
            request.escalateDaysOverdue(),
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

  @PatchMapping("/expense")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateExpenseSettings(
      @Valid @RequestBody UpdateExpenseSettingsRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateExpenseSettings(request.defaultExpenseMarkupPercent(), actor));
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
}
