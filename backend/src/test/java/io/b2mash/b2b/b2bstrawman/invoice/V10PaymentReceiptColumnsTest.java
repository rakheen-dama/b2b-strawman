package io.b2mash.b2b.b2bstrawman.invoice;

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
 * Tenant {@code V10__phase89_go_live} §1 (Phase 89 632B, ADR-364, G88-F32): {@code
 * payment_events.receipt_generated_document_id} (nullable FK → {@code generated_documents} ON
 * DELETE SET NULL, partial index), {@code payment_events.receipt_number} and {@code
 * invoices.receipt_sequence} (NOT NULL DEFAULT 0). A scratch schema is migrated to V9, seeded with
 * a fee note + payment event + generated document, then migrated to V10.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V10PaymentReceiptColumnsTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v10_payment_receipt_columns_scratch");

  private static final String ROLE_ID = "00000000-0000-0000-0000-00000000a010";
  private static final String MEMBER_ID = "00000000-0000-0000-0000-00000000a011";
  private static final String CUSTOMER_ID = "00000000-0000-0000-0000-00000000a012";
  private static final String INVOICE_ID = "00000000-0000-0000-0000-00000000a013";
  private static final String PAYMENT_EVENT_ID = "00000000-0000-0000-0000-00000000a014";
  private static final String TEMPLATE_ID = "00000000-0000-0000-0000-00000000a015";
  private static final String DOCUMENT_ID = "00000000-0000-0000-0000-00000000a016";

  private final DataSource migrationDataSource;
  private String invoiceXminBefore;
  private String invoiceXminAfter;

  @Autowired
  V10PaymentReceiptColumnsTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV9() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    flyway().target(MigrationVersion.fromVersion("9")).load().migrate();
    exec(
        """
        INSERT INTO "%s".org_roles (id, name, slug) VALUES
          ('00000000-0000-0000-0000-00000000a010', 'Owner', 'v10-owner');
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id) VALUES
          ('00000000-0000-0000-0000-00000000a011', 'user_v10', 'v10@test.com',
           '00000000-0000-0000-0000-00000000a010');
        INSERT INTO "%s".customers (id, name, email, created_by) VALUES
          ('00000000-0000-0000-0000-00000000a012', 'V10 Client', 'v10c@test.com',
           '00000000-0000-0000-0000-00000000a011');
        INSERT INTO "%s".invoices
          (id, customer_id, invoice_number, status, currency, total, customer_name, org_name,
           created_by)
        VALUES
          ('00000000-0000-0000-0000-00000000a013', '00000000-0000-0000-0000-00000000a012',
           'INV-0001', 'SENT', 'ZAR', 1000.00, 'V10 Client', 'V10 Firm',
           '00000000-0000-0000-0000-00000000a011');
        INSERT INTO "%s".payment_events
          (id, invoice_id, provider_slug, status, amount, currency)
        VALUES
          ('00000000-0000-0000-0000-00000000a014', '00000000-0000-0000-0000-00000000a013',
           'manual', 'COMPLETED', 400.00, 'ZAR');
        INSERT INTO "%s".document_templates
          (id, name, slug, category, primary_entity_type)
        VALUES
          ('00000000-0000-0000-0000-00000000a015', 'V10 Receipt', 'v10-receipt', 'OTHER',
           'INVOICE');
        INSERT INTO "%s".generated_documents
          (id, template_id, primary_entity_type, primary_entity_id, file_name, s3_key, file_size,
           generated_by)
        VALUES
          ('00000000-0000-0000-0000-00000000a016', '00000000-0000-0000-0000-00000000a015',
           'INVOICE', '00000000-0000-0000-0000-00000000a013', 'receipt.pdf', 'k/receipt.pdf', 10,
           '00000000-0000-0000-0000-00000000a011');
        """);

    invoiceXminBefore = invoiceRowVersion();
    flyway().target(MigrationVersion.fromVersion("10")).load().migrate();
    invoiceXminAfter = invoiceRowVersion();
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void paymentEventReceiptColumnsAreNullable() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'payment_events'
                  AND column_name IN ('receipt_generated_document_id', 'receipt_number')
                ORDER BY column_name
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("receipt_generated_document_id");
      assertThat(rs.getString(2)).isEqualTo("uuid");
      assertThat(rs.getString(3)).isEqualTo("YES");
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("receipt_number");
      assertThat(rs.getString(2)).isEqualTo("character varying");
      assertThat(rs.getString(3)).isEqualTo("YES");
      assertThat(rs.getInt(4)).isEqualTo(40);
      assertThat(rs.next()).isFalse();
    }
  }

  @Test
  void invoiceReceiptSequenceIsNotNullDefaultZeroAndExistingRowReadsZero() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'invoices'
                  AND column_name = 'receipt_sequence'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("integer");
      assertThat(rs.getString(2)).isEqualTo("NO");
      assertThat(rs.getString(3)).isEqualTo("0");
    }
    assertThat(queryString("SELECT receipt_sequence::text FROM \"%s\".invoices WHERE id = '%s'"))
        .isEqualTo("0");
    // ADD COLUMN ... DEFAULT 0 is a catalog-only change — the existing row is not rewritten.
    assertThat(invoiceXminAfter).isNotNull().isEqualTo(invoiceXminBefore);
  }

  @Test
  void receiptForeignKeyIsOnDeleteSetNull() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT count(*), max(pg_get_constraintdef(c.oid))
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.conname = 'payment_events_receipt_generated_document_id_fkey'
                  AND c.contype = 'f' AND n.nspname = '%s'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
      assertThat(rs.getString(2))
          .contains("FOREIGN KEY (receipt_generated_document_id)")
          .contains("REFERENCES")
          .contains("generated_documents(id)")
          .contains("ON DELETE SET NULL");
    }
  }

  @Test
  void partialIndexCoversOnlyRowsWithAReceipt() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = '%s' AND indexname = 'idx_payment_events_receipt_doc'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1))
          .contains("(receipt_generated_document_id)")
          .contains("WHERE (receipt_generated_document_id IS NOT NULL)");
      assertThat(rs.next()).isFalse();
    }
  }

  /** Deleting the receipt document nulls the pointer; the payment event survives (D8). */
  @Test
  void deletingTheReceiptDocumentNullsThePointerAndKeepsThePaymentEvent() throws Exception {
    exec(
        "UPDATE \"%s\".payment_events SET receipt_generated_document_id = '"
            + DOCUMENT_ID
            + "', receipt_number = 'INV-0001/R1' WHERE id = '"
            + PAYMENT_EVENT_ID
            + "'");
    exec("DELETE FROM \"%s\".generated_documents WHERE id = '" + DOCUMENT_ID + "'");

    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT receipt_generated_document_id, receipt_number FROM \"%s\".payment_events WHERE id = '%s'"
                    .formatted(SCRATCH_SCHEMA, PAYMENT_EVENT_ID))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getObject(1)).isNull();
      // The stored number is history, not a pointer — it is never cleared by the FK (R6.2).
      assertThat(rs.getString(2)).isEqualTo("INV-0001/R1");
    }
  }

  /** Re-running the whole V10 body duplicates no constraint or index and rewrites no row. */
  @Test
  void reRunningTheV10BodyIsANoOp() throws Exception {
    String v10 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V10__phase89_go_live.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    String before = invoiceRowVersion();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v10);
      } finally {
        s.execute("RESET search_path"); // pooled connection — leave it as found
      }
    }

    assertThat(invoiceRowVersion()).isEqualTo(before);
    assertThat(
            queryString(
                """
                SELECT count(*)::text
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = '%s'
                  AND c.conname = 'payment_events_receipt_generated_document_id_fkey'
                """))
        .isEqualTo("1");
    assertThat(
            queryString(
                "SELECT count(*)::text FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_payment_events_receipt_doc'"))
        .isEqualTo("1");
    // A second Flyway migrate to the same target applies nothing.
    assertThat(
            flyway().target(MigrationVersion.fromVersion("10")).load().migrate().migrationsExecuted)
        .isZero();
  }

  private String invoiceRowVersion() throws Exception {
    return queryString("SELECT xmin::text FROM \"%s\".invoices WHERE id = '%s'");
  }

  /** {@code %s} placeholders: first = scratch schema, second (optional) = the seeded invoice id. */
  private String queryString(String sql) throws Exception {
    String resolved = sql.replaceFirst("%s", SCRATCH_SCHEMA).replace("%s", INVOICE_ID);
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
