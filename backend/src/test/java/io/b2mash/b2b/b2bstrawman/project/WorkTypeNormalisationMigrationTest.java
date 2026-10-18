package io.b2mash.b2b.b2bstrawman.project;

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
 * Tenant {@code V3__normalise_work_types} rewrites existing free-text work types into the canonical
 * form {@link WorkTypes#normalize} produces, so stored values written before the normaliser existed
 * match work-type-scoped field groups. A scratch schema is migrated to V2, seeded with mixed-case,
 * blank, canonical and NULL values, then migrated to latest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkTypeNormalisationMigrationTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_work_type_normalise_scratch");

  private final DataSource migrationDataSource;
  private final Map<String, String> projectWorkType = new HashMap<>();
  // Row versions (xmin) before V3 and after V3, keyed "<table>:<name>" — an untouched row keeps it.
  private Map<String, String> xminBefore;
  private Map<String, String> xminAfter;
  private final Map<String, String> templateWorkType = new HashMap<>();
  private final Map<String, String> groupWorkTypes = new HashMap<>();

  @Autowired
  WorkTypeNormalisationMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV2() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("2")).load().migrate();

    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '00000000-0000-0000-0000-000000000001', 'user_wtn', 'wtn@test.com', id
        FROM "%s".org_roles WHERE slug = 'owner'
        """);
    exec(
        """
        INSERT INTO "%s".projects (name, created_by, work_type)
        VALUES
          ('mixed', '00000000-0000-0000-0000-000000000001', 'Conveyancing'),
          ('spaced', '00000000-0000-0000-0000-000000000001', ' personal  injury '),
          ('tabbed', '00000000-0000-0000-0000-000000000001', E'\\tEstates\\n'),
          ('blank', '00000000-0000-0000-0000-000000000001', '   '),
          ('empty', '00000000-0000-0000-0000-000000000001', ''),
          ('canonical', '00000000-0000-0000-0000-000000000001', 'LITIGATION'),
          ('null', '00000000-0000-0000-0000-000000000001', NULL)
        """);
    exec(
        """
        INSERT INTO "%s".project_templates (name, name_pattern, source, created_by, work_type)
        VALUES
          ('mixed', 'p', 'MANUAL', gen_random_uuid(), 'collections'),
          ('blank', 'p', 'MANUAL', gen_random_uuid(), '  '),
          ('canonical', 'p', 'SEEDER', gen_random_uuid(), 'CONVEYANCING'),
          ('null', 'p', 'MANUAL', gen_random_uuid(), NULL)
        """);
    exec(
        """
        INSERT INTO "%s".field_groups
          (entity_type, name, slug, applicable_work_types)
        VALUES
          ('PROJECT', 'mixed', 'mixed', '["conveyancing", "LITIGATION", "Personal Injury"]'),
          ('PROJECT', 'blank-entry', 'blank-entry', '["  ", "estates"]'),
          ('PROJECT', 'canonical', 'canonical', '["CONVEYANCING"]'),
          ('PROJECT', 'empty', 'empty', '[]'),
          ('PROJECT', 'null', 'null', NULL)
        """);

    xminBefore = rowVersions();
    flyway().load().migrate();
    xminAfter = rowVersions();

    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try (ResultSet rs =
          s.executeQuery("SELECT name, work_type FROM \"%s\".projects".formatted(SCRATCH_SCHEMA))) {
        while (rs.next()) {
          projectWorkType.put(rs.getString(1), rs.getString(2));
        }
      }
      try (ResultSet rs =
          s.executeQuery(
              "SELECT name, work_type FROM \"%s\".project_templates".formatted(SCRATCH_SCHEMA))) {
        while (rs.next()) {
          templateWorkType.put(rs.getString(1), rs.getString(2));
        }
      }
      try (ResultSet rs =
          s.executeQuery(
              "SELECT name, applicable_work_types::text FROM \"%s\".field_groups"
                  .formatted(SCRATCH_SCHEMA))) {
        while (rs.next()) {
          groupWorkTypes.put(rs.getString(1), rs.getString(2));
        }
      }
    }
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void projectWorkTypesAreNormalisedWithTheJavaRule() {
    assertThat(projectWorkType)
        .containsEntry("mixed", "CONVEYANCING")
        .containsEntry("spaced", "PERSONAL_INJURY")
        .containsEntry("tabbed", "ESTATES")
        .containsEntry("blank", null)
        .containsEntry("empty", null);
    assertThat(projectWorkType.get("spaced")).isEqualTo(WorkTypes.normalize(" personal  injury "));
  }

  @Test
  void canonicalAndNullProjectsAreUntouched() {
    assertThat(projectWorkType)
        .containsEntry("canonical", "LITIGATION")
        .containsEntry("null", null);
    assertUntouched("projects:canonical", "projects:null");
    assertThat(xminAfter.get("projects:mixed")).isNotEqualTo(xminBefore.get("projects:mixed"));
  }

  @Test
  void templateWorkTypesAreNormalised() {
    assertThat(templateWorkType)
        .containsEntry("mixed", "COLLECTIONS")
        .containsEntry("blank", null)
        .containsEntry("canonical", "CONVEYANCING")
        .containsEntry("null", null);
    assertUntouched("project_templates:canonical", "project_templates:null");
  }

  @Test
  void fieldGroupScopesAreNormalisedAndBlankEntriesDropped() {
    assertThat(groupWorkTypes)
        .containsEntry("mixed", "[\"CONVEYANCING\", \"LITIGATION\", \"PERSONAL_INJURY\"]")
        .containsEntry("blank-entry", "[\"ESTATES\"]");
  }

  @Test
  void canonicalEmptyAndNullFieldGroupsAreUntouched() {
    assertThat(groupWorkTypes)
        .containsEntry("canonical", "[\"CONVEYANCING\"]")
        .containsEntry("empty", "[]")
        .containsEntry("null", null);
    assertUntouched("field_groups:canonical", "field_groups:empty", "field_groups:null");
  }

  @Test
  void migrationIsIdempotentWhenReRunAgainstNormalisedRows() throws Exception {
    // Re-applying the V3 statements to already-normalised data changes nothing.
    String v3 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V3__normalise_work_types.sql")
                .readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
      s.execute(v3);
    }
    assertThat(rowVersions()).isEqualTo(xminAfter);
  }

  private void assertUntouched(String... keys) {
    for (String key : keys) {
      assertThat(xminAfter.get(key)).as(key).isNotNull().isEqualTo(xminBefore.get(key));
    }
  }

  private Map<String, String> rowVersions() throws Exception {
    var versions = new HashMap<String, String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      for (String table : new String[] {"projects", "project_templates", "field_groups"}) {
        try (ResultSet rs =
            s.executeQuery(
                "SELECT name, xmin::text FROM \"%s\".%s".formatted(SCRATCH_SCHEMA, table))) {
          while (rs.next()) {
            versions.put(table + ":" + rs.getString(1), rs.getString(2));
          }
        }
      }
    }
    return versions;
  }

  private void exec(String sqlWithSchemaPlaceholders) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sqlWithSchemaPlaceholders.replace("%s", SCRATCH_SCHEMA));
    }
  }

  private FluentConfiguration flyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
