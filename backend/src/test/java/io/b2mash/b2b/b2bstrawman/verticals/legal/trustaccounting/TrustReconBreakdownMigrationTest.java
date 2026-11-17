package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 92 slice 646C (task 646.18, architecture §16.2.11 / §16.7.4, ADR-377): tenant {@code V19}
 * adds the creditor breakdown to {@code trust_reconciliations}. A scratch tenant schema is migrated
 * to V18 and seeded with reconciliations of the old shape — calculated (client ledgers as the whole
 * creditor side), never calculated (all zero), and the ruling-O6 edge (client ledgers 0, adjusted
 * bank non-zero) — then migrated to V19, pinned so a later migration cannot change these
 * expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconBreakdownMigrationTest {

  /** The breakdown migration (pinned, not "latest"). */
  private static final String BREAKDOWN_VERSION = "19";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V19__phase92_trust_recon_breakdown.sql";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_recon_breakdown_scratch");

  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%statement", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%reconCalculated", "00000000-0000-0000-0000-0000000000e1");
    IDS.put("%reconZero", "00000000-0000-0000-0000-0000000000e2");
    IDS.put("%reconNoClients", "00000000-0000-0000-0000-0000000000e3");
    IDS.put("%reconUnbalanced", "00000000-0000-0000-0000-0000000000e4");
  }

  private final DataSource migrationDataSource;

  @Autowired
  TrustReconBreakdownMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV18() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("18")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_trb', 'trb@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%account', 'Trust 1', 'FNB', '250655', '62000000019', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".bank_statements (id, trust_account_id, period_start, period_end,
            opening_balance, closing_balance, file_key, file_name, format, line_count,
            matched_count, status, imported_by)
        VALUES ('%statement', '%account', DATE '2025-03-01', DATE '2025-03-31', 0, 1000,
                'k/statement.csv', 'statement.csv', 'CSV', 1, 1, 'IMPORTED', '%owner');
        INSERT INTO "%s".trust_reconciliations (id, trust_account_id, period_end,
            bank_statement_id, bank_balance, cashbook_balance, client_ledger_total,
            outstanding_deposits, outstanding_payments, adjusted_bank_balance, is_balanced)
        VALUES ('%reconCalculated', '%account', DATE '2025-03-31', '%statement',
                1000, 1000, 1000, 0, 0, 1000, true),
               ('%reconZero', '%account', DATE '2025-03-31', '%statement',
                0, 0, 0, 0, 0, 0, false),
               ('%reconNoClients', '%account', DATE '2025-03-31', '%statement',
                250, 0, 0, 0, 0, 250, false),
               ('%reconUnbalanced', '%account', DATE '2025-03-31', '%statement',
                1200, 1000, 1000, 0, 0, 1200, false);
        """);
    tenantFlyway().target(MigrationVersion.fromVersion(BREAKDOWN_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void theSevenColumnsExistWithTheirTypesAndDefaults() throws Exception {
    var columns =
        texts(
            "SELECT column_name, data_type, COALESCE(numeric_precision::text, '-'),"
                + " COALESCE(numeric_scale::text, '-'), is_nullable, column_default"
                + " FROM information_schema.columns WHERE table_schema = '%s'"
                + " AND table_name = 'trust_reconciliations' AND column_name IN"
                + " ('investment_creditor_total', 'fund_ledger_balance',"
                + " 'unidentified_deposit_total', 'inter_account_balance', 'creditor_total',"
                + " 'difference', 'integrity_flags') ORDER BY column_name");
    assertThat(columns)
        .containsExactly(
            "creditor_total|numeric|15|2|NO|0",
            "difference|numeric|15|2|NO|0",
            "fund_ledger_balance|numeric|15|2|NO|0",
            "integrity_flags|jsonb|-|-|NO|'[]'::jsonb",
            "inter_account_balance|numeric|15|2|NO|0",
            "investment_creditor_total|numeric|15|2|NO|0",
            "unidentified_deposit_total|numeric|15|2|NO|0");
  }

  @Test
  void aCalculatedRowTakesItsClientLedgersAsTheCreditorTotal() throws Exception {
    assertBreakdown("%reconCalculated", "1000", "0");
  }

  @Test
  void aNeverCalculatedRowStaysAtZero() throws Exception {
    assertBreakdown("%reconZero", "0", "0");
  }

  /** Ruling O6: §16.7.4's single UPDATE would have left difference 0 here. */
  @Test
  void aRowWithNoClientLedgersButABankBalanceGetsItsDifference() throws Exception {
    assertBreakdown("%reconNoClients", "0", "250");
  }

  @Test
  void anUnbalancedRowKeepsItsDifference() throws Exception {
    assertBreakdown("%reconUnbalanced", "1000", "200");
  }

  @Test
  void noRowIsDeletedAndTheNewTermsAreZero() throws Exception {
    assertThat(
            text(
                "SELECT count(*) FROM \"%s\".trust_reconciliations WHERE"
                    + " investment_creditor_total = 0 AND fund_ledger_balance = 0"
                    + " AND unidentified_deposit_total = 0 AND inter_account_balance = 0"))
        .isEqualTo("4");
  }

  @Test
  void runningTheMigrationBodyTwice_isANoOp() throws Exception {
    var before = fingerprint();
    var body =
        new String(
            new ClassPathResource(MIGRATION_FILE).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    try (Connection c = migrationDataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        // SET LOCAL: the search_path reverts at commit, so the pooled connection is unaffected.
        s.execute("SET LOCAL search_path TO \"" + SCRATCH_SCHEMA + "\"");
        s.execute(body);
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      } finally {
        c.setAutoCommit(true);
      }
    }
    assertThat(fingerprint()).isEqualTo(before);
  }

  // --- helpers ---

  private void assertBreakdown(String recon, String creditorTotal, String difference)
      throws Exception {
    var row =
        text(
            "SELECT creditor_total::text, difference::text, integrity_flags::text"
                + " FROM \"%s\".trust_reconciliations WHERE id = '"
                + recon
                + "'");
    var parts = row.split("\\|");
    assertThat(new BigDecimal(parts[0]))
        .as("%s creditor_total", recon)
        .isEqualByComparingTo(creditorTotal);
    assertThat(new BigDecimal(parts[1]))
        .as("%s difference", recon)
        .isEqualByComparingTo(difference);
    assertThat(parts[2]).as("%s integrity_flags", recon).isEqualTo("[]");
  }

  /** Every reconciliation's stored figures — what a second run must leave untouched. */
  private List<String> fingerprint() throws Exception {
    return texts(
        "SELECT id || ':' || creditor_total || ':' || difference || ':' || client_ledger_total"
            + " || ':' || adjusted_bank_balance || ':' || integrity_flags::text || ':'"
            + " || updated_at FROM \"%s\".trust_reconciliations ORDER BY id");
  }

  private String text(String sql) throws Exception {
    var rows = texts(sql);
    assertThat(rows).as("one row from %s", sql).hasSize(1);
    return rows.getFirst();
  }

  /** Every row of {@code sql}, its columns joined with {@code |} (NULL as "NULL"). */
  private List<String> texts(String sql) throws Exception {
    var rows = new ArrayList<String>();
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(resolve(sql))) {
      int columns = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        var values = new ArrayList<String>();
        for (int i = 1; i <= columns; i++) {
          var value = rs.getString(i);
          values.add(value == null ? "NULL" : value);
        }
        rows.add(String.join("|", values));
      }
    }
    return rows;
  }

  private void exec(String sql) throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(resolve(sql));
    }
  }

  /** Replaces the id placeholders, then {@code %s} with the scratch schema. */
  private static String resolve(String sql) {
    var resolved = sql;
    for (var entry : IDS.entrySet()) {
      resolved = resolved.replace(entry.getKey(), entry.getValue());
    }
    return resolved.replace("%s", SCRATCH_SCHEMA);
  }

  private FluentConfiguration tenantFlyway() {
    // Same locations/config as TenantProvisioningService.runTenantMigrations.
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(SCRATCH_SCHEMA)
        .baselineOnMigrate(false);
  }
}
