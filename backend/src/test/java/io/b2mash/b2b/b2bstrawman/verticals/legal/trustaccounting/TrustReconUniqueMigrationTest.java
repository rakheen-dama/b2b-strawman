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
import java.util.stream.Stream;
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
 * Phase 92 slice 652C (task 652.9, architecture §16.3.12 / §16.7.11): tenant {@code V29} keeps one
 * reconciliation per trust account and period end and backs it with {@code
 * uq_trust_recon_account_period}. A scratch tenant schema is migrated to V28 and seeded with
 * duplicate rows of every shape the migration ranks — two drafts, a COMPLETED beside a newer DRAFT,
 * a list-referenced COMPLETED beside a later-completed one, two COMPLETED at a mid-month period end
 * — plus single rows that must survive, then migrated to V29, pinned so a later migration cannot
 * change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconUniqueMigrationTest {

  /** The unique-reconciliation migration (pinned, not "latest"). */
  private static final String UNIQUE_VERSION = "29";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V29__phase92_trust_recon_unique.sql";

  private static final String UNIQUE_VIOLATION = "23505";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_trust_recon_unique_scratch");

  // Placeholders: none is a prefix of another (resolve() replaces them in insertion order).
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%acctA", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%acctB", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%draftOld", "00000000-0000-0000-0000-000000000e01");
    IDS.put("%draftNew", "00000000-0000-0000-0000-000000000e02");
    IDS.put("%completedFeb", "00000000-0000-0000-0000-000000000e03");
    IDS.put("%draftFeb", "00000000-0000-0000-0000-000000000e04");
    IDS.put("%listedMar", "00000000-0000-0000-0000-000000000e05");
    IDS.put("%laterMar", "00000000-0000-0000-0000-000000000e06");
    IDS.put("%midOld", "00000000-0000-0000-0000-000000000e07");
    IDS.put("%midNew", "00000000-0000-0000-0000-000000000e08");
    IDS.put("%lone", "00000000-0000-0000-0000-000000000e09");
    IDS.put("%otherAcct", "00000000-0000-0000-0000-000000000e0a");
    IDS.put("%monthList", "00000000-0000-0000-0000-000000000f01");
  }

  private final DataSource migrationDataSource;

  @Autowired
  TrustReconUniqueMigrationTest(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV28() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("28")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_tru', 'tru@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%acctA', 'Trust A', 'FNB', '250655', '62000000029', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01', NULL),
               ('%acctB', 'Trust B', 'FNB', '250655', '62000000030', 'GENERAL', false, false,
                'ACTIVE', DATE '2025-01-01', NULL);
        INSERT INTO "%s".trust_reconciliations (id, trust_account_id, period_end, bank_balance,
            cashbook_balance, client_ledger_total, adjusted_bank_balance, is_balanced, status,
            completed_by, completed_at, updated_at)
        VALUES ('%draftOld', '%acctA', DATE '2026-01-31', 0, 0, 0, 0, false, 'DRAFT',
                NULL, NULL, TIMESTAMPTZ '2026-02-01 10:00Z'),
               ('%draftNew', '%acctA', DATE '2026-01-31', 0, 0, 0, 0, false, 'DRAFT',
                NULL, NULL, TIMESTAMPTZ '2026-02-03 10:00Z'),
               ('%completedFeb', '%acctA', DATE '2026-02-28', 100.00, 100.00, 100.00, 100.00,
                true, 'COMPLETED', '%owner', TIMESTAMPTZ '2026-03-02 10:00Z',
                TIMESTAMPTZ '2026-03-02 10:00Z'),
               ('%draftFeb', '%acctA', DATE '2026-02-28', 0, 0, 0, 0, false, 'DRAFT',
                NULL, NULL, TIMESTAMPTZ '2026-03-05 10:00Z'),
               ('%listedMar', '%acctA', DATE '2026-03-31', 100.00, 100.00, 100.00, 100.00,
                true, 'COMPLETED', '%owner', TIMESTAMPTZ '2026-04-02 10:00Z',
                TIMESTAMPTZ '2026-04-02 10:00Z'),
               ('%laterMar', '%acctA', DATE '2026-03-31', 100.00, 100.00, 100.00, 100.00,
                true, 'COMPLETED', '%owner', TIMESTAMPTZ '2026-04-05 10:00Z',
                TIMESTAMPTZ '2026-04-05 10:00Z'),
               ('%midOld', '%acctA', DATE '2026-04-15', 100.00, 100.00, 100.00, 100.00,
                true, 'COMPLETED', '%owner', TIMESTAMPTZ '2026-04-16 10:00Z',
                TIMESTAMPTZ '2026-04-16 10:00Z'),
               ('%midNew', '%acctA', DATE '2026-04-15', 100.00, 100.00, 100.00, 100.00,
                true, 'COMPLETED', '%owner', TIMESTAMPTZ '2026-04-18 10:00Z',
                TIMESTAMPTZ '2026-04-18 10:00Z'),
               ('%lone', '%acctA', DATE '2026-05-31', 0, 0, 0, 0, false, 'DRAFT',
                NULL, NULL, TIMESTAMPTZ '2026-06-01 10:00Z'),
               ('%otherAcct', '%acctB', DATE '2026-01-31', 0, 0, 0, 0, false, 'DRAFT',
                NULL, NULL, TIMESTAMPTZ '2026-02-01 10:00Z');
        INSERT INTO "%s".trust_monthly_lists (id, trust_account_id, period_end, reconciliation_id,
            bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance,
            cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance,
            unidentified_deposit_total, inter_account_balance, creditor_total, difference, lines,
            locked_by, locked_at)
        VALUES ('%monthList', '%acctA', DATE '2026-03-31', '%listedMar', 100.00, 0, 0, 100.00, 100.00,
                100.00, 0, 0, 0, 0, 100.00, 0, '[]'::jsonb, '%owner', now());
        """);
    tenantFlyway().target(MigrationVersion.fromVersion(UNIQUE_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  /**
   * One row per (account, period end): the newer of two drafts; a COMPLETED over a newer DRAFT; the
   * list-referenced COMPLETED over a later-completed one; the later of two COMPLETED (D-2); the
   * single rows untouched.
   */
  @Test
  void duplicateRows_keepOnePerAccountAndPeriodEnd_inTheStatedOrder() throws Exception {
    var expected =
        Stream.of("%draftNew", "%completedFeb", "%listedMar", "%midNew", "%lone", "%otherAcct")
            .map(IDS::get)
            .sorted()
            .toList();
    assertThat(texts("SELECT id FROM \"%s\".trust_reconciliations ORDER BY id"))
        .containsExactlyElementsOf(expected);
  }

  @Test
  void aRowAMonthlyListReferences_isNeverDeleted() throws Exception {
    assertThat(texts("SELECT id || '|' || reconciliation_id FROM \"%s\".trust_monthly_lists"))
        .containsExactly(IDS.get("%monthList") + "|" + IDS.get("%listedMar"));
  }

  @Test
  void theUniqueIndexExists_andTheLatestFirstIndexIsKept() throws Exception {
    assertThat(
            texts(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'uq_trust_recon_account_period'"))
        .singleElement()
        .satisfies(
            def -> {
              assertThat(def).startsWith("CREATE UNIQUE INDEX");
              assertThat(def).endsWith("(trust_account_id, period_end)");
            });
    assertThat(
            texts(
                "SELECT indexname FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_trust_recon_account_period'"))
        .containsExactly("idx_trust_recon_account_period");
  }

  @Test
  void aSecondRowForOneAccountAndPeriodEnd_isRefused() {
    assertRefused(
        "INSERT INTO \"%s\".trust_reconciliations (trust_account_id, period_end, bank_balance,"
            + " cashbook_balance, client_ledger_total, adjusted_bank_balance)"
            + " VALUES ('%acctA', DATE '2026-05-31', 0, 0, 0, 0)",
        UNIQUE_VIOLATION, "uq_trust_recon_account_period");
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

  /** Every reconciliation and list row, and the table's indexes — what a second run must keep. */
  private List<String> fingerprint() throws Exception {
    var rows =
        new ArrayList<>(
            texts(
                "SELECT id || ':' || status || ':' || updated_at"
                    + " FROM \"%s\".trust_reconciliations ORDER BY id"));
    rows.addAll(texts("SELECT id FROM \"%s\".trust_monthly_lists ORDER BY id"));
    rows.addAll(
        texts(
            "SELECT indexname FROM pg_indexes WHERE schemaname = '%s'"
                + " AND tablename = 'trust_reconciliations' ORDER BY indexname"));
    return rows;
  }

  private void assertRefused(String sql, String sqlState, String constraintFragment) {
    assertThatThrownBy(() -> exec(sql))
        .as("refused: %s", sql)
        .isInstanceOfSatisfying(
            SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo(sqlState);
              assertThat(e.getMessage()).contains(constraintFragment);
            });
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
