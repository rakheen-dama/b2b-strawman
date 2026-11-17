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
 * Phase 92 slice 648C (task 648.1, architecture §16.2.7 and the {@code trust_accounts} part of
 * §16.7.7, ADR-379): tenant {@code V22} gives a trust account its investment basis — {@code
 * trust_accounts.investment_basis}, backfilled to {@code FIRM_DISCRETION} on existing {@code
 * INVESTMENT} accounts, with {@code chk_trust_account_investment_basis} (non-null exactly when the
 * type is {@code INVESTMENT}). A scratch tenant schema is migrated to V21, seeded with a {@code
 * GENERAL}, a {@code SECTION_86} and an {@code INVESTMENT} account, then migrated to V22 — pinned
 * so a later migration cannot change these expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentAccountBasisMigrationTest {

  /** The investment-basis migration (pinned, not "latest"). */
  private static final String MIGRATION_VERSION = "22";

  private static final String MIGRATION_FILE =
      "db/migration/tenant/V22__phase92_investment_account_basis.sql";

  private static final String CHECK_VIOLATION = "23514";
  private static final String CONSTRAINT = "chk_trust_account_investment_basis";

  // Never provisioned or mapped — only names the scratch schema.
  private static final String SCRATCH_SCHEMA =
      SchemaNameGenerator.generateSchemaName("org_investment_account_basis_scratch");

  /** Placeholders — none is a prefix of another. */
  private static final Map<String, String> IDS = new LinkedHashMap<>();

  static {
    IDS.put("%general", "00000000-0000-0000-0000-0000000000c1");
    IDS.put("%banking", "00000000-0000-0000-0000-0000000000c2");
    IDS.put("%invest", "00000000-0000-0000-0000-0000000000c3");
  }

  private final DataSource migrationDataSource;
  private List<String> seededRowsBefore;

  @Autowired
  InvestmentAccountBasisMigrationTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchSchemaFromV21() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
    exec("CREATE SCHEMA \"%s\"");
    tenantFlyway().target(MigrationVersion.fromVersion("21")).load().migrate();
    exec(
        """
        INSERT INTO "%s".trust_accounts
          (id, account_name, bank_name, branch_code, account_number, account_type, is_primary,
           require_dual_approval, payment_approval_threshold, status, opened_date, notes)
        VALUES ('%general', 'Trust General', 'FNB', '250655', '62000000022', 'GENERAL', true,
                false, NULL, 'ACTIVE', DATE '2025-01-01', NULL),
               ('%banking', 'Trust Section 86', 'Absa', '632005', '40000000022', 'SECTION_86', false,
                true, 50000.00, 'ACTIVE', DATE '2025-02-01', 'demo main account'),
               ('%invest', 'Trust Investment', 'Investec', '580105', '71000000022', 'INVESTMENT',
                false, false, NULL, 'ACTIVE', DATE '2025-03-01', 'call account');
        """);
    seededRowsBefore = seededRows();
    tenantFlyway().target(MigrationVersion.fromVersion(MIGRATION_VERSION)).load().migrate();
  }

  @AfterAll
  void dropScratch() throws Exception {
    exec("DROP SCHEMA IF EXISTS \"%s\" CASCADE");
  }

  @Test
  void theColumnExistsWithItsType() throws Exception {
    assertThat(
            text(
                "SELECT data_type || '|' || character_maximum_length || '|' || is_nullable"
                    + " FROM information_schema.columns WHERE table_schema = '%s'"
                    + " AND table_name = 'trust_accounts' AND column_name = 'investment_basis'"))
        .isEqualTo("character varying|20|YES");
  }

  @Test
  void anExistingInvestmentAccountIsBackfilledToFirmDiscretion_theOthersStayNull()
      throws Exception {
    assertThat(basisOf("%invest")).isEqualTo("FIRM_DISCRETION");
    assertThat(basisOf("%general")).isEqualTo("NULL");
    assertThat(basisOf("%banking")).isEqualTo("NULL");
    // Every other column of the three rows is unchanged; no row is deleted
    assertThat(seededRows()).isEqualTo(seededRowsBefore);
  }

  @Test
  void theConstraintExists_andNamesBothBases() throws Exception {
    assertThat(
            text(
                "SELECT c.contype::text || '|' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                    + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = '%s'"
                    + " AND c.conname = '"
                    + CONSTRAINT
                    + "'"))
        .startsWith("c|")
        .contains("'FIRM_DISCRETION'", "'CLIENT_INSTRUCTION'", "'INVESTMENT'")
        // A NULL basis must not slip through three-valued logic (a CHECK passes on NULL)
        .contains("investment_basis IS NOT NULL");
  }

  @Test
  void theCheckRefusesTheBadShapes_andAcceptsTheGoodOnes() throws Exception {
    assertRefused(account("INVESTMENT", "NULL"));
    assertRefused(account("INVESTMENT", "'OTHER'"));
    assertRefused(account("GENERAL", "'FIRM_DISCRETION'"));
    assertRefused(account("SECTION_86", "'CLIENT_INSTRUCTION'"));

    exec(account("INVESTMENT", "'FIRM_DISCRETION'"));
    exec(account("INVESTMENT", "'CLIENT_INSTRUCTION'"));
    exec(account("GENERAL", "NULL"));
    exec(account("SECTION_86", "NULL"));
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

  /** A non-primary account of {@code type} with {@code basis} (an SQL literal). */
  private static String account(String type, String basis) {
    return """
        INSERT INTO "%%s".trust_accounts (account_name, bank_name, branch_code, account_number,
            account_type, is_primary, investment_basis, opened_date)
        VALUES ('Shape %1$s', 'FNB', '250655', '62000000099', '%1$s', false, %2$s,
                DATE '2025-04-01')
        """
        .formatted(type, basis);
  }

  private String basisOf(String idPlaceholder) throws Exception {
    return text(
        "SELECT COALESCE(investment_basis, 'NULL') FROM \"%s\".trust_accounts WHERE id = '"
            + idPlaceholder
            + "'");
  }

  /** Refused by {@code chk_trust_account_investment_basis} (SQLState 23514, the CHECK named). */
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

  /** The seeded rows, every column but the new one — what the migration must not change. */
  private List<String> seededRows() throws Exception {
    return texts(
        "SELECT id, account_name, bank_name, branch_code, account_number, account_type,"
            + " is_primary, require_dual_approval, COALESCE(payment_approval_threshold::text,"
            + " '-'), status, opened_date, COALESCE(closed_date::text, '-'),"
            + " COALESCE(notes, '-'), created_at, updated_at FROM \"%s\".trust_accounts"
            + " WHERE id IN ('%general', '%banking', '%invest') ORDER BY id");
  }

  /** The column, the constraint and the seeded rows' basis — what a second run must not change. */
  private List<String> fingerprint() throws Exception {
    var rows =
        texts(
            "SELECT data_type || '|' || character_maximum_length || '|' || is_nullable"
                + " FROM information_schema.columns WHERE table_schema = '%s'"
                + " AND table_name = 'trust_accounts' AND column_name = 'investment_basis'");
    rows.addAll(
        texts(
            "SELECT c.conname || '|' || pg_get_constraintdef(c.oid) FROM pg_constraint c"
                + " JOIN pg_namespace n ON n.oid = c.connamespace JOIN pg_class t"
                + " ON t.oid = c.conrelid WHERE n.nspname = '%s' AND t.relname = 'trust_accounts'"
                + " ORDER BY c.conname"));
    rows.addAll(
        texts(
            "SELECT id || '|' || COALESCE(investment_basis, 'NULL') FROM \"%s\".trust_accounts"
                + " WHERE id IN ('%general', '%banking', '%invest') ORDER BY id"));
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
