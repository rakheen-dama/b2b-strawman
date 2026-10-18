package io.b2mash.b2b.b2bstrawman.projecttemplate;

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
 * Tenant {@code V2__add_project_template_work_type} backfills the work type of legal-za templates
 * that were pack-seeded before the column existed (the pack seeder never re-applies an installed
 * pack). A scratch schema is migrated to V1, seeded with template rows the way a pre-V2 tenant
 * would hold them, then migrated to latest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTemplateWorkTypeBackfillMigrationTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_template_work_type_backfill_scratch");

  private final DataSource migrationDataSource;
  private Map<String, String> workTypeByKey;

  @Autowired
  ProjectTemplateWorkTypeBackfillMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV1() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP SCHEMA IF EXISTS \"" + SCRATCH_SCHEMA + "\" CASCADE");
      s.execute("CREATE SCHEMA \"" + SCRATCH_SCHEMA + "\"");
    }
    flyway().target(MigrationVersion.fromVersion("1")).load().migrate();

    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(
          """
          INSERT INTO "%s".project_templates (name, name_pattern, source, created_by, description)
          VALUES
            ('Litigation (Road Accident Fund -- RAF)', 'p', 'SEEDER', gen_random_uuid(), 'raf'),
            ('Litigation (Personal Injury / General)', 'p', 'SEEDER', gen_random_uuid(), 'pi'),
            ('Deceased Estate Administration', 'p', 'SEEDER', gen_random_uuid(), 'estates'),
            ('Collections (Debt Recovery)', 'p', 'SEEDER', gen_random_uuid(), 'collections'),
            ('Commercial (Corporate & Contract)', 'p', 'SEEDER', gen_random_uuid(), 'commercial'),
            ('Property Transfer (Conveyancing)', 'p', 'SEEDER', gen_random_uuid(), 'conveyancing'),
            ('Property Transfer (Conveyancing)', 'p', 'MANUAL', gen_random_uuid(), 'manual-copy'),
            ('Some Other Seeded Template', 'p', 'SEEDER', gen_random_uuid(), 'unknown-seeded')
          """
              .formatted(SCRATCH_SCHEMA));
    }

    flyway().load().migrate();

    workTypeByKey = new HashMap<>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT description, work_type FROM \"%s\".project_templates"
                    .formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        workTypeByKey.put(rs.getString("description"), rs.getString("work_type"));
      }
    }
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP SCHEMA IF EXISTS \"" + SCRATCH_SCHEMA + "\" CASCADE");
    }
  }

  @Test
  void seededLegalTemplatesAreBackfilledWithTheirPackMatterType() {
    assertThat(workTypeByKey)
        .containsEntry("raf", "LITIGATION")
        .containsEntry("pi", "LITIGATION")
        .containsEntry("estates", "ESTATES")
        .containsEntry("collections", "COLLECTIONS")
        .containsEntry("commercial", "COMMERCIAL")
        .containsEntry("conveyancing", "CONVEYANCING");
  }

  @Test
  void nonSeededAndUnknownTemplatesAreLeftAlone() {
    assertThat(workTypeByKey).containsEntry("manual-copy", null);
    assertThat(workTypeByKey).containsEntry("unknown-seeded", null);
  }

  private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
