package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
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
 * Phase 93 / 657A (task 657.2, architecture §17.7.3, ADR-386, ADR-387): tenant {@code
 * V33__phase93_trust_take_on} (guide V31/V32; V31 and V32 were taken — next free, D-55). A scratch
 * schema is walked to V32 with Phase 92 trust data (a posted deposit, an unidentified deposit
 * opened from its statement line with its posted row, a section 86(4) investment, a completed
 * reconciliation), then migrated to V33: every old row satisfies the new CHECKs, and each CHECK
 * rejects the shape it forbids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTakeOnSchemaTest {

  private static final String MIGRATION_FILE = "db/migration/tenant/V33__phase93_trust_take_on.sql";
  private static final String PREVIOUS_VERSION = "32";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String S =
      SchemaNameGenerator.generateSchemaName("org_trust_takeon_scratch");

  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID MATTER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID MATTER_2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID INV_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
  private static final UUID UD_CREDITOR = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID FUND_CREDITOR = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
  private static final UUID DEPOSIT_ROW = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID STATEMENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID LINE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID ITEM = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
  private static final UUID ITEM_ROW = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
  private static final UUID INVESTMENT = UUID.fromString("00000000-0000-0000-0000-000000000091");
  private static final UUID RECON = UUID.fromString("00000000-0000-0000-0000-000000000081");

  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private UUID batch;

  @BeforeAll
  void walkPhase92Schema() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
    exec("CREATE SCHEMA \"" + S + "\"");
    tenantFlyway().target(MigrationVersion.fromVersion(PREVIOUS_VERSION)).load().migrate();
    exec(
        """
        INSERT INTO "%s".members (id, clerk_user_id, email, org_role_id)
        SELECT '%o', 'user_tto', 'tto@test.com', id FROM "%s".org_roles WHERE slug = 'owner';
        INSERT INTO "%s".customers (id, name, email, created_by)
        VALUES ('%c', 'Phase 92 client', 'p92@test.com', '%o');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%m', 'Phase 92 matter', '%o');
        INSERT INTO "%s".projects (id, name, created_by) VALUES ('%m2', 'Second matter', '%o');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date)
        VALUES ('%a', 'Trust 1', 'FNB', '250655', '62000000093', 'GENERAL', true, false,
                'ACTIVE', DATE '2025-01-01');
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, status, opened_date, investment_basis)
        VALUES ('%ia', 'Investment 1', 'Investec', '580105', '62000000094', 'INVESTMENT', false,
                false, 'ACTIVE', DATE '2025-01-01', 'CLIENT_INSTRUCTION');
        INSERT INTO "%s".trust_creditor_accounts (id, trust_account_id, account_type, name)
        VALUES ('%ud', '%a', 'UNIDENTIFIED_DEPOSITS', 'Unidentified deposits'),
               ('%fc', '%a', 'FUND_INTEREST_AND_CHARGES', 'Fund');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, reference, transaction_date, status, recorded_by, ledger,
            cashbook_effect, ledger_effect, posted_at)
        VALUES ('%d', '%a', 'DEPOSIT', 1000.00, '%c', '%m', 'DEP-1',
                DATE '2026-09-30', 'RECORDED', '%o', 'CLIENT', 1000.00, 1000.00, now());
        INSERT INTO "%s".bank_statements (id, trust_account_id, period_start, period_end,
            opening_balance, closing_balance, file_key, file_name, format, line_count,
            matched_count, status, imported_by)
        VALUES ('%bs', '%a', DATE '2026-09-01', DATE '2026-09-30', 0, 1500.00, 'k', 's.csv',
                'CSV', 1, 1, 'IMPORTED', '%o');
        INSERT INTO "%s".bank_statement_lines (id, bank_statement_id, line_number,
            transaction_date, description, amount)
        VALUES ('%l', '%bs', 1, DATE '2026-09-15', 'Unknown credit', 500.00);
        INSERT INTO "%s".unidentified_deposits (id, trust_account_id, creditor_account_id,
            bank_statement_line_id, received_date, bank_description, amount, status)
        VALUES ('%it', '%a', '%ud', '%l', DATE '2026-09-15', 'Unknown credit', 500.00, 'HELD');
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            creditor_account_id, unidentified_deposit_id, bank_statement_line_id, reference,
            transaction_date, status, recorded_by, ledger, cashbook_effect, ledger_effect,
            posted_at)
        VALUES ('%ir', '%a', 'UNIDENTIFIED_DEPOSIT', 500.00, '%ud', '%it', '%l', 'UID-1',
                DATE '2026-09-15', 'RECORDED', '%o', 'NON_CLIENT', 500.00, 500.00, now());
        UPDATE "%s".unidentified_deposits SET deposit_transaction_id = '%ir' WHERE id = '%it';
        UPDATE "%s".bank_statement_lines SET match_status = 'RECORDED',
            classification = 'UNIDENTIFIED_DEPOSIT', trust_transaction_id = '%ir' WHERE id = '%l';
        INSERT INTO "%s".trust_investments (id, trust_account_id, investment_account_id,
            customer_id, project_id, institution, account_number, principal, interest_rate,
            deposit_date, investment_basis)
        VALUES ('%iv', '%a', '%ia', '%c', '%m', 'Investec', 'INV-1', 2000.00, 0.0500,
                DATE '2026-08-01', 'CLIENT_INSTRUCTION');
        INSERT INTO "%s".trust_reconciliations (id, trust_account_id, period_end, bank_balance,
            cashbook_balance, client_ledger_total, adjusted_bank_balance, is_balanced, status,
            creditor_total)
        VALUES ('%rc', '%a', DATE '2026-09-30', 1500.00, 1500.00, 1000.00, 1500.00, true,
                'COMPLETED', 1500.00);
        """
            .replace("%s", S)
            .replace("%o", OWNER.toString())
            .replace("%c", CLIENT.toString())
            .replace("%m2", MATTER_2.toString())
            .replace("%m", MATTER.toString())
            .replace("%ia", INV_ACCOUNT.toString())
            .replace("%a", ACCOUNT.toString())
            .replace("%ud", UD_CREDITOR.toString())
            .replace("%fc", FUND_CREDITOR.toString())
            .replace("%d", DEPOSIT_ROW.toString())
            .replace("%bs", STATEMENT.toString())
            .replace("%l", LINE.toString())
            .replace("%it", ITEM.toString())
            .replace("%ir", ITEM_ROW.toString())
            .replace("%iv", INVESTMENT.toString())
            .replace("%rc", RECON.toString()));
    tenantFlyway().load().migrate();
    batch = insertBatch();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"" + S + "\" CASCADE");
  }

  // 1
  @Test
  void migrationAppliesOnAWalkedPhase92Schema_andLeavesOldRowsAlone() {
    // Applied (not "the latest": a later migration must not break this pin — 655A, V34).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".flyway_schema_history WHERE success AND version = ?"
                    .formatted(S),
                Integer.class,
                "33"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".trust_transactions WHERE id IN (?, ?)"
                        + " AND take_on_batch_id IS NULL AND NOT cleared_at_cutover")
                    .formatted(S),
                Integer.class,
                DEPOSIT_ROW,
                ITEM_ROW))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT bank_statement_line_id FROM \"%s\".unidentified_deposits WHERE id = ?"
                    .formatted(S),
                UUID.class,
                ITEM))
        .isEqualTo(LINE);
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".trust_investments WHERE id = ?"
                        + " AND take_on_batch_id IS NULL AND NOT endorsement_confirmed")
                    .formatted(S),
                Integer.class,
                INVESTMENT))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM \"%s\".trust_reconciliations WHERE id = ?".formatted(S),
                String.class,
                RECON))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".trust_accounts WHERE take_on_cutover_date IS NULL"
                        + " AND take_on_batch_id IS NULL")
                    .formatted(S),
                Integer.class))
        .isEqualTo(2);
  }

  // 2
  @Test
  void typeCheck_namesExactlyTheRegistry() {
    String def = constraintDef("chk_trust_txn_type");
    var names =
        Arrays.stream(def.split("'"))
            .filter(s -> s.matches("[A-Z_]+"))
            .collect(java.util.stream.Collectors.toSet());
    assertThat(names)
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(TrustTransactionType.values()).map(Enum::name).toList())
        .hasSize(36);
    assertRejected(
        () -> insertClientRow("TAKEON_BOGUS", MATTER, batch, false), "chk_trust_txn_type");
  }

  // 3
  @Test
  void takeOnBatch_isNamedExactlyByTakeOnRows() {
    assertRejected(
        () -> insertClientRow("TAKEON_CLIENT", MATTER, null, false), "chk_trust_txn_take_on_batch");
    assertRejected(
        () -> insertClientRow("DEPOSIT", MATTER, batch, false), "chk_trust_txn_take_on_batch");
  }

  // 4
  @Test
  void clearedAtCutover_onlyOnATakeOnRowWithoutALine() {
    assertRejected(
        () -> insertRow("TAKEON_CLIENT", "CLIENT", CLIENT, MATTER_2, null, null, batch, true, LINE),
        "chk_trust_txn_cutover_cleared");
    assertRejected(
        () -> insertClientRow("DEPOSIT", MATTER_2, null, true), "chk_trust_txn_cutover_cleared");
  }

  // 5
  @Test
  void takeOnUnidentifiedRow_namesItsItem() {
    assertRejected(
        () ->
            insertRow(
                "TAKEON_UNIDENTIFIED",
                "NON_CLIENT",
                null,
                null,
                UD_CREDITOR,
                null,
                batch,
                true,
                null),
        "chk_trust_txn_unidentified_item");
  }

  // 6
  @Test
  void oneBroughtForwardBalancePerClientAndMatter() {
    var otherBatch = insertBatch();
    var first = insertRow("TAKEON_CLIENT", "CLIENT", CLIENT, MATTER, null, null, batch, true, null);
    assertThat(first).isNotNull();
    assertRejected(
        () ->
            insertRow(
                "TAKEON_CLIENT", "CLIENT", CLIENT, MATTER, null, null, otherBatch, true, null),
        "uq_trust_txn_takeon_client_matter");
    // the outstanding items of the same client and matter are not limited
    insertRow("TAKEON_PAY_UNPRES", "CLIENT", CLIENT, MATTER, null, null, batch, false, null);
    insertRow("TAKEON_PAY_UNPRES", "CLIENT", CLIENT, MATTER, null, null, batch, false, null);
  }

  // 6b
  @Test
  void oneBroughtForwardBalancePerClient_withoutAMatterToo() {
    var otherBatch = insertBatch();
    var first = insertRow("TAKEON_CLIENT", "CLIENT", CLIENT, null, null, null, batch, true, null);
    assertThat(first).isNotNull();
    // NULLS NOT DISTINCT: a second matterless balance for the same client is refused
    assertRejected(
        () ->
            insertRow("TAKEON_CLIENT", "CLIENT", CLIENT, null, null, null, otherBatch, true, null),
        "uq_trust_txn_takeon_client_matter");
  }

  // 7
  @Test
  void accountTakeOn_dateAndBatchTogether() {
    assertRejected(
        () ->
            jdbc.update(
                "UPDATE \"%s\".trust_accounts SET take_on_cutover_date = DATE '2026-10-31' WHERE id = ?"
                    .formatted(S),
                INV_ACCOUNT),
        "chk_trust_account_take_on");
    assertRejected(
        () ->
            jdbc.update(
                "UPDATE \"%s\".trust_accounts SET take_on_batch_id = ? WHERE id = ?".formatted(S),
                batch,
                INV_ACCOUNT),
        "chk_trust_account_take_on");
  }

  // 8
  @Test
  void unidentifiedItem_comesFromALineOrATakeOn_neverBothOrNeither() {
    assertRejected(() -> insertItem(null, null), "chk_unidentified_deposit_origin");
    assertRejected(() -> insertItem(LINE, batch), "chk_unidentified_deposit_origin");
    assertThat(insertItem(null, batch)).isNotNull();
  }

  // 9
  @Test
  void takenOnSection864Investment_needsTheInstruction() {
    assertRejected(
        () -> insertInvestment("CLIENT_INSTRUCTION", batch, null, false, null, null),
        "chk_trust_invest_take_on_instruction");
    assertRejected(
        () -> insertInvestment("CLIENT_INSTRUCTION", batch, "INSTR-1", false, null, null),
        "chk_trust_invest_take_on_instruction");
    assertThat(insertInvestment("CLIENT_INSTRUCTION", batch, "INSTR-1", true, null, null))
        .isNotNull();
    // a section 86(3) investment taken on needs no client instruction
    assertThat(insertInvestment("FIRM_DISCRETION", batch, null, false, null, null)).isNotNull();
    // an investment placed in Kazi (no batch) is not touched by the rule
    assertThat(insertInvestment("CLIENT_INSTRUCTION", null, null, false, null, null)).isNotNull();
  }

  // 10
  @Test
  void takeOnAmounts_areNeverNegative() {
    assertRejected(
        () -> insertInvestment("FIRM_DISCRETION", batch, null, false, "-0.01", null),
        "chk_trust_invest_take_on_amounts");
    assertRejected(
        () -> insertInvestment("FIRM_DISCRETION", batch, null, false, null, "-0.01"),
        "chk_trust_invest_take_on_amounts");
    assertThat(insertInvestment("FIRM_DISCRETION", batch, null, false, "1234.56", "0.00"))
        .isNotNull();
  }

  // 11
  @Test
  void batchReferences_areForeignKeysThatNeverCascade() {
    var missing = UUID.randomUUID();
    assertRejected(
        () ->
            insertRow("TAKEON_CLIENT", "CLIENT", CLIENT, MATTER_2, null, null, missing, true, null),
        "trust_transactions_take_on_batch_fkey");
    assertRejected(
        () ->
            jdbc.update(
                ("UPDATE \"%s\".trust_accounts SET take_on_cutover_date = DATE '2026-10-31',"
                        + " take_on_batch_id = ? WHERE id = ?")
                    .formatted(S),
                missing,
                INV_ACCOUNT),
        "trust_accounts_take_on_batch_fkey");
    assertRejected(() -> insertItem(null, missing), "unidentified_deposits_take_on_batch_fkey");
    assertRejected(
        () -> insertInvestment("FIRM_DISCRETION", missing, null, false, null, null),
        "trust_investments_take_on_batch_fkey");
    for (String fk :
        java.util.List.of(
            "trust_transactions_take_on_batch_fkey",
            "trust_accounts_take_on_batch_fkey",
            "unidentified_deposits_take_on_batch_fkey",
            "trust_investments_take_on_batch_fkey")) {
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT c.confdeltype::text FROM pg_constraint c
                  JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = ? AND n.nspname = ?
                  """,
                  String.class,
                  fk,
                  S))
          .as("%s never cascades", fk)
          .isEqualTo("a");
    }
  }

  // 12
  @Test
  void rerunningTheMigrationBody_changesNothing_andThePoolIndexIsPartial() throws Exception {
    String typeBefore = constraintDef("chk_trust_txn_type");
    String itemBefore = constraintDef("chk_trust_txn_unidentified_item");
    rerunMigrationBody();
    assertThat(constraintDef("chk_trust_txn_type")).isEqualTo(typeBefore);
    assertThat(constraintDef("chk_trust_txn_unidentified_item"))
        .isEqualTo(itemBefore)
        .contains("TAKEON_UNIDENTIFIED");
    String poolIndex =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            String.class,
            S,
            "idx_trust_txn_take_on_open_items");
    assertThat(poolIndex)
        .contains("WHERE")
        .contains("take_on_batch_id IS NOT NULL")
        .contains("bank_statement_line_id IS NULL");
    String uniqueIndex =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?",
            String.class,
            S,
            "uq_trust_txn_takeon_client_matter");
    assertThat(uniqueIndex)
        .contains("UNIQUE")
        .contains("NULLS NOT DISTINCT")
        .contains("TAKEON_CLIENT");
  }

  // --- helpers ---

  private UUID insertBatch() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO "%s".take_on_batches
          (id, dataset, status, trust_account_id, cutover_date, created_by, created_at, updated_at)
        VALUES (?, 'TRUST_POSITION', 'UPLOADED', ?, DATE '2026-10-31', ?, now(), now())
        """
            .formatted(S),
        id,
        ACCOUNT,
        OWNER);
    return id;
  }

  private UUID insertClientRow(String type, UUID matter, UUID takeOnBatch, boolean cleared) {
    return insertRow(type, "CLIENT", CLIENT, matter, null, null, takeOnBatch, cleared, null);
  }

  private UUID insertRow(
      String type,
      String ledger,
      UUID customer,
      UUID project,
      UUID creditor,
      UUID unidentifiedItem,
      UUID takeOnBatch,
      boolean cleared,
      UUID line) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO "%s".trust_transactions (id, trust_account_id, transaction_type, amount,
            customer_id, project_id, creditor_account_id, unidentified_deposit_id, reference,
            transaction_date, status, recorded_by, ledger, cashbook_effect, ledger_effect,
            posted_at, take_on_batch_id, cleared_at_cutover, bank_statement_line_id)
        VALUES (?, ?, ?, 10.00, ?, ?, ?, ?, 'TO-1', DATE '2026-10-31', 'APPROVED', ?, ?,
                10.00, 10.00, now(), ?, ?, ?)
        """
            .formatted(S),
        id,
        ACCOUNT,
        type,
        customer,
        project,
        creditor,
        unidentifiedItem,
        OWNER,
        ledger,
        takeOnBatch,
        cleared,
        line);
    return id;
  }

  private UUID insertItem(UUID line, UUID takeOnBatch) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO "%s".unidentified_deposits (id, trust_account_id, creditor_account_id,
            bank_statement_line_id, received_date, bank_description, amount, status,
            take_on_batch_id)
        VALUES (?, ?, ?, ?, DATE '2025-06-01', 'Brought forward', 20.00, 'HELD', ?)
        """
            .formatted(S),
        id,
        ACCOUNT,
        UD_CREDITOR,
        line,
        takeOnBatch);
    return id;
  }

  private UUID insertInvestment(
      String basis,
      UUID takeOnBatch,
      String instructionReference,
      boolean endorsed,
      String accrued,
      String fundAmount) {
    UUID id = UUID.randomUUID();
    boolean client = "CLIENT_INSTRUCTION".equals(basis);
    jdbc.update(
        """
        INSERT INTO "%s".trust_investments (id, trust_account_id, investment_account_id,
            customer_id, project_id, institution, account_number, principal, interest_rate,
            deposit_date, investment_basis, take_on_batch_id, instruction_reference,
            instruction_date, endorsement_confirmed, accrued_interest_at_take_on,
            fund_amount_at_take_on)
        VALUES (?, ?, ?, ?, ?, 'Investec', 'INV-T', 100.00, 0.0500, DATE '2026-01-01', ?, ?, ?,
                ?, ?, ?, ?)
        """
            .formatted(S),
        id,
        ACCOUNT,
        INV_ACCOUNT,
        client ? CLIENT : null,
        client ? MATTER : null,
        basis,
        takeOnBatch,
        instructionReference,
        instructionReference == null ? null : java.sql.Date.valueOf("2025-12-01"),
        endorsed,
        accrued == null ? null : new java.math.BigDecimal(accrued),
        fundAmount == null ? null : new java.math.BigDecimal(fundAmount));
    return id;
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

  private static void assertRejected(Runnable write, String constraint) {
    assertThatThrownBy(write::run)
        .as("rejected by %s", constraint)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraint);
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
