package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Global {@code V7__portal_trust_invested_balance} (Phase 92 648D, §16.6.3, §16.7.8 global part):
 * {@code portal.portal_trust_balance.invested_balance} and {@code
 * portal.portal_trust_transaction.ledger}. A scratch DATABASE (global V1 hard-codes {@code portal.}
 * and unqualified {@code public} tables) is migrated to V6, seeded with one balance and one
 * transaction row in the pre-V7 shape, then migrated to V7: the rows projected before V7 read
 * {@code invested_balance = 0} and {@code ledger = 'CLIENT'}, right for every one of them (until
 * 648D the sync admitted CLIENT-ledger rows only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalV7PortalTrustInvestedBalanceTest {

  private static final String SCRATCH_GLOBAL_DB = "global_v7_portal_trust_invested_scratch";
  private static final String BALANCE_CUSTOMER = "7a000000-0000-0000-0000-000000000001";
  private static final String TXN_ID = "7a000000-0000-0000-0000-0000000000a1";

  private final DataSource migrationDataSource;
  private DataSource scratch;
  private List<String> columnsAtV6;

  @Autowired
  GlobalV7PortalTrustInvestedBalanceTest(
      @Qualifier("migrationDataSource") DataSource migrationDataSource) {
    this.migrationDataSource = migrationDataSource;
  }

  @BeforeAll
  void migrateScratchGlobalFromV6() throws Exception {
    String serverUrl;
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
      s.execute("CREATE DATABASE " + SCRATCH_GLOBAL_DB);
      serverUrl = c.getMetaData().getURL();
    }
    String scratchUrl =
        serverUrl.replaceFirst("^(jdbc:postgresql://[^/]+/)[^?]*", "$1" + SCRATCH_GLOBAL_DB);
    try (Connection c = migrationDataSource.getConnection()) {
      scratch =
          new SimpleDriverDataSource(
              new org.postgresql.Driver(), scratchUrl, c.getMetaData().getUserName(), "postgres");
    }
    flyway().target(MigrationVersion.fromVersion("6")).load().migrate();
    columnsAtV6 = newColumns();
    // Rows projected before V7, in the V6 column list (transaction_date / recorded_at NOT NULL
    // since V4)
    exec(
        """
        INSERT INTO portal.portal_trust_balance
            (customer_id, matter_id, current_balance, last_transaction_at)
        VALUES ('%s', '7a000000-0000-0000-0000-000000000002', 1500.00, now())
        """
            .formatted(BALANCE_CUSTOMER));
    exec(
        """
        INSERT INTO portal.portal_trust_transaction
            (id, customer_id, matter_id, transaction_type, amount, running_balance, occurred_at,
             description, reference, transaction_date, recorded_at)
        VALUES ('%s', '%s', '7a000000-0000-0000-0000-000000000002', 'DEPOSIT', 1500.00,
                1500.00, now(), 'Deposit', 'DEP-V7', DATE '2026-10-01', now())
        """
            .formatted(TXN_ID, BALANCE_CUSTOMER));
    flyway().target(MigrationVersion.fromVersion("7")).load().migrate();
  }

  @AfterAll
  void dropScratchDatabase() throws Exception {
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + SCRATCH_GLOBAL_DB + " WITH (FORCE)");
    }
  }

  @Test
  void columnsAreAbsentAtV6AndMatchTheSpecAtV7() throws Exception {
    assertThat(columnsAtV6).isEmpty();
    assertThat(newColumns())
        .containsExactly(
            "portal_trust_balance|invested_balance|numeric|15|2||NO|0",
            "portal_trust_transaction|ledger|character varying|||20|NO|'CLIENT'::character varying");
  }

  @Test
  void rowsProjectedBeforeV7ReadTheDefaults() throws Exception {
    assertThat(
            single(
                "SELECT invested_balance::text FROM portal.portal_trust_balance"
                    + " WHERE customer_id = '"
                    + BALANCE_CUSTOMER
                    + "'"))
        .isEqualTo("0.00");
    assertThat(
            single(
                "SELECT ledger FROM portal.portal_trust_transaction WHERE id = '" + TXN_ID + "'"))
        .isEqualTo("CLIENT");
  }

  @Test
  void reRunningTheV7BodyAndMigratingAgainAreNoOps() throws Exception {
    String v7 =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/global/V7__portal_trust_invested_balance.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    exec(v7);

    assertThat(
            flyway().target(MigrationVersion.fromVersion("7")).load().migrate().migrationsExecuted)
        .isZero();
    assertThat(
            single(
                "SELECT max(version::int) || '|' || count(*) || '|' || bool_and(success)"
                    + " FROM public.flyway_schema_history"))
        .isEqualTo("7|7|true");
    assertThat(newColumns()).hasSize(2);
  }

  private List<String> newColumns() throws Exception {
    return rows(
        """
        SELECT table_name || '|' || column_name || '|' || data_type || '|'
               || coalesce(numeric_precision::text, '') || '|'
               || coalesce(numeric_scale::text, '') || '|'
               || coalesce(character_maximum_length::text, '') || '|' || is_nullable || '|'
               || coalesce(column_default, '')
        FROM information_schema.columns
        WHERE table_schema = 'portal'
          AND ((table_name = 'portal_trust_balance' AND column_name = 'invested_balance')
            OR (table_name = 'portal_trust_transaction' AND column_name = 'ledger'))
        ORDER BY table_name
        """);
  }

  // Same config as FlywayConfig.globalFlyway, on the scratch database.
  private FluentConfiguration flyway() {
    return Flyway.configure()
        .dataSource(scratch)
        .locations("classpath:db/migration/global")
        .schemas("public")
        .baselineOnMigrate(false);
  }

  private void exec(String sql) throws Exception {
    try (Connection c = scratch.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private String single(String sql) throws Exception {
    List<String> out = rows(sql);
    assertThat(out).hasSize(1);
    return out.getFirst();
  }

  private List<String> rows(String sql) throws Exception {
    List<String> out = new ArrayList<>();
    try (Connection c = scratch.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    }
    return out;
  }
}
