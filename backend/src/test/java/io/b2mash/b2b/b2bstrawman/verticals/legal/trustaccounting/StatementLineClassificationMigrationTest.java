package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
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
 * Phase 92 slice 647A (task 647.1, architecture §16.7.5, ADR-380, ADR-381): tenant {@code V20}
 * gives a statement line a classification (non-null exactly when {@code RECORDED}, which the
 * swapped match-status CHECK now admits) and a parser-suggestion column, and gives a {@code
 * BANK_CHARGE} row its recoverable flag and VAT. A scratch tenant schema is migrated to V19, seeded
 * with lines in every old status and posted non-client rows, then migrated to V20 — pinned so a
 * later migration cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementLineClassificationMigrationTest {

  /** The classification migration (pinned, not "latest"). */
  private static final String CLASSIFICATION_VERSION = "20";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V20__phase92_statement_line_classification.sql";

  private static final String CHECK_VIOLATION = "23514";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_stmt_line_classification_scratch");

  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%fund", "00000000-0000-0000-0000-0000000000d1");
    IDS.put("%bankStatement", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%lineUnmatched", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%lineAuto", "00000000-0000-0000-0000-0000000000a2");
    IDS.put("%lineManual", "00000000-0000-0000-0000-0000000000a3");
    IDS.put("%lineExcluded", "00000000-0000-0000-0000-0000000000a4");
    IDS.put("%txInterest", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%txSweep", "00000000-0000-0000-0000-0000000000b2");
  }

  private final DataSource migrationDataSource;

  @Autowired
  StatementLineClassificationMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV19() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("19")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_slc', 'slc@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%account', 'Trust 1', 'FNB', '250655', '62000000020', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".trust_creditor_accounts (id, trust_account_id, account_type, name)
        VALUES ('%fund', '%account', 'FUND_INTEREST_AND_CHARGES', 'Fidelity Fund');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            creditor_account_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at)
        VALUES ('%txInterest', '%account', 'FUND_INTEREST', 100.00, '%fund', 'INT-1',
                DATE '2025-03-31', 'RECORDED', '%owner', 'NON_CLIENT', 100.00, 100.00, now()),
               ('%txSweep', '%account', 'FUND_SWEEP', 40.00, '%fund', 'SWP-1',
                DATE '2025-03-31', 'RECORDED', '%owner', 'NON_CLIENT', -40.00, -40.00, now());
        INSERT INTO "%s".bank_statements (id, trust_account_id, period_start, period_end,
            opening_balance, closing_balance, file_key, file_name, format, line_count,
            matched_count, status, imported_by)
        VALUES ('%bankStatement', '%account', DATE '2025-03-01', DATE '2025-03-31', 0, 60,
                'k/statement.csv', 'statement.csv', 'CSV', 4, 2, 'IMPORTED', '%owner');
        INSERT INTO "%s".bank_statement_lines (id, bank_statement_id, line_number,
            transaction_date, description, amount, match_status, trust_transaction_id,
            match_confidence, excluded_reason)
        VALUES ('%lineUnmatched', '%bankStatement', 1, DATE '2025-03-10', 'Unknown', 5.00,
                'UNMATCHED', NULL, NULL, NULL),
               ('%lineAuto', '%bankStatement', 2, DATE '2025-03-31', 'Interest', 100.00,
                'AUTO_MATCHED', '%txInterest', 0.95, NULL),
               ('%lineManual', '%bankStatement', 3, DATE '2025-03-31', 'LPFF', -40.00,
                'MANUALLY_MATCHED', '%txSweep', NULL, NULL),
               ('%lineExcluded', '%bankStatement', 4, DATE '2025-03-15', 'Bank error', 9.00,
                'EXCLUDED', NULL, NULL, 'Bank error, reversed next day');
        """);
    tenantFlyway().target(MigrationVersion.fromVersion(CLASSIFICATION_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void theFourColumnsExistWithTheirTypes() throws Exception {
    var columns =
        texts(
            "SELECT table_name, column_name, data_type,"
                + " COALESCE(character_maximum_length::text, '-'),"
                + " COALESCE(numeric_precision::text, '-'), COALESCE(numeric_scale::text, '-'),"
                + " is_nullable FROM information_schema.columns WHERE table_schema = '%s'"
                + " AND column_name IN ('classification', 'suggested_classification',"
                + " 'charge_recoverable', 'charge_vat_amount') ORDER BY table_name, column_name");
    assertThat(columns)
        .containsExactly(
            "bank_statement_lines|classification|character varying|30|-|-|YES",
            "bank_statement_lines|suggested_classification|character varying|30|-|-|YES",
            "trust_transactions|charge_recoverable|boolean|-|-|-|YES",
            "trust_transactions|charge_vat_amount|numeric|-|15|2|YES");
  }

  @Test
  void theSeededRowsAreUntouched() throws Exception {
    assertThat(
            texts(
                "SELECT line_number, match_status, COALESCE(classification, 'NULL'),"
                    + " COALESCE(suggested_classification, 'NULL')"
                    + " FROM \"%s\".bank_statement_lines WHERE id IN ('%lineUnmatched',"
                    + " '%lineAuto', '%lineManual', '%lineExcluded') ORDER BY line_number"))
        .containsExactly(
            "1|UNMATCHED|NULL|NULL",
            "2|AUTO_MATCHED|NULL|NULL",
            "3|MANUALLY_MATCHED|NULL|NULL",
            "4|EXCLUDED|NULL|NULL");
    assertThat(
            texts(
                "SELECT transaction_type, amount::text, COALESCE(charge_recoverable::text, 'NULL'),"
                    + " COALESCE(charge_vat_amount::text, 'NULL') FROM \"%s\".trust_transactions"
                    + " WHERE id IN ('%txInterest', '%txSweep') ORDER BY transaction_type"))
        .containsExactly("FUND_INTEREST|100.00|NULL|NULL", "FUND_SWEEP|40.00|NULL|NULL");
  }

  @Test
  void theThreeConstraintsExist_andMatchStatusAdmitsRecorded() throws Exception {
    var constraints =
        texts(
            "SELECT c.conname, pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = '%s'"
                + " AND c.conname IN ('chk_bank_line_match_status', 'chk_bank_line_classification',"
                + " 'chk_trust_txn_charge') ORDER BY c.conname");
    assertThat(constraints).hasSize(3);
    assertThat(constraints.get(0)).startsWith("chk_bank_line_classification|");
    assertThat(constraints.get(1)).startsWith("chk_bank_line_match_status|").contains("RECORDED");
    assertThat(constraints.get(2)).startsWith("chk_trust_txn_charge|");
  }

  @Test
  void theLineChecksRefuseTheBadShapes() {
    // RECORDED without a classification
    assertRefused(
        "UPDATE \"%s\".bank_statement_lines SET match_status = 'RECORDED' WHERE id = '%lineUnmatched'");
    // a classification on a line that is not RECORDED
    assertRefused(
        "UPDATE \"%s\".bank_statement_lines SET classification = 'FUND_INTEREST'"
            + " WHERE id = '%lineUnmatched'");
    // an unknown classification
    assertRefused(
        "UPDATE \"%s\".bank_statement_lines SET match_status = 'RECORDED', classification = 'BOGUS'"
            + " WHERE id = '%lineUnmatched'");
    // the parser never suggests an unidentified deposit
    assertRefused(
        "UPDATE \"%s\".bank_statement_lines SET suggested_classification = 'UNIDENTIFIED_DEPOSIT'"
            + " WHERE id = '%lineUnmatched'");
    // an unknown match status
    assertRefused(
        "UPDATE \"%s\".bank_statement_lines SET match_status = 'POSTED' WHERE id = '%lineUnmatched'");
  }

  @Test
  void theChargeCheckRefusesTheBadShapes() {
    // a BANK_CHARGE without its detail
    assertRefused(chargeRow("57.50", "NULL", "NULL"));
    assertRefused(chargeRow("57.50", "true", "NULL"));
    assertRefused(chargeRow("57.50", "NULL", "7.50"));
    // VAT equal to the amount, and negative VAT
    assertRefused(chargeRow("57.50", "false", "57.50"));
    assertRefused(chargeRow("57.50", "false", "-0.01"));
    // charge detail on a row that is not a charge
    assertRefused(
        "UPDATE \"%s\".trust_transactions SET charge_recoverable = false WHERE id = '%txSweep'");
    assertRefused(
        "UPDATE \"%s\".trust_transactions SET charge_vat_amount = 0 WHERE id = '%txSweep'");
  }

  @Test
  void theChecksAcceptTheGoodShapes() throws Exception {
    exec(
        """
        INSERT INTO "%s".bank_statement_lines (bank_statement_id, line_number, transaction_date,
            description, amount, match_status, classification, suggested_classification)
        VALUES ('%bankStatement', 10, DATE '2025-03-31', 'Credit interest', 12.34, 'RECORDED',
                'FUND_INTEREST', 'FUND_INTEREST'),
               ('%bankStatement', 11, DATE '2025-03-31', 'Service fee', -5.00, 'UNMATCHED',
                NULL, 'BANK_CHARGE');
        """);
    exec(chargeRow("57.50", "true", "0"));
    exec(chargeRow("57.50", "false", "7.50"));
    exec(
        """
        INSERT INTO "%s".trust_transactions (trust_account_id, transaction_type, amount,
            creditor_account_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at, reversal_of)
        VALUES ('%account', 'REVERSAL', 40.00, '%fund', 'REV-SWP-1', DATE '2025-03-31',
                'RECORDED', '%owner', 'NON_CLIENT', 40.00, 40.00, now(), '%txSweep');
        """);
    assertThat(
            text(
                "SELECT count(*) FROM \"%s\".trust_transactions WHERE transaction_type ="
                    + " 'BANK_CHARGE' AND charge_recoverable IS NOT NULL"))
        .isEqualTo("2");
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
    assertThat(
            text(
                "SELECT count(*) FROM pg_constraint c JOIN pg_namespace n"
                    + " ON n.oid = c.connamespace WHERE n.nspname = '%s'"
                    + " AND c.conname = 'chk_bank_line_match_status'"
                    + " AND pg_get_constraintdef(c.oid) LIKE '%RECORDED%'"))
        .isEqualTo("1");
  }

  // --- helpers ---

  /** An insert of a posted BANK_CHARGE of {@code amount} with the given detail (SQL literals). */
  private static String chargeRow(String amount, String recoverable, String vat) {
    return """
        INSERT INTO "%%s".trust_transactions (trust_account_id, transaction_type, amount,
            creditor_account_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at, charge_recoverable, charge_vat_amount)
        VALUES ('%%account', 'BANK_CHARGE', %1$s, '%%fund', 'CHG', DATE '2025-03-31', 'RECORDED',
                '%%owner', 'NON_CLIENT', -%1$s, -%1$s, now(), %2$s, %3$s)
        """
        .formatted(amount, recoverable, vat);
  }

  private void assertRefused(String sql) {
    assertThatThrownBy(() -> exec(sql))
        .as("refused: %s", sql)
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION));
  }

  /** The new columns and constraints — what a second run must leave untouched. */
  private List<String> fingerprint() throws Exception {
    var rows =
        texts(
            "SELECT c.conname || ':' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = '%s'"
                + " AND c.conname IN ('chk_bank_line_match_status', 'chk_bank_line_classification',"
                + " 'chk_trust_txn_charge') ORDER BY c.conname");
    rows.addAll(
        texts(
            "SELECT table_name || '.' || column_name || ':' || data_type FROM"
                + " information_schema.columns WHERE table_schema = '%s' AND column_name IN"
                + " ('classification', 'suggested_classification', 'charge_recoverable',"
                + " 'charge_vat_amount') ORDER BY 1"));
    return rows;
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
