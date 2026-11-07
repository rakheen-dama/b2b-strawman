package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * G89-F52 (D18): tenant {@code V14} adds {@code contains_trust_data} to {@code generated_documents}
 * and {@code documents} and back-fills it for documents generated before the flag existed: a
 * Statement of Account (pack key or a {@code statement-of-account*} slug), a Client Trust Statement
 * or a trust receipt is flagged unless its snapshot records the trust figure as withheld; anything
 * else stays false; the linked {@code documents} row follows its generated document. A scratch
 * tenant schema is migrated to V13, seeded, then migrated to latest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContainsTrustDataMigrationTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_contains_trust_data_scratch");

  private static final String OWNER = "00000000-0000-0000-0000-000000000001";
  private static final String MATTER = "00000000-0000-0000-0000-0000000000a1";

  private final DataSource migrationDataSource;
  private final Map<String, Boolean> generatedFlag = new HashMap<>();
  private final Map<String, Boolean> documentFlag = new HashMap<>();

  @Autowired
  ContainsTrustDataMigrationTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV13() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("13")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_ctd', 'ctd@test.com', id FROM "%s".org_roles WHERE slug = 'owner'
        """);
    exec(
        """
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%m', 'Trust matter', '%o')
        """);
    exec(
        """
        INSERT INTO "%s".document_templates
          (id, name, slug, category, primary_entity_type, source, pack_id, pack_template_key)
        VALUES
          ('00000000-0000-0000-0000-0000000000b1', 'Statement of Account', 'statement-of-account',
           'REPORT', 'PROJECT', 'PLATFORM', 'legal-za', 'statement-of-account'),
          ('00000000-0000-0000-0000-0000000000b2', 'Firm SoA', 'statement-of-account-firm',
           'REPORT', 'PROJECT', 'ORG_CUSTOM', NULL, NULL),
          ('00000000-0000-0000-0000-0000000000b3', 'Client Trust Statement',
           'client-trust-statement', 'REPORT', 'CUSTOMER', 'PLATFORM', 'legal-za',
           'client-trust-statement'),
          ('00000000-0000-0000-0000-0000000000b4', 'Trust Receipt', 'trust-receipt',
           'OTHER', 'CUSTOMER', 'PLATFORM', 'legal-za', 'trust-receipt'),
          ('00000000-0000-0000-0000-0000000000b5', 'Power of Attorney', 'power-of-attorney',
           'OTHER', 'CUSTOMER', 'PLATFORM', 'legal-za', 'power-of-attorney')
        """);
    seed("soa-held", "b1", "{\"summary\":{\"trust_balance_held\":\"4200.00\"}}");
    seed("soa-withheld", "b1", "{\"summary\":{\"trust_withheld\":true}}");
    seed("soa-clone", "b2", "{\"summary\":{\"trust_balance_held\":\"10.00\"}}");
    seed("cts", "b3", "{\"template_name\":\"Client Trust Statement\"}");
    seed("trust-receipt", "b4", null);
    seed("poa", "b5", "{\"template_name\":\"Power of Attorney\"}");

    tenantFlyway().load().migrate();

    read("SELECT file_name, contains_trust_data FROM \"%s\".generated_documents", generatedFlag);
    read("SELECT file_name, contains_trust_data FROM \"%s\".documents", documentFlag);
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void statementsWithATrustFigure_clientTrustStatements_andTrustReceipts_areFlagged() {
    assertThat(generatedFlag)
        .containsEntry("soa-held", true)
        .containsEntry("soa-clone", true)
        .containsEntry("cts", true)
        .containsEntry("trust-receipt", true);
  }

  @Test
  void aStatementWithTrustWithheld_andAPowerOfAttorney_stayUnflagged() {
    assertThat(generatedFlag).containsEntry("soa-withheld", false).containsEntry("poa", false);
  }

  @Test
  void linkedDocuments_followTheirGeneratedDocument() {
    assertThat(documentFlag).isEqualTo(generatedFlag);
  }

  // --- helpers ---

  /** A generated document of {@code templateSuffix} with a linked documents row of that name. */
  private void seed(String name, String templateSuffix, String snapshotJson) throws Exception {
    exec(
        """
        WITH d AS (
          INSERT INTO "%s".documents
            (id, project_id, file_name, content_type, size, s3_key, status, uploaded_by, scope)
          VALUES (gen_random_uuid(), '%m', '%n', 'application/pdf', 10, 'k/%n', 'UPLOADED', '%o', 'PROJECT')
          RETURNING id)
        INSERT INTO "%s".generated_documents
          (template_id, primary_entity_type, primary_entity_id, document_id, file_name, s3_key,
           file_size, generated_by, context_snapshot)
        SELECT '00000000-0000-0000-0000-0000000000%t', 'PROJECT', '%m', d.id, '%n', 'k/%n', 10,
               '%o', %j
        FROM d
        """
            .replace("%n", name)
            .replace("%t", templateSuffix)
            .replace("%j", snapshotJson == null ? "NULL" : "'" + snapshotJson + "'::jsonb"));
  }

  private void read(String sql, Map<String, Boolean> into) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql.replace("%s", SCRATCH_SCHEMA))) {
      while (rs.next()) {
        into.put(rs.getString(1), rs.getBoolean(2));
      }
    }
  }

  private void exec(String sqlWithPlaceholders) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(
          sqlWithPlaceholders
              .replace("%s", SCRATCH_SCHEMA)
              .replace("%o", OWNER)
              .replace("%m", MATTER));
    }
  }

  private FluentConfiguration tenantFlyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
