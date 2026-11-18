package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

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
 * Phase 92 slice 648A (task 648.3, architecture §16.2.7 and the {@code trust_investments} part of
 * §16.7.7, ADR-379, ruling #12): tenant {@code V23} restructures {@code trust_investments} — an
 * investment sits in an {@code INVESTMENT} trust account ({@code investment_account_id}, NOT NULL),
 * names its matter ({@code project_id}), and a section 86(3) investment names no client ({@code
 * customer_id} nullable, {@code chk_trust_invest_client_by_basis}). Legacy investments cannot be
 * carried (no investment account exists for them) and are deleted; their placement {@code PAYMENT}
 * and withdrawal {@code DEPOSIT} rows stay, so every balance is unchanged.
 *
 * <p>648C found.md F-1: the CHECK names {@code investment_basis IS NOT NULL} explicitly and tests
 * every column with {@code IS [NOT] NULL} — a CHECK passes on NULL, so without it a NULL basis
 * would slip through three-valued logic.
 *
 * <p>A scratch tenant schema is migrated to V22, seeded with a legacy investment (its posted
 * placement payment and withdrawal deposit), then migrated to V23 — pinned so a later migration
 * cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentAccountsMigrationTest {

  /** The investment-accounts migration (pinned, not "latest"). */
  private static final String MIGRATION_VERSION = "23";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V23__phase92_investment_accounts.sql";

  private static final String CHECK_VIOLATION = "23514";
  private static final String NOT_NULL_VIOLATION = "23502";
  private static final String CONSTRAINT = "chk_trust_invest_client_by_basis";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_investment_accounts_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%client", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%matter", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%general", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%investAcct", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%openingDep", "00000000-0000-0000-0000-0000000000d1");
    IDS.put("%legacyPay", "00000000-0000-0000-0000-0000000000d2");
    IDS.put("%legacyDep", "00000000-0000-0000-0000-0000000000d3");
    IDS.put("%activePay", "00000000-0000-0000-0000-0000000000d4");
    IDS.put("%withdrawnInv", "00000000-0000-0000-0000-0000000000e1");
    IDS.put("%activeInv", "00000000-0000-0000-0000-0000000000e2");
    IDS.put("%keptInv", "00000000-0000-0000-0000-0000000000e3");
  }

  private final DataSource migrationDataSource;
  private List<String> trustRowsBefore;
  private List<String> balancesBefore;

  @Autowired
  TrustInvestmentAccountsMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV22() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("22")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_tiam', 'tiam@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, customer_type, lifecycle_status, created_by)
        VALUES ('%client', 'Client A', 'a@tiam.test', 'INDIVIDUAL', 'ACTIVE', '%owner');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%matter', 'Matter', '%owner');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%general', 'Trust General', 'FNB', '250655', '62000000023', 'GENERAL', true,
                false, 'ACTIVE', DATE '2025-01-01', NULL),
               ('%investAcct', 'Trust Investment', 'Investec', '580105', '71000000023',
                'INVESTMENT', false, false, 'ACTIVE', DATE '2025-01-01', 'CLIENT_INSTRUCTION');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at)
        VALUES ('%openingDep', '%general', 'DEPOSIT', 5000.00, '%client', '%matter', 'DEP-1',
                DATE '2025-02-01', 'RECORDED', '%owner', 'CLIENT', 5000.00, 5000.00, now()),
               ('%legacyPay', '%general', 'PAYMENT', 1000.00, '%client', '%matter',
                'INV-Investec-1', DATE '2025-02-02', 'APPROVED', '%owner', 'CLIENT', -1000.00,
                -1000.00, now()),
               ('%legacyDep', '%general', 'DEPOSIT', 1010.00, '%client', '%matter',
                'INV-WD-Investec-1', DATE '2025-03-02', 'RECORDED', '%owner', 'CLIENT', 1010.00,
                1010.00, now()),
               ('%activePay', '%general', 'PAYMENT', 2000.00, '%client', '%matter',
                'INV-Investec-2', DATE '2025-02-03', 'APPROVED', '%owner', 'CLIENT', -2000.00,
                -2000.00, now());
        INSERT INTO "%s".trust_investments (id, trust_account_id, customer_id, institution,
            account_number, principal, interest_rate, deposit_date, interest_earned, status,
            withdrawal_date, withdrawal_amount, deposit_transaction_id, withdrawal_transaction_id,
            investment_basis)
        VALUES ('%withdrawnInv', '%general', '%client', 'Investec', '1', 1000.00, 0.0500,
                DATE '2025-02-02', 10.00, 'WITHDRAWN', DATE '2025-03-02', 1010.00, '%legacyPay',
                '%legacyDep', 'CLIENT_INSTRUCTION'),
               ('%activeInv', '%general', '%client', 'Investec', '2', 2000.00, 0.0500,
                DATE '2025-02-03', 0.00, 'ACTIVE', NULL, NULL, '%activePay', NULL,
                'FIRM_DISCRETION');
        """);
    trustRowsBefore = trustRows();
    balancesBefore = balances();
    tenantFlyway().target(MigrationVersion.fromVersion(MIGRATION_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void legacyInvestmentsAreDeleted_theirPaymentsAndDepositsRemain_balancesUnchanged()
      throws Exception {
    assertThat(
            texts(
                "SELECT id FROM \"%s\".trust_investments WHERE id IN ('%withdrawnInv',"
                    + " '%activeInv')"))
        .isEmpty();
    // The placement PAYMENTs and the withdrawal DEPOSIT stay as the rows they were posted as
    assertThat(trustRows()).isEqualTo(trustRowsBefore).hasSize(4);
    // Cashbook and client ledger of the account unchanged (5000 − 1000 + 1010 − 2000 = 3010)
    assertThat(balances()).isEqualTo(balancesBefore).containsExactly("3010.00|3010.00");
  }

  @Test
  void theColumnsHaveTheirTypeAndNullability() throws Exception {
    assertThat(column("investment_account_id")).isEqualTo("uuid|NO");
    assertThat(column("project_id")).isEqualTo("uuid|YES");
    assertThat(column("customer_id")).isEqualTo("uuid|YES");
    assertThat(column("deposit_transaction_id")).isEqualTo("uuid|YES");
  }

  @Test
  void theForeignKeysAndTheIndexExist() throws Exception {
    assertThat(constraintDef("trust_investments_investment_account_id_fkey"))
        .contains("FOREIGN KEY (investment_account_id)")
        .contains("trust_accounts(id)");
    assertThat(constraintDef("trust_investments_project_id_fkey"))
        .contains("FOREIGN KEY (project_id)")
        .contains("projects(id)");
    assertThat(
            text(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_trust_invest_investment_account'"))
        .contains("(investment_account_id, status)");
  }

  @Test
  void theCheckNamesTheBasisExplicitly_andBothBases() throws Exception {
    assertThat(constraintDef(CONSTRAINT))
        .startsWith("CHECK")
        .contains("'CLIENT_INSTRUCTION'", "'FIRM_DISCRETION'")
        // F-1: a NULL basis must not slip through three-valued logic (a CHECK passes on NULL)
        .contains("investment_basis IS NOT NULL")
        .contains("customer_id IS NOT NULL", "customer_id IS NULL")
        .contains("project_id IS NOT NULL", "project_id IS NULL");
  }

  @Test
  void theCheckRefusesTheBadShapes_andAcceptsTheGoodOnes() throws Exception {
    // Section 86(4): client and matter both required
    assertRefused(investment("'%investAcct'", "NULL", "'%matter'", "CLIENT_INSTRUCTION"));
    assertRefused(investment("'%investAcct'", "'%client'", "NULL", "CLIENT_INSTRUCTION"));
    // Section 86(3): neither a client nor a matter
    assertRefused(investment("'%investAcct'", "'%client'", "NULL", "FIRM_DISCRETION"));
    assertRefused(investment("'%investAcct'", "NULL", "'%matter'", "FIRM_DISCRETION"));
    // Every investment sits in an investment account
    assertThatThrownBy(() -> exec(investment("NULL", "NULL", "NULL", "FIRM_DISCRETION")))
        .isInstanceOfSatisfying(
            SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo(NOT_NULL_VIOLATION);
              assertThat(e.getMessage()).contains("investment_account_id");
            });

    exec(investment("'%investAcct'", "'%client'", "'%matter'", "CLIENT_INSTRUCTION"));
    exec(investment("'%investAcct'", "NULL", "NULL", "FIRM_DISCRETION"));
  }

  @Test
  void runningTheMigrationBodyTwice_isANoOp_andKeepsNewShapeInvestments() throws Exception {
    exec(
        investment("'%investAcct'", "NULL", "NULL", "FIRM_DISCRETION")
            .replace("VALUES (", "VALUES ('%keptInv', ")
            .replace("(trust_account_id,", "(id, trust_account_id,"));
    var before = fingerprint();
    for (int run = 0; run < 2; run++) {
      runBody();
    }
    assertThat(fingerprint()).isEqualTo(before);
    assertThat(texts("SELECT id FROM \"%s\".trust_investments WHERE id = '%keptInv'")).hasSize(1);
  }

  // --- helpers ---

  /** A new-shape investment with the given SQL literals (account, client, matter) and basis. */
  private static String investment(
      String investmentAccount, String client, String matter, String basis) {
    return """
        INSERT INTO "%%s".trust_investments (trust_account_id, investment_account_id, customer_id,
            project_id, institution, account_number, principal, interest_rate, deposit_date,
            investment_basis)
        VALUES ('%%general', %1$s, %2$s, %3$s, 'Investec', 'SHAPE', 100.00, 0.0500,
                DATE '2025-04-01', '%4$s')
        """
        .formatted(investmentAccount, client, matter, basis);
  }

  private void runBody() throws Exception {
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
  }

  private String column(String name) throws Exception {
    return text(
        "SELECT data_type || '|' || is_nullable FROM information_schema.columns"
            + " WHERE table_schema = '%s' AND table_name = 'trust_investments'"
            + " AND column_name = '"
            + name
            + "'");
  }

  private String constraintDef(String name) throws Exception {
    return text(
        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c"
            + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = '%s'"
            + " AND c.conname = '"
            + name
            + "'");
  }

  /** Refused by {@code chk_trust_invest_client_by_basis} (SQLState 23514, the CHECK named). */
  private void assertRefused(String sql) {
    assertThatThrownBy(() -> exec(sql))
        .as("refused: %s", sql)
        .isInstanceOfSatisfying(
            SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION);
              assertThat(e.getMessage()).contains("\"" + CONSTRAINT + "\"");
            });
  }

  /** The seeded trust rows, every column the migration must not change. */
  private List<String> trustRows() throws Exception {
    return texts(
        "SELECT id, transaction_type, amount, customer_id, project_id, reference, status, ledger,"
            + " cashbook_effect, ledger_effect, COALESCE(investment_id::text, '-')"
            + " FROM \"%s\".trust_transactions ORDER BY id");
  }

  /** The account's cashbook and client-ledger total. */
  private List<String> balances() throws Exception {
    return texts(
        "SELECT SUM(cashbook_effect), SUM(ledger_effect) FILTER (WHERE ledger = 'CLIENT')"
            + " FROM \"%s\".trust_transactions WHERE trust_account_id = '%general'"
            + " AND posted_at IS NOT NULL");
  }

  /** The table's columns, constraints, indexes and rows — what a second run must not change. */
  private List<String> fingerprint() throws Exception {
    var rows =
        texts(
            "SELECT column_name || '|' || data_type || '|' || is_nullable"
                + " FROM information_schema.columns WHERE table_schema = '%s'"
                + " AND table_name = 'trust_investments' ORDER BY column_name");
    rows.addAll(
        texts(
            "SELECT c.conname || '|' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace JOIN pg_class t"
                + " ON t.oid = c.conrelid WHERE n.nspname = '%s' AND t.relname = 'trust_investments'"
                + " ORDER BY c.conname"));
    rows.addAll(
        texts(
            "SELECT indexname || '|' || indexdef FROM pg_indexes WHERE schemaname = '%s'"
                + " AND tablename = 'trust_investments' ORDER BY indexname"));
    rows.addAll(texts("SELECT id FROM \"%s\".trust_investments ORDER BY id"));
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
