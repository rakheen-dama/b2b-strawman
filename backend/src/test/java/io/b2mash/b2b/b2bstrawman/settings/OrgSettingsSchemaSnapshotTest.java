package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
 * Schema snapshot / safety harness for the {@code org_settings} table (Wave 3.3 embeddable
 * refactor). Pins the ENTIRE table shape — every column's name, data type (including varchar length
 * and numeric precision/scale), and nullability — as materialised by Flyway + Hibernate DDL
 * validation against the embedded-Postgres test database.
 *
 * <p>This test is the refactor's safety net: extracting fields into {@code @Embeddable} groups via
 * {@code @Embedded} + {@code @AttributeOverride} must produce ZERO schema change. If any
 * {@code @AttributeOverride} drifts a column name, type, or nullability, the pinned snapshot below
 * will fail. DO NOT update the pinned snapshot to "make it pass" without an authorised, intentional
 * schema migration — that is the entire point of the harness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgSettingsSchemaSnapshotTest {
  private static final String ORG_ID = "org_orgsettings_schema_snapshot";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  /**
   * Pinned shape of {@code org_settings}: one line per column, sorted by column name, formatted as
   * {@code <column_name> | <data_type>[(<varchar_length>|<precision,scale>)] | <is_nullable>}.
   * Captured against the UNMODIFIED entity before the embeddable refactor; varchar lengths and
   * numeric precision/scale added to the pin so an {@code @AttributeOverride} length/precision
   * drift cannot pass silently (Hibernate ddl-auto is none here — Flyway owns DDL — so the
   * information_schema snapshot is the only guard).
   */
  // V133 (Phase 83): the five collections_* columns added below are a deliberate, authorised pin
  // update (CollectionsSettings embeddable + V133 migration), not an accidental drift.
  private static final String EXPECTED_SNAPSHOT =
      """
      acceptance_expiry_days | integer | YES
      accounting_enabled | boolean | NO
      ai_enabled | boolean | NO
      automation_pack_status | jsonb | YES
      billing_batch_async_threshold | integer | NO
      billing_email_rate_limit | integer | NO
      brand_color | character varying(7) | YES
      clause_pack_status | jsonb | YES
      collections_enabled | boolean | NO
      collections_escalate_days | integer | YES
      collections_stage1_days | integer | YES
      collections_stage2_days | integer | YES
      collections_stage3_days | integer | YES
      compliance_pack_status | jsonb | YES
      created_at | timestamp with time zone | NO
      data_protection_jurisdiction | character varying(10) | YES
      data_request_deadline_days | integer | YES
      deal_pipeline_pack_status | jsonb | YES
      default_billing_run_currency | character varying(3) | YES
      default_currency | character varying(3) | NO
      default_expense_markup_percent | numeric(5,2) | YES
      default_request_reminder_days | integer | YES
      default_retention_months | integer | YES
      default_weekly_capacity_hours | numeric(5,2) | YES
      digest_last_sent_at | timestamp with time zone | YES
      document_footer_text | text | YES
      document_signing_enabled | boolean | NO
      dormancy_threshold_days | integer | YES
      enabled_modules | jsonb | YES
      field_pack_status | jsonb | YES
      financial_retention_months | integer | YES
      id | uuid | NO
      information_officer_email | character varying(255) | YES
      information_officer_name | character varying(255) | YES
      legal_matter_retention_years | integer | YES
      logo_s3_key | character varying(500) | YES
      onboarding_dismissed_at | timestamp with time zone | YES
      portal_digest_cadence | character varying(12) | YES
      portal_notification_doc_types | jsonb | NO
      portal_retainer_member_display | character varying(20) | YES
      project_naming_pattern | character varying(500) | YES
      project_template_pack_status | jsonb | YES
      rate_pack_status | jsonb | YES
      report_pack_status | jsonb | YES
      request_pack_status | jsonb | YES
      retention_policy_enabled | boolean | NO
      schedule_pack_status | jsonb | YES
      tax_inclusive | boolean | NO
      tax_label | character varying(20) | YES
      tax_registration_label | character varying(30) | YES
      tax_registration_number | character varying(50) | YES
      template_pack_status | jsonb | YES
      terminology_namespace | character varying(100) | YES
      time_reminder_days | character varying(50) | YES
      time_reminder_enabled | boolean | NO
      time_reminder_min_minutes | integer | YES
      time_reminder_time | time without time zone | YES
      updated_at | timestamp with time zone | NO
      vertical_profile | character varying(50) | YES
      """
          .strip();

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OrgSettings Schema Snapshot Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_oss_owner", "oss_owner@test.com", "OSS Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void orgSettingsTableShapeIsPinned() throws Exception {
    String actual = readSnapshot();
    assertThat(actual)
        .as(
            "org_settings table shape must match the pinned snapshot exactly (zero schema change). "
                + "If this fails after the embeddable refactor, an @AttributeOverride drifted a "
                + "column name/type/nullability.\n\n--- ACTUAL ---\n%s\n",
            actual)
        .isEqualTo(EXPECTED_SNAPSHOT);
  }

  private String readSnapshot() throws Exception {
    List<String> lines = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      ResultSet rs =
          stmt.executeQuery(
              "SELECT column_name, data_type, is_nullable, "
                  + "character_maximum_length, numeric_precision, numeric_scale "
                  + "FROM information_schema.columns "
                  + "WHERE table_schema = '"
                  + tenantSchema
                  + "' AND table_name = 'org_settings' "
                  + "ORDER BY column_name");
      while (rs.next()) {
        String dataType = rs.getString("data_type");
        String detail = "";
        if ("character varying".equals(dataType)) {
          detail = "(" + rs.getInt("character_maximum_length") + ")";
        } else if ("numeric".equals(dataType)) {
          detail = "(" + rs.getInt("numeric_precision") + "," + rs.getInt("numeric_scale") + ")";
        }
        lines.add(
            rs.getString("column_name")
                + " | "
                + dataType
                + detail
                + " | "
                + rs.getString("is_nullable"));
      }
    }
    return String.join("\n", lines);
  }
}
