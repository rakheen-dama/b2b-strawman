package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 93 / 656A (task 656.2, architecture §17.2.6, §17.7.5): tenant {@code
 * V35__phase93_go_live_checklist} (guide V33; V34 taken by 655A — next free, D-154). A scratch
 * schema is walked to V34 with an owner member, then migrated: the table has its four columns, the
 * key CHECK accepts exactly the four items, the key is one row per item, the member reference is a
 * foreign key that never cascades, and re-running the body changes nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnGoLiveSchemaTest {

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V35__phase93_go_live_checklist.sql";
  private static final String PREVIOUS_VERSION = "34";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String S =
      SchemaNameGenerator.generateSchemaName("org_takeon_go_live_scratch");

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final List<String> KEYS =
      List.of(
          "TRUST_RECORDS_RETAINED",
          "FICA_RECORDS_RETAINED",
          "ARCHIVE_HOSTING_CONSIDERED",
          "AUDITOR_INFORMED");

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  @BeforeAll
  void walkToV34() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
    exec("CREATE SCHEMA \"" + S + "\"");
    tenantFlyway().target(MigrationVersion.fromVersion(PREVIOUS_VERSION)).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_glv', 'glv@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        """
            .replace("%s", S)
            .replace("%o", OWNER.toString()));
    tenantFlyway().load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
  }

  // 1
  @Test
  void migrationAppliesOnAWalkedSchema_withTheFourColumns() {
    // Applied (not "the latest": a later migration must not break this pin).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".flyway_schema_history WHERE success AND version = ?"
                    .formatted(S),
                Integer.class,
                "35"))
        .isEqualTo(1);
    List<Map<String, Object>> columns =
        jdbc.queryForList(
            """
            SELECT column_name, data_type, is_nullable, character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = 'take_on_go_live_items'
            ORDER BY ordinal_position
            """,
            S);
    assertThat(columns)
        .extracting(c -> c.get("column_name"))
        .containsExactly("item_key", "acknowledged_by", "acknowledged_at", "note");
    assertThat(columns.get(0))
        .containsEntry("data_type", "character varying")
        .containsEntry("is_nullable", "NO")
        .containsEntry("character_maximum_length", 40);
    assertThat(columns.get(1))
        .containsEntry("data_type", "uuid")
        .containsEntry("is_nullable", "NO");
    assertThat(columns.get(2))
        .containsEntry("data_type", "timestamp with time zone")
        .containsEntry("is_nullable", "NO");
    assertThat(columns.get(3))
        .containsEntry("data_type", "character varying")
        .containsEntry("is_nullable", "YES")
        .containsEntry("character_maximum_length", 1000);
  }

  // 2
  @Test
  void theKeyCheck_acceptsExactlyTheFourItems() {
    String def = constraintDef("chk_go_live_item_key");
    for (String key : KEYS) {
      assertThat(def).contains("'" + key + "'");
    }
    for (String key : KEYS) {
      assertThat(insert(key, OWNER)).as("%s is accepted", key).isEqualTo(1);
    }
    jdbc.update("DELETE FROM \"%s\".take_on_go_live_items".formatted(S));
    assertRejected(() -> insert("SOMETHING_ELSE", OWNER), "chk_go_live_item_key");
    assertRejected(() -> insert("trust_records_retained", OWNER), "chk_go_live_item_key");
  }

  // 3
  @Test
  void oneRowPerItem_andTheRequiredColumnsAreRequired() {
    assertThat(insert("TRUST_RECORDS_RETAINED", OWNER)).isEqualTo(1);
    assertRejected(() -> insert("TRUST_RECORDS_RETAINED", OWNER), "take_on_go_live_items_pkey");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    ("INSERT INTO \"%s\".take_on_go_live_items (item_key, acknowledged_at)"
                            + " VALUES ('FICA_RECORDS_RETAINED', now())")
                        .formatted(S)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("acknowledged_by");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    ("INSERT INTO \"%s\".take_on_go_live_items (item_key, acknowledged_by)"
                            + " VALUES ('FICA_RECORDS_RETAINED', ?)")
                        .formatted(S),
                    OWNER))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("acknowledged_at");
    jdbc.update(
        "DELETE FROM \"%s\".take_on_go_live_items WHERE item_key = 'TRUST_RECORDS_RETAINED'"
            .formatted(S));
  }

  // 4
  @Test
  void theMemberReference_isAForeignKeyThatNeverCascades() {
    assertRejected(
        () -> insert("AUDITOR_INFORMED", UUID.randomUUID()), "take_on_go_live_items_member_fkey");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT c.confdeltype::text FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.conname = ? AND n.nspname = ?
                """,
                String.class,
                "take_on_go_live_items_member_fkey",
                S))
        .isEqualTo("a");
  }

  // 5
  @Test
  void rerunningTheMigrationBody_changesNothing() throws Exception {
    String checkBefore = constraintDef("chk_go_live_item_key");
    rerunMigrationBody();
    assertThat(constraintDef("chk_go_live_item_key")).isEqualTo(checkBefore);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = ? AND c.conname IN ('take_on_go_live_items_pkey',
                  'chk_go_live_item_key', 'take_on_go_live_items_member_fkey')
                """,
                Integer.class,
                S))
        .isEqualTo(3);
  }

  // --- helpers ---

  private int insert(String key, UUID member) {
    return jdbc.update(
        ("INSERT INTO \"%s\".take_on_go_live_items (item_key, acknowledged_by, acknowledged_at)"
                + " VALUES (?, ?, now())")
            .formatted(S),
        key,
        member);
  }

  private String constraintDef(String name) {
    return jdbc.queryForObject(
        """
        SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.conname = ? AND n.nspname = ?
        """,
        String.class,
        name,
        S);
  }

  private static void assertRejected(Runnable write, String constraint) {
    assertThatThrownBy(write::run)
        .as("rejected by %s", constraint)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
  }

  private void rerunMigrationBody() throws Exception {
    var body =
        new String(
            new ClassPathResource(MIGRATION_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        // SET LOCAL: the search_path reverts at commit, so the pooled connection is unaffected.
        s.execute("SET LOCAL search_path TO \"" + S + "\"");
        s.execute(body);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
  }

  private void exec(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private FluentConfiguration tenantFlyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(S)
        .baselineOnMigrate(false);
  }
}
