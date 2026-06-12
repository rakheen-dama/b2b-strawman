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
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgSettingsEmbeddableNullReloadTest {
  private static final String ORG_ID = "org_orgsettings_null_reload";

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
    //    embedded (the genuine NPE risk). The portal group's portal_notification_doc_types column
    //    is NOT NULL (V117 JSONB DEFAULT) and cannot be nulled — it is set to an empty array, which
    //    still exercises the null-safe portal accessors against an otherwise-blank group.
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("SET search_path TO " + tenantSchema);
      int updated =
          stmt.executeUpdate(
              "UPDATE org_settings SET "
                  + "logo_s3_key = NULL, brand_color = NULL, document_footer_text = NULL, "
                  + "portal_retainer_member_display = NULL, portal_digest_cadence = NULL, "
                  + "digest_last_sent_at = NULL, portal_notification_doc_types = '[]'::jsonb, "
                  // Expense + Capacity are single-column nullable groups (Wave 3.4) — nulling the
                  // sole column makes Hibernate materialise a NULL embedded, the genuine NPE risk.
                  + "default_expense_markup_percent = NULL, default_weekly_capacity_hours = NULL, "
                  // Billing has a NOT-NULL-defaulted pair, but default_billing_run_currency is
                  // nullable; null it to exercise the null-safe billing accessor too.
                  + "default_billing_run_currency = NULL, "
                  // Wave 3.5 DataRequest group — all three columns are nullable, so nulling them
                  // makes Hibernate materialise a NULL embedded (the genuine NPE risk).
                  + "data_request_deadline_days = NULL, default_request_reminder_days = NULL, "
                  + "dormancy_threshold_days = NULL, "
                  // Wave 3.5 PackStatus group — all ten jsonb columns are nullable, so nulling them
                  // all makes Hibernate materialise a NULL embedded (the genuine NPE risk).
                  + "field_pack_status = NULL, template_pack_status = NULL, "
                  + "compliance_pack_status = NULL, report_pack_status = NULL, "
                  + "clause_pack_status = NULL, request_pack_status = NULL, "
                  + "automation_pack_status = NULL, rate_pack_status = NULL, "
                  + "schedule_pack_status = NULL, project_template_pack_status = NULL, "
                  // Wave 3.5 DataProtection + TimeReminder groups each carry a NOT NULL column
                  // (retention_policy_enabled / time_reminder_enabled), so the embedded never fully
                  // materialises as NULL — but null their remaining columns to exercise the
                  // null-tolerant accessors on an otherwise-blank group.
                  + "data_protection_jurisdiction = NULL, default_retention_months = NULL, "
                  + "financial_retention_months = NULL, information_officer_name = NULL, "
                  + "information_officer_email = NULL, legal_matter_retention_years = NULL, "
                  + "time_reminder_days = NULL, time_reminder_time = NULL, "
                  + "time_reminder_min_minutes = NULL "
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
                    assertThat(reloaded.getPortal().getPortalRetainerMemberDisplay()).isNull();
                    assertThat(reloaded.getPortal().getPortalDigestCadence()).isNull();
                    assertThat(reloaded.getPortal().getDigestLastSentAt()).isNull();
                    // null-safe accessor returns empty list, never null
                    assertThat(reloaded.getPortal().getPortalNotificationDocTypes()).isEmpty();

                    // Effective-getter fallbacks must still work on the all-null group.
                    assertThat(reloaded.getPortal().getEffectivePortalDigestCadence())
                        .isEqualTo(PortalDigestCadence.WEEKLY);
                    assertThat(reloaded.getPortal().getEffectivePortalRetainerMemberDisplay())
                        .isEqualTo(PortalRetainerMemberDisplay.FIRST_NAME_ROLE);

                    // Wave 3.4 single-column nullable groups: nulling the sole column makes the
                    // embedded materialise as NULL, so the lazy-fallback getter is the only thing
                    // keeping this NPE-safe.
                    assertThat(reloaded.getExpense()).isNotNull();
                    assertThat(reloaded.getExpense().getDefaultExpenseMarkupPercent()).isNull();
                    assertThat(reloaded.getCapacity()).isNotNull();
                    assertThat(reloaded.getCapacity().getDefaultWeeklyCapacityHours()).isNull();

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

                    // Wave 3.5 PackStatus group: all ten jsonb columns nulled, so the embedded
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
                    assertThat(reloaded.getPackStatus().getRatePackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getSchedulePackStatus()).isNull();
                    assertThat(reloaded.getPackStatus().getProjectTemplatePackStatus()).isNull();
                    // is*Applied accessors must tolerate the all-null group (return false, no NPE).
                    assertThat(reloaded.getPackStatus().isAutomationPackApplied("x")).isFalse();
                    assertThat(reloaded.getPackStatus().isRatePackApplied("x", 1)).isFalse();

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

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
