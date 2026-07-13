package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.datarequest.ComplianceTemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.datarequest.JurisdictionDefaults;
import io.b2mash.b2b.b2bstrawman.datarequest.ProcessingActivityService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.retention.RetentionService;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.seeder.ProjectTemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.RatePackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.SchedulePackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.dto.CollectionsSettingsResponse;
import io.b2mash.b2b.b2bstrawman.settings.dto.DataProtectionSettingsRequest;
import io.b2mash.b2b.b2bstrawman.settings.dto.ModuleSettingsResponse;
import io.b2mash.b2b.b2bstrawman.settings.dto.ModuleSettingsResponse.ModuleStatus;
import io.b2mash.b2b.b2bstrawman.settings.dto.SettingsResponse;
import io.b2mash.b2b.b2bstrawman.verticals.ModuleCategory;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleRegistry;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// TODO(BE-013): This service exceeds 800 lines — decompose when adding new features
@Service
public class OrgSettingsService {

  private static final Logger log = LoggerFactory.getLogger(OrgSettingsService.class);
  private static final String DEFAULT_CURRENCY = "USD";
  private static final BigDecimal DEFAULT_WEEKLY_CAPACITY_HOURS = new BigDecimal("40.00");
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);
  private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2MB
  private static final java.util.Set<String> ALLOWED_LOGO_CONTENT_TYPES =
      java.util.Set.of("image/png", "image/jpeg", "image/svg+xml");

  private final OrgSettingsRepository orgSettingsRepository;
  private final OrganizationRepository organizationRepository;
  private final AuditService auditService;
  private final StorageService storageService;
  private final VerticalProfileRegistry verticalProfileRegistry;
  private final RetentionService retentionService;
  private final ProcessingActivityService processingActivityService;
  private final ComplianceTemplatePackSeeder complianceTemplatePackSeeder;
  private final ProjectTemplatePackSeeder projectTemplatePackSeeder;
  private final RatePackSeeder ratePackSeeder;
  private final SchedulePackSeeder schedulePackSeeder;
  private final VerticalModuleRegistry moduleRegistry;

  public OrgSettingsService(
      OrgSettingsRepository orgSettingsRepository,
      OrganizationRepository organizationRepository,
      AuditService auditService,
      StorageService storageService,
      VerticalProfileRegistry verticalProfileRegistry,
      RetentionService retentionService,
      ProcessingActivityService processingActivityService,
      ComplianceTemplatePackSeeder complianceTemplatePackSeeder,
      ProjectTemplatePackSeeder projectTemplatePackSeeder,
      RatePackSeeder ratePackSeeder,
      SchedulePackSeeder schedulePackSeeder,
      VerticalModuleRegistry moduleRegistry) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.organizationRepository = organizationRepository;
    this.auditService = auditService;
    this.storageService = storageService;
    this.verticalProfileRegistry = verticalProfileRegistry;
    this.retentionService = retentionService;
    this.processingActivityService = processingActivityService;
    this.complianceTemplatePackSeeder = complianceTemplatePackSeeder;
    this.projectTemplatePackSeeder = projectTemplatePackSeeder;
    this.ratePackSeeder = ratePackSeeder;
    this.schedulePackSeeder = schedulePackSeeder;
    this.moduleRegistry = moduleRegistry;
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
                resolveOrgName(),
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
                5, // defaultRequestReminderDays
                false, // timeReminderEnabled
                "MON,TUE,WED,THU,FRI", // timeReminderDays
                "17:00", // timeReminderTime
                4.0, // timeReminderMinHours
                null, // defaultExpenseMarkupPercent
                DEFAULT_WEEKLY_CAPACITY_HOURS, // defaultWeeklyCapacityHours
                50, // billingBatchAsyncThreshold
                5, // billingEmailRateLimit
                null, // defaultBillingRunCurrency
                null, // projectNamingPattern
                null, // verticalProfile
                List.of(), // enabledModules
                null, // terminologyNamespace
                null, // dataProtectionJurisdiction
                false, // retentionPolicyEnabled
                null, // defaultRetentionMonths
                null, // financialRetentionMonths
                null, // informationOfficerName
                null, // informationOfficerEmail
                PortalRetainerMemberDisplay.FIRST_NAME_ROLE.name(), // portalRetainerMemberDisplay
                PortalDigestCadence.WEEKLY.name())); // portalDigestCadence
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
      String projectNamingPattern,
      ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    var existing = orgSettingsRepository.findForCurrentTenant();

    OrgSettings settings;

    if (existing.isPresent()) {
      settings = existing.get();
      settings.updateCurrency(defaultCurrency);
    } else {
      settings = new OrgSettings(defaultCurrency);
    }

    settings.getBranding().setBrandColor(brandColor);
    settings.getBranding().setDocumentFooterText(documentFooterText);

    // Update integration flags if provided (null = don't change)
    settings.updateIntegrationFlags(
        accountingEnabled != null ? accountingEnabled : settings.isAccountingEnabled(),
        aiEnabled != null ? aiEnabled : settings.isAiEnabled(),
        documentSigningEnabled != null
            ? documentSigningEnabled
            : settings.isDocumentSigningEnabled());

    if (projectNamingPattern != null) {
      settings.setProjectNamingPattern(projectNamingPattern);
    }

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

  /** Uploads a logo to storage and updates the org settings. Validates file constraints first. */
  @Transactional
  public SettingsResponse uploadLogo(MultipartFile file, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    if (file.isEmpty()) {
      throw new InvalidStateException("Invalid file", "File is empty");
    }
    if (file.getSize() > MAX_LOGO_SIZE) {
      throw new InvalidStateException("File too large", "Logo file must be under 2MB");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_LOGO_CONTENT_TYPES.contains(contentType)) {
      throw new InvalidStateException("Invalid file type", "Logo must be PNG, JPG, or SVG");
    }

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

      settings.getBranding().setLogoS3Key(s3Key);
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
  public SettingsResponse deleteLogo(ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    String oldKey = settings.getBranding().getLogoS3Key();
    if (oldKey != null) {
      storageService.delete(oldKey);
      settings.getBranding().setLogoS3Key(null);
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

  /** Resolves the org display name from the organizations table via the current ORG_ID scope. */
  private String resolveOrgName() {
    String orgId = RequestScopes.getOrgIdOrNull();
    if (orgId == null) {
      return null;
    }
    return organizationRepository.findByExternalOrgId(orgId).map(org -> org.getName()).orElse(null);
  }

  /** Maps an OrgSettings entity to a SettingsResponse DTO including compliance and tax fields. */
  private SettingsResponse toSettingsResponse(OrgSettings settings) {
    String logoUrl = generateLogoUrl(settings.getBranding().getLogoS3Key());
    return new SettingsResponse(
        resolveOrgName(),
        settings.getDefaultCurrency(),
        logoUrl,
        settings.getBranding().getBrandColor(),
        settings.getBranding().getDocumentFooterText(),
        settings.getDataRequest().getDormancyThresholdDays(),
        settings.getDataRequest().getDataRequestDeadlineDays(),
        settings.getPackStatus().getCompliancePackStatus(),
        settings.isAccountingEnabled(),
        settings.isAiEnabled(),
        settings.isDocumentSigningEnabled(),
        settings.getTax().getTaxRegistrationNumber(),
        settings.getTax().getTaxRegistrationLabel(),
        settings.getTax().getTaxLabel(),
        settings.getTax().isTaxInclusive(),
        settings.getAcceptanceExpiryDays(),
        settings.getDataRequest().getDefaultRequestReminderDays(),
        settings.getTimeReminder().isTimeReminderEnabled(),
        settings.getTimeReminder().getTimeReminderDays(),
        settings.getTimeReminder().getTimeReminderTime() != null
            ? settings.getTimeReminder().getTimeReminderTime().toString()
            : null,
        settings.getTimeReminder().getTimeReminderMinHours(),
        settings.getExpense().getDefaultExpenseMarkupPercent(),
        settings.getCapacity().getDefaultWeeklyCapacityHours() != null
            ? settings.getCapacity().getDefaultWeeklyCapacityHours()
            : DEFAULT_WEEKLY_CAPACITY_HOURS,
        settings.getBilling().getBillingBatchAsyncThreshold(),
        settings.getBilling().getBillingEmailRateLimit(),
        settings.getBilling().getDefaultBillingRunCurrency(),
        settings.getProjectNamingPattern(),
        settings.getVerticalProfile(),
        settings.getEnabledModules(),
        settings.getTerminologyNamespace(),
        settings.getDataProtection().getDataProtectionJurisdiction(),
        settings.getDataProtection().isRetentionPolicyEnabled(),
        settings.getDataProtection().getDefaultRetentionMonths(),
        settings.getDataProtection().getFinancialRetentionMonths(),
        settings.getDataProtection().getInformationOfficerName(),
        settings.getDataProtection().getInformationOfficerEmail(),
        settings.getPortal().getEffectivePortalRetainerMemberDisplay().name(),
        settings.getPortal().getEffectivePortalDigestCadence().name());
  }

  /**
   * Returns the OrgSettings for the current tenant, creating a default row (with "USD" currency) if
   * none exists. Centralises the get-or-create pattern used across settings mutations.
   */
  @Transactional
  public OrgSettings getOrCreateForCurrentTenant() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .orElseGet(
            () -> {
              var newSettings = new OrgSettings(DEFAULT_CURRENCY);
              return orgSettingsRepository.save(newSettings);
            });
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

  /**
   * Returns the raw {@code legal_matter_retention_years} value for the current tenant — without
   * applying the {@link DataProtectionSettings#DEFAULT_LEGAL_MATTER_RETENTION_YEARS} fallback.
   * Returns {@link Optional#empty()} when no settings row exists, when the column is null, or when
   * the stored value is &le; 0 (defensive — the DB CHECK enforces &ge; 1, but legacy rows or future
   * schema widening shouldn't surface a misleading retention end-date).
   *
   * <p>Used by the matter detail endpoint (GAP-OBS-Day60-RetentionShape) where a missing/zero value
   * must surface as {@code retentionEndsOn = null} rather than masking a misconfigured tenant with
   * the silent 5-year default.
   */
  @Transactional(readOnly = true)
  public Optional<Integer> getRawLegalMatterRetentionYears() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> s.getDataProtection().getLegalMatterRetentionYears())
        .filter(years -> years != null && years > 0);
  }

  /**
   * Returns the enabled module IDs for the current tenant. Returns an empty list if no settings row
   * exists. Hibernate L1 cache ensures at most one DB read per request.
   */
  @Transactional(readOnly = true)
  public List<String> getEnabledModulesForCurrentTenant() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getEnabledModules)
        .orElse(List.of());
  }

  /**
   * Returns all horizontal modules with their current enabled state for the current tenant. Used to
   * render Settings → Features. Vertical modules are intentionally excluded.
   */
  @Transactional(readOnly = true)
  public ModuleSettingsResponse getHorizontalModuleSettings() {
    Set<String> enabledSet = new HashSet<>(getEnabledModulesForCurrentTenant());
    List<ModuleStatus> statuses =
        moduleRegistry.getHorizontalModules().stream()
            .map(
                m ->
                    new ModuleStatus(
                        m.id(), m.name(), m.description(), enabledSet.contains(m.id())))
            .toList();
    return new ModuleSettingsResponse(statuses);
  }

  /**
   * Replaces the set of enabled horizontal modules for the current tenant. All other enabled ids
   * are preserved: vertical modules (managed by the vertical profile) and ids unknown to the module
   * registry (profile-owned slugs such as {@code deadlines}, owned by {@code
   * PortalDeadlineService#MODULE_ID}). Admin-or-owner only. See ADR-239.
   *
   * @throws InvalidStateException if any requested ID is unknown or is a vertical module
   * @throws io.b2mash.b2b.b2bstrawman.exception.ForbiddenException if the actor is not admin/owner
   */
  @Transactional
  public SettingsResponse updateHorizontalModules(
      List<String> requestedModuleIds, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    // Validate every requested ID exists in the registry and is categorised HORIZONTAL.
    for (String id : requestedModuleIds) {
      var module =
          moduleRegistry
              .getModule(id)
              .orElseThrow(
                  () -> new InvalidStateException("Unknown module", "Unknown module ID: " + id));
      if (module.category() != ModuleCategory.HORIZONTAL) {
        throw new InvalidStateException(
            "Invalid module",
            "Module " + id + " cannot be toggled manually (managed by vertical profile)");
      }
    }

    var settings = getOrCreateForCurrentTenant();
    List<String> before = List.copyOf(settings.getEnabledModules());

    // Only the horizontal subset is replaceable via this endpoint. Preserve everything else:
    // vertical modules AND ids unknown to the registry — profile-owned slugs such as
    // PortalDeadlineService.MODULE_ID ("deadlines") are deliberately not registered here
    // (LZKC-026: dropping unknown ids clobbered "deadlines" on every features-page save).
    List<String> preservedIds =
        before.stream()
            .filter(
                id ->
                    moduleRegistry
                        .getModule(id)
                        .map(m -> m.category() != ModuleCategory.HORIZONTAL)
                        .orElse(true))
            .toList();
    List<String> merged = new ArrayList<>(preservedIds);
    for (String id : requestedModuleIds) {
      if (!merged.contains(id)) {
        merged.add(id);
      }
    }

    settings.setEnabledModules(merged);
    settings = orgSettingsRepository.save(settings);

    List<String> added = new ArrayList<>(merged);
    added.removeAll(before);
    List<String> removed = new ArrayList<>(before);
    removed.removeAll(merged);

    log.info(
        "Updated horizontal modules: before={}, after={}, added={}, removed={}",
        before,
        merged,
        added,
        removed);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.modules_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "before", before,
                    "after", merged,
                    "added", added,
                    "removed", removed))
            .build());

    return toSettingsResponse(settings);
  }

  /** Updates compliance-related settings (dormancy threshold, data request deadline). */
  @Transactional
  public SettingsResponse updateComplianceSettings(
      Integer dormancyThresholdDays, Integer dataRequestDeadlineDays, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

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
      settings.getDataRequest().setDormancyThresholdDays(dormancyThresholdDays);
    }
    if (dataRequestDeadlineDays != null) {
      settings.getDataRequest().setDataRequestDeadlineDays(dataRequestDeadlineDays);
    }
    settings.touchUpdatedAt();
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
      ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var s = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(s);
                });

    settings
        .getTax()
        .updateTaxSettings(taxRegistrationNumber, taxRegistrationLabel, taxLabel, taxInclusive);
    settings.touchUpdatedAt();
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
      Integer acceptanceExpiryDays, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

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

  /**
   * Updates the firm-wide default expense markup percentage. Requires admin or owner role.
   *
   * <p>This single-field endpoint treats an explicit {@code null} as a request to CLEAR the markup
   * (set the column to {@code null}). This diverges from the multi-field sibling endpoints (e.g.
   * time-reminders) where {@code null} means "keep existing" — the time-tracking form lets the
   * operator empty the markup input, which must clear the stored value, not no-op.
   */
  @Transactional
  public SettingsResponse updateExpenseSettings(
      BigDecimal defaultExpenseMarkupPercent, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var s = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(s);
                });

    settings.getExpense().setDefaultExpenseMarkupPercent(defaultExpenseMarkupPercent);
    settings.touchUpdatedAt();
    settings = orgSettingsRepository.save(settings);

    log.info("Updated default expense markup percent to {}", defaultExpenseMarkupPercent);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.expense_markup_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "default_expense_markup_percent",
                    defaultExpenseMarkupPercent != null
                        ? defaultExpenseMarkupPercent.toString()
                        : ""))
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
      ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

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
            : settings.getTimeReminder().getTimeReminderTime();

    settings
        .getTimeReminder()
        .updateTimeReminderSettings(
            timeReminderEnabled != null
                ? timeReminderEnabled
                : settings.getTimeReminder().isTimeReminderEnabled(),
            timeReminderDays != null
                ? timeReminderDays
                : settings.getTimeReminder().getTimeReminderDays(),
            timeReminderTime,
            timeReminderMinMinutes != null
                ? timeReminderMinMinutes
                : settings.getTimeReminder().getTimeReminderMinMinutes());
    settings.touchUpdatedAt();
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated time reminder settings: enabled={}, days={}, time={}, minMinutes={}",
        settings.getTimeReminder().isTimeReminderEnabled(),
        settings.getTimeReminder().getTimeReminderDays(),
        settings.getTimeReminder().getTimeReminderTime(),
        settings.getTimeReminder().getTimeReminderMinMinutes());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.time_reminders_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "time_reminder_enabled",
                    settings.getTimeReminder().isTimeReminderEnabled(),
                    "time_reminder_days",
                    settings.getTimeReminder().getTimeReminderDays() != null
                        ? settings.getTimeReminder().getTimeReminderDays()
                        : ""))
            .build());

    return toSettingsResponse(settings);
  }

  // --- Collections / dunning policy (Phase 83, Epic 588B) ---

  /**
   * Reads the collections / dunning policy group (Phase 83, §4.2). Member-readable. Returns
   * baked-in defaults ({@code false, 7, 21, 45, 60}) for a fresh tenant with no {@code
   * org_settings} row yet — without persisting — mirroring {@link #getSettingsWithBranding()}.
   */
  @Transactional(readOnly = true)
  public CollectionsSettingsResponse getCollectionsSettings() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> toCollectionsResponse(s.getCollections()))
        .orElse(new CollectionsSettingsResponse(false, 7, 21, 45, 60));
  }

  /**
   * Updates the collections / dunning policy (Phase 83, §4.2). Admin/owner only. Full-replace
   * semantics: all five fields are meaningful on every call. Thresholds are validated service-side
   * — each ≥ 1 and strictly increasing (stage1 &lt; stage2 &lt; stage3 &lt; escalate) — as a
   * semantic rule, not a DB constraint. Audits {@code collections.policy.updated} with old/new
   * numbers only (POPIA: no PII in details).
   */
  @Transactional
  public CollectionsSettingsResponse updateCollectionsSettings(
      boolean collectionsEnabled,
      Integer stage1DaysOverdue,
      Integer stage2DaysOverdue,
      Integer stage3DaysOverdue,
      Integer escalateDaysOverdue,
      ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());
    validateCollectionsThresholds(
        stage1DaysOverdue, stage2DaysOverdue, stage3DaysOverdue, escalateDaysOverdue);

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    // Capture OLD values BEFORE mutating so the audit delta is accurate.
    var collections = settings.getCollections();
    boolean oldEnabled = collections.isCollectionsEnabled();
    Integer oldStage1 = collections.getStage1DaysOverdue();
    Integer oldStage2 = collections.getStage2DaysOverdue();
    Integer oldStage3 = collections.getStage3DaysOverdue();
    Integer oldEscalate = collections.getEscalateDaysOverdue();

    collections.updateCollectionsSettings(
        collectionsEnabled,
        stage1DaysOverdue,
        stage2DaysOverdue,
        stage3DaysOverdue,
        escalateDaysOverdue);
    settings.touchUpdatedAt();
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated collections policy: enabled={}, stage1={}, stage2={}, stage3={}, escalate={}",
        collectionsEnabled,
        stage1DaysOverdue,
        stage2DaysOverdue,
        stage3DaysOverdue,
        escalateDaysOverdue);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("collections.policy.updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "collections_enabled", Map.of("from", oldEnabled, "to", collectionsEnabled),
                    "stage1_days", Map.of("from", oldStage1, "to", stage1DaysOverdue),
                    "stage2_days", Map.of("from", oldStage2, "to", stage2DaysOverdue),
                    "stage3_days", Map.of("from", oldStage3, "to", stage3DaysOverdue),
                    "escalate_days", Map.of("from", oldEscalate, "to", escalateDaysOverdue)))
            .build());

    return toCollectionsResponse(settings.getCollections());
  }

  /**
   * Enforces the collections-threshold invariant service-side (Phase 83, §2.1): each threshold ≥ 1
   * and strictly increasing. Semantic exception → HTTP 400.
   */
  private void validateCollectionsThresholds(
      Integer stage1, Integer stage2, Integer stage3, Integer escalate) {
    if (stage1 == null
        || stage2 == null
        || stage3 == null
        || escalate == null
        || stage1 < 1
        || stage2 < 1
        || stage3 < 1
        || escalate < 1) {
      throw new InvalidStateException(
          "Invalid collections thresholds", "Each days-overdue threshold must be at least 1");
    }
    if (!(stage1 < stage2 && stage2 < stage3 && stage3 < escalate)) {
      throw new InvalidStateException(
          "Invalid collections thresholds",
          "Thresholds must be strictly increasing: stage1 < stage2 < stage3 < escalate");
    }
  }

  private static CollectionsSettingsResponse toCollectionsResponse(CollectionsSettings c) {
    return new CollectionsSettingsResponse(
        c.isCollectionsEnabled(),
        c.getStage1DaysOverdue(),
        c.getStage2DaysOverdue(),
        c.getStage3DaysOverdue(),
        c.getEscalateDaysOverdue());
  }

  /**
   * Returns the org default weekly capacity hours, falling back to 40.0 if no settings row or null
   * field.
   */
  @Transactional(readOnly = true)
  public BigDecimal getDefaultWeeklyCapacityHours() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> s.getCapacity().getDefaultWeeklyCapacityHours())
        .orElse(DEFAULT_WEEKLY_CAPACITY_HOURS);
  }

  /** Updates the default weekly capacity hours. Requires admin or owner role. */
  @Transactional
  public SettingsResponse updateDefaultWeeklyCapacityHours(BigDecimal hours, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var s = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(s);
                });

    settings.getCapacity().setDefaultWeeklyCapacityHours(hours);
    settings.touchUpdatedAt();
    settings = orgSettingsRepository.save(settings);

    log.info("Updated default weekly capacity hours to {}", hours);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.capacity_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("default_weekly_capacity_hours", hours.toString()))
            .build());

    return toSettingsResponse(settings);
  }

  /** Updates batch billing settings. */
  @Transactional
  public SettingsResponse updateBatchBillingSettings(
      Integer billingBatchAsyncThreshold,
      Integer billingEmailRateLimit,
      String defaultBillingRunCurrency,
      ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    if (billingBatchAsyncThreshold == null
        && billingEmailRateLimit == null
        && defaultBillingRunCurrency == null) {
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

    if (billingBatchAsyncThreshold != null) {
      settings.getBilling().setBillingBatchAsyncThreshold(billingBatchAsyncThreshold);
    }
    if (billingEmailRateLimit != null) {
      settings.getBilling().setBillingEmailRateLimit(billingEmailRateLimit);
    }
    if (defaultBillingRunCurrency != null) {
      settings.getBilling().setDefaultBillingRunCurrency(defaultBillingRunCurrency);
    }
    settings.touchUpdatedAt();
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated batch billing settings: asyncThreshold={}, rateLimit={}, currency={}",
        billingBatchAsyncThreshold,
        billingEmailRateLimit,
        defaultBillingRunCurrency);

    var auditDetails = new java.util.HashMap<String, Object>();
    if (billingBatchAsyncThreshold != null) {
      auditDetails.put("billing_batch_async_threshold", billingBatchAsyncThreshold);
    }
    if (billingEmailRateLimit != null) {
      auditDetails.put("billing_email_rate_limit", billingEmailRateLimit);
    }
    if (defaultBillingRunCurrency != null) {
      auditDetails.put("default_billing_run_currency", defaultBillingRunCurrency);
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.batch_billing_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(auditDetails)
            .build());

    return toSettingsResponse(settings);
  }

  /**
   * Updates the vertical profile, setting enabledModules and terminologyNamespace from registry.
   * After saving, triggers rate, project template, and schedule pack seeders for the new profile.
   * Seeding is idempotent — switching to the same profile multiple times is safe.
   */
  @Transactional
  public SettingsResponse updateVerticalProfile(String verticalProfile, ActorContext actor) {
    RequestScopes.requireOwner();

    List<String> enabledModules;
    String terminologyNamespace;

    if (verticalProfile != null) {
      var profileDef =
          verticalProfileRegistry
              .getProfile(verticalProfile)
              .orElseThrow(
                  () ->
                      new InvalidStateException(
                          "Invalid profile", "Unknown vertical profile: " + verticalProfile));
      enabledModules = profileDef.enabledModules();
      terminologyNamespace = profileDef.terminologyNamespace();
    } else {
      enabledModules = List.of();
      terminologyNamespace = null;
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    // Preserve horizontal modules across profile changes (ADR-239). Vertical modules from the new
    // profile fully replace the previous vertical set; horizontal modules are independent of the
    // profile and must survive profile switches (including clearing to null).
    List<String> currentHorizontal =
        settings.getEnabledModules().stream()
            .filter(
                id ->
                    moduleRegistry
                        .getModule(id)
                        .map(m -> m.category() == ModuleCategory.HORIZONTAL)
                        .orElse(false))
            .toList();
    List<String> mergedModules = new ArrayList<>(enabledModules);
    for (String id : currentHorizontal) {
      if (!mergedModules.contains(id)) {
        mergedModules.add(id);
      }
    }

    String oldProfile = settings.getVerticalProfile();
    settings.updateVerticalProfile(verticalProfile, mergedModules, terminologyNamespace);
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated vertical profile: {} -> {}, enabledModules={}",
        oldProfile,
        verticalProfile,
        mergedModules);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.vertical_profile_changed")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "old_profile", oldProfile != null ? oldProfile : "",
                    "new_profile", verticalProfile != null ? verticalProfile : "",
                    "enabled_modules", mergedModules))
            .build());

    // Trigger rate and schedule pack seeding for the new profile (idempotent).
    // The seeders use TenantTransactionHelper which joins the current transaction
    // and reads the updated OrgSettings from Hibernate's L1 cache.
    String tenantId = RequestScopes.TENANT_ID.get();
    String orgId = RequestScopes.ORG_ID.get();
    ratePackSeeder.seedPacksForTenant(tenantId, orgId);
    projectTemplatePackSeeder.seedPacksForTenant(tenantId, orgId);
    schedulePackSeeder.seedPacksForTenant(tenantId, orgId);

    return toSettingsResponse(settings);
  }

  /**
   * Updates data protection settings (jurisdiction, retention config, information officer).
   * OWNER-only — validates financial retention minimum against jurisdiction statutory minimum.
   */
  @Transactional
  public SettingsResponse updateDataProtectionSettings(
      DataProtectionSettingsRequest request, ActorContext actor) {
    RequestScopes.requireOwner();

    var settings = getOrCreateForCurrentTenant();

    // Capture old jurisdiction before updating
    String oldJurisdiction = settings.getDataProtection().getDataProtectionJurisdiction();

    // Validate financial retention minimum against jurisdiction
    String jurisdiction =
        request.dataProtectionJurisdiction() != null
            ? request.dataProtectionJurisdiction()
            : settings.getDataProtection().getDataProtectionJurisdiction();
    if (request.financialRetentionMonths() != null) {
      int minMonths = JurisdictionDefaults.getMinFinancialRetentionMonths(jurisdiction);
      if (request.financialRetentionMonths() < minMonths) {
        throw new InvalidStateException(
            "Financial retention too short",
            "financialRetentionMonths must be at least "
                + minMonths
                + " months for jurisdiction "
                + jurisdiction);
      }
    }

    settings
        .getDataProtection()
        .updateDataProtectionSettings(
            request.dataProtectionJurisdiction() != null
                ? request.dataProtectionJurisdiction()
                : settings.getDataProtection().getDataProtectionJurisdiction(),
            request.retentionPolicyEnabled() != null
                ? request.retentionPolicyEnabled()
                : settings.getDataProtection().isRetentionPolicyEnabled(),
            request.defaultRetentionMonths() != null
                ? request.defaultRetentionMonths()
                : settings.getDataProtection().getDefaultRetentionMonths(),
            request.financialRetentionMonths() != null
                ? request.financialRetentionMonths()
                : settings.getDataProtection().getFinancialRetentionMonths(),
            request.informationOfficerName() != null
                ? request.informationOfficerName()
                : settings.getDataProtection().getInformationOfficerName(),
            request.informationOfficerEmail() != null
                ? request.informationOfficerEmail()
                : settings.getDataProtection().getInformationOfficerEmail());
    settings.touchUpdatedAt();
    settings = orgSettingsRepository.save(settings);

    // Seed jurisdiction defaults when jurisdiction is first set
    String newJurisdiction = settings.getDataProtection().getDataProtectionJurisdiction();
    if (oldJurisdiction == null && newJurisdiction != null) {
      retentionService.seedJurisdictionDefaults(newJurisdiction);
      processingActivityService.seedJurisdictionDefaults(newJurisdiction);
      complianceTemplatePackSeeder.seedCompliancePack(newJurisdiction);
    }

    log.info(
        "Updated data protection settings: jurisdiction={}, retentionEnabled={}",
        settings.getDataProtection().getDataProtectionJurisdiction(),
        settings.getDataProtection().isRetentionPolicyEnabled());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.protection.settings.updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "jurisdiction",
                    settings.getDataProtection().getDataProtectionJurisdiction() != null
                        ? settings.getDataProtection().getDataProtectionJurisdiction()
                        : "",
                    "retention_enabled",
                    settings.getDataProtection().isRetentionPolicyEnabled(),
                    "member_id",
                    actor.memberId().toString(),
                    "org_role",
                    actor.orgRole()))
            .build());

    return toSettingsResponse(settings);
  }

  /**
   * Returns the effective portal-retainer member-display mode for the current tenant (ADR-255, Epic
   * 496A). Falls back to {@link PortalRetainerMemberDisplay#FIRST_NAME_ROLE} when no settings row
   * exists or the column is null.
   */
  @Transactional(readOnly = true)
  public PortalRetainerMemberDisplay getPortalRetainerMemberDisplay() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> s.getPortal().getEffectivePortalRetainerMemberDisplay())
        .orElse(PortalRetainerMemberDisplay.FIRST_NAME_ROLE);
  }

  /**
   * Updates the portal-retainer member-display privacy toggle (ADR-255, Epic 496A). Admin-or-owner
   * only. Emits an audit event.
   */
  @Transactional
  public SettingsResponse updatePortalRetainerMemberDisplay(
      PortalRetainerMemberDisplay mode, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    if (mode == null) {
      throw new InvalidStateException(
          "Missing field", "portalRetainerMemberDisplay must not be null");
    }

    var settings = getOrCreateForCurrentTenant();
    settings.getPortal().setPortalRetainerMemberDisplay(mode);
    settings = orgSettingsRepository.save(settings);

    log.info("Updated portal retainer member display mode to {}", mode);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.portal_retainer_member_display_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("portal_retainer_member_display", mode.name()))
            .build());

    return toSettingsResponse(settings);
  }

  /**
   * Returns the effective portal digest cadence for the current tenant (ADR-258, Epic 498A). Falls
   * back to {@link PortalDigestCadence#WEEKLY} when no settings row exists or the column is null.
   */
  @Transactional(readOnly = true)
  public PortalDigestCadence getPortalDigestCadence() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> s.getPortal().getEffectivePortalDigestCadence())
        .orElse(PortalDigestCadence.WEEKLY);
  }

  /**
   * Updates the firm-wide portal digest cadence (ADR-258, Epic 498A). Admin-or-owner only. Emits an
   * audit event. Returns the full {@link SettingsResponse} so callers can re-hydrate the settings
   * view in a single round-trip — mirrors the shape of {@link
   * #updatePortalRetainerMemberDisplay(PortalRetainerMemberDisplay, ActorContext)}.
   */
  @Transactional
  public SettingsResponse updatePortalDigestCadence(
      PortalDigestCadence cadence, ActorContext actor) {
    requireAdminOrOwner(actor.orgRole());

    if (cadence == null) {
      throw new InvalidStateException("Missing field", "portalDigestCadence must not be null");
    }

    var settings = getOrCreateForCurrentTenant();
    settings.getPortal().setPortalDigestCadence(cadence);
    settings = orgSettingsRepository.save(settings);

    log.info("Updated portal digest cadence to {}", cadence);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.portal_digest_cadence_updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("portal_digest_cadence", cadence.name()))
            .build());

    return toSettingsResponse(settings);
  }

  /**
   * Returns effective DSAR deadline days. If tenant override is set, caps it at the jurisdiction
   * maximum. Otherwise uses the jurisdiction default. See ADR-195.
   */
  @Transactional(readOnly = true)
  public int getEffectiveDsarDeadlineDays() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(this::resolveDeadlineDays)
        .orElse(JurisdictionDefaults.getDefaultDeadlineDays(null));
  }

  private int resolveDeadlineDays(OrgSettings settings) {
    Integer tenantOverride = settings.getDataRequest().getDataRequestDeadlineDays();
    String jurisdiction = settings.getDataProtection().getDataProtectionJurisdiction();
    if (tenantOverride != null && tenantOverride > 0) {
      return Math.min(tenantOverride, JurisdictionDefaults.getMaxDeadlineDays(jurisdiction));
    }
    return JurisdictionDefaults.getDefaultDeadlineDays(jurisdiction);
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
