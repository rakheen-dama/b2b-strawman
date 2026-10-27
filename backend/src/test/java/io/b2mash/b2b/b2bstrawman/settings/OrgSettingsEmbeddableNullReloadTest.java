package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards the all-null-embedded NPE risk introduced by the Wave 3.3 embeddable refactor. Hibernate
 * materialises a NULL embedded object when every mapped column of the group is NULL. An OLD {@code
 * org_settings} row written before the relevant columns existed can therefore reload with a null
 * {@code branding}/{@code portal} reference — and any caller doing {@code
 * settings.getBranding().getX()} would NPE.
 *
 * <p>This test nulls out every nullable branding + portal column via raw SQL (simulating such a
 * legacy row) — all three branding columns are nullable, so the branding embedded materialises as
 * NULL (the genuine NPE risk). It then reloads the entity through the repository on a fresh tenant
 * connection and asserts the group accessors return non-null objects whose getters yield null
 * without throwing. The lazy-initialising {@link OrgSettings#getBranding()} / {@link
 * OrgSettings#getPortal()} fallback is what makes this safe.
 *
 * <p>Phase 88 U8 (ADR-353, tenant V7) adds the {@link LetterheadSettings} group: all sixteen {@code
 * letterhead_*} columns are nullable, so every tenant that has not filled the letterhead reloads it
 * as NULL — {@link OrgSettings#getLetterhead()} lazily re-creates it, and writes through the lazily
 * created group must persist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgSettingsEmbeddableNullReloadTest {
  private static final String ORG_ID = "org_orgsettings_null_reload";

  /** SET clause nulling all sixteen Phase 88 U8 letterhead columns (tenant V7). */
  private static final String LETTERHEAD_NULLS =
      "letterhead_address_line1 = NULL, letterhead_address_line2 = NULL, "
          + "letterhead_address_line3 = NULL, letterhead_city = NULL, "
          + "letterhead_postal_code = NULL, letterhead_country = NULL, letterhead_phone = NULL, "
          + "letterhead_email = NULL, letterhead_website = NULL, letterhead_bank_name = NULL, "
          + "letterhead_bank_branch_code = NULL, letterhead_bank_account_number = NULL, "
          + "letterhead_bank_account_type = NULL, letterhead_payment_reference_note = NULL, "
          + "letterhead_signatory_name = NULL, letterhead_signatory_designation = NULL";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OrgSettings Null Reload Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_onr_owner", "onr_owner@test.com", "ONR Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void allNullEmbeddedGroups_reloadWithoutNpe() throws Exception {
    var idRef = new AtomicReference<UUID>();

    // 1. Persist a settings row inside the tenant (own connection / search_path).
    runInTenant(() -> idRef.set(orgSettingsRepository.save(new OrgSettings("USD")).getId()));
    UUID id = idRef.get();

    // 2. Null out the nullable branding + portal columns via raw SQL — simulating a legacy row.
    //    All three branding columns are nullable, so Hibernate materialises a NULL branding
    //    embedded (the genuine NPE risk). Since Phase 88 U13 / ADR-350 the portal group is also
    //    all-nullable (portal_digest_cadence + digest_last_sent_at; the unmapped allowlist column
    //    portal_notification_doc_types is no longer part of it), so it materialises as NULL too.
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + tenantSchema);
      int updated =
          stmt.executeUpdate(
              "UPDATE org_settings SET "
                  + "logo_s3_key = NULL, brand_color = NULL, document_footer_text = NULL, "
                  + "portal_digest_cadence = NULL, "
                  + "digest_last_sent_at = NULL, "
                  // Expense is a single-column nullable group (Wave 3.4) — nulling the sole
                  // column makes Hibernate materialise a NULL embedded, the genuine NPE risk.
                  + "default_expense_markup_percent = NULL, "
                  // Billing has a NOT-NULL-defaulted pair, but default_billing_run_currency is
                  // nullable; null it to exercise the null-safe billing accessor too.
                  + "default_billing_run_currency = NULL, "
                  // Wave 3.5 DataRequest group — all three columns are nullable, so nulling them
                  // makes Hibernate materialise a NULL embedded (the genuine NPE risk).
                  + "data_request_deadline_days = NULL, default_request_reminder_days = NULL, "
                  + "dormancy_threshold_days = NULL, "
                  // Wave 3.5 PackStatus group — all eight jsonb columns are nullable, so nulling
                  // them
                  // all makes Hibernate materialise a NULL embedded (the genuine NPE risk).
                  + "field_pack_status = NULL, template_pack_status = NULL, "
                  + "compliance_pack_status = NULL, report_pack_status = NULL, "
                  + "clause_pack_status = NULL, request_pack_status = NULL, "
                  + "automation_pack_status = NULL, "
                  + "project_template_pack_status = NULL, "
                  // Wave 3.5 DataProtection + TimeReminder groups each carry a NOT NULL column
                  // (retention_policy_enabled / time_reminder_enabled), so the embedded never fully
                  // materialises as NULL — but null their remaining columns to exercise the
                  // null-tolerant accessors on an otherwise-blank group.
                  + "data_protection_jurisdiction = NULL, default_retention_months = NULL, "
                  + "financial_retention_months = NULL, information_officer_name = NULL, "
                  + "information_officer_email = NULL, legal_matter_retention_years = NULL, "
                  + "time_reminder_days = NULL, time_reminder_time = NULL, "
                  + "time_reminder_min_minutes = NULL, "
                  // Phase 88 U8 letterhead group — all sixteen columns nullable, so the embedded
                  // materialises as NULL: the genuine NPE risk.
                  + LETTERHEAD_NULLS
                  + " "
                  + "WHERE id = '"
                  + id
                  + "'");
      assertThat(updated).as("the null-out UPDATE must hit exactly one row").isEqualTo(1);
    }

    // 3. Reload on a fresh tenant connection (no first-level cache carry-over) and assert the
    //    group accessors are non-null and their getters do not NPE.
    runInTenant(
        () -> {
          var reloaded = orgSettingsRepository.findById(id).orElseThrow();
          assertThatCode(
                  () -> {
                    assertThat(reloaded.getBranding()).isNotNull();
                    assertThat(reloaded.getBranding().getLogoS3Key()).isNull();
                    assertThat(reloaded.getBranding().getBrandColor()).isNull();
                    assertThat(reloaded.getBranding().getDocumentFooterText()).isNull();

                    assertThat(reloaded.getPortal()).isNotNull();
                    assertThat(reloaded.getPortal().getPortalDigestCadence()).isNull();
                    assertThat(reloaded.getPortal().getDigestLastSentAt()).isNull();

                    // Effective-getter fallbacks must still work on the all-null group.
                    assertThat(reloaded.getPortal().getEffectivePortalDigestCadence())
                        .isEqualTo(PortalDigestCadence.WEEKLY);

                    // Wave 3.4 single-column nullable group: nulling the sole column makes the
                    // embedded materialise as NULL, so the lazy-fallback getter is the only thing
                    // keeping this NPE-safe.
                    assertThat(reloaded.getExpense()).isNotNull();
                    assertThat(reloaded.getExpense().getDefaultExpenseMarkupPercent()).isNull();

                    // Wave 3.4 billing group: its NOT-NULL-defaulted columns keep the embedded
                    // non-NULL on reload, but the nullable currency override is null — exercise the
                    // accessor to prove the group is reachable and null-tolerant.
                    assertThat(reloaded.getBilling()).isNotNull();
                    assertThat(reloaded.getBilling().getDefaultBillingRunCurrency()).isNull();

                    // Wave 3.4 tax group: tax_inclusive is NOT NULL, so the embedded never fully
                    // materialises as NULL — but the group accessor and its nullable fields must
                    // still be safe to read.
                    assertThat(reloaded.getTax()).isNotNull();
                    assertThat(reloaded.getTax().getTaxLabel()).isNull();
                    assertThat(reloaded.getTax().getTaxRegistrationNumber()).isNull();

                    // Wave 3.5 DataRequest group: all three columns nulled, so the embedded
                    // materialises as NULL on reload — the lazy-fallback getter is the only thing
                    // keeping this NPE-safe.
                    assertThat(reloaded.getDataRequest()).isNotNull();
                    assertThat(reloaded.getDataRequest().getDataRequestDeadlineDays()).isNull();
                    assertThat(reloaded.getDataRequest().getDefaultRequestReminderDays()).isNull();
                    assertThat(reloaded.getDataRequest().getDormancyThresholdDays()).isNull();

                    // Wave 3.5 PackStatus group: all eight jsonb columns nulled, so the embedded
                    // materialises as NULL on reload — the lazy-fallback getter keeps it NPE-safe,
                    // and each nullable list accessor returns null without throwing.
                    assertThat(reloaded.getPackStatus()).isNotNull();
                    assertThat(reloaded.getPackStatus().getFieldPackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getTemplatePackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getCompliancePackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getReportPackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getClausePackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getRequestPackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getAutomationPackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getProjectTemplatePackStatus()).isNull();
                    // is*Applied accessors must tolerate the all-null group (return false, no NPE).
                    assertThat(reloaded.getPackStatus().isAutomationPackApplied("x")).isFalse();

                    // Wave 3.5 DataProtection group: retention_policy_enabled is NOT NULL, so the
                    // embedded never fully materialises as NULL — but its nullable fields and the
                    // effective-getter fallback must still be safe to read.
                    assertThat(reloaded.getDataProtection()).isNotNull();
                    assertThat(reloaded.getDataProtection().getDataProtectionJurisdiction())
                        .isNull();
                    assertThat(reloaded.getDataProtection().getInformationOfficerName()).isNull();
                    assertThat(reloaded.getDataProtection().getLegalMatterRetentionYears())
                        .isNull();
                    assertThat(reloaded.getDataProtection().getEffectiveLegalMatterRetentionYears())
                        .isEqualTo(DataProtectionSettings.DEFAULT_LEGAL_MATTER_RETENTION_YEARS);

                    // Wave 3.5 TimeReminder group: time_reminder_enabled is NOT NULL, so the
                    // embedded never fully materialises as NULL — but the nullable fields and the
                    // working-days / min-hours fallbacks must still be safe to read.
                    assertThat(reloaded.getTimeReminder()).isNotNull();
                    assertThat(reloaded.getTimeReminder().getTimeReminderDays()).isNull();
                    assertThat(reloaded.getTimeReminder().getTimeReminderTime()).isNull();
                    assertThat(reloaded.getTimeReminder().getWorkingDays()).isEmpty();
                    assertThat(reloaded.getTimeReminder().getTimeReminderMinHours()).isEqualTo(4.0);

                    // Phase 88 U8 letterhead group: all sixteen columns nulled, so the embedded
                    // materialises as NULL — the lazy-fallback getter keeps it NPE-safe, and the
                    // derived address helpers tolerate the all-null group.
                    var letterhead = reloaded.getLetterhead();
                    assertThat(letterhead).isNotNull();
                    assertThat(letterhead.getAddressLine1()).isNull();
                    assertThat(letterhead.getAddressLine2()).isNull();
                    assertThat(letterhead.getAddressLine3()).isNull();
                    assertThat(letterhead.getCity()).isNull();
                    assertThat(letterhead.getPostalCode()).isNull();
                    assertThat(letterhead.getCountry()).isNull();
                    assertThat(letterhead.getPhone()).isNull();
                    assertThat(letterhead.getEmail()).isNull();
                    assertThat(letterhead.getWebsite()).isNull();
                    assertThat(letterhead.getBankName()).isNull();
                    assertThat(letterhead.getBankBranchCode()).isNull();
                    assertThat(letterhead.getBankAccountNumber()).isNull();
                    assertThat(letterhead.getBankAccountType()).isNull();
                    assertThat(letterhead.getPaymentReferenceNote()).isNull();
                    assertThat(letterhead.getSignatoryName()).isNull();
                    assertThat(letterhead.getSignatoryDesignation()).isNull();
                    assertThat(letterhead.addressLines()).isEmpty();
                    assertThat(letterhead.addressJoined()).isEmpty();
                  })
              .doesNotThrowAnyException();
        });
  }

  /**
   * Pins the restored {@code updatedAt} contract: before the embeddable refactor every mutator
   * (including plain branding/portal setters) bumped {@code updatedAt}; the entity-level
   * {@code @PreUpdate} callback now does this uniformly on any dirty flush, so mutating a field
   * through an embedded group must still refresh the timestamp.
   */
  @Test
  void updatedAtRefreshesOnEmbeddedGroupMutation() throws Exception {
    var idRef = new AtomicReference<UUID>();
    runInTenant(() -> idRef.set(orgSettingsRepository.save(new OrgSettings("EUR")).getId()));
    UUID id = idRef.get();

    var beforeRef = new AtomicReference<Instant>();
    runInTenant(
        () -> beforeRef.set(orgSettingsRepository.findById(id).orElseThrow().getUpdatedAt()));

    // Guard against clock-resolution ties between the constructor stamp and the flush stamp.
    Thread.sleep(5);

    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findById(id).orElseThrow();
          settings.getBranding().setBrandColor("#123456");
          orgSettingsRepository.save(settings);
        });

    runInTenant(
        () ->
            assertThat(orgSettingsRepository.findById(id).orElseThrow().getUpdatedAt())
                .as("updatedAt must refresh when an embedded group field is mutated")
                .isAfter(beforeRef.get()));
  }

  /**
   * Phase 88 U13 / ADR-350: with the allowlist column unmapped, the portal group is all-nullable
   * ({@code portal_digest_cadence} + {@code digest_last_sent_at}) and materialises as NULL on every
   * fresh tenant. Writes go through the lazy {@link OrgSettings#getPortal()} (e.g. {@code
   * OrgSettingsService} cadence update, {@code PortalDigestScheduler.markDigestSent}) — prove that
   * a write through the lazily created group after a NULL reload is actually persisted.
   */
  @Test
  void portalGroupAllNull_writeThroughLazyGetter_persists() throws Exception {
    var idRef = new AtomicReference<UUID>();
    runInTenant(() -> idRef.set(orgSettingsRepository.save(new OrgSettings("GBP")).getId()));
    UUID id = idRef.get();

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + tenantSchema);
      int updated =
          stmt.executeUpdate(
              "UPDATE org_settings SET portal_digest_cadence = NULL, digest_last_sent_at = NULL"
                  + " WHERE id = '"
                  + id
                  + "'");
      assertThat(updated).isEqualTo(1);
    }

    Instant sentAt = Instant.parse("2026-10-24T08:00:00Z");
    runInTenant(
        () -> {
          var reloaded = orgSettingsRepository.findById(id).orElseThrow();
          reloaded.getPortal().setPortalDigestCadence(PortalDigestCadence.BIWEEKLY);
          reloaded.getPortal().markDigestSent(sentAt);
          orgSettingsRepository.save(reloaded);
        });

    runInTenant(
        () -> {
          var fresh = orgSettingsRepository.findById(id).orElseThrow();
          assertThat(fresh.getPortal().getPortalDigestCadence())
              .as("cadence written through the lazily created portal group must persist")
              .isEqualTo(PortalDigestCadence.BIWEEKLY);
          assertThat(fresh.getPortal().getDigestLastSentAt())
              .as("markDigestSent through the lazily created portal group must persist")
              .isEqualTo(sentAt);
        });
  }

  /**
   * Phase 88 U8 (ADR-353): every fresh tenant reloads the all-nullable letterhead group as NULL.
   * The Firm › General PATCH writes through the lazy {@link OrgSettings#getLetterhead()} — prove
   * that a write through the lazily created group after a NULL reload is actually persisted.
   */
  @Test
  void letterheadGroupAllNull_writeThroughLazyGetter_persists() throws Exception {
    var idRef = new AtomicReference<UUID>();
    runInTenant(() -> idRef.set(orgSettingsRepository.save(new OrgSettings("ZAR")).getId()));
    UUID id = idRef.get();

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + tenantSchema);
      int updated =
          stmt.executeUpdate(
              "UPDATE org_settings SET " + LETTERHEAD_NULLS + " WHERE id = '" + id + "'");
      assertThat(updated).isEqualTo(1);
    }

    runInTenant(
        () -> {
          var reloaded = orgSettingsRepository.findById(id).orElseThrow();
          reloaded.getLetterhead().setBankName("FNB");
          reloaded.getLetterhead().setSignatoryName("Thandi Mathebula");
          orgSettingsRepository.save(reloaded);
        });

    runInTenant(
        () -> {
          var fresh = orgSettingsRepository.findById(id).orElseThrow();
          assertThat(fresh.getLetterhead().getBankName())
              .as("bank name written through the lazily created letterhead group must persist")
              .isEqualTo("FNB");
          assertThat(fresh.getLetterhead().getSignatoryName())
              .as("signatory written through the lazily created letterhead group must persist")
              .isEqualTo("Thandi Mathebula");
          assertThat(fresh.getLetterhead().addressJoined()).isEmpty();
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
