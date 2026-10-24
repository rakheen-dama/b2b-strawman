package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * Tenant {@code V4__phase88_demo_ready} §3 (Phase 88 U15, ADR-347 A3/A5): {@code
 * invoices.amount_paid numeric(14,2) NOT NULL DEFAULT 0} backfilled to {@code total} for existing
 * PAID rows (guarded by {@code total > 0} so a zero-total PAID row is never rewritten), {@code
 * chk_invoice_status} swapped for six values ({@code PARTIALLY_PAID}), {@code
 * chk_invoices_amount_paid} and {@code payment_events.payment_method} with {@code
 * chk_payment_events_method}. A scratch schema is migrated to V3, seeded, then migrated to V4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4InvoiceStatusCheckTest {

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_v4_invoice_status_check_scratch");

  private static final String PAID_1150 = "00000000-0000-0000-0000-0000000003c1";
  private static final String PAID_ZERO = "00000000-0000-0000-0000-0000000003c2";
  private static final String SENT_500 = "00000000-0000-0000-0000-0000000003c3";
  private static final String EXISTING_EVENT = "00000000-0000-0000-0000-0000000003d1";

  private final DataSource migrationDataSource;
  private final Map<String, BigDecimal> amountPaidById = new HashMap<>();
  private Map<String, String> xminBefore;
  private Map<String, String> xminAfter;

  @Autowired
  V4InvoiceStatusCheckTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
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
        SELECT '00000000-0000-0000-0000-0000000003a1'::uuid, 'user_v4_status_a1',
               'v4_status_a1@test.com', r.id
        FROM "%s".org_roles r
        WHERE r.slug = 'member'
        """);
    exec(
        """
        INSERT INTO "%s".customers (id, name, email, created_by)
        VALUES ('00000000-0000-0000-0000-0000000003b1', 'V4 Status Client',
                'v4_status_client@test.com', '00000000-0000-0000-0000-0000000003a1')
        """);
    exec(
        """
        INSERT INTO "%s".invoices
          (id, customer_id, status, currency, customer_name, org_name, created_by,
           invoice_number, issue_date, due_date, subtotal, tax_amount, total)
        VALUES
          ('00000000-0000-0000-0000-0000000003c1', '00000000-0000-0000-0000-0000000003b1',
           'PAID', 'ZAR', 'V4 Status Client', 'V4 Org', '00000000-0000-0000-0000-0000000003a1',
           'V4S-0001', DATE '2026-03-01', DATE '2026-03-31', 1000.00, 150.00, 1150.00),
          ('00000000-0000-0000-0000-0000000003c2', '00000000-0000-0000-0000-0000000003b1',
           'PAID', 'ZAR', 'V4 Status Client', 'V4 Org', '00000000-0000-0000-0000-0000000003a1',
           'V4S-0002', DATE '2026-03-01', DATE '2026-03-31', 0, 0, 0),
          ('00000000-0000-0000-0000-0000000003c3', '00000000-0000-0000-0000-0000000003b1',
           'SENT', 'ZAR', 'V4 Status Client', 'V4 Org', '00000000-0000-0000-0000-0000000003a1',
           'V4S-0003', DATE '2026-03-01', DATE '2026-03-31', 500.00, 0, 500.00)
        """);
    exec(
        """
        INSERT INTO "%s".payment_events
          (id, invoice_id, provider_slug, status, amount, currency)
        VALUES ('00000000-0000-0000-0000-0000000003d1', '00000000-0000-0000-0000-0000000003c1',
                'manual', 'COMPLETED', 1150.00, 'ZAR')
        """);

    xminBefore = rowVersions();
    flyway().target(MigrationVersion.fromVersion("4")).load().migrate();
    xminAfter = rowVersions();

    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT id::text, amount_paid FROM \"%s\".invoices".formatted(SCRATCH_SCHEMA))) {
      while (rs.next()) {
        amountPaidById.put(rs.getString(1), rs.getBigDecimal(2));
      }
    }
  }

  @AfterAll
  void dropScratchSchema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void paidRowIsBackfilledToItsTotal() {
    assertThat(amountPaidById.get(PAID_1150)).isEqualByComparingTo("1150.00");
  }

  @Test
  void sentRowKeepsAmountPaidZero() {
    assertThat(amountPaidById.get(SENT_500)).isEqualByComparingTo("0.00");
    assertThat(xminAfter.get(SENT_500)).isNotNull().isEqualTo(xminBefore.get(SENT_500));
  }

  /** The {@code total > 0} guard: a zero-total PAID row already has amount_paid = total = 0. */
  @Test
  void zeroTotalPaidRowIsNotRewritten() {
    assertThat(amountPaidById.get(PAID_ZERO)).isEqualByComparingTo("0.00");
    assertThat(xminAfter.get(PAID_ZERO)).isNotNull().isEqualTo(xminBefore.get(PAID_ZERO));
  }

  @Test
  void existingPaymentEventHasNullPaymentMethod() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT payment_method FROM \"%s\".payment_events WHERE id = '%s'"
                    .formatted(SCRATCH_SCHEMA, EXISTING_EVENT))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isNull();
    }
  }

  @Test
  void invoiceStatusCheckIsSwappedForSixValues() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT count(*), max(pg_get_constraintdef(c.oid))
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE c.conname = 'chk_invoice_status' AND c.contype = 'c'
                  AND n.nspname = '%s'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
      assertThat(rs.getString(2))
          .contains("DRAFT")
          .contains("APPROVED")
          .contains("'SENT'")
          .contains("PARTIALLY_PAID")
          .contains("'PAID'")
          .contains("VOID");
    }
  }

  @Test
  void partiallyPaidStatusIsAcceptedAndBogusIsRejected() throws Exception {
    exec(
        "UPDATE \"%s\".invoices SET status = 'PARTIALLY_PAID', amount_paid = 100.00 WHERE id = '"
            + SENT_500
            + "'");
    try {
      try (Connection c = migrationDataSource.getConnection();
          Statement s = c.createStatement();
          ResultSet rs =
              s.executeQuery(
                  "SELECT status FROM \"%s\".invoices WHERE id = '%s'"
                      .formatted(SCRATCH_SCHEMA, SENT_500))) {
        rs.next();
        assertThat(rs.getString(1)).isEqualTo("PARTIALLY_PAID");
      }
      assertCheckViolation(
          "UPDATE \"%s\".invoices SET status = 'BOGUS' WHERE id = '" + SENT_500 + "'");
    } finally {
      exec(
          "UPDATE \"%s\".invoices SET status = 'SENT', amount_paid = 0 WHERE id = '"
              + SENT_500
              + "'");
    }
  }

  @Test
  void amountPaidCheckRejectsAboveTotalAndNegative() throws Exception {
    assertCheckViolation(
        "UPDATE \"%s\".invoices SET amount_paid = total + 1 WHERE id = '" + SENT_500 + "'");
    assertCheckViolation(
        "UPDATE \"%s\".invoices SET amount_paid = -1 WHERE id = '" + SENT_500 + "'");
  }

  @Test
  void paymentMethodCheckAcceptsEnumAndNullAndRejectsOthers() throws Exception {
    try {
      assertCheckViolation(
          "UPDATE \"%s\".payment_events SET payment_method = 'BITCOIN' WHERE id = '"
              + EXISTING_EVENT
              + "'");
      exec(
          "UPDATE \"%s\".payment_events SET payment_method = 'EFT' WHERE id = '"
              + EXISTING_EVENT
              + "'");
      for (var method : PaymentMethod.values()) {
        exec(
            "UPDATE \"%s\".payment_events SET payment_method = '"
                + method.name()
                + "' WHERE id = '"
                + EXISTING_EVENT
                + "'");
      }
    } finally {
      exec(
          "UPDATE \"%s\".payment_events SET payment_method = NULL WHERE id = '"
              + EXISTING_EVENT
              + "'");
    }
  }

  @Test
  void amountPaidColumnIsNotNullNumeric14_2Default0() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT data_type, numeric_precision, numeric_scale, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'invoices'
                  AND column_name = 'amount_paid'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("numeric");
      assertThat(rs.getInt(2)).isEqualTo(14);
      assertThat(rs.getInt(3)).isEqualTo(2);
      assertThat(rs.getString(4)).isEqualTo("NO");
      assertThat(rs.getString(5)).isEqualTo("0");
    }
  }

  @Test
  void paymentMethodColumnIsNullableVarchar20() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = '%s' AND table_name = 'payment_events'
                  AND column_name = 'payment_method'
                """
                    .formatted(SCRATCH_SCHEMA))) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("character varying");
      assertThat(rs.getInt(2)).isEqualTo(20);
      assertThat(rs.getString(3)).isEqualTo("YES");
    }
  }

  /** Re-running the whole V4 body rewrites no invoice row and duplicates no constraint. */
  @Test
  void reRunningTheV4BodyIsANoOp() throws Exception {
    String v4 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/tenant/V4__phase88_demo_ready.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    // Other tests update (and restore) SENT_500, so compare against the versions just before.
    var before = rowVersions();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      try {
        s.execute("SET search_path TO \"%s\"".formatted(SCRATCH_SCHEMA));
        s.execute(v4);
      } finally {
        s.execute("RESET search_path"); // pooled connection — leave it as found
      }
    }

    var versions = rowVersions();
    assertThat(before).hasSize(3);
    for (var id : before.keySet()) {
      assertThat(versions.get(id)).as("xmin of %s", id).isEqualTo(before.get(id));
    }
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                SELECT c.conname, count(*)
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = '%s'
                  AND c.conname IN ('chk_invoice_status', 'chk_invoices_amount_paid',
                                    'chk_payment_events_method')
                GROUP BY c.conname
                """
                    .formatted(SCRATCH_SCHEMA))) {
      var counts = new HashMap<String, Integer>();
      while (rs.next()) {
        counts.put(rs.getString(1), rs.getInt(2));
      }
      assertThat(counts)
          .containsEntry("chk_invoice_status", 1)
          .containsEntry("chk_invoices_amount_paid", 1)
          .containsEntry("chk_payment_events_method", 1);
    }
  }

  private void assertCheckViolation(String sqlWithSchemaPlaceholders) {
    assertThatThrownBy(() -> exec(sqlWithSchemaPlaceholders))
        .isInstanceOf(SQLException.class)
        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
  }

  private Map<String, String> rowVersions() throws Exception {
    var versions = new HashMap<String, String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT id::text, xmin::text FROM \"%s\".invoices".formatted(SCRATCH_SCHEMA))) {
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
