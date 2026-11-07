package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * G89-F26 / D12: the legal-za matter templates' descriptions ended "Matter type: LITIGATION" (the
 * enum, not a label) and every matter created from them — and its portal copy — inherited it. The
 * pack (v4) no longer ships the suffix; tenant {@code V13} strips it from seeded project templates
 * and from matters, and global {@code V5} from the portal read-model copy. A scratch tenant schema
 * is migrated to V12 and a scratch global database to V4, seeded, then migrated to latest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterTypeSuffixMigrationTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_matter_type_suffix_scratch");
  private static final String SCRATCH_GLOBAL_DB = "global_matter_type_suffix_scratch";

  private static final String OWNER = "00000000-0000-0000-0000-000000000001";

  private final DataSource migrationDataSource;
  private DataSource scratchGlobal;
  private final Map<String, String> projectDescription = new HashMap<>();
  private final Map<String, String> templateDescription = new HashMap<>();
  private final Map<String, String> portalDescription = new HashMap<>();

  @Autowired
  MatterTypeSuffixMigrationTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemas() throws Exception {
    migrateTenantScratchFromV12();
    migrateGlobalScratchFromV4();
  }

  private void migrateTenantScratchFromV12() throws Exception {
    exec(migrationDataSource, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec(migrationDataSource, "CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("12")).load().migrate();
    exec(
        migrationDataSource,
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_mts', 'mts@test.com', id FROM "%s".org_roles WHERE slug = 'owner'
        """);
    exec(
        migrationDataSource,
        """
        INSERT INTO "%s".projects (name, created_by, description)
        VALUES
          ('suffixed', '%o', 'Debt recovery workflow from demand to execution. Matter type: COLLECTIONS'),
          ('only-suffix', '%o', 'Matter type: LITIGATION'),
          ('mid-sentence', '%o', 'Note the Matter type: LITIGATION field before filing.'),
          ('lowercase', '%o', 'Ours. Matter type: litigation'),
          ('plain', '%o', 'A matter without the suffix'),
          ('null', '%o', NULL)
        """);
    exec(
        migrationDataSource,
        """
        INSERT INTO "%s".project_templates (name, name_pattern, source, created_by, description)
        VALUES
          ('seeded', 'p', 'SEEDER', '%o', 'Administration of deceased estate from reporting to final distribution. Matter type: ESTATES'),
          ('manual', 'p', 'MANUAL', '%o', 'Firm template. Matter type: COMMERCIAL')
        """);

    tenantFlyway().load().migrate();

    readInto(
        migrationDataSource, "SELECT name, description FROM \"%s\".projects", projectDescription);
    readInto(
        migrationDataSource,
        "SELECT name, description FROM \"%s\".project_templates",
        templateDescription);
  }

  private void migrateGlobalScratchFromV4() throws Exception {
    String serverUrl;
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
      s.execute("CREATE DATABASE " + SCRATCH_GLOBAL_DB);
      serverUrl = c.getMetaData().getURL();
    }
    String scratchUrl =
        serverUrl.replaceFirst("^(jdbc:postgresql://[^/]+/)[^?]*", "$1" + SCRATCH_GLOBAL_DB);
    try (Connection c = migrationDataSource.getConnection()) {
      scratchGlobal =
          new SimpleDriverDataSource(
              new org.postgresql.Driver(), scratchUrl, c.getMetaData().getUserName(), "postgres");
    }
    globalFlyway().target(MigrationVersion.fromVersion("4")).load().migrate();
    exec(
        scratchGlobal,
        """
        INSERT INTO portal.portal_projects (id, org_id, customer_id, name, status, description, created_at)
        VALUES
          (gen_random_uuid(), 'org_x', gen_random_uuid(), 'suffixed', 'ACTIVE',
           'Standard conveyancing workflow. Matter type: CONVEYANCING', now()),
          (gen_random_uuid(), 'org_x', gen_random_uuid(), 'plain', 'ACTIVE', 'No suffix here', now()),
          (gen_random_uuid(), 'org_x', gen_random_uuid(), 'null', 'ACTIVE', NULL, now())
        """);

    globalFlyway().load().migrate();

    readInto(
        scratchGlobal, "SELECT name, description FROM portal.portal_projects", portalDescription);
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec(migrationDataSource, "DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
    }
  }

  @Test
  void matterDescriptions_loseTheTrailingMatterTypeSuffix() {
    assertThat(projectDescription)
        .containsEntry("suffixed", "Debt recovery workflow from demand to execution.")
        .containsEntry("only-suffix", null);
  }

  @Test
  void matterDescriptions_withoutATrailingEnumSuffix_areUntouched() {
    assertThat(projectDescription)
        .containsEntry("mid-sentence", "Note the Matter type: LITIGATION field before filing.")
        .containsEntry("lowercase", "Ours. Matter type: litigation")
        .containsEntry("plain", "A matter without the suffix")
        .containsEntry("null", null);
  }

  @Test
  void seededProjectTemplates_loseTheSuffix_firmTemplatesAreUntouched() {
    assertThat(templateDescription)
        .containsEntry(
            "seeded", "Administration of deceased estate from reporting to final distribution.")
        .containsEntry("manual", "Firm template. Matter type: COMMERCIAL");
  }

  @Test
  void portalMatterCopies_loseTheSuffix() {
    assertThat(portalDescription)
        .containsEntry("suffixed", "Standard conveyancing workflow.")
        .containsEntry("plain", "No suffix here")
        .containsEntry("null", null);
  }

  @Test
  void legalZaProjectTemplatePack_shipsNoMatterTypeSuffix() throws Exception {
    try (InputStream in =
        new ClassPathResource("project-template-packs/legal-za.json").getInputStream()) {
      String pack = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(pack).doesNotContain("Matter type:");
      assertThat(pack).contains("\"version\": 4");
    }
  }

  // --- helpers ---

  private static void readInto(DataSource ds, String sql, Map<String, String> into)
      throws Exception {
    try (Connection c = ds.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql.replace("%s", SCRATCH_SCHEMA))) {
      while (rs.next()) {
        into.put(rs.getString(1), rs.getString(2));
      }
    }
  }

  private static void exec(DataSource ds, String sqlWithPlaceholders) throws Exception {
    try (Connection c = ds.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sqlWithPlaceholders.replace("%s", SCRATCH_SCHEMA).replace("%o", OWNER));
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

  private FluentConfiguration globalFlyway() {
    return Flyway.configure()
        .dataSource(scratchGlobal)
        .locations("classpath:db/migration/global")
        .schemas("public")
        .baselineOnMigrate(false);
  }
}
