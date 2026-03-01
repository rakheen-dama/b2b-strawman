package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsController.SettingsResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrgSettingsService {

  private static final Logger log = LoggerFactory.getLogger(OrgSettingsService.class);
  private static final String DEFAULT_CURRENCY = "USD";
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final StorageService storageService;

  public OrgSettingsService(
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      StorageService storageService) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.storageService = storageService;
  }

  /**
   * Returns the org settings for the current tenant. Never returns null — if no row exists, returns
   * a default response with "USD" as the currency without persisting.
   */
  @Transactional(readOnly = true)
  public OrgSettingsResponse getSettings() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> new OrgSettingsResponse(s.getDefaultCurrency()))
        .orElse(new OrgSettingsResponse(DEFAULT_CURRENCY));
  }

  /** Returns settings with branding information (logoUrl, brandColor, documentFooterText). */
  @Transactional(readOnly = true)
  public SettingsResponse getSettingsWithBranding() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(this::toSettingsResponse)
        .orElse(
            new SettingsResponse(
                DEFAULT_CURRENCY,
                null, // logoUrl
                null, // brandColor
                null, // documentFooterText
                null, // dormancyThresholdDays
                null, // dataRequestDeadlineDays
                null, // compliancePackStatus
                false, // accountingEnabled
                false, // aiEnabled
                false, // documentSigningEnabled
                null, // taxRegistrationNumber
                null, // taxRegistrationLabel
                null, // taxLabel
                false, // taxInclusive
                null, // acceptanceExpiryDays
                false, // timeReminderEnabled
                "MON,TUE,WED,THU,FRI", // timeReminderDays
                "17:00", // timeReminderTime
                4.0, // timeReminderMinHours
                null)); // defaultExpenseMarkupPercent
  }

  /** Updates settings including branding fields. */
  @Transactional
  public SettingsResponse updateSettingsWithBranding(
      String defaultCurrency,
      String brandColor,
      String documentFooterText,
      Boolean accountingEnabled,
      Boolean aiEnabled,
      Boolean documentSigningEnabled,
      UUID memberId,
      String orgRole) {
    requireAdminOrOwner(orgRole);

    var existing = orgSettingsRepository.findForCurrentTenant();

    OrgSettings settings;

    if (existing.isPresent()) {
      settings = existing.get();
      settings.updateCurrency(defaultCurrency);
    } else {
      settings = new OrgSettings(defaultCurrency);
    }

    settings.setBrandColor(brandColor);
    settings.setDocumentFooterText(documentFooterText);

    // Update integration flags if provided (null = don't change)
    settings.updateIntegrationFlags(
        accountingEnabled != null ? accountingEnabled : settings.isAccountingEnabled(),
        aiEnabled != null ? aiEnabled : settings.isAiEnabled(),
        documentSigningEnabled != null
            ? documentSigningEnabled
            : settings.isDocumentSigningEnabled());

    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated org settings with branding: currency={}, brandColor={}",
        defaultCurrency,
        brandColor);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "default_currency",
                    defaultCurrency,
                    "brand_color",
                    brandColor != null ? brandColor : ""))
            .build());

    return toSettingsResponse(settings);
  }

  /** Uploads a logo to storage and updates the org settings. */
  @Transactional
  public SettingsResponse uploadLogo(MultipartFile file, UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    String tenantId = RequestScopes.TENANT_ID.get();
    String ext = extensionFromContentType(file.getContentType());
    String s3Key = "org/" + tenantId + "/branding/logo." + ext;

    try {
      storageService.upload(s3Key, file.getInputStream(), file.getSize(), file.getContentType());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to upload logo to storage", e);
    }

    OrgSettings settings;
    try {
      settings =
          orgSettingsRepository
              .findForCurrentTenant()
              .orElseGet(
                  () -> {
                    var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                    return orgSettingsRepository.save(newSettings);
                  });

      settings.setLogoS3Key(s3Key);
      settings = orgSettingsRepository.save(settings);
    } catch (RuntimeException e) {
      // DB save failed — clean up the orphaned storage object
      log.warn("DB save failed after storage upload, deleting orphaned object: {}", s3Key);
      storageService.delete(s3Key);
      throw e;
    }

    log.info("Uploaded logo for tenant {}: {}", tenantId, s3Key);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.logo_uploaded")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("s3_key", s3Key))
            .build());

    return toSettingsResponse(settings);
  }

  /** Deletes the logo from storage and clears the logoS3Key in org settings. */
  @Transactional
  public SettingsResponse deleteLogo(UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    String oldKey = settings.getLogoS3Key();
    if (oldKey != null) {
      storageService.delete(oldKey);
      settings.setLogoS3Key(null);
      settings = orgSettingsRepository.save(settings);

      log.info("Deleted logo: {}", oldKey);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("org_settings.logo_deleted")
              .entityType("org_settings")
              .entityId(settings.getId())
              .details(Map.of("deleted_s3_key", oldKey))
              .build());
    }

    return toSettingsResponse(settings);
  }

  /** Maps an OrgSettings entity to a SettingsResponse DTO including compliance and tax fields. */
  private SettingsResponse toSettingsResponse(OrgSettings settings) {
    String logoUrl = generateLogoUrl(settings.getLogoS3Key());
    return new SettingsResponse(
        settings.getDefaultCurrency(),
        logoUrl,
        settings.getBrandColor(),
        settings.getDocumentFooterText(),
        settings.getDormancyThresholdDays(),
        settings.getDataRequestDeadlineDays(),
        settings.getCompliancePackStatus(),
        settings.isAccountingEnabled(),
        settings.isAiEnabled(),
        settings.isDocumentSigningEnabled(),
        settings.getTaxRegistrationNumber(),
        settings.getTaxRegistrationLabel(),
        settings.getTaxLabel(),
        settings.isTaxInclusive(),
        settings.getAcceptanceExpiryDays(),
        settings.isTimeReminderEnabled(),
        settings.getTimeReminderDays(),
        settings.getTimeReminderTime() != null ? settings.getTimeReminderTime().toString() : null,
        settings.getTimeReminderMinHours(),
        settings.getDefaultExpenseMarkupPercent());
  }

  /**
   * Returns the stored default currency for the current tenant, or "USD" if no settings row exists.
   * Useful for other services that need the org default currency.
   */
  @Transactional(readOnly = true)
  public String getDefaultCurrency() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getDefaultCurrency)
        .orElse(DEFAULT_CURRENCY);
  }

  /** Updates compliance-related settings (dormancy threshold, data request deadline). */
  @Transactional
  public SettingsResponse updateComplianceSettings(
      Integer dormancyThresholdDays,
      Integer dataRequestDeadlineDays,
      UUID memberId,
      String orgRole) {
    requireAdminOrOwner(orgRole);

    if (dormancyThresholdDays == null && dataRequestDeadlineDays == null) {
      return getSettingsWithBranding();
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    if (dormancyThresholdDays != null) {
      settings.setDormancyThresholdDays(dormancyThresholdDays);
    }
    if (dataRequestDeadlineDays != null) {
      settings.setDataRequestDeadlineDays(dataRequestDeadlineDays);
    }
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated compliance settings: dormancyThresholdDays={}, dataRequestDeadlineDays={}",
        dormancyThresholdDays,
        dataRequestDeadlineDays);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.compliance_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "dormancy_threshold_days",
                    dormancyThresholdDays != null ? dormancyThresholdDays : "",
                    "data_request_deadline_days",
                    dataRequestDeadlineDays != null ? dataRequestDeadlineDays : ""))
            .build());

    return toSettingsResponse(settings);
  }

  /** Updates tax configuration settings. */
  @Transactional
  public SettingsResponse updateTaxSettings(
      String taxRegistrationNumber,
      String taxRegistrationLabel,
      String taxLabel,
      boolean taxInclusive,
      UUID memberId,
      String orgRole) {
    requireAdminOrOwner(orgRole);

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var s = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(s);
                });

    settings.updateTaxSettings(taxRegistrationNumber, taxRegistrationLabel, taxLabel, taxInclusive);
    settings = orgSettingsRepository.save(settings);

    log.info("Updated tax settings: taxLabel={}, taxInclusive={}", taxLabel, taxInclusive);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.tax_configured")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "tax_registration_number",
                    taxRegistrationNumber != null ? taxRegistrationNumber : "",
                    "tax_registration_label",
                    taxRegistrationLabel != null ? taxRegistrationLabel : "",
                    "tax_label",
                    taxLabel != null ? taxLabel : "",
                    "tax_inclusive",
                    taxInclusive))
            .build());

    return toSettingsResponse(settings);
  }

  /** Updates acceptance-related settings (expiry days). */
  @Transactional
  public SettingsResponse updateAcceptanceSettings(
      Integer acceptanceExpiryDays, UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    if (acceptanceExpiryDays == null) {
      return getSettingsWithBranding();
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    settings.setAcceptanceExpiryDays(acceptanceExpiryDays);
    settings = orgSettingsRepository.save(settings);

    log.info("Updated acceptance settings: acceptanceExpiryDays={}", acceptanceExpiryDays);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.acceptance_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("acceptance_expiry_days", acceptanceExpiryDays))
            .build());

    return toSettingsResponse(settings);
  }

  /** Updates time reminder settings. */
  @Transactional
  public SettingsResponse updateTimeReminderSettings(
      Boolean timeReminderEnabled,
      String timeReminderDays,
      String timeReminderTimeStr,
      Integer timeReminderMinMinutes,
      UUID memberId,
      String orgRole) {
    requireAdminOrOwner(orgRole);

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    LocalTime timeReminderTime =
        timeReminderTimeStr != null
            ? LocalTime.parse(timeReminderTimeStr)
            : settings.getTimeReminderTime();

    settings.updateTimeReminderSettings(
        timeReminderEnabled != null ? timeReminderEnabled : settings.isTimeReminderEnabled(),
        timeReminderDays != null ? timeReminderDays : settings.getTimeReminderDays(),
        timeReminderTime,
        timeReminderMinMinutes != null
            ? timeReminderMinMinutes
            : settings.getTimeReminderMinMinutes());
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated time reminder settings: enabled={}, days={}, time={}, minMinutes={}",
        settings.isTimeReminderEnabled(),
        settings.getTimeReminderDays(),
        settings.getTimeReminderTime(),
        settings.getTimeReminderMinMinutes());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.time_reminders_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "time_reminder_enabled",
                    settings.isTimeReminderEnabled(),
                    "time_reminder_days",
                    settings.getTimeReminderDays() != null ? settings.getTimeReminderDays() : ""))
            .build());

    return toSettingsResponse(settings);
  }

  private void requireAdminOrOwner(String orgRole) {
    if (!Roles.ORG_ADMIN.equals(orgRole) && !Roles.ORG_OWNER.equals(orgRole)) {
      throw new ForbiddenException(
          "Insufficient permissions", "Only admins and owners can update org settings");
    }
  }

  private String generateLogoUrl(String logoS3Key) {
    if (logoS3Key == null) {
      return null;
    }
    try {
      return storageService.generateDownloadUrl(logoS3Key, LOGO_URL_EXPIRY).url();
    } catch (RuntimeException e) {
      log.warn("Failed to generate logo download URL for key: {}", logoS3Key, e);
      return null;
    }
  }

  private static final Map<String, String> CONTENT_TYPE_TO_EXT =
      Map.of("image/png", "png", "image/jpeg", "jpg", "image/svg+xml", "svg");

  private String extensionFromContentType(String contentType) {
    if (contentType == null) {
      return "png"; // default
    }
    return CONTENT_TYPE_TO_EXT.getOrDefault(contentType, "png");
  }

  /** Response DTO for org settings. */
  public record OrgSettingsResponse(String defaultCurrency) {}
}
