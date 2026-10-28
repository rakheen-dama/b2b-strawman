package io.b2mash.b2b.b2bstrawman.comment;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * Tenant {@code V9__phase88_comment_attachments} (Phase 88 §7 U24, A13): the {@code
 * comment_attachments} child table keyed by (comment_id, document_id), both FKs {@code ON DELETE
 * CASCADE}, plus the reverse-lookup index on document_id. A scratch schema is migrated to V8,
 * seeded with a member, a matter, two comments and three documents, then migrated to V9.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V9CommentAttachmentsTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v9_comment_attachments_scratch");

  private static final String MEMBER = "00000000-0000-0000-0000-0000000009a1";
  private static final String PROJECT = "00000000-0000-0000-0000-0000000009b1";
  private static final String COMMENT_1 = "00000000-0000-0000-0000-0000000009c1";
  private static final String COMMENT_2 = "00000000-0000-0000-0000-0000000009c2";
  private static final String DOC_1 = "00000000-0000-0000-0000-0000000009d1";
  private static final String DOC_2 = "00000000-0000-0000-0000-0000000009d2";
  private static final String DOC_3 = "00000000-0000-0000-0000-0000000009d3";

  /** (column, data_type, is_nullable, column_default) ordered by ordinal position. */
  private static final List<String> EXPECTED_COLUMNS =
      List.of(
          "comment_id|uuid|NO|null",
          "document_id|uuid|NO|null",
          "sort_order|integer|NO|0",
          "created_at|timestamp with time zone|NO|now()");

  private final DataSource migrationDataSource;

  @Autowired
  V9CommentAttachmentsTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV8() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("8")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '#MEMBER#'::uuid, 'user_v9_comment_attachments', 'v9_ca@test.com', r.id
        FROM "%s".org_roles r
        WHERE r.slug = 'member'
        """
            .replace("#MEMBER#", MEMBER));
    exec(
        "INSERT INTO \"%s\".projects (id, name, created_by, status) VALUES ('"
            + PROJECT
            + "', 'V9 matter', '"
            + MEMBER
            + "', 'ACTIVE')");
    for (String comment : List.of(COMMENT_1, COMMENT_2)) {
      exec(
          "INSERT INTO \"%s\".comments"
              + " (id, entity_type, entity_id, project_id, author_member_id, body, visibility)"
              + " VALUES ('%c', 'PROJECT', '%p', '%p', '%m', 'V9 comment', 'SHARED')"
                  .replace("%c", comment)
                  .replace("%p", PROJECT)
                  .replace("%m", MEMBER));
    }
    for (String doc : List.of(DOC_1, DOC_2, DOC_3)) {
      exec(
          "INSERT INTO \"%s\".documents"
              + " (id, project_id, file_name, size, s3_key, status, uploaded_by)"
              + " VALUES ('%d', '%p', 'v9.pdf', 10, 'k/%d', 'UPLOADED', '%m')"
                  .replace("%d", doc)
                  .replace("%p", PROJECT)
                  .replace("%m", MEMBER));
    }
    flyway().target(MigrationVersion.fromVersion("9")).load().migrate();
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void tableHasTheFourColumns() throws Exception {
    List<String> actual = new ArrayList<>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT column_name, data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'comment_attachments'
                ORDER BY ordinal_position
                """
                    .formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        actual.add(
            rs.getString(1)
                + "|"
                + rs.getString(2)
                + "|"
                + rs.getString(3)
                + "|"
                + rs.getString(4));
      }
    }
    assertThat(actual).containsExactlyElementsOf(EXPECTED_COLUMNS);
  }

  @Test
  void primaryKeyIsCommentIdAndDocumentId() throws Exception {
    assertThat(
            queryOne(
                """
                SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = '%s' AND c.conname = 'comment_attachments_pkey' AND c.contype = 'p'
                """))
        .isEqualTo("PRIMARY KEY (comment_id, document_id)");
  }

  @Test
  void bothForeignKeysCascadeOnDelete() throws Exception {
    Map<String, String> fks = new TreeMap<>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT c.conname, c.confdeltype, pg_get_constraintdef(c.oid) FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = '%1$s' AND c.contype = 'f'
                  AND c.conrelid = '"%1$s".comment_attachments'::regclass
                """
                    .formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        assertThat(rs.getString(2)).as(rs.getString(1)).isEqualTo("c");
        fks.put(rs.getString(1), rs.getString(3));
      }
    }
    assertThat(fks)
        .containsOnlyKeys(
            "comment_attachments_comment_id_fkey", "comment_attachments_document_id_fkey");
    assertThat(fks.get("comment_attachments_comment_id_fkey"))
        .contains("comments(id)")
        .contains("ON DELETE CASCADE");
    assertThat(fks.get("comment_attachments_document_id_fkey"))
        .contains("documents(id)")
        .contains("ON DELETE CASCADE");
  }

  @Test
  void reverseLookupIndexOnDocumentIdIsUnique() throws Exception {
    // UNIQUE: the database, not only the service's check-then-insert, enforces that a document
    // is attached to at most one comment, so two concurrent attaches cannot both succeed.
    assertThat(
            queryOne(
                """
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = '%s' AND indexname = 'idx_comment_attachments_document'
                """))
        .startsWith("CREATE UNIQUE INDEX")
        .contains("comment_attachments")
        .endsWith("(document_id)");
  }

  @Test
  void deletingACommentRemovesItsLinksAndKeepsTheDocuments() throws Exception {
    exec(
        "INSERT INTO \"%s\".comment_attachments (comment_id, document_id, sort_order) VALUES"
            + " ('"
            + COMMENT_1
            + "', '"
            + DOC_1
            + "', 0), ('"
            + COMMENT_1
            + "', '"
            + DOC_2
            + "', 1)");
    assertThat(count("comment_attachments WHERE comment_id = '" + COMMENT_1 + "'")).isEqualTo(2);

    exec("DELETE FROM \"%s\".comments WHERE id = '" + COMMENT_1 + "'");

    assertThat(count("comment_attachments WHERE comment_id = '" + COMMENT_1 + "'")).isZero();
    assertThat(count("documents WHERE id IN ('" + DOC_1 + "', '" + DOC_2 + "')")).isEqualTo(2);
  }

  @Test
  void deletingADocumentRemovesItsLink() throws Exception {
    exec(
        "INSERT INTO \"%s\".comment_attachments (comment_id, document_id) VALUES ('"
            + COMMENT_2
            + "', '"
            + DOC_3
            + "')");
    assertThat(count("comment_attachments WHERE document_id = '" + DOC_3 + "'")).isEqualTo(1);
    assertThat(
            queryOne(
                "SELECT sort_order::text FROM \"%s\".comment_attachments WHERE document_id = '"
                    + DOC_3
                    + "'"))
        .isEqualTo("0");

    exec("DELETE FROM \"%s\".documents WHERE id = '" + DOC_3 + "'");

    assertThat(count("comment_attachments WHERE document_id = '" + DOC_3 + "'")).isZero();
    assertThat(count("comments WHERE id = '" + COMMENT_2 + "'")).isEqualTo(1);
  }

  @Test
  void scratchHistoryEndsAtV9() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT max(version::int), count(*) FILTER (WHERE NOT success)"
                    + " FROM \"%s\".flyway_schema_history WHERE version IS NOT NULL"
                        .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(9);
      assertThat(rs.getInt(2)).isZero();
    }
  }

  /** Re-running the whole V9 body adds no table, constraint or index. */
  @Test
  void reRunningTheV9BodyIsANoOp() throws Exception {
    String v9 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V9__phase88_comment_attachments.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    String before = catalogFingerprint();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v9);
      } finally {
        s.execute("RESET search_path"); // pooled connection — leave it as found
      }
    }
    assertThat(catalogFingerprint()).isEqualTo(before);
  }

  private String catalogFingerprint() throws Exception {
    return queryOne(
        """
        SELECT (SELECT count(*) FROM information_schema.tables
                WHERE table_schema = '%1$s' AND table_name = 'comment_attachments')
            || '/' || (SELECT count(*) FROM pg_constraint c
                       JOIN pg_namespace n ON n.oid = c.connamespace
                       WHERE n.nspname = '%1$s' AND c.conname LIKE 'comment\\_attachments\\_%%')
            || '/' || (SELECT count(*) FROM pg_indexes
                       WHERE schemaname = '%1$s' AND tablename = 'comment_attachments')
        """);
  }

  private int count(String fromWhere) throws Exception {
    return Integer.parseInt(queryOne("SELECT count(*)::text FROM \"%s\"." + fromWhere));
  }

  /** Runs a single-value query; {@code %s} / {@code %1$s} are replaced with the scratch schema. */
  private String queryOne(String sql) throws Exception {
    String resolved =
        sql.contains("%1$s") ? sql.formatted(SCRATCH_SCHEMA) : sql.replace("%s", SCRATCH_SCHEMA);
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(resolved)) {
      assertThat(rs.next()).as(resolved).isTrue();
      return rs.getString(1);
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
