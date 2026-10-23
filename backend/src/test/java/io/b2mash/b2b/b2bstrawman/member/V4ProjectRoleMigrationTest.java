package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
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
 * Tenant {@code V4__phase88_demo_ready} §1 (ADR-345) rewrites {@code project_members.project_role}
 * to the canonical {@code lead}/{@code member} in the index-safe order — strays to member, demote
 * duplicate leads case-insensitively keeping the earliest (created_at, id), then lower-case — and
 * adds {@code chk_project_members_role}. A scratch schema is migrated to V3, seeded with the
 * mixed-case duplicate-lead shape (an older 'lead' and a newer 'LEAD' on one matter, which the
 * lowercase-only partial unique index would reject if lower-cased first), the reverse shape (an
 * older 'LEAD' and a newer 'lead'), a 'CONTRIBUTOR' and a 'MEMBER' row, then migrated to V4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4ProjectRoleMigrationTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v4_project_role_scratch");

  // project_members rows
  private static final String OLDER_LEAD = "00000000-0000-0000-0000-0000000000c1"; // 'lead' -2d
  private static final String NEWER_LEAD = "00000000-0000-0000-0000-0000000000c2"; // 'LEAD' -1d
  private static final String CONTRIBUTOR = "00000000-0000-0000-0000-0000000000c3";
  private static final String UPPER_MEMBER = "00000000-0000-0000-0000-0000000000c4";
  private static final String TIE_LOW_ID = "00000000-0000-0000-0000-0000000000d1"; // 'LEAD'
  private static final String TIE_HIGH_ID = "00000000-0000-0000-0000-0000000000d2"; // 'Lead'
  private static final String LONE_UPPER_LEAD = "00000000-0000-0000-0000-0000000000e1";
  private static final String CANONICAL_MEMBER = "00000000-0000-0000-0000-0000000000e2";
  private static final String REVERSE_OLDER_UPPER =
      "00000000-0000-0000-0000-0000000000f1"; // 'LEAD'
  private static final String REVERSE_NEWER_LOWER =
      "00000000-0000-0000-0000-0000000000f2"; // 'lead'

  private final DataSource migrationDataSource;
  private final Map<String, String> roleById = new HashMap<>();
  private Map<String, String> xminBefore;
  private Map<String, String> xminAfter;

  @Autowired
  V4ProjectRoleMigrationTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV3() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("3")).load().migrate();

    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT v.id::uuid, v.clerk, v.email, r.id
        FROM (VALUES
          ('00000000-0000-0000-0000-0000000000a1', 'user_v4_a1', 'v4_a1@test.com'),
          ('00000000-0000-0000-0000-0000000000a2', 'user_v4_a2', 'v4_a2@test.com'),
          ('00000000-0000-0000-0000-0000000000a3', 'user_v4_a3', 'v4_a3@test.com'),
          ('00000000-0000-0000-0000-0000000000a4', 'user_v4_a4', 'v4_a4@test.com')
        ) AS v(id, clerk, email)
        CROSS JOIN "%s".org_roles r
        WHERE r.slug = 'member'
        """);
    exec(
        """
        INSERT INTO "%s".projects (id, name, created_by) VALUES
          ('00000000-0000-0000-0000-0000000000b1', 'v4 mixed duplicate lead',
           '00000000-0000-0000-0000-0000000000a1'),
          ('00000000-0000-0000-0000-0000000000b2', 'v4 tie on created_at',
           '00000000-0000-0000-0000-0000000000a1'),
          ('00000000-0000-0000-0000-0000000000b3', 'v4 template-only lead',
           '00000000-0000-0000-0000-0000000000a1'),
          ('00000000-0000-0000-0000-0000000000b4', 'v4 reverse mixed duplicate lead',
           '00000000-0000-0000-0000-0000000000a1')
        """);
    exec(
        """
        INSERT INTO "%s".project_members (id, project_id, member_id, project_role, created_at)
        VALUES
          -- one matter: older lowercase lead (creator) + newer uppercase lead (template) + strays
          ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1',
           '00000000-0000-0000-0000-0000000000a1', 'lead', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
          ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000b1',
           '00000000-0000-0000-0000-0000000000a2', 'LEAD', TIMESTAMPTZ '2026-01-02 00:00:00+00'),
          ('00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000b1',
           '00000000-0000-0000-0000-0000000000a3', 'CONTRIBUTOR', TIMESTAMPTZ '2026-01-03 00:00:00+00'),
          ('00000000-0000-0000-0000-0000000000c4', '00000000-0000-0000-0000-0000000000b1',
           '00000000-0000-0000-0000-0000000000a4', 'MEMBER', TIMESTAMPTZ '2026-01-03 00:00:00+00'),
          -- equal created_at: the smaller id keeps the lead
          ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000b2',
           '00000000-0000-0000-0000-0000000000a1', 'LEAD', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
          ('00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-0000000000b2',
           '00000000-0000-0000-0000-0000000000a2', 'Lead', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
          -- the walked-tenant shape: a template-only uppercase lead, plus an already-canonical row
          ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b3',
           '00000000-0000-0000-0000-0000000000a1', 'LEAD', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
          ('00000000-0000-0000-0000-0000000000e2', '00000000-0000-0000-0000-0000000000b3',
           '00000000-0000-0000-0000-0000000000a2', 'member', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
          -- reverse shape: older uppercase lead + newer lowercase lead on one matter
          ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000b4',
           '00000000-0000-0000-0000-0000000000a1', 'LEAD', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
          ('00000000-0000-0000-0000-0000000000f2', '00000000-0000-0000-0000-0000000000b4',
           '00000000-0000-0000-0000-0000000000a2', 'lead', TIMESTAMPTZ '2026-01-02 00:00:00+00')
        """);

    xminBefore = rowVersions();
    flyway().target(MigrationVersion.fromVersion("4")).load().migrate();
    xminAfter = rowVersions();

    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT id::text, project_role FROM \"%s\".project_members"
                    .formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        roleById.put(rs.getString(1), rs.getString(2));
      }
    }
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void olderLeadStaysTheOnlyLeadAndTheNewerUppercaseLeadIsDemoted() {
    assertThat(roleById).containsEntry(OLDER_LEAD, "lead").containsEntry(NEWER_LEAD, "member");
  }

  @Test
  void olderUppercaseLeadStaysTheOnlyLeadAndTheNewerLowercaseLeadIsDemoted() {
    assertThat(roleById)
        .containsEntry(REVERSE_OLDER_UPPER, "lead")
        .containsEntry(REVERSE_NEWER_LOWER, "member");
  }

  @Test
  void contributorAndUppercaseMemberBecomeMember() {
    assertThat(roleById).containsEntry(CONTRIBUTOR, "member").containsEntry(UPPER_MEMBER, "member");
  }

  @Test
  void createdAtTieIsBrokenByTheSmallerId() {
    assertThat(roleById).containsEntry(TIE_LOW_ID, "lead").containsEntry(TIE_HIGH_ID, "member");
  }

  @Test
  void loneUppercaseLeadIsLowerCasedAndCanonicalRowIsUntouched() {
    assertThat(roleById)
        .containsEntry(LONE_UPPER_LEAD, "lead")
        .containsEntry(CANONICAL_MEMBER, "member");
    assertThat(xminAfter.get(CANONICAL_MEMBER))
        .isNotNull()
        .isEqualTo(xminBefore.get(CANONICAL_MEMBER));
  }

  // The scratch schema is private to this class, so an exact count cannot bleed.
  @Test
  void everyRowIsCanonicalAndEachMatterHasAtMostOneLead() {
    assertThat(roleById).hasSize(10);
    assertThat(roleById.values()).allMatch(r -> r.equals("lead") || r.equals("member"));
    assertThat(roleById.values()).allMatch(r -> r.equals(r.toLowerCase(Locale.ROOT)));
    // one lead per matter, four matters
    assertThat(roleById.values().stream().filter("lead"::equals).count()).isEqualTo(4);
  }

  @Test
  void checkConstraintExistsInTheMigratedSchema() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT count(*), max(pg_get_constraintdef(c.oid))
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.conname = 'chk_project_members_role' AND c.contype = 'c'
                  AND n.nspname = '%s'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
      assertThat(rs.getString(2)).contains("'lead'").contains("'member'");
    }
  }

  @Test
  void checkRejectsANonCanonicalRole() {
    assertThatThrownBy(
            () ->
                exec(
                    """
                    INSERT INTO "%s".project_members (project_id, member_id, project_role)
                    VALUES ('00000000-0000-0000-0000-0000000000b3',
                            '00000000-0000-0000-0000-0000000000a3', 'LEAD')
                    """))
        .isInstanceOf(SQLException.class)
        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
  }

  @Test
  void reRunningTheV4BodyIsANoOp() throws Exception {
    String v4 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V4__phase88_demo_ready.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v4);
      } finally {
        s.execute("RESET search_path"); // pooled connection — leave it as found
      }
    }
    assertThat(rowVersions()).isEqualTo(xminAfter);
  }

  private Map<String, String> rowVersions() throws Exception {
    var versions = new HashMap<String, String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT id::text, xmin::text FROM \"%s\".project_members"
                    .formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        versions.put(rs.getString(1), rs.getString(2));
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
