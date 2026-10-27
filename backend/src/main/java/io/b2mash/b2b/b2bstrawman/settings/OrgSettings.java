package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

  /**
   * Branding / document-identity group (Wave 3.3). Persisted inline on {@code org_settings} via
   * {@code @Embedded} — column names pinned by {@code @AttributeOverride} for zero schema change.
   * Initialised non-null so a fresh entity and an all-null DB row never NPE on {@link
   * #getBranding()}.
   */
  @Embedded
  @AttributeOverride(name = "logoS3Key", column = @Column(name = "logo_s3_key", length = 500))
  @AttributeOverride(name = "brandColor", column = @Column(name = "brand_color", length = 7))
  @AttributeOverride(
      name = "documentFooterText",
      column = @Column(name = "document_footer_text", columnDefinition = "TEXT"))
  private BrandingSettings branding = new BrandingSettings();

  /**
   * Pack-application status group (Wave 3.5): the nine JSONB pack-status lists (field / template /
   * compliance / report / clause / request / automation / rate / project-template) plus their
   * idempotency record/remove/is-applied helpers. Persisted inline on {@code org_settings} via
   * {@code @Embedded} — column names pinned by {@code @AttributeOverride} for zero schema change.
   * All nine columns are nullable, so an all-null group materialises as a NULL embedded on reload;
   * initialised non-null + lazy-fallback {@link #getPackStatus()} keeps it NPE-safe.
   */
  @Embedded
  @AttributeOverride(
      name = "fieldPackStatus",
      column = @Column(name = "field_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "templatePackStatus",
      column = @Column(name = "template_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "compliancePackStatus",
      column = @Column(name = "compliance_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "reportPackStatus",
      column = @Column(name = "report_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "clausePackStatus",
      column = @Column(name = "clause_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "requestPackStatus",
      column = @Column(name = "request_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "automationPackStatus",
      column = @Column(name = "automation_pack_status", columnDefinition = "jsonb"))
  @AttributeOverride(
      name = "projectTemplatePackStatus",
      column = @Column(name = "project_template_pack_status", columnDefinition = "jsonb"))
  private PackStatusSettings packStatus = new PackStatusSettings();

  /**
   * Operational request-handling group (Wave 3.5): the DSAR deadline, request-reminder cadence, and
   * customer dormancy threshold — scheduler-driven tunables, split from the POPIA officer panel.
   * Persisted inline on {@code org_settings} via {@code @Embedded} — column names pinned by
   * {@code @AttributeOverride} for zero schema change. All three columns are nullable, so an
   * all-null group materialises as a NULL embedded on reload; initialised non-null + lazy-fallback
   * {@link #getDataRequest()} keeps it NPE-safe.
   */
  @Embedded
  @AttributeOverride(
      name = "dataRequestDeadlineDays",
      column = @Column(name = "data_request_deadline_days"))
  @AttributeOverride(
      name = "defaultRequestReminderDays",
      column = @Column(name = "default_request_reminder_days"))
  @AttributeOverride(
      name = "dormancyThresholdDays",
      column = @Column(name = "dormancy_threshold_days"))
  private DataRequestSettings dataRequest = new DataRequestSettings();

  @Column(name = "acceptance_expiry_days")
  private Integer acceptanceExpiryDays;

  @Column(name = "accounting_enabled", nullable = false)
  private boolean accountingEnabled;

  @Column(name = "ai_enabled", nullable = false)
  private boolean aiEnabled;

  @Column(name = "document_signing_enabled", nullable = false)
  private boolean documentSigningEnabled;

  /**
   * Tax configuration group (Wave 3.4): tax-inclusive pricing toggle and the tax-registration
   * identity fields. Persisted inline on {@code org_settings} via {@code @Embedded} — column names
   * pinned by {@code @AttributeOverride} for zero schema change. Initialised non-null so a fresh
   * entity and an all-null DB row never NPE on {@link #getTax()}. ({@code tax_inclusive} is NOT
   * NULL, so the group never fully materialises as NULL on reload — but the null-safe getter is
   * kept for symmetry with the other groups.)
   */
  @Embedded
  @AttributeOverride(
      name = "taxInclusive",
      column = @Column(name = "tax_inclusive", nullable = false))
  @AttributeOverride(
      name = "taxRegistrationNumber",
      column = @Column(name = "tax_registration_number", length = 50))
  @AttributeOverride(
      name = "taxRegistrationLabel",
      column = @Column(name = "tax_registration_label", length = 30))
  @AttributeOverride(name = "taxLabel", column = @Column(name = "tax_label", length = 20))
  private TaxSettings tax = new TaxSettings();

  /**
   * Expense configuration group (Wave 3.4): the firm-wide default expense markup percent. Persisted
   * inline on {@code org_settings} via {@code @Embedded} — column name/precision pinned by
   * {@code @AttributeOverride} for zero schema change. Single nullable column, so it can
   * materialise as NULL on reload; initialised non-null + lazy-fallback {@link #getExpense()} keeps
   * it NPE-safe.
   */
  @Embedded
  @AttributeOverride(
      name = "defaultExpenseMarkupPercent",
      column = @Column(name = "default_expense_markup_percent", precision = 5, scale = 2))
  private ExpenseSettings expense = new ExpenseSettings();

  /**
   * Time-tracking reminder group (Wave 3.5): the daily time-entry reminder enabled toggle,
   * working-day CSV, fire time, and minimum-minutes threshold. Persisted inline on {@code
   * org_settings} via {@code @Embedded} — column names pinned by {@code @AttributeOverride} for
   * zero schema change. Initialised non-null so a fresh entity and an all-null DB row never NPE on
   * {@link #getTimeReminder()}. ({@code time_reminder_enabled} is NOT NULL, so the group never
   * fully materialises as NULL on reload — but the null-safe getter is kept for symmetry.)
   */
  @Embedded
  @AttributeOverride(
      name = "timeReminderEnabled",
      column = @Column(name = "time_reminder_enabled", nullable = false))
  @AttributeOverride(
      name = "timeReminderDays",
      column = @Column(name = "time_reminder_days", length = 50))
  @AttributeOverride(name = "timeReminderTime", column = @Column(name = "time_reminder_time"))
  @AttributeOverride(
      name = "timeReminderMinMinutes",
      column = @Column(name = "time_reminder_min_minutes"))
  private TimeReminderSettings timeReminder = new TimeReminderSettings();

  /**
   * Collections / dunning policy group (Phase 83): master enable switch + the four days-overdue
   * thresholds (stage 1-3 reminders, escalation flag). Persisted inline on {@code org_settings} via
   * {@code @Embedded} — column names pinned by {@code @AttributeOverride} (tenant baseline;
   * deliberate OrgSettingsSchemaSnapshotTest pin update). Initialised non-null so a fresh entity
   * and an all-null DB row never NPE on {@link #getCollections()}. ({@code collections_enabled} is
   * NOT NULL, so the group never fully materialises as NULL on reload — but the null-safe getter is
   * kept for symmetry.)
   */
  @Embedded
  @AttributeOverride(
      name = "collectionsEnabled",
      column = @Column(name = "collections_enabled", nullable = false))
  @AttributeOverride(name = "stage1DaysOverdue", column = @Column(name = "collections_stage1_days"))
  @AttributeOverride(name = "stage2DaysOverdue", column = @Column(name = "collections_stage2_days"))
  @AttributeOverride(name = "stage3DaysOverdue", column = @Column(name = "collections_stage3_days"))
  @AttributeOverride(
      name = "escalateDaysOverdue",
      column = @Column(name = "collections_escalate_days"))
  private CollectionsSettings collections = new CollectionsSettings();

  /**
   * Billing-run configuration group (Wave 3.4): batch async threshold, per-run email rate limit,
   * the optional default billing-run currency override, and the firm default payment terms in days
   * (Phase 88 U3, tenant V4 §2). Persisted inline on {@code org_settings} via {@code @Embedded} —
   * column names pinned by {@code @AttributeOverride}. Initialised non-null so a fresh entity and
   * an all-null DB row never NPE on {@link #getBilling()}; the embeddable's field initialisers
   * reproduce the constructor defaults (threshold 50, rate limit 5, payment terms 30). NOTE: the
   * org base currency ({@code default_currency}) stays top-level — see {@link BillingSettings}.
   */
  @Embedded
  @AttributeOverride(
      name = "billingBatchAsyncThreshold",
      column = @Column(name = "billing_batch_async_threshold"))
  @AttributeOverride(
      name = "billingEmailRateLimit",
      column = @Column(name = "billing_email_rate_limit"))
  @AttributeOverride(
      name = "defaultBillingRunCurrency",
      column = @Column(name = "default_billing_run_currency", length = 3))
  @AttributeOverride(
      name = "defaultPaymentTermsDays",
      column = @Column(name = "default_payment_terms_days"))
  private BillingSettings billing = new BillingSettings();

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

  /**
   * Data-protection / POPIA group (Wave 3.5): jurisdiction, retention-policy toggle and windows
   * (default / financial / legal-matter), and the information officer identity. Persisted inline on
   * {@code org_settings} via {@code @Embedded} — column names pinned by {@code @AttributeOverride}
   * for zero schema change. Initialised non-null so a fresh entity and an all-null DB row never NPE
   * on {@link #getDataProtection()}. ({@code retention_policy_enabled} is NOT NULL, so the group
   * never fully materialises as NULL on reload.) {@code legal_matter_retention_years} carries its
   * bean-validation {@code @Min}/{@code @Max} annotations on the embeddable field.
   */
  @Embedded
  @AttributeOverride(
      name = "dataProtectionJurisdiction",
      column = @Column(name = "data_protection_jurisdiction", length = 10))
  @AttributeOverride(
      name = "retentionPolicyEnabled",
      column = @Column(name = "retention_policy_enabled", nullable = false))
  @AttributeOverride(
      name = "defaultRetentionMonths",
      column = @Column(name = "default_retention_months"))
  @AttributeOverride(
      name = "financialRetentionMonths",
      column = @Column(name = "financial_retention_months"))
  @AttributeOverride(
      name = "informationOfficerName",
      column = @Column(name = "information_officer_name", length = 255))
  @AttributeOverride(
      name = "informationOfficerEmail",
      column = @Column(name = "information_officer_email", length = 255))
  @AttributeOverride(
      name = "legalMatterRetentionYears",
      column = @Column(name = "legal_matter_retention_years"))
  private DataProtectionSettings dataProtection = new DataProtectionSettings();

  /**
   * Customer-portal settings group (Wave 3.3): digest cadence and last-digest-sent timestamp.
   * Persisted inline on {@code org_settings} via {@code @Embedded} — column names pinned by
   * {@code @AttributeOverride} for zero schema change. Both columns are nullable, so the group
   * materialises as NULL on reload when both are null (every fresh tenant); {@link #getPortal()}
   * lazily re-creates it (guarded by {@code OrgSettingsEmbeddableNullReloadTest}). The former
   * allowlist column {@code portal_notification_doc_types} stays in the table, unmapped (Phase 88
   * U13 / ADR-350, A15).
   */
  @Embedded
  @AttributeOverride(
      name = "portalDigestCadence",
      column = @Column(name = "portal_digest_cadence", length = 12))
  @AttributeOverride(name = "digestLastSentAt", column = @Column(name = "digest_last_sent_at"))
  private PortalSettings portal = new PortalSettings();

  /**
   * Firm letterhead / contact / banking / signatory group (Phase 88 U8, ADR-353, tenant V7). All
   * sixteen columns are nullable, so the group materialises as NULL on reload for every tenant that
   * has not filled it; {@link #getLetterhead()} lazily re-creates it (guarded by {@code
   * OrgSettingsEmbeddableNullReloadTest}). Column names pinned by {@code @AttributeOverride} so
   * {@code OrgSettingsSchemaSnapshotTest} sees exactly the V7 columns.
   */
  @Embedded
  @AttributeOverride(
      name = "addressLine1",
      column = @Column(name = "letterhead_address_line1", length = 255))
  @AttributeOverride(
      name = "addressLine2",
      column = @Column(name = "letterhead_address_line2", length = 255))
  @AttributeOverride(
      name = "addressLine3",
      column = @Column(name = "letterhead_address_line3", length = 255))
  @AttributeOverride(name = "city", column = @Column(name = "letterhead_city", length = 100))
  @AttributeOverride(
      name = "postalCode",
      column = @Column(name = "letterhead_postal_code", length = 20))
  @AttributeOverride(name = "country", column = @Column(name = "letterhead_country", length = 100))
  @AttributeOverride(name = "phone", column = @Column(name = "letterhead_phone", length = 50))
  @AttributeOverride(name = "email", column = @Column(name = "letterhead_email", length = 255))
  @AttributeOverride(name = "website", column = @Column(name = "letterhead_website", length = 255))
  @AttributeOverride(
      name = "bankName",
      column = @Column(name = "letterhead_bank_name", length = 100))
  @AttributeOverride(
      name = "bankBranchCode",
      column = @Column(name = "letterhead_bank_branch_code", length = 20))
  @AttributeOverride(
      name = "bankAccountNumber",
      column = @Column(name = "letterhead_bank_account_number", length = 50))
  @AttributeOverride(
      name = "bankAccountType",
      column = @Column(name = "letterhead_bank_account_type", length = 30))
  @AttributeOverride(
      name = "paymentReferenceNote",
      column = @Column(name = "letterhead_payment_reference_note", columnDefinition = "TEXT"))
  @AttributeOverride(
      name = "signatoryName",
      column = @Column(name = "letterhead_signatory_name", length = 255))
  @AttributeOverride(
      name = "signatoryDesignation",
      column = @Column(name = "letterhead_signatory_designation", length = 255))
  private LetterheadSettings letterhead = new LetterheadSettings();

  protected OrgSettings() {}

  /**
   * Refreshes {@code updatedAt} on every dirty flush. Before the embeddable refactor every mutator
   * (including the plain branding/portal setters) bumped {@code updatedAt}; embeddable setters
   * cannot reach the owning entity's timestamp, so this entity-level callback restores the contract
   * uniformly — for the embedded groups, the remaining top-level mutators, and any groups extracted
   * in later waves.
   */
  @PreUpdate
  private void refreshUpdatedAtOnFlush() {
    this.updatedAt = Instant.now();
  }

  public OrgSettings(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.accountingEnabled = false;
    this.aiEnabled = false;
    this.documentSigningEnabled = false;
  }

  /** Returns the branding settings group. Never null. */
  public BrandingSettings getBranding() {
    if (branding == null) {
      branding = new BrandingSettings();
    }
    return branding;
  }

  /** Returns the customer-portal settings group. Never null. */
  public PortalSettings getPortal() {
    if (portal == null) {
      portal = new PortalSettings();
    }
    return portal;
  }

  /** Returns the tax configuration settings group. Never null. */
  public TaxSettings getTax() {
    if (tax == null) {
      tax = new TaxSettings();
    }
    return tax;
  }

  /** Returns the expense configuration settings group. Never null. */
  public ExpenseSettings getExpense() {
    if (expense == null) {
      expense = new ExpenseSettings();
    }
    return expense;
  }

  /** Returns the billing-run configuration settings group. Never null. */
  public BillingSettings getBilling() {
    if (billing == null) {
      billing = new BillingSettings();
    }
    return billing;
  }

  /** Returns the time-tracking reminder settings group. Never null. */
  public TimeReminderSettings getTimeReminder() {
    if (timeReminder == null) {
      timeReminder = new TimeReminderSettings();
    }
    return timeReminder;
  }

  /** Returns the collections / dunning policy settings group. Never null. */
  public CollectionsSettings getCollections() {
    if (collections == null) {
      collections = new CollectionsSettings();
    }
    return collections;
  }

  /** Returns the pack-application status settings group. Never null. */
  public PackStatusSettings getPackStatus() {
    if (packStatus == null) {
      packStatus = new PackStatusSettings();
    }
    return packStatus;
  }

  /** Returns the operational request-handling settings group. Never null. */
  public DataRequestSettings getDataRequest() {
    if (dataRequest == null) {
      dataRequest = new DataRequestSettings();
    }
    return dataRequest;
  }

  /** Returns the data-protection / POPIA settings group. Never null. */
  public DataProtectionSettings getDataProtection() {
    if (dataProtection == null) {
      dataProtection = new DataProtectionSettings();
    }
    return dataProtection;
  }

  /** Returns the firm letterhead settings group. Never null. */
  public LetterheadSettings getLetterhead() {
    if (letterhead == null) {
      letterhead = new LetterheadSettings();
    }
    return letterhead;
  }

  public void updateCurrency(String currency) {
    this.defaultCurrency = currency;
    this.updatedAt = Instant.now();
  }

  /**
   * Bumps {@code updatedAt} to now. Embeddable-group setters
   * (tax/billing/expense/time-reminder/pack-status/data-protection/data-request/letterhead) do not
   * touch the owning entity's timestamp; service methods that mutate a group through {@link
   * #getTax()}/{@link #getBilling()}/etc. call this to preserve the explicit timestamp-bump
   * semantics the former top-level entity mutators provided.
   */
  public void touchUpdatedAt() {
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

  /** Updates all three integration domain flags and the timestamp. */
  public void updateIntegrationFlags(boolean accounting, boolean ai, boolean documentSigning) {
    this.accountingEnabled = accounting;
    this.aiEnabled = ai;
    this.documentSigningEnabled = documentSigning;
    this.updatedAt = Instant.now();
  }

  public String getProjectNamingPattern() {
    return projectNamingPattern;
  }

  public void setProjectNamingPattern(String projectNamingPattern) {
    this.projectNamingPattern = projectNamingPattern;
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
}
