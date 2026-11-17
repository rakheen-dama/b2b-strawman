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
 * Phase 92 slice 647B (task 647.9, architecture §16.7.6, ADR-376): tenant {@code V21} itemises
 * unidentified deposits — the {@code unidentified_deposits} table with its CHECKs, unique opening
 * row and eight foreign keys, and {@code trust_transactions.unidentified_deposit_id} with {@code
 * chk_trust_txn_unidentified_item} (every row of the three item types names its item). A scratch
 * tenant schema is migrated to V20, seeded with a trust account, both creditor ledgers, a posted
 * Fund row and a statement line, then migrated to V21 — pinned so a later migration cannot change
 * these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnidentifiedDepositsMigrationTest {

  /** The unidentified-deposits migration (pinned, not "latest"). */
  private static final String MIGRATION_VERSION = "21";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V21__phase92_unidentified_deposits.sql";

  private static final String CHECK_VIOLATION = "23514";
  private static final String UNIQUE_VIOLATION = "23505";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_unidentified_deposits_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%owner", "00000000-0000-0000-0000-000000000001");
    IDS.put("%account", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%fund", "00000000-0000-0000-0000-0000000000d1");
    IDS.put("%udLedger", "00000000-0000-0000-0000-0000000000d2");
    IDS.put("%bankStatement", "00000000-0000-0000-0000-0000000000f1");
    IDS.put("%creditLine", "00000000-0000-0000-0000-0000000000a1");
    IDS.put("%txInterest", "00000000-0000-0000-0000-0000000000b1");
    IDS.put("%goodItem", "00000000-0000-0000-0000-0000000000e1");
    IDS.put("%goodRow", "00000000-0000-0000-0000-0000000000b2");
    IDS.put("%uniqueItemA", "00000000-0000-0000-0000-0000000000e2");
    IDS.put("%uniqueItemB", "00000000-0000-0000-0000-0000000000e3");
  }

  /** The new table's constraints, by name. */
  private static final List<String> ITEM_CONSTRAINTS =
      List.of(
          "chk_unidentified_deposit_allocated",
          "chk_unidentified_deposit_amount",
          "chk_unidentified_deposit_resolved",
          "chk_unidentified_deposit_status",
          "unidentified_deposits_alloc_credit_txn_fkey",
          "unidentified_deposits_creditor_account_id_fkey",
          "unidentified_deposits_customer_fkey",
          "unidentified_deposits_deposit_txn_fkey",
          "unidentified_deposits_line_fkey",
          "unidentified_deposits_pkey",
          "unidentified_deposits_project_fkey",
          "unidentified_deposits_resolution_txn_fkey",
          "unidentified_deposits_trust_account_id_fkey",
          "uq_unidentified_deposit_txn");

  private final DataSource migrationDataSource;
  private List<String> seededRowsBefore;

  @Autowired
  UnidentifiedDepositsMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV20() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("20")).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%owner', 'user_udm', 'udm@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%account', 'Trust 1', 'FNB', '250655', '62000000021', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".trust_creditor_accounts (id, trust_account_id, account_type, name)
        VALUES ('%fund', '%account', 'FUND_INTEREST_AND_CHARGES', 'Fidelity Fund'),
               ('%udLedger', '%account', 'UNIDENTIFIED_DEPOSITS', 'Unidentified deposits');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            creditor_account_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at)
        VALUES ('%txInterest', '%account', 'FUND_INTEREST', 100.00, '%fund', 'INT-1',
                DATE '2025-03-31', 'RECORDED', '%owner', 'NON_CLIENT', 100.00, 100.00, now());
        INSERT INTO "%s".bank_statements (id, trust_account_id, period_start, period_end,
            opening_balance, closing_balance, file_key, file_name, format, line_count,
            matched_count, status, imported_by)
        VALUES ('%bankStatement', '%account', DATE '2025-03-01', DATE '2025-03-31', 0, 300,
                'k/statement.csv', 'statement.csv', 'CSV', 1, 0, 'IMPORTED', '%owner');
        INSERT INTO "%s".bank_statement_lines (id, bank_statement_id, line_number,
            transaction_date, description, reference, amount, match_status)
        VALUES ('%creditLine', '%bankStatement', 1, DATE '2025-03-10', 'Unknown credit',
                'UNK-1', 300.00, 'UNMATCHED');
        """);
    seededRowsBefore = seededRows();
    tenantFlyway().target(MigrationVersion.fromVersion(MIGRATION_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void theTableAndTheNewColumnExistWithTheirTypes() throws Exception {
    assertThat(
            texts(
                "SELECT column_name, data_type, COALESCE(character_maximum_length::text, '-'),"
                    + " COALESCE(numeric_precision::text, '-'),"
                    + " COALESCE(numeric_scale::text, '-'), is_nullable"
                    + " FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'unidentified_deposits' ORDER BY ordinal_position"))
        .containsExactly(
            "id|uuid|-|-|-|NO",
            "trust_account_id|uuid|-|-|-|NO",
            "creditor_account_id|uuid|-|-|-|NO",
            "deposit_transaction_id|uuid|-|-|-|YES",
            "bank_statement_line_id|uuid|-|-|-|NO",
            "received_date|date|-|-|-|NO",
            "bank_description|character varying|500|-|-|NO",
            "bank_reference|character varying|200|-|-|YES",
            "amount|numeric|-|15|2|NO",
            "status|character varying|20|-|-|NO",
            "resolution_transaction_id|uuid|-|-|-|YES",
            "allocation_credit_transaction_id|uuid|-|-|-|YES",
            "allocated_customer_id|uuid|-|-|-|YES",
            "allocated_project_id|uuid|-|-|-|YES",
            "resolved_date|date|-|-|-|YES",
            "notes|text|-|-|-|YES",
            "created_at|timestamp with time zone|-|-|-|NO",
            "updated_at|timestamp with time zone|-|-|-|NO");
    assertThat(
            text(
                "SELECT data_type || '|' || is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = '%s' AND table_name = 'trust_transactions'"
                    + " AND column_name = 'unidentified_deposit_id'"))
        .isEqualTo("uuid|YES");
  }

  @Test
  void theSeededRowsAreUntouched_andNameNoItem() throws Exception {
    assertThat(seededRows()).isEqualTo(seededRowsBefore);
    assertThat(
            text(
                "SELECT COALESCE(unidentified_deposit_id::text, 'NULL') FROM"
                    + " \"%s\".trust_transactions WHERE id = '%txInterest'"))
        .isEqualTo("NULL");
  }

  @Test
  void everyConstraintExists_andNoForeignKeyCascades() throws Exception {
    var names =
        texts(
            "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace"
                + " JOIN pg_class t ON t.oid = c.conrelid WHERE n.nspname = '%s'"
                + " AND t.relname = 'unidentified_deposits' ORDER BY c.conname");
    assertThat(names).containsExactlyElementsOf(ITEM_CONSTRAINTS);
    assertThat(
            text(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n"
                    + " ON n.oid = c.connamespace WHERE n.nspname = '%s'"
                    + " AND c.conname = 'chk_unidentified_deposit_status'"))
        .contains("'HELD'", "'ALLOCATED'", "'PAID_TO_FUND'", "'REVERSED'");
    assertThat(
            texts(
                "SELECT c.conname || '|' || c.contype::text FROM pg_constraint c JOIN pg_namespace n"
                    + " ON n.oid = c.connamespace WHERE n.nspname = '%s' AND c.conname IN"
                    + " ('trust_transactions_unidentified_deposit_id_fkey',"
                    + " 'chk_trust_txn_unidentified_item') ORDER BY c.conname"))
        .containsExactly(
            "chk_trust_txn_unidentified_item|c",
            "trust_transactions_unidentified_deposit_id_fkey|f");
    // The eight item foreign keys and the row's one: none cascades (confdeltype 'a' = NO ACTION).
    assertThat(
            texts(
                "SELECT c.conname || '|' || c.confdeltype::text FROM pg_constraint c JOIN pg_namespace n"
                    + " ON n.oid = c.connamespace WHERE n.nspname = '%s' AND c.contype = 'f'"
                    + " AND (c.conname LIKE 'unidentified_deposits_%%'"
                    + " OR c.conname = 'trust_transactions_unidentified_deposit_id_fkey')"
                    + " ORDER BY c.conname"))
        .hasSize(9)
        .allSatisfy(row -> assertThat(row).endsWith("|a"));
  }

  @Test
  void bothIndexesExist_theRowIndexIsPartial() throws Exception {
    assertThat(
            text(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_unidentified_deposits_account_status'"))
        .contains("(trust_account_id, status, received_date)");
    assertThat(
            text(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = '%s'"
                    + " AND indexname = 'idx_trust_txn_unidentified_deposit'"))
        .contains("(unidentified_deposit_id, transaction_date)")
        .contains("WHERE (unidentified_deposit_id IS NOT NULL)");
  }

  @Test
  void theItemChecksRefuseTheBadShapes() {
    // amount must be positive
    assertRefused(item("0.00", "'HELD'", "NULL", "NULL"), "chk_unidentified_deposit_amount");
    assertRefused(item("-5.00", "'HELD'", "NULL", "NULL"), "chk_unidentified_deposit_amount");
    // an unknown status (it fails chk_unidentified_deposit_resolved too — either may report it;
    // the status CHECK's own list is pinned in everyConstraintExists_andNoForeignKeyCascades)
    assertRefused(
        item("10.00", "'OPEN'", "NULL", "NULL"), "chk_unidentified_deposit_(status|resolved)");
    // HELD with a resolved date; REVERSED / PAID_TO_FUND without one
    assertRefused(
        item("10.00", "'HELD'", "DATE '2025-03-11'", "NULL"), "chk_unidentified_deposit_resolved");
    assertRefused(item("10.00", "'REVERSED'", "NULL", "NULL"), "chk_unidentified_deposit_resolved");
    assertRefused(
        item("10.00", "'PAID_TO_FUND'", "NULL", "NULL"), "chk_unidentified_deposit_resolved");
    // ALLOCATED without its client, matter and credit row
    assertRefused(
        item("10.00", "'ALLOCATED'", "DATE '2025-03-11'", "NULL"),
        "chk_unidentified_deposit_allocated");
  }

  @Test
  void aRowOfTheThreeItemTypesMustNameItsItem_aFundRowNeedNot() throws Exception {
    for (var type : List.of("UNIDENTIFIED_DEPOSIT", "UNIDENT_ALLOC_OUT", "UNIDENT_FUND_PAY")) {
      assertRefused(unidentifiedRow(type, "NULL", "NULL"), "chk_trust_txn_unidentified_item");
    }
    exec(
        """
        INSERT INTO "%s".trust_transactions (trust_account_id, transaction_type, amount,
            creditor_account_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at)
        VALUES ('%account', 'FUND_INTEREST', 5.00, '%fund', 'INT-2', DATE '2025-03-31',
                'RECORDED', '%owner', 'NON_CLIENT', 5.00, 5.00, now());
        """);
  }

  @Test
  void theGoodShape_itemFirst_thenTheRowNamingIt_thenTheOpeningRow() throws Exception {
    exec(item("300.00", "'HELD'", "NULL", "'%goodItem'"));
    exec(unidentifiedRow("UNIDENTIFIED_DEPOSIT", "'%goodItem'", "'%goodRow'"));
    exec(
        "UPDATE \"%s\".unidentified_deposits SET deposit_transaction_id = '%goodRow'"
            + " WHERE id = '%goodItem'");
    exec(
        "UPDATE \"%s\".unidentified_deposits SET status = 'REVERSED',"
            + " resolved_date = DATE '2025-03-10' WHERE id = '%goodItem'");
    assertThat(
            text(
                "SELECT d.status || '|' || d.deposit_transaction_id || '|' || t.unidentified_deposit_id"
                    + " FROM \"%s\".unidentified_deposits d JOIN \"%s\".trust_transactions t"
                    + " ON t.id = d.deposit_transaction_id WHERE d.id = '%goodItem'"))
        .isEqualTo("REVERSED|" + IDS.get("%goodRow") + "|" + IDS.get("%goodItem"));
  }

  @Test
  void anOpeningRowOpensOneItemOnly() throws Exception {
    exec(
        "INSERT INTO \"%s\".unidentified_deposits (id, trust_account_id, creditor_account_id,"
            + " deposit_transaction_id, bank_statement_line_id, received_date, bank_description,"
            + " amount) VALUES ('%uniqueItemA', '%account', '%udLedger', '%txInterest',"
            + " '%creditLine', DATE '2025-03-10', 'Unknown credit', 10.00)");
    assertRefused(
        "INSERT INTO \"%s\".unidentified_deposits (id, trust_account_id, creditor_account_id,"
            + " deposit_transaction_id, bank_statement_line_id, received_date, bank_description,"
            + " amount) VALUES ('%uniqueItemB', '%account', '%udLedger', '%txInterest',"
            + " '%creditLine', DATE '2025-03-10', 'Unknown credit', 10.00)",
        UNIQUE_VIOLATION, "uq_unidentified_deposit_txn");
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

  /** An item insert on the seeded line with the given amount / status / resolved date / id. */
  private static String item(String amount, String status, String resolvedDate, String id) {
    return """
        INSERT INTO "%%s".unidentified_deposits (id, trust_account_id, creditor_account_id,
            bank_statement_line_id, received_date, bank_description, amount, status, resolved_date)
        VALUES (COALESCE(%4$s::uuid, gen_random_uuid()), '%%account', '%%udLedger', '%%creditLine',
                DATE '2025-03-10', 'Unknown credit', %1$s, %2$s, %3$s)
        """
        .formatted(amount, status, resolvedDate, id);
  }

  /** A posted row of {@code type} on the unidentified ledger naming {@code item} (SQL literals). */
  private static String unidentifiedRow(String type, String item, String id) {
    return """
        INSERT INTO "%%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            creditor_account_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at, unidentified_deposit_id,
            bank_statement_line_id)
        VALUES (COALESCE(%3$s::uuid, gen_random_uuid()), '%%account', '%1$s', 300.00,
                '%%udLedger', 'UID-1', DATE '2025-03-10', 'RECORDED', '%%owner', 'NON_CLIENT',
                300.00, 300.00, now(), %2$s, '%%creditLine')
        """
        .formatted(type, item, id);
  }

  /** Refused by the CHECK {@code constraint} (SQLState 23514, the constraint named). */
  private void assertRefused(String sql, String constraint) {
    assertRefused(sql, CHECK_VIOLATION, constraint);
  }

  private void assertRefused(String sql, String sqlState, String constraint) {
    assertThatThrownBy(() -> exec(sql))
        .as("refused: %s", sql)
        .isInstanceOfSatisfying(
            SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo(sqlState);
              assertThat(e.getMessage()).containsPattern("\"" + constraint + "\"");
            });
  }

  /** The seeded rows a migration must not change. */
  private List<String> seededRows() throws Exception {
    var rows =
        texts(
            "SELECT id, transaction_type, amount::text, status, ledger, cashbook_effect::text,"
                + " ledger_effect::text, creditor_account_id FROM \"%s\".trust_transactions"
                + " WHERE id = '%txInterest'");
    rows.addAll(
        texts(
            "SELECT id, match_status, amount::text, COALESCE(classification, 'NULL')"
                + " FROM \"%s\".bank_statement_lines WHERE id = '%creditLine'"));
    rows.addAll(
        texts(
            "SELECT id, account_type FROM \"%s\".trust_creditor_accounts"
                + " WHERE trust_account_id = '%account' ORDER BY account_type"));
    return rows;
  }

  /** The new table, column, constraints and indexes — what a second run must leave untouched. */
  private List<String> fingerprint() throws Exception {
    var rows =
        texts(
            "SELECT c.conname || ':' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = '%s'"
                + " AND (c.conname LIKE '%%unidentified%%') ORDER BY c.conname");
    rows.addAll(
        texts(
            "SELECT indexname || ':' || indexdef FROM pg_indexes WHERE schemaname = '%s'"
                + " AND indexname LIKE '%%unidentified%%' ORDER BY indexname"));
    rows.addAll(
        texts(
            "SELECT table_name || '.' || column_name || ':' || data_type FROM"
                + " information_schema.columns WHERE table_schema = '%s' AND (table_name ="
                + " 'unidentified_deposits' OR column_name = 'unidentified_deposit_id')"
                + " ORDER BY 1"));
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
