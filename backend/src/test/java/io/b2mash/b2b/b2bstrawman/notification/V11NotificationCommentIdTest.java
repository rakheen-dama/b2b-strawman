package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * Tenant {@code V11__phase89_comment_notification_id} (Phase 89 634C, G88-M6, architecture §14.7.1
 * §2): {@code notifications.reference_comment_id uuid NULL} — no FK (a deleted comment must not
 * cascade to the notification) and no index (read, never searched). A scratch schema is migrated to
 * V10, seeded with a notification row, then migrated to V11.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V11NotificationCommentIdTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v11_notification_comment_id_scratch");

  private static final String NOTIFICATION_ID = "00000000-0000-0000-0000-00000000b113";

  private final DataSource migrationDataSource;
  private String notificationXminBefore;
  private String notificationXminAfter;

  @Autowired
  V11NotificationCommentIdTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV10() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("10")).load().migrate();
    exec(
        """
        INSERT INTO "%s".org_roles (id, name, slug) VALUES
          ('00000000-0000-0000-0000-00000000b110', 'Owner', 'v11-owner');
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id) VALUES
          ('00000000-0000-0000-0000-00000000b111', 'user_v11', 'v11@test.com',
           '00000000-0000-0000-0000-00000000b110');
        INSERT INTO "%s".notifications
          (id, recipient_member_id, type, title, reference_entity_type, reference_entity_id,
           reference_project_id)
        VALUES
          ('00000000-0000-0000-0000-00000000b113', '00000000-0000-0000-0000-00000000b111',
           'COMMENT_ADDED', 'Alice commented on task "Draft"', 'TASK',
           '00000000-0000-0000-0000-00000000b114', '00000000-0000-0000-0000-00000000b115');
        """);

    notificationXminBefore = notificationRowVersion();
    flyway().target(MigrationVersion.fromVersion("11")).load().migrate();
    notificationXminAfter = notificationRowVersion();
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void referenceCommentIdIsNullableUuidWithoutDefault() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'notifications'
                  AND column_name = 'reference_comment_id'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("uuid");
      assertThat(rs.getString(2)).isEqualTo("YES");
      assertThat(rs.getString(3)).isNull();
      assertThat(rs.next()).isFalse();
    }
  }

  /** Pre-V11 rows read NULL (they keep the entity-level link) and are not rewritten. */
  @Test
  void existingRowReadsNullAndIsNotRewritten() throws Exception {
    assertThat(
            queryString(
                "SELECT coalesce(reference_comment_id::text, 'NULL') FROM \"%s\".notifications"
                    + " WHERE id = '%s'"))
        .isEqualTo("NULL");
    // ADD COLUMN without a default is a catalog-only change — the existing row keeps its xmin.
    assertThat(notificationXminAfter).isNotNull().isEqualTo(notificationXminBefore);
  }

  /** No FK: a deleted comment must not cascade-delete the notification that mentions it. */
  @Test
  void referenceCommentIdHasNoForeignKey() throws Exception {
    assertThat(
            queryString(
                """
                SELECT string_agg(c.conname, ',' ORDER BY c.conname)
                FROM pg_constraint c
                WHERE c.conrelid = '"%s".notifications'::regclass AND c.contype = 'f'
                """))
        .isEqualTo("notifications_recipient_member_id_fkey");
    assertThat(
            queryString(
                """
                SELECT count(*)::text
                FROM pg_constraint c
                WHERE c.conrelid = '"%s".notifications'::regclass
                  AND pg_get_constraintdef(c.oid) LIKE '%%reference_comment_id%%'
                """))
        .isEqualTo("0");
  }

  /** No index: the column is read, never searched. */
  @Test
  void referenceCommentIdHasNoIndex() throws Exception {
    assertThat(
            queryString(
                """
                SELECT count(*)::text FROM pg_indexes
                WHERE schemaname = '%s' AND tablename = 'notifications'
                  AND indexdef LIKE '%%reference_comment_id%%'
                """))
        .isEqualTo("0");
  }

  /** Re-running the whole V11 body is a no-op; a second migrate to V11 executes nothing. */
  @Test
  void reRunningTheV11BodyIsANoOp() throws Exception {
    String v11 =
        new String(
            getClass()
                .getResourceAsStream(
                    "/db/migration/tenant/V11__phase89_comment_notification_id.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    String before = notificationRowVersion();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v11);
      } finally {
        s.execute("RESET search_path"); // pooled connection — leave it as found
      }
    }

    assertThat(notificationRowVersion()).isEqualTo(before);
    assertThat(
            queryString(
                """
                SELECT count(*)::text FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'notifications'
                  AND column_name = 'reference_comment_id'
                """))
        .isEqualTo("1");
    assertThat(
            flyway().target(MigrationVersion.fromVersion("11")).load().migrate().migrationsExecuted)
        .isZero();
  }

  private String notificationRowVersion() throws Exception {
    return queryString("SELECT xmin::text FROM \"%s\".notifications WHERE id = '%s'");
  }

  /**
   * {@code %s} placeholders: first = scratch schema, second (optional) = the seeded notification
   * id. {@code %%} is a literal percent (LIKE patterns).
   */
  private String queryString(String sql) throws Exception {
    String resolved =
        sql.replaceFirst("%s", SCRATCH_SCHEMA).replace("%s", NOTIFICATION_ID).replace("%%", "%");
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(resolved)) {
      return rs.next() ? rs.getString(1) : null;
    }
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
