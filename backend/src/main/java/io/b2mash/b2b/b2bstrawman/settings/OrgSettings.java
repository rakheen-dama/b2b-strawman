package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "org_settings")
public class OrgSettings {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "default_currency", nullable = false, length = 3)
  private String defaultCurrency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> fieldPackStatus;

  @Column(name = "logo_s3_key", length = 500)
  private String logoS3Key;

  @Column(name = "brand_color", length = 7)
  private String brandColor;

  @Column(name = "document_footer_text", columnDefinition = "TEXT")
  private String documentFooterText;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "template_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> templatePackStatus;

  @Column(name = "dormancy_threshold_days")
  private Integer dormancyThresholdDays;

  @Column(name = "data_request_deadline_days")
  private Integer dataRequestDeadlineDays;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "compliance_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> compliancePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "report_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> reportPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "clause_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> clausePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> requestPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "automation_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> automationPackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rate_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> ratePackStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "schedule_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> schedulePackStatus;

  @Column(name = "default_request_reminder_days")
  private Integer defaultRequestReminderDays;

  @Column(name = "acceptance_expiry_days")
  private Integer acceptanceExpiryDays;

  @Column(name = "accounting_enabled", nullable = false)
  private boolean accountingEnabled;

  @Column(name = "ai_enabled", nullable = false)
  private boolean aiEnabled;

  @Column(name = "document_signing_enabled", nullable = false)
  private boolean documentSigningEnabled;

  @Column(name = "tax_inclusive", nullable = false)
  private boolean taxInclusive;

  @Column(name = "tax_registration_number", length = 50)
  private String taxRegistrationNumber;

  @Column(name = "tax_registration_label", length = 30)
  private String taxRegistrationLabel;

  @Column(name = "tax_label", length = 20)
  private String taxLabel;

  @DecimalMin(value = "0.00", message = "Default expense markup percent must be non-negative")
  @Column(name = "default_expense_markup_percent", precision = 5, scale = 2)
  private BigDecimal defaultExpenseMarkupPercent;

  @Column(name = "default_weekly_capacity_hours", precision = 5, scale = 2)
  private BigDecimal defaultWeeklyCapacityHours;

  @Column(name = "time_reminder_enabled", nullable = false)
  private boolean timeReminderEnabled;

  @Column(name = "time_reminder_days", length = 50)
  private String timeReminderDays;

  @Column(name = "time_reminder_time")
  private LocalTime timeReminderTime;

  @Column(name = "time_reminder_min_minutes")
  private Integer timeReminderMinMinutes;

  @Column(name = "billing_batch_async_threshold")
  private Integer billingBatchAsyncThreshold;

  @Column(name = "billing_email_rate_limit")
  private Integer billingEmailRateLimit;

  @Column(name = "default_billing_run_currency", length = 3)
  private String defaultBillingRunCurrency;

  @Column(name = "project_naming_pattern", length = 500)
  private String projectNamingPattern;

  @Column(name = "onboarding_dismissed_at")
  private Instant onboardingDismissedAt;

  @Column(name = "vertical_profile", length = 50)
  private String verticalProfile;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "enabled_modules", columnDefinition = "jsonb")
  private List<String> enabledModules = new ArrayList<>();

  @Column(name = "terminology_namespace", length = 100)
  private String terminologyNamespace;

  // --- Phase 50: Data Protection ---
  @Column(name = "data_protection_jurisdiction", length = 10)
  private String dataProtectionJurisdiction;

  @Column(name = "retention_policy_enabled", nullable = false)
  private boolean retentionPolicyEnabled;

  @Column(name = "default_retention_months")
  private Integer defaultRetentionMonths;

  @Column(name = "financial_retention_months")
  private Integer financialRetentionMonths = 60;

  @Column(name = "information_officer_name", length = 255)
  private String informationOfficerName;

  @Column(name = "information_officer_email", length = 255)
  private String informationOfficerEmail;

  protected OrgSettings() {}

  public OrgSettings(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.accountingEnabled = false;
    this.aiEnabled = false;
    this.documentSigningEnabled = false;
    this.taxInclusive = false;
    this.timeReminderEnabled = false;
    this.retentionPolicyEnabled = false;
    this.billingBatchAsyncThreshold = 50;
    this.billingEmailRateLimit = 5;
  }

  public void updateCurrency(String currency) {
    this.defaultCurrency = currency;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getDefaultCurrency() {
    return defaultCurrency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<Map<String, Object>> getFieldPackStatus() {
    return fieldPackStatus;
  }

  public void setFieldPackStatus(List<Map<String, Object>> fieldPackStatus) {
    this.fieldPackStatus = fieldPackStatus;
  }

  /** Records a field pack application in the status list. */
  public void recordPackApplication(String packId, int version) {
    if (this.fieldPackStatus == null) {
      this.fieldPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.fieldPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public String getLogoS3Key() {
    return logoS3Key;
  }

  public void setLogoS3Key(String logoS3Key) {
    this.logoS3Key = logoS3Key;
    this.updatedAt = Instant.now();
  }

  public String getBrandColor() {
    return brandColor;
  }

  public void setBrandColor(String brandColor) {
    this.brandColor = brandColor;
    this.updatedAt = Instant.now();
  }

  public String getDocumentFooterText() {
    return documentFooterText;
  }

  public void setDocumentFooterText(String text) {
    this.documentFooterText = text;
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getTemplatePackStatus() {
    return templatePackStatus;
  }

  public void setTemplatePackStatus(List<Map<String, Object>> status) {
    this.templatePackStatus = status;
  }

  /** Records a template pack application in the status list. */
  public void recordTemplatePackApplication(String packId, int version) {
    if (this.templatePackStatus == null) {
      this.templatePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.templatePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public Integer getDormancyThresholdDays() {
    return dormancyThresholdDays;
  }

  public void setDormancyThresholdDays(Integer dormancyThresholdDays) {
    this.dormancyThresholdDays = dormancyThresholdDays;
    this.updatedAt = Instant.now();
  }

  public Integer getDataRequestDeadlineDays() {
    return dataRequestDeadlineDays;
  }

  public void setDataRequestDeadlineDays(Integer dataRequestDeadlineDays) {
    this.dataRequestDeadlineDays = dataRequestDeadlineDays;
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getCompliancePackStatus() {
    return compliancePackStatus;
  }

  /**
   * Records a compliance pack application in the status list. Uses String version (not int) because
   * compliance packs use semantic versioning (e.g. "1.0.0"), unlike field/template packs which use
   * sequential integers.
   */
  public void recordCompliancePackApplication(String packId, String version) {
    if (this.compliancePackStatus == null) {
      this.compliancePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.compliancePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getReportPackStatus() {
    return reportPackStatus;
  }

  /** Records a report pack application in the status list. */
  public void recordReportPackApplication(String packId, int version) {
    if (this.reportPackStatus == null) {
      this.reportPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.reportPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getClausePackStatus() {
    return clausePackStatus;
  }

  public void setClausePackStatus(List<Map<String, Object>> status) {
    this.clausePackStatus = status;
  }

  /** Records a clause pack application in the status list. */
  public void recordClausePackApplication(String packId, int version) {
    if (this.clausePackStatus == null) {
      this.clausePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.clausePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getRequestPackStatus() {
    return requestPackStatus;
  }

  public void setRequestPackStatus(List<Map<String, Object>> status) {
    this.requestPackStatus = status;
  }

  /** Records a request pack application in the status list. */
  public void recordRequestPackApplication(String packId, int version) {
    if (this.requestPackStatus == null) {
      this.requestPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.requestPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  public List<Map<String, Object>> getAutomationPackStatus() {
    return automationPackStatus;
  }

  public void setAutomationPackStatus(List<Map<String, Object>> status) {
    this.automationPackStatus = status;
  }

  /** Records an automation pack application in the status list. */
  public void recordAutomationPackApplication(String packId, int version) {
    if (this.automationPackStatus == null) {
      this.automationPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.automationPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  /** Checks whether an automation pack has already been applied. */
  public boolean isAutomationPackApplied(String packId) {
    if (this.automationPackStatus == null) {
      return false;
    }
    return this.automationPackStatus.stream().anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  public List<Map<String, Object>> getRatePackStatus() {
    return ratePackStatus;
  }

  /** Records a rate pack application. Idempotent -- skips if already applied. */
  public void recordRatePackApplication(String packId, int version) {
    if (isRatePackApplied(packId, version)) {
      return;
    }
    if (this.ratePackStatus == null) {
      this.ratePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.ratePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  /** Returns true if the given rate pack (specific version) has been applied. */
  public boolean isRatePackApplied(String packId, int version) {
    if (this.ratePackStatus == null) {
      return false;
    }
    return this.ratePackStatus.stream()
        .anyMatch(
            entry ->
                packId.equals(entry.get("packId"))
                    && ((Number) entry.get("version")).intValue() == version);
  }

  public List<Map<String, Object>> getSchedulePackStatus() {
    return schedulePackStatus;
  }

  /** Records a schedule pack application. Idempotent -- skips if already applied. */
  public void recordSchedulePackApplication(String packId, int version) {
    if (isSchedulePackApplied(packId, version)) {
      return;
    }
    if (this.schedulePackStatus == null) {
      this.schedulePackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.schedulePackStatus.add(entry);
    this.updatedAt = Instant.now();
  }

  /** Returns true if the given schedule pack (specific version) has been applied. */
  public boolean isSchedulePackApplied(String packId, int version) {
    if (this.schedulePackStatus == null) {
      return false;
    }
    return this.schedulePackStatus.stream()
        .anyMatch(
            entry ->
                packId.equals(entry.get("packId"))
                    && ((Number) entry.get("version")).intValue() == version);
  }

  public Integer getDefaultRequestReminderDays() {
    return defaultRequestReminderDays;
  }

  public void setDefaultRequestReminderDays(Integer defaultRequestReminderDays) {
    this.defaultRequestReminderDays = defaultRequestReminderDays;
    this.updatedAt = Instant.now();
  }

  public Integer getAcceptanceExpiryDays() {
    return acceptanceExpiryDays;
  }

  public void setAcceptanceExpiryDays(Integer acceptanceExpiryDays) {
    this.acceptanceExpiryDays = acceptanceExpiryDays;
    this.updatedAt = Instant.now();
  }

  /** Returns the configured acceptance expiry days, defaulting to 30 if not set. */
  public int getEffectiveAcceptanceExpiryDays() {
    return acceptanceExpiryDays != null ? acceptanceExpiryDays : 30;
  }

  public boolean isAccountingEnabled() {
    return accountingEnabled;
  }

  public boolean isAiEnabled() {
    return aiEnabled;
  }

  public boolean isDocumentSigningEnabled() {
    return documentSigningEnabled;
  }

  public boolean isTaxInclusive() {
    return taxInclusive;
  }

  public void setTaxInclusive(boolean taxInclusive) {
    this.taxInclusive = taxInclusive;
    this.updatedAt = Instant.now();
  }

  public String getTaxRegistrationNumber() {
    return taxRegistrationNumber;
  }

  public void setTaxRegistrationNumber(String taxRegistrationNumber) {
    this.taxRegistrationNumber = taxRegistrationNumber;
    this.updatedAt = Instant.now();
  }

  public String getTaxRegistrationLabel() {
    return taxRegistrationLabel;
  }

  public void setTaxRegistrationLabel(String taxRegistrationLabel) {
    this.taxRegistrationLabel = taxRegistrationLabel;
    this.updatedAt = Instant.now();
  }

  public String getTaxLabel() {
    return taxLabel;
  }

  public void setTaxLabel(String taxLabel) {
    this.taxLabel = taxLabel;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getDefaultExpenseMarkupPercent() {
    return defaultExpenseMarkupPercent;
  }

  public void setDefaultExpenseMarkupPercent(BigDecimal defaultExpenseMarkupPercent) {
    this.defaultExpenseMarkupPercent = defaultExpenseMarkupPercent;
    this.updatedAt = Instant.now();
  }

  public BigDecimal getDefaultWeeklyCapacityHours() {
    return defaultWeeklyCapacityHours;
  }

  public void setDefaultWeeklyCapacityHours(BigDecimal defaultWeeklyCapacityHours) {
    this.defaultWeeklyCapacityHours = defaultWeeklyCapacityHours;
    this.updatedAt = Instant.now();
  }

  /** Updates all four tax configuration fields and the timestamp. */
  public void updateTaxSettings(
      String taxRegistrationNumber,
      String taxRegistrationLabel,
      String taxLabel,
      boolean taxInclusive) {
    this.taxRegistrationNumber = taxRegistrationNumber;
    this.taxRegistrationLabel = taxRegistrationLabel;
    this.taxLabel = taxLabel;
    this.taxInclusive = taxInclusive;
    this.updatedAt = Instant.now();
  }

  /** Updates all three integration domain flags and the timestamp. */
  public void updateIntegrationFlags(boolean accounting, boolean ai, boolean documentSigning) {
    this.accountingEnabled = accounting;
    this.aiEnabled = ai;
    this.documentSigningEnabled = documentSigning;
    this.updatedAt = Instant.now();
  }

  public boolean isTimeReminderEnabled() {
    return timeReminderEnabled;
  }

  public void setTimeReminderEnabled(boolean timeReminderEnabled) {
    this.timeReminderEnabled = timeReminderEnabled;
    this.updatedAt = Instant.now();
  }

  public String getTimeReminderDays() {
    return timeReminderDays;
  }

  public void setTimeReminderDays(String timeReminderDays) {
    this.timeReminderDays = timeReminderDays;
    this.updatedAt = Instant.now();
  }

  public LocalTime getTimeReminderTime() {
    return timeReminderTime;
  }

  public void setTimeReminderTime(LocalTime timeReminderTime) {
    this.timeReminderTime = timeReminderTime;
    this.updatedAt = Instant.now();
  }

  public Integer getTimeReminderMinMinutes() {
    return timeReminderMinMinutes;
  }

  public void setTimeReminderMinMinutes(Integer timeReminderMinMinutes) {
    this.timeReminderMinMinutes = timeReminderMinMinutes;
    this.updatedAt = Instant.now();
  }

  /** Returns the minimum hours threshold, computed from minutes. Defaults to 4.0 if not set. */
  public double getTimeReminderMinHours() {
    return timeReminderMinMinutes != null ? timeReminderMinMinutes / 60.0 : 4.0;
  }

  public Integer getBillingBatchAsyncThreshold() {
    return billingBatchAsyncThreshold;
  }

  public void setBillingBatchAsyncThreshold(Integer billingBatchAsyncThreshold) {
    this.billingBatchAsyncThreshold = billingBatchAsyncThreshold;
    this.updatedAt = Instant.now();
  }

  public Integer getBillingEmailRateLimit() {
    return billingEmailRateLimit;
  }

  public void setBillingEmailRateLimit(Integer billingEmailRateLimit) {
    this.billingEmailRateLimit = billingEmailRateLimit;
    this.updatedAt = Instant.now();
  }

  public String getDefaultBillingRunCurrency() {
    return defaultBillingRunCurrency;
  }

  public void setDefaultBillingRunCurrency(String defaultBillingRunCurrency) {
    this.defaultBillingRunCurrency = defaultBillingRunCurrency;
    this.updatedAt = Instant.now();
  }

  public String getProjectNamingPattern() {
    return projectNamingPattern;
  }

  public void setProjectNamingPattern(String projectNamingPattern) {
    this.projectNamingPattern = projectNamingPattern;
    this.updatedAt = Instant.now();
  }

  private static final Map<String, DayOfWeek> DAY_ABBREVIATIONS =
      Map.of(
          "MON", DayOfWeek.MONDAY,
          "TUE", DayOfWeek.TUESDAY,
          "WED", DayOfWeek.WEDNESDAY,
          "THU", DayOfWeek.THURSDAY,
          "FRI", DayOfWeek.FRIDAY,
          "SAT", DayOfWeek.SATURDAY,
          "SUN", DayOfWeek.SUNDAY);

  /** Parses timeReminderDays CSV (e.g. "MON,TUE,WED,THU,FRI") into a Set of DayOfWeek. */
  public Set<DayOfWeek> getWorkingDays() {
    if (timeReminderDays == null || timeReminderDays.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(timeReminderDays.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> DAY_ABBREVIATIONS.get(s.toUpperCase()))
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
  }

  /** Updates all time reminder fields and the timestamp. */
  public void updateTimeReminderSettings(
      boolean enabled, String days, LocalTime time, Integer minMinutes) {
    this.timeReminderEnabled = enabled;
    this.timeReminderDays = days;
    this.timeReminderTime = time;
    this.timeReminderMinMinutes = minMinutes;
    this.updatedAt = Instant.now();
  }

  public Instant getOnboardingDismissedAt() {
    return onboardingDismissedAt;
  }

  public void setOnboardingDismissedAt(Instant onboardingDismissedAt) {
    this.onboardingDismissedAt = onboardingDismissedAt;
    this.updatedAt = Instant.now();
  }

  /** Sets the onboarding dismissed timestamp to now. Idempotent — overwrites any previous value. */
  public void dismissOnboarding() {
    this.onboardingDismissedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Returns true if onboarding has been dismissed. */
  public boolean isOnboardingDismissed() {
    return onboardingDismissedAt != null;
  }

  public String getVerticalProfile() {
    return verticalProfile;
  }

  public void setVerticalProfile(String verticalProfile) {
    this.verticalProfile = verticalProfile;
    this.updatedAt = Instant.now();
  }

  public List<String> getEnabledModules() {
    return enabledModules != null ? List.copyOf(enabledModules) : new ArrayList<>();
  }

  public void setEnabledModules(List<String> enabledModules) {
    this.enabledModules = enabledModules != null ? enabledModules : new ArrayList<>();
    this.updatedAt = Instant.now();
  }

  public String getTerminologyNamespace() {
    return terminologyNamespace;
  }

  public void setTerminologyNamespace(String terminologyNamespace) {
    this.terminologyNamespace = terminologyNamespace;
    this.updatedAt = Instant.now();
  }

  /** Updates all three vertical profile fields atomically and bumps updatedAt. */
  public void updateVerticalProfile(
      String verticalProfile, List<String> enabledModules, String terminologyNamespace) {
    this.verticalProfile = verticalProfile;
    this.enabledModules =
        enabledModules != null ? new ArrayList<>(enabledModules) : new ArrayList<>();
    this.terminologyNamespace = terminologyNamespace;
    this.updatedAt = Instant.now();
  }

  // --- Data Protection getters/setters ---

  public String getDataProtectionJurisdiction() {
    return dataProtectionJurisdiction;
  }

  public void setDataProtectionJurisdiction(String dataProtectionJurisdiction) {
    this.dataProtectionJurisdiction = dataProtectionJurisdiction;
    this.updatedAt = Instant.now();
  }

  public boolean isRetentionPolicyEnabled() {
    return retentionPolicyEnabled;
  }

  public void setRetentionPolicyEnabled(boolean retentionPolicyEnabled) {
    this.retentionPolicyEnabled = retentionPolicyEnabled;
    this.updatedAt = Instant.now();
  }

  public Integer getDefaultRetentionMonths() {
    return defaultRetentionMonths;
  }

  public void setDefaultRetentionMonths(Integer defaultRetentionMonths) {
    this.defaultRetentionMonths = defaultRetentionMonths;
    this.updatedAt = Instant.now();
  }

  public Integer getFinancialRetentionMonths() {
    return financialRetentionMonths;
  }

  public void setFinancialRetentionMonths(Integer financialRetentionMonths) {
    this.financialRetentionMonths = financialRetentionMonths;
    this.updatedAt = Instant.now();
  }

  public String getInformationOfficerName() {
    return informationOfficerName;
  }

  public void setInformationOfficerName(String informationOfficerName) {
    this.informationOfficerName = informationOfficerName;
    this.updatedAt = Instant.now();
  }

  public String getInformationOfficerEmail() {
    return informationOfficerEmail;
  }

  public void setInformationOfficerEmail(String informationOfficerEmail) {
    this.informationOfficerEmail = informationOfficerEmail;
    this.updatedAt = Instant.now();
  }

  /** Updates all data protection settings atomically and bumps updatedAt. */
  public void updateDataProtectionSettings(
      String jurisdiction,
      boolean retentionEnabled,
      Integer defaultRetentionMonths,
      Integer financialRetentionMonths,
      String officerName,
      String officerEmail) {
    this.dataProtectionJurisdiction = jurisdiction;
    this.retentionPolicyEnabled = retentionEnabled;
    this.defaultRetentionMonths = defaultRetentionMonths;
    this.financialRetentionMonths = financialRetentionMonths;
    this.informationOfficerName = officerName;
    this.informationOfficerEmail = officerEmail;
    this.updatedAt = Instant.now();
  }
}
