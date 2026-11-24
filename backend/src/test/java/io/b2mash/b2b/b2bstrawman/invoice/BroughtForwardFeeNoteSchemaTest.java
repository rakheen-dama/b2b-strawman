package io.b2mash.b2b.b2bstrawman.invoice;

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
 * Phase 93 / 658A (task 658.2, architecture §17.2.11, §17.7.7): tenant {@code
 * V37__phase93_brought_forward_fee_notes} (guide V35; task file V36; V36 taken by 657C — next free,
 * P-1). A scratch schema is walked to V36 with an owner member, a client, four fee notes (DRAFT,
 * APPROVED, SENT, PAID) and a debtors take-on batch, then migrated: {@code invoices} gains a
 * nullable {@code take_on_batch_id} that is a foreign key to the batch (never cascading); {@code
 * chk_invoice_brought_forward_status} holds for every existing fee note and rejects a batch-linked
 * fee note that is not SENT or later; re-running the body changes nothing. Raw SQL is the point
 * here — the migration is under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BroughtForwardFeeNoteSchemaTest {

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V37__phase93_brought_forward_fee_notes.sql";
  private static final String PREVIOUS_VERSION = "36";
  private static final String CHECK = "chk_invoice_brought_forward_status";
  private static final String FK = "invoices_take_on_batch_fkey";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String S =
      SchemaNameGenerator.generateSchemaName("org_bf_fee_note_schema_scratch");

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  @BeforeAll
  void walkToV36() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
    exec("CREATE SCHEMA \"" + S + "\"");
    tenantFlyway().target(MigrationVersion.fromVersion(PREVIOUS_VERSION)).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_bfs', 'bfs@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, created_by)
        VALUES ('%c', 'Schema Client', 'bfs.client@test.com', '%o');
        INSERT INTO "%s".invoices (customer_id, status, currency, customer_name, org_name, created_by)
        VALUES ('%c', 'DRAFT', 'ZAR', 'Schema Client', 'Schema Firm', '%o'),
               ('%c', 'APPROVED', 'ZAR', 'Schema Client', 'Schema Firm', '%o'),
               ('%c', 'SENT', 'ZAR', 'Schema Client', 'Schema Firm', '%o'),
               ('%c', 'PAID', 'ZAR', 'Schema Client', 'Schema Firm', '%o');
        INSERT INTO "%s".take_on_batches (id, dataset, status, cutover_date, created_by,
                                          created_at, updated_at)
        VALUES ('%b', 'DEBTOR_BALANCES', 'VALIDATED', DATE '2026-10-31', '%o', now(), now());
        """
            .replace("%s", S)
            .replace("%o", OWNER.toString())
            .replace("%c", CLIENT.toString())
            .replace("%b", BATCH.toString()));
    tenantFlyway().load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
  }

  // 1
  @Test
  void migrationAppliesOnAWalkedSchema_withANullableUuidColumn() {
    // Applied (not "the latest": a later migration must not break this pin).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".flyway_schema_history WHERE success AND version = ?"
                    .formatted(S),
                Integer.class,
                "37"))
        .isEqualTo(1);
    List<Map<String, Object>> columns =
        jdbc.queryForList(
            """
            SELECT data_type, is_nullable FROM information_schema.columns
            WHERE table_schema = ? AND table_name = 'invoices' AND column_name = 'take_on_batch_id'
            """,
            S);
    assertThat(columns)
        .singleElement()
        .isEqualTo(Map.of("data_type", "uuid", "is_nullable", "YES"));
  }

  // 2
  @Test
  void theBatchReference_isAForeignKeyToTakeOnBatchesThatNeverCascades() {
    var fk =
        jdbc.queryForMap(
            """
            SELECT c.confdeltype::text AS on_delete, c.confrelid::regclass::text AS target
            FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
            WHERE c.conname = ? AND n.nspname = ?
            """,
            FK,
            S);
    assertThat(fk.get("on_delete")).isEqualTo("a");
    assertThat((String) fk.get("target")).endsWith("take_on_batches");
  }

  // 3
  @Test
  void theStatusCheck_holdsForEveryExistingFeeNote_whichKeptNoBatch() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".invoices WHERE take_on_batch_id IS NULL".formatted(S),
                Integer.class))
        .isGreaterThanOrEqualTo(4);
    assertThat(constraintDef(CHECK))
        .contains("take_on_batch_id IS NULL")
        .contains("'SENT'")
        .contains("'PARTIALLY_PAID'")
        .contains("'PAID'")
        .contains("'VOID'");
  }

  // 4
  @Test
  void aDraftOrApprovedFeeNoteWithABatch_isRejected() {
    for (String status : List.of("DRAFT", "APPROVED")) {
      assertThatThrownBy(() -> insert(status, BATCH))
          .as("%s with a batch", status)
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining(CHECK);
    }
  }

  // 5
  @Test
  void aSentOrLaterFeeNoteWithABatch_isAccepted() {
    for (String status : List.of("SENT", "PARTIALLY_PAID", "PAID", "VOID")) {
      assertThat(insert(status, BATCH)).as("%s with a batch", status).isEqualTo(1);
    }
    jdbc.update("DELETE FROM \"%s\".invoices WHERE take_on_batch_id = ?".formatted(S), BATCH);
  }

  // 6
  @Test
  void anUnknownBatch_isRejectedByTheForeignKey() {
    assertThatThrownBy(() -> insert("SENT", UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(FK);
  }

  // 7
  @Test
  void rerunningTheMigrationBody_changesNothing() throws Exception {
    String checkBefore = constraintDef(CHECK);
    rerunMigrationBody();
    assertThat(constraintDef(CHECK)).isEqualTo(checkBefore);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = ? AND c.conname IN (?, ?)
                """,
                Integer.class,
                S,
                CHECK,
                FK))
        .isEqualTo(2);
  }

  // --- helpers ---

  private int insert(String status, UUID batchId) {
    return jdbc.update(
        ("INSERT INTO \"%s\".invoices (customer_id, status, currency, customer_name, org_name,"
                + " created_by, take_on_batch_id) VALUES (?, ?, 'ZAR', 'Schema Client',"
                + " 'Schema Firm', ?, ?)")
            .formatted(S),
        CLIENT,
        status,
        OWNER,
        batchId);
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
